package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCustomizationLogicTest {
    @Test
    fun assistantLayoutAdaptsContinuouslyToRealHostBoundsAndFontScale() {
        val compact = assistantWidgetLayoutMetrics(WidgetRenderSize(220, 105), fontScale = 1f)
        val comfortable = assistantWidgetLayoutMetrics(WidgetRenderSize(340, 160), fontScale = 1f)
        val largeFont = assistantWidgetLayoutMetrics(WidgetRenderSize(340, 160), fontScale = 1.5f)

        assertTrue(compact.verticalPaddingDp < comfortable.verticalPaddingDp)
        assertTrue(compact.horizontalPaddingDp < comfortable.horizontalPaddingDp)
        assertTrue(compact.textScale < comfortable.textScale)
        assertTrue(largeFont.textScale < comfortable.textScale)
    }

    @Test
    fun launcherReportedCompactHeightIsNotInflatedPastItsRealBounds() {
        assertEquals(WidgetRenderSize(320, 96), normalizedWidgetRenderSize(320, 96))
        assertEquals(WidgetRenderSize(80, 80), normalizedWidgetRenderSize(0, 0))
    }

    @Test
    fun courseIndicatorOnlyGrowsModestlyWithAnExpandedCourseGroup() {
        val compact = courseIndicatorHeightDp(WidgetRenderSize(320, 96), TodayWidgetVariant.LARGE)
        val regular = courseIndicatorHeightDp(WidgetRenderSize(336, 168), TodayWidgetVariant.LARGE)
        val tall = courseIndicatorHeightDp(WidgetRenderSize(336, 260), TodayWidgetVariant.LARGE)

        assertTrue(compact <= regular)
        assertTrue(tall > regular)
        assertTrue(compact >= 8)
        assertTrue(tall <= 44)
    }

    @Test
    fun eachCourseIndicatorFitsItsOwnCenteredContentRegion() {
        val size = WidgetRenderSize(336, 168)
        val list = coursesWidgetLayoutMetrics(size, TodayWidgetVariant.LARGE, courseCount = 2)
        val grid = coursesWidgetLayoutMetrics(size, TodayWidgetVariant.LARGE, courseCount = 4)

        assertTrue(list.indicatorHeightDp <= list.groupHeightDp - list.groupVerticalPaddingDp * 2)
        assertTrue(grid.indicatorHeightDp <= grid.groupHeightDp - grid.groupVerticalPaddingDp * 2)
        assertEquals(list.groupHeightDp, grid.groupHeightDp)
    }

    @Test
    fun compactHeightUsesOneGridRowInsteadOfCrushingTwoListRows() {
        val compact = coursesWidgetLayoutMetrics(
            WidgetRenderSize(320, 110),
            TodayWidgetVariant.LARGE,
            courseCount = 2
        )

        assertTrue(compact.useGrid)
        assertEquals(1, compact.rowCapacity)
        assertEquals(2, compact.maxCourses)
        assertTrue(compact.groupHeightDp >= 44)
    }

    @Test
    fun foldingUsesActualHostHeightInsteadOfCourseCountAlone() {
        val short = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            TodayWidgetVariant.LARGE,
            courseCount = 3
        )
        val tall = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 280),
            TodayWidgetVariant.LARGE,
            courseCount = 3
        )

        assertTrue(short.useGrid)
        assertFalse(tall.useGrid)
        assertEquals(3, tall.maxCourses)
        assertEquals(3, tall.rowCapacity)
    }

    @Test
    fun tallerWidgetKeepsCourseGroupsCompactAndAddsCapacity() {
        val regular = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            TodayWidgetVariant.LARGE,
            courseCount = 8
        )
        val tall = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 340),
            TodayWidgetVariant.LARGE,
            courseCount = 8
        )

        assertTrue(tall.groupHeightDp > regular.groupHeightDp)
        assertTrue(tall.groupHeightDp - regular.groupHeightDp <= 12)
        assertEquals(4, regular.maxCourses)
        assertEquals(8, tall.maxCourses)
        assertTrue(tall.groupCornerRadiusDp > regular.groupCornerRadiusDp)
    }

    @Test
    fun largeFontScaleShrinksTextWithoutChangingCourseGeometry() {
        val normal = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            TodayWidgetVariant.LARGE,
            courseCount = 4,
            fontScale = 1f
        )
        val largeFont = coursesWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            TodayWidgetVariant.LARGE,
            courseCount = 4,
            fontScale = 1.5f
        )

        assertEquals(normal.groupHeightDp, largeFont.groupHeightDp)
        assertTrue(largeFont.textScale < normal.textScale)
    }

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
