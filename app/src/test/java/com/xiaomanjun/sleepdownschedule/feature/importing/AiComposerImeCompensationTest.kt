package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.ui.layout.floatingInputBottomInsetPx

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
    fun combinedResizeAndPanDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 1900,
                baselineTop = 0,
                currentTop = -400
            )
        )
    }

    @Test
    fun partialCombinedAdjustmentOnlyReceivesPhysicalRemainder() {
        assertEquals(
            400,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 2100,
                baselineTop = 0,
                currentTop = -200
            )
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
    ): Int = floatingInputBottomInsetPx(
        ime, navigation,
        if (baselineHeight > 0) baselineTop + baselineHeight else 0,
        currentTop + currentHeight
    )
}
