package com.xiaomanjun.sleepdownschedule.transition

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.AnchoredDetailActivityMorph
import com.xiaomanjun.sleepdownschedule.AnchoredDetailMotionStyle
import com.xiaomanjun.sleepdownschedule.AnchoredDetailOpeningMode

/**
 * Destination-side arbitration between a native leash and the validated SleepDown Morph. During
 * registration the renderer holds the exact source frame; only the matching session callback can
 * release it to native ownership.
 */
@Composable
fun CrossActivityTransitionHost(
    activity: ComponentActivity,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val sessionId = remember(activity.intent) { activity.intent.transitionSessionIdOrNull() }
    val session = remember(sessionId) { TransitionPayloadStore.session(sessionId) }
    val payload = remember(sessionId) { TransitionPayloadStore.payload(sessionId) }
    val routeId = session?.routeId ?: activity.intent.transitionRouteIdOrNull()
    val profile = routeId
        ?.let(TransitionRouteCatalog::get)
        ?.legacyProfile as? LegacyTransitionProfile.Anchored
    val state = if (session != null) {
        val observed by session.state.collectAsState()
        observed
    } else {
        null
    }
    val openingMode = when {
        state == TransitionSessionState.NativeRegistered ->
            AnchoredDetailOpeningMode.HoldSourceFrame
        session?.hasReachedNativeRunning == true &&
            state != TransitionSessionState.LegacyRunning ->
            AnchoredDetailOpeningMode.ShowDestination
        else -> AnchoredDetailOpeningMode.AnimateLegacy
    }

    val openingAnchor = payload?.openingAnchor
    val returnAnchor = remember(payload) { payload?.returnAnchorProvider?.resolve() }
    AnchoredDetailActivityMorph(
        sourceBounds = openingAnchor?.boundsInWindow,
        collapseBounds = returnAnchor?.boundsInWindow,
        sourceCornerRadius = (profile?.sourceCornerRadiusDp ?: 0f).dp,
        collapseCornerRadius = (profile?.returnCornerRadiusDp
            ?: profile?.sourceCornerRadiusDp
            ?: 0f).dp,
        backgroundSnapshot = payload?.backgroundBitmap,
        sourceSnapshot = openingAnchor?.bitmap,
        collapseSnapshot = returnAnchor?.bitmap,
        motionStyle = profile?.profileId.toMotionStyle(),
        destinationFirstOpening = profile?.destinationFirstOpening == true,
        openingMode = openingMode,
        onOpened = {
            if (session?.hasReachedNativeRunning != true) {
                ActivityTransitionCoordinator.markOpen(sessionId)
            }
        },
        onCloseRequested = {
            if (ActivityTransitionCoordinator.requestNativeClose(activity, sessionId)) {
                activity.finish()
                true
            } else {
                ActivityTransitionCoordinator.beginLegacyClose(sessionId)
                false
            }
        },
        onFinished = {
            ActivityTransitionCoordinator.finishAfterLegacy(activity, sessionId)
        },
        sourceContent = sourceContent,
        content = content
    )
}

private fun AnchoredLegacyProfileId?.toMotionStyle(): AnchoredDetailMotionStyle = when (this) {
    AnchoredLegacyProfileId.HomeMenuDestination ->
        AnchoredDetailMotionStyle.HomeMenuDestination
    AnchoredLegacyProfileId.CourseManagementDetail ->
        AnchoredDetailMotionStyle.CourseManagementDetail
    AnchoredLegacyProfileId.Parabolic -> AnchoredDetailMotionStyle.Parabolic
    AnchoredLegacyProfileId.Liquid,
    null -> AnchoredDetailMotionStyle.Liquid
}
