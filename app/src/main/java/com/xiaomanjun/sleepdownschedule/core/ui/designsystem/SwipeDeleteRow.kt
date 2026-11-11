package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.CouiMetaballBody
import com.itos.coui.effect.CouiMetaballRenderer
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.motion.rememberCouiMetaballFollowingTranslation
import com.itos.coui.swipereveal.CouiSwipeRevealDefaults
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeRowRadius = 18.dp
// ColorOS SystemUI notification_menu_icon_with_background_width / standard_height.
private val SwipeActionWidth = 60.dp
private val SwipeActionHeight = 68.dp

internal data class SwipeDeleteCorners(val top: Dp, val bottom: Dp = top) {
    fun shape(): Shape = if (top == bottom) RoundedRectangle(top)
        else RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/** Shares the notification-style reveal, fusion and dismiss animation across lists. */
@Composable
internal fun SwipeDeleteRow(
    rowKey: Any,
    surfaceColor: Color,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onDelete: suspend () -> Unit,
    onDeleteFailed: (Exception) -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    slideContent: Boolean = true,
    confirmDelete: (suspend () -> Boolean)? = null,
    onSlideProgress: ((Float) -> Unit)? = null,
    rowCorners: (Float) -> SwipeDeleteCorners = { SwipeDeleteCorners(SwipeRowRadius) },
    onBoundsChanged: (Rect) -> Unit = {},
    content: @Composable (Modifier, Boolean, ((Rect) -> Unit) -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val defaults = CouiSwipeRevealDefaults
    val actionSize = with(density) { Size(SwipeActionWidth.toPx(), SwipeActionHeight.toPx()) }
    val actionWidth = actionSize.width
    val followingPx = with(density) { defaults.menuFollowingDistance.toPx() }
    val restPx = actionWidth + with(density) { defaults.menuMinimumGap.toPx() }
    val openThreshold = with(density) { defaults.openTriggerDistance.toPx() }
    val maxVelocity = with(density) { defaults.itemMaxVelocity.toPx() }
    val revealStart = (actionWidth + followingPx) * defaults.fusedRevealStart
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val motion = remember(rowKey) { Animatable(0f) }
    val collapse = remember(rowKey) { Animatable(0f) }
    var distance by remember(rowKey) { mutableFloatStateOf(0f) }
    var dragging by remember(rowKey) { mutableStateOf(false) }
    var deleting by remember(rowKey) { mutableStateOf(false) }
    var confirming by remember(rowKey) { mutableStateOf(false) }
    var pastDeleteThreshold by remember(rowKey) { mutableStateOf(false) }
    var job by remember(rowKey) { mutableStateOf<Job?>(null) }
    var widthPx by remember(rowKey) { mutableFloatStateOf(0f) }
    var cardBounds by remember(rowKey) { mutableStateOf(Rect.Zero) }
    val latestDelete by rememberUpdatedState(onDelete)
    val latestDeleteFailed by rememberUpdatedState(onDeleteFailed)
    val latestConfirmation by rememberUpdatedState(confirmDelete)
    val latestProgress by rememberUpdatedState(onSlideProgress)
    val latestRevealChange by rememberUpdatedState(onRevealChange)
    val atRest by remember { derivedStateOf { !dragging && !deleting && distance <= .5f } }
    val actionVisible by remember(revealStart) {
        derivedStateOf {
            distance >= revealStart && (!deleting || pastDeleteThreshold && distance < widthPx)
        }
    }
    val lidProgress by animateFloatAsState(
        targetValue = if (pastDeleteThreshold) 1f else 0f,
        animationSpec = tween(120, delayMillis = if (pastDeleteThreshold) 16 else 0, easing = FastOutSlowInEasing),
        label = "删除桶盖",
    )
    val following by rememberCouiMetaballFollowingTranslation({ distance }, actionWidth, followingPx)

    fun swipeProgress(): Float = (distance / restPx).coerceIn(0f, 1f)

    fun moveTo(position: Float) {
        distance = position.coerceAtLeast(0f)
        latestProgress?.invoke(swipeProgress())
    }

    fun settle(target: Float, velocity: Float = 0f) {
        job?.cancel()
        pastDeleteThreshold = false
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            motion.snapTo(distance)
            motion.animateTo(target, defaults.itemSettleSpec, initialVelocity = velocity) {
                moveTo(value)
            }
        }
    }
    LaunchedEffect(revealed, restPx) {
        if (!dragging && !confirming && !deleting && !revealed && distance > .5f) settle(0f)
    }
    LaunchedEffect(swipeEnabled) {
        if (!swipeEnabled && !deleting) {
            job?.cancel()
            dragging = false
            pastDeleteThreshold = false
            moveTo(0f)
        }
    }
    LaunchedEffect(cardBounds, atRest, swipeEnabled) {
        onBoundsChanged(if (atRest && swipeEnabled) cardBounds else Rect.Zero)
    }

    Box(modifier.clipToBounds().layout { measurable, constraints ->
        // Keep the card at its measured size while the viewport collapses after dismissal.
        val card = measurable.measure(constraints)
        val height = constraints.constrainHeight((card.height * (1f - collapse.value)).roundToInt())
        layout(card.width, height) { card.placeRelative(0, 0) }
    }.onSizeChanged { widthPx = it.width.toFloat() }) {
        val commitPx = (restPx + with(density) { 54.dp.toPx() }).coerceAtMost(widthPx * .9f)
        fun delete() {
            if (deleting || confirming) return
            confirming = true
            job?.cancel()
            job = scope.launch {
                try {
                    if (latestConfirmation?.invoke() == false) {
                        pastDeleteThreshold = false
                        latestRevealChange(false)
                        motion.snapTo(distance)
                        motion.animateTo(0f, defaults.itemSettleSpec) { moveTo(value) }
                        confirming = false
                        return@launch
                    }
                    deleting = true
                    confirming = false
                    latestRevealChange(false)
                    motion.snapTo(distance)
                    motion.animateTo(widthPx + actionWidth, tween(240, easing = FastOutSlowInEasing)) { moveTo(value) }
                    collapse.animateTo(1f, tween(145, easing = FastOutSlowInEasing)) {
                        latestProgress?.invoke(1f - value)
                    }
                    latestDelete()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    pastDeleteThreshold = false
                    moveTo(0f)
                    collapse.animateTo(0f, defaults.itemSettleSpec)
                    confirming = false
                    deleting = false
                    latestDeleteFailed(failure)
                }
            }
        }

        fun openOrClose(onOpen: (Rect) -> Unit) {
            if (deleting || confirming) return
            if (distance > .5f || revealed) {
                settle(0f)
                latestRevealChange(false)
            } else {
                onOpen(cardBounds)
            }
        }

        val active = !atRest && (swipeEnabled || deleting)
        val renderer = remember(active) { if (active && Build.VERSION.SDK_INT >= 33) CouiMetaballRenderer() else null }
        val mask = remember(active) { if (active && Build.VERSION.SDK_INT >= 33) CouiMetaballRenderer() else null }
        val actionShape = RoundedRectangle(SwipeRowRadius)
        Box(Modifier.pointerInput(rowKey, widthPx, restPx, rtl, swipeEnabled) {
            if (!swipeEnabled) return@pointerInput
            val tracker = VelocityTracker()
            var initialDistance = 0f
            var lastPosition: Offset? = null
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!deleting && !confirming) {
                        job?.cancel()
                        dragging = true
                        initialDistance = distance
                        lastPosition = null
                        pastDeleteThreshold = false
                        tracker.resetTracking()
                        latestRevealChange(true)
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (dragging && !deleting) {
                        change.consume()
                        if (lastPosition == null) tracker.addPosition(change.previousUptimeMillis, change.previousPosition)
                        tracker.addPosition(change.uptimeMillis, change.position)
                        lastPosition = change.position
                        moveTo((distance - direction * amount).coerceIn(0f, widthPx))
                        val commitReached = distance >= commitPx
                        if (commitReached && !pastDeleteThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }
                        pastDeleteThreshold = commitReached
                    }
                },
                onDragEnd = {
                    if (dragging && !deleting) {
                        dragging = false
                        if (distance >= commitPx) delete()
                        else {
                            lastPosition?.let { tracker.addPosition(SystemClock.uptimeMillis(), it) }
                            val velocity = if (lastPosition == null) 0f
                                else (-direction * tracker.calculateVelocity().x).coerceIn(-maxVelocity, maxVelocity)
                            val returning = initialDistance >= restPx * .8f && initialDistance - distance >= openThreshold
                            val open = !returning && distance >= openThreshold
                            settle(if (open) restPx else 0f, velocity)
                            latestRevealChange(open)
                        }
                    }
                },
                onDragCancel = {
                    if (dragging) {
                        dragging = false
                        val open = initialDistance >= restPx * .8f
                        settle(if (open) restPx else 0f)
                        latestRevealChange(open)
                    }
                },
            )
        }) {
            if (active) Canvas(Modifier.matchParentSize()) {
                drawSwipeSurface(renderer, distance, actionSize, following, restPx, deleting, rtl,
                    surfaceColor, rowCorners(swipeProgress()))
            }
            val cardContentClip = Modifier.drawWithContent {
                clipRect(left = if (rtl) distance.coerceAtMost(size.width) else 0f,
                    right = if (rtl) size.width else (size.width - distance).coerceAtLeast(0f)) {
                    this@drawWithContent.drawContent()
                }
            }
            val cardSurface = when {
                !active -> Modifier.graphicsLayer {
                    shape = rowCorners(0f).shape()
                    clip = true
                }.background(surfaceColor)
                mask != null -> Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawSwipeSurface(mask, distance, actionSize, following, restPx, deleting, rtl,
                            Color.White, rowCorners(swipeProgress()), BlendMode.DstIn)
                    }
                    // When content stays anchored, the delete surface must not reveal the row's trailing icon.
                    .then(if (slideContent) Modifier else cardContentClip)
                else -> cardContentClip
            }
            Box(Modifier.then(cardSurface)
                .onGloballyPositioned { coordinates ->
                    cardBounds = coordinates.boundsInWindow().takeIf { bounds ->
                        bounds.width >= coordinates.size.width - 1 && bounds.height >= coordinates.size.height - 1
                    } ?: Rect.Zero
                }
                .semantics {
                    if (atRest && swipeEnabled && !confirming) customActions = listOf(
                        CustomAccessibilityAction("显示删除操作") {
                            latestRevealChange(true)
                            settle(restPx)
                            true
                        }
                    )
                }) {
                val contentMotion = if (slideContent) Modifier.graphicsLayer {
                    translationX = if (active) -direction * distance else 0f
                } else Modifier
                content(
                    contentMotion,
                    deleting || confirming,
                    ::openOrClose,
                )
            }
            if (actionVisible && swipeEnabled) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.align(Alignment.CenterEnd).layout { measurable, constraints ->
                    val width = swipeActionWidth(actionWidth, distance, restPx, widthPx).roundToInt()
                        .coerceAtMost(constraints.maxWidth)
                    val height = actionSize.height.roundToInt().coerceAtMost(constraints.maxHeight)
                    val action = measurable.measure(Constraints.fixed(width, height))
                    layout(action.width, action.height) { action.placeRelative(0, 0) }
                }.graphicsLayer {
                    translationX = direction * following
                    val revealAlpha = ((distance / (actionWidth + followingPx) - defaults.fusedRevealStart) /
                        (1f - defaults.fusedRevealStart)).coerceIn(0f, 1f)
                    val deleteAlpha = if (deleting) (1f - distance / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
                    alpha = revealAlpha * deleteAlpha
                }.clip(actionShape).couiButtonFeedback(dark, interaction, enabled = !confirming && !deleting)
                    .clickable(interaction, null, enabled = !confirming && !deleting, onClick = ::delete),
                    contentAlignment = Alignment.Center) {
                    SwipeDeleteIcon { lidProgress }
                }
            }
        }
    }
}

