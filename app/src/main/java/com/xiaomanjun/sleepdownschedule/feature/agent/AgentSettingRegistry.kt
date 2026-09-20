package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.glass.ui.*

import com.xiaomanjun.sleepdownschedule.core.wallpaper.*

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.weekCardHeightScaleFromSlider
import com.xiaomanjun.sleepdownschedule.feature.home.weekCardHeightSliderFromScale

import android.content.Context
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconMode
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconStyle
import java.time.LocalDate
import java.time.LocalTime

data class AgentSettingDefinition(
    val key: String,
    val description: String,
    val acceptedValues: String,
    val page: String
)

object AgentSettingRegistry {
    private val periodTimeKey = Regex("PERIOD_(\\d+)_TIME")
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
        AgentSettingDefinition("LIVE_UPDATE_CHIP_TEXT", "实时活动缩略文字", "LOCATION/COUNTDOWN/NORMAL", "NOTIFICATIONS"),
        AgentSettingDefinition("FOLLOW_SYSTEM_DARK_MODE", "跟随系统深浅模式", "true/false", "GENERAL"),
        AgentSettingDefinition("DARK_MODE", "应用深色模式（关闭跟随后生效）", "true/false", "GENERAL"),
        AgentSettingDefinition("HIDE_FROM_RECENTS", "从最近任务隐藏", "true/false", "GENERAL"),
        AgentSettingDefinition("AUTO_CHECK_UPDATES", "自动检查应用更新", "true/false", "GENERAL"),
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
        AgentSettingDefinition("WEEK_CARD_HEIGHT_PERCENT", "周视图行高（50为当前设备自适应）", "0..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("WEEK_CARD_CORNER_PERCENT", "周视图课程卡片圆角", "0..100百分比", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_COLOR_MODE", "课程卡片配色模式", "SOLID/GRADIENT/COLORFUL", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_COLOR", "纯色或渐变模式的基准色", "MULTICOLOR或#AARRGGBB", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_PALETTE", "彩色模式的算法种子调色板", "AUTO_WALLPAPER或1到4个#AARRGGBB，用逗号分隔", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_OUTLINE_LIGHT", "课程卡片浅色描边", "true/false", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_REFRACTION", "课程卡片折射强度", "0..1小数", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_GAUSSIAN_BLUR", "课程卡片高斯模糊", "true/false", "PERSONALIZATION"),
        AgentSettingDefinition("COURSE_CARD_COLORED_TEXT", "课程卡片彩色文字", "true/false", "PERSONALIZATION"),
        AgentSettingDefinition("HOME_CHROME_BLUR_SCALE", "首页玻璃模糊倍数", "0..2小数", "PERSONALIZATION"),
        AgentSettingDefinition("HOME_CHROME_SAMPLING_SCALE", "首页玻璃采样倍数", "0..2小数", "PERSONALIZATION"),
        AgentSettingDefinition("APP_ICON_STYLE", "应用图标风格", "MINIMAL/KANBAN", "GENERAL"),
        AgentSettingDefinition("APP_ICON_MODE", "应用图标深浅", "LIGHT/DARK/FOLLOW_DARK_MODE", "GENERAL"),
        AgentSettingDefinition("DAY_AGENT_ENABLED", "AI助理总开关", "true/false", "DAY_AGENT"),
        AgentSettingDefinition("DAY_AGENT_WEEK_ENABLED", "周视图AI助理", "true/false", "DAY_AGENT"),
        AgentSettingDefinition("DAY_AGENT_WEATHER", "AI助理天气提醒", "true/false", "DAY_AGENT"),
        AgentSettingDefinition("DAY_AGENT_MEMORY_ENABLED", "AI助理记忆", "true/false", "DAY_AGENT")
    )

