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
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.drawContinuousRoundRect
import com.xiaomanjun.sleepdownschedule.courseEndTime
import com.xiaomanjun.sleepdownschedule.courseStartTime
import com.xiaomanjun.sleepdownschedule.coursesForDate
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.widget.TodayTomorrowWidgetLayoutMetrics
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceEntity
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceVariant
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetBackgroundRenderer
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetBackgroundResult
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCourseColors
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetDefaultAppearanceId
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetRenderSize
import com.xiaomanjun.sleepdownschedule.feature.widget.todayTomorrowWidgetLayoutMetrics
import com.xiaomanjun.sleepdownschedule.feature.widget.widgetRenderSizes
import com.xiaomanjun.sleepdownschedule.feature.widget.widgetResponsiveViews
import com.xiaomanjun.sleepdownschedule.scheduleWeekForDateOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

internal fun remainingCoursesForTodayWidget(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    now: LocalTime
): List<CourseEntity> = courses.filter { course ->
    courseEndTime(course, periods)?.isAfter(now) != false
}

internal object TodayTomorrowWidgetRenderer {
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
            val type = WidgetAppearanceVariant.TODAY_TOMORROW
            val appearance = app.widgetAppearanceRepository.get(type, WidgetDefaultAppearanceId)
            val sizes = widgetRenderSizes(manager, id, type)
            val views = sizes.map { size -> size to buildViews(context, state, appearance, size) }
            val result = widgetResponsiveViews(views)
            runCatching { manager.updateAppWidget(id, result) }
                .onFailure { Log.e("ScheduleWidget", "Failed to update today/tomorrow widget $id", it) }
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
        val dates = listOf(today, today.plusDays(1))
        val metrics = todayTomorrowWidgetLayoutMetrics(
            size,
            context.resources.configuration.fontScale
        )
        val allCourses = dates.mapIndexed { index, date ->
            val courses = MiuixTodayWidgetRenderer.coursesForDate(state, date)
            if (index == 0) remainingCoursesForTodayWidget(courses, state.periods, now) else courses
        }
        val dark = MiuixTodayWidgetRenderer.usesDarkTheme(context, state.config)
        val shownCourses = allCourses.map { it.take(metrics.maxCoursesPerDay.coerceAtMost(6)) }
        val custom = WidgetBackgroundRenderer.render(
            context = context,
            appearance = appearance,
            size = size,
            courseCount = shownCourses.sumOf(List<CourseEntity>::size),
            darkMode = dark,
            dayCourseCounts = shownCourses.map(List<CourseEntity>::size),
            todayTomorrowMetrics = metrics
        )
        val globallyDark = custom?.darkBackground ?: dark
        val primary = custom?.header ?: if (globallyDark) Color.WHITE else Color.rgb(17, 17, 17)
        val secondary = custom?.headerSecondary ?: if (globallyDark) {
            Color.argb(170, 255, 255, 255)
        } else {
            Color.argb(150, 0, 0, 0)
        }
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val courseColors = WidgetCourseColors.assignments(context, state, dark)
        val week = scheduleWeekForDateOrNull(state.config, today)
            ?: effectiveCurrentWeek(state.config, today)

