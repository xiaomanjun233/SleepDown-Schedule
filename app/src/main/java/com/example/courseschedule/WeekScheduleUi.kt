package com.example.courseschedule

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import java.io.File
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun WeekScheduleScreen(state: AppState, displayWeek: Int, cardHeight: Dp, cardColor: ComposeColor, textColor: ComposeColor, backdrop: Backdrop?, onSwipeWeek: (Int) -> Unit, onCourseClick: (CourseEntity, Int, Rect?) -> Unit) {
    val weekdays = FullWeekdays
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
    val weekBuckets = remember(state.courses, displayWeek) {
        weekCourseBuckets(state.courses, displayWeek)
    }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(displayWeek, state.config.totalWeeks) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            horizontalDrag <= -80f -> onSwipeWeek(1)
                            horizontalDrag >= 80f -> onSwipeWeek(-1)
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                WeekSwitchButton("<", state.config, backdrop, enabled = displayWeek > 1) { onSwipeWeek(-1) }
                Text(
                    "第${displayWeek}周",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                WeekSwitchButton(">", state.config, backdrop, enabled = displayWeek < state.config.totalWeeks) { onSwipeWeek(1) }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(rowHeaderWidth)) {
                    Box(Modifier.height(42.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("节次", style = MaterialTheme.typography.labelMedium, color = textColor) }
                    state.periods.forEach { period ->
                        Box(
                            Modifier
                                .height(cardHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    period.periodIndex.toString(),
                                    fontSize = 13.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = textColor
                                )
                                Text(
                                    period.startTime,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    textAlign = TextAlign.Center,
                                    color = textColor.copy(alpha = 0.86f)
                                )
                                Text(
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

                weekdays.forEach { day ->
                    Column(modifier = Modifier.weight(1f)) {
                        Box(Modifier.height(42.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val isToday = day == LocalDate.now().dayOfWeek.toChineseWeekday()
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isToday) MaterialTheme.colorScheme.primaryContainer else ComposeColor.Transparent
                        ) {
                            Text(
                                "周" + weekdayLabel(day),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                color = textColor
                            )
                        }
                    }
                    WeekDayColumn(
                        courses = weekBuckets.byWeekday[day].orEmpty(),
                        periods = state.periods,
                        cardHeight = cardHeight,
                        cardColor = cardColor,
                        emptyBackground = ComposeColor.Transparent,
                        backdrop = backdrop,
                        config = state.config,
                        onCourseClick = { course, sourceBounds -> onCourseClick(course, displayWeek, sourceBounds) }
                    )
                }
                }
            }
            Spacer(Modifier.height(WeekDockScrollPadding))
        }
    }
}

@Composable
fun SinglePillWeekScheduleScreen(
    state: AppState,
    displayWeek: Int,
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
    onDeleteCourseSingleWeek: (CourseEntity, Int) -> Unit = { _, _ -> },
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit
) {
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
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
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
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
                val offscreenOffset = with(density) { (screenWidth + 88.dp).toPx() } * direction
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
    val weekEditOverlay = rememberWeekEditOverlayController(scrollState)
    val overlayScreenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val overlayEdgePx = with(density) { 88.dp.toPx() }
    LaunchedEffect(displayWeek, weekEditMode) {
        if (!weekEditMode) weekEditOverlay.clear()
    }
    LaunchedEffect(state.courses, displayWeek) {
        if (weekEditOverlay.awaitingCommit) {
            withFrameNanos { }
            withFrameNanos { }
            delay(if (weekEditOverlay.request?.mode == WeekEditOverlayMode.Resize) 180 else 90)
            weekEditOverlay.clear()
        }
    }

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
        Column {
            Spacer(Modifier.height(HomeInitialTopInset - 22.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                WeekSwitchButton("<", state.config, headerBackdrop, enabled = displayWeek > 1) { onSwipeWeek(-1) }
                Text(
                    text = "第${displayWeek}周",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                WeekSwitchButton(">", state.config, headerBackdrop, enabled = displayWeek < state.config.totalWeeks) { onSwipeWeek(1) }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WeekHeaderPill(headerBackdrop, state.config, selected = false) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.width(rowHeaderWidth - 4.dp).fillMaxHeight(),
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
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight * state.periods.size)
                        .clipToBounds()
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
                                    Text(
                                        period.periodIndex.toString(),
                                        fontSize = 13.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = textColor
                                    )
                                    Text(
                                        period.startTime,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.Center,
                                        color = textColor.copy(alpha = 0.86f)
                                    )
                                    Text(
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        outgoingCourses.value?.let { oldCourses ->
                            WeekCourseColumnsLayer(
                                modifier = Modifier.padding(start = rowHeaderWidth),
                                courses = oldCourses,
                                weekdays = outgoingWeekdays.value,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                floatingBackdrop = floatingCourseBackdrop,
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
                                .fillMaxWidth()
                                .height(cardHeight * state.periods.size),
                            userScrollEnabled = !weekEditMode,
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
                                modifier = Modifier.padding(start = rowHeaderWidth),
                                courses = pageCourses,
                                weekdays = pageWeekdays,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                floatingBackdrop = floatingCourseBackdrop,
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
                                        periodIndexes = periodIndexes,
                                        weekdayCount = pageWeekdays.size,
                                        onUpdateCourseSingleWeek = onUpdateCourseSingleWeek
                                    )
                                },
                                onFinishResizeOverlay = { velocity ->
                                    weekEditOverlay.finishResize(
                                        velocity = velocity,
                                        periodIndexes = periodIndexes,
                                        weekdayCount = pageWeekdays.size,
                                        resizePaddingPx = with(density) { 4.dp.toPx() },
                                        onUpdateCourseSingleWeek = onUpdateCourseSingleWeek
                                    )
                                },
                                onCancelWeekEditOverlay = weekEditOverlay::clear,
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
            gridOffsetY = weekEditOverlay.gridOffsetY,
            gridScrollCompensationY = weekEditOverlay.gridScrollCompensationY,
            heightPx = weekEditOverlay.height,
            backdrop = floatingCourseBackdrop ?: backdrop,
            config = state.config
        )
    }
}

@Composable
private fun WeekEditOverlayHost(
    request: WeekEditOverlayRequest?,
    hostBounds: Rect?,
    offsetX: Float,
    offsetY: Float,
    overlayScale: Float,
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
    val left = req.sourceBounds.left - host.left + offsetX
    val top = req.sourceBounds.top - host.top + offsetY
    val target = when (req.mode) {
        WeekEditOverlayMode.Move -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex + (offsetX / req.gridColumnWidthPx).roundToInt(),
            startPeriod = req.periodIndex + (gridOffsetY / req.periodRowHeightPx).roundToInt(),
            span = req.currentSpan,
            weekdayCount = 7
        )
        WeekEditOverlayMode.Resize -> weekCourseEditTarget(
            periodIndexes = req.periodIndexes,
            weekday = req.dayIndex,
            startPeriod = req.periodIndex,
            span = (heightPx / req.periodRowHeightPx).roundToInt().coerceIn(1, req.maxSpan),
            weekdayCount = 7
        )
    }
    val previewCourse = when (req.mode) {
        WeekEditOverlayMode.Move -> req.course.copy(weekday = target.weekday, periods = target.periods)
        WeekEditOverlayMode.Resize -> req.course.copy(periods = target.periods)
    }
    val conflict = !target.valid || hasWeekCourseEditConflict(req.course, previewCourse, req.weekCourses, req.editWeek)
    val previewLeft = req.sourceBounds.left - host.left + (target.weekday - req.dayIndex) * req.gridColumnWidthPx
    val previewTop = req.sourceBounds.top - host.top - gridScrollCompensationY +
        ((target.periods.firstOrNull() ?: req.periodIndex) - req.periodIndex) * req.periodRowHeightPx
    val previewHeight = (req.periodRowHeightPx * target.periods.size.coerceAtLeast(1) - with(density) { 4.dp.toPx() }).coerceAtLeast(with(density) { 18.dp.toPx() })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(previewLeft.roundToInt(), previewTop.roundToInt()) }
                .width(widthDp)
                .height(with(density) { previewHeight.toDp() })
                .clip(RoundedCornerShape(9.dp))
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
                    scaleX = overlayScale
                    scaleY = overlayScale
                }
        ) {
            CourseGlassCard(
                backdrop = backdrop,
                config = config,
                course = req.course,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
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
        val courseFontScale = config.courseCardFontScale.coerceIn(0.80f, 1.35f)
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
fun LiquidWeekScheduleScreen(
    state: AppState,
    displayWeek: Int,
    cardHeight: Dp,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    headerBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit
) {
    val weekdays = FullWeekdays
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = scheduleWeekStartDate(state.config, displayWeek, today)
    val weekBuckets = remember(state.courses, displayWeek) {
        weekCourseBuckets(state.courses, displayWeek)
    }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(displayWeek, state.config.totalWeeks) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            horizontalDrag <= -80f -> onSwipeWeek(1)
                            horizontalDrag >= 80f -> onSwipeWeek(-1)
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                WeekSwitchButton("<", state.config, headerBackdrop, enabled = displayWeek > 1) { onSwipeWeek(-1) }
                Text(
                    text = "第${displayWeek}周",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                WeekSwitchButton(">", state.config, headerBackdrop, enabled = displayWeek < state.config.totalWeeks) { onSwipeWeek(1) }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(rowHeaderWidth)) {
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                        WeekHeaderPill(headerBackdrop, state.config, selected = false) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "节次",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    state.periods.forEach { period ->
                        Box(
                            modifier = Modifier
                                .height(cardHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    period.periodIndex.toString(),
                                    fontSize = 13.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = textColor
                                )
                                Text(
                                    period.startTime,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    textAlign = TextAlign.Center,
                                    color = textColor.copy(alpha = 0.86f)
                                )
                                Text(
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

                weekdays.forEach { day ->
                    val isToday = day == today.dayOfWeek.toChineseWeekday()
                    val date = weekStart.plusDays((day - 1).toLong())
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WeekHeaderPill(headerBackdrop, state.config, selected = isToday) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "周${weekdayLabel(day)}",
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
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
                        WeekDayColumn(
                            courses = weekBuckets.byWeekday[day].orEmpty(),
                            periods = state.periods,
                            cardHeight = cardHeight,
                            cardColor = cardColor,
                            emptyBackground = ComposeColor.Transparent,
                            backdrop = backdrop,
                            config = state.config,
                            onCourseClick = { course, sourceBounds -> onCourseClick(course, displayWeek, sourceBounds) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(WeekDockScrollPadding))
        }
    }
}

@Composable
fun WeekSwitchButton(label: String, config: ScheduleConfigEntity, backdrop: Backdrop?, enabled: Boolean, onClick: () -> Unit) {
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) ComposeColor.White else ComposeColor(0xFF121212)
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
            blurRadius = HomeHeaderGlassBlur,
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
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
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeekScheduleHeaderLabels(
    weekdays: List<Int>,
    weekStart: LocalDate,
    today: LocalDate,
    rowHeaderWidth: Dp,
    textColor: ComposeColor,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(rowHeaderWidth - 4.dp).fillMaxHeight(),
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
        weekdays.forEach { day ->
            val date = weekStart.plusDays((day - 1).toLong())
            val isToday = date == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(vertical = 2.dp, horizontal = 2.dp)
                    .then(
                        if (isToday) Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        else Modifier
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
        tokens = homeHeaderGlassTokens(glassUsesLightStyle(config)),
        selected = selected,
        content = content
    )
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
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onDragStateChanged: (dayIndex: Int?, courseId: Long?) -> Unit = { _, _ -> },
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
    val periodIndexes = periods.map { it.periodIndex }
    var periodCursor = 0
    while (periodCursor < periods.size) {
        val period = periods[periodCursor]
        val startingCourses = courses
            .filter { courseStartsAt(it, period.periodIndex) }
            .sortedBy { it.name }
        if (startingCourses.isEmpty()) {
            EmptyWeekCell(cardHeight, emptyBackground)
            periodCursor += 1
        } else {
            val span = startingCourses.maxOf { continuousSpanFrom(it, period.periodIndex, periodIndexes) }.coerceAtLeast(1)
            MergedWeekCell(
                courses = startingCourses,
                periods = periods,
                height = cardHeight * span.toFloat(),
                cardColor = cardColor,
                background = emptyBackground,
                backdrop = backdrop,
                floatingBackdrop = floatingBackdrop,
                config = config,
                weekMotionDirection = weekMotionDirection,
                weekMotionOutgoing = weekMotionOutgoing,
                dayIndex = dayIndex,
                gridColumnWidth = gridColumnWidth,
                periodRowHeight = periodRowHeight,
                periodIndex = period.periodIndex,
                layerOffset = layerOffset,
                layerTravel = layerTravel,
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
            periodCursor += span
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
    val travel = with(density) { (LocalConfiguration.current.screenWidthDp.dp + 96.dp).toPx() }
    val coursesByWeekday = remember(courses) { courses.groupBy { it.weekday } }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = layerOffset.value + gestureOffset() }
    ) {
        val dayColumnWidth = maxWidth / weekdays.size.coerceAtLeast(1)
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
                        onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                        onCourseClick = onCourseClick,
                        onDragStateChanged = { dayIndex, courseId ->
                            draggingDayIndex = dayIndex
                            draggingCourseId = courseId
                        },
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

private fun courseStartsAt(course: CourseEntity, periodIndex: Int): Boolean {
    if (periodIndex !in course.periods) return false
    return (periodIndex - 1) !in course.periods
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

private data class WeekCourseEditTarget(
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
    val editWeek: Int
)

@Composable
private fun rememberWeekEditOverlayController(scrollState: ScrollState): WeekEditOverlayController {
    val scope = rememberCoroutineScope()
    return remember(scope, scrollState) { WeekEditOverlayController(scope, scrollState) }
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
    private var scrollCompensationY by mutableFloatStateOf(0f)
    private var autoScrollDirection by mutableIntStateOf(0)
    private var dragPointerY by mutableFloatStateOf(0f)

    val offsetX: Float get() = overlayX.value
    val offsetY: Float get() = overlayY.value
    val height: Float get() = overlayHeight.value
    val scale: Float get() = overlayScale.value
    val gridOffsetY: Float get() = overlayY.value + scrollCompensationY
    val gridScrollCompensationY: Float get() = scrollCompensationY

    fun clear() {
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = null
        scrollCompensationY = 0f
        autoScrollDirection = 0
        dragPointerY = 0f
    }

    fun start(nextRequest: WeekEditOverlayRequest) {
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = nextRequest
        scope.launch {
            overlayX.snapTo(0f)
            overlayY.snapTo(0f)
            overlayHeight.snapTo(nextRequest.sourceBounds.height)
            overlayScale.snapTo(1.035f)
            scrollCompensationY = 0f
            autoScrollDirection = 0
            dragPointerY = nextRequest.sourceBounds.center.y
        }
    }

    fun drag(
        delta: Offset,
        screenHeightPx: Float,
        edgePx: Float,
        resizePaddingPx: Float
    ) {
        val activeRequest = request ?: return
        scope.launch {
            when (activeRequest.mode) {
                WeekEditOverlayMode.Move -> {
                    overlayX.snapTo(overlayX.value + delta.x)
                    overlayY.snapTo(overlayY.value + delta.y)
                    dragPointerY += delta.y
                    autoScroll(activeRequest, screenHeightPx, edgePx)
                }
                WeekEditOverlayMode.Resize -> {
                    val minHeight = activeRequest.periodRowHeightPx - resizePaddingPx
                    val maxHeight = activeRequest.periodRowHeightPx * activeRequest.maxSpan - resizePaddingPx
                    overlayHeight.snapTo((overlayHeight.value + delta.y).coerceIn(minHeight, maxHeight))
                }
            }
        }
    }

    fun finishMove(
        velocity: Velocity,
        periodIndexes: List<Int>,
        weekdayCount: Int,
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ) {
        val activeRequest = request ?: return
        val projectedX = overlayX.value + velocity.x * 0.08f
        val projectedGridY = gridOffsetY + velocity.y * 0.08f
        val target = weekCourseEditTarget(
            periodIndexes = periodIndexes,
            weekday = activeRequest.dayIndex + (projectedX / activeRequest.gridColumnWidthPx).roundToInt(),
            startPeriod = activeRequest.periodIndex + (projectedGridY / activeRequest.periodRowHeightPx).roundToInt(),
            span = activeRequest.currentSpan,
            weekdayCount = weekdayCount
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
        scope.launch {
            launch {
                overlayX.animateTo(
                    targetX,
                    spring(dampingRatio = 0.72f, stiffness = 520f),
                    initialVelocity = velocity.x
                )
            }
            launch {
                overlayScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.78f, stiffness = 520f)
                )
            }
            overlayY.animateTo(
                targetY,
                spring(dampingRatio = 0.72f, stiffness = 520f),
                initialVelocity = velocity.y
            )
            if (canSave) {
                committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
                committedTargetWeek = activeRequest.editWeek
                onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
                awaitingCommit = true
            } else {
                clear()
                overlayX.snapTo(0f)
                overlayY.snapTo(0f)
                overlayScale.snapTo(1f)
            }
        }
    }

    fun finishResize(
        velocity: Velocity,
        periodIndexes: List<Int>,
        weekdayCount: Int,
        resizePaddingPx: Float,
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ) {
        val activeRequest = request ?: return
        val projectedHeight = overlayHeight.value + velocity.y * 0.08f
        val targetSpan = (projectedHeight / activeRequest.periodRowHeightPx)
            .roundToInt()
            .coerceIn(1, activeRequest.maxSpan)
        val target = weekCourseEditTarget(
            periodIndexes = periodIndexes,
            weekday = activeRequest.dayIndex,
            startPeriod = activeRequest.periodIndex,
            span = targetSpan,
            weekdayCount = weekdayCount
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
        scope.launch {
            launch {
                overlayScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.78f, stiffness = 520f)
                )
            }
            overlayHeight.animateTo(
                targetHeight,
                spring(dampingRatio = 0.72f, stiffness = 480f),
                initialVelocity = velocity.y
            )
            if (canSave) {
                committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
                committedTargetWeek = activeRequest.editWeek
                onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
                awaitingCommit = true
            } else {
                clear()
                overlayHeight.snapTo(0f)
                overlayScale.snapTo(1f)
            }
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

private fun weekCourseEditTarget(
    periodIndexes: List<Int>,
    weekday: Int,
    startPeriod: Int,
    span: Int,
    weekdayCount: Int
): WeekCourseEditTarget {
    val safeWeekday = weekday.coerceIn(1, weekdayCount.coerceAtLeast(1))
    val startPosition = periodIndexes.indexOf(startPeriod)
    if (startPosition < 0 || span <= 0) {
        return WeekCourseEditTarget(safeWeekday, emptyList(), false)
    }
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
fun MergedWeekCell(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    height: Dp,
    cardColor: ComposeColor,
    background: ComposeColor,
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
    val hasDraggingCourse = draggingCourseId?.let { id -> courses.any { it.id == id } } == true
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(2.dp)
            .background(background)
            .zIndex(if (hasDraggingCourse) 1f else 0f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            courses.take(2).forEachIndexed { stackIndex, course ->
                val courseHeight = if (courses.size > 1) (height - 8.dp) / 2 else height - 4.dp
                WeekCourseBlock(
                    course = course,
                    periods = periods,
                    height = courseHeight,
                    cardColor = cardColor,
                    backdrop = backdrop,
                    floatingBackdrop = floatingBackdrop,
                    config = config,
                    weekMotionDirection = weekMotionDirection,
                    weekMotionOutgoing = weekMotionOutgoing,
                    dayIndex = dayIndex,
                    periodIndex = periodIndex,
                    gridColumnWidth = gridColumnWidth,
                    periodRowHeight = periodRowHeight,
                    layerOffset = layerOffset,
                    layerTravel = layerTravel,
                    stackIndex = stackIndex,
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
                    onCancelWeekEditOverlay = onCancelWeekEditOverlay
                )
            }
            if (courses.size > 2) Text("+${courses.size - 2}", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
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
    onCancelWeekEditOverlay: () -> Unit = {}
) {
    val locationText = course.location.orEmpty()
    val hasLocation = locationText.isNotBlank()
    val hasTeacher = !course.teacher.isNullOrBlank()
    val resolvedCardColor = if (config.cardColorArgb == MulticolorCourseCardArgb) courseCardBaseColor(config, course) else cardColor
    val courseTextColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else readableOn(resolvedCardColor)
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
    val isOverlayTarget = activeOverlayTargetKey != null &&
        activeOverlayTargetWeek == editWeek &&
        activeOverlayTargetWeek in course.weeks &&
        course.occurrenceOverrideKey() == activeOverlayTargetKey
    val isOverlaySource = activeOverlayCourseId == course.id || isOverlayTarget
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val currentSpan = remember(course.periods, periodIndex, periodIndexes) {
        continuousSpanFrom(course, periodIndex, periodIndexes)
            .takeIf { it > 0 }
            ?: course.periods.size.coerceAtLeast(1)
    }
    var moveDragX by remember(course.id, editWeek) { mutableFloatStateOf(0f) }
    var moveDragY by remember(course.id, editWeek) { mutableFloatStateOf(0f) }
    var resizeDragY by remember(course.id, editWeek) { mutableFloatStateOf(0f) }
    var resizeVelocityHintY by remember(course.id, editWeek) { mutableFloatStateOf(0f) }
    var bodyDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var handleDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var settlingResizeTarget by remember(course.id, editWeek) { mutableStateOf<WeekCourseEditTarget?>(null) }
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edgeScrollThresholdPx = with(density) { 92.dp.toPx() }
    val measuredCardWidthPx = ownBounds?.width?.coerceAtLeast(1f) ?: with(density) { 48.dp.toPx() }
    val gridColumnWidthPx = with(density) { gridColumnWidth.toPx() }
        .takeIf { it > 1f }
        ?: measuredCardWidthPx
    val periodRowHeightPx = with(density) { periodRowHeight.toPx() }.coerceAtLeast(1f)
    fun moveTarget(): WeekCourseEditTarget {
        val dayDelta = (moveDragX / gridColumnWidthPx).roundToInt()
        val periodDelta = (moveDragY / periodRowHeightPx).roundToInt()
        return weekCourseEditTarget(
            periodIndexes = periodIndexes,
            weekday = dayIndex + dayDelta,
            startPeriod = periodIndex + periodDelta,
            span = currentSpan,
            weekdayCount = weekdayCount
        )
    }
    fun resizeTargetForDrag(dragY: Float): WeekCourseEditTarget {
        val spanDelta = (dragY / periodRowHeightPx).roundToInt()
        return weekCourseEditTarget(
            periodIndexes = periodIndexes,
            weekday = dayIndex,
            startPeriod = periodIndex,
            span = (currentSpan + spanDelta).coerceIn(1, periodIndexes.size.coerceAtLeast(1)),
            weekdayCount = weekdayCount
        )
    }
    fun resizeTarget(): WeekCourseEditTarget = resizeTargetForDrag(resizeDragY)
    val liveTarget = when {
        bodyDragging -> moveTarget()
        handleDragging -> resizeTarget()
        else -> null
    }
    val activeCardBackdrop = if (bodyDragging || handleDragging) {
        floatingBackdrop ?: backdrop
    } else {
        backdrop
    }
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
        targetValue = if (bodyDragging || handleDragging) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "week-edit-press-scale-${course.id}"
    )
    val settledResizeHeight = settlingResizeTarget?.let { target ->
        val rawHeight = periodRowHeight * target.periods.size.coerceAtLeast(1).toFloat() - 4.dp
        if (rawHeight < 18.dp) 18.dp else rawHeight
    } ?: height
    val animatedSettledResizeHeight by animateDpAsState(
        targetValue = settledResizeHeight,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 430f),
        label = "week-edit-saved-height-${course.id}"
    )
    val displayedHeight = if (settlingResizeTarget != null) animatedSettledResizeHeight else height
    val resizeStartIndex = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
    val resizeMaxSpan = (periodIndexes.size - resizeStartIndex).coerceAtLeast(1)
    val baseHeightPx = with(density) { height.toPx() }
    val minResizeHeightPx = with(density) { periodRowHeightPx - 4.dp.toPx() }
    val maxResizeHeightPx = with(density) { periodRowHeightPx * resizeMaxSpan - 4.dp.toPx() }
    val resizeRevealHeight = if (handleDragging) {
        with(density) {
            (baseHeightPx + resizeDragY)
                .coerceAtLeast(minResizeHeightPx)
                .coerceAtMost(maxResizeHeightPx)
                .toDp()
        }
    } else {
        displayedHeight
    }
    val resizeGlassShellHeight = if (handleDragging || settlingResizeTarget != null) {
        with(density) { maxResizeHeightPx.toDp() }
    } else {
        resizeRevealHeight
    }
    fun buildWeekEditOverlayRequest(mode: WeekEditOverlayMode): WeekEditOverlayRequest? {
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
            editWeek = editWeek
        )
    }
    val dragActive = bodyDragging || handleDragging || settlingResizeTarget != null
    LaunchedEffect(dragActive) {
        if (dragActive) onDragStateChanged(dayIndex, course.id) else onDragStateChanged(null, null)
    }
    val bodyGestureModifier = Modifier.pointerInput(editMode, course.id, editWeek, currentSpan) {
        if (editMode) {
            val velocityTracker = VelocityTracker()
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    velocityTracker.resetTracking()
                    bodyDragging = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    buildWeekEditOverlayRequest(WeekEditOverlayMode.Move)?.let(onStartWeekEditOverlay)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    onDragWeekEditOverlay(dragAmount)
                },
                onDragEnd = {
                    bodyDragging = false
                    onFinishMoveOverlay(velocityTracker.calculateVelocity())
                },
                onDragCancel = {
                    bodyDragging = false
                    onCancelWeekEditOverlay()
                }
            )
        } else {
            detectTapGestures(
                onTap = { onCourseClick(course, ownBounds) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnterEditMode()
                }
            )
        }
    }
    val editingId = LocalEditingCourseId.current
    val launchingId = LocalLaunchingCourseId.current
    val launchPressScale = rememberCoursePressScale(
        pressed = false,
        forcePressed = launchingId == course.id
    )
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
    val tailModifier = Modifier
        .graphicsLayer {
            val tailX = layerOffset?.let { offset ->
                val progress = (kotlin.math.abs(offset.value) / layerTravel.coerceAtLeast(1f)).coerceIn(0f, 1f)
                tailBase * progress * tailDirection
            } ?: 0f
            translationX = tailX
        }
        .onGloballyPositioned { coordinates ->
            ownBounds = coordinates.boundsInRoot()
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editingId != course.id,
        sharedScope = sharedScope,
        modifier = baseModifier,
        shape = RoundedCornerShape(8.dp)
    ) { sharedModifier ->
        Box(
            modifier = sharedModifier
                .then(startupModifier)
                .then(tailModifier)
                .then(bodyGestureModifier)
                .zIndex(if (bodyDragging || handleDragging || settlingResizeTarget != null) 3f else 0f)
        ) {
            if (bodyDragging && liveTarget != null) {
                val target = liveTarget
                val targetOffsetX = (target.weekday - dayIndex) * gridColumnWidthPx
                val targetOffsetY = ((target.periods.firstOrNull() ?: periodIndex) - periodIndex) * periodRowHeightPx
                val rawTargetHeight = periodRowHeight * target.periods.size.coerceAtLeast(1).toFloat() - 4.dp
                val targetPreviewHeight = if (rawTargetHeight < 18.dp) 18.dp else rawTargetHeight
                val editedCourse = course.copy(weekday = target.weekday, periods = target.periods)
                val targetHasConflict = !target.valid || hasWeekCourseEditConflict(course, editedCourse, allWeekCourses, editWeek)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(targetPreviewHeight)
                        .graphicsLayer {
                            translationX = targetOffsetX
                            translationY = targetOffsetY
                        }
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (targetHasConflict) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
                            } else {
                                ComposeColor.Gray.copy(alpha = 0.24f)
                            }
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resizeRevealHeight)
                    .graphicsLayer {
                        val activeScale = if (bodyDragging || handleDragging) {
                            1.035f
                        } else {
                            pressScale * launchPressScale
                        }
                        translationX = if (bodyDragging) moveDragX else 0f
                        translationY = if (bodyDragging) moveDragY else 0f
                        rotationZ = if (bodyDragging || handleDragging) 0f else editJitter
                        scaleX = activeScale
                        scaleY = activeScale
                        alpha = if (isOverlaySource) 0f else 1f
                    }
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resizeRevealHeight)
                    .clipToBounds()
            ) {
            CourseGlassCard(
                backdrop = activeCardBackdrop,
                config = config,
                course = course,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resizeGlassShellHeight),
                shape = RoundedCornerShape(8.dp),
                forcePressed = false,
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
            val courseFontScale = config.courseCardFontScale.coerceIn(0.80f, 1.35f)
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
            if (editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-5).dp, y = (-5).dp)
                        .size(22.dp)
                        .zIndex(7f)
                        .clip(RoundedCornerShape(50))
                        .background(ComposeColor(0xFFFF1F2D).copy(alpha = 0.96f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDeleteSingleWeekCourse(course)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ComposeColor.White)
                    )
                }
                WeekResizeCornerHandle(
                    config = config,
                    selected = handleDragging,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(44.dp)
                        .zIndex(6f)
                        .pointerInput(course.id, editWeek, currentSpan) {
                            val velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    handleDragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    buildWeekEditOverlayRequest(WeekEditOverlayMode.Resize)?.let(onStartWeekEditOverlay)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    onDragWeekEditOverlay(dragAmount)
                                },
                                onDragEnd = {
                                    handleDragging = false
                                    onFinishResizeOverlay(velocityTracker.calculateVelocity())
                                },
                                onDragCancel = {
                                    handleDragging = false
                                    onCancelWeekEditOverlay()
                                }
                            )
                        }
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
