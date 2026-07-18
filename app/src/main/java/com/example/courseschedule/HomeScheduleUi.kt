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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.foundation.layout.requiredSize
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
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
fun HomeDateTitle(
    state: AppState,
    displayDate: LocalDate,
    displayWeek: Int,
    beforeScheduleTerm: Boolean,
    showReturnToCurrentWeekHint: Boolean,
    onReturnCurrent: () -> Unit
) {
    val color = homeForegroundColor(state.config)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onReturnCurrent),
        verticalArrangement = Arrangement.spacedBy((-6).dp)
    ) {
        Text(
            "${displayDate.monthValue}月${displayDate.dayOfMonth}日 周${weekdayLabel(displayDate.dayOfWeek.toChineseWeekday())}",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Text(
            when {
                beforeScheduleTerm -> "当前暂未开学"
                showReturnToCurrentWeekHint -> "点击此处回到本周"
                else -> "第${displayWeek}周"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.78f),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun HomeModeSwitch(backdrop: Backdrop?, config: ScheduleConfigEntity, mode: HomeMode, onModeChange: (HomeMode) -> Unit) {
    val lightGlass = LocalAdaptiveGlass.current.lightGlass
    if (backdrop != null) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(104.dp)
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(width = 112.dp, height = 52.dp)
                    .clip(RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { if (mode == HomeMode.Day) 0 else 1 },
                    onTabSelected = { index -> onModeChange(if (index == 0) HomeMode.Day else HomeMode.Week) },
                    backdrop = backdrop,
                    tabsCount = 2,
                    modifier = Modifier.size(width = 104.dp, height = 44.dp),
                    containerHeight = 44.dp,
                    indicatorHeight = 36.dp,
                    horizontalPadding = 4.dp,
                    blurRadius = HomeHeaderGlassBlur,
                    containerAlpha = homeChromeGlassSurfaceAlpha(lightGlass),
                    lensHeight = HomeHeaderGlassLensHeight,
                    lensAmount = HomeHeaderGlassLensAmount,
                    indicatorWidthOverflow = 4.dp,
                    indicatorHeightOverflow = 2.dp,
                    indicatorLensHeight = HomeHeaderGlassLensHeight,
                    indicatorLensAmount = HomeHeaderGlassLensAmount,
                    officialHighlightAlpha = HomeHeaderGlassHighlightAlpha,
                    officialShadowAlpha = 0f,
                    officialInnerShadowAlpha = HomeHeaderGlassInnerShadowAlpha,
                    chromaticAberrationEnabled = true,
                    isLightThemeOverride = lightGlass,
                    useOfficialGlassParameters = true
                ) {
                    LiquidBottomTab(onClick = { onModeChange(HomeMode.Day) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_day_view), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("\u65E5", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    LiquidBottomTab(onClick = { onModeChange(HomeMode.Week) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_week_view), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("\u5468", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    } else {
        GlassPill(backdrop = null, config = config, modifier = Modifier.padding(end = 12.dp).height(44.dp).padding(4.dp)) {
            Row(Modifier.width(104.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeModePill(null, config, R.drawable.ic_day_view, "\u65E5") { onModeChange(HomeMode.Day) }
                HomeModePill(null, config, R.drawable.ic_week_view, "\u5468") { onModeChange(HomeMode.Week) }
            }
        }
    }
}

@Composable
fun HomeModePill(backdrop: Backdrop?, config: ScheduleConfigEntity, iconRes: Int, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(34.dp)
            .width(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (pressed) {
            GlassLens(
                backdrop = backdrop,
                config = config,
            pressProgress = 1f,
            modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: AppState,
    mode: HomeMode,
    weekCardHeight: Dp,
    displayWeek: Int,
    displayDate: LocalDate,
    backdrop: Backdrop?,
    floatingCourseBackdrop: Backdrop? = backdrop,
    weekHeaderBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit = {},
    onAgentAction: (AgentValidatedAction) -> Unit = {},
    onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    onDeleteCourseSingleWeek: (CourseEntity, Int) -> Unit = { _, _ -> },
    onScheduleLongPress: () -> Unit = {},
) {
    val homeOverscrollFactory = rememberHapticMiuixOverscrollFactory()
    val cardColor = remember(state.config.cardColorArgb, state.config.cardAlpha) {
        ComposeColor(state.config.cardColorArgb.toInt()).copy(alpha = state.config.cardAlpha)
    }
    val textColor = homeForegroundColor(state.config)
    var weekEditMode by remember { mutableStateOf(false) }
    var pendingSingleWeekDelete by remember { mutableStateOf<Pair<CourseEntity, Int>?>(null) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(weekEditMode, onScheduleLongPress) {
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
        BackHandler(enabled = mode == HomeMode.Week && weekEditMode) {
            weekEditMode = false
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val direction = if (targetState == HomeMode.Week) 1 else -1
                (
                    fadeIn(tween(180, delayMillis = 40)) +
                        slideInHorizontally(tween(200)) { direction * it / 10 }
                    ) togetherWith (
                    fadeOut(tween(120)) +
                        slideOutHorizontally(tween(180)) { -direction * it / 10 }
                    ) using SizeTransform(clip = false)
            },
            label = "home-mode"
        ) { targetMode ->
            when (targetMode) {
                HomeMode.Day -> CompositionLocalProvider(LocalOverscrollFactory provides homeOverscrollFactory) {
                    DayScheduleScreen(
                        state = state,
                        displayDate = displayDate,
                        displayWeek = effectiveCurrentWeek(state.config, displayDate),
                        cardColor = cardColor,
                        textColor = textColor,
                        backdrop = backdrop,
                        onSwipeDay = onSwipeDay,
                        onContentUnderTopBarChange = onContentUnderTopBarChange,
                        onCourseClick = onCourseClick,
                        onAddCourse = onAddCourse,
                        onAgentAction = onAgentAction
                    )
                }
                HomeMode.Week -> CompositionLocalProvider(LocalOverscrollFactory provides homeOverscrollFactory) {
                    SinglePillWeekScheduleScreen(
                        state = state,
                        displayWeek = displayWeek,
                        cardHeight = weekCardHeight,
                        cardColor = cardColor,
                        textColor = textColor,
                        backdrop = backdrop,
                        floatingCourseBackdrop = floatingCourseBackdrop,
                        headerBackdrop = weekHeaderBackdrop,
                        onSwipeWeek = onSwipeWeek,
                        onContentUnderTopBarChange = onContentUnderTopBarChange,
                        weekEditMode = weekEditMode,
                        onEnterWeekEditMode = { weekEditMode = true },
                        onUpdateCourseSingleWeek = onUpdateCourseSingleWeek,
                        onDeleteCourseSingleWeek = { course, week ->
                            pendingSingleWeekDelete = course to week
                        },
                        onCourseClick = { course, week, sourceBounds -> onCourseClick(course, week, sourceBounds) }
                    )
                }
            }
        }
        pendingSingleWeekDelete?.let { (course, week) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(80f)
                    .background(ComposeColor.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                LiquidAlertSurface(
                    title = "删除单周课程",
                    message = "确定删除第${week}周的“${course.name}”吗？只会删除当前周这一次，不会删除其它周的同名课程。",
                    actions = listOf(
                        LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) {
                            pendingSingleWeekDelete = null
                        },
                        LiquidAlertAction("确认删除", LiquidAlertActionStyle.Destructive) {
                            pendingSingleWeekDelete = null
                            onDeleteCourseSingleWeek(course, week)
                        }
                    ),
                    backdrop = backdrop,
                    config = state.config,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {}
                )
            }
        }
    }
}

@Composable
fun HomeWallpaper(
    config: ScheduleConfigEntity,
    images: HomeWallpaperImages,
    phase: StartupPhase,
    reduceQuality: Boolean = false
) {
    val targetBitmap = images.source
    val targetBlurredBitmap = images.blurredSource
    val targetReducedBitmap = images.reducedSource
    var visibleBitmap by remember { mutableStateOf<Bitmap?>(targetBitmap) }
    var visibleBlurredBitmap by remember { mutableStateOf<Bitmap?>(targetBlurredBitmap) }
    var visibleReducedBitmap by remember { mutableStateOf<Bitmap?>(targetReducedBitmap) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousBlurredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousReducedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crossfadeTarget by remember { mutableFloatStateOf(1f) }
    val crossfadeAlpha by animateFloatAsState(
        targetValue = crossfadeTarget,
        animationSpec = tween(durationMillis = 140),
        label = "wallpaper-crossfade"
    )
    LaunchedEffect(targetBitmap, targetBlurredBitmap, targetReducedBitmap) {
        if (
            targetBitmap != visibleBitmap ||
            targetBlurredBitmap != visibleBlurredBitmap ||
            targetReducedBitmap != visibleReducedBitmap
        ) {
            previousBitmap = visibleBitmap
            previousBlurredBitmap = visibleBlurredBitmap
            previousReducedBitmap = visibleReducedBitmap
            visibleBitmap = targetBitmap
            visibleBlurredBitmap = targetBlurredBitmap
            visibleReducedBitmap = targetReducedBitmap
            crossfadeTarget = 0f
            withFrameNanos { }
            crossfadeTarget = 1f
            delay(160)
            previousBitmap = null
            previousBlurredBitmap = null
            previousReducedBitmap = null
        }
    }
    val uri = config.wallpaperUri
    if (!uri.isNullOrBlank()) {
        val bitmap = visibleBitmap ?: return
        bitmap.let {
            Box(modifier = Modifier.fillMaxSize()) {
                previousBitmap?.let { old ->
                    HomeWallpaperLayer(
                        bitmap = wallpaperBitmapForRender(
                            source = old,
                            reduced = previousReducedBitmap,
                            blurred = previousBlurredBitmap,
                            reduceQuality = reduceQuality
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = true
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = true
                )
                WallpaperToneOverlay(config)
            }
        }
        return
    }
    if (config.defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN) {
        visibleBitmap?.let {
            Box(modifier = Modifier.fillMaxSize()) {
                previousBitmap?.let { old ->
                    HomeWallpaperLayer(
                        bitmap = wallpaperBitmapForRender(
                            source = old,
                            reduced = previousReducedBitmap,
                            blurred = previousBlurredBitmap,
                            reduceQuality = reduceQuality
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = false
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = false
                )
                WallpaperToneOverlay(config)
            }
        }
        return
    }
}

private fun wallpaperBitmapForRender(
    source: Bitmap,
    reduced: Bitmap?,
    blurred: Bitmap?,
    reduceQuality: Boolean
): Bitmap {
    return if (reduceQuality) {
        blurred ?: reduced ?: source
    } else {
        blurred ?: source
    }
}

@Composable
private fun HomeWallpaperLayer(
    bitmap: Bitmap,
    config: ScheduleConfigEntity,
    alpha: Float,
    useSavedCrop: Boolean
) {
    FocusCroppedWallpaper(
        bitmap = bitmap,
        config = config,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = alpha),
        useSavedCrop = useSavedCrop
    )
}

internal fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean {
    return !wallpaperUri.isNullOrBlank() || defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN
}

@Composable
internal fun HomeWallpaperLoadingMask(config: ScheduleConfigEntity) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (appUsesDarkTheme(config)) ComposeColor.Black else ComposeColor.White)
    )
}

@Composable
private fun WallpaperToneOverlay(config: ScheduleConfigEntity) {
    val dim = (1f - config.wallpaperBrightness.coerceIn(0.35f, 1f)).coerceIn(0f, 0.65f)
    if (dim > 0f) Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = dim)))
}

@Composable
fun HomeBackdropFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.035f))
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(260.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f))
        )
    }
}

@Composable
fun ApplyCourseEditDialog(
    original: CourseEntity,
    edited: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSingle: () -> Unit,
    onAll: () -> Unit,
    onCancel: () -> Unit
) {
    LiquidAlertSurface(
        title = "应用修改",
        message = "要将“${original.name}”的修改应用到哪里？仅单次只修改当前周，应用全部会修改这门课的所有周。",
        actions = listOf(
            LiquidAlertAction("仅单次", LiquidAlertActionStyle.Primary, onSingle),
            LiquidAlertAction("应用全部", LiquidAlertActionStyle.Secondary, onAll),
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onCancel)
        ),
        backdrop = backdrop,
        config = config
    )
}

@Composable
fun ApplyCourseDeleteDialog(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSingle: () -> Unit,
    onAll: () -> Unit,
    onCancel: () -> Unit
) {
    LiquidAlertSurface(
        title = "删除课程",
        message = "要将“${course.name}”从哪里删除？仅单次只删除当前周，删除全部会删除这门课的所有周。",
        actions = listOf(
            LiquidAlertAction("仅删除本周", LiquidAlertActionStyle.Primary, onSingle),
            LiquidAlertAction("删除全部", LiquidAlertActionStyle.Destructive, onAll),
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onCancel)
        ),
        backdrop = backdrop,
        config = config
    )
}

