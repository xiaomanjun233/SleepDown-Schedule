package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensAmount
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensHeight
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeHeaderGlassTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.drawContinuousRoundRect
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberPageMotionBlur
import com.xiaomanjun.sleepdownschedule.core.ui.text.AutoFitSingleLineText
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadableText
import com.xiaomanjun.sleepdownschedule.feature.home.day.LocalHomeTextShadowScale
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.glass.ui.BlueStatusGlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun WeekSwitchButton(direction: Int, config: ScheduleConfigEntity, backdrop: Backdrop?, enabled: Boolean, onClick: () -> Unit) {
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) HomeLightGlassSurfaceColor else ComposeColor(0xFF121212)
    val textColor = glassForegroundColor(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            enabled = enabled,
            backdrop = backdrop,
            modifier = Modifier
                .size(34.dp)
                .excludeParentPullGesture()
                .graphicsLayer(alpha = if (enabled) 1f else 0.35f),
            isInteractive = enabled,
            surfaceColor = surfaceColor.copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass)),
            height = 34.dp,
            contentPadding = PaddingValues(0.dp),
            blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (direction < 0) "上一周" else "下一周",
                    tint = textColor,
                    modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = if (direction > 0) 180f else 0f)
                )
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = Modifier.size(34.dp).excludeParentPullGesture().graphicsLayer(alpha = if (enabled) 1f else 0.35f),
            onClick = if (enabled) onClick else null
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (direction < 0) "上一周" else "下一周",
                    tint = textColor,
                    modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = if (direction > 0) 180f else 0f)
                )
            }
        }
    }
}

internal enum class WeekdayTodayStyle {
    PILL,
    LIGHTWEIGHT
}

/** Height of the boundless week header row, shared with the home top bar slot renderer. */
internal val BoundlessWeekHeaderRowHeight = 46.dp
internal val BoundlessWeekRowHeaderWidth = 44.dp
internal val BoundlessWeekGridEndPadding = 4.dp

/**
 * The boundless week header lives on the app top bar layer (above the top gradient blur), so the
 * weekday/date labels stay crisp and are never covered by the gradient. It reuses the exact
 * course-column geometry: a rowHeaderWidth leading slot (same width as the timeline rail) plus
 * equal weekday columns with weekGridEndPadding trailing. The leading slot is fixed width so the
 * grid always stays aligned. The leading week caption and every weekday column are shared
 * elements: changing words dissolve, changing slots travel with a spring.
 */
