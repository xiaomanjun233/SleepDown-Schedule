package com.example.courseschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal val LocalGlassMiuixEnabled = compositionLocalOf { false }
internal val LocalGlassSettingsContentTopPadding = compositionLocalOf<Dp?> { null }

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
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(LocalGlassMiuixEnabled provides true, content = content)
    }
}

@Composable
fun GlassMiuixRootSettingsScaffold(
    title: String,
    config: ScheduleConfigEntity,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    GlassMiuixSettingsTheme(config) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    color = Color.Transparent,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            CompositionLocalProvider(
                LocalGlassSettingsContentTopPadding provides innerPadding.calculateTopPadding()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    content(innerPadding)
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
    content: @Composable (Backdrop?) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    val scrollBehavior = MiuixScrollBehavior()
    val logRecording = DiagnosticLogCapture.recording.collectAsStateWithLifecycle().value

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
                    Box(Modifier.fillMaxWidth()) {
                        if (showTopGradientBlur) {
                            HomeTopGradientBlur(
                                config = pageConfig,
                                backdrop = contentBackdrop,
                                height = detailTopOverlayHeight(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }
                        TopAppBar(
                            title = title,
                            largeTitle = title,
                            color = Color.Transparent,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                TopBackButton(
                                    backdrop = chromeBackdrop,
                                    config = pageConfig,
                                    onClick = onBack,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(42.dp)
                                )
                            }
                        )
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
                            content(backgroundBackdrop)
                        }
                    }
                }
            }
            DiagnosticLogStopOverlay(
                visible = logRecording,
                config = pageConfig,
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(40f)
            )
        }
    }
}
