package com.xiaomanjun.sleepdownschedule.feature.widget.providers

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

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
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class TodayWidgetVariant { LARGE, SQUARE }

private val widgetWorkMutex = Mutex()

internal data class CoursesWidgetLayoutMetrics(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val headerHeightDp: Int,
    val courseTopMarginDp: Int,
    val groupGapDp: Int,
    val groupHeightDp: Int,
    val groupVerticalPaddingDp: Int,
    val rowCapacity: Int,
    val maxCourses: Int,
    val useGrid: Boolean,
    val indicatorHeightDp: Int,
    val groupCornerRadiusDp: Int,
    val textScale: Float
)

internal fun coursesWidgetLayoutMetrics(
    size: WidgetRenderSize,
    variant: TodayWidgetVariant,
    courseCount: Int,
    fontScale: Float = 1f
): CoursesWidgetLayoutMetrics {
    fun progress(value: Int, compact: Int, comfortable: Int): Float =
        ((value - compact).toFloat() / (comfortable - compact).toFloat()).coerceIn(0f, 1f)

    val isSquare = variant == TodayWidgetVariant.SQUARE
    val heightProgress = progress(size.heightDp, 96, 160)
    val expansionProgress = if (isSquare) 0f else progress(size.heightDp, 168, 340)
    val widthProgress = progress(size.widthDp, if (isSquare) 110 else 220, if (isSquare) 168 else 336)
    val horizontalPaddingDp = if (isSquare) {
        (8f + 3f * widthProgress).roundToInt()
    } else {
        (10f + 4f * widthProgress).roundToInt()
    }
    val verticalPaddingDp = (8f + (if (isSquare) 3f else 6f) * heightProgress).roundToInt()
    val headerHeightDp = (22f + 2f * heightProgress).roundToInt()
    val courseTopMarginDp = 4
    val groupGapDp = 4
    val preferredGroupHeightDp = if (isSquare) 50 else (54f + 12f * expansionProgress).roundToInt()
    val availableHeightDp = (
        size.heightDp - verticalPaddingDp * 2 - headerHeightDp - courseTopMarginDp
    ).coerceAtLeast(34)
    val maximumRows = if (isSquare) 2 else 4
    val rowCapacity = floor(
        (availableHeightDp + groupGapDp).toFloat() /
            (preferredGroupHeightDp + groupGapDp).toFloat()
    ).toInt().coerceIn(1, maximumRows)
    // Fold into two columns only when the host's real height cannot fit the visible courses.
    val useGrid = !isSquare && courseCount > rowCapacity
    val maxCourses = when {
        isSquare -> rowCapacity.coerceAtMost(2)
        useGrid -> rowCapacity * 2
        else -> rowCapacity
    }
    val visibleCourses = courseCount.coerceAtMost(maxCourses).coerceAtLeast(1)
    val usedRows = if (useGrid) ceil(visibleCourses / 2f).toInt() else visibleCourses
    val groupHeightDp = floor(
        (availableHeightDp - groupGapDp * (usedRows - 1)).toFloat() / usedRows.toFloat()
    ).toInt().coerceIn(34, preferredGroupHeightDp)
    val groupVerticalPaddingDp = (3f + 2f * progress(groupHeightDp, 38, preferredGroupHeightDp))
        .roundToInt()
    val fontCompensation = (1f / (1f + (fontScale.coerceAtLeast(1f) - 1f) * 0.52f))
        .coerceIn(0.82f, 1f)
    val groupScale = (
        0.86f + 0.20f * progress(groupHeightDp, 34, preferredGroupHeightDp) + 0.06f * expansionProgress
    )
    val textScale = minOf(groupScale, 0.96f + 0.12f * widthProgress, fontCompensation)
    val indicatorHeightDp = ((groupHeightDp - groupVerticalPaddingDp * 2) * 0.82f)
        .roundToInt()
        .coerceIn(8, 44)
    val shortSideProgress = progress(minOf(size.widthDp, size.heightDp), 100, 320)
    val groupCornerRadiusDp = (
        11f + 3f * progress(groupHeightDp, 34, preferredGroupHeightDp) + 2f * shortSideProgress
    ).roundToInt()

    return CoursesWidgetLayoutMetrics(
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        headerHeightDp = headerHeightDp,
        courseTopMarginDp = courseTopMarginDp,
        groupGapDp = groupGapDp,
        groupHeightDp = groupHeightDp,
        groupVerticalPaddingDp = groupVerticalPaddingDp,
        rowCapacity = rowCapacity,
        maxCourses = maxCourses,
        useGrid = useGrid,
        indicatorHeightDp = indicatorHeightDp,
        groupCornerRadiusDp = groupCornerRadiusDp,
        textScale = textScale
    )
}