internal fun courseWeeksChanged(original: CourseEntity, edited: CourseEntity): Boolean {
    return original.weeks.sorted() != edited.weeks.sorted() || original.weekParity != edited.weekParity
}

internal fun coursesVisibleInWeek(courses: List<CourseEntity>, week: Int): List<CourseEntity> {
    val visible = courses.filter { course ->
        week in course.weeks && parityMatches(course.weekParity, week)
    }
    val singleWeekOverrideKeys = visible
        .filter { it.weeks.distinct() == listOf(week) }
        .map { it.occurrenceOverrideKey() }
        .toSet()
    if (singleWeekOverrideKeys.isEmpty()) return visible
    return visible.filter { course ->
        course.weeks.distinct() == listOf(week) || course.occurrenceOverrideKey() !in singleWeekOverrideKeys
    }
}

internal data class WeekCourseBuckets(
    val visibleCourses: List<CourseEntity>,
    val byWeekday: Map<Int, List<CourseEntity>>,
    val weekendHasCourse: Boolean
)

internal val SchoolWeekdays = (1..5).toList()
internal val FullWeekdays = (1..7).toList()

internal fun weekCourseBuckets(courses: List<CourseEntity>, week: Int): WeekCourseBuckets {
    val visibleCourses = coursesVisibleInWeek(courses, week)
    return WeekCourseBuckets(
        visibleCourses = visibleCourses,
        byWeekday = visibleCourses.groupBy { it.weekday },
        weekendHasCourse = visibleCourses.any { it.weekday == 6 || it.weekday == 7 }
    )
}

