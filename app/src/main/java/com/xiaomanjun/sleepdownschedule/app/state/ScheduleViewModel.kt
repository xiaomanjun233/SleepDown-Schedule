package com.xiaomanjun.sleepdownschedule.app.state

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlan
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlanExecutionResult
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentSettingPlanExecutor
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.feature.schedule.*
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val mutableSnackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = mutableSnackbar.asStateFlow()
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
            mutableSnackbar.value = error.message ?: "系统课表界面刷新失败，请稍后重试"
        }
    )

    private val agentSettingPlans = AgentSettingPlanExecutor(
        app = app,
        repository = repository,
        scope = viewModelScope,
        requestRefresh = refreshCoordinator::request
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
                            mutableSnackbar.value = error.message ?: "个性化设置保存失败，请重试"
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

    fun copyCourses(courses: List<CourseEntity>, onResult: (Boolean) -> Unit) =
        launchCourseMutation(onSuccess = { onResult(true) }, onFailure = { onResult(false) }) {
            repository.copyCourses(courses)
        }

    fun updateCourse(course: CourseEntity) = launchCourseMutation("课程已更新") {
        repository.updateCourse(course)
    }

    fun replaceCourseGroup(originals: List<CourseEntity>, replacements: List<CourseEntity>) =
        launchCourseMutation("课程已更新") {
            repository.replaceCourseGroup(originals, replacements)
        }

    fun replaceCourseGroupAndThen(
        originals: List<CourseEntity>,
        replacements: List<CourseEntity>,
        onSuccess: () -> Unit
    ) = launchCourseMutation("课程已更新", onSuccess) {
        repository.replaceCourseGroup(originals, replacements)
    }

    fun saveManagedCourseGroup(
        originals: List<CourseEntity>,
        replacements: List<CourseEntity>,
        onFinished: (Boolean) -> Unit
    ) = launchCourseMutation(
        successMessage = if (replacements.isEmpty()) "课程已删除" else "课程已更新",
        onSuccess = { onFinished(true) }, onFailure = { onFinished(false) }
    ) {
        if (replacements.isEmpty()) repository.deleteCourses(originals)
        else repository.replaceCourseGroup(originals, replacements)
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

    fun deleteCourses(courses: List<CourseEntity>, onFailure: (() -> Unit)? = null) =
        launchCourseMutation("课程已删除", onFailure = onFailure) {
        repository.deleteCourses(courses)
    }

    fun deleteCoursesAndThen(courses: List<CourseEntity>, onSuccess: () -> Unit) =
        launchCourseMutation("课程已删除", onSuccess) {
            repository.deleteCourses(courses)
        }

    fun deleteCoursesSingleWeek(courses: List<CourseEntity>, targetWeek: Int, onFailure: (() -> Unit)? = null) =
        launchCourseMutation("课程已删除", onFailure = onFailure) {
            repository.deleteCoursesSingleWeek(courses, targetWeek)
        }

    fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) =
        launchCourseMutation("课程已删除") {
        repository.deleteCourseSingleWeek(course, targetWeek)
    }

    private fun launchCourseMutation(
        successMessage: String? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null,
        mutation: suspend () -> Unit
    ) = viewModelScope.launch {
        try {
            mutation()
            refreshCoordinator.request()
            successMessage?.let { mutableSnackbar.value = it }
            onSuccess?.invoke()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("ScheduleViewModel", "Course mutation rejected", error)
            mutableSnackbar.value = error.message ?: "课程操作失败，请重试"
            onFailure?.invoke()
        }
    }

    fun executeAgentPlan(
        actions: List<AgentValidatedAction>,
        onResult: (AgentPlanExecutionResult) -> Unit = {}
    ) = viewModelScope.launch {
        val result = repository.executeAgentPlan(AgentPlan(actions))
        if (result.success) refreshCoordinator.request()
        mutableSnackbar.value = result.message
        onResult(result)
    }

    fun executeAgentSettingPlan(
        actions: List<AgentValidatedAction>,
        onResult: (AgentPlanExecutionResult) -> Unit = {}
    ) = agentSettingPlans.execute(actions, onResult)

    fun importDraft(
        draft: ImportDraft,
        createNewSchedule: Boolean = false,
        onFailure: ((Throwable) -> Unit)? = null,
        onDone: (Int) -> Unit
    ) = viewModelScope.launch {
        try {
            val scheduleId = repository.importDraft(draft, createNewSchedule)
            refreshCoordinator.request()
            mutableSnackbar.value = if (createNewSchedule) "已导入到新课表" else "课程表已导入"
            onDone(scheduleId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableSnackbar.value = error.message ?: "保存课表失败，请重试"
            onFailure?.invoke(error)
        }
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
                mutableSnackbar.value = error.message ?: "个性化设置保存失败，请重试"
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
                mutableSnackbar.value = error.message ?: "设置保存失败，请重试"
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

    /**
     * Agent-facing schedule actions. These run outside the course transaction because they change
     * the global active context, so the result reports the action itself rather than a course diff.
     * Creating a schedule deliberately does not switch to it; an unrequested context switch would
     * make every later fact read belong to a different schedule than the user was looking at.
     */
    fun openAgentPage(onResult: (AgentPlanExecutionResult) -> Unit, open: (AppState) -> Unit) = viewModelScope.launch {
        val error = runCatching { open(repository.snapshot()) }.exceptionOrNull()
        onResult(AgentPlanExecutionResult(error == null, null, error == null,
            if (error == null) "页面已打开" else "打开页面失败：${error.message ?: "请重试"}"))
    }

    fun createScheduleFromAgent(name: String, onResult: (AgentPlanExecutionResult) -> Unit) =
        viewModelScope.launch {
            val error = runCatching {
                val createdId = repository.createSchedule(name)
                check(repository.snapshot().schedules.any { it.id == createdId && it.name == name.trim().take(30) }) {
                    "创建后的课表未通过回读校验"
                }
                refreshCoordinator.request()
            }.exceptionOrNull()
            onResult(
                AgentPlanExecutionResult(
                    success = error == null,
                    preview = null,
                    verified = error == null,
                    message = if (error == null) {
                        "已创建课表「$name」，当前仍在使用原课表；需要切换请再确认"
                    } else {
                        "创建课表失败：${error.message ?: "未知错误"}"
                    }
                )
            )
        }

    fun activateScheduleFromAgent(scheduleId: Int, onResult: (AgentPlanExecutionResult) -> Unit) =
        viewModelScope.launch {
            val error = runCatching {
                repository.activateSchedule(scheduleId)
                check(repository.snapshot().config.id == scheduleId) { "切换后的课表未通过回读校验" }
                refreshCoordinator.request()
            }.exceptionOrNull()
            onResult(
                AgentPlanExecutionResult(
                    success = error == null,
                    preview = null,
                    verified = error == null,
                    message = if (error == null) "已切换到目标课表" else "切换课表失败：${error.message ?: "目标课表不存在"}"
                )
            )
        }

    fun deleteScheduleFromAgent(scheduleId: Int, onResult: (AgentPlanExecutionResult) -> Unit) =
        viewModelScope.launch {
            if (repository.snapshot().schedules.size <= 1) {
                onResult(
                    AgentPlanExecutionResult(
                        success = false,
                        preview = null,
                        verified = false,
                        message = "当前仅剩一个课表，无法删除；已为你保留使用中的课表"
                    )
                )
                return@launch
            }
            val error = runCatching {
                repository.deleteSchedule(scheduleId)
                check(repository.snapshot().schedules.none { it.id == scheduleId }) { "删除后的课表未通过回读校验" }
                cleanupScheduleWallpaperFiles()
                refreshCoordinator.request()
            }.exceptionOrNull()
            onResult(
                AgentPlanExecutionResult(
                    success = error == null,
                    preview = null,
                    verified = error == null,
                    message = if (error == null) "已删除课表" else "删除课表失败：${error.message ?: "未知错误"}"
                )
            )
        }

    fun saveScheduleAdjustments(scheduleId: Int, original: String, updated: String,
        onSuccess: (() -> Unit)? = null, onFailure: (() -> Unit)? = null) =
        launchCourseMutation("调休安排已保存", onSuccess = onSuccess, onFailure = onFailure) {
            repository.saveScheduleAdjustments(scheduleId, original, updated)
        }

    fun clearSnackbar() {
        mutableSnackbar.value = null
    }

    /** Previews the draft settings so the result proves the current switches, not the saved ones. */
    fun previewLiveUpdate(config: ScheduleConfigEntity) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            NotificationScheduler.showLiveUpdatePreview(app, config)
        }
        val minutes = config.notificationLeadMinutes.coerceIn(1, 30)
        mutableSnackbar.value = when (result) {
            NotificationScheduler.LiveUpdatePreviewResult.POSTED ->
                "已启动测试实时活动（${minutes}分钟倒计时）"
            NotificationScheduler.LiveUpdatePreviewResult.NOTIFICATIONS_UNAVAILABLE ->
                "测试通知未发送，请检查应用通知权限和通知渠道"
            NotificationScheduler.LiveUpdatePreviewResult.VENDOR_HANDLES_PREVIEW ->
                "当前通知由厂商课程组件处理，请使用流体云测试入口"
            NotificationScheduler.LiveUpdatePreviewResult.DELIVERY_FAILED ->
                "测试通知发送失败，请稍后重试"
            NotificationScheduler.LiveUpdatePreviewResult.ISLAND_PRIVILEGE_REQUIRED ->
                "请先为超级岛授权 Shizuku 或 Root"
            NotificationScheduler.LiveUpdatePreviewResult.ISLAND_FOCUS_REQUIRED ->
                "请在系统通知设置中允许本应用的焦点通知／超级岛"
        }
    }

    fun refreshNotifications() {
        refreshCoordinator.request()
    }

    fun refreshNotificationsAfterSave() {
        // A settings Activity may finish as soon as its save callback returns. Keep the
        // refresh alive long enough to observe the transaction that just completed.
        (app as CourseScheduleApp).applicationScope.launch {
            refreshCoordinator.refreshNow()
        }
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
