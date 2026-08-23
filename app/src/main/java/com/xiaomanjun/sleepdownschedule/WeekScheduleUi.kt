package com.xiaomanjun.sleepdownschedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DatePickerDialog
import android.app.Application
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassGroupCandidate
import com.xiaomanjun.sleepdownschedule.glass.GlassGroupMaximumMembers
import com.xiaomanjun.sleepdownschedule.glass.GlassGroupPlanner
import com.xiaomanjun.sleepdownschedule.glass.GlassGroupRenderEligibility
import com.xiaomanjun.sleepdownschedule.glass.GlassSceneKeys
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSceneState
import com.xiaomanjun.sleepdownschedule.glass.adaptiveCourseGlassSampleScale
import com.xiaomanjun.sleepdownschedule.glass.glassGroupEligibility
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.sampled
import com.xiaomanjun.sleepdownschedule.glass.sleepDownGlassGroupSurface
import com.xiaomanjun.sleepdownschedule.glass.toTightLayerPlan
import com.xiaomanjun.sleepdownschedule.glass.isGlassGroupEnabled
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun SinglePillWeekScheduleScreen(
    state: AppState,
    displayWeek: Int,
    adaptiveMetrics: HomeAdaptiveMetrics,
    cardHeight: Dp,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    floatingCourseBackdrop: Backdrop? = backdrop,
    headerBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
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
    // unit while the header below derives its leading slot from the exact same boundary.
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
    val now = LocalTime.now()
    val currentPeriod = currentTimelinePeriod(state.periods, now)
    val weekBuckets = remember(state.courses, displayWeek) {
        weekCourseBuckets(state.courses, displayWeek)
    }
    val visibleCourses = weekBuckets.visibleCourses
    val weekdays = remember(weekBuckets, state.config.hideEmptyWeekends) {
        visibleWeekdaysForBuckets(weekBuckets, state.config.hideEmptyWeekends)
    }
    val periodIndexes = remember(state.periods) {
        state.periods.map { it.periodIndex }
    }
    var previousDisplayWeek by remember { mutableIntStateOf(displayWeek) }
    var weekMotionDirection by remember { mutableIntStateOf(0) }
    val outgoingCourses = remember { mutableStateOf<List<CourseEntity>?>(null) }
    val outgoingWeekdays = remember { mutableStateOf<List<Int>>(emptyList()) }
    val outgoingWeekKey = remember { mutableIntStateOf(displayWeek) }
    val outgoingDirection = remember { mutableIntStateOf(0) }
    val incomingLayerOffset = remember { Animatable(0f) }
    val outgoingLayerOffset = remember { Animatable(0f) }
    var gestureCommittedWeek by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val screenWidth = adaptiveMetrics.screenWidth
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
    val weekGridEndPadding = if (adaptiveMetrics.isLargeScreen) 0.dp else 8.dp
    val weekHeaderOuterHorizontalPadding = 4.dp
    val weekHeaderLeadingSlotWidth =
        (rowHeaderWidth - weekHeaderOuterHorizontalPadding).coerceAtLeast(0.dp)
    val weekHeaderLabelsEndPadding =
        (weekGridEndPadding - weekHeaderOuterHorizontalPadding).coerceAtLeast(0.dp)
    val transitionTravelWidth = if (adaptiveMetrics.isLargeScreen) {
        (
            adaptiveMetrics.screenWidth - horizontalContentStartPadding -
                horizontalContentEndPadding - rowHeaderWidth
            ).coerceAtLeast(1.dp)
    } else {
        screenWidth
    }
    val pagerState = rememberPagerState(
        initialPage = (displayWeek - 1).coerceAtLeast(0),
        pageCount = { state.config.totalWeeks.coerceAtLeast(1) }
    )
    LaunchedEffect(pagerState.settledPage, state.config.totalWeeks) {
        val settledWeek = (pagerState.settledPage + 1).coerceIn(1, state.config.totalWeeks.coerceAtLeast(1))
        if (settledWeek != displayWeek) {
            gestureCommittedWeek = settledWeek
            onSwipeWeek(settledWeek - displayWeek)
        }
    }
    LaunchedEffect(pagerState, displayWeek, state.config.totalWeeks) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.settledPage,
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            )
        }.distinctUntilChanged().collect { (scrolling, settledPage, pagePosition) ->
            if (!scrolling) return@collect
            val delta = pagePosition - settledPage
            val desiredPage = when {
                delta >= 0.75f -> settledPage + 1
                delta <= -0.75f -> settledPage - 1
                else -> settledPage
            }.coerceIn(0, state.config.totalWeeks.coerceAtLeast(1) - 1)
            val desiredWeek = desiredPage + 1
            if (desiredWeek != displayWeek) {
                gestureCommittedWeek = desiredWeek
                onSwipeWeek(desiredWeek - displayWeek)
            }
        }
    }
    LaunchedEffect(displayWeek, state.config.totalWeeks) {
        val targetPage = (displayWeek - 1).coerceIn(0, state.config.totalWeeks.coerceAtLeast(1) - 1)
        if (pagerState.settledPage != targetPage && gestureCommittedWeek == 0) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(displayWeek) {
        val direction = (displayWeek - previousDisplayWeek).coerceIn(-1, 1)
        if (direction != 0 && displayWeek == gestureCommittedWeek) {
            outgoingCourses.value = null
            incomingLayerOffset.snapTo(0f)
            outgoingLayerOffset.snapTo(0f)
            weekMotionDirection = 0
            previousDisplayWeek = displayWeek
            gestureCommittedWeek = 0
            return@LaunchedEffect
        }
        if (direction != 0) {
            val oldWeek = previousDisplayWeek
            val oldBuckets = weekCourseBuckets(state.courses, oldWeek)
            outgoingCourses.value = oldBuckets.visibleCourses
            outgoingWeekdays.value = visibleWeekdaysForBuckets(oldBuckets, state.config.hideEmptyWeekends)
            outgoingWeekKey.intValue = oldWeek
            outgoingDirection.intValue = direction
        }
        weekMotionDirection = direction
        previousDisplayWeek = displayWeek
            if (direction != 0) {
                val offscreenOffset = with(density) { (transitionTravelWidth + 88.dp).toPx() } * direction
                incomingLayerOffset.snapTo(offscreenOffset)
                outgoingLayerOffset.snapTo(0f)
                launch {
                    incomingLayerOffset.animateTo(
                        0f,
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 300f)
                    )
                }
                launch {
                    outgoingLayerOffset.animateTo(
                        -offscreenOffset,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 280f)
                    )
                    if (outgoingWeekKey.intValue != displayWeek) {
                        outgoingCourses.value = null
                    outgoingLayerOffset.snapTo(0f)
                }
            }
            launch {
                delay(220)
                if (outgoingWeekKey.intValue != displayWeek) outgoingCourses.value = null
            }
        } else {
            incomingLayerOffset.snapTo(0f)
            outgoingLayerOffset.snapTo(0f)
        }
    }
    val scrollState = rememberScrollState()
    val contentUnderTopBar by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    LaunchedEffect(contentUnderTopBar) {
        onContentUnderTopBarChange(contentUnderTopBar)
    }
    var overlayHostBounds by remember { mutableStateOf<Rect?>(null) }
    val weekEditOverlay = rememberWeekEditOverlayController(
        scrollState = scrollState,
        scheduleId = state.config.id
    )
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
    // Keep equal drawing/scrolling room on both sides while edit chrome is visible. The
    // previous top-only gutter protected the first-row delete pill but left the last-row card
    // and resize handle inside the pager's clip boundary, so the final grid cell could neither
    // be reached reliably nor be shown completely during the edit-mode entrance frame.
    val editControlOverflow = if (retainEditControlOverflow) 12.dp else 0.dp
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

    CompositionLocalProvider(LocalWeekEditMotionState provides weekEditOverlay) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayHostBounds = it.boundsInRoot() }
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier.padding(
                start = horizontalContentStartPadding,
                end = horizontalContentEndPadding
            )
        ) {
            Spacer(Modifier.height(topSpacerHeight))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                WeekSwitchButton(-1, state.config, headerBackdrop, enabled = displayWeek > 1) { onSwipeWeek(-1) }
                Text(
                    text = "第${displayWeek}周",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                WeekSwitchButton(1, state.config, headerBackdrop, enabled = displayWeek < state.config.totalWeeks) { onSwipeWeek(1) }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
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
                                    fontWeight = FontWeight.SemiBold,
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
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = weekHeaderLabelsEndPadding)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight * state.periods.size + editControlOverflow)
                        .then(if (retainEditControlOverflow) Modifier else Modifier.clipToBounds())
                ) {
                    Column(
                        modifier = Modifier
                            .width(rowHeaderWidth)
                            .fillMaxHeight()
                            .zIndex(2f)
                    ) {
                        state.periods.forEach { period ->
                            Box(
                                modifier = Modifier
                                    .height(cardHeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val isCurrent = currentPeriod?.periodIndex == period.periodIndex
                                    Box(
                                        modifier = if (isCurrent) Modifier
                                            .background(ComposeColor(0xFF0A84FF), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                        else Modifier,
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent) {
                                            Text(
                                                period.periodIndex.toString(),
                                                fontSize = 13.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = ComposeColor.White
                                            )
                                        } else {
                                            HomeReadableText(
                                                period.periodIndex.toString(),
                                                fontSize = 13.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = textColor
                                            )
                                        }
                                    }
                                    HomeReadableText(
                                        period.startTime,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Center,
                                        color = textColor.copy(alpha = 0.86f)
                                    )
                                    HomeReadableText(
                                        period.endTime,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Center,
                                        color = textColor.copy(alpha = 0.86f)
                                    )
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
                        outgoingCourses.value?.let { oldCourses ->
                            WeekCourseColumnsLayer(
                                modifier = Modifier.padding(
                                    start = rowHeaderWidth,
                                    end = weekGridEndPadding
                                ),
                                courses = oldCourses,
                                weekdays = outgoingWeekdays.value,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                // This subtree is recorded into stationaryCoursesBackdrop. It must
                                // never receive the combined backdrop that includes that recorder.
                                floatingBackdrop = backdrop,
                                config = state.config,
                                weekMotionDirection = outgoingDirection.intValue,
                                outgoing = true,
                                layerOffset = outgoingLayerOffset,
                            editMode = false,
                            editWeek = outgoingWeekKey.intValue,
                            allWeekCourses = oldCourses,
                            editScrollState = scrollState,
                            onCourseClick = { course, sourceBounds -> onCourseClick(course, outgoingWeekKey.intValue, sourceBounds) }
                            )
                        }
                        val pagerZeroOffset = remember { Animatable(0f) }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .offset(y = -editControlOverflow)
                                .fillMaxWidth()
                                .height(cardHeight * state.periods.size + editControlOverflow * 2f),
                            userScrollEnabled = !weekEditMode,
                            // Keep the pager topology stable while a home overlay opens/closes.
                            // Disposing the adjacent week at the exact frame Personalization
                            // starts, then rebuilding it on close, competes with the full-screen
                            // glass/background layers and is visible as a week-only hitch.
                            beyondViewportPageCount = 1,
                            key = { it }
                        ) { page ->
                            val pageWeek = page + 1
                            val pageBuckets = remember(state.courses, pageWeek) {
                                weekCourseBuckets(state.courses, pageWeek)
                            }
                            val pageCourses = pageBuckets.visibleCourses
                            val pageWeekdays = remember(pageBuckets, state.config.hideEmptyWeekends) {
                                visibleWeekdaysForBuckets(pageBuckets, state.config.hideEmptyWeekends)
                            }
                            val isActivePage = pageWeek == displayWeek && pagerState.settledPage == page
                            WeekCourseColumnsLayer(
                                modifier = Modifier.padding(
                                    start = rowHeaderWidth,
                                    top = editControlOverflow,
                                    bottom = editControlOverflow,
                                    end = weekGridEndPadding
                                ),
                                courses = pageCourses,
                                weekdays = pageWeekdays,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                // The lifted combined backdrop is reserved for WeekEditOverlayHost,
                                // which is outside stationaryCoursesBackdrop.
                                floatingBackdrop = backdrop,
                                config = state.config,
                                weekMotionDirection = if (isActivePage) weekMotionDirection else 0,
                                outgoing = false,
                                layerOffset = if (isActivePage) incomingLayerOffset else pagerZeroOffset,
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
                                activeOverlayCourseId = weekEditOverlay.request?.course?.id,
                                activeOverlayTargetKey = weekEditOverlay.committedTargetKey,
                                activeOverlayTargetWeek = weekEditOverlay.committedTargetWeek,
                                onStartWeekEditOverlay = weekEditOverlay::start,
                                onDragWeekEditOverlay = { delta ->
                                    weekEditOverlay.drag(delta, overlayScreenHeightPx, overlayEdgePx, with(density) { 4.dp.toPx() })
                                },
                                onFinishMoveOverlay = { velocity ->
                                    weekEditOverlay.finishMove(
                                        velocity = velocity,
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
                                onCourseClick = { course, sourceBounds ->
                                    onCourseClick(course, pageWeek, sourceBounds)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(WeekDockScrollPadding))
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
            pointerPosition = weekEditOverlay.pointerPosition,
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

@Composable
private fun WeekEditOverlayHost(
    request: WeekEditOverlayRequest?,
    hostBounds: Rect?,
    offsetX: Float,
    offsetY: Float,
    overlayScale: Float,
    overlayAlpha: Float,
    liftProgress: Float,
    rotation: Float,
    pointerPosition: Offset,
    gestureActive: Boolean,
    gridOffsetY: Float,
    gridScrollCompensationY: Float,
    heightPx: Float,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val req = request ?: return
    val host = hostBounds ?: return
    if (heightPx <= 1f || req.sourceBounds.width <= 1f) return
    val density = LocalDensity.current
    val widthDp = with(density) { req.sourceBounds.width.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val windowSize = currentWindowSizeDp()
    val cardCorner = adaptiveWeekCardCornerRadius(
        cardWidth = widthDp,
        cardHeight = heightDp,
        windowWidth = windowSize.width,
        windowHeight = windowSize.height
    )
    val cardShape = remember(cardCorner) { RoundedRectangle(cardCorner) }
    val highlightScope = rememberCoroutineScope()
    val glowRadiusPx = with(density) { (widthDp * 1.9f).coerceIn(84.dp, 140.dp).toPx() }
    val dragHighlight = remember(highlightScope, glowRadiusPx) {
        InteractiveHighlight(
            animationScope = highlightScope,
            radius = { glowRadiusPx },
            ambientAlpha = 0f,
            spotAlpha = 0.115f,
            fallbackAlpha = 0.075f
        )
    }
    val localPointer = Offset(pointerPosition.x - host.left, pointerPosition.y - host.top)
    LaunchedEffect(localPointer, gestureActive) {
        dragHighlight.updateExternal(localPointer, gestureActive, followPointerExactly = true)
    }
    val left = req.sourceBounds.left - host.left + offsetX
    val top = req.sourceBounds.top - host.top + offsetY
    val cardGlowRadiusPx = with(density) { (widthDp * 1.65f).coerceIn(72.dp, 120.dp).toPx() }
    val cardHighlight = remember(highlightScope, cardGlowRadiusPx) {
        InteractiveHighlight(
            animationScope = highlightScope,
            radius = { cardGlowRadiusPx },
            ambientAlpha = 0f,
            spotAlpha = 0.068f,
            fallbackAlpha = 0.046f
        )
    }
    val liftPx = with(density) { 8.dp.toPx() }
    val touchTransformOrigin = remember(req.initialPointerPosition, req.sourceBounds) {
        TransformOrigin(
            pivotFractionX = (
                (req.initialPointerPosition.x - req.sourceBounds.left) /
                    req.sourceBounds.width.coerceAtLeast(1f)
                ).coerceIn(0f, 1f),
            pivotFractionY = (
                (req.initialPointerPosition.y - req.sourceBounds.top) /
                    req.sourceBounds.height.coerceAtLeast(1f)
                ).coerceIn(0f, 1f)
        )
    }
    val localCardCenter = Offset(
        x = left + req.sourceBounds.width / 2f,
        y = top + heightPx / 2f - liftPx * liftProgress
    )
    val cardGlowPressed = gestureActive || liftProgress > 0.01f
    LaunchedEffect(cardGlowPressed) {
        cardHighlight.updateExternal(
            Offset(cardGlowRadiusPx, cardGlowRadiusPx),
            cardGlowPressed
        )
    }
    val cardGlowDiameterDp = with(density) { (cardGlowRadiusPx * 2f).toDp() }
    val target = when (req.mode) {
        WeekEditOverlayMode.Move -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex + (offsetX / req.gridColumnWidthPx).roundToInt(),
            startPeriod = req.periodIndex + (gridOffsetY / req.periodRowHeightPx).roundToInt(),
            span = req.currentSpan,
            weekdayCount = req.weekdayCount
        )
        WeekEditOverlayMode.Resize -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex,
            startPeriod = req.periodIndex,
            span = (heightPx / req.periodRowHeightPx).roundToInt().coerceIn(1, req.maxSpan),
            weekdayCount = req.weekdayCount
        )
    }
    val previewCourse = when (req.mode) {
        WeekEditOverlayMode.Move -> req.course.copy(weekday = target.weekday, periods = target.periods)
        WeekEditOverlayMode.Resize -> req.course.copy(periods = target.periods)
    }
    val conflict = !target.valid ||
        hasWeekCourseEditConflict(req.course, previewCourse, req.weekCourses, req.editWeek)
    val previewLeft = req.sourceBounds.left - host.left + (target.weekday - req.dayIndex) * req.gridColumnWidthPx
    val previewTop = req.sourceBounds.top - host.top - gridScrollCompensationY +
        ((target.periods.firstOrNull() ?: req.periodIndex) - req.periodIndex) * req.periodRowHeightPx
    val previewHeight = (req.periodRowHeightPx * target.periods.size.coerceAtLeast(1) - with(density) { 4.dp.toPx() }).coerceAtLeast(with(density) { 18.dp.toPx() })
    val animatedPreviewLeft by animateFloatAsState(
        targetValue = previewLeft,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-left"
    )
    val animatedPreviewTop by animateFloatAsState(
        targetValue = previewTop,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "week-edit-preview-top"
    )
    val animatedPreviewHeight by animateFloatAsState(
        targetValue = previewHeight,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
        label = "week-edit-preview-height"
    )
    val elevationPx = with(density) { 18.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
            .then(dragHighlight.modifier)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (localCardCenter.x - cardGlowRadiusPx).roundToInt(),
                        (localCardCenter.y - cardGlowRadiusPx).roundToInt()
                    )
                }
                .size(cardGlowDiameterDp)
                .graphicsLayer { alpha = overlayAlpha }
                .then(cardHighlight.modifier)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedPreviewLeft.roundToInt(), animatedPreviewTop.roundToInt()) }
                .width(widthDp)
                .height(with(density) { animatedPreviewHeight.toDp() })
                .graphicsLayer { alpha = 0.18f + liftProgress * 0.72f }
                .clip(cardShape)
                .background(
                    if (conflict) MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
                    else ComposeColor.Gray.copy(alpha = 0.24f)
                )
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(widthDp)
                .height(heightDp)
                .graphicsLayer {
                    // A resize handle owns only the lower edge: keep the top edge and width fixed
                    // instead of applying the move gesture's two-axis lifted scale around the
                    // fingertip. The card can therefore grow/shrink in exactly one direction.
                    transformOrigin = if (req.mode == WeekEditOverlayMode.Resize) {
                        TransformOrigin(0.5f, 0f)
                    } else {
                        touchTransformOrigin
                    }
                    scaleX = if (req.mode == WeekEditOverlayMode.Resize) 1f else overlayScale
                    scaleY = if (req.mode == WeekEditOverlayMode.Resize) 1f else overlayScale
                    translationY = -liftPx * liftProgress
                    rotationZ = rotation
                    alpha = overlayAlpha
                    shadowElevation = elevationPx * liftProgress
                    shape = cardShape
                }
        ) {
            CourseGlassCard(
                backdrop = backdrop,
                config = config,
                course = req.course,
                modifier = Modifier.fillMaxSize(),
                shape = cardShape,
                onClick = null
            ) {
                WeekCourseOverlayCardContent(req.course, config)
            }
            if (req.mode == WeekEditOverlayMode.Resize) {
                WeekResizeCornerHandle(
                    config = config,
                    selected = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(44.dp)
                        .zIndex(2f)
                )
            }
        }
    }
}

@Composable
private fun WeekCourseOverlayCardContent(course: CourseEntity, config: ScheduleConfigEntity) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightDp = maxHeight.value
        val widthDp = maxWidth.value
        val locationText = course.location.orEmpty()
        val hasLocation = locationText.isNotBlank()
        val hasTeacher = !course.teacher.isNullOrBlank()
        val textColor = if (config.courseCardGlassEnabled) {
            LocalAdaptiveGlass.current.contentColor
        } else if (glassUsesLightStyle(config)) {
            ComposeColor.Black
        } else {
            ComposeColor.White
        }
        val compact = heightDp < 78f
        val tiny = heightDp < 52f
        val verticalPadding = when {
            tiny -> 1.dp
            compact -> 2.dp
            else -> 2.5.dp
        }
        val horizontalPadding = if (widthDp < 54f) 4.dp else 5.dp
        val tabletFontBoost = if (widthDp >= 120f) 1.10f else 1f
        val previewFontScale = LocalPersonalizationPreview.current?.cardFontScale
        val courseFontScale = ((previewFontScale ?: config.courseCardFontScale) * tabletFontBoost)
            .coerceIn(0.80f, 1.35f)
        fun scaledOverlayText(value: TextUnit): TextUnit =
            scaledWeekText((value.value * courseFontScale).sp, density.fontScale)
        val nameFont = scaledOverlayText(if (tiny) 8.8.sp else if (compact) 9.7.sp else 10.7.sp)
        val nameLineHeight = scaledOverlayText(if (tiny) 8.2.sp else if (compact) 9.1.sp else 10.0.sp)
        val locationFont = scaledOverlayText(if (tiny) 8.1.sp else if (compact) 8.7.sp else 9.5.sp)
        val locationLineHeight = scaledOverlayText(if (tiny) 8.0.sp else if (compact) 8.6.sp else 9.3.sp)
        val teacherFont = scaledOverlayText(8.4.sp)
        val teacherLineHeight = scaledOverlayText(7.9.sp)
        val contentWidthPx = with(density) { (maxWidth - horizontalPadding * 2f).coerceAtLeast(24.dp).toPx() }
        val availableTextPx = with(density) { (maxHeight - verticalPadding * 2f).coerceAtLeast(0.dp).toPx() }

        fun estimatedLines(text: String, fontSize: TextUnit): Int {
            if (text.isBlank()) return 0
            val averageCharPx = with(density) { fontSize.toPx() } * 1.08f
            val charsPerLine = (contentWidthPx / averageCharPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            return ceil(text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
        }

        val canShowTeacher = hasTeacher && heightDp >= 52f
        val teacherPx = if (canShowTeacher) with(density) { teacherLineHeight.toPx() } else 0f
        val usablePx = (availableTextPx - teacherPx).coerceAtLeast(0f)
        val averageLinePx = minOf(
            with(density) { nameLineHeight.toPx() },
            with(density) { locationLineHeight.toPx() }
        ).coerceAtLeast(1f)
        val totalSlots = (usablePx / averageLinePx).toInt().coerceAtLeast(1)
        val maxNameLines = when {
            heightDp >= 150f -> 12
            heightDp >= 112f -> 9
            heightDp >= 78f -> 6
            else -> 4
        }
        val wantedNameLines = estimatedLines(course.name, nameFont).coerceIn(1, maxNameLines)
        val wantedLocationLines = if (hasLocation) {
            estimatedLines(locationText, locationFont).coerceIn(1, if (heightDp >= 150f) 4 else if (heightDp >= 96f) 3 else 2)
        } else {
            0
        }
        val nameMinimum = 1
        val locationMinimum = if (hasLocation && (totalSlots >= 2 || tiny)) 1 else 0
        var remainingSlots = (totalSlots - nameMinimum - locationMinimum).coerceAtLeast(0)
        var nameLines = nameMinimum
        var locationLines = locationMinimum
        var nameNeed = (wantedNameLines - nameLines).coerceAtLeast(0)
        var locationNeed = (wantedLocationLines - locationLines).coerceAtLeast(0)
        while (remainingSlots > 0 && (nameNeed > 0 || locationNeed > 0)) {
            if (nameNeed >= locationNeed && nameNeed > 0) {
                nameLines += 1
                nameNeed -= 1
            } else if (locationNeed > 0) {
                locationLines += 1
                locationNeed -= 1
            } else {
                nameLines += 1
                nameNeed -= 1
            }
            remainingSlots -= 1
        }
        if (remainingSlots > 0 && nameLines < maxNameLines) {
            val extraNameLines = minOf(remainingSlots, maxNameLines - nameLines)
            nameLines += extraNameLines
            remainingSlots -= extraNameLines
        }
        if (remainingSlots > 0 && hasLocation) {
            locationLines += remainingSlots
        }
        if (tiny && hasLocation) {
            locationLines = 1
            nameLines = (totalSlots - locationLines).coerceAtLeast(1)
        }
        val renderedLocationLines = minOf(locationLines, wantedLocationLines).coerceAtLeast(0)
        val locationReserve = if (hasLocation && renderedLocationLines > 0) {
            with(density) { (locationLineHeight.toPx() * renderedLocationLines).toDp() }
        } else {
            0.dp
        }
        val teacherReserve = if (canShowTeacher) {
            with(density) { teacherLineHeight.toPx().toDp() }
        } else {
            0.dp
        }
        val centerReserve = maxOf(locationReserve, teacherReserve) + 1.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (hasLocation && locationLines > 0) {
                Text(
                    locationText,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    fontSize = locationFont,
                    lineHeight = locationLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
                    maxLines = locationLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                course.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(vertical = centerReserve),
                fontSize = nameFont,
                lineHeight = nameLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (canShowTeacher) {
                Text(
                    course.teacher,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    fontSize = teacherFont,
                    lineHeight = teacherLineHeight,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WeekResizeCornerHandle(
    config: ScheduleConfigEntity,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val handleLight = glassUsesLightStyle(config)
    val handleColor = if (handleLight) {
        ComposeColor.Black.copy(alpha = if (selected) 0.74f else 0.58f)
    } else {
        ComposeColor.White.copy(alpha = if (selected) 0.88f else 0.72f)
    }
    Box(
        modifier = modifier.graphicsLayer {
            val scale = if (selected) 1.14f else 1f
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.BottomEnd
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidth = 7.5.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val right = size.width - halfStroke
            val bottom = size.height - halfStroke
            val radius = 9.dp.toPx()
            val arm = 3.5.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(right, (bottom - radius - arm).coerceAtLeast(halfStroke))
                cubicTo(
                    right,
                    bottom - radius * 0.35f,
                    right - radius * 0.35f,
                    bottom,
                    right - radius,
                    bottom
                )
                lineTo((right - radius - arm).coerceAtLeast(halfStroke), bottom)
            }
            drawPath(
                path = path,
                color = handleColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun WeekSwitchButton(direction: Int, config: ScheduleConfigEntity, backdrop: Backdrop?, enabled: Boolean, onClick: () -> Unit) {
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) HomeLightGlassSurfaceColor else ComposeColor(0xFF121212)
    val textColor = glassForegroundColor(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            modifier = Modifier
                .size(34.dp)
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
            modifier = Modifier.size(34.dp).graphicsLayer(alpha = if (enabled) 1f else 0.35f),
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

@Composable
private fun WeekPagerHeaderLabels(
    pagerState: PagerState,
    displayWeek: Int,
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity,
    today: LocalDate,
    textColor: ComposeColor,
    modifier: Modifier = Modifier
) {
    val maxPage = (config.totalWeeks - 1).coerceAtLeast(0)
    val currentPage = pagerState.currentPage.coerceIn(0, maxPage)
    val settledPage = pagerState.settledPage.coerceIn(0, maxPage)
    val pageOffset = pagerState.currentPageOffsetFraction
    val adjacentPage = when {
        pageOffset > 0f -> (currentPage + 1).coerceAtMost(maxPage)
        pageOffset < 0f -> (currentPage - 1).coerceAtLeast(0)
        else -> currentPage
    }

    fun weekdaysForPage(page: Int): List<Int> {
        val buckets = weekCourseBuckets(courses, page + 1)
        return visibleWeekdaysForBuckets(buckets, config.hideEmptyWeekends)
    }

    val currentWeekdays = remember(courses, currentPage, config.hideEmptyWeekends) {
        weekdaysForPage(currentPage)
    }
    val adjacentWeekdays = remember(courses, adjacentPage, config.hideEmptyWeekends) {
        weekdaysForPage(adjacentPage)
    }
    val slideHeader = abs(pageOffset) > 0.0001f &&
        adjacentPage != currentPage &&
        currentWeekdays != adjacentWeekdays

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
                    val translation = ((page - currentPage) - pageOffset) * widthPx
                    WeekdayHeaderLabels(
                        weekdays = pageWeekdays,
                        weekStart = scheduleWeekStartDate(config, page + 1, today),
                        today = today,
                        textColor = textColor,
                        modifier = Modifier.graphicsLayer { translationX = translation }
                    )
                }
            }
        } else {
            val displayedPage = (displayWeek - 1).coerceIn(0, maxPage)
            val displayedWeekdays = remember(courses, displayedPage, config.hideEmptyWeekends) {
                weekdaysForPage(displayedPage)
            }
            WeekdayHeaderLabels(
                weekdays = displayedWeekdays,
                weekStart = scheduleWeekStartDate(config, displayedPage + 1, today),
                today = today,
                textColor = textColor
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdays.forEach { day ->
            val date = weekStart.plusDays((day - 1).toLong())
            val isToday = day == today.dayOfWeek.toChineseWeekday()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (isToday) Modifier
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        else Modifier.padding(vertical = 2.dp, horizontal = 2.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "周${weekdayLabel(day)}",
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "${date.monthValue}/${date.dayOfMonth}",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WeekHeaderPill(backdrop: Backdrop?, config: ScheduleConfigEntity, selected: Boolean, content: @Composable () -> Unit) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(50),
        tokens = homeHeaderGlassTokens(
            lightGlass = glassUsesLightStyle(config),
            blurScale = config.homeChromeBlurScale
        ),
        selected = selected,
        baseSurfaceColorOverride = if (glassUsesLightStyle(config)) HomeLightGlassSurfaceColor else null,
        content = content
    )
}

private data class WeekRenderedSegment(
    val groupIndex: Int,
    val group: WeekConflictGroup,
    val segment: WeekCourseSegment
)

private fun renderedWeekSegments(
    conflictGroups: List<WeekConflictGroup>,
    conflictFocusCourseId: Long?,
    conflictFocusCourseKey: String?
): List<WeekRenderedSegment> = buildList {
    conflictGroups.forEachIndexed { groupIndex, group ->
        val visibleCourse = group.courses.firstOrNull { it.id == conflictFocusCourseId }
            ?: group.courses.firstOrNull {
                conflictFocusCourseKey != null &&
                    it.occurrenceOverrideKey() == conflictFocusCourseKey
            }
            ?: group.courses.first()
        val visibleSegments = group.segments.filter { it.course.id == visibleCourse.id }
        val segments = if (visibleCourse.hasCustomTime()) {
            visibleSegments.minByOrNull { it.startPosition }?.let(::listOf).orEmpty()
        } else {
            visibleSegments
        }
        segments.forEach { segment ->
            add(WeekRenderedSegment(groupIndex, group, segment))
        }
    }
}

@Composable
fun WeekDayColumn(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    cardColor: ComposeColor,
    emptyBackground: ComposeColor,
    backdrop: Backdrop?,
    floatingBackdrop: Backdrop? = backdrop,
    config: ScheduleConfigEntity,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    gridColumnWidth: Dp = 0.dp,
    periodRowHeight: Dp = cardHeight,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    editMode: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    weekdayCount: Int = 7,
    editScrollState: ScrollState? = null,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onDragStateChanged: (dayIndex: Int?, courseId: Long?) -> Unit = { _, _ -> },
    composedCourseCardCount: Int = courses.size,
    draggingCourseId: Long? = null,
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {}
) {
    val density = LocalDensity.current
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val conflictGroups = remember(courses, periodIndexes, periods) {
        buildWeekConflictGroups(courses, periodIndexes, periods)
    }
    val renderedSegments = remember(
        conflictGroups,
        conflictFocusCourseId,
        conflictFocusCourseKey
    ) {
        renderedWeekSegments(
            conflictGroups = conflictGroups,
            conflictFocusCourseId = conflictFocusCourseId,
            conflictFocusCourseKey = conflictFocusCourseKey
        )
    }
    val glassSceneState = LocalGlassSceneState.current
    val quality = LocalGlassQuality.current
    val previewState = LocalPersonalizationPreview.current
    val startupPhase = LocalStartupPhase.current
    val hasWallpaper = config.hasAnyWallpaper()
    val tokens = GlassTokens.courseCard(config.courseCardBlur)
    val liveBlur = previewState?.cardBlur ?: config.courseCardBlur
    val effectFrame = courseCardGlassEffectFrame(
        tokens = tokens,
        liveBlur = liveBlur,
        quality = quality,
        hasWallpaper = hasWallpaper
    )
    val courseBackdropSampleScale = adaptiveCourseGlassSampleScale(
        composedCardCount = composedCourseCardCount,
        enabled = !editMode &&
            glassSceneState?.isGlassGroupEnabled(GlassSceneKeys.WeekCourseCards) == true
    )
    val windowSize = currentWindowSizeDp()
    val mayGroupCourseCards = glassSceneState != null &&
        backdrop != null &&
        config.courseCardGlassEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        startupPhase == StartupPhase.FullQuality &&
        !editMode &&
        draggingCourseId == null &&
        !weekMotionOutgoing &&
        layerOffset?.isRunning != true &&
        abs(layerOffset?.value ?: 0f) < 0.5f &&
        activeOverlayCourseId == null &&
        activeOverlayTargetKey == null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight * periods.size.toFloat())
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val horizontalInsetPx = with(density) { 2.dp.roundToPx().toFloat() }
        val groupedCandidates = remember(
            mayGroupCourseCards,
            renderedSegments,
            periods,
            cardHeight,
            maxWidth,
            gridColumnWidth,
            windowSize,
            density.density
        ) {
            if (!mayGroupCourseCards) {
                emptyList()
            } else {
                renderedSegments.map { rendered ->
                    val segment = rendered.segment
                    val exactPlacement = exactTimeWeekPlacement(segment.course, periods)
                    val segmentTopRows = exactPlacement?.topRows ?: segment.startPosition.toFloat()
                    val segmentHeightRows = exactPlacement?.heightRows ?: segment.span.toFloat()
                    val segmentHeight = if (exactPlacement != null) {
                        (cardHeight * segmentHeightRows).coerceAtLeast(1.dp)
                    } else {
                        (cardHeight * segmentHeightRows - 4.dp).coerceAtLeast(18.dp)
                    }
                    val segmentTopInset = if (exactPlacement != null) 0.dp else 2.dp
                    val topPx = with(density) {
                        (cardHeight * segmentTopRows + segmentTopInset).roundToPx().toFloat()
                    }
                    val heightPx = with(density) {
                        segmentHeight.roundToPx().toFloat()
                    }.coerceAtLeast(1f)
                    val cardWidthForCorner = gridColumnWidth.takeIf { it > 0.dp } ?: maxWidth
                    val cardCorner = adaptiveWeekCardCornerRadius(
                        cardWidth = (cardWidthForCorner - 4.dp).coerceAtLeast(1.dp),
                        cardHeight = segmentHeight,
                        windowWidth = windowSize.width,
                        windowHeight = windowSize.height
                    )
                    GlassGroupCandidate(
                        id = "${segment.course.id}:${segment.startPosition}:${segment.endPosition}:${rendered.groupIndex}",
                        domain = GlassBackdropDomain.Content,
                        materialKey = "week-course-card-liquid",
                        boundsInViewport = Rect(
                            left = horizontalInsetPx,
                            top = topPx,
                            right = (viewportWidthPx - horizontalInsetPx)
                                .coerceAtLeast(horizontalInsetPx + 1f),
                            bottom = topPx + heightPx
                        ),
                        cornerRadiusPx = with(density) { cardCorner.toPx() }
                    )
                }
            }
        }
        val groupViewport = remember(viewportWidthPx, viewportHeightPx) {
            Rect(0f, 0f, viewportWidthPx, viewportHeightPx)
        }
        val groupPlans = remember(groupViewport, groupedCandidates) {
            GlassGroupPlanner.plan(
                viewport = groupViewport,
                candidates = groupedCandidates,
                maxMembersPerPlan = GlassGroupMaximumMembers
            )
        }
        val groupLayerPlans = remember(groupPlans) {
            groupPlans.map { it.toTightLayerPlan() }
        }
        val sampledGroupLayerPlans = remember(groupLayerPlans, courseBackdropSampleScale) {
            groupLayerPlans.map { it.sampled(courseBackdropSampleScale) }
        }
        val groupedSurfaceEnabled = mayGroupCourseCards &&
            groupedCandidates.size > sampledGroupLayerPlans.size &&
            sampledGroupLayerPlans.isNotEmpty() &&
            sampledGroupLayerPlans.all { layerPlan ->
                requireNotNull(glassSceneState).glassGroupEligibility(
                    sceneKey = GlassSceneKeys.WeekCourseCards,
                    plan = layerPlan.localPlan,
                    effectFrame = effectFrame
                ) == GlassGroupRenderEligibility.Eligible
            }

        if (groupedSurfaceEnabled) {
            val activeSceneState = requireNotNull(glassSceneState)
            val activeBackdrop = requireNotNull(backdrop)
            sampledGroupLayerPlans.forEachIndexed { index, layerPlan ->
                val plan = layerPlan.localPlan
                val layerWidth = with(density) { layerPlan.size.width.toDp() }
                val layerHeight = with(density) { layerPlan.size.height.toDp() }
                key("week-course-glass-group", index, plan.members.first().id) {
                    Box(
                        modifier = Modifier
                            .offset { layerPlan.offsetInViewport }
                            .size(layerWidth, layerHeight)
                            .sleepDownGlassGroupSurface(
                                backdrop = activeBackdrop,
                                plan = plan,
                                material = tokens,
                                effectFrame = effectFrame,
                                sceneState = activeSceneState,
                                sceneKey = GlassSceneKeys.WeekCourseCards,
                                sampleScale = courseBackdropSampleScale
                            )
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            periods.forEach { EmptyWeekCell(cardHeight, emptyBackground) }
        }
        renderedSegments.forEach { rendered ->
            val groupIndex = rendered.groupIndex
            val group = rendered.group
            val segment = rendered.segment
            val underlyingSegment = group.segments
                .asSequence()
                .filter { it.course.id != segment.course.id }
                .filter {
                    it.startPosition <= segment.endPosition &&
                        it.endPosition >= segment.startPosition
                }
                .sortedWith(
                    compareBy<WeekCourseSegment> { it.startPosition }
                        .thenBy { it.endPosition }
                        .thenBy { it.course.id }
                )
                .firstOrNull()
            val exactPlacement = exactTimeWeekPlacement(segment.course, periods)
            val segmentTopRows = exactPlacement?.topRows ?: segment.startPosition.toFloat()
            val segmentHeightRows = exactPlacement?.heightRows ?: segment.span.toFloat()
            val segmentHeight = if (exactPlacement != null) {
                (cardHeight * segmentHeightRows).coerceAtLeast(1.dp)
            } else {
                (cardHeight * segmentHeightRows - 4.dp).coerceAtLeast(18.dp)
            }
            val segmentTopInset = if (exactPlacement != null) 0.dp else 2.dp
            Box(
                modifier = Modifier
                    .offset(y = cardHeight * segmentTopRows + segmentTopInset)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(segmentHeight)
                    .zIndex(groupIndex.toFloat())
            ) {
                WeekCourseBlock(
                    course = segment.course,
                    periods = periods,
                    height = segmentHeight,
                    cardColor = cardColor,
                    backdrop = backdrop,
                    floatingBackdrop = floatingBackdrop,
                    config = config,
                    weekMotionDirection = weekMotionDirection,
                    weekMotionOutgoing = weekMotionOutgoing,
                    dayIndex = dayIndex,
                    periodIndex = periodIndexes[segment.startPosition],
                    gridColumnWidth = gridColumnWidth,
                    periodRowHeight = periodRowHeight,
                    layerOffset = layerOffset,
                    layerTravel = layerTravel,
                    stackIndex = groupIndex,
                    conflictWarning = group.hasConflict,
                    conflictUnderlyingCourse = underlyingSegment?.course,
                    conflictUnderlyingPeriodIndex = underlyingSegment
                        ?.let { periodIndexes[it.startPosition] },
                    conflictUnderlyingSpan = underlyingSegment?.span ?: 0,
                    onResolveConflict = { moved ->
                        onResolveCourseConflict(segment.course, moved)
                    },
                    editMode = editMode,
                    editWeek = editWeek,
                    allWeekCourses = allWeekCourses,
                    weekdayCount = weekdayCount,
                    editScrollState = editScrollState,
                    onEnterEditMode = onEnterEditMode,
                    onUpdateSingleWeekCourse = onUpdateSingleWeekCourse,
                    onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                    onCourseClick = onCourseClick,
                    onDragStateChanged = onDragStateChanged,
                    activeOverlayCourseId = activeOverlayCourseId,
                    activeOverlayTargetKey = activeOverlayTargetKey,
                    activeOverlayTargetWeek = activeOverlayTargetWeek,
                    onStartWeekEditOverlay = onStartWeekEditOverlay,
                    onDragWeekEditOverlay = onDragWeekEditOverlay,
                    onFinishMoveOverlay = onFinishMoveOverlay,
                    onFinishResizeOverlay = onFinishResizeOverlay,
                    onCancelWeekEditOverlay = onCancelWeekEditOverlay,
                    renderCardSurface = !groupedSurfaceEnabled,
                    backdropSampleScale = courseBackdropSampleScale
                )
                exactPlacement?.let {
                    val labelColor = glassForegroundColor(config)
                    Text(
                                text = segment.course.customStartTime.orEmpty(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-10).dp)
                                    .zIndex(12f),
                                color = labelColor,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                    )
                    Text(
                                text = segment.course.customEndTime.orEmpty(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 10.dp)
                                    .zIndex(12f),
                                color = labelColor,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun WeekCourseColumnsLayer(
    modifier: Modifier = Modifier,
    courses: List<CourseEntity>,
    weekdays: List<Int>,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    cardColor: ComposeColor,
    backdrop: Backdrop?,
    floatingBackdrop: Backdrop? = backdrop,
    config: ScheduleConfigEntity,
    weekMotionDirection: Int,
    outgoing: Boolean,
    layerOffset: Animatable<Float, AnimationVector1D>,
    gestureOffset: () -> Float = { 0f },
    editMode: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    editScrollState: ScrollState? = null,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit
) {
    val density = LocalDensity.current
    val coursesByWeekday = remember(courses) { courses.groupBy { it.weekday } }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = layerOffset.value + gestureOffset() }
    ) {
        val dayColumnWidth = maxWidth / weekdays.size.coerceAtLeast(1)
        val travel = with(density) { (maxWidth + 96.dp).toPx() }
        var draggingDayIndex by remember { mutableStateOf<Int?>(null) }
        var draggingCourseId by remember { mutableStateOf<Long?>(null) }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .zIndex(if (draggingDayIndex == day) 1f else 0f)
                ) {
                    WeekDayColumn(
                        courses = coursesByWeekday[day].orEmpty(),
                        periods = periods,
                        cardHeight = cardHeight,
                        cardColor = cardColor,
                        emptyBackground = ComposeColor.Transparent,
                        backdrop = backdrop,
                        floatingBackdrop = floatingBackdrop,
                        config = config,
                        weekMotionDirection = weekMotionDirection,
                        weekMotionOutgoing = outgoing,
                        dayIndex = day,
                        gridColumnWidth = dayColumnWidth,
                        periodRowHeight = cardHeight,
                        layerOffset = layerOffset,
                        layerTravel = travel,
                        editMode = editMode,
                        editWeek = editWeek,
                        allWeekCourses = allWeekCourses,
                        weekdayCount = weekdays.size,
                        editScrollState = editScrollState,
                        onEnterEditMode = onEnterEditMode,
                        onUpdateSingleWeekCourse = onUpdateSingleWeekCourse,
                        conflictFocusCourseId = conflictFocusCourseId,
                        conflictFocusCourseKey = conflictFocusCourseKey,
                        onResolveCourseConflict = onResolveCourseConflict,
                        onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                        onCourseClick = onCourseClick,
                        onDragStateChanged = { dayIndex, courseId ->
                            draggingDayIndex = dayIndex
                            draggingCourseId = courseId
                        },
                        composedCourseCardCount = courses.size,
                        draggingCourseId = draggingCourseId,
                        activeOverlayCourseId = activeOverlayCourseId,
                        activeOverlayTargetKey = activeOverlayTargetKey,
                        activeOverlayTargetWeek = activeOverlayTargetWeek,
                        onStartWeekEditOverlay = onStartWeekEditOverlay,
                        onDragWeekEditOverlay = onDragWeekEditOverlay,
                        onFinishMoveOverlay = onFinishMoveOverlay,
                        onFinishResizeOverlay = onFinishResizeOverlay,
                        onCancelWeekEditOverlay = onCancelWeekEditOverlay
                    )
                }
            }
        }
    }
}

private fun continuousSpanFrom(course: CourseEntity, start: Int, periodIndexes: List<Int>): Int {
    var span = 0
    var expected = start
    for (periodIndex in periodIndexes.dropWhile { it != start }) {
        if (periodIndex != expected || periodIndex !in course.periods) break
        span += 1
        expected += 1
    }
    return span
}

internal data class WeekCourseEditTarget(
    val weekday: Int,
    val periods: List<Int>,
    val valid: Boolean
)

enum class WeekEditOverlayMode {
    Move,
    Resize
}

data class WeekEditOverlayRequest(
    val mode: WeekEditOverlayMode,
    val course: CourseEntity,
    val sourceBounds: Rect,
    val dayIndex: Int,
    val periodIndex: Int,
    val currentSpan: Int,
    val maxSpan: Int,
    val gridColumnWidthPx: Float,
    val periodRowHeightPx: Float,
    val periodIndexes: List<Int>,
    val weekCourses: List<CourseEntity>,
    val editWeek: Int,
    val weekdayCount: Int,
    val initialPointerPosition: Offset
)

internal const val WeekEditLiftedScale = 1.12f
private const val WeekEditProjectionSeconds = 0.015f
// The bottom handle moves with the resized card. A conservative gain prevents tiny local pointer
// changes from turning into several timetable rows while retaining continuous visual feedback.
private const val WeekEditResizeDragGain = 0.52f

internal fun weekEditResizeHeightAfterDrag(
    currentHeightPx: Float,
    dragDeltaYPx: Float,
    periodRowHeightPx: Float,
    maxSpan: Int,
    resizePaddingPx: Float
): Float {
    val safeRowHeight = periodRowHeightPx.coerceAtLeast(1f)
    val safeMaxSpan = maxSpan.coerceAtLeast(1)
    val minHeight = (safeRowHeight - resizePaddingPx).coerceAtLeast(1f)
    val maxHeight = (safeRowHeight * safeMaxSpan - resizePaddingPx).coerceAtLeast(minHeight)
    return (currentHeightPx + dragDeltaYPx * WeekEditResizeDragGain)
        .coerceIn(minHeight, maxHeight)
}

internal fun weekEditResizeTargetSpan(
    heightPx: Float,
    periodRowHeightPx: Float,
    maxSpan: Int
): Int = (heightPx / periodRowHeightPx.coerceAtLeast(1f))
    .roundToInt()
    .coerceIn(1, maxSpan.coerceAtLeast(1))

internal data class WeekEditNeighborRippleTransform(
    val translationFactor: Float,
    val scale: Float,
    val rotationFactor: Float
)

internal data class WeekEditLandingImpactTransform(
    val translationFactor: Float,
    val scaleX: Float,
    val scaleY: Float
)

internal data class WeekEditRealCardLandingTransform(
    val liftFactor: Float,
    val scale: Float
)

/**
 * Maps the destination card from the lifted finger pose to its real timetable geometry.
 * Progress intentionally accepts a small spring overshoot so the physical card can pass the
 * surface by a fraction before settling, without ever changing its logical grid position.
 */
internal fun weekEditRealCardLandingTransform(progress: Float): WeekEditRealCardLandingTransform {
    val springProgress = progress.coerceIn(-0.20f, 1.22f)
    val remaining = 1f - springProgress
    return WeekEditRealCardLandingTransform(
        liftFactor = remaining,
        scale = 1f + (WeekEditLiftedScale - 1f) * remaining
    )
}

internal fun weekEditLandingImpactTransform(progress: Float): WeekEditLandingImpactTransform {
    val safeProgress = progress.coerceIn(0f, 1f)
    fun smooth(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }
    val zScale = when {
        safeProgress <= 0.18f -> 1f - 0.095f * smooth(safeProgress / 0.18f)
        safeProgress <= 0.46f -> 0.905f + 0.155f * smooth((safeProgress - 0.18f) / 0.28f)
        safeProgress <= 0.72f -> 1.060f - 0.080f * smooth((safeProgress - 0.46f) / 0.26f)
        else -> 0.980f + 0.020f * smooth((safeProgress - 0.72f) / 0.28f)
    }
    return WeekEditLandingImpactTransform(
        translationFactor = 0f,
        scaleX = zScale,
        scaleY = zScale
    )
}

internal fun weekEditProjectedOffset(
    position: Float,
    velocity: Float,
    maximumProjection: Float
): Float = position + (velocity * WeekEditProjectionSeconds)
    .coerceIn(-maximumProjection.coerceAtLeast(0f), maximumProjection.coerceAtLeast(0f))

internal fun weekEditNeighborRippleTransform(
    distancePx: Float,
    radiusPx: Float,
    progress: Float,
    horizontalDirection: Float = 0f
): WeekEditNeighborRippleTransform {
    val safeRadius = radiusPx.coerceAtLeast(1f)
    val distanceRatio = (distancePx / safeRadius).coerceIn(0f, 1f)
    val delayedStart = distanceRatio * 0.28f
    val arrival = (progress.coerceIn(0f, 1f) - delayedStart) / (1f - delayedStart)
    if (distanceRatio >= 1f || arrival <= 0f || arrival >= 1f) {
        return WeekEditNeighborRippleTransform(0f, 1f, 0f)
    }
    val safeArrival = arrival.coerceIn(0f, 1f)
    // The squared sine envelope has zero slope at both boundaries. Combined with exponential
    // damping it gives the neighbouring cards a real acceleration/deceleration curve instead of
    // letting a linear remaining-time multiplier cut the second bounce off abruptly.
    val smoothEnvelope = sin(Math.PI.toFloat() * safeArrival).pow(2f)
    val distanceAttenuation = (1f - distanceRatio).pow(0.72f)
    val damping = exp(-1.15f * safeArrival)
    val attenuation = distanceAttenuation * smoothEnvelope * damping
    // Two and a half deliberately slow Z cycles. The final half-cycle and zero-slope envelope
    // converge on the resting scale without a velocity discontinuity.
    val zWave = -sin(safeArrival * Math.PI.toFloat() * 5f) * attenuation
    return WeekEditNeighborRippleTransform(
        translationFactor = 0f,
        scale = 1f + zWave * 0.105f,
        rotationFactor = horizontalDirection.coerceIn(-1f, 1f) * zWave * 0f
    )
}

internal fun weekEditCommitTargetPresent(
    courses: List<CourseEntity>,
    targetKey: String?,
    targetWeek: Int
): Boolean {
    if (targetKey == null || targetWeek <= 0) return false
    return coursesVisibleInWeek(courses, targetWeek).any {
        it.occurrenceOverrideKey() == targetKey
    }
}

private val LocalWeekEditMotionState = compositionLocalOf<WeekEditOverlayController?> { null }

@Composable
private fun rememberWeekEditOverlayController(
    scrollState: ScrollState,
    scheduleId: Int
): WeekEditOverlayController {
    val scope = rememberCoroutineScope()
    return remember(scope, scrollState, scheduleId) {
        WeekEditOverlayController(scope, scrollState)
    }
}

private class WeekEditOverlayController(
    private val scope: CoroutineScope,
    private val scrollState: ScrollState
) {
    var request by mutableStateOf<WeekEditOverlayRequest?>(null)
        private set
    var awaitingCommit by mutableStateOf(false)
        private set
    var committedTargetKey by mutableStateOf<String?>(null)
        private set
    var committedTargetWeek by mutableIntStateOf(0)
        private set

    private val overlayX = Animatable(0f)
    private val overlayY = Animatable(0f)
    private val overlayHeight = Animatable(0f)
    private val overlayScale = Animatable(1f)
    private val overlayAlpha = Animatable(1f)
    private val overlayReveal = Animatable(0f)
    private val overlayLift = Animatable(0f)
    private val realCardLandingAnimation = Animatable(1f)
    private val landingImpactAnimation = Animatable(1f)
    private val overlayRotation = Animatable(0f)
    private val landingRippleAnimation = Animatable(1f)
    private var scrollCompensationY by mutableFloatStateOf(0f)
    private var autoScrollDirection by mutableIntStateOf(0)
    private var dragPointerX by mutableFloatStateOf(0f)
    private var dragPointerY by mutableFloatStateOf(0f)
    private var gestureTargetX by mutableFloatStateOf(0f)
    private var gestureTargetY by mutableFloatStateOf(0f)
    private var gestureTargetHeight by mutableFloatStateOf(0f)
    private var releaseFrameX by mutableFloatStateOf(0f)
    private var releaseFrameY by mutableFloatStateOf(0f)
    private var releaseFrameHeight by mutableFloatStateOf(0f)
    private var releaseFrameActive by mutableStateOf(false)
    private var directManipulation by mutableStateOf(false)
    private var handoffRunning = false
    private var landingFlightRunning = false
    private var landingFlightComplete = false
    private var commitTargetReady = false
    private var realCardTargetMeasured = false
    private var pendingLandingCenter = Offset.Zero
    private var pendingLandingRadius = 1f
    private var landingRippleStarted = false
    var gestureActive by mutableStateOf(false)
        private set
    var realCardVisible by mutableStateOf(false)
        private set
    var realCardLandingActive by mutableStateOf(false)
        private set
    var landingRippleCenter by mutableStateOf<Offset?>(null)
        private set
    var landingRippleRadius by mutableFloatStateOf(1f)
        private set

    val offsetX: Float get() =
        when {
            releaseFrameActive -> releaseFrameX
            directManipulation && request?.mode == WeekEditOverlayMode.Move -> gestureTargetX
            else -> overlayX.value
        }
    val offsetY: Float get() =
        when {
            releaseFrameActive -> releaseFrameY
            directManipulation && request?.mode == WeekEditOverlayMode.Move -> gestureTargetY
            else -> overlayY.value
        }
    val height: Float get() =
        when {
            releaseFrameActive -> releaseFrameHeight
            directManipulation && request?.mode == WeekEditOverlayMode.Resize -> gestureTargetHeight
            else -> overlayHeight.value
        }
    val scale: Float get() = overlayScale.value
    val alpha: Float get() = overlayAlpha.value
    val revealProgress: Float get() = overlayReveal.value
    val liftProgress: Float get() = overlayLift.value
    val realCardLandingProgress: Float get() = realCardLandingAnimation.value
    val impactProgress: Float get() = landingImpactAnimation.value
    val rotation: Float get() = overlayRotation.value
    val pointerPosition: Offset get() = Offset(dragPointerX, dragPointerY)
    val landingRippleProgress: Float get() = landingRippleAnimation.value
    val gridOffsetY: Float get() = offsetY + scrollCompensationY
    val gridScrollCompensationY: Float get() = scrollCompensationY

    fun clear(preserveLandingRipple: Boolean = false) {
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = null
        gestureActive = false
        directManipulation = false
        handoffRunning = false
        landingFlightRunning = false
        landingFlightComplete = false
        commitTargetReady = false
        realCardTargetMeasured = false
        realCardVisible = false
        realCardLandingActive = false
        pendingLandingCenter = Offset.Zero
        pendingLandingRadius = 1f
        scrollCompensationY = 0f
        autoScrollDirection = 0
        dragPointerX = 0f
        dragPointerY = 0f
        gestureTargetX = 0f
        gestureTargetY = 0f
        gestureTargetHeight = 0f
        releaseFrameX = 0f
        releaseFrameY = 0f
        releaseFrameHeight = 0f
        releaseFrameActive = false
        landingRippleStarted = false
        if (!preserveLandingRipple) {
            landingRippleCenter = null
            landingRippleRadius = 1f
        }
    }

    fun start(nextRequest: WeekEditOverlayRequest) {
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = nextRequest
        gestureActive = true
        directManipulation = true
        handoffRunning = false
        landingFlightRunning = false
        landingFlightComplete = false
        commitTargetReady = false
        realCardTargetMeasured = false
        realCardVisible = false
        realCardLandingActive = false
        pendingLandingCenter = Offset.Zero
        pendingLandingRadius = 1f
        landingRippleStarted = false
        landingRippleCenter = null
        landingRippleRadius = 1f
        gestureTargetX = 0f
        gestureTargetY = 0f
        gestureTargetHeight = nextRequest.sourceBounds.height
        releaseFrameX = 0f
        releaseFrameY = 0f
        releaseFrameHeight = nextRequest.sourceBounds.height
        releaseFrameActive = false
        dragPointerX = nextRequest.initialPointerPosition.x
        dragPointerY = nextRequest.initialPointerPosition.y
        scope.launch {
            overlayX.snapTo(0f)
            overlayY.snapTo(0f)
            overlayHeight.snapTo(nextRequest.sourceBounds.height)
            overlayScale.snapTo(0.985f)
            overlayAlpha.snapTo(1f)
            overlayReveal.snapTo(0f)
            overlayLift.snapTo(0f)
            realCardLandingAnimation.snapTo(1f)
            landingImpactAnimation.snapTo(1f)
            overlayRotation.snapTo(0f)
            landingRippleAnimation.snapTo(1f)
            scrollCompensationY = 0f
            autoScrollDirection = 0
            launch {
                overlayReveal.animateTo(
                    1f,
                    tween(durationMillis = 90, easing = CubicBezierEasing(0.20f, 0.78f, 0.18f, 1f))
                )
            }
            launch {
                overlayScale.animateTo(
                    WeekEditLiftedScale,
                    spring(dampingRatio = 0.66f, stiffness = 980f)
                )
            }
            launch {
                overlayLift.animateTo(
                    1f,
                    spring(dampingRatio = 0.68f, stiffness = 720f)
                )
            }
        }
    }

    fun drag(
        delta: Offset,
        screenHeightPx: Float,
        edgePx: Float,
        resizePaddingPx: Float
    ) {
        val activeRequest = request ?: return
        when (activeRequest.mode) {
            WeekEditOverlayMode.Move -> {
                // Keep the logical target on the pointer event itself. Queuing these additions in
                // animation coroutines made fast drags accumulate a visible one-to-two-frame lag.
                gestureTargetX += delta.x
                gestureTargetY += delta.y
                dragPointerX += delta.x
                dragPointerY += delta.y
                scope.launch {
                    launch {
                        overlayRotation.animateTo(
                            (delta.x * 0.018f).coerceIn(-0.28f, 0.28f),
                            spring(dampingRatio = 0.96f, stiffness = 1_450f)
                        )
                    }
                    autoScroll(activeRequest, screenHeightPx, edgePx)
                }
            }
            WeekEditOverlayMode.Resize -> {
                val previousHeight = gestureTargetHeight
                gestureTargetHeight = weekEditResizeHeightAfterDrag(
                    currentHeightPx = previousHeight,
                    dragDeltaYPx = delta.y,
                    periodRowHeightPx = activeRequest.periodRowHeightPx,
                    maxSpan = activeRequest.maxSpan,
                    resizePaddingPx = resizePaddingPx
                )
                dragPointerX += delta.x
                dragPointerY += gestureTargetHeight - previousHeight
            }
        }
    }

    fun cancelGesture() {
        val activeRequest = request ?: return
        gestureActive = false
        scope.launch {
            overlayX.snapTo(gestureTargetX)
            overlayY.snapTo(gestureTargetY)
            overlayHeight.snapTo(gestureTargetHeight)
            directManipulation = false
            val xJob = launch {
                overlayX.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = 440f))
            }
            val yJob = launch {
                overlayY.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = 440f))
            }
            val heightJob = launch {
                overlayHeight.animateTo(
                    activeRequest.sourceBounds.height,
                    spring(dampingRatio = 0.66f, stiffness = 440f)
                )
            }
            val scaleJob = launch {
                overlayScale.animateTo(1f, spring(dampingRatio = 0.60f, stiffness = 450f))
            }
            val liftJob = launch {
                overlayLift.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 470f))
            }
            val rotationJob = launch {
                overlayRotation.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 450f))
            }
            xJob.join()
            yJob.join()
            heightJob.join()
            scaleJob.join()
            liftJob.join()
            rotationJob.join()
            clear()
        }
    }

    fun finishMove(
        velocity: Velocity,
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ) {
        val activeRequest = request ?: return
        gestureActive = false
        // The preview and persistence target must be calculated from the exact same finger frame.
        // A release-velocity projection made the saved cell differ from the one shown under the
        // card, especially near half-cell boundaries.
        val releaseGridX = gestureTargetX
        val releaseGridY = gestureTargetY + scrollCompensationY
        val target = weekCourseEditTarget(
            periodIndexes = activeRequest.periodIndexes,
            weekday = activeRequest.dayIndex + (releaseGridX / activeRequest.gridColumnWidthPx).roundToInt(),
            startPeriod = activeRequest.periodIndex + (releaseGridY / activeRequest.periodRowHeightPx).roundToInt(),
            span = activeRequest.currentSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(weekday = target.weekday, periods = target.periods)
        val canSave = target.valid &&
            !hasWeekCourseEditConflict(activeRequest.course, edited, activeRequest.weekCourses, activeRequest.editWeek) &&
            (edited.weekday != activeRequest.course.weekday || edited.periods != activeRequest.course.periods)
        val targetX = if (canSave) (target.weekday - activeRequest.dayIndex) * activeRequest.gridColumnWidthPx else 0f
        val targetY = if (canSave) {
            ((target.periods.firstOrNull() ?: activeRequest.periodIndex) - activeRequest.periodIndex) *
                activeRequest.periodRowHeightPx - scrollCompensationY
        } else {
            0f
        }
        pendingLandingCenter = activeRequest.sourceBounds.center + Offset(targetX, targetY)
        pendingLandingRadius = maxOf(
            activeRequest.gridColumnWidthPx * 3.8f,
            activeRequest.periodRowHeightPx * 3.0f
        )
        if (canSave) {
            committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = targetX,
                targetY = targetY,
                targetHeight = activeRequest.sourceBounds.height,
                releaseVelocity = velocity
            )
            onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
        } else {
            // The original occurrence is already a real card, so it can take over immediately.
            committedTargetKey = activeRequest.course.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = false
            commitTargetReady = true
            beginLandingFlight(
                targetX = targetX,
                targetY = targetY,
                targetHeight = activeRequest.sourceBounds.height,
                releaseVelocity = velocity
            )
        }
    }

    fun finishResize(
        velocity: Velocity,
        resizePaddingPx: Float,
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ) {
        val activeRequest = request ?: return
        gestureActive = false
        val targetSpan = weekEditResizeTargetSpan(
            heightPx = gestureTargetHeight,
            periodRowHeightPx = activeRequest.periodRowHeightPx,
            maxSpan = activeRequest.maxSpan
        )
        val target = weekCourseEditTarget(
            periodIndexes = activeRequest.periodIndexes,
            weekday = activeRequest.dayIndex,
            startPeriod = activeRequest.periodIndex,
            span = targetSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(periods = target.periods)
        val canSave = target.valid &&
            !hasWeekCourseEditConflict(activeRequest.course, edited, activeRequest.weekCourses, activeRequest.editWeek) &&
            edited.periods != activeRequest.course.periods
        val targetHeight = if (canSave) {
            activeRequest.periodRowHeightPx * targetSpan - resizePaddingPx
        } else {
            activeRequest.sourceBounds.height
        }
        pendingLandingCenter = Offset(
            x = activeRequest.sourceBounds.center.x,
            y = activeRequest.sourceBounds.top + targetHeight / 2f
        )
        pendingLandingRadius = maxOf(
            activeRequest.gridColumnWidthPx * 3.8f,
            activeRequest.periodRowHeightPx * 3.0f
        )
        if (canSave) {
            committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = 0f,
                targetY = 0f,
                targetHeight = targetHeight,
                releaseVelocity = Velocity(0f, velocity.y * WeekEditResizeDragGain)
            )
            onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
        } else {
            committedTargetKey = activeRequest.course.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = false
            commitTargetReady = true
            beginLandingFlight(
                targetX = 0f,
                targetY = 0f,
                targetHeight = targetHeight,
                releaseVelocity = Velocity(0f, velocity.y * WeekEditResizeDragGain)
            )
        }
    }

    fun completeCommitHandoff() {
        if (!awaitingCommit) return
        awaitingCommit = false
        commitTargetReady = true
        maybeHandoffOverlayToRealCard()
    }

    fun updateRealLandingCenter(center: Offset) {
        if (committedTargetKey != null) {
            pendingLandingCenter = center
            realCardTargetMeasured = true
            maybeHandoffOverlayToRealCard()
        }
    }

    private fun beginLandingFlight(
        targetX: Float,
        targetY: Float,
        targetHeight: Float,
        releaseVelocity: Velocity
    ) {
        if (landingFlightRunning || landingFlightComplete) return
        landingFlightRunning = true
        // Publish the exact release pose synchronously. Animatable.snapTo is suspendable, so
        // waiting to leave direct-manipulation mode inside the coroutine could leave one rendered
        // frame parked at its previous value before the landing spring became observable.
        releaseFrameX = gestureTargetX
        releaseFrameY = gestureTargetY
        releaseFrameHeight = gestureTargetHeight
        releaseFrameActive = true
        directManipulation = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            overlayX.snapTo(gestureTargetX)
            overlayY.snapTo(gestureTargetY)
            overlayHeight.snapTo(gestureTargetHeight)
            releaseFrameActive = false
            // Reuse the original conflict-return flight: the same damped spring handles a legal
            // destination and a rejected drop (whose target is the source). This keeps the release
            // continuous with the finger instead of pinning the clone through a new tween.
            val landingPositionSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 390f)
            val landingHeightSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 360f)
            val xJob = launch {
                overlayX.animateTo(
                    targetX,
                    landingPositionSpec,
                    initialVelocity = releaseVelocity.x.coerceIn(-2_400f, 2_400f)
                )
            }
            val yJob = launch {
                overlayY.animateTo(
                    targetY,
                    landingPositionSpec,
                    initialVelocity = releaseVelocity.y.coerceIn(-2_400f, 2_400f)
                )
            }
            val heightJob = launch {
                overlayHeight.animateTo(
                    targetHeight,
                    landingHeightSpec,
                    initialVelocity = releaseVelocity.y.coerceIn(-1_800f, 1_800f)
                )
            }
            val scaleJob = launch {
                // Keep the impact on the floating card and start it in the release frame. The
                // lower stiffness preserves one readable compression/rebound instead of finishing
                // before the clone reaches its snapped cell.
                overlayScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.46f, stiffness = 300f)
                )
            }
            val rotationJob = launch {
                overlayRotation.animateTo(
                    0f,
                    spring(dampingRatio = 0.68f, stiffness = 390f)
                )
            }
            // The clone must lose its lifted Z offset during the same landing flight. Room can
            // legitimately take longer than the spring; keeping liftProgress at 1f until the
            // repository-backed card appears makes the clone visibly hover at its destination.
            // Returning it to the grid plane here keeps the visual landing continuous while the
            // existing real-card handoff still waits for the persisted occurrence.
            val liftJob = launch {
                overlayLift.animateTo(
                    0f,
                    spring(dampingRatio = 0.66f, stiffness = 360f)
                )
            }
            xJob.join()
            yJob.join()
            heightJob.join()
            if (!landingRippleStarted) {
                landingRippleStarted = true
                startLandingRipple(
                    center = pendingLandingCenter,
                    radius = pendingLandingRadius
                )
            }
            rotationJob.join()
            liftJob.join()
            scaleJob.join()
            landingFlightRunning = false
            landingFlightComplete = true
            maybeHandoffOverlayToRealCard()
        }
    }

    private fun maybeHandoffOverlayToRealCard() {
        if (commitTargetReady && landingFlightComplete && realCardTargetMeasured) {
            handoffOverlayToRealCard()
        }
    }

    private fun handoffOverlayToRealCard() {
        if (handoffRunning || committedTargetKey == null) return
        handoffRunning = true
        scope.launch {
            awaitingCommit = false
            // The overlay has already completed the full visible flight and rebound. Room only
            // transfers ownership in place and must never gate or restart the landing motion.
            realCardLandingAnimation.snapTo(1f)
            landingImpactAnimation.snapTo(1f)
            realCardLandingActive = false
            realCardVisible = true
            overlayAlpha.animateTo(
                0f,
                tween(
                    durationMillis = 135,
                    easing = CubicBezierEasing(0.20f, 0.72f, 0.22f, 1f)
                )
            )
            request = null
            gestureActive = false
            directManipulation = false
            releaseFrameActive = false
            scrollCompensationY = 0f
            autoScrollDirection = 0
            handoffRunning = false
            overlayAlpha.snapTo(1f)
        }
    }

    private fun startLandingRipple(center: Offset, radius: Float) {
        landingRippleCenter = center
        landingRippleRadius = radius.coerceAtLeast(1f)
        scope.launch {
            landingRippleAnimation.snapTo(0f)
            landingRippleAnimation.animateTo(
                1f,
                tween(durationMillis = 900, easing = LinearEasing)
            )
            landingRippleCenter = null
            landingRippleRadius = 1f
        }
    }

    private suspend fun autoScroll(
        activeRequest: WeekEditOverlayRequest,
        screenHeightPx: Float,
        edgePx: Float
    ) {
        if (overlayHeight.value >= screenHeightPx - edgePx * 1.25f) {
            autoScrollDirection = 0
            return
        }
        val deadZone = edgePx * 0.22f
        val bottomLimit = screenHeightPx - edgePx
        val topLimit = edgePx
        val wantedDirection = when {
            dragPointerY > bottomLimit + deadZone -> 1
            dragPointerY < topLimit - deadZone -> -1
            dragPointerY < bottomLimit - deadZone && dragPointerY > topLimit + deadZone -> 0
            else -> autoScrollDirection
        }
        autoScrollDirection = wantedDirection
        val delta = when {
            wantedDirection > 0 -> {
                val pressure = ((dragPointerY - bottomLimit) / edgePx).coerceIn(0f, 1f)
                1.2f + 6.5f * pressure
            }
            wantedDirection < 0 -> {
                val pressure = ((topLimit - dragPointerY) / edgePx).coerceIn(0f, 1f)
                -(1.2f + 6.5f * pressure)
            }
            else -> 0f
        }
        if (delta == 0f) return
        val before = scrollState.value
        val next = (before + delta).roundToInt().coerceIn(0, scrollState.maxValue)
        if (next != before) {
            scrollState.scrollTo(next)
            scrollCompensationY += (scrollState.value - before).toFloat()
        }
    }
}

