package com.xiaomanjun.sleepdownschedule.feature.importing.history

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.CouiMetaballBody
import com.itos.coui.effect.CouiMetaballRenderer
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.motion.rememberCouiMetaballFollowingTranslation
import com.itos.coui.swipereveal.CouiSwipeRevealDefaults
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryEntry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HistoryRowRadius = 18.dp
// ColorOS SystemUI notification_menu_icon_with_background_width / standard_height.
private val HistorySwipeActionWidth = 60.dp
private val HistorySwipeActionHeight = 68.dp

@Composable
internal fun AiImportHistorySwipeRow(
    entry: AiImportHistoryEntry,
    textColor: Color,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpen: (Rect) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val defaults = CouiSwipeRevealDefaults
    val actionSize = with(density) { Size(HistorySwipeActionWidth.toPx(), HistorySwipeActionHeight.toPx()) }
    val actionWidth = actionSize.width
    val followingPx = with(density) { defaults.menuFollowingDistance.toPx() }
    val restPx = actionWidth + with(density) { defaults.menuMinimumGap.toPx() }
    val openThreshold = with(density) { defaults.openTriggerDistance.toPx() }
    val maxVelocity = with(density) { defaults.itemMaxVelocity.toPx() }
    val motion = remember(entry.id) { Animatable(0f) }
    val collapse = remember(entry.id) { Animatable(0f) }
    var distance by remember(entry.id) { mutableFloatStateOf(0f) }
    var dragging by remember(entry.id) { mutableStateOf(false) }
    var deleting by remember(entry.id) { mutableStateOf(false) }
    var job by remember(entry.id) { mutableStateOf<Job?>(null) }
    var widthPx by remember(entry.id) { mutableFloatStateOf(0f) }
    var cardBounds by remember(entry.id) { mutableStateOf(Rect.Zero) }
    val latestDelete by rememberUpdatedState(onDelete)
    val latestRevealChange by rememberUpdatedState(onRevealChange)
    val atRest by remember { derivedStateOf { !dragging && !deleting && distance <= .5f } }
    val actionVisible by remember(actionWidth, followingPx) {
        derivedStateOf { !deleting && distance >= (actionWidth + followingPx) * defaults.fusedRevealStart }
    }
    val following by rememberCouiMetaballFollowingTranslation({ distance }, actionWidth, followingPx)

    fun settle(target: Float, velocity: Float = 0f) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            motion.snapTo(distance)
            motion.animateTo(target, defaults.itemSettleSpec, initialVelocity = velocity) {
                distance = value.coerceAtLeast(0f)
            }
        }
    }
    LaunchedEffect(revealed, restPx) {
        if (!dragging && !deleting && !revealed && distance > .5f) settle(0f)
    }
    LaunchedEffect(cardBounds, atRest) { onBoundsChanged(if (atRest) cardBounds else Rect.Zero) }

    Box(Modifier.clipToBounds().layout { measurable, constraints ->
        // Keep the card at its measured size while the viewport collapses after dismissal.
        val card = measurable.measure(constraints)
        val height = constraints.constrainHeight((card.height * (1f - collapse.value)).roundToInt())
        layout(card.width, height) { card.placeRelative(0, 0) }
    }.onSizeChanged { widthPx = it.width.toFloat() }) {
        val commitPx = (restPx + with(density) { 54.dp.toPx() }).coerceAtMost(widthPx * .9f)
        fun delete() {
            if (deleting) return
            deleting = true
            latestRevealChange(false)
            job?.cancel()
            job = scope.launch {
                motion.snapTo(distance)
                motion.animateTo(widthPx + actionWidth, tween(240, easing = FastOutSlowInEasing)) { distance = value }
                collapse.animateTo(1f, tween(145, easing = FastOutSlowInEasing))
                latestDelete()
            }
        }
        val active = !atRest
        val renderer = remember(active) { if (active && Build.VERSION.SDK_INT >= 33) CouiMetaballRenderer() else null }
        val mask = remember(active) { if (active && Build.VERSION.SDK_INT >= 33) CouiMetaballRenderer() else null }
        val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val shape = RoundedRectangle(HistoryRowRadius)
        Box(Modifier.pointerInput(entry.id, widthPx, restPx, rtl) {
            val tracker = VelocityTracker()
            var initialDistance = 0f
            var lastPosition: Offset? = null
            var crossed = false
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!deleting) {
                        job?.cancel()
                        dragging = true
                        initialDistance = distance
                        lastPosition = null
                        crossed = false
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
                        distance = (distance - direction * amount).coerceIn(0f, widthPx)
                        val commitReached = distance >= commitPx
                        if (commitReached && !crossed) haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        crossed = commitReached
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
                drawHistorySwipe(renderer, distance, actionSize, following, restPx, deleting, rtl, cardColor)
            }
            val cardSurface = when {
                !active -> Modifier.clip(shape).background(cardColor)
                mask != null -> Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawHistorySwipe(mask, distance, actionSize, following, restPx, deleting, rtl, Color.White, BlendMode.DstIn)
                    }
                else -> Modifier.drawWithContent {
                    clipRect(left = if (rtl) distance.coerceAtMost(size.width) else 0f,
                        right = if (rtl) size.width else (size.width - distance).coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
                }
            }
            Box(Modifier.then(cardSurface)
                .onGloballyPositioned { coordinates ->
                    cardBounds = coordinates.boundsInWindow().takeIf { bounds ->
                        bounds.width >= coordinates.size.width - 1 && bounds.height >= coordinates.size.height - 1
                    } ?: Rect.Zero
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !deleting,
                    onClick = {
                        if (distance > .5f || revealed) { settle(0f); latestRevealChange(false) }
                        else if (cardBounds.width > 0f) onOpen(cardBounds)
                    },
                ).semantics {
                    if (atRest) customActions = listOf(CustomAccessibilityAction("显示删除操作") {
                        latestRevealChange(true)
                        settle(restPx)
                        true
                    })
                }) {
                AiImportHistoryRowContent(
                    entry = entry,
                    textColor = textColor,
                    modifier = Modifier.graphicsLayer { translationX = -direction * distance },
                )
            }
            if (actionVisible) {
                val interaction = remember { MutableInteractionSource() }
                val dark = MaterialTheme.colorScheme.background.luminance() < .5f
                Box(Modifier.align(Alignment.CenterEnd).size(HistorySwipeActionWidth, HistorySwipeActionHeight).graphicsLayer {
                    translationX = direction * following
                    alpha = ((distance / (actionWidth + followingPx) - defaults.fusedRevealStart) /
                        (1f - defaults.fusedRevealStart)).coerceIn(0f, 1f)
                }.clip(shape).couiButtonFeedback(dark, interaction)
                    .clickable(interaction, null, onClick = ::delete), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_delete_history), "删除", Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun DrawScope.drawHistorySwipe(
    renderer: CouiMetaballRenderer?, distance: Float, actionSize: Size, following: Float,
    rest: Float, deleting: Boolean, rtl: Boolean, color: Color, blend: BlendMode = BlendMode.SrcOver,
) {
    val cardWidth = (size.width - distance).coerceAtLeast(0f)
    val actionWidth = (actionSize.width + (distance - rest).coerceAtLeast(0f)).coerceAtMost(size.width)
    val actionHeight = actionSize.height.coerceAtMost(size.height)
    val radius = HistoryRowRadius.toPx()
    val actionAlpha = if (deleting) (cardWidth / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
    scale(if (rtl) -1f else 1f, 1f, pivot = center) {
        val actionX = size.width - actionWidth + following
        if (Build.VERSION.SDK_INT >= 33 && renderer != null) {
            renderer.draw(
                scope = this,
                first = CouiMetaballBody(Offset(cardWidth / 2f, size.height / 2f), Size(cardWidth, size.height), radius),
                second = CouiMetaballBody(Offset(actionX + actionWidth / 2f, size.height / 2f),
                    Size(actionWidth, actionHeight), radius),
                color = color, blendRangePx = CouiSwipeRevealDefaults.metaballBlendRange.toPx(),
                fusionEnabled = distance in 0f..rest && !deleting,
                secondAlpha = actionAlpha, blendMode = blend, size = size,
            )
        } else {
            drawRoundRect(color, size = Size(cardWidth, size.height), cornerRadius = CornerRadius(radius))
            drawRoundRect(color.copy(alpha = actionAlpha), Offset(actionX, (size.height - actionHeight) / 2f),
                Size(actionWidth, actionHeight), CornerRadius(radius))
        }
    }
}
