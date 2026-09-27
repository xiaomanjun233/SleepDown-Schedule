package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class EduPageCompactionTest {
    private fun snapshot() = EduPageSnapshot("课表", "https://school.example/kb?token=fixture-secret", 0, 0,
        "星期一 | 星期二\n[rowspan=2] 高等数学 | [空]\n高等数学 | [空]", "重复课程容器",
        "<table>重复 HTML</table>", "第一学期", "", "", "课表说明：第八周停课", emptyList())

    @Test fun repeatedScrollSnapshotsDoNotRepeatTablesOrHtml() {
        val result = compactEduPageSnapshots(listOf(snapshot(), snapshot().copy(scrollY = 100)))
        assertEquals(1, Regex("星期一").findAll(result).count())
        assertEquals(2, Regex("高等数学").findAll(result).count())
        assertTrue(result.contains("rowspan=2"))
        assertTrue(result.contains("第八周停课"))
        assertFalse(result.contains("fixture-secret"))
        assertFalse(result.contains("重复 HTML"))
        assertFalse(result.contains("重复课程容器"))
    }

    @Test fun changedVirtualRowsAndNonTableStructuresRemainAvailable() {
        val first = snapshot()
        val result = compactEduPageSnapshots(listOf(first,
            first.copy(tables = "星期三 | 物理"),
            first.copy(tables = "", semanticHtml = "<div data-day='4'>化学</div>",
                iframeText = "iframe课程", shadowText = "shadow课程")))
        listOf("高等数学", "物理", "data-day='4'", "化学", "iframe课程", "shadow课程").forEach {
            assertTrue(it, result.contains(it))
        }
    }
}
