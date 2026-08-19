package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun settledHistoryPageReleasesItsTransientMorphClip() {
        assertTrue(detailMorphUsesTransientClip(0.998f, closing = false))
        assertFalse(detailMorphUsesTransientClip(1f, closing = false))
        assertTrue(detailMorphUsesTransientClip(1f, closing = true))
    }

    @Test
    fun snapshotDepthSupportsBothDetailShrinkAndHomeDestinationZoom() {
        assertEquals(0f, morphSnapshotDepthProgress(1f), 0.0001f)
        assertEquals(1f, morphSnapshotDepthProgress(0.92f), 0.0001f)
        assertEquals(1f, morphSnapshotDepthProgress(1.08f), 0.0001f)
    }
}
