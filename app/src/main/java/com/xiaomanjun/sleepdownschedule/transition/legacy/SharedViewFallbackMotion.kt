package com.xiaomanjun.sleepdownschedule.transition.legacy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
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
private val FloatingAddExpansionEasing = CubicBezierEasing(0.28f, 0f, 0.18f, 1f)

internal class SharedViewFallbackMotion(style: PageMorphStyle) {
    // The aperture and its center have separate timelines. A button can travel far in Y
    // without holding up the opening of the page, while a menu keeps its orientation on return.
    val open = when (style) {
        PageMorphStyle.Card -> tween<Float>(540, easing = easeOut(4.5f))
        PageMorphStyle.Button -> tween(440, easing = easeOut(4f))
        PageMorphStyle.FloatingAdd -> tween(430, easing = LinearEasing)
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
        PageMorphStyle.Button -> 285
        PageMorphStyle.FloatingAdd -> 310
        PageMorphStyle.Menu -> 310
        PageMorphStyle.Welcome -> 280
    }
    val close = tween<Float>(closeMillis, easing = if (style == PageMorphStyle.FloatingAdd)
        CubicBezierEasing(0.32f, 0.06f, 0.30f, 1f) else easeOut(3f))
    val closeVertical = if (style == PageMorphStyle.FloatingAdd) close else tween(closeMillis, easing = easeOut(2f))
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

internal fun sharedViewGeometry(
    source: Rect,
    target: Rect,
    sourceRadiusPx: Float,
    targetRadiusPx: Float,
    progress: Float,
    verticalProgress: Float,
    style: PageMorphStyle,
): HomeAnchoredMorphGeometry {
    val floatingAdd = style == PageMorphStyle.FloatingAdd
    val expansion = if (floatingAdd) {
        val liftLead = 0.17f
        FloatingAddExpansionEasing.transform(((progress - liftLead) / (1f - liftLead)).coerceIn(0f, 1f))
    } else progress
    val rect = if (floatingAdd) {
        val liftDistance = minOf(source.height * 0.58f, (source.top - target.top).coerceAtLeast(0f))
        val lift = liftDistance * homeMorphSmoothStep(0f, 0.24f, progress) * (1f - expansion)
        sharedViewRect(source, target, expansion, expansion).translate(Offset(0f, -lift))
    } else sharedViewRect(source, target, expansion, verticalProgress)
    val sourceEnd = if (style == PageMorphStyle.Menu) 0.42f else 0.33f
    val contentStart = if (style == PageMorphStyle.Welcome) 0.06f else 0.10f
    val sourceAlpha = 1f - (expansion / sourceEnd).coerceIn(0f, 1f)
    val iconAlpha = if (floatingAdd) 1f - homeMorphSmoothStep(0f, 0.34f, expansion) else sourceAlpha
    return HomeAnchoredMorphGeometry(
        rect = rect,
        cornerRadiusPx = lerp(sourceRadiusPx, targetRadiusPx, (expansion / 0.7f).coerceIn(0f, 1f)),
        sourceScale = if (floatingAdd) 1f else lerp(1f, 1.12f, 1f - sourceAlpha),
        sourceAlpha = iconAlpha,
        surfaceAlpha = 1f,
        contentAlpha = if (floatingAdd) homeMorphSmoothStep(0.13f, 0.70f, expansion)
            else ((expansion - contentStart) / (0.60f - contentStart)).coerceIn(0f, 1f),
        pathProgress = verticalProgress,
        expansionProgress = expansion,
    )
}
