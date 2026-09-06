package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.*

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

internal const val MinimumWeekCardHeightScale = 0.72f
internal const val MaximumWeekCardHeightScale = 1.45f

/*
 * The expanded week rail renders one 15sp period number plus two 11sp time lines. Its
 * current-period badge adds 1dp above and below the number. When a short window cannot fit that
 * complete stack, rendering switches to the compact number-only contract instead of clipping or
 * shrinking the text.
 */
private const val WeekTimelinePeriodLineHeightSp = 15f
private const val WeekTimelineTimeLineHeightSp = 11f
private const val WeekTimelineCurrentBadgeVerticalPaddingDp = 2f
private const val WeekTimelineVerticalBreathingRoomDp = 4f
private const val WeekCardHeightFineLooseScale = 1.18f

internal fun minimumWeekTimelineRowHeightDp(fontScale: Float): Float {
    val safeFontScale = fontScale.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val measuredTimelineHeight = (
        WeekTimelinePeriodLineHeightSp + WeekTimelineTimeLineHeightSp * 2f
        ) * safeFontScale +
        WeekTimelineCurrentBadgeVerticalPaddingDp +
        WeekTimelineVerticalBreathingRoomDp
    // 46dp is the established readable row height at the default font size.  The explicit text
    // measurement makes the contract hold when system font scaling exceeds that baseline.
    return maxOf(46f * safeFontScale, measuredTimelineHeight)
}

/**
 * Minimum row height that still renders the period number cleanly. When the compact slider
 * pushes the row below the full three-line rail ([minimumWeekTimelineRowHeightDp]), the left
 * time column switches to number-only instead of clipping the time lines (see the time-column
 * switch in WeekScheduleUi).
 */
internal fun minimumWeekTimelineNumberOnlyRowHeightDp(fontScale: Float): Float {
    val safeFontScale = fontScale.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val measuredNumberHeight = WeekTimelinePeriodLineHeightSp * safeFontScale +
        WeekTimelineCurrentBadgeVerticalPaddingDp +
        WeekTimelineVerticalBreathingRoomDp
    return maxOf(22f * safeFontScale, measuredNumberHeight)
}

/**
 * Row height that lets the complete week grid fill the usable page.  The floating dock is an
 * overlay and the scroll tail below the grid is only gesture clearance, so neither belongs in
 * this measurement. The established three-line period rail is the readable floor: compact mode
 * may make the page scroll, but must never remove or clip either time line.
 */
internal fun adaptiveWeekRowHeightDp(
    viewportHeightDp: Float,
    topSpacerDp: Float,
    periodCount: Int,
    headerHeightDp: Float = 44f + 46f,
    bottomInsetDp: Float = 0f,
    fontScale: Float = 1f,
    minimumReadableHeightDp: Float = 46f
): Float {
    val rows = periodCount.coerceAtLeast(1)
    val available = viewportHeightDp - topSpacerDp - headerHeightDp - bottomInsetDp
    val safeFontScale = fontScale.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val readableFloor = maxOf(
        minimumReadableHeightDp * safeFontScale,
        minimumWeekTimelineRowHeightDp(safeFontScale)
    )
    return (available / rows).coerceAtLeast(readableFloor)
}

/**
 * The saved setting remains a device-independent relative scale.  Only the rendering/UI floor is
 * resolved from the live window and font metrics, so the same preference can still become compact
 * on a taller device. The floor stops at a clean number-only rail: below the full three-line
 * height the time lines hide instead of clipping, so the compact range may shrink further than
 * the old "always show time" contract.
 */
internal fun minimumWeekCardHeightScale(
    adaptiveRowHeightDp: Float,
    fontScale: Float
): Float {
    val safeAdaptiveRowHeight = adaptiveRowHeightDp.takeIf(Float::isFinite)
        ?.coerceAtLeast(1f)
        ?: 1f
    return (minimumWeekTimelineNumberOnlyRowHeightDp(fontScale) / safeAdaptiveRowHeight)
        .coerceIn(MinimumWeekCardHeightScale, 1f)
}

private fun resolvedWeekCardHeightMinimumScale(minimumScale: Float): Float =
    minimumScale.takeIf(Float::isFinite)?.coerceIn(MinimumWeekCardHeightScale, 1f)
        ?: MinimumWeekCardHeightScale

internal fun normalizedWeekCardHeightScale(
    value: Float,
    minimumScale: Float = MinimumWeekCardHeightScale
): Float = value.takeIf(Float::isFinite)?.coerceIn(
    resolvedWeekCardHeightMinimumScale(minimumScale),
    MaximumWeekCardHeightScale
) ?: 1f

/**
 * Slider midpoint is always the adaptive fit. The loose half mirrors the liquid-glass control:
 * 50%–75% is a fine adjustment range and 75%–100% opens the remaining expansion range.
 */
