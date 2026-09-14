package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorWeekGrid
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent
import com.xiaomanjun.sleepdownschedule.feature.home.day.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.week.weekEditLandingImpactTransform
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityMatches
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CourseCopyFlightFrame(val center: Offset, val width: Float, val height: Float, val rotation: Float)

internal fun courseCopyGridTap(
    controller: CourseCopyController,
    week: Int,
    grid: CourseEditorWeekGrid,
    weekdays: List<Int>,
    periods: List<PeriodEntity>,
    point: Offset
): CourseCopyTap {
    val draft = controller.draft ?: return CourseCopyTap.Rejected
    val source = controller.source ?: return CourseCopyTap.Rejected
    if (week !in draft.weeks || !parityMatches(draft.weekParity, week)) {
        return controller.reject("请切回所选上课周")
    }
    if (weekdays.isEmpty() || point.x < 0 || point.x >= grid.width || point.y < 0 ||
        point.y >= grid.rowHeight * grid.periodIndexes.size) return CourseCopyTap.Rejected
    val localGrid = grid.copy(origin = Offset.Zero, scroll = null, scrollAtCapture = 0)
    val selected = controller.target?.takeIf {
        it.week == week && courseCopyBounds(it.course, localGrid, weekdays, periods).any { rect -> rect.contains(point) }
    }
    val physicalColumn = (point.x / (grid.width / weekdays.size)).toInt()
    val column = if (grid.rightToLeft) weekdays.lastIndex - physicalColumn else physicalColumn
    // Snap the card's center to the tap, including exact-time cards whose height varies by slot.
    // Keep conflict validation on the chosen target; never silently move it to another free slot.
    val centeredTarget = if (selected == null) {
        grid.periodIndexes.asSequence().mapNotNull { index ->
            val candidate = courseCopyAtSlot(draft, weekdays[column], index, periods)
                ?: return@mapNotNull null
            val card = courseCopyBounds(candidate, localGrid, weekdays, periods).firstOrNull()
                ?: return@mapNotNull null
            Triple(index, candidate, kotlin.math.abs(card.center.y - point.y))
        }.minByOrNull { it.third }
    } else null
    val periodIndex = selected?.periodIndex ?: centeredTarget?.first
        ?: return controller.reject("节次不足，请换个位置")
    val course = selected?.course ?: checkNotNull(centeredTarget).second
    val bounds = courseCopyBounds(course, grid, weekdays, periods)
    if (bounds.isEmpty()) return controller.reject("请换个位置")
    val sourceBounds = courseCopyBounds(source.course, grid, weekdays, periods).firstOrNull()
        ?: controller.sourceBounds(week, source.bounds)
    return controller.select(CourseCopyTarget(course, week, periodIndex, bounds, sourceBounds))
}

internal fun courseCopyFlightFrame(progress: Float, source: Rect, target: Rect): CourseCopyFlightFrame {
    val p = progress.coerceIn(0f, 1f)
    // The first beat buds a duplicate from the source; the eased arc accelerates into contact.
    val travel = ((p - 0.13f) / 0.87f).coerceIn(0f, 1f)
    val t = CubicBezierEasing(0.32f, 0f, 0.68f, 1f).transform(travel)
    val dx = target.center.x - source.center.x
    val dy = target.center.y - source.center.y
    val arc = (hypot(dx, dy) * 0.28f + source.width * 0.4f).coerceAtMost(source.width * 2.4f)
    val lift = sin(PI.toFloat() * t)
    val bloom = 1f + sin(PI.toFloat() * p) * 0.07f
    return CourseCopyFlightFrame(
        Offset(source.center.x + dx * t, source.center.y + dy * t - arc * lift),
        (source.width + (target.width - source.width) * t) * bloom,
        (source.height + (target.height - source.height) * t) * bloom,
        (dx / source.width.coerceAtLeast(1f)).coerceIn(-1f, 1f) * 6f * lift
    )
}

