package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SharedPeriodSchemeBackupTest {
    @Test fun globalRoundTripPreservesSharingAndUnreferencedSchemes() {
        val archive = decode(export())
        val shared = requireNotNull(archive.data.sharedPeriodSchemes)
        assertEquals(2, shared.size)
        assertEquals(1, archive.data.schedules.map { it.activePeriodSchemeId }.distinct().size)
        assertEquals(2, archive.preferences.savedPeriodSchemes!!.size)
        assertTrue(archive.data.schedules.all { it.periodSchemes.single().isActive })
        val plan = BackupImportPlanBuilder.build(archive, "shared-roundtrip", BackupImportTargetSnapshot(schemeIds = setOf(1, 2)))
        val rows = BackupRoomRestoreMapper.map(archive, plan)
        assertEquals(2, rows.periodSchemes.size)
        assertEquals(1, rows.configs.map { it.activePeriodSchemeId }.distinct().size)
        assertTrue(rows.periodSchemes.all { it.id !in setOf(1L, 2L) })
        assertEquals(setOf("shared", "unused"), rows.periodSchemes.map { it.publicId }.toSet())
        assertEquals("08:00", rows.periods.first().startTime)
    }

    @Test fun legacyArchiveImportsLocalSchemesAndPreferenceLibraryTogether() {
        val modern = export()
        val legacy = modern.copy(data = modern.data.copy(sharedPeriodSchemes = null,
            schedules = modern.data.schedules.map { it.copy(activePeriodSchemeId = null) }))
        val archive = decode(legacy)
        val rows = BackupRoomRestoreMapper.map(archive, BackupImportPlanBuilder.build(archive, "legacy-library"))
        assertEquals(4, rows.periodSchemes.size)
        assertNotEquals(rows.configs[0].activePeriodSchemeId, rows.configs[1].activePeriodSchemeId)
        assertTrue(rows.periodSchemes.any { it.publicId == "unused" })
        val merge = mergeBackupSchemeRows(emptyList(), emptyList(), rows.periodSchemes, rows.periodSchemeTimes, "legacy-library")
        assertEquals(2, merge.additions.size)
        assertEquals(merge.ids[rows.configs[0].activePeriodSchemeId], merge.ids[rows.configs[1].activePeriodSchemeId])
    }

    @Test fun periodsOnlyOldBackupGetsItsOwnBoundScheme() {
        val modern = export()
        val legacy = modern.copy(data = modern.data.copy(sharedPeriodSchemes = null,
            schedules = modern.data.schedules.take(1).map { it.copy(activePeriodSchemeId = null, periodSchemes = emptyList()) }),
            preferences = modern.preferences.copy(savedPeriodSchemes = null))
        val archive = decode(legacy)
        val rows = BackupRoomRestoreMapper.map(archive, BackupImportPlanBuilder.build(archive, "periods-only"))
        assertEquals(rows.periodSchemes.single().id, rows.configs.single().activePeriodSchemeId)
        assertEquals("08:00", rows.periodSchemeTimes.single().startTime)
    }

    @Test fun legacyVisibleTimelineAndDifferentSavedTimelineAreBothPreserved() {
        val modern = export()
        val schedule = modern.data.schedules.first()
        val legacy = modern.copy(data = modern.data.copy(sharedPeriodSchemes = null,
            schedules = listOf(schedule.copy(activePeriodSchemeId = null,
                periodSchemes = listOf(schedule.periodSchemes.single().copy(
                    times = listOf(BackupPeriodSchemeTime(1, "09:00", "09:45"))))))),
            preferences = modern.preferences.copy(savedPeriodSchemes = null))
        val archive = decode(legacy)
        val rows = BackupRoomRestoreMapper.map(archive, BackupImportPlanBuilder.build(archive, "legacy-diverged"))
        assertEquals(2, rows.periodSchemes.size)
        assertEquals(setOf("08:00", "09:00"), rows.periodSchemeTimes.map { it.startTime }.toSet())
        assertEquals("08:00", rows.periods.single().startTime)
        assertEquals("08:00", rows.periodSchemeTimes.single {
            it.schemeId == rows.configs.single().activePeriodSchemeId
        }.startTime)
    }

    @Test fun newArchiveNeverResurrectsCompatibilityPreferenceCopy() {
        val modern = export()
        val emptyLibrary = modern.copy(data = modern.data.copy(schedules = emptyList(), sharedPeriodSchemes = emptyList()))
        val archive = decode(emptyLibrary)
        val rows = BackupRoomRestoreMapper.map(archive, BackupImportPlanBuilder.build(archive, "empty-library"))
        assertTrue(rows.periodSchemes.isEmpty())
    }

    @Test fun olderJournalPlanGainsMissingMappingsWithoutRenumberingItsRows() {
        val modern = export()
        val legacy = decode(modern.copy(data = modern.data.copy(sharedPeriodSchemes = null,
            schedules = modern.data.schedules.map { it.copy(activePeriodSchemeId = null) })))
        val complete = BackupImportPlanBuilder.build(legacy, "old-journal")
        val oldIds = legacy.data.schedules.flatMap { it.periodSchemes }.map { it.id }.toSet()
        val oldPlan = complete.copy(schemeIds = complete.schemeIds.filterKeys { it in oldIds })
        val rows = BackupRoomRestoreMapper.map(legacy, oldPlan)
        assertEquals(4, rows.periodSchemes.size)
        oldPlan.schemeIds.forEach { (stable, room) ->
            val owner = legacy.data.schedules.single { schedule -> schedule.periodSchemes.any { it.id == stable } }
            assertEquals(room, rows.configs.single { it.id == oldPlan.scheduleIds[owner.id] }.activePeriodSchemeId)
        }
        assertEquals(rows, BackupRoomRestoreMapper.map(legacy, oldPlan))
    }

    @Test fun oldJournalDefaultsToPendingSharedGraphAndNewFlagRoundTrips() {
        val archive = decode(export())
        val marker = BackupRestoreMarker("journal-version", "fingerprint", BackupRestoreState.DB_COMMITTED,
            BackupImportPlanBuilder.build(archive, "journal-version"), sharedSchemeGraphCommitted = true)
        val serialized = BackupRestoreJson.encodeToString(marker)
        assertTrue(BackupRestoreJson.decodeFromString<BackupRestoreMarker>(serialized).sharedSchemeGraphCommitted)
        val old = JsonObject(BackupRestoreJson.parseToJsonElement(serialized).jsonObject
            .filterKeys { it != "sharedSchemeGraphCommitted" }).toString()
        assertFalse(BackupRestoreJson.decodeFromString<BackupRestoreMarker>(old).sharedSchemeGraphCommitted)
    }

    @Test fun collisionsKeepExistingDataAndDeterministicallyForkImportedIdentity() {
        val current = scheme(1, "same-id")
        val incoming = current.copy(name = "Imported", classDurationMinutes = 50)
        val times = times(1)
        val merge = mergeBackupSchemeRows(listOf(current), times, listOf(incoming), times, "collision", false)
        val added = merge.additions.single()
        assertNotEquals(current.id, added.id)
        assertNotEquals(current.publicId, added.publicId)
        assertEquals(45, current.classDurationMinutes)
        assertEquals(50, added.classDurationMinutes)
        assertEquals(merge, mergeBackupSchemeRows(listOf(current), times, listOf(incoming), times, "collision", false))
        val replay = mergeBackupSchemeRows(listOf(current, added), times + merge.times, listOf(incoming), times, "collision", false)
        assertTrue(replay.additions.isEmpty())
        assertEquals(added.id, replay.ids[incoming.id])
    }

    @Test fun authoritativeSharedArchivePreservesIntentionalEqualCopies() {
        val source = export()
        val globals = requireNotNull(source.data.sharedPeriodSchemes)
        val equalCopy = globals[0].copy(id = globals[1].id, publicId = globals[1].publicId, name = "Explicit copy")
        val archive = decode(source.copy(data = source.data.copy(sharedPeriodSchemes = listOf(globals[0], equalCopy))))
        val rows = BackupRoomRestoreMapper.map(archive, BackupImportPlanBuilder.build(archive, "equal-copies"))
        assertFalse(rows.deduplicateLegacySchemes)
        val merge = mergeBackupSchemeRows(emptyList(), emptyList(), rows.periodSchemes,
            rows.periodSchemeTimes, rows.restoreOperationId, rows.deduplicateLegacySchemes)
        assertEquals(2, merge.additions.size)
        assertNotEquals(merge.additions[0].publicId, merge.additions[1].publicId)
        val replay = mergeBackupSchemeRows(merge.additions, merge.times, rows.periodSchemes,
            rows.periodSchemeTimes, rows.restoreOperationId, rows.deduplicateLegacySchemes)
        assertTrue(replay.additions.isEmpty())
        assertEquals(2, replay.ids.values.distinct().size)
    }

    @Test fun fullContentEqualityReusesExistingWithoutChangingItsTitleOrIdentity() {
        val current = scheme(7, "local")
        val incoming = current.copy(id = 30, publicId = "import", name = "Different title", sourceScheduleName = "Old school")
        val merge = mergeBackupSchemeRows(listOf(current), times(7), listOf(incoming), times(30), "equal")
        assertTrue(merge.additions.isEmpty())
        assertEquals(7L, merge.ids[30])
    }

    @Test fun equalityIncludesModeCountsGenerationInputsAndOverrides() {
        val current = scheme(7, "local")
        val variants = listOf(
            current.copy(mode = PeriodSchemeMode.AUTO_MATCH),
            current.copy(morningPeriodCount = 0, noonPeriodCount = 1),
            current.copy(breakDurationMinutes = 12),
            current.copy(noonStartTime = "12:30"),
            current.copy(specialBreaksJson = "{\"1\":15}"),
            current.copy(overridesJson = "[1]")
        )
        variants.forEach { variant ->
            assertFalse(sameBackupSchemeContent(current, times(7), variant, times(7)))
        }
    }

    @Test fun codecRejectsDanglingSharedReference() {
        val valid = export()
        val invalid = valid.copy(data = valid.data.copy(schedules = valid.data.schedules.map {
            it.copy(activePeriodSchemeId = BackupStableId.new(BackupStableId.SCHEME_PREFIX))
        }))
        try { BackupCodec.encode(invalid); fail("Dangling shared reference must fail") }
        catch (expected: BackupCodecException) { assertTrue(expected.message!!.isNotEmpty()) }
    }

    @Test fun codecRejectsCourseOutsideItsEffectiveSharedTimeline() {
        val valid = export()
        val schedule = valid.data.schedules.first()
        val invalid = valid.copy(data = valid.data.copy(schedules = listOf(schedule.copy(
            periods = schedule.periods + BackupPeriod(2, "09:00", "09:45"),
            courses = listOf(BackupCourse(BackupStableId.new(BackupStableId.COURSE_PREFIX),
                "Dangling course", null, null, 1, listOf(2), listOf(1), "ALL", null))
        ))))
        try { BackupCodec.encode(invalid); fail("Course outside selected shared timeline must fail") }
        catch (expected: BackupCodecException) { assertTrue(expected.message!!.contains("所选作息")) }
    }

    @Test fun codecRejectsDivergentProjectionInAuthoritativeSharedArchive() {
        val valid = export()
        val invalid = valid.copy(data = valid.data.copy(schedules = valid.data.schedules.map { schedule ->
            schedule.copy(periods = listOf(BackupPeriod(1, "09:00", "09:45")))
        }))
        try { BackupCodec.encode(invalid); fail("Divergent compatibility projection must fail") }
        catch (expected: BackupCodecException) { assertTrue(expected.message!!.contains("共享作息不一致")) }
    }

    @Test fun clippedSourceAndAlignmentModeRoundTripAndCanRecoverAllLessons() {
        val source = "11,10:00-10:40;12,10:50-11:30;13,11:40-12:20;14,12:30-13:10"
        val archive = export()
        val original = archive.data.schedules.first()
        val dto = BackupCourse(BackupStableId.new(BackupStableId.COURSE_PREFIX), "隐藏课程", null, null,
            1, (11..14).toList(), listOf(1), "ALL", null, originalPeriodTimes = source)
        val encoded = decode(archive.copy(data = archive.data.copy(schedules = listOf(original.copy(
            courses = listOf(dto), config = original.config.copy(periodAlignmentMode = "TIME"))))))
        val rows = BackupRoomRestoreMapper.map(encoded, BackupImportPlanBuilder.build(encoded, "recoverable-source"))
        assertEquals(PeriodAlignmentMode.TIME, rows.configs.single().periodAlignmentMode)
        assertEquals(source, rows.courses.single().originalPeriodTimes)
        assertEquals((11..14).toList(), rows.courses.single().periods)
        val restored = projectCourseArrangement(rows.courses.single(), rows.configs.single(), rows.periods)
        assertTrue(restored.periods.isEmpty())
        val originalBells = com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes(source)
            .map { PeriodEntity(it.index, it.start.toString(), it.end.toString(), rows.configs.single().id) }
        val recovered = projectCourseArrangement(restored,
            rows.configs.single().copy(periodAlignmentMode = PeriodAlignmentMode.INDEX), originalBells)
        assertEquals((11..14).toList(), recovered.periods)
        assertEquals(rows.courses.single(), recovered.originalArrangement())
    }

    @Test fun legacyCourseGetsASourceSnapshotFromItsOwnBackupBells() {
        val archive = export()
        val course = BackupCourse(BackupStableId.new(BackupStableId.COURSE_PREFIX), "旧课程", null, null,
            1, listOf(1), listOf(1), "ALL", null)
        val legacy = decode(archive.copy(data = archive.data.copy(schedules = archive.data.schedules.map {
            it.copy(courses = listOf(course.copy(id = BackupStableId.new(BackupStableId.COURSE_PREFIX))))
        })))
        val rows = BackupRoomRestoreMapper.map(legacy, BackupImportPlanBuilder.build(legacy, "legacy-source"))
        assertTrue(rows.courses.all { it.originalPeriodTimes == "1,08:00-08:45" })
        assertTrue(rows.configs.all { it.periodAlignmentMode == PeriodAlignmentMode.INDEX })
    }

    @Test fun timeAlignmentRejectsMissingSourceBeforeRestore() {
        val archive = export()
        val source = archive.data.schedules.first()
        val course = BackupCourse(BackupStableId.new(BackupStableId.COURSE_PREFIX), "缺失时间", null, null,
            1, listOf(1), listOf(1), "ALL", null, originalPeriodTimes = "")
        val invalid = archive.copy(data = archive.data.copy(schedules = listOf(source.copy(
            config = source.config.copy(periodAlignmentMode = "TIME"), courses = listOf(course)))))
        try { decode(invalid); fail("Unknown source must fail before writing") }
        catch (expected: BackupCodecException) { assertTrue(expected.message!!.contains("完整原始时间")) }
    }

    private fun scheme(id: Long, publicId: String) = PeriodSchemeEntity(
        id = id, scheduleId = 0, name = "Shared", publicId = publicId, morningPeriodCount = 1)

    private fun times(id: Long) = listOf(PeriodSchemeTimeEntity(id, 1, "08:00", "08:45"))

    private fun export(): BackupArchive {
        val configs = listOf(7, 9).map { defaultConfig(it).copy(activePeriodSchemeId = 1,
            morningPeriodCount = 1, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0) }
        return BackupExportMapper.toArchive(
            metadata = BackupSourceMetadata("2026-10-04T12:00:00Z", "1.1.6", 45,
                "com.xiaomanjun.sleepdownschedule", APP_DATABASE_VERSION, "Android"),
            snapshot = BackupRoomSnapshot(
                schedules = listOf(ScheduleProfileEntity(7, "First", true), ScheduleProfileEntity(9, "Second", false)),
                configs = configs,
                periods = configs.map { PeriodEntity(1, "08:00", "08:45", it.id) },
                periodSchemes = listOf(scheme(1, "shared"), scheme(2, "unused").copy(breakDurationMinutes = 15)),
                periodSchemeTimes = times(1) + times(2)
            ), preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION)
        )
    }

    private fun decode(archive: BackupArchive) = BackupCodec.decode(BackupCodec.encode(archive))
}
