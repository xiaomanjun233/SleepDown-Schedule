package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodDayPart
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DayDockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownTimeSectionDivider
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextMotionFrozen
import com.xiaomanjun.sleepdownschedule.courseEndTime
import com.xiaomanjun.sleepdownschedule.courseStartTime
import com.xiaomanjun.sleepdownschedule.currentTimelinePeriod
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentActionHandler
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentBackgroundMotionState
import com.xiaomanjun.sleepdownschedule.feature.agent.TodayAgentHost
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.HomeDayPageDrawingScope
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePagerStartOverflow
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible
import com.xiaomanjun.sleepdownschedule.feature.home.homeSidebarOverflowViewport
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.home.week.activeWeekCourseBell
import com.xiaomanjun.sleepdownschedule.feature.home.week.specialCourseCoversTime
import com.xiaomanjun.sleepdownschedule.glass.ui.BlueStatusGlassPill
import com.xiaomanjun.sleepdownschedule.periodRange
import com.xiaomanjun.sleepdownschedule.scheduleDayNavigationRange
import com.xiaomanjun.sleepdownschedule.scheduleWeekForDateOrNull
import com.xiaomanjun.sleepdownschedule.toChineseWeekday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun shouldShowSecondaryDay(
    displayDayCount: Int,
    targetDate: LocalDate,
    now: LocalDateTime,
    dayCourses: List<CourseEntity>,
    periods: List<PeriodEntity>
): Boolean {
    if (displayDayCount >= 2) return true
    if (displayDayCount != 1 || targetDate != now.toLocalDate()) return false
    if (dayCourses.isEmpty()) return true
    val lastCourseEnd = dayCourses.mapNotNull { courseEndTime(it, periods) }.maxOrNull()
        ?: return false
    return !now.toLocalTime().isBefore(lastCourseEnd)
}