    /**
     * Keys that write the same underlying state. Every alias is still accepted so existing prompts
     * and models keep working, but the catalog names the canonical key and the execution layer
     * rejects a plan that sets two keys of one group to conflicting values. Without this, the
     * stored result depended on the order of the JSON array.
     */
    val canonicalGroups: List<Set<String>> = listOf(
        setOf("NOTIFICATION_MODE", "REALTIME_ACTIVITY"),
        setOf("COURSE_CARD_COLOR", "COURSE_CARD_COLOR_MODE", "COURSE_CARD_PALETTE")
    )

    private val aliasKeyOf: Map<String, String> = canonicalGroups
        .flatMap { group -> group.drop(1).map { it to group.first() } }
        .toMap()

    /** Returns the first overlapping group written by more than one key of [keys]. */
    fun conflictingGroup(keys: List<String>): Set<String>? =
        keys.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { setOf(it.key) }
            ?: canonicalGroups.firstOrNull { group -> keys.count { it in group } > 1 }

    fun promptCatalog(
        currentValues: Map<String, String> = emptyMap()
    ): String = buildString {
        appendLine("读取成功：以下是当前课表与应用的完整可访问设置快照。")
        appendLine("普通字段可用 SET_SETTING 提交；节次拓扑与作息方案用 SET_PERIOD_SETTINGS 提交。")
        appendLine("列：键|说明|可选值|当前|页面。UNKNOWN 表示未读取。")
        definitions.forEach { definition ->
            val alias = aliasKeyOf[definition.key]
                ?.let { canonical -> "；等价别名=$canonical（同一计划内只用一个）" }
                .orEmpty()
            appendLine(
                    "${definition.key}|${definition.description}|${definition.acceptedValues}|" +
                    "${currentValues[definition.key] ?: "UNKNOWN"}|${definition.page}$alias"
            )
        }
        val structuredKeys = listOf(
            "MORNING_PERIOD_COUNT" to "上午节数",
            "NOON_PERIOD_COUNT" to "中午节数",
            "AFTERNOON_PERIOD_COUNT" to "下午节数",
            "EVENING_PERIOD_COUNT" to "晚上节数",
            "WALLPAPER_PRESENT" to "是否使用自定义壁纸",
            "WALLPAPER_PORTRAIT_CENTER_X" to "竖屏壁纸中心X",
            "WALLPAPER_PORTRAIT_CENTER_Y" to "竖屏壁纸中心Y",
            "WALLPAPER_PORTRAIT_SCALE" to "竖屏壁纸缩放",
            "WALLPAPER_LANDSCAPE_CENTER_X" to "横屏壁纸中心X",
            "WALLPAPER_LANDSCAPE_CENTER_Y" to "横屏壁纸中心Y",
            "WALLPAPER_LANDSCAPE_SCALE" to "横屏壁纸缩放",
            "WALLPAPER_SOURCE_SIZE" to "壁纸原始尺寸",
            "TERM_STATE" to "学期状态",
            "TERM_STATUS" to "学期状态提示"
        )
        appendLine("结构化/只读个性化事实：")
        structuredKeys.forEach { (key, label) ->
            appendLine("- $key: $label；当前=${currentValues[key] ?: "UNKNOWN"}")
        }
        // 逐节精确时间只由 GET_PERIODS 提供；这里重复输出会让模型在一条计划里同时依赖两份
        // 可能不同步的节次事实，因此只保留一条指向性说明。
        appendLine("逐节精确时间请读取 GET_PERIODS（SET_SETTING 的 PERIOD_n_TIME 键需以其为准）。")
        appendLine("用户使用‘更高/更低/更模糊/亮一点’等相对表达时，根据当前值计算一个幅度克制的绝对值。")
        appendLine("彩色调色板只是稳定课程配色算法的种子，不能承诺四种颜色按课程顺序轮转或直接绑定某门课程。")
        appendLine("查询个性化设置时直接使用以上真实值回答。只有选择新的壁纸文件、重新裁切图片、API Key 或系统权限等无法由 JSON 表达的交互，才使用 OPEN_SETTINGS。")
    }