internal fun weekCourseEditTarget(
    periodIndexes: List<Int>,
    weekday: Int,
    startPeriod: Int,
    span: Int,
    weekdayCount: Int
): WeekCourseEditTarget {
    val safeWeekday = weekday.coerceIn(1, weekdayCount.coerceAtLeast(1))
    if (periodIndexes.isEmpty() || span <= 0) {
        return WeekCourseEditTarget(safeWeekday, emptyList(), false)
    }
    val requestedPosition = periodIndexes.indexOf(startPeriod).let { exactPosition ->
        when {
            exactPosition >= 0 -> exactPosition
            // A fast release can put the overlay a few pixels below the timetable. Treat that
            // overshoot as the last legal start row instead of rejecting an otherwise valid drop
            // on the bottom cell. Keep an above-top release invalid so accidental edge escapes
            // still spring back to the source card.
            startPeriod > periodIndexes.last() -> periodIndexes.lastIndex
            else -> -1
        }
    }
    if (requestedPosition < 0) {
        return WeekCourseEditTarget(safeWeekday, emptyList(), false)
    }
    val maxStartPosition = (periodIndexes.size - span).coerceAtLeast(0)
    val startPosition = requestedPosition.coerceIn(0, maxStartPosition)
    val targetPeriods = periodIndexes.drop(startPosition).take(span)
    return WeekCourseEditTarget(
        weekday = safeWeekday,
        periods = targetPeriods,
        valid = targetPeriods.size == span
    )
}

