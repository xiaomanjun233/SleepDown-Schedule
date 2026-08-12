package com.xiaomanjun.sleepdownschedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class DayAgentPreferenceSnapshot(
    val hasDecision: Boolean,
    val enabled: Boolean,
    val dailyAiEnabled: Boolean,
    val weatherEnabled: Boolean,
    val memoryEnabled: Boolean
)

class ScheduleViewModel(
    private val app: Application,
    private val repository: ScheduleRepository
) : AndroidViewModel(app) {
    val state: StateFlow<AppState> = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppState()
    )
    val allSchedulesState: StateFlow<AppState> = repository.allSchedulesState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppState()
    )
    val themeConfig: StateFlow<ScheduleConfigEntity> = state
        .map { it.config }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultConfig())
    val snackbar = MutableStateFlow<String?>(null)
    // Each schedule keeps one complete pending personalization snapshot. The signal is conflated,
    // while the map preserves the latest snapshot for every schedule, so rapid edits never replay
    // obsolete states or drop another field changed between two database writes.
    private val personalizationSaveSignal = Channel<Unit>(Channel.CONFLATED)
    private val personalizationSaveLock = Any()
    private val pendingPersonalizationSnapshots = linkedMapOf<Int, ScheduleConfigEntity>()
    private val refreshCoordinator = ScheduleRefreshCoordinator(
        scope = viewModelScope,
        refresh = ::refreshScheduleSurfaces,
        onFailure = { error ->
            Log.w("ScheduleViewModel", "Schedule surface refresh failed", error)
            snackbar.value = error.message ?: "系统课表界面刷新失败，请稍后重试"
        }
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            refreshCoordinator.refreshNow()
        }
        viewModelScope.launch {
            for (ignored in personalizationSaveSignal) {
                while (true) {
                    val batch = synchronized(personalizationSaveLock) {
                        if (pendingPersonalizationSnapshots.isEmpty()) {
                            emptyList()
                        } else {
                            pendingPersonalizationSnapshots.values.toList().also {
                                pendingPersonalizationSnapshots.clear()
                            }
                        }
                    }
                    if (batch.isEmpty()) break
                    batch.forEach { snapshot ->
                        try {
                            if (repository.savePersonalizationSnapshot(snapshot)) {
                                cleanupScheduleWallpaperFiles()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Log.w("ScheduleViewModel", "Personalization save failed", error)
                            snackbar.value = error.message ?: "个性化设置保存失败，请重试"
                        }
                    }
                }
            }
        }
    }

    fun addCourse(course: CourseEntity) = launchCourseMutation {
        repository.addCourse(course)
    }

    fun addCourses(courses: List<CourseEntity>) = launchCourseMutation {
        repository.addCourses(courses)
    }

    fun updateCourse(course: CourseEntity) = launchCourseMutation("课程已更新") {
        repository.updateCourse(course)
    }

    fun replaceCourseGroup(originals: List<CourseEntity>, replacements: List<CourseEntity>) =
        launchCourseMutation("课程已更新") {
            repository.replaceCourseGroup(originals, replacements)
        }

    fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) =
        launchCourseMutation {
        repository.updateCourseSingleWeek(original, edited, targetWeek)
    }

    fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) =
        launchCourseMutation("课程已更新") {
        repository.updateRelatedCourses(original, edited)
    }

    fun deleteCourse(course: CourseEntity) = launchCourseMutation("课程已删除") {
        repository.deleteCourse(course)
    }

    fun deleteCourses(courses: List<CourseEntity>) = launchCourseMutation("课程已删除") {
        repository.deleteCourses(courses)
    }

    fun deleteCoursesSingleWeek(courses: List<CourseEntity>, targetWeek: Int) =
        launchCourseMutation("课程已删除") {
            repository.deleteCoursesSingleWeek(courses, targetWeek)
        }

    fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) =
        launchCourseMutation("课程已删除") {
        repository.deleteCourseSingleWeek(course, targetWeek)
    }

    private fun launchCourseMutation(
        successMessage: String? = null,
        mutation: suspend () -> Unit
    ) = viewModelScope.launch {
        try {
            mutation()
            refreshCoordinator.request()
            successMessage?.let { snackbar.value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("ScheduleViewModel", "Course mutation rejected", error)
            snackbar.value = error.message ?: "课程操作失败，请重试"
        }
    }

    fun executeAgentPlan(
        actions: List<AgentValidatedAction>,
        onResult: (AgentPlanExecutionResult) -> Unit = {}
    ) = viewModelScope.launch {
        val result = repository.executeAgentPlan(AgentPlan(actions))
        if (result.success) refreshCoordinator.request()
        snackbar.value = result.message
        onResult(result)
    }

    fun executeAgentSettingPlan(
        actions: List<AgentValidatedAction>,
        onResult: (AgentPlanExecutionResult) -> Unit = {}
    ) = viewModelScope.launch {
        val settingActions = actions.filter {
            it.type == AgentValidatedActionType.SET_SETTING ||
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS
        }
        if (settingActions.size != actions.size || settingActions.isEmpty()) {
            onResult(
                AgentPlanExecutionResult(false, null, false, "这组操作不是完整的设置计划")
            )
            return@launch
        }

        val before = repository.snapshot()
        val scheduleId = before.config.id
        val beforeSchemes = repository.loadPeriodSchemes(scheduleId)
        val beforeName = before.schedules.firstOrNull { it.id == scheduleId }?.name
        val beforeAgentPreferences = captureDayAgentPreferences()
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

        val writeError = runCatching {
            targetSchemes?.let { repository.saveScheduleDetail(targetConfig, it) }
                ?: repository.saveConfigForSchedule(scheduleId, targetConfig, targetPeriods)
            targetName?.let { repository.renameSchedule(scheduleId, it) }
            settingActions
                .filter { AgentSettingRegistry.isPreferenceSetting(it.settingKey) }
                .forEach { action ->
                    check(
                        AgentSettingRegistry.applyPreference(
                            app, action.settingKey, action.settingValue
                        )
                    )
                }
        }.exceptionOrNull()
        if (writeError != null) {
            runCatching {
                repository.saveScheduleDetail(before.config, beforeSchemes)
                beforeName?.let { repository.renameSchedule(scheduleId, it) }
                restoreDayAgentPreferences(beforeAgentPreferences)
            }
            refreshCoordinator.request()
            onResult(
                AgentPlanExecutionResult(
                    false, null, false,
                    "设置保存失败，已恢复修改前状态：${writeError.message ?: "未知错误"}"
                )
            )
            return@launch
        }

        val actual = repository.snapshot()
        val verified = settingActions.all { action ->
            when {
                action.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ->
                    actual.config.hasSamePeriodTopology(targetConfig) &&
                        actual.periods.sortedBy { it.periodIndex } ==
                        targetPeriods.sortedBy { it.periodIndex }
                AgentSettingRegistry.isPeriodTimeSetting(action.settingKey) -> {
                    val expected = AgentSettingRegistry.applyPeriodTime(
                        actual.periods, action.settingKey, action.settingValue
                    )
                    expected == actual.periods.sortedBy { it.periodIndex }
                }
                action.settingKey == "SCHEDULE_NAME" ->
                    actual.schedules.firstOrNull { it.id == scheduleId }?.name == action.settingValue
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
            repository.saveScheduleDetail(before.config, beforeSchemes)
            beforeName?.let { repository.renameSchedule(scheduleId, it) }
            restoreDayAgentPreferences(beforeAgentPreferences)
            refreshCoordinator.request()
            onResult(
                AgentPlanExecutionResult(
                    false, null, false,
                    "数据库回读与目标不一致，已自动回滚全部修改"
                )
            )
            return@launch
        }

        refreshCoordinator.request()
        onResult(
            AgentPlanExecutionResult(
                success = true,
                preview = null,
                verified = true,
                message = "已应用并回读验证 ${settingActions.size} 项设置",
                undo = { undoResult ->
                    viewModelScope.launch {
                        val restored = runCatching {
                            repository.saveScheduleDetail(before.config, beforeSchemes)
                            beforeName?.let { repository.renameSchedule(scheduleId, it) }
                            restoreDayAgentPreferences(beforeAgentPreferences)
                            refreshCoordinator.request()
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
        val topologyChanged = !config.hasSamePeriodTopology(targetConfig)
        val targetDraft = draft.copy(
            schemes = updatedSchemes,
            topologyOperations = if (topologyChanged) {
                listOf(PeriodTopologyOperation.AddAfter(config.totalPeriodCount()))
            } else {
                draft.topologyOperations
            }
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

    fun importDraft(
        draft: ImportDraft,
        createNewSchedule: Boolean = false,
        onDone: (Int) -> Unit
    ) = viewModelScope.launch {
        val scheduleId = repository.importDraft(draft, createNewSchedule)
        refreshCoordinator.request()
        snackbar.value = if (createNewSchedule) "已导入到新课表" else "课程表已导入"
        onDone(scheduleId)
    }

    fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) = viewModelScope.launch {
        repository.saveConfig(config, periods)
        refreshCoordinator.request()
    }

    fun saveConfigForSchedule(
        scheduleId: Int,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        finish: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.saveConfigForSchedule(scheduleId, config, periods)
        refreshCoordinator.request()
        finish?.invoke()
    }

    fun savePersonalization(config: ScheduleConfigEntity) {
        synchronized(personalizationSaveLock) {
            pendingPersonalizationSnapshots[config.id] = config
        }
        personalizationSaveSignal.trySend(Unit)
    }

    fun savePersonalizationAndFinish(config: ScheduleConfigEntity, finish: () -> Unit) =
        viewModelScope.launch {
            try {
                if (repository.savePersonalizationSnapshot(config)) {
                    cleanupScheduleWallpaperFiles()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w("ScheduleViewModel", "Personalization exit save failed", error)
                snackbar.value = error.message ?: "个性化设置保存失败，请重试"
            }
            finish()
        }

    fun saveGeneralSettings(config: ScheduleConfigEntity) {
        (app as CourseScheduleApp).enqueueGeneralSettingsSave(config)
    }

    fun saveHomeChromeBlurScale(value: Float) {
        (app as CourseScheduleApp).enqueueHomeChromeBlurScaleSave(value)
    }

    fun saveNotificationSettings(config: ScheduleConfigEntity) {
        (app as CourseScheduleApp).enqueueNotificationSettingsSave(config)
    }

    fun saveGlobalSettingsAndFinish(
        config: ScheduleConfigEntity,
        onFinished: (Boolean) -> Unit
    ) =
        viewModelScope.launch {
            try {
                repository.saveGlobalSettings(config)
                refreshCoordinator.request()
                onFinished(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w("ScheduleViewModel", "Global settings exit save failed", error)
                snackbar.value = error.message ?: "设置保存失败，请重试"
                onFinished(false)
            }
        }

    fun createSchedule(
        name: String = "\u65B0\u8BFE\u8868",
        activate: Boolean = true,
        onCreated: ((Int) -> Unit)? = null
    ) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.createSchedule name=$name")
        val scheduleId = repository.createSchedule(name)
        Log.d("ScheduleManager", "repository.createSchedule created id=$scheduleId")
        if (activate) repository.activateSchedule(scheduleId)
        refreshCoordinator.request()
        onCreated?.invoke(scheduleId)
    }

    fun activateSchedule(scheduleId: Int, finish: (() -> Unit)? = null) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.activateSchedule id=$scheduleId")
        repository.activateSchedule(scheduleId)
        refreshCoordinator.request()
        finish?.invoke()
    }

    fun renameSchedule(scheduleId: Int, name: String) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.renameSchedule id=$scheduleId name=$name")
        repository.renameSchedule(scheduleId, name)
    }

    fun deleteSchedule(scheduleId: Int) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.deleteSchedule id=$scheduleId")
        repository.deleteSchedule(scheduleId)
        cleanupScheduleWallpaperFiles()
        refreshCoordinator.request()
    }

    fun clearSnackbar() {
        snackbar.value = null
    }

    fun previewLiveUpdate() = viewModelScope.launch {
        val snapshot = repository.activeSnapshot()
        NotificationScheduler.showLiveUpdatePreview(app, snapshot.config)
        val minutes = snapshot.config.notificationLeadMinutes.coerceIn(1, 30)
        snackbar.value = "已启动测试实时活动（${minutes}分钟倒计时）"
    }

    fun refreshNotifications() {
        refreshCoordinator.request()
    }

    private fun captureDayAgentPreferences(): DayAgentPreferenceSnapshot =
        DayAgentPreferenceSnapshot(
            hasDecision = DayAgentPreferences.hasDecision(app),
            enabled = DayAgentPreferences.isEnabled(app),
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
        DayAgentPreferences.saveOptions(
            app,
            dailyAiEnabled = snapshot.dailyAiEnabled,
            weatherEnabled = snapshot.weatherEnabled
        )
        DayAgentPreferences.setMemoryEnabled(app, snapshot.memoryEnabled)
    }

    private suspend fun refreshScheduleSurfaces() = withContext(Dispatchers.IO) {
        val snapshot = repository.activeSnapshot()
        NotificationScheduler.refreshToday(app, snapshot.courses, snapshot.config, snapshot.periods)
        TodayCoursesWidgetProvider.refreshAll(app)
    }

    private suspend fun cleanupScheduleWallpaperFiles() = withContext(Dispatchers.IO) {
        cleanupUnreferencedScheduleWallpapers(app, repository.referencedWallpaperUris())
    }
}

class ScheduleViewModelFactory(
    private val app: Application,
    private val repository: ScheduleRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ScheduleViewModel(app, repository) as T
}
