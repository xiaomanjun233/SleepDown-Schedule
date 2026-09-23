package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.*
import org.junit.Test

class AdjacentWeekJumpTest {
    @Test fun everyJumpUsesOneSlotInTheRequestedDirectionWithUniqueStableKeys() {
        val pages = (0 until 30).toList()
        for (source in pages) for (target in pages) {
            if (source == target) continue
            val jump = AdjacentWeekJump(source, target)
            assertEquals((target - source).compareTo(0), target - jump.sourceSlot)
            assertTrue(jump.sourceSlot in pages)
            assertEquals(source, jump.logicalPage(jump.sourceSlot))
            assertEquals(target, jump.logicalPage(target))
            assertEquals(pages, pages.map(jump::logicalPage).sorted())
            assertEquals(setOf(source, target), pages.filter(jump::contains).map(jump::logicalPage).toSet())
        }
    }

    @Test fun neighboringWeeksNeedNoKeySubstitution() {
        for (source in 1..28) for (target in listOf(source - 1, source + 1)) {
            val jump = AdjacentWeekJump(source, target)
            (0 until 30).forEach { assertEquals(it, jump.logicalPage(it)) }
        }
    }
}
