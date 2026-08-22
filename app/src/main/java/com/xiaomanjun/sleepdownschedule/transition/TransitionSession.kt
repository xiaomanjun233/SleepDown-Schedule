package com.xiaomanjun.sleepdownschedule.transition

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JvmInline
value class TransitionSessionId(val value: String) {
    companion object {
        fun create(): TransitionSessionId = TransitionSessionId(UUID.randomUUID().toString())
    }
}

enum class TransitionSessionState {
    Created,
    SourceReady,
    NativeRegistered,
    NativeRunning,
    LegacyRunning,
    Open,
    Closing,
    Finished,
    Cancelled,
    Failed;

    val terminal: Boolean
        get() = this == Finished || this == Cancelled || this == Failed
}

internal val legalTransitionSessionEdges: Map<TransitionSessionState, Set<TransitionSessionState>> =
    mapOf(
        TransitionSessionState.Created to setOf(
            TransitionSessionState.SourceReady,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.SourceReady to setOf(
            TransitionSessionState.NativeRegistered,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.NativeRegistered to setOf(
            TransitionSessionState.NativeRunning,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.NativeRunning to setOf(
            TransitionSessionState.Open,
            TransitionSessionState.Closing,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.LegacyRunning to setOf(
            TransitionSessionState.Open,
            TransitionSessionState.Closing,
            TransitionSessionState.Finished,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.Open to setOf(
            TransitionSessionState.Closing,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Finished,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        ),
        TransitionSessionState.Closing to setOf(
            TransitionSessionState.Finished,
            TransitionSessionState.LegacyRunning,
            TransitionSessionState.Cancelled,
            TransitionSessionState.Failed
        )
    )

/** Per-navigation state; no backend state is shared between unrelated or nested routes. */
class TransitionSession internal constructor(
    val id: TransitionSessionId,
    val routeId: TransitionRouteId,
    val parentSessionId: TransitionSessionId?
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(TransitionSessionState.Created)
    private var callbackGeneration = 0L
    private var reachedNativeRunning = false

    val state: StateFlow<TransitionSessionState> = mutableState.asStateFlow()
    val currentState: TransitionSessionState get() = mutableState.value
    val hasReachedNativeRunning: Boolean
        get() = synchronized(lock) { reachedNativeRunning }

    fun moveTo(next: TransitionSessionState): Boolean = synchronized(lock) {
        val current = mutableState.value
        if (current == next) return true
        if (next !in legalTransitionSessionEdges[current].orEmpty()) return false
        if (next == TransitionSessionState.NativeRunning) reachedNativeRunning = true
        mutableState.value = next
        true
    }

    /** Claims a state edge once; useful when timeout/end/back events can race. */
    fun moveFrom(
        expected: TransitionSessionState,
        next: TransitionSessionState
    ): Boolean = synchronized(lock) {
        if (mutableState.value != expected) return false
        if (next !in legalTransitionSessionEdges[expected].orEmpty()) return false
        if (next == TransitionSessionState.NativeRunning) reachedNativeRunning = true
        mutableState.value = next
        true
    }

    fun nextCallbackGeneration(): Long = synchronized(lock) {
        callbackGeneration += 1L
        callbackGeneration
    }

    fun invalidateCallbacks() = synchronized(lock) {
        callbackGeneration += 1L
    }

    fun isCurrentCallback(generation: Long): Boolean = synchronized(lock) {
        callbackGeneration == generation && !mutableState.value.terminal
    }
}
