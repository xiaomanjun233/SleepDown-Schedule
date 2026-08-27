package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupCodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object BackupCodecLimits {
    const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
    const val MAX_ENTRY_COUNT = 1024
    const val MAX_JSON_BYTES = 8L * 1024L * 1024L
    const val MAX_ASSET_BYTES = 32L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
    const val MAX_ASSET_COUNT = 256
    const val MAX_COMPRESSION_RATIO = 100L
    const val MAX_FIELD_LENGTH = 256
}

internal val BackupJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
    isLenient = false
    prettyPrint = false
}

/**
 * ZIP codec for the stable v1 protocol. This class is deliberately independent from Android
 * Context, Room and Repository so it can be exercised with ordinary JVM tests.
 */
object BackupCodec {
    private val mimeTypePattern = Regex(
        "[A-Za-z0-9][A-Za-z0-9!#\$&^_.+\\-]*/[A-Za-z0-9][A-Za-z0-9!#\$&^_.+\\-]*"
    )

    fun encode(archive: BackupArchive): ByteArray {
        val output = ByteArrayOutputStream()
        write(output, archive)
        return output.toByteArray()
    }

    fun write(output: OutputStream, archive: BackupArchive) {
        val prepared = prepare(archive)
        val zip = ZipOutputStream(LimitedOutputStream(output, BackupCodecLimits.MAX_ARCHIVE_BYTES))
        try {
            writeEntry(zip, BackupFormatV1.MANIFEST_ENTRY, prepared.manifestBytes)
            writeEntry(zip, BackupFormatV1.DATA_ENTRY, prepared.dataBytes)
            writeEntry(zip, BackupFormatV1.PREFERENCES_ENTRY, prepared.preferencesBytes)
            writeEntry(zip, BackupFormatV1.CHECKSUMS_ENTRY, prepared.checksumsBytes)
            prepared.assetEntries
                .sortedBy { it.first }
                .forEach { (path, bytes) -> writeEntry(zip, path, bytes) }
            zip.finish()
        } catch (error: BackupCodecException) {
            throw error
        } catch (error: Exception) {
            throw BackupCodecException("写入 SleepDown 备份失败", error)
        }
    }

    fun decode(bytes: ByteArray): DecodedBackupArchive = decode(ByteArrayInputStream(bytes))

    fun decode(input: InputStream): DecodedBackupArchive {
        val raw = CountingInputStream(input, BackupCodecLimits.MAX_ARCHIVE_BYTES)
        val tail = TailBufferingInputStream(raw)
        val entries = readZipEntries(tail)
        try {
            tail.drain()
        } catch (error: Exception) {
            throw BackupCodecException("读取备份 ZIP 尾部失败", error)
        }
        if (!tail.hasEndOfCentralDirectory()) {
            fail("备份 ZIP 缺少有效的 end-of-central-directory")
        }
        return decodeEntries(entries)
    }

    private data class PreparedArchive(
        val manifestBytes: ByteArray,
        val dataBytes: ByteArray,
        val preferencesBytes: ByteArray,
        val checksumsBytes: ByteArray,
        val assetEntries: List<Pair<String, ByteArray>>
    )

