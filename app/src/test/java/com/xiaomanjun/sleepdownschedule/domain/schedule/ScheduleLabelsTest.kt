package com.xiaomanjun.sleepdownschedule.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleLabelsTest {
    @Test fun periodRangesKeepGapsAndOriginalNumbers() {
        assertEquals("第1–4节", compactPeriodsLabel(listOf(1, 2, 3, 4)))
        assertEquals("第1–2、5–6节", compactPeriodsLabel(listOf(6, 1, 2, 5, 2)))
        assertEquals("未指定节次", compactPeriodsLabel(emptyList()))
    }
    @Test fun continuousWeeksBecomeOneRange() {
        assertEquals("第1–8周", compactWeeksLabel((1..8).toList()))
    }
    @Test fun gapsAreRetainedRatherThanFilledIn() {
        assertEquals("第1–4、6–8周", compactWeeksLabel((1..4).toList() + (6..8).toList()))
        assertEquals("第1、3、5、7周", compactWeeksLabel(listOf(1, 3, 5, 7)))
    }
    @Test fun presentationSortsAndDeduplicatesWithoutChangingSource() {
        val source = listOf(8, 2, 1, 2, 6, 7, 4)
        assertEquals("第1–2、4、6–8周", compactWeeksLabel(source))
        assertEquals(listOf(8, 2, 1, 2, 6, 7, 4), source)
    }
    @Test fun singleAndEmptySelectionsHaveClearLabels() {
        assertEquals("第3周", compactWeeksLabel(listOf(3)))
        assertEquals("未指定周次", compactWeeksLabel(emptyList(), "未指定周次"))
    }
}