internal fun visibleWeekdaysForBuckets(
    buckets: WeekCourseBuckets,
    hideEmptyWeekends: Boolean
): List<Int> = if (hideEmptyWeekends && !buckets.weekendHasCourse) {
    SchoolWeekdays
} else {
    FullWeekdays
}

internal fun CourseEntity.occurrenceOverrideKey(): String {
    return listOf(
        weekday.toString(),
        periods.distinct().sorted().joinToString(","),
        name.trim()
    ).joinToString("|")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperColorSamplerScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val config = state.config
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        config.wallpaperUri,
        config.defaultWallpaperStyle,
        useDarkDefaultWallpaper
    ) {
        value = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)
        }
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var sampledColor by remember(config.cardColorArgb) { mutableStateOf(config.cardColorArgb) }
    val sampledComposeColor = ComposeColor(sampledColor.toInt())
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NormalizedDialogHeader(
            title = "壁纸取色",
            onCancel = onCancel,
            onSave = { onSelected(sampledColor) },
            backdrop = backdrop,
            config = config
        )
        Text(
            "点击壁纸预览中的位置，课程卡片会使用该处颜色。",
            color = glassForegroundColor(config).copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                .onSizeChanged { previewSize = it }
                .pointerInput(bitmap, previewSize) {
                    detectTapGestures { tap ->
                        val sourceBitmap = bitmap ?: return@detectTapGestures
                        val orientation = if (previewSize.height >= previewSize.width) WallpaperPreviewOrientation.Portrait else WallpaperPreviewOrientation.Landscape
                        val cropState = if (!config.wallpaperUri.isNullOrBlank()) config.wallpaperCropState(orientation) else WallpaperCropState()
                        val color = sampleCroppedBitmapColor(sourceBitmap, previewSize, tap.x, tap.y, cropState)
                        if (color != null) sampledColor = color
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val wallpaperBitmap = bitmap
            if (wallpaperBitmap != null) {
                FocusCroppedWallpaper(
                    bitmap = wallpaperBitmap,
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    useSavedCrop = !config.wallpaperUri.isNullOrBlank()
                )
            } else {
                Text("当前没有可取色的壁纸", color = glassForegroundColor(config))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(46.dp),
                shape = RoundedCornerShape(50),
                color = sampledComposeColor,
                border = BorderStroke(2.dp, readableOn(sampledComposeColor).copy(alpha = 0.72f))
            ) {}
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0xFFD6E9FF, 0xFFBFE0FF, 0xFF9ED4FF, 0xFFFFE1E8,
                0xFFFFC4D6, 0xFFD8F3DC, 0xFFB7E4C7, 0xFFFFF0C2,
                0xFFFFD166, 0xFFE8D7FF, 0xFFD7C0FF, 0xFFE8EAED
            ).forEach { color ->
                val selected = sampledColor == color
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(50),
                    color = ComposeColor(color.toInt()),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                    ),
                    onClick = { sampledColor = color }
                ) {}
            }
        }
    }
}

