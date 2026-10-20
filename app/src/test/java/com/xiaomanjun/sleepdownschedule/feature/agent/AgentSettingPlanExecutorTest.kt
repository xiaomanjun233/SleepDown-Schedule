package com.xiaomanjun.sleepdownschedule.feature.agent

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlanExecutionResult
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class AgentSettingPlanExecutorTest {
    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository
    private var refreshes = 0

    @Before
    fun setUp() = runBlocking {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("day_agent_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ScheduleRepository(database)
        repository.ensureDefaults()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmedSettingIsReadBackAndUndoRestoresThePreviousState() = runBlocking {
        val before = repository.snapshot()
        val result = execute(setting("TOTAL_WEEKS", "24"))

        assertTrue(result.success && result.verified)
        assertEquals(24, repository.snapshot().config.totalWeeks)
        assertEquals(1, refreshes)
        assertTrue(undo(result).success)
        assertEquals(before.config, repository.snapshot().config)
        assertEquals(before.periods, repository.snapshot().periods)
        assertEquals(2, refreshes)
    }

    @Test
    fun conflictingKeysAreRejectedBeforeAnyWriteOrRefresh() = runBlocking {
        val before = repository.snapshot()
        val result = execute(setting("NOTIFICATION_MODE", "STANDARD"), setting("REALTIME_ACTIVITY", "true"))

        assertFalse(result.success)
        assertNull(result.undo)
        assertEquals(before, repository.snapshot())
        assertEquals(0, refreshes)
    }

    @Test
    fun planFromAnotherScheduleCannotModifyTheActiveSchedule() = runBlocking {
        val before = repository.snapshot()
        val result = execute(setting("TOTAL_WEEKS", "24").copy(sourceScheduleId = before.config.id + 1))

        assertFalse(result.success)
        assertEquals(before, repository.snapshot())
        assertEquals(0, refreshes)
    }

    @Test
    fun preferenceWriteFailureRollsBackTheDatabaseAndEarlierPreferences() = runBlocking {
        val before = repository.snapshot()
        val result = execute(setting("TOTAL_WEEKS", "24"), setting("DAY_AGENT_ENABLED", "true"),
            setting("DAY_AGENT_MEMORY_ENABLED", "invalid"))

        assertFalse(result.success)
        assertEquals(before.config, repository.snapshot().config)
        assertEquals(before.periods, repository.snapshot().periods)
        assertFalse(DayAgentPreferences.isEnabled(app))
        assertFalse(DayAgentPreferences.hasDecision(app))
    }

    @Test
    fun undoCannotOverwriteASettingChangedAfterThePlan() = runBlocking {
        val result = execute(setting("TOTAL_WEEKS", "24"))
        val after = repository.snapshot()
        repository.saveConfig(after.config.copy(totalWeeks = 30), after.periods)

        assertFalse(undo(result).success)
        assertEquals(30, repository.snapshot().config.totalWeeks)
    }

    @Test
    fun preferenceConsentAndOptionsAreRestoredOnUndo() = runBlocking {
        val result = execute(setting("DAY_AGENT_ENABLED", "true"), setting("DAY_AGENT_WEATHER", "false"),
            setting("DAY_AGENT_MEMORY_ENABLED", "true"))

        assertTrue(result.success)
        assertTrue(DayAgentPreferences.hasDecision(app))
        assertTrue(DayAgentPreferences.isEnabled(app))
        assertFalse(DayAgentPreferences.isWeatherEnabled(app))
        assertTrue(DayAgentPreferences.isMemoryEnabled(app))
        assertTrue(undo(result).success)
        assertFalse(DayAgentPreferences.hasDecision(app))
        assertFalse(DayAgentPreferences.isEnabled(app))
        assertTrue(DayAgentPreferences.isWeatherEnabled(app))
        assertFalse(DayAgentPreferences.isMemoryEnabled(app))
    }

    @Test
    fun weekAssistantPreferenceIsRestoredOnUndo() = runBlocking {
        assertTrue(DayAgentPreferences.isWeekAssistantEnabled(app))
        val result = execute(setting("DAY_AGENT_WEEK_ENABLED", "false"))

        assertTrue(result.success)
        assertFalse(DayAgentPreferences.isWeekAssistantEnabled(app))
        assertTrue(undo(result).success)
        assertTrue(DayAgentPreferences.isWeekAssistantEnabled(app))
    }

    @Test
    fun laterPreferenceFailureRestoresTheWeekAssistantPreference() = runBlocking {
        val result = execute(setting("DAY_AGENT_WEEK_ENABLED", "false"),
            setting("DAY_AGENT_MEMORY_ENABLED", "invalid"))

        assertFalse(result.success)
        assertTrue(DayAgentPreferences.isWeekAssistantEnabled(app))
    }

    @Test
    fun newerWeekPreferenceRejectsUndoBeforeTheDatabaseCanBeRestored() = runBlocking {
        val result = execute(setting("TOTAL_WEEKS", "24"))
        DayAgentPreferences.setWeekAssistantEnabled(app, false)

        val undone = undo(result)

        assertFalse(undone.success)
        assertTrue(undone.message.contains("已有后续修改"))
        assertEquals(24, repository.snapshot().config.totalWeeks)
        assertFalse(DayAgentPreferences.isWeekAssistantEnabled(app))
    }

    @Test
    fun newerConsentDecisionIsNotOverwrittenByUndo() = runBlocking {
        val result = execute(setting("DAY_AGENT_ENABLED", "true"))
        DayAgentPreferences.setEnabled(app, false)

        assertFalse(undo(result).success)
        assertFalse(DayAgentPreferences.isEnabled(app))
        assertTrue(DayAgentPreferences.hasDecision(app))
    }

    private suspend fun CoroutineScope.execute(vararg actions: AgentValidatedAction): AgentPlanExecutionResult {
        var result: AgentPlanExecutionResult? = null
        var callbacks = 0
        AgentSettingPlanExecutor(app, repository, this) { refreshes++ }
            .execute(actions.toList()) { callbacks++; result = it }.join()
        assertEquals(1, callbacks)
        return requireNotNull(result)
    }

    private suspend fun undo(result: AgentPlanExecutionResult): AgentPlanExecutionResult = withTimeout(5_000) {
        val undone = CompletableDeferred<AgentPlanExecutionResult>()
        requireNotNull(result.undo).invoke { undone.complete(it) }
        undone.await()
    }

    private fun setting(key: String, value: String) = AgentValidatedAction(
        type = AgentValidatedActionType.SET_SETTING, settingKey = key, settingValue = value, summary = key)
}
