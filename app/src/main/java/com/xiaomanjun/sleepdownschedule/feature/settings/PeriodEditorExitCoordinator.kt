package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Page navigation waits until the timeline has handed its draft back to settings. */
@Stable
class PeriodEditorExitCoordinator {
    var isOpen by mutableStateOf(false)
        private set
    var requestVersion by mutableIntStateOf(0)
        private set
    var resultVersion by mutableIntStateOf(0)
        private set
    var result by mutableStateOf<Boolean?>(null)
        private set
    var pending by mutableStateOf(false)
        private set

    fun opened() { isOpen = true }

    fun requestPageExit(): Boolean {
        if (!isOpen) return false
        if (!pending) {
            pending = true
            requestVersion++
        }
        return true
    }

    fun closedAfterDraftHandoff() {
        isOpen = false
        if (pending) complete(true)
    }

    fun cancelRequest() {
        if (pending) complete(false)
    }

    fun abandon() {
        cancelRequest()
        isOpen = false
    }

    private fun complete(accepted: Boolean) {
        pending = false
        result = accepted
        resultVersion++
    }
}