fun sampleCroppedBitmapColor(
    bitmap: Bitmap,
    previewSize: IntSize,
    tapX: Float,
    tapY: Float,
    cropState: WallpaperCropState
): Long? {
    if (previewSize.width <= 0 || previewSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return null
    val rect = calculateFocusCropRect(
        bitmap.width,
        bitmap.height,
        previewSize.width.toFloat(),
        previewSize.height.toFloat(),
        cropState
    )
    if (rect == androidx.compose.ui.geometry.Rect.Zero || rect.width <= 0f || rect.height <= 0f) return null
    val bitmapX = ((tapX - rect.left) * bitmap.width / rect.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val bitmapY = ((tapY - rect.top) * bitmap.height / rect.height).roundToInt().coerceIn(0, bitmap.height - 1)
    return bitmap.getPixel(bitmapX, bitmapY).toLong() and 0xFFFFFFFFL
}

@Composable
fun homeForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    if (config.wallpaperUri.isNullOrBlank()) return MaterialTheme.colorScheme.onBackground
    return LocalAdaptiveGlass.current.contentColor
}

fun readableOn(background: ComposeColor): ComposeColor {
    val alpha = background.alpha.coerceIn(0f, 1f)
    val blended = if (alpha < 1f) {
        val fallback = if (background.luminance() < 0.5f) ComposeColor.Black else ComposeColor.White
        blendOver(background, fallback)
    } else {
        background
    }
    return if (blended.luminance() < 0.54f) ComposeColor.White else ComposeColor.Black
}

fun deepenColor(color: ComposeColor, amount: Float = 0.18f): ComposeColor {
    val mix = amount.coerceIn(0f, 1f)
    return ComposeColor(
        red = color.red * (1f - mix),
        green = color.green * (1f - mix),
        blue = color.blue * (1f - mix),
        alpha = color.alpha
    )
}

fun blendOver(foreground: ComposeColor, background: ComposeColor): ComposeColor {
    val alpha = foreground.alpha.coerceIn(0f, 1f)
    return ComposeColor(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f
    )
}

fun ComposeColor.luminance(): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

@Composable
fun glassForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return if (glassUsesLightStyle(config)) ComposeColor.Black else ComposeColor.White
}

