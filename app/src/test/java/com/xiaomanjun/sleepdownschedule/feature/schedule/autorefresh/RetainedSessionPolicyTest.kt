package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.*
import org.junit.Test

class RetainedSessionPolicyTest {
    private val profile = AutoRefreshScheduleProfile(
        schoolId = "school", schoolName = "学校", adapterId = "adapter", adapterName = "教务",
        username = "", password = "", scheduleId = 1
    )

    @Test fun existingApiProfilesKeepTheirSchedulingBehavior() {
        assertFalse(profile.sessionOnly)
        assertFalse(profile.automaticRefreshEnabled)
        assertTrue(profile.copy(automatic = true).automaticRefreshEnabled)
    }

    @Test fun retainedOnlyProfilesNeverScheduleAnAutomaticImport() {
        assertFalse(profile.copy(sessionOnly = true).automaticRefreshEnabled)
        assertFalse(profile.copy(sessionOnly = true, automatic = true).automaticRefreshEnabled)
    }
}
