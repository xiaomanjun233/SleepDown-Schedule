package com.xiaomanjun.sleepdownschedule.feature.schedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-owned schedule state without the home editor, Agent or personalization queues. */
internal class ScheduleScreenViewModel(
    app: Application,
    private val repository: ScheduleRepository,
    private val applicationScope: CoroutineScope
) : AndroidViewModel(app) {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())
    val allSchedulesState = repository.allSchedulesState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())
    private val refresh = ScheduleRefreshCoordinator(
        scope = viewModelScope,
        refresh = { refreshScheduleSurfaces(app, repository) },
        onFailure = { Log.w("ScheduleScreen", "Schedule surface refresh failed", it) }
    )
    val actions = ScheduleActions(app, repository, viewModelScope, refresh::request)
    private val _adjustmentCommit = MutableStateFlow(AdjustmentCommit())
    val adjustmentCommit = _adjustmentCommit.asStateFlow()

    data class AdjustmentCommit(val saving: Boolean = false, val savedJson: String? = null)

    suspend fun commitAdjustments(scheduleId: Int, expected: String, updated: String) {
        // The write belongs to the Activity's ViewModel so a window recreation cannot cancel it.
        viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            val previous = _adjustmentCommit.value
            check(!previous.saving) { "正在保存调休安排" }
            _adjustmentCommit.value = previous.copy(saving = true)
            try {
                repository.saveScheduleAdjustments(scheduleId, expected, updated)
            } catch (failure: Exception) {
                _adjustmentCommit.value = previous
                throw failure
            }
            _adjustmentCommit.value = AdjustmentCommit(savedJson = updated)
            refreshNotificationsAfterSave()
        }.await()
    }

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            refresh.refreshNow()
        }
    }

    fun refreshNotificationsAfterSave() {
        // The Activity can finish in its save callback; the completed write still needs a refresh.
        applicationScope.launch { refresh.refreshNow() }
    }

    class Factory(
        private val app: Application,
        private val repository: ScheduleRepository,
        private val applicationScope: CoroutineScope
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScheduleScreenViewModel::class.java))
            return ScheduleScreenViewModel(app, repository, applicationScope) as T
        }
    }
}