@Composable
fun appPanelForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return if (appUsesDarkTheme(config)) ComposeColor.White else ComposeColor(0xFF111111)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayScheduleScreen(
    state: AppState,
    displayDate: LocalDate,
    displayWeek: Int,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit,
    onAgentAction: (AgentValidatedAction) -> Unit
) {
    val centerPage = 10_000
    val anchorDate = remember { displayDate }
    val pagerState = rememberPagerState(
        initialPage = centerPage,
        pageCount = { centerPage * 2 + 1 }
    )
    var gestureCommittedDate by remember { mutableStateOf<LocalDate?>(null) }
    var programmaticDayScroll by remember { mutableStateOf(false) }

    fun dateForPage(page: Int): LocalDate = anchorDate.plusDays((page - centerPage).toLong())

    LaunchedEffect(pagerState, displayDate) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.settledPage,
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            )
        }.distinctUntilChanged().collect { (scrolling, settledPage, pagePosition) ->
            if (!scrolling || programmaticDayScroll) return@collect
            val delta = pagePosition - settledPage
            val desiredPage = when {
                delta >= 0.75f -> settledPage + 1
                delta <= -0.75f -> settledPage - 1
                else -> settledPage
            }.coerceIn(0, centerPage * 2)
            val desiredDate = dateForPage(desiredPage)
            if (desiredDate != displayDate) {
                gestureCommittedDate = desiredDate
                onSwipeDay(ChronoUnit.DAYS.between(displayDate, desiredDate).toInt())
            }
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (programmaticDayScroll) return@LaunchedEffect
        val settledDate = dateForPage(pagerState.settledPage)
        if (settledDate != displayDate) {
            gestureCommittedDate = settledDate
            onSwipeDay(ChronoUnit.DAYS.between(displayDate, settledDate).toInt())
        } else {
            gestureCommittedDate = null
        }
    }
    LaunchedEffect(displayDate) {
        if (gestureCommittedDate == displayDate) return@LaunchedEffect
        val dayOffset = ChronoUnit.DAYS.between(anchorDate, displayDate).toInt()
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
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
            val targetDate = dateForPage(page)
            val targetWeek = effectiveCurrentWeek(state.config, targetDate)
            val targetWeekday = targetDate.dayOfWeek.toChineseWeekday()
            val dayCourses = remember(state.courses, targetWeek, targetWeekday) {
                weekCourseBuckets(state.courses, targetWeek)
                    .byWeekday[targetWeekday]
                    .orEmpty()
                    .sortedWith(compareBy<CourseEntity> { it.periods.minOrNull() ?: Int.MAX_VALUE }.thenBy { it.name })
            }
            val listState = rememberLazyListState()
            val contentUnderTopBar by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            val agentCollapsed by remember {
                // The agent card changes height when it collapses. Using its pixel offset as the
                // threshold makes a long message move the list back across that threshold, which
                // repeatedly expands/collapses the sticky header during overscroll.
                derivedStateOf { listState.firstVisibleItemIndex > 0 }
            }
            LaunchedEffect(contentUnderTopBar) {
                if (page == pagerState.settledPage) {
                    onContentUnderTopBarChange(contentUnderTopBar)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = HomeInitialTopInset, bottom = DayDockScrollPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "${targetDate.monthValue}月${targetDate.dayOfMonth}日 周${weekdayLabel(targetWeekday)} · 第${targetWeek}周",
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                if (
                    targetDate == LocalDate.now(ZoneId.of("Asia/Shanghai")) &&
                    abs(page - pagerState.currentPage) <= 1
                ) {
                    stickyHeader(key = "today-agent-${state.config.id}") {
                        TodayAgentHost(
                            state = state,
                            date = targetDate,
                            backdrop = backdrop,
                            textColor = textColor,
                            collapsed = agentCollapsed,
                            onAgentAction = onAgentAction
                        )
                    }
                }
                if (dayCourses.isEmpty()) item { Text("这一天没有课程", color = textColor) }
                itemsIndexed(dayCourses, key = { _, it -> it.id }) { index, course ->
                    DayTimelineCourse(course, targetWeek, state.periods, cardColor, backdrop, state.config, onCourseClick, entranceIndex = index)
                }
            }
    }
}

