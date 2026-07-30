package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCustomizationLogicTest {
    @Test
    fun tabletPreviewUsesRemoteViewsLogicalSizeWithoutUpscaling() {
        assertEquals(
            WidgetRenderSize(336, 168),
            canonicalWidgetPreviewSize(WidgetAppearanceVariant.COURSES_LARGE)
        )
        assertEquals(
            WidgetRenderSize(168, 168),
            canonicalWidgetPreviewSize(WidgetAppearanceVariant.COURSES_SQUARE)
        )
        assertEquals(
            WidgetRenderSize(336, 168),
            canonicalWidgetPreviewSize(WidgetAppearanceVariant.TODAY_ASSISTANT)
        )
    }

    @Test
    fun copiedDefaultBecomesIndependentInstance() {
        val default = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_LARGE).copy(
            enabled = true,
            wallpaperUri = "file:///shared.jpg",
            blurDp = 9f
        )
        val instance = default.copy(appWidgetId = 42)
        val changedDefault = default.copy(blurDp = 18f)

        assertEquals(9f, instance.blurDp)
        assertEquals(18f, changedDefault.blurDp)
        assertEquals("file:///shared.jpg", instance.wallpaperUri)
    }

    @Test
    fun appearanceValuesAreClampedToSupportedRanges() {
        val normalized = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_SQUARE).copy(
            centerX = -2f,
            centerY = 3f,
            scale = 20f,
            blurDp = 99f,
            brightness = 0.1f
        ).normalized()

        assertEquals(0f, normalized.centerX)
        assertEquals(1f, normalized.centerY)
        assertEquals(6f, normalized.scale)
        assertEquals(10f, normalized.blurDp)
        assertEquals(0.35f, normalized.brightness)
    }

    @Test
    fun sharedWallpaperIsDeletedOnlyAfterLastReferenceDisappears() {
        val shared = "file:///shared.jpg"
        val other = "file:///other.jpg"
        val first = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_LARGE).copy(wallpaperUri = shared)
        val second = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_SQUARE, 7).copy(wallpaperUri = shared)

        assertFalse(shared in unreferencedWidgetWallpaperUris(listOf(first, second), listOf(shared, other)))
        assertTrue(other in unreferencedWidgetWallpaperUris(listOf(first, second), listOf(shared, other)))
        assertTrue(shared in unreferencedWidgetWallpaperUris(emptyList(), listOf(shared)))
    }

    @Test
    fun cropAlwaysCoversWideSquareAndExtremeTargets() {
        val targets = listOf(336f to 168f, 168f to 168f, 620f to 90f, 90f to 620f)
        targets.forEach { (width, height) ->
            val rect = calculateFocusCropRect(
                bitmapWidth = 4032,
                bitmapHeight = 3024,
                containerWidth = width,
                containerHeight = height,
                cropState = WallpaperCropState(centerX = 0.98f, centerY = 0.02f, scale = 5.8f)
            )
            assertTrue(rect.left <= 0f)
            assertTrue(rect.top <= 0f)
            assertTrue(rect.right >= width)
            assertTrue(rect.bottom >= height)
        }
    }

    @Test
    fun clampedFocusStaysStableAcrossResize() {
        val original = WallpaperCropState(0.5f, 0.5f, 2.2f)
        val square = clampCropState(original, 4000, 3000, 180, 180)
        val wide = clampCropState(square, 4000, 3000, 360, 180)

        assertEquals(square.centerX, wide.centerX, 0.0001f)
        assertEquals(square.centerY, wide.centerY, 0.0001f)
        assertEquals(2.2f, wide.scale, 0.0001f)
    }
}
