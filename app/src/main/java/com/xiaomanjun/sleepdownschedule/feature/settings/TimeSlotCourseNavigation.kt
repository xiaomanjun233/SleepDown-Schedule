package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.transition.*
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

internal const val TimeSlotCourseOriginalExtra = "special_course_original"
internal const val TimeSlotCourseStateTokenExtra = "special_course_state_token"

/** Same bounded, process-local first-frame handoff as course management; Room remains authoritative. */
internal object TimeSlotCourseStateHandoff {
    class Entry(val state: AppState) {
        @Volatile var sourceRemoved: Boolean = false
    }
    private val next = AtomicLong(1)
    private val entries = ConcurrentHashMap<Long, Entry>()
    fun put(state: AppState): Long {
        val token = next.getAndIncrement()
        entries[token] = Entry(state)
        if (entries.size > 6) entries.keys.sorted().dropLast(6).forEach(entries::remove)
        return token
    }
    fun get(token: Long): Entry? = entries[token]
    fun remove(token: Long) { entries.remove(token) }
}

internal fun stableTimeSlotEditorState(live: AppState, initial: AppState?, scheduleId: Int): AppState =
    if (initial != null && (!live.loaded || live.allConfigs.none { it.id == scheduleId } ||
            live.allPeriods.none { it.scheduleId == scheduleId })) initial else live

internal tailrec fun Context.timeSlotTransitionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.timeSlotTransitionActivity()
    else -> null
}

internal suspend fun openTimeSlotCourseEditor(
    activity: Activity,
    state: AppState,
    original: TimeSlotCourse?,
    backgroundColor: Int,
    canReturn: () -> Boolean,
    sourceView: View?,
    onSourceReleased: () -> Unit
): TransitionLaunchResult {
    val token = TimeSlotCourseStateHandoff.put(state)
    val handoff = checkNotNull(TimeSlotCourseStateHandoff.get(token))
    val intent = Intent().setClassName(activity.packageName,"com.xiaomanjun.sleepdownschedule.feature.settings.SpecialCourseEditorActivity")
        .putExtra(SettingsDetailPageExtra, SettingsPage.SpecialCourses.name)
        .putExtra(ScheduleCustomizeIdExtra, state.config.id)
        .putExtra(TimeSlotCourseOriginalExtra, original?.let { encodeTimeSlotCourses(listOf(it)) }.orEmpty())
        .putExtra(TimeSlotCourseStateTokenExtra, token)
    val route = TransitionRouteCatalog.get(TransitionRouteId.SpecialCourseToEditor)
    val sourceReleased = AtomicBoolean(false)
    val restoreSource = { if (sourceReleased.compareAndSet(false, true)) onSourceReleased() }
    try {
        val viewBounds = sourceView?.liveTransitionBoundsInActivity(activity)
        val liveSource = sourceView?.takeIf { viewBounds != null }
        val anchor = viewBounds?.let { bounds ->
            val radius = (liveSource as? LiveTransitionSourceView)?.sourceRadiusPx
                ?: minOf(bounds.width, bounds.height) / 2f
            TransitionAnchorFrame(bounds, radius, bitmap = null)
        }
        fun returnAnchor(frame: TransitionAnchorFrame?) = TransitionAnchorProvider {
            if (handoff.sourceRemoved || !canReturn()) null
            else liveSource?.liveTransitionBoundsInActivity(activity, coveredWindow = true)?.let {
                frame?.copy(boundsInWindow = it)
            }
        }
        val result = ActivityTransitionCoordinator.open(activity, route.id, intent, TransitionPayload(
            openingAnchor = anchor,
            returnAnchorProvider = returnAnchor(anchor),
            backgroundColor = backgroundColor,
            onSourceReleased = restoreSource,
            openingSourceView = liveSource
        ), prepareLegacy = { payload ->
            val frame = anchor?.let {
                it.copy(bitmap = captureGlassWindowRegion(activity.window, it.boundsInWindow))
            }
            payload.copy(openingAnchor = frame, returnAnchorProvider = returnAnchor(frame))
        })
        if (result is TransitionLaunchResult.Failed) {
            TimeSlotCourseStateHandoff.remove(token)
            restoreSource()
        }
        return result
    } catch (failure: Exception) {
        restoreSource()
        TransitionPayloadStore.remove(intent.transitionSessionIdOrNull())
        TimeSlotCourseStateHandoff.remove(token)
        throw failure
    }
}
