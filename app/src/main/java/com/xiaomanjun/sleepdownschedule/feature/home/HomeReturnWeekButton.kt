package com.xiaomanjun.sleepdownschedule.feature.home

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.lerp as lerpColor
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
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.navigation.CouiDockStyle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.glass.GlassDropletGeometry
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import com.xiaomanjun.sleepdownschedule.glass.sleepDownDropletGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import kotlin.math.roundToInt

// Calendar 17.3.70, phone with gesture navigation: 100dp - (8dp + (88dp + 56dp) / 2).
private val HomeReturnWeekGap = 20.dp
private val HomeReturnWeekAppear = tween<Float>(620, easing = CubicBezierEasing(0.35f, 0.08f, 0.27f, 1f))
private val HomeReturnWeekDisappear = tween<Float>(400, easing = HomeDropletEasing)

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
    val mounted by remember { derivedStateOf { progress.value > 0.001f } }
    val settled by remember { derivedStateOf { progress.value >= 0.999f && !progress.isRunning } }
    val bridgeVisible by remember { derivedStateOf { progress.value in 0.06f..0.92f } }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val palette = LocalAdaptiveGlass.current
    val dark = LocalHomeMaterialStyle.current?.dark ?: !palette.lightGlass
    val accent = LocalHomeDockAccent.current?.let(::Color)
        ?: homeDockImportButtonGlassColor(palette.lightGlass)
    // ColorOS Calendar's Theme.COUI.Blue and couiColorBar in both theme resources.
    val ink = if (dark) Color(0xFF5C9DFF) else Color(0xFF0080FF)
    val neutral = if (dark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    var root by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        val source = plusBounds().takeUnless { !hasPhoneDock || it.isEmpty }?.translate(-root.topLeft)
        val ready = !root.isEmpty && (!hasPhoneDock || source != null)
        LaunchedEffect(visible, ready) {
            val target = if (visible && ready) 1f else 0f
            if (progress.value != target) {
                progress.animateTo(target, if (target == 1f) HomeReturnWeekAppear else HomeReturnWeekDisappear)
            }
        }
        if (!ready || (!visible && !mounted)) return@BoxWithConstraints
        val diameter = source?.width ?: with(density) { 44.dp.toPx() }
        val radius = diameter / 2f
        val buttonSize = with(density) { diameter.toDp() }
        val target = with(density) {
            val centerX = source?.center?.x ?: (maxWidth - 16.dp).toPx() - radius
            val bottom = source?.top?.minus(HomeReturnWeekGap.toPx())
                ?: (maxHeight - safeBottom - 14.dp).toPx()
            Rect(centerX - radius, bottom - diameter, centerX + radius, bottom)
        }
        fun scale() = lerp(0.50f, 1f, progress.value)
        val hiddenOffset = Offset(0f, source?.center?.y?.minus(target.center.y) ?: 0f)
        fun offset() = hiddenOffset * (1f - progress.value)

        if (bridgeVisible && source != null) {
            val padding = with(density) { 14.dp.toPx() }
            val neck = with(density) { 24.dp.toPx() }
            val envelope = Rect(target.left, target.top, source.right, source.bottom).inflate(padding)
            val localSource = source.translate(-envelope.topLeft)
            val geometry: () -> GlassDropletGeometry = {
                val p = progress.value
                val center = target.center + offset() - envelope.topLeft
                val dropRadius = radius * scale()
                GlassDropletGeometry(
                    body = localSource, bodyRadius = radius,
                    drop = Rect(center - Offset(dropRadius, dropRadius), center + Offset(dropRadius, dropRadius)),
                    dropRadius = dropRadius,
                    joinRadius = neck * dropletReveal(0.06f, 0.34f, p) * (1f - dropletReveal(0.72f, 0.92f, p))
                )
            }
            Box(
                Modifier.absoluteOffset { IntOffset(envelope.left.roundToInt(), envelope.top.roundToInt()) }
                    .size(with(density) { envelope.width.toDp() }, with(density) { envelope.height.toDp() })
                    .drawWithCache {
                        val frame = geometry()
                        val circles = Path().apply {
                            addRoundRect(RoundRect(localSource, CornerRadius(radius)))
                            addRoundRect(RoundRect(frame.drop, CornerRadius(frame.dropRadius)))
                        }
                        onDrawWithContent { clipPath(circles, ClipOp.Difference) { this@onDrawWithContent.drawContent() } }
                    }
                    .sleepDownDropletGlass(
                        backdrop = backdrop, geometry = geometry, surfaceColor = accent,
                        dropColor = { lerpColor(accent, neutral.copy(alpha = 0.60f), dropletReveal(0.28f, 0.78f, progress.value)) },
                        blurRadius = 12.dp, lensHeight = 7.dp, lensAmount = 9.dp,
                        debugLabel = "HomeReturnWeekDroplet"
                    )
            )
        }
        val enabled = visible && settled
        val button = Modifier.absoluteOffset {
            IntOffset(target.left.roundToInt(), target.top.roundToInt())
        }.size(buttonSize).then(
            when {
                source == null -> Modifier.graphicsLayer { alpha = progress.value }
                !settled -> Modifier.drawWithContent {
                    if (target.center.y + offset().y - radius * scale() < source.top) {
                        val plusMask = Path().apply {
                            addRoundRect(RoundRect(source.translate(-target.topLeft), CornerRadius(radius)))
                        }
                        clipPath(plusMask, ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
                }
                else -> Modifier
            }
        ).then(if (enabled) Modifier.excludeParentPullGesture() else Modifier)

        ReturnWeekFab(button, backdrop, dark, neutral, ink, enabled, ::offset, ::scale,
            shadowAlpha = { dropletReveal(0.73f, 0.99f, progress.value) }, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onReturn()
        }) {
            Text(
                "今", color = ink, fontSize = with(density) { 22.dp.toSp() },
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                    alpha = dropletReveal(0.46f, 0.84f, progress.value)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            )
        }
    }
}

