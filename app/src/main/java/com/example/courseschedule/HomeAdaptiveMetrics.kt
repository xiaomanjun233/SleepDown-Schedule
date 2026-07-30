package com.example.courseschedule

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class HomeAdaptiveProfile {
    Phone,
    Large,
    TabletLandscape
}

internal data class HomeAdaptiveMetrics(
    val profile: HomeAdaptiveProfile,
    val screenWidth: Dp,
    val screenHeight: Dp,
    val safeTop: Dp,
    val safeBottom: Dp,
    val isThreeTwoLike: Boolean,
    val topOverlayHeight: Dp,
    val topGradientHeight: Dp,
    val dayContentTopPadding: Dp,
    val weekTopSpacerHeight: Dp,
    val daySidePaneWidth: Dp,
    val dayPaneGap: Dp,
    val tabletContentMargin: Dp,
    val tabletContentTop: Dp,
    val animationArc: Dp
) {
    val isLargeScreen: Boolean
        get() = profile != HomeAdaptiveProfile.Phone

    val isTabletLandscape: Boolean
        get() = profile == HomeAdaptiveProfile.TabletLandscape
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberHomeAdaptiveMetrics(): HomeAdaptiveMetrics {
    val density = LocalDensity.current
    val windowSize = currentWindowSizeDp()
    val safeTop = with(density) {
        WindowInsets.safeDrawing.getTop(this).toDp()
    }
    val safeBottom = with(density) {
        // Keep all home geometry stable while an Agent Dialog owns the IME. On MIUI,
        // safeDrawing.bottom may temporarily become the keyboard height even though the
        // underlying Activity uses adjustNothing.
        WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp()
    }
    val widthDp = windowSize.width.value.roundToInt().coerceAtLeast(1)
    val heightDp = windowSize.height.value.roundToInt().coerceAtLeast(1)
    return remember(
        widthDp,
        heightDp,
        safeTop,
        safeBottom,
        density.fontScale
    ) {
        calculateHomeAdaptiveMetrics(
            widthDp = widthDp,
            heightDp = heightDp,
            safeTop = safeTop,
            safeBottom = safeBottom,
            fontScale = density.fontScale
        )
    }
}

@Composable
internal fun currentWindowSizeDp(): DpSize {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return with(density) {
        DpSize(containerSize.width.toDp(), containerSize.height.toDp())
    }
}

internal fun calculateHomeAdaptiveMetrics(
    widthDp: Int,
    heightDp: Int,
    safeTop: Dp,
    safeBottom: Dp,
    fontScale: Float
): HomeAdaptiveMetrics {
    val screenWidth = widthDp.dp
    val screenHeight = heightDp.dp
    val isLandscape = widthDp > heightDp
    val isLargeScreen = widthDp >= 600 && heightDp >= 480
    val isTabletLandscape = isLargeScreen && isLandscape && widthDp >= 840 && heightDp >= 560
    val aspect = widthDp.toFloat() / heightDp.toFloat().coerceAtLeast(1f)
    val isThreeTwoLike = isTabletLandscape && aspect in 1.35f..1.75f
    val baseWeekTop = HomeInitialTopInset - 22.dp
    val screenAdjustment = ((heightDp - 720) * 0.08f).dp
    val statusAdjustment = (safeTop - 24.dp) * 0.35f
    val fontScaleAdjustment = (0f - (fontScale - 1f).coerceAtLeast(0f) * 12f).dp
    val compactWeekTop = (baseWeekTop + screenAdjustment + statusAdjustment + fontScaleAdjustment)
        .coerceIn(84.dp, 108.dp)

    if (!isLargeScreen) {
        return HomeAdaptiveMetrics(
            profile = HomeAdaptiveProfile.Phone,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            safeTop = safeTop,
            safeBottom = safeBottom,
            isThreeTwoLike = false,
            topOverlayHeight = HomeTopOverlayHeight,
            topGradientHeight = HomeTopOverlayHeight,
            dayContentTopPadding = HomeInitialTopInset,
            weekTopSpacerHeight = compactWeekTop,
            daySidePaneWidth = 0.dp,
            dayPaneGap = 0.dp,
            tabletContentMargin = 0.dp,
            tabletContentTop = 0.dp,
            animationArc = 48.dp
        )
    }

    if (!isTabletLandscape) {
        return HomeAdaptiveMetrics(
            profile = HomeAdaptiveProfile.Large,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            safeTop = safeTop,
            safeBottom = safeBottom,
            isThreeTwoLike = false,
            topOverlayHeight = HomeTopOverlayHeight,
            topGradientHeight = HomeTopOverlayHeight,
            dayContentTopPadding = HomeInitialTopInset,
            weekTopSpacerHeight = compactWeekTop,
            daySidePaneWidth = 0.dp,
            dayPaneGap = 0.dp,
            tabletContentMargin = if (widthDp >= 840) 28.dp else 20.dp,
            tabletContentTop = (safeTop + 64.dp).coerceIn(72.dp, 112.dp),
            animationArc = 44.dp
        )
    }

    val topOverlay = (safeTop + if (isThreeTwoLike) 96.dp else 104.dp).coerceIn(104.dp, 132.dp)
    val contentTop = (safeTop + if (isThreeTwoLike) 76.dp else 84.dp).coerceIn(84.dp, 112.dp)
    // The day view's left rail is a working pane (date + Today Agent), not a narrow summary
    // sidebar. Give it enough room for a readable conversation while retaining a larger safe
    // margin at both window edges on tablets.
    val sidePaneWidth = (screenWidth * if (isThreeTwoLike) 0.38f else 0.39f)
        .coerceIn(384.dp, 520.dp)
    return HomeAdaptiveMetrics(
        profile = HomeAdaptiveProfile.TabletLandscape,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        safeTop = safeTop,
        safeBottom = safeBottom,
        isThreeTwoLike = isThreeTwoLike,
        topOverlayHeight = topOverlay,
        topGradientHeight = topOverlay + 10.dp,
        dayContentTopPadding = contentTop,
        weekTopSpacerHeight = (contentTop - 30.dp).coerceIn(54.dp, 82.dp),
        daySidePaneWidth = sidePaneWidth,
        dayPaneGap = 20.dp,
        tabletContentMargin = if (isThreeTwoLike) 38.dp else 40.dp,
        tabletContentTop = contentTop,
        animationArc = 40.dp
    )
}

internal fun HomeAdaptiveMetrics.contentRectPx(rootSize: IntSize, density: Float): Rect {
    if (rootSize.width <= 0 || rootSize.height <= 0) return Rect.Zero

    val safeDensity = density.coerceAtLeast(0.001f)
    val rootWidthDp = rootSize.width / safeDensity
    val phoneMarginDp = (rootWidthDp * 0.03f).coerceIn(10f, 16f)
    val margin = (if (isLargeScreen) tabletContentMargin.value else phoneMarginDp) * safeDensity
    val top = (
        if (isLargeScreen) tabletContentTop.value else safeTop.value + 8f
        ).coerceAtLeast(0f) * safeDensity
    val bottomInset = (
        safeBottom.value + if (isLargeScreen) 18f else 12f
        ).coerceAtLeast(0f) * safeDensity
    val right = (rootSize.width - margin).coerceAtLeast(margin + 1f)
    val bottom = (rootSize.height - bottomInset).coerceAtLeast(top + 1f)
    return Rect(margin, top, right, bottom)
}

/**
 * Keeps the phone week-card silhouette unchanged while allowing wide tablet grids to use a
 * slightly softer continuous corner. The radius is bounded by the card's shorter side so short
 * one-period cards never collapse into pills.
 */
internal fun adaptiveWeekCardCornerRadius(
    cardWidth: Dp,
    cardHeight: Dp,
    windowWidth: Dp,
    windowHeight: Dp
): Dp {
    val shortWindowEdge = min(windowWidth.value, windowHeight.value)
    if (shortWindowEdge < 600f) return 8.dp

    val shortCardEdge = min(cardWidth.value, cardHeight.value).coerceAtLeast(1f)
    val resolutionScale = ((shortWindowEdge - 600f) / 600f).coerceIn(0f, 1f)
    val maximumRadius = 14f + 2f * resolutionScale
    return (shortCardEdge * 0.18f).coerceIn(10f, maximumRadius).dp
}
