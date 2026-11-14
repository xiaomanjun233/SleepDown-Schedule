package com.xiaomanjun.sleepdownschedule.transition.legacy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import com.xiaomanjun.sleepdownschedule.glass.liquidPreloadedContentAlpha
import com.xiaomanjun.sleepdownschedule.transition.AnchoredLegacyProfileId
import kotlin.math.abs
import kotlin.math.pow

internal enum class PageMorphStyle { Card, Button, FloatingAdd, Menu, Welcome }

internal fun pageMorphStyle(
    profile: AnchoredLegacyProfileId?, bounds: Rect?, radius: Float, floatingAddSource: Boolean,
): PageMorphStyle =
    when {
        floatingAddSource -> PageMorphStyle.FloatingAdd
        profile == AnchoredLegacyProfileId.Welcome -> PageMorphStyle.Welcome
        bounds != null && abs(bounds.width - bounds.height) <= 2f &&
            radius >= minOf(bounds.width, bounds.height) * 0.40f -> PageMorphStyle.Button
        profile == AnchoredLegacyProfileId.HomeMenuDestination -> PageMorphStyle.Menu
        else -> PageMorphStyle.Card
    }

private fun easeOut(power: Float) = Easing { t -> 1f - (1f - t).pow(power) }

internal class SharedViewFallbackMotion(style: PageMorphStyle) {
    // The floating add opening applies the panel's curves in its geometry. Ordinary button
    // returns apply easing here so the aperture can narrow before its center reaches the anchor.
    val open = when (style) {
        PageMorphStyle.Card -> tween<Float>(540, easing = easeOut(4.5f))
        PageMorphStyle.Button -> tween(440, easing = easeOut(4f))
        PageMorphStyle.FloatingAdd -> tween(AnchoredPanelMotion.OpenDurationMillis, easing = LinearEasing)
        PageMorphStyle.Menu -> tween(490, easing = easeOut(4f))
        PageMorphStyle.Welcome -> tween(420, easing = CubicBezierEasing(0.30f, 0.50f, 0.20f, 1f))
    }
    val openVertical = when (style) {
        PageMorphStyle.Card -> tween<Float>(500, easing = easeOut(5f))
        PageMorphStyle.Button -> tween(405, easing = easeOut(5f))
        PageMorphStyle.FloatingAdd -> open
        PageMorphStyle.Menu -> tween(450, easing = easeOut(4.5f))
        PageMorphStyle.Welcome -> tween(400, easing = CubicBezierEasing(0.22f, 0.52f, 0.19f, 1f))
    }
    private val closeMillis = when (style) {
        PageMorphStyle.Card -> 320
        PageMorphStyle.Button, PageMorphStyle.FloatingAdd -> 285
        PageMorphStyle.Menu -> 310
        PageMorphStyle.Welcome -> 280
    }
    val close = tween<Float>(closeMillis, easing = easeOut(3f))
    val closeVertical = tween<Float>(closeMillis, easing = easeOut(2f))
    val backgroundOpen = tween<Float>(open.durationMillis, easing = FastOutSlowInEasing)
    val backgroundClose = tween<Float>(closeMillis, easing = FastOutSlowInEasing)
}

internal val UnanchoredPageMotion = tween<Float>(240, easing = FastOutSlowInEasing)

internal fun unanchoredPageStart(full: Rect): Rect = Rect(
    full.left + full.width * 0.04f, full.top + full.height * 0.04f,
    full.right - full.width * 0.04f, full.bottom - full.height * 0.04f,
)

internal fun sharedViewRect(source: Rect, target: Rect, progress: Float, verticalProgress: Float): Rect {
    if (progress == 0f && verticalProgress == 0f) return source
    if (progress == 1f && verticalProgress == 1f) return target
    val width = lerp(source.width, target.width, progress)
    val height = lerp(source.height, target.height, progress)
    val x = lerp(source.center.x, target.center.x, progress)
    val y = lerp(source.center.y, target.center.y, verticalProgress)
    return Rect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
}

internal fun floatingAddReturnGeometry(
    source: Rect,
    sourceRadiusPx: Float,
    from: HomeAnchoredMorphGeometry,
    progress: Float,
    verticalProgress: Float,
): HomeAnchoredMorphGeometry {
    if (progress == 1f && verticalProgress == 1f) return from
    val expansion = from.expansionProgress * progress
    val contentAlpha = liquidPreloadedContentAlpha(expansion, earlyContentHandoff = true)
    return from.copy(
        rect = sharedViewRect(source, from.rect, progress, verticalProgress),
        cornerRadiusPx = lerp(sourceRadiusPx, from.cornerRadiusPx, progress),
        sourceAlpha = 1f - contentAlpha,
        contentAlpha = contentAlpha,
        pathProgress = verticalProgress,
        expansionProgress = expansion,
    )
}

internal fun sharedViewGeometry(
    source: Rect,
    target: Rect,
    sourceRadiusPx: Float,
    targetRadiusPx: Float,
    progress: Float,
    verticalProgress: Float,
    style: PageMorphStyle,
): HomeAnchoredMorphGeometry {
    if (style == PageMorphStyle.FloatingAdd) {
        val phase = progress.coerceIn(0f, 1f)
        val position = AnchoredPanelMotion.OpenPositionEasing.transform(phase)
        val size = AnchoredPanelMotion.OpenSizeEasing.transform(phase)
        // Keep the position's head start local to the button. Let the center of the
        // expanding page cover the remaining distance as the size catches up.
        val rise = minOf(source.height, (source.top - target.top).coerceAtLeast(0f)) * (position - size)
        val rect = sharedViewRect(source, target, size, size).translate(Offset(0f, -rise))
        val contentAlpha = liquidPreloadedContentAlpha(phase, earlyContentHandoff = true, expansionProgress = size)
        return HomeAnchoredMorphGeometry(
            rect = rect,
            cornerRadiusPx = lerp(sourceRadiusPx, targetRadiusPx, size),
            sourceScale = 1f,
            sourceAlpha = 1f - contentAlpha,
            surfaceAlpha = 1f,
            contentAlpha = contentAlpha,
            pathProgress = position,
            expansionProgress = size,
        )
    }
    val sourceEnd = if (style == PageMorphStyle.Menu) 0.42f else 0.33f
    val contentStart = if (style == PageMorphStyle.Welcome) 0.06f else 0.10f
    val sourceAlpha = 1f - (progress / sourceEnd).coerceIn(0f, 1f)
    return HomeAnchoredMorphGeometry(
        rect = sharedViewRect(source, target, progress, verticalProgress),
        cornerRadiusPx = lerp(sourceRadiusPx, targetRadiusPx, (progress / 0.7f).coerceIn(0f, 1f)),
        sourceScale = lerp(1f, 1.12f, 1f - sourceAlpha),
        sourceAlpha = sourceAlpha,
        surfaceAlpha = 1f,
        contentAlpha = ((progress - contentStart) / (0.60f - contentStart)).coerceIn(0f, 1f),
        pathProgress = verticalProgress,
        expansionProgress = progress,
    )
}
