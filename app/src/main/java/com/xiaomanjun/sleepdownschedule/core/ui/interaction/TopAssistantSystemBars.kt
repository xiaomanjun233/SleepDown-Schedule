package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Owns only the status bar while a top-anchored surface is present. Never hides navigation/IME. */
@Composable
internal fun TopAssistantSystemBars(hidden: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.assistantActivity()?.window } ?: return
    val insets = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    val originalBehavior = remember(window) { insets.systemBarsBehavior }
    val originalCutout = remember(window) {
        if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else null
    }
    val alpha = remember(window) { Animatable(1f) }
    DisposableEffect(window) {
        onDispose {
            insets.show(WindowInsetsCompat.Type.statusBars())
            insets.systemBarsBehavior = originalBehavior
            if (Build.VERSION.SDK_INT >= 28 && originalCutout != null) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutout }
            }
        }
    }
    LaunchedEffect(window, hidden) {
        val type = WindowInsetsCompat.Type.statusBars()
        insets.systemBarsBehavior = if (hidden) {
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else originalBehavior
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (hidden) {
                    if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else originalCutout ?: layoutInDisplayCutoutMode
            }
        }
        val alreadyShown = ViewCompat.getRootWindowInsets(view)?.isVisible(type) != false
        if (!hidden && alreadyShown && alpha.value >= 0.999f) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < 30) {
            if (hidden) insets.hide(type) else insets.show(type)
            alpha.snapTo(if (hidden) 0f else 1f)
            return@LaunchedEffect
        }
        val cancellation = CancellationSignal()
        try {
            val control = withTimeoutOrNull(240L) {
                suspendCancellableCoroutine<WindowInsetsAnimationControllerCompat?> { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    insets.controlWindowInsetsAnimation(type, -1L, null, cancellation,
                        object : WindowInsetsAnimationControlListenerCompat {
                            override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                                if (continuation.isActive) continuation.resume(controller)
                            }
                            override fun onFinished(controller: WindowInsetsAnimationControllerCompat) = Unit
                            override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                }
            }
            if (control == null) {
                if (hidden) insets.hide(type) else insets.show(type)
                alpha.snapTo(if (hidden) 0f else 1f)
            } else {
                alpha.animateTo(if (hidden) 0f else 1f, tween(if (hidden) 160 else 180)) {
                    if (control.isReady) {
                        val fraction = if (hidden) 1f - value else value
                        control.setInsetsAndAlpha(control.shownStateInsets, value.coerceIn(0f, 1f), fraction.coerceIn(0f, 1f))
                    }
                }
                if (control.isReady) control.finish(!hidden)
                // If SystemUI took control (e.g. the user swiped its edge), do not fight it.
            }
        } finally {
            cancellation.cancel()
        }
    }
}

private tailrec fun Context.assistantActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.assistantActivity()
    else -> null
}