/** Called inside the grid; the placeholder scrolls with its real slot, without a second scene. */
@Composable
internal fun CourseCopyPlaceholder(
    controller: CourseCopyController?,
    week: Int,
    grid: CourseEditorWeekGrid,
    weekdays: List<Int>,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    onSelect: (Offset) -> Unit
) {
    val target = controller?.target?.takeIf { controller.active && it.week == week } ?: return
    val color = courseCardBaseColor(config, target.course)
    val density = LocalDensity.current
    courseCopyBounds(target.course, grid, weekdays, periods).forEach { bounds ->
        Box(
            Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .background(color.copy(alpha = 0.13f), RoundedRectangle(10.dp))
                .border(1.5.dp, color.copy(alpha = 0.85f), RoundedRectangle(10.dp))
                .clickable(enabled = !controller.busy, onClickLabel = "确认复制到此位置") { onSelect(bounds.center) }
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center
        ) {
            Text("待复制", color = glassForegroundColor(config), fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** A sibling of the recorded home: the floating card and notification never sample themselves. */
@Composable
internal fun CourseCopyOverlay(
    controller: CourseCopyController,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    cardBackdrop: Backdrop?
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); controller.reset() }
    }
    BackHandler(controller.active) { if (!controller.busy) controller.reset() }
    LaunchedEffect(controller.landed) {
        if (controller.landed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.boundsInRoot().topLeft }
            .then(if (controller.busy) Modifier.pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            } else Modifier)
    ) {
        val target = controller.target
        if (target != null && (controller.phase == CourseCopyPhase.Flying || controller.phase == CourseCopyPhase.AwaitingCard)) {
            val destination = target.bounds.first()
            val width = with(density) { destination.width.toDp() }
            val height = with(density) { destination.height.toDp() }
            Box(
                Modifier.size(width, height).graphicsLayer {
                    val frame = courseCopyFlightFrame(controller.flight.value, target.sourceBounds, destination)
                    val impact = if (controller.landed) weekEditLandingImpactTransform(controller.impact.value) else null
                    translationX = frame.center.x - hostOrigin.x - size.width / 2f
                    translationY = frame.center.y - hostOrigin.y - size.height / 2f
                    scaleX = frame.width / size.width * (impact?.scaleX ?: 1f)
                    scaleY = frame.height / size.height * (impact?.scaleY ?: 1f)
                    rotationZ = frame.rotation
                    alpha = (controller.flight.value / 0.065f).coerceIn(0f, 1f)
                }
            ) {
                CourseGlassCard(
                    backdrop = cardBackdrop, config = config, course = target.course,
                    modifier = Modifier.fillMaxSize(), shape = RoundedRectangle(10.dp)
                ) { WeekCourseOverlayCardContent(target.course, config) }
            }
        }
        CourseCopyNotice(
            visible = controller.active && !controller.landed,
            message = controller.message,
            busy = controller.busy,
            config = config,
            backdrop = backdrop,
            onCancel = controller::reset,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun CourseCopyNotice(
    visible: Boolean,
    message: String,
    busy: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    modifier: Modifier
) {
    val drop = remember { Animatable(0f) }
    val spread = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) mounted = true
        coroutineScope {
            launch { drop.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 440f)) }
            launch { spread.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.84f, stiffness = 360f)) }
        }
        if (!visible) mounted = false
    }
    if (!mounted) return
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat()
    val foreground = glassForegroundColor(config)
    val scope = rememberCoroutineScope()
    val interaction = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val closeInteraction = remember { MutableInteractionSource() }
    // Keep the final notification text while the capsule retracts.
    var displayedMessage by remember { mutableStateOf(message) }
    LaunchedEffect(visible, message) { if (visible) displayedMessage = message }
    Box(modifier.padding(top = with(density) { safeTop.toDp() } + 8.dp, start = 16.dp, end = 16.dp)) {
        GlassSurface(
            backdrop = backdrop, config = config, shape = Capsule(),
            tokens = GlassTokens.pill(intensity = 0.9f).copy(blur = 16.dp, surfaceAlpha = 0.26f),
            modifier = Modifier.widthIn(max = 380.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                translationY = -(safeTop * 0.55f + size.height * 0.6f) * (1f - drop.value)
                scaleX = 0.12f + 0.88f * spread.value
                scaleY = 0.32f + 0.68f * drop.value
                alpha = (drop.value * 4f).coerceIn(0f, 1f)
            }.liquidButtonVisualTransform(interaction)
                .then(interaction.gestureModifier)
        ) {
            Row(
                Modifier.animateContentSize(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
                    alignment = Alignment.Center
                ).then(interaction.modifier)
                    .padding(start = 14.dp, end = 4.dp).heightIn(min = 52.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Text(
                    displayedMessage, color = foreground, fontSize = 14.sp,
                    lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp)
                )
                Box(
                    Modifier.size(44.dp).clickable(
                        enabled = !busy,
                        interactionSource = closeInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onCancel
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = if (busy) null else "取消复制",
                        tint = foreground, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
