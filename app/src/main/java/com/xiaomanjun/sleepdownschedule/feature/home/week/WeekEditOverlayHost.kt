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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.layout
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.findRootCoordinates
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
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.feature.home.adaptiveWeekCardCornerRadius
import com.xiaomanjun.sleepdownschedule.feature.home.currentWindowSizeDp
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import kotlin.math.roundToInt

@Composable
internal fun WeekEditOverlayHost(
    motion: WeekEditOverlayController,
    hostBounds: androidx.compose.runtime.State<Rect?>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val req = motion.request ?: return
    val heightPx = motion.height
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
    val originLeft = req.sourceBounds.left - host.left
    val originTop = req.sourceBounds.top - host.top
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
    val cardGlowPressed = motion.gestureActive
    val nativeCourse = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode.current?.enabled == true
    val cardCoordinates = remember(req) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val cardGlowDiameterDp = with(density) { (cardGlowRadiusPx * 2f).toDp() }
    val target by remember(req, motion) { derivedStateOf { when (req.mode) {
        WeekEditOverlayMode.Move -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.weekdayAfterMove(motion.offsetX),
            startPeriod = req.startPeriodAfterMove(motion.gridOffsetY),
            span = req.currentSpan,
            weekdayCount = req.weekdayCount
        )
        WeekEditOverlayMode.Resize -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex,
            startPeriod = req.periodIndex,
            span = req.spanForHeight(motion.height),
            weekdayCount = req.weekdayCount
        )
    } } }
    val invalidTarget = !target.valid
    val previewLeft = req.sourceBounds.left - host.left + req.columnDeltaTo(target.weekday) * req.gridColumnWidthPx
    val previewTop = req.sourceBounds.top - host.top +
        req.rowTopFor(target.periods.firstOrNull() ?: req.periodIndex) - req.rowTopFor(req.periodIndex)
    val previewHeight = (req.heightForSpan(target.periods.size.coerceAtLeast(1), target.periods.firstOrNull() ?: req.periodIndex, with(density) { 4.dp.toPx() })).coerceAtLeast(with(density) { 18.dp.toPx() })
    val animatedPreviewLeft = animateFloatAsState(
        targetValue = previewLeft,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-left"
    )
    val animatedPreviewTop = animateFloatAsState(
        targetValue = previewTop,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-top"
    )
    val animatedPreviewHeight = animateFloatAsState(
        targetValue = previewHeight,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
        label = "week-edit-preview-height"
    )
    val elevationPx = with(density) { 18.dp.toPx() }
    val parentRedraw = rememberUpdatedState(LocalGlassSampleRecordKey.current)
    val tuckActive = motion.tuckUnderActive
    val dipMaxPx = (heightPx * 0.78f).coerceIn(
        with(density) { 80.dp.toPx() },
        with(density) { 148.dp.toPx() }
    )
    val tuckTransform = if (tuckActive) {
        weekEditTuckUnderTransform(motion.tuckUnderProgress, dipMaxPx)
    } else null
    val absorbTransform = if (tuckActive) {
        weekEditFrontAbsorbTransform(motion.tuckUnderProgress)
    } else null
    val redraw = remember(motion) { {
        parentRedraw.value()
        // Placement and transforms do not invalidate a retained drawing on their own.
        motion.offsetX; motion.offsetY; motion.height; motion.scale; motion.rotation; motion.liftProgress; motion.tuckUnderProgress
    } }
    CompositionLocalProvider(LocalGlassSampleRecordKey provides redraw) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(90f)
        ) {
            if (!nativeCourse) WeekEditCardGlow(
                config = config,
                pressed = cardGlowPressed, radiusPx = cardGlowRadiusPx,
                spotAlpha = 0.068f, fallbackAlpha = 0.046f,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (originLeft + motion.offsetX + req.sourceBounds.width / 2f - cardGlowRadiusPx).roundToInt(),
                            (originTop + motion.offsetY + motion.height / 2f - liftPx * motion.liftProgress - cardGlowRadiusPx).roundToInt()
                        )
                    }
                    .size(cardGlowDiameterDp)
                    .graphicsLayer { alpha = motion.alpha }
            )
            val placeholderAlpha = if (tuckActive && motion.tuckUnderProgress > 0.01f) {
                0f
            } else {
                0.18f + motion.liftProgress * 0.72f
            }
            if (placeholderAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedPreviewLeft.value.roundToInt(), (animatedPreviewTop.value - motion.gridScrollCompensationY).roundToInt()) }
                        .width(widthDp)
                        .layout { measurable, constraints ->
                            val h = animatedPreviewHeight.value.roundToInt().coerceAtLeast(1)
                            val child = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                            layout(child.width, child.height) { child.place(0, 0) }
                        }
                        .graphicsLayer { alpha = placeholderAlpha }
                        .clip(cardShape)
                        .background(
                            if (invalidTarget) MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
                            else ComposeColor.Gray.copy(alpha = 0.24f)
                        )
                )
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset((originLeft + motion.offsetX).roundToInt(), (originTop + motion.offsetY).roundToInt()) }
                    .width(widthDp)
                    .height(heightDp)
                    .zIndex(if (tuckTransform?.behindFront == true) 1f else 3f)
                    .onGloballyPositioned { cardCoordinates[0] = it }
                    .graphicsLayer {
                        // A resize handle owns only the lower edge: keep the top edge and width fixed
                        // instead of applying the move gesture's two-axis lifted scale around the
                        // fingertip. The card can therefore grow/shrink in exactly one direction.
                        transformOrigin = when {
                            tuckTransform != null -> TransformOrigin.Center
                            req.mode == WeekEditOverlayMode.Resize -> TransformOrigin(0.5f, 0f)
                            else -> touchTransformOrigin
                        }
                        val tuckScale = tuckTransform?.scale ?: 1f
                        val baseScale = if (req.mode == WeekEditOverlayMode.Resize) 1f else motion.scale
                        scaleX = baseScale * tuckScale
                        scaleY = baseScale * tuckScale
                        translationY = -liftPx * motion.liftProgress + (tuckTransform?.dipOffsetY ?: 0f)
                        rotationZ = motion.rotation
                        alpha = motion.alpha * (tuckTransform?.alpha ?: 1f)
                        shadowElevation = elevationPx * motion.liftProgress * (tuckTransform?.shadowFraction ?: 1f)
                        shape = cardShape
                    }
            ) {
                CourseGlassCard(
                    backdrop = backdrop,
                    config = config,
                    course = req.course,
                    modifier = Modifier.fillMaxSize(),
                    shape = cardShape,
                    followTouch = true,
                    externalTouch = {
                        val coordinates = cardCoordinates[0]?.takeIf { it.isAttached }
                        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSurfaceTouch(
                            coordinates?.localPositionOf(coordinates.findRootCoordinates(), motion.pointerPosition)
                                ?: Offset(req.sourceBounds.width / 2f, heightPx / 2f),
                            motion.gestureActive
                        )
                    },
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
            val tuckHost = motion.tuckUnderHostCourse
            val tuckHostBounds = motion.liveTuckUnderHostBounds()
            if (tuckActive && tuckHost != null && tuckHostBounds != null && absorbTransform != null) {
                val hostLeft = tuckHostBounds.left - host.left
                val hostTop = tuckHostBounds.top - host.top
                val hostWidthDp = with(density) { tuckHostBounds.width.toDp().coerceAtLeast(1.dp) }
                val hostHeightDp = with(density) { tuckHostBounds.height.toDp().coerceAtLeast(1.dp) }
                val hostCorner = adaptiveWeekCardCornerRadius(
                    cardWidth = hostWidthDp,
                    cardHeight = hostHeightDp,
                    windowWidth = windowSize.width,
                    windowHeight = windowSize.height,
                    progress = cornerProgress
                )
                val hostShape = remember(hostCorner) { RoundedRectangle(hostCorner) }
                val absorbLiftPx = with(density) { 5.dp.toPx() }
                val badgeReveal = ((motion.tuckUnderProgress - 0.66f) / 0.22f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(hostLeft.roundToInt(), hostTop.roundToInt()) }
                        .size(hostWidthDp, hostHeightDp)
                        .zIndex(2f)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = absorbTransform.scale
                            scaleY = absorbTransform.scale
                            translationY = absorbTransform.liftOffsetY * absorbLiftPx
                            alpha = motion.alpha
                            shape = hostShape
                        }
                ) {
                    CourseGlassCard(
                        backdrop = backdrop,
                        config = config,
                        course = tuckHost,
                        modifier = Modifier.fillMaxSize(),
                        shape = hostShape,
                        followTouch = false,
                        onClick = null
                    ) {
                        WeekCourseOverlayCardContent(tuckHost, config)
                    }
                    if (motion.tuckUnderOverlapCount > 1 && badgeReveal > 0.01f) {
                        com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseOverlapBadge(
                            count = motion.tuckUnderOverlapCount,
                            cardCorner = hostCorner,
                            backdrop = backdrop,
                            config = config,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(7f)
                                .graphicsLayer {
                                    val badgeScale = 0.62f + 0.38f * badgeReveal
                                    alpha = badgeReveal
                                    scaleX = badgeScale
                                    scaleY = badgeScale
                                }
                        )
                    }
                }
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
            modifier, com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme(config),
            position, pressed, radiusPx)) == true) return
    val scope = rememberCoroutineScope()
    val highlight = remember(scope, radiusPx, spotAlpha, fallbackAlpha) {
        InteractiveHighlight(scope, radius = { radiusPx }, ambientAlpha = 0f,
            spotAlpha = spotAlpha, fallbackAlpha = fallbackAlpha)
    }
    LaunchedEffect(position, pressed) { highlight.updateExternal(position, pressed, followPointerExactly = true) }
    Box(modifier.then(highlight.modifier))
}
