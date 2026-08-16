package com.xiaomanjun.sleepdownschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal val LocalGlassMiuixEnabled = compositionLocalOf { false }
internal val LocalGlassSettingsContentTopPadding = compositionLocalOf<Dp?> { null }
internal val LocalSettingsPopupBackdrop = compositionLocalOf<Backdrop?> { null }

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
            content = content
        )
    }
}

@Composable
fun GlassMiuixRootSettingsScaffold(
    title: String,
    config: ScheduleConfigEntity,
    content: @Composable (PaddingValues) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val pageColor = settingsPageBackground(pageConfig)
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    val scrollBehavior = rememberSettingsScrollBehavior()
    GlassMiuixSettingsTheme(pageConfig) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                SettingsGradientTopBar(
                    config = pageConfig,
                    backdrop = contentBackdrop,
                    enabled = true
                ) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        ) { innerPadding ->
            CompositionLocalProvider(
                LocalGlassSettingsContentTopPadding provides innerPadding.calculateTopPadding()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .layerBackdrop(contentBackdrop)
                ) {
                    content(innerPadding)
                }
            }
        }
    }
}

@Composable
internal fun GlassMiuixTabletDetailPaneScaffold(
    title: String,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    topBarVisible: Boolean = true,
    showBackButton: Boolean = false,
    useMiuixCollapsedTitleStyle: Boolean = false,
    onBack: () -> Unit = {},
    horizontalContentInset: Dp = 16.dp,
    content: @Composable (Backdrop?) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val pageColor = settingsPageBackground(pageConfig)
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    GlassMiuixSettingsTheme(pageConfig) {
        Box(modifier.background(pageColor)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .layerBackdrop(backgroundBackdrop)
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (topBarVisible) {
                        SettingsGradientTopBar(
                            config = pageConfig,
                            backdrop = contentBackdrop,
                            enabled = true
                        ) {
                            DetailTopBar(
                                title = title,
                                config = pageConfig,
                                backdrop = contentBackdrop,
                                onBack = onBack,
                                centerTitle = true,
                                useMiuixCollapsedTitleStyle = useMiuixCollapsedTitleStyle,
                                showBackButton = showBackButton,
                                backButtonStartPadding = horizontalContentInset + 16.dp
                            )
                        }
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(
                    LocalGlassSettingsContentTopPadding provides innerPadding.calculateTopPadding(),
                    LocalSettingsPopupBackdrop provides contentBackdrop
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalContentInset)
                            .layerBackdrop(contentBackdrop)
                    ) {
                        content(backgroundBackdrop)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassMiuixDetailActivityScaffold(
    title: String,
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    showTopGradientBlur: Boolean,
    isolateContentFromBackdrop: Boolean,
    compactTopBar: Boolean,
    centerCompactTitle: Boolean,
    compactTitleMatchesSettings: Boolean,
    topBarVisible: Boolean,
    topBarActions: @Composable (Backdrop?) -> Unit,
    content: @Composable (Backdrop?) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val pageColor = settingsPageBackground(pageConfig)
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    val scrollBehavior = rememberSettingsScrollBehavior()
    val compactTopBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 58.dp

    GlassMiuixSettingsTheme(pageConfig) {
        Box(
            Modifier
                .fillMaxSize()
                .background(settingsPageBackground(pageConfig))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(settingsPageBackground(pageConfig))
                    .layerBackdrop(backgroundBackdrop)
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (compactTopBar) Modifier.height(compactTopBarHeight) else Modifier)
                    ) {
                        if (topBarVisible) {
                            SettingsGradientTopBar(
                                config = pageConfig,
                                backdrop = contentBackdrop,
                                enabled = showTopGradientBlur
                            ) {
                                if (compactTopBar) {
                                    DetailTopBar(
                                        title = title,
                                        config = pageConfig,
                                        backdrop = contentBackdrop,
                                        onBack = onBack,
                                        centerTitle = centerCompactTitle,
                                        useMiuixCollapsedTitleStyle = compactTitleMatchesSettings,
                                        actions = { topBarActions(contentBackdrop) }
                                    )
                                } else {
                                    TopAppBar(
                                        title = title,
                                        largeTitle = title,
                                        color = Color.Transparent,
                                        scrollBehavior = scrollBehavior,
                                        navigationIconPadding = 16.dp,
                                        navigationIcon = {
                                            TopBackButton(
                                                backdrop = contentBackdrop,
                                                config = pageConfig,
                                                onClick = onBack,
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(
                    LocalGlassSettingsContentTopPadding provides innerPadding.calculateTopPadding()
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                    if (isolateContentFromBackdrop) {
                        Box(contentModifier) { content(null) }
                    } else {
                        Box(contentModifier.layerBackdrop(contentBackdrop)) {
                            CompositionLocalProvider(
                                LocalSettingsPopupBackdrop provides contentBackdrop
                            ) {
                                // Normal settings glass samples the plain page producer. Root
                                // overlays sample this complete page producer, which excludes the
                                // popup hosted later by the MIUIX root scaffold.
                                content(backgroundBackdrop)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGradientTopBar(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val tintColor = if (glassUsesLightStyle(config)) Color.White else Color(0xFF111111)
    val blurModifier = if (enabled) {
        Modifier.progressiveBackdropBlur(
            backdrop = backdrop,
            tintColor = tintColor,
            blurRadius = 12.dp,
            tintIntensity = 0.18f,
            direction = ProgressiveBlurDirection.TopToBottom,
            topMaskFadeStart = 0.68f,
            topMaskFadeEnd = 1.14f,
            topTintFadeStart = 0.58f,
            topTintFadeEnd = 1.10f,
            fallbackTintStops = listOf(
                0f to tintColor.copy(alpha = 0.42f),
                0.68f to tintColor.copy(alpha = 0.18f),
                1f to tintColor.copy(alpha = 0.04f)
            )
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(blurModifier)
    ) {
        content()
    }
}

@Composable
private fun rememberSettingsScrollBehavior(): ScrollBehavior {
    val base = MiuixScrollBehavior()
    return remember(base) {
        object : ScrollBehavior by base {
            override val nestedScrollConnection: NestedScrollConnection =
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0f) {
                            base.state.heightOffset += available.y
                        }
                        // The page keeps the full delta so content and the large title move together.
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        base.state.contentOffset += consumed.y
                        if (available.y > 0f) {
                            base.state.heightOffset += available.y
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        return base.nestedScrollConnection.onPostFling(consumed, available)
                    }
                }
        }
    }
}
