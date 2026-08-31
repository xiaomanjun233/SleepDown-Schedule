package com.xiaomanjun.sleepdownschedule.core.ui.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogRenderInRootScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
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

/** Root-level sibling host for controls that must float outside the scroll/card subtree. */
internal class DetailActivityFloatingOverlayHost {
    private val contentState = mutableStateOf<(@Composable () -> Unit)?>(null)

    var content: (@Composable () -> Unit)?
        get() = contentState.value
        set(value) {
            contentState.value = value
        }
}

internal val LocalDetailActivityFloatingOverlayHost =
    compositionLocalOf<DetailActivityFloatingOverlayHost?> { null }

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
    val backgroundBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Background,
        providerId = "settings-root-background"
    )
    val contentBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "settings-root-content"
    ) {
        drawRect(pageColor)
        drawContent()
    }
    val scrollBehavior = rememberSettingsScrollBehavior()
    val dialogSceneBackdrop = rememberCenteredDialogSceneBackdrop("settings-root-dialog-scene")
    GlassMiuixSettingsTheme(pageConfig) {
        CompositionLocalProvider(
            LocalCenteredDialogSceneBackdrop provides dialogSceneBackdrop,
            LocalCenteredDialogRenderInRootScaffold provides false,
            LocalSettingsPopupBackdrop provides dialogSceneBackdrop
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pageColor)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .glassBackdropProducer(backgroundBackdrop)
            )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            underlayModifier = Modifier
                .fillMaxSize()
                .centeredDialogSceneProducer(dialogSceneBackdrop),
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
                        .glassBackdropProducer(contentBackdrop)
                ) {
                    content(innerPadding)
                }
            }
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
    val backgroundBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Background,
        providerId = "settings-tablet-background"
    )
    val contentBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "settings-tablet-content"
    ) {
        drawRect(pageColor)
        drawContent()
    }
    val dialogSceneBackdrop = rememberCenteredDialogSceneBackdrop("settings-tablet-dialog-scene")
    GlassMiuixSettingsTheme(pageConfig) {
        CompositionLocalProvider(
            LocalCenteredDialogSceneBackdrop provides dialogSceneBackdrop,
            LocalCenteredDialogRenderInRootScaffold provides false,
            LocalSettingsPopupBackdrop provides dialogSceneBackdrop
        ) {
        Box(
            modifier
                .background(pageColor)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .glassBackdropProducer(backgroundBackdrop)
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                underlayModifier = Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .centeredDialogSceneProducer(dialogSceneBackdrop),
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
                    LocalGlassSettingsContentTopPadding provides innerPadding.calculateTopPadding()
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalContentInset)
                            .glassBackdropProducer(contentBackdrop)
                    ) {
                        content(backgroundBackdrop)
                    }
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
    topBarBackdropOverride: Backdrop?,
    topBarActions: @Composable (Backdrop?) -> Unit,
    content: @Composable (Backdrop?) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val pageColor = settingsPageBackground(pageConfig)
    val backgroundBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Background,
        providerId = "settings-detail-background"
    )
    val contentBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "settings-detail-content"
    ) {
        drawRect(pageColor)
        drawContent()
    }
    val topBarBackdrop = topBarBackdropOverride ?: contentBackdrop
    val scrollBehavior = rememberSettingsScrollBehavior()
    val compactTopBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        SleepDownDesignTokens.SecondaryPage.CompactTopBarHeight
    val dialogSceneBackdrop = rememberCenteredDialogSceneBackdrop("settings-detail-dialog-scene")
    // Reuse a host supplied by the activity transition/home overlay when present. This keeps a
    // destination search dock outside the transition shell and the page/card clipping chain.
    val inheritedFloatingOverlayHost = LocalDetailActivityFloatingOverlayHost.current
    val floatingOverlayHost = remember(inheritedFloatingOverlayHost) {
        inheritedFloatingOverlayHost ?: DetailActivityFloatingOverlayHost()
    }
    GlassMiuixSettingsTheme(pageConfig) {
        CompositionLocalProvider(
            LocalCenteredDialogSceneBackdrop provides dialogSceneBackdrop,
            LocalCenteredDialogRenderInRootScaffold provides false,
            LocalSettingsPopupBackdrop provides dialogSceneBackdrop,
            LocalDetailActivityFloatingOverlayHost provides floatingOverlayHost
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(settingsPageBackground(pageConfig))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(settingsPageBackground(pageConfig))
                    .glassBackdropProducer(backgroundBackdrop)
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                underlayModifier = Modifier
                    .fillMaxSize()
                    .background(settingsPageBackground(pageConfig))
                    .centeredDialogSceneProducer(dialogSceneBackdrop),
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
                                backdrop = topBarBackdrop,
                                enabled = showTopGradientBlur
                            ) {
                                if (compactTopBar) {
                                    DetailTopBar(
                                        title = title,
                                        config = pageConfig,
                                        backdrop = topBarBackdrop,
                                        onBack = onBack,
                                        centerTitle = centerCompactTitle,
                                        useMiuixCollapsedTitleStyle = compactTitleMatchesSettings,
                                        actions = { topBarActions(topBarBackdrop) }
                                    )
                                } else {
                                    TopAppBar(
                                        title = title,
                                        largeTitle = title,
                                        color = Color.Transparent,
                                        scrollBehavior = scrollBehavior,
                                        navigationIconPadding = 16.dp,
                                        actions = { topBarActions(topBarBackdrop) },
                                        navigationIcon = {
                                            TopBackButton(
                                                backdrop = topBarBackdrop,
                                                config = pageConfig,
                                                onClick = onBack,
                                                modifier = Modifier.size(
                                                    SleepDownDesignTokens.SecondaryPage.BackButtonSize
                                                )
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
                        Box(contentModifier.glassBackdropProducer(contentBackdrop)) {
                            // The content producer covers only the region below the top bar, so the
                            // top bar glass samples the real page content. Popup consumers sample
                            // the same backdrop from the root host drawn afterwards, and neither
                            // side can re-enter its own sampling tree.
                            content(backgroundBackdrop)
                        }
                    }
                }
            }
            if (inheritedFloatingOverlayHost == null) {
                floatingOverlayHost.content?.let { overlayContent ->
                    // This is a root-level sibling of the Miuix Scaffold, so a pressed/expanded
                    // search dock cannot be clipped by the body/card layout below it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { clip = false }
                            .zIndex(2f)
                    ) {
                        overlayContent()
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
