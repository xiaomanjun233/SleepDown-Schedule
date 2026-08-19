package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallationAnalyticsPolicyTest {
    @Test
    fun installationIdRequiresCanonicalUuid() {
        assertTrue(InstallationAnalytics.isValidInstallationId("236711a4-2ec5-4cb4-a993-1bb0efed72c1"))
        assertTrue(InstallationAnalytics.isValidInstallationId("236711A4-2EC5-4CB4-A993-1BB0EFED72C1"))
        assertFalse(InstallationAnalytics.isValidInstallationId("not-a-uuid"))
        assertFalse(InstallationAnalytics.isValidInstallationId("236711a42ec54cb4a9931bb0efed72c1"))
    }

    @Test
    fun successfulPingIsLimitedToOncePerNaturalDate() {
        assertTrue(InstallationAnalytics.shouldPing(null, "2026-08-16"))
        assertFalse(InstallationAnalytics.shouldPing("2026-08-16", "2026-08-16"))
        assertTrue(InstallationAnalytics.shouldPing("2026-08-16", "2026-08-17"))
    }
}