internal fun shouldRenderSecondaryDay(
    displayDayCount: Int,
    secondaryCourses: List<CourseEntity>
): Boolean = displayDayCount >= 2 || secondaryCourses.isNotEmpty()

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayScheduleScreen(
    state: AppState,
    agentState: AppState = state,
    adaptiveMetrics: HomeAdaptiveMetrics,
    displayDate: LocalDate,
    displayWeek: Int,
    displayDayCount: Int = 1,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    dayAgentBackdrop: Backdrop? = backdrop,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onRenderedCardCountChanged: (Int) -> Unit = {},
    dayAgentBackgroundMotionState: DayAgentBackgroundMotionState,
    onAgentPagerSettledChange: (Boolean) -> Unit,
    onAgentPrepareOpen: suspend () -> Unit,
    onAgentDismissed: () -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit,
    onAgentAction: AgentActionHandler,
    onAgentImportFile: ((android.net.Uri) -> Unit)? = null
) {
    val centerPage = 10_000
    val anchorDate = remember { displayDate }
    val navigationRange = remember(state.config, anchorDate) {
        scheduleDayNavigationRange(state.config)
    }
    val pagerState = rememberPagerState(
        initialPage = centerPage,
        pageCount = { centerPage * 2 + 1 }
    )
    var gestureCommittedDate by remember { mutableStateOf<LocalDate?>(null) }
    var programmaticDayScroll by remember { mutableStateOf(false) }

    fun dateForPage(page: Int): LocalDate = anchorDate.plusDays((page - centerPage).toLong())
    fun clampToNavigationRange(date: LocalDate): LocalDate {
        val range = navigationRange ?: return date
        return date.coerceIn(range.start, range.endInclusive)
    }

    val paneVisible = LocalHomePaneVisible.current
    val currentPagerSettledChange by rememberUpdatedState(onAgentPagerSettledChange)
    LaunchedEffect(pagerState, displayDate, paneVisible) {
        snapshotFlow {
            paneVisible.value && !pagerState.isScrollInProgress &&
                pagerState.currentPage == pagerState.settledPage &&
                kotlin.math.abs(pagerState.currentPageOffsetFraction) < 0.0005f &&
                dateForPage(pagerState.settledPage) == displayDate
        }.distinctUntilChanged().collect {
            currentPagerSettledChange(it)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onAgentPagerSettledChange(false) }
    }

    // Commit only settled pages. Publishing at 75% rebuilt the date's cards while flinging.
    LaunchedEffect(pagerState.settledPage) {
        if (programmaticDayScroll) return@LaunchedEffect
        val settledDate = dateForPage(pagerState.settledPage)
        val allowedDate = clampToNavigationRange(settledDate)
        if (settledDate != allowedDate) {
            programmaticDayScroll = true
            try {
                val allowedPage = (centerPage + ChronoUnit.DAYS.between(anchorDate, allowedDate).toInt())
                    .coerceIn(0, centerPage * 2)
                pagerState.animateScrollToPage(allowedPage, animationSpec = tween(durationMillis = 180))
            } finally {
                programmaticDayScroll = false
                gestureCommittedDate = null
            }
            return@LaunchedEffect
        }
        if (settledDate != displayDate) {
            gestureCommittedDate = settledDate
            onSwipeDay(ChronoUnit.DAYS.between(displayDate, settledDate).toInt())
        } else {
            gestureCommittedDate = null
        }
    }
    LaunchedEffect(displayDate) {
        if (gestureCommittedDate == displayDate) return@LaunchedEffect
        val safeDisplayDate = clampToNavigationRange(displayDate)
        if (safeDisplayDate != displayDate) {
            onSwipeDay(ChronoUnit.DAYS.between(displayDate, safeDisplayDate).toInt())
            return@LaunchedEffect
        }
        val dayOffset = ChronoUnit.DAYS.between(anchorDate, safeDisplayDate).toInt()
        val targetPage = (centerPage + dayOffset).coerceIn(0, centerPage * 2)
        if (pagerState.settledPage != targetPage) {
            programmaticDayScroll = true
            try {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(durationMillis = 240)
                )
            } finally {
                programmaticDayScroll = false
                gestureCommittedDate = null
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().homeSidebarOverflowViewport(),
        contentPadding = PaddingValues(start = LocalHomePagerStartOverflow.current),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
        HomeDayPageDrawingScope(pagerState, page) {
            val targetDate = remember(anchorDate, page) { dateForPage(page) }
            val targetAdjustment = remember(state.config.scheduleAdjustmentsJson, targetDate) {
                com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(state.config, targetDate)
            }
            // A day off keeps its regular cards on screen, greyed and read-only.
            val targetCancelled = targetAdjustment != null && targetAdjustment.sourceDate == null
            val targetTeachingDate = com.xiaomanjun.sleepdownschedule.domain.schedule.teachingDateForSchedule(state.config, targetDate)
            val targetWeekOrNull = when {
                targetCancelled -> scheduleWeekForDateOrNull(state.config, targetDate)
                else -> targetTeachingDate?.let {
                    if (it != targetDate) com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(state.config, it)
                    else scheduleWeekForDateOrNull(state.config, it)
                }
            }
            val targetWeek = targetWeekOrNull ?: effectiveCurrentWeek(state.config, targetDate)
            val targetWeekday = (targetTeachingDate ?: targetDate).dayOfWeek.toChineseWeekday()
            val dayCourses = remember(state.courses, state.periods, state.config.scheduleAdjustmentsJson, targetWeekOrNull, targetWeekday) {
                if (targetWeekOrNull == null) emptyList() else weekCourseBuckets(state.courses, targetWeekOrNull)
                    .byWeekday[targetWeekday]
                    .orEmpty()
                    .sortedWith(
                        compareBy<CourseEntity> { courseStartTime(it, state.periods) ?: LocalTime.MAX }
                            .thenBy { it.name }
                    )
            }
            val backgroundFrozen = LocalHomeBackgroundFrozen.current
            val dayLifecycleOwner = LocalLifecycleOwner.current
            val minuteClock by produceState(initialValue = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), page, backgroundFrozen, dayLifecycleOwner) {
                // Observe freeze in the clock coroutine, not the entire lazy page. Resuming
                // within the same minute must not rebuild all day cards with new nanoseconds.
                snapshotFlow { backgroundFrozen.value }.collectLatest { frozen ->
                    if (!frozen) dayLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                        while (true) {
                            val nowMillis = System.currentTimeMillis()
                            delay((60_000L - nowMillis % 60_000L + 100L).coerceAtLeast(1_000L))
                            value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                        }
                    }
                }
            }
            val listState = rememberLazyListState()
            val contentUnderTopBar by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            val agentCollapsed by remember { mutableStateOf(false) }
            LaunchedEffect(contentUnderTopBar, pagerState.settledPage, onContentUnderTopBarChange) {
                if (page == pagerState.settledPage) {
                    onContentUnderTopBarChange(contentUnderTopBar)
                }
            }
            val isToday = targetDate == minuteClock.toLocalDate()
            val specialBell = if (isToday && targetWeekOrNull != null && !targetCancelled) {
                activeWeekCourseBell(dayCourses, targetWeekday, minuteClock.toLocalTime())
            } else null
            val specialCourseInProgress = specialBell != null ||
                (isToday && targetWeekOrNull != null && !targetCancelled &&
                    specialCourseCoversTime(dayCourses, targetWeekday, minuteClock.toLocalTime()))
            val currentPeriod = if (isToday && targetWeekOrNull != null && !targetCancelled && !specialCourseInProgress) {
                currentTimelinePeriod(state.periods, minuteClock.toLocalTime())
            } else null
            val headerContent: @Composable () -> Unit = {
                (specialBell?.index ?: currentPeriod?.periodIndex)?.let { periodIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().homeSwitchGroup(),
                        contentAlignment = Alignment.Center
                    ) {
                        DayStatusGlassPill(
                            text = "第${periodIndex}节",
                            backdrop = backdrop,
                            config = state.config,
                            special = specialBell != null
                        )
                    }
                }
            }
            val agentContent: @Composable () -> Unit = {
                if (isToday && abs(page - pagerState.currentPage) <= 1) {
                    Box(Modifier.homeSwitchGroup()) {
                    TodayAgentHost(
                        state = agentState,
                        date = targetDate,
                        backdrop = dayAgentBackdrop,
                        textColor = textColor,
                        collapsed = agentCollapsed,
                        isActive = paneVisible.value && page == pagerState.settledPage,
                        backgroundMotionState = dayAgentBackgroundMotionState,
                        onPrepareOpen = onAgentPrepareOpen,
                        onAgentDismissed = onAgentDismissed,
                        onAgentAction = onAgentAction,
                        onImportFile = onAgentImportFile
                    )
                    }
                }
            }
            val remainingDayCourses = remember(dayCourses, state.periods, targetDate, minuteClock) {
                dayCourses.filterNot { hasDayCourseEnded(it, state.periods, targetDate, minuteClock) }
            }
            val groupedDayCourses = remember(remainingDayCourses, state.config) {
                groupDayCourses(state.config, remainingDayCourses)
            }
            val primaryDayFinished = shouldShowSecondaryDay(
                displayDayCount = displayDayCount,
                targetDate = targetDate,
                now = minuteClock,
                dayCourses = dayCourses,
                periods = state.periods
            )
            val secondaryDate = when {
                displayDayCount >= 2 -> targetDate.plusDays(1)
                displayDayCount == 1 && primaryDayFinished -> targetDate.plusDays(1)
                else -> null
            }
            val secondaryAdjustment = remember(state.config.scheduleAdjustmentsJson, secondaryDate) {
                secondaryDate?.let { com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(state.config, it) }
            }
            val secondaryCancelled = secondaryAdjustment != null && secondaryAdjustment.sourceDate == null
            val secondaryTeachingDate = if (secondaryCancelled) null else secondaryDate?.let { com.xiaomanjun.sleepdownschedule.domain.schedule.teachingDateForSchedule(state.config, it) }
            val secondaryWeekOrNull = when {
                secondaryCancelled -> secondaryDate?.let { scheduleWeekForDateOrNull(state.config, it) }
                else -> secondaryTeachingDate?.let {
                    if (it != secondaryDate) com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(state.config, it)
                    else scheduleWeekForDateOrNull(state.config, it)
                }
            }
            val secondaryWeek = secondaryWeekOrNull ?: secondaryDate?.let { effectiveCurrentWeek(state.config, it) }
                ?: targetWeek
            val secondaryWeekday = (secondaryTeachingDate ?: secondaryDate)?.dayOfWeek?.toChineseWeekday()
            val secondaryCourses = remember(
                state.courses,
                state.periods,
                state.config.scheduleAdjustmentsJson,
                secondaryWeekOrNull,
                secondaryWeekday
            ) {
                if (secondaryWeekOrNull == null || secondaryWeekday == null) {
                    emptyList()
                } else {
                    weekCourseBuckets(state.courses, secondaryWeekOrNull)
                        .byWeekday[secondaryWeekday]
                        .orEmpty()
                        .sortedWith(
                            compareBy<CourseEntity> { courseStartTime(it, state.periods) ?: LocalTime.MAX }
                                .thenBy { it.name }
                        )
                }
            }
            val remainingSecondaryCourses = remember(secondaryCourses, state.periods, secondaryDate, minuteClock) {
                secondaryCourses.filterNot { secondaryDate != null && hasDayCourseEnded(it, state.periods, secondaryDate, minuteClock) }
            }
            val groupedSecondaryCourses = remember(remainingSecondaryCourses, state.config) {
                groupDayCourses(state.config, remainingSecondaryCourses)
            }
            // Two-day mode owns two calendar days, even when the following day is empty. The old
            // course-presence gate made the second day disappear while browsing dates whose next
            // day had no classes, which made the mode look as if it only worked on Today.
            val visibleSecondaryDate = secondaryDate?.takeIf {
                shouldRenderSecondaryDay(displayDayCount, secondaryCourses)
            }
            if (targetDate == displayDate) SideEffect {
                onRenderedCardCountChanged(remainingDayCourses.size +
                    if (visibleSecondaryDate != null) remainingSecondaryCourses.size else 0)
            }
            val courseList: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (remainingDayCourses.isEmpty()) item(key = "primary-empty-$targetDate") {
                    HomeReadableText(if (isToday && dayCourses.isNotEmpty()) "今天的课程已结束"
                        else if (isToday) "今天没有课程" else "这一天没有课程", color = textColor,
                        modifier = Modifier.homeSwitchGroup())
                }
                groupedDayCourses.forEach { (part, coursesInPart) ->
                    item(key = "primary-day-part-$targetDate-${part?.name ?: "OTHER"}") {
                        DayPartHeader(
                            part = part,
                            courses = coursesInPart,
                            periods = state.periods,
                            textColor = textColor
                        )
                    }
                    itemsIndexed(coursesInPart, key = { _, it -> "primary-$targetDate-${it.id}" }) { _, course ->
                        val coursePeriods = course.periods.toSet()
                        val simultaneousCount = remainingDayCourses.count { other ->
                            other.id == course.id || other.periods.any(coursePeriods::contains)
                        }
                        CompositionLocalProvider(
                            LocalCourseTextMotionFrozen provides
                                rememberUpdatedState(listState.isScrollInProgress || pagerState.isScrollInProgress)
                        ) { DayTimelineCourse(
                            course,
                            targetWeek,
                            state.periods,
                            cardColor,
                            backdrop,
                            state.config,
                            onCourseClick,
                            simultaneousCount = simultaneousCount,
                            tabletFontScale = if (adaptiveMetrics.isTabletLandscape) 1.10f else 1f,
                            readOnly = targetAdjustment != null,
                            occurrenceDate = targetDate,
                            muted = targetCancelled,
                            completed = hasDayCourseEnded(course, state.periods, targetDate, minuteClock)
                        ) }
                    }
                }
                visibleSecondaryDate?.let { visibleDate ->
                    item(key = "secondary-date-$visibleDate") {
                        DayDateSectionHeader(
                            badge = if (displayDayCount == 1) "明日预告" else "第二天课程",
                            backdrop = backdrop,
                            config = state.config
                        )
                    }
                    if (remainingSecondaryCourses.isEmpty()) {
                        item(key = "secondary-empty-$visibleDate") {
                            HomeReadableText(if (secondaryCourses.isNotEmpty()) "当天的课程已结束" else "第二天没有课程", color = textColor.copy(alpha = 0.82f),
                                modifier = Modifier.homeSwitchGroup())
                        }
                    }
                    groupedSecondaryCourses.forEach { (part, coursesInPart) ->
                        item(key = "secondary-day-part-$visibleDate-${part?.name ?: "OTHER"}") {
                            DayPartHeader(
                                part = part,
                                courses = coursesInPart,
                                periods = state.periods,
                                textColor = textColor.copy(alpha = 0.82f)
                            )
                        }
                        itemsIndexed(
                            coursesInPart,
                            key = { _, course -> "secondary-$visibleDate-${course.id}" }
                        ) { index, course ->
                            val coursePeriods = course.periods.toSet()
                            val simultaneousCount = remainingSecondaryCourses.count { other ->
                                other.id == course.id || other.periods.any(coursePeriods::contains)
                            }
                            CompositionLocalProvider(
                                LocalCourseTextMotionFrozen provides
                                    rememberUpdatedState(listState.isScrollInProgress || pagerState.isScrollInProgress)
                            ) { DayTimelineCourse(
                                course = course,
                                currentWeek = secondaryWeek,
                                periods = state.periods,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                config = state.config,
                                onCourseClick = onCourseClick,
                                simultaneousCount = simultaneousCount,
                                tabletFontScale = if (adaptiveMetrics.isTabletLandscape) 1.10f else 1f,
                                readOnly = secondaryAdjustment != null,
                                occurrenceDate = visibleDate,
                                muted = secondaryCancelled,
                                completed = hasDayCourseEnded(course, state.periods, visibleDate, minuteClock)
                            ) }
                        }
                    }
                }
            }
            if (adaptiveMetrics.isTabletLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = adaptiveMetrics.tabletContentMargin,
                            end = adaptiveMetrics.tabletContentMargin
                        ),
                    horizontalArrangement = Arrangement.spacedBy(adaptiveMetrics.dayPaneGap)
                ) {
                    Column(
                        modifier = Modifier
                            .width(adaptiveMetrics.daySidePaneWidth)
                            .fillMaxHeight()
                            .padding(top = adaptiveMetrics.dayContentTopPadding)
                            .verticalScroll(rememberScrollState())
                            // Bottom clearance belongs to the scroll content. Padding the viewport
                            // itself clips the assistant cards before they reach the window edge.
                            .padding(bottom = if (adaptiveMetrics.usesSidebar) 20.dp + adaptiveMetrics.safeBottom else DayDockScrollPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        headerContent()
                        agentContent()
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = adaptiveMetrics.dayContentTopPadding,
                            bottom = if (adaptiveMetrics.usesSidebar) 20.dp + adaptiveMetrics.safeBottom else DayDockScrollPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = courseList
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = if (adaptiveMetrics.isLargeScreen) {
                            Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        },
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = adaptiveMetrics.dayContentTopPadding,
                            bottom = if (adaptiveMetrics.usesSidebar) 20.dp + adaptiveMetrics.safeBottom else DayDockScrollPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { headerContent() }
                        if (isToday && abs(page - pagerState.currentPage) <= 1) {
                            item(key = "today-agent-${state.config.id}") { agentContent() }
                        }
                        courseList()
                    }
                }
            }
    }
    }
}