internal fun courseIndicatorHeightDp(
    size: WidgetRenderSize,
    variant: TodayWidgetVariant,
    compactGrid: Boolean = false
): Int = coursesWidgetLayoutMetrics(
    size = size,
    variant = variant,
    courseCount = if (compactGrid) 4 else 2
).indicatorHeightDp

private fun TodayWidgetVariant.appearanceVariant(): WidgetAppearanceVariant = when (this) {
    TodayWidgetVariant.LARGE -> WidgetAppearanceVariant.COURSES_LARGE
    TodayWidgetVariant.SQUARE -> WidgetAppearanceVariant.COURSES_SQUARE
}

private data class CoursesWidgetTypography(
    val titleSp: Float,
    val subtitleSp: Float,
    val emptySp: Float,
    val timeSp: Float,
    val courseNameSp: Float,
    val courseDetailSp: Float,
    val compactNameSp: Float,
    val compactDetailSp: Float
)

private data class AssistantWidgetTypography(
    val activitySp: Float,
    val courseSp: Float,
    val countdownSp: Float,
    val detailSp: Float,
    val timeSp: Float,
    val countSp: Float,
    val weatherSp: Float,
    val trailingSp: Float
)

internal data class AssistantWidgetLayoutMetrics(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val textScale: Float
)

/**
 * Launchers report widget bounds in dp, independent of the device's physical dpi. Use those real
 * bounds and font scale continuously so compact grids, display-size changes and large fonts all
 * converge on a layout that fits; there are deliberately no model or dpi special cases here.
 */
internal fun assistantWidgetLayoutMetrics(
    size: WidgetRenderSize,
    fontScale: Float
): AssistantWidgetLayoutMetrics {
    fun normalized(value: Int, compact: Int, comfortable: Int): Float =
        ((value - compact).toFloat() / (comfortable - compact).toFloat()).coerceIn(0f, 1f)

    val heightProgress = normalized(size.heightDp, 96, 156)
    val widthProgress = normalized(size.widthDp, 200, 340)
    val fontCompensation = (1f / (1f + (fontScale.coerceAtLeast(1f) - 1f) * 0.52f))
        .coerceIn(0.84f, 1f)
    val heightScale = 0.84f + 0.20f * heightProgress
    val widthScale = 0.94f + 0.10f * widthProgress
    return AssistantWidgetLayoutMetrics(
        horizontalPaddingDp = (9f + 5f * widthProgress).roundToInt(),
        verticalPaddingDp = (6f + 6f * heightProgress * fontCompensation).roundToInt(),
        textScale = minOf(heightScale, widthScale, fontCompensation)
    )
}

private fun coursesWidgetTypography(
    variant: TodayWidgetVariant,
    metrics: CoursesWidgetLayoutMetrics
): CoursesWidgetTypography {
    val scale = metrics.textScale
    fun sp(base: Float, minimum: Float): Float = (base * scale).coerceAtLeast(minimum)
    return CoursesWidgetTypography(
        titleSp = sp(if (variant == TodayWidgetVariant.SQUARE) 16f else 17f, 12f),
        subtitleSp = sp(if (variant == TodayWidgetVariant.SQUARE) 12f else 15f, 10f),
        emptySp = sp(if (variant == TodayWidgetVariant.SQUARE) 13f else 15f, 10.5f),
        timeSp = sp(13.8f, 9.8f),
        courseNameSp = sp(16f, 11f),
        courseDetailSp = sp(11.8f, 8.7f),
        compactNameSp = sp(13f, 9.8f),
        compactDetailSp = sp(10.2f, 8f)
    )
}

