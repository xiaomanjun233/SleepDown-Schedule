package com.xiaomanjun.sleepdownschedule

import android.app.Application
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.PendingScheduleRefresh
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutoRefreshReviewRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ScheduleRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ScheduleRepository(db)
        repository.ensureDefaults()
        repository.addCourse(CourseEntity(name = "数学", teacher = null, location = "101", weekday = 1,
            periods = listOf(1), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = "保留笔记",
            customColorArgb = 123))
    }
    @After fun close() { db.close() }
    private suspend fun review(): PendingScheduleRefresh {
        val state = repository.scheduleSnapshot(1)
        val original = db.courseDao().getCourses(1).single()
        return PendingScheduleRefresh.create(AutoRefreshScheduleProfile("test", "学校", "a", "入口", "", "", 1),
            state, listOf(original.copy(id = 0, location = "202")), state.config.copy(totalWeeks = 18))
    }

    @Test fun pendingRoundTripDoesNotWriteAndReviewKeepsCourseId() = runBlocking {
        val before = db.courseDao().getCourses(1)
        val pending = Json.decodeFromString<PendingScheduleRefresh>(Json.encodeToString(review()))
        assertEquals(before, db.courseDao().getCourses(1))
        repository.applyReviewedRefresh(1, pending.before, pending.timingKey,
            mergeReviewedRefresh(pending.before, pending.differences, setOf(0)), null)
        val current = db.courseDao().getCourses(1).single()
        assertEquals(before.single().id, current.id)
        assertEquals("202", current.location)
        assertEquals(before.single().note, current.note)
        assertEquals(before.single().customColorArgb, current.customColorArgb)
        assertEquals(pending.beforeTerm.totalWeeks, db.configDao().getConfig(1)!!.totalWeeks)
    }

    @Test fun staleReviewCannotOverwriteNewEditsOrPartiallyUpdateTerm() = runBlocking {
        val pending = review()
        val edited = db.courseDao().getCourses(1).single().copy(note = "核对期间新笔记")
        repository.updateCourse(edited)
        val result = runCatching {
            repository.applyReviewedRefresh(1, pending.before, pending.timingKey,
                mergeReviewedRefresh(pending.before, pending.differences, setOf(0)), pending.afterTerm)
        }
        assertTrue(result.exceptionOrNull() is ScheduleRefreshStaleException)
        assertEquals("核对期间新笔记", db.courseDao().getCourses(1).single().note)
        assertEquals("101", db.courseDao().getCourses(1).single().location)
        assertEquals(pending.beforeTerm.totalWeeks, db.configDao().getConfig(1)!!.totalWeeks)
    }

    @Test fun acceptingTermDoesNotReplaceCoursesOrAppearance() = runBlocking {
        val pending = review()
        val before = db.courseDao().getCourses(1)
        val config = db.configDao().getConfig(1)!!
        db.configDao().upsertConfig(config.copy(cardAlpha = 0.55f))
        repository.applyReviewedRefresh(1, pending.before, pending.timingKey, pending.before, pending.afterTerm)
        assertEquals(before, db.courseDao().getCourses(1))
        assertEquals(18, db.configDao().getConfig(1)!!.totalWeeks)
        assertEquals(0.55f, db.configDao().getConfig(1)!!.cardAlpha)
    }

    @Test fun legacyMissingClockDoesNotProduceAFalseDifference() = runBlocking {
        val legacy = db.courseDao().getCourses(1).single().copy(originalPeriodTimes = null)
        db.courseDao().updateCourse(legacy)
        val state = repository.scheduleSnapshot(1)
        val incoming = captureOriginalPeriodTimes(legacy.copy(id = 0), state.periods)
        val pending = PendingScheduleRefresh.create(AutoRefreshScheduleProfile("test", "学校", "a", "入口", "", "", 1),
            state, listOf(incoming), state.config)
        assertEquals(0, pending.count)
        assertNull(pending.before.single().originalTimes)
    }

    @Test fun rebaseKeepsIncomingDraftAndRechecksCurrentStoredContents() = runBlocking {
        val pending = review()
        repository.updateCourse(db.courseDao().getCourses(1).single().copy(location = "303"))
        val rebased = pending.rebase(repository.scheduleSnapshot(1))
        assertNotEquals(pending.id, rebased.id)
        assertEquals(pending.after, rebased.after)
        assertEquals("303", rebased.differences.single().before!!.location)
        assertEquals("202", rebased.differences.single().after!!.location)
    }
}