internal fun courseDayPart(
    config: ScheduleConfigEntity,
    course: CourseEntity
): PeriodDayPart? {
    val firstPeriod = course.periods.minOrNull() ?: return null
    return PeriodDayPart.entries.firstOrNull { firstPeriod in config.periodRange(it) }
}

/** Every course must remain reachable even when its periods have no configured day part. */
internal fun groupDayCourses(
    config: ScheduleConfigEntity,
    courses: List<CourseEntity>
): List<Pair<PeriodDayPart?, List<CourseEntity>>> {
    val byPart = courses.groupBy { courseDayPart(config, it) }
    return (PeriodDayPart.entries + null).mapNotNull { part ->
        byPart[part]?.let { part to it }
    }
}

private fun dayPartLabel(part: PeriodDayPart?): String = when (part) {
    PeriodDayPart.MORNING -> "上午"
    PeriodDayPart.NOON -> "中午"
    PeriodDayPart.AFTERNOON -> "下午"
    PeriodDayPart.EVENING -> "晚上"
    null -> "其他时间"
}

@Composable
private fun DayStatusGlassPill(
    text: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    special: Boolean = false
) {
    BlueStatusGlassPill(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        accentColor = if (special) ComposeColor(0xFFFFC247) else ComposeColor(0xFF0A84FF)
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .heightIn(min = 34.dp)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (special) ComposeColor(0xFF3D2B00) else ComposeColor.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DayDateSectionHeader(
    badge: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .homeSwitchGroup()
            .homeCountdownShockwave(0.75f),
        contentAlignment = Alignment.Center
    ) {
        DayStatusGlassPill(
            text = badge,
            backdrop = backdrop,
            config = config
        )
    }
}

@Composable
private fun DayPartHeader(
    part: PeriodDayPart?,
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    textColor: ComposeColor
) {
    val timeRange = remember(courses, periods) {
        val start = courses.mapNotNull { courseStartTime(it, periods) }.minOrNull()
        val end = courses.mapNotNull { courseEndTime(it, periods) }.maxOrNull()
        if (start != null && end != null) "$start–$end" else null
    }
    SleepDownTimeSectionDivider(modifier = Modifier.homeSwitchGroup().homeCountdownShockwave(0.75f), textColor = textColor, label = {
        HomeReadableText(
            text = dayPartLabel(part),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }, time = {
        timeRange?.let {
            HomeReadableText(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.66f)
            )
        }
    })
}
