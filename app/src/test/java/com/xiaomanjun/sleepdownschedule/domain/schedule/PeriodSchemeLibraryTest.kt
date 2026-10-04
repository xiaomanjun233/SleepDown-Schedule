package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PeriodSchemeLibraryTest {
    private val config = defaultConfig(7).copy(morningPeriodCount = 2, noonPeriodCount = 0,
        afternoonPeriodCount = 0, eveningPeriodCount = 0)
    private val source = PeriodSchemeDraft(PeriodSchemeEntity(42, 7, "夏令时", isActive = true), listOf(
        PeriodSchemeTimeEntity(42, 1, "09:00", "09:50"), PeriodSchemeTimeEntity(42, 2, "10:20", "11:00")))

    @Test fun snapshotHasNoScheduleIdentityAndSurvivesSerialization() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val serialized = Json.encodeToString(saved)
        assertFalse(serialized.contains("scheduleId"))
        assertFalse(serialized.contains("schemeId"))
        assertEquals(saved, Json.decodeFromString<SavedPeriodScheme>(serialized))
    }

    @Test fun applyingToDifferentSchedulesCopiesBellsAndPreservesExistingSchemes() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        for (scheduleId in listOf(8, 19)) {
            val targetConfig = config.copy(id = scheduleId, morningPeriodCount = 1, afternoonPeriodCount = 1)
            val existing = source.copy(scheme = source.scheme.copy(scheduleId = scheduleId, name = "原作息"))
            val draft = SchedulePeriodSchemesDraft(listOf(existing), 42)
            val applied = applySavedPeriodScheme(saved, targetConfig, draft)
            assertEquals(scheduleId, applied.config.id)
            assertEquals(2, applied.config.morningPeriodCount)
            assertEquals(0, applied.config.afternoonPeriodCount)
            assertEquals(2, applied.draft.schemes.size)
            assertEquals(existing.times, applied.draft.schemes.first().times)
            val added = applied.draft.schemes.last()
            assertTrue(added.scheme.id < 0)
            assertEquals(scheduleId, added.scheme.scheduleId)
            assertEquals(added.scheme.id, applied.draft.activeSchemeId)
            assertEquals(listOf("09:00", "10:20"), added.times.map { it.startTime })
            assertTrue(applied.draft.topologyOperations.isEmpty())
            assertEquals(1, draft.schemes.size)
        }
    }

    @Test fun storedAutomaticBellsAreNeverRegeneratedBySavingOrApplyingLibrary() {
        val auto = source.copy(scheme = source.scheme.copy(mode = PeriodSchemeMode.AUTO_MATCH))
        val saved = savePeriodSchemeSnapshot("independent", "自定义铃声", config, auto)
        assertEquals("10:20", saved.times.last().startTime)
        val applied = applySavedPeriodScheme(saved, config, SchedulePeriodSchemesDraft(listOf(auto), 42))
        assertEquals(auto.times, applied.draft.schemes.first().times)
        assertEquals(PeriodSchemeMode.MANUAL, applied.draft.schemes.first().scheme.mode)
    }

    @Test fun repeatedApplicationReusesEquivalentBellsAndKeepsPendingTopologyOperations() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val draft = SchedulePeriodSchemesDraft(listOf(source), 42, listOf(PeriodTopologyOperation.AddAfter(1)))
        val first = applySavedPeriodScheme(saved, config, draft)
        val second = applySavedPeriodScheme(saved, first.config, first.draft)
        assertEquals(first.draft.activeSchemeId, second.draft.activeSchemeId)
        assertEquals(1, second.draft.schemes.size)
        assertEquals(draft.topologyOperations, second.draft.topologyOperations)
    }

    @Test fun equivalentBellsMergeNamesAndAllSourcesRegardlessOfTheirIds() {
        val saved = savePeriodSchemeSnapshot("first", "夏令时", config, source)
            .copy(sources = listOf(PeriodSchemeSource("课表甲", "夏令时")))
        val other = saved.copy(id = "other", name = "学校作息", times = saved.times.reversed(),
            sources = listOf(PeriodSchemeSource("课表乙", "学校作息")), noonStartTime = "15:00")
        val merged = normalizePeriodSchemeLibrary(listOf(saved, other))
        assertEquals(1, merged.size)
        assertEquals("first", merged.single().id)
        assertEquals(listOf("学校作息"), merged.single().alternateNames)
        assertEquals(setOf("课表甲", "课表乙"), merged.single().sources.map { it.scheduleName }.toSet())
        assertEquals(merged, normalizePeriodSchemeLibrary(merged))
    }

    @Test fun differentBellsStructureOrEditingDefaultsRemainSeparate() {
        val saved = savePeriodSchemeSnapshot("first", "夏令时", config, source)
        val changes = listOf(
            saved.copy(id = "time", times = saved.times.map { if (it.periodIndex == 1) it.copy(startTime = "09:05") else it }),
            saved.copy(id = "structure", morningPeriodCount = 1, afternoonPeriodCount = 1),
            saved.copy(id = "duration", classDurationMinutes = saved.classDurationMinutes + 1),
            saved.copy(id = "anchor", morningStartTime = "08:15")
        )
        assertEquals(5, normalizePeriodSchemeLibrary(listOf(saved) + changes).size)
    }

    @Test fun upgradingOldImportsAddsSourcesWithoutOverwritingEditedOrRecreatingDeletedEntries() {
        val old = savePeriodSchemeSnapshot("legacy", "原作息", config, source)
        val edited = old.copy(name = "自己修改", times = old.times.map {
            if (it.periodIndex == 1) it.copy(startTime = "09:05") else it
        })
        val deleted = old.copy(id = "deleted", name = "已删除", classDurationMinutes = 30)
        val result = migrateSchedulePeriodSchemes(listOf(edited), listOf(old, deleted), "旧课表", previouslyImported = true)
        assertEquals(1, result.size)
        assertEquals(edited.times, result.single().times)
        assertEquals("自己修改", result.single().name)
        assertEquals(listOf(PeriodSchemeSource("旧课表", "原作息")), result.single().sources)
        assertEquals(listOf("原作息"), result.single().alternateNames)
    }

    @Test fun firstImportMergesIntoExistingLibraryAndPreservesItsIdentity() {
        val saved = savePeriodSchemeSnapshot("owned", "我的作息", config, source).copy(createdInLibrary = true)
        val imported = saved.copy(id = "legacy", name = "教务作息", createdInLibrary = false)
        val result = migrateSchedulePeriodSchemes(listOf(saved), listOf(imported), "其他课表", previouslyImported = false)
        assertEquals("owned", result.single().id)
        assertTrue(result.single().createdInLibrary)
        assertEquals(listOf("教务作息"), result.single().alternateNames)
        assertEquals("其他课表", result.single().sources.single().scheduleName)
    }

    @Test fun newlyRestoredScheduleWithReusedLocalIdsDoesNotOverwriteExistingLibrary() {
        val saved = savePeriodSchemeSnapshot("legacy-id", "保留作息", config, source)
        val restored = saved.copy(name = "恢复的课表作息", times = saved.times.map {
            if (it.periodIndex == 1) it.copy(startTime = "09:05") else it
        })
        val result = migrateSchedulePeriodSchemes(listOf(saved), listOf(restored), "恢复课表", previouslyImported = false)
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.id }.distinct().size)
        assertEquals(saved, result.first())
        assertEquals(restored.times, result.last().times)
        assertEquals("恢复课表", result.last().sources.single().scheduleName)
    }

    @Test fun editingIntoEquivalentEntryKeepsRequestedNameAndCombinedProvenance() {
        val saved = savePeriodSchemeSnapshot("first", "学校作息", config, source)
            .copy(sources = listOf(PeriodSchemeSource("学校课表", "学校作息")))
        val edited = saved.copy(id = "edited", name = "我改的名称", sources = emptyList(), createdInLibrary = true)
        val result = normalizePeriodSchemeLibrary(listOf(saved, edited), preferredId = "edited")
        assertEquals("edited", result.single().id)
        assertEquals("我改的名称", result.single().name)
        assertEquals(saved.sources, result.single().sources)
        assertTrue(result.single().createdInLibrary)
        assertEquals(listOf("学校作息"), result.single().alternateNames)
    }

    @Test fun restoreDeduplicatesContentButRetainsConflictingIdsWithDifferentBells() {
        val saved = savePeriodSchemeSnapshot("same-id", "我的作息", config, source)
        val same = saved.copy(id = "another-id", name = "备份作息",
            sources = listOf(PeriodSchemeSource("备份课表", "备份作息")))
        assertEquals(1, mergeRestoredPeriodSchemes(listOf(saved), listOf(same)).size)
        val changed = saved.copy(times = saved.times.map { if (it.periodIndex == 1) it.copy(startTime = "09:05") else it })
        val result = mergeRestoredPeriodSchemes(listOf(saved), listOf(changed))
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.id }.distinct().size)
        assertTrue(result.any { it.times == saved.times })
        assertTrue(result.any { it.times == changed.times })
    }

    @Test fun originFieldsAreOptionalForLegacyJsonAndRoundTripWithoutRoomIds() {
        val saved = savePeriodSchemeSnapshot("first", "夏令时", config, source)
        val legacy = Json.encodeToString(saved)
        val decoded = Json.decodeFromString<SavedPeriodScheme>(legacy)
        assertTrue(decoded.sources.isEmpty())
        assertTrue(decoded.alternateNames.isEmpty())
        assertFalse(decoded.createdInLibrary)
        val upgraded = decoded.copy(sources = listOf(PeriodSchemeSource("原课表", "夏令时")),
            alternateNames = listOf("教务作息"), createdInLibrary = true)
        val text = Json.encodeToString(upgraded)
        assertEquals(upgraded, Json.decodeFromString<SavedPeriodScheme>(text))
        assertFalse(text.contains("scheduleId"))
        assertFalse(text.contains("schemeId"))
    }

    @Test fun mismatchedPeriodCountCannotRewriteTargetCourseNumbering() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val target = config.copy(morningPeriodCount = 3)
        val draft = SchedulePeriodSchemesDraft(listOf(source), 42)
        try { applySavedPeriodScheme(saved, target, draft); fail("Must reject different period counts") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("先在详细节次编辑中调整")) }
        assertEquals(42L, draft.activeSchemeId)
        assertEquals(3, target.morningPeriodCount)
    }
}
