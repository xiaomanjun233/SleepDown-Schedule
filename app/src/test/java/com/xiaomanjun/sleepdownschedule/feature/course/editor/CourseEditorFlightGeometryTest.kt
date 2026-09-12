package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import com.xiaomanjun.sleepdownschedule.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class CourseEditorFlightGeometryTest {
    @Test fun closeKeepsOpeningOrientationAndSettlesAtBothEndpoints() {
        for (delta in listOf(-400f, 400f)) {
            for (step in 0..100) {
                val p = step / 100f
                assertEquals(courseEditorOpeningTaper(1f - p, delta, 600f),
                    courseEditorOpeningTaper(p, delta, 600f, closing = true), 0.00001f)
            }
            assertEquals(0f, courseEditorOpeningTaper(1f, delta, 600f, closing = true), 0f)
            assertEquals(0f, courseEditorOpeningTaper(0f, delta, 600f, closing = true), 0f)
        }
    }

    @Test fun editorLensCornerScalesWithTheSampleTextureDensity() {
        val shape = CourseEditorMorphCornerShape(112f, 112f, 0.2f, sourceDensity = 3.5f)
        val size = Size(700f, 1200f)
        assertEquals(112f, shape.topStart.toPx(size, Density(3.5f)), 0.001f)
        assertEquals(56f, shape.topStart.toPx(size * 0.5f, Density(1.75f)), 0.001f)
    }

    @Test fun gentleTaperPreservesAtLeastSeventyTwoPercentOfTheNarrowEdge() {
        for (step in 0..100) {
            for (delta in listOf(-2000f, -300f, 0f, 300f, 2000f)) {
                val taper = courseEditorOpeningTaper(step / 100f, delta, 600f)
                assertTrue(taper.isFinite())
                assertTrue("Keep the narrow edge close to the normal width", kotlin.math.abs(taper) <= 0.145f)
            }
        }
        assertTrue(kotlin.math.abs(courseEditorOpeningTaper(0.5f, 300f, 600f)) in 0.04f..0.08f)
    }

    @Test fun recoveryIsEvenlyDistributedAfterTheInitialTaper() {
        for (delta in listOf(-400f, 400f)) {
            val samples = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f).map {
                kotlin.math.abs(courseEditorOpeningTaper(it, delta, 600f))
            }
            val expectedDrop = samples.first() / 4f
            samples.zipWithNext().forEach { (before, after) ->
                assertEquals(expectedDrop, before - after, 0.00001f)
            }
            assertTrue(kotlin.math.abs(courseEditorOpeningTaper(0.12f, delta, 600f)) > samples.first())
        }
    }

    @Test fun sharedProjectionMapsEveryShellCornerAndKeepsInteriorRowsInside() {
        val width = 360f
        val height = 600f
        for (taper in listOf(-0.14f, 0f, 0.14f)) {
            val matrix = courseEditorTaperTransform(width, height, taper)
            val top = taper.coerceAtLeast(0f) * width
            val bottom = (-taper).coerceAtLeast(0f) * width
            assertMappedPoint(matrix, 0f, 0f, top, 0f)
            assertMappedPoint(matrix, width, 0f, width - top, 0f)
            assertMappedPoint(matrix, 0f, height, bottom, height)
            assertMappedPoint(matrix, width, height, width - bottom, height)
            var previousY = -1f
            for (row in 0..20) {
                val point = project(matrix, width / 2f, height * row / 20f)
                assertEquals(width / 2f, point.x, 0.001f)
                assertTrue(point.y > previousY)
                previousY = point.y
            }
        }
    }

    @Test fun projectionIsIdenticalInFullAndHalfResolutionCoordinates() {
        for (taper in listOf(-0.14f, 0.14f)) {
            val full = courseEditorTaperTransform(700f, 1200f, taper)
            val half = courseEditorTaperTransform(350f, 600f, taper)
            for (row in 0..10) {
                val original = project(full, 120f, row * 120f)
                val sampled = project(half, 60f, row * 60f)
                assertEquals(original.x / 2f, sampled.x, 0.001f)
                assertEquals(original.y / 2f, sampled.y, 0.001f)
            }
        }
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            courseEditorTaperTransform(0f, 0f, 0.14f), 0f)
    }

    private fun project(matrix: FloatArray, x: Float, y: Float): Offset {
        val denominator = matrix[6] * x + matrix[7] * y + matrix[8]
        return Offset((matrix[0] * x + matrix[1] * y + matrix[2]) / denominator,
            (matrix[3] * x + matrix[4] * y + matrix[5]) / denominator)
    }

    private fun assertMappedPoint(matrix: FloatArray, x: Float, y: Float, expectedX: Float, expectedY: Float) {
        val actual = project(matrix, x, y)
        assertEquals(expectedX, actual.x, 0.001f)
        assertEquals(expectedY, actual.y, 0.001f)
    }

    @Test fun upperAndLowerSourcesHaveOppositeTrailingEdgesAndUndistortedEndpoints() {
        val upper = courseEditorOpeningTaper(0.4f, -400f, 600f)
        assertTrue(upper > 0f)
        assertEquals(-upper, courseEditorOpeningTaper(0.4f, 400f, 600f), 0.00001f)
        for (y in listOf(-500f, 0f, 500f)) {
            assertEquals(0f, courseEditorOpeningTaper(0f, y, 600f), 0f)
            assertEquals(0f, courseEditorOpeningTaper(1f, y, 600f), 0f)
        }
        assertEquals(0f, courseEditorOpeningTaper(0.4f, 0f, 600f), 0f)
    }

    @Test fun copyLandingFollowsDestinationColumnAndConfiguredPeriodOrder() {
        val source = CourseEntity(name = "课程", teacher = null, location = null, weekday = 2,
            periods = listOf(4, 6), weeks = listOf(1),
            weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL, note = null)
        val destination = source.copy(weekday = 4, periods = listOf(8, 10))
        val result = courseEditorWeekLandingBounds(
            Rect(100f, 210f, 180f, 366f), source, destination,
            listOf(2, 4, 6, 8, 10), rowHeight = 80f, gap = 4f
        )
        assertEquals(Rect(268f, 370f, 348f, 526f), result)
        assertNull(courseEditorWeekLandingBounds(Rect.Zero, source, destination, emptyList(), 80f, 4f))
    }

    @Test fun gridLandingHandlesNewWeekendColumnRtlAndSeparatePeriodRuns() = runBlocking {
        val course = CourseEntity(name = "课程", teacher = null, location = null, weekday = 7,
            periods = listOf(2, 3, 6), weeks = listOf(1),
            weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL, note = null)
        val grid = CourseEditorWeekGrid(Offset(20f, 100f), 700f, 80f, 4f,
            (1..8).toList(), null, 0, false)
        assertEquals(Rect(622f, 182f, 718f, 338f), grid.reveal(course, (1..7).toList()))
        assertEquals(Rect(22f, 182f, 118f, 338f), grid.copy(rightToLeft = true).reveal(course, (1..7).toList()))
        assertNull(grid.reveal(course, (1..5).toList()))
    }
}
