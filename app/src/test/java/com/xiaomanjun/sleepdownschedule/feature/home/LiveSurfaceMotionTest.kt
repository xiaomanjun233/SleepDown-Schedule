package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.glass.CourseGlassRestoreGroupsPerBatch
import com.xiaomanjun.sleepdownschedule.glass.courseGlassRestoreFrameDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSurfaceMotionTest {
    @Test
    fun restoreAcceptsSixtyHzJitterWithoutDoublingHighRefreshWork() {
        val start = 1_000_000_000L
        assertTrue(courseGlassRestoreFrameDue(start, start + 16_500_000L))
        assertFalse(courseGlassRestoreFrameDue(start, start + 8_333_333L))
        assertFalse(courseGlassRestoreFrameDue(start, start + 11_111_111L))
        assertTrue(courseGlassRestoreFrameDue(start, start + 16_666_666L))
        assertTrue(courseGlassRestoreFrameDue(start, start + 22_222_222L))
        assertEquals(2, CourseGlassRestoreGroupsPerBatch)
    }
}
