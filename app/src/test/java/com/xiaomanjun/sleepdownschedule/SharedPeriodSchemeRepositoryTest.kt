package com.xiaomanjun.sleepdownschedule

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentActionScope
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentValidatedActionType
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Room transactions and SQLite, without requiring a connected Android device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharedPeriodSchemeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("period_scheme_library", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ScheduleRepository(database)
        val scheme = PeriodSchemeEntity(id = 11, scheduleId = 0, name = "共享作息", publicId = "shared",
            morningPeriodCount = 2, afternoonPeriodCount = 1)
        database.periodSchemeDao().upsertScheme(scheme)
        database.periodSchemeDao().upsertTimes(standardTimes(11))
        for (id in 1..2) {
            database.scheduleProfileDao().upsertProfile(ScheduleProfileEntity(id, "课表$id", id == 1))
            database.configDao().upsertConfig(defaultConfig(id).copy(activePeriodSchemeId = 11,
                totalWeeks = if (id == 1) 16 else 20, morningPeriodCount = 2,
                noonPeriodCount = 0, afternoonPeriodCount = 1, eveningPeriodCount = 0))
            database.configDao().upsertPeriods(standardTimes(11).map {
                PeriodEntity(it.periodIndex, it.startTime, it.endTime, id)
            })
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bothTablesReadTheSameGlobalRecordAndOpeningDoesNotWrite() = runBlocking {
        val before = state()
        assertEquals(11L, repository.loadPeriodSchemes(1).activeSchemeId)
        assertEquals(11L, repository.loadPeriodSchemes(2).activeSchemeId)
        assertEquals(listOf(1, 2), repository.publicPeriodSchemes().single().usages.orEmpty().map { it.config.id })
        assertEquals(database.periodSchemeDao().getSchemes(1), database.periodSchemeDao().getSchemes(999))
        assertEquals(before, state())
    }

    @Test
    fun detailChangeCreatesPublicCopyAndRebindsOnlyEditedTable() = runBlocking {
        val original = repository.loadPeriodSchemes(1)
        val edited = original.copy(schemes = listOf(changeFirstBell(original.schemes.single())))
        repository.saveScheduleDetail(requireNotNull(database.configDao().getConfig(1)), edited)

        val newId = requireNotNull(database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertNotEquals(11L, newId)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals("08:05", database.configDao().getPeriods(1).first().startTime)
        assertEquals("08:00", database.configDao().getPeriods(2).first().startTime)
        assertEquals("08:00", database.periodSchemeDao().getTimes(11).first().startTime)
        assertEquals(2, repository.publicPeriodSchemes().size)
        assertEquals(16, database.configDao().getConfig(1)?.totalWeeks)
        assertEquals(20, database.configDao().getConfig(2)?.totalWeeks)
    }

    @Test
    fun compatibilityConfigSaveAlsoCopiesOnWriteAndKeepsOtherTable() = runBlocking {
        val config = requireNotNull(database.configDao().getConfig(1))
        val periods = database.configDao().getPeriods(1).map {
            if (it.periodIndex == 1) it.copy(startTime = "08:05", endTime = "08:50") else it
        }
        repository.saveConfigForSchedule(1, config.copy(currentWeek = 2), periods)
        assertNotEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals("08:05", database.configDao().getPeriods(1).first().startTime)
        assertEquals("08:00", database.configDao().getPeriods(2).first().startTime)
        assertEquals("08:00", database.periodSchemeDao().getTimes(11).first().startTime)
        assertEquals(2, database.configDao().getConfig(1)?.currentWeek)
    }

    @Test
    fun detailNoOpAndNonTimetableSaveDoNotCreateCopies() = runBlocking {
        val draft = repository.loadPeriodSchemes(1)
        val config = requireNotNull(database.configDao().getConfig(1))
        repository.saveScheduleDetail(config, draft)
        repository.saveScheduleDetail(config.copy(totalWeeks = 18), draft)

        assertEquals(1, database.periodSchemeDao().getAllSchemes().size)
        assertEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(18, database.configDao().getConfig(1)?.totalWeeks)
        assertEquals("08:00", database.configDao().getPeriods(2).first().startTime)
    }

    @Test
    fun restoredDeletionIsANetNoOpAndDoesNotCreateACopy() = runBlocking {
        val initial = PeriodTimelineSession(requireNotNull(database.configDao().getConfig(1)), repository.loadPeriodSchemes(1))
        val deleted = requireNotNull(deleteTimelinePeriod(initial, 2))
        val restored = requireNotNull(insertTimelinePeriod(deleted, deleted.vacancies.single().id))
        assertEquals(initial.active.times, restored.active.times)
        repository.saveScheduleDetail(restored.config, restored.draft)
        assertEquals(1, database.periodSchemeDao().getAllSchemes().size)
        assertEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
    }

    @Test
    fun publicManagerEditUpdatesEveryReferenceInPlace() = runBlocking {
        val explicitClock = course(1, listOf(1)).copy(customStartTime = "10:10", customEndTime = "10:40",
            customPeriodTimes = "1,10:10-10:40")
        val explicitId = database.courseDao().insertCourse(explicitClock)
        val saved = repository.publicPeriodSchemes().single()
        val session = savedPeriodSchemeSession(saved, defaultConfig())
        val result = repository.savePublicPeriodScheme(saved,
            session.updateActive(changeFirstBell(session.active)), saved.id)

        assertEquals("shared", result.id)
        assertEquals(11L, result.roomId)
        assertEquals(1, database.periodSchemeDao().getAllSchemes().size)
        for (id in 1..2) {
            assertEquals(11L, database.configDao().getConfig(id)?.activePeriodSchemeId)
            assertEquals("08:05", database.configDao().getPeriods(id).first().startTime)
        }
        assertEquals(explicitClock.copy(id = explicitId), database.courseDao().getCourses(1).single())
    }

    @Test
    fun selectorBindsMigratedProvenanceRecordWithoutCopyingOrChangingIt() = runBlocking {
        val legacy = requireNotNull(database.periodSchemeDao().getScheme(11)).copy(id = 22, publicId = "legacy-22",
            scheduleId = 7, isActive = true, name = "冬季", sourceScheduleName = "旧课表（冬季）")
        database.periodSchemeDao().upsertScheme(legacy)
        database.periodSchemeDao().upsertTimes(standardTimes(22))
        val config = requireNotNull(database.configDao().getConfig(1))
        val selected = repository.publicPeriodSchemes().single { it.roomId == 22L }
        val applied = applySavedPeriodScheme(selected, config, repository.loadPeriodSchemes(1))
        repository.saveScheduleDetail(applied.config, applied.draft)
        assertEquals(22L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals(legacy, database.periodSchemeDao().getScheme(22))
        assertEquals(2, database.periodSchemeDao().getAllSchemes().size)
    }

    @Test
    fun changedCurrentBindingRejectsAStaleDetailEvenWhenTimelinesAreIdentical() = runBlocking {
        val initial = repository.loadPeriodSchemes(1)
        val config = requireNotNull(database.configDao().getConfig(1))
        val equivalent = repository.duplicatePublicPeriodScheme("shared")
        repository.switchPeriodScheme(1, equivalent.roomId)
        val before = state()
        rejected { repository.saveScheduleDetail(config, initial) }
        assertEquals(before, state())
    }

    @Test
    fun creatingTableCanBindAnExistingPublicSchemeWithoutCopying() = runBlocking {
        val id = repository.createSchedule("新学期", publicSchemeId = 11)
        assertEquals(11L, database.configDao().getConfig(id)?.activePeriodSchemeId)
        assertEquals(listOf("08:00", "09:00", "14:00"), database.configDao().getPeriods(id).map { it.startTime })
        assertEquals(1, database.periodSchemeDao().getAllSchemes().size)
        assertEquals(1, database.scheduleProfileDao().getActiveProfile()?.id)
    }

    @Test
    fun crossCountSwitchRetainsOutOfRangeCoursesAndRebindsWithoutCopies() = runBlocking {
        val target = PeriodSchemeEntity(id = 22, scheduleId = 0, name = "两节作息", publicId = "short",
            morningPeriodCount = 1, noonPeriodCount = 1)
        database.periodSchemeDao().upsertScheme(target)
        database.periodSchemeDao().upsertTimes(listOf(PeriodSchemeTimeEntity(22, 1, "10:00", "10:45"),
            PeriodSchemeTimeEntity(22, 2, "12:00", "12:45")))
        val courseId = database.courseDao().insertCourse(course(1, listOf(3)))
        repository.switchPeriodScheme(1, 22)
        assertEquals(courseId, database.courseDao().getCourses(1).single().id)
        assertEquals(listOf(3), database.courseDao().getCourses(1).single().periods)
        assertTrue(repository.activeSnapshot().courses.single().isHiddenByPeriodAlignment())
        val switched = requireNotNull(database.configDao().getConfig(1))
        assertEquals(22L, switched.activePeriodSchemeId)
        assertEquals(listOf(1, 1, 0, 0), listOf(switched.morningPeriodCount, switched.noonPeriodCount,
            switched.afternoonPeriodCount, switched.eveningPeriodCount))
        assertEquals(2, database.configDao().getPeriods(1).size)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals(2, database.periodSchemeDao().getAllSchemes().size)
    }

    @Test
    fun importReplacesOnlyDestinationBindingAndKeepsSharedOriginal() = runBlocking {
        val destination = defaultConfig(1).copy(morningPeriodCount = 1, noonPeriodCount = 0,
            afternoonPeriodCount = 0, eveningPeriodCount = 0)
        repository.importDraft(ImportDraft(destination, listOf(PeriodEntity(1, "10:00", "10:45")),
            listOf(course(1, listOf(1)).copy(name = "导入课程"))))

        assertNotEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals("08:00", database.periodSchemeDao().getTimes(11).first().startTime)
        assertEquals("08:00", database.configDao().getPeriods(2).first().startTime)
        assertEquals("10:00", database.configDao().getPeriods(1).first().startTime)
        assertEquals("导入课程", database.courseDao().getCourses(1).single().name)
        assertEquals(2, database.periodSchemeDao().getAllSchemes().size)
    }

    @Test
    fun identicalImportReusesGlobalRecordAndPreservesExplicitCourseClocks() = runBlocking {
        val config = requireNotNull(database.configDao().getConfig(1))
        val incoming = course(1, listOf(1, 2)).copy(customStartTime = "08:12", customEndTime = "09:26",
            customPeriodTimes = "1,08:12-08:38;2,08:50-09:26")
        repository.importDraft(ImportDraft(config, database.configDao().getPeriods(1), listOf(incoming)))
        assertEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals(1, database.periodSchemeDao().getAllSchemes().size)
        val stored = database.courseDao().getCourses(1).single()
        assertEquals(incoming.customStartTime, stored.customStartTime)
        assertEquals(incoming.customEndTime, stored.customEndTime)
        assertEquals(incoming.customPeriodTimes, stored.customPeriodTimes)
    }

    @Test
    fun deletingTableKeepsPublicSchemeAndInUseSchemeCannotBeDeleted() = runBlocking {
        repository.deleteSchedule(1)
        assertNotNull(database.periodSchemeDao().getScheme(11))
        assertEquals(3, database.periodSchemeDao().getTimes(11).size)
        assertEquals(listOf(2), repository.publicPeriodSchemes().single().usages.orEmpty().map { it.config.id })
        val before = state()
        rejected { repository.deletePublicPeriodScheme("shared") }
        assertEquals(before, state())
    }

    @Test
    fun occupiedDeleteInAnotherReferencingTableRejectsWholeSharedEdit() = runBlocking {
        database.courseDao().insertCourse(course(2, listOf(2)))
        val saved = repository.publicPeriodSchemes().single()
        val session = savedPeriodSchemeSession(saved, defaultConfig())
        val deleted = requireNotNull(deleteTimelinePeriod(session, 2))
        val before = state()
        rejected { repository.savePublicPeriodScheme(saved, deleted, saved.id) }
        assertEquals(before, state())
    }

    @Test
    fun deleteThenAddSameCountCannotHideAnOccupiedDeletion() = runBlocking {
        database.courseDao().insertCourse(course(2, listOf(2)))
        val saved = repository.publicPeriodSchemes().single()
        val session = savedPeriodSchemeSession(saved, defaultConfig())
        val deleted = requireNotNull(deleteTimelinePeriod(session, 2, keepVacancy = false))
        val sameCount = requireNotNull(appendTimelinePeriod(deleted, PeriodDayPart.MORNING))
        assertEquals(session.config.totalPeriodCount(), sameCount.config.totalPeriodCount())
        val before = state()
        rejected { repository.savePublicPeriodScheme(saved, sameCount, saved.id) }
        assertEquals(before, state())
    }

    @Test
    fun harmlessInsertRemapsCoursesInEveryReferencingTable() = runBlocking {
        database.courseDao().insertCourse(course(1, listOf(3)))
        database.courseDao().insertCourse(course(2, listOf(3)))
        val saved = repository.publicPeriodSchemes().single()
        val session = savedPeriodSchemeSession(saved, defaultConfig())
        val inserted = requireNotNull(appendTimelinePeriod(session, PeriodDayPart.MORNING))
        repository.savePublicPeriodScheme(saved, inserted, saved.id)
        for (id in 1..2) {
            assertEquals(listOf(4), database.courseDao().getCourses(id).single().periods)
            assertEquals(4, database.configDao().getPeriods(id).size)
            assertEquals(3, database.configDao().getConfig(id)?.morningPeriodCount)
        }
    }

    @Test
    fun changedUsageSincePreviewRejectsStalePublicSaveWithoutPartialWrites() = runBlocking {
        val saved = repository.publicPeriodSchemes().single()
        val session = savedPeriodSchemeSession(saved, defaultConfig())
        database.courseDao().insertCourse(course(2, listOf(1)))
        val before = state()
        rejected { repository.savePublicPeriodScheme(saved,
            session.updateActive(changeFirstBell(session.active)), saved.id) }
        assertEquals(before, state())
    }

    @Test
    fun staleDetailConfirmationCannotOverwriteNewerCourses() = runBlocking {
        val draft = repository.loadPeriodSchemes(1)
        val config = requireNotNull(database.configDao().getConfig(1))
        val expectedCourses = database.courseDao().getCourses(1)
        val expectedPeriods = database.configDao().getPeriods(1)
        database.courseDao().insertCourse(course(1, listOf(1)))
        val before = state()
        rejected { repository.saveScheduleDetail(config,
            draft.copy(schemes = listOf(changeFirstBell(draft.schemes.single()))), expectedCourses, expectedPeriods) }
        assertEquals(before, state())
    }

    @Test
    fun combinedAgentSettingsAndCoursePlanApplyTheExplicitRemapExactlyOnce() = runBlocking {
        database.courseDao().insertCourse(course(1, listOf(3)))
        database.courseDao().insertCourse(course(2, listOf(3)))
        val before = repository.snapshot()
        val original = before.courses.single()
        val session = PeriodTimelineSession(before.config, repository.loadPeriodSchemes(1))
        val inserted = requireNotNull(appendTimelinePeriod(session, PeriodDayPart.MORNING))
        val (result, after) = repository.commitAgentSettingPlan(before, inserted.config,
            inserted.active.times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, 1) },
            inserted.draft, null, listOf(updateAllWeeks(original, original.copy(periods = listOf(4)))))

        assertTrue(result.success && result.verified)
        assertEquals(listOf(4), after.courses.single().periods)
        assertEquals(original.id, after.courses.single().id)
        assertNotEquals(11L, after.config.activePeriodSchemeId)
        assertEquals(listOf(3), database.courseDao().getCourses(2).single().periods)
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
        assertEquals(3, database.periodSchemeDao().getTimes(11).size)
        assertEquals(2, database.periodSchemeDao().getAllSchemes().size)
    }

    @Test
    fun impossibleCombinedAgentPlanRollsBackCopyConfigCoursesAndName() = runBlocking {
        database.courseDao().insertCourse(course(1, listOf(3)))
        val before = repository.snapshot()
        val original = before.courses.single()
        val session = PeriodTimelineSession(before.config, repository.loadPeriodSchemes(1))
        val inserted = requireNotNull(appendTimelinePeriod(session, PeriodDayPart.MORNING))
        val initial = state()
        rejected { repository.commitAgentSettingPlan(before, inserted.config,
            inserted.active.times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, 1) },
            inserted.draft, "不应保存的名称",
            listOf(updateAllWeeks(original, original.copy(periods = listOf(99))))) }
        assertEquals(initial, state())
    }

    @Test
    fun undoAgentSettingsRebindsOriginalSharedIdentityWithoutMutatingIt() = runBlocking {
        database.courseDao().insertCourse(course(1, listOf(3)))
        val before = repository.snapshot()
        val original = before.courses.single()
        val oldSchemes = repository.loadPeriodSchemes(1)
        val oldPublic = requireNotNull(database.periodSchemeDao().getScheme(11))
        val oldTimes = database.periodSchemeDao().getTimes(11)
        val session = PeriodTimelineSession(before.config, oldSchemes)
        val inserted = requireNotNull(appendTimelinePeriod(session, PeriodDayPart.MORNING))
        val (_, after) = repository.commitAgentSettingPlan(before, inserted.config,
            inserted.active.times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, 1) },
            inserted.draft, "更新后的课表",
            listOf(updateAllWeeks(original, original.copy(periods = listOf(4)))))
        val copiedId = requireNotNull(after.config.activePeriodSchemeId)

        repository.restoreAgentSettingPlan(after, before, oldSchemes, "课表1")
        assertEquals(11L, database.configDao().getConfig(1)?.activePeriodSchemeId)
        assertEquals(before.config, database.configDao().getConfig(1))
        assertEquals(before.periods, database.configDao().getPeriods(1))
        assertEquals(before.courses.map { it.originalArrangement() }, database.courseDao().getCourses(1))
        assertEquals(oldPublic, database.periodSchemeDao().getScheme(11))
        assertEquals(oldTimes, database.periodSchemeDao().getTimes(11))
        assertNotNull(database.periodSchemeDao().getScheme(copiedId))
        assertEquals(11L, database.configDao().getConfig(2)?.activePeriodSchemeId)
    }

    @Test
    fun deliberateDuplicateIsNotContinuouslyDeduplicated() = runBlocking {
        val duplicate = repository.duplicatePublicPeriodScheme("shared")
        assertNotEquals("shared", duplicate.id)
        repository.ensureDefaults()
        assertEquals(2, repository.publicPeriodSchemes().size)
        repository.deletePublicPeriodScheme(duplicate.id)
        assertEquals(listOf("shared"), repository.publicPeriodSchemes().map { it.id })
    }

    @Test
    fun committedLegacyPreferenceImportNeverResurrectsDeletedScheme() = runBlocking {
        val old = repository.publicPeriodSchemes().single().copy(id = "old-pref", roomId = 0,
            name = "旧偏好作息", times = listOf(SavedPeriodTime(1, "07:00", "07:45"),
                SavedPeriodTime(2, "09:00", "09:45"), SavedPeriodTime(3, "14:00", "14:45")))
        context.getSharedPreferences("period_scheme_library", Context.MODE_PRIVATE).edit()
            .putString("schemes", Json.encodeToString(listOf(old))).commit()
        repository.migrateLegacyPeriodSchemeLibrary(context)
        assertNotNull(database.periodSchemeDao().getSchemeByPublicId("old-pref"))
        assertTrue(database.periodSchemeDao().hasMigration("legacy-preferences-v1"))
        repository.deletePublicPeriodScheme("old-pref")

        ScheduleRepository(database).migrateLegacyPeriodSchemeLibrary(context)
        assertNull(database.periodSchemeDao().getSchemeByPublicId("old-pref"))
        assertEquals(1, repository.publicPeriodSchemes().size)
    }

    private fun standardTimes(id: Long) = listOf(PeriodSchemeTimeEntity(id, 1, "08:00", "08:45"),
        PeriodSchemeTimeEntity(id, 2, "09:00", "09:45"), PeriodSchemeTimeEntity(id, 3, "14:00", "14:45"))

    private fun changeFirstBell(draft: PeriodSchemeDraft) = draft.copy(times = draft.times.map {
        if (it.periodIndex == 1) it.copy(startTime = "08:05", endTime = "08:50") else it
    })

    private fun course(scheduleId: Int, periods: List<Int>) = CourseEntity(name = "课程$scheduleId",
        teacher = null, location = null, weekday = 1, periods = periods, weeks = listOf(1, 2),
        weekParity = WeekParity.ALL, note = null, scheduleId = scheduleId)

    private fun updateAllWeeks(original: CourseEntity, edited: CourseEntity) = AgentValidatedAction(
        type = AgentValidatedActionType.UPDATE, original = original, edited = edited,
        scope = AgentActionScope.ALL_WEEKS, sourceScheduleId = original.scheduleId, summary = "调整课程节次")

    private suspend fun state(): List<Any> = listOf(database.scheduleProfileDao().getProfiles(),
        database.configDao().getAllConfigs(), database.configDao().getAllPeriods(),
        database.courseDao().getAllCourses(), database.periodSchemeDao().getAllSchemes(),
        database.periodSchemeDao().getAllTimes())

    private suspend fun rejected(action: suspend () -> Unit) {
        val failure = runCatching { action() }.exceptionOrNull()
        assertTrue("Expected validation failure, got $failure",
            failure is IllegalArgumentException || failure is IllegalStateException)
    }
}
