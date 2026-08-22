package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.content.Intent

data class TransitionOpenRequest(
    val activity: Activity,
    val intent: Intent,
    val route: TransitionRouteSpec,
    val session: TransitionSession,
    val payload: TransitionPayload?
)

sealed interface TransitionBackendOpenResult {
    data object Started : TransitionBackendOpenResult
    data class Rejected(val reason: String) : TransitionBackendOpenResult
}

sealed interface TransitionLaunchResult {
    val sessionId: TransitionSessionId

    data class LegacyStarted(override val sessionId: TransitionSessionId) : TransitionLaunchResult
    data class NativeRegistered(override val sessionId: TransitionSessionId) : TransitionLaunchResult
    data class Failed(override val sessionId: TransitionSessionId, val reason: String) :
        TransitionLaunchResult
}

internal interface TransitionBackend {
    val name: String
    suspend fun open(request: TransitionOpenRequest): TransitionBackendOpenResult
}
