package com.xiaomanjun.sleepdownschedule

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId

const val ScheduleCustomizeIdExtra = "schedule_customize_id"
private const val AnchoredSourceLeftExtra = "anchored_source_left"
private const val AnchoredSourceTopExtra = "anchored_source_top"
private const val AnchoredSourceRightExtra = "anchored_source_right"
private const val AnchoredSourceBottomExtra = "anchored_source_bottom"
private const val AnchoredCollapseLeftExtra = "anchored_collapse_left"
private const val AnchoredCollapseTopExtra = "anchored_collapse_top"
private const val AnchoredCollapseRightExtra = "anchored_collapse_right"
private const val AnchoredCollapseBottomExtra = "anchored_collapse_bottom"

fun Activity.installScheduleDepthTransitions() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.schedule_depth_enter,
            R.anim.schedule_depth_exit
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.schedule_depth_pop_enter,
            R.anim.schedule_depth_pop_exit
        )
    }
}

fun Activity.applyLegacyScheduleDepthCloseTransition() {
    if (Build.VERSION.SDK_INT < 34) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.schedule_depth_pop_enter, R.anim.schedule_depth_pop_exit)
    }
}

fun android.content.Intent.putScheduleCustomizeId(scheduleId: Int): android.content.Intent =
    putExtra(ScheduleCustomizeIdExtra, scheduleId)

fun Intent.putAnchoredSourceBounds(bounds: Rect): Intent = apply {
    putExtra(AnchoredSourceLeftExtra, bounds.left)
    putExtra(AnchoredSourceTopExtra, bounds.top)
    putExtra(AnchoredSourceRightExtra, bounds.right)
    putExtra(AnchoredSourceBottomExtra, bounds.bottom)
}

fun Intent.putAnchoredCollapseBounds(bounds: Rect): Intent = apply {
    putExtra(AnchoredCollapseLeftExtra, bounds.left)
    putExtra(AnchoredCollapseTopExtra, bounds.top)
    putExtra(AnchoredCollapseRightExtra, bounds.right)
    putExtra(AnchoredCollapseBottomExtra, bounds.bottom)
}

fun Activity.returnToScheduleHome() {
    ActivityTransitionCoordinator.openImmediate(
        this,
        TransitionRouteId.ReturnToHome,
        Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
    )
    finish()
}

fun Intent.anchoredSourceBoundsOrNull(): Rect? {
    if (!hasExtra(AnchoredSourceLeftExtra)) return null
    return Rect(
        getFloatExtra(AnchoredSourceLeftExtra, 0f),
        getFloatExtra(AnchoredSourceTopExtra, 0f),
        getFloatExtra(AnchoredSourceRightExtra, 0f),
        getFloatExtra(AnchoredSourceBottomExtra, 0f)
    ).takeIf { it.width > 1f && it.height > 1f }
}

fun Intent.anchoredCollapseBoundsOrNull(): Rect? {
    if (!hasExtra(AnchoredCollapseLeftExtra)) return null
    return Rect(
        getFloatExtra(AnchoredCollapseLeftExtra, 0f),
        getFloatExtra(AnchoredCollapseTopExtra, 0f),
        getFloatExtra(AnchoredCollapseRightExtra, 0f),
        getFloatExtra(AnchoredCollapseBottomExtra, 0f)
    ).takeIf { it.width > 1f && it.height > 1f }
}
