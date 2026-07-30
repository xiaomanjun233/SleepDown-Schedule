package com.example.courseschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDownloadPermissionTest {
    @Test
    fun androidEightAndNineRequestPermissionOnlyWhenMissing() {
        assertTrue(shouldRequestLegacyDownloadPermission(sdkInt = 26, permissionGranted = false))
        assertTrue(shouldRequestLegacyDownloadPermission(sdkInt = 28, permissionGranted = false))
        assertFalse(shouldRequestLegacyDownloadPermission(sdkInt = 28, permissionGranted = true))
    }

    @Test
    fun scopedStorageVersionsNeverRequestLegacyPermission() {
        assertFalse(shouldRequestLegacyDownloadPermission(sdkInt = 29, permissionGranted = false))
        assertFalse(shouldRequestLegacyDownloadPermission(sdkInt = 37, permissionGranted = false))
    }
}