    private fun prepare(archive: BackupArchive): PreparedArchive {
        validateMetadata(archive.metadata)
        val assetsById = validateAssets(archive.assets)
        val dataBytes = encodeJson("data.json", archive.data)
        val preferencesBytes = encodeJson("preferences.json", archive.preferences)
        validateSecretFreePreferences(preferencesBytes)
        validateData(archive.data, assetsById.keys)
        validatePreferences(archive.preferences, assetsById.keys)
        validateStableIdGraph(
            archive.data,
            archive.preferences,
            archive.assets.map { it.assetId },
            archive.assets.map { it.ownerId }
        )

        val assetManifest = archive.assets.map { asset ->
            val path = assetPath(asset.category, asset.assetId)
            val bytes = asset.bytes
            if (bytes == null) {
                if (asset.missingReason.isNullOrBlank()) {
                    fail("缺失 asset 必须提供 missingReason: ${asset.assetId}")
                }
                BackupAssetManifestEntry(
                    assetId = asset.assetId,
                    category = asset.category,
                    purpose = asset.purpose,
                    relativePath = path,
                    mediaType = asset.mediaType,
                    byteLength = 0,
                    sha256 = "",
                    present = false,
                    optional = asset.optional,
                    ownerId = asset.ownerId,
                    missingReason = asset.missingReason
                )
            } else {
                BackupAssetManifestEntry(
                    assetId = asset.assetId,
                    category = asset.category,
                    purpose = asset.purpose,
                    relativePath = path,
                    mediaType = asset.mediaType,
                    byteLength = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    present = true,
                    optional = asset.optional,
                    ownerId = asset.ownerId,
                    missingReason = null
                )
            }
        }
        val manifest = BackupManifest(
            formatVersion = BackupFormatV1.FORMAT_VERSION,
            product = BackupFormatV1.PRODUCT,
            createdAt = archive.metadata.createdAt,
            sourceAppVersionName = archive.metadata.sourceAppVersionName,
            sourceVersionCode = archive.metadata.sourceVersionCode,
            sourcePackageName = archive.metadata.sourcePackageName,
            sourceDatabaseVersion = archive.metadata.sourceDatabaseVersion,
            devicePlatform = archive.metadata.devicePlatform,
            assetCount = assetManifest.count { it.present },
            missingAssetCount = assetManifest.count { !it.present },
            assets = assetManifest
        )
        val manifestBytes = encodeJson(BackupFormatV1.MANIFEST_ENTRY, manifest)
        val assetEntries = archive.assets.mapNotNull { asset ->
            asset.bytes?.let { assetPath(asset.category, asset.assetId) to it }
        }
        val checksumEntries = linkedMapOf(
            BackupFormatV1.MANIFEST_ENTRY to sha256(manifestBytes),
            BackupFormatV1.DATA_ENTRY to sha256(dataBytes),
            BackupFormatV1.PREFERENCES_ENTRY to sha256(preferencesBytes)
        )
        assetEntries.sortedBy { it.first }.forEach { (path, bytes) ->
            checksumEntries[path] = sha256(bytes)
        }
        val checksums = BackupChecksums(
            checksumVersion = BackupFormatV1.CHECKSUM_VERSION,
            algorithm = BackupFormatV1.CHECKSUM_ALGORITHM,
            entries = checksumEntries
        )
        val checksumsBytes = encodeJson(BackupFormatV1.CHECKSUMS_ENTRY, checksums)
        val totalUncompressed = manifestBytes.size.toLong() +
            dataBytes.size.toLong() +
            preferencesBytes.size.toLong() +
            checksumsBytes.size.toLong() +
            assetEntries.sumOf { it.second.size.toLong() }
        if (totalUncompressed > BackupCodecLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
            fail("备份解压后总大小超过限制")
        }
        return PreparedArchive(manifestBytes, dataBytes, preferencesBytes, checksumsBytes, assetEntries)
    }

    private fun decodeEntries(entries: Map<String, ByteArray>): DecodedBackupArchive {
        BackupFormatV1.fixedEntries.forEach { required ->
            if (required !in entries) fail("备份缺少必需 entry: $required")
        }
        entries.keys.forEach { name ->
            if (name !in BackupFormatV1.fixedEntries && !name.startsWith("assets/")) {
                fail("未知备份 entry: $name")
            }
        }

        val manifest = decodeJson<BackupManifest>(BackupFormatV1.MANIFEST_ENTRY, entries.getValue(BackupFormatV1.MANIFEST_ENTRY))
        validateManifest(manifest, entries.keys)
        val dataBytes = entries.getValue(BackupFormatV1.DATA_ENTRY)
        val preferencesBytes = entries.getValue(BackupFormatV1.PREFERENCES_ENTRY)
        val checksums = decodeJson<BackupChecksums>(BackupFormatV1.CHECKSUMS_ENTRY, entries.getValue(BackupFormatV1.CHECKSUMS_ENTRY))
        validateChecksums(manifest, checksums, entries)
        validateSecretFreePreferences(preferencesBytes)
        val data = decodeJson<BackupData>(BackupFormatV1.DATA_ENTRY, dataBytes)
        val preferences = decodeJson<BackupPreferences>(BackupFormatV1.PREFERENCES_ENTRY, preferencesBytes)
        val assetsById = manifest.assets.associateBy { it.assetId }
        validateData(data, assetsById.keys)
        validatePreferences(preferences, assetsById.keys)
        validateStableIdGraph(
            data,
            preferences,
            manifest.assets.map { it.assetId },
            manifest.assets.map { it.ownerId }
        )
        val assets = manifest.assets.map { descriptor ->
            val assetBytes = if (descriptor.present) entries[descriptor.relativePath] else null
            if (descriptor.present && assetBytes!!.size.toLong() != descriptor.byteLength) {
                fail("asset ${descriptor.assetId} 的实际大小与 manifest 不一致")
            }
            BackupAsset(
                assetId = descriptor.assetId,
                category = descriptor.category,
                purpose = descriptor.purpose,
                mediaType = descriptor.mediaType,
                bytes = assetBytes,
                optional = descriptor.optional,
                ownerId = descriptor.ownerId,
                missingReason = descriptor.missingReason
            )
        }
        return DecodedBackupArchive(manifest, data, preferences, checksums, assets)
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        val zip = try {
            ZipInputStream(NonClosingInputStream(input))
        } catch (error: Exception) {
            throw BackupCodecException("无法打开备份 ZIP", error)
        }
        var entryCount = 0
        var totalUncompressed = 0L
        try {
            while (true) {
                val entry = try {
                    zip.nextEntry
                } catch (error: Exception) {
                    throw BackupCodecException("备份 ZIP 目录损坏", error)
                } ?: break
                entryCount += 1
                if (entryCount > BackupCodecLimits.MAX_ENTRY_COUNT) {
                    fail("备份 entry 数量超过限制")
                }
                validateZipEntryName(entry.name)
                if (entry.isDirectory) fail("备份不允许目录 entry: ${entry.name}")
                if (result.containsKey(entry.name)) fail("备份包含重复 entry: ${entry.name}")
                val limit = if (entry.name in BackupFormatV1.fixedEntries) {
                    BackupCodecLimits.MAX_JSON_BYTES
                } else {
                    BackupCodecLimits.MAX_ASSET_BYTES
                }
                val bytes = readEntryBytes(zip, entry, limit, totalUncompressed)
                totalUncompressed += bytes.size.toLong()
                if (totalUncompressed > BackupCodecLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    fail("备份解压后总大小超过限制")
                }
                result[entry.name] = bytes
                zip.closeEntry()
            }
        } catch (error: BackupCodecException) {
            throw error
        } catch (error: Exception) {
            throw BackupCodecException("读取备份 ZIP 失败", error)
        } finally {
            runCatching { zip.close() }
        }
        return result
    }

