package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class DayAgentPresentation(val visual: DayAgentCardVisual, val accent: Color, val initialText: String)

@Composable
internal fun rememberDayAgentPresentation(
    facts: DayAgentFacts,
    collapsed: Boolean,
    cardIsDark: Boolean,
    weatherEnabled: Boolean,
    weatherPermissionGranted: Boolean,
    hasApiKey: Boolean,
    showApiKeyHint: Boolean,
    focusOverride: AgentCourseSlot? = null
): DayAgentPresentation {
    val now = facts.now
    val date = facts.date
    val weather = facts.weather
    val presentationClock = if (focusOverride == null && now.toLocalDate() != date)
        date.atTime(now.toLocalTime()) else now
    val lesson = remember(facts.today, facts.periodDefinitions, presentationClock, focusOverride) {
        agentLessonFocus(focusOverride?.let(::listOf) ?: facts.today, facts.periodDefinitions, presentationClock)
    }
    val currentSlot = lesson?.slot?.takeIf { lesson.phase != AgentLessonPhase.BEFORE }
    val nextSlot = lesson?.slot?.takeIf { lesson.phase == AgentLessonPhase.BEFORE }
    val previewTomorrow = now.toLocalDate() == date &&
        now.toLocalTime() >= LocalTime.of(22, 0) &&
        currentSlot == null &&
        nextSlot == null &&
        facts.tomorrow.isNotEmpty()
    val activityAccent = when {
        currentSlot != null -> if (cardIsDark) Color(0xFFFF7474) else Color(0xFFD92D2D)
        nextSlot != null || previewTomorrow -> if (cardIsDark) Color(0xFFFFB45C) else Color(0xFFD96A00)
        else -> if (cardIsDark) Color(0xFF62B5FF) else Color(0xFF006EDC)
    }
    val focusSlot = lesson?.slot ?: facts.tomorrow.firstOrNull().takeIf { previewTomorrow }
    val remainingSeconds = lesson?.secondsRemainingAt(presentationClock)
    val remainingMinutes = remainingSeconds?.let { (it + 59L) / 60L }
    val finalTenMinutes = remainingSeconds != null && remainingSeconds <= 600L &&
        (focusOverride != null || now.toLocalDate() == date)
    val activityLabel = when {
        lesson?.phase == AgentLessonPhase.IN_CLASS && finalTenMinutes ->
            if (lesson.finalLesson) "距下课" else "距课间"
        lesson?.phase == AgentLessonPhase.IN_CLASS -> "上课中"
        lesson?.phase == AgentLessonPhase.BREAK && finalTenMinutes -> "距上课"
        lesson?.phase == AgentLessonPhase.BREAK -> "课间中"
        lesson?.phase == AgentLessonPhase.BEFORE && finalTenMinutes -> "距上课"
        lesson?.phase == AgentLessonPhase.BEFORE -> "下节课"
        previewTomorrow -> "明日首课"
        facts.today.isEmpty() -> "今日无课"
        else -> "课程已结束"
    }
    val countdownText = when {
        finalTenMinutes -> agentCountdownText(remainingSeconds)
        lesson?.phase == AgentLessonPhase.IN_CLASS && remainingMinutes != null ->
            if (lesson.finalLesson) "${remainingMinutes} 分钟后下课" else "${remainingMinutes} 分钟后课间"
        lesson?.phase == AgentLessonPhase.BREAK && remainingMinutes != null -> "${remainingMinutes} 分钟后上课"
        nextSlot != null && remainingMinutes != null && (focusOverride != null || now.toLocalTime() >= LocalTime.of(6, 0)) ->
            "${remainingMinutes} 分钟后"
        previewTomorrow -> ""
        facts.today.isEmpty() -> "轻松一天"
        else -> "今日完成"
    }
    val locationText = focusSlot?.course?.let { course ->
        listOfNotNull(course.location?.takeIf(String::isNotBlank), course.teacher?.takeIf(String::isNotBlank)).joinToString(" | ")
    }.orEmpty()
    val focusTimeText = lesson?.segment?.let {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        "${it.start.format(formatter)} - ${it.end.format(formatter)}"
    } ?: focusSlot?.let {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        "${it.start.format(formatter)} - ${it.end.format(formatter)}"
    }.orEmpty()
    val weatherText = when {
        !weatherEnabled -> "天气未启用"
        weather != null -> {
            val icon = weatherEmoji(weather.summary)
            val condition = weather.summary.substringBefore('，')
            "$icon ${weather.temperature}°C $condition"
        }
        !weatherPermissionGranted -> "📍 点击开启天气"
        else -> "天气加载中"
    }
    val weatherAlertText = weather?.let(::weatherAlertText)
    val assistantHintText = if (weatherAlertText == null) {
        when {
            hasApiKey -> "点击卡片和助手对话"
            showApiKeyHint -> "绑定 API Key 后可以启用更多智慧功能"
            else -> null
        }
    } else null
    val cardVisual = DayAgentCardVisual(
        activityLabel = activityLabel,
        courseName = focusSlot?.course?.name,
        countdownText = countdownText,
        locationText = locationText,
        focusTimeText = focusTimeText,
        courseCountText = if (previewTomorrow) {
            "明天有 ${facts.tomorrow.size} 节课"
        } else {
            "今天有 ${facts.today.size} 节课"
        },
        weatherText = weatherText,
        trailingStatus = weatherAlertText?.let { "⚠️ $it" } ?: assistantHintText,
        weatherAlert = weatherAlertText != null,
        collapsed = collapsed,
        cardIsDark = cardIsDark,
        countdownSeconds = remainingSeconds?.takeIf { finalTenMinutes },
        transitionKey = lesson?.transitionKey
    )
    val conversationInitialText = remember(facts.today, facts.tomorrow, focusSlot, weather, previewTomorrow) {
        when {
            previewTomorrow && focusSlot != null ->
                "明天有 ${facts.tomorrow.size} 节课，最早一节是${focusSlot.course.name}${locationText.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}。"
            focusSlot != null -> "今天有 ${facts.today.size} 节课，${activityLabel}是${focusSlot.course.name}${locationText.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}。"
            facts.today.isEmpty() -> "今天没有课程。"
            else -> "今天的课程已经结束。"
        }
    }

    return DayAgentPresentation(cardVisual, activityAccent, conversationInitialText)
}
