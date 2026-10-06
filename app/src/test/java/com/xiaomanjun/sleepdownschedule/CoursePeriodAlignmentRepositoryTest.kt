package com.xiaomanjun.sleepdownschedule

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CoursePeriodAlignmentRepositoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val name = "a.db"
    private lateinit var db: AppDatabase
    private lateinit var repository: ScheduleRepository
    private fun open() {
        context.getDatabasePath(name).parentFile?.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        repository = ScheduleRepository(db)
    }
    private fun times(id: Long, count: Int, shift: Long = 0) = (1..count).map { index ->
        val start = LocalTime.of(8, 0).plusMinutes((index - 1) * 50L + shift)
        PeriodSchemeTimeEntity(id, index, start.toString(), start.plusMinutes(40).toString())
    }
    private suspend fun scheme(id: Long, count: Int, shift: Long = 0) {
        db.periodSchemeDao().upsertScheme(PeriodSchemeEntity(id = id, scheduleId = 0, publicId = "scheme-$id",
            name = "作息$id", morningPeriodCount = count, noonPeriodCount = 0,
            afternoonPeriodCount = 0, eveningPeriodCount = 0, classDurationMinutes = 40))
        db.periodSchemeDao().upsertTimes(times(id, count, shift))
    }
    private fun course(indices: List<Int>, weekday: Int = 1) = CourseEntity(name = "课程$weekday",
        teacher = null, location = null, weekday = weekday, periods = indices, weeks = listOf(1, 2),
        weekParity = WeekParity.ALL, note = null)
    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        open()
        scheme(11, 14)
        scheme(22, 12)
        scheme(33, 8, 20)
        for (id in 1..2) {
            db.scheduleProfileDao().upsertProfile(ScheduleProfileEntity(id, "课表$id", id == 1))
            db.configDao().upsertConfig(defaultConfig(id).copy(activePeriodSchemeId = 11,
                morningPeriodCount = 14, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0,
                autoCurrentWeek = false, classDurationMinutes = 40))
            db.configDao().upsertPeriods(times(11, 14).map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, id) })
        }
        repository.addCourse(course((11..14).toList()))
        repository.addCourse(course(listOf(13, 14), 2))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun fourteenTwelveFourteenRestoresCourses() = runBlocking {
        val raw = db.courseDao().getCourses(1)
        val preview = repository.previewPeriodSchemeSwitch(1, 22)
        assertEquals(1, preview.impact.partialCount)
        assertEquals(1, preview.impact.hiddenCount)
        assertEquals(raw, db.courseDao().getCourses(1))
        repository.switchPeriodScheme(1, 22, expected = preview)
        val displayed = repository.activeSnapshot().courses
        assertEquals(listOf(11, 12), displayed.first().periods)
        assertTrue(displayed.last().isHiddenByPeriodAlignment())
        assertEquals(raw, db.courseDao().getCourses(1))
        repository.switchPeriodScheme(1, 11)
        assertEquals(raw, repository.activeSnapshot().courses.map { it.originalArrangement() })
        assertEquals(listOf(11, 12, 13, 14), repository.activeSnapshot().courses.first().periods)
        assertEquals(listOf(13, 14), repository.activeSnapshot().courses.last().periods)
        assertEquals(3, db.periodSchemeDao().getAllSchemes().size)
    }

    @Test fun mixedModeCyclesPreserveSource() = runBlocking {
        val raw = db.courseDao().getCourses(1)
        repeat(3) {
            repository.switchPeriodScheme(1, 22, PeriodAlignmentMode.INDEX)
            repository.switchPeriodScheme(1, 33, PeriodAlignmentMode.TIME)
            assertTrue(repository.activeSnapshot().courses.all { it.isHiddenByPeriodAlignment() })
            repository.switchPeriodScheme(1, 11, PeriodAlignmentMode.TIME)
            assertEquals(raw.map { it.periods }, repository.activeSnapshot().courses.map { it.periods })
            repository.switchPeriodScheme(1, 22, PeriodAlignmentMode.TIME)
            repository.switchPeriodScheme(1, 33, PeriodAlignmentMode.INDEX)
            repository.switchPeriodScheme(1, 11, PeriodAlignmentMode.INDEX)
            assertEquals(raw, db.courseDao().getCourses(1))
        }
    }

    @Test fun reopenPreservesSourceAndMode() = runBlocking {
        val raw = db.courseDao().getCourses(1)
        repository.switchPeriodScheme(1, 22, PeriodAlignmentMode.TIME)
        db.close()
        open()
        repository.ensureDefaults()
        assertEquals(PeriodAlignmentMode.TIME, repository.activeSnapshot().config.periodAlignmentMode)
        assertEquals(listOf(11, 12), repository.activeSnapshot().courses.first().periods)
        assertEquals(raw, db.courseDao().getCourses(1))
        repository.switchPeriodScheme(1, 11)
        assertEquals(raw.map { it.periods }, repository.activeSnapshot().courses.map { it.periods })
    }

    @Test fun switchingOneTableKeepsReadersConsistent() = runBlocking {
        val other = db.configDao().getConfig(2)
        val otherPeriods = db.configDao().getPeriods(2)
        repository.switchPeriodScheme(1, 22)
        val active = repository.activeSnapshot()
        assertEquals(active.courses, repository.snapshot().courses)
        assertEquals(active.courses, repository.scheduleSnapshot(1).courses)
        assertEquals(active.courses, repository.state.first().courses)
        assertEquals(active.courses, repository.allSchedulesState.first().courses)
        assertEquals(other, db.configDao().getConfig(2))
        assertEquals(otherPeriods, db.configDao().getPeriods(2))
    }

    @Test fun metadataAndAiEditsKeepHiddenLessons() = runBlocking {
        repository.switchPeriodScheme(1, 22)
        val clipped = repository.activeSnapshot().courses.first()
        repository.updateCourse(clipped.copy(name = "改名", teacher = "教师"))
        val stored = db.courseDao().getCourses(1).first()
        assertEquals((11..14).toList(), stored.periods)
        assertEquals(clipped.originalArrangement().originalPeriodTimes, stored.originalPeriodTimes)
        val current = repository.activeSnapshot().courses.first()
        val action = AgentValidatedAction(type = AgentValidatedActionType.UPDATE, original = current,
            edited = current.copy(location = "教室"), scope = AgentActionScope.ALL_WEEKS, targetWeek = 1,
            summary = "修改地点", sourceScheduleId = 1)
        val result = repository.executeAgentPlan(AgentPlan(listOf(action)))
        assertTrue(result.message, result.success)
        assertEquals((11..14).toList(), db.courseDao().getCourses(1).first().periods)
        assertEquals(stored.originalPeriodTimes, db.courseDao().getCourses(1).first().originalPeriodTimes)
    }

    @Test fun aiPartialDeleteAfterIndexSwitchKeepsMorningSourceClock() = runBlocking {
        deleteInAfternoonAndRecover()
        assertEquals("2,08:50-09:30", db.courseDao().getCourses(1).single { it.weekday == 3 }.originalPeriodTimes)
    }

    @Test fun aiPartialDeleteInFirstWeekKeepsBothSourceFragments() = runBlocking {
        deleteInAfternoonAndRecover(selectedWeek = 1)
    }

    @Test fun aiPartialDeleteInLastWeekKeepsBothSourceFragments() = runBlocking {
        deleteInAfternoonAndRecover(selectedWeek = 2)
    }

    @Test fun aiScopedPartialDeleteKeepsMixedExactAndOrdinaryTimes() = runBlocking {
        deleteInAfternoonAndRecover(selectedWeek = 1, indices = listOf(1, 2, 3),
            custom = "2,08:57-09:16")
        val remaining = db.courseDao().getCourses(1).single { it.weekday == 3 && it.weeks == listOf(1) }
        assertEquals("2,08:57-09:16", remaining.customPeriodTimes)
        assertNull(remaining.customStartTime)
        assertNull(remaining.customEndTime)
    }

    @Test fun aiDeletingCustomPartKeepsOrdinarySourceClock() = runBlocking {
        deleteInAfternoonAndRecover(custom = "2,08:57-09:16", deleted = listOf(2))
        val remaining = db.courseDao().getCourses(1).single { it.weekday == 3 }
        assertEquals("1,08:00-08:40", remaining.originalPeriodTimes)
        assertNull(remaining.customPeriodTimes)
    }

    @Test fun aiExplicitClockEditAfterSwitchCapturesNewSourceClock() = runBlocking {
        val original = morningCourseInAfternoon()
        val edited = original.copy(customStartTime = "16:05", customEndTime = "16:30")
        val result = repository.executeAgentPlan(AgentPlan(listOf(AgentValidatedAction(
            type = AgentValidatedActionType.UPDATE, original = original, edited = edited,
            scope = AgentActionScope.ALL_WEEKS, targetWeek = 1, summary = "修改时间", sourceScheduleId = 1
        ))))
        assertTrue(result.message, result.success && result.verified)
        val stored = db.courseDao().getCourses(1).single { it.weekday == 3 }
        assertEquals("1,14:00-14:40;2,14:50-15:30", stored.originalPeriodTimes)
        repository.switchPeriodScheme(1, 55, PeriodAlignmentMode.TIME)
        val displayed = repository.activeSnapshot().courses.single { it.weekday == 3 }
        assertEquals("16:05 - 16:30", courseTimeLabel(displayed, repository.activeSnapshot().periods))
    }

    @Test fun aiPartialMetadataEditKeepsEachFragmentsSourceClock() = runBlocking {
        val original = morningCourseInAfternoon()
        val state = repository.activeSnapshot()
        val facts = buildDayAgentFacts(state.courses, state.periods, state.config,
            java.time.LocalDate.of(2026, 10, 5), null).copy(semesterCourses = state.courses)
        val action = parseAgentActions("""<agent_actions>[{"type":"UPDATE_COURSE","courseId":${original.id},
            "scope":"SELECTED_WEEKS","sourceWeeks":[1],"sourcePeriods":[2],
            "course":{"name":"改名后的早课"}}]</agent_actions>""", facts).actions.single()
        val result = repository.executeAgentPlan(AgentPlan(listOf(action)))
        assertTrue(result.message, result.success && result.verified)
        val edited = db.courseDao().getCourses(1).single { it.name == "改名后的早课" }
        assertEquals("2,08:50-09:30", edited.originalPeriodTimes)
        assertEquals(listOf(1), edited.weeks)
        repository.switchPeriodScheme(1, 55, PeriodAlignmentMode.TIME)
        assertEquals(listOf(2), repository.activeSnapshot().courses.single { it.name == edited.name }.periods)
    }

    @Test fun aiWriteRollsBackWhenDatabaseChangesOriginalClock() = runBlocking {
        val original = morningCourseInAfternoon()
        val before = db.courseDao().getCourses(1)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER corrupt_source_clock
            AFTER INSERT ON courses
            WHEN NEW.id = ${original.id} AND NEW.originalPeriodTimes <> '2,14:50-15:30'
            BEGIN UPDATE courses SET originalPeriodTimes = '2,14:50-15:30' WHERE id = NEW.id; END""")
        val action = AgentValidatedAction(type = AgentValidatedActionType.DELETE, original = original,
            scope = AgentActionScope.ALL_WEEKS, targetWeek = 1, sourcePeriods = listOf(1),
            summary = "删除部分节次", sourceScheduleId = 1)
        val result = repository.executeAgentPlan(AgentPlan(listOf(action)))
        assertFalse(result.success)
        assertFalse(result.verified)
        assertTrue(result.message, result.message.contains("真实状态与操作计划不一致"))
        assertEquals(before, db.courseDao().getCourses(1))
    }

    private suspend fun morningCourseInAfternoon(indices: List<Int> = listOf(1, 2), custom: String? = null): CourseEntity {
        scheme(55, 3)
        scheme(44, 3, 360)
        repository.switchPeriodScheme(1, 55)
        repository.addCourse(course(indices, 3).copy(customPeriodTimes = custom))
        repository.switchPeriodScheme(1, 44, PeriodAlignmentMode.INDEX)
        return repository.activeSnapshot().courses.single { it.weekday == 3 }
    }

    private suspend fun deleteInAfternoonAndRecover(selectedWeek: Int? = null,
        indices: List<Int> = listOf(1, 2), custom: String? = null, deleted: List<Int> = listOf(1)) {
        val original = morningCourseInAfternoon(indices, custom)
        val source = original.originalArrangement()
        val action = AgentValidatedAction(type = AgentValidatedActionType.DELETE, original = original,
            scope = if (selectedWeek == null) AgentActionScope.ALL_WEEKS else AgentActionScope.SELECTED_WEEKS,
            targetWeek = 1, sourceWeeks = selectedWeek?.let(::listOf).orEmpty(), sourcePeriods = deleted,
            summary = "删除部分节次", sourceScheduleId = 1)
        val result = repository.executeAgentPlan(AgentPlan(listOf(action)))
        assertTrue(result.message, result.success && result.verified)
        val stored = db.courseDao().getCourses(1).filter { it.weekday == 3 }
        assertEquals(if (selectedWeek == null) 1 else 2, stored.size)
        source.weeks.forEach { week ->
            val expectedPeriods = if (selectedWeek == null || week == selectedWeek) indices - deleted.toSet() else indices
            val row = stored.single { week in it.weeks }
            assertEquals(expectedPeriods, row.periods)
            assertEquals(encodeCoursePeriodTimes(parseCoursePeriodTimes(source.originalPeriodTimes)
                .filter { it.index in expectedPeriods }), row.originalPeriodTimes)
            val previewRow = result.preview!!.after.single { it.weekday == 3 && week in it.weeks }.originalArrangement()
            assertEquals(row.originalPeriodTimes, previewRow.originalPeriodTimes)
        }
        db.close()
        open()
        assertEquals(stored, db.courseDao().getCourses(1).filter { it.weekday == 3 })
        repository.switchPeriodScheme(1, 55, PeriodAlignmentMode.TIME)
        val state = repository.activeSnapshot()
        state.courses.filter { it.weekday == 3 }.forEach { displayed ->
            assertFalse(displayed.isHiddenByPeriodAlignment())
            val raw = displayed.originalArrangement()
            assertEquals(raw.periods, displayed.periods)
            val expectedTimes = parseCoursePeriodTimes(source.originalPeriodTimes).filter { it.index in raw.periods }
                .associateBy { it.index }.toMutableMap()
            parseCoursePeriodTimes(custom).filter { it.index in raw.periods }.forEach { expectedTimes[it.index] = it }
            assertEquals(expectedTimes.values.sortedBy { it.start }, courseTimeSegments(displayed, state.periods))
        }
    }

    @Test fun cancelledNoOpAndStaleSwitchDoNotWrite() = runBlocking {
        val raw = db.courseDao().getCourses(1)
        val config = db.configDao().getConfig(1)
        val preview = repository.previewPeriodSchemeSwitch(1, 22)
        assertEquals(config, db.configDao().getConfig(1))
        repository.switchPeriodScheme(1, 11)
        assertEquals(config, db.configDao().getConfig(1))
        repository.updateCourse(repository.activeSnapshot().courses.first().copy(note = "新备注"))
        val latest = db.courseDao().getCourses(1)
        try { repository.switchPeriodScheme(1, 22, expected = preview); fail("Stale preview must fail") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("重新确认")) }
        assertEquals(latest, db.courseDao().getCourses(1))
        assertEquals(config, db.configDao().getConfig(1))
        assertEquals(raw.map { it.periods }, latest.map { it.periods })
        assertEquals(3, db.periodSchemeDao().getAllSchemes().size)
    }

    @Test fun missingClockFailsPreviewWithoutWrites() = runBlocking {
        val raw = db.courseDao().getCourses(1).first()
        db.courseDao().updateCourse(raw.copy(originalPeriodTimes = ""))
        val before = repository.activeSnapshot()
        try { repository.previewPeriodSchemeSwitch(1, 22, PeriodAlignmentMode.TIME); fail("Incomplete source") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("不完整")) }
        assertEquals(before, repository.activeSnapshot())
        assertEquals(3, db.periodSchemeDao().getAllSchemes().size)
    }

    @Test fun aiSettingEditAndUndoKeepHiddenCourses() = runBlocking {
        repository.switchPeriodScheme(1, 22)
        val before = repository.snapshot()
        val originalSchemes = repository.loadPeriodSchemes(1)
        val (_, after) = repository.commitAgentSettingPlan(before,
            before.config.copy(totalWeeks = 18), before.periods, null, "改名课表", emptyList())
        assertEquals(before.courses.map { it.originalArrangement() }, db.courseDao().getCourses(1))
        assertTrue(after.courses.last().isHiddenByPeriodAlignment())
        repository.restoreAgentSettingPlan(after, before, originalSchemes, "课表1")
        assertEquals(before.courses.map { it.originalArrangement() }, db.courseDao().getCourses(1))
        assertEquals(3, db.periodSchemeDao().getAllSchemes().size)
    }

    @Test fun detailEditCopiesSchemePreservingSource() = runBlocking {
        repository.switchPeriodScheme(1, 22)
        val raw = db.courseDao().getCourses(1)
        val state = repository.activeSnapshot()
        val draft = repository.loadPeriodSchemes(1)
        val edited = draft.copy(schemes = draft.schemes.map { it.copy(times = it.times.map { bell ->
            if (bell.periodIndex == 1) bell.copy(startTime = "08:02", endTime = "08:42") else bell }) })
        repository.saveScheduleDetail(state.config, edited, state.courses, state.periods)
        assertEquals(4, db.periodSchemeDao().getAllSchemes().size)
        assertNotEquals(22L, repository.activeSnapshot().config.activePeriodSchemeId)
        assertEquals(raw, db.courseDao().getCourses(1))
        assertEquals("08:00", db.periodSchemeDao().getTimes(22).first().startTime)
        repository.switchPeriodScheme(1, 11)
        assertEquals(raw.map { it.periods }, repository.activeSnapshot().courses.map { it.periods })
    }

    @Test fun importNeverRetimesAnotherSharedTable() = runBlocking {
        val imported = listOf(PeriodEntity(1, "13:00", "13:40"))
        repository.importDraft(ImportDraft(defaultConfig().copy(morningPeriodCount = 1, noonPeriodCount = 0,
            afternoonPeriodCount = 0, eveningPeriodCount = 0), imported, listOf(course(listOf(1)))))
        assertEquals("1,13:00-13:40", db.courseDao().getCourses(1).single().originalPeriodTimes)
        assertEquals("08:00", db.configDao().getPeriods(2).first().startTime)
        assertEquals(11L, db.configDao().getConfig(2)!!.activePeriodSchemeId)
        repository.switchPeriodScheme(1, 11, PeriodAlignmentMode.TIME)
        assertEquals(listOf(7), repository.activeSnapshot().courses.single().periods)
    }

    @Test fun copyChecksEffectiveTimeAfterTimeAlignment() = runBlocking {
        repository.addCourse(course(listOf(2), 3))
        repository.switchPeriodScheme(1, 33, PeriodAlignmentMode.TIME)
        val before = db.courseDao().getCourses(1)
        assertEquals(listOf(1, 2), repository.activeSnapshot().courses.last().periods)
        try { repository.copyCourses(listOf(course(listOf(1), 3))); fail("Mapped course occupies this target time") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("已有课程")) }
        assertEquals(before, db.courseDao().getCourses(1))
    }

    @Test fun failedTimeImportRollsBackAllData() = runBlocking {
        val before = repository.snapshot()
        val schemeCount = db.periodSchemeDao().getAllSchemes().size
        try {
            repository.importDraft(ImportDraft(defaultConfig().copy(periodAlignmentMode = PeriodAlignmentMode.TIME),
                listOf(PeriodEntity(1, "08:00", "08:40")),
                listOf(course(listOf(13)).copy(originalPeriodTimes = ""))), createNewSchedule = true)
            fail("Unknown ordinary source cannot be time aligned")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("不完整")) }
        assertEquals(before, repository.snapshot())
        assertEquals(schemeCount, db.periodSchemeDao().getAllSchemes().size)
    }
}
