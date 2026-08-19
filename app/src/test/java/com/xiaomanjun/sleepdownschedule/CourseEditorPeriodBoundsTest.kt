package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditorPeriodBoundsTest {
    @Test
    fun endingPeriodNeverExceedsTheExplicitUpperBound() {
        val bounds = boundedPeriodPickerSelection(
            startIndex = 3,
            endIndex = 99,
            requestedEndIndexUpperBound = 7,
            labelsLastIndex = 11
        )

        assertEquals(3, bounds.startIndex)
        assertEquals(7, bounds.endIndex)
        assertEquals(7, bounds.endIndexUpperBound)
    }

    @Test
    fun invalidStartIsClampedBeforeBuildingEitherWheelRange() {
        val bounds = boundedPeriodPickerSelection(
            startIndex = 10,
            endIndex = -4,
            requestedEndIndexUpperBound = 5,
            labelsLastIndex = 11
        )

        assertEquals(5, bounds.startIndex)
        assertEquals(5, bounds.endIndex)
        assertEquals(5, bounds.endIndexUpperBound)
    }
}
