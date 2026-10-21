package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorOsMaterialCornersTest {
    @Test fun fullScreenRectangleClearsTheCapsuleCategory() {
        assertEquals(ColorOsMaterialCorners(0f, 3f), colorOsMaterialCorners(
            androidx.compose.ui.graphics.RectangleShape, Size(360f, 800f), Density(1f), LayoutDirection.Ltr))
    }
    @Test fun tallCourseCardUsesItsOwnCornerInsteadOfHalfTheHeight() {
        assertEquals(ColorOsMaterialCorners(24f, 3f),
            colorOsMaterialCorners(RoundedRectangle(12.dp), Size(160f, 480f), Density(2f), LayoutDirection.Ltr))
    }
    @Test fun capsulesTrackSizeAndUseTheNativeCircleWeightOnlyForCircles() {
        fun corners(size: Size) = colorOsMaterialCorners(Capsule(), size, Density(1f), LayoutDirection.Ltr)
        assertEquals(ColorOsMaterialCorners(20f, 1f), corners(Size(140f, 40f)))
        assertEquals(ColorOsMaterialCorners(30f, 1f), corners(Size(180f, 60f)))
        assertEquals(ColorOsMaterialCorners(20f, 2f), corners(Size(40f, 40f)))
    }
    @Test fun changingPanelRadiusAndSizeDoesNotRetainCollapsedCapsuleGeometry() {
        val density = Density(1f)
        val collapsed = colorOsMaterialCorners(RoundedRectangle(16.dp), Size(160f, 32f), density, LayoutDirection.Ltr)
        val expanded = colorOsMaterialCorners(RoundedRectangle(30.dp), Size(360f, 180f), density, LayoutDirection.Ltr)
        assertEquals(ColorOsMaterialCorners(16f, 1f), collapsed)
        assertEquals(ColorOsMaterialCorners(30f, 3f), expanded)
    }
    @Test fun unevenPopupCornersAreNotCoercedIntoOneCapsule() {
        assertNull(colorOsMaterialCorners(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp,
            bottomStart = 24.dp, bottomEnd = 24.dp), Size(200f, 200f), Density(1f), LayoutDirection.Rtl))
    }
}