    private fun readEntryBytes(
        input: ZipInputStream,
        entry: ZipEntry,
        limit: Long,
        previousTotal: Long
    ): ByteArray {
        if (entry.size > limit) fail("entry ${entry.name} 超过大小限制")
        val output = ByteArrayOutputStream(minOf(limit, 64L * 1024L).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (output.size().toLong() + count > limit) fail("entry ${entry.name} 超过大小限制")
            if (previousTotal + output.size().toLong() + count > BackupCodecLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                fail("备份解压后总大小超过限制")
            }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        if (entry.size >= 0 && entry.size != bytes.size.toLong()) {
            fail("entry ${entry.name} 的声明大小不匹配")
        }
        val compressedSize = entry.compressedSize
        if (compressedSize > 0 && bytes.size.toLong() > compressedSize * BackupCodecLimits.MAX_COMPRESSION_RATIO) {
            fail("entry ${entry.name} 的压缩比超过限制")
        }
        return bytes
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        validateZipEntryName(name)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private inline fun <reified T> encodeJson(name: String, value: T): ByteArray {
        return try {
            BackupJson.encodeToString(value).toByteArray(StandardCharsets.UTF_8).also {
                if (it.size > BackupCodecLimits.MAX_JSON_BYTES) fail("$name 超过 JSON 大小限制")
            }
        } catch (error: BackupCodecException) {
            throw error
        } catch (error: Exception) {
            throw BackupCodecException("编码 $name 失败", error)
        }
    }

    private inline fun <reified T> decodeJson(name: String, bytes: ByteArray): T {
        if (bytes.size.toLong() > BackupCodecLimits.MAX_JSON_BYTES) fail("$name 超过 JSON 大小限制")
        return try {
            BackupJson.decodeFromString<T>(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: BackupCodecException) {
            throw error
        } catch (error: SerializationException) {
            throw BackupCodecException("$name JSON 损坏", error)
        } catch (error: Exception) {
            throw BackupCodecException("$name JSON 无法解析", error)
        }
    }

    private fun validateMetadata(metadata: BackupSourceMetadata) {
        validateShortText("createdAt", metadata.createdAt, allowBlank = false)
        validateShortText("sourceAppVersionName", metadata.sourceAppVersionName, allowBlank = false)
        validateShortText("sourcePackageName", metadata.sourcePackageName, allowBlank = false)
        validateShortText("devicePlatform", metadata.devicePlatform, allowBlank = false)
        if (metadata.sourceVersionCode < 0 || metadata.sourceDatabaseVersion < 0) {
            fail("版本号不能为负数")
        }
    }

    private fun validateAssets(assets: List<BackupAsset>): Map<String, BackupAsset> {
        if (assets.size > BackupCodecLimits.MAX_ASSET_COUNT) fail("asset 数量超过限制")
        val byId = LinkedHashMap<String, BackupAsset>()
        assets.forEach { asset ->
            BackupStableId.requireValid(asset.assetId, BackupStableId.ASSET_PREFIX)
            if (asset.category !in BackupAssetCategory.all) fail("未知 asset category: ${asset.category}")
            if (asset.purpose !in BackupAssetPurpose.all) fail("未知 asset purpose: ${asset.purpose}")
            validateShortText("asset purpose", asset.purpose, allowBlank = false)
            validateShortText("asset mediaType", asset.mediaType, allowBlank = false)
            validateMimeType(asset.mediaType)
            if (asset.bytes != null) {
                if (asset.bytes.isEmpty()) fail("present asset 不能是空文件: ${asset.assetId}")
                if (asset.bytes.size.toLong() > BackupCodecLimits.MAX_ASSET_BYTES) {
                    fail("asset 超过大小限制: ${asset.assetId}")
                }
                if (!asset.missingReason.isNullOrBlank()) fail("present asset 不应有 missingReason")
            } else if (asset.missingReason.isNullOrBlank()) {
                fail("缺失 asset 必须提供 missingReason: ${asset.assetId}")
            }
            asset.missingReason?.let { validateText("asset missingReason", it) }
            asset.ownerId?.let { BackupStableId.requireValid(it) }
            if (byId.put(asset.assetId, asset) != null) fail("重复 asset ID: ${asset.assetId}")
        }
        return byId
    }

    private fun validateManifest(manifest: BackupManifest, entryNames: Set<String>) {
        if (manifest.formatVersion != BackupFormatV1.FORMAT_VERSION) {
            fail("无法读取 formatVersion=${manifest.formatVersion} 的备份")
        }
        if (manifest.product != BackupFormatV1.PRODUCT) fail("不是 SleepDown Backup 文件")
        validateShortText("createdAt", manifest.createdAt, allowBlank = false)
        validateShortText("sourceAppVersionName", manifest.sourceAppVersionName, allowBlank = false)
        validateShortText("sourcePackageName", manifest.sourcePackageName, allowBlank = false)
        validateShortText("devicePlatform", manifest.devicePlatform, allowBlank = false)
        if (manifest.sourceVersionCode < 0 || manifest.sourceDatabaseVersion < 0) fail("manifest 版本号非法")
        if (manifest.assets.size > BackupCodecLimits.MAX_ASSET_COUNT) fail("manifest asset 数量超过限制")
        if (manifest.assetCount < 0 || manifest.missingAssetCount < 0) fail("manifest asset 计数非法")
        val presentCount = manifest.assets.count { it.present }
        val missingCount = manifest.assets.count { !it.present }
        if (manifest.assetCount != presentCount || manifest.missingAssetCount != missingCount) {
            fail("manifest asset 计数不一致")
        }
        val ids = HashSet<String>()
        val paths = HashSet<String>()
        manifest.assets.forEach { descriptor ->
            BackupStableId.requireValid(descriptor.assetId, BackupStableId.ASSET_PREFIX)
            if (!ids.add(descriptor.assetId)) fail("manifest 包含重复 asset ID")
            if (descriptor.category !in BackupAssetCategory.all) fail("未知 manifest asset category")
            if (descriptor.purpose !in BackupAssetPurpose.all) fail("未知 manifest asset purpose")
            validateShortText("asset purpose", descriptor.purpose, allowBlank = false)
            validateShortText("asset mediaType", descriptor.mediaType, allowBlank = false)
            validateMimeType(descriptor.mediaType)
            val expectedPath = assetPath(descriptor.category, descriptor.assetId)
            if (descriptor.relativePath != expectedPath) fail("asset path 不符合 manifest 规则")
            if (!paths.add(descriptor.relativePath)) fail("manifest 包含重复 asset path")
            if (descriptor.present) {
                if (descriptor.byteLength <= 0 || descriptor.byteLength > BackupCodecLimits.MAX_ASSET_BYTES) {
                    fail("manifest asset 大小非法")
                }
                if (!descriptor.sha256.matches(sha256Pattern)) fail("manifest asset checksum 非法")
                if (!entryNames.contains(descriptor.relativePath)) fail("manifest 声明的 asset 不在 ZIP 中")
                if (descriptor.missingReason != null) fail("present asset 不应有 missingReason")
            } else {
                if (descriptor.byteLength != 0L || descriptor.sha256.isNotEmpty()) fail("missing asset 不应有 bytes/checksum")
                if (descriptor.missingReason.isNullOrBlank()) fail("missing asset 缺少 missingReason")
                validateText("asset missingReason", descriptor.missingReason)
                if (entryNames.contains(descriptor.relativePath)) fail("missing asset 不应出现在 ZIP 中")
            }
            descriptor.ownerId?.let { BackupStableId.requireValid(it) }
        }
        val declaredPaths = manifest.assets.filter { it.present }.mapTo(HashSet()) { it.relativePath }
        val actualAssetPaths = entryNames.filter { it.startsWith("assets/") }.toSet()
        if (actualAssetPaths != declaredPaths) fail("ZIP asset entries 与 manifest 不一致")
    }

    private fun validateChecksums(
        manifest: BackupManifest,
        checksums: BackupChecksums,
        entries: Map<String, ByteArray>
    ) {
        if (checksums.checksumVersion != BackupFormatV1.CHECKSUM_VERSION) fail("未知 checksum 版本")
        if (checksums.algorithm != BackupFormatV1.CHECKSUM_ALGORITHM) fail("不支持的 checksum 算法")
        val expected = linkedSetOf(
            BackupFormatV1.MANIFEST_ENTRY,
            BackupFormatV1.DATA_ENTRY,
            BackupFormatV1.PREFERENCES_ENTRY
        ).apply {
            manifest.assets.filter { it.present }.forEach { add(it.relativePath) }
        }
        if (checksums.entries.keys != expected) fail("checksums 覆盖范围不完整或包含未知 entry")
        checksums.entries.forEach { (name, digest) ->
            if (!digest.matches(sha256Pattern)) fail("checksum 格式非法: $name")
            val bytes = entries[name] ?: fail("checksum 对应的 entry 不存在: $name")
            if (sha256(bytes) != digest) fail("checksum mismatch: $name")
        }
    }

    private fun validateData(data: BackupData, assetIds: Set<String>) {
        if (data.dataVersion != BackupFormatV1.DATA_VERSION) fail("未知 dataVersion=${data.dataVersion}")
        if (data.schedules.size > BackupCodecLimits.MAX_ENTRY_COUNT) fail("schedule 数量超过限制")
        val scheduleIds = HashSet<String>()
        var activeCount = 0
        data.schedules.forEach { schedule ->
            BackupStableId.requireValid(schedule.id, BackupStableId.SCHEDULE_PREFIX)
            if (!scheduleIds.add(schedule.id)) fail("重复 schedule stable ID")
            validateText("schedule name", schedule.name)
            if (schedule.isActive) activeCount += 1
            validateConfig(schedule.config, assetIds)
            val periodIndexes = HashSet<Int>()
            schedule.periods.forEach { period ->
                if (period.periodIndex < 0) fail("periodIndex 非法")
                if (!periodIndexes.add(period.periodIndex)) fail("重复 periodIndex")
                validateShortText("period startTime", period.startTime, allowBlank = false)
                validateShortText("period endTime", period.endTime, allowBlank = false)
            }
            validateUniqueIds(schedule.courses.map { it.id }, BackupStableId.COURSE_PREFIX, "course")
            schedule.courses.forEach { course ->
                validateText("course name", course.name)
                course.teacher?.let { validateText("course teacher", it) }
                course.location?.let { validateText("course location", it) }
                course.note?.let { validateText("course note", it) }
                course.customStartTime?.let { validateShortText("course customStartTime", it, allowBlank = false) }
                course.customEndTime?.let { validateShortText("course customEndTime", it, allowBlank = false) }
                if ((course.customStartTime == null) != (course.customEndTime == null)) {
                    fail("课程自定义起止时间必须同时存在")
                }
                if (course.customStartTime != null && course.customEndTime != null) {
                    val start = runCatching { java.time.LocalTime.parse(course.customStartTime) }.getOrNull()
                        ?: fail("课程自定义开始时间非法")
                    val end = runCatching { java.time.LocalTime.parse(course.customEndTime) }.getOrNull()
                        ?: fail("课程自定义结束时间非法")
                    if (!end.isAfter(start)) fail("课程自定义结束时间必须晚于开始时间")
                }
                if (course.weekday !in 1..7) fail("课程 weekday 非法")
                if (course.periods.any { it < 0 } || course.weeks.any { it < 0 }) fail("课程 periods/weeks 非法")
                if (course.periods.any { it !in periodIndexes }) fail("课程引用了不存在的 periodIndex")
                validateShortText("course weekParity", course.weekParity, allowBlank = false)
            }
            validateUniqueIds(schedule.periodSchemes.map { it.id }, BackupStableId.SCHEME_PREFIX, "period scheme")
            if (schedule.periodSchemes.count { it.isActive } > 1) fail("一个 schedule 不能有多个 active period scheme")
            schedule.periodSchemes.forEach { scheme ->
                validateText("scheme name", scheme.name)
                validateShortText("scheme mode", scheme.mode, allowBlank = false)
                if (scheme.classDurationMinutes < 0 || scheme.breakDurationMinutes < 0) {
                    fail("scheme 时长参数非法")
                }
                validateShortText("scheme morningStartTime", scheme.morningStartTime, allowBlank = false)
                validateShortText("scheme noonStartTime", scheme.noonStartTime, allowBlank = false)
                validateShortText("scheme afternoonStartTime", scheme.afternoonStartTime, allowBlank = false)
                validateShortText("scheme eveningStartTime", scheme.eveningStartTime, allowBlank = false)
                validateText("scheme specialBreaksJson", scheme.specialBreaksJson)
                validateText("scheme overridesJson", scheme.overridesJson)
                val schemePeriodIndexes = HashSet<Int>()
                scheme.times.forEach { time ->
                    if (time.periodIndex < 0) fail("scheme time periodIndex 非法")
                    if (!schemePeriodIndexes.add(time.periodIndex)) fail("重复 scheme time periodIndex")
                    if (time.periodIndex !in periodIndexes) fail("scheme time 引用了不存在的 periodIndex")
                    validateShortText("scheme time start", time.startTime, allowBlank = false)
                    validateShortText("scheme time end", time.endTime, allowBlank = false)
                }
            }
            validateUniqueIds(schedule.agentDailySessions.map { it.id }, BackupStableId.SESSION_PREFIX, "agent session")
            validateUniqueIds(schedule.agentMessages.map { it.id }, BackupStableId.MESSAGE_PREFIX, "agent message")
            schedule.agentDailySessions.forEach { session ->
                validateShortText("agent session date", session.date, allowBlank = false)
                validateText("agent dailyPackJson", session.dailyPackJson)
                validateShortText("agent providerId", session.providerId)
                validateShortText("agent model", session.model)
                validateShortText("agent generationStatus", session.generationStatus, allowBlank = false)
                session.lastError?.let { validateText("agent lastError", it) }
            }
            val sessionIds = schedule.agentDailySessions.mapTo(HashSet()) { it.id }
            schedule.agentMessages.forEach { message ->
                message.sessionId?.let {
                    BackupStableId.requireValid(it, BackupStableId.SESSION_PREFIX)
                    if (it !in sessionIds) fail("Agent message 引用了不存在的 session")
                }
                validateShortText("agent sessionDate", message.sessionDate, allowBlank = false)
                validateShortText("agent role", message.role, allowBlank = false)
                validateText("agent content", message.content)
                validateShortText("agent status", message.status, allowBlank = false)
                message.attachmentAssetIds.forEach { assetId ->
                    BackupStableId.requireValid(assetId, BackupStableId.ASSET_PREFIX)
                    if (assetId !in assetIds) fail("Agent message 引用了未声明的 asset")
                }
            }
        }
        if (activeCount > 1) fail("备份不能包含多个 active schedule")
        validateUniqueIds(data.widgetAppearances.map { it.id }, BackupStableId.WIDGET_PREFIX, "widget appearance")
        data.widgetAppearances.forEach { appearance ->
            validateShortText("widget variant", appearance.variant, allowBlank = false)
            if (appearance.scope != "default" && appearance.scope != "instance") fail("widget scope 非法")
            appearance.wallpaperAssetId?.let { assetId ->
                BackupStableId.requireValid(assetId, BackupStableId.ASSET_PREFIX)
                if (assetId !in assetIds) fail("widget 引用了未声明的 asset")
            }
        }
    }

    private fun validatePreferences(preferences: BackupPreferences, assetIds: Set<String>) {
        if (preferences.preferencesVersion != BackupFormatV1.PREFERENCES_VERSION) {
            fail("未知 preferencesVersion=${preferences.preferencesVersion}")
        }
        if (preferences.aiImportHistoryRetentionDays !in BackupFormatV1.AI_IMPORT_HISTORY_RETENTION_OPTIONS) {
            fail("AI import history retentionDays 非法")
        }
        preferences.appIcon?.let { appIcon ->
            validateShortText("app icon mode", appIcon.mode, allowBlank = false)
        }
        preferences.dayAgent?.let { dayAgent ->
            validateText("day agent memory", dayAgent.memory)
            dayAgent.memoryTurnDay?.let { validateShortText("day agent memoryTurnDay", it) }
            dayAgent.memoryLastAgentUpdateDay?.let { validateShortText("day agent memoryLastAgentUpdateDay", it) }
            dayAgent.appliedActionsBySchedule.forEach { (scheduleId, actions) ->
                BackupStableId.requireValid(scheduleId, BackupStableId.SCHEDULE_PREFIX)
                actions.forEach { validateShortText("day agent action", it) }
            }
        }
        preferences.dayAgent?.appliedActionsBySchedule?.keys?.forEach { scheduleId ->
            BackupStableId.requireValid(scheduleId, BackupStableId.SCHEDULE_PREFIX)
        }
        preferences.aiImport?.let { ai ->
            validateShortText("selectedProviderId", ai.selectedProviderId)
            val providerIds = HashSet<String>()
            ai.providers.forEach { provider ->
                validateShortText("AI provider id", provider.id, allowBlank = false)
                if (!providerIds.add(provider.id)) fail("重复 AI provider ID")
                validateText("AI provider displayName", provider.displayName)
                validateShortText("AI provider providerType", provider.providerType, allowBlank = false)
                validateText("AI provider baseUrl", provider.baseUrl)
                validateShortText("AI provider model", provider.model)
                validateShortText("AI provider authType", provider.authType, allowBlank = false)
                validateShortText("AI provider endpointStyle", provider.endpointStyle, allowBlank = false)
                validateShortText("AI provider structuredOutputMode", provider.structuredOutputMode, allowBlank = false)
                validateShortText("AI provider inputMode", provider.inputMode, allowBlank = false)
                provider.availableModels.forEach { validateShortText("AI provider available model", it) }
                validateShortText("AI provider reasoningEffort", provider.reasoningEffort)
            }
        }
        val historyIds = HashSet<String>()
        preferences.aiImportHistory.forEach { history ->
            BackupStableId.requireValid(history.id, BackupStableId.HISTORY_PREFIX)
            if (!historyIds.add(history.id)) fail("重复 AI history stable ID")
            validateText("AI history title", history.title)
            validateText("AI history prompt", history.prompt)
            validateText("AI history sourceSummary", history.sourceSummary)
            validateText("AI history payload", history.payload)
            history.contextAssetId?.let { assetId ->
                BackupStableId.requireValid(assetId, BackupStableId.ASSET_PREFIX)
                if (assetId !in assetIds) fail("AI history 引用了未声明的 context asset")
            }
        }
    }

    private fun validateConfig(config: BackupScheduleConfig, assetIds: Set<String>) {
        if (config.totalWeeks < 0 || config.currentWeek < 0 || config.notificationLeadMinutes < 0) {
            fail("课表周次/提醒参数非法")
        }
        config.wallpaperAssetId?.let { assetId ->
            BackupStableId.requireValid(assetId, BackupStableId.ASSET_PREFIX)
            if (assetId !in assetIds) fail("ScheduleConfig 引用了未声明的 wallpaper asset")
        }
        validateShortText("termState", config.termState, allowBlank = false)
        validateShortText("notificationMode", config.notificationMode, allowBlank = false)
        validateShortText("defaultWallpaperStyle", config.defaultWallpaperStyle, allowBlank = false)
        validateShortText("dockAlignment", config.dockAlignment, allowBlank = false)
        validateShortText("defaultHomeMode", config.defaultHomeMode, allowBlank = false)
        validateShortText("liveUpdateChipTextMode", config.liveUpdateChipTextMode, allowBlank = false)
        validateShortText("courseCardColorMode", config.courseCardColorMode, allowBlank = false)
        validateShortText(
            "alternateCourseCardColorMode",
            config.alternateCourseCardColorMode,
            allowBlank = false
        )
        if (config.courseCardPalette.length > 512 || config.alternateCourseCardPalette.length > 512) {
            fail("课程卡片调色板数据过长")
        }
        if (!config.weekCardHeightScale.isFinite() || config.weekCardHeightScale !in 0.72f..1.45f) {
            fail("周视图行高比例非法")
        }
        if (!config.weekCardCornerProgress.isFinite() || config.weekCardCornerProgress !in 0f..1f) {
            fail("周视图卡片圆角非法")
        }
        if (
            !config.homeChromeBlurScale.isFinite() ||
            config.homeChromeBlurScale !in MinHomeChromeBlurScale..MaxHomeChromeBlurScale
        ) {
            fail("首页玻璃模糊倍率非法")
        }
        if (!config.homeChromeSamplingScale.isFinite() || config.homeChromeSamplingScale !in 0.5f..1f) {
            fail("首页玻璃采样分辨率非法")
        }
    }

    private fun validateStableIdGraph(
        data: BackupData,
        preferences: BackupPreferences,
        assetIds: Iterable<String>,
        assetOwnerIds: Iterable<String?>
    ) {
        val ids = HashSet<String>()

        fun add(id: String, prefix: String, label: String) {
            BackupStableId.requireValid(id, prefix)
            if (!ids.add(id)) fail("重复 $label stable ID: $id")
        }

        data.schedules.forEach { schedule ->
            add(schedule.id, BackupStableId.SCHEDULE_PREFIX, "schedule")
            schedule.courses.forEach { add(it.id, BackupStableId.COURSE_PREFIX, "course") }
            schedule.periodSchemes.forEach { add(it.id, BackupStableId.SCHEME_PREFIX, "period scheme") }
            schedule.agentDailySessions.forEach { add(it.id, BackupStableId.SESSION_PREFIX, "agent session") }
            schedule.agentMessages.forEach { add(it.id, BackupStableId.MESSAGE_PREFIX, "agent message") }
        }
        data.widgetAppearances.forEach { add(it.id, BackupStableId.WIDGET_PREFIX, "widget appearance") }
        preferences.aiImportHistory.forEach { add(it.id, BackupStableId.HISTORY_PREFIX, "AI history") }
        assetIds.forEach { add(it, BackupStableId.ASSET_PREFIX, "asset") }
        assetOwnerIds.forEach { ownerId ->
            if (ownerId != null && ownerId !in ids) {
                fail("asset owner 引用了不存在的 stable ID: $ownerId")
            }
        }
    }

    private fun validateUniqueIds(values: List<String>, prefix: String, label: String) {
        val ids = HashSet<String>()
        values.forEach { value ->
            BackupStableId.requireValid(value, prefix)
            if (!ids.add(value)) fail("重复 $label stable ID")
        }
    }

    private fun validateSecretFreePreferences(bytes: ByteArray) {
        val root = decodeJson<JsonElement>(BackupFormatV1.PREFERENCES_ENTRY, bytes)
        assertNoSecretKeys(root, "preferences")
    }

    private fun assertNoSecretKeys(element: JsonElement, path: String) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (forbiddenKeyPattern.containsMatchIn(key)) fail("preferences 包含禁止的 secret 字段: $path.$key")
                assertNoSecretKeys(value, "$path.$key")
            }
            else -> Unit
        }
    }

