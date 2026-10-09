package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.model.NotificationMode
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationAdjustmentSchedulingTest {
    private val today = LocalDate.of(2026, 10, 9)
    private val rest = ScheduleAdjustment("2026-10-12", label = "停课")
    private val makeup = ScheduleAdjustment("2026-10-10", "2026-10-08", "补课")

    private fun signature(value: String, mode: NotificationMode = NotificationMode.STANDARD) =
        NotificationScheduler.scheduleSignature(
            courses = emptyList(),
            config = defaultConfig().copy(
                notificationMode = mode,
                autoCurrentWeek = true,
                termStartDate = "2026-08-31",
                scheduleAdjustmentsJson = value
            ),
            periods = emptyList(),
            today = today
        )

    @Test
    fun addingAndRemovingRestDayInvalidatesAlarmsInBothNotificationModes() {
        NotificationMode.entries.forEach { mode ->
            val original = signature("", mode)
            val cancelled = signature(encodeScheduleAdjustments(listOf(rest)), mode)
            val restored = signature(encodeScheduleAdjustments(emptyList()), mode)

            assertNotEquals(original, cancelled)
            assertNotEquals(cancelled, restored)
            assertEquals(original, restored)
        }
    }

    @Test
    fun addingChangingAndRemovingMakeupInvalidatesAlarmsInBothNotificationModes() {
        NotificationMode.entries.forEach { mode ->
            val original = signature(encodeScheduleAdjustments(listOf(rest)), mode)
            val added = signature(encodeScheduleAdjustments(listOf(rest, makeup)), mode)
            val changedSource = signature(encodeScheduleAdjustments(listOf(
                rest, makeup.copy(sourceDate = "2026-10-09")
            )), mode)
            val changedTarget = signature(encodeScheduleAdjustments(listOf(
                rest, makeup.copy(date = "2026-10-11")
            )), mode)
            val removed = signature(encodeScheduleAdjustments(listOf(rest)), mode)

            assertNotEquals(original, added)
            assertNotEquals(added, changedSource)
            assertNotEquals(added, changedTarget)
            assertNotEquals(added, removed)
            assertEquals(original, removed)
        }
    }

    @Test
    fun changingRestDayToMakeupInvalidatesAlarms() {
        assertNotEquals(
            signature(encodeScheduleAdjustments(listOf(rest))),
            signature(encodeScheduleAdjustments(listOf(rest.copy(sourceDate = "2026-10-08"))))
        )
    }

    @Test
    fun signatureIncludesCompleteNormalizedAdjustmentSettings() {
        assertNotEquals(
            signature(encodeScheduleAdjustments(listOf(makeup))),
            signature(encodeScheduleAdjustments(listOf(makeup.copy(label = "调休补课"))))
        )
    }

    @Test
    fun reorderingEntriesAndJsonFieldsDoesNotInvalidateEquivalentAlarms() {
        val reordered = """
            [
              {"label":"停课","sourceDate":null,"date":"2026-10-12"},
              {"sourceDate":"2026-10-08","label":"补课","date":"2026-10-10"}
            ]
        """.trimIndent()

        assertEquals(
            signature(encodeScheduleAdjustments(listOf(makeup, rest))),
            signature(reordered)
        )
    }

    @Test
    fun explicitDefaultsAndLegacyEmptyValuesHaveTheSameSignature() {
        assertEquals(signature(""), signature("[]"))
        assertEquals(signature(""), signature("  "))
        assertEquals(
            signature("""[{"date":"2026-10-12"}]"""),
            signature("""[{"date":"2026-10-12","sourceDate":null,"label":""}]""")
        )
    }
}
