package com.xiaomanjun.sleepdownschedule.feature.schedule

import android.app.Application
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.model.ScheduleAdjustment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleActionsTest {
    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository
    private var refreshes = 0
    private val messages = mutableListOf<String>()

    @Before fun setUp() = runBlocking {
        app = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ScheduleRepository(database)
        repository.ensureDefaults()
    }

    @After fun tearDown() { database.close() }

    @Test fun createSwitchesBeforeRefreshingAndReturningTheCreatedId() = runBlocking {
        var created = -1
        actions().createSchedule("第二课表") {
            assertEquals(1, refreshes)
            created = it
        }.join()
        val snapshot = repository.snapshot()
        assertEquals(created, snapshot.config.id)
        assertEquals(created, snapshot.schedules.single { it.isActive }.id)
    }

    @Test fun creatingWithoutActivationKeepsTheOriginalContext() = runBlocking {
        val original = repository.snapshot().config.id
        var created = -1
        actions().createSchedule("待切换", activate = false) { created = it }.join()
        assertEquals(original, repository.snapshot().config.id)
        assertTrue(repository.snapshot().schedules.any { it.id == created })
        actions().activateSchedule(created) { assertEquals(2, refreshes) }.join()
        assertEquals(created, repository.snapshot().config.id)
    }

    @Test fun deletingTheActiveScheduleRefreshesOnlyAfterAReplacementIsActive() = runBlocking {
        val original = repository.snapshot().config.id
        val other = repository.createSchedule("保留")
        actions().deleteSchedule(original).join()
        assertEquals(other, repository.snapshot().config.id)
        assertEquals(listOf(other), repository.snapshot().schedules.map { it.id })
        assertEquals(1, refreshes)
    }

    @Test fun adjustmentsCommitOnlyTheirFieldBeforeTheSuccessCallback() = runBlocking {
        val original = repository.snapshot().config
        val updated = encodeScheduleAdjustments(listOf(ScheduleAdjustment("2026-10-24", "2026-10-26", "补课")))
        var succeeded = false
        actions().saveScheduleAdjustments(original.id, original.scheduleAdjustmentsJson, updated,
            onSuccess = { assertEquals(1, refreshes); succeeded = true }).join()
        assertTrue(succeeded)
        assertEquals(original.copy(scheduleAdjustmentsJson = updated), repository.snapshot().config)
        assertEquals(listOf("调休安排已保存"), messages)
    }

    @Test fun staleAdjustmentDraftPreservesTheNewerValueAndReturnsFailureWithoutRefresh() = runBlocking {
        val original = repository.snapshot().config
        val newer = encodeScheduleAdjustments(listOf(ScheduleAdjustment("2026-10-24", label = "停课")))
        repository.saveScheduleAdjustments(original.id, original.scheduleAdjustmentsJson, newer)
        var failed = false
        actions().saveScheduleAdjustments(original.id, original.scheduleAdjustmentsJson, "[]",
            onSuccess = { fail("stale draft was committed") }, onFailure = { failed = true }).join()
        assertTrue(failed)
        assertEquals(newer, repository.snapshot().config.scheduleAdjustmentsJson)
        assertEquals(0, refreshes)
        assertEquals(1, messages.size)
    }

    @Test fun cancelledOwnerCannotStartAWriteOrDeliverAnActivityResult() = runBlocking {
        val before = repository.snapshot()
        val owner = CoroutineScope(coroutineContext + Job())
        owner.cancel()
        var callback = false
        owner.actions().createSchedule("不应保存") { callback = true }.join()
        assertEquals(before, repository.snapshot())
        assertFalse(callback)
        assertEquals(0, refreshes)
    }

    private fun CoroutineScope.actions() = ScheduleActions(app, repository, this,
        requestRefresh = { refreshes++ }, onMessage = { messages += it })
}
