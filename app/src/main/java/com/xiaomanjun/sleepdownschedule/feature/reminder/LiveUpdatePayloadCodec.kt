package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode

/** Stable alarm/service wire keys and restart storage; scheduling and Service lifetime stay outside. */
internal object LiveUpdatePayloadCodec {
    const val EXTRA_LIVE_UPDATE_NAME = "live_update_name"
    const val EXTRA_LIVE_UPDATE_TIME = "live_update_time"
    const val EXTRA_LIVE_UPDATE_LOCATION = "live_update_location"
    const val EXTRA_LIVE_UPDATE_ACTIONS = "live_update_actions"
    const val EXTRA_LIVE_UPDATE_MUTE_KEY = "live_update_mute_key"
    const val EXTRA_LIVE_UPDATE_MUTE_UNTIL = "live_update_mute_until"
    const val EXTRA_LIVE_UPDATE_CHIP_MODE = "live_update_chip_mode"
    const val EXTRA_LIVE_UPDATE_KIND = "live_update_kind"
    const val EXTRA_LIVE_UPDATE_SEGMENTS = "live_update_segments"
    const val EXTRA_LIVE_UPDATE_DURING_CLASS = "live_update_during_class"
    const val EXTRA_LIVE_UPDATE_BREAK_STATUS = "live_update_break_status"
    const val EXTRA_LIVE_UPDATE_EXPIRES_AT = "live_update_expires_at"
    const val EXTRA_LIVE_UPDATE_TOMORROW_COUNT = "live_update_tomorrow_count"

