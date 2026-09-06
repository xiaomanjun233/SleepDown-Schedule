package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.*

import android.app.Notification
import android.content.Context
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

internal enum class LiveUpdateKind { COURSE, TOMORROW }

internal data class LiveUpdateSegment(
    val startAtMillis: Long,
    val endAtMillis: Long
)

internal enum class LiveUpdatePhase { BEFORE_CLASS, IN_CLASS, BREAK, FINISHED, TOMORROW }

internal data class LiveUpdateStatus(
    val phase: LiveUpdatePhase,
    val statusText: String,
    val detailText: String,
    val minutesToTransition: Int,
    val nextTransitionAtMillis: Long?
)

internal data class LiveUpdatePayload(
    val kind: LiveUpdateKind = LiveUpdateKind.COURSE,
    val name: String,
    val timeText: String,
    val location: String,
    val showActions: Boolean,
    val muteKey: String,
    val muteUntil: String,
    val chipTextMode: LiveUpdateChipTextMode,
    val segments: List<LiveUpdateSegment> = emptyList(),
    val duringClassEnabled: Boolean = false,
    val breakStatusEnabled: Boolean = true,
    val expiresAtMillis: Long = 0L,
    val tomorrowCourseCount: Int = 0
) {
    companion object {
        const val PREFS = "live_update_service_state"
    }

    fun startAtMillis(): Long? = segments.firstOrNull()?.startAtMillis

    fun endAtMillis(): Long? = segments.lastOrNull()?.endAtMillis

    fun startTime(): LocalTime? = startAtMillis()?.let(::localTimeAt)
        ?: runCatching { LocalTime.parse(timeText.substringBefore("-").trim()) }.getOrNull()

    fun isPreview(): Boolean = muteKey.startsWith("preview:")

    fun shouldStop(nowMillis: Long = System.currentTimeMillis()): Boolean = when {
        expiresAtMillis > 0L && nowMillis >= expiresAtMillis -> true
        kind == LiveUpdateKind.TOMORROW -> false
        isPreview() -> startAtMillis()?.let { nowMillis >= it } ?: false
        duringClassEnabled -> endAtMillis()?.let { nowMillis >= it } ?: false
        else -> startAtMillis()?.let { nowMillis >= it } ?: false
    }

    fun statusAt(nowMillis: Long = System.currentTimeMillis()): LiveUpdateStatus {
        if (kind == LiveUpdateKind.TOMORROW) {
            return LiveUpdateStatus(
                phase = LiveUpdatePhase.TOMORROW,
                statusText = "明日课程提醒",
                detailText = listOf("记得检查并设置闹钟", timeText, location)
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                minutesToTransition = 0,
                nextTransitionAtMillis = null
            )
        }
        val timeline = segments.filter { it.endAtMillis > it.startAtMillis }.sortedBy { it.startAtMillis }
        val first = timeline.firstOrNull()
        val last = timeline.lastOrNull()
        if (first == null || last == null) {
            return LiveUpdateStatus(
                phase = LiveUpdatePhase.BEFORE_CLASS,
                statusText = "准备上课",
                detailText = timeText,
                minutesToTransition = 0,
                nextTransitionAtMillis = null
            )
        }
        if (nowMillis < first.startAtMillis) {
            val minutes = minutesUntil(nowMillis, first.startAtMillis)
            return LiveUpdateStatus(
                phase = LiveUpdatePhase.BEFORE_CLASS,
                statusText = if (minutes <= 0) "准备上课" else "还有${minutes}分钟上课",
                detailText = timeText,
                minutesToTransition = minutes,
                nextTransitionAtMillis = first.startAtMillis
            )
        }
        timeline.forEachIndexed { index, segment ->
            if (nowMillis < segment.endAtMillis) {
                val nextSegment = timeline.getOrNull(index + 1)
                val transition = if (breakStatusEnabled && nextSegment != null) {
                    segment.endAtMillis
                } else {
                    last.endAtMillis
                }
                val minutes = minutesUntil(nowMillis, transition)
                val target = if (breakStatusEnabled && nextSegment != null) {
                    "${formatTime(segment.endAtMillis)}课间"
                } else {
                    "${formatTime(last.endAtMillis)}下课"
                }
                return LiveUpdateStatus(
                    phase = LiveUpdatePhase.IN_CLASS,
                    statusText = "上课中",
                    detailText = "$target · 还有${minutes}分钟",
                    minutesToTransition = minutes,
                    nextTransitionAtMillis = transition
                )
            }
            val nextSegment = timeline.getOrNull(index + 1)
            if (nextSegment != null && nowMillis < nextSegment.startAtMillis) {
                if (!breakStatusEnabled) {
                    val minutes = minutesUntil(nowMillis, last.endAtMillis)
                    return LiveUpdateStatus(
                        phase = LiveUpdatePhase.IN_CLASS,
                        statusText = "课程进行中",
                        detailText = "${formatTime(last.endAtMillis)}下课 · 还有${minutes}分钟",
                        minutesToTransition = minutes,
                        nextTransitionAtMillis = last.endAtMillis
                    )
                }
                val minutes = minutesUntil(nowMillis, nextSegment.startAtMillis)
                return LiveUpdateStatus(
                    phase = LiveUpdatePhase.BREAK,
                    statusText = "课间中",
                    detailText = "${formatTime(nextSegment.startAtMillis)}上课 · 还有${minutes}分钟",
                    minutesToTransition = minutes,
                    nextTransitionAtMillis = nextSegment.startAtMillis
                )
            }
        }
        return LiveUpdateStatus(
            phase = LiveUpdatePhase.FINISHED,
            statusText = "已下课",
            detailText = "本次课程已经结束",
            minutesToTransition = 0,
            nextTransitionAtMillis = null
        )
    }

    fun buildNotification(context: Context): Notification =
        NotificationScheduler.liveUpdateNotification(context, this)
}

private fun minutesUntil(nowMillis: Long, targetMillis: Long): Int =
    ceil((targetMillis - nowMillis).coerceAtLeast(0L) / 60_000.0).toInt()

private fun formatTime(epochMillis: Long): String =
    localTimeAt(epochMillis).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun localTimeAt(epochMillis: Long): LocalTime =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
