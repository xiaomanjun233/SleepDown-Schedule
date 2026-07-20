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
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal enum class TodayWidgetVariant { LARGE, SQUARE }

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
            ids.forEach { manager.updateAppWidget(it, buildViews(context, state, variant)) }
            scheduleNextBoundaryRefresh(context, state)
        }
    }

    private fun buildViews(context: Context, state: AppState, variant: TodayWidgetVariant): RemoteViews {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
        val currentWeek = effectiveCurrentWeek(state.config, targetDate)
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
        return RemoteViews(context.packageName, layout).apply {
            applyTheme(dark, variant)
            val dayLabel = chineseWeekday(targetDate)
            val dayPrefix = if (targetDate == today) "今日" else "明日"
            setTextViewText(
                R.id.widget_title,
                when (variant) {
                    TodayWidgetVariant.LARGE -> "${dayPrefix}课程 · $dayLabel"
                    TodayWidgetVariant.SQUARE -> "${dayPrefix}课程"
                }
            )
            setTextViewText(R.id.widget_subtitle, "第${currentWeek}周")
            if (variant != TodayWidgetVariant.LARGE) {
                setViewVisibility(R.id.widget_subtitle, View.GONE)
            }
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
            setViewVisibility(R.id.widget_empty, if (courses.isEmpty()) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, if (targetDate == today) "今天没课了" else "明天没有课")
            when (variant) {
                TodayWidgetVariant.LARGE -> {
                    val useGrid = courses.size >= 3
                    setViewVisibility(
                        R.id.widget_large_courses,
                        if (!useGrid && courses.isNotEmpty()) View.VISIBLE else View.GONE
                    )
                    setViewVisibility(R.id.widget_grid_courses, if (useGrid) View.VISIBLE else View.GONE)
                    if (useGrid) fillGridCourses(state, courses, dark) else fillLargeCourses(state, courses, dark)
                }
                TodayWidgetVariant.SQUARE -> fillCompactCourses(state, courses, dark, 2)
            }
        }
    }

    private fun RemoteViews.fillLargeCourses(state: AppState, courses: List<CourseEntity>, dark: Boolean) {
        val rows = intArrayOf(R.id.widget_course_row_1, R.id.widget_course_row_2)
        val starts = intArrayOf(R.id.widget_course_time_1, R.id.widget_course_time_2)
        val ends = intArrayOf(R.id.widget_course_end_1, R.id.widget_course_end_2)
        val indicators = intArrayOf(R.id.widget_course_indicator_1, R.id.widget_course_indicator_2)
        val names = intArrayOf(R.id.widget_course_name_1, R.id.widget_course_name_2)
        val details = intArrayOf(R.id.widget_course_detail_1, R.id.widget_course_detail_2)
        rows.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(rows[index], if (course == null) View.GONE else View.VISIBLE)
            if (course != null) {
                setInt(rows[index], "setBackgroundResource", if (dark) R.drawable.widget_course_background_dark else R.drawable.widget_course_background)
                setTextViewText(starts[index], courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(ends[index], courseEndTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(names[index], course.name)
                setTextViewText(details[index], courseDetail(course))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                setTextColor(starts[index], if (dark) Color.argb(210, 255, 255, 255) else Color.argb(180, 17, 17, 17))
                setTextColor(ends[index], if (dark) Color.argb(140, 255, 255, 255) else Color.argb(105, 17, 17, 17))
                setTextColor(names[index], if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.fillGridCourses(state: AppState, courses: List<CourseEntity>, dark: Boolean) {
        val cells = intArrayOf(R.id.widget_grid_cell_1, R.id.widget_grid_cell_2, R.id.widget_grid_cell_3, R.id.widget_grid_cell_4)
        val indicators = intArrayOf(R.id.widget_grid_indicator_1, R.id.widget_grid_indicator_2, R.id.widget_grid_indicator_3, R.id.widget_grid_indicator_4)
        val names = intArrayOf(R.id.widget_grid_name_1, R.id.widget_grid_name_2, R.id.widget_grid_name_3, R.id.widget_grid_name_4)
        val details = intArrayOf(R.id.widget_grid_detail_1, R.id.widget_grid_detail_2, R.id.widget_grid_detail_3, R.id.widget_grid_detail_4)
        cells.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(cells[index], if (course == null) View.INVISIBLE else View.VISIBLE)
            if (course != null) {
                setInt(cells[index], "setBackgroundResource", if (dark) R.drawable.widget_course_background_compact_dark else R.drawable.widget_course_background_compact)
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                setTextColor(names[index], if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.fillCompactCourses(
        state: AppState,
        courses: List<CourseEntity>,
        dark: Boolean,
        count: Int
    ) {
        val rows = intArrayOf(R.id.widget_compact_row_1, R.id.widget_compact_row_2, R.id.widget_compact_row_3)
        val indicators = intArrayOf(R.id.widget_compact_indicator_1, R.id.widget_compact_indicator_2, R.id.widget_compact_indicator_3)
        val names = intArrayOf(R.id.widget_compact_name_1, R.id.widget_compact_name_2, R.id.widget_compact_name_3)
        val details = intArrayOf(R.id.widget_compact_detail_1, R.id.widget_compact_detail_2, R.id.widget_compact_detail_3)
        repeat(count) { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(rows[index], if (course == null) View.GONE else View.VISIBLE)
            if (course != null) {
                if (count > 1) {
                    setInt(rows[index], "setBackgroundResource", if (dark) R.drawable.widget_course_background_compact_dark else R.drawable.widget_course_background_compact)
                }
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setInt(indicators[index], "setColorFilter", stableCourseColor(state.config, course))
                setTextColor(names[index], if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
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
        setInt(R.id.widget_refresh, "setBackgroundResource", if (dark) R.drawable.widget_refresh_background_dark else R.drawable.widget_refresh_background)
        setTextColor(R.id.widget_title, if (dark) Color.WHITE else Color.rgb(17, 17, 17))
        setTextColor(R.id.widget_subtitle, if (dark) Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0))
        setInt(R.id.widget_refresh, "setColorFilter", Color.rgb(10, 132, 255))
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

    private fun coursesForDate(state: AppState, date: LocalDate): List<CourseEntity> {
        val weekday = date.dayOfWeek.toChineseWeekday()
        val week = effectiveCurrentWeek(state.config, date)
        return state.courses
            .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
            .sortedBy { courseStartTime(it, state.periods) ?: LocalTime.MAX }
    }

    private fun usesDarkTheme(context: Context, config: ScheduleConfigEntity): Boolean {
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
        val trigger = if (nextBoundary != null) {
            ZonedDateTime.of(targetDate, nextBoundary, zone).plusSeconds(2)
        } else {
            ZonedDateTime.of(today.plusDays(1), LocalTime.of(22, 0), zone)
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.set(AlarmManager.RTC, trigger.toInstant().toEpochMilli(), refreshPendingIntent(context))
    }
}
