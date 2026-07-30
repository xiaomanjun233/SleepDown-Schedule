package com.example.courseschedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.os.Build
import android.view.WindowManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.State
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
import androidx.compose.runtime.saveable.rememberSaveable

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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
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

private fun performButtonHaptic(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

sealed interface Screen {
    data object Home : Screen
    data object Config : Screen
}

enum class HomeMode { Day, Week }
enum class SettingsSection { Schedule, Notifications }
enum class SettingsPage { Root, General, Widgets, AiImport, DayAgent, Schedule, Notifications, ScheduleManager, About, Changelog, Download, Donate }

private fun agentSettingsPage(value: String?): SettingsPage? = when (value) {
    "GENERAL" -> SettingsPage.General
    "AI_IMPORT" -> SettingsPage.AiImport
    "DAY_AGENT" -> SettingsPage.DayAgent
    "SCHEDULE" -> SettingsPage.Schedule
    "NOTIFICATIONS" -> SettingsPage.Notifications
    "SCHEDULE_MANAGER" -> SettingsPage.ScheduleManager
    "ABOUT" -> SettingsPage.About
    "CHANGELOG" -> SettingsPage.Changelog
    "DOWNLOAD" -> SettingsPage.Download
    "DONATE" -> SettingsPage.Donate
    else -> null
}

private const val SettingsDetailPageExtra = "settings_page"
private const val EduAdapterExtra = "edu_adapter"

private fun SettingsPage.title(): String = when (this) {
    SettingsPage.Root -> "设置"
    SettingsPage.General -> "通用设置"
    SettingsPage.Widgets -> "小组件设置"
    SettingsPage.AiImport -> "AI 设置"
    SettingsPage.DayAgent -> "今日助手"
    SettingsPage.Schedule -> "课表详细设置"
    SettingsPage.Notifications -> "通知设置"
    SettingsPage.ScheduleManager -> "课表设置"
    SettingsPage.About -> "关于"
    SettingsPage.Changelog -> "更新日志"
    SettingsPage.Download -> "下载新版"
    SettingsPage.Donate -> "捐赠支持"
}

internal val DayDockScrollPadding = 104.dp
internal val WeekDockScrollPadding = 132.dp
internal val DockScrollPadding = 132.dp
internal val HomeHeaderGlassBlur = 2.dp
internal val HomeHeaderGlassLensHeight = 12.dp
internal val HomeHeaderGlassLensAmount = 24.dp
internal val HomeLightGlassSurfaceColor = ComposeColor(0xFFF2F4F8)
internal val HomeLightGlassGradientColor = ComposeColor(0xFFF7F8FB)
internal val HomeLightGlassAccentColor = ComposeColor(0xFF0A84FF)
internal val HomeLightGlassSelectedAccentColor = ComposeColor(0xFF006FD6)
internal const val HomeLightGlassChromeTintAlpha = 0.54f
internal const val HomeLightGlassAccentTintAlpha = 0.24f
internal const val HomeLightGlassPanelTintAlpha = 0.15f
internal const val HomeLightGlassMenuTintAlpha = 0.14f
internal fun homeChromeGlassSurfaceAlpha(lightGlass: Boolean): Float =
    if (lightGlass) HomeLightGlassChromeTintAlpha else 0.45f
internal fun dockImeCompensationPx(imeBottomPx: Int, systemBottomPx: Int): Int =
    (imeBottomPx - systemBottomPx).coerceAtLeast(0)
internal const val HomeHeaderGlassHighlightAlpha = 0.09f
internal const val HomeHeaderGlassShadowAlpha = 0.05f
internal const val HomeHeaderGlassOuterShadowAlpha = 0.018f
internal const val HomeHeaderGlassInnerShadowAlpha = 0.08f
internal fun homeHeaderGlassTokens(lightGlass: Boolean): GlassTokens =
    GlassTokens.pill(intensity = 0.95f).copy(surfaceAlpha = homeChromeGlassSurfaceAlpha(lightGlass))

sealed interface HomeDialog {
    data object ImportSchedule : HomeDialog
    data object EduImport : HomeDialog
    data class ConfirmImport(val draft: ImportDraft, val returnDialog: HomeDialog? = ImportSchedule) : HomeDialog
    data class EditWallpaper(val uri: Uri, val entrySnapshot: Bitmap?) : HomeDialog
    data object SampleWallpaperColor : HomeDialog
    data class EditCourse(val course: CourseEntity?, val targetWeek: Int? = null) : HomeDialog
    data class ApplyCourseEdit(val original: CourseEntity, val edited: CourseEntity, val targetWeek: Int) : HomeDialog
    data class ConfirmCourseConflicts(
        val original: CourseEntity,
        val edited: CourseEntity,
        val targetWeek: Int,
        val conflictWeeks: List<Int>,
        val singleWeekOnly: Boolean = false
    ) : HomeDialog
    data class ApplyCourseDelete(val course: CourseEntity, val targetWeek: Int) : HomeDialog
}

private var splashEntranceDone = false
internal val LocalEditingCourseId = compositionLocalOf<Long?> { null }
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
@Volatile
internal var hideFromRecentsEnabled = false

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CourseScheduleAppUi(
    viewModel: ScheduleViewModel,
    externalIcsUri: Uri? = null,
    onExternalIcsConsumed: (Uri) -> Unit = {},
    onStartupContentReady: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allSchedulesState by viewModel.allSchedulesState.collectAsStateWithLifecycle()
    val message by viewModel.snackbar.collectAsStateWithLifecycle()
    val pickerState = rememberSchedulePickerState()
    var previewScheduleId by remember { mutableStateOf<Int?>(null) }
    var pendingPickerEditorScheduleId by remember { mutableStateOf<Int?>(null) }
    var quickScheduleDraft by remember { mutableStateOf<QuickScheduleDraft?>(null) }
    val dayAgentBackgroundMotionState = rememberDayAgentBackgroundMotionState()
    var dayAgentPagerSettled by remember { mutableStateOf(false) }
    var detailMorphState by remember { mutableStateOf<DetailMorphState>(DetailMorphState.Idle) }
    var detailMorphRequest by remember { mutableStateOf<DetailMorphRequest?>(null) }
    var detailCaptureCoverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailCaptureRecordCleanFrame by remember { mutableStateOf(false) }
    val detailCaptureMaskActive = remember { AtomicBoolean(false) }
    val baseVisualState = previewScheduleId?.let(allSchedulesState::forSchedule) ?: state
    var personalizationPreviewConfig by remember { mutableStateOf<ScheduleConfigEntity?>(null) }
    var personalizationPendingCommitConfig by remember { mutableStateOf<ScheduleConfigEntity?>(null) }
    var personalizationSliderPreviewKey by remember { mutableStateOf<String?>(null) }
    val personalizationPreviewProgress by animateFloatAsState(
        targetValue = if (personalizationSliderPreviewKey != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.22f, 0f, 0.2f, 1f)),
        label = "personalization-preview-transition"
    )
    LaunchedEffect(baseVisualState.config, personalizationPendingCommitConfig) {
        val pending = personalizationPendingCommitConfig
        if (pending != null && baseVisualState.config == pending) {
            if (personalizationPreviewConfig == pending) {
                personalizationPreviewConfig = null
            }
            personalizationPendingCommitConfig = null
        }
    }
    val visualState = personalizationPreviewConfig
        ?.takeIf { it.id == baseVisualState.config.id }
        ?.let { baseVisualState.copy(config = it) }
        ?: baseVisualState
    val screenGraphicsLayer = rememberGraphicsLayer()
    val detailScreenGraphicsLayer = rememberGraphicsLayer()
    val recordedScheduleId = remember { AtomicInteger(-1) }
    val recordedHomeGeneration = remember { AtomicLong(0L) }
    var captureRenderToken by remember { mutableIntStateOf(0) }
    var snapshotGeneration by remember { mutableIntStateOf(0) }
    var snapshotJob by remember { mutableStateOf<Job?>(null) }
    var cacheHydrationJob by remember { mutableStateOf<Job?>(null) }
    var entryPrewarmJob by remember { mutableStateOf<Job?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var homeMode by remember { mutableStateOf(if (state.config.defaultHomeMode == HomeStartMode.DAY) HomeMode.Day else HomeMode.Week) }
    LaunchedEffect(state.config.defaultHomeMode) {
        homeMode = if (state.config.defaultHomeMode == HomeStartMode.DAY) HomeMode.Day else HomeMode.Week
    }
    var homeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    var renderedHomeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    var homeDialogVisible by remember { mutableStateOf(false) }
    var courseEditorRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var courseEditorRenderedCourseId by remember { mutableStateOf<Long?>(null) }
    val courseEditorMotionState = rememberCourseEditorMotionState()
    val courseEditorOverlayPhase = courseEditorMotionState.phase
    fun openCourseEditor(course: CourseEntity, targetWeek: Int?, sourceBounds: Rect?) {
        if (courseEditorRequest != null) return
        courseEditorRequest = CourseEditorOverlayRequest(
            course = course,
            targetWeek = targetWeek,
            sourceBoundsInRoot = sourceBounds,
            sourceIsDayCard = homeMode == HomeMode.Day && sourceBounds != null
        )
    }
    fun closeCourseEditor() {
        courseEditorRequest = null
    }
    fun dismissHomeDialog() {
        homeDialogVisible = false
        homeDialog = null
    }
    LaunchedEffect(homeDialog) {
        if (homeDialog != null) {
            renderedHomeDialog = homeDialog
            homeDialogVisible = true
        } else if (renderedHomeDialog != null) {
            homeDialogVisible = false
            delay(320)
            renderedHomeDialog = null
        }
    }
    val homeAnchoredMorphState = rememberHomeAnchoredMorphState()
    val homeMenuDestinationMotionState = rememberHomeMenuDestinationMotionState()
    var homeMenuDestinationRequest by remember { mutableStateOf<HomeMenuDestinationRequest?>(null) }
    var homeMenuSourceHidden by remember { mutableStateOf(false) }
    var homeAddMenuBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var homeAnchoredOverlayRequest by remember { mutableStateOf<HomeAnchoredOverlayRequest?>(null) }
    var addButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var personalizeButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingHomeAnchoredOverlay by remember { mutableStateOf<HomeAnchoredOverlayKind?>(null) }
    var showScheduleEntryPill by remember { mutableStateOf(false) }
    val editingCourseId: Long? = courseEditorRequest?.course?.id ?: courseEditorRenderedCourseId
    val activeHomeAnchoredOverlay =
        homeAnchoredOverlayRequest?.kind ?: homeAnchoredMorphState.renderedKind

    fun openHomeAnchoredOverlay(kind: HomeAnchoredOverlayKind) {
        if (homeAnchoredOverlayRequest != null ||
            homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle
        ) return
        val bounds = when (kind) {
            HomeAnchoredOverlayKind.Add -> addButtonBounds
            HomeAnchoredOverlayKind.Personalize -> personalizeButtonBounds
        }
        if (bounds == null || bounds.width <= 2f || bounds.height <= 2f) {
            pendingHomeAnchoredOverlay = kind
            return
        }
        pendingHomeAnchoredOverlay = null
        homeAnchoredOverlayRequest = HomeAnchoredOverlayRequest(kind, bounds)
    }

    fun toggleHomeAnchoredOverlay(kind: HomeAnchoredOverlayKind) {
        if (homeAnchoredOverlayRequest?.kind == kind) {
            homeAnchoredOverlayRequest = null
        } else {
            openHomeAnchoredOverlay(kind)
        }
    }

    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val homeAdaptiveMetrics = rememberHomeAdaptiveMetrics()

    LaunchedEffect(
        pendingHomeAnchoredOverlay,
        addButtonBounds,
        personalizeButtonBounds,
        homeAnchoredMorphState.phase,
        screen
    ) {
        val pending = pendingHomeAnchoredOverlay ?: return@LaunchedEffect
        if (screen !is Screen.Home || homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle) {
            return@LaunchedEffect
        }
        openHomeAnchoredOverlay(pending)
    }
    LaunchedEffect(screen) {
        if (screen !is Screen.Home) {
            pendingHomeAnchoredOverlay = null
            homeAnchoredOverlayRequest = null
            personalizationSliderPreviewKey = null
            personalizationPreviewConfig = null
        }
    }
    LaunchedEffect(homeAnchoredOverlayRequest) {
        if (homeAnchoredOverlayRequest?.kind != HomeAnchoredOverlayKind.Personalize) {
            personalizationSliderPreviewKey = null
            personalizationPreviewConfig = null
        }
    }
    var homeContentUnderTopBar by remember { mutableStateOf(false) }
    val adaptiveWeekCardHeight = if (visualState.periods.size >= 10) 72f else 80f
    var weekCardHeight by remember(visualState.periods.size, visualState.config.weekCardHeightDp) {
        mutableFloatStateOf((visualState.config.weekCardHeightDp ?: adaptiveWeekCardHeight).coerceIn(38f, 80f))
    }
    val context = LocalContext.current
    var pendingImportedSetupId by remember(context) {
        mutableStateOf(PendingImportSetupStore.consume(context))
    }
    val appScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(externalIcsUri, state.loaded) {
        val uri = externalIcsUri ?: return@LaunchedEffect
        if (!state.loaded) return@LaunchedEffect
        onExternalIcsConsumed(uri)
        screen = Screen.Home
        pendingHomeAnchoredOverlay = null
        homeAnchoredOverlayRequest = null
        loadAiImportFile(context, uri)
            .onSuccess { file ->
                if (!file.isIcs) {
                    Toast.makeText(context, "分享的文件不是有效的 ICS 日历", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                IcsScheduleCodec.parse(file.bytes, state.config)
                    .onSuccess { draft ->
                        homeDialog = HomeDialog.ConfirmImport(draft, returnDialog = null)
                    }
                    .onFailure { error ->
                        Toast.makeText(context, error.message ?: "ICS 解析失败", Toast.LENGTH_SHORT).show()
                    }
            }
            .onFailure { error ->
                Toast.makeText(context, error.message ?: "ICS 文件读取失败", Toast.LENGTH_SHORT).show()
            }
    }
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val pickerSceneBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    var homeReadabilityRootSize by remember { mutableStateOf(IntSize.Zero) }
    var homeRootPositionOnScreen by remember { mutableStateOf(Offset.Zero) }
    fun openHomeMenuDestination(kind: HomeMenuDestinationKind) {
        val sourceButton = addButtonBounds ?: return
        if (homeReadabilityRootSize.width <= 0 || homeReadabilityRootSize.height <= 0) return
        val menuBounds = homeAddMenuBoundsInRoot ?: homeAddMenuTargetRect(
            source = sourceButton,
            rootSize = homeReadabilityRootSize,
            density = density.density,
            actionCount = 3,
            adaptiveMetrics = homeAdaptiveMetrics
        )
        homeMenuDestinationRequest = HomeMenuDestinationRequest(
            kind = kind,
            sourceBoundsInRoot = menuBounds,
            collapseBoundsInRoot = sourceButton
        )
    }
    val currentVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    var automaticUpdateDialog by remember { mutableStateOf<SettingsUpdateDialog?>(null) }
    var automaticDownloadedUpdate by remember { mutableStateOf<java.io.File?>(null) }
    val automaticInstallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = automaticDownloadedUpdate
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                .onFailure { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
        }
    }
    fun downloadAutomaticUpdate(release: GiteeReleaseInfo) {
        if (release.apkUrl == null) {
            automaticUpdateDialog = SettingsUpdateDialog.NoApk(release)
            return
        }
        automaticUpdateDialog = SettingsUpdateDialog.Downloading(release)
        appScope.launch {
            GiteeAppUpdater.downloadApk(context, release).fold(
                onSuccess = { apk ->
                    automaticDownloadedUpdate = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                            .onSuccess { automaticUpdateDialog = null }
                            .onFailure { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    } else {
                        automaticUpdateDialog = SettingsUpdateDialog.InstallPermissionRequired
                    }
                },
                onFailure = { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }
    LaunchedEffect(state.loaded, state.config.autoCheckUpdates, currentVersionName) {
        if (!state.loaded) return@LaunchedEffect
        GiteeAppUpdater.restoreCachedStatus(context, currentVersionName)
        if (!state.config.autoCheckUpdates || !GiteeAppUpdater.shouldRunDailyCheck(context)) return@LaunchedEffect
        GiteeAppUpdater.markDailyCheckStarted(context)
        GiteeAppUpdater.checkForUpdate(currentVersionName).onSuccess { result ->
            GiteeAppUpdater.recordCheckResult(context, result)
            if (result is GiteeUpdateCheckResult.UpdateAvailable) {
                automaticUpdateDialog = SettingsUpdateDialog.Available(result.release)
            }
        }
    }
    val wallpaperImages by rememberHomeWallpaperImages(visualState.config)
    val expectedWallpaperRenderKey = homeWallpaperRenderKey(
        visualState.config,
        appUsesDarkTheme(visualState.config)
    )
    val wallpaperLoadFinished = !visualState.config.hasAnyWallpaper() ||
        wallpaperImages.renderKey == expectedWallpaperRenderKey
    val latestOnStartupContentReady = rememberUpdatedState(onStartupContentReady)
    var startupContentReported by remember { mutableStateOf(false) }
    val latestVisualState = rememberUpdatedState(visualState)
    val latestWallpaperImages = rememberUpdatedState(wallpaperImages)
    val latestAllSchedulesState = rememberUpdatedState(allSchedulesState)
    val startupAnimationsEnabled = remember(context) { animationsEnabled(context) }

    var startupPhase by remember {
        mutableStateOf(if (splashEntranceDone || !startupAnimationsEnabled) StartupPhase.FullQuality else StartupPhase.Loading)
    }
    LaunchedEffect(state.loaded, wallpaperLoadFinished, startupAnimationsEnabled) {
        if (!state.loaded || !wallpaperLoadFinished) return@LaunchedEffect
        if (startupPhase == StartupPhase.Loading && !startupAnimationsEnabled) {
            splashEntranceDone = true
            startupPhase = StartupPhase.FullQuality
        }
        // Let the ready bitmap participate in a complete Compose frame before
        // releasing MainActivity's first-draw gate.
        withFrameNanos { }
        if (!startupContentReported) {
            startupContentReported = true
            latestOnStartupContentReady.value()
        }
        // Show the already-rendered wallpaper for a brief beat, then let the
        // home elements fly in. This keeps the entrance readable without
        // bringing back the old loading mask or circular reveal.
        if (startupPhase == StartupPhase.Loading && startupAnimationsEnabled) {
            delay(140)
            startupPhase = StartupPhase.Entrance
        }
    }
    LaunchedEffect(startupPhase, startupAnimationsEnabled) {
        if (!startupAnimationsEnabled) {
            splashEntranceDone = true
            startupPhase = StartupPhase.FullQuality
            return@LaunchedEffect
        }
        if (startupPhase == StartupPhase.Entrance) {
            splashEntranceDone = false
            delay(820)
            startupPhase = StartupPhase.Settle
            delay(180)
            splashEntranceDone = true
            startupPhase = StartupPhase.FullQuality
        }
    }
    val glassQuality = animatedGlassQuality(startupPhase)
    val adaptiveGlassState = rememberFallbackAdaptiveGlassState(visualState.config)
    val startupAnimation = when {
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Preparing -> "CourseEditorPrepare"
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Opening -> "CourseEditorOpen"
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Closing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Disposing -> "CourseEditorClose"
        startupPhase == StartupPhase.Reveal -> "StartupReveal"
        startupPhase == StartupPhase.Entrance -> "HomeFlyInEntrance"
        startupPhase == StartupPhase.Settle -> "HomeFlyInEntrance"
        homeDialogVisible -> "DialogOpen"
        else -> "Idle"
    }
    val startupEntranceSpec = rememberStartupEntranceSpec(
        phase = startupPhase,
        courseCount = state.courses.size,
        animationsEnabled = startupAnimationsEnabled
    )
    StartupPerformanceBoost(
        startupPhase == StartupPhase.Reveal ||
            startupPhase == StartupPhase.Entrance ||
            startupPhase == StartupPhase.Settle ||
            homeDialogVisible
    )
    PerformanceAnimationState(startupAnimation, startupAnimation != "Idle")
    StartupJankStats(
        phase = startupPhase,
        screen = if (screen is Screen.Home) "Home" else if (screen is Screen.Config) "Settings" else "Other",
        animation = startupAnimation
    )
    val reduceWallpaperQualityForCourseEditor =
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Preparing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Opening ||
            (courseEditorRequest != null && courseEditorOverlayPhase == CourseEditorOverlayPhase.Open)
    val courseEditorBackdropBase =
        if (
            reduceWallpaperQualityForCourseEditor ||
            (courseEditorRequest != null && courseEditorOverlayPhase != CourseEditorOverlayPhase.Open)
        ) {
            backgroundBackdrop
        } else {
            chromeBackdrop
        }
    val courseEditorBackdrop = rememberScreenScaledBackdrop(
        backdrop = courseEditorBackdropBase,
        scale = { courseEditorMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val homeAnchoredOverlayBackdrop = rememberScreenScaledBackdrop(
        backdrop = chromeBackdrop,
        scale = {
            val zoom = homeAnchoredMorphState.backgroundZoom.value
            1f + (zoom - 1f) * (1f - personalizationPreviewProgress)
        },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    // Android Dialog content owns a separate Compose root/window. Wrap each recorded layer in
    // screen coordinates before combining them, otherwise LayerBackdrop mixes the dialog and
    // activity window origins and samples with a decor/status-bar offset on ColorOS.
    val homeDialogBackgroundBackdrop = rememberScreenScaledBackdrop(
        backdrop = backgroundBackdrop,
        scale = { 1f },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    ) ?: backgroundBackdrop
    val homeDialogContentBackdrop = rememberScreenScaledBackdrop(
        backdrop = contentBackdrop,
        scale = { 1f },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    ) ?: contentBackdrop
    val homeDialogBackdrop = rememberCombinedBackdrop(
        homeDialogBackgroundBackdrop,
        homeDialogContentBackdrop
    )
    val homeMenuDestinationBackdrop = rememberScreenScaledBackdrop(
        backdrop = chromeBackdrop,
        scale = { homeMenuDestinationMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val eduMenuDestinationActive =
        homeMenuDestinationRequest?.kind == HomeMenuDestinationKind.EduImport ||
            (homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle &&
                homeMenuDestinationMotionState.backgroundZoom.value > 1.0001f)
    val homeOverlayBackgroundZoom: () -> Float = {
        if (personalizationPreviewProgress > 0.001f) {
            val zoom = homeAnchoredMorphState.backgroundZoom.value
            1f + (zoom - 1f) * (1f - personalizationPreviewProgress)
        } else if (eduMenuDestinationActive) {
            homeMenuDestinationMotionState.backgroundZoom.value
        } else {
            maxOf(
                courseEditorMotionState.backgroundZoom.value,
                homeAnchoredMorphState.backgroundZoom.value,
                homeMenuDestinationMotionState.backgroundZoom.value
            )
        }
    }
    val dayAgentBackdrop = rememberScreenScaledBackdrop(
        backdrop = backgroundBackdrop,
        scale = { dayAgentBackgroundMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val systemDark = isSystemInDarkTheme()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            appScope.launch {
                val entrySnapshot = runCatching {
                    withFrameNanos { }
                    screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                homeDialog = HomeDialog.EditWallpaper(uri, entrySnapshot)
            }
        }
    }
    val todayDate = LocalDate.now()
    val homeCurrentWeek = effectiveCurrentWeek(visualState.config)
    val beforeScheduleTerm = isBeforeScheduleTerm(visualState.config, todayDate)
    val afterScheduleTerm = isAfterScheduleTerm(visualState.config, todayDate)
    var homeDisplayWeek by remember(visualState.config.id) { mutableIntStateOf(1) }
    var pendingConflictCourseId by remember(visualState.config.id) { mutableStateOf<Long?>(null) }
    var pendingConflictCourseKey by remember(visualState.config.id) { mutableStateOf<String?>(null) }
    var pendingConflictWeeks by remember(visualState.config.id) { mutableStateOf<List<Int>>(emptyList()) }
    var homeWeekInitialized by remember(visualState.config.id) { mutableStateOf(false) }
    var homeDisplayDate by remember(visualState.config.id) { mutableStateOf(todayDate) }
    LaunchedEffect(
        state.loaded,
        visualState.config.id,
        pickerState.overlayVisible,
        homeMode,
        homeDisplayDate
    ) {
        // The Agent background is transformed live now, so there is no frozen snapshot to
        // invalidate when leaving the day page or opening the picker.
    }
    LaunchedEffect(visualState.loaded, visualState.config.id, visualState.config.totalWeeks, homeCurrentWeek, visualState.config.autoCurrentWeek, beforeScheduleTerm) {
        if (!visualState.loaded) return@LaunchedEffect
        val currentTargetWeek = if (beforeScheduleTerm) 1 else homeCurrentWeek
        if (!homeWeekInitialized) {
            homeDisplayWeek = currentTargetWeek
            homeWeekInitialized = true
            return@LaunchedEffect
        }
        homeDisplayWeek = homeDisplayWeek.coerceIn(1, visualState.config.totalWeeks.coerceAtLeast(1))
        if (visualState.config.autoCurrentWeek) homeDisplayWeek = currentTargetWeek
    }
    val homeTitleWeek = if (homeMode == HomeMode.Day) {
        effectiveCurrentWeek(visualState.config, homeDisplayDate)
    } else {
        homeDisplayWeek
    }
    val homeTitleDate = if (homeMode == HomeMode.Day) {
        homeDisplayDate
    } else {
        todayDate
    }
    val homeReturnTargetWeek = if (beforeScheduleTerm) 1 else homeCurrentWeek
    val homeShowingAnotherWeek = homeMode == HomeMode.Week && homeDisplayWeek != homeReturnTargetWeek
    val homeCourseColorSignature = visualState.courses
        .map(::courseCardColorKey)
        .distinct()
        .sorted()
    val homeCourseColorAssignments = remember(
        visualState.config.id,
        homeCourseColorSignature,
        wallpaperImages.representativeColors
    ) {
        // Card movement and resizing must not recolor the whole grid.
        // The semantic color-key set changes only when the actual course identities do.
        buildCourseCardColorAssignments(
            visualState.courses,
            wallpaperImages.representativeColors
        )
    }

    suspend fun awaitRenderedSchedule(scheduleId: Int): Boolean {
        val generationBeforeSwitch = recordedHomeGeneration.get()
        Log.d("SchedulePicker", "await start requested=$scheduleId recorded=${recordedScheduleId.get()} generation=$generationBeforeSwitch")
        previewScheduleId = scheduleId
        captureRenderToken += 1
        snapshotFlow { latestVisualState.value.config.id }.first { it == scheduleId }
        Log.d("SchedulePicker", "visual state ready requested=$scheduleId token=$captureRenderToken")
        val target = latestAllSchedulesState.value.allConfigs.firstOrNull { it.id == scheduleId }
            ?: defaultConfig(scheduleId)
        if (target.hasAnyWallpaper()) {
            // The wallpaper loader currently exposes no terminal error state. Bound this wait
            // so an unreadable URI degrades to the same loading/fallback layer as the real home.
            withTimeoutOrNull(2_500L) {
                snapshotFlow {
                    latestVisualState.value.config.id to (latestWallpaperImages.value.source != null)
                }.first { (renderedId, wallpaperReady) -> renderedId == scheduleId && wallpaperReady }
            }
        }
        val ready = withTimeoutOrNull(900L) {
            while (
                recordedScheduleId.get() != scheduleId ||
                recordedHomeGeneration.get() == generationBeforeSwitch
            ) {
                withFrameNanos { }
            }
            // The marker is written after graphicsLayer.record completes. Cross one more
            // frame boundary so capture never races the frame that produced the marker.
            withFrameNanos { }
            true
        } ?: false
        Log.d(
            "SchedulePicker",
            "await end requested=$scheduleId ready=$ready recorded=${recordedScheduleId.get()} generation=${recordedHomeGeneration.get()}"
        )
        return ready
    }

    suspend fun captureRenderedSchedule(scheduleId: Int): Bitmap? {
        if (!awaitRenderedSchedule(scheduleId)) return null
        val bitmap = runCatching { screenGraphicsLayer.toImageBitmap().asAndroidBitmap() }
            .onFailure { Log.e("SchedulePicker", "capture failed for schedule=$scheduleId", it) }
            .getOrNull()
        Log.d("SchedulePicker", "capture result requested=$scheduleId bitmap=${bitmap?.width}x${bitmap?.height}")
        if (bitmap != null) {
            appScope.launch { ScheduleSnapshotStore.save(context, scheduleId, bitmap) }
        }
        return bitmap
    }

    fun hydratePersistedSnapshots(generation: Int) {
        cacheHydrationJob?.cancel()
        cacheHydrationJob = appScope.launch {
            val selectedId = pickerState.selectedScheduleId ?: return@launch
            val selectedIndex = pickerState.orderIds.indexOf(selectedId)
            val neighborIds = listOf(selectedIndex - 1, selectedIndex + 1)
                .mapNotNull(pickerState.orderIds::getOrNull)
            val orderedTargets = (neighborIds + pickerState.orderIds)
                .distinct()
                .filter { it != selectedId && pickerState.snapshots[it] == null }
            for (targetId in orderedTargets) {
                if (generation != snapshotGeneration || !pickerState.overlayVisible) break
                ScheduleSnapshotStore.load(context, targetId)?.let { persisted ->
                    pickerState.snapshots[targetId] = persisted
                }
            }
        }
    }

    fun prewarmCurrentScheduleSnapshot() {
        entryPrewarmJob?.cancel()
        val requestedId = state.config.id
        entryPrewarmJob = appScope.launch {
            val snapshot = captureRenderedSchedule(requestedId) ?: return@launch
            if (
                pickerState.phase is CustomizeUiState.ShowingEntryButton &&
                state.config.id == requestedId
            ) {
                pickerState.currentSnapshot = snapshot
                pickerState.currentSnapshotScheduleId = requestedId
                pickerState.snapshots[requestedId] = snapshot
            }
        }
    }

    fun enterCustomizePage() {
        if (pickerState.phase !is CustomizeUiState.Home && pickerState.phase !is CustomizeUiState.ShowingEntryButton) return
        snapshotJob?.cancel()
        val generation = ++snapshotGeneration
        appScope.launch {
            showScheduleEntryPill = false
            pickerState.phase = CustomizeUiState.CapturingSnapshots
            val entryExitStartedAt = withFrameNanos { it }
            var frameTime: Long
            do {
                frameTime = withFrameNanos { it }
            } while (frameTime - entryExitStartedAt < 120_000_000L)
            val currentId = state.config.id
            pickerState.selectedScheduleId = currentId
            pickerState.originalScheduleId = currentId
            pickerState.orderIds.clear()
            pickerState.orderIds += currentId
            pickerState.orderIds += allSchedulesState.schedules.map { it.id }.filter { it != currentId }
            val prewarmed = pickerState.currentSnapshot
                ?.takeIf { pickerState.currentSnapshotScheduleId == currentId }
            val recordedNow = if (prewarmed == null && recordedScheduleId.get() == currentId) {
                runCatching { screenGraphicsLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
            } else null
            val currentSnapshot = prewarmed
                ?: recordedNow
                ?: ScheduleSnapshotStore.load(context, currentId)
                ?: ScheduleSnapshotStore.createEmptySchedulePlaceholder(
                    context,
                    windowContainerSize.width,
                    windowContainerSize.height,
                    if (state.config.followSystemDarkMode) systemDark else state.config.darkMode
                )
            pickerState.currentSnapshot = currentSnapshot
            pickerState.snapshots[currentId] = currentSnapshot
            pickerState.currentSnapshotScheduleId = currentId
            if (recordedNow != null) {
                appScope.launch { ScheduleSnapshotStore.save(context, currentId, recordedNow) }
            }
            pickerState.enterProgress.snapTo(0f)
            pickerState.chromeProgress.snapTo(0f)
            pickerState.pageSpacingProgress.snapTo(0f)
            pickerState.cornerProgress.snapTo(0f)
            pickerState.realHomeRevealProgress.snapTo(0f)
            pickerState.phase = CustomizeUiState.EnteringPicker
            withFrameNanos { }
            coroutineScope {
                launch { pickerState.enterProgress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch { pickerState.cornerProgress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch {
                    val titleDelayStart = withFrameNanos { it }
                    var titleFrameTime: Long
                    do {
                        titleFrameTime = withFrameNanos { it }
                    } while (titleFrameTime - titleDelayStart < 100_000_000L)
                    pickerState.chromeProgress.animateTo(1f, tween(350))
                }
                launch { pickerState.pageSpacingProgress.animateTo(1f, tween(500)) }
            }
            if (generation != snapshotGeneration) return@launch
            pickerState.phase = CustomizeUiState.Picker
            hydratePersistedSnapshots(generation)
        }
    }

    fun switchPickerSchedule(scheduleId: Int) {
        if (pickerState.phase !is CustomizeUiState.Picker || scheduleId == pickerState.selectedScheduleId) return
        snapshotJob?.cancel()
        val generation = ++snapshotGeneration
        pickerState.selectedScheduleId = scheduleId
        pickerState.snapshots[scheduleId]?.let {
            pickerState.currentSnapshot = it
            pickerState.currentSnapshotScheduleId = scheduleId
        }
        snapshotJob = appScope.launch {
            val requestedId = scheduleId
            val cached = pickerState.snapshots[requestedId]
                ?: ScheduleSnapshotStore.load(context, requestedId)
            if (cached != null && generation == snapshotGeneration && pickerState.selectedScheduleId == requestedId) {
                pickerState.snapshots[requestedId] = cached
                pickerState.currentSnapshot = cached
                pickerState.currentSnapshotScheduleId = requestedId
            }
            // Pager navigation is visual-only. Do not switch or recapture the hidden real
            // homepage here; doing so rebuilt the entire schedule on every settled swipe and
            // competed with the Pager animation for both main-thread and GPU time.
            if (generation == snapshotGeneration && pickerState.selectedScheduleId == requestedId) {
                hydratePersistedSnapshots(generation)
            }
        }
    }

    fun quickDraftFor(scheduleId: Int): QuickScheduleDraft {
        val config = latestAllSchedulesState.value.allConfigs.firstOrNull { it.id == scheduleId }
            ?: defaultConfig(scheduleId)
        val totalWeeks = config.totalWeeks.coerceIn(1, 60)
        val resolvedWeek = resolveScheduleCurrentWeek(
            config,
            totalWeeks,
            config.currentWeek,
            config.termStartDate,
            config.autoCurrentWeek
        )
        return QuickScheduleDraft(
            scheduleId = scheduleId,
            totalWeeks = totalWeeks,
            currentWeek = resolvedWeek,
            autoCurrentWeek = config.autoCurrentWeek,
            hideEmptyWeekends = config.hideEmptyWeekends,
            termStartDate = config.termStartDate.orEmpty()
        )
    }

    LaunchedEffect(pendingImportedSetupId) {
        val scheduleId = pendingImportedSetupId ?: return@LaunchedEffect
        screen = Screen.Home
        dismissHomeDialog()
        snapshotFlow {
            val latest = latestAllSchedulesState.value
            latest.schedules.any { it.id == scheduleId } &&
                latest.allConfigs.any { it.id == scheduleId } &&
                latestVisualState.value.config.id == scheduleId
        }.first { it }
        if (pickerState.overlayVisible) pickerState.reset()
        withFrameNanos { }
        enterCustomizePage()
        snapshotFlow {
            pickerState.phase is CustomizeUiState.Picker &&
                pickerState.selectedScheduleId == scheduleId
        }.first { it }
        quickScheduleDraft = quickDraftFor(scheduleId)
        pendingImportedSetupId = null
    }

    fun exitPicker(
        apply: Boolean,
        targetOverride: Int? = null,
        commitTarget: Boolean = apply,
        crossfadeToTarget: Boolean = !apply,
        onFinished: (() -> Unit)? = null
    ) {
        if (pickerState.interactionsLocked) return
        snapshotJob?.cancel()
        ++snapshotGeneration
        val targetId = targetOverride
            ?: if (apply) pickerState.selectedScheduleId ?: state.config.id else state.config.id
        appScope.launch {
            // Always start from exactly what the centered card is currently showing, even when
            // Cancel is returning to a different, previously-applied schedule.
            val centeredId = pickerState.selectedScheduleId
            val cachedCardSnapshot = centeredId?.let { id ->
                pickerState.currentSnapshot
                    ?.takeIf { pickerState.currentSnapshotScheduleId == id }
                    ?: pickerState.snapshots[id]
            }
            pickerState.phase = if (commitTarget) CustomizeUiState.Applying else CustomizeUiState.ExitingPicker
            pickerState.preparingExit = true
            val cancelTargetSnapshot = if (crossfadeToTarget) {
                pickerState.snapshots[targetId] ?: ScheduleSnapshotStore.load(context, targetId)
            } else {
                null
            }
            // Prepare the actual live destination, but do not turn it into the second visual
            // layer. The cached centered card will later fade away to reveal this real home.
            awaitRenderedSchedule(targetId)
            pickerState.currentSnapshot = cachedCardSnapshot ?: pickerState.currentSnapshot
            // Cancel visually dissolves the original/applied schedule's persisted card directly
            // over the currently centered card while it grows. Apply continues to enlarge the
            // selected card and hands it directly to the live destination.
            pickerState.transitionFromSnapshot = cancelTargetSnapshot
            pickerState.snapshotCoverBitmap = null
            if (commitTarget) {
                val activationFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
                viewModel.activateSchedule(targetId) { activationFinished.complete(Unit) }
                activationFinished.await()
                withTimeoutOrNull(1_200L) {
                    snapshotFlow { latestVisualState.value.config.id }.first { it == targetId }
                }
            }
            // Force the fully loaded destination through two additional draw/vsync boundaries.
            // The loading indicator remains visible throughout this preparation window.
            captureRenderToken += 1
            withFrameNanos { }
            withFrameNanos { }
            pickerState.preparingExit = false
            coroutineScope {
                launch { pickerState.enterProgress.animateTo(0f, tween(420, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch { pickerState.chromeProgress.animateTo(0f, tween(220)) }
                launch { pickerState.pageSpacingProgress.animateTo(0f, tween(320)) }
            }
            // Geometry is now fully screen-sized. Only now flatten the corner radius so no
            // square card edge can be exposed while the card is still floating over the home.
            pickerState.cornerProgress.animateTo(0f, tween(90))
            // The cached card is now fully screen-sized and square. Reveal the already-rendered
            // real home underneath instead of crossfading to another bitmap.
            pickerState.realHomeRevealProgress.animateTo(1f, tween(250))
            val handoffHoldStart = withFrameNanos { it }
            var handoffFrame: Long
            do {
                handoffFrame = withFrameNanos { it }
            } while (handoffFrame - handoffHoldStart < 64_000_000L)
            val temporaryToDelete = pickerState.temporaryIds.filter { !commitTarget || it != targetId }
            previewScheduleId = null
            pickerState.reset()
            temporaryToDelete.forEach(viewModel::deleteSchedule)
            withFrameNanos { }
            onFinished?.invoke()
        }
    }

    val latestPickerBackAction by rememberUpdatedState(newValue = {
        if (pickerState.phase is CustomizeUiState.Picker) {
            if (pickerState.deletingScheduleId != null) {
                pickerState.deletingScheduleId = null
                pickerState.deleteReveal = 0f
            } else {
                // System Back is deliberately identical to the visible Cancel action.
                exitPicker(apply = false)
            }
        }
    })
    BackHandler(enabled = pickerState.overlayVisible) {
        latestPickerBackAction()
    }
    val returnHomeToCurrentDateAndWeek = {
        if (beforeScheduleTerm) {
            homeDisplayDate = parseScheduleDate(visualState.config.termStartDate) ?: LocalDate.now()
            homeDisplayWeek = 1
        } else {
            homeDisplayDate = LocalDate.now()
            homeDisplayWeek = homeCurrentWeek
        }
    }

    LaunchedEffect(state.config.hideFromRecents) {
        hideFromRecentsEnabled = state.config.hideFromRecents
    }

    var initialLifecycleStartSeen by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                PendingImportSetupStore.consume(context)?.let { pendingImportedSetupId = it }
                if (initialLifecycleStartSeen) {
                    viewModel.refreshNotifications()
                    pendingPickerEditorScheduleId?.let { scheduleId ->
                        pendingPickerEditorScheduleId = null
                        val generation = ++snapshotGeneration
                        appScope.launch {
                            pickerState.phase = CustomizeUiState.ExitingEditor
                            val snap = captureRenderedSchedule(scheduleId)
                            if (generation == snapshotGeneration && pickerState.selectedScheduleId == scheduleId) {
                                if (snap != null) {
                                    pickerState.snapshots[scheduleId] = snap
                                    pickerState.currentSnapshot = snap
                                    pickerState.currentSnapshotScheduleId = scheduleId
                                }
                                pickerState.phase = CustomizeUiState.Picker
                                hydratePersistedSnapshots(generation)
                            }
                        }
                    }
                } else {
                    initialLifecycleStartSeen = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(1800)
            viewModel.clearSnackbar()
        }
    }
    // Root wrapper — ensures splash covers all content including Scaffold internals
    @OptIn(ExperimentalSharedTransitionApi::class)
    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize()
    ) {
    val sharedTransitionScope = this
    val activeSharedTransitionScope = if (startupPhase == StartupPhase.FullQuality) sharedTransitionScope else null
    CompositionLocalProvider(
        LocalSharedTransitionScope provides activeSharedTransitionScope,
        LocalEditingCourseId provides editingCourseId,
        LocalStartupPhase provides startupPhase,
        LocalGlassQuality provides glassQuality,
        LocalStartupEntranceSpec provides startupEntranceSpec,
        LocalAdaptiveGlass provides adaptiveGlassState,
        LocalHomeReadability provides HomeReadabilityContext(
            bitmap = wallpaperImages.readabilityBitmap,
            config = visualState.config,
            rootSize = homeReadabilityRootSize
        ),
        LocalCourseCardPalette provides wallpaperImages.representativeColors,
        LocalCourseCardColorAssignments provides homeCourseColorAssignments
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned {
                homeReadabilityRootSize = it.size
                homeRootPositionOnScreen = it.positionOnScreen()
            }
            .drawWithContent {
                val recordCleanFrame =
                    detailMorphState is DetailMorphState.Capturing && detailCaptureRecordCleanFrame
                val courseEditorOwnsFrame =
                    courseEditorRequest != null ||
                        courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle
                // Never record the course editor into the detail-page capture layer. It contains
                // several backdrop consumers and recording that entire overlay on every animation
                // frame causes an explosive offscreen-render workload after the first use.
                if ((detailMorphState is DetailMorphState.Idle && !courseEditorOwnsFrame) || recordCleanFrame) {
                    detailCaptureMaskActive.set(recordCleanFrame)
                    try {
                        detailScreenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                    } finally {
                        detailCaptureMaskActive.set(false)
                    }
                }
                drawContent()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    // Live background depth: blur + inertial zoom on the real home surface,
                    // driven by a single slow Animatable that trails the card morph. No frozen
                    // snapshot — the backdrop-heavy home is transformed directly each frame.
                    val zoom = dayAgentBackgroundMotionState.backgroundZoom.value
                    val depthProgress =
                        ((1f - zoom) / (1f - DayAgentBackgroundZoomRestScale)).coerceIn(0f, 1f)
                    scaleX = zoom
                    scaleY = zoom
                    val blurPx = with(density) { 12.dp.toPx() } * depthProgress
                    renderEffect = if (depthProgress > 0.001f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
                .drawWithContent {
                    val mayRecordHome =
                        courseEditorRequest == null &&
                            courseEditorOverlayPhase == CourseEditorOverlayPhase.Idle
                    if (mayRecordHome) {
                        captureRenderToken // Reading the token explicitly invalidates this draw node for capture.
                        screenGraphicsLayer.record { this@drawWithContent.drawContent() }
                        recordedScheduleId.set(visualState.config.id)
                        recordedHomeGeneration.incrementAndGet()
                    }
                    drawContent()
                }
        ) {
        // The live home surface owns shared depth for source-anchored overlays. Consumers render
        // outside this layer and compensate through rememberScreenScaledBackdrop, keeping the
        // liquid sampling coordinates aligned without blurring the foreground panel itself.
        HomeBackgroundZoomLayer(
            zoom = homeOverlayBackgroundZoom
        ) {
        HomeBackgroundBlurLayer(
            zoom = homeOverlayBackgroundZoom,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        Scaffold(
            containerColor = ComposeColor.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (screen !is Screen.Config) TopBarEntranceContainer(
                    phase = startupPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rootTopBarLayoutHeight(screen))
                ) {
                    if (screen is Screen.Home) {
                        AnimatedVisibility(
                            visible = screen is Screen.Config || homeContentUnderTopBar,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f),
                            enter = fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)),
                            exit = fadeOut(animationSpec = spring(dampingRatio = 0.95f, stiffness = 560f))
                        ) {
                            HomeTopGradientBlur(
                                config = visualState.config,
                                backdrop = chromeBackdrop,
                                height = rootTopGradientHeight(screen),
                                modifier = Modifier
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(11f)
                    ) {
                        AppTopBar(
                            screen = screen,
                            state = if (screen is Screen.Home) visualState else state,
                            settingsPage = SettingsPage.Root,
                            backdrop = chromeBackdrop,
                            homeMode = homeMode,
                            onHomeModeChange = { homeMode = it },
                            homeDisplayDate = homeTitleDate,
                            homeDisplayWeek = homeTitleWeek,
                            beforeScheduleTerm = beforeScheduleTerm,
                            afterScheduleTerm = afterScheduleTerm,
                            homeShowingAnotherWeek = homeShowingAnotherWeek,
                            onReturnHomeToCurrentWeek = returnHomeToCurrentDateAndWeek,
                            activeHomeOverlay = activeHomeAnchoredOverlay,
                            onAddButtonPositioned = { addButtonBounds = it },
                            onPersonalizeButtonPositioned = { personalizeButtonBounds = it },
                            onToggleAddMenu = { toggleHomeAnchoredOverlay(HomeAnchoredOverlayKind.Add) },
                            onTogglePersonalize = {
                                toggleHomeAnchoredOverlay(HomeAnchoredOverlayKind.Personalize)
                            },
                            onBackHome = { screen = Screen.Home }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backgroundBackdrop)
                ) {
                    if (screen is Screen.Home) {
                        if (!visualState.loaded) {
                            HomeBackdropFallback()
                        } else {
                            if (wallpaperImages.source != null) {
                                HomeWallpaper(
                                    visualState.config,
                                    wallpaperImages,
                                    startupPhase,
                                    reduceQuality = reduceWallpaperQualityForCourseEditor
                                )
                            } else {
                                HomeBackdropFallback()
                            }
                        }
                    } else if (screen is Screen.Config) {
                        Box(Modifier.fillMaxSize().background(settingsPageBackground(settingsVisualConfig(state.config))))
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                }
                /*
                 * Wallpaper brightness is a visual treatment for the wallpaper only. Keep it
                 * outside backgroundBackdrop's provider node: liquid course cards should sample
                 * the original wallpaper, otherwise lowering wallpaper brightness also darkens
                 * every glass surface that consumes this backdrop.
                 */
                if (
                    screen is Screen.Home &&
                    visualState.loaded &&
                    visualState.config.hasAnyWallpaper() &&
                    wallpaperImages.source != null
                ) {
                    WallpaperToneOverlay(visualState.config)
                }
                val contentModifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                Column(modifier = contentModifier) {
                    if (screen !is Screen.Home) {
                        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    }
                    ContentEntranceContainer(phase = startupPhase, modifier = Modifier.weight(1f)) {
                        when (screen) {
                            Screen.Home -> {
                                 HomeScreen(
                                     state = visualState,
                                     agentState = state,
                                    mode = homeMode,
                                    adaptiveMetrics = homeAdaptiveMetrics,
                                    weekCardHeight = weekCardHeight.dp,
                                    displayWeek = homeDisplayWeek,
                                    displayDate = homeDisplayDate,
                                    backdrop = backgroundBackdrop,
                                    dayAgentBackdrop = dayAgentBackdrop,
                                    // Dragged week cards must not sample contentBackdrop/chromeBackdrop:
                                    // they live inside contentBackdrop, so sampling it can recursively include themselves.
                                    floatingCourseBackdrop = backgroundBackdrop,
                                    weekHeaderBackdrop = backgroundBackdrop,
                                    onSwipeWeek = { delta -> homeDisplayWeek = (homeDisplayWeek + delta).coerceIn(1, visualState.config.totalWeeks.coerceAtLeast(1)) },
                                    onSwipeDay = { delta ->
                                        val requested = homeDisplayDate.plusDays(delta.toLong())
                                        val range = scheduleDayNavigationRange(visualState.config, todayDate)
                                        homeDisplayDate = if (range == null) requested
                                        else requested.coerceIn(range.start, range.endInclusive)
                                    },
                                    onContentUnderTopBarChange = { homeContentUnderTopBar = it },
                                    dayAgentBackgroundMotionState = dayAgentBackgroundMotionState,
                                    onAgentPagerSettledChange = { settled ->
                                        dayAgentPagerSettled = settled
                                    },
                                    onAgentPrepareOpen = {
                                        // Never open over a partially swiped day page. The background is now
                                        // transformed live (blur + zoom), so no frozen snapshot capture is
                                        // needed — only the pager-settled gate remains.
                                        if (!dayAgentPagerSettled) {
                                            snapshotFlow { dayAgentPagerSettled }.first { it }
                                        }
                                    },
                                    onAgentDismissed = {},
                                    onCourseClick = { course, week, sourceBounds ->
                                        openCourseEditor(course, week, sourceBounds)
                                    },
                                    onAddCourse = viewModel::addCourse,
                                    onAgentAction = {
                                        plan: AgentPlan,
                                        onResult: (AgentPlanExecutionResult) -> Unit ->
                                        val actions = plan.actions
                                        val action = actions.singleOrNull()
                                        val courseActions = actions.filter {
                                            it.type == AgentValidatedActionType.ADD ||
                                                it.type == AgentValidatedActionType.UPDATE ||
                                                it.type == AgentValidatedActionType.DELETE
                                        }
                                         val settingActions = actions.filter {
                                             it.type == AgentValidatedActionType.SET_SETTING ||
                                                 it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS
                                        }
                                        when {
                                            courseActions.size == actions.size && actions.isNotEmpty() ->
                                                viewModel.executeAgentPlan(actions, onResult)
                                            settingActions.size == actions.size && actions.isNotEmpty() ->
                                                viewModel.executeAgentSettingPlan(actions, onResult)
                                            action == null -> onResult(
                                                AgentPlanExecutionResult(
                                                    success = false,
                                                    preview = null,
                                                    verified = false,
                                                    message = "课程操作与页面或设置操作不能在同一事务中执行"
                                                )
                                            )
                                            else -> when (action.type) {
                                            AgentValidatedActionType.ADD,
                                            AgentValidatedActionType.UPDATE,
                                            AgentValidatedActionType.DELETE ->
                                                viewModel.executeAgentPlan(actions, onResult)
                                            AgentValidatedActionType.OPEN_SETTINGS -> {
                                                if (action.settingsPage == "PERSONALIZATION") {
                                                    openHomeAnchoredOverlay(HomeAnchoredOverlayKind.Personalize)
                                                } else agentSettingsPage(action.settingsPage)?.let { page ->
                                                    val intent = Intent(context, SettingsDetailActivity::class.java)
                                                        .putExtra(SettingsDetailPageExtra, page.name)
                                                    if (page == SettingsPage.Schedule) {
                                                        intent.putExtra(ScheduleCustomizeIdExtra, state.config.id)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                                onResult(
                                                    AgentPlanExecutionResult(
                                                        success = true,
                                                        preview = null,
                                                        verified = true,
                                                        message = "页面已打开"
                                                    )
                                                )
                                            }
                                            AgentValidatedActionType.SET_SETTING -> {
                                                when {
                                                    action.settingKey == "SCHEDULE_NAME" -> action.settingValue
                                                        ?.let { name -> viewModel.renameSchedule(state.config.id, name) }
                                                    AgentSettingRegistry.isPreferenceSetting(action.settingKey) -> {
                                                        AgentSettingRegistry.applyPreference(
                                                            context,
                                                            action.settingKey,
                                                            action.settingValue
                                                        )
                                                    }
                                                    AgentSettingRegistry.isPeriodTimeSetting(action.settingKey) -> {
                                                        AgentSettingRegistry.applyPeriodTime(
                                                            state.periods,
                                                            action.settingKey,
                                                            action.settingValue
                                                        )?.let { updatedPeriods ->
                                                            viewModel.saveConfig(state.config, updatedPeriods)
                                                        }
                                                    }
                                                    else -> AgentSettingRegistry.apply(
                                                        state.config,
                                                        action.settingKey,
                                                        action.settingValue
                                                    )?.let(viewModel::savePersonalization)
                                                }
                                                onResult(
                                                    AgentPlanExecutionResult(
                                                        success = true,
                                                        preview = null,
                                                        verified = false,
                                                        message = "设置修改已提交"
                                                    )
                                                )
                                            }
                                            AgentValidatedActionType.SET_PERIOD_SETTINGS ->
                                                viewModel.executeAgentSettingPlan(actions, onResult)
                                        }
                                        }
                                    },
                                    onUpdateCourseSingleWeek = viewModel::updateCourseSingleWeek,
                                    conflictFocusCourseId = pendingConflictCourseId
                                        ?.takeIf { homeDisplayWeek in pendingConflictWeeks },
                                    conflictFocusCourseKey = pendingConflictCourseKey
                                        ?.takeIf { homeDisplayWeek in pendingConflictWeeks },
                                    onResolveCourseConflict = { course, moved, week ->
                                            viewModel.updateCourseSingleWeek(course, moved, week)
                                        appScope.launch {
                                            delay(180)
                                            pendingConflictCourseId = null
                                            pendingConflictCourseKey = null
                                            pendingConflictWeeks = emptyList()
                                        }
                                    },
                                    onDeleteCourseSingleWeek = viewModel::deleteCourseSingleWeek,
                                    onScheduleLongPress = {
                                        if (pickerState.phase is CustomizeUiState.Home) {
                                            pendingHomeAnchoredOverlay = null
                                            homeAnchoredOverlayRequest = null
                                            pickerState.phase = CustomizeUiState.ShowingEntryButton
                                            showScheduleEntryPill = true
                                            prewarmCurrentScheduleSnapshot()
                                        }
                                    }
                                )
                            }
                            Screen.Config -> Box(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                        page = SettingsPage.Root,
                                        state = state,
                                        backdrop = backgroundBackdrop,
                                        onPageChange = {
                                            val intent = Intent(context, SettingsDetailActivity::class.java)
                                                .putExtra(SettingsDetailPageExtra, it.name)
                                            if (it == SettingsPage.Schedule) {
                                                intent.putExtra(ScheduleCustomizeIdExtra, state.config.id)
                                            }
                                            context.startActivity(intent)
                                        },
                                        onSave = viewModel::saveConfig,
                                        onUpdateConfig = viewModel::savePersonalization,
                                        onPreviewLiveUpdate = viewModel::previewLiveUpdate
                                    )
                            }
                        }
                        if (screen is Screen.Home) {
                            DockBackdropContinuityPatch(
                                config = visualState.config,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            if (screen !is Screen.Home) {
                showScheduleEntryPill = false
                if (pickerState.phase is CustomizeUiState.ShowingEntryButton) pickerState.phase = CustomizeUiState.Home
            }
            if (screen is Screen.Home || screen is Screen.Config) {
                DockEntranceContainer(
                    phase = startupPhase,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    FloatingDock(
                        selected = screen,
                        backdrop = chromeBackdrop,
                        config = if (screen is Screen.Home) visualState.config else state.config,
                        onHome = { screen = Screen.Home },
                        onConfig = {
                            screen = Screen.Config
                        }
                    )
                }
            }
        }
        }

        // This control intentionally lives outside the recorded home layer. It can be visible
        // while the real home is pre-captured without becoming part of its own preview bitmap.
        if (screen is Screen.Home) {
            ScheduleManagerEntryPill(
                visible = showScheduleEntryPill,
                backdrop = chromeBackdrop,
                config = visualState.config,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(89f),
                onClick = ::enterCustomizePage,
                onDismiss = {
                    entryPrewarmJob?.cancel()
                    showScheduleEntryPill = false
                    if (pickerState.phase is CustomizeUiState.ShowingEntryButton) {
                        pickerState.phase = CustomizeUiState.Home
                    }
                }
            )
        }

        pickerState.snapshotCoverBitmap?.let { cover ->
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().zIndex(90f)
            )
        }

        SchedulePickerOverlay(
            pickerState = pickerState,
            allState = allSchedulesState,
            backdrop = chromeBackdrop,
            dialogBackdrop = pickerSceneBackdrop,
            onPageSelected = ::switchPickerSchedule,
            onApply = { exitPicker(apply = true) },
            onClose = { exitPicker(apply = false) },
            onBack = { centeredId ->
                exitPicker(apply = false)
            },
            onCreate = {
                if (pickerState.phase is CustomizeUiState.Picker) {
                    snapshotJob?.cancel()
                    pickerState.phase = CustomizeUiState.CreatingCombination
                    viewModel.createSchedule(activate = false) { newId ->
                        appScope.launch {
                            snapshotFlow {
                                val latest = latestAllSchedulesState.value
                                latest.schedules.any { it.id == newId } &&
                                    latest.allConfigs.any { it.id == newId } &&
                                    latest.allPeriods.any { it.scheduleId == newId }
                            }.first { it }
                            pickerState.temporaryIds += newId
                            // Keep the currently centered card as the transition source. The new
                            // schedule is rendered underneath and revealed by the same crossfade
                            // hand-off used by Cancel, so no fake card or Pager insertion is needed.
                            pickerState.phase = CustomizeUiState.Picker
                            exitPicker(
                                apply = true,
                                targetOverride = newId,
                                commitTarget = true,
                                crossfadeToTarget = true,
                                onFinished = {
                                    quickScheduleDraft = quickDraftFor(newId)
                                }
                            )
                        }
                    }
                }
            },
            onShare = { scheduleId, shareType ->
                val profile = allSchedulesState.schedules.firstOrNull { it.id == scheduleId }
                val config = allSchedulesState.allConfigs.firstOrNull { it.id == scheduleId } ?: defaultConfig(scheduleId)
                val periods = allSchedulesState.allPeriods.filter { it.scheduleId == scheduleId }.ifEmpty { defaultPeriods(scheduleId) }
                val courses = allSchedulesState.allCourses.filter { it.scheduleId == scheduleId }
                val scheduleName = profile?.name ?: "课表"
                when (shareType) {
                    ScheduleShareType.TOKEN -> shareScheduleToken(
                        context,
                        scheduleName,
                        buildSleepDownScheduleToken(config, periods, courses)
                    )
                    ScheduleShareType.ICS -> appScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                IcsScheduleCodec.writeShareFile(context, scheduleName, config, periods, courses)
                            }
                        }.onSuccess { shareScheduleIcs(context, scheduleName, it) }
                            .onFailure { Toast.makeText(context, it.message ?: "ICS 文件生成失败", Toast.LENGTH_SHORT).show() }
                    }
                }
            },
            onCustomize = { scheduleId ->
                if (pickerState.phase is CustomizeUiState.Picker) {
                    quickScheduleDraft = quickDraftFor(scheduleId)
                }
            },
            onRename = viewModel::renameSchedule,
            onDeleteRequest = { scheduleId ->
                if (pickerState.phase is CustomizeUiState.Picker && pickerState.orderIds.size > 1) {
                    snapshotJob?.cancel()
                    pickerState.phase = CustomizeUiState.DeletingCombination
                    appScope.launch {
                        val animation = Animatable(0f)
                        animation.animateTo(1f, tween(280)) {
                            pickerState.deleteScale = 1f - value * 0.4f
                            pickerState.deleteAlpha = 1f - value
                        }
                        val deletedIndex = pickerState.orderIds.indexOf(scheduleId)
                        pickerState.orderIds.remove(scheduleId)
                        pickerState.temporaryIds.remove(scheduleId)
                        pickerState.snapshots.remove(scheduleId)
                        appScope.launch { ScheduleSnapshotStore.delete(context, scheduleId) }
                        viewModel.deleteSchedule(scheduleId)
                        val nextId = pickerState.orderIds.getOrNull(deletedIndex.coerceAtMost(pickerState.orderIds.lastIndex))
                            ?: pickerState.orderIds.firstOrNull()
                        pickerState.deletingScheduleId = null
                        pickerState.deleteReveal = 0f
                        pickerState.deleteScale = 1f
                        pickerState.deleteAlpha = 1f
                        pickerState.phase = CustomizeUiState.Picker
                        nextId?.let(::switchPickerSchedule)
                    }
                }
            },
            // Capture the complete manager (cards, title, indicators and actions) in a producer
            // that does not contain the later quick-settings sheet consumer.
            modifier = Modifier.layerBackdrop(pickerSceneBackdrop)
        )
    }
    }
    } // end HomeBackgroundZoomLayer

    val latestOpenHomeMenuDestination = rememberUpdatedState<(HomeMenuDestinationKind) -> Unit> {
        kind -> openHomeMenuDestination(kind)
    }
    val homeAddActions = remember {
        listOf(
            AddMenuAction(R.drawable.ic_add_course, "添加单节课") {
                latestOpenHomeMenuDestination.value(HomeMenuDestinationKind.AddCourse)
            },
            AddMenuAction(R.drawable.ic_ai_import, "手动导入课表") {
                latestOpenHomeMenuDestination.value(HomeMenuDestinationKind.ManualImport)
            },
            AddMenuAction(R.drawable.ic_school_import, "教务系统导入") {
                latestOpenHomeMenuDestination.value(HomeMenuDestinationKind.EduImport)
            }
        )
    }

    fun closeHomeMenuDestination() {
        homeAnchoredOverlayRequest = null
        homeMenuDestinationRequest = null
    }

    HomeAnchoredMorphOverlayHost(
        request = if (screen is Screen.Home) homeAnchoredOverlayRequest else null,
        motionState = homeAnchoredMorphState,
        backdrop = homeAnchoredOverlayBackdrop,
        config = state.config,
        addActions = homeAddActions,
        adaptiveMetrics = homeAdaptiveMetrics,
        modifier = Modifier
            .zIndex(24f)
            .graphicsLayer { alpha = if (homeMenuSourceHidden) 0f else 1f },
        onDismissRequest = { homeAnchoredOverlayRequest = null },
        onAddMenuBoundsChanged = { homeAddMenuBoundsInRoot = it },
        personalizePreviewProgress = personalizationPreviewProgress,
        sourceContent = { kind, sourceModifier ->
            when (kind) {
                HomeAnchoredOverlayKind.Add -> HomeIconButtonVisual(
                    backdrop = homeAnchoredOverlayBackdrop,
                    config = state.config,
                    iconRes = R.drawable.ic_add_course,
                    contentDescription = "添加",
                    accentColor = if (glassUsesLightStyle(state.config)) {
                        HomeLightGlassAccentColor
                    } else {
                        ComposeColor(0xFF0A84FF)
                    },
                    modifier = sourceModifier,
                    isInteractive = false
                )
                HomeAnchoredOverlayKind.Personalize -> HomeIconButtonVisual(
                    backdrop = homeAnchoredOverlayBackdrop,
                    config = state.config,
                    iconRes = R.drawable.ic_tune,
                    contentDescription = "个性化",
                    modifier = sourceModifier,
                    isInteractive = false
                )
            }
        },
        personalizeContent = { panelModifier ->
            PersonalizePanel(
                modifier = panelModifier,
                drawSurface = false,
                state = visualState,
                backdrop = homeAnchoredOverlayBackdrop,
                mode = homeMode,
                weekCardHeight = weekCardHeight,
                onWeekCardHeight = {
                    val safeHeight = it.coerceIn(38f, 80f)
                    val committedConfig = visualState.config.copy(weekCardHeightDp = safeHeight)
                    weekCardHeight = safeHeight
                    personalizationPreviewConfig = committedConfig
                    personalizationPendingCommitConfig = committedConfig
                    viewModel.savePersonalization(committedConfig)
                },
                onWeekCardHeightPreview = {
                    val safeHeight = it.coerceIn(38f, 80f)
                    weekCardHeight = safeHeight
                    personalizationPreviewConfig = visualState.config.copy(weekCardHeightDp = safeHeight)
                },
                onPickWallpaper = {
                    wallpaperLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSampleWallpaperColor = { homeDialog = HomeDialog.SampleWallpaperColor },
                onUpdateConfig = {
                    personalizationPreviewConfig = it
                    personalizationPendingCommitConfig = it
                    viewModel.savePersonalization(it)
                },
                onPreviewConfig = { personalizationPreviewConfig = it },
                previewSliderKey = personalizationSliderPreviewKey,
                previewProgress = personalizationPreviewProgress,
                onSliderPreviewActiveChange = { key, active ->
                    personalizationSliderPreviewKey = key.takeIf { active }
                }
            )
        }
    )

    HomeMenuDestinationOverlayHost(
        request = homeMenuDestinationRequest,
        motionState = homeMenuDestinationMotionState,
        state = state,
        backdrop = homeMenuDestinationBackdrop,
        adaptiveMetrics = homeAdaptiveMetrics,
        modifier = Modifier.zIndex(90f),
        onDismissRequest = ::closeHomeMenuDestination,
        sourceActions = homeAddActions,
        onSourceHandoff = { homeMenuSourceHidden = true },
        onCollapseHandoff = { homeMenuSourceHidden = false },
        onClosed = { homeMenuSourceHidden = false },
        onAddCourse = { course ->
            viewModel.addCourse(course)
            closeHomeMenuDestination()
        },
        onManualImportParsed = { draft ->
            closeHomeMenuDestination()
            appScope.launch {
                delay(HomeMenuDestinationCloseDurationMillis.toLong())
                homeDialog = HomeDialog.ConfirmImport(draft)
            }
        },
        onEduAdapterSelected = { adapter ->
            context.startActivity(
                Intent(context, EduImportActivity::class.java)
                    .putExtra(EduAdapterExtra, adapter.toIntentKey())
            )
        }
    )
    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
        QuickScheduleSettingsSheets(
            draft = quickScheduleDraft,
            config = state.config,
            // The home/chrome producers are siblings below the sheet, so this remains a real
            // liquid backdrop without ever recording the dialog that consumes it. In particular,
            // do not restore the former root-level quickSheetBackdrop: it caused the native
            // RenderThread recursion when the new-schedule sheet opened after Picker exit.
            backdrop = if (pickerState.overlayVisible) pickerSceneBackdrop else chromeBackdrop,
            onDraftChange = { quickScheduleDraft = it },
            onDismiss = { quickScheduleDraft = null },
            onDismissFinished = {
                // Direct customization leaves the manager below the sheet. New-schedule setup
                // still returns through the existing home-to-picker Morph after its sheet closes.
                if (!pickerState.overlayVisible) {
                    pickerState.phase = CustomizeUiState.Home
                    enterCustomizePage()
                }
            },
            onSave = { draft, onSaved ->
                val latest = latestAllSchedulesState.value
                val baseConfig = latest.allConfigs.firstOrNull { it.id == draft.scheduleId }
                    ?: return@QuickScheduleSettingsSheets
                val totalWeeks = draft.totalWeeks.coerceIn(1, 60)
                val manualWeek = draft.currentWeek.coerceIn(1, totalWeeks)
                val datedConfig = baseConfig.copy(
                    totalWeeks = totalWeeks,
                    currentWeek = manualWeek,
                    termStartDate = draft.termStartDate.ifBlank { null },
                    autoCurrentWeek = draft.autoCurrentWeek,
                    hideEmptyWeekends = draft.hideEmptyWeekends
                )
                val periods = latest.allPeriods.filter { it.scheduleId == draft.scheduleId }
                    .ifEmpty { defaultPeriods(draft.scheduleId) }
                viewModel.saveConfigForSchedule(
                    draft.scheduleId,
                    // The automatic display week is derived at runtime. Persist the
                    // selected fallback instead of writing a clamped pre-term week 1.
                    datedConfig.copy(currentWeek = manualWeek),
                    periods,
                    onSaved
                )
            },
            suppressDetailedButton = detailMorphState !is DetailMorphState.Idle,
            onDetailedSettings = { scheduleId, sourceBounds, saveBeforeOpening ->
                if (detailMorphState !is DetailMorphState.Idle) {
                    return@QuickScheduleSettingsSheets
                }
                appScope.launch {
                    val fullSnapshot = runCatching {
                        detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                    }.getOrNull()
                    if (fullSnapshot == null || fullSnapshot.width <= 0 || fullSnapshot.height <= 0) {
                        detailMorphState = DetailMorphState.Idle
                        saveBeforeOpening { }
                        return@launch
                    }
                    val x = sourceBounds.left.toInt().coerceIn(0, fullSnapshot.width - 1)
                    val y = sourceBounds.top.toInt().coerceIn(0, fullSnapshot.height - 1)
                    val width = sourceBounds.width.toInt().coerceIn(1, fullSnapshot.width - x)
                    val height = sourceBounds.height.toInt().coerceIn(1, fullSnapshot.height - y)
                    val sourceCardSnapshot = runCatching {
                        Bitmap.createBitmap(fullSnapshot, x, y, width, height)
                    }.getOrNull()
                    if (sourceCardSnapshot == null) {
                        detailMorphState = DetailMorphState.Idle
                        saveBeforeOpening { }
                        return@launch
                    }
                    // Freeze the old frame for the display, then record one clean frame after the
                    // real source button has been removed (its fixed-size spacer keeps sheet layout
                    // stable). The cover itself is suppressed only inside the recording pass below.
                    detailCaptureCoverBitmap = fullSnapshot
                    detailMorphState = DetailMorphState.Capturing
                    detailCaptureRecordCleanFrame = true
                    withFrameNanos { }
                    withFrameNanos { }
                    val cleanBackgroundSnapshot = runCatching {
                        detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                    }.getOrNull()
                    detailCaptureRecordCleanFrame = false
                    if (cleanBackgroundSnapshot == null) {
                        detailMorphState = DetailMorphState.Idle
                        saveBeforeOpening { }
                        return@launch
                    }
                    val request = DetailMorphRequest(
                        scheduleId = scheduleId,
                        sourceBounds = sourceBounds,
                        backgroundSnapshot = cleanBackgroundSnapshot,
                        sourceCardSnapshot = sourceCardSnapshot
                    )
                    saveBeforeOpening {
                        detailMorphRequest = request
                        detailCaptureCoverBitmap = null
                    }
                }
            }
        )

        // Keep the course editor inside the same root stack as the stock MIUIX popup
        // host. The host stays in its 1.0.6 position and therefore preserves the manager
        // quick sheet's original backdrop, colors and animation, while its later draw
        // order still places picker dialogs above the editor shell.
        CourseEditorContainerOverlayHost(
            request = courseEditorRequest,
            state = state,
            backdrop = courseEditorBackdrop,
            config = state.config,
            adaptiveMetrics = homeAdaptiveMetrics,
            modifier = Modifier.zIndex(100f),
            onDismissRequest = { closeCourseEditor() },
            onSave = { original, edited, targetWeek ->
                if (courseWeeksChanged(original, edited)) {
                    val conflictWeeks = conflictWeeksForEditedCourse(original, edited, state.courses)
                    if (conflictWeeks.isEmpty()) {
                        viewModel.updateCourse(edited)
                        closeCourseEditor()
                    } else {
                        homeDialog = HomeDialog.ConfirmCourseConflicts(
                            original,
                            edited,
                            targetWeek ?: effectiveCurrentWeek(state.config),
                            conflictWeeks
                        )
                    }
                } else {
                    // Keep the editor fully mounted behind the choice dialog. Closing it first
                    // lets its Morph/interaction shield cover and stall the confirmation.
                    homeDialog = HomeDialog.ApplyCourseEdit(original, edited, targetWeek ?: effectiveCurrentWeek(state.config))
                }
            },
            onDelete = { course, targetWeek ->
                homeDialog = HomeDialog.ApplyCourseDelete(course, targetWeek ?: effectiveCurrentWeek(state.config))
            },
            motionState = courseEditorMotionState,
            onRenderedCourseIdChange = { courseEditorRenderedCourseId = it },
            onPhaseChange = {}
        )

        if (courseEditorRequest != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(101f)) {
                top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
            }
        } else {
            // Exact 1.0.6 host structure for the manager quick sheet and all regular dialogs.
            top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
        }
    }

    detailCaptureCoverBitmap?.let { cover ->
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(259f)
                .drawWithContent {
                    if (!detailCaptureMaskActive.get()) drawContent()
                }
                .pointerInput(cover) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    detailMorphRequest?.let { request ->
        val detailState = allSchedulesState.forSchedule(request.scheduleId)
        DetailScheduleMorphOverlay(
            request = request,
            detailState = detailState,
            onMorphStateChange = { detailMorphState = it },
            onSave = { config, periods ->
                viewModel.saveConfigForSchedule(request.scheduleId, config, periods)
            },
            onPreviewLiveUpdate = viewModel::previewLiveUpdate,
            onFinished = {
                detailMorphRequest = null
                detailCaptureCoverBitmap = null
                detailCaptureRecordCleanFrame = false
                detailMorphState = DetailMorphState.Idle
            },
            modifier = Modifier.zIndex(260f)
        )
    }

    (renderedHomeDialog as? HomeDialog.EditWallpaper)?.let { dialog ->
        WallpaperEditorOverlay(
            uri = dialog.uri,
            entrySnapshot = dialog.entrySnapshot,
            config = state.config,
            backdrop = chromeBackdrop,
            visible = homeDialogVisible,
            adaptiveMetrics = homeAdaptiveMetrics,
            onCancel = { dismissHomeDialog() },
            onApply = { nextConfig ->
                viewModel.savePersonalization(nextConfig)
                dismissHomeDialog()
            },
            modifier = Modifier.zIndex(280f)
        )
    }

    // Dialog-based dialogs for all other types (including EditCourse without a source card)
    renderedHomeDialog?.let { dialog ->
        if (dialog !is HomeDialog.EditWallpaper && (dialog !is HomeDialog.EditCourse || dialog.course == null)) {
        if (dialog is HomeDialog.ApplyCourseEdit) {
            ApplyCourseEditDialog(
                original = dialog.original,
                edited = dialog.edited,
                backdrop = homeDialogBackdrop,
                config = state.config,
                onSingle = {
                    val conflictWeeks = conflictWeeksForSingleWeekEdit(
                        dialog.original,
                        dialog.edited,
                        dialog.targetWeek,
                        state.courses
                    )
                    if (conflictWeeks.isEmpty()) {
                        viewModel.updateCourseSingleWeek(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek
                        )
                        dismissHomeDialog()
                        closeCourseEditor()
                    } else {
                        homeDialog = HomeDialog.ConfirmCourseConflicts(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek,
                            conflictWeeks,
                            singleWeekOnly = true
                        )
                    }
                },
                onAll = {
                    val conflictWeeks = conflictWeeksForEditedCourse(
                        dialog.original,
                        dialog.edited,
                        state.courses
                    )
                    if (conflictWeeks.isEmpty()) {
                        viewModel.updateCourse(dialog.edited)
                        dismissHomeDialog()
                        closeCourseEditor()
                    } else {
                        homeDialog = HomeDialog.ConfirmCourseConflicts(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek,
                            conflictWeeks
                        )
                    }
                },
                onCancel = { dismissHomeDialog() }
            )
        } else if (dialog is HomeDialog.ConfirmCourseConflicts) {
            CourseConflictRetentionDialog(
                course = dialog.edited,
                conflictWeeks = dialog.conflictWeeks,
                backdrop = homeDialogBackdrop,
                config = state.config,
                onKeepTemporarily = {
                    val editorWasOpen = courseEditorRequest != null
                    val editedRetractBounds = courseEditorRequest
                        ?.sourceBoundsInRoot
                        ?.takeIf { homeMode == HomeMode.Week }
                        ?.let { source ->
                            val periodIndexes = state.periods.map { it.periodIndex }
                            val originalStart = dialog.original.periods
                                .map(periodIndexes::indexOf)
                                .filter { it >= 0 }
                                .minOrNull()
                            val editedPositions = dialog.edited.periods
                                .map(periodIndexes::indexOf)
                                .filter { it >= 0 }
                                .distinct()
                                .sorted()
                            val editedStart = editedPositions.firstOrNull()
                            if (originalStart == null || editedStart == null) {
                                null
                            } else {
                                val cardHeightPx = with(density) { weekCardHeight.dp.toPx() }
                                val cardGapPx = with(density) { 4.dp.toPx() }
                                val left = source.left +
                                    (dialog.edited.weekday - dialog.original.weekday) *
                                    (source.width + cardGapPx)
                                val top = source.top +
                                    (editedStart - originalStart) * cardHeightPx
                                val targetHeight = (
                                    cardHeightPx * editedPositions.size.coerceAtLeast(1) -
                                        cardGapPx
                                    ).coerceAtLeast(with(density) { 18.dp.toPx() })
                                Rect(left, top, left + source.width, top + targetHeight)
                            }
                        }
                    courseEditorMotionState.retractTo(editedRetractBounds)
                    if (dialog.singleWeekOnly) {
                        viewModel.updateCourseSingleWeek(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek
                        )
                    } else {
                        viewModel.updateCourse(dialog.edited)
                    }
                    pendingConflictCourseId =
                        dialog.edited.id.takeUnless { dialog.singleWeekOnly }
                    pendingConflictCourseKey = dialog.edited.occurrenceOverrideKey()
                    pendingConflictWeeks = dialog.conflictWeeks
                    dismissHomeDialog()
                    closeCourseEditor()
                    val conflictWeek = dialog.conflictWeeks.first()
                    if (editorWasOpen) {
                        appScope.launch {
                            delay(CourseEditorCloseDurationMillis.toLong() + 32L)
                            homeDisplayWeek = conflictWeek
                            homeMode = HomeMode.Week
                        }
                    } else {
                        homeDisplayWeek = conflictWeek
                        homeMode = HomeMode.Week
                    }
                },
                onReturn = {
                    homeDialog = HomeDialog.ApplyCourseEdit(
                        dialog.original,
                        dialog.edited,
                        dialog.targetWeek
                    )
                }
            )
        } else if (dialog is HomeDialog.ApplyCourseDelete) {
            ApplyCourseDeleteDialog(
                course = dialog.course,
                backdrop = homeDialogBackdrop,
                config = state.config,
                onSingle = {
                    viewModel.deleteCourseSingleWeek(dialog.course, dialog.targetWeek)
                    dismissHomeDialog()
                    closeCourseEditor()
                },
                onAll = {
                    viewModel.deleteCourse(dialog.course)
                    dismissHomeDialog()
                    closeCourseEditor()
                },
                onCancel = {
                    dismissHomeDialog()
                }
            )
        } else {
        Dialog(onDismissRequest = { dismissHomeDialog() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            if (dialog is HomeDialog.ImportSchedule) {
                val dialogView = LocalView.current
                DisposableEffect(dialogView) {
                    val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
                    val previousSoftInputMode = dialogWindow?.attributes?.softInputMode
                    dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    onDispose {
                        previousSoftInputMode?.let { dialogWindow.setSoftInputMode(it) }
                    }
                }
            }
            val animatedDialogContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = homeDialogVisible,
                    enter = popEnterTransition(),
                    exit = popExitTransition()
                ) {
                val dialogContent: @Composable () -> Unit = {
                    when (dialog) {
                    is HomeDialog.EditCourse -> {
                        val editor: @Composable () -> Unit = {
                            NormalizedCourseEditorScreen(
                                state = state,
                                initialCourse = dialog.course,
                                onCancel = { dismissHomeDialog() },
                                onSave = {
                                    if (dialog.course == null) {
                                        viewModel.addCourse(it)
                                        dismissHomeDialog()
                                    } else if (courseWeeksChanged(dialog.course, it)) {
                                        viewModel.updateCourse(it)
                                        dismissHomeDialog()
                                    } else {
                                        homeDialog = HomeDialog.ApplyCourseEdit(dialog.course, it, dialog.targetWeek ?: effectiveCurrentWeek(state.config))
                                    }
                                },
                                onDelete = {
                                    homeDialog = HomeDialog.ApplyCourseDelete(it, dialog.targetWeek ?: effectiveCurrentWeek(state.config))
                                },
                                backdrop = homeDialogBackdrop,
                                pickerRenderInRootScaffold = dialog.course != null
                            )
                        }
                        if (dialog.course == null) {
                            // Keep the Android dialog itself under its original centered constraints.
                            // Only the editor content owns a local overlay host, so wheel pickers can
                            // cover the form without changing the glass shell's size or position.
                            top.yukonga.miuix.kmp.basic.Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = ComposeColor.Transparent,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                editor()
                            }
                        } else {
                            editor()
                        }
                    }
                    HomeDialog.ImportSchedule -> NormalizedAiManualImportScreen(
                        state = state,
                        backdrop = homeDialogBackdrop,
                        onCancel = { dismissHomeDialog() },
                        onParsed = { homeDialog = HomeDialog.ConfirmImport(it) }
                    )
                    HomeDialog.EduImport -> Unit
                    is HomeDialog.EditWallpaper -> Unit
                    is HomeDialog.ConfirmImport -> ConfirmScheduleScreen(
                        draft = dialog.draft,
                        backdrop = homeDialogBackdrop,
                        onCancel = { homeDialog = dialog.returnDialog },
                        onConfirm = { createNewSchedule ->
                            viewModel.importDraft(dialog.draft, createNewSchedule) { scheduleId ->
                                dismissHomeDialog()
                                pendingImportedSetupId = scheduleId
                            }
                        }
                    )
                    HomeDialog.SampleWallpaperColor -> WallpaperColorSamplerScreen(
                        state = state,
                        backdrop = homeDialogBackdrop,
                        onCancel = { dismissHomeDialog() },
                        onSelected = { color ->
                            viewModel.savePersonalization(state.config.copy(cardColorArgb = color))
                            dismissHomeDialog()
                        }
                    )
                            is HomeDialog.ApplyCourseEdit -> ApplyCourseEditDialog(
                        original = dialog.original,
                        edited = dialog.edited,
                        backdrop = homeDialogBackdrop,
                        config = state.config,
                        onSingle = {
                            val conflictWeeks = conflictWeeksForSingleWeekEdit(
                                dialog.original,
                                dialog.edited,
                                dialog.targetWeek,
                                state.courses
                            )
                            if (conflictWeeks.isEmpty()) {
                                viewModel.updateCourseSingleWeek(
                                    dialog.original,
                                    dialog.edited,
                                    dialog.targetWeek
                                )
                                dismissHomeDialog()
                            } else {
                                homeDialog = HomeDialog.ConfirmCourseConflicts(
                                    dialog.original,
                                    dialog.edited,
                                    dialog.targetWeek,
                                    conflictWeeks,
                                    singleWeekOnly = true
                                )
                            }
                        },
                            onAll = {
                            val conflictWeeks = conflictWeeksForEditedCourse(
                                dialog.original,
                                dialog.edited,
                                state.courses
                            )
                            if (conflictWeeks.isEmpty()) {
                                viewModel.updateCourse(dialog.edited)
                                dismissHomeDialog()
                            } else {
                                homeDialog = HomeDialog.ConfirmCourseConflicts(
                                    dialog.original,
                                    dialog.edited,
                                    dialog.targetWeek,
                                    conflictWeeks
                                )
                            }
                        },
                        onCancel = {
                            dismissHomeDialog()
                        }
                    )
                    is HomeDialog.ConfirmCourseConflicts -> Unit
                    is HomeDialog.ApplyCourseDelete -> ApplyCourseDeleteDialog(
                        course = dialog.course,
                        backdrop = homeDialogBackdrop,
                        config = state.config,
                        onSingle = {
                            viewModel.deleteCourseSingleWeek(dialog.course, dialog.targetWeek)
                            dismissHomeDialog()
                        },
                        onAll = {
                            viewModel.deleteCourse(dialog.course)
                            dismissHomeDialog()
                        },
                        onCancel = {
                            dismissHomeDialog()
                            openCourseEditor(dialog.course, dialog.targetWeek, null)
                        }
                    )
                    }
                }
                CenterLiquidDialog(
                        backdrop = homeDialogBackdrop,
                        config = state.config,
                        modifier = Modifier,
                        size = if (dialog is HomeDialog.ImportSchedule) {
                            LiquidDialogSize.Compact
                        } else {
                            LiquidDialogSize.Standard
                        }
                    ) {
                        dialogContent()
                    }
                }
            }
            animatedDialogContent()
        }
        }
        }
        }

        SettingsUpdateDialogHost(
            dialog = automaticUpdateDialog,
            backdrop = chromeBackdrop,
            config = state.config,
            onDismiss = { automaticUpdateDialog = null },
            onRetry = {
                automaticUpdateDialog = SettingsUpdateDialog.Checking
                appScope.launch {
                    automaticUpdateDialog = GiteeAppUpdater.checkForUpdate(currentVersionName).fold(
                        onSuccess = { result ->
                            GiteeAppUpdater.recordCheckResult(context, result)
                            when (result) {
                                is GiteeUpdateCheckResult.UpdateAvailable -> SettingsUpdateDialog.Available(result.release)
                                is GiteeUpdateCheckResult.UpToDate -> SettingsUpdateDialog.UpToDate(result.release.tagName)
                            }
                        },
                        onFailure = { SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    )
                }
            },
            onDownload = ::downloadAutomaticUpdate,
            onOpenRelease = { release ->
                context.startActivity(Intent(Intent.ACTION_VIEW, release.releaseUrl.toUri()))
            },
            onOpenBackup = {
                automaticUpdateDialog = null
                screen = Screen.Config
            },
            onRequestInstallPermission = {
                automaticInstallPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
            }
        )

    }
    }
    }

}

@Composable
private fun HomeBackgroundZoomLayer(
    zoom: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                val currentZoom = zoom()
                scaleX = currentZoom
                scaleY = currentZoom
            },
        content = content
    )
}

internal val HomeTopOverlayHeight = 178.dp
private val DetailTopBarHeight = 58.dp
private val DetailTopOverlayExtra = 74.dp
private val DetailContentTopGap = 44.dp
internal val HomeInitialTopInset = 122.dp

@Composable
private fun HomeBackgroundBlurLayer(
    zoom: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier.graphicsLayer {
            val depthRange = maxOf(
                abs(BackgroundZoomOpenScale - 1f),
                abs(HomeMenuDestinationEduBackgroundScale - 1f)
            )
            val depthProgress = (abs(zoom() - 1f) / depthRange).coerceIn(0f, 1f)
            val blurPx = with(density) { 12.dp.toPx() } * depthProgress
            renderEffect = if (blurPx > 0.01f) {
                BlurEffect(blurPx, blurPx, TileMode.Clamp)
            } else {
                null
            }
        },
        content = content
    )
}

@Composable
private fun rootTopBarLayoutHeight(screen: Screen): Dp {
    return when (screen) {
        Screen.Home -> rememberHomeAdaptiveMetrics().topOverlayHeight
        Screen.Config -> detailTopOverlayHeight()
    }
}

@Composable
internal fun detailTopOverlayHeight(): Dp {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    return statusTop + DetailTopBarHeight + DetailTopOverlayExtra
}

@Composable
private fun rootTopGradientHeight(screen: Screen): Dp {
    return when (screen) {
        Screen.Home -> rememberHomeAdaptiveMetrics().topGradientHeight
        Screen.Config -> detailTopOverlayHeight()
    }
}

@Composable
internal fun detailContentTopPadding(): Dp {
    LocalGlassSettingsContentTopPadding.current?.let { return it }
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    return statusTop + DetailTopBarHeight + DetailContentTopGap
}

@Composable
fun CenterLiquidDialog(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    size: LiquidDialogSize = LiquidDialogSize.Standard,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidDialogSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        size = size
    ) {
        Column(
            modifier = if (size == LiquidDialogSize.Standard) {
                Modifier.fillMaxSize()
            } else {
                // Let compact content determine the real LiquidPanel height. fillMaxSize forced
                // it back to the 600dp maximum and merely moved the action into the middle.
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun DetailActivityScaffold(
    title: String,
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    showTopGradientBlur: Boolean = true,
    isolateContentFromBackdrop: Boolean = false,
    compactTopBar: Boolean = false,
    centerCompactTitle: Boolean = false,
    topBarVisible: Boolean = true,
    content: @Composable (Backdrop?) -> Unit
) {
    GlassMiuixDetailActivityScaffold(
        title = title,
        config = config,
        onBack = onBack,
        showTopGradientBlur = showTopGradientBlur,
        isolateContentFromBackdrop = isolateContentFromBackdrop,
        compactTopBar = compactTopBar,
        centerCompactTitle = centerCompactTitle,
        topBarVisible = topBarVisible,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onBack: () -> Unit,
    centerTitle: Boolean = false,
    showBackButton: Boolean = true
) {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + DetailTopBarHeight)
    ) {
        if (centerTitle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DetailTopBarHeight)
                    .align(Alignment.BottomCenter)
            ) {
                if (showBackButton) {
                    TopBackButton(
                        backdrop = backdrop,
                        config = config,
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(42.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = MaterialTheme.typography.titleLarge.fontSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp)
                )
            }
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                TopBackButton(backdrop = backdrop, config = config, onClick = onBack, modifier = Modifier.size(42.dp))
            }
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = MaterialTheme.typography.titleLarge.fontSize,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun settingsPageBackground(config: ScheduleConfigEntity): ComposeColor {
    return if (appUsesDarkTheme(config)) ComposeColor.Black else ComposeColor(0xFFF2F2F7)
}

@Composable
fun settingsVisualConfig(config: ScheduleConfigEntity): ScheduleConfigEntity {
    return config.copy(
        wallpaperUri = null,
        wallpaperBrightness = 1f,
        homeTextLight = appUsesDarkTheme(config),
        defaultWallpaperStyle = DefaultWallpaperStyle.NONE
    )
}

@Composable
fun HomeTopGradientBlur(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    height: Dp = HomeTopOverlayHeight
) {
    val lightGlass = glassUsesLightStyle(config)
    val tintColor = if (lightGlass) HomeLightGlassGradientColor else ComposeColor(0xFF111111)
    ProgressiveBackdropBlur(
        backdrop = backdrop,
        modifier = modifier,
        tintColor = tintColor,
        height = height,
        blurRadius = 18.dp,
        tintIntensity = if (lightGlass) 0.15f else 0.18f,
        direction = ProgressiveBlurDirection.TopToBottom,
        fallbackTintStops = listOf(
            0f to tintColor.copy(alpha = if (lightGlass) 0.34f else 0.42f),
            0.42f to tintColor.copy(alpha = if (lightGlass) 0.14f else 0.18f),
            1f to ComposeColor.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    screen: Screen,
    state: AppState,
    settingsPage: SettingsPage,
    backdrop: Backdrop?,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    homeDisplayDate: LocalDate,
    homeDisplayWeek: Int,
    beforeScheduleTerm: Boolean,
    afterScheduleTerm: Boolean,
    homeShowingAnotherWeek: Boolean,
    onReturnHomeToCurrentWeek: () -> Unit,
    activeHomeOverlay: HomeAnchoredOverlayKind?,
    onAddButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onPersonalizeButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onToggleAddMenu: () -> Unit,
    onTogglePersonalize: () -> Unit,
    onBackHome: () -> Unit
) {
    val adaptiveTopBarColor = LocalAdaptiveGlass.current.contentColor
    val homeTextColor = adaptiveTopBarColor
    val view = LocalView.current
    TopAppBar(
        modifier = Modifier.statusBarsPadding().height(66.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ComposeColor.Transparent,
            scrolledContainerColor = ComposeColor.Transparent,
            titleContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground
        ),
        title = {
            if (screen is Screen.Home) {
                HomeDateTitle(
                    state = state,
                    displayDate = homeDisplayDate,
                    displayWeek = homeDisplayWeek,
                    beforeScheduleTerm = beforeScheduleTerm,
                    afterScheduleTerm = afterScheduleTerm,
                    showReturnToCurrentWeekHint = homeShowingAnotherWeek,
                    onReturnCurrent = onReturnHomeToCurrentWeek
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        when (screen) {
                            Screen.Home -> ""
                            Screen.Config -> when (settingsPage) {
                                SettingsPage.Root -> "设置"
                                SettingsPage.General -> "通用设置"
                                SettingsPage.Widgets -> "小组件设置"
                                SettingsPage.AiImport -> "AI 设置"
                                SettingsPage.DayAgent -> "今日助手"
                                SettingsPage.Schedule -> "课表详细设置"
                                SettingsPage.Notifications -> "通知设置"
                                SettingsPage.ScheduleManager -> "课表设置"
                                SettingsPage.About -> "关于"
                                SettingsPage.Changelog -> "更新日志"
                                SettingsPage.Download -> "下载新版"
                                SettingsPage.Donate -> "捐赠支持"
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp
                    )
                }
            }
        },
        navigationIcon = {
            if (screen is Screen.Config && settingsPage != SettingsPage.Root) {
                TopBackButton(
                    backdrop = backdrop,
                    config = state.config,
                    onClick = onBackHome,
                    modifier = Modifier.padding(start = 8.dp).size(42.dp)
                )
            }
        },
        actions = {
            if (screen is Screen.Home) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeIconButton(
                        backdrop,
                        state.config,
                        R.drawable.ic_tune,
                        "个性化",
                        selected = activeHomeOverlay == HomeAnchoredOverlayKind.Personalize,
                        visible = activeHomeOverlay != HomeAnchoredOverlayKind.Personalize,
                        onClick = {
                            performButtonHaptic(view)
                            onTogglePersonalize()
                        },
                        onButtonPositioned = onPersonalizeButtonPositioned
                    )
                    HomeIconButton(
                        backdrop = backdrop,
                        config = state.config,
                        iconRes = R.drawable.ic_add_course,
                        contentDescription = "添加",
                        selected = activeHomeOverlay == HomeAnchoredOverlayKind.Add,
                        accentColor = if (glassUsesLightStyle(state.config)) {
                            HomeLightGlassAccentColor
                        } else {
                            ComposeColor(0xFF0A84FF)
                        },
                        visible = activeHomeOverlay != HomeAnchoredOverlayKind.Add,
                        onClick = {
                            performButtonHaptic(view)
                            onToggleAddMenu()
                        },
                        onButtonPositioned = onAddButtonPositioned
                    )
                    HomeModeSwitch(backdrop, state.config, homeMode, onHomeModeChange)
                }
            }
        }
    )
}

private fun popEnterTransition(): EnterTransition =
    fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)) +
        scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f))

private fun popExitTransition(): ExitTransition =
    fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 560f)) +
        scaleOut(targetScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 560f))

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun CourseBoundsSource(
    courseId: Long,
    visible: Boolean,
    sharedScope: SharedTransitionScope?,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    content(
        modifier.graphicsLayer {
            alpha = if (visible) 1f else 0f
        }
    )
}

@Composable
fun TopBackButton(backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lightGlass = glassUsesLightStyle(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.26f) else ComposeColor(0xFF121212).copy(alpha = 0.28f),
            contentPadding = PaddingValues(0.dp),
            blurRadius = 3.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false,
            shadowEnabled = false
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier,
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun HomeIconButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    iconRes: Int,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: ComposeColor = ComposeColor.Unspecified,
    visible: Boolean = true,
    onClick: () -> Unit,
    onButtonPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .padding(end = 7.dp)
            .size(42.dp)
            .then(
                if (onButtonPositioned != null) {
                    Modifier.onGloballyPositioned { onButtonPositioned(it.boundsInRoot()) }
                } else {
                    Modifier
                }
            )
            .graphicsLayer { alpha = if (visible) 1f else 0f }
    ) {
        HomeIconButtonVisual(
            backdrop = backdrop,
            config = config,
            iconRes = iconRes,
            contentDescription = contentDescription,
            selected = selected,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
            isInteractive = visible,
            onClick = onClick
        )
    }
}

@Composable
internal fun HomeIconButtonVisual(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: ComposeColor = ComposeColor.Unspecified,
    isInteractive: Boolean = true,
    onClick: () -> Unit = {}
) {
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = adaptiveGlass.lightGlass
    val baseSurfaceColor = if (lightGlass) HomeLightGlassSurfaceColor else ComposeColor(0xFF121212)
    val buttonSurfaceColor = if (accentColor.isSpecified) {
        accentColor.copy(alpha = if (lightGlass) HomeLightGlassAccentTintAlpha else 0.32f)
    } else {
        baseSurfaceColor.copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass))
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = if (isInteractive) onClick else ({}),
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            tint = if (accentColor.isSpecified) accentColor else ComposeColor.Unspecified,
            surfaceColor = buttonSurfaceColor,
            contentPadding = PaddingValues(0.dp),
            blurRadius = HomeHeaderGlassBlur,
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false,
            shadowEnabled = false
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = adaptiveGlass.contentColor
                )
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier,
            selected = selected,
            onClick = if (isInteractive) onClick else ({})
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
            }
        }
    }
}

data class AddMenuAction(
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun AddMenuLiquidItem(
    config: ScheduleConfigEntity,
    action: AddMenuAction,
    highlighted: Boolean,
    itemHeight: Dp
) {
    val baseText = glassForegroundColor(config)
    val accentColor = if (glassUsesLightStyle(config)) HomeLightGlassSelectedAccentColor else ComposeColor(0xFF0A84FF)
    val textColor = if (highlighted) accentColor else baseText
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight - 6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (highlighted) accentColor.copy(alpha = 0.10f) else ComposeColor.Transparent)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.width(150.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(action.iconRes), contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    action.label,
                    modifier = Modifier.width(94.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingDock(selected: Screen, backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier, onHome: () -> Unit, onConfig: () -> Unit) {
    val lightGlass = LocalAdaptiveGlass.current.lightGlass
    val density = LocalDensity.current
    // MIUI reports the IME-sized bottom edge through safeDrawing for the underlying Activity
    // while a transparent Compose Dialog owns the keyboard. That makes a bottom-aligned Dock jump
    // above the keyboard even though the Activity itself uses adjustNothing. Use the stable
    // navigation-bar inset as the physical screen edge; the separate IME compensation still
    // handles devices that genuinely resize the Activity window.
    val systemBottomPx = WindowInsets.navigationBarsIgnoringVisibility
        .only(WindowInsetsSides.Bottom)
        .getBottom(density)
    val bottomInset = with(density) { systemBottomPx.toDp() }
    val imeCompensationPx = dockImeCompensationPx(
        imeBottomPx = WindowInsets.ime.getBottom(density),
        systemBottomPx = systemBottomPx
    )
    val bottomOffset = (bottomInset + 8.dp).coerceAtLeast(8.dp)
    val dockTextColor = LocalAdaptiveGlass.current.contentColor
    val dockAlignment = when (config.dockAlignment) {
        DockAlignment.LEFT -> Alignment.BottomStart
        DockAlignment.CENTER -> Alignment.BottomCenter
        DockAlignment.RIGHT -> Alignment.BottomEnd
    }
    val horizontalPadding = when (config.dockAlignment) {
        DockAlignment.LEFT -> PaddingValues(start = 18.dp, bottom = bottomOffset)
        DockAlignment.CENTER -> PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomOffset)
        DockAlignment.RIGHT -> PaddingValues(end = 18.dp, bottom = bottomOffset)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            // MainActivity keeps adjustResize for text fields. Counter only the IME portion
            // here so the home dock stays at its physical bottom position behind the keyboard.
            .graphicsLayer { translationY = imeCompensationPx.toFloat() },
        contentAlignment = dockAlignment
    ) {
        if (backdrop != null) {
            CompositionLocalProvider(LocalContentColor provides dockTextColor) {
                LiquidBottomTabs(
                    selectedTabIndex = { if (selected is Screen.Home) 0 else 1 },
                    onTabSelected = { index -> if (index == 0) onHome() else onConfig() },
                    backdrop = backdrop,
                    tabsCount = 2,
                    modifier = Modifier.width(140.dp),
                    containerHeight = 54.dp,
                    indicatorHeight = 46.dp,
                    blurRadius = 1.3.dp,
                    containerAlpha = homeChromeGlassSurfaceAlpha(lightGlass),
                    lensHeight = 10.dp,
                    lensAmount = 40.dp,
                    indicatorWidthOverflow = 8.dp,
                    indicatorHeightOverflow = 4.dp,
                    indicatorLensHeight = 12.dp,
                    indicatorLensAmount = 17.dp,
                    officialHighlightAlpha = 0.07f,
                    officialShadowAlpha = 0.05f,
                    officialInnerShadowAlpha = 0.08f,
                    chromaticAberrationEnabled = true,
                    isLightThemeOverride = lightGlass,
                    lightContainerColor = HomeLightGlassSurfaceColor,
                    lightAccentColor = HomeLightGlassSelectedAccentColor,
                    useOfficialGlassParameters = true
                ) {
                    LiquidBottomTab(onClick = onHome) {
                        DockTabContent(R.drawable.ic_courses, "课程", iconSize = 23.dp)
                    }
                    LiquidBottomTab(onClick = onConfig) {
                        DockTabContent(R.drawable.ic_settings, "设置", iconSize = 24.dp)
                    }
                }
            }
        } else {
            GlassPill(backdrop = null, config = config, modifier = Modifier.width(140.dp)) {
                Row(modifier = Modifier.height(54.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DockItem(selected is Screen.Home, null, config, R.drawable.ic_courses, "课程", onHome)
                    DockItem(selected is Screen.Config, null, config, R.drawable.ic_settings, "设置", onConfig)
                }
            }
        }
    }
}

@Composable
fun ScheduleManagerEntryPill(
    visible: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(5_000)
            onDismiss()
        }
    }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
    val dockBottomOffset = (bottomInset + 8.dp).coerceAtLeast(8.dp)
    val pillBottomOffset = dockBottomOffset + 68.dp
    val dockAlignment = when (config.dockAlignment) {
        DockAlignment.LEFT -> Alignment.BottomStart
        DockAlignment.CENTER -> Alignment.BottomCenter
        DockAlignment.RIGHT -> Alignment.BottomEnd
    }
    val horizontalPadding = when (config.dockAlignment) {
        DockAlignment.LEFT -> PaddingValues(start = 18.dp, bottom = pillBottomOffset)
        DockAlignment.CENTER -> PaddingValues(start = 18.dp, end = 18.dp, bottom = pillBottomOffset)
        DockAlignment.RIGHT -> PaddingValues(end = 18.dp, bottom = pillBottomOffset)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalPadding),
        contentAlignment = dockAlignment
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(320)) +
                scaleIn(initialScale = 0.4f, animationSpec = tween(320, easing = CubicBezierEasing(0.2f, 0.75f, 0.2f, 1f))),
            exit = fadeOut(animationSpec = tween(120)) +
                scaleOut(targetScale = 0.4f, animationSpec = tween(120))
        ) {
            val lightGlass = glassUsesLightStyle(config)
            if (backdrop != null) {
                LiquidButton(
                    onClick = onClick,
                    backdrop = backdrop,
                    modifier = Modifier.width(140.dp),
                    height = 48.dp,
                    blurRadius = 2.dp,
                    lensHeight = 12.dp,
                    lensAmount = 24.dp,
                    surfaceColor = if (lightGlass) HomeLightGlassSurfaceColor.copy(alpha = 0.075f) else ComposeColor(0xFF121212).copy(alpha = 0.075f),
                    chromaticAberration = false,
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    Text(
                        "课表设置",
                        color = glassForegroundColor(config),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            } else {
                GlassPill(
                    backdrop = null,
                    config = config,
                    modifier = Modifier
                        .width(140.dp)
                        .height(48.dp),
                    onClick = onClick
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "课表设置",
                            color = glassForegroundColor(config),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DockBackdropContinuityPatch(config: ScheduleConfigEntity, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
    val height = (bottomInset + 92.dp).coerceAtLeast(92.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ComposeColor.Transparent)
    )
}

@Composable
private fun DockTabContent(iconRes: Int, label: String, iconSize: Dp) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(ComposeColor.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private const val PersonalizeWallpaperBlurSlider = "wallpaper-blur"
private const val PersonalizeWallpaperBrightnessSlider = "wallpaper-brightness"
private const val PersonalizeWeekHeightSlider = "week-height"
private const val PersonalizeCardAlphaSlider = "card-alpha"
private const val PersonalizeCardBlurSlider = "card-blur"
private const val PersonalizeCardFontSlider = "card-font"

private fun Modifier.personalizePreviewVisibility(
    activeKey: String?,
    previewProgress: Float,
    ownKey: String? = null
): Modifier = graphicsLayer {
    val selectedSlider = activeKey != null && activeKey == ownKey
    alpha = if (selectedSlider) 1f else 1f - previewProgress.coerceIn(0f, 1f)
}

@Composable
private fun PersonalizeSliderLabel(
    value: State<Float>,
    label: (Float) -> String
) {
    Text(label(value.value), style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun PersonalizeValueSlider(
    sliderKey: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    label: (Float) -> String,
    onCommit: (Float) -> Unit,
    onPreviewValueChange: (Float) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit,
    snapValue: Float? = null,
    onTouchActiveChange: (Boolean) -> Unit = {}
) {
    val displayValue = remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    LaunchedEffect(value, valueRange) {
        displayValue.floatValue = value.coerceIn(valueRange)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .personalizePreviewVisibility(previewSliderKey, previewProgress, sliderKey),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PersonalizeSliderLabel(displayValue, label)
        LiquidControlSlider(
            value = value,
            onValueChange = onCommit,
            valueRange = valueRange,
            backdrop = backdrop,
            onLiveValueChange = {
                displayValue.floatValue = it
                onPreviewValueChange(it)
            },
            snapValue = snapValue,
            onSliderTouchActiveChange = { active ->
                onTouchActiveChange(active)
                onSliderPreviewActiveChange(sliderKey, active)
            }
        )
    }
}

@Composable
private fun WallpaperBlurControl(
    blurDp: Float,
    backdrop: Backdrop?,
    onCommit: (Float) -> Unit,
    onPreviewValueChange: (Float) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit,
    onTouchActiveChange: (Boolean) -> Unit
) {
    PersonalizeValueSlider(
        sliderKey = PersonalizeWallpaperBlurSlider,
        value = wallpaperBlurPercent(blurDp),
        valueRange = 0f..100f,
        backdrop = backdrop,
        label = { "壁纸模糊 ${it.roundToInt()}%" },
        onCommit = onCommit,
        onPreviewValueChange = onPreviewValueChange,
        previewSliderKey = previewSliderKey,
        previewProgress = previewProgress,
        onSliderPreviewActiveChange = onSliderPreviewActiveChange,
        snapValue = 0f,
        onTouchActiveChange = onTouchActiveChange
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizePanel(
    modifier: Modifier = Modifier,
    drawSurface: Boolean = true,
    state: AppState,
    backdrop: Backdrop?,
    mode: HomeMode,
    weekCardHeight: Float,
    onWeekCardHeight: (Float) -> Unit,
    onWeekCardHeightPreview: (Float) -> Unit,
    onPickWallpaper: () -> Unit,
    onSampleWallpaperColor: () -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onPreviewConfig: (ScheduleConfigEntity) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit
) {
    val adaptiveHeight = if (state.periods.size >= 10) 72f else 80f
    var sliderTouchActive by remember { mutableStateOf(false) }
    @Composable
    fun PersonalizeSection(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    ComposeColor.Black.copy(
                        alpha = 0.10f * (1f - previewProgress.coerceIn(0f, 1f))
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
    @Composable
    fun PanelContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonalizeSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("首页壁纸", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiquidMenuButton(
                            backdrop,
                            "\u9009\u62E9\u58C1\u7EB8",
                            onClick = onPickWallpaper,
                            textColorOverride = ComposeColor.White,
                            surfaceColorOverride = ComposeColor(0xFF0A84FF).copy(alpha = 0.72f)
                        )
                        if (state.config.wallpaperUri != null) {
                            LiquidMenuButton(
                                backdrop,
                                "\u6E05\u9664",
                                onClick = {
                                    onUpdateConfig(
                                        state.config.copy(
                                            wallpaperUri = null,
                                            wallpaperPortraitCenterX = 0.5f,
                                            wallpaperPortraitCenterY = 0.5f,
                                            wallpaperPortraitScale = 1f,
                                            wallpaperLandscapeCenterX = 0.5f,
                                            wallpaperLandscapeCenterY = 0.5f,
                                            wallpaperLandscapeScale = 1f,
                                            wallpaperSourceWidth = null,
                                            wallpaperSourceHeight = null,
                                            homeTextLight = false
                                        )
                                    )
                                },
                                textColorOverride = ComposeColor.White,
                                surfaceColorOverride = ComposeColor(0xFFFF453A).copy(alpha = 0.66f)
                            )
                        }
                    }
                }
                WallpaperBlurControl(
                    blurDp = state.config.wallpaperBlur,
                    backdrop = backdrop,
                    onCommit = { percent ->
                        onUpdateConfig(state.config.copy(wallpaperBlur = wallpaperBlurDp(percent)))
                    },
                    onPreviewValueChange = { percent ->
                        onPreviewConfig(state.config.copy(wallpaperBlur = wallpaperBlurDp(percent)))
                    },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    onTouchActiveChange = { sliderTouchActive = it }
                )
                PersonalizeValueSlider(
                    sliderKey = PersonalizeWallpaperBrightnessSlider,
                    value = state.config.wallpaperBrightness.coerceIn(0.35f, 1f),
                    valueRange = 0.35f..1f,
                    backdrop = backdrop,
                    label = { "壁纸亮度 ${(it * 100).toInt()}%" },
                    onCommit = { onUpdateConfig(state.config.copy(wallpaperBrightness = it)) },
                    onPreviewValueChange = { onPreviewConfig(state.config.copy(wallpaperBrightness = it)) },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 1f,
                    onTouchActiveChange = { sliderTouchActive = it }
                )
            }
            PersonalizeSection {
                if (mode == HomeMode.Week) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("周视图行高", style = MaterialTheme.typography.labelLarge)
                            Text("${weekCardHeight.toInt()}dp", style = MaterialTheme.typography.labelMedium)
                        }
                        LiquidMenuButton(
                            backdrop,
                            "\u81EA\u9002\u5E94",
                            onClick = { onWeekCardHeight(adaptiveHeight) },
                            textColorOverride = ComposeColor.White,
                            surfaceColorOverride = ComposeColor(0xFF0A84FF).copy(alpha = 0.72f)
                        )
                    }
                    Box(
                        modifier = Modifier.personalizePreviewVisibility(
                            previewSliderKey,
                            previewProgress,
                            PersonalizeWeekHeightSlider
                        )
                    ) {
                        LiquidControlSlider(
                            value = weekCardHeight,
                            onValueChange = onWeekCardHeight,
                            valueRange = 38f..80f,
                            backdrop = backdrop,
                            onLiveValueChange = onWeekCardHeightPreview,
                            snapValue = adaptiveHeight.coerceIn(38f, 80f),
                            onSliderTouchActiveChange = { active ->
                                sliderTouchActive = active
                                onSliderPreviewActiveChange(PersonalizeWeekHeightSlider, active)
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("课程卡片颜色", style = MaterialTheme.typography.labelLarge)
                    LiquidMenuButton(
                        backdrop,
                        "从壁纸取色",
                        onClick = onSampleWallpaperColor,
                        textColorOverride = ComposeColor.White,
                        surfaceColorOverride = ComposeColor(0xFF0A84FF).copy(alpha = 0.72f)
                    )
                }
                val courseCardPalette = listOf(
                    0xFFD6E9FF, 0xFFBFE0FF, 0xFF9ED4FF, 0xFFFFE1E8,
                    0xFFFFC4D6, 0xFFD8F3DC, 0xFFB7E4C7, 0xFFFFF0C2,
                    0xFFFFD166, 0xFFE8D7FF, 0xFFD7C0FF,
                    MulticolorCourseCardArgb
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .personalizePreviewVisibility(previewSliderKey, previewProgress)
                ) {
                    // Always balance the palette into exactly two rows. The count is
                    // derived from the current palette while SpaceBetween consumes the
                    // actual panel width, avoiding FlowRow's asymmetric 7 + 5 wrapping.
                    val firstRowCount = (courseCardPalette.size + 1) / 2
                    val paletteRows = listOf(
                        courseCardPalette.take(firstRowCount),
                        courseCardPalette.drop(firstRowCount)
                    )
                    val largestRow = paletteRows.maxOf { it.size }.coerceAtLeast(1)
                    val minimumGap = 6.dp
                    val responsiveSwatchSize = minOf(
                        34.dp,
                        ((maxWidth - minimumGap * (largestRow - 1)) / largestRow).coerceAtLeast(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        paletteRows.forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (rowColors.size == 1) {
                                    Arrangement.Center
                                } else {
                                    Arrangement.SpaceBetween
                                }
                            ) {
                                rowColors.forEach { color ->
                                    val selected = state.config.cardColorArgb == color
                                    val multicolor = color == MulticolorCourseCardArgb
                                    Surface(
                                        modifier = Modifier.size(responsiveSwatchSize),
                                        shape = RoundedCornerShape(50),
                                        color = if (multicolor) ComposeColor.Transparent else ComposeColor(color.toInt()),
                                        border = BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                                        ),
                                        onClick = { onUpdateConfig(state.config.copy(cardColorArgb = color)) }
                                    ) {
                                        if (multicolor) {
                                            Box(
                                                modifier = Modifier.background(
                                                    Brush.sweepGradient(
                                                        listOf(
                                                            ComposeColor(0xFFFF453A),
                                                            ComposeColor(0xFFFFD60A),
                                                            ComposeColor(0xFF30D158),
                                                            ComposeColor(0xFF64D2FF),
                                                            ComposeColor(0xFF0A84FF),
                                                            ComposeColor(0xFFBF5AF2),
                                                            ComposeColor(0xFFFF453A)
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val alphaLabel = if (state.config.courseCardGlassEnabled) "课程卡片着色强度" else "课程卡片不透明度"
                PersonalizeValueSlider(
                    sliderKey = PersonalizeCardAlphaSlider,
                    value = state.config.cardAlpha.coerceIn(0f, 1f),
                    valueRange = 0f..1f,
                    backdrop = backdrop,
                    label = { "$alphaLabel ${(it * 100).toInt()}%" },
                    onCommit = { onUpdateConfig(state.config.copy(cardAlpha = it)) },
                    onPreviewValueChange = { onPreviewConfig(state.config.copy(cardAlpha = it)) },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 0.5f,
                    onTouchActiveChange = { sliderTouchActive = it }
                )
                PersonalizeValueSlider(
                    sliderKey = PersonalizeCardBlurSlider,
                    value = state.config.courseCardBlur.coerceIn(0f, 10f) / 10f * 100f,
                    valueRange = 0f..100f,
                    backdrop = backdrop,
                    label = { "课程卡片模糊 ${it.toInt()}%" },
                    onCommit = { onUpdateConfig(state.config.copy(courseCardBlur = it / 100f * 10f)) },
                    onPreviewValueChange = { onPreviewConfig(state.config.copy(courseCardBlur = it / 100f * 10f)) },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 35f,
                    onTouchActiveChange = { sliderTouchActive = it }
                )
                PersonalizeValueSlider(
                    sliderKey = PersonalizeCardFontSlider,
                    value = state.config.courseCardFontScale,
                    valueRange = 0.80f..1.35f,
                    backdrop = backdrop,
                    label = { "课程卡片字体 ${(it * 100).toInt()}%" },
                    onCommit = { onUpdateConfig(state.config.copy(courseCardFontScale = it)) },
                    onPreviewValueChange = { onPreviewConfig(state.config.copy(courseCardFontScale = it)) },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 1f,
                    onTouchActiveChange = { sliderTouchActive = it }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("课程卡片液态玻璃", style = MaterialTheme.typography.labelLarge)
                    LiquidControlToggle(
                        checked = state.config.courseCardGlassEnabled,
                        onCheckedChange = { onUpdateConfig(state.config.copy(courseCardGlassEnabled = it)) },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
    val scrollState = rememberScrollState()
    val contentModifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState, enabled = !sliderTouchActive)
    val panelTextColor = personalizePanelForegroundColor(state.config)
    if (!drawSurface) {
        Box(modifier = modifier) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    } else if (backdrop != null) {
        val lightGlass = glassUsesLightStyle(state.config)
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.18f) else ComposeColor(0xFF121212).copy(alpha = 0.30f)
        ) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    } else {
        GlassDialogSurface(
            backdrop = null,
            config = state.config,
            modifier = modifier
        ) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    }
}

@Composable
private fun personalizePanelForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return homeForegroundColor(config)
}

@Composable
fun LiquidMenuButton(
    backdrop: Backdrop?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    textColorOverride: ComposeColor? = null,
    surfaceColorOverride: ComposeColor? = null
) {
    val textColor = textColorOverride ?: if (destructive) ComposeColor(0xFFFF453A) else MaterialTheme.colorScheme.primary
    val surfaceColor = surfaceColorOverride ?: if (destructive) ComposeColor(0xFFFF453A).copy(alpha = 0.16f) else ComposeColor.White.copy(alpha = 0.10f)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 40.dp,
            surfaceColor = surfaceColor,
            contentPadding = PaddingValues(horizontal = 14.dp),
            blurRadius = 8.dp,
            lensHeight = 24.dp,
            lensAmount = 28.dp,
            chromaticAberration = false
        ) {
            Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    } else {
        Text(
            label,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.14f)))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false
        )
    }
}

enum class DialogButtonRole { Neutral, Confirm, Cancel }

@Composable
fun DialogLiquidButton(
    backdrop: Backdrop?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: DialogButtonRole = DialogButtonRole.Neutral,
    iconRes: Int? = null,
    blurRadius: Dp = 3.dp,
    destructiveFilled: Boolean = false,
    monochromeNeutral: Boolean = false,
    roundIcon: Boolean = (role == DialogButtonRole.Cancel && label == "取消") ||
        (role == DialogButtonRole.Confirm && label == "保存")
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useMonochromeNeutral = role == DialogButtonRole.Neutral && monochromeNeutral
    val useRoundIcon = roundIcon && role != DialogButtonRole.Neutral
    val resolvedIconRes = iconRes ?: when {
        useRoundIcon && role == DialogButtonRole.Cancel -> R.drawable.ic_close_light
        useRoundIcon && role == DialogButtonRole.Confirm -> R.drawable.ic_check
        else -> null
    }
    val textColor = when (role) {
        DialogButtonRole.Confirm -> ComposeColor.White
        DialogButtonRole.Cancel -> if (useRoundIcon || destructiveFilled) ComposeColor.White else ComposeColor(0xFFFF453A)
        DialogButtonRole.Neutral -> if (useMonochromeNeutral) {
            if (darkTheme) ComposeColor.White else ComposeColor.Black
        } else MaterialTheme.colorScheme.primary
    }
    val surfaceColor = when (role) {
        DialogButtonRole.Confirm -> ComposeColor(0xFF0A84FF).copy(alpha = 0.82f)
        DialogButtonRole.Cancel -> ComposeColor(0xFFFF453A).copy(alpha = if (useRoundIcon || destructiveFilled) 0.78f else 0.16f)
        DialogButtonRole.Neutral -> if (useMonochromeNeutral) {
            (if (darkTheme) ComposeColor.Black else ComposeColor.White)
                .copy(alpha = if (darkTheme) 0.46f else 0.62f)
        } else ComposeColor.Transparent
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = if (useRoundIcon) modifier.size(42.dp) else modifier,
            height = if (useRoundIcon) 42.dp else 40.dp,
            surfaceColor = surfaceColor,
            contentPadding = if (useRoundIcon) {
                PaddingValues(0.dp)
            } else {
                PaddingValues(horizontal = 15.dp)
            },
            blurRadius = blurRadius,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false
        ) {
            resolvedIconRes?.let {
                Icon(painterResource(it), contentDescription = label, modifier = Modifier.size(20.dp), tint = textColor)
            }
            if (!useRoundIcon) {
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    } else {
        Row(
            modifier = (if (useRoundIcon) modifier.size(42.dp) else modifier)
                .clip(RoundedCornerShape(50))
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(if (role == DialogButtonRole.Neutral) 0f else 0.16f)))
                .clickable(onClick = onClick)
                .then(
                    if (useRoundIcon) Modifier
                    else Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            resolvedIconRes?.let {
                Icon(painterResource(it), contentDescription = label, modifier = Modifier.size(20.dp), tint = textColor)
            }
            if (!useRoundIcon) {
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun DialogCapsuleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    cornerRadius: Dp? = null
) {
    val dark = appUsesDarkTheme(config)
    val fieldBase = if (dark) ComposeColor(0xFF2C2C2E) else ComposeColor.White
    val background = fieldBase.copy(alpha = if (dark) 0.54f else 0.70f)
    val textColor = readableOn(fieldBase)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (minLines == 1) ImeAction.Done else ImeAction.Default),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius ?: if (minLines == 1) 50.dp else 24.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = if (minLines == 1) 12.dp else 14.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        color = textColor.copy(alpha = 0.52f),
                        maxLines = if (minLines == 1) 1 else 2,
                        overflow = TextOverflow.Clip
                    )
                }
                innerTextField()
            }
        }
    )
}

class SettingsDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val customizeScheduleId = intent.getIntExtra(ScheduleCustomizeIdExtra, -1).takeIf { it > 0 }
        val useEntrySnapshot = intent.getBooleanExtra(ScheduleEntrySnapshotExtra, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val section = SettingsPage.valueOf(intent.getStringExtra(SettingsDetailPageExtra) ?: SettingsPage.General.name)
            val stateFlow = if (customizeScheduleId != null || section == SettingsPage.ScheduleManager) {
                viewModel.allSchedulesState
            } else {
                viewModel.state
            }
            val state by stateFlow.collectAsStateWithLifecycle()
            CourseScheduleTheme(config = state.config) {
                val scheduleEditState = remember(state, customizeScheduleId) {
                    if (customizeScheduleId != null) scheduleConfigStateForEdit(state, customizeScheduleId) else state
                }
                val scheduleEditReady = customizeScheduleId == null ||
                    state.allConfigs.any { it.id == customizeScheduleId }
                val editEntrySnapshot = remember(customizeScheduleId, useEntrySnapshot) {
                    customizeScheduleId?.takeIf { useEntrySnapshot }?.let {
                        BitmapFactory.decodeFile(ScheduleSnapshotStore.file(this, it).absolutePath)
                    }
                }
                var editEntrySnapshotVisible by remember(editEntrySnapshot) { mutableStateOf(editEntrySnapshot != null) }
                LaunchedEffect(editEntrySnapshot) {
                    if (editEntrySnapshot != null) {
                        withFrameNanos { }
                        withFrameNanos { }
                        editEntrySnapshotVisible = false
                    }
                }
                var scheduleExitRequest by remember { mutableIntStateOf(0) }
                var aiExitRequest by remember { mutableIntStateOf(0) }
                var widgetEditorVisible by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                DetailActivityScaffold(
                    title = section.title(),
                    config = state.config,
                    compactTopBar = section == SettingsPage.Widgets,
                    centerCompactTitle = section == SettingsPage.Widgets,
                    topBarVisible = !widgetEditorVisible,
                    onBack = {
                        if (section == SettingsPage.Schedule || section == SettingsPage.Notifications) {
                            scheduleExitRequest++
                        } else if (section == SettingsPage.AiImport) {
                            finish()
                        } else {
                            finish()
                        }
                    }
                ) { backdrop ->
                    when (section) {
                        SettingsPage.General -> GeneralSettingsScreen(state, backdrop, viewModel::savePersonalization)
                        SettingsPage.Widgets -> WidgetCustomizationScreen(
                            state = state,
                            backdrop = backdrop,
                            onEditorVisibilityChange = { widgetEditorVisible = it }
                        )
                        SettingsPage.AiImport -> AiImportSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            exitCommitRequest = aiExitRequest,
                            onExitCommitFinished = { saved -> if (saved) finish() }
                        )
                        SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
                        SettingsPage.Schedule -> if (!scheduleEditReady) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        } else {
                            ScheduleConfigScreen(
                                scheduleEditState,
                                backdrop,
                                SettingsSection.Schedule,
                                onSave = { config, periods ->
                                    val targetId = customizeScheduleId
                                    if (targetId != null) {
                                        viewModel.saveConfigForSchedule(targetId, config, periods)
                                    } else {
                                        viewModel.saveConfig(config, periods)
                                    }
                                },
                                onPreviewLiveUpdate = viewModel::previewLiveUpdate,
                                exitCommitRequest = scheduleExitRequest,
                                onExitCommitFinished = { saved -> if (saved) finish() }
                            )
                        }
                        SettingsPage.Notifications -> ScheduleConfigScreen(
                            state = state,
                            backdrop = backdrop,
                            section = SettingsSection.Notifications,
                            onSave = { config, _ -> viewModel.saveNotificationSettings(config) },
                            onPreviewLiveUpdate = viewModel::previewLiveUpdate,
                            exitCommitRequest = scheduleExitRequest,
                            onExitCommitFinished = { saved -> if (saved) finish() }
                        )
                        SettingsPage.About -> AboutSettingsScreen(state, backdrop)
                        SettingsPage.Changelog -> ChangelogSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onDownload = {
                                startActivity(
                                    Intent(this@SettingsDetailActivity, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Download.name)
                                )
                            },
                            onDonate = {
                                startActivity(
                                    Intent(this@SettingsDetailActivity, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Donate.name)
                                )
                            }
                        )
                        SettingsPage.Donate -> DonateSettingsScreen(state, backdrop)
                        SettingsPage.Download -> DownloadUpdateScreen(state, backdrop)
                        SettingsPage.ScheduleManager -> ScheduleManagerScreen(
                            state = state, backdrop = backdrop,
                            onCreateSchedule = { viewModel.createSchedule(it) },
                            onActivateSchedule = { id, finish -> viewModel.activateSchedule(id, finish) },
                            onRenameSchedule = { id, name -> viewModel.renameSchedule(id, name) },
                            onDeleteSchedule = { viewModel.deleteSchedule(it) }
                        )
                        SettingsPage.Root -> SettingsRootScreen(state, backdrop) {}
                    }
                }
                AnimatedVisibility(
                    visible = editEntrySnapshotVisible,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(220)),
                    modifier = Modifier.fillMaxSize().zIndex(200f)
                ) {
                    editEntrySnapshot?.let { snapshot ->
                        Image(
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
    }
}

private fun scheduleConfigStateForEdit(state: AppState, scheduleId: Int): AppState {
    val targetConfig = state.allConfigs.firstOrNull { it.id == scheduleId }
        ?: state.config.takeIf { it.id == scheduleId }
        ?: defaultConfig(scheduleId)
    val targetPeriods = state.allPeriods.filter { it.scheduleId == scheduleId }
        .ifEmpty { state.periods.takeIf { targetConfig.id == state.config.id } ?: defaultPeriods(scheduleId) }
    return state.copy(config = targetConfig.copy(id = scheduleId), periods = targetPeriods)
}

class EduSchoolSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            CourseScheduleTheme(config = state.config) {
                DetailActivityScaffold(
                    title = "选择学校",
                    config = state.config,
                    onBack = { finish() }
                ) { backdrop ->
                    EduSchoolPickerScreen(
                        state = state,
                        onSelect = { adapter ->
                            startActivity(
                                Intent(this, EduImportActivity::class.java)
                                    .putExtra(EduAdapterExtra, adapter.toIntentKey())
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class EduImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val adapter = remember { eduAdapterFromIntentKey(intent.getStringExtra(EduAdapterExtra)) }
            var pendingDraft by remember { mutableStateOf<ImportDraft?>(null) }
            CourseScheduleTheme(config = state.config) {
                if (pendingDraft == null) {
                    DetailActivityScaffold(
                        title = adapter?.school?.name ?: "教务导入",
                        config = state.config,
                        onBack = { finish() },
                        compactTopBar = true
                    ) { backdrop ->
                        if (adapter == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = detailContentTopPadding())
                            ) {
                                MissingCourseScreen(onBack = { finish() })
                            }
                        } else {
                            EduImportActivityScreen(
                                state = state,
                                adapter = adapter,
                                backdrop = backdrop,
                                useDetailTopPadding = true,
                                onParsed = { draft -> pendingDraft = draft }
                            )
                        }
                    }
                } else {
                    DetailActivityScaffold(
                        title = "导入预览",
                        config = state.config,
                        onBack = { finish() },
                        showTopGradientBlur = false,
                        isolateContentFromBackdrop = true
                    ) { backdrop ->
                    if (adapter == null) {
                        MissingCourseScreen(onBack = { finish() })
                    } else {
                        Box(modifier = Modifier.padding(top = detailContentTopPadding())) {
                            ConfirmScheduleScreen(
                                draft = pendingDraft!!,
                                warning = if (adapter.isGeneralEduTool()) "可能部分节次信息会有误，请自行检查修改。" else null,
                                onCancel = { pendingDraft = null },
                                onConfirm = { createNewSchedule ->
                                    viewModel.importDraft(pendingDraft!!, createNewSchedule) { scheduleId ->
                                        PendingImportSetupStore.put(this@EduImportActivity, scheduleId)
                                        finish()
                                    }
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

@Composable
fun LiquidControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onLiveValueChange: ((Float) -> Unit)? = null,
    snapValue: Float? = null,
    onSliderTouchActiveChange: (Boolean) -> Unit = {},
    visibilityThreshold: Float = 0.01f,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val safeSnapValue = snapValue?.coerceIn(valueRange)
    var localValue by remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    var previewModeActive by remember(valueRange) { mutableStateOf(false) }
    var lastSnapSide by remember(valueRange, safeSnapValue) {
        mutableIntStateOf(snapSide(value.coerceIn(valueRange), safeSnapValue, visibilityThreshold))
    }
    var snapZoneActive by remember(valueRange, safeSnapValue) {
        mutableStateOf(lastSnapSide == 0 && safeSnapValue != null)
    }

    fun updatePreview(candidate: Float) {
        val next = candidate.coerceIn(valueRange)
        val nextSide = snapSide(next, safeSnapValue, visibilityThreshold)
        val enteredSnapZone = safeSnapValue != null && nextSide == 0 && !snapZoneActive
        val crossedSnap = nextSide != 0 && !snapZoneActive &&
            lastSnapSide != 0 && nextSide != lastSnapSide
        if (enteredSnapZone || crossedSnap) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        snapZoneActive = safeSnapValue != null && nextSide == 0
        if (nextSide != 0) lastSnapSide = nextSide
        localValue = next
        onLiveValueChange?.invoke(next)
    }

    fun commit(candidate: Float) {
        val finalValue = candidate.coerceIn(valueRange)
        if (abs(localValue - finalValue) > visibilityThreshold) updatePreview(finalValue)
        onValueChange(finalValue)
    }

    LaunchedEffect(value, valueRange) {
        val externalValue = value.coerceIn(valueRange)
        if (!previewModeActive && abs(localValue - externalValue) > visibilityThreshold) {
            localValue = externalValue
            lastSnapSide = snapSide(externalValue, safeSnapValue, visibilityThreshold)
            snapZoneActive = safeSnapValue != null && lastSnapSide == 0
        }
    }

    SliderWithSnapMarker(
        modifier = modifier,
        snapValue = safeSnapValue,
        valueRange = valueRange
    ) {
        if (backdrop != null && enabled) {
            LiquidSlider(
                value = { localValue },
                onPreviewValueChange = ::updatePreview,
                onPreviewModeChange = { active ->
                    previewModeActive = active
                    onSliderTouchActiveChange(active)
                },
                onCommit = ::commit,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                backdrop = backdrop,
                snapValue = safeSnapValue,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Slider(
                value = localValue,
                enabled = enabled,
                onValueChange = {
                    if (!previewModeActive) {
                        previewModeActive = true
                        onSliderTouchActiveChange(true)
                    }
                    updatePreview(it)
                },
                onValueChangeFinished = {
                    commit(localValue)
                    if (previewModeActive) {
                        previewModeActive = false
                        onSliderTouchActiveChange(false)
                    }
                },
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun snapSide(candidate: Float, snapValue: Float?, threshold: Float): Int {
    val snap = snapValue ?: return 0
    return when {
        candidate < snap - threshold -> -1
        candidate > snap + threshold -> 1
        else -> 0
    }
}

@Composable
private fun SliderWithSnapMarker(
    modifier: Modifier,
    snapValue: Float?,
    valueRange: ClosedFloatingPointRange<Float>,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        content()
        val snap = snapValue ?: return@BoxWithConstraints
        val fraction = ((snap - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val trackWidth = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = ((trackWidth - 20.dp) * fraction) + 7.5.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ComposeColor.White.copy(alpha = 0.78f))
            )
        }
    }
}
@Composable
fun LiquidControlToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    if (backdrop != null) {
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop,
            modifier = modifier
        )
    } else {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
    }
}

@Composable
fun DockItem(selected: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, iconRes: Int, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(60.dp)
            .width(78.dp),
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
        Column(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: AppState,
    backdrop: Backdrop?,
    onPageChange: (SettingsPage) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onPreviewLiveUpdate: () -> Unit,
    onCreateSchedule: (String) -> Unit = {},
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit = { _, _ -> },
    onRenameSchedule: (Int, String) -> Unit = { _, _ -> },
    onDeleteSchedule: (Int) -> Unit = {}
) {
    val pageConfig = settingsVisualConfig(state.config)
    val pageState = state.copy(config = pageConfig)
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    GlassMiuixSettingsTheme(pageConfig) {
        if (page == SettingsPage.Root && adaptiveMetrics.isLargeScreen) {
            var selectedPageName by rememberSaveable { mutableStateOf(SettingsPage.General.name) }
            var tabletWidgetEditorVisible by remember { mutableStateOf(false) }
            val selectedPage = runCatching { SettingsPage.valueOf(selectedPageName) }
                .getOrDefault(SettingsPage.General)
                .takeUnless { it == SettingsPage.Root }
                ?: SettingsPage.General
            val portrait = adaptiveMetrics.screenHeight > adaptiveMetrics.screenWidth
            val navigationWidth = if (portrait) {
                (adaptiveMetrics.screenWidth * 0.38f).coerceIn(280.dp, 328.dp)
            } else {
                (adaptiveMetrics.screenWidth * 0.31f).coerceIn(336.dp, 408.dp)
            }
            LaunchedEffect(selectedPage) {
                if (selectedPage != SettingsPage.Widgets) tabletWidgetEditorVisible = false
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(settingsPageBackground(pageConfig))
            ) {
                Box(Modifier.width(navigationWidth).fillMaxHeight()) {
                    SettingsRootScreen(
                        state = pageState,
                        backdrop = backdrop,
                        selectedPage = selectedPage,
                        onPageChange = { selectedPageName = it.name }
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f))
                )
                GlassMiuixTabletDetailPaneScaffold(
                    title = selectedPage.title(),
                    config = pageConfig,
                    topBarVisible = !(selectedPage == SettingsPage.Widgets && tabletWidgetEditorVisible),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { paneBackdrop ->
                    SettingsPageContent(
                        page = selectedPage,
                        state = state,
                        pageState = pageState,
                        backdrop = paneBackdrop,
                        onPageChange = { selectedPageName = it.name },
                        onSave = onSave,
                        onUpdateConfig = onUpdateConfig,
                        onPreviewLiveUpdate = onPreviewLiveUpdate,
                        onCreateSchedule = onCreateSchedule,
                        onActivateSchedule = onActivateSchedule,
                        onRenameSchedule = onRenameSchedule,
                        onDeleteSchedule = onDeleteSchedule,
                        onWidgetEditorVisibilityChange = { tabletWidgetEditorVisible = it }
                    )
                }
            }
        } else {
            SettingsPageContent(
                page = page,
                state = state,
                pageState = pageState,
                backdrop = backdrop,
                onPageChange = onPageChange,
                onSave = onSave,
                onUpdateConfig = onUpdateConfig,
                onPreviewLiveUpdate = onPreviewLiveUpdate,
                onCreateSchedule = onCreateSchedule,
                onActivateSchedule = onActivateSchedule,
                onRenameSchedule = onRenameSchedule,
                onDeleteSchedule = onDeleteSchedule
            )
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    state: AppState,
    pageState: AppState,
    backdrop: Backdrop?,
    onPageChange: (SettingsPage) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onPreviewLiveUpdate: () -> Unit,
    onCreateSchedule: (String) -> Unit,
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit,
    onRenameSchedule: (Int, String) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onWidgetEditorVisibilityChange: (Boolean) -> Unit = {}
) {
    when (page) {
        SettingsPage.Root -> SettingsRootScreen(pageState, backdrop, onPageChange = onPageChange)
        SettingsPage.General -> GeneralSettingsScreen(state, backdrop, onUpdateConfig)
        SettingsPage.Widgets -> WidgetCustomizationScreen(
            state,
            backdrop,
            onEditorVisibilityChange = onWidgetEditorVisibilityChange
        )
        SettingsPage.AiImport -> AiImportSettingsScreen(state, backdrop)
        SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
        SettingsPage.Schedule -> ScheduleConfigScreen(state, backdrop, SettingsSection.Schedule, onSave, onPreviewLiveUpdate)
        SettingsPage.Notifications -> ScheduleConfigScreen(state, backdrop, SettingsSection.Notifications, onSave, onPreviewLiveUpdate)
        SettingsPage.ScheduleManager -> ScheduleManagerScreen(
            state,
            backdrop,
            onCreateSchedule,
            onActivateSchedule,
            onRenameSchedule,
            onDeleteSchedule
        )
        SettingsPage.About -> AboutSettingsScreen(pageState, backdrop)
        SettingsPage.Changelog -> ChangelogSettingsScreen(
            pageState,
            backdrop,
            onDownload = { onPageChange(SettingsPage.Download) },
            onDonate = { onPageChange(SettingsPage.Donate) }
        )
        SettingsPage.Download -> DownloadUpdateScreen(pageState, backdrop)
        SettingsPage.Donate -> DonateSettingsScreen(pageState, backdrop)
    }
}

@Composable
fun SettingsRootScreen(
    state: AppState,
    backdrop: Backdrop?,
    selectedPage: SettingsPage? = null,
    onPageChange: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    val appName = remember {
        runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault("SleepDown课程表")
    }

    val updateAvailable by GiteeAppUpdater.updateAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(versionName) {
        GiteeAppUpdater.restoreCachedStatus(context, versionName)
    }
    var updateDialog by remember { mutableStateOf<SettingsUpdateDialog?>(null) }
    var downloadedUpdate by remember { mutableStateOf<java.io.File?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedUpdate
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                .onFailure { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
        }
    }

    fun checkForUpdate() {
        if (updateDialog is SettingsUpdateDialog.Checking || updateDialog is SettingsUpdateDialog.Downloading) return
        updateDialog = SettingsUpdateDialog.Checking
        GiteeAppUpdater.markDailyCheckStarted(context)
        scope.launch {
            updateDialog = GiteeAppUpdater.checkForUpdate(versionName).fold(
                onSuccess = { result ->
                    GiteeAppUpdater.recordCheckResult(context, result)
                    when (result) {
                        is GiteeUpdateCheckResult.UpdateAvailable -> SettingsUpdateDialog.Available(result.release)
                        is GiteeUpdateCheckResult.UpToDate -> SettingsUpdateDialog.UpToDate(result.release.tagName)
                    }
                },
                onFailure = { SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }

    fun downloadAndInstall(release: GiteeReleaseInfo) {
        if (release.apkUrl == null) {
            updateDialog = SettingsUpdateDialog.NoApk(release)
            return
        }
        updateDialog = SettingsUpdateDialog.Downloading(release)
        scope.launch {
            GiteeAppUpdater.downloadApk(context, release).fold(
                onSuccess = { apk ->
                    downloadedUpdate = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                            .onSuccess { updateDialog = null }
                            .onFailure { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    } else {
                        updateDialog = SettingsUpdateDialog.InstallPermissionRequired
                    }
                },
                onFailure = { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }
    GlassMiuixRootSettingsScaffold(
        title = "设置",
        config = state.config
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = DockScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = appName,
                    summary = "开发者：小漫君",
                    startAction = {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    },
                    endActions = {
                        Text(
                            "版本 $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (selectedPage == SettingsPage.Changelog) 0.12f else 0f
                            )
                        ),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    onClick = { onPageChange(SettingsPage.Changelog) }
                )
                SettingsNavigationRow(
                    "检查更新",
                    when (updateDialog) {
                        SettingsUpdateDialog.Checking -> "正在检查 Gitee Release…"
                        is SettingsUpdateDialog.Downloading -> "正在下载 APK 安装包…"
                        else -> if (updateAvailable) "发现新版本，点击查看" else "从 Gitee 检查新版本"
                    },
                    badgeText = if (updateAvailable) "有新版" else null,
                    onClick = ::checkForUpdate
                )
            }
        }
        item {
            GlassPreferenceSection("应用") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "通用设置",
                        "深色模式与系统外观",
                        selected = selectedPage == SettingsPage.General,
                        onClick = { onPageChange(SettingsPage.General) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "小组件设置",
                        "自定义小组件背景",
                        selected = selectedPage == SettingsPage.Widgets,
                        onClick = { onPageChange(SettingsPage.Widgets) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "当前课表详细设置",
                        "编辑当前课表的周数、节次与显示规则",
                        selected = selectedPage == SettingsPage.Schedule,
                        onClick = { onPageChange(SettingsPage.Schedule) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "通知设置",
                        "上课提醒与实时活动",
                        selected = selectedPage == SettingsPage.Notifications,
                        onClick = { onPageChange(SettingsPage.Notifications) }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("智能助手") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "AI 设置",
                        "配置智能功能共用的服务商、模型和 API Key。",
                        selected = selectedPage == SettingsPage.AiImport,
                        onClick = { onPageChange(SettingsPage.AiImport) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "今日助手",
                        "管理日视图助手、天气与预警。",
                        selected = selectedPage == SettingsPage.DayAgent,
                        onClick = { onPageChange(SettingsPage.DayAgent) }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("其他") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "关于",
                        "软件信息与开源引用",
                        selected = selectedPage == SettingsPage.About,
                        onClick = { onPageChange(SettingsPage.About) }
                    )
                }
            }
        }
        }
    }

    SettingsUpdateDialogHost(
        dialog = updateDialog,
        backdrop = backdrop,
        config = state.config,
        onDismiss = { updateDialog = null },
        onRetry = ::checkForUpdate,
        onDownload = ::downloadAndInstall,
        onOpenRelease = { release ->
            context.startActivity(Intent(Intent.ACTION_VIEW, release.releaseUrl.toUri()))
        },
        onOpenBackup = { onPageChange(SettingsPage.Download); updateDialog = null },
        onRequestInstallPermission = {
            installPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
        }
    )
}

private sealed interface SettingsUpdateDialog {
    data object Checking : SettingsUpdateDialog
    data class Available(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class UpToDate(val latestTag: String) : SettingsUpdateDialog
    data class Downloading(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class NoApk(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class Error(val message: String) : SettingsUpdateDialog
    data object InstallPermissionRequired : SettingsUpdateDialog
}

@Composable
private fun SettingsUpdateDialogHost(
    dialog: SettingsUpdateDialog?,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit,
    onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    when (dialog) {
        null -> Unit
        SettingsUpdateDialog.Checking -> LiquidAlertDialog(
            title = "正在检查更新",
            message = "正在读取 Gitee 上最新的 SleepDown-Schedule Release。",
            actions = listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Available -> {
            val release = dialog.release
            val notes = release.notes.trim().ifBlank { "该版本没有填写更新说明。" }
            LiquidAlertDialog(
                title = "发现新版本 ${release.name}",
                message = "$notes\n\n当前将从 Gitee 下载 APK，安装前仍会由系统向你确认。",
                actions = listOf(
                    LiquidAlertAction("稍后", LiquidAlertActionStyle.Secondary, onClick = onDismiss),
                    LiquidAlertAction("下载并安装", LiquidAlertActionStyle.Primary) { onDownload(release) }
                ),
                backdrop = backdrop,
                config = config,
                onDismissRequest = onDismiss
            )
        }
        is SettingsUpdateDialog.UpToDate -> LiquidAlertDialog(
            title = "已是最新版本",
            message = "当前安装版本已不低于 Gitee 最新 Release（${dialog.latestTag}）。",
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary, onClick = onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Downloading -> LiquidAlertDialog(
            title = "正在下载 ${dialog.release.name}",
            message = "正在从 Gitee 下载 APK。现在可以返回或退到桌面，下载会在后台继续，并通过实时活动显示进度。",
            actions = listOf(
                LiquidAlertAction(
                    "后台下载",
                    LiquidAlertActionStyle.Secondary,
                    onClick = onDismiss
                )
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.NoApk -> LiquidAlertDialog(
            title = "Release 中没有 APK",
            message = "已找到 ${dialog.release.name}，但该 Release 没有附带 APK 安装包。可以查看发行版，或使用备用下载页。",
            actions = listOf(
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onClick = onOpenBackup),
                LiquidAlertAction("查看发行版", LiquidAlertActionStyle.Primary) { onOpenRelease(dialog.release) }
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Error -> LiquidAlertDialog(
            title = "检查更新失败",
            message = dialog.message,
            actions = listOf(
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onClick = onOpenBackup),
                LiquidAlertAction("重试", LiquidAlertActionStyle.Primary, onClick = onRetry)
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        SettingsUpdateDialog.InstallPermissionRequired -> LiquidAlertDialog(
            title = "允许安装更新",
            message = "Android 需要你先允许 SleepDown 安装来自 Gitee 的更新。授权返回后会继续打开系统安装确认页面。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onDismiss),
                LiquidAlertAction("去授权", LiquidAlertActionStyle.Primary, onClick = onRequestInstallPermission)
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
    }
}

private fun Throwable.readableUpdateMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "网络请求或安装包处理失败，请稍后重试。"

@Composable
fun ScheduleManagerScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCreateSchedule: (String) -> Unit,
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit,
    onRenameSchedule: (Int, String) -> Unit,
    onDeleteSchedule: (Int) -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val topPadding = detailContentTopPadding()
    val coursesPerSchedule = remember(state.allCourses) {
        state.allCourses.groupBy { it.scheduleId }.mapValues { it.value.size }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "课表设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                SettingsActionButton("新建课表", backdrop, onClick = { showCreateDialog = true })
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                state.schedules.forEachIndexed { idx, profile ->
                    val courseCount = coursesPerSchedule[profile.id] ?: 0
                    val isActive = profile.isActive
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                context.startActivity(
                                    Intent(context, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Schedule.name)
                                        .putExtra(ScheduleCustomizeIdExtra, profile.id)
                                )
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isActive) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "$courseCount 门课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isActive) {
                                SettingsActionButton("设为", backdrop, onClick = {
                                    onActivateSchedule(profile.id, null)
                                })
                            }
                            SettingsActionButton("重命名", backdrop, onClick = {
                                renameTarget = Pair(profile.id, profile.name)
                            })
                            if (!isActive && state.schedules.size > 1) {
                                SettingsActionButton("删除", backdrop, onClick = {
                                    deleteTarget = Pair(profile.id, profile.name)
                                }, destructive = true)
                            }
                        }
                    }
                    if (idx != state.schedules.lastIndex) SettingsDivider()
                }
            }
        }
    }

    if (showCreateDialog) {
        ScheduleNameDialog(
            title = "新建课表",
            initialName = "",
            backdrop = backdrop,
            config = state.config,
            onConfirm = { name ->
                showCreateDialog = false
                if (name.isNotBlank()) onCreateSchedule(name)
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { (id, name) ->
        ScheduleNameDialog(
            title = "重命名课表",
            initialName = name,
            backdrop = backdrop,


            config = state.config,
            onConfirm = { newName ->
                renameTarget = null
                if (newName.isNotBlank()) onRenameSchedule(id, newName)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { (id, name) ->
        LiquidAlertDialog(
            title = "删除课表",
            message = "确定要删除「$name」吗？该课表下的所有课程都会被删除，且无法恢复。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { deleteTarget = null },
                LiquidAlertAction("确认删除", LiquidAlertActionStyle.Destructive) {
                    val targetId = id
                    deleteTarget = null
                    onDeleteSchedule(targetId)
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { deleteTarget = null }
        )
    }
}

@Composable
fun ScheduleNameDialog(
    title: String,
    initialName: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val textColor = glassForegroundColor(config)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CenterLiquidDialog(backdrop = backdrop, config = config) {
            LiquidDialogHeader(title, onDismiss, backdrop, config, onConfirm = { onConfirm(name.trim()) })
            DialogCapsuleField(name, { name = it }, "课表名称", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun AboutSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    val topPadding = detailContentTopPadding()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0 beta" }.getOrDefault("1.0 beta")
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsValueRow("软件名称", "SleepDown课程表")
                SettingsDivider()
                SettingsValueRow("版本", versionName)
                SettingsDivider()
                SettingsValueRow("开发者", "小漫君")
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("软件介绍", "SleepDown课程表是一款面向学生的课程表工具，支持手动编辑、教务导入、课程提醒、实时活动和个性化玻璃外观。")
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsValueRow("AndroidLiquidGlass", "Kyant")
                SettingsDivider()
                SettingsValueRow("shiguang_warehouse", "XingHeYuZhuan")
                SettingsDivider()
                SettingsValueRow("MIUIX", "compose-miuix-ui")
            }
        }
    }
}

@Composable
fun ChangelogSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    onDownload: () -> Unit = {},
    onDonate: () -> Unit = {}
) {
    val topPadding = detailContentTopPadding()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsNavigationRow("下载新版", "打开蓝奏云备用下载页", onClick = onDownload)
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsNavigationRow("捐赠支持", "如果 SleepDown 课程表帮到了你，可以请小漫君喝杯奶茶。", onClick = onDonate)
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalCollapsibleSettingsInfoRows provides true) {
                SettingsInfoRow("1.1.1", "本次更新进一步缩小安装包，减少安装后的空间占用，并优化壁纸、桌面组件和助手历史等数据的存储，长期使用更省空间；修复部分用户从旧版本升级后闪退的问题；新增武汉科技大学教务导入适配；优化平板等大屏设备的界面布局与操作体验，并提升整体性能和稳定性。支持直接覆盖安装，已有课表和设置不会丢失。")
                SettingsDivider()
                SettingsInfoRow("1.1.0", "桌面小组件现在也能自由个性化，4×2、2×2 今日课程组件和今日助手组件可分别设置背景图片，支持独立调整取景、缩放、模糊与亮度，保存前即可预览实际效果；今日助手现在支持发送图片，并加入可随时关闭、查看和编辑的长期记忆，课程查找更准确，工具执行状态更清晰，长回复与图片预览也更加流畅稳定；新增更具流动感的液态动画，首页加号菜单、个性化面板、添加课程、手动导入和教务导入之间能够自然衔接，课程编辑弹窗的展开、背景景深与收回动画也更加连贯，并会收回到课程修改后的位置；新增动态模糊过渡，内容展开或收起时会以自然的模糊效果衔接，减少文字和页面的突兀跳变；新增课程冲突处理，修改星期、节次、周次、单周课程或全部周课程时，应用会主动提醒本次新增的冲突，你可以先保留修改并跳转到冲突周，再点击“冲突”将课程移到最近空位，被单独调整的周次也会正确保留，不会再次并回整段周次；新用户将不再默认启用看板娘壁纸，默认日间、夜间壁纸已更新，并会跟随应用深色模式自动切换，应用图标也已换新，浅色与深色模式各有对应样式；调整课程卡片的玻璃采样层级，长按提起或移动卡片时，玻璃材质可以正确透出下方课程，层次更加自然；统一优化课程编辑、弹窗、滑条、加号菜单、个性化面板和教务页之间的动画衔接，减少闪烁和突兀切换；移动课程或调整节次时不再重新打乱整页课程配色，多彩卡片的颜色更加稳定；压缩并整理课程编辑弹窗的间距，常用信息更集中，操作更顺手；优化今日助手的流式回复、图片处理和复杂动画性能，长内容场景下更加流畅；移除首次启动时的遮罩展开动画，壁纸会在首个可见画面直接呈现，随后再自然进入首页；课程保存后，编辑弹窗会收回到修改后的卡片位置，动画方向与最终结果保持一致。")
                SettingsDivider()
                SettingsInfoRow("1.0.9", "今日助手全面升级，能够根据需要读取当前课表和设置，连续完成查询、修改、确认结果与撤销操作，并支持 MiMo 联网搜索；新增课程卡片和今日助手打开时的背景随动缩放效果，课程卡片展开与收回采用更自然的抛物线运动轨迹，配合弹性缩放和更流畅的页面交接，动画更加灵动；增强课程、节次、作息方案和个性化设置的智能调整能力，修改节次后可更合理地处理原有课程安排；修复今日助手偶尔读取错误课表、工具调用中断、回复内容缺失，以及实时活动倒计时停止刷新、测试提醒无法取消等问题。")
                SettingsDivider()
                SettingsInfoRow("1.0.8", "桌面小组件新增今日助手，展示当前或下节课、倒计时、地点与教师、上下课时间、今日课程数量、天气和预警，并补齐今日课程与今日助手三款小组件在系统选择页的独立名称和预览；修复升级后部分课表的节次时间与详细设置被错误重建为默认值的问题，完善多作息方案保存和数据库迁移兼容；将周次切换字符替换为矢量图标，并修复添加单节课选择器层级等交互问题。")
                SettingsDivider()
                SettingsInfoRow("1.0.7", "重构个性化与壁纸调整流程，新增卡片式壁纸裁切页面、横竖屏独立构图及更连贯的无缝过渡，并优化配色布局、玻璃材质、壁纸模糊与全部调节滑条的性能；强化课表详细设置与课程编辑，补充中午时段、总节次配置、时段防重叠和多课程翻页编辑，完善节次选择器、特殊课间以及 ICS 导入导出的完整作息信息，并支持从系统分享或打开方式直接调用 SleepDown 导入 ICS；重新设计今日助手卡片，集中展示课程、天气和预警信息，保留对话入口并增强自然语言课程与设置操作的识别稳定性；改进首页文字可读性和非液态玻璃课程卡片的高斯模糊效果，修复详情页进入闪帧、后台任务卡片隐藏范围及多项动画、数据与交互问题。")
                SettingsDivider()
                SettingsInfoRow("1.0.6", "重构课表详细设置与节次时间管理，支持上午、下午、晚上分段配置、多套作息方案、自动匹配、特殊课间与手动微调，并完善保存确认、课程节次重映射和不同课表间的数据隔离；增强今日助手的课程与设置操作能力，修复操作按钮缺失、切换课表后当前节次不显示以及生成文案后首页卡顿等问题；优化日视图、周视图、课程卡片与多课表管理的动画性能和交接效果，补全开学前与学期结束后的日期边界处理；更新下载新增后台持续下载与原生实时进度通知。")
                SettingsDivider()
                SettingsInfoRow("1.0.5", "优化今日助手样式与动画，修复日视图布局错误；重新设计桌面小组件，新增2x1样式；性能优化减少卡顿。日视图与周视图表头新增当前节次标识，上课时段一目了然。课表详细设置页面精简标题、取消二次确认、动画更流畅。节次时间编辑改版：可添加多条大课间，自动匹配一键重算，时间线合并展示。")
                SettingsDivider()
                SettingsInfoRow("1.0.4", "优化课程卡片、设置页面和今日助手的动画效果，切换更流畅，减少闪烁感；统一课表设置弹窗中的日期选择器、按钮和浮层样式；改进不同字体大小下的排版适配，文字显示更完整。")
                SettingsDivider()
                SettingsInfoRow("1.0.3", "多课表管理页面全新改版，卡片堆叠效果更灵动流畅；设置页跳转动画更连贯；整体玻璃质感优化，文字更清晰易读；日期选择器和课程编辑弹窗布局改进，操作更顺手。")
                SettingsDivider()
                SettingsInfoRow("1.0.2", "扩展今日 Agent 能力边界，支持结合当前课表理解更多课程与设置需求，并可引导进入对应功能；优化设置分类与信息层级，常用配置更易查找；优化首页日视图与周视图的跟手切换动画，日期、周次及课程内容衔接更自然；调整日视图课程卡片圆角，使卡片层级与整体界面更加协调；新增 ICS 课表文件导入与导出分享，可通过系统分享器保存或发送课表；通用教务导入会保存曾打开的教务站地址与登录状态，方便下次快速进入；新增每日自动检查更新功能，发现新版本时展示版本号和更新日志；通用设置与通知设置改为修改后直接保存，不再需要二次确认。")
                SettingsDivider()
                SettingsInfoRow("1.0.1", "新增今日助手，可结合当天课程与时间生成日程提醒，并支持快捷提问；新增课程卡片彩色模式，可从壁纸提取代表色并为同页课程分配不同配色；优化周视图课程卡片排版，课程名称、地点与教师信息层级更清晰。")
                SettingsDivider()
                SettingsInfoRow("1.0", "优化二级页面排版。")
                SettingsInfoRow("1.10 beta", "优化页面切换与周视图渲染性能；新增快速编辑当前周卡片功能，长按卡片会弹出角标和删除按钮，拖拽把手可以修改课程持续时间，按住卡片拖拽可以修改上课时间，编辑体验更顺畅；修复了导入未来学期课表时，无法正确映射第一周的问题。")
                SettingsDivider()
                SettingsInfoRow("1.09 beta", "新增 AI 导入功能，绑定 API Key 之后，可以在原有教务导入无法识别网页课表结构时调用大模型来组织课表结构；无法抓取网页时，可以通过识屏进行强制抓取。此导入方法作为兜底方案，课表导入准确度取决于学校网站结构、选用大模型能力等。目前仅 DeepSeek 和小米 MIMO 经过了全流程测试，DeepSeek 不支持多模态，所以无法使用图片导入功能；优化各项玻璃参数，视觉效果更透亮；优化了个性化弹窗和加号菜单打开的动画。")
                SettingsDivider()
                SettingsInfoRow("1.08 beta", "优化动画过渡，课程卡片打开与收回更顺滑；优化渐变模糊效果，顶部与背景过渡更自然。")
                SettingsDivider()
                SettingsInfoRow("1.07 beta", "优化个性化面板布局，壁纸与课程卡片设置分区更清晰；调整弹窗取消与保存按钮为圆形液态图标按钮；优化课程卡片可读性，周视图课程名、地点、教师信息层级更分明；改进滑块默认值交互，点击标记点即可快速恢复默认并提供震动反馈；支持点击首页日期快速回到本周，日视图也可左右滑动切换日期；修复壁纸模糊时出现马赛克的问题。")
                SettingsDivider()
                SettingsInfoRow("1.06 beta", "重构首页自定义壁纸设置，支持竖屏和横屏分别调整显示区域，横竖屏切换和大屏窗口下显示更稳定；新增课程卡片字体大小调节；调整优化液态玻璃参数，修复液态玻璃可能出现分界线的问题，视效更通透灵动；优化加号菜单动画和首次启动课程卡片入场动画。")
                SettingsDivider()
                SettingsInfoRow("1.05 beta", "新增多课表功能，首页长按即可进入多课表管理页面，设置亦可进入；新增课表分享功能，可以一键复制课表口令；优化首次启动掉帧问题；优化编辑卡片弹窗无缝动画。")
                SettingsDivider()
                SettingsInfoRow("1.04 beta", "新增首次启动课程卡片飞入动画；新增隐藏后台卡片功能，返回桌面后自动从最近任务移除；修复自定义壁纸可能在应用重启后丢失的问题；全面适配 120Hz 高刷屏动画。")
                SettingsDivider()
                SettingsInfoRow("1.03 beta", "新增加号菜单连贯展开动画；新增课程卡片无缝展开与返回动画；调整玻璃通透度；增加课程卡片通透度可调范围；优化壁纸设置；优化周视图甩尾动画掉帧问题；优化桌面小组件排版、深色模式和剩余课程显示逻辑；调整实时活动提示文本，并支持系统新增荣耀 MagicOS 10。")
                SettingsDivider()
                SettingsInfoRow("1.02 beta", "修复教务 WebView 在部分 CAS 页面显示半截的问题；接入 Custom Tabs 浏览器登录流程；优化西南大学节次时间表；通用教务导入预览增加节次检查提示。")
                SettingsDivider()
                SettingsInfoRow("1.01 beta", "修复教务导入预览与节次信息问题；新增组件测试页、本次日志抓取、更新日志入口和下载新版页面；优化课程编辑删除作用范围；为周视图切换周加入课程卡片甩尾过渡动画。")
                SettingsDivider()
                SettingsInfoRow("1.0 beta", "完成基础课程表、手动导入、教务导入、通知提醒、实时活动、深色模式、壁纸与液态玻璃个性化设置。")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DownloadUpdateScreen(state: AppState, backdrop: Backdrop?) {
    val topPadding = detailContentTopPadding()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        Text(
            "密码：i224",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    webViewClient = sleepDownWebViewClient(context)
                    webChromeClient = WebChromeClient()
                    enableSleepDownDownloads()
                    loadUrl("https://wwbhx.lanzout.com/b01d6z3uid")
                }
            },
            update = {},
            onRelease = { it.releaseSleepDownWebView() }
        )
    }
}

internal fun WebView.releaseSleepDownWebView() {
    runCatching { stopLoading() }
    runCatching { clearHistory() }
    // Resource cache can grow into tens of megabytes after repeated school-site imports.
    // Cookies and DOM storage are intentionally retained so login state is not lost.
    runCatching { clearCache(true) }
    runCatching { detachEduImportBridge() }
    runCatching { destroy() }
}

private fun sleepDownWebViewClient(context: Context) = object : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.let {
            runCatching { (it.parent as? ViewGroup)?.removeView(it) }
            runCatching { it.destroy() }
        }
        Toast.makeText(context, "网页渲染进程已重启，请重新打开页面", Toast.LENGTH_SHORT).show()
        return true
    }
}

internal fun WebView.enableSleepDownDownloads() {
    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        handleSleepDownDownload(url, userAgent, contentDisposition, mimeType)
    }
}

private fun WebView.handleSleepDownDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    when {
        url.startsWith("blob:", ignoreCase = true) -> Toast.makeText(context, "请用浏览器下载此文件", Toast.LENGTH_SHORT).show()
        url.startsWith("data:", ignoreCase = true) -> {
            if (context.ensureLegacyDownloadPermission()) {
                context.handleSleepDownDataUrl(url)
            }
        }
        else -> {
            if (context.ensureLegacyDownloadPermission()) {
                context.handleSleepDownWebDownload(url, userAgent, contentDisposition, mimeType, this.url)
            }
        }
    }
}

internal fun shouldRequestLegacyDownloadPermission(
    sdkInt: Int,
    permissionGranted: Boolean
): Boolean = sdkInt < Build.VERSION_CODES.Q && !permissionGranted

private fun Context.ensureLegacyDownloadPermission(): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    if (!shouldRequestLegacyDownloadPermission(Build.VERSION.SDK_INT, granted)) {
        return true
    }
    val activity = findActivity()
    if (activity == null) {
        Toast.makeText(this, "无法申请存储权限，请使用系统浏览器下载", Toast.LENGTH_SHORT).show()
        return false
    }
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        LegacyDownloadPermissionRequestCode
    )
    Toast.makeText(this, "授权存储权限后，请再次点击下载", Toast.LENGTH_SHORT).show()
    return false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val LegacyDownloadPermissionRequestCode = 60730

private fun Context.handleSleepDownWebDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
    referer: String? = null
) {
    val uri = runCatching { url.toUri() }.getOrNull()
    if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(this, "无法处理下载链接", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val appContext = applicationContext
    val cookie = CookieManager.getInstance().getCookie(url)
    Toast.makeText(appContext, "已开始下载", Toast.LENGTH_SHORT).show()
    Thread {
        runCatching {
            appContext.downloadWebFile(
                url = url,
                fallbackContentDisposition = contentDisposition,
                userAgent = userAgent,
                cookie = cookie,
                referer = referer,
                mimeType = mimeType
            )
        }.onSuccess {
            appContext.showToastOnMain("已保存到下载目录")
        }.onFailure {
            appContext.showToastOnMain("下载失败：${it.message ?: "未知错误"}")
        }
    }.start()
}

private fun Context.downloadWebFile(
    url: String,
    fallbackContentDisposition: String?,
    userAgent: String?,
    cookie: String?,
    referer: String?,
    mimeType: String?
) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15000
        readTimeout = 30000
        requestMethod = "GET"
        if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
        if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
    }
    connection.inputStream.use { input ->
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) error("HTTP $responseCode")
        val resolvedMimeType = connection.contentType?.substringBefore(';') ?: mimeType ?: "application/octet-stream"
        val resolvedFileName = resolveWebDownloadFileName(
            originalUrl = url,
            finalUrl = connection.url?.toString(),
            contentDisposition = connection.getHeaderField("Content-Disposition") ?: fallbackContentDisposition,
            mimeType = resolvedMimeType
        )
        openDownloadOutput(resolvedFileName, resolvedMimeType).use { output ->
            input.copyTo(output)
        }
    }
    connection.disconnect()
}

private fun Context.openDownloadOutput(fileName: String, mimeType: String): java.io.OutputStream {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        openScopedDownloadOutput(fileName, mimeType)
    } else {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        File(downloads, fileName).outputStream()
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.openScopedDownloadOutput(fileName: String, mimeType: String): java.io.OutputStream {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val targetUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建文件")
    return contentResolver.openOutputStream(targetUri) ?: error("无法写入文件")
}

private fun resolveWebDownloadFileName(
    originalUrl: String,
    finalUrl: String?,
    contentDisposition: String?,
    mimeType: String?
): String {
    val fromDisposition = contentDisposition?.let { disposition ->
        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.getOrNull(1)
            ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.getOrNull(1)
    }?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    val guessed = URLUtil.guessFileName(finalUrl ?: originalUrl, contentDisposition, mimeType)
    val name = fromDisposition?.takeIf { it.isNotBlank() } ?: guessed
    if (!name.endsWith(".bin", ignoreCase = true) && name.substringAfterLast('.', "").isNotBlank()) return name
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.orEmpty())
        ?.takeIf { it.isNotBlank() }
        ?: when {
            mimeType?.contains("android", ignoreCase = true) == true -> "apk"
            mimeType?.contains("zip", ignoreCase = true) == true -> "zip"
            mimeType?.contains("pdf", ignoreCase = true) == true -> "pdf"
            else -> null
        }
    val base = name.removeSuffix(".bin").ifBlank { "download" }
    return if (extension != null) "$base.$extension" else name
}

private fun Context.showToastOnMain(message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.handleSleepDownDataUrl(dataUrl: String) {
    val commaIndex = dataUrl.indexOf(',')
    if (commaIndex <= 0) {
        Toast.makeText(this, "无法处理下载链接", Toast.LENGTH_SHORT).show()
        return
    }
    val header = dataUrl.substring(0, commaIndex)
    val payload = dataUrl.substring(commaIndex + 1)
    val mimeType = header.substringAfter("data:", "application/octet-stream").substringBefore(';')
    handleSleepDownBase64Download(payload, mimeType, null)
}

private fun Context.handleSleepDownBase64Download(base64: String, mimeType: String?, title: String?) {
    runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val safeTitle = title?.takeIf { it.isNotBlank() }?.replace(Regex("[\\\\/:*?\"<>|]"), "_") ?: "download"
        val extension = when {
            mimeType?.contains("pdf", ignoreCase = true) == true -> ".pdf"
            mimeType?.contains("zip", ignoreCase = true) == true -> ".zip"
            mimeType?.contains("excel", ignoreCase = true) == true || mimeType?.contains("spreadsheet", ignoreCase = true) == true -> ".xlsx"
            mimeType?.contains("word", ignoreCase = true) == true -> ".docx"
            mimeType?.contains("image/png", ignoreCase = true) == true -> ".png"
            mimeType?.contains("image/jpeg", ignoreCase = true) == true -> ".jpg"
            else -> ""
        }
        val fileName = "$safeTitle$extension"
        openDownloadOutput(fileName, mimeType ?: "application/octet-stream").use { it.write(bytes) }
        Toast.makeText(this, "已保存到下载目录", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show()
    }
}
