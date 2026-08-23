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
import androidx.core.graphics.get
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.CourseGlassOcclusionPhase
import com.xiaomanjun.sleepdownschedule.glass.CourseGlassRestorePlan
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassOcclusionPhase
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassRestorePlan
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
import java.time.LocalTime
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

data class HomeReadabilityContext(
    val bitmap: Bitmap? = null,
    val config: ScheduleConfigEntity? = null,
    val rootSize: IntSize = IntSize.Zero
)

val LocalHomeReadability = compositionLocalOf { HomeReadabilityContext() }

internal enum class HomeReadabilityShadow {
    None,
    Dark,
    Light
}

internal fun homeReadabilityShadowForLuminance(
    textLuminance: Float,
    backgroundLuminance: Float,
    current: HomeReadabilityShadow
): HomeReadabilityShadow = when {
    textLuminance >= 0.62f -> {
        if (backgroundLuminance >= if (current == HomeReadabilityShadow.Dark) 0.43f else 0.53f) {
            HomeReadabilityShadow.Dark
        } else HomeReadabilityShadow.None
    }
    textLuminance <= 0.38f -> {
        if (backgroundLuminance <= if (current == HomeReadabilityShadow.Light) 0.42f else 0.32f) {
            HomeReadabilityShadow.Light
        } else HomeReadabilityShadow.None
    }
    else -> HomeReadabilityShadow.None
}

private fun regionTextShadow(
    context: HomeReadabilityContext,
    bounds: Rect,
    textColor: ComposeColor,
    current: HomeReadabilityShadow
): HomeReadabilityShadow {
    val visibleLuminance = sampleVisibleWallpaperLuminance(context, bounds)
        ?: return HomeReadabilityShadow.None
    // Symmetric adaptive contrast: light text receives a dark shadow on bright wallpaper, while
    // dark text receives a very soft white shadow only over locally dark wallpaper. Hysteresis in
    // both directions prevents labels from toggling during fractional pager/list motion.
    return homeReadabilityShadowForLuminance(textColor.luminance(), visibleLuminance, current)
}

private fun sampleVisibleWallpaperLuminance(
    context: HomeReadabilityContext,
    bounds: Rect,
    columns: Int = 5,
    rows: Int = 3
): Float? {
    if (bounds.width <= 0f || bounds.height <= 0f) return null
    val bitmap = context.bitmap ?: return null
    val config = context.config ?: return null
    val root = context.rootSize
    if (root.width <= 0 || root.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return null
    }
    val useSavedCrop = !config.wallpaperUri.isNullOrBlank()
    val cropState = if (useSavedCrop) {
        config.wallpaperCropState(
            if (root.width <= root.height) WallpaperPreviewOrientation.Portrait
            else WallpaperPreviewOrientation.Landscape
        )
    } else WallpaperCropState()
    val drawn = calculateFocusCropRect(
        bitmapWidth = bitmap.width,
        bitmapHeight = bitmap.height,
        containerWidth = root.width.toFloat(),
        containerHeight = root.height.toFloat(),
        cropState = cropState
    )
    if (drawn.width <= 0f || drawn.height <= 0f) return null
    var weightedLuminance = 0f
    var samples = 0
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val rootX = bounds.left + bounds.width * ((column + 0.5f) / columns)
            val rootY = bounds.top + bounds.height * ((row + 0.5f) / rows)
            val x = (((rootX - drawn.left) / drawn.width) * bitmap.width)
                .roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (((rootY - drawn.top) / drawn.height) * bitmap.height)
                .roundToInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap[x, y]
            val color = ComposeColor(pixel)
            weightedLuminance += color.luminance()
            samples++
        }
    }
    val dim = (1f - config.wallpaperBrightness.coerceIn(0.35f, 1f)).coerceIn(0f, 0.65f)
    return weightedLuminance / samples.coerceAtLeast(1) * (1f - dim)
}