@Composable
fun DayTimelineCourse(course: CourseEntity, currentWeek: Int, periods: List<PeriodEntity>, cardColor: ComposeColor, backdrop: Backdrop?, config: ScheduleConfigEntity, onCourseClick: (CourseEntity, Int, Rect?) -> Unit, entranceIndex: Int = 0) {
    val resolvedCardColor = courseCardBaseColor(config, course)
    val timePillColor = deepenColor(resolvedCardColor, 0.16f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassSurface(
            backdrop = backdrop,
            config = config,
            modifier = Modifier.wrapContentWidth(),
            shape = RoundedCornerShape(50),
            tokens = GlassTokens.pill(intensity = 0.75f)
        ) {
            Box(Modifier.background(timePillColor.copy(alpha = 0.26f))) {
                Text(
                    courseTimeLabel(course, periods),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = readableOn(timePillColor)
                )
            }
        }
        CourseCard(course, periods, showTime = false, showWeeks = false, cardColor = cardColor, backdrop = backdrop, config = config, onClick = { sourceBounds -> onCourseClick(course, currentWeek, sourceBounds) }, entranceIndex = entranceIndex)
    }
}

@Composable
fun CourseCard(course: CourseEntity, periods: List<PeriodEntity>, showTime: Boolean = true, showWeeks: Boolean = true, cardColor: ComposeColor = MaterialTheme.colorScheme.surfaceVariant, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), onClick: ((Rect?) -> Unit)? = null, entranceIndex: Int? = null, enableSharedTransition: Boolean = true) {
    val resolvedCardColor = if (config.cardColorArgb == MulticolorCourseCardArgb) courseCardBaseColor(config, course) else cardColor
    val textColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else readableOn(resolvedCardColor)
    var ownBounds by remember { mutableStateOf<Rect?>(null) }
    val editId = LocalEditingCourseId.current
    val startupPhase = LocalStartupPhase.current
    val sharedScope = if (startupPhase == StartupPhase.FullQuality && enableSharedTransition && course.id > 0L) LocalSharedTransitionScope.current else null
    val startIndex = entranceIndex ?: 0
    val entranceOrigin = if (startIndex % 2 == 0) {
        if (startIndex < 2) StartupFlyOrigin.Left else StartupFlyOrigin.BottomLeft
    } else {
        if (startIndex < 2) StartupFlyOrigin.Right else StartupFlyOrigin.BottomRight
    }
    val entranceModifier = Modifier
        .then(
            if (entranceIndex != null) {
                Modifier.startupFlyIn(
                    key = "day_${course.id}_${startIndex}",
                    index = startIndex,
                    totalCount = 36,
                    origin = entranceOrigin,
                    intensity = if (startIndex < 2) 0.68f else 0.95f,
                    delayFactor = 0.12f,
                    alphaStart = 0f
                )
            } else {
                Modifier
            }
        )
        .onGloballyPositioned { coordinates ->
            ownBounds = coordinates.boundsInRoot()
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editId != course.id,
        sharedScope = sharedScope,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) { sharedModifier ->
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        course = course,
        modifier = sharedModifier.then(entranceModifier),
        shape = RoundedCornerShape(24.dp),
        onClick = if (onClick != null) ({ onClick(ownBounds) }) else null
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, color = textColor)
            if (showTime) Text(courseTimeLabel(course, periods) + " · 第 " + course.periods.joinToString(",") + " 节", color = textColor.copy(alpha = 0.86f))
            if (!course.location.isNullOrBlank()) Text("地点：" + course.location, color = textColor.copy(alpha = 0.86f))
            if (!course.teacher.isNullOrBlank()) Text("教师：" + course.teacher, color = textColor.copy(alpha = 0.86f))
            if (showWeeks) Text("周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity), color = textColor.copy(alpha = 0.86f))
            if (!course.note.isNullOrBlank()) Text("备注：" + course.note, color = textColor.copy(alpha = 0.86f))
        }
    }
    }
}

@Composable
fun ImportPreviewCourseCard(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity = defaultConfig()
) {
    val cardColor = courseCardBaseColor(config, course).copy(alpha = config.cardAlpha.coerceIn(0f, 1f))
    val textColor = readableOn(cardColor)
    CourseGlassCard(
        backdrop = null,
        config = config,
        course = course,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(courseTimeLabel(course, periods) + " · 第 " + course.periods.joinToString(",") + " 节", color = textColor.copy(alpha = 0.86f))
            if (!course.location.isNullOrBlank()) Text("地点：" + course.location, color = textColor.copy(alpha = 0.86f))
            if (!course.teacher.isNullOrBlank()) Text("教师：" + course.teacher, color = textColor.copy(alpha = 0.86f))
            Text("周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity), color = textColor.copy(alpha = 0.86f))
            if (!course.note.isNullOrBlank()) Text("备注：" + course.note, color = textColor.copy(alpha = 0.86f))
        }
    }
}
