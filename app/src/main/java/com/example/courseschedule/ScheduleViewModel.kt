package com.example.courseschedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val snackbar = MutableStateFlow<String?>(null)
    private val refreshCoordinator = ScheduleRefreshCoordinator(
        scope = viewModelScope,
        refresh = ::refreshScheduleSurfaces
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            refreshCoordinator.refreshNow()
        }
    }

    fun addCourse(course: CourseEntity) = viewModelScope.launch {
        repository.addCourse(course)
        refreshCoordinator.request()
    }

    fun updateCourse(course: CourseEntity) = viewModelScope.launch {
        repository.updateCourse(course)
        refreshCoordinator.request()
        snackbar.value = "课程已更新"
    }

    fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) = viewModelScope.launch {
        repository.updateCourseSingleWeek(original, edited, targetWeek)
        refreshCoordinator.request()
    }

    fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) = viewModelScope.launch {
        repository.updateRelatedCourses(original, edited)
        refreshCoordinator.request()
        snackbar.value = "课程已更新"
    }

    fun deleteCourse(course: CourseEntity) = viewModelScope.launch {
        repository.deleteCourse(course)
        refreshCoordinator.request()
        snackbar.value = "课程已删除"
    }

    fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) = viewModelScope.launch {
        repository.deleteCourseSingleWeek(course, targetWeek)
        refreshCoordinator.request()
        snackbar.value = "课程已删除"
    }

    fun importDraft(
        draft: ImportDraft,
        createNewSchedule: Boolean = false,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        repository.importDraft(draft, createNewSchedule)
        refreshCoordinator.request()
        snackbar.value = if (createNewSchedule) "已导入到新课表" else "课程表已导入"
        onDone()
    }

    fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) = viewModelScope.launch {
        repository.saveConfig(config, periods)
        refreshCoordinator.request()
        snackbar.value = "设置已保存"
    }

    fun saveConfigForSchedule(
        scheduleId: Int,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        finish: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.saveConfigForSchedule(scheduleId, config, periods)
        refreshCoordinator.request()
        snackbar.value = "设置已保存"
        finish?.invoke()
    }

    fun savePersonalization(config: ScheduleConfigEntity) = viewModelScope.launch {
        repository.saveConfigOnly(config)
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
        refreshCoordinator.request()
    }

    fun clearSnackbar() {
        snackbar.value = null
    }

    fun previewLiveUpdate() {
        NotificationScheduler.showLiveUpdatePreview(app)
        snackbar.value = "已发送实时活动预览"
    }

    fun refreshNotifications() {
        refreshCoordinator.request()
    }

    private suspend fun refreshScheduleSurfaces() = withContext(Dispatchers.IO) {
        val snapshot = repository.snapshot()
        NotificationScheduler.refreshToday(app, snapshot.courses, snapshot.config, snapshot.periods)
        TodayCoursesWidgetProvider.refreshAll(app)
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
