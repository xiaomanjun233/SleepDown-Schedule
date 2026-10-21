package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOsAlertSlotsTest {
    @Test fun threeActionsKeepPrimaryAtTheTopAndCancelAtTheBottom() {
        val styles = listOf(LiquidAlertActionStyle.Primary, LiquidAlertActionStyle.Destructive, LiquidAlertActionStyle.Cancel)
        for (first in styles) for (second in styles.filter { it != first }) {
            val order = listOf(first, second, styles.single { it != first && it != second })
            val slots = colorOsAlertSlots(order)
            assertEquals(3, slots.toSet().size)
            assertEquals(ColorOsAlertSlot.Negative, slots[order.indexOf(LiquidAlertActionStyle.Cancel)])
            assertEquals(ColorOsAlertSlot.Neutral, slots[order.indexOf(LiquidAlertActionStyle.Primary)])
            assertEquals(ColorOsAlertSlot.Positive, slots[order.indexOf(LiquidAlertActionStyle.Destructive)])
        }
    }

    @Test fun shareActionsMapToPrimaryThenOrdinaryThenCancelRegardlessOfInputOrder() {
        val styles = listOf(LiquidAlertActionStyle.Secondary, LiquidAlertActionStyle.Cancel, LiquidAlertActionStyle.Primary)
        val callbacks = listOf("share-ics", "cancel", "share-code")
        val slots = colorOsAlertSlots(styles)
        assertEquals("share-code", callbacks[slots.indexOf(ColorOsAlertSlot.Neutral)])
        assertEquals("share-ics", callbacks[slots.indexOf(ColorOsAlertSlot.Positive)])
        assertEquals("cancel", callbacks[slots.indexOf(ColorOsAlertSlot.Negative)])
        assertEquals(listOf(ColorOsAlertSlot.Negative, ColorOsAlertSlot.Positive),
            colorOsAlertSlots(listOf(LiquidAlertActionStyle.Secondary, LiquidAlertActionStyle.Primary)))
    }

    @Test fun destructiveConfirmationNeverAssignsTheCancelActionToPositive() {
        assertEquals(listOf(ColorOsAlertSlot.Negative, ColorOsAlertSlot.Positive),
            colorOsAlertSlots(listOf(LiquidAlertActionStyle.Cancel, LiquidAlertActionStyle.Destructive)))
        assertEquals(listOf(ColorOsAlertSlot.Positive, ColorOsAlertSlot.Negative),
            colorOsAlertSlots(listOf(LiquidAlertActionStyle.Destructive, LiquidAlertActionStyle.Cancel)))
    }

    @Test fun customFormsAndSingleActionsKeepTheirExistingDispatch() {
        assertEquals(emptyList<ColorOsAlertSlot>(), colorOsAlertSlots(emptyList()))
        assertEquals(listOf(ColorOsAlertSlot.Positive), colorOsAlertSlots(listOf(LiquidAlertActionStyle.Secondary)))
    }
}