internal fun homeStatusBarWallpaperLuminance(context: HomeReadabilityContext): Float? {
    val root = context.rootSize
    if (root.width <= 0 || root.height <= 0) return null
    // Status-bar appearance is global, so sample both icon clusters across the complete top strip.
    // The crop mapping is shared with HomeReadableText and therefore follows the visible wallpaper,
    // including a user-saved portrait/landscape crop rather than the uncropped source bitmap.
    return sampleVisibleWallpaperLuminance(
        context = context,
        bounds = Rect(
            left = 0f,
            top = 0f,
            right = root.width.toFloat(),
            bottom = (root.height * 0.06f).coerceAtLeast(1f)
        ),
        columns = 9,
        rows = 3
    )
}

@Composable
fun HomeReadableText(
    text: String,
    modifier: Modifier = Modifier,
    color: ComposeColor,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val readability = LocalHomeReadability.current
    var readabilityShadow by remember(readability.bitmap, readability.config, readability.rootSize, color) {
        mutableStateOf(HomeReadabilityShadow.None)
    }
    val measuredModifier = modifier.onGloballyPositioned { coordinates ->
        val next = regionTextShadow(
            readability,
            coordinates.boundsInRoot(),
            color,
            readabilityShadow
        )
        if (next != readabilityShadow) readabilityShadow = next
    }
    Box(
        modifier = measuredModifier
    ) {
        Text(
            text = text,
            color = color,
            style = when (readabilityShadow) {
                HomeReadabilityShadow.Dark -> style.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = ComposeColor.Black.copy(alpha = 0.44f),
                        offset = Offset(0f, 1.2f),
                        blurRadius = 12f
                    )
                )
                HomeReadabilityShadow.Light -> style.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = ComposeColor.White.copy(alpha = 0.38f),
                        offset = Offset(0f, 1f),
                        blurRadius = 9f
                    )
                )
                HomeReadabilityShadow.None -> style
            },
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow
        )
    }
}

