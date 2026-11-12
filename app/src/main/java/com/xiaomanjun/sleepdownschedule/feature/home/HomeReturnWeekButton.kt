package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.glass.GlassDropletGeometry
import com.xiaomanjun.sleepdownschedule.glass.sleepDownDropletGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import kotlin.math.roundToInt

// Calendar 17.3.70, phone with gesture navigation: 100dp - (8dp + (88dp + 56dp) / 2).
private val HomeReturnWeekGap = 20.dp

/** A separate action in the bottom corner; only its transition borrows the import FAB. */
@Composable
internal fun HomeReturnWeekButton(
    visible: Boolean,
    plusBounds: () -> Rect,
    hasPhoneDock: Boolean,
    safeBottom: Dp,
    backdrop: Backdrop,
    onReturn: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, tween(280, easing = HomeDropletEasing))
    }
    val mounted by remember { derivedStateOf { progress.value > 0.001f } }
    if (!visible && !mounted) return
    val moving by remember { derivedStateOf { progress.value < 0.999f } }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val palette = LocalAdaptiveGlass.current
    val accent = LocalHomeDockAccent.current?.let(::Color)
        ?: homeDockImportButtonGlassColor(palette.lightGlass)
    val neutral = if (palette.lightGlass) Color.White.copy(alpha = 0.55f) else Color(0xFF141418).copy(alpha = 0.52f)
    var root by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        if (root.isEmpty) return@BoxWithConstraints
        val source = plusBounds().takeUnless { !hasPhoneDock || it.isEmpty }?.translate(-root.topLeft)
        if (hasPhoneDock && source == null) return@BoxWithConstraints
        val diameter = source?.width ?: with(density) { 44.dp.toPx() }
        val radius = diameter / 2f
        val buttonSize = with(density) { diameter.toDp() }
        val target = with(density) {
            val centerX = source?.center?.x ?: (maxWidth - 16.dp).toPx() - radius
            val bottom = source?.top?.minus(HomeReturnWeekGap.toPx())
                ?: (maxHeight - safeBottom - 14.dp).toPx()
            Rect(centerX - radius, bottom - diameter, centerX + radius, bottom)
        }
        val morphPadding = with(density) { 10.dp.toPx() }
        val envelope = if (moving && source != null) Rect(
            minOf(target.left, source.left) - morphPadding, minOf(target.top, source.top) - morphPadding,
            maxOf(target.right, source.right) + morphPadding, maxOf(target.bottom, source.bottom) + morphPadding
        ) else target
        val localTarget = target.translate(-envelope.topLeft)
        val localSource = source?.translate(-envelope.topLeft)
        val geometry: () -> GlassDropletGeometry = {
            val p = progress.value.coerceIn(0f, 1f)
            val start = localSource?.center ?: localTarget.center
            val center = Offset(lerp(start.x, localTarget.center.x, p), lerp(start.y, localTarget.center.y, p))
            val dropRadius = radius * lerp(0.48f, 1f, p)
            GlassDropletGeometry(
                body = if (moving) localSource ?: Rect.Zero else Rect.Zero,
                bodyRadius = if (moving) (source?.width ?: 0f) / 2f else 0f,
                drop = Rect(center - Offset(dropRadius, dropRadius), center + Offset(dropRadius, dropRadius)),
                dropRadius = dropRadius,
                joinRadius = if (moving && source != null) morphPadding * (1f - dropletReveal(0.46f, 0.86f, p)) else 0f
            )
        }
        Box(
            Modifier.absoluteOffset { IntOffset(envelope.left.roundToInt(), envelope.top.roundToInt()) }
                .size(with(density) { envelope.width.toDp() }, with(density) { envelope.height.toDp() })
                .drawWithCache {
                    val circle = if (moving && localSource != null) Path().apply {
                        addRoundRect(RoundRect(localSource, CornerRadius(localSource.width / 2f)))
                    } else null
                    // The actual FAB owns the center and its plus glyph during the merge.
                    onDrawWithContent {
                        if (circle != null) clipPath(circle, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                        else drawContent()
                    }
                }
                .sleepDownDropletGlass(
                    backdrop = backdrop, geometry = geometry, surfaceColor = accent,
                    dropColor = { lerp(accent, neutral, dropletReveal(0.35f, 0.92f, progress.value)) },
                    blurRadius = 8.dp, lensHeight = 7.dp, lensAmount = 9.dp,
                    debugLabel = "HomeReturnWeekDroplet"
                )
        ) {
            Box(
                Modifier.absoluteOffset {
                    val center = geometry().drop.center
                    val half = 16.dp.toPx()
                    IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt())
                }.size(32.dp).graphicsLayer {
                    alpha = dropletReveal(0.36f, 0.87f, progress.value)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.clearAndSetSemantics {},
                contentAlignment = Alignment.Center
            ) {
                Text("今", color = palette.contentColor, fontSize = with(density) { 22.dp.toSp() },
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (visible && !moving) {
            Box(
                Modifier.absoluteOffset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(buttonSize)
                    .excludeParentPullGesture()
                    .semantics { contentDescription = "回到本周" }
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReturn()
                    }
            )
        }
    }
}
