package com.xiaomanjun.sleepdownschedule.app.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx
import com.xiaomanjun.sleepdownschedule.feature.onboarding.VersionWelcomeScreen
import com.xiaomanjun.sleepdownschedule.transition.legacy.PageMorphStyle
import com.xiaomanjun.sleepdownschedule.transition.legacy.SharedViewFallbackMotion
import com.xiaomanjun.sleepdownschedule.transition.legacy.homeMorphSmoothStep
import com.xiaomanjun.sleepdownschedule.transition.legacy.sharedViewGeometry
import com.xiaomanjun.sleepdownschedule.transition.liveTransitionBoundsInActivity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Reveals the measured homepage through the moving opening in the welcome page. */
@Composable
internal fun InlineVersionWelcome(
    activity: Activity,
    homeReady: Boolean,
    onPrepareHome: () -> Unit,
    onLightBackground: (Boolean) -> Unit,
    onFinished: () -> Unit,
) {
    var opening by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var offsetInWindow by remember { mutableStateOf(Offset.Zero) }
    val extent = remember { Animatable(0f) }
    val vertical = remember { Animatable(0f) }
    val targetRadius = deviceScreenCornerRadiusPx()
    val backdrop = MaterialTheme.colorScheme.background
    LaunchedEffect(opening, homeReady) {
        if (!opening || !homeReady) return@LaunchedEffect
        if (anchor != null) {
            val motion = SharedViewFallbackMotion(PageMorphStyle.Welcome)
            coroutineScope {
                launch { extent.animateTo(1f, motion.open) }
                launch { vertical.animateTo(1f, motion.openVertical) }
            }
        }
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { offsetInWindow = it.positionInWindow() }
            .pointerInput(Unit) {
                // The welcome page owns the touch target, including empty areas over the timetable.
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .drawWithCache {
                val hole = Path()
                val full = Rect(Offset.Zero, size)
                onDrawWithContent {
                    val source = anchor?.translate(-offsetInWindow)
                    val progress = extent.value
                    val y = vertical.value
                    if (source == null || (progress == 0f && y == 0f)) {
                        drawContent()
                    } else if (progress < 1f || y < 1f) {
                        val frame = sharedViewGeometry(source, full, source.height / 2f, targetRadius,
                            progress, y, PageMorphStyle.Welcome)
                        val radius = minOf(frame.cornerRadiusPx, frame.rect.width / 2f, frame.rect.height / 2f)
                        hole.rewind()
                        hole.addRoundRect(RoundRect(frame.rect, CornerRadius(radius)))
                        clipPath(hole, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                        val surfaceAlpha = 1f - homeMorphSmoothStep(0f, 0.46f, progress)
                        if (surfaceAlpha > 0f) clipPath(hole) { drawRect(backdrop, alpha = surfaceAlpha) }
                    }
                }
            }
    ) {
        VersionWelcomeScreen(
            opening = opening,
            onPrepareHome = onPrepareHome,
            onLightBackground = onLightBackground,
            onContinue = { source ->
                anchor = source?.liveTransitionBoundsInActivity(activity)
                opening = true
                onPrepareHome()
            },
        )
    }
}
