package com.xiaomanjun.sleepdownschedule.feature.widget.providers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.graphics.withClip
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.drawContinuousRoundRect
import com.xiaomanjun.sleepdownschedule.courseStartTime
import com.xiaomanjun.sleepdownschedule.currentTimelinePeriod
import com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.home.day.visibleWeekdaysForBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceEntity
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceVariant
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCourseColors
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetDefaultAppearanceId
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetRenderSize
import com.xiaomanjun.sleepdownschedule.feature.widget.readableWidgetTextColor
import com.xiaomanjun.sleepdownschedule.feature.widget.widgetRenderSize
import com.xiaomanjun.sleepdownschedule.scheduleWeekForDateOrNull
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

internal data class WeekWidgetLayoutMetrics(
    val paddingDp: Int,
    val headerHeightDp: Int,
    val weekdayHeaderHeightDp: Int,
    val periodLabelWidthDp: Int,
    val columnGapDp: Float,
    val textScale: Float
)

internal fun weekWidgetLayoutMetrics(
    size: WidgetRenderSize,
    fontScale: Float = 1f
): WeekWidgetLayoutMetrics {
    fun progress(value: Int, compact: Int, comfortable: Int): Float =
        ((value - compact).toFloat() / (comfortable - compact).toFloat()).coerceIn(0f, 1f)

    val widthProgress = progress(size.widthDp, 220, 480)
    val heightProgress = progress(size.heightDp, 180, 560)
    val fontCompensation = (1f / (1f + (fontScale.coerceAtLeast(1f) - 1f) * 0.52f))
        .coerceIn(0.84f, 1f)
    return WeekWidgetLayoutMetrics(
        paddingDp = (9f + 4f * minOf(widthProgress, heightProgress)).roundToInt(),
        headerHeightDp = (30f + 8f * heightProgress).roundToInt(),
        weekdayHeaderHeightDp = (30f + 7f * heightProgress).roundToInt(),
        periodLabelWidthDp = (19f + 7f * widthProgress).roundToInt(),
        columnGapDp = 1.8f + 1.2f * widthProgress,
        textScale = minOf(0.96f + 0.08f * widthProgress, 0.94f + 0.10f * heightProgress, fontCompensation)
    )
}

internal data class WeekWidgetPeriodWindow(
    val firstPosition: Int,
    val count: Int
) {
    val lastExclusive: Int get() = firstPosition + count
}

internal fun weekWidgetPeriodWindow(
    periods: List<PeriodEntity>,
    size: WidgetRenderSize,
    now: LocalTime
): WeekWidgetPeriodWindow {
    if (periods.isEmpty()) return WeekWidgetPeriodWindow(0, 0)
    val metrics = weekWidgetLayoutMetrics(size)
    val availableHeight = (
        size.heightDp - metrics.paddingDp * 2 - metrics.headerHeightDp -
            metrics.weekdayHeaderHeightDp - 12
    ).coerceAtLeast(1)
    val maxSupportedPeriods = minOf(periods.size, 12)
    val visibleCount = floor(availableHeight / 27f)
        .toInt()
        .coerceAtLeast(1)
        .coerceAtMost(maxSupportedPeriods)
    if (visibleCount >= periods.size) return WeekWidgetPeriodWindow(0, periods.size)

    val anchor = periods.indexOfFirst { period ->
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull()
        end != null && !now.isAfter(end)
    }.let { if (it >= 0) it else periods.lastIndex }
    val preferredBefore = (visibleCount / 3).coerceAtLeast(1)
    val first = (anchor - preferredBefore).coerceIn(0, periods.size - visibleCount)
    return WeekWidgetPeriodWindow(first, visibleCount)
}

internal fun weekWidgetCurrentPeriodIndex(
    periods: List<PeriodEntity>,
    now: LocalTime
): Int? = currentTimelinePeriod(periods, now)?.periodIndex

internal fun weekWidgetInitialScrollPosition(
    periods: List<PeriodEntity>,
    now: LocalTime,
    visiblePeriodCount: Int = 5
): Int {
    if (periods.isEmpty()) return 0
    val visibleCount = visiblePeriodCount.coerceAtLeast(1).coerceAtMost(periods.size)
    val anchor = periods.indexOfFirst { period ->
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull()
        end != null && !now.isAfter(end)
    }.let { if (it >= 0) it else periods.lastIndex }
    return (anchor - 1).coerceIn(0, (periods.size - visibleCount).coerceAtLeast(0))
}

internal fun weekWidgetPresetOutlineEnabled(hasCustomWallpaper: Boolean): Boolean =
    hasCustomWallpaper

internal data class WeekWidgetCourseTextMetrics(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val nameSp: Float,
    val locationSp: Float,
    val teacherSp: Float,
    val nameMaxLines: Int,
    val locationMaxLines: Int,
    val centerReserveDp: Float,
    val showTeacher: Boolean
)