private fun assistantWidgetTypography(context: Context, size: WidgetRenderSize): AssistantWidgetTypography {
    val scale = assistantWidgetLayoutMetrics(
        size = size,
        fontScale = context.resources.configuration.fontScale
    ).textScale
    fun sp(base: Float, minimum: Float): Float = (base * scale).coerceAtLeast(minimum)
    return AssistantWidgetTypography(
        activitySp = sp(19f, 14.5f),
        courseSp = sp(19f, 14.5f),
        countdownSp = sp(17f, 13f),
        detailSp = sp(13f, 10.5f),
        timeSp = sp(13f, 10.5f),
        countSp = sp(17f, 13f),
        weatherSp = sp(15f, 12f),
        trailingSp = sp(14f, 11f)
    )
}

internal fun RemoteViews.setWidgetTextSize(viewId: Int, sp: Float) {
    setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, sp)
}

internal fun RemoteViews.setWidgetIndicatorHeight(viewId: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutHeight(viewId, heightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }
}

internal fun RemoteViews.setWidgetHeight(viewId: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutHeight(viewId, heightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }
}

internal fun RemoteViews.setWidgetWidth(viewId: Int, widthDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutWidth(viewId, widthDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }
}

internal fun RemoteViews.setWidgetCornerRadius(viewId: Int, radiusDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewOutlinePreferredRadius(viewId, radiusDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        setBoolean(viewId, "setClipToOutline", true)
    }
}

internal fun widgetCourseDetail(course: CourseEntity): String {
    val range = when {
        course.hasCustomTime() -> "${course.customStartTime}-${course.customEndTime}"
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

private fun RemoteViews.applyCoursesWidgetLayout(
    context: Context,
    variant: TodayWidgetVariant,
    metrics: CoursesWidgetLayoutMetrics
) {
    val density = context.resources.displayMetrics.density
    fun px(dp: Int): Int = (dp * density).roundToInt()

    setViewPadding(
        R.id.widget_content,
        px(metrics.horizontalPaddingDp),
        px(metrics.verticalPaddingDp),
        px(metrics.horizontalPaddingDp),
        px(metrics.verticalPaddingDp)
    )
    setWidgetHeight(R.id.widget_header, metrics.headerHeightDp)
    val listRows = if (variant == TodayWidgetVariant.LARGE) {
        intArrayOf(
            R.id.widget_course_row_1,
            R.id.widget_course_row_2,
            R.id.widget_course_row_3,
            R.id.widget_course_row_4
        )
    } else {
        intArrayOf(R.id.widget_compact_row_1, R.id.widget_compact_row_2)
    }
    listRows.forEach { row ->
        setWidgetHeight(row, metrics.groupHeightDp)
        setViewPadding(
            row,
            px(if (variant == TodayWidgetVariant.LARGE) 10 else 7),
            px(metrics.groupVerticalPaddingDp),
            px(if (variant == TodayWidgetVariant.LARGE) 10 else 7),
            px(metrics.groupVerticalPaddingDp)
        )
        setWidgetCornerRadius(row, metrics.groupCornerRadiusDp)
    }
    if (variant == TodayWidgetVariant.LARGE) {
        intArrayOf(
            R.id.widget_grid_row_1,
            R.id.widget_grid_row_2,
            R.id.widget_grid_row_3,
            R.id.widget_grid_row_4
        ).forEach { setWidgetHeight(it, metrics.groupHeightDp) }
        intArrayOf(
            R.id.widget_grid_cell_1,
            R.id.widget_grid_cell_2,
            R.id.widget_grid_cell_3,
            R.id.widget_grid_cell_4,
            R.id.widget_grid_cell_5,
            R.id.widget_grid_cell_6,
            R.id.widget_grid_cell_7,
            R.id.widget_grid_cell_8
        ).forEach { cell ->
            setViewPadding(
                cell,
                px(7),
                px(metrics.groupVerticalPaddingDp),
                px(7),
                px(metrics.groupVerticalPaddingDp)
            )
            setWidgetCornerRadius(cell, metrics.groupCornerRadiusDp)
        }
    }
}

internal fun launchWidgetWork(
    context: Context,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val app = context.applicationContext as CourseScheduleApp
    return app.applicationScope.launch(Dispatchers.IO) {
        widgetWorkMutex.withLock { block() }
    }
}

internal fun AppWidgetProvider.keepBroadcastAliveUntil(job: Job) {
    val pendingResult = goAsync()
    job.invokeOnCompletion {
        runCatching { pendingResult.finish() }
    }
}

open class TodayCoursesSquareWidgetProviderHost : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        keepBroadcastAliveUntil(
            MiuixTodayWidgetRenderer.refreshAsync(context, manager, ids, TodayWidgetVariant.SQUARE)
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        keepBroadcastAliveUntil(
            MiuixTodayWidgetRenderer.refreshAsync(
                context,
                manager,
                intArrayOf(appWidgetId),
                TodayWidgetVariant.SQUARE
            )
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        keepBroadcastAliveUntil(
            launchWidgetWork(context) {
                appWidgetIds.forEach {
                    app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.COURSES_SQUARE, it)
                }
            }
        )
    }
}

open class TodayAssistantWidgetProviderHost : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        keepBroadcastAliveUntil(TodayAssistantWidgetRenderer.refreshAsync(context, manager, ids))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        keepBroadcastAliveUntil(
            TodayAssistantWidgetRenderer.refreshAsync(context, manager, intArrayOf(appWidgetId))
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        keepBroadcastAliveUntil(
            launchWidgetWork(context) {
                appWidgetIds.forEach {
                    app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.TODAY_ASSISTANT, it)
                }
            }
        )
    }
}

