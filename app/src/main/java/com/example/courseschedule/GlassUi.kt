package com.example.courseschedule

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow

data class GlassTokens(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val surfaceAlpha: Float,
    val borderAlpha: Float
) {
    companion object {
        fun pill(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else (4f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (40f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (44f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.86f else 0.18f,
            borderAlpha = if (reduceTransparency) 0.18f else 0.42f
        )

        fun dialog(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else (6f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (36f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (40f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.40f,
            borderAlpha = if (reduceTransparency) 0.16f else 0.36f
        )

        fun courseCard(blur: Float, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else blur.coerceIn(0f, 34f).dp,
            lensHeight = if (reduceTransparency) 0.dp else 24.dp,
            lensAmount = if (reduceTransparency) 0.dp else 26.dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.58f,
            borderAlpha = if (reduceTransparency) 0.14f else 0.30f
        )
    }
}

@Composable
fun appUsesDarkTheme(config: ScheduleConfigEntity): Boolean {
    val systemDark = isSystemInDarkTheme()
    return if (config.followSystemDarkMode) systemDark else config.darkMode
}

@Composable
fun glassUsesLightStyle(config: ScheduleConfigEntity): Boolean {
    if (config.wallpaperUri.isNullOrBlank()) return !appUsesDarkTheme(config)
    return when {
        config.wallpaperBrightness < 0.72f -> false
        config.wallpaperBrightness > 1.18f -> true
        config.homeTextLight -> false
        else -> true
    }
}

@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    tokens: GlassTokens = GlassTokens.pill(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val useGlass = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val quality = LocalGlassQuality.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, label = "glass-press")
    val lightGlass = glassUsesLightStyle(config)
    val base = if (lightGlass) Color.White else Color(0xFF050505)
    val selectedColor = if (useGlass) {
        if (lightGlass) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val clearAlpha = tokens.surfaceAlpha * quality
    val surfaceColor = if (selected) selectedColor else base.copy(alpha = clearAlpha)
    val contentModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                blur((tokens.blur * quality).toPx())
                lens(
                    (tokens.lensHeight * quality).toPx() * (0.7f + 0.3f * pressProgress),
                    (tokens.lensAmount * quality).toPx() * (0.85f + 0.35f * pressProgress),
                    chromaticAberration = false
                )
            },
            highlight = {
                Highlight.Default.copy(alpha = if (selected) 0.18f + 0.18f * pressProgress else 0.20f * pressProgress)
            },
            shadow = {
                Shadow(alpha = if (selected) 0.35f + 0.25f * pressProgress else 0.28f * pressProgress)
            },
            innerShadow = {
                InnerShadow(
                    radius = if (selected) 8.dp else 4.dp * pressProgress,
                    alpha = if (selected) 0.45f else 0.35f * pressProgress
                )
            },
            layerBlock = {
                val scale = 1f + 0.055f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(surfaceColor)
                if (lightGlass) {
                    drawRect(Color.White.copy(alpha = 0.035f + 0.035f * pressProgress), blendMode = BlendMode.Screen)
                } else {
                    drawRect(Color.Black.copy(alpha = 0.03f + 0.03f * pressProgress))
                    drawRect(Color.White.copy(alpha = 0.012f + 0.016f * pressProgress), blendMode = BlendMode.Screen)
                }
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.86f)))
            .graphicsLayer {
                val scale = 1f + 0.04f * pressProgress
                scaleX = scale
                scaleY = scale
            }
    }
        .then(
            if (onClick == null) Modifier else Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
        )

    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tokens = GlassTokens.pill(),
        selected = selected,
        onClick = onClick,
        content = content
    )
}

@Composable
fun GlassLens(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    pressProgress: Float = 1f,
    content: @Composable () -> Unit = {}
) {
    val useGlass = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val quality = LocalGlassQuality.current
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) Color.Black.copy(alpha = 0.07f * quality) else Color.White.copy(alpha = 0.08f * quality)
    val shape = RoundedCornerShape(50)
    val contentModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                blur((5.dp * quality).toPx())
                lens(
                    (22.dp * quality).toPx() + (30.dp * quality).toPx() * pressProgress,
                    (20.dp * quality).toPx() + (30.dp * quality).toPx() * pressProgress,
                    chromaticAberration = false
                )
            },
            highlight = { Highlight.Default.copy(alpha = 0.20f + 0.18f * pressProgress) },
            shadow = { Shadow(alpha = 0.45f + 0.25f * pressProgress) },
            innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.55f) },
            layerBlock = {
                val scale = 1f + 0.04f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(surfaceColor)
                if (lightGlass) {
                    drawRect(Color.White.copy(alpha = 0.035f), blendMode = BlendMode.Screen)
                } else {
                    drawRect(Color.Black.copy(alpha = 0.03f))
                    drawRect(Color.White.copy(alpha = 0.012f), blendMode = BlendMode.Screen)
                }
            }
        )
    } else {
        modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer)
    }
    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = shape,
        tokens = GlassTokens.dialog(),
        content = content
    )
}

@Composable
fun CourseGlassCard(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val useGlass = config.courseCardGlassEnabled && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val quality = LocalGlassQuality.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, label = "course-card-press")
    val glassTint = Color(config.cardColorArgb.toInt()).copy(alpha = ((config.cardAlpha * 0.34f).coerceIn(0.10f, 0.42f) * quality).coerceIn(0.06f, 0.42f))
    val solidColor = Color(config.cardColorArgb.toInt()).copy(alpha = config.cardAlpha.coerceIn(0.28f, 1f))
    val tokens = GlassTokens.courseCard(config.courseCardBlur)
    val lightGlass = glassUsesLightStyle(config)
    val glassModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                blur((tokens.blur * quality).toPx())
                lens(
                    (tokens.lensHeight * quality).toPx() * (0.85f + 0.15f * pressProgress),
                    (tokens.lensAmount * quality).toPx() * (0.9f + 0.2f * pressProgress),
                    chromaticAberration = false
                )
                vibrancy()
            },
            highlight = { Highlight.Default.copy(alpha = 0.08f + 0.16f * pressProgress) },
            shadow = { Shadow(alpha = 0.22f + 0.18f * pressProgress) },
            innerShadow = { InnerShadow(radius = 6.dp + 2.dp * pressProgress, alpha = 0.20f + 0.18f * pressProgress) },
            layerBlock = {
                val scale = 1f + 0.018f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(glassTint)
                drawRect(Color.White.copy(alpha = if (lightGlass) 0.028f else 0.018f), blendMode = BlendMode.Screen)
                drawRect(Color.Black.copy(alpha = if (lightGlass) 0.008f else 0.030f))
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(solidColor.copy(alpha = solidColor.alpha.coerceAtLeast(0.86f)))
    }
        .then(
            if (onClick == null) Modifier else Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
        )
    Box(modifier = glassModifier) {
        content()
    }
}
