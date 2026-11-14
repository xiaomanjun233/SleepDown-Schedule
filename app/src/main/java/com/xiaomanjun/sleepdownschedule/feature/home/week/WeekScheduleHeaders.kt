package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.layout.Spacer

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberMotionBlur
import com.xiaomanjun.sleepdownschedule.core.ui.text.AutoFitSingleLineText
import com.xiaomanjun.sleepdownschedule.feature.home.homeHeaderBlur
import com.xiaomanjun.sleepdownschedule.feature.home.homeHeaderTextLayer
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadableText
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.glass.ui.BlueStatusGlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import java.time.LocalDate
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
 * grid always stays aligned. The weekday columns keep their identity as their slots move.
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
        Spacer(Modifier.width(rowHeaderWidth))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val strip = rememberWeekdayStrip(courses, displayWeek, config, today)
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
internal fun WeekdayHeaderLabels(
    displayWeek: Int,
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity,
    today: LocalDate,
    textColor: ComposeColor,
    endPadding: Dp,
    modifier: Modifier = Modifier
) {
    val strip = rememberWeekdayStrip(courses, displayWeek, config, today)
    WeekdayHeaderStrip(
        transition = rememberWeekdayStripTransition(strip),
        today = today,
        textColor = textColor,
        endPadding = endPadding,
        todayStyle = WeekdayTodayStyle.PILL,
        config = config,
        modifier = modifier
    )
}

@Composable
private fun rememberWeekdayStrip(
    courses: List<CourseEntity>,
    displayWeek: Int,
    config: ScheduleConfigEntity,
    today: LocalDate
): WeekdayStrip = remember(courses, displayWeek, config, today) {
    val week = displayWeek.coerceIn(1, config.totalWeeks.coerceAtLeast(1))
    val buckets = weekCourseBuckets(courses, week, config, today)
    WeekdayStrip(
        weekStart = scheduleWeekStartDate(config, week, today),
        weekdays = weekPresentationWeekdays(buckets, courses, config, week)
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
    val progress = remember(transition) { { transition.progress.value } }
    val blur = rememberMotionBlur(TileMode.Decal)
    val targetDays = to.weekdays
    val leavingDays = remember(from, to) { from.weekdays.filter { it !in to.weekdays } }
    val endPaddingPx = with(LocalDensity.current) { endPadding.toPx() }
    val lastTargetDay = targetDays.lastOrNull()
    val lastFromDay = from.weekdays.lastOrNull()
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
                        blur = blur,
                        endPadding = endPadding,
                        modifier = Modifier
                    )
                }
            }
            leavingDays.forEach { day ->
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
                        blur = blur,
                        endPadding = endPadding,
                        modifier = Modifier
                    )
                }
            }
        },
        modifier = if (clipEdges) modifier.clipToBounds() else modifier
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
    blur: (Float) -> RenderEffect?,
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
    val shown = to ?: checkNotNull(from)
    val outgoing = from ?: shown
    val incoming = to ?: shown
    val marked = incoming.takeIf { it.isToday } ?: outgoing.takeIf { it.isToday }
    val markerChanging = outgoing.isToday != incoming.isToday
    fun markerOpacity(): Float = when {
        !markerChanging -> 1f
        incoming.isToday -> motion.fraction()
        else -> 1f - motion.fraction()
    }
    val showTodayCapsule = marked != null && todayStyle == WeekdayTodayStyle.LIGHTWEIGHT &&
        backdrop != null && config != null
    val sampleForPagePalette = todayStyle == WeekdayTodayStyle.LIGHTWEIGHT && !showTodayCapsule
    val adjustmentAccent = ComposeColor(0xFFFFB928)
    val indicatorColor = if (marked?.adjusted == true) adjustmentAccent else MaterialTheme.colorScheme.primary
    // The last visible day has a small trailing gutter so its pill can reach the outer header
    // edge. Keep that extra width centered on the day cell; extending only to the right makes
    // Friday/Sunday look visibly off-center when weekends are hidden or shown.
    val extendsToHeaderEnd = if (incoming.isToday && to != null) {
        incoming.weekday == lastTargetDay
    } else {
        outgoing.weekday == lastFromDay
    }
    fun weekdayInk(state: WeekdayCellState) =
        if (showTodayCapsule && state.isToday) ComposeColor.White else textColor
    fun dateInk(state: WeekdayCellState) =
        weekdayInk(state).copy(alpha = if (showTodayCapsule && state.isToday) 0.92f else 0.72f)
    Box(
        modifier = placed.then(
                if (marked != null && todayStyle == WeekdayTodayStyle.PILL) Modifier.drawWithContent {
                    val horizontalInset = 1.dp.toPx()
                    val verticalInset = 2.dp.toPx()
                    val edgeExtension = if (extendsToHeaderEnd) {
                        endPadding.toPx() + horizontalInset
                    } else {
                        0f
                    }
                    drawContinuousRoundRect(
                        color = indicatorColor.copy(alpha = 0.22f * markerOpacity()),
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
        if (showTodayCapsule) {
            BlueStatusGlassPill(
                backdrop = backdrop,
                config = config,
                accentColor = if (marked.adjusted) adjustmentAccent else ComposeColor(0xFF0A84FF),
                modifier = Modifier
                    .widthIn(min = if (toCount >= 6) 64.dp else 92.dp)
                    .heightIn(min = 34.dp)
                    .then(if (markerChanging) Modifier.graphicsLayer { alpha = markerOpacity() } else Modifier)
            ) {}
        }
        Column(
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HomeReadableText(
                text = "周${weekdayLabel(shown.weekday)}",
                fontSize = if (enlargeWeekdayLabels) 13.sp else 11.sp,
                lineHeight = if (enlargeWeekdayLabels) 14.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = weekdayInk(shown),
                drawColor = if (showTodayCapsule && markerChanging) {
                    ColorProducer { lerp(textColor, ComposeColor.White, markerOpacity()) }
                } else null,
                textAlign = TextAlign.Center,
                maxLines = 1,
                sampleForPagePalette = sampleForPagePalette
            )
            if (outgoing.date != incoming.date) {
                Box(
                    Modifier.graphicsLayer { homeHeaderBlur(motion.fraction(), 3.dp, blur) },
                    contentAlignment = Alignment.Center
                ) {
                    WeekdayDateLabel(
                        outgoing.date, dateInk(outgoing), enlargeWeekdayLabels,
                        Modifier.homeHeaderTextLayer({ 1f - motion.fraction() }).clearAndSetSemantics {},
                        sampleForPagePalette = false
                    )
                    WeekdayDateLabel(
                        incoming.date, dateInk(incoming), enlargeWeekdayLabels,
                        Modifier.homeHeaderTextLayer(motion::fraction),
                        sampleForPagePalette = sampleForPagePalette
                    )
                }
            } else {
                WeekdayDateLabel(shown.date, dateInk(shown), enlargeWeekdayLabels,
                    sampleForPagePalette = sampleForPagePalette)
            }
        }
    }
}

@Composable
private fun WeekdayDateLabel(
    date: LocalDate,
    color: ComposeColor,
    enlarge: Boolean,
    modifier: Modifier = Modifier,
    sampleForPagePalette: Boolean,
) {
    HomeReadableText(
        text = "${date.monthValue}/${date.dayOfMonth}",
        modifier = modifier,
        fontSize = if (enlarge) 11.sp else 9.sp,
        lineHeight = if (enlarge) 12.sp else 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        sampleForPagePalette = sampleForPagePalette
    )
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
