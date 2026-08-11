package com.example.courseschedule

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

class BackupCodecTest {
    @Test
    fun emptyArchiveRoundTripsWithoutImplicitDefaults() {
        val source = BackupArchive(
            metadata = BackupSourceMetadata(
                createdAt = "2026-08-10T12:00:00Z",
                sourceAppVersionName = "1.1.4",
                sourceVersionCode = 24,
                sourcePackageName = "com.example.courseschedule",
                sourceDatabaseVersion = 34,
                devicePlatform = "Android"
            ),
            data = BackupData(dataVersion = BackupFormatV1.DATA_VERSION),
            preferences = BackupPreferences(preferencesVersion = BackupFormatV1.PREFERENCES_VERSION)
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(source))

        assertEquals(0, decoded.manifest.assetCount)
        assertEquals(0, decoded.manifest.missingAssetCount)
        assertEquals(source.data, decoded.data)
        assertEquals(source.preferences, decoded.preferences)
    }

    @Test
    fun roundTripPreservesStableIdsAndAssetManifest() {
        val source = fixtureArchive()

        val decoded = BackupCodec.decode(BackupCodec.encode(source))

        assertEquals(BackupFormatV1.FORMAT_VERSION, decoded.manifest.formatVersion)
        assertEquals(1, decoded.manifest.assetCount)
        assertEquals(1, decoded.manifest.missingAssetCount)
        assertEquals(source.data, decoded.data)
        assertEquals(source.preferences, decoded.preferences)
        assertEquals(source.assets.map { it.assetId }, decoded.assets.map { it.assetId })
        assertArrayEquals(source.assets.first().bytes, decoded.assets.first().bytes)
        assertFalse(decoded.assets[1].bytes != null)
        assertEquals("源文件已经被用户删除", decoded.assets[1].missingReason)
    }

    @Test
    fun stableIdsAreTypedAndDoNotAcceptRoomNumbers() {
        val id = BackupStableId.new(BackupStableId.SCHEDULE_PREFIX)

        assertTrue(BackupStableId.isValid(id, BackupStableId.SCHEDULE_PREFIX))
        assertFalse(BackupStableId.isValid("schedule_7", BackupStableId.SCHEDULE_PREFIX))
        assertFalse(BackupStableId.isValid(id, BackupStableId.COURSE_PREFIX))
        assertFalse(BackupStableId.isValid("../schedule_550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun unknownFormatVersionFailsBeforeAnyImportCanRun() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        val manifest = BackupJson.decodeFromString<BackupManifest>(
            entries.getValue(BackupFormatV1.MANIFEST_ENTRY).toString(StandardCharsets.UTF_8)
        ).copy(formatVersion = 2)
        entries[BackupFormatV1.MANIFEST_ENTRY] = BackupJson.encodeToString(manifest)
            .toByteArray(StandardCharsets.UTF_8)

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun checksumMismatchFails() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        entries[BackupFormatV1.DATA_ENTRY] = "corrupted".toByteArray(StandardCharsets.UTF_8)

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun outputUsesFixedEntryOrderAndManifestDerivedAssetPath() {
        val source = fixtureArchive()
        val names = zipEntryNames(BackupCodec.encode(source))

        assertEquals(
            listOf(
                BackupFormatV1.MANIFEST_ENTRY,
                BackupFormatV1.DATA_ENTRY,
                BackupFormatV1.PREFERENCES_ENTRY,
                BackupFormatV1.CHECKSUMS_ENTRY,
                "assets/wallpapers/${source.assets.first().assetId}"
            ),
            names
        )
    }

    @Test
    fun missingRequiredEntryFailsBeforeDecode() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        entries.remove(BackupFormatV1.DATA_ENTRY)

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun duplicateEntryFails() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        val pairs = entries.entries.map { it.key to it.value } +
            (BackupFormatV1.MANIFEST_ENTRY to entries.getValue(BackupFormatV1.MANIFEST_ENTRY))

        assertCodecFailure { BackupCodec.decode(storedZip(pairs)) }
    }

    @Test
    fun malformedJsonFailsAfterChecksumValidation() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        replaceEntryAndChecksum(entries, BackupFormatV1.DATA_ENTRY, "{}".toByteArray(StandardCharsets.UTF_8))

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun corruptZipCrcFails() {
        val payload = ByteArray(4096) { 0x5a }
        val crc = CRC32().apply { update(payload) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entry = ZipEntry(BackupFormatV1.DATA_ENTRY).apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = payload.size.toLong()
                this.crc = crc.value
            }
            zip.putNextEntry(entry)
            zip.write(payload)
            zip.closeEntry()
        }
        val corrupted = output.toByteArray()
        val payloadStart = indexOfSequence(corrupted, payload)
        assertTrue(payloadStart >= 0)
        corrupted[payloadStart] = 0x00

        assertCodecFailure { BackupCodec.decode(corrupted) }
    }