internal object MiuixTodayWidgetRenderer {
    private val ACTION_REFRESH = "${BuildConfig.APPLICATION_ID}.action.REFRESH_TODAY_WIDGET"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isRefreshAction(action: String?): Boolean =
        action == ACTION_REFRESH ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED

    fun refreshAll(context: Context) {
        refreshAllAsync(context)
    }

    internal fun refreshAllAsync(context: Context): Job = launchWidgetWork(context) {
        val manager = AppWidgetManager.getInstance(context)
        refreshComponentNow(context, manager, TodayCoursesWidgetProvider::class.java, TodayWidgetVariant.LARGE)
        refreshComponentNow(context, manager, TodayCoursesSquareWidgetProvider::class.java, TodayWidgetVariant.SQUARE)
        val todayTomorrowIds = manager.getAppWidgetIds(
            ComponentName(context, TodayTomorrowWidgetProvider::class.java)
        )
        if (todayTomorrowIds.isNotEmpty()) {
            TodayTomorrowWidgetRenderer.refreshNow(context, manager, todayTomorrowIds)
        }
        val weekIds = manager.getAppWidgetIds(ComponentName(context, WeekScheduleWidgetProvider::class.java))
        if (weekIds.isNotEmpty()) {
            WeekScheduleWidgetRenderer.refreshNow(context, manager, weekIds)
        }
        val assistantIds = manager.getAppWidgetIds(ComponentName(context, TodayAssistantWidgetProvider::class.java))
        if (assistantIds.isNotEmpty()) {
            TodayAssistantWidgetRenderer.refreshNow(context, manager, assistantIds)
        }
    }

