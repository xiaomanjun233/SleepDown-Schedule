package com.xiaomanjun.sleepdownschedule.transition

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.ViewOverlay
import kotlin.math.roundToInt

private const val ParabolicSourcePlaceholderMillis = 180L
private const val HomeMenuDestinationSourcePlaceholderMillis = 330L
private const val CourseManagementDetailSourcePlaceholderMillis = 380L

internal class LegacyTransitionBackend : TransitionBackend {
    override val name: String = "sleepdownLegacy"

    override suspend fun open(request: TransitionOpenRequest): TransitionBackendOpenResult =
        openImmediate(request)

    fun openImmediate(request: TransitionOpenRequest): TransitionBackendOpenResult {
        // A business-owned cover may bridge asynchronous native registration. Legacy owns its
        // own source placeholder, so exchange the two synchronously before starting the Activity.
        TransitionPayloadStore.handoffOpeningSource(request.session.id)
        val placeholder = request.attachAnchoredSourcePlaceholder()
        return runCatching {
            request.activity.startActivity(request.intent)
            when (val profile = request.route.legacyProfile) {
                is LegacyTransitionProfile.Anchored -> {
                    @Suppress("DEPRECATION")
                    request.activity.overridePendingTransition(0, 0)
                }
                is LegacyTransitionProfile.Depth -> if (Build.VERSION.SDK_INT < 34) {
                    @Suppress("DEPRECATION")
                    request.activity.overridePendingTransition(
                        profile.openEnterAnimation,
                        profile.openExitAnimation
                    )
                }
                LegacyTransitionProfile.PlatformDefault,
                LegacyTransitionProfile.TaskReturn -> Unit
            }
            TransitionBackendOpenResult.Started
        }.onSuccess {
            placeholder?.scheduleRemoval(request)
        }.getOrElse { error ->
            placeholder?.remove()
            TransitionBackendOpenResult.Rejected(
                "legacyStart:${error.javaClass.simpleName}"
            )
        }
    }
}

private data class LegacySourcePlaceholder(
    val overlay: ViewOverlay,
    val drawable: BitmapDrawable,
    val removalDelayMillis: Long
) {
    fun remove() {
        overlay.remove(drawable)
    }

    fun scheduleRemoval(request: TransitionOpenRequest) {
        request.activity.window.decorView.postDelayed(::remove, removalDelayMillis)
    }
}

private fun TransitionOpenRequest.attachAnchoredSourcePlaceholder(): LegacySourcePlaceholder? {
    val profile = route.legacyProfile as? LegacyTransitionProfile.Anchored ?: return null
    val removalDelayMillis = when (profile.profileId) {
        AnchoredLegacyProfileId.Parabolic -> ParabolicSourcePlaceholderMillis
        // Home hides the real three-dot menu before recording a clean one-way backdrop. Its
        // validated Legacy renderer still needs that exact captured menu until the 330ms opening
        // finishes. Keeping this placeholder inside the Legacy backend prevents it from leaking
        // beneath an Oplus leash when native registration succeeds.
        AnchoredLegacyProfileId.HomeMenuDestination ->
            HomeMenuDestinationSourcePlaceholderMillis
        AnchoredLegacyProfileId.CourseManagementDetail ->
            CourseManagementDetailSourcePlaceholderMillis
        AnchoredLegacyProfileId.DetailSettings ->
            CourseManagementDetailSourcePlaceholderMillis
        AnchoredLegacyProfileId.Liquid -> return null
    }
    val anchor = payload?.openingAnchor?.takeIf(TransitionAnchorFrame::isValid) ?: return null
    val bitmap = anchor.bitmap ?: return null
    val bounds = anchor.boundsInWindow
    val overlay = activity.window.decorView.overlay
    val drawable = BitmapDrawable(activity.resources, bitmap).apply {
        setBounds(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt()
        )
    }
    return runCatching {
        overlay.add(drawable)
        LegacySourcePlaceholder(overlay, drawable, removalDelayMillis)
    }.getOrNull()
}