internal fun weekWidgetCourseTextMetrics(
    cardWidthDp: Float,
    cardHeightDp: Float,
    hasLocation: Boolean,
    hasTeacher: Boolean,
    courseFontScale: Float = 1f,
    systemFontScale: Float = 1f
): WeekWidgetCourseTextMetrics {
    val tiny = cardHeightDp < 52f
    val compact = cardHeightDp < 78f
    val horizontalPadding = if (cardWidthDp < 54f) 4 else 5
    val verticalPadding = when {
        tiny -> 1
        compact -> 2
        else -> 3
    }
    val effectiveScale = courseFontScale.coerceIn(0.80f, 1.35f) /
        systemFontScale.coerceAtLeast(1f)
    val nameBase = if (tiny) 9.5f else if (compact) 10.4f else 11.4f
    val locationBase = if (tiny) 8.7f else if (compact) 9.4f else 10.2f
    val nameLineHeight = (if (tiny) 8.9f else if (compact) 9.8f else 10.7f) * effectiveScale
    val locationLineHeight = (if (tiny) 8.6f else if (compact) 9.2f else 10f) * effectiveScale
    val teacherLineHeight = 8.5f * effectiveScale
    val locationMaxLines = when {
        !hasLocation -> 0
        cardHeightDp >= 150f -> 4
        cardHeightDp >= 96f -> 2
        else -> 1
    }
    val showTeacher = hasTeacher && cardHeightDp >= 52f
    val locationReserve = if (locationMaxLines > 0) locationLineHeight * locationMaxLines else 0f
    val teacherReserve = if (showTeacher) teacherLineHeight else 0f
    val centerReserve = maxOf(locationReserve, teacherReserve) + if (hasLocation || showTeacher) 1f else 0f
    val availableNameHeight = (
        cardHeightDp - verticalPadding * 2f - centerReserve * 2f
    ).coerceAtLeast(nameLineHeight)
    val appMaximumNameLines = when {
        cardHeightDp >= 150f -> 12
        cardHeightDp >= 112f -> 9
        cardHeightDp >= 78f -> 6
        else -> 4
    }
    val nameMaxLines = floor(availableNameHeight / nameLineHeight.coerceAtLeast(1f))
        .toInt()
        .coerceIn(1, appMaximumNameLines)
    return WeekWidgetCourseTextMetrics(
        horizontalPaddingDp = horizontalPadding,
        verticalPaddingDp = verticalPadding,
        nameSp = nameBase * effectiveScale,
        locationSp = locationBase * effectiveScale,
        teacherSp = 9f * effectiveScale,
        nameMaxLines = nameMaxLines,
        locationMaxLines = locationMaxLines,
        centerReserveDp = centerReserve,
        showTeacher = showTeacher
    )
}

