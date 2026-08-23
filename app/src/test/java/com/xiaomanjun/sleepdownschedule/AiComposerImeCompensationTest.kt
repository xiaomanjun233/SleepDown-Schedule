package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AiComposerImeCompensationTest {
    @Test
    fun hiddenImeKeepsNavigationInset() {
        assertEquals(
            72,
            inset(ime = 0, navigation = 72, baselineHeight = 2400, currentHeight = 2400)
        )
    }

    @Test
    fun untouchedWindowReceivesOneFullImeInset() {
        assertEquals(
            900,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 2400)
        )
    }

    @Test
    fun resizedWindowDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 1500)
        )
    }

    @Test
    fun pannedWindowDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 2400,
                baselineTop = 0,
                currentTop = -900
            )
        )
    }

    @Test
    fun partialSystemAdjustmentOnlyReceivesRemainingInset() {
        assertEquals(
            400,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 1900)
        )
    }

    @Test
    fun missingBaselineSafelyFallsBackToFullImeInset() {
        assertEquals(
            900,
            inset(ime = 900, navigation = 72, baselineHeight = 0, currentHeight = 1500)
        )
    }

    private fun inset(
        ime: Int,
        navigation: Int,
        baselineHeight: Int,
        currentHeight: Int,
        baselineTop: Int = 0,
        currentTop: Int = 0
    ): Int = aiComposerBottomInsetPx(
        imeBottomPx = ime,
        navigationBottomPx = navigation,
        baselineRootHeightPx = baselineHeight,
        currentRootHeightPx = currentHeight,
        baselineRootTopOnScreenPx = baselineTop,
        currentRootTopOnScreenPx = currentTop,
        tolerancePx = 24
    )
}