    fun snapshot(
        config: ScheduleConfigEntity,
        scheduleName: String?,
        context: Context? = null,
        date: LocalDate = LocalDate.now()
    ): Map<String, String> = linkedMapOf(
        "SCHEDULE_NAME" to scheduleName.orEmpty(),
        "TOTAL_WEEKS" to config.totalWeeks.toString(),
        "CURRENT_WEEK" to config.currentWeek.toString(),
        "TERM_START_DATE" to config.termStartDate.orEmpty(),
        "AUTO_CURRENT_WEEK" to config.autoCurrentWeek.toString(),
        "TERM_STATE" to derivedScheduleTermState(config, date).name,
        "TERM_STATUS" to scheduleTermStatusDescription(config, date),
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
        "AUTO_CHECK_UPDATES" to config.autoCheckUpdates.toString(),
        "HOME_TEXT_STYLE" to if (config.homeTextLight) "LIGHT" else "DARK",
        "DEFAULT_WALLPAPER_STYLE" to config.defaultWallpaperStyle.name,
        "DEFAULT_HOME_MODE" to config.defaultHomeMode.name,
        "DOCK_ALIGNMENT" to config.dockAlignment.name,
        "WALLPAPER_BLUR_PERCENT" to wallpaperBlurPercent(config.wallpaperBlur).toInt().toString(),
        "WALLPAPER_BRIGHTNESS_PERCENT" to (config.wallpaperBrightness * 100f).toInt().toString(),
        "COURSE_CARD_ALPHA_PERCENT" to (config.cardAlpha * 100f).toInt().toString(),
        "COURSE_CARD_BLUR_PERCENT" to (
            config.courseCardBlur / courseCardBlurMaximum(config.courseCardGlassEnabled) * 100f
            ).toInt().coerceIn(0, 100).toString(),
        "COURSE_CARD_GLASS_ENABLED" to config.courseCardGlassEnabled.toString(),
        "COURSE_CARD_FONT_PERCENT" to (config.courseCardFontScale * 100f).toInt().toString(),
        "WEEK_CARD_HEIGHT_PERCENT" to agentWeekHeightPercent(config.weekCardHeightScale).toInt().toString(),
        "WEEK_CARD_CORNER_PERCENT" to (config.weekCardCornerProgress.coerceIn(0f, 1f) * 100f).toInt().toString(),
        "COURSE_CARD_COLOR_MODE" to config.courseCardColorMode.name,
        "COURSE_CARD_COLOR" to if (config.courseCardColorMode == CourseCardColorMode.COLORFUL) "MULTICOLOR" else "#%08X".format(config.cardColorArgb),
        "COURSE_CARD_OUTLINE_LIGHT" to config.courseCardOutlineLightEnabled.toString(),
        "COURSE_CARD_REFRACTION" to config.courseCardRefractionStrength.toString(),
        "COURSE_CARD_GAUSSIAN_BLUR" to config.courseCardGaussianBlurEnabled.toString(),
        "COURSE_CARD_COLORED_TEXT" to config.courseCardColoredTextEnabled.toString(),
        "HOME_CHROME_BLUR_SCALE" to config.homeChromeBlurScale.toString(),
        "HOME_CHROME_SAMPLING_SCALE" to config.homeChromeSamplingScale.toString(),
        "APP_ICON_STYLE" to (context?.let { AppIconManager.currentStyle(it).name } ?: "UNKNOWN"),
        "APP_ICON_MODE" to (context?.let { AppIconManager.currentMode(it).name } ?: "UNKNOWN"),
        "COURSE_CARD_PALETTE" to decodeCourseCardPalette(config.courseCardPalette)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",") { "#%08X".format(it) }
            .orEmpty()
            .ifBlank { "AUTO_WALLPAPER" },
        "MORNING_PERIOD_COUNT" to config.morningPeriodCount.toString(),
        "NOON_PERIOD_COUNT" to config.noonPeriodCount.toString(),
        "AFTERNOON_PERIOD_COUNT" to config.afternoonPeriodCount.toString(),
        "EVENING_PERIOD_COUNT" to config.eveningPeriodCount.toString(),
        "WALLPAPER_PRESENT" to (!config.wallpaperUri.isNullOrBlank()).toString(),
        "WALLPAPER_PORTRAIT_CENTER_X" to (config.wallpaperPortraitCenterX ?: 0.5f).toString(),
        "WALLPAPER_PORTRAIT_CENTER_Y" to (config.wallpaperPortraitCenterY ?: 0.5f).toString(),
        "WALLPAPER_PORTRAIT_SCALE" to (config.wallpaperPortraitScale ?: 1f).toString(),
        "WALLPAPER_LANDSCAPE_CENTER_X" to (config.wallpaperLandscapeCenterX ?: 0.5f).toString(),
        "WALLPAPER_LANDSCAPE_CENTER_Y" to (config.wallpaperLandscapeCenterY ?: 0.5f).toString(),
        "WALLPAPER_LANDSCAPE_SCALE" to (config.wallpaperLandscapeScale ?: 1f).toString(),
        "WALLPAPER_SOURCE_SIZE" to "${config.wallpaperSourceWidth ?: 0}x${config.wallpaperSourceHeight ?: 0}",
        "DAY_AGENT_ENABLED" to context?.let(DayAgentPreferences::isEnabled).toStringOrUnknown(),
        "DAY_AGENT_WEEK_ENABLED" to context?.let(DayAgentPreferences::isWeekAssistantEnabled).toStringOrUnknown(),
        "DAY_AGENT_WEATHER" to context?.let(DayAgentPreferences::isWeatherEnabled).toStringOrUnknown(),
        "DAY_AGENT_MEMORY_ENABLED" to context?.let(DayAgentPreferences::isMemoryEnabled).toStringOrUnknown()
    )

    fun normalize(keyValue: String?, rawValue: String?): Pair<String, String>? {
        val key = keyValue?.trim()?.uppercase() ?: return null
        if (definitions.none { it.key == key } && !periodTimeKey.matches(key)) return null
        val raw = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = when (key) {
            "SCHEDULE_NAME" -> raw.take(30).takeIf { it.isNotBlank() }
            else -> if (periodTimeKey.matches(key)) normalizePeriodRange(raw) else when (key) {
            "TOTAL_WEEKS" -> raw.intIn(1, 60)
            "CURRENT_WEEK" -> raw.intIn(1, 60)
            "TERM_START_DATE" -> raw.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            "CLASS_DURATION_MINUTES" -> raw.intIn(1, 300)
            "BREAK_DURATION_MINUTES" -> raw.intIn(0, 300)
            "NOTIFICATION_LEAD_MINUTES" -> raw.intIn(0, 180)
            "WALLPAPER_BLUR_PERCENT", "COURSE_CARD_ALPHA_PERCENT", "COURSE_CARD_BLUR_PERCENT" -> raw.floatIn(0f, 100f)
            "WALLPAPER_BRIGHTNESS_PERCENT" -> raw.floatIn(35f, 100f)
             "COURSE_CARD_FONT_PERCENT" -> raw.floatIn(80f, 135f)
             "WEEK_CARD_HEIGHT_PERCENT", "WEEK_CARD_CORNER_PERCENT" -> raw.floatIn(0f, 100f)
            "COURSE_CARD_REFRACTION", "HOME_CHROME_BLUR_SCALE", "HOME_CHROME_SAMPLING_SCALE" ->
                raw.floatIn(0f, 2f)
            "APP_ICON_STYLE" -> raw.uppercase().takeIf { it in setOf("MINIMAL", "KANBAN") }
            "APP_ICON_MODE" -> raw.uppercase().takeIf {
                it in setOf("LIGHT", "DARK", "FOLLOW_DARK_MODE")
            }
            "COURSE_CARD_COLOR" -> normalizeColor(raw)
            "COURSE_CARD_PALETTE" -> normalizeCoursePalette(raw)
            "COURSE_CARD_COLOR_MODE" -> raw.uppercase().takeIf {
                it in setOf("SOLID", "GRADIENT", "COLORFUL")
            }
            "LIVE_UPDATE_CHIP_TEXT" -> when (raw.uppercase()) {
                "SHORT" -> "NORMAL"
                "LOCATION", "COUNTDOWN", "NORMAL" -> raw.uppercase()
                else -> null
            }
            "NOTIFICATION_MODE" -> raw.uppercase().takeIf { it in setOf("STANDARD", "LIVE_UPDATE") }
            "HOME_TEXT_STYLE" -> raw.uppercase().takeIf { it in setOf("LIGHT", "DARK") }
            "DEFAULT_WALLPAPER_STYLE" -> raw.uppercase().takeIf { it in setOf("KANBAN", "NONE") }
            "DEFAULT_HOME_MODE" -> raw.uppercase().takeIf { it in setOf("DAY", "WEEK") }
            "DOCK_ALIGNMENT" -> raw.uppercase().takeIf { it in setOf("LEFT", "CENTER", "RIGHT") }
            else -> raw.uppercase().takeIf { it in setOf("TRUE", "FALSE") }
            }
        } ?: return null
        return key to normalized
    }

    fun isPeriodTimeSetting(key: String?): Boolean = key?.let(periodTimeKey::matches) == true

    fun applyPeriodTime(periods: List<PeriodEntity>, key: String?, value: String?): List<PeriodEntity>? {
        return applyPeriodTimes(periods, listOf(key to value))
    }

    /**
     * Applies a complete group of period edits before validating the resulting timeline. Validating
     * one item at a time rejects perfectly valid plans whose intermediate state temporarily overlaps.
     */
    fun applyPeriodTimes(
        periods: List<PeriodEntity>,
        changes: List<Pair<String?, String?>>
    ): List<PeriodEntity>? {
        val replacements = linkedMapOf<Int, Pair<String, String>>()
        changes.forEach { (key, value) ->
            val index = key?.let(periodTimeKey::matchEntire)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val normalized = value?.let(::normalizePeriodRange) ?: return null
            val parts = normalized.split('-', limit = 2)
            replacements[index] = parts[0] to parts[1]
        }
        if (replacements.keys.any { index -> periods.none { it.periodIndex == index } }) return null
        val updated = periods.map { period ->
            replacements[period.periodIndex]?.let { (start, end) ->
                period.copy(startTime = start, endTime = end)
            } ?: period
        }.sortedBy { it.periodIndex }
        val valid = updated.all { period ->
            LocalTime.parse(period.startTime) < LocalTime.parse(period.endTime)
        } && updated.zipWithNext().all { (left, right) ->
            LocalTime.parse(left.endTime) <= LocalTime.parse(right.startTime)
        }
        return updated.takeIf { valid }
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
        "AUTO_CHECK_UPDATES" -> value.agentBoolean()?.let { config.copy(autoCheckUpdates = it) }
        "HOME_TEXT_STYLE" -> value?.let { config.copy(homeTextLight = it == "LIGHT") }
        "DEFAULT_WALLPAPER_STYLE" -> enumValueOrNull<DefaultWallpaperStyle>(value)?.let { config.copy(defaultWallpaperStyle = it) }
        "DEFAULT_HOME_MODE" -> enumValueOrNull<HomeStartMode>(value)?.let { config.copy(defaultHomeMode = it) }
        "DOCK_ALIGNMENT" -> enumValueOrNull<DockAlignment>(value)?.let { config.copy(dockAlignment = it) }
        "WALLPAPER_BLUR_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(wallpaperBlur = wallpaperBlurDp(it)) }
        "WALLPAPER_BRIGHTNESS_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(wallpaperBrightness = it / 100f) }
        "COURSE_CARD_ALPHA_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(cardAlpha = it / 100f) }
        "COURSE_CARD_BLUR_PERCENT" -> value?.toFloatOrNull()?.let {
            config.copy(courseCardBlur = it / 100f * courseCardBlurMaximum(config.courseCardGlassEnabled))
        }
        "COURSE_CARD_GLASS_ENABLED" -> value.agentBoolean()?.let { config.copy(courseCardGlassEnabled = it) }
        "COURSE_CARD_FONT_PERCENT" -> value?.toFloatOrNull()?.let { config.copy(courseCardFontScale = it / 100f) }
        "WEEK_CARD_HEIGHT_PERCENT" -> value?.toFloatOrNull()?.let {
            config.copy(
                weekCardHeightDp = null,
                weekCardHeightScale = agentWeekHeightScale(it)
            )
        }
        "WEEK_CARD_CORNER_PERCENT" -> value?.toFloatOrNull()?.let {
            config.copy(weekCardCornerProgress = (it / 100f).coerceIn(0f, 1f))
        }
        "COURSE_CARD_COLOR_MODE" -> enumValueOrNull<CourseCardColorMode>(value)?.let {
            config.copy(courseCardColorMode = it)
        }
        "COURSE_CARD_OUTLINE_LIGHT" -> value.agentBoolean()?.let {
            config.copy(courseCardOutlineLightEnabled = it)
        }
        "COURSE_CARD_REFRACTION" -> value?.toFloatOrNull()?.let {
            config.copy(courseCardRefractionStrength = it)
        }
        "COURSE_CARD_GAUSSIAN_BLUR" -> value.agentBoolean()?.let {
            config.copy(courseCardGaussianBlurEnabled = it)
        }
        "COURSE_CARD_COLORED_TEXT" -> value.agentBoolean()?.let {
            config.copy(courseCardColoredTextEnabled = it)
        }
        "HOME_CHROME_BLUR_SCALE" -> value?.toFloatOrNull()?.let {
            config.copy(homeChromeBlurScale = it)
        }
        "HOME_CHROME_SAMPLING_SCALE" -> value?.toFloatOrNull()?.let {
            config.copy(homeChromeSamplingScale = it)
        }
        "COURSE_CARD_COLOR" -> value?.let { parseColor(it) }?.let { color ->
            if (color == MulticolorCourseCardArgb) {
                config.copy(courseCardColorMode = CourseCardColorMode.COLORFUL)
            } else {
                config.copy(
                    cardColorArgb = color,
                    courseCardColorMode = if (config.courseCardColorMode == CourseCardColorMode.GRADIENT) {
                        CourseCardColorMode.GRADIENT
                    } else {
                        CourseCardColorMode.SOLID
                    }
                )
            }
        }
        "COURSE_CARD_PALETTE" -> when (value) {
            "AUTO_WALLPAPER" -> config.copy(
                courseCardColorMode = CourseCardColorMode.COLORFUL,
                courseCardPalette = ""
            )
            else -> value?.let(::parseCoursePalette)?.takeIf { it.isNotEmpty() }?.let { palette ->
                config.copy(
                    cardColorArgb = palette.first(),
                    courseCardColorMode = CourseCardColorMode.COLORFUL,
                    courseCardPalette = encodeCourseCardPalette(palette)
                )
            }
        }
        else -> null
    }

    fun isPreferenceSetting(key: String?): Boolean = key in setOf(
        "DAY_AGENT_ENABLED",
        "DAY_AGENT_WEEK_ENABLED",
        "DAY_AGENT_WEATHER",
        "DAY_AGENT_MEMORY_ENABLED"
    )

    /**
     * Icon style/mode live in their own SharedPreferences and must not be written through
     * [apply] or [applyPreference]: the latter verifies with a lowercase comparison that would
     * reject the uppercase enum names these keys use.
     */
    fun isAppIconSetting(key: String?): Boolean = key in setOf("APP_ICON_STYLE", "APP_ICON_MODE")

    fun applyAppIcon(context: Context, key: String?, value: String?): Boolean = when (key) {
        "APP_ICON_STYLE" -> runCatching { AppIconStyle.valueOf(value.orEmpty()) }
            .getOrNull()
            ?.let { AppIconManager.setStyle(context, it); true }
            ?: false
        "APP_ICON_MODE" -> runCatching { AppIconMode.valueOf(value.orEmpty()) }
            .getOrNull()
            ?.let { AppIconManager.setMode(context, it); true }
            ?: false
        else -> false
    }

    fun currentAppIconValue(context: Context, key: String?): String? = when (key) {
        "APP_ICON_STYLE" -> AppIconManager.currentStyle(context).name
        "APP_ICON_MODE" -> AppIconManager.currentMode(context).name
        else -> null
    }

    fun applyPreference(context: Context, key: String?, value: String?): Boolean {
        val enabled = value.agentBoolean() ?: return false
        return when (key) {
            "DAY_AGENT_ENABLED" -> {
                DayAgentPreferences.setEnabled(context, enabled)
                true
            }
            "DAY_AGENT_WEEK_ENABLED" -> {
                DayAgentPreferences.setWeekAssistantEnabled(context, enabled)
                true
            }
            "DAY_AGENT_WEATHER" -> {
                DayAgentPreferences.saveOptions(context, DayAgentPreferences.isDailyAiEnabled(context), enabled)
                true
            }
            "DAY_AGENT_MEMORY_ENABLED" -> {
                DayAgentPreferences.setMemoryEnabled(context, enabled)
                true
            }
            else -> false
        }
    }
}

