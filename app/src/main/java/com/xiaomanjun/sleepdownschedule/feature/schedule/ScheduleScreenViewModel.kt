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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-owned schedule state without the home editor, Agent or personalization queues. */
internal class ScheduleScreenViewModel(
    app: Application,
    repository: ScheduleRepository,
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
