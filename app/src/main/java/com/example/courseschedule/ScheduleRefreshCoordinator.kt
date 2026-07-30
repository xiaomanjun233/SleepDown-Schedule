package com.example.courseschedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private val onFailure: (Throwable) -> Unit = {},
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
                .collect { refreshSafely() }
        }
    }

    fun request() {
        requests.tryEmit(Unit)
    }

    suspend fun refreshNow() {
        refreshSafely()
    }

    private suspend fun refreshSerialized() {
        refreshMutex.withLock { refresh() }
    }

    private suspend fun refreshSafely() {
        try {
            refreshSerialized()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onFailure(error)
        }
    }
}