internal object WeekScheduleWidgetRenderer {
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
        ids.forEach { id ->
            val type = WidgetAppearanceVariant.WEEK_SCHEDULE
            val appearance = app.widgetAppearanceRepository.get(type, WidgetDefaultAppearanceId)
            val size = widgetRenderSize(manager, id, type)
            val views = buildViews(context, state, appearance, size)
            runCatching { manager.updateAppWidget(id, views) }
                .onFailure { Log.e("ScheduleWidget", "Failed to update week widget $id", it) }
        }
    }

    internal fun buildViews(
        context: Context,
        state: AppState,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize,
        transparentBackground: Boolean = false
    ): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val displayWeek = scheduleWeekForDateOrNull(state.config, today)
            ?: effectiveCurrentWeek(state.config, today)
        val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
        val weekBuckets = weekCourseBuckets(state.courses, displayWeek, state.config, today)
        // A cancelled day keeps its cards in the app as a greyed reminder, but the widget only
        // shows classes that actually take place.
        val widgetCourses = weekBuckets.visibleCourses.filter { it.weekday !in weekBuckets.cancelledWeekdays }
        val weekdays = visibleWeekdaysForBuckets(weekBuckets, state.config.hideEmptyWeekends)
        val dark = MiuixTodayWidgetRenderer.usesDarkTheme(context, state.config)
        val surface = createExpandedWidgetSurface(
            context,
            appearance,
            size,
            dark,
            transparentBackground
        )
        val blurred = createCardBlur(surface, state)
        val metrics = weekWidgetLayoutMetrics(
            size,
            context.resources.configuration.fontScale
        )
        val periodWindow = weekWidgetPeriodWindow(state.periods, size, now)
        val scrollable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val surfacePeriodWindow = if (scrollable) {
            WeekWidgetPeriodWindow(0, minOf(5, state.periods.size))
        } else {
            periodWindow
        }
        val courseColors = WidgetCourseColors.assignments(context, state, dark)
        drawWeekScheduleSurfaces(
            bitmap = surface.output,
            blurred = blurred,
            state = state,
            size = size,
            weekStart = weekStart,
            today = today,
            weekdays = weekdays,
            visibleCourses = widgetCourses,
            surface = surface,
            metrics = metrics,
            periodWindow = surfacePeriodWindow,
            courseColors = courseColors,
            includeCourseSurfaces = !scrollable
        )
        recycleSurface(surface, blurred)
        val bitmap = finishExpandedWidgetBitmap(surface, transparentBackground)

        val layoutId = if (scrollable) {
            R.layout.widget_week_schedule_adaptive_v8
        } else {
            R.layout.widget_week_schedule_adaptive_v7
        }
        return RemoteViews(context.packageName, layoutId).apply {
            setImageViewResource(R.id.widget_app_icon, currentIconResId(context))
            setInt(
                R.id.widget_week_root,
                "setBackgroundResource",
                if (surface.dark) R.drawable.widget_today_background_dark else R.drawable.widget_today_background
            )
            if (transparentBackground) {
                setInt(R.id.widget_week_root, "setBackgroundColor", Color.TRANSPARENT)
                setInt(R.id.widget_week_image, "setBackgroundColor", Color.TRANSPARENT)
            }
            setImageViewBitmap(R.id.widget_week_image, bitmap)
            if (scrollable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyWeekScrollableContent(
                    context = context,
                    state = state,
                    size = size,
                    displayWeek = displayWeek,
                    weekStart = weekStart,
                    today = today,
                    now = now,
                    weekdays = weekdays,
                    visibleCourses = widgetCourses,
                    surface = surface,
                    metrics = metrics,
                    courseColors = courseColors
                )
            } else {
                applyWeekNativeContent(
                    context = context,
                    state = state,
                    size = size,
                    displayWeek = displayWeek,
                    weekStart = weekStart,
                    today = today,
                    now = now,
                    weekdays = weekdays,
                    visibleCourses = widgetCourses,
                    surface = surface,
                    metrics = metrics,
                    periodWindow = periodWindow,
                    courseColors = courseColors
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setWidgetCornerRadius(R.id.widget_week_root, 18)
                setWidgetCornerRadius(R.id.widget_week_image, 18)
            }
            setOnClickPendingIntent(R.id.widget_week_root, openAppPendingIntent(context))
        }
    }

    private fun RemoteViews.applyWeekNativeContent(
        context: Context,
        state: AppState,
        size: WidgetRenderSize,
        displayWeek: Int,
        weekStart: LocalDate,
        today: LocalDate,
        now: LocalTime,
        weekdays: List<Int>,
        visibleCourses: List<CourseEntity>,
        surface: ExpandedWidgetSurface,
        metrics: WeekWidgetLayoutMetrics,
        periodWindow: WeekWidgetPeriodWindow,
        courseColors: Map<String, Int>
    ) {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        fun id(name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)
        val periodIndexes = state.periods.map(PeriodEntity::periodIndex)
        val currentPeriodIndex = weekWidgetCurrentPeriodIndex(state.periods, now)
        setViewPadding(
            R.id.widget_week_content,
            px(metrics.paddingDp),
            px(metrics.paddingDp),
            px(metrics.paddingDp),
            px(metrics.paddingDp)
        )
        setWidgetHeight(R.id.widget_week_header, metrics.headerHeightDp)
        setWidgetHeight(R.id.widget_week_day_header, metrics.weekdayHeaderHeightDp)
        setWidgetWidth(R.id.widget_week_period_header_spacer, metrics.periodLabelWidthDp)
        setTextViewText(R.id.widget_week_title, "周视图课程表")
        setTextViewText(R.id.widget_week_meta, "第${displayWeek}周")
        setTextColor(R.id.widget_week_title, surface.primary)
        setTextColor(R.id.widget_week_meta, surface.secondary)
        setWidgetTextSize(R.id.widget_week_title, (17.5f * metrics.textScale).coerceAtLeast(12f))
        setWidgetTextSize(R.id.widget_week_meta, (15f * metrics.textScale).coerceAtLeast(10f))

        val visibleDays = weekdays.toSet()
        (1..7).forEach { weekday ->
            val dayContainer = id("widget_week_day_container_$weekday")
            val dayName = id("widget_week_day_name_$weekday")
            val dayDate = id("widget_week_day_date_$weekday")
            val visible = weekday in visibleDays
            setViewVisibility(dayContainer, if (visible) View.VISIBLE else View.GONE)
            if (visible) {
                val date = weekStart.plusDays((weekday - 1).toLong())
                setTextViewText(dayName, arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[weekday - 1])
                setTextViewText(dayDate, date.dayOfMonth.toString())
                setWidgetTextSize(dayName, (12.5f * metrics.textScale).coerceAtLeast(9.7f))
                setWidgetTextSize(dayDate, (10f * metrics.textScale).coerceAtLeast(7.8f))
                val isToday = date == today
                setInt(
                    dayContainer,
                    "setBackgroundResource",
                    android.R.color.transparent
                )
                setTextColor(dayName, if (isToday) Color.WHITE else surface.primary)
                setTextColor(
                    dayDate,
                    if (isToday) surface.accent else surface.secondary
                )
            }
        }

        val visiblePositionsByCourse = visibleCourses.associateWith { course ->
            course.periods
                .mapNotNull(periodIndexes::indexOf)
                .filter { it in periodWindow.firstPosition until periodWindow.lastExclusive }
                .distinct()
                .sorted()
        }
        val timelineTopDp = metrics.paddingDp + metrics.headerHeightDp + 6f +
            metrics.weekdayHeaderHeightDp
        val timelineBottomDp = size.heightDp - metrics.paddingDp - 6f
        val rowHeightDp = if (periodWindow.count > 0) {
            ((timelineBottomDp - timelineTopDp) / periodWindow.count.toFloat()).coerceAtLeast(8f)
        } else {
            8f
        }
        val gridWidthDp = (
            size.widthDp - metrics.paddingDp * 2f - 12f - metrics.periodLabelWidthDp
        ).coerceAtLeast(10f)
        val columnWidthDp = (gridWidthDp / weekdays.size.coerceAtLeast(1).toFloat())
            .coerceAtLeast(10f)

        repeat(12) { rowSlot ->
            val rowId = id("widget_week_row_${rowSlot + 1}")
            val visibleRow = rowSlot < periodWindow.count
            setViewVisibility(rowId, if (visibleRow) View.VISIBLE else View.GONE)
            if (!visibleRow) return@repeat
            val absolutePosition = periodWindow.firstPosition + rowSlot
            val periodIndex = periodIndexes.getOrNull(absolutePosition) ?: return@repeat
            val periodLabel = id("widget_week_period_${rowSlot + 1}")
            setWidgetWidth(periodLabel, metrics.periodLabelWidthDp)
            setTextViewText(periodLabel, periodIndex.toString())
            val isCurrentPeriod = periodIndex == currentPeriodIndex
            setInt(
                periodLabel,
                "setBackgroundResource",
                if (isCurrentPeriod) {
                    R.drawable.widget_week_current_period_pill
                } else {
                    android.R.color.transparent
                }
            )
            setTextColor(periodLabel, if (isCurrentPeriod) Color.WHITE else surface.secondary)
            setWidgetTextSize(periodLabel, (8.5f * metrics.textScale).coerceAtLeast(6.8f))
            (1..7).forEach { weekday ->
                val cell = id("widget_week_cell_${rowSlot + 1}_$weekday")
                val name = id("widget_week_cell_name_${rowSlot + 1}_$weekday")
                val detail = id("widget_week_cell_detail_${rowSlot + 1}_$weekday")
                val teacher = id("widget_week_cell_teacher_${rowSlot + 1}_$weekday")
                val dayVisible = weekday in visibleDays
                setViewVisibility(cell, if (dayVisible) View.VISIBLE else View.GONE)
                if (!dayVisible) return@forEach
                setWidgetHeight(cell, rowHeightDp.roundToInt())
                val starting = visibleCourses.filter { course ->
                    course.weekday == weekday &&
                        periodIndex in course.periods &&
                        visiblePositionsByCourse[course]?.firstOrNull() == absolutePosition
                }
                if (starting.isEmpty()) {
                    setTextViewText(name, "")
                    setTextViewText(detail, "")
                    setTextViewText(teacher, "")
                    return@forEach
                }
                val first = starting.first()
                val positions = starting
                    .flatMap { visiblePositionsByCourse[it].orEmpty() }
                    .distinct()
                    .sorted()
                val span = positions
                    .takeIf { it.isNotEmpty() }
                    ?.let { it.last() - it.first() + 1 }
                    ?.coerceAtLeast(1)
                    ?: 1
                val cardHeightDp = rowHeightDp * span
                val cardWidthDp = if (starting.size > 1) {
                    (
                        columnWidthDp - (starting.size - 1) * metrics.columnGapDp
                    ) / starting.size.toFloat()
                } else {
                    columnWidthDp
                }.coerceAtLeast(8f)
                val primary = if (state.config.courseCardGlassEnabled) {
                    surface.primary
                } else {
                    readableWidgetTextColor(
                        WidgetCourseColors.color(state.config, first, courseColors)
                    )
                }
                val hasConflict = starting.size > 1
                val detailText = if (hasConflict) "课程冲突" else first.location.orEmpty()
                val teacherText = if (hasConflict) "" else first.teacher.orEmpty()
                val textMetrics = weekWidgetCourseTextMetrics(
                    cardWidthDp = cardWidthDp,
                    cardHeightDp = cardHeightDp,
                    hasLocation = detailText.isNotBlank(),
                    hasTeacher = teacherText.isNotBlank(),
                    courseFontScale = state.config.courseCardFontScale,
                    systemFontScale = context.resources.configuration.fontScale
                )
                setWidgetHeight(cell, cardHeightDp.roundToInt())
                setViewPadding(
                    cell,
                    px(textMetrics.horizontalPaddingDp),
                    px(textMetrics.verticalPaddingDp),
                    px(textMetrics.horizontalPaddingDp),
                    px(textMetrics.verticalPaddingDp)
                )
                setTextViewText(name, starting.joinToString(" / ") { it.name })
                setTextViewText(detail, detailText)
                setTextViewText(teacher, teacherText)
                setViewVisibility(detail, if (detailText.isNotBlank()) View.VISIBLE else View.GONE)
                setViewVisibility(
                    teacher,
                    if (textMetrics.showTeacher && teacherText.isNotBlank()) View.VISIBLE else View.GONE
                )
                setTextColor(name, primary)
                setTextColor(detail, withAlpha(primary, 199))
                setTextColor(teacher, withAlpha(primary, 148))
                setWidgetTextSize(name, textMetrics.nameSp)
                setWidgetTextSize(detail, textMetrics.locationSp)
                setWidgetTextSize(teacher, textMetrics.teacherSp)
                setInt(detail, "setMaxLines", textMetrics.locationMaxLines.coerceAtLeast(1))
                setInt(name, "setMaxLines", textMetrics.nameMaxLines)
                val centerPadding = (textMetrics.centerReserveDp * density).roundToInt()
                setViewPadding(name, 0, centerPadding, 0, centerPadding)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun RemoteViews.applyWeekScrollableContent(
        context: Context,
        state: AppState,
        size: WidgetRenderSize,
        displayWeek: Int,
        weekStart: LocalDate,
        today: LocalDate,
        now: LocalTime,
        weekdays: List<Int>,
        visibleCourses: List<CourseEntity>,
        surface: ExpandedWidgetSurface,
        metrics: WeekWidgetLayoutMetrics,
        courseColors: Map<String, Int>
    ) {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        fun id(name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)
        setViewPadding(
            R.id.widget_week_content,
            px(metrics.paddingDp),
            px(metrics.paddingDp),
            px(metrics.paddingDp),
            px(metrics.paddingDp)
        )
        setWidgetHeight(R.id.widget_week_header, metrics.headerHeightDp)
        setWidgetHeight(R.id.widget_week_day_header, metrics.weekdayHeaderHeightDp)
        setWidgetWidth(R.id.widget_week_period_header_spacer, metrics.periodLabelWidthDp)
        setTextViewText(R.id.widget_week_title, "周视图课程表")
        setTextViewText(R.id.widget_week_meta, "第${displayWeek}周")
        setTextColor(R.id.widget_week_title, surface.primary)
        setTextColor(R.id.widget_week_meta, surface.secondary)
        setWidgetTextSize(R.id.widget_week_title, (17.5f * metrics.textScale).coerceAtLeast(12f))
        setWidgetTextSize(R.id.widget_week_meta, (15f * metrics.textScale).coerceAtLeast(10f))

        val visibleDays = weekdays.toSet()
        (1..7).forEach { weekday ->
            val dayName = id("widget_week_day_name_$weekday")
            val visible = weekday in visibleDays
            setViewVisibility(dayName, if (visible) View.VISIBLE else View.GONE)
            if (visible) {
                val isToday = weekStart.plusDays((weekday - 1).toLong()) == today
                setTextViewText(
                    dayName,
                    arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[weekday - 1]
                )
                setWidgetTextSize(dayName, (12.5f * metrics.textScale).coerceAtLeast(9.7f))
                setTextColor(dayName, if (isToday) Color.WHITE else surface.primary)
            }
        }

        val timelineTopDp = metrics.paddingDp + metrics.headerHeightDp + 6f +
            metrics.weekdayHeaderHeightDp
        val timelineBottomDp = size.heightDp - metrics.paddingDp - 6f
        val rowHeightDp = ((timelineBottomDp - timelineTopDp) / 5f).coerceAtLeast(24f)
        val rowWidthDp = (
            size.widthDp - metrics.paddingDp * 2f - 12f
        ).coerceAtLeast(96f)
        val collectionBuilder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        state.periods.take(12).forEachIndexed { position, period ->
            val row = RemoteViews(context.packageName, R.layout.widget_week_scroll_row).apply {
                setWidgetHeight(R.id.widget_week_scroll_row_root, rowHeightDp.roundToInt())
                setImageViewBitmap(
                    R.id.widget_week_scroll_row_image,
                    renderWeekScrollableRow(
                        state = state,
                        rowWidthDp = rowWidthDp,
                        rowHeightDp = rowHeightDp,
                        position = position,
                        weekdays = weekdays,
                        visibleCourses = visibleCourses,
                        surface = surface,
                        metrics = metrics,
                        courseColors = courseColors,
                        now = now
                    )
                )
                setOnClickFillInIntent(R.id.widget_week_scroll_row_root, Intent())
            }
            collectionBuilder.addItem(period.periodIndex.toLong(), row)
        }
        setRemoteAdapter(R.id.widget_week_period_list, collectionBuilder.build())
        setPendingIntentTemplate(R.id.widget_week_period_list, openAppPendingIntent(context))
        setScrollPosition(
            R.id.widget_week_period_list,
            weekWidgetInitialScrollPosition(state.periods.take(12), now, 5)
        )
    }

    private fun renderWeekScrollableRow(
        state: AppState,
        rowWidthDp: Float,
        rowHeightDp: Float,
        position: Int,
        weekdays: List<Int>,
        visibleCourses: List<CourseEntity>,
        surface: ExpandedWidgetSurface,
        metrics: WeekWidgetLayoutMetrics,
        courseColors: Map<String, Int>,
        now: LocalTime
    ): Bitmap {
        val renderScale = 1.6f
        val bitmap = Bitmap.createBitmap(
            (rowWidthDp * renderScale).roundToInt().coerceAtLeast(1),
            (rowHeightDp * renderScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(renderScale, renderScale)
        val periodIndexes = state.periods.map(PeriodEntity::periodIndex)
        val period = state.periods.getOrNull(position) ?: return bitmap
        val currentPeriod = weekWidgetCurrentPeriodIndex(state.periods, now)
        val labelRect = RectF(0f, 0f, metrics.periodLabelWidthDp.toFloat(), rowHeightDp)
        val periodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = if (period.periodIndex == currentPeriod) Color.WHITE else surface.secondary
            textSize = 10.2f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        if (period.periodIndex == currentPeriod) {
            val pillSize = minOf(22f, rowHeightDp - 5f).coerceAtLeast(14f)
            val pill = RectF(
                labelRect.centerX() - pillSize / 2f,
                labelRect.centerY() - pillSize / 2f,
                labelRect.centerX() + pillSize / 2f,
                labelRect.centerY() + pillSize / 2f
            )
            canvas.drawPath(
                continuousRoundedRectPath(pill, pillSize * 0.38f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface.accent }
            )
        }
        canvas.drawText(
            period.periodIndex.toString(),
            labelRect.centerX(),
            centeredTextBaseline(labelRect, periodPaint),
            periodPaint
        )

        if (weekdays.isEmpty()) return bitmap
        val gridLeftDp = metrics.periodLabelWidthDp.toFloat()
        val columnWidthDp = ((rowWidthDp - gridLeftDp) / weekdays.size.toFloat())
            .coerceAtLeast(10f)
        weekdays.forEachIndexed { dayIndex, weekday ->
            val groups = buildWeekConflictGroups(
                visibleCourses.filter { it.weekday == weekday },
                periodIndexes
            )
            val dayLeftDp = gridLeftDp + dayIndex * columnWidthDp
            groups.forEach { group ->
                val lanes = group.segments.size.coerceAtLeast(1)
                group.segments.forEachIndexed { lane, segment ->
                    if (position !in segment.startPosition..segment.endPosition) {
                        return@forEachIndexed
                    }
                    val laneGapDp = 1.4f
                    val laneWidthDp = (
                        columnWidthDp - laneGapDp * (lanes - 1).coerceAtLeast(0)
                    ) / lanes
                    val left = dayLeftDp + lane * (laneWidthDp + laneGapDp) + 1.2f
                    val right = left + laneWidthDp - 2.4f
                    val top = (segment.startPosition - position) * rowHeightDp + 1.2f
                    val bottom = (segment.endPosition - position + 1) * rowHeightDp - 1.2f
                    if (right <= left || bottom <= top) return@forEachIndexed
                    val fullCard = RectF(left, top, right, bottom)
                    val radius = minOf(18f, fullCard.width() * 0.46f, fullCard.height() * 0.42f)
                        .coerceAtLeast(5f)
                    val path = continuousRoundedRectPath(fullCard, radius)
                    val courseColor = WidgetCourseColors.color(
                        state.config,
                        segment.course,
                        courseColors
                    )
                    val opaque = surface.custom == null
                    val fillAlpha = if (opaque) {
                        255
                    } else if (state.config.courseCardGlassEnabled) {
                        196
                    } else {
                        (state.config.cardAlpha.coerceIn(0.86f, 1f) * 255f).roundToInt()
                    }
                    canvas.drawPath(
                        path,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                            color = withAlpha(courseColor, fillAlpha)
                        }
                    )
                    if (!opaque) {
                        canvas.drawPath(
                            path,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = 0.8f
                                color = withAlpha(if (surface.dark) Color.WHITE else Color.BLACK, 42)
                            }
                        )
                    }

                    val textColor = if (opaque) {
                        readableWidgetTextColor(courseColor)
                    } else {
                        surface.primary
                    }
                    val availableWidth = (fullCard.width() - 8f).coerceAtLeast(2f)
                    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        color = textColor
                        textSize = 11.8f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        textAlign = Paint.Align.CENTER
                    }
                    val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        color = withAlpha(textColor, 205)
                        textSize = 9.4f
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        textAlign = Paint.Align.CENTER
                    }
                    val teacherPaint = TextPaint(detailPaint).apply {
                        color = withAlpha(textColor, 170)
                        textSize = 9f
                    }
                    canvas.withClip(path) {
                        val location = segment.course.location.orEmpty()
                        if (location.isNotBlank()) {
                            drawText(
                                ellipsizedWidgetText(location, detailPaint, availableWidth),
                                fullCard.centerX(),
                                fullCard.top + 4f - detailPaint.fontMetrics.ascent,
                                detailPaint
                            )
                        }
                        drawText(
                            ellipsizedWidgetText(segment.course.name, namePaint, availableWidth),
                            fullCard.centerX(),
                            centeredTextBaseline(fullCard, namePaint),
                            namePaint
                        )
                        val teacher = segment.course.teacher.orEmpty()
                        if (teacher.isNotBlank() && fullCard.height() >= 48f) {
                            drawText(
                                ellipsizedWidgetText(teacher, teacherPaint, availableWidth),
                                fullCard.centerX(),
                                fullCard.bottom - 4f - teacherPaint.fontMetrics.descent,
                                teacherPaint
                            )
                        }
                    }
                }
            }
        }
        return bitmap
    }

    private fun drawWeekScheduleSurfaces(
        bitmap: Bitmap,
        blurred: Bitmap?,
        state: AppState,
        size: WidgetRenderSize,
        weekStart: LocalDate,
        today: LocalDate,
        weekdays: List<Int>,
        visibleCourses: List<CourseEntity>,
        surface: ExpandedWidgetSurface,
        metrics: WeekWidgetLayoutMetrics,
        periodWindow: WeekWidgetPeriodWindow,
        courseColors: Map<String, Int>,
        includeCourseSurfaces: Boolean = true
    ) {
        val canvas = Canvas(bitmap)
        val sx = bitmap.width / size.widthDp.toFloat()
        val sy = bitmap.height / size.heightDp.toFloat()
        val unit = minOf(sx, sy)
        fun x(dp: Float) = dp * sx
        fun y(dp: Float) = dp * sy
        fun u(dp: Float) = dp * unit

        val padding = metrics.paddingDp.toFloat()
        val bodyTopDp = padding + metrics.headerHeightDp + 2f
        val bodyRect = RectF(
            x(padding),
            y(bodyTopDp),
            x(size.widthDp - padding),
            y(size.heightDp - padding)
        )
        if (bodyRect.width() <= u(40f) || bodyRect.height() <= u(48f)) return
        drawNeutralGlassSurface(
            canvas,
            blurred,
            bodyRect,
            u(18f),
            sx,
            sy,
            surface.dark,
            strong = true,
            showOutline = weekWidgetPresetOutlineEnabled(surface.custom != null),
        )
        if (periodWindow.count <= 0 || weekdays.isEmpty()) return

        val innerLeftDp = padding + 6f
        val innerRightDp = size.widthDp - padding - 6f
        val weekdayTopDp = bodyTopDp + 4f
        val timelineTopDp = weekdayTopDp + metrics.weekdayHeaderHeightDp
        val timelineBottomDp = size.heightDp - padding - 6f
        if (timelineBottomDp <= timelineTopDp) return
        val gridLeftDp = innerLeftDp + metrics.periodLabelWidthDp
        val gridRightDp = innerRightDp
        val columnWidthDp = ((gridRightDp - gridLeftDp) / weekdays.size.toFloat())
            .coerceAtLeast(10f)
        val rowHeightDp = ((timelineBottomDp - timelineTopDp) / periodWindow.count.toFloat())
            .coerceAtLeast(8f)
        val periodIndexes = state.periods.map(PeriodEntity::periodIndex)
        weekdays.forEachIndexed { dayIndex, weekday ->
            val leftDp = gridLeftDp + dayIndex * columnWidthDp
            val pillHeightDp = minOf(20f, metrics.weekdayHeaderHeightDp * 0.57f)
            val pillTopDp = if (includeCourseSurfaces) {
                weekdayTopDp + 2.5f
            } else {
                weekdayTopDp + (metrics.weekdayHeaderHeightDp - pillHeightDp) / 2f
            }
            val pillRect = RectF(
                x(leftDp + 2f),
                y(pillTopDp),
                x(leftDp + columnWidthDp - 2f),
                y(pillTopDp + pillHeightDp)
            )
            if (pillRect.width() <= u(6f) || pillRect.height() <= u(8f)) {
                return@forEachIndexed
            }
            val isToday = weekStart.plusDays((weekday - 1).toLong()) == today
            val pillColor = if (isToday) {
                surface.accent
            } else {
                withAlpha(surface.primary, if (surface.dark) 34 else 20)
            }
            canvas.drawPath(
                continuousRoundedRectPath(pillRect, pillRect.height() / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor }
            )
        }
        if (!includeCourseSurfaces) return
        weekdays.forEachIndexed { dayIndex, weekday ->
            val groups = buildWeekConflictGroups(
                visibleCourses.filter { it.weekday == weekday },
                periodIndexes
            )
            val dayLeftDp = gridLeftDp + dayIndex * columnWidthDp
            groups.forEach { group ->
                val lanes = group.segments.size.coerceAtLeast(1)
                group.segments.forEachIndexed { lane, segment ->
                    if (
                        segment.endPosition < periodWindow.firstPosition ||
                        segment.startPosition >= periodWindow.lastExclusive
                    ) return@forEachIndexed
                    val laneGapDp = 1f
                    val laneWidthDp = (
                        columnWidthDp - laneGapDp * (lanes - 1).coerceAtLeast(0)
                    ) / lanes
                    val visibleStart = maxOf(segment.startPosition, periodWindow.firstPosition)
                    val visibleEnd = minOf(segment.endPosition + 1, periodWindow.lastExclusive)
                    val leftDp = dayLeftDp + lane * (laneWidthDp + laneGapDp) + 1f
                    val rightDp = leftDp + laneWidthDp - 2f
                    val topDp = timelineTopDp +
                        (visibleStart - periodWindow.firstPosition) * rowHeightDp + 1f
                    val bottomDp = timelineTopDp +
                        (visibleEnd - periodWindow.firstPosition) * rowHeightDp - 1f
                    if (rightDp <= leftDp || bottomDp <= topDp) return@forEachIndexed
                    val region = RectF(x(leftDp), y(topDp), x(rightDp), y(bottomDp))
                    val radius = minOf(u(16.5f), region.width() * 0.42f, region.height() * 0.40f)
                        .coerceAtLeast(u(4.5f))
                    drawCourseGlassSurface(
                        canvas,
                        blurred,
                        region,
                        radius,
                        sx,
                        sy,
                        surface.dark,
                        state,
                        WidgetCourseColors.color(state.config, segment.course, courseColors),
                        showOutline = weekWidgetPresetOutlineEnabled(surface.custom != null),
                        forceOpaque = surface.custom == null
                    )
                }
            }
        }
    }

    private fun drawWeekSchedule(
        bitmap: Bitmap,
        blurred: Bitmap?,
        state: AppState,
        size: WidgetRenderSize,
        displayWeek: Int,
        weekStart: LocalDate,
        today: LocalDate,
        now: LocalTime,
        weekdays: List<Int>,
        visibleCourses: List<CourseEntity>,
        surface: ExpandedWidgetSurface,
        metrics: WeekWidgetLayoutMetrics,
        periodWindow: WeekWidgetPeriodWindow,
        courseColors: Map<String, Int>
    ) {
        val canvas = Canvas(bitmap)
        val sx = bitmap.width / size.widthDp.toFloat()
        val sy = bitmap.height / size.heightDp.toFloat()
        val unit = minOf(sx, sy)
        fun x(dp: Float) = dp * sx
        fun y(dp: Float) = dp * sy
        fun u(dp: Float) = dp * unit

        val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val padding = metrics.paddingDp.toFloat()
        val headerRect = RectF(
            x(padding),
            y(padding),
            x(size.widthDp - padding),
            y(padding + metrics.headerHeightDp)
        )
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.primary
            textSize = u(17.5f * metrics.textScale)
            typeface = medium
            textAlign = Paint.Align.LEFT
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.secondary
            textSize = u(15f * metrics.textScale)
            typeface = regular
            textAlign = Paint.Align.RIGHT
        }
        val periodIndexes = state.periods.map { it.periodIndex }
        val metaText = "第${displayWeek}周"
        canvas.drawText(
            metaText,
            headerRect.right,
            centeredTextBaseline(headerRect, metaPaint),
            metaPaint
        )
        val titleWidth = (
            headerRect.width() - metaPaint.measureText(metaText) - u(8f)
        ).coerceAtLeast(u(42f))
        canvas.drawText(
            ellipsizedWidgetText("周视图课程表", titlePaint, titleWidth),
            headerRect.left,
            centeredTextBaseline(headerRect, titlePaint),
            titlePaint
        )

        val bodyTopDp = padding + metrics.headerHeightDp + 2f
        val bodyRect = RectF(
            x(padding),
            y(bodyTopDp),
            x(size.widthDp - padding),
            y(size.heightDp - padding)
        )
        if (bodyRect.width() <= u(40f) || bodyRect.height() <= u(48f)) return
        drawNeutralGlassSurface(
            canvas,
            blurred,
            bodyRect,
            u(16f),
            sx,
            sy,
            surface.dark,
            strong = true,
            showOutline = weekWidgetPresetOutlineEnabled(surface.custom != null),
        )

        val innerLeftDp = padding + 6f
        val innerRightDp = size.widthDp - padding - 6f
        val weekdayTopDp = bodyTopDp + 4f
        val timelineTopDp = weekdayTopDp + metrics.weekdayHeaderHeightDp
        val timelineBottomDp = size.heightDp - padding - 6f
        if (
            periodWindow.count <= 0 || weekdays.isEmpty() ||
            timelineBottomDp <= timelineTopDp
        ) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = surface.secondary
                textSize = u(11f * metrics.textScale)
                typeface = regular
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("暂无课程数据", bodyRect.centerX(), centeredTextBaseline(bodyRect, emptyPaint), emptyPaint)
            return
        }

        val gridLeftDp = innerLeftDp + metrics.periodLabelWidthDp
        val gridRightDp = innerRightDp
        val totalGapDp = metrics.columnGapDp * (weekdays.size - 1).coerceAtLeast(0)
        val columnWidthDp = (
            (gridRightDp - gridLeftDp - totalGapDp) / weekdays.size.toFloat()
        ).coerceAtLeast(10f)
        val rowHeightDp = (
            (timelineBottomDp - timelineTopDp) / periodWindow.count.toFloat()
        ).coerceAtLeast(8f)
        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.primary
            textSize = u(12.5f * metrics.textScale)
            typeface = medium
            textAlign = Paint.Align.CENTER
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.secondary
            textSize = u(10f * metrics.textScale)
            typeface = regular
            textAlign = Paint.Align.CENTER
        }
        val periodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.secondary
            textSize = u(8.8f * metrics.textScale)
            typeface = medium
            textAlign = Paint.Align.CENTER
        }
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        weekdays.forEachIndexed { index, weekday ->
            val leftDp = gridLeftDp + index * (columnWidthDp + metrics.columnGapDp)
            val rightDp = leftDp + columnWidthDp
            val date = weekStart.plusDays((weekday - 1).toLong())
            val dayHeaderRect = RectF(
                x(leftDp + 0.5f),
                y(weekdayTopDp + 1f),
                x(rightDp - 0.5f),
                y(timelineTopDp - 3f)
            )
            val dayNameRect = RectF(
                dayHeaderRect.left,
                dayHeaderRect.top,
                dayHeaderRect.right,
                dayHeaderRect.top + dayHeaderRect.height() * 0.58f
            )
            val dateRect = RectF(
                dayHeaderRect.left,
                dayNameRect.bottom - u(1f),
                dayHeaderRect.right,
                dayHeaderRect.bottom
            )
            val pillRect = RectF(
                dayNameRect.left + u(1.5f),
                dayNameRect.top + u(0.5f),
                dayNameRect.right - u(1.5f),
                dayNameRect.bottom - u(0.5f)
            )
            canvas.drawPath(
                continuousRoundedRectPath(pillRect, pillRect.height() / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (date == today) {
                        surface.accent
                    } else {
                        withAlpha(surface.primary, if (surface.dark) 34 else 20)
                    }
                }
            )
            if (date == today) {
                dayPaint.color = Color.WHITE
                datePaint.color = surface.accent
            } else {
                dayPaint.color = surface.primary
                datePaint.color = surface.secondary
            }
            canvas.drawText(
                dayNames[(weekday - 1).coerceIn(0, 6)],
                dayNameRect.centerX(),
                centeredTextBaseline(dayNameRect, dayPaint),
                dayPaint
            )
            canvas.drawText(
                date.dayOfMonth.toString(),
                dateRect.centerX(),
                centeredTextBaseline(dateRect, datePaint),
                datePaint
            )
        }

        val currentPeriodPosition = weekWidgetCurrentPeriodIndex(state.periods, now)
            ?.let(periodIndexes::indexOf)
            ?.takeIf { it >= 0 }
        repeat(periodWindow.count) { visibleRow ->
            val absolutePosition = periodWindow.firstPosition + visibleRow
            val rowTop = y(timelineTopDp + visibleRow * rowHeightDp)
            val rowBottom = y(timelineTopDp + (visibleRow + 1) * rowHeightDp)
            val labelRect = RectF(x(innerLeftDp), rowTop, x(gridLeftDp - 4f), rowBottom)
            if (absolutePosition == currentPeriodPosition) {
                val pill = RectF(
                    labelRect.centerX() - u(8f),
                    labelRect.centerY() - u(8f),
                    labelRect.centerX() + u(8f),
                    labelRect.centerY() + u(8f)
                )
                canvas.drawContinuousRoundRect(
                    pill,
                    u(6f),
                    u(6f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 132, 255) }
                )
                periodPaint.color = Color.WHITE
            } else {
                periodPaint.color = surface.secondary
            }
            canvas.drawText(
                periodIndexes[absolutePosition].toString(),
                labelRect.centerX(),
                centeredTextBaseline(labelRect, periodPaint),
                periodPaint
            )
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = medium
            textAlign = Paint.Align.LEFT
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = regular
            textAlign = Paint.Align.LEFT
        }
        weekdays.forEachIndexed { dayIndex, weekday ->
            val dayCourses = visibleCourses.filter { it.weekday == weekday }
            val groups = buildWeekConflictGroups(dayCourses, periodIndexes)
            val dayLeftDp = gridLeftDp + dayIndex * (columnWidthDp + metrics.columnGapDp)
            groups.forEach { group ->
                val lanes = group.segments.size.coerceAtLeast(1)
                group.segments.forEachIndexed { lane, segment ->
                    if (
                        segment.endPosition < periodWindow.firstPosition ||
                        segment.startPosition >= periodWindow.lastExclusive
                    ) return@forEachIndexed
                    val laneGapDp = 1f
                    val laneWidthDp = (
                        columnWidthDp - laneGapDp * (lanes - 1).coerceAtLeast(0)
                    ) / lanes
                    val visibleStart = maxOf(segment.startPosition, periodWindow.firstPosition)
                    val visibleEnd = minOf(segment.endPosition + 1, periodWindow.lastExclusive)
                    val leftDp = dayLeftDp + lane * (laneWidthDp + laneGapDp) + 0.8f
                    val rightDp = leftDp + laneWidthDp - 1.6f
                    val topDp = timelineTopDp +
                        (visibleStart - periodWindow.firstPosition) * rowHeightDp + 1f
                    val bottomDp = timelineTopDp +
                        (visibleEnd - periodWindow.firstPosition) * rowHeightDp - 1f
                    if (rightDp <= leftDp || bottomDp <= topDp) return@forEachIndexed
                    val region = RectF(x(leftDp), y(topDp), x(rightDp), y(bottomDp))
                    val radius = minOf(u(16.5f), region.width() * 0.42f, region.height() * 0.40f)
                        .coerceAtLeast(u(4.5f))
                    val courseColor = WidgetCourseColors.color(state.config, segment.course, courseColors)
                    drawCourseGlassSurface(
                        canvas,
                        blurred,
                        region,
                        radius,
                        sx,
                        sy,
                        surface.dark,
                        state,
                        courseColor,
                        showOutline = weekWidgetPresetOutlineEnabled(surface.custom != null),
                    )

                    val courseTextColor = if (state.config.courseCardGlassEnabled) {
                        surface.primary
                    } else {
                        readableWidgetTextColor(courseColor)
                    }
                    val inset = u(3.2f)
                    val availableWidth = region.width() - inset * 2f
                    if (availableWidth <= u(4f) || region.height() <= u(7f)) return@forEachIndexed
                    namePaint.color = courseTextColor
                    namePaint.textSize = u(10.2f * metrics.textScale)
                        .coerceAtMost(region.height() * 0.34f)
                    detailPaint.color = withAlpha(courseTextColor, 178)
                    detailPaint.textSize = u(7.8f * metrics.textScale)
                        .coerceAtMost(region.height() * 0.23f)
                    drawWeekCourseText(
                        canvas = canvas,
                        course = segment.course,
                        periods = state.periods,
                        region = region,
                        inset = inset,
                        availableWidth = availableWidth,
                        namePaint = namePaint,
                        detailPaint = detailPaint
                    )
                }
            }
        }
    }

    private fun drawWeekCourseText(
        canvas: Canvas,
        course: CourseEntity,
        periods: List<PeriodEntity>,
        region: RectF,
        inset: Float,
        availableWidth: Float,
        namePaint: TextPaint,
        detailPaint: TextPaint
    ) {
        val nameLineHeight = namePaint.fontSpacing.coerceAtLeast(1f)
        val canUseTwoNameLines = region.height() >= nameLineHeight * 2f + detailPaint.fontSpacing + inset * 2f
        val firstCount = namePaint.breakText(course.name, true, availableWidth, null)
            .coerceIn(0, course.name.length)
        val firstLine = if (canUseTwoNameLines && firstCount in 1 until course.name.length) {
            course.name.substring(0, firstCount)
        } else {
            ellipsizedWidgetText(course.name, namePaint, availableWidth)
        }
        var baseline = region.top + inset - namePaint.fontMetrics.ascent
        canvas.drawText(firstLine, region.left + inset, baseline, namePaint)
        var usedNameLines = 1
        if (canUseTwoNameLines && firstCount in 1 until course.name.length) {
            baseline += nameLineHeight
            canvas.drawText(
                ellipsizedWidgetText(course.name.substring(firstCount), namePaint, availableWidth),
                region.left + inset,
                baseline,
                namePaint
            )
            usedNameLines = 2
        }

        val start = courseStartTime(course, periods)?.format(WidgetTimeFormatter)
        val detail = course.location?.takeIf(String::isNotBlank) ?: start.orEmpty()
        val detailBaseline = region.top + inset + nameLineHeight * usedNameLines - detailPaint.fontMetrics.ascent
        if (detail.isNotBlank() && detailBaseline <= region.bottom - inset) {
            canvas.drawText(
                ellipsizedWidgetText(detail, detailPaint, availableWidth),
                region.left + inset,
                detailBaseline,
                detailPaint
            )
        }
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            2701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
