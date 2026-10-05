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