@Composable
private fun ReturnWeekFab(
    modifier: Modifier,
    backdrop: Backdrop,
    dark: Boolean,
    neutral: Color,
    ink: Color,
    enabled: Boolean,
    offset: () -> Offset,
    scale: () -> Float,
    shadowAlpha: () -> Float,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= 34) {
        val renderer = remember {
            try {
                CouiFloatingRenderer.standalone()
            } catch (failure: IllegalArgumentException) {
                android.util.Log.w("SleepDownCOUI", "Cannot compile return-week FAB", failure)
                null
            }
        }
        if (renderer != null) {
            val density = LocalDensity.current.density
            // Calendar NaviSearchView uses FRAMEWORK_BOTTOM_BAR, CAPSULE_3 and level-3b elevation.
            val style = remember(dark, density, ink) {
                CouiDockStyle.reference(dark, ink, ink, density, contourGlowEnabled = false)
            }
            Fab(
                modifier = modifier, backdrop = rememberCouiGlassBackdrop(backdrop), renderer = renderer,
                onClick = onClick, dark = dark, material = style.material,
                shadow = style.shadow.copy(causticColor = Color.Transparent),
                foregroundComposition = true, contentColor = ink, enabled = enabled,
                spotlightPreset = SpotlightPreset.OpaqueMedium2,
                spotlightColor = if (dark) Color.White else Color.Black,
                appearanceOffset = offset, appearanceScale = scale,
                appearanceShadowAlpha = shadowAlpha, contentLabel = "回到本周", content = content
            )
            return
        }
    }
    Box(
        modifier.graphicsLayer {
            val movement = offset()
            translationX = movement.x
            translationY = movement.y
            scaleX = scale()
            scaleY = scaleX
        }.clip(CircleShape).background(neutral)
            .then(if (enabled) Modifier.semantics { contentDescription = "回到本周" }
                .clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}