    fun putPayload(intent: Intent, payload: LiveUpdatePayload): Intent =
        intent.putExtra(EXTRA_LIVE_UPDATE_KIND, payload.kind.name)
            .putExtra(EXTRA_LIVE_UPDATE_NAME, payload.name)
            .putExtra(EXTRA_LIVE_UPDATE_TIME, payload.timeText)
            .putExtra(EXTRA_LIVE_UPDATE_LOCATION, payload.location)
            .putExtra(EXTRA_LIVE_UPDATE_ACTIONS, payload.showActions)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_KEY, payload.muteKey)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_UNTIL, payload.muteUntil)
            .putExtra(EXTRA_LIVE_UPDATE_CHIP_MODE, payload.chipTextMode.name)
            .putExtra(EXTRA_LIVE_UPDATE_SEGMENTS, encodeSegments(payload.segments))
            .putExtra(EXTRA_LIVE_UPDATE_DURING_CLASS, payload.duringClassEnabled)
            .putExtra(EXTRA_LIVE_UPDATE_BREAK_STATUS, payload.breakStatusEnabled)
            .putExtra(EXTRA_LIVE_UPDATE_EXPIRES_AT, payload.expiresAtMillis)
            .putExtra(EXTRA_LIVE_UPDATE_TOMORROW_COUNT, payload.tomorrowCourseCount)

    fun payloadFromIntent(intent: Intent): LiveUpdatePayload? {
        val name = intent.getStringExtra(EXTRA_LIVE_UPDATE_NAME)
            ?: intent.getStringExtra("courseName")
            ?: return null
        val timeText = intent.getStringExtra(EXTRA_LIVE_UPDATE_TIME)
            ?: intent.getStringExtra("timeText")
            ?: return null
        return LiveUpdatePayload(
            kind = runCatching {
                LiveUpdateKind.valueOf(intent.getStringExtra(EXTRA_LIVE_UPDATE_KIND) ?: LiveUpdateKind.COURSE.name)
            }.getOrDefault(LiveUpdateKind.COURSE),
            name = name,
            timeText = timeText,
            location = intent.getStringExtra(EXTRA_LIVE_UPDATE_LOCATION)
                ?: intent.getStringExtra("location")
                ?: "",
            showActions = intent.getBooleanExtra(
                EXTRA_LIVE_UPDATE_ACTIONS,
                intent.getBooleanExtra("liveUpdateActionsEnabled", true)
            ),
            muteKey = intent.getStringExtra(EXTRA_LIVE_UPDATE_MUTE_KEY)
                ?: intent.getStringExtra("muteKey")
                ?: "",
            muteUntil = intent.getStringExtra(EXTRA_LIVE_UPDATE_MUTE_UNTIL)
                ?: intent.getStringExtra("muteUntil")
                ?: "",
            chipTextMode = runCatching {
                LiveUpdateChipTextMode.valueOf(
                    intent.getStringExtra(EXTRA_LIVE_UPDATE_CHIP_MODE)
                        ?: intent.getStringExtra("liveUpdateChipTextMode")
                        ?: LiveUpdateChipTextMode.LOCATION.name
                )
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION),
            segments = decodeSegments(intent.getStringExtra(EXTRA_LIVE_UPDATE_SEGMENTS).orEmpty()),
            duringClassEnabled = intent.getBooleanExtra(EXTRA_LIVE_UPDATE_DURING_CLASS, false),
            breakStatusEnabled = intent.getBooleanExtra(EXTRA_LIVE_UPDATE_BREAK_STATUS, true),
            expiresAtMillis = intent.getLongExtra(EXTRA_LIVE_UPDATE_EXPIRES_AT, 0L),
            tomorrowCourseCount = intent.getIntExtra(EXTRA_LIVE_UPDATE_TOMORROW_COUNT, 0)
        )
    }

    fun storePayload(prefs: SharedPreferences, payload: LiveUpdatePayload) {
        prefs.edit {
            putString("kind", payload.kind.name)
                .putString("name", payload.name)
                .putString("time", payload.timeText)
                .putString("location", payload.location)
                .putBoolean("actions", payload.showActions)
                .putString("mute_key", payload.muteKey)
                .putString("mute_until", payload.muteUntil)
                .putString("chip_mode", payload.chipTextMode.name)
                .putString("segments", encodeSegments(payload.segments))
                .putBoolean("during_class", payload.duringClassEnabled)
                .putBoolean("break_status", payload.breakStatusEnabled)
                .putLong("expires_at", payload.expiresAtMillis)
                .putInt("tomorrow_count", payload.tomorrowCourseCount)
        }
    }

    fun restorePayload(prefs: SharedPreferences): LiveUpdatePayload? {
        val name = prefs.getString("name", null)?.takeIf { it.isNotBlank() } ?: return null
        val time = prefs.getString("time", null)?.takeIf { it.isNotBlank() } ?: return null
        return LiveUpdatePayload(
            kind = runCatching {
                LiveUpdateKind.valueOf(
                    prefs.getString("kind", LiveUpdateKind.COURSE.name)
                        ?: LiveUpdateKind.COURSE.name
                )
            }.getOrDefault(LiveUpdateKind.COURSE),
            name = name,
            timeText = time,
            location = prefs.getString("location", "").orEmpty(),
            showActions = prefs.getBoolean("actions", true),
            muteKey = prefs.getString("mute_key", "").orEmpty(),
            muteUntil = prefs.getString("mute_until", "").orEmpty(),
            chipTextMode = runCatching {
                LiveUpdateChipTextMode.valueOf(
                    prefs.getString("chip_mode", LiveUpdateChipTextMode.LOCATION.name)
                        ?: LiveUpdateChipTextMode.LOCATION.name
                )
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION),
            segments = decodeSegments(prefs.getString("segments", "").orEmpty()),
            duringClassEnabled = prefs.getBoolean("during_class", false),
            breakStatusEnabled = prefs.getBoolean("break_status", true),
            expiresAtMillis = prefs.getLong("expires_at", 0L),
            tomorrowCourseCount = prefs.getInt("tomorrow_count", 0)
        )
    }

    private fun encodeSegments(segments: List<LiveUpdateSegment>): String = segments.joinToString(";") {
        "${it.startAtMillis}:${it.endAtMillis}"
    }

    private fun decodeSegments(value: String): List<LiveUpdateSegment> = value.split(';').mapNotNull { encoded ->
        val start = encoded.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val end = encoded.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        LiveUpdateSegment(start, end).takeIf { end > start }
    }
}
