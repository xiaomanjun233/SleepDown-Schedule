package com.example.courseschedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces bursts of database writes into one serialized notification/widget refresh. */
class ScheduleRefreshCoordinator(
    scope: CoroutineScope,
    private val refresh: suspend () -> Unit,
    debounceMillis: Long = 120L
) {
    private val refreshMutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            requests
                .debounce(debounceMillis)
                .collect { refreshSerialized() }
        }
    }

    fun request() {
        requests.tryEmit(Unit)
    }

    suspend fun refreshNow() {
        refreshSerialized()
    }

    private suspend fun refreshSerialized() {
        refreshMutex.withLock { refresh() }
    }
}