    private fun validateText(name: String, value: String, allowBlank: Boolean = true) {
        if (!allowBlank && value.isBlank()) fail("$name 不能为空")
        if (value.any { it == '\u0000' || it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
            fail("$name 包含非法控制字符")
        }
    }

    private fun validateShortText(name: String, value: String, allowBlank: Boolean = true) {
        validateText(name, value, allowBlank)
        if (value.toByteArray(StandardCharsets.UTF_8).size > BackupCodecLimits.MAX_FIELD_LENGTH) {
            fail("$name 过长")
        }
    }

    private fun validateMimeType(value: String) {
        if (!mimeTypePattern.matches(value)) fail("asset mediaType 非法: $value")
    }

    private fun validateZipEntryName(name: String) {
        if (name.isBlank() || name.toByteArray(StandardCharsets.UTF_8).size > BackupCodecLimits.MAX_FIELD_LENGTH) {
            fail("ZIP entry 名称非法")
        }
        if (name.contains('\\') || name.contains('\u0000') || name.startsWith('/') || name.contains(':')) {
            fail("ZIP entry 路径不安全: $name")
        }
        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("ZIP entry 路径包含 traversal: $name")
        }
        if (name.endsWith('/')) fail("ZIP 目录 entry 不允许: $name")
    }

    private fun assetPath(category: String, assetId: String): String {
        if (category !in BackupAssetCategory.all) fail("未知 asset category: $category")
        BackupStableId.requireValid(assetId, BackupStableId.ASSET_PREFIX)
        return "assets/$category/$assetId"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun fail(message: String): Nothing = throw BackupCodecException(message)

    private class CountingInputStream(
        input: InputStream,
        private val limit: Long
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) increment(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val value = super.read(buffer, offset, length)
            if (value > 0) increment(value.toLong())
            return value
        }

        private fun increment(value: Long) {
            if (value < 0 || count > limit - value) fail("备份原始输入超过大小限制")
            count += value
        }
    }

