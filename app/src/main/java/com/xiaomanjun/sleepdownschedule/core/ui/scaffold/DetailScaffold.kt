package com.xiaomanjun.sleepdownschedule.core.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.DefaultWallpaperStyle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopBackButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixDetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalGlassSettingsContentTopPadding
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DetailTopBarHeight = SleepDownDesignTokens.SecondaryPage.CompactTopBarHeight
private val DetailTopOverlayExtra = SleepDownDesignTokens.SecondaryPage.TopOverlayExtra
private val DetailContentTopGap = SleepDownDesignTokens.SecondaryPage.ContentTopGap
@Composable
internal fun detailTopOverlayHeight(): Dp {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    return statusTop + DetailTopBarHeight + DetailTopOverlayExtra
}

@Composable
internal fun detailContentTopPadding(): Dp {
    LocalGlassSettingsContentTopPadding.current?.let { return it }
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    return statusTop + DetailTopBarHeight + DetailContentTopGap
}

@Composable
fun DetailActivityScaffold(
    title: String,
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    showTopGradientBlur: Boolean = true,
    isolateContentFromBackdrop: Boolean = false,
    compactTopBar: Boolean = false,
    centerCompactTitle: Boolean = false,
    compactTitleMatchesSettings: Boolean = false,
    topBarVisible: Boolean = true,
    preserveStatusBarSpace: Boolean = false,
    topBarBackdropOverride: Backdrop? = null,
    topBarActions: @Composable (Backdrop?) -> Unit = {},
    content: @Composable (Backdrop?) -> Unit
) {
    GlassMiuixDetailActivityScaffold(
        title = title,
        config = config,
        onBack = onBack,
        showTopGradientBlur = showTopGradientBlur,
        isolateContentFromBackdrop = isolateContentFromBackdrop,
        compactTopBar = compactTopBar,
        centerCompactTitle = centerCompactTitle,
        compactTitleMatchesSettings = compactTitleMatchesSettings,
        topBarVisible = topBarVisible,
        preserveStatusBarSpace = preserveStatusBarSpace,
        topBarBackdropOverride = topBarBackdropOverride,
        topBarActions = topBarActions,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onBack: () -> Unit,
    centerTitle: Boolean = false,
    useMiuixCollapsedTitleStyle: Boolean = false,
    showBackButton: Boolean = true,
    backButtonStartPadding: Dp = 16.dp,
    statusTopOverride: Dp? = null,
    actions: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val statusTop = statusTopOverride
        ?: with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + DetailTopBarHeight)
            .graphicsLayer { clip = false }
    ) {
        if (centerTitle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DetailTopBarHeight)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { clip = false }
            ) {
                if (showBackButton) {
                    TopBackButton(
                        backdrop = backdrop,
                        config = config,
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = backButtonStartPadding)
                            .size(SleepDownDesignTokens.SecondaryPage.BackButtonSize)
                    )
                }
                Text(
                    title,
                    style = if (useMiuixCollapsedTitleStyle) {
                        MiuixTheme.textStyles.title3
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = if (useMiuixCollapsedTitleStyle) {
                        FontWeight.Medium
                    } else {
                        FontWeight.SemiBold
                    },
                    color = if (useMiuixCollapsedTitleStyle) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    lineHeight = if (useMiuixCollapsedTitleStyle) {
                        MiuixTheme.textStyles.title3.fontSize
                    } else {
                        MaterialTheme.typography.titleLarge.fontSize
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(
                        horizontal = SleepDownDesignTokens.SecondaryPage.CenterTitleSideClearance
                    )
                )
            }
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .align(Alignment.BottomCenter)
                .padding(start = backButtonStartPadding, end = 16.dp)
                .graphicsLayer { clip = false },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                TopBackButton(
                    backdrop = backdrop,
                    config = config,
                    onClick = onBack,
                    modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize)
                )
            }
            Box(
                modifier = Modifier
                    .height(SleepDownDesignTokens.SecondaryPage.BackButtonSize)
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = MaterialTheme.typography.titleLarge.fontSize,
                    textAlign = TextAlign.Center
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .height(DetailTopBarHeight)
                .padding(end = backButtonStartPadding)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.CenterEnd
        ) { actions() }
    }
}

@Composable
fun settingsPageBackground(config: ScheduleConfigEntity): ComposeColor {
    return if (appUsesDarkTheme(config)) ComposeColor.Black else SleepDownDesignTokens.LightPageBackground
}

@Composable
fun settingsVisualConfig(config: ScheduleConfigEntity): ScheduleConfigEntity {
    return config.copy(
        wallpaperUri = null,
        wallpaperBrightness = 1f,
        homeTextLight = appUsesDarkTheme(config),
        defaultWallpaperStyle = DefaultWallpaperStyle.NONE
    )
}
