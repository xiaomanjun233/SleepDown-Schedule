package com.xiaomanjun.sleepdownschedule.feature.agent

import android.app.Application
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlanExecutionResult
import com.xiaomanjun.sleepdownschedule.domain.schedule.SchedulePeriodSchemesDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasSamePeriodTopology
import com.xiaomanjun.sleepdownschedule.domain.schedule.periodTopologyResizeOperations
import com.xiaomanjun.sleepdownschedule.domain.schedule.resolveSchemeTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.totalPeriodCount
import com.xiaomanjun.sleepdownschedule.domain.schedule.validateResolvedPeriodTimes
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeMode
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.agent.AgentPeriodSettingsPatch
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class DayAgentPreferenceSnapshot(
    val hasDecision: Boolean,
    val enabled: Boolean,
    val weekAssistantEnabled: Boolean,
    val dailyAiEnabled: Boolean,
    val weatherEnabled: Boolean,
    val memoryEnabled: Boolean
)

/**
 * Executes one confirmed settings plan, including its Room write, preferences, verification and undo.
 * The caller owns the scope; neither execution nor undo outlives the original ViewModel session.
 */
internal class AgentSettingPlanExecutor(
    private val app: Application,
    private val repository: ScheduleRepository,
    private val scope: CoroutineScope,
    private val requestRefresh: () -> Unit
) {
    fun execute(
        actions: List<AgentValidatedAction>,
        onResult: (AgentPlanExecutionResult) -> Unit = {}
    ) = scope.launch {
        val settingActions = actions.filter {
            it.type == AgentValidatedActionType.SET_SETTING ||
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
                it.type == AgentValidatedActionType.SET_ADJUSTMENTS
        }
        val courseActions = actions.filter { it.type in setOf(AgentValidatedActionType.ADD,
            AgentValidatedActionType.UPDATE, AgentValidatedActionType.REPLACE, AgentValidatedActionType.DELETE) }
        if (settingActions.size + courseActions.size != actions.size || settingActions.isEmpty()) {
            onResult(
                AgentPlanExecutionResult(false, null, false, "这组操作不是完整的设置计划")
            )
            return@launch
        }
        /*
         * Overlapping keys (for example NOTIFICATION_MODE and REALTIME_ACTIVITY) write the same
         * state. Accepting both made the stored result depend on the order of the JSON array, so a
         * plan that uses two keys of one group is rejected outright instead of silently picking one.
         */
        AgentSettingRegistry
            .conflictingGroup(settingActions.mapNotNull { it.settingKey })
            ?.let { group ->
                onResult(
                    AgentPlanExecutionResult(
                        false, null, false,
                        "同一组设置只能用一个键：${group.joinToString(" / ")}，请只保留一个后重试"
                    )
                )
                return@launch
            }

        val before = repository.snapshot()
        val scheduleId = before.config.id
        if (actions.any { it.sourceScheduleId != null && it.sourceScheduleId != scheduleId }) {
            onResult(AgentPlanExecutionResult(false, null, false, "当前课表已切换，请基于新课表重新生成后续修改"))
            return@launch
        }
        val beforeSchemes = repository.loadPeriodSchemes(scheduleId)
        val beforeName = before.schedules.firstOrNull { it.id == scheduleId }?.name
        val beforeAgentPreferences = captureDayAgentPreferences()
        val beforeIcons = settingActions.mapNotNull { it.settingKey }
            .filter(AgentSettingRegistry::isAppIconSetting).associateWith {
            AgentSettingRegistry.currentAppIconValue(app, it)
        }
        fun restorePreferences() {
            restoreDayAgentPreferences(beforeAgentPreferences)
            beforeIcons.forEach { (key, value) -> AgentSettingRegistry.applyAppIcon(app, key, value) }
        }
        var targetConfig = before.config
        var targetPeriods = before.periods
        var targetName = beforeName

        val periodActions = settingActions.filter {
            AgentSettingRegistry.isPeriodTimeSetting(it.settingKey)
        }
        val structuredPeriodAction = settingActions
            .filter { it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS }
            .singleOrNull()
        if (settingActions.count { it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS } > 1) {
            onResult(AgentPlanExecutionResult(false, null, false, "一次只能提交一份完整节次设置"))
            return@launch
        }
        if (settingActions.count { it.type == AgentValidatedActionType.SET_ADJUSTMENTS } > 1 ||
            structuredPeriodAction != null && periodActions.isNotEmpty()) {
            onResult(AgentPlanExecutionResult(false, null, false, "同一设置只能提交一份完整方案，不能混用两种节次写入方式"))
            return@launch
        }
        if (periodActions.isNotEmpty()) {
            targetPeriods = AgentSettingRegistry.applyPeriodTimes(
                before.periods,
                periodActions.map { it.settingKey to it.settingValue }
            ) ?: run {
                onResult(
                    AgentPlanExecutionResult(
                        false, null, false,
                        "节次时间存在重叠、倒序或无效节次，未写入任何修改"
                    )
                )
                return@launch
            }
        }

        settingActions
            .filter { it.type == AgentValidatedActionType.SET_SETTING }
            .filterNot { AgentSettingRegistry.isPeriodTimeSetting(it.settingKey) }
            .forEach { action ->
                when {
                    action.settingKey == "SCHEDULE_NAME" -> {
                        targetName = action.settingValue
                    }
                    AgentSettingRegistry.isPreferenceSetting(action.settingKey) -> Unit
                    AgentSettingRegistry.isAppIconSetting(action.settingKey) -> Unit
                    else -> {
                        targetConfig = AgentSettingRegistry.apply(
                            targetConfig,
                            action.settingKey,
                            action.settingValue
                        ) ?: run {
                            onResult(
                                AgentPlanExecutionResult(
                                    false, null, false,
                                    "无法应用“${action.summary}”，整组设置均未写入"
                                )
                            )
                            return@launch
                        }
                    }
                }
            }

        settingActions
            .filter { it.type == AgentValidatedActionType.SET_ADJUSTMENTS }
            .forEach { action ->
                targetConfig = targetConfig.copy(
                    scheduleAdjustmentsJson = encodeScheduleAdjustments(
                        action.adjustments.orEmpty()
                    )
                )
            }

        var targetSchemes: SchedulePeriodSchemesDraft? = null
        structuredPeriodAction?.periodSettings?.let { patch ->
            val prepared = prepareAgentPeriodSettings(
                config = targetConfig,
                draft = beforeSchemes,
                patch = patch,
                scheduleId = scheduleId
            ) ?: run {
                onResult(
                    AgentPlanExecutionResult(
                        false, null, false,
                        "节次设置 JSON 与当前作息方案不一致，未写入任何修改"
                    )
                )
                return@launch
            }
            targetConfig = prepared.first
            targetSchemes = prepared.second
            val active = prepared.second.schemes.firstOrNull {
                it.scheme.id == prepared.second.activeSchemeId
            }
            targetPeriods = active?.let { resolveSchemeTimes(prepared.first, it) }
                ?.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) }
                ?: targetPeriods
        }

        var committed: AppState? = null
        var courseResult: AgentPlanExecutionResult? = null
        val writeError = runCatching {
            val saved = repository.commitAgentSettingPlan(before, targetConfig, targetPeriods,
                targetSchemes, targetName, courseActions)
            courseResult = saved.first
            committed = saved.second
            settingActions
                .filter { AgentSettingRegistry.isPreferenceSetting(it.settingKey) }
                .forEach { action ->
                    check(
                        AgentSettingRegistry.applyPreference(
                            app, action.settingKey, action.settingValue
                        )
                    )
                }
            settingActions
                .filter { AgentSettingRegistry.isAppIconSetting(it.settingKey) }
                .forEach { action ->
                    check(AgentSettingRegistry.applyAppIcon(app, action.settingKey, action.settingValue))
                }
        }.exceptionOrNull()
        if (writeError != null) {
            val rollback = runCatching {
                committed?.let { repository.restoreAgentSettingPlan(it, before, beforeSchemes, beforeName) }
                restorePreferences()
            }
            requestRefresh()
            onResult(
                AgentPlanExecutionResult(
                    false, null, false,
                    if (rollback.isSuccess) "设置保存失败，修改已回滚：${writeError.message ?: "未知错误"}"
                    else "设置保存失败，恢复未完成：${rollback.exceptionOrNull()?.message}"
                )
            )
            return@launch
        }

        val actual = repository.snapshot()
        val verified = actual.config == committed?.config && actual.periods == committed?.periods &&
            actual.courses == committed?.courses && settingActions.all { action ->
            when {
                action.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ->
                    actual.config.hasSamePeriodTopology(targetConfig) &&
                        actual.periods.sortedBy { it.periodIndex } ==
                        targetPeriods.sortedBy { it.periodIndex }
                action.type == AgentValidatedActionType.SET_ADJUSTMENTS ->
                    runCatching {
                        decodeScheduleAdjustments(actual.config.scheduleAdjustmentsJson)
                    }.getOrDefault(emptyList()) == action.adjustments.orEmpty().sortedBy { it.date }
                AgentSettingRegistry.isPeriodTimeSetting(action.settingKey) -> {
                    val expected = AgentSettingRegistry.applyPeriodTime(
                        actual.periods, action.settingKey, action.settingValue
                    )
                    expected == actual.periods.sortedBy { it.periodIndex }
                }
                action.settingKey == "SCHEDULE_NAME" ->
                    actual.schedules.firstOrNull { it.id == scheduleId }?.name == action.settingValue
                AgentSettingRegistry.isAppIconSetting(action.settingKey) ->
                    AgentSettingRegistry.currentAppIconValue(app, action.settingKey) == action.settingValue
                AgentSettingRegistry.isPreferenceSetting(action.settingKey) ->
                    AgentSettingRegistry.snapshot(
                        actual.config,
                        actual.schedules.firstOrNull { it.id == scheduleId }?.name,
                        app
                    )[action.settingKey] == action.settingValue?.lowercase()
                else -> AgentSettingRegistry.apply(
                    actual.config, action.settingKey, action.settingValue
                ) == actual.config
            }
        }
        if (!verified) {
            val rollback = runCatching {
                repository.restoreAgentSettingPlan(requireNotNull(committed), before, beforeSchemes, beforeName)
                restorePreferences()
            }
            requestRefresh()
            onResult(
                AgentPlanExecutionResult(
                    false, null, false,
                    if (rollback.isSuccess) "数据库回读与目标不一致，已自动回滚全部修改"
                    else "回读不一致，恢复未完成：${rollback.exceptionOrNull()?.message}"
                )
            )
            return@launch
        }

        requestRefresh()
        onResult(
            AgentPlanExecutionResult(
                success = true,
                preview = courseResult?.preview,
                verified = true,
                message = "已应用并回读验证 ${actions.size} 项修改",
                undo = { undoResult ->
                    scope.launch {
                        val restored = runCatching {
                            repository.restoreAgentSettingPlan(requireNotNull(committed), before, beforeSchemes, beforeName)
                            restorePreferences()
                            requestRefresh()
                        }.isSuccess
                        undoResult(
                            AgentPlanExecutionResult(
                                restored, null, restored,
                                if (restored) "已撤销本次设置修改" else "撤销失败"
                            )
                        )
                    }
                }
            )
        )
    }

    private fun prepareAgentPeriodSettings(
        config: ScheduleConfigEntity,
        draft: SchedulePeriodSchemesDraft,
        patch: AgentPeriodSettingsPatch,
        scheduleId: Int
    ): Pair<ScheduleConfigEntity, SchedulePeriodSchemesDraft>? {
        val targetConfig = config.copy(
            morningPeriodCount = patch.morningPeriodCount ?: config.morningPeriodCount,
            noonPeriodCount = patch.noonPeriodCount ?: config.noonPeriodCount,
            afternoonPeriodCount = patch.afternoonPeriodCount ?: config.afternoonPeriodCount,
            eveningPeriodCount = patch.eveningPeriodCount ?: config.eveningPeriodCount,
            classDurationMinutes = patch.classDurationMinutes ?: config.classDurationMinutes,
            breakDurationMinutes = patch.breakDurationMinutes ?: config.breakDurationMinutes
        )
        val total = targetConfig.totalPeriodCount()
        if (total !in 1..30) return null
        val activeIndex = draft.schemes.indexOfFirst { it.scheme.id == draft.activeSchemeId }
        if (activeIndex < 0) return null
        val explicitTimes = patch.periods?.sortedBy { it.periodIndex }?.map {
            PeriodSchemeTimeEntity(
                schemeId = draft.activeSchemeId,
                periodIndex = it.periodIndex,
                startTime = it.startTime,
                endTime = it.endTime
            )
        }
        if (explicitTimes != null &&
            (explicitTimes.size != total || validateResolvedPeriodTimes(explicitTimes) != null)
        ) return null

        val updatedSchemes = draft.schemes.mapIndexed { index, item ->
            val isActive = index == activeIndex
            val requestedMode = patch.mode?.let {
                runCatching { PeriodSchemeMode.valueOf(it) }.getOrNull()
            }
            val mode = when {
                isActive && requestedMode != null -> requestedMode
                isActive && explicitTimes != null -> PeriodSchemeMode.MANUAL
                else -> item.scheme.mode
            }
            val entity = if (isActive) item.scheme.copy(
                scheduleId = scheduleId,
                name = patch.schemeName?.trim()?.takeIf(String::isNotBlank)
                    ?: item.scheme.name,
                mode = mode,
                classDurationMinutes = patch.classDurationMinutes
                    ?: item.scheme.classDurationMinutes,
                breakDurationMinutes = patch.breakDurationMinutes
                    ?: item.scheme.breakDurationMinutes,
                morningStartTime = patch.morningStartTime ?: item.scheme.morningStartTime,
                noonStartTime = patch.noonStartTime ?: item.scheme.noonStartTime,
                afternoonStartTime = patch.afternoonStartTime ?: item.scheme.afternoonStartTime,
                eveningStartTime = patch.eveningStartTime ?: item.scheme.eveningStartTime
            ) else item.scheme
            val times = when {
                isActive && explicitTimes != null ->
                    explicitTimes.map { it.copy(schemeId = entity.id) }
                entity.mode == PeriodSchemeMode.AUTO_MATCH ->
                    resolveSchemeTimes(
                        targetConfig,
                        item.copy(scheme = entity)
                    )
                else -> resizeManualPeriodTimes(
                    item.times,
                    total,
                    entity.id,
                    entity.classDurationMinutes,
                    entity.breakDurationMinutes
                )
            }
            val specialBreaks = if (isActive && patch.specialBreaks != null) {
                patch.specialBreaks.mapNotNull { (key, value) ->
                    key.toIntOrNull()?.let { it to value }
                }.toMap()
            } else item.specialBreaks
            val overrides = if (isActive && patch.overriddenPeriods != null) {
                patch.overriddenPeriods.toSet()
            } else item.overriddenPeriods
            item.copy(
                scheme = entity,
                times = times,
                specialBreaks = specialBreaks,
                overriddenPeriods = overrides
            )
        }
        val topologyOperations = periodTopologyResizeOperations(config, targetConfig,
            automaticSections = explicitTimes == null && updatedSchemes[activeIndex].scheme.mode == PeriodSchemeMode.AUTO_MATCH)
        val targetDraft = draft.copy(
            schemes = updatedSchemes,
            topologyOperations = draft.topologyOperations + topologyOperations
        )
        val active = targetDraft.schemes[activeIndex]
        if (validateResolvedPeriodTimes(resolveSchemeTimes(targetConfig, active)) != null) return null
        return targetConfig to targetDraft
    }

    private fun resizeManualPeriodTimes(
        source: List<PeriodSchemeTimeEntity>,
        count: Int,
        schemeId: Long,
        durationMinutes: Int,
        breakMinutes: Int
    ): List<PeriodSchemeTimeEntity> {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val result = source.sortedBy { it.periodIndex }
            .filter { it.periodIndex in 1..count }
            .toMutableList()
        while (result.size < count) {
            val index = result.size + 1
            val start = result.lastOrNull()?.endTime
                ?.let { runCatching { LocalTime.parse(it).plusMinutes(breakMinutes.toLong()) }.getOrNull() }
                ?: LocalTime.of(8, 0)
            val end = start.plusMinutes(durationMinutes.toLong())
            result += PeriodSchemeTimeEntity(
                schemeId = schemeId,
                periodIndex = index,
                startTime = start.format(formatter),
                endTime = end.format(formatter)
            )
        }
        return result.map { it.copy(schemeId = schemeId) }
    }

    private fun captureDayAgentPreferences(): DayAgentPreferenceSnapshot =
        DayAgentPreferenceSnapshot(
            hasDecision = DayAgentPreferences.hasDecision(app),
            enabled = DayAgentPreferences.isEnabled(app),
            weekAssistantEnabled = DayAgentPreferences.isWeekAssistantEnabled(app),
            dailyAiEnabled = DayAgentPreferences.isDailyAiEnabled(app),
            weatherEnabled = DayAgentPreferences.isWeatherEnabled(app),
            memoryEnabled = DayAgentPreferences.isMemoryEnabled(app)
        )

    private fun restoreDayAgentPreferences(snapshot: DayAgentPreferenceSnapshot) {
        DayAgentPreferences.setEnabled(
            app,
            enabled = snapshot.enabled,
            markDecided = snapshot.hasDecision
        )
        DayAgentPreferences.setWeekAssistantEnabled(app, snapshot.weekAssistantEnabled)
        DayAgentPreferences.saveOptions(
            app,
            dailyAiEnabled = snapshot.dailyAiEnabled,
            weatherEnabled = snapshot.weatherEnabled
        )
        DayAgentPreferences.setMemoryEnabled(app, snapshot.memoryEnabled)
    }

}