internal fun weekCardHeightScaleFromSlider(
    progress: Float,
    minimumScale: Float = MinimumWeekCardHeightScale
): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    val floor = resolvedWeekCardHeightMinimumScale(minimumScale)
    return if (safeProgress <= 0.5f) {
        floor + (1f - floor) * (safeProgress * 2f)
    } else if (safeProgress <= 0.75f) {
        1f + (WeekCardHeightFineLooseScale - 1f) * ((safeProgress - 0.5f) * 4f)
    } else {
        WeekCardHeightFineLooseScale +
            (MaximumWeekCardHeightScale - WeekCardHeightFineLooseScale) *
            ((safeProgress - 0.75f) * 4f)
    }
}

internal fun weekCardHeightSliderFromScale(
    scale: Float,
    minimumScale: Float = MinimumWeekCardHeightScale
): Float {
    val floor = resolvedWeekCardHeightMinimumScale(minimumScale)
    val safeScale = normalizedWeekCardHeightScale(scale, floor)
    if (floor >= 1f) {
        return when {
            safeScale <= 1f -> 0.5f
            safeScale <= WeekCardHeightFineLooseScale ->
                (0.5f + (safeScale - 1f) /
                    (WeekCardHeightFineLooseScale - 1f) / 4f).coerceIn(0.5f, 0.75f)
            else ->
                (0.75f + (safeScale - WeekCardHeightFineLooseScale) /
                    (MaximumWeekCardHeightScale - WeekCardHeightFineLooseScale) / 4f)
                    .coerceIn(0.75f, 1f)
        }
    }
    return if (safeScale <= 1f) {
        ((safeScale - floor) / (1f - floor) / 2f).coerceIn(0f, 0.5f)
    } else if (safeScale <= WeekCardHeightFineLooseScale) {
        (0.5f + (safeScale - 1f) /
            (WeekCardHeightFineLooseScale - 1f) / 4f).coerceIn(0.5f, 0.75f)
    } else {
        (0.75f + (safeScale - WeekCardHeightFineLooseScale) /
            (MaximumWeekCardHeightScale - WeekCardHeightFineLooseScale) / 4f)
            .coerceIn(0.75f, 1f)
    }
}

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
    val dayHeightAdjustment = ((heightDp - 800) * 0.025f).dp
    val compactDayTop = if (isLandscape) {
        (safeTop + 64.dp + dayHeightAdjustment + fontScaleAdjustment).coerceIn(76.dp, 96.dp)
    } else {
        (110.dp + dayHeightAdjustment + statusAdjustment + fontScaleAdjustment).coerceIn(104.dp, 118.dp)
    }

    if (!isLargeScreen) {
        return HomeAdaptiveMetrics(
            profile = HomeAdaptiveProfile.Phone,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            safeTop = safeTop,
            safeBottom = safeBottom,
            isThreeTwoLike = false,
            topOverlayHeight = HomeTopOverlayHeight,
            // The top gradient extends well past the week's fixed header band so the boundless
            // header reads as a continuation of the app top bar chrome.
            topGradientHeight = 230.dp,
            dayContentTopPadding = compactDayTop,
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
            // The top gradient extends well past the week's fixed header band so the boundless
            // header reads as a continuation of the app top bar chrome.
            topGradientHeight = 230.dp,
            dayContentTopPadding = compactDayTop.coerceIn(100.dp, 116.dp),
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
        topGradientHeight = topOverlay + 90.dp,
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
    windowHeight: Dp,
    progress: Float = 0.5f
): Dp {
    val shortWindowEdge = min(windowWidth.value, windowHeight.value)
    val shortCardEdge = min(cardWidth.value, cardHeight.value).coerceAtLeast(1f)
    val baseRadius = if (shortWindowEdge < 600f) {
        8f
    } else {
        val resolutionScale = ((shortWindowEdge - 600f) / 600f).coerceIn(0f, 1f)
        val oldMaximum = 14f + 2f * resolutionScale
        (shortCardEdge * 0.18f).coerceIn(10f, oldMaximum)
    }
    val minimumRadius = (baseRadius * 0.5f).coerceAtLeast(3f)
    val maximumRadius = min(shortCardEdge * 0.32f, baseRadius * 1.7f)
        .coerceAtLeast(baseRadius)
    val safeProgress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
    val radius = if (safeProgress <= 0.5f) {
        minimumRadius + (baseRadius - minimumRadius) * safeProgress * 2f
    } else {
        baseRadius + (maximumRadius - baseRadius) * (safeProgress - 0.5f) * 2f
    }
    // 大屏（宽窗口）在滑块基础上再扩大一截曲率，短卡仍保持不塌成胶囊
    val tabletBoost = if (shortWindowEdge >= 600f) 3f else 0f
    val scaledRadius = (radius + tabletBoost).coerceAtMost(shortCardEdge * 0.34f)
    return scaledRadius.dp
}
