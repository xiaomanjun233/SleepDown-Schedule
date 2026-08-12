package com.xiaomanjun.sleepdownschedule

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAdaptiveMetricsTest {
    @Test
    fun phoneUsesStableResolutionAwareDayGeometry() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 412,
            heightDp = 915,
            safeTop = 24.dp,
            safeBottom = 24.dp,
            fontScale = 1f
        )

        assertEquals(HomeAdaptiveProfile.Phone, metrics.profile)
        assertFalse(metrics.isLargeScreen)
        assertEquals(HomeTopOverlayHeight, metrics.topOverlayHeight)
        assertTrue(metrics.dayContentTopPadding in 104.dp..118.dp)
        assertTrue(metrics.dayContentTopPadding < HomeInitialTopInset)
        assertEquals(0.dp, metrics.tabletContentMargin)
    }

    @Test
    fun tallerPhoneAddsOnlyASmallBoundedDayOffset() {
        val short = calculateHomeAdaptiveMetrics(412, 700, 24.dp, 24.dp, 1f)
        val tall = calculateHomeAdaptiveMetrics(412, 960, 24.dp, 24.dp, 1f)

        assertTrue(tall.dayContentTopPadding > short.dayContentTopPadding)
        assertTrue(tall.dayContentTopPadding - short.dayContentTopPadding <= 8.dp)
    }

    @Test
    fun portraitTabletUsesLargeScreenProfileForTwoPaneSettings() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 800,
            heightDp = 1280,
            safeTop = 24.dp,
            safeBottom = 24.dp,
            fontScale = 1f
        )

        assertEquals(HomeAdaptiveProfile.Large, metrics.profile)
        assertTrue(metrics.isLargeScreen)
        assertFalse(metrics.isTabletLandscape)
        assertEquals(20.dp, metrics.tabletContentMargin)
        assertEquals(0.dp, metrics.daySidePaneWidth)
    }

    @Test
    fun expandedLandscapeTabletUsesTwoPaneLayout() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 1280,
            heightDp = 800,
            safeTop = 24.dp,
            safeBottom = 24.dp,
            fontScale = 1f
        )

        assertEquals(HomeAdaptiveProfile.TabletLandscape, metrics.profile)
        assertTrue(metrics.isLargeScreen)
        assertTrue(metrics.isTabletLandscape)
        assertTrue(metrics.daySidePaneWidth >= 384.dp)
        assertTrue(metrics.daySidePaneWidth >= metrics.screenWidth * 0.38f)
        assertTrue(metrics.tabletContentMargin >= 38.dp)
        assertTrue(metrics.tabletContentTop < HomeInitialTopInset)
    }

    @Test
    fun tabletAgentConversationOnlyGrowsDownFromSourceCard() {
        val source = androidx.compose.ui.geometry.Rect(60f, 230f, 760f, 450f)
        val target = tabletDayAgentConversationTargetRect(
            source = source,
            windowHeightPx = 1600f,
            density = 2f,
            safeBottom = 24.dp
        )

        assertEquals(source.left, target.left, 0.01f)
        assertEquals(474f, target.top, 0.01f)
        assertEquals(source.right, target.right, 0.01f)
        assertEquals(1482f, target.bottom, 0.01f)
        assertTrue(target.height > source.height)
    }

    @Test
    fun tabletAgentConversationStartsFromSlotHiddenBehindCard() {
        val card = androidx.compose.ui.geometry.Rect(60f, 230f, 760f, 450f)
        val source = tabletDayAgentConversationSourceRect(card, density = 2f)

        assertEquals(card.center.x, source.center.x, 0.01f)
        assertEquals(430f, source.top, 0.01f)
        assertEquals(72f, source.width, 0.01f)
        assertEquals(16f, source.height, 0.01f)
        assertTrue(source.bottom < card.bottom)
    }

    @Test
    fun homeReadabilityUsesSymmetricLocalShadowsWithHysteresis() {
        assertEquals(
            HomeReadabilityShadow.Dark,
            homeReadabilityShadowForLuminance(0.9f, 0.7f, HomeReadabilityShadow.None)
        )
        assertEquals(
            HomeReadabilityShadow.Light,
            homeReadabilityShadowForLuminance(0.05f, 0.18f, HomeReadabilityShadow.None)
        )
        assertEquals(
            HomeReadabilityShadow.Light,
            homeReadabilityShadowForLuminance(0.05f, 0.38f, HomeReadabilityShadow.Light)
        )
        assertEquals(
            HomeReadabilityShadow.None,
            homeReadabilityShadowForLuminance(0.05f, 0.62f, HomeReadabilityShadow.Light)
        )
    }

    @Test
    fun shallowLandscapeWindowAvoidsTwoPaneLayout() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 900,
            heightDp = 500,
            safeTop = 0.dp,
            safeBottom = 0.dp,
            fontScale = 1f
        )

        assertEquals(HomeAdaptiveProfile.Large, metrics.profile)
        assertFalse(metrics.isTabletLandscape)
    }

    @Test
    fun phoneOverlayBoundsFollowSafeInsetsAndWindowWidth() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 412,
            heightDp = 915,
            safeTop = 24.dp,
            safeBottom = 28.dp,
            fontScale = 1f
        )
        val root = IntSize(1236, 2745)
        val bounds = metrics.contentRectPx(root, density = 3f)
        val destination = homeMenuDestinationTargetRect(
            kind = HomeMenuDestinationKind.AddCourse,
            rootSize = root,
            density = 3f,
            adaptiveMetrics = metrics
        )

        assertTrue(bounds.left > 30f)
        assertEquals(96f, bounds.top, 0.01f)
        assertTrue(bounds.bottom < root.height - 100f)
        assertTrue(destination.left >= bounds.left)
        assertTrue(destination.top >= bounds.top)
        assertTrue(destination.right <= bounds.right)
        assertTrue(destination.bottom <= bounds.bottom)
    }

    @Test
    fun tabletDestinationUsesCenteredBoundedPanel() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 1280,
            heightDp = 800,
            safeTop = 24.dp,
            safeBottom = 24.dp,
            fontScale = 1f
        )
        val root = IntSize(2560, 1600)
        val content = metrics.contentRectPx(root, density = 2f)
        val destination = homeMenuDestinationTargetRect(
            kind = HomeMenuDestinationKind.ManualImport,
            rootSize = root,
            density = 2f,
            adaptiveMetrics = metrics
        )

        assertEquals(1360f, destination.width, 0.01f)
        assertTrue(destination.left >= content.left)
        assertTrue(destination.top >= content.top)
        assertTrue(destination.right <= content.right)
        assertTrue(destination.bottom <= content.bottom)
    }

    @Test
    fun weekCardCornersStayFixedOnPhoneAndScaleWithinTabletBounds() {
        val phone = adaptiveWeekCardCornerRadius(
            cardWidth = 52.dp,
            cardHeight = 96.dp,
            windowWidth = 412.dp,
            windowHeight = 915.dp
        )
        val shortTabletCard = adaptiveWeekCardCornerRadius(
            cardWidth = 180.dp,
            cardHeight = 52.dp,
            windowWidth = 1280.dp,
            windowHeight = 800.dp
        )
        val tallTabletCard = adaptiveWeekCardCornerRadius(
            cardWidth = 180.dp,
            cardHeight = 96.dp,
            windowWidth = 1280.dp,
            windowHeight = 800.dp
        )

        assertEquals(8.dp, phone)
        assertTrue(shortTabletCard >= 10.dp)
        assertTrue(tallTabletCard > shortTabletCard)
        assertTrue(tallTabletCard <= 16.dp)
    }
}
