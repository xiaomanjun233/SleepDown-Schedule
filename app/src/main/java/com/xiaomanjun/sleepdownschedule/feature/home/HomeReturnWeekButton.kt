package com.xiaomanjun.sleepdownschedule.feature.home

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingPair
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.navigation.CouiDockDefaults
import com.itos.coui.navigation.CouiDockStyle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import kotlin.math.roundToInt

// Calendar 17.3.70, phone with gesture navigation: 100dp - (8dp + (88dp + 56dp) / 2).
private val HomeReturnWeekGap = 20.dp

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
    val mounted by remember { derivedStateOf { progress.isRunning || progress.value > 0.001f } }
    val settled by remember { derivedStateOf { progress.value >= 0.999f && !progress.isRunning } }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val palette = LocalAdaptiveGlass.current
    val dark = LocalHomeMaterialStyle.current?.dark ?: !palette.lightGlass
    val dockAccent = LocalHomeDockAccent.current?.let(::Color)
    val glowDock = Build.VERSION.SDK_INT >= 34 && LocalVisualEffects.current.usesCouiDock
    val dockSpring = remember { CouiDockDefaults.attachmentSpring<Float>() }
    val buttonRenderer = if (Build.VERSION.SDK_INT >= 34) remember {
        lazy {
            try {
                CouiFloatingRenderer.standalone()
            } catch (failure: IllegalArgumentException) {
                Log.w("SleepDownCOUI", "Cannot compile return-week FAB", failure)
                null
            }
        }
    } else null
    val pairRenderer = if (Build.VERSION.SDK_INT >= 34 && glowDock && hasPhoneDock) remember {
        lazy {
            try {
                CouiFloatingRenderer.pair()
            } catch (failure: IllegalArgumentException) {
                Log.w("SleepDownCOUI", "Cannot compile return-week fusion", failure)
                null
            }
        }
    } else null
    // ColorOS Calendar's Theme.COUI.Blue and couiColorBar in both theme resources.
    val ink = if (dark) Color(0xFF5C9DFF) else Color(0xFF0080FF)
    val neutral = if (dark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    var root by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        val source = plusBounds().takeUnless { !hasPhoneDock || it.isEmpty }?.translate(-root.topLeft)
        val ready = !root.isEmpty && (!hasPhoneDock || source != null)
        val animation = if (glowDock && source != null) dockSpring else HomeDockSplitSpec
        LaunchedEffect(visible, ready, animation) {
            val target = if (visible && ready) 1f else 0f
            if (progress.value != target) {
                progress.animateTo(target, animation)
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
        fun positionProgress() = progress.value.coerceIn(0f, 1.08f)
        val hiddenOffset = Offset(0f, source?.center?.y?.minus(target.center.y) ?: 0f)
        fun offset() = hiddenOffset * (1f - positionProgress())

        if (Build.VERSION.SDK_INT >= 34 && !settled && source != null) {
            val pair = pairRenderer?.value
            if (pair != null) {
                ReturnWeekFusion(
                    source, target, buttonSize, backdrop, pair, dark,
                    dockAccent ?: homeDockImportButtonGlassColor(palette.lightGlass),
                    ink, ::positionProgress, ::offset
                )
                return@BoxWithConstraints
            }
        }
        val enabled = visible && settled
        val button = Modifier.absoluteOffset {
            IntOffset(target.left.roundToInt(), target.top.roundToInt())
        }.size(buttonSize).then(
            when {
                source == null -> Modifier.graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                !settled -> Modifier.clipBehindDockPlus(source.translate(-target.topLeft))
                else -> Modifier
            }
        ).then(if (enabled) Modifier.excludeParentPullGesture() else Modifier)

        ReturnWeekFab(button, backdrop, buttonRenderer?.value, dark, neutral, ink, enabled, ::offset, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onReturn()
        }) { ReturnWeekGlyph(ink) }
    }
}

// Keep the live dock button beneath the attachment, including its own glyph and press feedback.
private fun Modifier.clipBehindDockPlus(bounds: Rect): Modifier = drawWithCache {
    val mask = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(bounds.height / 2f))) }
    onDrawWithContent { clipPath(mask, ClipOp.Difference) { this@onDrawWithContent.drawContent() } }
}

