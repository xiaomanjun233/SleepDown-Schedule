package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.model.WeekParity

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedWeekNormalizationTest {
    @Test
    fun `complete even week list becomes editable even range`() {
        val result = normalizeImportedWeekSelection(
            weeks = listOf(2, 4, 6, 8, 10, 12, 14, 16, 18),
            parity = WeekParity.ALL
        )

        assertEquals((2..18).toList(), result.weeks)
        assertEquals(WeekParity.EVEN, result.parity)
    }

    @Test
    fun `irregular even week list stays explicit`() {
        val result = normalizeImportedWeekSelection(
            weeks = listOf(2, 6, 8),
            parity = WeekParity.ALL
        )

        assertEquals(listOf(2, 6, 8), result.weeks)
        assertEquals(WeekParity.ALL, result.parity)
    }
}