    private suspend fun refreshComponentNow(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<*>,
        variant: TodayWidgetVariant
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) refreshNow(context, manager, ids, variant)
    }

    fun refresh(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        variant: TodayWidgetVariant
    ) {
        refreshAsync(context, manager, ids, variant)
    }

    internal fun refreshAsync(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        variant: TodayWidgetVariant
    ): Job = launchWidgetWork(context) {
        refreshNow(context, manager, ids, variant)
    }

    internal suspend fun refreshNow(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        variant: TodayWidgetVariant
    ) {
        if (ids.isEmpty()) return
        val app = context.applicationContext as CourseScheduleApp
        app.repository.ensureDefaults()
        val state = app.repository.activeSnapshot()
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
            runCatching { manager.updateAppWidget(id, result) }
                .onFailure { Log.e("ScheduleWidget", "Failed to update courses widget $id", it) }
        }
        scheduleNextBoundaryRefresh(context, state)
    }

    internal fun buildViews(
        context: Context,
        state: AppState,
        variant: TodayWidgetVariant,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize
    ): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
        val currentWeek = scheduleWeekForDateOrNull(state.config, targetDate)
        val termStatus = scheduleTermStatusLabel(state.config, targetDate)
        val allCourses = coursesForDate(state, targetDate)
            .filter { targetDate != today || courseEndTime(it, state.periods)?.isAfter(now) != false }
        val metrics = coursesWidgetLayoutMetrics(
            size = size,
            variant = variant,
            courseCount = allCourses.size,
            fontScale = context.resources.configuration.fontScale
        )
        val courses = allCourses.take(metrics.maxCourses)
        val layout = when (variant) {
            TodayWidgetVariant.LARGE -> R.layout.widget_today_courses_miuix_adaptive_v3
            TodayWidgetVariant.SQUARE -> R.layout.widget_today_courses_square_adaptive_v2
        }
        val dark = usesDarkTheme(context, state.config)
        val custom = WidgetBackgroundRenderer.render(
            context = context,
            appearance = appearance,
            size = size,
            courseCount = courses.size,
            darkMode = dark,
            coursesMetrics = metrics
        )
        val courseColorAssignments = WidgetCourseColors.assignments(context, state, dark)
        val typography = coursesWidgetTypography(variant, metrics)
        return RemoteViews(context.packageName, layout).apply {
            applyTheme(dark, variant)
            applyCustomBackground(custom)
            applyCoursesWidgetLayout(context, variant, metrics)
            setWidgetTextSize(R.id.widget_title, typography.titleSp)
            setWidgetTextSize(R.id.widget_subtitle, typography.subtitleSp)
            setWidgetTextSize(R.id.widget_empty, typography.emptySp)
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
                    setViewVisibility(
                        R.id.widget_large_courses,
                        if (!metrics.useGrid && courses.isNotEmpty()) View.VISIBLE else View.GONE
                    )
                    setViewVisibility(R.id.widget_grid_courses, if (metrics.useGrid) View.VISIBLE else View.GONE)
                    if (metrics.useGrid) {
                        fillGridCourses(
                            state,
                            courses,
                            dark,
                            custom,
                            typography,
                            metrics,
                            courseColorAssignments
                        )
                    } else {
                        fillLargeCourses(
                            state,
                            courses,
                            dark,
                            custom,
                            typography,
                            metrics,
                            courseColorAssignments
                        )
                    }
                }
                TodayWidgetVariant.SQUARE -> fillCompactCourses(
                    state,
                    courses,
                    dark,
                    2,
                    custom,
                    typography,
                    metrics,
                    courseColorAssignments
                )
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
        custom: WidgetBackgroundResult?,
        typography: CoursesWidgetTypography,
        metrics: CoursesWidgetLayoutMetrics,
        courseColorAssignments: Map<String, Int>
    ) {
        val rows = intArrayOf(
            R.id.widget_course_row_1, R.id.widget_course_row_2,
            R.id.widget_course_row_3, R.id.widget_course_row_4
        )
        val starts = intArrayOf(
            R.id.widget_course_time_1, R.id.widget_course_time_2,
            R.id.widget_course_time_3, R.id.widget_course_time_4
        )
        val ends = intArrayOf(
            R.id.widget_course_end_1, R.id.widget_course_end_2,
            R.id.widget_course_end_3, R.id.widget_course_end_4
        )
        val indicators = intArrayOf(
            R.id.widget_course_indicator_1, R.id.widget_course_indicator_2,
            R.id.widget_course_indicator_3, R.id.widget_course_indicator_4
        )
        val names = intArrayOf(
            R.id.widget_course_name_1, R.id.widget_course_name_2,
            R.id.widget_course_name_3, R.id.widget_course_name_4
        )
        val details = intArrayOf(
            R.id.widget_course_detail_1, R.id.widget_course_detail_2,
            R.id.widget_course_detail_3, R.id.widget_course_detail_4
        )
        val courseBackground = when {
            custom?.darkBackground == true -> R.drawable.widget_course_background_custom_dark
            custom != null -> R.drawable.widget_course_background_custom
            dark -> R.drawable.widget_course_background_dark
            else -> R.drawable.widget_course_background
        }
        rows.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(
                rows[index],
                when {
                    course != null -> View.VISIBLE
                    courses.isNotEmpty() -> View.GONE
                    else -> View.GONE
                }
            )
            if (course != null) {
                setInt(
                    rows[index],
                    "setBackgroundResource",
                    courseBackground
                )
                setTextViewText(starts[index], courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(ends[index], courseEndTime(course, state.periods)?.format(timeFormatter).orEmpty())
                setTextViewText(names[index], course.name)
                setTextViewText(details[index], widgetCourseDetail(course))
                setWidgetTextSize(starts[index], typography.timeSp)
                setWidgetTextSize(ends[index], typography.timeSp)
                setWidgetTextSize(names[index], typography.courseNameSp)
                setWidgetTextSize(details[index], typography.courseDetailSp)
                setWidgetIndicatorHeight(indicators[index], metrics.indicatorHeightDp)
                setInt(
                    indicators[index],
                    "setColorFilter",
                    WidgetCourseColors.color(state.config, course, courseColorAssignments)
                )
                val primary = custom?.content?.getOrNull(index) ?: custom?.header
                val secondary = custom?.contentSecondary?.getOrNull(index) ?: custom?.headerSecondary
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
        custom: WidgetBackgroundResult?,
        typography: CoursesWidgetTypography,
        metrics: CoursesWidgetLayoutMetrics,
        courseColorAssignments: Map<String, Int>
    ) {
        val rows = intArrayOf(
            R.id.widget_grid_row_1,
            R.id.widget_grid_row_2,
            R.id.widget_grid_row_3,
            R.id.widget_grid_row_4
        )
        val cells = intArrayOf(
            R.id.widget_grid_cell_1, R.id.widget_grid_cell_2,
            R.id.widget_grid_cell_3, R.id.widget_grid_cell_4,
            R.id.widget_grid_cell_5, R.id.widget_grid_cell_6,
            R.id.widget_grid_cell_7, R.id.widget_grid_cell_8
        )
        val indicators = intArrayOf(
            R.id.widget_grid_indicator_1, R.id.widget_grid_indicator_2,
            R.id.widget_grid_indicator_3, R.id.widget_grid_indicator_4,
            R.id.widget_grid_indicator_5, R.id.widget_grid_indicator_6,
            R.id.widget_grid_indicator_7, R.id.widget_grid_indicator_8
        )
        val names = intArrayOf(
            R.id.widget_grid_name_1, R.id.widget_grid_name_2,
            R.id.widget_grid_name_3, R.id.widget_grid_name_4,
            R.id.widget_grid_name_5, R.id.widget_grid_name_6,
            R.id.widget_grid_name_7, R.id.widget_grid_name_8
        )
        val details = intArrayOf(
            R.id.widget_grid_detail_1, R.id.widget_grid_detail_2,
            R.id.widget_grid_detail_3, R.id.widget_grid_detail_4,
            R.id.widget_grid_detail_5, R.id.widget_grid_detail_6,
            R.id.widget_grid_detail_7, R.id.widget_grid_detail_8
        )
        val courseBackground = when {
            custom?.darkBackground == true -> R.drawable.widget_course_background_compact_custom_dark
            custom != null -> R.drawable.widget_course_background_compact_custom
            dark -> R.drawable.widget_course_background_compact_dark
            else -> R.drawable.widget_course_background_compact
        }
        val usedRows = ceil(courses.size / 2f).toInt()
        rows.indices.forEach { index ->
            setViewVisibility(rows[index], if (index < usedRows) View.VISIBLE else View.GONE)
        }
        cells.indices.forEach { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(cells[index], if (course == null) View.INVISIBLE else View.VISIBLE)
            if (course != null) {
                setInt(
                    cells[index],
                    "setBackgroundResource",
                    courseBackground
                )
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setWidgetTextSize(names[index], typography.compactNameSp)
                setWidgetTextSize(details[index], typography.compactDetailSp)
                setWidgetIndicatorHeight(indicators[index], metrics.indicatorHeightDp)
                setInt(
                    indicators[index],
                    "setColorFilter",
                    WidgetCourseColors.color(state.config, course, courseColorAssignments)
                )
                setTextColor(names[index], custom?.content?.getOrNull(index) ?: custom?.header ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], custom?.contentSecondary?.getOrNull(index) ?: custom?.headerSecondary ?: if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
            }
        }
    }

    private fun RemoteViews.fillCompactCourses(
        state: AppState,
        courses: List<CourseEntity>,
        dark: Boolean,
        count: Int,
        custom: WidgetBackgroundResult?,
        typography: CoursesWidgetTypography,
        metrics: CoursesWidgetLayoutMetrics,
        courseColorAssignments: Map<String, Int>
    ) {
        val rows = intArrayOf(R.id.widget_compact_row_1, R.id.widget_compact_row_2, R.id.widget_compact_row_3)
        val indicators = intArrayOf(R.id.widget_compact_indicator_1, R.id.widget_compact_indicator_2, R.id.widget_compact_indicator_3)
        val names = intArrayOf(R.id.widget_compact_name_1, R.id.widget_compact_name_2, R.id.widget_compact_name_3)
        val details = intArrayOf(R.id.widget_compact_detail_1, R.id.widget_compact_detail_2, R.id.widget_compact_detail_3)
        val courseBackground = when {
            custom?.darkBackground == true -> R.drawable.widget_course_background_compact_custom_dark
            custom != null -> R.drawable.widget_course_background_compact_custom
            dark -> R.drawable.widget_course_background_compact_dark
            else -> R.drawable.widget_course_background_compact
        }
        repeat(count) { index ->
            val course = courses.getOrNull(index)
            setViewVisibility(
                rows[index],
                when {
                    course != null -> View.VISIBLE
                    courses.isNotEmpty() -> View.GONE
                    else -> View.GONE
                }
            )
            if (course != null) {
                if (count > 1) {
                    setInt(
                        rows[index],
                        "setBackgroundResource",
                        courseBackground
                    )
                }
                setTextViewText(names[index], course.name)
                val time = courseStartTime(course, state.periods)?.format(timeFormatter).orEmpty()
                val location = course.location?.takeIf(String::isNotBlank)
                setTextViewText(details[index], listOfNotNull(time.takeIf(String::isNotBlank), location).joinToString(" · "))
                setWidgetTextSize(names[index], typography.compactNameSp)
                setWidgetTextSize(details[index], typography.compactDetailSp)
                setWidgetIndicatorHeight(indicators[index], metrics.indicatorHeightDp)
                setInt(
                    indicators[index],
                    "setColorFilter",
                    WidgetCourseColors.color(state.config, course, courseColorAssignments)
                )
                setTextColor(names[index], custom?.content?.getOrNull(index) ?: custom?.header ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17))
                setTextColor(details[index], custom?.contentSecondary?.getOrNull(index) ?: custom?.headerSecondary ?: if (dark) Color.argb(150, 255, 255, 255) else Color.argb(105, 17, 17, 17))
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
        val zone = ZoneId.systemDefault()
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
        refreshAsync(context, manager, ids)
    }

    internal fun refreshAsync(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ): Job = launchWidgetWork(context) {
        refreshNow(context, manager, ids)
    }

    internal suspend fun refreshNow(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        if (ids.isEmpty()) return
        val app = context.applicationContext as CourseScheduleApp
        app.repository.ensureDefaults()
        val state = app.repository.activeSnapshot()
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
            runCatching { manager.updateAppWidget(id, result) }
                .onFailure { Log.e("ScheduleWidget", "Failed to update assistant widget $id", it) }
        }
    }

    internal fun buildViews(
        context: Context,
        state: AppState,
        weather: AgentWeatherSnapshot?,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize
    ): RemoteViews {
        val zone = ZoneId.systemDefault()
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
        val typography = assistantWidgetTypography(context, size)
        val layout = assistantWidgetLayoutMetrics(size, context.resources.configuration.fontScale)
        val primary = custom?.header ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17)
        val secondary = custom?.headerSecondary ?: if (dark) Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0)
        val accent = custom?.accent ?: if (dark) AccentDark else AccentLight
        return RemoteViews(context.packageName, R.layout.widget_today_assistant).apply {
            val density = context.resources.displayMetrics.density
            val horizontalPadding = (layout.horizontalPaddingDp * density).roundToInt()
            val verticalPadding = (layout.verticalPaddingDp * density).roundToInt()
            setViewPadding(
                R.id.widget_agent_content,
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
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
            setWidgetTextSize(R.id.widget_agent_activity, typography.activitySp)
            setWidgetTextSize(R.id.widget_agent_course, typography.courseSp)
            setWidgetTextSize(R.id.widget_agent_countdown, typography.countdownSp)
            setWidgetTextSize(R.id.widget_agent_detail, typography.detailSp)
            setWidgetTextSize(R.id.widget_agent_time, typography.timeSp)
            setWidgetTextSize(R.id.widget_agent_count, typography.countSp)
            setWidgetTextSize(R.id.widget_agent_weather, typography.weatherSp)
            setWidgetTextSize(R.id.widget_agent_trailing, typography.trailingSp)
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
