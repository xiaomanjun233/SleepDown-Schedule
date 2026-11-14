package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class EduImportEvidenceTest {
    @Test fun entryDoesNotCountAsInvestigationAndRealInterfaceWorkNeedsPageReview() {
        val evidence = EduImportEvidence()
        assertFalse(evidence.readyToSubmit)
        evidence.recordInterfaceAttempt()
        assertFalse(evidence.readyToSubmit)
        evidence.recordPageRead()
        assertTrue(evidence.readyToSubmit)
    }
    @Test fun pageFallbackRequiresObservedPageAndAnExplicitReason() {
        val evidence = EduImportEvidence()
        assertThrows(IllegalArgumentException::class.java) { evidence.choosePageFallback("页面未提供任何结构化来源") }
        evidence.recordPageRead()
        assertFalse(evidence.readyToSubmit)
        assertThrows(IllegalArgumentException::class.java) { evidence.choosePageFallback("试试") }
        evidence.choosePageFallback("页面为静态课表表格，没有整学期接口或内嵌结构化数据")
        assertTrue(evidence.readyToSubmit)
    }
    @Test fun entryContainsOnlyRedactedAddressAndTitleAndPreservesTheRealPort() {
        val input = eduWorkspaceEntryText("https://student:secret@edu.example.edu:8443/portal?token=private#secret", "教务门户")
        assertTrue(input.contains("https://edu.example.edu:8443/portal"))
        assertTrue(input.contains("没有发送页面全文或截图"))
        assertFalse(input.contains("private"))
        assertFalse(input.contains("secret"))
    }
}