    private class TailBufferingInputStream(
        input: InputStream,
        private val capacity: Int = 65_557
    ) : FilterInputStream(input) {
        private val buffer = ByteArray(capacity)
        private var start = 0
        private var size = 0

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(value.toByte())
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val value = super.read(buffer, offset, length)
            if (value > 0) record(buffer, offset, value)
            return value
        }

        fun drain() {
            val scratch = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = read(scratch)
                if (count < 0) return
                if (count == 0) {
                    if (read() < 0) return
                }
            }
        }

        fun hasEndOfCentralDirectory(): Boolean {
            val snapshot = snapshot()
            if (snapshot.size < 22) return false
            for (offset in 0..snapshot.size - 22) {
                if (snapshot[offset].toInt() and 0xff != 0x50 ||
                    snapshot[offset + 1].toInt() and 0xff != 0x4b ||
                    snapshot[offset + 2].toInt() and 0xff != 0x05 ||
                    snapshot[offset + 3].toInt() and 0xff != 0x06
                ) continue
                val commentLength = (snapshot[offset + 20].toInt() and 0xff) or
                    ((snapshot[offset + 21].toInt() and 0xff) shl 8)
                if (offset + 22 + commentLength == snapshot.size) return true
            }
            return false
        }

        private fun record(value: Byte) {
            if (size < capacity) {
                buffer[(start + size) % capacity] = value
                size += 1
            } else {
                buffer[start] = value
                start = (start + 1) % capacity
            }
        }

        private fun record(source: ByteArray, offset: Int, length: Int) {
            for (index in offset until offset + length) record(source[index])
        }

        private fun snapshot(): ByteArray {
            return ByteArray(size) { index -> buffer[(start + index) % capacity] }
        }
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class LimitedOutputStream(
        output: OutputStream,
        private val limit: Long
    ) : FilterOutputStream(output) {
        private var count = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            out.write(value)
            count += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length.toLong())
            out.write(buffer, offset, length)
            count += length.toLong()
        }

        private fun ensureCapacity(incoming: Long) {
            if (incoming < 0 || count > limit - incoming) {
                fail("备份 ZIP 原始输出超过大小限制")
            }
        }
    }

    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val forbiddenKeyPattern = Regex(
        "(?i)(api.?key|access.?token|refresh.?token|password|cookie|secret|keystore|encryption.?key|signing.?key|authorization|credential)"
    )
}