@Composable
fun HomeDateTitle(
    state: AppState,
    displayDate: LocalDate,
    displayWeek: Int,
    beforeScheduleTerm: Boolean,
    afterScheduleTerm: Boolean,
    showReturnToCurrentWeekHint: Boolean,
    onReturnCurrent: () -> Unit
) {
    val color = homeForegroundColor(state.config)
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = Modifier
            .height(42.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onReturnCurrent)
    ) {
        val density = LocalDensity.current
        val requestedLineHeightPx = with(density) { 18.sp.toPx() + 21.sp.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        // Keep the accepted 16sp/19sp appearance at normal font scale. If accessibility font
        // scaling would make the two physical line boxes exceed the same 42dp occupied by the
        // adjacent top-bar buttons, shrink both lines by only the overflow ratio. This avoids
        // clipping either line without changing the bar height or its alignment.
        val lineScale = (availableHeightPx / requestedLineHeightPx.coerceAtLeast(1f))
            .coerceIn(0.1f, 1f)
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            HomeReadableText(
                when {
                    beforeScheduleTerm -> "当前暂未开学"
                    afterScheduleTerm -> "学期已结束"
                    showReturnToCurrentWeekHint -> "点击此处回到本周"
                    else -> "第${displayWeek}周"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (16f * lineScale).sp,
                    lineHeight = (18f * lineScale).sp
                ),
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.68f),
                maxLines = 1,
                softWrap = false
            )
            HomeReadableText(
                "${displayDate.monthValue}月${displayDate.dayOfMonth}日 周${weekdayLabel(displayDate.dayOfWeek.toChineseWeekday())}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (19f * lineScale).sp,
                    lineHeight = (21f * lineScale).sp
                ),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                    .requiredSize(width = 112.dp, height = 52.dp),
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
                    blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
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
                    containerShadowEnabled = false,
                    indicatorShadowEnabled = false,
                    indicatorInnerShadowEnabled = false,
                    chromaticAberrationEnabled = true,
                    isLightThemeOverride = lightGlass,
                    lightContainerColor = HomeLightGlassSurfaceColor,
                    lightAccentColor = HomeLightGlassSelectedAccentColor,
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
internal fun HomeScreen(
    state: AppState,
    agentState: AppState = state,
    personalizationPreviewState: PersonalizationPreviewState,
    mode: HomeMode,
    adaptiveMetrics: HomeAdaptiveMetrics,
    weekCardHeight: Dp,
    displayWeek: Int,
    displayDate: LocalDate,
    backdrop: Backdrop?,
    dayAgentBackdrop: Backdrop? = backdrop,
    floatingCourseBackdrop: Backdrop? = backdrop,
    weekHeaderBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    dayAgentBackgroundMotionState: DayAgentBackgroundMotionState,
    onAgentPagerSettledChange: (Boolean) -> Unit = {},
    onAgentPrepareOpen: suspend () -> Unit = {},
    onAgentDismissed: () -> Unit = {},
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit = {},
    onAgentAction: AgentActionHandler = { _, _ -> },
    onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity, Int) -> Unit = { _, _, _ -> },
    onDeleteCourseSingleWeek: (CourseEntity, Int) -> Unit = { _, _ -> },
    onScheduleLongPress: () -> Unit = {},
    weekEditInteractionEnabled: Boolean = true,
    courseGlassOcclusionPhase: CourseGlassOcclusionPhase = CourseGlassOcclusionPhase.Live,
    courseGlassRestoreWave: Int = com.xiaomanjun.sleepdownschedule.glass.CourseGlassRestoreWaveCount,
) {
    val homeOverscrollFactory = rememberHapticMiuixOverscrollFactory()
    val cardColor = remember(state.config.cardColorArgb, state.config.cardAlpha) {
        ComposeColor(state.config.cardColorArgb.toInt()).copy(alpha = state.config.cardAlpha)
    }
    val textColor = homeForegroundColor(state.config)
    var weekEditMode by remember(state.config.id) { mutableStateOf(false) }
    var pendingSingleWeekDelete by remember(state.config.id) {
        mutableStateOf<Pair<CourseEntity, Int>?>(null)
    }
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // `weekCardHeight` already resolves to the adaptive value when the user has not chosen one.
    // Recomputing it here on tablets discarded live slider updates and made personalization look
    // broken even though the config was saved correctly.
    val effectiveWeekCardHeight = weekCardHeight

    LaunchedEffect(state.config.id, weekEditInteractionEnabled) {
        if (!weekEditInteractionEnabled) {
            // The multi-schedule picker temporarily owns the home gesture surface. Clear the
            // edit session before it opens so returning to Home recreates the long-press entry
            // path instead of leaving the root pointer input in its edit-mode tap-only branch.
            weekEditMode = false
            pendingSingleWeekDelete = null
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                weekEditMode = false
                pendingSingleWeekDelete = null
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
                        cardColor = cardColor,
                        textColor = textColor,
                        backdrop = backdrop,
                        dayAgentBackdrop = dayAgentBackdrop,
                        onSwipeDay = onSwipeDay,
                        onContentUnderTopBarChange = onContentUnderTopBarChange,
                        dayAgentBackgroundMotionState = dayAgentBackgroundMotionState,
                        onAgentPagerSettledChange = onAgentPagerSettledChange,
                        onAgentPrepareOpen = onAgentPrepareOpen,
                        onAgentDismissed = onAgentDismissed,
                        onCourseClick = onCourseClick,
                        onAddCourse = onAddCourse,
                        onAgentAction = onAgentAction
                    )
                }
                HomeMode.Week -> key(state.config.id) {
                    val courseGlassRestorePlan = remember(
                        courseGlassOcclusionPhase,
                        displayWeek,
                        courseGlassRestoreWave
                    ) {
                        CourseGlassRestorePlan(
                            phase = courseGlassOcclusionPhase,
                            targetWeek = displayWeek,
                            restoredWave = courseGlassRestoreWave
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
                            adaptiveMetrics = adaptiveMetrics,
                            cardHeight = effectiveWeekCardHeight,
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
                            conflictFocusCourseId = conflictFocusCourseId,
                            conflictFocusCourseKey = conflictFocusCourseKey,
                            onResolveCourseConflict = onResolveCourseConflict,
                            onDeleteCourseSingleWeek = { course, week ->
                                pendingSingleWeekDelete = course to week
                            },
                            onCourseClick = { course, week, sourceBounds ->
                                onCourseClick(course, week, sourceBounds)
                            }
                        )
                    }
                }
            }
        }
        pendingSingleWeekDelete?.let { (course, week) ->
            LiquidAlertDialog(
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
                onDismissRequest = { pendingSingleWeekDelete = null }
            )
        }
    }
}