@Composable
private fun SwipeDeleteIcon(openProgress: () -> Float) {
    Icon(
        painterResource(R.drawable.ic_delete_history),
        "删除",
        Modifier.size(24.dp).drawWithContent {
            val gap = size.height * (7.3f / 24f)
            clipRect(top = gap) { this@drawWithContent.drawContent() }
            // The system icon rotates its lid 18 degrees about the right end of the rim.
            val hinge = Offset(size.width * (19.5f / 24f), size.height * (6.55f / 24f))
            rotate(18f * openProgress(), hinge) {
                clipRect(bottom = gap) { this@drawWithContent.drawContent() }
            }
        },
        tint = MaterialTheme.colorScheme.error
    )
}

private fun swipeActionWidth(base: Float, distance: Float, rest: Float, viewport: Float): Float =
    (base + (distance - rest).coerceAtLeast(0f)).coerceAtMost(viewport)

private fun DrawScope.drawSwipeSurface(
    renderer: CouiMetaballRenderer?, distance: Float, actionSize: Size, following: Float,
    rest: Float, deleting: Boolean, rtl: Boolean, color: Color, corners: SwipeDeleteCorners,
    blend: BlendMode = BlendMode.SrcOver,
) {
    val cardWidth = (size.width - distance).coerceAtLeast(0f)
    val actionWidth = swipeActionWidth(actionSize.width, distance, rest, size.width)
    val actionHeight = actionSize.height.coerceAtMost(size.height)
    val radius = SwipeRowRadius.toPx()
    val topRadius = corners.top.toPx()
    val bottomRadius = corners.bottom.toPx()
    val actionAlpha = if (deleting) (cardWidth / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
    scale(if (rtl) -1f else 1f, 1f, pivot = center) {
        val actionX = size.width - actionWidth + following
        if (Build.VERSION.SDK_INT >= 33 && renderer != null) {
            renderer.draw(
                scope = this,
                first = CouiMetaballBody(Offset(cardWidth / 2f, size.height / 2f), Size(cardWidth, size.height), topRadius),
                second = CouiMetaballBody(Offset(actionX + actionWidth / 2f, size.height / 2f),
                    Size(actionWidth, actionHeight), radius),
                color = color, blendRangePx = CouiSwipeRevealDefaults.metaballBlendRange.toPx(),
                fusionEnabled = distance in 0f..rest && !deleting,
                secondAlpha = actionAlpha, blendMode = blend, size = size,
                firstBottomCornerRadius = bottomRadius,
            )
        } else {
            if (topRadius == bottomRadius) {
                drawRoundRect(color, size = Size(cardWidth, size.height), cornerRadius = CornerRadius(topRadius))
            } else {
                val limit = minOf(cardWidth, size.height) / 2f
                val top = CornerRadius(topRadius.coerceAtMost(limit))
                val bottom = CornerRadius(bottomRadius.coerceAtMost(limit))
                val card = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, 0f, cardWidth, size.height), top, top, bottom, bottom))
                }
                drawPath(card, color)
            }
            drawRoundRect(color.copy(alpha = actionAlpha), Offset(actionX, (size.height - actionHeight) / 2f),
                Size(actionWidth, actionHeight), CornerRadius(radius))
        }
    }
}