@RequiresApi(34)
@Composable
private fun ReturnWeekFusion(
    source: Rect,
    target: Rect,
    buttonSize: Dp,
    backdrop: Backdrop,
    renderer: CouiFloatingRenderer,
    dark: Boolean,
    accent: Color,
    ink: Color,
    progress: () -> Float,
    offset: () -> Offset,
) {
    val density = LocalDensity.current
    val extension = with(density) { 4.dp.roundToPx() }
    val style = rememberReturnWeekStyle(dark, ink)
    val materials = remember(accent, dark, extension, style) {
        listOf(
            CouiFabDefaults.translucentMaterial(accent, dark, extension).copy(index = 1),
            style.material.copy(index = 2, extension = extension),
        )
    }
    val shadows = remember(style) {
        val shadow = style.shadow.copy(causticColor = Color.Transparent)
        listOf(shadow.copy(color = Color.Transparent), shadow)
    }
    val shape = CouiFabDefaults.circleShape
    val sourceLayer = rememberGraphicsLayer()
    val glyphLayer = rememberGraphicsLayer()
    val envelope = Rect(target.left, target.top, source.right, source.bottom)
    val origin = Offset(envelope.left.roundToInt().toFloat(), envelope.top.roundToInt().toFloat())
    val localSource = source.translate(-origin)
    val localTarget = target.translate(-origin)
    Box(
        Modifier.absoluteOffset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .size(with(density) { envelope.width.toDp() }, with(density) { envelope.height.toDp() })
            .clipBehindDockPlus(localSource)
            .couiFloatingPair(
                backdrop = rememberCouiGlassBackdrop(backdrop),
                renderer = renderer,
                materials = materials,
                shadows = shadows,
                shapes = listOf(shape, shape),
                contentLayers = listOf(sourceLayer, glyphLayer),
                contourGlowEnabled = LocalCouiContourGlowEnabled.current,
                foregroundComposition = true,
                verticalFusion = true,
            ) {
                val p = progress()
                val scale = lerp(0.5f, 1f, p)
                listOf(
                    CouiFloatingSurface(localSource, cornerWeight = 2f),
                    CouiFloatingSurface(
                        localTarget.translate(offset()), scaleX = scale, scaleY = scale,
                        cornerWeight = 2f, alpha = p.coerceAtMost(1f)
                    ),
                )
            },
    ) {
        Box(
            Modifier.size(buttonSize).drawWithContent {
                glyphLayer.record { this@drawWithContent.drawContent() }
            },
            contentAlignment = Alignment.Center,
        ) { ReturnWeekGlyph(ink) }
    }
}

@Composable
private fun ReturnWeekGlyph(ink: Color) {
    Text(
        "今", color = ink, fontSize = with(LocalDensity.current) { 22.dp.toSp() },
        fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.clearAndSetSemantics {}
    )
}

@RequiresApi(34)
@Composable
private fun rememberReturnWeekStyle(dark: Boolean, ink: Color): CouiDockStyle {
    val density = LocalDensity.current.density
    // Calendar NaviSearchView uses FRAMEWORK_BOTTOM_BAR, CAPSULE_3 and level-3b elevation.
    return remember(dark, density, ink) {
        CouiDockStyle.reference(dark, ink, ink, density, contourGlowEnabled = false)
    }
}

@Composable
private fun ReturnWeekFab(
    modifier: Modifier,
    backdrop: Backdrop,
    renderer: CouiFloatingRenderer?,
    dark: Boolean,
    neutral: Color,
    ink: Color,
    enabled: Boolean,
    offset: () -> Offset,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= 34 && renderer != null) {
        val style = rememberReturnWeekStyle(dark, ink)
        Fab(
            modifier = modifier, backdrop = rememberCouiGlassBackdrop(backdrop), renderer = renderer,
            onClick = onClick, dark = dark, material = style.material,
            shadow = style.shadow.copy(causticColor = Color.Transparent),
            foregroundComposition = true, contentColor = ink, enabled = enabled,
            spotlightPreset = SpotlightPreset.OpaqueMedium2,
            spotlightColor = if (dark) Color.White else Color.Black,
            appearanceOffset = offset, contentLabel = "回到本周", content = content
        )
        return
    }
    Box(
        modifier.graphicsLayer {
            val movement = offset()
            translationX = movement.x
            translationY = movement.y
        }.clip(CircleShape).background(neutral)
            .then(if (enabled) Modifier.semantics { contentDescription = "回到本周" }
                .clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}
