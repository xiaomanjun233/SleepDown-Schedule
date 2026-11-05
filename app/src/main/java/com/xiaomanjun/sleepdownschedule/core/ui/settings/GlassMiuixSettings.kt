package com.xiaomanjun.sleepdownschedule.core.ui.settings


import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalToolbarBackdropBlurred
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberHapticMiuixOverscrollFactory
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalLegacyProgressiveBlur
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBlurDirection
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.glass.ui.progressiveBackdropBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal val LocalGlassMiuixEnabled = compositionLocalOf { false }
internal val LocalGlassSettingsContentTopPadding = compositionLocalOf<Dp?> { null }
internal val LocalSettingsPopupBackdrop = compositionLocalOf<Backdrop?> { null }
// A page can temporarily move its chrome away while an in-place editor owns navigation.
internal val LocalSettingsEditorProgress = compositionLocalOf<androidx.compose.runtime.MutableFloatState?> { null }

/** Root-level sibling host for controls that must float outside the scroll/card subtree. */
internal class DetailActivityFloatingOverlayHost(
    val controlsModifier: @Composable () -> Modifier = { Modifier }
) {
    private val contentState = mutableStateOf<(@Composable () -> Unit)?>(null)

    var content: (@Composable () -> Unit)?
        get() = contentState.value
        set(value) {
            contentState.value = value
        }
}

internal val LocalDetailActivityFloatingOverlayHost =
    compositionLocalOf<DetailActivityFloatingOverlayHost?> { null }

/** 只托管改版 Miuix 浮层；正文、顶栏和安全区继续由 COUI 页面负责。 */
@Composable
internal fun SettingsMiuixPopupHost(content: @Composable () -> Unit) {
    val accent = com.itos.coui.theme.CouiTheme.colorScheme.accent
    val colors = if (com.itos.coui.theme.CouiTheme.isDark) darkColorScheme(primary = accent)
        else lightColorScheme(primary = accent)
    MiuixTheme(colors = colors) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { content() }
    }
}

@Composable
fun GlassMiuixSettingsTheme(
    config: ScheduleConfigEntity,
    content: @Composable () -> Unit
) {
    val darkTheme = appUsesDarkTheme(config)
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceVariant = Color.Transparent
        )
    } else {
        lightColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceVariant = Color.Transparent
        )
    }
    val overscrollFactory = rememberHapticMiuixOverscrollFactory()
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(
            LocalGlassMiuixEnabled provides true,
            LocalOverscrollFactory provides overscrollFactory,
            content = { CouiSettingsTheme(config, content) }
        )
    }
}

@Composable
internal fun SettingsGradientTopBar(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tintColor = if (glassUsesLightStyle(config)) Color.White else Color(0xFF111111)
    val webTransition = LocalLegacyProgressiveBlur.current
    val blurModifier = if (enabled) {
        Modifier.progressiveBackdropBlur(
            backdrop = backdrop,
            tintColor = tintColor,
            blurRadius = 12.dp,
            tintIntensity = 0.18f,
            direction = ProgressiveBlurDirection.TopToBottom,
            topMaskFadeStart = if (webTransition) 0.35f else 0.68f,
            topMaskFadeEnd = if (webTransition) 1f else 1.14f,
            topTintFadeStart = if (webTransition) 0.28f else 0.58f,
            topTintFadeEnd = if (webTransition) 1f else 1.10f,
            fallbackTintStops = listOf(
                0f to tintColor.copy(alpha = 0.42f),
                0.68f to tintColor.copy(alpha = 0.18f),
                1f to tintColor.copy(alpha = if (webTransition) 0f else 0.04f)
            )
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (webTransition) Modifier else blurModifier)
            .graphicsLayer { clip = false }
    ) {
        if (webTransition && enabled) {
            Box(
                Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        // Extend only the existing blur consumer, keeping the actual toolbar and
                        // WebView layout fixed. Its alpha reaches zero before the drawing ends.
                        val extendedHeight = constraints.maxHeight + 48.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = extendedHeight, maxHeight = extendedHeight)
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .then(blurModifier)
            )
        }
        CompositionLocalProvider(LocalToolbarBackdropBlurred provides (enabled && backdrop != null)) {
            content()
        }
    }
}
