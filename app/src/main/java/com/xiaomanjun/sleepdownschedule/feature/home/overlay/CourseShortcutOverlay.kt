package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.AddMenuAction
import com.xiaomanjun.sleepdownschedule.app.ui.HomeMode
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class CourseShortcutRequest(
    val course: CourseEntity,
    val week: Int,
    val bounds: Rect,
    val cornerPx: Float,
    val pivotX: Float,
    val enterEditMode: () -> Unit
)

internal val LocalCourseShortcuts = staticCompositionLocalOf<CourseShortcutController?> { null }

@Stable
internal class CourseShortcutController(private val scope: CoroutineScope) {
    var request by mutableStateOf<CourseShortcutRequest?>(null)
        private set
    var copyRequest by mutableStateOf<CourseShortcutRequest?>(null)
    var closing by mutableStateOf(false)
        private set
    val progress = Animatable(0f)
    val cardScale = Animatable(1f)
    private var motion: Job? = null

    fun open(value: CourseShortcutRequest) {
        motion?.cancel()
        request = value
        closing = false
        motion = scope.launch {
            progress.snapTo(0f)
            cardScale.snapTo(1f)
            // Press first. The return lift and menu expansion then share the same start frame.
            cardScale.animateTo(0.98f, tween(85, easing = FastOutSlowInEasing))
            launch {
                cardScale.animateTo(1.015f, keyframes {
                    durationMillis = 215
                    0.98f at 0 using FastOutSlowInEasing
                    1.02f at 135 using FastOutSlowInEasing
                    1.015f at 215
                })
            }
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 380f))
        }
    }

    fun close(afterClose: (() -> Unit)? = null) {
        if (request == null || closing) return
        closing = true
        motion?.cancel()
        motion = scope.launch {
            launch { cardScale.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
            progress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            request = null
            closing = false
            afterClose?.invoke()
        }
    }

    // The source pointer owns the whole gesture. Retire the menu without waiting before
    // handing that pointer to the existing week drag controller.
    fun takeOverDrag() {
        motion?.cancel()
        request = null
        closing = false
        motion = scope.launch {
            progress.animateTo(0f, tween(100))
            cardScale.snapTo(1f)
        }
    }

    fun reset() {
        motion?.cancel()
        request = null
        copyRequest = null
        closing = false
        motion = scope.launch {
            progress.snapTo(0f)
            cardScale.snapTo(1f)
        }
    }
}

internal data class CourseShortcutPlacement(val bounds: Rect, val pivotX: Float, val belowAnchor: Boolean) {
    val pivotY: Float get() = if (belowAnchor) 0f else 1f
}

/** Only the single middle column opens centrally; even column counts have no central column. */
internal fun courseShortcutPivot(columnIndex: Int, columnCount: Int, layoutDirection: LayoutDirection): Float {
    val pivot = when {
        columnIndex * 2 < columnCount - 1 -> 0f
        columnIndex * 2 > columnCount - 1 -> 1f
        else -> 0.5f
    }
    return if (layoutDirection == LayoutDirection.Rtl) 1f - pivot else pivot
}

internal fun courseShortcutPlacement(anchor: Rect, available: Rect, width: Float, height: Float, gap: Float, pivot: Float): CourseShortcutPlacement {
    val w = width.coerceAtMost(available.width).coerceAtLeast(1f)
    val h = height.coerceAtMost(available.height).coerceAtLeast(1f)
    val x = (anchor.left + anchor.width * pivot - w * pivot)
        .coerceIn(available.left, (available.right - w).coerceAtLeast(available.left))
    val belowAnchor = anchor.top - h - gap < available.top
    val y = (if (belowAnchor) anchor.bottom + gap else anchor.top - h - gap)
        .coerceIn(available.top, (available.bottom - h).coerceAtLeast(available.top))
    return CourseShortcutPlacement(Rect(x, y, x + w, y + h), pivot, belowAnchor)
}

internal fun copiedShortcutCourse(course: CourseEntity, singleWeek: Int?): CourseEntity = course.copy(
    id = 0,
    weeks = singleWeek?.let(::listOf) ?: course.weeks,
    weekParity = if (singleWeek != null) WeekParity.ALL else course.weekParity
)

