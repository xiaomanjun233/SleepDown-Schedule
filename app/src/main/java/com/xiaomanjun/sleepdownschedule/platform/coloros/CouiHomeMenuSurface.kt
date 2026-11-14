package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.itos.coui.motion.rememberCouiTouchMotion
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSurfaceTouch

/** The anchored menu host still owns hit testing, slide selection and the opening/return morph. */
@RequiresApi(34)
@Composable
internal fun CouiHomeMenuSurface(request: NativeMenuSurfaceRequest, content: @Composable () -> Unit): Boolean {
    if (request.surface.backdrop == null || AppMaterialPreferences.isSuperPerformance) return false
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var touch by remember { mutableStateOf(NativeSurfaceTouch(Offset.Unspecified, false)) }
    val source = request.touchSource
    val sink = request.motion
    if (source != null) DisposableEffect(source) {
        source.onTouch = { event ->
            val local = coordinates?.takeIf { it.isAttached }?.screenToLocal(Offset(event.rawX, event.rawY))
            if (local != null && local.x.isFinite() && local.y.isFinite()) {
                touch = NativeSurfaceTouch(local,
                    event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
            } else if (touch.pressed) {
                touch = touch.copy(pressed = false)
            }
        }
        onDispose { source.onTouch = null }
    }
    if (sink != null) {
        val motion = rememberCouiTouchMotion(expanded = true)
        LaunchedEffect(sink, motion) {
            snapshotFlow { listOf(motion.translationX.value, motion.translationY.value, motion.scaleX.value, motion.scaleY.value) }
                .collect { (x, y, sx, sy) -> sink.update(x, y, sx, sy) }
        }
        DisposableEffect(sink, motion) {
            val tracker = VelocityTracker.obtain()
            sink.onTouch = { event ->
                val point = Offset(event.rawX, event.rawY)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tracker.clear()
                        tracker.addMovement(event)
                        coordinates?.takeIf { it.isAttached }?.let { motion.begin(point, it.size) }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        tracker.addMovement(event)
                        motion.move(point)
                    }
                    MotionEvent.ACTION_UP -> {
                        tracker.addMovement(event)
                        tracker.computeCurrentVelocity(1)
                        motion.release(Offset(tracker.xVelocity, tracker.yVelocity))
                    }
                    MotionEvent.ACTION_CANCEL -> motion.release(Offset.Zero)
                }
            }
            onDispose {
                sink.onTouch = null
                sink.update(0f, 0f, 1f, 1f)
                tracker.recycle()
            }
        }
    }
    return CouiMaterialSurface(request.surface.copy(
        modifier = request.surface.modifier.onGloballyPositioned { coordinates = it },
        role = NativeMaterialSurfaceRole.Menu,
        externalTouch = if (source != null) ({ touch }) else null,
    ), content = content)
}
