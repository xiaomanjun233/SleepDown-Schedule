package com.itos.coui.dialog

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogWindowInsetsTest {
    @Test fun inputCardClearsTheNavigationAreaWhenTheKeyboardIsClosed() {
        assertEquals(56f, CouiDialogDefaults.inputBottomPaddingPx(24, 0f, Density(1f)), .01f)
    }

    @Test fun keyboardHeightDoesNotCountTheNavigationAreaTwice() {
        assertEquals(32f, CouiDialogDefaults.inputBottomPaddingPx(24, 300f, Density(1f)), .01f)
    }

    @Test fun startingTheKeyboardAnimationKeepsTheCardClearanceContinuous() {
        val before = CouiDialogDefaults.inputBottomPaddingPx(24, 0f, Density(1f))
        val during = CouiDialogDefaults.inputBottomPaddingPx(24, 12f, Density(1f)) + 12f
        assertEquals(before, during, .01f)
    }
}
