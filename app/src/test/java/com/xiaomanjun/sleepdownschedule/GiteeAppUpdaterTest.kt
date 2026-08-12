package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiteeAppUpdaterTest {
    @Test
    fun comparesNumericVersions() {
        assertTrue(GiteeAppUpdater.isVersionNewer("v1.1", "1.0"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.0.1", "1.0"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.0", "1.0"))
        assertFalse(GiteeAppUpdater.isVersionNewer("v0.9.9", "1.0"))
    }

    @Test
    fun stableReleaseSupersedesSameVersionBeta() {
        assertTrue(GiteeAppUpdater.isVersionNewer("1.0", "1.0 beta"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.0 beta", "1.0"))
    }
}
