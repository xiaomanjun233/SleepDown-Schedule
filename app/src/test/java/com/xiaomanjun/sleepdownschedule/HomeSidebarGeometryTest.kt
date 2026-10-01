package com.xiaomanjun.sleepdownschedule

import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveProfile
import com.xiaomanjun.sleepdownschedule.feature.home.calculateHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.withSidebarInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSidebarGeometryTest {
    @Test
    fun phoneLandscapeEntersSidebarAtSixHundredDpWithoutHeightGate() {
        val narrow = calculateHomeAdaptiveMetrics(599, 360, 24.dp, 0.dp, 1f)
        val wide = calculateHomeAdaptiveMetrics(600, 360, 24.dp, 0.dp, 1f)
        assertFalse(narrow.isLargeScreen)
        assertTrue(wide.isLargeScreen)
        assertEquals(HomeAdaptiveProfile.TabletLandscape, wide.profile)
        assertTrue(wide.isTabletLandscape)
        val content = wide.withSidebarInsets(96.dp, 0.dp)
        val courseWidth = content.contentWidth - content.tabletContentMargin * 2 -
            content.dayPaneGap - content.daySidePaneWidth
        assertTrue(content.daySidePaneWidth >= 150.dp)
        assertTrue(courseWidth >= 200.dp)
    }

    @Test
    fun navigationDoesNotChangeWindowProfileOrConsumeCoursePane() {
        val window = calculateHomeAdaptiveMetrics(840, 560, 24.dp, 24.dp, 1f)
        val page = window.withSidebarInsets(200.dp, 24.dp)
        assertEquals(window.profile, page.profile)
        assertEquals(840.dp, page.screenWidth)
        assertEquals(616.dp, page.contentWidth)
        assertTrue(page.usesSidebar)
        assertTrue(page.daySidePaneWidth < window.daySidePaneWidth)
        val courseWidth = page.contentWidth - page.tabletContentMargin * 2 -
            page.dayPaneGap - page.daySidePaneWidth
        assertTrue(courseWidth >= 200.dp)
    }

    @Test
    fun portraitPhoneKeepsItsFullContentWidth() {
        val phone = calculateHomeAdaptiveMetrics(412, 915, 24.dp, 24.dp, 1f)
        val page = phone.withSidebarInsets(96.dp, 0.dp)
        assertEquals(phone, page)
        assertFalse(page.usesSidebar)
    }
}
