package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberHapticMiuixOverscrollFactory
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentActionHandler
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentBackgroundMotionState
import com.xiaomanjun.sleepdownschedule.feature.agent.LocalHomeAssistant
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSwitchMotion
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSwitchPane
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible
import com.xiaomanjun.sleepdownschedule.feature.home.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.feature.home.PersonalizationPreviewState
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseCopy
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeSwitchMotion
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeTopBarScrollReporter
import com.xiaomanjun.sleepdownschedule.feature.home.week.SinglePillWeekScheduleScreen
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekViewStyle
import com.xiaomanjun.sleepdownschedule.glass.CourseGlassOcclusionPhase
import com.xiaomanjun.sleepdownschedule.glass.CourseGlassRestorePlan
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassOcclusionPhase
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassRestorePlan
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import java.time.LocalDate

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    state: AppState,
    agentState: AppState = state,
    personalizationPreviewState: PersonalizationPreviewState,
    mode: HomeMode,
    dayViewMode: DayViewMode,
    weekViewStyle: WeekViewStyle = WeekViewStyle.BOUNDLESS,
    adaptiveMetrics: HomeAdaptiveMetrics,
    weekCardHeight: Dp,
    displayWeek: Int,
    returnToCurrentWeekRequest: Int = 0,
    displayDate: LocalDate,
    backdrop: Backdrop?,
    dayAgentBackdrop: Backdrop? = backdrop,
    floatingCourseBackdrop: Backdrop? = backdrop,
    weekHeaderBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onWeekHeaderPreview: (Int?) -> Unit = {},
    onWeekJumpSettled: (Int) -> Unit = {},
    onRenderedCardCountChanged: (HomeMode, Int) -> Unit = { _, _ -> },
    dayAgentBackgroundMotionState: DayAgentBackgroundMotionState,
    onAgentPagerSettledChange: (Boolean) -> Unit = {},
    onAgentPrepareOpen: suspend () -> Unit = {},
    onAgentDismissed: () -> Unit = {},
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit = {},
    onAgentAction: AgentActionHandler = { _, _ -> },
    onAgentImportFile: ((android.net.Uri) -> Unit)? = null,
    onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    onDeleteCourseSingleWeek: (CourseEntity, Int) -> Unit = { _, _ -> },
    onScheduleLongPress: () -> Unit = {},
    weekEditInteractionEnabled: Boolean = true,
    courseGlassOcclusionPhase: CourseGlassOcclusionPhase = CourseGlassOcclusionPhase.Live,
    courseGlassRestoredGroupKeys: Set<String> = emptySet(),
    modeMotion: HomeSwitchMotion = rememberHomeSwitchMotion(mode == HomeMode.Week, "home-mode"),
) {
    val modeStateHolder = rememberSaveableStateHolder()
    val homeOverscrollFactory = rememberHapticMiuixOverscrollFactory()
    val cardColor = remember(state.config.cardColorArgb, state.config.cardAlpha) {
        ComposeColor(state.config.cardColorArgb.toInt()).copy(alpha = state.config.cardAlpha)
    }
    val textColor = homeForegroundColor(state.config)
    var weekEditMode by remember(state.config.id) { mutableStateOf(false) }
    LaunchedEffect(mode) { if (mode != HomeMode.Week) weekEditMode = false }
    val homeAssistant = com.xiaomanjun.sleepdownschedule.feature.agent.LocalHomeAssistant.current
    SideEffect { homeAssistant?.editing = weekEditMode }
    DisposableEffect(homeAssistant) { onDispose { homeAssistant?.editing = false } }
    val copyPlacementActive = LocalCourseCopy.current?.active == true
    LaunchedEffect(copyPlacementActive) {
        if (copyPlacementActive) weekEditMode = false
    }
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // `weekCardHeight` already resolves to the adaptive value when the user has not chosen one.
    // Recomputing it here on tablets discarded live slider updates and made personalization look
    // broken even though the config was saved correctly.
    val effectiveWeekCardHeight = weekCardHeight
    val reportModeContentUnderTopBar = rememberHomeTopBarScrollReporter(mode, state.config.id, onContentUnderTopBarChange)

    LaunchedEffect(state.config.id, weekEditInteractionEnabled) {
        if (!weekEditInteractionEnabled) {
            // The multi-schedule picker temporarily owns the home gesture surface. Clear the
            // edit session before it opens so returning to Home recreates the long-press entry
            // path instead of leaving the root pointer input in its edit-mode tap-only branch.
            weekEditMode = false
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                weekEditMode = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                state.config.id,
                weekEditMode,
                onScheduleLongPress,
                weekEditInteractionEnabled
            ) {
                if (!weekEditInteractionEnabled) return@pointerInput
                if (weekEditMode) {
                    detectTapGestures(onTap = { weekEditMode = false })
                } else {
                    detectTapGestures(onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScheduleLongPress()
                    })
                }
            }
    ) {
        BackHandler(enabled = weekEditMode && mode == HomeMode.Week && LocalHomePaneVisible.current.value) {
            weekEditMode = false
        }
        HomeMode.entries.forEach { targetMode ->
            key(targetMode) {
            val reportContentUnderTopBar = remember(targetMode, reportModeContentUnderTopBar) {
                { under: Boolean ->
                    reportModeContentUnderTopBar(targetMode, under)
                }
            }
            HomeSwitchPane(
                motion = modeMotion,
                secondary = targetMode == HomeMode.Week,
                retainContent = true,
                modifier = Modifier.fillMaxSize()
            ) {
            modeStateHolder.SaveableStateProvider(targetMode.name) {
            when (targetMode) {
                HomeMode.Day -> CompositionLocalProvider(
                    LocalOverscrollFactory provides homeOverscrollFactory,
                    LocalPersonalizationPreview provides personalizationPreviewState
                ) {
                    DayScheduleScreen(
                        state = state,
                        agentState = agentState,
                        adaptiveMetrics = adaptiveMetrics,
                        displayDate = displayDate,
                        displayWeek = effectiveCurrentWeek(state.config, displayDate),
                        displayDayCount = if (dayViewMode == DayViewMode.TWO_DAY) 2 else 1,
                        cardColor = cardColor,
                        textColor = textColor,
                        backdrop = backdrop,
                        dayAgentBackdrop = dayAgentBackdrop,
                        onSwipeDay = onSwipeDay,
                        onContentUnderTopBarChange = reportContentUnderTopBar,
                        onRenderedCardCountChanged = { onRenderedCardCountChanged(HomeMode.Day, it) },
                        dayAgentBackgroundMotionState = dayAgentBackgroundMotionState,
                        onAgentPagerSettledChange = onAgentPagerSettledChange,
                        onAgentPrepareOpen = onAgentPrepareOpen,
                        onAgentDismissed = onAgentDismissed,
                        onCourseClick = onCourseClick,
                        onAddCourse = onAddCourse,
                        onAgentAction = onAgentAction,
                        onAgentImportFile = onAgentImportFile
                    )
                }
                HomeMode.Week -> key(state.config.id) {
                    val courseGlassRestorePlan = remember(
                        courseGlassOcclusionPhase,
                        courseGlassRestoredGroupKeys
                    ) {
                        CourseGlassRestorePlan(
                            phase = courseGlassOcclusionPhase,
                            restoredGroupKeys = courseGlassRestoredGroupKeys
                        )
                    }
                    CompositionLocalProvider(
                        LocalOverscrollFactory provides homeOverscrollFactory,
                        LocalPersonalizationPreview provides personalizationPreviewState,
                        LocalCourseGlassOcclusionPhase provides courseGlassOcclusionPhase,
                        LocalCourseGlassRestorePlan provides courseGlassRestorePlan
                    ) {
                        SinglePillWeekScheduleScreen(
                            state = state,
                            displayWeek = displayWeek,
                            returnToCurrentWeekRequest = returnToCurrentWeekRequest,
                            adaptiveMetrics = adaptiveMetrics,
                            cardHeight = effectiveWeekCardHeight,
                            cardColor = cardColor,
                            textColor = textColor,
                            backdrop = backdrop,
                            floatingCourseBackdrop = floatingCourseBackdrop,
                            headerBackdrop = weekHeaderBackdrop,
                            onSwipeWeek = onSwipeWeek,
                            onWeekHeaderPreview = onWeekHeaderPreview,
                            onWeekJumpSettled = onWeekJumpSettled,
                            onRenderedCardCountChanged = { onRenderedCardCountChanged(HomeMode.Week, it) },
                            onContentUnderTopBarChange = reportContentUnderTopBar,
                            style = weekViewStyle,
                            weekEditMode = weekEditMode,
                            onEnterWeekEditMode = { weekEditMode = true },
                            onUpdateCourseSingleWeek = onUpdateCourseSingleWeek,
                            conflictFocusCourseId = conflictFocusCourseId,
                            conflictFocusCourseKey = conflictFocusCourseKey,
                            onResolveCourseConflict = onResolveCourseConflict,
                            onDeleteCourseSingleWeek = onDeleteCourseSingleWeek,
                            onCourseClick = { course, week, sourceBounds ->
                                onCourseClick(course, week, sourceBounds)
                            }
                        )
                    }
                }
            }
            }
            }
            }
        }
    }
}
