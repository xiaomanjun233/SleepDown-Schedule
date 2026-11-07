package com.itos.coui.menu

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class SystemPopupPlacementTest {
    private val safe = Rect(16f, 40f, 344f, 768f)

    @Test fun toolbarCentersThenClampsWithinTheWindow() {
        val menu = systemPopupMenuBounds(Rect(300f, 48f, 342f, 90f), safe, IntSize(196, 178), 8f, false, false)
        assertEquals(Rect(148f, 98f, 344f, 276f), menu)
    }

    @Test fun courseShortcutAlignsItsLogicalEdgeAndFlipsAboveTheBottomCard() {
        val anchor = Rect(44f, 650f, 114f, 740f)
        val left = systemPopupMenuBounds(anchor, safe, IntSize(196, 178), 8f, true, false)
        val right = systemPopupMenuBounds(Rect(246f, 650f, 316f, 740f), safe, IntSize(196, 178), 8f, true, true)
        assertEquals(44f, left.left, 0f)
        assertEquals(316f, right.right, 0f)
        assertEquals(642f, left.bottom, 0f)
    }

    @Test fun constrainedWindowsClampBothSizeAndPosition() {
        val menu = systemPopupMenuBounds(Rect(0f, 0f, 42f, 42f), Rect(16f, 32f, 164f, 156f),
            IntSize(196, 300), 8f, true, true)
        assertEquals(Rect(16f, 32f, 164f, 156f), menu)
    }
}