private fun hasWeekCourseEditConflict(
    original: CourseEntity,
    edited: CourseEntity,
    weekCourses: List<CourseEntity>,
    week: Int
): Boolean {
    val editedPeriods = edited.periods.toSet()
    return weekCourses.any { other ->
        other.id != original.id &&
            week in other.weeks &&
            other.weekday == edited.weekday &&
            other.periods.any { it in editedPeriods }
    }
}

@Composable
fun EmptyWeekCell(height: Dp, background: ComposeColor) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(1.dp)
            .background(background)
    )
}

@Composable
fun WeekCourseBlock(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    height: Dp,
    cardColor: ComposeColor,
    backdrop: Backdrop?,
    floatingBackdrop: Backdrop? = backdrop,
    config: ScheduleConfigEntity,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    periodIndex: Int = 1,
    gridColumnWidth: Dp = 0.dp,
    periodRowHeight: Dp = height,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    stackIndex: Int = 0,
    conflictWarning: Boolean = false,
    conflictUnderlyingCourse: CourseEntity? = null,
    conflictUnderlyingPeriodIndex: Int? = null,
    conflictUnderlyingSpan: Int = 0,
    onResolveConflict: (CourseEntity) -> Unit = {},
    editMode: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    weekdayCount: Int = 7,
    editScrollState: ScrollState? = null,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onDragStateChanged: (dayIndex: Int?, courseId: Long?) -> Unit = { _, _ -> },
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {},
    renderCardSurface: Boolean = true,
    backdropSampleScale: Float = 1f
) {
    val locationText = course.location.orEmpty()
    val hasLocation = locationText.isNotBlank()
    val hasTeacher = !course.teacher.isNullOrBlank()
    val resolvedCardColor = if (config.cardColorArgb == MulticolorCourseCardArgb) courseCardBaseColor(config, course) else cardColor
    val courseTextColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else if (config.courseCardGlassEnabled) readableOn(resolvedCardColor)
        else glassForegroundColor(config)
    val density = LocalDensity.current
    val tailDirection = if (weekMotionOutgoing) -weekMotionDirection else weekMotionDirection
    val tailBase = with(density) { (32.dp + ((periodIndex - 1).coerceAtLeast(0).coerceAtMost(9) * 9f).dp + (stackIndex * 16f).dp).toPx() }
    val startupPhase = LocalStartupPhase.current
    val startupOrigin = startupOriginForWeekCard(
        dayIndex = dayIndex,
        periodIndex = periodIndex,
        weekdayCount = 7,
        periodCount = periods.size
    )
    val startupIndex = ((periodIndex - 1).coerceAtLeast(0) * 7 + (dayIndex - 1).coerceAtLeast(0)) * 2 + stackIndex
    var ownBounds by remember { mutableStateOf<Rect?>(null) }
    val haptic = LocalHapticFeedback.current
    val weekEditMotionState = LocalWeekEditMotionState.current
    val isOverlayTarget = activeOverlayTargetKey != null &&
        activeOverlayTargetWeek == editWeek &&
        activeOverlayTargetWeek in course.weeks &&
        course.occurrenceOverrideKey() == activeOverlayTargetKey
    val editControlHandoffTarget = when {
        isOverlayTarget -> if (weekEditMotionState?.realCardVisible == true) 1f else 0f
        activeOverlayCourseId == course.id -> 1f - (weekEditMotionState?.revealProgress ?: 1f)
        else -> 1f
    }.coerceIn(0f, 1f)
    val editControlHandoffProgress by animateFloatAsState(
        targetValue = editControlHandoffTarget,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 620f),
        label = "week-edit-controls-handoff-${course.id}"
    )
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val customTimeLocked = !courseAllowsWeekPeriodDrag(course)
    val currentSpan = remember(course.periods, periodIndex, periodIndexes) {
        continuousSpanFrom(course, periodIndex, periodIndexes)
            .takeIf { it > 0 }
            ?: course.periods.size.coerceAtLeast(1)
    }
    var bodyDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var handleDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var conflictActionResolving by remember(course.id, editWeek) { mutableStateOf(false) }
    var conflictFlightTarget by remember(course.id, editWeek) { mutableStateOf<CourseEntity?>(null) }
    val conflictPillDismiss = remember(course.id, editWeek) { Animatable(0f) }
    val conflictCardFlight = remember(course.id, editWeek) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(density) { currentWindowSizeDp().height.toPx() }
    val edgeScrollThresholdPx = with(density) { 92.dp.toPx() }
    val measuredCardWidthPx = ownBounds?.width?.coerceAtLeast(1f) ?: with(density) { 48.dp.toPx() }
    val gridColumnWidthPx = with(density) { gridColumnWidth.toPx() }
        .takeIf { it > 1f }
        ?: measuredCardWidthPx
    val periodRowHeightPx = with(density) { periodRowHeight.toPx() }.coerceAtLeast(1f)
    /*
     * WeekCourseBlock always lives inside the stationary course recorder. Sampling the
     * lifted backdrop here would make the source card read the recorder that is currently
     * drawing the source card, creating a recursive backdrop loop on long-press. The real
     * lifted card is rendered by WeekEditOverlayHost outside that recorder.
     */
    val activeCardBackdrop = backdrop
    val editJitter by if (editMode) {
        rememberInfiniteTransition(label = "week-edit-jitter-${course.id}").animateFloat(
            initialValue = -0.35f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 115), RepeatMode.Reverse),
            label = "week-edit-jitter-value-${course.id}"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (bodyDragging || handleDragging) WeekEditLiftedScale else 1f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 470f),
        label = "week-edit-press-scale-${course.id}"
    )
    val editActivationProgress by animateFloatAsState(
        targetValue = if (editMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
        label = "week-edit-activation-${course.id}"
    )
    val displayedHeight = height
    val windowSize = currentWindowSizeDp()
    val cardWidthForCorner = gridColumnWidth
        .takeIf { it > 0.dp }
        ?: (windowSize.width / 8f)
    val cardCorner = adaptiveWeekCardCornerRadius(
        cardWidth = (cardWidthForCorner - 4.dp).coerceAtLeast(1.dp),
        cardHeight = displayedHeight,
        windowWidth = windowSize.width,
        windowHeight = windowSize.height
    )
    val cardShape = remember(cardCorner) { RoundedRectangle(cardCorner) }
    val resizeStartIndex = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
    val resizeMaxSpan = (periodIndexes.size - resizeStartIndex).coerceAtLeast(1)
    val baseHeightPx = with(density) { height.toPx() }
    fun buildWeekEditOverlayRequest(
        mode: WeekEditOverlayMode,
        pointerInSource: Offset
    ): WeekEditOverlayRequest? {
        val bounds = ownBounds ?: return null
        return WeekEditOverlayRequest(
            mode = mode,
            course = course,
            sourceBounds = bounds,
            dayIndex = dayIndex,
            periodIndex = periodIndex,
            currentSpan = currentSpan,
            maxSpan = resizeMaxSpan,
            gridColumnWidthPx = gridColumnWidthPx,
            periodRowHeightPx = periodRowHeightPx,
            periodIndexes = periodIndexes,
            weekCourses = allWeekCourses,
            editWeek = editWeek,
            weekdayCount = weekdayCount,
            initialPointerPosition = bounds.topLeft + pointerInSource
        )
    }
    val liftedVisualActive = bodyDragging || handleDragging || conflictActionResolving
    val resizeHandleInputEnabled =
        editMode &&
            !customTimeLocked &&
            !bodyDragging &&
            !conflictActionResolving &&
            (
                activeOverlayCourseId == null ||
                    (handleDragging && activeOverlayCourseId == course.id)
                )
    LaunchedEffect(liftedVisualActive) {
        if (liftedVisualActive) onDragStateChanged(dayIndex, course.id) else onDragStateChanged(null, null)
    }
    LaunchedEffect(conflictWarning) {
        if (!conflictWarning) {
            conflictActionResolving = false
            conflictFlightTarget = null
            conflictPillDismiss.snapTo(0f)
            conflictCardFlight.snapTo(0f)
        }
    }
    val bodyGestureModifier = Modifier.pointerInput(editMode, customTimeLocked, course.id, editWeek, currentSpan) {
        if (editMode && !customTimeLocked) {
            val velocityTracker = VelocityTracker()
            detectDragGesturesAfterLongPress(
                onDragStart = { startPosition ->
                    velocityTracker.resetTracking()
                    buildWeekEditOverlayRequest(WeekEditOverlayMode.Move, startPosition)?.let { request ->
                        bodyDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartWeekEditOverlay(request)
                    }
                },
                onDrag = { change, dragAmount ->
                    if (bodyDragging) {
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        onDragWeekEditOverlay(dragAmount)
                    }
                },
                onDragEnd = {
                    if (bodyDragging) {
                        onFinishMoveOverlay(velocityTracker.calculateVelocity())
                        bodyDragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onDragCancel = {
                    if (bodyDragging) {
                        bodyDragging = false
                        onCancelWeekEditOverlay()
                    }
                }
            )
        } else {
            detectTapGestures(
                onTap = { onCourseClick(course, ownBounds) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!editMode) onEnterEditMode()
                }
            )
        }
    }
    val editingId = LocalEditingCourseId.current
    val sharedScope = if (startupPhase == StartupPhase.FullQuality && course.id > 0L) LocalSharedTransitionScope.current else null
    val baseModifier = Modifier
        .fillMaxWidth()
        .height(height)
    val startupModifier = Modifier
        .startupFlyIn(
            key = "week_${course.id}_${dayIndex}_${periodIndex}_${stackIndex}",
            index = startupIndex,
            totalCount = periods.size * 7 * 2,
            origin = startupOrigin,
            intensity = if (startupOrigin == StartupFlyOrigin.Center) 0.62f else 1f,
            delayFactor = 0.12f,
            alphaStart = 0f
        )
    val realLandingLiftPx = with(density) { 8.dp.toPx() }
    val tailModifier = Modifier
        .graphicsLayer {
            val tailX = layerOffset?.let { offset ->
                val progress = (kotlin.math.abs(offset.value) / layerTravel.coerceAtLeast(1f)).coerceIn(0f, 1f)
                tailBase * progress * tailDirection
            } ?: 0f
            val rippleCenter = weekEditMotionState?.landingRippleCenter
            val bounds = ownBounds
            val ripple = if (
                rippleCenter != null &&
                bounds != null &&
                !isOverlayTarget &&
                activeOverlayCourseId != course.id
            ) {
                val deltaX = bounds.center.x - rippleCenter.x
                val deltaY = bounds.center.y - rippleCenter.y
                weekEditNeighborRippleTransform(
                    distancePx = sqrt(deltaX * deltaX + deltaY * deltaY),
                    radiusPx = weekEditMotionState.landingRippleRadius,
                    progress = weekEditMotionState.landingRippleProgress,
                    horizontalDirection = deltaX / weekEditMotionState.landingRippleRadius.coerceAtLeast(1f)
                )
            } else {
                WeekEditNeighborRippleTransform(0f, 1f, 0f)
            }
            val realLanding = if (isOverlayTarget && weekEditMotionState?.realCardLandingActive == true) {
                weekEditRealCardLandingTransform(weekEditMotionState.realCardLandingProgress)
            } else {
                WeekEditRealCardLandingTransform(liftFactor = 0f, scale = 1f)
            }
            val landingImpact = if (isOverlayTarget && weekEditMotionState?.realCardLandingActive == true) {
                weekEditLandingImpactTransform(weekEditMotionState.impactProgress)
            } else {
                WeekEditLandingImpactTransform(translationFactor = 0f, scaleX = 1f, scaleY = 1f)
            }
            translationX = tailX
            // Landing happens on the repository-backed card at its new logical bounds. Only Z
            // depth is animated; its timetable X/Y never diverges from the persisted target.
            translationY = -realLandingLiftPx * realLanding.liftFactor
            scaleX = ripple.scale * realLanding.scale * landingImpact.scaleX
            scaleY = ripple.scale * realLanding.scale * landingImpact.scaleY
            rotationZ = ripple.rotationFactor
        }
        .onGloballyPositioned { coordinates ->
            ownBounds = coordinates.boundsInRoot()
            if (isOverlayTarget) {
                weekEditMotionState?.updateRealLandingCenter(coordinates.boundsInRoot().center)
            }
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editingId != course.id,
        sharedScope = sharedScope,
        modifier = baseModifier,
        shape = cardShape
    ) { sharedModifier ->
        Box(
            modifier = sharedModifier
                .then(startupModifier)
                .then(tailModifier)
                .then(bodyGestureModifier)
                .zIndex(if (liftedVisualActive) 3f else 0f)
        ) {
            conflictUnderlyingCourse
                ?.takeIf { conflictActionResolving && conflictUnderlyingSpan > 0 }
                ?.let { underlyingCourse ->
                    val sourcePosition = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
                    val underlyingPosition = conflictUnderlyingPeriodIndex
                        ?.let(periodIndexes::indexOf)
                        ?.takeIf { it >= 0 }
                        ?: sourcePosition
                    val underlyingHeight = (
                        periodRowHeight * conflictUnderlyingSpan.toFloat() - 4.dp
                        ).coerceAtLeast(18.dp)
                    val ownerReveal =
                        (conflictCardFlight.value / 0.14f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(
                                y = periodRowHeight *
                                    (underlyingPosition - sourcePosition).toFloat()
                            )
                            .height(underlyingHeight)
                            .graphicsLayer { alpha = ownerReveal }
                    ) {
                        CourseGlassCard(
                            backdrop = activeCardBackdrop,
                            config = config,
                            course = underlyingCourse,
                            modifier = Modifier.fillMaxSize(),
                            shape = cardShape,
                            onClick = null
                        ) {
                            WeekCourseOverlayCardContent(underlyingCourse, config)
                        }
                    }
                }
            conflictFlightTarget?.takeIf { conflictActionResolving }?.let { target ->
                val flightProgress = conflictCardFlight.value.coerceIn(0f, 1f)
                val sourcePosition = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
                val orderedSourcePeriods = course.periods
                    .distinct()
                    .sortedBy { periodIndexes.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                val orderedTargetPeriods = target.periods
                    .distinct()
                    .sortedBy { periodIndexes.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                val segmentPeriodOrdinal = orderedSourcePeriods.indexOf(periodIndex).coerceAtLeast(0)
                val targetPeriod = orderedTargetPeriods
                    .getOrNull(segmentPeriodOrdinal)
                    ?: orderedTargetPeriods.firstOrNull()
                    ?: periodIndex
                val targetPosition = periodIndexes.indexOf(targetPeriod)
                    .takeIf { it >= 0 }
                    ?: sourcePosition
                val targetOffsetX = (target.weekday - dayIndex) * gridColumnWidthPx
                val targetOffsetY = (targetPosition - sourcePosition) * periodRowHeightPx
                val flightArcPx = with(density) { 10.dp.toPx() }
                val flightElevationPx = with(density) { 14.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .graphicsLayer {
                            translationX = targetOffsetX * flightProgress
                            translationY = targetOffsetY * flightProgress -
                                sin(Math.PI * flightProgress).toFloat() * flightArcPx
                            val flightScale = 0.985f + 0.015f * flightProgress
                            scaleX = flightScale
                            scaleY = flightScale
                            alpha = (flightProgress / 0.12f).coerceIn(0f, 1f)
                            shadowElevation =
                                sin(Math.PI * flightProgress).toFloat().coerceAtLeast(0f) *
                                    flightElevationPx
                            shape = cardShape
                        }
                ) {
                    CourseGlassCard(
                        backdrop = activeCardBackdrop,
                        config = config,
                        course = target,
                        modifier = Modifier.fillMaxSize(),
                        shape = cardShape,
                        onClick = null
                    ) {
                        WeekCourseOverlayCardContent(target, config)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight)
                    .graphicsLayer {
                        val activeScale = if (bodyDragging) {
                            WeekEditLiftedScale
                        } else {
                            pressScale * (1f + editActivationProgress * 0.006f)
                        }
                        transformOrigin = if (handleDragging) {
                            TransformOrigin(0.5f, 0f)
                        } else {
                            TransformOrigin.Center
                        }
                        rotationZ = if (bodyDragging || handleDragging) 0f else editJitter * editActivationProgress
                        scaleX = activeScale
                        scaleY = activeScale
                        val departureAlpha = if (conflictActionResolving) {
                            1f - (conflictCardFlight.value / 0.12f).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        alpha = when {
                            isOverlayTarget -> if (weekEditMotionState?.realCardVisible == true) 1f else 0f
                            activeOverlayCourseId == course.id ->
                                1f - (weekEditMotionState?.revealProgress ?: 1f)
                            else -> departureAlpha
                        }
                    }
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight)
                    .clipToBounds()
            ) {
            CourseGlassCard(
                backdrop = activeCardBackdrop,
                config = config,
                course = course,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight),
                shape = cardShape,
                renderSurface = renderCardSurface,
                backdropSampleScale = backdropSampleScale,
                onClick = null
            ) {}
            BoxWithConstraints(Modifier.fillMaxWidth().height(displayedHeight).clipToBounds()) {
            val density = LocalDensity.current
            val heightDp = displayedHeight.value
            val widthDp = maxWidth.value
            val compact = heightDp < 78f
            val tiny = heightDp < 52f
            val verticalPadding = when {
                tiny -> 1.dp
                compact -> 2.dp
                else -> 2.5.dp
            }
            val horizontalPadding = if (widthDp < 54f) 4.dp else 5.dp
            val fontScaleCompensation = density.fontScale.coerceAtLeast(1f)
            val tabletFontBoost = if (gridColumnWidth >= 120.dp) 1.10f else 1f
            val previewFontScale = LocalPersonalizationPreview.current?.cardFontScale
            val courseFontScale = ((previewFontScale ?: config.courseCardFontScale) * tabletFontBoost)
                .coerceIn(0.80f, 1.35f)
            fun scaledCourseWeekText(value: TextUnit): TextUnit = scaledWeekText((value.value * courseFontScale).sp, fontScaleCompensation)
            val nameFont = scaledCourseWeekText(if (tiny) 8.8.sp else if (compact) 9.7.sp else 10.7.sp)
            val nameLineHeight = scaledCourseWeekText(if (tiny) 8.2.sp else if (compact) 9.1.sp else 10.0.sp)
            val locationFont = scaledCourseWeekText(if (tiny) 8.1.sp else if (compact) 8.7.sp else 9.5.sp)
            val locationLineHeight = scaledCourseWeekText(if (tiny) 8.0.sp else if (compact) 8.6.sp else 9.3.sp)
            val teacherFont = scaledCourseWeekText(8.4.sp)
            val teacherLineHeight = scaledCourseWeekText(7.9.sp)
            val contentWidthPx = with(density) { (maxWidth - horizontalPadding * 2f).coerceAtLeast(24.dp).toPx() }
            val availableTextPx = with(density) { (maxHeight - verticalPadding * 2f).coerceAtLeast(0.dp).toPx() }

            fun estimatedLines(text: String, fontSize: TextUnit): Int {
                if (text.isBlank()) return 0
                val averageCharPx = with(density) { fontSize.toPx() } * 1.08f
                val charsPerLine = (contentWidthPx / averageCharPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                return ceil(text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
            }

            val canShowTeacher = hasTeacher && heightDp >= 52f
            val teacherLines = if (canShowTeacher) 1 else 0
            val teacherPx = if (teacherLines > 0) with(density) { teacherLineHeight.toPx() } else 0f
            val usablePx = (availableTextPx - teacherPx).coerceAtLeast(0f)
            val averageLinePx = minOf(with(density) { nameLineHeight.toPx() }, with(density) { locationLineHeight.toPx() }).coerceAtLeast(1f)
            val totalSlots = (usablePx / averageLinePx).toInt().coerceAtLeast(1)
            val maxNameLines = when {
                heightDp >= 150f -> 12
                heightDp >= 112f -> 9
                heightDp >= 78f -> 6
                else -> 4
            }
            val wantedNameLines = estimatedLines(course.name, nameFont).coerceIn(1, maxNameLines)
            val wantedLocationLines = if (hasLocation) {
                estimatedLines(locationText, locationFont).coerceIn(1, if (heightDp >= 150f) 4 else if (heightDp >= 96f) 3 else 2)
            } else {
                0
            }
            val nameMinimum = 1
            val locationMinimum = if (hasLocation && (totalSlots >= 2 || tiny)) 1 else 0
            var remainingSlots = (totalSlots - nameMinimum - locationMinimum).coerceAtLeast(0)
            var nameLines = nameMinimum
            var locationLines = locationMinimum
            var nameNeed = (wantedNameLines - nameLines).coerceAtLeast(0)
            var locationNeed = (wantedLocationLines - locationLines).coerceAtLeast(0)
            while (remainingSlots > 0 && (nameNeed > 0 || locationNeed > 0)) {
                if (nameNeed >= locationNeed && nameNeed > 0) {
                    nameLines += 1
                    nameNeed -= 1
                } else if (locationNeed > 0) {
                    locationLines += 1
                    locationNeed -= 1
                } else {
                    nameLines += 1
                    nameNeed -= 1
                }
                remainingSlots -= 1
            }
            if (remainingSlots > 0 && nameLines < maxNameLines) {
                val extraNameLines = minOf(remainingSlots, maxNameLines - nameLines)
                nameLines += extraNameLines
                remainingSlots -= extraNameLines
            }
            if (remainingSlots > 0 && hasLocation) {
                locationLines += remainingSlots
            }
            if (tiny && hasLocation) {
                locationLines = 1
                nameLines = (totalSlots - locationLines).coerceAtLeast(1)
            }

            val renderedLocationLines = minOf(locationLines, wantedLocationLines).coerceAtLeast(0)
            val locationReserve = if (hasLocation && renderedLocationLines > 0) {
                with(density) { (locationLineHeight.toPx() * renderedLocationLines).toDp() }
            } else {
                0.dp
            }
            val teacherReserve = if (canShowTeacher) {
                with(density) { teacherLineHeight.toPx().toDp() }
            } else {
                0.dp
            }
            val centerReserve = maxOf(locationReserve, teacherReserve) + 1.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            ) {
                if (hasLocation && locationLines > 0) {
                    Text(
                        locationText,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        fontSize = locationFont,
                        lineHeight = locationLineHeight,
                        fontWeight = FontWeight.Medium,
                        color = courseTextColor.copy(alpha = 0.78f),
                        maxLines = locationLines,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    course.name,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(vertical = centerReserve),
                    fontSize = nameFont,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = courseTextColor,
                    maxLines = nameLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (canShowTeacher) {
                    Text(
                        course.teacher,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        fontSize = teacherFont,
                        lineHeight = teacherLineHeight,
                        fontWeight = FontWeight.Normal,
                        color = courseTextColor.copy(alpha = 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
            }
            if (conflictWarning && !editMode && !customTimeLocked) {
                val pillDismissProgress = conflictPillDismiss.value.coerceIn(0f, 1f)
                val pillTextColor =
                    if (glassUsesLightStyle(config)) ComposeColor.Black else ComposeColor.White
                GlassSurface(
                    backdrop = activeCardBackdrop,
                    config = config,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-4).dp)
                        .size(width = 34.dp, height = 20.dp)
                        .zIndex(8f)
                        .graphicsLayer {
                            val dismissScale = 1f - 0.30f * pillDismissProgress
                            scaleX = dismissScale
                            scaleY = dismissScale
                            alpha = 1f - pillDismissProgress
                        },
                    shape = RoundedCornerShape(50),
                    tokens = GlassTokens.pill(intensity = 0.86f).copy(
                        surfaceAlpha = 0.32f,
                        shadowAlpha = 0.18f,
                        innerShadowAlpha = 0.12f
                    ),
                    selected = true,
                    onClick = {
                        if (!conflictActionResolving) {
                            val moved = nearestAvailableCourseMove(
                                course = course,
                                week = editWeek,
                                courses = allWeekCourses,
                                periodIndexes = periodIndexes,
                                weekdayCount = weekdayCount
                            )
                            if (moved != null) {
                                conflictFlightTarget = moved
                                conflictActionResolving = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    conflictPillDismiss.snapTo(0f)
                                    conflictCardFlight.snapTo(0f)
                                    conflictPillDismiss.animateTo(
                                        1f,
                                        tween(
                                            durationMillis = 165,
                                            easing = CubicBezierEasing(0.32f, 0f, 0.68f, 1f)
                                        )
                                    )
                                    delay(24)
                                    conflictCardFlight.animateTo(
                                        1f,
                                        tween(
                                            durationMillis = 420,
                                            easing = CubicBezierEasing(0.16f, 0.82f, 0.18f, 1f)
                                        )
                                    )
                                    onResolveConflict(moved)
                                }
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                ) {
                    Text(
                        text = "冲突",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillTextColor
                    )
                }
            }
            AnimatedVisibility(
                visible = editMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-5).dp, y = (-5).dp)
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = editControlHandoffProgress.coerceIn(0f, 1f)
                        val handoffScale = 0.42f + editControlHandoffProgress * 0.58f
                        scaleX = handoffScale
                        scaleY = handoffScale
                    }
                    .zIndex(7f),
                enter = fadeIn(tween(125, delayMillis = (startupIndex % 7) * 9)) +
                    scaleIn(
                        animationSpec = spring(dampingRatio = 0.54f, stiffness = 520f),
                        initialScale = 0.28f,
                        transformOrigin = TransformOrigin(1f, 1f)
                    ),
                exit = fadeOut(tween(90)) +
                    scaleOut(tween(120), targetScale = 0.55f, transformOrigin = TransformOrigin(1f, 1f))
            ) {
                GlassSurface(
                    backdrop = activeCardBackdrop,
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(50),
                    tokens = GlassTokens.pill(intensity = 0.82f).copy(
                        surfaceAlpha = 0.36f,
                        shadowAlpha = 0.18f,
                        innerShadowAlpha = 0.14f
                    ),
                    selected = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDeleteSingleWeekCourse(course)
                    }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(50))
                            .background(ComposeColor(0xFFFF1F2D).copy(alpha = 0.48f))
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(10.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ComposeColor.White)
                    )
                }
            }
            AnimatedVisibility(
                visible = editMode && !customTimeLocked,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(44.dp)
                    .graphicsLayer {
                        alpha = editControlHandoffProgress.coerceIn(0f, 1f)
                        val handoffScale = 0.42f + editControlHandoffProgress * 0.58f
                        scaleX = handoffScale
                        scaleY = handoffScale
                    }
                    .zIndex(6f),
                enter = fadeIn(tween(135, delayMillis = 45 + (startupIndex % 7) * 8)) +
                    scaleIn(
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 470f),
                        initialScale = 0.32f,
                        transformOrigin = TransformOrigin(0f, 0f)
                    ),
                exit = fadeOut(tween(90)) +
                    scaleOut(tween(125), targetScale = 0.55f, transformOrigin = TransformOrigin(0f, 0f))
            ) {
                WeekResizeCornerHandle(
                    config = config,
                    selected = handleDragging,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (resizeHandleInputEnabled) {
                                Modifier.pointerInput(course.id, editWeek, currentSpan) {
                            val velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    buildWeekEditOverlayRequest(
                                        WeekEditOverlayMode.Resize,
                                        Offset(measuredCardWidthPx, baseHeightPx)
                                    )?.let { request ->
                                        handleDragging = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onStartWeekEditOverlay(request)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (handleDragging) {
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        onDragWeekEditOverlay(dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    if (handleDragging) {
                                        onFinishResizeOverlay(velocityTracker.calculateVelocity())
                                        handleDragging = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragCancel = {
                                    if (handleDragging) {
                                        handleDragging = false
                                        onCancelWeekEditOverlay()
                                    }
                                }
                            )
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            }
            }
        }
    }
}

private fun scaledWeekText(value: TextUnit, fontScale: Float): TextUnit {
    return (value.value / fontScale.coerceAtLeast(1f)).sp
}

internal fun androidx.compose.ui.text.TextStyle.scaledCourseCardStyle(scale: Float): androidx.compose.ui.text.TextStyle {
    val safeScale = scale.coerceIn(0.80f, 1.35f)
    val scaledFontSize = if (fontSize == TextUnit.Unspecified) fontSize else (fontSize.value * safeScale).sp
    val scaledLineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else (lineHeight.value * safeScale).sp
    return copy(fontSize = scaledFontSize, lineHeight = scaledLineHeight)
}