@Composable
internal fun HomeWallpaper(
    config: ScheduleConfigEntity,
    images: HomeWallpaperImages,
    phase: StartupPhase,
    reduceQuality: Boolean = false,
    previewState: PersonalizationPreviewState? = null
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
                            reduceQuality = reduceQuality,
                            transientBlurActive = previewState?.wallpaperBlur != null
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = true,
                        previewBlurDp = previewState?.wallpaperBlur
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality,
                        transientBlurActive = previewState?.wallpaperBlur != null
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = true,
                    previewBlurDp = previewState?.wallpaperBlur
                )
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
                            reduceQuality = reduceQuality,
                            transientBlurActive = previewState?.wallpaperBlur != null
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = false,
                        previewBlurDp = previewState?.wallpaperBlur
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality,
                        transientBlurActive = previewState?.wallpaperBlur != null
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = false,
                    previewBlurDp = previewState?.wallpaperBlur
                )
            }
        }
        return
    }
}

private fun wallpaperBitmapForRender(
    source: Bitmap,
    reduced: Bitmap?,
    blurred: Bitmap?,
    reduceQuality: Boolean,
    transientBlurActive: Boolean
): Bitmap {
    if (transientBlurActive) return reduced ?: source
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
    useSavedCrop: Boolean,
    previewBlurDp: Float?
) {
    val density = LocalDensity.current
    val previewBlurPx = with(density) { (previewBlurDp ?: 0f).dp.toPx() }
    FocusCroppedWallpaper(
        bitmap = bitmap,
        config = config,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                renderEffect = if (previewBlurPx > 0.1f) {
                    BlurEffect(previewBlurPx, previewBlurPx, TileMode.Decal)
                } else {
                    null
                }
            },
        useSavedCrop = useSavedCrop
    )
}

internal fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean {
    return !wallpaperUri.isNullOrBlank() || defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN
}

@Composable
internal fun WallpaperToneOverlay(
    config: ScheduleConfigEntity,
    previewState: PersonalizationPreviewState? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val brightness = (previewState?.wallpaperBrightness ?: config.wallpaperBrightness)
                    .coerceIn(0.35f, 1f)
                val totalDim = (1f - brightness).coerceIn(0f, 0.65f)
                val samplingDim = wallpaperGlassSamplingDim(brightness)
                // The sampling tone is already inside backgroundBackdrop. Apply only the
                // mathematically exact remainder here so the visible wallpaper still reaches the
                // user's requested brightness instead of being darkened twice.
                val dim = if (samplingDim >= 0.999f) {
                    0f
                } else {
                    ((totalDim - samplingDim) / (1f - samplingDim)).coerceIn(0f, 0.65f)
                }
                if (dim > 0f) drawRect(ComposeColor.Black.copy(alpha = dim))
            }
    )
}

internal fun wallpaperGlassSamplingDim(brightness: Float): Float {
    val wallpaperDim = (1f - brightness.coerceIn(0.35f, 1f)).coerceIn(0f, 0.65f)
    // Quadratic onset keeps glass nearly unchanged for small wallpaper adjustments; the 0.72
    // gain then keeps even the darkest setting materially brighter than the wallpaper itself.
    return (wallpaperDim * wallpaperDim * 0.72f).coerceIn(0f, wallpaperDim)
}

@Composable
internal fun WallpaperGlassSamplingToneOverlay(
    config: ScheduleConfigEntity,
    previewState: PersonalizationPreviewState? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val brightness = previewState?.wallpaperBrightness ?: config.wallpaperBrightness
                val dim = wallpaperGlassSamplingDim(brightness)
                if (dim > 0f) drawRect(ComposeColor.Black.copy(alpha = dim))
            }
    )
}