private fun normalizePeriodRange(value: String): String? {
    val match = Regex("(\\d{1,2}:\\d{2})\\s*[-~至]\\s*(\\d{1,2}:\\d{2})").matchEntire(value.trim()) ?: return null
    val start = runCatching { LocalTime.parse(match.groupValues[1].padStart(5, '0')) }.getOrNull() ?: return null
    val end = runCatching { LocalTime.parse(match.groupValues[2].padStart(5, '0')) }.getOrNull() ?: return null
    if (!start.isBefore(end)) return null
    return "%02d:%02d-%02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
}

private fun String.intIn(min: Int, max: Int): String? = toIntOrNull()?.takeIf { it in min..max }?.toString()
private fun String.floatIn(min: Float, max: Float): String? = toFloatOrNull()?.takeIf { it in min..max }?.toString()
private fun agentWeekHeightScale(percent: Float): Float {
    val progress = (percent / 100f).coerceIn(0f, 1f)
    return weekCardHeightScaleFromSlider(progress)
}
private fun agentWeekHeightPercent(scale: Float): Float {
    return weekCardHeightSliderFromScale(scale) * 100f
}
private fun String?.agentBoolean(): Boolean? = when (this?.uppercase()) { "TRUE" -> true; "FALSE" -> false; else -> null }
private fun Boolean?.toStringOrUnknown(): String = this?.toString() ?: "UNKNOWN"
private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? = runCatching { enumValueOf<T>(value.orEmpty()) }.getOrNull()
private fun normalizeColor(value: String): String? = when {
    value.equals("MULTICOLOR", true) -> "MULTICOLOR"
    Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(value) -> value.uppercase()
    else -> null
}
private fun normalizeCoursePalette(value: String): String? {
    if (value.equals("AUTO_WALLPAPER", ignoreCase = true)) return "AUTO_WALLPAPER"
    val colors = value.split(',', '，', ';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .takeIf { it.size in 1..4 }
        ?: return null
    val parsed = colors.map { normalizeColor(it)?.takeUnless { normalized -> normalized == "MULTICOLOR" } ?: return null }
    return parsed.joinToString(",")
}
private fun parseCoursePalette(value: String): List<Long>? = value
    .split(',')
    .map { parseColor(it.trim()) ?: return null }
    .takeIf { it.size in 1..4 }
private fun parseColor(value: String): Long? = when {
    value == "MULTICOLOR" -> MulticolorCourseCardArgb
    value.length == 7 -> ("FF" + value.drop(1)).toLongOrNull(16)
    value.length == 9 -> value.drop(1).toLongOrNull(16)
    else -> null
}
