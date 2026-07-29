package com.example.courseschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal enum class TodayWidgetVariant { LARGE, SQUARE }

private fun TodayWidgetVariant.appearanceVariant(): WidgetAppearanceVariant = when (this) {
    TodayWidgetVariant.LARGE -> WidgetAppearanceVariant.COURSES_LARGE
    TodayWidgetVariant.SQUARE -> WidgetAppearanceVariant.COURSES_SQUARE
}

class TodayCoursesSquareWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        MiuixTodayWidgetRenderer.refresh(context, manager, ids, TodayWidgetVariant.SQUARE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (MiuixTodayWidgetRenderer.isRefreshAction(intent.action)) {
            TodayCoursesWidgetProvider.refreshAll(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        MiuixTodayWidgetRenderer.refresh(context, manager, intArrayOf(appWidgetId), TodayWidgetVariant.SQUARE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach {
                app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.COURSES_SQUARE, it)
            }
        }
    }
}

class TodayAssistantWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        TodayAssistantWidgetRenderer.refresh(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (MiuixTodayWidgetRenderer.isRefreshAction(intent.action)) {
            TodayCoursesWidgetProvider.refreshAll(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        TodayAssistantWidgetRenderer.refresh(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach {
                app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.TODAY_ASSISTANT, it)
            }
        }
    }
}