@Composable
fun HomeBackdropFallback() {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f

    Canvas(Modifier.fillMaxSize()) {
        drawRect(colors.background)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to colors.primaryContainer.copy(alpha = if (dark) 0.34f else 0.42f),
                    0.24f to colors.surfaceVariant.copy(alpha = if (dark) 0.18f else 0.24f),
                    0.52f to colors.background,
                    0.76f to colors.tertiaryContainer.copy(alpha = if (dark) 0.22f else 0.30f),
                    1f to colors.secondaryContainer.copy(alpha = if (dark) 0.18f else 0.26f)
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.18f to colors.primary.copy(alpha = if (dark) 0.18f else 0.15f),
                    0.42f to colors.secondary.copy(alpha = if (dark) 0.10f else 0.09f),
                    0.66f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                start = Offset(-size.width * 0.20f, size.height * 0.02f),
                end = Offset(size.width * 1.12f, size.height * 0.62f)
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.30f to ComposeColor.Transparent,
                    0.54f to colors.tertiary.copy(alpha = if (dark) 0.14f else 0.12f),
                    0.76f to colors.primary.copy(alpha = if (dark) 0.10f else 0.08f),
                    1f to ComposeColor.Transparent
                ),
                start = Offset(size.width * 1.18f, size.height * 0.18f),
                end = Offset(-size.width * 0.12f, size.height * 0.96f)
            )
        )
        // A wallpaper normally provides the local edges that make refraction readable. These
        // narrow, low-contrast bands provide the same sampling structure without turning the
        // wallpaper-free state into a busy illustration.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.16f to ComposeColor.Transparent,
                    0.23f to colors.onBackground.copy(alpha = if (dark) 0.045f else 0.026f),
                    0.29f to ComposeColor.Transparent,
                    0.47f to ComposeColor.Transparent,
                    0.54f to colors.primary.copy(alpha = if (dark) 0.075f else 0.050f),
                    0.61f to ComposeColor.Transparent,
                    0.79f to ComposeColor.Transparent,
                    0.86f to colors.onBackground.copy(alpha = if (dark) 0.038f else 0.022f),
                    0.92f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.38f to ComposeColor.Transparent,
                    0.47f to colors.secondary.copy(alpha = if (dark) 0.065f else 0.042f),
                    0.54f to colors.onBackground.copy(alpha = if (dark) 0.032f else 0.018f),
                    0.62f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                start = Offset(-size.width * 0.08f, size.height * 0.70f),
                end = Offset(size.width * 1.08f, size.height * 0.28f)
            )
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
    LiquidAlertDialog(
        title = "应用修改",
        message = "要将“${original.name}”的修改应用到哪里？仅单次只修改当前周，应用全部会修改这门课的所有周。",
        actions = listOf(
            LiquidAlertAction("仅单次", LiquidAlertActionStyle.Primary, onClick = onSingle),
            LiquidAlertAction("应用全部", LiquidAlertActionStyle.Secondary, onClick = onAll),
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onCancel)
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onCancel
    )
}

