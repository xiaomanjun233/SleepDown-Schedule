package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.Application
import android.content.Context
import android.content.Intent
import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveUpdatePayloadCodecTest {
    private val payload = LiveUpdatePayload(
        kind = LiveUpdateKind.TOMORROW, name = "明天有2门课", timeText = "08:00开始", location = "教学楼",
        showActions = false, muteKey = "tomorrow:2026-10-21", muteUntil = "9000",
        chipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
        segments = listOf(LiveUpdateSegment(1000, 2000), LiveUpdateSegment(3000, 4000)),
        duringClassEnabled = true, breakStatusEnabled = false, expiresAtMillis = 9000,
        tomorrowCourseCount = 2
    )

    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences(
        LiveUpdatePayload.PREFS, Context.MODE_PRIVATE
    ).also { it.edit().clear().commit() }

    private fun minimalIntent() = Intent().putExtra("courseName", "课程").putExtra("timeText", "08:00 - 08:45")

    @Test fun writingIntentPreservesPublishedKeysValuesAndTheAlarmEnvelope() {
        val intent = Intent("existing.action").putExtra("reminder_event", "course")
        assertSame(intent, NotificationScheduler.putPayload(intent, payload))
        val expected = mapOf(
            "live_update_kind" to "TOMORROW", "live_update_name" to payload.name,
            "live_update_time" to payload.timeText, "live_update_location" to payload.location,
            "live_update_actions" to false, "live_update_mute_key" to payload.muteKey,
            "live_update_mute_until" to payload.muteUntil, "live_update_chip_mode" to "COUNTDOWN",
            "live_update_segments" to "1000:2000;3000:4000", "live_update_during_class" to true,
            "live_update_break_status" to false, "live_update_expires_at" to 9000L,
            "live_update_tomorrow_count" to 2, "reminder_event" to "course"
        )
        val extras = intent.extras!!
        assertEquals(expected.keys, extras.keySet())
        expected.forEach { (key, value) -> assertEquals(key, value, extras.get(key)) }
        assertEquals("existing.action", intent.action)
        assertEquals(payload, NotificationScheduler.payloadFromIntent(intent))
    }

    @Test fun previouslyScheduledLiteralExtrasDecodeWithoutUsingTheWriter() {
        val intent = Intent()
            .putExtra("live_update_kind", "TOMORROW").putExtra("live_update_name", payload.name)
            .putExtra("live_update_time", payload.timeText).putExtra("live_update_location", payload.location)
            .putExtra("live_update_actions", false).putExtra("live_update_mute_key", payload.muteKey)
            .putExtra("live_update_mute_until", payload.muteUntil).putExtra("live_update_chip_mode", "COUNTDOWN")
            .putExtra("live_update_segments", "1000:2000;3000:4000").putExtra("live_update_during_class", true)
            .putExtra("live_update_break_status", false).putExtra("live_update_expires_at", 9000L)
            .putExtra("live_update_tomorrow_count", 2)
        assertEquals(payload, NotificationScheduler.payloadFromIntent(intent))
    }

    @Test fun oldAlarmAliasesAndTheirDefaultsRemainReadable() {
        val intent = minimalIntent().putExtra("location", "旧教室")
            .putExtra("liveUpdateActionsEnabled", false).putExtra("muteKey", "old-key")
            .putExtra("muteUntil", "old-until").putExtra("liveUpdateChipTextMode", "SHORT")
        assertEquals(LiveUpdatePayload(
            name = "课程", timeText = "08:00 - 08:45", location = "旧教室", showActions = false,
            muteKey = "old-key", muteUntil = "old-until", chipTextMode = LiveUpdateChipTextMode.SHORT
        ), NotificationScheduler.payloadFromIntent(intent))
        val defaults = NotificationScheduler.payloadFromIntent(minimalIntent())!!
        assertTrue(defaults.showActions)
        assertTrue(defaults.breakStatusEnabled)
        assertFalse(defaults.duringClassEnabled)
        assertEquals(LiveUpdateKind.COURSE, defaults.kind)
        assertEquals(LiveUpdateChipTextMode.LOCATION, defaults.chipTextMode)
        assertEquals(0L, defaults.expiresAtMillis)
    }

    @Test fun currentKeysTakePriorityOverOldAliasesIncludingFalseAndEmptyValues() {
        val intent = minimalIntent().putExtra("location", "旧教室")
            .putExtra("liveUpdateActionsEnabled", true).putExtra("liveUpdateChipTextMode", "SHORT")
        NotificationScheduler.putPayload(intent, payload.copy(location = ""))
        assertEquals(payload.copy(location = ""), NotificationScheduler.payloadFromIntent(intent))
    }

    @Test fun unknownEnumsAndMalformedSegmentsKeepTheExistingFallbackPolicyInBothSources() {
        val malformed = "bad;10:20;30:30;40:35;50:no;60:70;80;90:100:110"
        val intent = minimalIntent().putExtra("live_update_kind", "FUTURE")
            .putExtra("live_update_chip_mode", "FUTURE").putExtra("live_update_segments", malformed)
        val prefs = prefs()
        prefs.edit().putString("name", "课程").putString("time", "08:00 - 08:45")
            .putString("kind", "FUTURE").putString("chip_mode", "FUTURE")
            .putString("segments", malformed).commit()
        val fromIntent = NotificationScheduler.payloadFromIntent(intent)!!
        val restored = LiveUpdatePayloadCodec.restorePayload(prefs)!!
        assertEquals(fromIntent, restored)
        assertEquals(listOf(LiveUpdateSegment(10, 20), LiveUpdateSegment(60, 70)), restored.segments)
        assertEquals(LiveUpdateKind.COURSE, restored.kind)
        assertEquals(LiveUpdateChipTextMode.LOCATION, restored.chipTextMode)
    }

    @Test fun serviceStorageKeepsEveryPublishedPreferenceKeyAndRestoresTheFullPayload() {
        val prefs = prefs()
        prefs.edit().putString("unrelated", "retained").commit()
        LiveUpdatePayloadCodec.storePayload(prefs, payload)
        val expected = mapOf(
            "kind" to "TOMORROW", "name" to payload.name, "time" to payload.timeText,
            "location" to payload.location, "actions" to false, "mute_key" to payload.muteKey,
            "mute_until" to payload.muteUntil, "chip_mode" to "COUNTDOWN",
            "segments" to "1000:2000;3000:4000", "during_class" to true, "break_status" to false,
            "expires_at" to 9000L, "tomorrow_count" to 2, "unrelated" to "retained"
        )
        assertEquals(expected, prefs.all)
        assertEquals(payload, LiveUpdatePayloadCodec.restorePayload(prefs))
    }

    @Test fun oldServicePreferencesRestoreWithTheSameDefaultsWithoutARewrite() {
        val prefs = prefs()
        prefs.edit().putString("name", "课程").putString("time", "08:00 - 08:45").commit()
        val before = prefs.all.toMap()
        assertEquals(NotificationScheduler.payloadFromIntent(minimalIntent()), LiveUpdatePayloadCodec.restorePayload(prefs))
        assertEquals(before, prefs.all)
    }

    @Test fun missingRequiredFieldsAndBlankRestartValuesKeepTheirDifferentExistingSemantics() {
        assertNull(NotificationScheduler.payloadFromIntent(Intent()))
        assertNull(NotificationScheduler.payloadFromIntent(Intent().putExtra("courseName", "课程")))
        assertNotNull(NotificationScheduler.payloadFromIntent(Intent().putExtra("courseName", "").putExtra("timeText", "")))
        val prefs = prefs()
        assertNull(LiveUpdatePayloadCodec.restorePayload(prefs))
        prefs.edit().putString("name", "课程").putString("time", " ").commit()
        assertNull(LiveUpdatePayloadCodec.restorePayload(prefs))
    }
}