/** Root popup sibling of the complete Home underlay; neither card nor menu samples itself. */
@Composable
internal fun CourseShortcutOverlay(
    controller: CourseShortcutController,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    cardBackdrop: Backdrop?,
    onEdit: (CourseShortcutRequest) -> Unit,
    onCopy: (CourseShortcutRequest, CourseEntity) -> Unit,
    onRemove: (CourseEntity, Int) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.reset()
        }
    }
    val request = controller.request
    BackHandler(enabled = request != null) { controller.close() }
    if (request != null) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val safe = WindowInsets.safeDrawing
        var hostBounds by remember { mutableStateOf<Rect?>(null) }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .onGloballyPositioned { hostBounds = it.boundsInRoot() }
                .pointerInput(controller) { detectTapGestures { controller.close() } }
        ) {
            val host = hostBounds ?: return@BoxWithConstraints
            val available = with(density) {
                Rect(
                    safe.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat() + 8.dp.toPx(),
                    safe.getTop(density).toFloat() + 8.dp.toPx(),
                    maxWidth.toPx() - safe.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr) - 8.dp.toPx(),
                    maxHeight.toPx() - safe.getBottom(density) - 8.dp.toPx()
                )
            }
            val source = request.bounds.translate(-host.topLeft)
            val rowHeight = 40.dp * density.fontScale.coerceAtLeast(1f)
            val placement = with(density) {
                val readableWidth = (120.dp + 64.dp * density.fontScale.coerceAtLeast(1f)).toPx()
                courseShortcutPlacement(source, available, maxOf(source.width + 40.dp.toPx(), readableWidth),
                    (rowHeight * 4f + CourseShortcutContentPaddingDp.dp * 2f).toPx(), 10.dp.toPx(), request.pivotX)
            }
            Box(
                Modifier.offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                    .size(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                    .graphicsLayer {
                        scaleX = controller.cardScale.value
                        scaleY = controller.cardScale.value
                        translationY = (1f - controller.cardScale.value) * with(density) { 100.dp.toPx() }
                    }
            ) {
                CourseGlassCard(
                    backdrop = cardBackdrop, config = config, course = request.course,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedRectangle(with(density) { request.cornerPx.toDp() }), onClick = null
                ) { WeekCourseOverlayCardContent(request.course, config) }
            }
            val actions = remember(request, controller, onEdit, onRemove) {
                listOf(
                    AddMenuAction(R.drawable.ic_edit, "快速编辑模式") {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close(request.enterEditMode)
                    },
                    AddMenuAction(label = "编辑单节课", imageVector = Icons.Rounded.EditNote) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close { onEdit(request) }
                    },
                    AddMenuAction(label = "复制课程", imageVector = Icons.Rounded.ContentCopy) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close { controller.copyRequest = request }
                    },
                    AddMenuAction(
                        R.drawable.ic_trash, "移除课程",
                        iconTint = Color(0xFFFF453A), textTint = Color(0xFFFF453A)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close { onRemove(request.course, request.week) }
                    }
                )
            }
            val target = placement.bounds
            HomeAddMenuMorphPanel(
                backdrop = backdrop, config = config, actions = actions,
                homeMode = HomeMode.Week, onHomeModeChange = {},
                targetSizeProvider = { IntSize(target.width.roundToInt(), target.height.roundToInt()) },
                surfaceAlphaProvider = { controller.progress.value.coerceIn(0f, 1f) },
                contentAlphaProvider = { controller.progress.value.coerceIn(0f, 1f) },
                interactive = !controller.closing,
                // The selection has a 1dp vertical inset; keep its radius concentric with the shell.
                shape = RoundedRectangle(rowHeight / 2 + CourseShortcutContentPaddingDp.dp),
                showModeSwitch = false, actionItemHeight = rowHeight, compactActions = true,
                shadowEnabled = false,
                modifier = Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                    .graphicsLayer {
                        val progress = controller.progress.value
                        transformOrigin = TransformOrigin(placement.pivotX, placement.pivotY)
                        scaleX = 0.18f + 0.82f * progress
                        scaleY = 0.18f + 0.82f * progress
                        translationX = (source.left + source.width * placement.pivotX -
                            target.left - target.width * placement.pivotX) * (1f - progress)
                        val originY = if (placement.belowAnchor) source.bottom else source.top
                        val targetY = if (placement.belowAnchor) target.top else target.bottom
                        translationY = (originY - targetY) * (1f - progress)
                        rotationZ = (placement.pivotX - 0.5f) * 6f * (1f - progress) *
                            (if (placement.belowAnchor) -1f else 1f)
                    }
            )
        }
    }
    controller.copyRequest?.let { copy ->
        LiquidAlertDialog(
            title = "复制课程",
            message = "复制“${copy.course.name}”的全部上课周，还是仅复制第${copy.week}周？选好范围后，在课表中点击目标位置，再点一次确认。",
            actions = listOf(
                LiquidAlertAction("所有上课周", LiquidAlertActionStyle.Primary) {
                    controller.copyRequest = null
                    onCopy(copy, copiedShortcutCourse(copy.course, null))
                },
                LiquidAlertAction("仅第${copy.week}周", LiquidAlertActionStyle.Secondary) {
                    controller.copyRequest = null
                    onCopy(copy, copiedShortcutCourse(copy.course, copy.week))
                },
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { controller.copyRequest = null }
            ),
            onDismissRequest = { controller.copyRequest = null },
            backdrop = backdrop, config = config
        )
    }
}