@Composable
internal fun BoundlessWeekdayHeaderRow(
    displayWeek: Int,
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity,
    today: LocalDate,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    rowHeaderWidth: Dp,
    weekGridEndPadding: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(BoundlessWeekHeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(rowHeaderWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            WeekHeaderCrossfade(
                target = displayWeek,
                modifier = Modifier.fillMaxSize()
            ) { week ->
                AutoFitSingleLineText(
                    text = "第${week}周",
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 1.dp),
                    alignment = Alignment.Center,
                    candidateFontSizes = listOf(14.sp, 13.sp, 12.sp, 11.sp, 10.sp),
                    content = { fittedSize ->
                        HomeReadableText(
                            text = "第${week}周", color = textColor,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = fittedSize, fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val maxPage = (config.totalWeeks - 1).coerceAtLeast(0)
            val displayedPage = (displayWeek - 1).coerceIn(0, maxPage)
            val displayedDays = remember(courses, displayedPage, config, today) {
                val buckets = weekCourseBuckets(courses, displayedPage + 1, config, today)
                weekPresentationWeekdays(buckets, courses, config, displayedPage + 1)
            }
            val weekStart = remember(config, displayWeek, today) {
                scheduleWeekStartDate(config, displayWeek, today)
            }
            val strip = remember(weekStart, displayedDays) { WeekdayStrip(weekStart, displayedDays) }
            WeekdayHeaderStrip(
                transition = rememberWeekdayStripTransition(strip),
                today = today,
                textColor = textColor,
                endPadding = weekGridEndPadding,
                todayStyle = WeekdayTodayStyle.LIGHTWEIGHT,
                backdrop = backdrop,
                config = config,
                enlargeWeekdayLabels = true,
                clipEdges = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun WeekPagerHeaderLabels(
    pagerState: PagerState,
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity,
    today: LocalDate,
    textColor: ComposeColor,
    endPadding: Dp,
    todayStyle: WeekdayTodayStyle = WeekdayTodayStyle.PILL,
    modifier: Modifier = Modifier
) {
    val maxPage = (config.totalWeeks - 1).coerceAtLeast(0)
    val pagePosition = remember(pagerState) {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val basePageState = remember(pagePosition, maxPage) {
        derivedStateOf { floor(pagePosition.value).toInt().coerceIn(0, maxPage) }
    }
    val moving by remember(pagePosition) {
        derivedStateOf {
            val position = pagePosition.value
            abs(position - position.roundToInt()) > 0.0001f
        }
    }
    val basePage by basePageState
    val nextPage = (basePage + 1).coerceAtMost(maxPage)
    val baseStrip = rememberWeekdayStrip(courses, basePage, config, today)
    val nextStrip = rememberWeekdayStrip(courses, nextPage, config, today)

    // The header follows the finger: columns keep their identity, dates dissolve column by column,
    // and a day-count change carries each column to its new slot instead of crossfading the rows.
    val transition = if (moving && nextPage != basePage) {
        remember(baseStrip, nextStrip, basePageState) {
            WeekdayStripTransition(baseStrip, nextStrip) {
                (pagePosition.value - basePageState.value).coerceIn(0f, 1f)
            }
        }
    } else {
        remember(baseStrip) { WeekdayStripTransition(baseStrip, baseStrip) { 1f } }
    }
    WeekdayHeaderStrip(
        transition = transition,
        today = today,
        textColor = textColor,
        endPadding = endPadding,
        todayStyle = todayStyle,
        modifier = modifier
    )
}

@Composable
private fun rememberWeekdayStrip(
    courses: List<CourseEntity>,
    page: Int,
    config: ScheduleConfigEntity,
    today: LocalDate
): WeekdayStrip = remember(courses, page, config, today) {
    val buckets = weekCourseBuckets(courses, page + 1, config, today)
    WeekdayStrip(
        weekStart = scheduleWeekStartDate(config, page + 1, today),
        weekdays = weekPresentationWeekdays(buckets, courses, config, page + 1)
    )
}

/**
 * Equal-width weekday columns placed from one strip to the other.
 *
 * The layout only decides the destination slots; every frame of the transition is drawn by the
 * cells themselves, so no relayout or recomposition happens while the columns travel. A column
 * that exists on both sides keeps its identity and moves, a column that only enters slides in
 * from the nearest header edge while fading in, and a column that only leaves slides out.
 */
@Composable
private fun WeekdayHeaderStrip(
    transition: WeekdayStripTransition,
    today: LocalDate,
    textColor: ComposeColor,
    endPadding: Dp,
    todayStyle: WeekdayTodayStyle,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity? = null,
    enlargeWeekdayLabels: Boolean = false,
    clipEdges: Boolean = true
) {
    val from = transition.from
    val to = transition.to
    val progress = transition.progress
    val targetDays = to.weekdays
    val leavingDays = remember(from, to) { from.weekdays.filter { it !in to.weekdays } }
    val endPaddingPx = with(LocalDensity.current) { endPadding.toPx() }
    val lastTargetDay = targetDays.lastOrNull()
    val lastFromDay = from.weekdays.lastOrNull()
    // The first version's blur envelope, applied once around the whole row: the labels soften
    // while the columns travel and are sharp again at both endpoints.
    val motionBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rememberPageMotionBlur() else null
    val rowBlur = if (from == to || motionBlur == null) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val fraction = progress().coerceIn(0f, 1f)
            renderEffect = motionBlur(4f * (1f - abs(2f * fraction - 1f)))
        }
    }

    Layout(
        content = {
            targetDays.forEachIndexed { index, day ->
                key(day) {
                    val targetState = weekdayCellState(day, to, config, today)
                    val fromState = if (day in from.weekdays) weekdayCellState(day, from, config, today) else null
                    WeekdayHeaderCell(
                        motion = SlotMotion(
                            fromIndex = from.weekdays.indexOf(day),
                            fromCount = from.weekdays.size,
                            toIndex = index,
                            toCount = targetDays.size,
                            leaving = false,
                            progress = progress
                        ),
                        from = fromState,
                        to = targetState,
                        toCount = targetDays.size,
                        lastTargetDay = lastTargetDay,
                        lastFromDay = lastFromDay,
                        textColor = textColor,
                        todayStyle = todayStyle,
                        backdrop = backdrop,
                        config = config,
                        enlargeWeekdayLabels = enlargeWeekdayLabels,
                        endPadding = endPadding,
                        modifier = Modifier
                    )
                }
            }
            leavingDays.forEachIndexed { offset, day ->
                key(day) {
                    WeekdayHeaderCell(
                        motion = SlotMotion(
                            fromIndex = from.weekdays.indexOf(day).coerceAtLeast(0),
                            fromCount = from.weekdays.size,
                            toIndex = -1,
                            toCount = targetDays.size,
                            leaving = true,
                            progress = progress
                        ),
                        from = weekdayCellState(day, from, config, today),
                        to = null,
                        toCount = from.weekdays.size,
                        lastTargetDay = lastFromDay,
                        lastFromDay = lastFromDay,
                        textColor = textColor,
                        todayStyle = todayStyle,
                        backdrop = backdrop,
                        config = config,
                        enlargeWeekdayLabels = enlargeWeekdayLabels,
                        endPadding = endPadding,
                        modifier = Modifier
                    )
                }
            }
        },
        modifier = (if (clipEdges) modifier.clipToBounds() else modifier).then(rowBlur)
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        // Cumulative rounding keeps the last column exactly on the course-grid boundary instead
        // of leaving up to one pixel per column unused at the right edge.
        val trackWidth = (width - endPaddingPx).coerceAtLeast(1f)
        fun slotX(index: Int, count: Int): Int =
            (trackWidth * index / count.coerceAtLeast(1)).roundToInt()
        fun slotWidth(index: Int, count: Int): Int =
            (slotX(index + 1, count) - slotX(index, count)).coerceAtLeast(1)
        val boundedHeight = constraints.hasBoundedHeight
        val height = if (boundedHeight) constraints.maxHeight else Constraints.Infinity
        val placeables = measurables.mapIndexed { index, measurable ->
            val column = if (index < targetDays.size) {
                slotWidth(index, targetDays.size)
            } else {
                val day = leavingDays[index - targetDays.size]
                slotWidth(from.weekdays.indexOf(day).coerceAtLeast(0), from.weekdays.size)
            }
            measurable.measure(
                if (boundedHeight) {
                    Constraints.fixed(column, height)
                } else {
                    Constraints(minWidth = column, maxWidth = column)
                }
            )
        }
        val rowHeight = if (boundedHeight) height else placeables.maxOfOrNull { it.height } ?: 0
        layout(width, rowHeight) {
            targetDays.indices.forEach { index ->
                placeables[index].place(slotX(index, targetDays.size), 0)
            }
            leavingDays.forEachIndexed { offset, day ->
                val oldIndex = from.weekdays.indexOf(day).coerceAtLeast(0)
                placeables[targetDays.size + offset].place(slotX(oldIndex, from.weekdays.size), 0)
            }
        }
    }
}

private data class WeekdayCellState(
    val weekday: Int,
    val date: LocalDate,
    val isToday: Boolean,
    val adjusted: Boolean
)

private fun weekdayCellState(
    weekday: Int,
    strip: WeekdayStrip,
    config: ScheduleConfigEntity?,
    today: LocalDate
): WeekdayCellState {
    val offset = config?.let {
        com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekdayOffset(it, weekday)
    } ?: (weekday - 1).toLong()
    val date = strip.weekStart.plusDays(offset)
    return WeekdayCellState(
        weekday = weekday,
        date = date,
        isToday = date == today,
        adjusted = config != null &&
            com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(config, date) != null
    )
}

/**
 * Where one column starts, where the layout put it, and where it has to end up.
 *
 * Everything is derived from the column width the cell was measured with, so drawing a frame
 * never needs another layout pass.
 */
private class SlotMotion(
    private val fromIndex: Int,
    private val fromCount: Int,
    private val toIndex: Int,
    private val toCount: Int,
    private val leaving: Boolean,
    private val progress: () -> Float
) {
    fun fraction(): Float = progress().coerceIn(0f, 1f)

    /** True when this column keeps its slot and width, so it needs no layer at all. */
    val motionless: Boolean
        get() = !leaving && fromIndex == toIndex && fromCount == toCount

    fun translation(cellWidth: Float): Float {
        if (cellWidth <= 0f || fromCount <= 0 || toCount <= 0) return 0f
        val trackWidth = if (leaving) cellWidth * fromCount else cellWidth * toCount
        val fromWidth = if (leaving) cellWidth else trackWidth / fromCount
        val toWidth = if (leaving) trackWidth / toCount else cellWidth
        if (leaving) {
            val start = fromIndex * fromWidth + fromWidth / 2f
            val end = if (fromIndex * 2 < fromCount) -cellWidth / 2f else trackWidth + cellWidth / 2f
            return (end - start) * progress()
        }
        val start = when {
            fromIndex >= 0 -> fromIndex * fromWidth + fromWidth / 2f
            toIndex * 2 < toCount -> -fromWidth / 2f
            else -> trackWidth + fromWidth / 2f
        }
        val end = toIndex * toWidth + toWidth / 2f
        return (start - end) * (1f - progress())
    }

    fun alpha(): Float = when {
        leaving -> 1f - fraction()
        fromIndex < 0 -> fraction()
        else -> 1f
    }
}

@Composable
private fun WeekdayHeaderCell(
    motion: SlotMotion,
    from: WeekdayCellState?,
    to: WeekdayCellState?,
    toCount: Int,
    lastTargetDay: Int?,
    lastFromDay: Int?,
    textColor: ComposeColor,
    todayStyle: WeekdayTodayStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity?,
    enlargeWeekdayLabels: Boolean,
    endPadding: Dp,
    modifier: Modifier
) {
    var cellWidth by remember { mutableFloatStateOf(0f) }
    val placed = modifier
        .fillMaxHeight()
        .onSizeChanged { cellWidth = it.width.toFloat() }
        .then(
            // A column that keeps its slot and width is drawn straight into the frame, so the
            // resting header owns no layer at all.
            if (motion.motionless) {
                Modifier
            } else {
                Modifier.graphicsLayer {
                    translationX = if (cellWidth > 0f) motion.translation(cellWidth) else 0f
                    alpha = motion.alpha()
                }
            }
        )
    Box(modifier = placed) {
        val animating = from != null && to != null && from != to
        if (animating) {
            val outgoing = checkNotNull(from)
            val incoming = checkNotNull(to)
            // Two stacked labels would otherwise stack their legibility shadows and darken the
            // wallpaper behind the column for the length of the dissolve.
            CompositionLocalProvider(LocalHomeTextShadowScale provides 0.5f) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - motion.fraction()
                    }
                ) {
                    WeekdayCellContent(
                        state = outgoing,
                        weekdayCount = toCount,
                        lastWeekday = lastFromDay,
                        textScale = { 1f - 0.02f * motion.fraction() },
                        textColor = textColor,
                        todayStyle = todayStyle,
                        backdrop = backdrop,
                        config = config,
                        enlargeWeekdayLabels = enlargeWeekdayLabels,
                        endPadding = endPadding
                    )
                }
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = motion.fraction()
                    }
                ) {
                    WeekdayCellContent(
                        state = incoming,
                        weekdayCount = toCount,
                        lastWeekday = lastTargetDay,
                        textScale = { 0.98f + 0.02f * motion.fraction() },
                        textColor = textColor,
                        todayStyle = todayStyle,
                        backdrop = backdrop,
                        config = config,
                        enlargeWeekdayLabels = enlargeWeekdayLabels,
                        endPadding = endPadding
                    )
                }
            }
        } else {
            WeekdayCellContent(
                state = (to ?: from) ?: return@Box,
                weekdayCount = toCount,
                lastWeekday = if (to != null) lastTargetDay else lastFromDay,
                textScale = { 1f },
                textColor = textColor,
                todayStyle = todayStyle,
                backdrop = backdrop,
                config = config,
                enlargeWeekdayLabels = enlargeWeekdayLabels,
                endPadding = endPadding
            )
        }
    }
}

@Composable
private fun WeekdayCellContent(
    state: WeekdayCellState,
    weekdayCount: Int,
    lastWeekday: Int?,
    textScale: () -> Float,
    textColor: ComposeColor,
    todayStyle: WeekdayTodayStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity?,
    enlargeWeekdayLabels: Boolean,
    endPadding: Dp,
    modifier: Modifier = Modifier
) {
    val isToday = state.isToday
    val lightweightToday = isToday && todayStyle == WeekdayTodayStyle.LIGHTWEIGHT
    val showTodayCapsule = lightweightToday && backdrop != null && config != null
    val capsuleTextColor = ComposeColor.White
    val indicatorColor = (if (state.adjusted) ComposeColor(0xFFFFB928) else MaterialTheme.colorScheme.primary)
        .copy(alpha = 0.22f)
    // The last visible day has a small trailing gutter so its pill can reach the outer header
    // edge. Keep that extra width centered on the day cell; extending only to the right makes
    // Friday/Sunday look visibly off-center when weekends are hidden or shown.
    val extendsToHeaderEnd = isToday && state.weekday == lastWeekday
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isToday && todayStyle == WeekdayTodayStyle.PILL) Modifier.drawWithContent {
                    val horizontalInset = 1.dp.toPx()
                    val verticalInset = 2.dp.toPx()
                    val edgeExtension = if (extendsToHeaderEnd) {
                        endPadding.toPx() + horizontalInset
                    } else {
                        0f
                    }
                    drawContinuousRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(
                            horizontalInset - edgeExtension,
                            verticalInset
                        ),
                        size = Size(
                            width = (size.width - horizontalInset * 2f + edgeExtension * 2f)
                                .coerceAtLeast(0f),
                            height = (size.height - verticalInset * 2f).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(
                            x = (size.height - verticalInset * 2f).coerceAtLeast(0f) / 2f,
                            y = (size.height - verticalInset * 2f).coerceAtLeast(0f) / 2f
                        )
                    )
                    drawContent()
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Today in boundless mode: a blue glass capsule sits BEHIND the text as an outer
        // container. The two-line weekday/date layout below stays byte-for-byte identical
        // to the other days (same texts, fonts, spacing and centering) — only the color
        // switches to white so it reads inside the blue glass container.
        if (showTodayCapsule) {
            BlueStatusGlassPill(
                backdrop = backdrop,
                config = config,
                accentColor = if (state.adjusted) ComposeColor(0xFFFFB928) else ComposeColor(0xFF0A84FF),
                modifier = Modifier
                    .widthIn(min = if (weekdayCount >= 6) 64.dp else 92.dp)
                    .heightIn(min = 34.dp)
            ) {}
        }
        Column(
            modifier = Modifier
                .padding(vertical = 2.dp, horizontal = 1.dp)
                .graphicsLayer {
                    val scale = textScale()
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HomeReadableText(
                text = "周${weekdayLabel(state.weekday)}",
                fontSize = if (enlargeWeekdayLabels) 13.sp else 11.sp,
                lineHeight = if (enlargeWeekdayLabels) 14.sp else 12.sp,
                fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                color = if (showTodayCapsule) capsuleTextColor else textColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            HomeReadableText(
                text = "${state.date.monthValue}/${state.date.dayOfMonth}",
                fontSize = if (enlargeWeekdayLabels) 11.sp else 9.sp,
                lineHeight = if (enlargeWeekdayLabels) 12.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (showTodayCapsule) {
                    capsuleTextColor.copy(alpha = 0.92f)
                } else {
                    textColor.copy(alpha = 0.72f)
                },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun WeekHeaderPill(backdrop: Backdrop?, config: ScheduleConfigEntity, selected: Boolean, content: @Composable () -> Unit) {
    GlassSurface(
        nativeMaterial = true,
        nativeBlurRadiusScale = 0.6f,
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxSize(),
        shape = Capsule(),
        tokens = homeHeaderGlassTokens(
            lightGlass = glassUsesLightStyle(config),
            blurScale = config.homeChromeBlurScale
        ),
        selected = selected,
        baseSurfaceColorOverride = if (glassUsesLightStyle(config)) HomeLightGlassSurfaceColor else null,
        content = content
    )
}
