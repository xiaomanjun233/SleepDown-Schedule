package com.example.courseschedule

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TodayCoursesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MiuixTodayWidgetRenderer.refresh(context, appWidgetManager, appWidgetIds, TodayWidgetVariant.LARGE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            refreshAll(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        MiuixTodayWidgetRenderer.refresh(context, appWidgetManager, intArrayOf(appWidgetId), TodayWidgetVariant.LARGE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach {
                app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.COURSES_LARGE, it)
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.courseschedule.action.REFRESH_TODAY_WIDGET"

        fun refreshAll(context: Context) {
            MiuixTodayWidgetRenderer.refreshAll(context)
        }

        private fun refresh(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val app = context.applicationContext as CourseScheduleApp
                app.repository.ensureDefaults()
                val state = app.repository.snapshot()
                val views = buildViews(context, state)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
                scheduleNextBoundaryRefresh(context, state)
            }
        }

        private fun buildViews(context: Context, state: AppState): RemoteViews {
            val zone = ZoneId.of("Asia/Shanghai")
            val today = LocalDate.now(zone)
            val now = LocalTime.now(zone)
            val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
            val currentWeek = scheduleWeekForDateOrNull(state.config, targetDate)
            val termStatus = scheduleTermStatusLabel(state.config, targetDate)
            val courses = coursesForWidgetDate(state, targetDate)
                .filter { course -> targetDate != today || courseEndTime(course, state.periods)?.isAfter(now) != false }
                .take(4)
            return RemoteViews(context.packageName, R.layout.widget_today_courses).apply {
                val dark = widgetUsesDarkTheme(context, state.config)
                applyWidgetTheme(dark)
                setTextViewText(R.id.widget_title, if (targetDate == today) "今日课程" else "明日课程")
                val subtitle = currentWeek?.let { "${targetDate.monthValue}月${targetDate.dayOfMonth}日 · 第${it}周" }
                    ?: "${targetDate.monthValue}月${targetDate.dayOfMonth}日 · ${termStatus.orEmpty()}"
                setTextViewText(R.id.widget_subtitle, subtitle)
                setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))

                val useGrid = courses.size > 2
                setViewVisibility(R.id.widget_large_courses, if (!useGrid && courses.isNotEmpty()) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_grid_courses, if (useGrid) View.VISIBLE else View.GONE)
                fillCourseViews(
                    context = context,
                    views = if (useGrid) {
                        listOf(R.id.widget_grid_course_1, R.id.widget_grid_course_2, R.id.widget_grid_course_3, R.id.widget_grid_course_4)
                    } else {
                        listOf(R.id.widget_course_1, R.id.widget_course_2)
                    },
                    courses = courses,
                    periods = state.periods,
                    compact = useGrid,
                    dark = dark
                )
                setViewVisibility(R.id.widget_empty, if (courses.isEmpty()) View.VISIBLE else View.GONE)
                setTextViewText(
                    R.id.widget_empty,
                    termStatus ?: if (targetDate == today) "今天没有剩余课程" else "明天没有课程"
                )
            }
        }

        private fun RemoteViews.fillCourseViews(
            context: Context,
            views: List<Int>,
            courses: List<CourseEntity>,
            periods: List<PeriodEntity>,
            compact: Boolean,
            dark: Boolean
        ) {
            views.forEachIndexed { index, viewId ->
                val course = courses.getOrNull(index)
                if (course == null) {
                    setViewVisibility(viewId, View.INVISIBLE)
                } else {
                    setViewVisibility(viewId, View.VISIBLE)
                    setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, if (compact) 11.5f else 13f)
                    setInt(viewId, "setMaxLines", if (compact) 2 else 3)
                    val verticalPadding = if (compact) dp(context, 5) else dp(context, 7)
                    val horizontalPadding = if (compact) dp(context, 8) else dp(context, 10)
                    setInt(viewId, "setBackgroundResource", if (dark) R.drawable.widget_course_background_dark else R.drawable.widget_course_background)
                    setTextColor(viewId, if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                    setViewPadding(viewId, horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    setTextViewText(viewId, widgetCourseText(course, periods, compact))
                }
            }
        }

        private fun RemoteViews.applyWidgetTheme(dark: Boolean) {
            setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_today_background_dark else R.drawable.widget_today_background)
            setInt(R.id.widget_refresh, "setBackgroundResource", if (dark) R.drawable.widget_refresh_background_dark else R.drawable.widget_refresh_background)
            setTextColor(R.id.widget_title, if (dark) Color.WHITE else Color.rgb(17, 17, 17))
            setTextColor(R.id.widget_subtitle, if (dark) Color.argb(170, 255, 255, 255) else Color.argb(153, 0, 0, 0))
            setTextColor(R.id.widget_refresh, Color.rgb(10, 132, 255))
            setTextColor(R.id.widget_empty, if (dark) Color.argb(170, 255, 255, 255) else Color.argb(153, 0, 0, 0))
        }

        private fun widgetUsesDarkTheme(context: Context, config: ScheduleConfigEntity): Boolean {
            if (!config.followSystemDarkMode) return config.darkMode
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMode == Configuration.UI_MODE_NIGHT_YES
        }

        private fun coursesForWidgetDate(state: AppState, date: LocalDate): List<CourseEntity> {
            val weekday = date.dayOfWeek.toChineseWeekday()
            val week = scheduleWeekForDateOrNull(state.config, date) ?: return emptyList()
            return state.courses
                .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
                .sortedBy { courseStartTime(it, state.periods) ?: LocalTime.MAX }
        }

        private fun widgetCourseText(course: CourseEntity, periods: List<PeriodEntity>, compact: Boolean): String {
            val time = courseTimeLabel(course, periods)
            val location = course.location?.takeIf { it.isNotBlank() }
            return if (compact) {
                buildString {
                    append(time)
                    append("  ")
                    append(course.name)
                    if (location != null) {
                        append(" · ")
                        append(location)
                    }
                }
            } else {
                buildString {
                    append(time)
                    append("  ")
                    append(course.name)
                    if (location != null) {
                        append('\n')
                        append(location)
                    }
                }
            }
        }

        private fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(context, 2301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, TodayCoursesWidgetProvider::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(context, 2302, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun scheduleNextBoundaryRefresh(context: Context, state: AppState) {
            val zone = ZoneId.of("Asia/Shanghai")
            val today = LocalDate.now(zone)
            val now = LocalTime.now(zone)
            val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
            val targetNow = if (targetDate == today) now else LocalTime.MIN
            val nextBoundary = coursesForWidgetDate(state, targetDate)
                .flatMap { course -> listOfNotNull(courseStartTime(course, state.periods), courseEndTime(course, state.periods)) }
                .filter { it.isAfter(targetNow) }
                .minOrNull()
            val triggerDateTime = if (nextBoundary != null) {
                ZonedDateTime.of(targetDate, nextBoundary, zone).plusSeconds(2)
            } else {
                val tomorrow = today.plusDays(1)
                ZonedDateTime.of(tomorrow, LocalTime.of(22, 0), zone)
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarmManager.set(AlarmManager.RTC, triggerDateTime.toInstant().toEpochMilli(), refreshPendingIntent(context))
        }
    }
}
