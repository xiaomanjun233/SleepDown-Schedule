package com.xiaomanjun.sleepdownschedule.feature.schedule.picker

import org.junit.Assert.*
import org.junit.Test

class QuickScheduleDraftTest {
    private val baseline = QuickScheduleDraft(7, 18, 3, true, true, "2026-09-01")

    @Test fun detailedSettingsChangesReachAnAlreadyOpenSheet() {
        val saved = baseline.copy(hideEmptyWeekends = false, showNonCurrentWeekCourses = true, weekStartsOnSunday = true)
        assertEquals(saved, rebaseQuickScheduleDraft(baseline, baseline, saved))
    }

    @Test fun externalSavePreservesUnrelatedUnconfirmedSheetEdits() {
        val local = baseline.copy(totalWeeks = 20, termStartDate = "2026-09-08")
        val saved = baseline.copy(hideEmptyWeekends = false)
        assertEquals(local.copy(hideEmptyWeekends = false), rebaseQuickScheduleDraft(local, baseline, saved))
    }

    @Test fun savedSheetValuesBecomeTheBaselineForTheNextDetailSave() {
        val local = baseline.copy(hideEmptyWeekends = false)
        val acknowledged = rebaseQuickScheduleDraft(local, baseline, local)
        val afterDetail = local.copy(hideEmptyWeekends = true, totalWeeks = 22)
        assertEquals(afterDetail, rebaseQuickScheduleDraft(acknowledged, local, afterDetail))
    }
}
