package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.app.ui.AppTopBar
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.WeekDockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextMotionFrozen
import com.xiaomanjun.sleepdownschedule.currentTimelinePeriod
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseAlignmentFragments
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseNeedsSupplementaryWeekRow
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePagerStartOverflow
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadableText
import com.xiaomanjun.sleepdownschedule.feature.home.day.WeekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.homeSidebarOverflowClip
import com.xiaomanjun.sleepdownschedule.feature.home.homeSidebarOverflowViewport
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.home.minimumWeekTimelineRowHeightDp
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun SinglePillWeekScheduleScreen(
    state: AppState,
    displayWeek: Int,
    returnToCurrentWeekRequest: Int = 0,
    adaptiveMetrics: HomeAdaptiveMetrics,
    cardHeight: Dp,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    floatingCourseBackdrop: Backdrop? = backdrop,
    headerBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onWeekHeaderPreview: (Int?) -> Unit = {},
    onWeekJumpSettled: (Int) -> Unit = {},
    onRenderedCardCountChanged: (Int) -> Unit = {},
    style: WeekViewStyle = WeekViewStyle.BOUNDLESS,
    weekEditMode: Boolean = false,
    onEnterWeekEditMode: () -> Unit = {},
    onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    onDeleteCourseSingleWeek: (CourseEntity, Int) -> Unit = { _, _ -> },
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit
) {
    // Keep the period rail and the weekday grid on one shared horizontal geometry. The phone
    // rail used to be 52dp, which left little breathing room around HH:mm labels and made the
    // course columns look slightly left-heavy. The wider rail moves the complete card grid as a
    // unit while the header below derives its leading slot from the exact same boundary. In
    // boundless mode the rail is narrowed so the period/time header shifts left, the left
    // clearance to the grid shrinks, and the freed width flows into every course column equally.
    val boundless = style == WeekViewStyle.BOUNDLESS
    val scrollState = rememberScrollState()
    val weekEditOverlay = rememberWeekEditOverlayController(scrollState = scrollState, scheduleId = state.config.id)
    val rowHeaderWidth = if (boundless) BoundlessWeekRowHeaderWidth else 56.dp
    val clock by produceState(initialValue = java.time.LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = java.time.LocalDateTime.now()
        }
    }
    val today = clock.toLocalDate()
    val now = clock.toLocalTime()
    val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
    val currentPeriod = currentTimelinePeriod(state.periods, now)
    // Keep one computed bucket per visited week, shared by the rail, supplements and Pager.
    // Data replacement invalidates the cache; swiping back reuses the same immutable lists.
    val weekBucketCache = remember(state.courses, state.config.id, state.config.totalWeeks,
        state.config.currentWeek, state.config.autoCurrentWeek, state.config.termStartDate,
        state.config.scheduleAdjustmentsJson, today) { mutableMapOf<Int, WeekCourseBuckets>() }
    fun bucketsForWeek(week: Int) = weekBucketCache.getOrPut(week) {
        weekCourseBuckets(state.courses, week, state.config, today)
    }
    fun openOccurrence(course: CourseEntity, week: Int, bounds: Rect?) {
        val original = state.courses.firstOrNull { it.id == course.id } ?: course
        val date = scheduleWeekStartDate(state.config, week, today).plusDays((course.weekday - 1).toLong())
        val source = com.xiaomanjun.sleepdownschedule.domain.schedule.teachingDateForSchedule(state.config, date)
        val sourceWeek = source?.let { com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(state.config, it, today) } ?: week
        onCourseClick(original, sourceWeek, bounds)
    }
    val currentOpenOccurrence = rememberUpdatedState<(CourseEntity, Int, Rect?) -> Unit>(::openOccurrence)
    val weekBuckets = bucketsForWeek(displayWeek)
    var weekJump by remember { mutableStateOf<AdjacentWeekJump?>(null) }
    val visibleCourses = weekBuckets.visibleCourses
    val showingCurrentWeek = !today.isBefore(weekStart) && today.isBefore(weekStart.plusWeeks(1))
    val specialBell = if (showingCurrentWeek) {
        activeWeekCourseBell(visibleCourses, today.dayOfWeek.value, now)
    } else null
    val specialCourseInProgress = showingCurrentWeek &&
        specialCourseCoversTime(visibleCourses, today.dayOfWeek.value, now)
    val hasAdjustmentBadges = remember(state.config.scheduleAdjustmentsJson) {
        com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments(state.config.scheduleAdjustmentsJson).isNotEmpty()
    }
    val supplementaryRowCount = remember(weekBucketCache, state.periods, displayWeek, weekJump, state.config.showNonCurrentWeekCourses) {
        val renderedWeeks = (displayWeek - 1..displayWeek + 1).toList() +
            listOfNotNull(weekJump?.sourcePage?.plus(1), weekJump?.targetPage?.plus(1))
        renderedWeeks.maxOf { week ->
            val actual = bucketsForWeek(week).visibleCourses
            val counts = actual.flatMap { courseAlignmentFragments(it, state.periods) }
                .filter { courseNeedsSupplementaryWeekRow(it, state.periods) }
                .groupingBy { it.weekday }.eachCount()
            (1..7).maxOf { day -> counts.getOrDefault(day, 0) +
                if (state.config.showNonCurrentWeekCourses)
                    nonCurrentSupplementaryCourses(state.courses, actual, week, day, state.periods).size else 0 }
        }
    }
    val supplementaryHeight = if (supplementaryRowCount > 0) 24.dp + 88.dp * supplementaryRowCount else 0.dp
    val weekdays = weekEditOverlay.request?.let { (1..it.weekdayCount).toList() } ?: remember(weekBuckets, state.courses, state.config, displayWeek) {
        weekPresentationWeekdays(weekBuckets, state.courses, state.config, displayWeek)
    }
    val periodIndexes = remember(state.periods) {
        state.periods.map { it.periodIndex }
    }
    var programmaticPage by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    val screenWidth = adaptiveMetrics.contentWidth
    val topSpacerHeight = adaptiveMetrics.weekTopSpacerHeight
    val horizontalContentStartPadding = if (adaptiveMetrics.isLargeScreen) {
        adaptiveMetrics.tabletContentMargin
    } else {
        0.dp
    }
    val horizontalContentEndPadding = if (adaptiveMetrics.isLargeScreen) {
        adaptiveMetrics.tabletContentMargin
    } else {
        0.dp
    }
    val weekGridEndPadding = when {
        adaptiveMetrics.isLargeScreen -> if (hasAdjustmentBadges) 4.dp else 0.dp
        boundless -> BoundlessWeekGridEndPadding
        else -> 8.dp
    }
    val weekHeaderOuterHorizontalPadding = 4.dp
    val weekHeaderLeadingSlotWidth =
        (rowHeaderWidth - weekHeaderOuterHorizontalPadding).coerceAtLeast(0.dp)
    val weekHeaderLabelsEndPadding =
        (weekGridEndPadding - weekHeaderOuterHorizontalPadding).coerceAtLeast(0.dp)
    // Boundless header geometry. Both values must stay in sync with their upstream sources:
    // AppTopBar in ScheduleAppUi.kt is statusBarsPadding + 66dp, and the week header row keeps
    // the same 46dp height as the normal-mode pill.
    val boundlessAppTopBarHeight = 66.dp
    val boundlessHeaderOffset =
        adaptiveMetrics.safeTop + boundlessAppTopBarHeight + BoundlessWeekHeaderRowHeight
    val pagerState = rememberPagerState(
        initialPage = (displayWeek - 1).coerceAtLeast(0),
        pageCount = { state.config.totalWeeks.coerceAtLeast(1) }
    )
    val latestDisplayWeek by rememberUpdatedState(displayWeek)
    val latestReturnRequest by rememberUpdatedState(returnToCurrentWeekRequest)
    val latestSwipeWeek by rememberUpdatedState(onSwipeWeek)
    val latestWeekHeaderPreview by rememberUpdatedState(onWeekHeaderPreview)
    val latestWeekJumpSettled by rememberUpdatedState(onWeekJumpSettled)
    val weekTail = rememberWeekPageTailMotion(pagerState,
        com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.policy.courseSamples(
            state.config.courseCardGlassEnabled, state.config.courseCardGaussianBlurEnabled, state.config.hasAnyWallpaper()),
        tailEnabled = !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance ||
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer.current != null)
    val homeSwitching = LocalHomeTextContrastFrozen.current
    LaunchedEffect(pagerState, boundless) {
        try {
            snapshotFlow {
                // currentPage changes at the halfway point in either direction. This is only
                // chrome preview; changing displayWeek here would cancel the active gesture.
                if (boundless && programmaticPage < 0) {
                    pagerState.currentPage.coerceIn(0, pagerState.pageCount - 1) + 1
                } else null
            }.distinctUntilChanged().collect { latestWeekHeaderPreview(it) }
        } finally {
            latestWeekHeaderPreview(null)
        }
    }
    LaunchedEffect(pagerState, state.config.totalWeeks) {
        // Publish user swipes only after settling. Programmatic changes already own displayWeek.
        snapshotFlow {
            if (!pagerState.isScrollInProgress && programmaticPage < 0) pagerState.settledPage else null
        }.distinctUntilChanged().collect { page ->
            if (page != null) {
                val settledWeek = (page + 1).coerceIn(1, state.config.totalWeeks.coerceAtLeast(1))
                if (settledWeek != latestDisplayWeek) latestSwipeWeek(settledWeek - latestDisplayWeek)
            }
        }
    }
    LaunchedEffect(pagerState, state.config.totalWeeks) {
        // Finish the currently visible pair, then consume the newest request. Intermediate rapid
        // clicks never become a queue of pages, and the settled observer cannot undo a pending jump.
        var handledReturnRequest = latestReturnRequest
        snapshotFlow { latestDisplayWeek to latestReturnRequest }.collect {
            fun targetPage() = (latestDisplayWeek - 1).coerceIn(0, pagerState.pageCount - 1)
            var moved = false
            try {
                while (pagerState.settledPage != targetPage() ||
                    kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.00001f) {
                    moved = true
                    val target = targetPage()
                    programmaticPage = target
                    weekTail.leadFromTop()
                    val source = pagerState.currentPage
                    if (source != target) {
                        val jump = AdjacentWeekJump(source, target)
                        weekJump = jump
                        // Relocate the SAME visible week beside the destination before moving.
                        // requestScrollToPage bypasses key-based position retention for this rebase.
                        pagerState.requestScrollToPage(jump.sourceSlot)
                        weekTail.snapTo(jump.sourceSlot.toFloat())
                        // The destination is adjacent but still outside the tail's visible range.
                        // Mount and record its glass before the leading group starts moving.
                        androidx.compose.runtime.withFrameNanos { }
                        androidx.compose.runtime.withFrameNanos { }
                    }
                    pagerState.animateScrollToPage(
                        target,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 350f)
                    )
                    weekTail.awaitSettled()
                    // The substituted source is now completely offscreen, including its tail.
                    weekJump = null
                }
            } finally {
                weekJump?.let { interrupted ->
                    val visiblePage = interrupted.logicalPage(pagerState.currentPage)
                        .coerceIn(0, pagerState.pageCount - 1)
                    weekJump = null
                    pagerState.requestScrollToPage(visiblePage)
                    weekTail.snapTo(visiblePage.toFloat())
                }
                programmaticPage = -1
            }
            if (moved || handledReturnRequest != latestReturnRequest) {
                handledReturnRequest = latestReturnRequest
                // Let the substituted page leave composition and draw once before recording
                // a new chrome/glass scene for the destination week.
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                latestWeekJumpSettled(pagerState.settledPage + 1)
            }
        }
    }
    val contentUnderTopBar by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    LaunchedEffect(contentUnderTopBar, onContentUnderTopBarChange) {
        onContentUnderTopBarChange(contentUnderTopBar)
    }
    val overlayHostBounds = remember { mutableStateOf<Rect?>(null) }
    val stationaryCoursesBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "week-stationary-courses"
    )
    val needsStationaryCoursesBackdrop = weekEditMode || weekEditOverlay.request != null
    val floatingSamplingBase = floatingCourseBackdrop ?: backdrop
    val liftedCourseBackdrop = if (floatingSamplingBase != null) {
        rememberGlassCombinedBackdrop(floatingSamplingBase, stationaryCoursesBackdrop)
    } else {
        null
    }
    val overlayScreenHeightPx = with(density) { adaptiveMetrics.screenHeight.toPx() }
    val overlayEdgePx = with(density) { if (adaptiveMetrics.isLargeScreen) 64.dp.toPx() else 88.dp.toPx() }
    val latestState by rememberUpdatedState(state)
    val latestWeekCourses by rememberUpdatedState<(Int) -> List<CourseEntity>>({ bucketsForWeek(it).visibleCourses })
    val pageEdgePx = with(density) { 28.dp.toPx() }
    val pageDragThresholdPx = with(density) { 12.dp.toPx() }
    LaunchedEffect(weekEditOverlay, pagerState, pageEdgePx) {
        snapshotFlow {
            val request = weekEditOverlay.request
            if (request == null || !weekEditOverlay.gestureActive || !weekEditOverlay.rootOwnsMoveGesture ||
                request.mode != WeekEditOverlayMode.Move ||
                abs(weekEditOverlay.offsetX) + abs(weekEditOverlay.offsetY) < pageDragThresholdPx) 0
            else {
                val left = request.sourceBounds.left - (request.dayIndex - 1) * request.gridColumnWidthPx
                weekDragEdgeDirection(weekEditOverlay.pointerPosition.x, left,
                    left + request.weekdayCount * request.gridColumnWidthPx, pageEdgePx,
                    request.destinationWeek, latestState.config.totalWeeks)
            }
        }.distinctUntilChanged().collectLatest { direction ->
            if (direction == 0) return@collectLatest
            var dwell = 450L
            while (true) {
                delay(dwell)
                val request = weekEditOverlay.request ?: break
                if (!weekEditOverlay.gestureActive) break
                if (pagerState.isScrollInProgress || programmaticPage >= 0) continue
                val target = (request.destinationWeek + direction).coerceIn(1, latestState.config.totalWeeks)
                if (target == request.destinationWeek) break
                weekEditOverlay.moveToWeek(target, latestWeekCourses(target))
                latestSwipeWeek(target - latestDisplayWeek)
                dwell = 650L
            }
        }
    }
    // Keep a small cross-axis drawing gutter around HorizontalPager while edit chrome is visible.
    // The first-row delete pill can then overlap the weekday header instead of being clipped or
    // pushed into the course card. Retain it briefly for the exit scale/fade as well.
    var retainEditControlOverflow by remember { mutableStateOf(weekEditMode) }
    LaunchedEffect(weekEditMode) {
        if (weekEditMode) {
            retainEditControlOverflow = true
        } else {
            delay(150)
            retainEditControlOverflow = false
        }
    }
    // Include the glass shadow and entrance overshoot, with extra room below the last row.
    // Adjustment badges sit on the lower corner. Keep bottom overflow stable across weeks
    // without adding badge space above the first row.
    val courseTopOverflow = when {
        retainEditControlOverflow -> 24.dp
        else -> 0.dp
    }
    val editControlBottomOverflow = when {
        retainEditControlOverflow -> 40.dp
        hasAdjustmentBadges -> 24.dp
        else -> 0.dp
    }
    LaunchedEffect(state.config.id, displayWeek, weekEditMode) {
        if (!weekEditMode) weekEditOverlay.clear()
    }
    LaunchedEffect(
        state.courses,
        displayWeek,
        weekEditOverlay.committedTargetKey,
        weekEditOverlay.committedTargetWeek
    ) {
        if (weekEditCommitTargetPresent(
                courses = state.courses,
                targetKey = weekEditOverlay.committedTargetKey,
                targetWeek = weekEditOverlay.committedTargetWeek
            )
        ) {
            // The target card reports its measured bounds through onGloballyPositioned. Waiting for
            // an unconditional extra frame here made the floating card settle, then visibly hover
            // while Room/Flow completed the handoff. Mark commit readiness immediately and let the
            // measured-target gate decide the exact ownership frame.
            weekEditOverlay.completeCommitHandoff()
        }
    }

    val pageTextFrozen = remember(homeSwitching, weekTail) {
        derivedStateOf { homeSwitching.value || weekTail.moving }
    }
    val courseTextFrozen = remember(homeSwitching, weekTail, scrollState) {
        derivedStateOf { homeSwitching.value || scrollState.isScrollInProgress || weekTail.moving }
    }
    CompositionLocalProvider(
        LocalWeekEditMotionState provides weekEditOverlay,
        LocalWeekPageTail provides weekTail,
        LocalHomeTextContrastFrozen provides pageTextFrozen,
        LocalCourseTextMotionFrozen provides courseTextFrozen
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
            .weekTailTouchAnchor(weekTail)
            .weekMoveGestureHost(weekEditOverlay,
                onDrag = { weekEditOverlay.drag(it, overlayScreenHeightPx, overlayEdgePx, with(density) { 4.dp.toPx() }) },
                onFinish = { velocity ->
                    val request = weekEditOverlay.request
                    val saved = weekEditOverlay.finishMove(velocity,
                        canMoveToWeekday = { day ->
                            com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(state.config,
                                scheduleWeekStartDate(state.config, request?.destinationWeek ?: displayWeek)
                                    .plusDays((day - 1).toLong())) == null
                        }, onUpdateCourseSingleWeek = onUpdateCourseSingleWeek)
                    if (!saved && request != null && request.destinationWeek != request.editWeek) {
                        latestSwipeWeek(request.editWeek - latestDisplayWeek)
                    }
                },
                onCancel = {
                    val source = weekEditOverlay.request?.editWeek
                    weekEditOverlay.cancelGesture()
                    if (source != null && source != latestDisplayWeek) latestSwipeWeek(source - latestDisplayWeek)
                })
            .onGloballyPositioned { overlayHostBounds.value = it.boundsInRoot() }
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeSidebarOverflowViewport()
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier.padding(
                start = horizontalContentStartPadding + LocalHomePagerStartOverflow.current,
                end = horizontalContentEndPadding
            )
        ) {
            if (boundless) {
                // Courses start right below the fixed header and travel behind it (and the app
                // bar) when the user scrolls up.
                Spacer(Modifier.height(boundlessHeaderOffset))
            } else {
                Spacer(Modifier.height(topSpacerHeight))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    WeekSwitchButton(-1, state.config, headerBackdrop, enabled = displayWeek > 1) { onSwipeWeek(-1) }
                    HomeReadableText(
                        text = "第${displayWeek}周",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                    WeekSwitchButton(1, state.config, headerBackdrop, enabled = displayWeek < state.config.totalWeeks) { onSwipeWeek(1) }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (!boundless) {
                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .fillMaxWidth()
                            .padding(horizontal = weekHeaderOuterHorizontalPadding, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WeekHeaderPill(headerBackdrop, state.config, selected = false) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.width(weekHeaderLeadingSlotWidth).fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "节次",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                WeekPagerHeaderLabels(
                                    pagerState = pagerState,
                                    displayWeek = displayWeek,
                                    courses = state.courses,
                                    config = state.config,
                                    today = today,
                                    textColor = textColor,
                                    endPadding = weekHeaderLabelsEndPadding,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight * state.periods.size + supplementaryHeight + editControlBottomOverflow)
                        .then(if (retainEditControlOverflow || hasAdjustmentBadges) Modifier else Modifier.homeSidebarOverflowClip())
                ) {
                    Column(
                        modifier = Modifier
                            .width(rowHeaderWidth)
                            .fillMaxHeight()
                            .zIndex(2f)
                    ) {
                        // When the row is too short to render the full period-number plus
                        // two-time-line stack without clipping, keep only the period number.
                        val showTimeColumn =
                            cardHeight.value >= minimumWeekTimelineRowHeightDp(density.fontScale)
                        state.periods.forEach { period ->
                            Box(
                                modifier = Modifier
                                    .height(cardHeight)
                                    .fillMaxWidth()
                                    .homeSwitchGroup(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val isSpecialCurrent = specialBell?.index == period.periodIndex
                                    val isCurrent = if (specialCourseInProgress) isSpecialCurrent
                                        else currentPeriod?.periodIndex == period.periodIndex
                                    Box(
                                        modifier = if (isCurrent) Modifier
                                            .background(
                                                ComposeColor(if (isSpecialCurrent) 0xFFFFC247 else 0xFF0A84FF),
                                                RoundedRectangle(5.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                        else Modifier,
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent) {
                                            Text(
                                                period.periodIndex.toString(),
                                                fontSize = 13.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                color = if (isSpecialCurrent) ComposeColor(0xFF3D2B00) else ComposeColor.White
                                            )
                                        } else {
                                            HomeReadableText(
                                                period.periodIndex.toString(),
                                                fontSize = 13.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                color = textColor
                                            )
                                        }
                                    }
                                    if (showTimeColumn) {
                                        HomeReadableText(
                                            specialBell?.takeIf { isSpecialCurrent }?.start?.toString() ?: period.startTime,
                                            fontSize = 10.sp,
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            color = textColor.copy(alpha = 0.86f)
                                        )
                                        HomeReadableText(
                                            specialBell?.takeIf { isSpecialCurrent }?.end?.toString() ?: period.endTime,
                                            fontSize = 10.sp,
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            color = textColor.copy(alpha = 0.86f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (needsStationaryCoursesBackdrop) {
                                    Modifier.glassBackdropProducer(stationaryCoursesBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val pagerZeroOffset = remember { Animatable(0f) }
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(start = LocalHomePagerStartOverflow.current),
                            modifier = Modifier
                                .offset(y = -courseTopOverflow)
                                .fillMaxWidth()
                                .homeSidebarOverflowViewport()
                                // The parent reserves the bottom gutter. Allow the pager's extra
                                // top gutter to extend above it without compressing the last row.
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .height(cardHeight * state.periods.size + supplementaryHeight + courseTopOverflow + editControlBottomOverflow),
                            userScrollEnabled = !weekEditMode && programmaticPage < 0,
                            // Keep the pager topology stable while a home overlay opens/closes.
                            // Disposing the adjacent week at the exact frame Personalization
                            // starts, then rebuilding it on close, competes with the full-screen
                            // glass/background layers and is visible as a week-only hitch.
                            beyondViewportPageCount = 1,
                            key = { weekJump?.logicalPage(it) ?: it }
                        ) { page ->
                            val pageWeek = (weekJump?.logicalPage(page) ?: page) + 1
                            val pageBuckets = bucketsForWeek(pageWeek)
                            val pageCourses = pageBuckets.visibleCourses
                            val pageWeekdays = weekEditOverlay.request?.let { (1..it.weekdayCount).toList() } ?: remember(pageBuckets, state.courses, state.config, pageWeek) {
                                weekPresentationWeekdays(pageBuckets, state.courses, state.config, pageWeek)
                            }
                            val isActivePage = programmaticPage < 0 && pageWeek == displayWeek && pagerState.settledPage == page
                            val openPageOccurrence = remember(pageWeek) {
                                { course: CourseEntity, bounds: Rect? ->
                                    currentOpenOccurrence.value(course, pageWeek, bounds)
                                }
                            }
                            val pageCardCount = remember(pageWeek) { mutableIntStateOf(0) }
                            WeekPageSamplingScope(weekTail, page, homeSwitching, weekJump) {
                            WeekCourseColumnsLayer(
                                modifier = Modifier.padding(
                                    start = rowHeaderWidth,
                                    top = courseTopOverflow,
                                    bottom = editControlBottomOverflow,
                                    end = weekGridEndPadding
                                ),
                                courses = pageCourses,
                                referenceCourses = state.courses,
                                onRenderedCardCountChanged = { count ->
                                    pageCardCount.intValue = count
                                    if (pageWeek == displayWeek) onRenderedCardCountChanged(count)
                                },
                                showSupplementaryRows = supplementaryRowCount > 0,
                                weekdays = pageWeekdays,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                // The lifted combined backdrop is reserved for WeekEditOverlayHost,
                                // which is outside stationaryCoursesBackdrop.
                                floatingBackdrop = backdrop,
                                config = state.config,
                                weekMotionDirection = 0,
                                outgoing = false,
                                layerOffset = pagerZeroOffset,
                                editMode = weekEditMode && isActivePage,
                                editWeek = pageWeek,
                                allWeekCourses = pageCourses,
                                editScrollState = scrollState,
                                onEnterEditMode = onEnterWeekEditMode,
                                onUpdateSingleWeekCourse = { original, edited ->
                                    onUpdateCourseSingleWeek(original, edited, pageWeek)
                                },
                                conflictFocusCourseId = conflictFocusCourseId,
                                conflictFocusCourseKey = conflictFocusCourseKey,
                                onResolveCourseConflict = { original, moved ->
                                    onResolveCourseConflict(original, moved, pageWeek)
                                },
                                onDeleteSingleWeekCourse = { course ->
                                    onDeleteCourseSingleWeek(course, pageWeek)
                                },
                                activeOverlayCourseId = weekEditOverlay.request?.takeIf { it.editWeek == pageWeek }?.course?.id,
                                activeOverlayTargetKey = weekEditOverlay.committedTargetKey,
                                activeOverlayTargetWeek = weekEditOverlay.committedTargetWeek,
                                onStartWeekEditOverlay = weekEditOverlay::start,
                                onDragWeekEditOverlay = { delta ->
                                    weekEditOverlay.drag(delta, overlayScreenHeightPx, overlayEdgePx, with(density) { 4.dp.toPx() })
                                },
                                onFinishMoveOverlay = { velocity ->
                                    weekEditOverlay.finishMove(
                                        velocity = velocity,
                                        canMoveToWeekday = { day ->
                                            com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(state.config,
                                                scheduleWeekStartDate(state.config, pageWeek).plusDays((day - 1).toLong())) == null
                                        },
                                        onUpdateCourseSingleWeek = onUpdateCourseSingleWeek
                                    )
                                },
                                onFinishResizeOverlay = { velocity ->
                                    weekEditOverlay.finishResize(
                                        velocity = velocity,
                                        resizePaddingPx = with(density) { 4.dp.toPx() },
                                        onUpdateCourseSingleWeek = onUpdateCourseSingleWeek
                                    )
                                },
                                onCancelWeekEditOverlay = weekEditOverlay::cancelGesture,
                                onCourseClick = openPageOccurrence
                            )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(
                (if (adaptiveMetrics.usesSidebar) 24.dp else WeekDockScrollPadding) + adaptiveMetrics.safeBottom
            ))
        }
    }
        WeekEditOverlayHost(
            request = weekEditOverlay.request,
            hostBounds = overlayHostBounds,
            offsetX = weekEditOverlay.offsetX,
            offsetY = weekEditOverlay.offsetY,
            overlayScale = weekEditOverlay.scale,
            overlayAlpha = weekEditOverlay.alpha * weekEditOverlay.revealProgress,
            liftProgress = weekEditOverlay.liftProgress,
            rotation = weekEditOverlay.rotation,
            gestureActive = weekEditOverlay.gestureActive,
            gridOffsetY = weekEditOverlay.gridOffsetY,
            gridScrollCompensationY = weekEditOverlay.gridScrollCompensationY,
            heightPx = weekEditOverlay.height,
            backdrop = liftedCourseBackdrop ?: floatingSamplingBase,
            config = state.config
        )
    }
    }
}
