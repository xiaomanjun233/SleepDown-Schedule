package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailMorphBlurTest {
    @Test
    fun blurIsClearAtBothSettledStates() {
        assertEquals(0f, detailMotionBlurRadiusDp(0f), 0f)
        assertEquals(0f, detailMotionBlurRadiusDp(1f), 0f)
    }

    @Test
    fun blurPeaksHalfwayAndIsSymmetric() {
        assertEquals(8f, detailMotionBlurRadiusDp(0.5f), 0.001f)
        assertEquals(
            detailMotionBlurRadiusDp(0.25f),
            detailMotionBlurRadiusDp(0.75f),
            0.001f
        )
    }

    @Test
    fun blurClampsOutOfRangeProgress() {
        assertEquals(0f, detailMotionBlurRadiusDp(-1f), 0f)
        assertEquals(0f, detailMotionBlurRadiusDp(2f), 0f)
    }
}