        return RemoteViews(context.packageName, R.layout.widget_today_tomorrow_adaptive_v4).apply {
            setImageViewResource(R.id.widget_app_icon, currentIconResId(context))
            setInt(
                R.id.widget_tt_root,
                "setBackgroundResource",
                if (globallyDark) R.drawable.widget_today_background_dark else R.drawable.widget_today_background
            )
            if (custom != null && !transparentBackground) {
                setViewVisibility(R.id.widget_tt_background_image, View.VISIBLE)
                setImageViewBitmap(R.id.widget_tt_background_image, custom.bitmap)
                setInt(R.id.widget_tt_root, "setBackgroundColor", Color.TRANSPARENT)
            } else {
                setViewVisibility(R.id.widget_tt_background_image, View.GONE)
            }
            if (transparentBackground) {
                setInt(R.id.widget_tt_root, "setBackgroundColor", Color.TRANSPARENT)
            }
            setViewPadding(
                R.id.widget_tt_content,
                px(metrics.horizontalPaddingDp),
                px(metrics.verticalPaddingDp),
                px(metrics.horizontalPaddingDp),
                px(metrics.verticalPaddingDp)
            )
            setWidgetHeight(R.id.widget_tt_header, metrics.headerHeightDp)
            setWidgetHeight(R.id.widget_tt_today_title, metrics.dayHeaderHeightDp)
            setWidgetHeight(R.id.widget_tt_tomorrow_title, metrics.dayHeaderHeightDp)
            setTextViewText(R.id.widget_tt_schedule, "今明课程")
            setTextViewText(R.id.widget_tt_meta, "第${week}周")
            setTextViewText(R.id.widget_tt_today_title, "今天 · ${shortChineseWeekday(today)}")
            setTextViewText(R.id.widget_tt_tomorrow_title, "明天 · ${shortChineseWeekday(dates.last())}")
            setTextColor(R.id.widget_tt_schedule, primary)
            setTextColor(R.id.widget_tt_meta, secondary)
            setTextColor(R.id.widget_tt_today_title, primary)
            setTextColor(R.id.widget_tt_tomorrow_title, primary)
            setTextColor(R.id.widget_tt_today_empty, secondary)
            setTextColor(R.id.widget_tt_tomorrow_empty, secondary)
            setWidgetTextSize(R.id.widget_tt_schedule, (17.5f * metrics.textScale).coerceAtLeast(12.5f))
            setWidgetTextSize(R.id.widget_tt_meta, (14.5f * metrics.textScale).coerceAtLeast(10.5f))
            setWidgetTextSize(R.id.widget_tt_today_title, (13f * metrics.textScale).coerceAtLeast(10f))
            setWidgetTextSize(R.id.widget_tt_tomorrow_title, (13f * metrics.textScale).coerceAtLeast(10f))
            setWidgetTextSize(R.id.widget_tt_today_empty, (11.2f * metrics.textScale).coerceAtLeast(9f))
            setWidgetTextSize(R.id.widget_tt_tomorrow_empty, (11.2f * metrics.textScale).coerceAtLeast(9f))
            setInt(
                R.id.widget_tt_divider,
                "setBackgroundColor",
                if (globallyDark) Color.argb(30, 255, 255, 255) else Color.argb(24, 0, 0, 0)
            )
            fillStaticDay(
                context = context,
                state = state,
                dayKey = "today",
                courses = shownCourses[0],
                contentOffset = 0,
                emptyId = R.id.widget_tt_today_empty,
                metrics = metrics,
                dark = globallyDark,
                custom = custom,
                courseColors = courseColors
            )
            fillStaticDay(
                context = context,
                state = state,
                dayKey = "tomorrow",
                courses = shownCourses[1],
                contentOffset = shownCourses[0].size,
                emptyId = R.id.widget_tt_tomorrow_empty,
                metrics = metrics,
                dark = globallyDark,
                custom = custom,
                courseColors = courseColors
            )
            setOnClickPendingIntent(R.id.widget_tt_root, openAppPendingIntent(context))
        }
    }

    private fun RemoteViews.fillStaticDay(
        context: Context,
        state: AppState,
        dayKey: String,
        courses: List<CourseEntity>,
        contentOffset: Int,
        emptyId: Int,
        metrics: TodayTomorrowWidgetLayoutMetrics,
        dark: Boolean,
        custom: WidgetBackgroundResult?,
        courseColors: Map<String, Int>
    ) {
        val courseBackground = if (dark) R.drawable.widget_course_background_compact_dark else R.drawable.widget_course_background_compact
        setViewVisibility(emptyId, if (courses.isEmpty()) View.VISIBLE else View.GONE)
        repeat(6) { index ->
            fun id(suffix: String): Int = context.resources.getIdentifier(
                "widget_tt_${dayKey}_${suffix}_${index + 1}",
                "id",
                context.packageName
            )
            val rowId = id("row")
            val backgroundId = id("background")
            val timeColumnId = id("time_column")
            val startId = id("start")
            val endId = id("end")
            val indicatorId = id("indicator")
            val nameId = id("name")
            val detailId = id("detail")
            val course = courses.getOrNull(index)
            setViewVisibility(rowId, if (course == null) View.GONE else View.VISIBLE)
            if (course == null) return@repeat
            setWidgetHeight(rowId, metrics.rowHeightDp)
            setWidgetCornerRadius(rowId, metrics.rowCornerRadiusDp)
            setWidgetWidth(timeColumnId, metrics.timeColumnWidthDp)
            applyWidgetCourseCardBackground(
                rowId = rowId,
                backgroundId = backgroundId,
                custom = custom,
                cardIndex = contentOffset + index,
                defaultBackground = courseBackground
            )
            setWidgetIndicatorHeight(
                indicatorId,
                (metrics.rowHeightDp - 12).coerceIn(12, 42)
            )
            setInt(
                indicatorId,
                "setColorFilter",
                WidgetCourseColors.color(state.config, course, courseColors)
            )
            setTextViewText(
                startId,
                courseStartTime(course, state.periods)?.format(WidgetTimeFormatter).orEmpty()
            )
            setTextViewText(
                endId,
                courseEndTime(course, state.periods)?.format(WidgetTimeFormatter).orEmpty()
            )
            setTextViewText(nameId, course.name)
            setTextViewText(detailId, widgetCourseDetail(course))
            setWidgetTextSize(startId, (9.8f * metrics.textScale).coerceAtLeast(8f))
            setWidgetTextSize(endId, (9.8f * metrics.textScale).coerceAtLeast(8f))
            setWidgetTextSize(nameId, (12.4f * metrics.textScale).coerceAtLeast(9.5f))
            setWidgetTextSize(detailId, (9.4f * metrics.textScale).coerceAtLeast(7.5f))
            val primary = custom?.content?.getOrNull(contentOffset + index) ?: custom?.header
                ?: if (dark) Color.WHITE else Color.rgb(17, 17, 17)
            val secondary = custom?.contentSecondary?.getOrNull(contentOffset + index) ?: custom?.headerSecondary
                ?: if (dark) Color.argb(156, 255, 255, 255) else Color.argb(112, 17, 17, 17)
            setTextColor(startId, primary)
            setTextColor(endId, secondary)
            setTextColor(nameId, primary)
            setTextColor(detailId, secondary)
        }
    }

    private fun drawTodayTomorrow(
        bitmap: Bitmap,
        blurred: Bitmap?,
        state: AppState,
        size: WidgetRenderSize,
        dates: List<LocalDate>,
        allCourses: List<List<CourseEntity>>,
        metrics: TodayTomorrowWidgetLayoutMetrics,
        surface: ExpandedWidgetSurface,
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
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.primary
            textSize = u(16.5f * metrics.textScale)
            typeface = medium
            textAlign = Paint.Align.LEFT
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = surface.secondary
            textSize = u(10f * metrics.textScale)
            typeface = regular
            textAlign = Paint.Align.RIGHT
        }
        val horizontalPadding = metrics.horizontalPaddingDp.toFloat()
        val verticalPadding = metrics.verticalPaddingDp.toFloat()
        val headerRect = RectF(
            x(horizontalPadding),
            y(verticalPadding),
            x(size.widthDp - horizontalPadding),
            y(verticalPadding + metrics.headerHeightDp)
        )
        canvas.drawText("今明课程", headerRect.left, centeredTextBaseline(headerRect, titlePaint), titlePaint)
        val week = scheduleWeekForDateOrNull(state.config, dates.first())
            ?: effectiveCurrentWeek(state.config, dates.first())
        canvas.drawText(
            "第${week}周 · ${dates.first().format(WidgetShortDateFormatter)}–${dates.last().format(WidgetShortDateFormatter)}",
            headerRect.right,
            centeredTextBaseline(headerRect, metaPaint),
            metaPaint
        )

        val panelTopDp = verticalPadding + metrics.headerHeightDp + metrics.headerGapDp
        val panelBottomDp = size.heightDp - verticalPadding
        val panelGapDp = 7f
        val panelWidthDp = (
            size.widthDp - horizontalPadding * 2f - panelGapDp
        ) / 2f
        dates.forEachIndexed { index, date ->
            val panelLeftDp = horizontalPadding + index * (panelWidthDp + panelGapDp)
            val panelRect = RectF(
                x(panelLeftDp),
                y(panelTopDp),
                x(panelLeftDp + panelWidthDp),
                y(panelBottomDp)
            )
            if (panelRect.width() <= u(28f) || panelRect.height() <= u(34f)) return@forEachIndexed
            drawNeutralGlassSurface(
                canvas,
                blurred,
                panelRect,
                u(15f),
                sx,
                sy,
                surface.dark,
                strong = true
            )

            val innerLeft = panelRect.left + u(7f)
            val innerRight = panelRect.right - u(7f)
            val dayHeaderTop = panelRect.top + u(6f)
            val dayHeaderHeight = u(27f)
            val chipRect = RectF(
                innerLeft,
                dayHeaderTop,
                innerLeft + u(22f),
                dayHeaderTop + u(22f)
            )
            val chipColor = if (index == 0) surface.accent else Color.rgb(122, 92, 255)
            canvas.drawContinuousRoundRect(
                chipRect,
                u(7f),
                u(7f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chipColor }
            )
            val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.WHITE
                textSize = u(10.5f * metrics.textScale)
                typeface = medium
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                if (index == 0) "今" else "明",
                chipRect.centerX(),
                centeredTextBaseline(chipRect, chipPaint),
                chipPaint
            )
            val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = surface.primary
                textSize = u(11.5f * metrics.textScale)
                typeface = medium
                textAlign = Paint.Align.LEFT
            }
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = surface.secondary
                textSize = u(8.4f * metrics.textScale)
                typeface = regular
                textAlign = Paint.Align.LEFT
            }
            val dayTextX = chipRect.right + u(6f)
            canvas.drawText(
                shortChineseWeekday(date),
                dayTextX,
                dayHeaderTop - dayPaint.fontMetrics.ascent,
                dayPaint
            )
            canvas.drawText(
                "${date.monthValue}月${date.dayOfMonth}日",
                dayTextX,
                dayHeaderTop + u(13f) - datePaint.fontMetrics.ascent,
                datePaint
            )

            val courses = allCourses[index]
            val shown = courses.take(metrics.maxCoursesPerDay)
            val overflow = courses.size - shown.size
            if (overflow > 0) {
                val overflowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = surface.secondary
                    textSize = u(8.5f * metrics.textScale)
                    typeface = medium
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(
                    "+$overflow",
                    innerRight,
                    dayHeaderTop + u(5f) - overflowPaint.fontMetrics.ascent,
                    overflowPaint
                )
            }

            val rowsTop = dayHeaderTop + dayHeaderHeight
            val rowsBottom = panelRect.bottom - u(7f)
            if (shown.isEmpty()) {
                val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = surface.secondary
                    textSize = u(10.5f * metrics.textScale)
                    typeface = regular
                    textAlign = Paint.Align.CENTER
                }
                val emptyRect = RectF(innerLeft, rowsTop, innerRight, rowsBottom)
                canvas.drawText(
                    if (index == 0) "今天没有课程" else "明天没有课程",
                    emptyRect.centerX(),
                    centeredTextBaseline(emptyRect, emptyPaint),
                    emptyPaint
                )
                return@forEachIndexed
            }

            val rowGap = u(metrics.rowGapDp.toFloat())
            val availableRowsHeight = rowsBottom - rowsTop - rowGap * (shown.size - 1)
            val rowHeight = minOf(
                u(metrics.rowHeightDp.toFloat()),
                availableRowsHeight / shown.size.toFloat()
            ).coerceAtLeast(u(20f))
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = surface.primary
                textSize = u(11.4f * metrics.textScale)
                typeface = medium
                textAlign = Paint.Align.LEFT
            }
            val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = surface.secondary
                textSize = u(8.5f * metrics.textScale)
                typeface = regular
                textAlign = Paint.Align.LEFT
            }
            shown.forEachIndexed { rowIndex, course ->
                val top = rowsTop + rowIndex * (rowHeight + rowGap)
                val rowRect = RectF(innerLeft, top, innerRight, top + rowHeight)
                drawNeutralGlassSurface(
                    canvas,
                    blurred,
                    rowRect,
                    minOf(u(11f), rowHeight * 0.28f),
                    sx,
                    sy,
                    surface.dark
                )
                val courseColor = WidgetCourseColors.color(state.config, course, courseColors)
                val indicatorWidth = u(3.4f)
                val indicatorRect = RectF(
                    rowRect.left + u(5f),
                    rowRect.top + u(6f),
                    rowRect.left + u(5f) + indicatorWidth,
                    rowRect.bottom - u(6f)
                )
                canvas.drawContinuousRoundRect(
                    indicatorRect,
                    indicatorWidth / 2f,
                    indicatorWidth / 2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = courseColor }
                )
                val textLeft = indicatorRect.right + u(5f)
                val textRight = rowRect.right - u(5f)
                val textWidth = (textRight - textLeft).coerceAtLeast(1f)
                val start = courseStartTime(course, state.periods)?.format(WidgetTimeFormatter)
                val end = courseEndTime(course, state.periods)?.format(WidgetTimeFormatter)
                val time = when {
                    start != null && end != null -> "$start–$end"
                    start != null -> start
                    else -> ""
                }
                val detail = listOfNotNull(
                    course.location?.takeIf(String::isNotBlank),
                    time.takeIf(String::isNotBlank)
                ).joinToString(" · ")
                if (detail.isBlank() || rowHeight < u(31f)) {
                    canvas.drawText(
                        ellipsizedWidgetText(course.name, namePaint, textWidth),
                        textLeft,
                        centeredTextBaseline(rowRect, namePaint),
                        namePaint
                    )
                } else {
                    val nameBaseline = rowRect.top + u(4.5f) - namePaint.fontMetrics.ascent
                    canvas.drawText(
                        ellipsizedWidgetText(course.name, namePaint, textWidth),
                        textLeft,
                        nameBaseline,
                        namePaint
                    )
                    canvas.drawText(
                        ellipsizedWidgetText(detail, detailPaint, textWidth),
                        textLeft,
                        (nameBaseline - namePaint.fontMetrics.descent + u(1f) - detailPaint.fontMetrics.ascent)
                            .coerceAtMost(rowRect.bottom - u(4f)),
                        detailPaint
                    )
                }
            }
        }
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            2601,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