    @Test
    fun truncatedZipCentralDirectoryFails() {
        val encoded = BackupCodec.encode(fixtureArchive())

        assertCodecFailure { BackupCodec.decode(encoded.copyOf(encoded.size - 5)) }
    }

    @Test
    fun decoderDoesNotCloseCallerInputStream() {
        val input = TrackingInputStream(BackupCodec.encode(fixtureArchive()))

        BackupCodec.decode(input)

        assertFalse(input.closed)
    }

    @Test
    fun compressionRatioLimitRejectsHighlyCompressedEntry() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormatV1.DATA_ENTRY))
            zip.write(ByteArray(1_000_000))
            zip.closeEntry()
        }

        assertCodecFailure { BackupCodec.decode(output.toByteArray()) }
    }

    @Test
    fun zipSlipEntryFails() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../evil"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }

        assertCodecFailure { BackupCodec.decode(output.toByteArray()) }
    }

    @Test
    fun unknownEntryFailsEvenWhenTheRequiredPayloadIsValid() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        entries["notes.txt"] = "not part of v1".toByteArray(StandardCharsets.UTF_8)

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun secretPreferenceKeyIsRejected() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        val unsafePreferences = """
            {"preferencesVersion":1,"apiKey":"must-not-be-exported"}
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        entries[BackupFormatV1.PREFERENCES_ENTRY] = unsafePreferences
        val checksums = BackupJson.decodeFromString<BackupChecksums>(
            entries.getValue(BackupFormatV1.CHECKSUMS_ENTRY).toString(StandardCharsets.UTF_8)
        )
        val updatedChecksums = checksums.copy(
            entries = checksums.entries.toMutableMap().apply {
                put(BackupFormatV1.PREFERENCES_ENTRY, sha256(unsafePreferences))
            }
        )
        entries[BackupFormatV1.CHECKSUMS_ENTRY] = BackupJson.encodeToString(updatedChecksums)
            .toByteArray(StandardCharsets.UTF_8)

        assertCodecFailure { BackupCodec.decode(zipEntriesToBytes(entries)) }
    }

    @Test
    fun encodedPreferencesContainNoCredentialFields() {
        val entries = zipEntries(BackupCodec.encode(fixtureArchive()))
        val preferences = entries.getValue(BackupFormatV1.PREFERENCES_ENTRY)
            .toString(StandardCharsets.UTF_8)

        assertFalse(preferences.contains("apiKey", ignoreCase = true))
        assertFalse(preferences.contains("accessToken", ignoreCase = true))
        assertFalse(preferences.contains("refreshToken", ignoreCase = true))
        assertFalse(preferences.contains("password", ignoreCase = true))
        assertFalse(preferences.contains("cookie", ignoreCase = true))
        assertFalse(preferences.contains("encryptionKey", ignoreCase = true))
        assertFalse(preferences.contains("credential", ignoreCase = true))
    }

    @Test
    fun oversizedJsonEntryFailsBeforeParsing() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormatV1.DATA_ENTRY))
            zip.write(ByteArray(BackupCodecLimits.MAX_JSON_BYTES.toInt() + 1))
            zip.closeEntry()
        }

        assertCodecFailure { BackupCodec.decode(output.toByteArray()) }
    }

    @Test
    fun oversizedAssetHeaderFailsBeforeAllocatingPayload() {
        val assetId = BackupStableId.new(BackupStableId.ASSET_PREFIX)
        val path = "assets/wallpapers/$assetId"

        assertCodecFailure {
            BackupCodec.decode(
                storedZipWithDeclaredSize(path, BackupCodecLimits.MAX_ASSET_BYTES + 1)
            )
        }
    }

    @Test
    fun assetStagerWritesOnlyToOperationDirectoryAndIsIdempotent() {
        val root = Files.createTempDirectory("sleepdown-asset-stage").toFile()
        try {
            val decoded = BackupCodec.decode(BackupCodec.encode(fixtureArchive()))

            val first = BackupAssetStager.stage(root, decoded, "op-20260810")
            val wallpaperId = decoded.assets.first().assetId
            val stagedWallpaper = first.presentFilesByAssetId.getValue(wallpaperId)
            val stagingRoot = File(root, ".sleepdown_restore").canonicalFile
            assertTrue(stagedWallpaper.exists())
            assertTrue(stagedWallpaper.canonicalPath.startsWith(stagingRoot.path + File.separator))
            assertEquals(1, first.missingAssets.size)

            val second = BackupAssetStager.stage(root, decoded, "op-20260810")
            assertEquals(stagedWallpaper.canonicalPath, second.presentFilesByAssetId.getValue(wallpaperId).canonicalPath)
            assertArrayEquals(stagedWallpaper.readBytes(), second.presentFilesByAssetId.getValue(wallpaperId).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun assetStagerRejectsChangedBytesForAnExistingOperation() {
        val root = Files.createTempDirectory("sleepdown-asset-stage-conflict").toFile()
        try {
            val decoded = BackupCodec.decode(BackupCodec.encode(fixtureArchive()))
            val first = BackupAssetStager.stage(root, decoded, "op-conflict")
            first.presentFilesByAssetId.values.single().writeBytes(byteArrayOf(9, 9, 9))

            assertStageFailure { BackupAssetStager.stage(root, decoded, "op-conflict") }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun stableIdsMustBeUniqueAcrossTheWholeArchive() {
        val source = fixtureArchive()
        val originalSchedule = source.data.schedules.single()
        val duplicateCourseSchedule = originalSchedule.copy(
            id = BackupStableId.new(BackupStableId.SCHEDULE_PREFIX),
            isActive = false,
            courses = originalSchedule.courses
        )
        val invalid = source.copy(
            data = source.data.copy(
                schedules = listOf(originalSchedule, duplicateCourseSchedule)
            )
        )

        assertCodecFailure { BackupCodec.encode(invalid) }
    }

    @Test
    fun assetPurposeMimeAndOwnerAreValidated() {
        val source = fixtureArchive()
        val unknownOwner = BackupStableId.new(BackupStableId.SCHEDULE_PREFIX)
        val invalid = source.copy(
            assets = source.assets.mapIndexed { index, asset ->
                if (index == 0) asset.copy(mediaType = "not-a-mime", ownerId = unknownOwner) else asset
            }
        )

        assertCodecFailure { BackupCodec.encode(invalid) }
    }

    private fun fixtureArchive(): BackupArchive {
        val scheduleId = BackupStableId.new(BackupStableId.SCHEDULE_PREFIX)
        val courseId = BackupStableId.new(BackupStableId.COURSE_PREFIX)
        val schemeId = BackupStableId.new(BackupStableId.SCHEME_PREFIX)
        val sessionId = BackupStableId.new(BackupStableId.SESSION_PREFIX)
        val messageId = BackupStableId.new(BackupStableId.MESSAGE_PREFIX)
        val widgetId = BackupStableId.new(BackupStableId.WIDGET_PREFIX)
        val wallpaperAssetId = BackupStableId.new(BackupStableId.ASSET_PREFIX)
        val missingAssetId = BackupStableId.new(BackupStableId.ASSET_PREFIX)
        val historyId = BackupStableId.new(BackupStableId.HISTORY_PREFIX)

        return BackupArchive(
            metadata = BackupSourceMetadata(
                createdAt = "2026-08-10T12:00:00Z",
                sourceAppVersionName = "1.1.4",
                sourceVersionCode = 24,
                sourcePackageName = "com.example.courseschedule",
                sourceDatabaseVersion = 34,
                devicePlatform = "Android"
            ),
            data = BackupData(
                dataVersion = BackupFormatV1.DATA_VERSION,
                schedules = listOf(
                    BackupSchedule(
                        id = scheduleId,
                        name = "源课表",
                        isActive = true,
                        config = fixtureConfig(wallpaperAssetId),
                        courses = listOf(
                            BackupCourse(
                                id = courseId,
                                name = "数据结构",
                                teacher = "老师",
                                location = "A101",
                                weekday = 2,
                                periods = listOf(1, 2),
                                weeks = listOf(1, 2, 3),
                                weekParity = "ALL",
                                note = "保留"
                            )
                        ),
                        periods = listOf(
                            BackupPeriod(1, "08:00", "08:45"),
                            BackupPeriod(2, "08:50", "09:35")
                        ),
                        periodSchemes = listOf(
                            BackupPeriodScheme(
                                id = schemeId,
                                name = "默认作息",
                                mode = "MANUAL",
                                isActive = true,
                                classDurationMinutes = 45,
                                breakDurationMinutes = 10,
                                morningStartTime = "08:00",
                                noonStartTime = "12:00",
                                afternoonStartTime = "14:00",
                                eveningStartTime = "19:00",
                                specialBreaksJson = "{}",
                                overridesJson = "{}",
                                times = listOf(BackupPeriodSchemeTime(1, "08:00", "08:45"))
                            )
                        ),
                        agentDailySessions = listOf(
                            BackupAgentDailySession(
                                id = sessionId,
                                date = "2026-08-10",
                                dailyPackJson = "{}",
                                providerId = "none",
                                model = "",
                                createdAt = 1L,
                                updatedAt = 2L,
                                generationStatus = "READY",
                                lastError = null
                            )
                        ),
                        agentMessages = listOf(
                            BackupAgentMessage(
                                id = messageId,
                                sessionId = sessionId,
                                sessionDate = "2026-08-10",
                                role = "user",
                                content = "请读取这张图",
                                createdAt = 3L,
                                status = "READY",
                                attachmentAssetIds = listOf(missingAssetId)
                            )
                        )
                    )
                ),
                widgetAppearances = listOf(
                    BackupWidgetAppearance(
                        id = widgetId,
                        variant = "COURSES_LARGE",
                        scope = "default",
                        enabled = true,
                        wallpaperAssetId = wallpaperAssetId,
                        centerX = 0.5f,
                        centerY = 0.5f,
                        scale = 1f,
                        sourceWidth = 100,
                        sourceHeight = 100,
                        blurDp = 2f,
                        brightness = 0.9f
                    )
                )
            ),
            preferences = BackupPreferences(
                preferencesVersion = BackupFormatV1.PREFERENCES_VERSION,
                appIcon = BackupAppIconPreferences("FOLLOW_DARK_MODE", true, false),
                dayAgent = BackupDayAgentPreferences(
                    hasDecision = true,
                    enabled = true,
                    dailyAiEnabled = true,
                    weatherEnabled = false,
                    memoryEnabled = true,
                    memory = "偏好简短回答",
                    memoryTurnDay = "2026-08-10",
                    memoryTurnCount = 3,
                    memoryLastAgentUpdateDay = null,
                    appliedActionsBySchedule = mapOf(scheduleId to listOf("action-1"))
                ),
                aiImport = BackupAiImportPreferences(
                    selectedProviderId = "custom:example",
                    providers = listOf(
                        BackupAiProvider(
                            id = "custom:example",
                            displayName = "自定义模型",
                            providerType = "OPENAI_COMPATIBLE",
                            baseUrl = "https://example.com/v1",
                            model = "model-a",
                            authType = "BEARER",
                            supportsImageInput = true,
                            supportsPdfFileInput = false,
                            supportsJsonSchema = true,
                            supportsJsonMode = true,
                            supportsFileUpload = false,
                            supportsResponses = false,
                            supportsVision = true,
                            supportsPdfDirect = false,
                            endpointStyle = "CHAT_COMPLETIONS",
                            structuredOutputMode = "JSON_SCHEMA",
                            inputMode = "TEXT_AND_IMAGE",
                            reasoningEffort = "DEFAULT"
                        )
                    )
                ),
                aiImportHistory = listOf(
                    BackupAiImportHistoryEntry(
                        id = historyId,
                        createdAt = 4L,
                        title = "导入记录",
                        prompt = "按规则导入",
                        sourceSummary = "本地图片",
                        payload = "{}",
                        contextAssetId = null
                    )
                )
            ),
            assets = listOf(
                BackupAsset(
                    assetId = wallpaperAssetId,
                    category = BackupAssetCategory.WALLPAPERS,
                    purpose = BackupAssetPurpose.SCHEDULE_WALLPAPER,
                    mediaType = "image/webp",
                    bytes = byteArrayOf(1, 2, 3, 4),
                    ownerId = scheduleId
                ),
                BackupAsset(
                    assetId = missingAssetId,
                    category = BackupAssetCategory.SCHEDULES,
                    purpose = BackupAssetPurpose.AGENT_ATTACHMENT,
                    mediaType = "image/jpeg",
                    bytes = null,
                    ownerId = messageId,
                    missingReason = "源文件已经被用户删除"
                )
            )
        )
    }

    private fun fixtureConfig(wallpaperAssetId: String) = BackupScheduleConfig(
        totalWeeks = 20,
        currentWeek = 6,
        notificationLeadMinutes = 15,
        termStartDate = "2026-02-23",
        autoCurrentWeek = true,
        termState = "ACTIVE",
        notificationsEnabled = true,
        notificationMode = "STANDARD",
        wallpaperAssetId = wallpaperAssetId,
        wallpaperBlur = 2f,
        wallpaperBrightness = 0.9f,
        wallpaperPortraitCenterX = 0.5f,
        wallpaperPortraitCenterY = 0.5f,
        wallpaperPortraitScale = 1f,
        wallpaperLandscapeCenterX = 0.5f,
        wallpaperLandscapeCenterY = 0.5f,
        wallpaperLandscapeScale = 1f,
        wallpaperSourceWidth = 100,
        wallpaperSourceHeight = 100,
        cardColorArgb = 0xFFD6E9FF,
        cardAlpha = 1f,
        courseCardBlur = 18f,
        courseCardGlassEnabled = true,
        courseCardFontScale = 1f,
        weekCardHeightDp = null,
        homeTextLight = false,
        followSystemDarkMode = true,
        darkMode = false,
        defaultWallpaperStyle = "NONE",
        hideEmptyWeekends = false,
        dockAlignment = "LEFT",
        defaultHomeMode = "WEEK",
        liveUpdateActionsEnabled = true,
        liveUpdateChipTextMode = "LOCATION",
        classDurationMinutes = 45,
        breakDurationMinutes = 10,
        morningPeriodCount = 4,
        noonPeriodCount = 0,
        afternoonPeriodCount = 4,
        eveningPeriodCount = 4,
        hideFromRecents = false,
        autoCheckUpdates = true
    )

    private fun assertCodecFailure(block: () -> Unit) {
        try {
            block()
            fail("预期 BackupCodecException")
        } catch (_: BackupCodecException) {
            // Expected.
        }
    }

    private fun assertStageFailure(block: () -> Unit) {
        try {
            block()
            fail("预期 BackupAssetStageException")
        } catch (_: BackupAssetStageException) {
            // Expected.
        }
    }

    private fun replaceEntryAndChecksum(
        entries: MutableMap<String, ByteArray>,
        name: String,
        bytes: ByteArray
    ) {
        entries[name] = bytes
        val checksums = BackupJson.decodeFromString<BackupChecksums>(
            entries.getValue(BackupFormatV1.CHECKSUMS_ENTRY).toString(StandardCharsets.UTF_8)
        )
        entries[BackupFormatV1.CHECKSUMS_ENTRY] = BackupJson.encodeToString(
            checksums.copy(entries = checksums.entries.toMutableMap().apply { put(name, sha256(bytes)) })
        ).toByteArray(StandardCharsets.UTF_8)
    }

    private fun zipEntries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return result
    }

    private fun zipEntriesToBytes(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.closeEntry()
            }
        }
        return names
    }

    private fun storedZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        val offsets = ArrayList<Int>(entries.size)
        val metadata = ArrayList<StoredEntry>(entries.size)
        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(bytes) }.value
            offsets += output.size()
            writeU32(output, 0x04034b50)
            writeU16(output, 20)
            writeU16(output, 0)
            writeU16(output, ZipEntry.STORED)
            writeU16(output, 0)
            writeU16(output, 0)
            writeU32(output, crc)
            writeU32(output, bytes.size.toLong())
            writeU32(output, bytes.size.toLong())
            writeU16(output, nameBytes.size)
            writeU16(output, 0)
            output.write(nameBytes)
            output.write(bytes)
            metadata += StoredEntry(nameBytes, crc, bytes.size)
        }
        val centralDirectoryOffset = output.size()
        metadata.forEachIndexed { index, entry ->
            writeU32(output, 0x02014b50)
            writeU16(output, 20)
            writeU16(output, 20)
            writeU16(output, 0)
            writeU16(output, ZipEntry.STORED)
            writeU16(output, 0)
            writeU16(output, 0)
            writeU32(output, entry.crc)
            writeU32(output, entry.size.toLong())
            writeU32(output, entry.size.toLong())
            writeU16(output, entry.name.size)
            writeU16(output, 0)
            writeU16(output, 0)
            writeU16(output, 0)
            writeU16(output, 0)
            writeU32(output, 0)
            writeU32(output, offsets[index].toLong())
            output.write(entry.name)
        }
        val centralDirectorySize = output.size() - centralDirectoryOffset
        writeU32(output, 0x06054b50)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU16(output, entries.size)
        writeU16(output, entries.size)
        writeU32(output, centralDirectorySize.toLong())
        writeU32(output, centralDirectoryOffset.toLong())
        writeU16(output, 0)
        return output.toByteArray()
    }

    private fun storedZipWithDeclaredSize(name: String, declaredSize: Long): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArrayOutputStream()
        writeU32(output, 0x04034b50)
        writeU16(output, 20)
        writeU16(output, 0)
        writeU16(output, ZipEntry.STORED)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU32(output, 0)
        writeU32(output, declaredSize)
        writeU32(output, declaredSize)
        writeU16(output, nameBytes.size)
        writeU16(output, 0)
        output.write(nameBytes)

        val centralDirectoryOffset = output.size()
        writeU32(output, 0x02014b50)
        writeU16(output, 20)
        writeU16(output, 20)
        writeU16(output, 0)
        writeU16(output, ZipEntry.STORED)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU32(output, 0)
        writeU32(output, declaredSize)
        writeU32(output, declaredSize)
        writeU16(output, nameBytes.size)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU32(output, 0)
        writeU32(output, 0)
        output.write(nameBytes)

        val centralDirectorySize = output.size() - centralDirectoryOffset
        writeU32(output, 0x06054b50)
        writeU16(output, 0)
        writeU16(output, 0)
        writeU16(output, 1)
        writeU16(output, 1)
        writeU32(output, centralDirectorySize.toLong())
        writeU32(output, centralDirectoryOffset.toLong())
        writeU16(output, 0)
        return output.toByteArray()
    }

    private fun writeU16(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeU32(output: ByteArrayOutputStream, value: Long) {
        output.write((value and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 24) and 0xff).toInt())
    }

    private data class StoredEntry(val name: ByteArray, val crc: Long, val size: Int)

    private fun indexOfSequence(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty()) return 0
        for (start in 0..source.size - target.size) {
            var matches = true
            for (offset in target.indices) {
                if (source[start + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
