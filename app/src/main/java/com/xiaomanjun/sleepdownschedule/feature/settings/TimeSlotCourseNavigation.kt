package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.withFrameNanos
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses
import com.xiaomanjun.sleepdownschedule.transition.*
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

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
    bounds: Rect?,
    cornerRadiusPx: Float,
    backgroundColor: Int,
    returnBounds: () -> Rect?,
    onSourceHidden: () -> Unit,
    onSourceVisible: () -> Unit,
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
    // Capture only the tapped card once. Normal slide navigation needs no full-screen bitmap.
    var bitmap: Bitmap? = null
    var releaseCover: (() -> Unit)? = null
    val sourceReleased = AtomicBoolean(false)
    val restoreSource = { if (sourceReleased.compareAndSet(false, true)) onSourceReleased() }
    try {
        bitmap = if (TransitionFeatureGate.allowsSharedViewKit(route) &&
            activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE && bounds != null)
            captureTimeSlotCard(activity, bounds) else null
        val anchor = if (bitmap != null && bounds != null) TransitionAnchorFrame(bounds, cornerRadiusPx, bitmap) else null
        releaseCover = anchor?.let { activity.attachOpeningSourceSnapshotHandoff(it) }
        if (anchor != null && releaseCover != null) {
            onSourceHidden()
            // Keep the exact cover while the actual source leaves its last drawn frame.
            withFrameNanos { }
            withFrameNanos { }
        }
        val result = ActivityTransitionCoordinator.open(activity, route.id, intent, TransitionPayload(
            openingAnchor = anchor,
            returnAnchorProvider = TransitionAnchorProvider {
                if (handoff.sourceRemoved) null else returnBounds()?.let { anchor?.copy(boundsInWindow = it) }
            },
            backgroundColor = backgroundColor,
            onOpeningSourceHandoff = releaseCover,
            onSourceReleased = restoreSource
        ))
        if (result !is TransitionLaunchResult.NativeRegistered) {
            releaseCover?.invoke()
            onSourceVisible()
        }
        if (result is TransitionLaunchResult.Failed) {
            TimeSlotCourseStateHandoff.remove(token)
            restoreSource()
        }
        bitmap?.let { pixels -> TransitionPayloadStore.registerCleanup(result.sessionId) {
            if (!pixels.isRecycled) pixels.recycle()
        } }
        return result
    } catch (failure: Exception) {
        releaseCover?.invoke()
        restoreSource()
        TransitionPayloadStore.remove(intent.transitionSessionIdOrNull())
        TimeSlotCourseStateHandoff.remove(token)
        bitmap?.takeUnless { it.isRecycled }?.recycle()
        if (failure is CancellationException) throw failure
        throw failure
    }
}

private suspend fun captureTimeSlotCard(activity: Activity, bounds: Rect): Bitmap? {
    val decor = activity.window.decorView
    val rect = android.graphics.Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
        bounds.right.roundToInt(), bounds.bottom.roundToInt())
    if (rect.width() < 2 || rect.height() < 2 || !decor.isAttachedToWindow ||
        rect.left < 0 || rect.top < 0 || rect.right > decor.width || rect.bottom > decor.height) return null
    val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(activity.window, rect, bitmap, { result ->
                if (!continuation.isActive || result != PixelCopy.SUCCESS) {
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(null)
                } else continuation.resume(bitmap)
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