internal object MiuixTodayWidgetRenderer {
    private const val ACTION_REFRESH = "com.example.courseschedule.action.REFRESH_TODAY_WIDGET"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isRefreshAction(action: String?): Boolean =
        action == ACTION_REFRESH ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_BOOT_COMPLETED

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        refreshComponent(context, manager, TodayCoursesWidgetProvider::class.java, TodayWidgetVariant.LARGE)
        refreshComponent(context, manager, TodayCoursesSquareWidgetProvider::class.java, TodayWidgetVariant.SQUARE)
        val assistantIds = manager.getAppWidgetIds(ComponentName(context, TodayAssistantWidgetProvider::class.java))
        if (assistantIds.isNotEmpty()) TodayAssistantWidgetRenderer.refresh(context, manager, assistantIds)
    }

    private fun refreshComponent(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<*>,
        variant: TodayWidgetVariant
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) refresh(context, manager, ids, variant)
    }

    fun refresh(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        variant: TodayWidgetVariant
    ) {
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as CourseScheduleApp
            app.repository.ensureDefaults()
            val state = app.repository.snapshot()
            ids.forEach { id ->
                val appearanceVariant = variant.appearanceVariant()
                val appearance = app.widgetAppearanceRepository.get(
                    appearanceVariant,
                    WidgetDefaultAppearanceId
                )
                val sizes = widgetRenderSizes(manager, id, appearanceVariant)
                val views = sizes.map { size ->
                    size to buildViews(context, state, variant, appearance, size)
                }
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && views.size > 1) {
                    RemoteViews(views.associate { (size, remote) ->
                        SizeF(size.widthDp.toFloat(), size.heightDp.toFloat()) to remote
                    })
                } else views.first().second
                manager.updateAppWidget(id, result)
            }
            scheduleNextBoundaryRefresh(context, state)
        }
    }

    internal fun buildViews(
        context: Context,
        state: AppState,
        variant: TodayWidgetVariant,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize
    ): RemoteViews {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
        val currentWeek = scheduleWeekForDateOrNull(state.config, targetDate)
        val termStatus = scheduleTermStatusLabel(state.config, targetDate)
        val allCourses = coursesForDate(state, targetDate)
            .filter { targetDate != today || courseEndTime(it, state.periods)?.isAfter(now) != false }
        val limit = when (variant) {
            TodayWidgetVariant.LARGE -> 4
            TodayWidgetVariant.SQUARE -> 2
        }
        val courses = allCourses.take(limit)
        val layout = when (variant) {
            TodayWidgetVariant.LARGE -> R.layout.widget_today_courses_miuix
            TodayWidgetVariant.SQUARE -> R.layout.widget_today_courses_square
        }
        val dark = usesDarkTheme(context, state.config)
        val custom = WidgetBackgroundRenderer.render(context, appearance, size, courses.size, dark)
        return RemoteViews(context.packageName, layout).apply {
            applyTheme(dark, variant)
            applyCustomBackground(custom)
            val dayLabel = chineseWeekday(targetDate)
            val dayPrefix = if (targetDate == today) "今日" else "明日"
            setTextViewText(
                R.id.widget_title,
                when (variant) {
                    TodayWidgetVariant.LARGE -> "${dayPrefix}课程 · $dayLabel"
                    TodayWidgetVariant.SQUARE -> "${dayPrefix}课程"
                }
            )
            setTextViewText(R.id.widget_subtitle, currentWeek?.let { "第${it}周" } ?: termStatus.orEmpty())
            if (variant != TodayWidgetVariant.LARGE) {
                setViewVisibility(R.id.widget_subtitle, View.GONE)
            }
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            setViewVisibility(R.id.widget_empty, if (courses.isEmpty()) View.VISIBLE else View.GONE)
            setTextViewText(
                R.id.widget_empty,
                termStatus ?: if (targetDate == today) "今天没课了" else "明天没有课"
            )
            when (variant) {
                TodayWidgetVariant.LARGE -> {
                    val useGrid = courses.size >= 3
                    setViewVisibility(
                        R.id.widget_large_courses,
                        if (!useGrid && courses.isNotEmpty()) View.VISIBLE else View.GONE
                    )
                    setViewVisibility(R.id.widget_grid_courses, if (useGrid) View.VISIBLE else View.GONE)
                    if (useGrid) fillGridCourses(state, courses, dark, custom) else fillLargeCourses(state, courses, dark, custom)
                }
                TodayWidgetVariant.SQUARE -> fillCompactCourses(state, courses, dark, 2, custom)
            }
        }
    }

    private fun RemoteViews.applyCustomBackground(custom: WidgetBackgroundResult?) {
        if (custom == null) {
            setViewVisibility(R.id.widget_background_image, View.GONE)
            return
        }
        setViewVisibility(R.id.widget_background_image, View.VISIBLE)
        setImageViewBitmap(R.id.widget_background_image, custom.bitmap)
        setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
        setTextColor(R.id.widget_title, custom.header)
        setTextColor(R.id.widget_subtitle, custom.headerSecondary)
        setTextColor(R.id.widget_empty, custom.headerSecondary)
    }

    private fun RemoteViews.fillLargeCourses(
        state: AppState,
        courses: List<CourseEntity>,
        dark: Boolean,
        custom: WidgetBackgroundResult?
    ) {
        val rows = intArrayOf(R.id.widget_course_row_1, R.id.widget_course_row_2)
        val starts = intArrayOf(R.id.widget_course_time_1, R.id.widget_course_time_2)
        val ends = intArrayOf(R.id.widget_course_end_1, R.id.widget_course_end_2)
        val indicators = intArrayOf(R.id.widget_course_indicator_1, R.id.widget_course_indicator_2)
        val names = intArrayOf(R.id.widget_course_name_1, R.id.widget_course_name_2)
        val details = intArrayOf(R.id.widget_course_detail_1, R.id.widget_course_detail_2)
        rows.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(
                rows[index],
                when {
                    course != null -> View.VISIBLE
                    courses.isNotEmpty() -> View.INVISIBLE
                    else -> View.GONE
                }
            )
            if (course != null) {
                setInt(
                    rows[index],
                    "setBackgroundResource",
                    if (custom != null) android.R.color.transparent
                    else if (dark) R.drawable.widget_course_background_dark else R.drawable.widget_course_background
                )
                setTextViewText(starts[index], courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(ends[index], courseEndTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(names[index], course.name)
                setTextViewText(details[index], courseDetail(course))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                val primary = custom?.content?.getOrNull(index)
                val secondary = custom?.contentSecondary?.getOrNull(index)
                setTextColor(starts[index], primary ?: if (dark) Color.argb(210, 255, 255, 255) else Color.argb(180, 17, 17, 17))
                setTextColor(ends[index], secondary ?: if (dark) Color.argb(140, 255, 255, 255) else Color.argb(105, 17, 17, 17))
                setTextColor(names[index], primary ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], secondary ?: if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.fillGridCourses(
        state: AppState,
        courses: List<CourseEntity>,
        dark: Boolean,
        custom: WidgetBackgroundResult?
    ) {
        val cells = intArrayOf(R.id.widget_grid_cell_1, R.id.widget_grid_cell_2, R.id.widget_grid_cell_3, R.id.widget_grid_cell_4)
        val indicators = intArrayOf(R.id.widget_grid_indicator_1, R.id.widget_grid_indicator_2, R.id.widget_grid_indicator_3, R.id.widget_grid_indicator_4)
        val names = intArrayOf(R.id.widget_grid_name_1, R.id.widget_grid_name_2, R.id.widget_grid_name_3, R.id.widget_grid_name_4)
        val details = intArrayOf(R.id.widget_grid_detail_1, R.id.widget_grid_detail_2, R.id.widget_grid_detail_3, R.id.widget_grid_detail_4)
        cells.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(cells[index], if (course == null) View.INVISIBLE else View.VISIBLE)
            if (course != null) {
                setInt(
                    cells[index],
                    "setBackgroundResource",
                    if (custom != null) android.R.color.transparent
                    else if (dark) R.drawable.widget_course_background_compact_dark else R.drawable.widget_course_background_compact
                )
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                setTextColor(names[index], custom?.content?.getOrNull(index) ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], custom?.contentSecondary?.getOrNull(index) ?: if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.fillCompactCourses(
        state: AppState,
        courses: List<CourseEntity>,
        dark: Boolean,
        count: Int,
        custom: WidgetBackgroundResult?
    ) {
        val rows = intArrayOf(R.id.widget_compact_row_1, R.id.widget_compact_row_2, R.id.widget_compact_row_3)
        val indicators = intArrayOf(R.id.widget_compact_indicator_1, R.id.widget_compact_indicator_2, R.id.widget_compact_indicator_3)
        val names = intArrayOf(R.id.widget_compact_name_1, R.id.widget_compact_name_2, R.id.widget_compact_name_3)
        val details = intArrayOf(R.id.widget_compact_detail_1, R.id.widget_compact_detail_2, R.id.widget_compact_detail_3)
        repeat(count) { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(
                rows[index],
                when {
                    course != null -> View.VISIBLE
                    courses.isNotEmpty() -> View.INVISIBLE
                    else -> View.GONE
                }
            )
            if (course != null) {
                if (count > 1) {
                    setInt(
                        rows[index],
                        "setBackgroundResource",
                        if (custom != null) android.R.color.transparent
                        else if (dark) R.drawable.widget_course_background_compact_dark else R.drawable.widget_course_background_compact
                    )
                }
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                setTextColor(names[index], custom?.content?.getOrNull(index) ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], custom?.contentSecondary?.getOrNull(index) ?: if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.applyTheme(dark: Boolean, variant: TodayWidgetVariant) {
        setInt(
            R.id.widget_root,
            "setBackgroundResource",
            when (variant) {
                TodayWidgetVariant.SQUARE -> if (dark) R.drawable.widget_today_background_compact_dark else R.drawable.widget_today_background_compact
                TodayWidgetVariant.LARGE -> if (dark) R.drawable.widget_today_background_dark else R.drawable.widget_today_background
            }
        )
        setTextColor(R.id.widget_title, if (dark) Color.WHITE else Color.rgb(17, 17, 17))
        setTextColor(R.id.widget_subtitle, if (dark) Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0))
        setTextColor(R.id.widget_empty, if (dark) Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0))
    }

    private fun stableCourseColor(config: ScheduleConfigEntity, course: CourseEntity): Int {
        if (config.cardColorArgb != MulticolorCourseCardArgb) return config.cardColorArgb.toInt()
        val key = courseCardColorKey(course)
        return DefaultCourseCardPalette[(key.hashCode() and Int.MAX_VALUE) % DefaultCourseCardPalette.size].toInt()
    }

    private fun courseDetail(course: CourseEntity): String {
        val range = when {
            course.periods.isEmpty() -> null
            course.periods.size == 1 -> "第${course.periods.first()}节"
            else -> "第${course.periods.minOrNull()}-${course.periods.maxOrNull()}节"
        }
        return listOfNotNull(
            range,
            course.location?.takeIf(String::isNotBlank),
            course.teacher?.takeIf(String::isNotBlank)
        ).joinToString(" | ")
    }

    private fun chineseWeekday(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }

    internal fun coursesForDate(state: AppState, date: LocalDate): List<CourseEntity> {
        val weekday = date.dayOfWeek.toChineseWeekday()
        val week = scheduleWeekForDateOrNull(state.config, date) ?: return emptyList()
        return state.courses
            .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
            .sortedBy { courseStartTime(it, state.periods) ?: LocalTime.MAX }
    }

    internal fun usesDarkTheme(context: Context, config: ScheduleConfigEntity): Boolean {
        if (!config.followSystemDarkMode) return config.darkMode
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 2401, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TodayCoursesWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 2402, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleNextBoundaryRefresh(context: Context, state: AppState) {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
        val targetNow = if (targetDate == today) now else LocalTime.MIN
        val nextBoundary = coursesForDate(state, targetDate)
            .flatMap { listOfNotNull(courseStartTime(it, state.periods), courseEndTime(it, state.periods)) }
            .filter { it.isAfter(targetNow) }
            .minOrNull()
        val boundaryTrigger = nextBoundary?.let {
            ZonedDateTime.of(targetDate, it, zone).plusSeconds(2)
        }
        val stateTransitionTrigger = when {
            now >= LocalTime.of(22, 0) ->
                ZonedDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT, zone).plusSeconds(2)
            now < LocalTime.of(6, 0) ->
                ZonedDateTime.of(today, LocalTime.of(6, 0), zone).plusSeconds(2)
            else -> ZonedDateTime.of(today.plusDays(1), LocalTime.of(22, 0), zone)
        }
        val trigger = listOfNotNull(boundaryTrigger, stateTransitionTrigger).minOrNull()
            ?: stateTransitionTrigger
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.set(AlarmManager.RTC, trigger.toInstant().toEpochMilli(), refreshPendingIntent(context))
    }
}

internal object TodayAssistantWidgetRenderer {
    private const val AccentLight = 0xFF006EDC.toInt()
    private const val AccentDark = 0xFF62B5FF.toInt()

    fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as CourseScheduleApp
            app.repository.ensureDefaults()
            val state = app.repository.snapshot()
            val weather = if (DayAgentPreferences.isWeatherEnabled(context)) {
                DayAgentWeatherRepository(context.applicationContext).getWeather()
            } else null
            ids.forEach { id ->
                val variant = WidgetAppearanceVariant.TODAY_ASSISTANT
                val appearance = app.widgetAppearanceRepository.get(
                    variant,
                    WidgetDefaultAppearanceId
                )
                val sizes = widgetRenderSizes(manager, id, variant)
                val views = sizes.map { size -> size to buildViews(context, state, weather, appearance, size) }
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && views.size > 1) {
                    RemoteViews(views.associate { (size, remote) ->
                        SizeF(size.widthDp.toFloat(), size.heightDp.toFloat()) to remote
                    })
                } else views.first().second
                manager.updateAppWidget(id, result)
            }
        }
    }

    internal fun buildViews(
        context: Context,
        state: AppState,
        weather: AgentWeatherSnapshot?,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize
    ): RemoteViews {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.now(zone)
        val facts = buildDayAgentFacts(
            courses = state.courses,
            periods = state.periods,
            config = state.config,
            date = now.toLocalDate(),
            weather = weather,
            now = now
        )
        val current = facts.today.firstOrNull { !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end) }
        val next = facts.today.firstOrNull { now.toLocalTime().isBefore(it.start) }
        val currentSlots = facts.today.filter {
            !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end)
        }
        val nextSlots = next?.let { firstNext ->
            facts.today.filter { it.start == firstNext.start }
        }.orEmpty()
        val previewTomorrow = now.toLocalTime() >= LocalTime.of(22, 0) &&
            current == null &&
            next == null &&
            facts.tomorrow.isNotEmpty()
        val focus = current ?: next ?: facts.tomorrow.firstOrNull().takeIf { previewTomorrow }
        val focusSlots = when {
            current != null -> currentSlots
            next != null -> nextSlots
            focus != null -> listOf(focus)
            else -> emptyList()
        }
        val focusTitle = focusSlots.joinToString("、") { it.course.name }
        val remaining = (current?.end ?: next?.start)?.let {
            Duration.between(now.toLocalTime(), it).toMinutes().coerceAtLeast(0)
        }
        val activity = when {
            current != null -> "当前"
            next != null -> "下节课"
            previewTomorrow -> "明日首课"
            facts.today.isEmpty() -> "今日无课"
            else -> "课程已结束"
        }
        val countdown = when {
            current != null && remaining != null -> "${remaining} 分钟后下课"
            next != null && remaining != null && now.toLocalTime() >= LocalTime.of(6, 0) ->
                "${remaining} 分钟后"
            previewTomorrow -> ""
            facts.today.isEmpty() -> "轻松一天"
            else -> "今日完成"
        }
        val detail = focus?.course?.let { course ->
            listOfNotNull(course.location?.takeIf(String::isNotBlank), course.teacher?.takeIf(String::isNotBlank))
                .joinToString(" | ")
        }.orEmpty()
        val timeText = focus?.let {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            "${it.start.format(formatter)} - ${it.end.format(formatter)}"
        }.orEmpty()
        val weatherText = when {
            !DayAgentPreferences.isWeatherEnabled(context) -> "天气未启用"
            weather != null -> "${widgetWeatherEmoji(weather.summary)} ${weather.temperature}°C ${weather.summary.substringBefore('，')}"
            else -> "天气加载中"
        }
        val alert = weather?.let(::widgetWeatherAlert)
        val trailingText = alert?.let { "⚠️ $it" }.orEmpty()
        val dark = if (!state.config.followSystemDarkMode) state.config.darkMode else {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mode == Configuration.UI_MODE_NIGHT_YES
        }
        val custom = WidgetBackgroundRenderer.render(context, appearance, size, 2, dark)
        val primary = custom?.header ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17)
        val secondary = custom?.headerSecondary ?: if (dark) Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0)
        val accent = custom?.accent ?: if (dark) AccentDark else AccentLight
        return RemoteViews(context.packageName, R.layout.widget_today_assistant).apply {
            setInt(R.id.widget_agent_root, "setBackgroundResource", if (dark) R.drawable.widget_today_background_dark else R.drawable.widget_today_background)
            if (custom == null) {
                setViewVisibility(R.id.widget_agent_background_image, View.GONE)
            } else {
                setViewVisibility(R.id.widget_agent_background_image, View.VISIBLE)
                setImageViewBitmap(R.id.widget_agent_background_image, custom.bitmap)
                setInt(R.id.widget_agent_root, "setBackgroundColor", Color.TRANSPARENT)
            }
            setTextViewText(R.id.widget_agent_activity, activity)
            setTextViewText(R.id.widget_agent_course, focusTitle)
            setViewVisibility(R.id.widget_agent_course, if (focus == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.widget_agent_countdown, countdown)
            setViewVisibility(
                R.id.widget_agent_countdown,
                if (countdown.isBlank()) View.GONE else View.VISIBLE
            )
            setTextViewText(R.id.widget_agent_detail, detail)
            setTextViewText(R.id.widget_agent_time, timeText)
            setViewVisibility(R.id.widget_agent_time, if (focus == null) View.GONE else View.VISIBLE)
            setTextViewText(
                R.id.widget_agent_count,
                if (previewTomorrow) {
                    "明天有 ${facts.tomorrow.size} 节课"
                } else {
                    "今天有 ${facts.today.size} 节课"
                }
            )
            setTextViewText(R.id.widget_agent_weather, weatherText)
            setTextViewText(R.id.widget_agent_trailing, trailingText)
            setViewVisibility(R.id.widget_agent_trailing, if (trailingText.isBlank()) View.GONE else View.VISIBLE)
            setTextColor(R.id.widget_agent_activity, accent)
            setTextColor(R.id.widget_agent_countdown, accent)
            setTextColor(R.id.widget_agent_course, primary)
            setTextColor(R.id.widget_agent_count, primary)
            setTextColor(R.id.widget_agent_detail, secondary)
            setTextColor(R.id.widget_agent_time, secondary)
            setTextColor(R.id.widget_agent_weather, secondary)
            setTextColor(R.id.widget_agent_trailing, if (alert == null) secondary else Color.rgb(255, 149, 0))
            setInt(R.id.widget_agent_divider, "setBackgroundColor", if (dark) Color.argb(28, 255, 255, 255) else Color.argb(22, 0, 0, 0))
            setOnClickPendingIntent(R.id.widget_agent_root, openAppPendingIntent(context))
        }
    }

    private fun widgetWeatherEmoji(summary: String): String {
        val condition = summary.substringBefore('，').substringBefore(',').trim()
        return when {
            "雷" in condition -> "⛈️"
            "雪" in condition -> "🌨️"
            "雨" in condition -> "🌧️"
            "雾" in condition || "霾" in condition -> "🌫️"
            "阴" in condition -> "☁️"
            "云" in condition -> "⛅"
            "晴" in condition -> "☀️"
            else -> "🌤️"
        }
    }

    private fun widgetWeatherAlert(weather: AgentWeatherSnapshot): String? {
        val condition = weather.summary.substringBefore('，').substringBefore(',').trim()
        return buildList {
            when {
                "雷" in condition -> add("雷暴提醒")
                "雪" in condition -> add("降雪提醒")
                "暴雨" in condition -> add("强降雨提醒")
                weather.precipitationProbability >= 60 -> add("降雨提醒")
            }
            if (weather.temperature >= 35 || weather.apparentTemperature >= 38) add("高温提醒")
            if (weather.temperature <= 5 || weather.apparentTemperature <= 2) add("低温提醒")
            if (weather.windSpeed >= 35) add("大风提醒")
        }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 2501, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
