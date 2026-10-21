package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.home.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.feature.home.adaptiveWeekCardCornerRadius
import com.xiaomanjun.sleepdownschedule.feature.home.currentWindowSizeDp
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import kotlin.math.roundToInt

@Composable
internal fun WeekEditOverlayHost(
    request: WeekEditOverlayRequest?,
    hostBounds: androidx.compose.runtime.State<Rect?>,
    offsetX: Float,
    offsetY: Float,
    overlayScale: Float,
    overlayAlpha: Float,
    liftProgress: Float,
    rotation: Float,
    gestureActive: Boolean,
    gridOffsetY: Float,
    gridScrollCompensationY: Float,
    heightPx: Float,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val req = request ?: return
    // Read coordinates only in the active overlay, never in the whole timetable during a slide.
    val host = hostBounds.value ?: return
    if (heightPx <= 1f || req.sourceBounds.width <= 1f) return
    val density = LocalDensity.current
    val widthDp = with(density) { req.sourceBounds.width.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val windowSize = currentWindowSizeDp()
    val cornerProgress = LocalPersonalizationPreview.current?.weekCardCornerProgress
        ?: config.weekCardCornerProgress
    val cardCorner = adaptiveWeekCardCornerRadius(
        cardWidth = widthDp,
        cardHeight = heightDp,
        windowWidth = windowSize.width,
        windowHeight = windowSize.height,
        progress = cornerProgress
    )
    val cardShape = remember(cardCorner) { RoundedRectangle(cardCorner) }
    val left = req.sourceBounds.left - host.left + offsetX
    val top = req.sourceBounds.top - host.top + offsetY
    val cardGlowRadiusPx = with(density) { (widthDp * 1.65f).coerceIn(72.dp, 120.dp).toPx() }
    val liftPx = with(density) { 8.dp.toPx() }
    val touchTransformOrigin = remember(req.initialPointerPosition, req.sourceBounds) {
        TransformOrigin(
            pivotFractionX = (
                (req.initialPointerPosition.x - req.sourceBounds.left) /
                    req.sourceBounds.width.coerceAtLeast(1f)
                ).coerceIn(0f, 1f),
            pivotFractionY = (
                (req.initialPointerPosition.y - req.sourceBounds.top) /
                    req.sourceBounds.height.coerceAtLeast(1f)
                ).coerceIn(0f, 1f)
        )
    }
    val localCardCenter = Offset(
        x = left + req.sourceBounds.width / 2f,
        y = top + heightPx / 2f - liftPx * liftProgress
    )
    val cardGlowPressed = gestureActive || liftProgress > 0.01f
    val cardGlowDiameterDp = with(density) { (cardGlowRadiusPx * 2f).toDp() }
    val target = when (req.mode) {
        WeekEditOverlayMode.Move -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex + (offsetX / req.gridColumnWidthPx).roundToInt(),
            startPeriod = req.periodIndex + (gridOffsetY / req.periodRowHeightPx).roundToInt(),
            span = req.currentSpan,
            weekdayCount = req.weekdayCount
        )
        WeekEditOverlayMode.Resize -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex,
            startPeriod = req.periodIndex,
            span = (heightPx / req.periodRowHeightPx).roundToInt().coerceIn(1, req.maxSpan),
            weekdayCount = req.weekdayCount
        )
    }
    val previewCourse = when (req.mode) {
        WeekEditOverlayMode.Move -> req.course.copy(weekday = target.weekday, periods = target.periods)
        WeekEditOverlayMode.Resize -> req.course.copy(periods = target.periods)
    }
    val conflict = !target.valid ||
        hasWeekCourseEditConflict(if (req.destinationWeek != req.editWeek) req.course.copy(id = -1) else req.course,
            previewCourse, req.weekCourses, req.destinationWeek)
    val previewLeft = req.sourceBounds.left - host.left + (target.weekday - req.dayIndex) * req.gridColumnWidthPx
    val previewTop = req.sourceBounds.top - host.top - gridScrollCompensationY +
        ((target.periods.firstOrNull() ?: req.periodIndex) - req.periodIndex) * req.periodRowHeightPx
    val previewHeight = (req.periodRowHeightPx * target.periods.size.coerceAtLeast(1) - with(density) { 4.dp.toPx() }).coerceAtLeast(with(density) { 18.dp.toPx() })
    val animatedPreviewLeft by animateFloatAsState(
        targetValue = previewLeft,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-left"
    )
    val animatedPreviewTop by animateFloatAsState(
        targetValue = previewTop,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-top"
    )
    val animatedPreviewHeight by animateFloatAsState(
        targetValue = previewHeight,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
        label = "week-edit-preview-height"
    )
    val elevationPx = with(density) { 18.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
    ) {
        WeekEditCardGlow(
            config = config,
            pressed = cardGlowPressed, radiusPx = cardGlowRadiusPx,
            spotAlpha = 0.068f, fallbackAlpha = 0.046f,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (localCardCenter.x - cardGlowRadiusPx).roundToInt(),
                        (localCardCenter.y - cardGlowRadiusPx).roundToInt()
                    )
                }
                .size(cardGlowDiameterDp)
                .graphicsLayer { alpha = overlayAlpha }
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedPreviewLeft.roundToInt(), animatedPreviewTop.roundToInt()) }
                .width(widthDp)
                .height(with(density) { animatedPreviewHeight.toDp() })
                .graphicsLayer { alpha = 0.18f + liftProgress * 0.72f }
                .clip(cardShape)
                .background(
                    if (conflict) MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
                    else ComposeColor.Gray.copy(alpha = 0.24f)
                )
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(widthDp)
                .height(heightDp)
                .graphicsLayer {
                    // A resize handle owns only the lower edge: keep the top edge and width fixed
                    // instead of applying the move gesture's two-axis lifted scale around the
                    // fingertip. The card can therefore grow/shrink in exactly one direction.
                    transformOrigin = if (req.mode == WeekEditOverlayMode.Resize) {
                        TransformOrigin(0.5f, 0f)
                    } else {
                        touchTransformOrigin
                    }
                    scaleX = if (req.mode == WeekEditOverlayMode.Resize) 1f else overlayScale
                    scaleY = if (req.mode == WeekEditOverlayMode.Resize) 1f else overlayScale
                    translationY = -liftPx * liftProgress
                    rotationZ = rotation
                    alpha = overlayAlpha
                    shadowElevation = elevationPx * liftProgress
                    shape = cardShape
                }
        ) {
            CourseGlassCard(
                backdrop = backdrop,
                config = config,
                course = req.course,
                modifier = Modifier.fillMaxSize(),
                shape = cardShape,
                followTouch = false,
                onClick = null
            ) {
                WeekCourseOverlayCardContent(req.course, config)
            }
            if (req.mode == WeekEditOverlayMode.Resize) {
                WeekResizeCornerHandle(
                    config = config,
                    backdrop = backdrop,
                    cardSize = androidx.compose.ui.unit.DpSize(widthDp, heightDp),
                    cardCorner = cardCorner,
                    selected = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(44.dp)
                        .zIndex(2f)
                )
            }
        }
    }
}

