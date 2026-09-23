package com.xiaomanjun.sleepdownschedule.feature.experimental

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentalNotificationModeTest {
    @Test
    fun eachVendorHasOnlyItsRequestedModes() {
        val base = listOf(ExperimentalNotificationMode.STANDARD, ExperimentalNotificationMode.LIVE_UPDATE)
        assertEquals(base, ExperimentalNotificationModes.availableForDevice(false, false, true, false))
        assertEquals(base + listOf(
            ExperimentalNotificationMode.FLUID_CLOUD,
            ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE
        ), ExperimentalNotificationModes.availableForDevice(true, false, true, false))
        assertEquals(base + ExperimentalNotificationMode.YOYO_LIVE_UPDATE,
            ExperimentalNotificationModes.availableForDevice(true, true, true, false))
        assertEquals(base + ExperimentalNotificationMode.SUPER_ISLAND,
            ExperimentalNotificationModes.availableForDevice(true, false, false, true))
        assertEquals(base, ExperimentalNotificationModes.availableForDevice(true, false, false, false))
    }

    @Test
    fun detectsXiaomiFamilyBrands() {
        assertEquals(true, XiaomiSuperIsland.isXiaomiDevice("Xiaomi", "Xiaomi"))
        assertEquals(true, XiaomiSuperIsland.isXiaomiDevice("unknown", "Redmi"))
        assertEquals(true, XiaomiSuperIsland.isXiaomiDevice("unknown", "POCO"))
        assertEquals(false, XiaomiSuperIsland.isXiaomiDevice("HONOR", "HONOR"))
    }
}
