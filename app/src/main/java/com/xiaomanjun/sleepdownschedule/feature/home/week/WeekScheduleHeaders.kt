package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.effects.blur
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
import com.xiaomanjun.sleepdownschedule.core.ui.text.AutoFitSingleLineText
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
import kotlin.math.abs
import kotlin.math.min

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
 * grid always stays aligned; the current-week caption stays hidden until the user swipes weeks.
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
            AutoFitSingleLineText(
                text = "第${displayWeek}周",
                color = textColor,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp),
                alignment = Alignment.Center,
                candidateFontSizes = listOf(14.sp, 13.sp, 12.sp, 11.sp, 10.sp),
                content = { fittedSize ->
                    HomeReadableText(
                        text = "第${displayWeek}周", color = textColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = fittedSize, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
        val displayedPage = (displayWeek - 1).coerceIn(0, config.totalWeeks.coerceAtLeast(1) - 1)
        val displayedWeekdays = remember(courses, displayedPage, config, today) {
            val buckets = weekCourseBuckets(courses, displayedPage + 1, config, today)
            weekPresentationWeekdays(buckets, courses, config, displayedPage + 1)
        }
        WeekdayHeaderLabels(
            weekdays = displayedWeekdays,
            weekStart = scheduleWeekStartDate(config, displayedPage + 1, today),
            today = today,
            textColor = textColor,
            endPadding = weekGridEndPadding,
            todayStyle = WeekdayTodayStyle.LIGHTWEIGHT,
            backdrop = backdrop,
            config = config,
            enlargeWeekdayLabels = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
internal fun WeekPagerHeaderLabels(
    pagerState: PagerState,
    displayWeek: Int,
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity,
    today: LocalDate,
    textColor: ComposeColor,
    endPadding: Dp,
    todayStyle: WeekdayTodayStyle = WeekdayTodayStyle.PILL,
    modifier: Modifier = Modifier
) {
    val maxPage = (config.totalWeeks - 1).coerceAtLeast(0)
    val currentPage = pagerState.currentPage.coerceIn(0, maxPage)
    val settledPage = pagerState.settledPage.coerceIn(0, maxPage)
    val adjacentPage by remember(pagerState, maxPage) {
        derivedStateOf {
            val page = pagerState.currentPage.coerceIn(0, maxPage)
            when {
                pagerState.currentPageOffsetFraction > 0f -> (page + 1).coerceAtMost(maxPage)
                pagerState.currentPageOffsetFraction < 0f -> (page - 1).coerceAtLeast(0)
                else -> page
            }
        }
    }

    fun weekdaysForPage(page: Int): List<Int> {
        val buckets = weekCourseBuckets(courses, page + 1, config, today)
        return weekPresentationWeekdays(buckets, courses, config, page + 1)
    }

    val currentWeekdays = remember(courses, currentPage, config, today) {
        weekdaysForPage(currentPage)
    }
    val adjacentWeekdays = remember(courses, adjacentPage, config, today) {
        weekdaysForPage(adjacentPage)
    }
    val offsetVisible by remember(pagerState) {
        derivedStateOf { abs(pagerState.currentPageOffsetFraction) > 0.0001f }
    }
    val slideHeader = offsetVisible && adjacentPage != currentPage && currentWeekdays != adjacentWeekdays

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        if (slideHeader) {
            val pages = if (currentPage < adjacentPage) {
                currentPage..adjacentPage
            } else {
                adjacentPage..currentPage
            }
            pages.forEach { page ->
                key(page) {
                    val pageWeekdays = if (page == currentPage) currentWeekdays else adjacentWeekdays
                    WeekdayHeaderLabels(
                        weekdays = pageWeekdays,
                        weekStart = scheduleWeekStartDate(config, page + 1, today),
                        today = today,
                        textColor = textColor,
                        endPadding = endPadding,
                        todayStyle = todayStyle,
                        config = config,
                        modifier = Modifier.graphicsLayer {
                            translationX = ((page - pagerState.currentPage) -
                                pagerState.currentPageOffsetFraction) * widthPx
                        }
                    )
                }
            }
        } else {
            val displayedPage = (displayWeek - 1).coerceIn(0, maxPage)
            val displayedWeekdays = remember(courses, displayedPage, config, today) {
                weekdaysForPage(displayedPage)
            }
            WeekdayHeaderLabels(
                weekdays = displayedWeekdays,
                weekStart = scheduleWeekStartDate(config, displayedPage + 1, today),
                today = today,
                textColor = textColor,
                endPadding = endPadding,
                todayStyle = todayStyle,
                config = config
            )
        }
    }
}

@Composable
private fun WeekdayHeaderLabels(
    weekdays: List<Int>,
    weekStart: LocalDate,
    today: LocalDate,
    textColor: ComposeColor,
    endPadding: Dp,
    todayStyle: WeekdayTodayStyle = WeekdayTodayStyle.PILL,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity? = null,
    enlargeWeekdayLabels: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(end = endPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdays.forEachIndexed { index, day ->
            val date = weekStart.plusDays((day - 1).toLong())
            val isToday = date == today
            val extendsToHeaderEnd = isToday && index == weekdays.lastIndex
            val adjusted = config != null && com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(config, date) != null
            val indicatorColor = (if (adjusted) ComposeColor(0xFFFFB928) else MaterialTheme.colorScheme.primary).copy(alpha = 0.22f)
            val lightweightToday = isToday && todayStyle == WeekdayTodayStyle.LIGHTWEIGHT
            val showTodayCapsule = lightweightToday && backdrop != null && config != null
            val capsuleTextColor = ComposeColor.White
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (isToday && todayStyle == WeekdayTodayStyle.PILL) Modifier
                            .drawWithContent {
                                val horizontalInset = 1.dp.toPx()
                                val verticalInset = 2.dp.toPx()
                                // The last visible day has a small trailing gutter so its pill can
                                // reach the outer header edge. Keep that extra width centered on
                                // the day cell; extending only to the right makes Friday/Sunday
                                // look visibly off-center when weekends are hidden or shown.
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
                            }
                        else Modifier
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
                        accentColor = if (adjusted) ComposeColor(0xFFFFB928) else ComposeColor(0xFF0A84FF),
                        modifier = Modifier
                            .widthIn(min = if (weekdays.size >= 6) 64.dp else 92.dp)
                            .heightIn(min = 34.dp)
                    ) {}
                }
                Column(
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HomeReadableText(
                        text = "周${weekdayLabel(day)}",
                        fontSize = if (enlargeWeekdayLabels) 13.sp else 11.sp,
                        lineHeight = if (enlargeWeekdayLabels) 14.sp else 12.sp,
                        fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                        color = if (showTodayCapsule) capsuleTextColor else textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    HomeReadableText(
                        text = "${date.monthValue}/${date.dayOfMonth}",
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