@Composable
internal fun WeekCourseOverlayCardContent(
    course: CourseEntity, config: ScheduleConfigEntity, muted: Boolean = false, badgeInset: Dp = 0.dp
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = badgeInset)) {
        WeekCourseCardTextContent(course, config, maxWidth, maxHeight, maxWidth, muted,
            adaptiveContrast = false)
    }
}

@Composable
private fun WeekEditCardGlow(config: ScheduleConfigEntity, pressed: Boolean,
    radiusPx: Float, spotAlpha: Float, fallbackAlpha: Float, modifier: Modifier) {
    // The halo belongs to the lifted card; touch coordinates only drive the editing gesture.
    val position = Offset(radiusPx, radiusPx)
    val native = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeFollowLightRenderer.current
    if (native?.invoke(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeFollowLightRequest(
            modifier, com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme(config),
            position, pressed, radiusPx)) == true) return
    val scope = rememberCoroutineScope()
    val highlight = remember(scope, radiusPx, spotAlpha, fallbackAlpha) {
        InteractiveHighlight(scope, radius = { radiusPx }, ambientAlpha = 0f,
            spotAlpha = spotAlpha, fallbackAlpha = fallbackAlpha)
    }
    LaunchedEffect(position, pressed) { highlight.updateExternal(position, pressed, followPointerExactly = true) }
    Box(modifier.then(highlight.modifier))
}
