package com.xiaomanjun.sleepdownschedule

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCustomizationLogicTest {
    @Test
    fun weekScheduleWidgetIsNotExposedInAppearanceSettings() {
        assertFalse(WidgetAppearanceVariant.WEEK_SCHEDULE in ActiveWidgetAppearanceVariants)
        assertTrue(WidgetAppearanceVariant.TODAY_TOMORROW in ActiveWidgetAppearanceVariants)
    }

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
        assertEquals(
            WidgetRenderSize(336, 168),
            canonicalWidgetPreviewSize(WidgetAppearanceVariant.TODAY_TOMORROW)
        )
        assertEquals(
            WidgetRenderSize(336, 252),
            canonicalWidgetPreviewSize(WidgetAppearanceVariant.WEEK_SCHEDULE)
        )
    }

    @Test
    fun todayTomorrowWidgetAddsRowsAsTheHostGetsTaller() {
        val compact = todayTomorrowWidgetLayoutMetrics(WidgetRenderSize(320, 110))
        val standard = todayTomorrowWidgetLayoutMetrics(WidgetRenderSize(336, 160))
        val tall = todayTomorrowWidgetLayoutMetrics(WidgetRenderSize(336, 360))

        assertEquals(1, compact.maxCoursesPerDay)
        assertTrue(standard.maxCoursesPerDay >= 2)
        assertTrue(tall.maxCoursesPerDay > standard.maxCoursesPerDay)
        assertTrue(tall.maxCoursesPerDay <= 6)
        assertTrue(compact.rowHeightDp >= 30)
        assertTrue(tall.rowHeightDp <= 64)
        assertTrue(compact.timeColumnWidthDp <= standard.timeColumnWidthDp)
        assertTrue(standard.timeColumnWidthDp in 30..38)
    }

    @Test
    fun newWidgetTypographyCompensatesForLargeSystemFontsWithoutChangingGeometry() {
        val normalTodayTomorrow = todayTomorrowWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            fontScale = 1f
        )
        val largeTodayTomorrow = todayTomorrowWidgetLayoutMetrics(
            WidgetRenderSize(336, 168),
            fontScale = 1.6f
        )
        val normalWeek = weekWidgetLayoutMetrics(WidgetRenderSize(336, 252), fontScale = 1f)
        val largeWeek = weekWidgetLayoutMetrics(WidgetRenderSize(336, 252), fontScale = 1.6f)

        assertEquals(normalTodayTomorrow.rowHeightDp, largeTodayTomorrow.rowHeightDp)
        assertEquals(normalWeek.headerHeightDp, largeWeek.headerHeightDp)
        assertTrue(largeTodayTomorrow.textScale < normalTodayTomorrow.textScale)
        assertTrue(largeWeek.textScale < normalWeek.textScale)
    }

    @Test
    fun weekWidgetUsesMoreBreathingRoomWhenExpandedTowardAFullPage() {
        val standard = weekWidgetLayoutMetrics(WidgetRenderSize(336, 252))
        val expanded = weekWidgetLayoutMetrics(WidgetRenderSize(520, 680))

        assertTrue(expanded.paddingDp > standard.paddingDp)
        assertTrue(expanded.headerHeightDp > standard.headerHeightDp)
        assertTrue(expanded.weekdayHeaderHeightDp > standard.weekdayHeaderHeightDp)
        assertTrue(expanded.periodLabelWidthDp > standard.periodLabelWidthDp)
    }

    @Test
    fun standardWeekWidgetKeepsTheWholeWeekButMovesItsPeriodWindowWithTime() {
        val periods = (1..12).map { index ->
            val hour = 7 + index
            PeriodEntity(
                periodIndex = index,
                startTime = "%02d:00".format(hour),
                endTime = "%02d:45".format(hour)
            )
        }
        val size = WidgetRenderSize(336, 252)
        val morning = weekWidgetPeriodWindow(periods, size, LocalTime.of(8, 20))
        val afternoon = weekWidgetPeriodWindow(periods, size, LocalTime.of(14, 20))
        val evening = weekWidgetPeriodWindow(periods, size, LocalTime.of(21, 0))

        assertTrue(morning.count < periods.size)
        assertEquals(0, morning.firstPosition)
        assertTrue(afternoon.firstPosition > morning.firstPosition)
        assertTrue(evening.firstPosition >= afternoon.firstPosition)
        assertEquals(periods.size, evening.lastExclusive)
    }

    @Test
    fun fullPageWeekWidgetShowsEveryPeriodInsteadOfAClockWindow() {
        val periods = (1..12).map { index ->
            PeriodEntity(index, "08:00", "08:45")
        }

        assertEquals(
            WeekWidgetPeriodWindow(firstPosition = 0, count = 12),
            weekWidgetPeriodWindow(periods, WidgetRenderSize(520, 680), LocalTime.of(16, 0))
        )
    }

    @Test
    fun weekWidgetOnlyUsesPresetOutlineWithACustomWallpaper() {
        assertFalse(weekWidgetPresetOutlineEnabled(hasCustomWallpaper = false))
        assertTrue(weekWidgetPresetOutlineEnabled(hasCustomWallpaper = true))
    }

    @Test
    fun weekWidgetCurrentPeriodMarkerMatchesTheAppTimelineRule() {
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "09:00", "09:45")
        )

        assertEquals(1, weekWidgetCurrentPeriodIndex(periods, LocalTime.of(8, 50)))
        assertEquals(2, weekWidgetCurrentPeriodIndex(periods, LocalTime.of(9, 20)))
        assertEquals(null, weekWidgetCurrentPeriodIndex(periods, LocalTime.of(10, 0)))
    }

    @Test
    fun scrollableWeekWidgetShowsFivePeriodsAndStartsNearCurrentTime() {
        val periods = (1..12).map { index ->
            val hour = 7 + index
            PeriodEntity(
                periodIndex = index,
                startTime = "%02d:00".format(hour),
                endTime = "%02d:45".format(hour)
            )
        }

        assertEquals(0, weekWidgetInitialScrollPosition(periods, LocalTime.of(8, 20)))
        assertEquals(5, weekWidgetInitialScrollPosition(periods, LocalTime.of(14, 20)))
        assertEquals(7, weekWidgetInitialScrollPosition(periods, LocalTime.of(22, 0)))
    }

    @Test
    fun weekWidgetCourseTypographyKeepsVerticalAnchorsStableAcrossWidthChanges() {
        val narrow = weekWidgetCourseTextMetrics(
            cardWidthDp = 42f,
            cardHeightDp = 86f,
            hasLocation = true,
            hasTeacher = true
        )
        val wide = weekWidgetCourseTextMetrics(
            cardWidthDp = 110f,
            cardHeightDp = 86f,
            hasLocation = true,
            hasTeacher = true
        )

        assertEquals(narrow.nameSp, wide.nameSp)
        assertEquals(narrow.locationSp, wide.locationSp)
        assertEquals(narrow.teacherSp, wide.teacherSp)
        assertEquals(narrow.centerReserveDp, wide.centerReserveDp)
        assertEquals(narrow.nameMaxLines, wide.nameMaxLines)
        assertTrue(narrow.showTeacher)
    }

    @Test
    fun weekWidgetCourseTypographyHidesTeacherOnlyWhenTheCardIsTooShort() {
        val oneRow = weekWidgetCourseTextMetrics(
            cardWidthDp = 48f,
            cardHeightDp = 38f,
            hasLocation = true,
            hasTeacher = true
        )
        val spanningCard = weekWidgetCourseTextMetrics(
            cardWidthDp = 48f,
            cardHeightDp = 76f,
            hasLocation = true,
            hasTeacher = true
        )

        assertFalse(oneRow.showTeacher)
        assertTrue(spanningCard.showTeacher)
        assertTrue(oneRow.nameMaxLines >= 1)
        assertTrue(spanningCard.nameMaxLines >= oneRow.nameMaxLines)
    }

    @Test
    fun todayTomorrowWidgetLetsRemainingCoursesReplaceFinishedCourses() {
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "09:00", "09:45")
        )
        fun course(id: Long, period: Int) = CourseEntity(
            id = id,
            name = "课程$id",
            teacher = null,
            location = null,
            weekday = 1,
            periods = listOf(period),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )
        val finished = course(1, 1)
        val remaining = course(2, 2)

        assertEquals(
            listOf(remaining),
            remainingCoursesForTodayWidget(
                listOf(finished, remaining),
                periods,
                LocalTime.of(8, 50)
            )
        )
    }

    @Test
    fun wallpaperControlsResolveTheExactWidgetVariant() {
        val today = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_LARGE).copy(
            enabled = true,
            wallpaperUri = "file:///today.jpg"
        )
        val week = WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.WEEK_SCHEDULE).copy(
            enabled = true,
            wallpaperUri = "file:///week.jpg"
        )

        val disabledWeek = widgetAppearanceForType(listOf(today, week), WidgetAppearanceVariant.WEEK_SCHEDULE)
            .copy(enabled = false)

        assertEquals(WidgetAppearanceVariant.WEEK_SCHEDULE.key, disabledWeek.variant)
        assertEquals("file:///week.jpg", disabledWeek.wallpaperUri)
        assertTrue(today.enabled)
        assertEquals("file:///today.jpg", today.wallpaperUri)
    }

    @Test
    fun defaultAppearanceUpdateCannotLeakIntoTheFirstWidgetType() {
        val staleFirstPage = WidgetAppearanceEntity.defaults(
            WidgetAppearanceVariant.COURSES_LARGE
        )
        val updated = updateWidgetDefaultAppearance(
            current = staleFirstPage,
            type = WidgetAppearanceVariant.WEEK_SCHEDULE
        ) { it.copy(enabled = true, brightness = 0.62f) }

        assertEquals(WidgetAppearanceVariant.WEEK_SCHEDULE.key, updated.variant)
        assertEquals(WidgetDefaultAppearanceId, updated.appWidgetId)
        assertTrue(updated.enabled)
        assertEquals(0.62f, updated.brightness)
    }

    @Test
    fun expandedWidgetBitmapRaisesSharpnessWithoutCrossingItsPixelBudget() {
        val (width, height) = boundedWidgetBitmapSize(1008, 756, maxPixels = 384_000f)

        assertTrue(width * height <= 384_500)
        assertTrue(width > 680)
        assertEquals(4f / 3f, width.toFloat() / height.toFloat(), 0.01f)
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
        val targets = listOf(336f to 168f, 336f to 252f, 168f to 168f, 620f to 90f, 90f to 620f)
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