@Composable
fun CourseConflictRetentionDialog(
    course: CourseEntity,
    conflictWeeks: List<Int>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onKeepTemporarily: () -> Unit,
    onReturn: () -> Unit,
    retentionMessage: String? = null
) {
    val weekSummary = conflictWeeks.take(4).joinToString("、") { "第${it}周" } +
        if (conflictWeeks.size > 4) "等${conflictWeeks.size}周" else ""
    LiquidAlertDialog(
        title = "检测到课程冲突",
        message = retentionMessage
            ?: "“${course.name}”在${weekSummary}与其他课程重合。可以暂时保留；保存后会跳到首个冲突周，点课程上的“冲突”即可移到最近空位。",
        actions = listOf(
            LiquidAlertAction("暂时保留", LiquidAlertActionStyle.Primary, onClick = onKeepTemporarily),
            LiquidAlertAction("返回修改", LiquidAlertActionStyle.Secondary, onClick = onReturn)
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onReturn
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
    LiquidAlertDialog(
        title = "删除课程",
        message = "要将“${course.name}”从哪里删除？仅单次只删除当前周，删除全部会删除这门课的所有周。",
        actions = listOf(
            LiquidAlertAction("仅删除本周", LiquidAlertActionStyle.Primary, onClick = onSingle),
            LiquidAlertAction("删除全部", LiquidAlertActionStyle.Destructive, onClick = onAll),
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onCancel)
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onCancel
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
        customStartTime.orEmpty(),
        customEndTime.orEmpty(),
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
    return bitmap[bitmapX, bitmapY].toLong() and 0xFFFFFFFFL
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
internal fun DayScheduleScreen(
    state: AppState,
    agentState: AppState = state,
    adaptiveMetrics: HomeAdaptiveMetrics,
    displayDate: LocalDate,
    displayWeek: Int,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    dayAgentBackdrop: Backdrop? = backdrop,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    dayAgentBackgroundMotionState: DayAgentBackgroundMotionState,
    onAgentPagerSettledChange: (Boolean) -> Unit,
    onAgentPrepareOpen: suspend () -> Unit,
    onAgentDismissed: () -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onAddCourse: (CourseEntity) -> Unit,
    onAgentAction: AgentActionHandler
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

    LaunchedEffect(pagerState, displayDate) {
        snapshotFlow {
            !pagerState.isScrollInProgress &&
                pagerState.currentPage == pagerState.settledPage &&
                kotlin.math.abs(pagerState.currentPageOffsetFraction) < 0.0005f &&
                dateForPage(pagerState.settledPage) == displayDate
        }.distinctUntilChanged().collect(onAgentPagerSettledChange)
    }
    DisposableEffect(Unit) {
        onDispose { onAgentPagerSettledChange(false) }
    }

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
            val desiredDate = clampToNavigationRange(dateForPage(desiredPage))
            if (desiredDate != displayDate) {
                gestureCommittedDate = desiredDate
                onSwipeDay(ChronoUnit.DAYS.between(displayDate, desiredDate).toInt())
            }
        }
    }
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
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
            val targetDate = dateForPage(page)
            val targetWeekOrNull = scheduleWeekForDateOrNull(state.config, targetDate)
            val targetWeek = targetWeekOrNull ?: effectiveCurrentWeek(state.config, targetDate)
            val targetWeekday = targetDate.dayOfWeek.toChineseWeekday()
            val dayCourses = remember(state.courses, state.periods, targetWeekOrNull, targetWeekday) {
                if (targetWeekOrNull == null) emptyList() else weekCourseBuckets(state.courses, targetWeekOrNull)
                    .byWeekday[targetWeekday]
                    .orEmpty()
                    .sortedWith(
                        compareBy<CourseEntity> { courseStartTime(it, state.periods) ?: LocalTime.MAX }
                            .thenBy { it.name }
                    )
            }
            val listState = rememberLazyListState()
            val contentUnderTopBar by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            val agentCollapsed by remember { mutableStateOf(false) }
            LaunchedEffect(contentUnderTopBar) {
                if (page == pagerState.settledPage) {
                    onContentUnderTopBarChange(contentUnderTopBar)
                }
            }
            val isToday = targetDate == LocalDate.now()
            val currentPeriod = if (isToday && targetWeekOrNull != null) {
                val now = LocalTime.now()
                currentTimelinePeriod(state.periods, now)
            } else null
            val headerContent: @Composable () -> Unit = {
                currentPeriod?.let { period ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassSurface(
                            backdrop = backdrop,
                            config = state.config,
                            shape = RoundedRectangle(50.dp),
                            tokens = GlassTokens.pill().copy(
                                blur = 4.dp,
                                surfaceAlpha = 0.72f,
                                highlightAlpha = 0.10f,
                                innerShadowAlpha = 0.10f
                            ),
                            baseSurfaceColorOverride = ComposeColor(0xFF0A84FF),
                            modifier = Modifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 80.dp)
                                    .heightIn(min = 34.dp)
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "第${period.periodIndex}节",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ComposeColor.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            val agentContent: @Composable () -> Unit = {
                if (isToday && abs(page - pagerState.currentPage) <= 1) {
                    TodayAgentHost(
                        state = agentState,
                        date = targetDate,
                        backdrop = dayAgentBackdrop,
                        textColor = textColor,
                        collapsed = agentCollapsed,
                        isActive = page == pagerState.settledPage,
                        backgroundMotionState = dayAgentBackgroundMotionState,
                        onPrepareOpen = onAgentPrepareOpen,
                        onAgentDismissed = onAgentDismissed,
                        onAgentAction = onAgentAction
                    )
                }
            }
            val groupedDayCourses = remember(dayCourses, state.config) {
                PeriodDayPart.entries.mapNotNull { part ->
                    dayCourses.filter { course -> courseDayPart(state.config, course) == part }
                        .takeIf { it.isNotEmpty() }
                        ?.let { part to it }
                }
            }
            val courseList: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (dayCourses.isEmpty()) item { HomeReadableText("这一天没有课程", color = textColor) }
                groupedDayCourses.forEach { (part, coursesInPart) ->
                    item(key = "day-part-${part.name}") {
                        DayPartHeader(
                            part = part,
                            courses = coursesInPart,
                            periods = state.periods,
                            textColor = textColor
                        )
                    }
                    itemsIndexed(coursesInPart, key = { _, it -> it.id }) { _, course ->
                        val coursePeriods = course.periods.toSet()
                        val simultaneousCount = dayCourses.count { other ->
                            other.id == course.id || other.periods.any(coursePeriods::contains)
                        }
                        DayTimelineCourse(
                            course,
                            targetWeek,
                            state.periods,
                            cardColor,
                            backdrop,
                            state.config,
                            onCourseClick,
                            entranceIndex = dayCourses.indexOf(course).coerceAtLeast(0),
                            simultaneousCount = simultaneousCount,
                            tabletFontScale = if (adaptiveMetrics.isTabletLandscape) 1.10f else 1f
                        )
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
                            .padding(
                                top = adaptiveMetrics.dayContentTopPadding,
                                bottom = DayDockScrollPadding
                            )
                            .verticalScroll(rememberScrollState()),
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
                            bottom = DayDockScrollPadding
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
                            bottom = DayDockScrollPadding
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

internal fun courseDayPart(
    config: ScheduleConfigEntity,
    course: CourseEntity
): PeriodDayPart? {
    val firstPeriod = course.periods.minOrNull() ?: return null
    return PeriodDayPart.entries.firstOrNull { firstPeriod in config.periodRange(it) }
}

private fun dayPartLabel(part: PeriodDayPart): String = when (part) {
    PeriodDayPart.MORNING -> "上午"
    PeriodDayPart.NOON -> "中午"
    PeriodDayPart.AFTERNOON -> "下午"
    PeriodDayPart.EVENING -> "晚上"
}

@Composable
private fun DayPartHeader(
    part: PeriodDayPart,
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    textColor: ComposeColor
) {
    val timeRange = remember(courses, periods) {
        val start = courses.mapNotNull { courseStartTime(it, periods) }.minOrNull()
        val end = courses.mapNotNull { courseEndTime(it, periods) }.maxOrNull()
        if (start != null && end != null) "$start–$end" else null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeReadableText(
            text = dayPartLabel(part),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(textColor.copy(alpha = 0.18f))
        )
        timeRange?.let {
            HomeReadableText(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.66f)
            )
        }
    }
}

@Composable
fun DayTimelineCourse(course: CourseEntity, currentWeek: Int, periods: List<PeriodEntity>, cardColor: ComposeColor, backdrop: Backdrop?, config: ScheduleConfigEntity, onCourseClick: (CourseEntity, Int, Rect?) -> Unit, entranceIndex: Int = 0, simultaneousCount: Int = 1, tabletFontScale: Float = 1f) {
    val resolvedCardColor = courseCardBaseColor(config, course)
    val timePillColor = deepenColor(resolvedCardColor, 0.16f)
    val glassContentColor = LocalAdaptiveGlass.current.contentColor
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassSurface(
            backdrop = backdrop,
            config = config,
            modifier = Modifier.wrapContentWidth(),
            shape = RoundedCornerShape(50),
            tokens = GlassTokens.pill(intensity = 0.75f),
            baseSurfaceColorOverride = if (glassUsesLightStyle(config)) HomeLightGlassSurfaceColor else null
        ) {
            Box(Modifier.background(timePillColor.copy(alpha = 0.26f))) {
                Text(
                    buildString {
                        append(courseTimeLabel(course, periods))
                        if (simultaneousCount > 1) append(" · 同时${simultaneousCount}门")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop != null) {
                        glassContentColor
                    } else {
                        readableOn(timePillColor)
                    }
                )
            }
        }
        CourseCard(course, periods, showTime = false, showWeeks = false, cardColor = cardColor, backdrop = backdrop, config = config, onClick = { sourceBounds -> onCourseClick(course, currentWeek, sourceBounds) }, entranceIndex = entranceIndex, tabletFontScale = tabletFontScale)
    }
}

@Composable
internal fun DayCourseCardTextContent(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    showTime: Boolean,
    showWeeks: Boolean,
    textColor: ComposeColor,
    tabletFontScale: Float
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val safeTabletScale = tabletFontScale.coerceAtLeast(1f)
        val titleStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize * safeTabletScale,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * safeTabletScale
        )
        val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = MaterialTheme.typography.bodyMedium.fontSize * safeTabletScale,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * safeTabletScale
        )
        Text(course.name, style = titleStyle, color = textColor)
        if (showTime) {
            Text(
                courseHomeTimeDetail(course, periods),
                style = bodyStyle,
                color = textColor.copy(alpha = 0.86f)
            )
        }
        if (!course.location.isNullOrBlank()) {
            Text("地点：" + course.location, style = bodyStyle, color = textColor.copy(alpha = 0.86f))
        }
        if (!course.teacher.isNullOrBlank()) {
            Text("教师：" + course.teacher, style = bodyStyle, color = textColor.copy(alpha = 0.86f))
        }
        if (showWeeks) {
            Text(
                "周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity),
                style = bodyStyle,
                color = textColor.copy(alpha = 0.86f)
            )
        }
        if (!course.note.isNullOrBlank()) {
            Text("备注：" + course.note, style = bodyStyle, color = textColor.copy(alpha = 0.86f))
        }
    }
}

@Composable
fun CourseCard(course: CourseEntity, periods: List<PeriodEntity>, showTime: Boolean = true, showWeeks: Boolean = true, cardColor: ComposeColor = MaterialTheme.colorScheme.surfaceVariant, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), onClick: ((Rect?) -> Unit)? = null, entranceIndex: Int? = null, enableSharedTransition: Boolean = true, tabletFontScale: Float = 1f) {
    val resolvedCardColor = if (config.cardColorArgb == MulticolorCourseCardArgb) courseCardBaseColor(config, course) else cardColor
    val textColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else if (config.courseCardGlassEnabled) readableOn(resolvedCardColor)
        else glassForegroundColor(config)
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
        DayCourseCardTextContent(
            course = course,
            periods = periods,
            showTime = showTime,
            showWeeks = showWeeks,
            textColor = textColor,
            tabletFontScale = tabletFontScale
        )
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
            Text(courseHomeTimeDetail(course, periods), color = textColor.copy(alpha = 0.86f))
            if (!course.location.isNullOrBlank()) Text("地点：" + course.location, color = textColor.copy(alpha = 0.86f))
            if (!course.teacher.isNullOrBlank()) Text("教师：" + course.teacher, color = textColor.copy(alpha = 0.86f))
            Text("周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity), color = textColor.copy(alpha = 0.86f))
            if (!course.note.isNullOrBlank()) Text("备注：" + course.note, color = textColor.copy(alpha = 0.86f))
        }
    }
}

private fun courseHomeTimeDetail(course: CourseEntity, periods: List<PeriodEntity>): String =
    if (course.hasCustomTime()) {
        courseTimeLabel(course, periods)
    } else {
        courseTimeLabel(course, periods) + " · 第 " + course.periods.joinToString(",") + " 节"
    }
