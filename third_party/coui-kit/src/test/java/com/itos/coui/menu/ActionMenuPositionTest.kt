package com.itos.coui.menu

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionMenuPositionTest {
    private val window = IntSize(400, 800)
    private val visible = IntRect(0, 24, 400, 776)
    private val menu = IntSize(196, 176)

    @Test fun toolbarCentersOnTheAnchorAndClampsAtTheWindowEdge() {
        val provider = provider(CouiMenuPlacement.AnchorCenter)
        assertEquals(IntOffset(107, 112), provider.calculatePosition(
            IntRect(184, 62, 226, 104), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(188, 112), provider.calculatePosition(
            IntRect(342, 62, 384, 104), window, LayoutDirection.Ltr, menu))
    }

    @Test fun courseUsesLogicalStartAndAbovePlacementKeepsTheSameGap() {
        val provider = provider(CouiMenuPlacement.AnchorStart)
        val anchor = IntRect(80, 620, 280, 720)
        assertEquals(IntOffset(80, 436), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(84, 436), provider.calculatePosition(anchor, window, LayoutDirection.Rtl, menu))
    }

    @Test fun imeAndSmallWindowsConstrainPlacementToTheVisibleRegion() {
        val provider = provider(CouiMenuPlacement.AnchorCenter, IntRect(0, 24, 400, 380))
        assertEquals(IntOffset(102, 96), provider.calculatePosition(
            IntRect(179, 280, 221, 322), window, LayoutDirection.Ltr, menu))
        assertEquals(40, provider.calculatePosition(
            IntRect(179, 180, 221, 222), window, LayoutDirection.Ltr, menu).y)
    }

    @Test fun endAnchoredCourseAlignsTheMenuWithTheCardInsteadOfTheScreenEdge() {
        val provider = provider(CouiMenuPlacement.AnchorEnd)
        val anchor = IntRect(255, 300, 340, 400)
        assertEquals(IntOffset(144, 408), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(188, 408), provider.calculatePosition(anchor, window, LayoutDirection.Rtl, menu))
    }

    private fun provider(placement: CouiMenuPlacement, bounds: IntRect = visible) =
        CouiMenuPositionProvider(DpOffset(0.dp, 8.dp), Density(1f), placement) { bounds }
}
