package com.example.courseschedule

import android.content.Context
import java.time.LocalDate

data class AgentSettingDefinition(
    val key: String,
    val description: String,
    val acceptedValues: String,
    val page: String
)

object AgentSettingRegistry {
    val definitions = listOf(
        AgentSettingDefinition("SCHEDULE_NAME", "当前课表名称", "1到30个字符", "SCHEDULE_MANAGER"),
        AgentSettingDefinition("TOTAL_WEEKS", "学期总周数", "1..60整数", "SCHEDULE"),
        AgentSettingDefinition("CURRENT_WEEK", "当前周", "1..总周数整数", "SCHEDULE"),
        AgentSettingDefinition("TERM_START_DATE", "学期开始日期", "yyyy-MM-dd", "SCHEDULE"),
        AgentSettingDefinition("AUTO_CURRENT_WEEK", "按开学日期自动计算当前周", "true/false", "SCHEDULE"),
        AgentSettingDefinition("HIDE_EMPTY_WEEKENDS", "隐藏没有课程的周末", "true/false", "SCHEDULE"),
        AgentSettingDefinition("CLASS_DURATION_MINUTES", "单节课时长", "1..300整数分钟", "SCHEDULE"),
        AgentSettingDefinition("BREAK_DURATION_MINUTES", "默认课间时长", "0..300整数分钟", "SCHEDULE"),
        AgentSettingDefinition("NOTIFICATIONS_ENABLED", "课程提醒总开关", "true/false", "NOTIFICATIONS"),
        AgentSettingDefinition("NOTIFICATION_LEAD_MINUTES", "提前提醒分钟", "0..180整数", "NOTIFICATIONS"),
        AgentSettingDefinition("NOTIFICATION_MODE", "课程提醒样式", "STANDARD/LIVE_UPDATE", "NOTIFICATIONS"),
        AgentSettingDefinition("REALTIME_ACTIVITY", "实时活动通知样式", "true/false", "NOTIFICATIONS"),
        AgentSettingDefinition("LIVE_UPDATE_ACTIONS_ENABLED", "实时活动内操作按钮", "true/false", "NOTIFICATIONS"),
        AgentSettingDefinition("LIVE_UPDATE_CHIP_TEXT", "实时活动缩略文字", "LOCATION/COUNTDOWN/SHORT/NORMAL", "NOTIFICATIONS"),
        AgentSettingDefinition("FOLLOW_SYSTEM_DARK_MODE", "跟随系统深浅模式", "true/false", "GENERAL"),
        AgentSettingDefinition("DARK_MODE", "应用深色模式（关闭跟随后生效）", "true/false", "GENERAL"),
        AgentSettingDefinition("HIDE_FROM_RECENTS", "从最近任务隐藏", "true/false", "GENERAL"),
        AgentSettingDefinition("HOME_TEXT_STYLE", "首页文字明暗", "LIGHT/DARK", "PERSONALIZATION"),
        AgentSettingDefinition("DEFAULT_WALLPAPER_STYLE", "默认壁纸样式", "KANBAN/NONE", "PERSONALIZATION"),
        AgentSettingDefinition("DEFAULT_HOME_MODE", "首页默认日视图或周视图", "DAY/WEEK", "GENERAL"),
        AgentSettingDefinition("DOCK_ALIGNMENT", "底部 Dock 对齐", "LEFT/CENTER/RIGHT", "GENERAL"),
        AgentSettingDefinition("WALLPAPER_BLUR_PERCENT", "首页壁纸模糊", "0..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("WALLPAPER_BRIGHTNESS_PERCENT", "首页壁纸亮度", "35..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_ALPHA_PERCENT", "课程卡片着色强度", "0..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_BLUR_PERCENT", "课程卡片模糊", "0..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_GLASS_ENABLED", "课程卡片液态玻璃", "true/false", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_FONT_PERCENT", "课程卡片字体大小", "80..135百分比", "PERSONALIZATION"),
        AgentSettingDefinition("WEEK_CARD_HEIGHT_DP", "周视图行高", "44..180数值", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_COLOR", "课程卡片颜色", "MULTICOLOR或#AARRGGBB", "PERSONALIZATION"),
        AgentSettingDefinition("DAY_AGENT_ENABLED", "今日 Agent 总开关", "true/false", "DAY_AGENT"),
        AgentSettingDefinition("DAY_AGENT_DAILY_AI", "每日 AI 个性化文案", "true/false", "DAY_AGENT"),
        AgentSettingDefinition("DAY_AGENT_WEATHER", "今日 Agent 天气提醒", "true/false", "DAY_AGENT")
    )

    fun promptCatalog(): String = buildString {
        appendLine("应用设置注册表（SET_SETTING 只能使用下列键）：")
        definitions.forEach { appendLine("- ${it.key}: ${it.description}；值=${it.acceptedValues}；页面=${it.page}") }
        appendLine("用户使用‘更高/更低/更模糊/亮一点’等相对表达时，根据当前值计算一个幅度克制的绝对值。")
        appendLine("选择壁纸、壁纸裁切和课程卡片外观等个性化界面操作使用 OPEN_SETTINGS 到 PERSONALIZATION；API Key、系统权限等也只能打开对应页面。")
    }

    fun snapshot(
        config: ScheduleConfigEntity,
        scheduleName: String?,
        context: Context? = null
    ): Map<String, String> = linkedMapOf(
        "SCHEDULE_NAME" to scheduleName.orEmpty(),
        "TOTAL_WEEKS" to config.totalWeeks.toString(),
        "CURRENT_WEEK" to config.currentWeek.toString(),
        "TERM_START_DATE" to config.termStartDate.orEmpty(),
        "AUTO_CURRENT_WEEK" to config.autoCurrentWeek.toString(),
        "HIDE_EMPTY_WEEKENDS" to config.hideEmptyWeekends.toString(),
        "CLASS_DURATION_MINUTES" to config.classDurationMinutes.toString(),
        "BREAK_DURATION_MINUTES" to config.breakDurationMinutes.toString(),
        "NOTIFICATIONS_ENABLED" to config.notificationsEnabled.toString(),
        "NOTIFICATION_LEAD_MINUTES" to config.notificationLeadMinutes.toString(),
        "NOTIFICATION_MODE" to config.notificationMode.name,
        "REALTIME_ACTIVITY" to (config.notificationMode == NotificationMode.LIVE_UPDATE).toString(),
        "LIVE_UPDATE_ACTIONS_ENABLED" to config.liveUpdateActionsEnabled.toString(),
        "LIVE_UPDATE_CHIP_TEXT" to config.liveUpdateChipTextMode.name,
        "FOLLOW_SYSTEM_DARK_MODE" to config.followSystemDarkMode.toString(),
        "DARK_MODE" to config.darkMode.toString(),
        "HIDE_FROM_RECENTS" to config.hideFromRecents.toString(),
        "HOME_TEXT_STYLE" to if (config.homeTextLight) "LIGHT" else "DARK",
        "DEFAULT_WALLPAPER_STYLE" to config.defaultWallpaperStyle.name,
        "DEFAULT_HOME_MODE" to config.defaultHomeMode.name,
        "DOCK_ALIGNMENT" to config.dockAlignment.name,
        "WALLPAPER_BLUR_PERCENT" to wallpaperBlurPercent(config.wallpaperBlur).toInt().toString(),
        "WALLPAPER_BRIGHTNESS_PERCENT" to (config.wallpaperBrightness * 100f).toInt().toString(),
        "COURSE_CARD_ALPHA_PERCENT" to (config.cardAlpha * 100f).toInt().toString(),
        "COURSE_CARD_BLUR_PERCENT" to (config.courseCardBlur / 10f * 100f).toInt().toString(),
        "COURSE_CARD_GLASS_ENABLED" to config.courseCardGlassEnabled.toString(),
        "COURSE_CARD_FONT_PERCENT" to (config.courseCardFontScale * 100f).toInt().toString(),
        "WEEK_CARD_HEIGHT_DP" to (config.weekCardHeightDp?.toInt()?.toString() ?: "自动"),
        "COURSE_CARD_COLOR" to if (config.cardColorArgb == MulticolorCourseCardArgb) "MULTICOLOR" else "#%08X".format(config.cardColorArgb),
        "DAY_AGENT_ENABLED" to context?.let(DayAgentPreferences::isEnabled).toStringOrUnknown(),
        "DAY_AGENT_DAILY_AI" to context?.let(DayAgentPreferences::isDailyAiEnabled).toStringOrUnknown(),
        "DAY_AGENT_WEATHER" to context?.let(DayAgentPreferences::isWeatherEnabled).toStringOrUnknown()
    )

    fun normalize(keyValue: String?, rawValue: String?): Pair<String, String>? {
        val key = keyValue?.trim()?.uppercase() ?: return null
        if (definitions.none { it.key == key }) return null
        val raw = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = when (key) {
            "SCHEDULE_NAME" -> raw.take(30).takeIf { it.isNotBlank() }
            "TOTAL_WEEKS" -> raw.intIn(1, 60)
            "CURRENT_WEEK" -> raw.intIn(1, 60)
            "TERM_START_DATE" -> raw.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            "CLASS_DURATION_MINUTES" -> raw.intIn(1, 300)
            "BREAK_DURATION_MINUTES" -> raw.intIn(0, 300)
            "NOTIFICATION_LEAD_MINUTES" -> raw.intIn(0, 180)
            "WALLPAPER_BLUR_PERCENT", "COURSE_CARD_ALPHA_PERCENT", "COURSE_CARD_BLUR_PERCENT" -> raw.floatIn(0f, 100f)
            "WALLPAPER_BRIGHTNESS_PERCENT" -> raw.floatIn(35f, 100f)
            "COURSE_CARD_FONT_PERCENT" -> raw.floatIn(80f, 135f)
            "WEEK_CARD_HEIGHT_DP" -> raw.floatIn(44f, 180f)
            "COURSE_CARD_COLOR" -> normalizeColor(raw)
            "LIVE_UPDATE_CHIP_TEXT" -> raw.uppercase().takeIf { it in setOf("LOCATION", "COUNTDOWN", "SHORT", "NORMAL") }
            "NOTIFICATION_MODE" -> raw.uppercase().takeIf { it in setOf("STANDARD", "LIVE_UPDATE") }
            "HOME_TEXT_STYLE" -> raw.uppercase().takeIf { it in setOf("LIGHT", "DARK") }
            "DEFAULT_WALLPAPER_STYLE" -> raw.uppercase().takeIf { it in setOf("KANBAN", "NONE") }
            "DEFAULT_HOME_MODE" -> raw.uppercase().takeIf { it in setOf("DAY", "WEEK") }
            "DOCK_ALIGNMENT" -> raw.uppercase().takeIf { it in setOf("LEFT", "CENTER", "RIGHT") }
            else -> raw.uppercase().takeIf { it in setOf("TRUE", "FALSE") }
        } ?: return null
        return key to normalized
    }

    fun apply(config: ScheduleConfigEntity, key: String?, value: String?): ScheduleConfigEntity? = when (key) {
        "TOTAL_WEEKS" -> value?.toIntOrNull()?.let { total -> config.copy(totalWeeks = total, currentWeek = config.currentWeek.coerceAtMost(total)) }
        "CURRENT_WEEK" -> value?.toIntOrNull()?.takeIf { it <= config.totalWeeks }?.let { config.copy(currentWeek = it, autoCurrentWeek = false) }
        "TERM_START_DATE" -> value?.let { config.copy(termStartDate = it) }
        "AUTO_CURRENT_WEEK" -> value.agentBoolean()?.let { config.copy(autoCurrentWeek = it) }
        "HIDE_EMPTY_WEEKENDS" -> value.agentBoolean()?.let { config.copy(hideEmptyWeekends = it) }
        "CLASS_DURATION_MINUTES" -> value?.toIntOrNull()?.let { config.copy(classDurationMinutes = it) }
        "BREAK_DURATION_MINUTES" -> value?.toIntOrNull()?.let { config.copy(breakDurationMinutes = it) }
        "NOTIFICATIONS_ENABLED" -> value.agentBoolean()?.let { config.copy(notificationsEnabled = it) }
        "NOTIFICATION_LEAD_MINUTES" -> value?.toIntOrNull()?.let { config.copy(notificationLeadMinutes = it) }
        "NOTIFICATION_MODE" -> enumValueOrNull<NotificationMode>(value)?.let { config.copy(notificationMode = it) }
        "REALTIME_ACTIVITY" -> value.agentBoolean()?.let { enabled ->
            if (enabled) config.copy(notificationsEnabled = true, notificationMode = NotificationMode.LIVE_UPDATE)
            else config.copy(notificationMode = NotificationMode.STANDARD)
        }
        "LIVE_UPDATE_ACTIONS_ENABLED" -> value.agentBoolean()?.let { config.copy(liveUpdateActionsEnabled = it) }
        "LIVE_UPDATE_CHIP_TEXT" -> enumValueOrNull<LiveUpdateChipTextMode>(value)?.let { config.copy(liveUpdateChipTextMode = it) }
        "FOLLOW_SYSTEM_DARK_MODE" -> value.agentBoolean()?.let { config.copy(followSystemDarkMode = it) }
        "DARK_MODE" -> value.agentBoolean()?.let { config.copy(followSystemDarkMode = false, darkMode = it) }
        "HIDE_FROM_RECENTS" -> value.agentBoolean()?.let { config.copy(hideFromRecents = it) }
        "HOME_TEXT_STYLE" -> value?.let { config.copy(homeTextLight = it == "LIGHT") }
        "DEFAULT_WALLPAPER_STYLE" -> enumValueOrNull<DefaultWallpaperStyle>(value)?.let { config.copy(defaultWallpaperStyle = it) }
        "DEFAULT_HOME_MODE" -> enumValueOrNull<HomeStartMode>(value)?.let { config.copy(defaultHomeMode = it) }
        "DOCK_ALIGNMENT" -> enumValueOrNull<DockAlignment>(value)?.let { config.copy(dockAlignment = it) }
        "WALLPAPER_BLUR_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(wallpaperBlur = wallpaperBlurDp(it)) }
        "WALLPAPER_BRIGHTNESS_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(wallpaperBrightness = it / 100f) }
        "COURSE_CARD_ALPHA_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(cardAlpha = it / 100f) }
        "COURSE_CARD_BLUR_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(courseCardBlur = it / 100f * 10f) }
        "COURSE_CARD_GLASS_ENABLED" -> value.agentBoolean()?.let { config.copy(courseCardGlassEnabled = it) }
        "COURSE_CARD_FONT_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(courseCardFontScale = it / 100f) }
        "WEEK_CARD_HEIGHT_DP" -> value?.toFloatOrNull()?.let { config.copy(weekCardHeightDp = it) }
        "COURSE_CARD_COLOR" -> value?.let { parseColor(it) }?.let { config.copy(cardColorArgb = it) }
        else -> null
    }

    fun isPreferenceSetting(key: String?): Boolean = key in setOf(
        "DAY_AGENT_ENABLED",
        "DAY_AGENT_DAILY_AI",
        "DAY_AGENT_WEATHER"
    )

    fun applyPreference(context: Context, key: String?, value: String?): Boolean {
        val enabled = value.agentBoolean() ?: return false
        return when (key) {
            "DAY_AGENT_ENABLED" -> {
                DayAgentPreferences.setEnabled(context, enabled)
                true
            }
            "DAY_AGENT_DAILY_AI" -> {
                DayAgentPreferences.saveOptions(context, enabled, DayAgentPreferences.isWeatherEnabled(context))
                true
            }
            "DAY_AGENT_WEATHER" -> {
                DayAgentPreferences.saveOptions(context, DayAgentPreferences.isDailyAiEnabled(context), enabled)
                true
            }
            else -> false
        }
    }
}

private fun String.intIn(min: Int, max: Int): String? = toIntOrNull()?.takeIf { it in min..max }?.toString()
private fun String.floatIn(min: Float, max: Float): String? = toFloatOrNull()?.takeIf { it in min..max }?.toString()
private fun String?.agentBoolean(): Boolean? = when (this?.uppercase()) { "TRUE" -> true; "FALSE" -> false; else -> null }
private fun Boolean?.toStringOrUnknown(): String = this?.toString() ?: "UNKNOWN"
private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? = runCatching { enumValueOf<T>(value.orEmpty()) }.getOrNull()
private fun normalizeColor(value: String): String? = when {
    value.equals("MULTICOLOR", true) -> "MULTICOLOR"
    Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(value) -> value.uppercase()
    else -> null
}
private fun parseColor(value: String): Long? = when {
    value == "MULTICOLOR" -> MulticolorCourseCardArgb
    value.length == 7 -> ("FF" + value.drop(1)).toLongOrNull(16)
    value.length == 9 -> value.drop(1).toLongOrNull(16)
    else -> null
}
