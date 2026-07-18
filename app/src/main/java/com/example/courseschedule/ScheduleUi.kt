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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.ui.window.DialogWindowProvider
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
    data object EduImport : Screen
    data class Confirm(val draft: ImportDraft) : Screen
}

enum class HomeMode { Day, Week }
enum class SettingsSection { Schedule, Notifications }
enum class SettingsPage { Root, General, AiImport, DayAgent, Schedule, Notifications, ScheduleManager, About, Changelog, Download, Donate }

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

sealed interface EduImportPage {
    data object SelectSchool : EduImportPage
    data class Import(val adapter: EduAdapter) : EduImportPage
}

private const val SettingsDetailPageExtra = "settings_page"
private const val EduAdapterExtra = "edu_adapter"

private fun SettingsPage.title(): String = when (this) {
    SettingsPage.Root -> "设置"
    SettingsPage.General -> "通用设置"
    SettingsPage.AiImport -> "AI 设置"
    SettingsPage.DayAgent -> "今日 Agent"
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
internal fun homeChromeGlassSurfaceAlpha(lightGlass: Boolean): Float = if (lightGlass) 0.68f else 0.45f
internal const val HomeHeaderGlassHighlightAlpha = 0.09f
internal const val HomeHeaderGlassShadowAlpha = 0.05f
internal const val HomeHeaderGlassOuterShadowAlpha = 0.018f
internal const val HomeHeaderGlassInnerShadowAlpha = 0.08f
private const val AddMenuMorphDurationMillis = 190
private const val AddMenuCloseSettleMillis = 36

private enum class AddMenuPhase {
    Idle,
    Opening,
    Open,
    Closing
}

internal fun homeHeaderGlassTokens(lightGlass: Boolean): GlassTokens =
    GlassTokens.pill(intensity = 0.95f).copy(surfaceAlpha = homeChromeGlassSurfaceAlpha(lightGlass))

sealed interface HomeDialog {
    data object ImportSchedule : HomeDialog
    data object EduImport : HomeDialog
    data class ConfirmImport(val draft: ImportDraft, val returnDialog: HomeDialog = ImportSchedule) : HomeDialog
    data class EditWallpaper(val uri: Uri) : HomeDialog
    data object SampleWallpaperColor : HomeDialog
    data class EditCourse(val course: CourseEntity?, val targetWeek: Int? = null) : HomeDialog
    data class ApplyCourseEdit(val original: CourseEntity, val edited: CourseEntity, val targetWeek: Int) : HomeDialog
    data class ApplyCourseDelete(val course: CourseEntity, val targetWeek: Int) : HomeDialog
}

private var splashEntranceDone = false
internal val LocalEditingCourseId = compositionLocalOf<Long?> { null }
internal val LocalLaunchingCourseId = compositionLocalOf<Long?> { null }
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
internal var hideFromRecentsEnabled = false

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CourseScheduleAppUi(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allSchedulesState by viewModel.allSchedulesState.collectAsStateWithLifecycle()
    val message by viewModel.snackbar.collectAsStateWithLifecycle()
    val pickerState = rememberSchedulePickerState()
    var previewScheduleId by remember { mutableStateOf<Int?>(null) }
    var pendingPickerEditorScheduleId by remember { mutableStateOf<Int?>(null) }
    var quickScheduleDraft by remember { mutableStateOf<QuickScheduleDraft?>(null) }
    var detailMorphState by remember { mutableStateOf<DetailMorphState>(DetailMorphState.Idle) }
    var detailMorphRequest by remember { mutableStateOf<DetailMorphRequest?>(null) }
    var detailCaptureCoverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailCaptureRecordCleanFrame by remember { mutableStateOf(false) }
    val detailCaptureMaskActive = remember { AtomicBoolean(false) }
    val visualState = previewScheduleId?.let(allSchedulesState::forSchedule) ?: state
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
    var pendingCourseEditorCapture by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var courseEditorCaptureCoverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var courseEditorCaptureCourseId by remember { mutableStateOf<Long?>(null) }
    var courseEditorRenderedCourseId by remember { mutableStateOf<Long?>(null) }
    val courseEditorMotionState = rememberCourseEditorMotionState()
    val courseEditorOverlayPhase = courseEditorMotionState.phase
    fun openCourseEditor(course: CourseEntity, targetWeek: Int?, sourceBounds: Rect?) {
        if (courseEditorRequest != null || pendingCourseEditorCapture != null) return
        val request = CourseEditorOverlayRequest(course, targetWeek, sourceBounds)
        if (sourceBounds != null) pendingCourseEditorCapture = request else courseEditorRequest = request
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
    var addMenuExpanded by remember { mutableStateOf(false) }
    var renderAddMenu by remember { mutableStateOf(false) }
    var addMenuPhase by remember { mutableStateOf(AddMenuPhase.Idle) }
    var addButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showScheduleEntryPill by remember { mutableStateOf(false) }
    val editingCourseId: Long? =
        courseEditorCaptureCourseId ?: courseEditorRequest?.course?.id ?: courseEditorRenderedCourseId
    LaunchedEffect(addMenuExpanded) {
        if (addMenuExpanded) {
            renderAddMenu = true
            addMenuPhase = AddMenuPhase.Opening
            withFrameNanos { }
            addMenuPhase = AddMenuPhase.Open
        } else if (renderAddMenu) {
            addMenuPhase = AddMenuPhase.Closing
            delay(AddMenuMorphDurationMillis.toLong())
            withFrameNanos { }
            addMenuPhase = AddMenuPhase.Idle
            delay((AddMenuCloseSettleMillis + 130).toLong())
            renderAddMenu = false
        } else {
            addMenuPhase = AddMenuPhase.Idle
        }
    }
    var showPersonalizePanel by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val personalizePanelOffscreenMarginPx = with(density) { 24.dp.roundToPx() }
    var personalizePanelHeightPx by remember { mutableIntStateOf(0) }
    val personalizePanelOffscreenOffsetY = remember(
        screenHeightPx,
        personalizePanelHeightPx,
        personalizePanelOffscreenMarginPx
    ) {
        val panelHeight = personalizePanelHeightPx.takeIf { it > 0 } ?: screenHeightPx
        -(screenHeightPx / 2 + panelHeight / 2 + personalizePanelOffscreenMarginPx)
    }
    var homeContentUnderTopBar by remember { mutableStateOf(false) }
    val adaptiveWeekCardHeight = if (visualState.periods.size >= 10) 72f else 80f
    var weekCardHeight by remember(visualState.periods.size, visualState.config.weekCardHeightDp) { mutableFloatStateOf(visualState.config.weekCardHeightDp ?: adaptiveWeekCardHeight) }
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    LaunchedEffect(pendingCourseEditorCapture) {
        val pending = pendingCourseEditorCapture ?: return@LaunchedEffect
        val sourceBounds = pending.sourceBoundsInRoot
        // Let the lightweight card press/rebound become visible before the synchronous bitmap
        // readback begins. The home recorder is already frozen while this request is pending, so
        // the source snapshot remains the last stable, unscaled card frame.
        delay(95)
        val fullSnapshot = runCatching {
            screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
        }.getOrNull()
        if (
            sourceBounds == null ||
            fullSnapshot == null ||
            fullSnapshot.width <= 0 ||
            fullSnapshot.height <= 0
        ) {
            pendingCourseEditorCapture = null
            courseEditorRequest = pending
            return@LaunchedEffect
        }
        val x = sourceBounds.left.toInt().coerceIn(0, fullSnapshot.width - 1)
        val y = sourceBounds.top.toInt().coerceIn(0, fullSnapshot.height - 1)
        val width = sourceBounds.width.toInt().coerceIn(1, fullSnapshot.width - x)
        val height = sourceBounds.height.toInt().coerceIn(1, fullSnapshot.height - y)
        val sourceSnapshot = runCatching {
            Bitmap.createBitmap(fullSnapshot, x, y, width, height)
        }.getOrNull()
        if (sourceSnapshot == null) {
            pendingCourseEditorCapture = null
            courseEditorRequest = pending
            return@LaunchedEffect
        }

        // Keep the exact visible frame on screen while the real card is hidden only in the
        // home recording layer. The editor overlay is outside that layer, so it cannot recurse.
        courseEditorCaptureCoverBitmap = fullSnapshot
        courseEditorCaptureCourseId = pending.course.id
        withFrameNanos { }
        withFrameNanos { }
        val cleanBackground = runCatching {
            screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
        }.getOrNull() ?: fullSnapshot
        courseEditorRequest = pending.copy(
            backgroundSnapshot = cleanBackground,
            sourceCardSnapshot = sourceSnapshot
        )
        withFrameNanos { }
        courseEditorCaptureCoverBitmap = null
        courseEditorCaptureCourseId = null
        // Clear the LaunchedEffect key last. Clearing it before the frame wait cancels this
        // coroutine and leaves the full-screen pointer-consuming cover mounted forever.
        pendingCourseEditorCapture = null
    }
    LaunchedEffect(courseEditorOverlayPhase, courseEditorRequest, pendingCourseEditorCapture) {
        if (
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Idle &&
            courseEditorRequest == null &&
            pendingCourseEditorCapture == null
        ) {
            // Defensive terminal cleanup: an interrupted capture/close must never leave an
            // invisible full-screen input layer or a permanently hidden course card behind.
            courseEditorCaptureCoverBitmap = null
            courseEditorCaptureCourseId = null
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
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
    val logRecording by DiagnosticLogCapture.recording.collectAsStateWithLifecycle()
    val wallpaperImages by rememberHomeWallpaperImages(visualState.config)
    val latestVisualState = rememberUpdatedState(visualState)
    val latestWallpaperImages = rememberUpdatedState(wallpaperImages)
    val latestAllSchedulesState = rememberUpdatedState(allSchedulesState)
    val startupAnimationsEnabled = remember(context) { animationsEnabled(context) }

    var startupPhase by remember {
        mutableStateOf(if (state.loaded || splashEntranceDone || !startupAnimationsEnabled) StartupPhase.FullQuality else StartupPhase.Loading)
    }
    LaunchedEffect(state.loaded, startupAnimationsEnabled) {
        if (state.loaded && startupPhase == StartupPhase.Loading) {
            if (startupAnimationsEnabled) {
                startupPhase = StartupPhase.Prewarm
            } else {
                splashEntranceDone = true
                startupPhase = StartupPhase.FullQuality
            }
        }
    }
    if (startupAnimationsEnabled && startupPhase == StartupPhase.Prewarm) {
        WaitForPrewarmFrames { startupPhase = StartupPhase.Reveal }
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
        courseEditorRequest != null ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Preparing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Opening ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Closing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Disposing
    val courseEditorBackdrop =
        if (
            reduceWallpaperQualityForCourseEditor ||
            (courseEditorRequest != null && courseEditorOverlayPhase != CourseEditorOverlayPhase.Open)
        ) {
            backgroundBackdrop
        } else {
            chromeBackdrop
        }
    // Startup splash with circular reveal
    val systemDark = isSystemInDarkTheme()
    val splashColor = if (systemDark) ComposeColor(0xFF000000) else ComposeColor(0xFFFFFFFF)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            homeDialog = HomeDialog.EditWallpaper(uri)
        }
    }
    val todayDate = LocalDate.now()
    val homeCurrentWeek = effectiveCurrentWeek(visualState.config)
    val beforeScheduleTerm = isBeforeScheduleTerm(visualState.config, todayDate)
    var homeDisplayWeek by remember(visualState.config.id) { mutableIntStateOf(1) }
    var homeWeekInitialized by remember(visualState.config.id) { mutableStateOf(false) }
    var homeDisplayDate by remember(visualState.config.id) { mutableStateOf(todayDate) }
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
    val homeCourseColorAssignments = remember(
        visualState.config.id,
        visualState.courses,
        wallpaperImages.representativeColors
    ) {
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
                    (configuration.screenWidthDp * density.density).roundToInt(),
                    (configuration.screenHeightDp * density.density).roundToInt(),
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
        return QuickScheduleDraft(
            scheduleId = scheduleId,
            totalWeeks = totalWeeks,
            currentWeek = config.currentWeek.coerceIn(1, totalWeeks),
            autoCurrentWeek = config.autoCurrentWeek,
            hideEmptyWeekends = config.hideEmptyWeekends,
            termStartDate = config.termStartDate.orEmpty()
        )
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
            pickerState.realHomeRevealProgress.animateTo(1f, tween(190))
            withFrameNanos { }
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
    BackHandler(enabled = showPersonalizePanel) {
        showPersonalizePanel = false
    }
    BackHandler(enabled = addMenuExpanded || renderAddMenu) {
        addMenuExpanded = false
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
        LocalLaunchingCourseId provides pendingCourseEditorCapture?.course?.id,
        LocalStartupPhase provides startupPhase,
        LocalGlassQuality provides glassQuality,
        LocalStartupEntranceSpec provides startupEntranceSpec,
        LocalAdaptiveGlass provides adaptiveGlassState,
        LocalCourseCardPalette provides wallpaperImages.representativeColors,
        LocalCourseCardColorAssignments provides homeCourseColorAssignments
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val recordCleanFrame =
                    detailMorphState is DetailMorphState.Capturing && detailCaptureRecordCleanFrame
                val courseEditorOwnsFrame =
                    pendingCourseEditorCapture != null ||
                        courseEditorCaptureCoverBitmap != null ||
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
        CourseEditorBackgroundBlurLayer(
            motionState = courseEditorMotionState,
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
                            eduImportPage = EduImportPage.SelectSchool,
                            backdrop = chromeBackdrop,
                            homeMode = homeMode,
                            onHomeModeChange = { homeMode = it },
                            homeDisplayDate = homeTitleDate,
                            homeDisplayWeek = homeTitleWeek,
                            beforeScheduleTerm = beforeScheduleTerm,
                            homeShowingAnotherWeek = homeShowingAnotherWeek,
                            onReturnHomeToCurrentWeek = returnHomeToCurrentDateAndWeek,
                            addMenuExpanded = addMenuExpanded,
                            onAddButtonPositioned = { addButtonBounds = it },
                            addMenuRendering = renderAddMenu && addMenuPhase != AddMenuPhase.Idle,
                            onToggleAddMenu = {
                                val next = !addMenuExpanded
                                if (next) {
                                    renderAddMenu = true
                                    addMenuPhase = AddMenuPhase.Opening
                                }
                                addMenuExpanded = next
                                if (next) showPersonalizePanel = false
                            },
                            showPersonalize = showPersonalizePanel,
                            onTogglePersonalize = {
                                val next = !showPersonalizePanel
                                showPersonalizePanel = next
                                if (next) addMenuExpanded = false
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
                            HomeWallpaperLoadingMask(visualState.config)
                        } else {
                            HomeWallpaper(
                                visualState.config,
                                wallpaperImages,
                                startupPhase,
                                reduceQuality = reduceWallpaperQualityForCourseEditor
                            )
                            if (visualState.config.hasAnyWallpaper() && wallpaperImages.source == null) {
                                HomeWallpaperLoadingMask(visualState.config)
                            } else if (!visualState.config.hasAnyWallpaper()) {
                                HomeBackdropFallback()
                            }
                        }
                    } else if (screen is Screen.Config) {
                        Box(Modifier.fillMaxSize().background(settingsPageBackground(settingsVisualConfig(state.config))))
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                }
                val contentModifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                Column(modifier = contentModifier) {
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    ContentEntranceContainer(phase = startupPhase, modifier = Modifier.weight(1f)) {
                        when (val current = screen) {
                            Screen.Home -> {
                                HomeScreen(
                                    state = visualState,
                                    mode = homeMode,
                                    weekCardHeight = weekCardHeight.dp,
                                    displayWeek = homeDisplayWeek,
                                    displayDate = homeDisplayDate,
                                    backdrop = backgroundBackdrop,
                                    // Dragged week cards must not sample contentBackdrop/chromeBackdrop:
                                    // they live inside contentBackdrop, so sampling it can recursively include themselves.
                                    floatingCourseBackdrop = backgroundBackdrop,
                                    weekHeaderBackdrop = backgroundBackdrop,
                                    onSwipeWeek = { delta -> homeDisplayWeek = (homeDisplayWeek + delta).coerceIn(1, visualState.config.totalWeeks.coerceAtLeast(1)) },
                                    onSwipeDay = { delta -> homeDisplayDate = homeDisplayDate.plusDays(delta.toLong()) },
                                    onContentUnderTopBarChange = { homeContentUnderTopBar = it },
                                    onCourseClick = { course, week, sourceBounds ->
                                        openCourseEditor(course, week, sourceBounds)
                                    },
                                    onAddCourse = viewModel::addCourse,
                                    onAgentAction = { action ->
                                        when (action.type) {
                                            AgentValidatedActionType.ADD -> action.edited?.let(viewModel::addCourse)
                                            AgentValidatedActionType.UPDATE -> {
                                                val original = action.original
                                                val edited = action.edited
                                                if (original != null && edited != null) {
                                                    if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                                        viewModel.updateCourseSingleWeek(original, edited, action.targetWeek)
                                                    } else {
                                                        viewModel.updateCourse(edited)
                                                    }
                                                }
                                            }
                                            AgentValidatedActionType.DELETE -> action.original?.let { course ->
                                                if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                                    viewModel.deleteCourseSingleWeek(course, action.targetWeek)
                                                } else {
                                                    viewModel.deleteCourse(course)
                                                }
                                            }
                                            AgentValidatedActionType.OPEN_SETTINGS -> {
                                                if (action.settingsPage == "PERSONALIZATION") {
                                                    addMenuExpanded = false
                                                    showPersonalizePanel = true
                                                } else agentSettingsPage(action.settingsPage)?.let { page ->
                                                    val intent = Intent(context, SettingsDetailActivity::class.java)
                                                        .putExtra(SettingsDetailPageExtra, page.name)
                                                    if (page == SettingsPage.Schedule) {
                                                        intent.putExtra(ScheduleCustomizeIdExtra, state.config.id)
                                                    }
                                                    context.startActivity(intent)
                                                }
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
                                                    else -> AgentSettingRegistry.apply(
                                                        state.config,
                                                        action.settingKey,
                                                        action.settingValue
                                                    )?.let(viewModel::savePersonalization)
                                                }
                                            }
                                        }
                                    },
                                    onUpdateCourseSingleWeek = viewModel::updateCourseSingleWeek,
                                    onDeleteCourseSingleWeek = viewModel::deleteCourseSingleWeek,
                                    onScheduleLongPress = {
                                        if (pickerState.phase is CustomizeUiState.Home) {
                                            addMenuExpanded = false
                                            showPersonalizePanel = false
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
                            Screen.EduImport -> Box(Modifier.fillMaxSize().padding(padding)) {
                                EduImportFlowScreen(
                                        page = EduImportPage.SelectSchool,
                                        state = state,
                                        onPageChange = {},
                                        onParsed = { screen = Screen.Confirm(it) }
                                    )
                            }
                            is Screen.Confirm -> Box(Modifier.fillMaxSize().padding(padding)) {
                                ConfirmScheduleScreen(current.draft, onCancel = { screen = Screen.Home }, onConfirm = { createNewSchedule -> viewModel.importDraft(current.draft, createNewSchedule) { screen = Screen.Home } })
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
            if (screen is Screen.Home && renderAddMenu) {
                AnimatedVisibility(
                    visible = addMenuPhase == AddMenuPhase.Open,
                    enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 140)),
                    modifier = Modifier.fillMaxSize().zIndex(23f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { addMenuExpanded = false }
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(24f)
                        .fillMaxSize()
                ) {
                    LiquidAddMenu(
                        backdrop = chromeBackdrop,
                        config = state.config,
                        expanded = addMenuPhase == AddMenuPhase.Open,
                        anchorBounds = addButtonBounds,
                        actions = listOf(
                            AddMenuAction(R.drawable.ic_add_course, "\u6DFB\u52A0\u5355\u8282\u8BFE") { addMenuExpanded = false; homeDialog = HomeDialog.EditCourse(null) },
                            AddMenuAction(R.drawable.ic_ai_import, "\u624B\u52A8\u5BFC\u5165\u8BFE\u8868") { addMenuExpanded = false; homeDialog = HomeDialog.ImportSchedule },
                            AddMenuAction(R.drawable.ic_school_import, "\u6559\u52A1\u7CFB\u7EDF\u5BFC\u5165") { addMenuExpanded = false; context.startActivity(Intent(context, EduSchoolSelectActivity::class.java)) }
                        )
                    )
                }
                if (false) LiquidPanel(
                    backdrop = chromeBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(24f)
                        .popIn()
                        .padding(top = 92.dp, end = 58.dp)
                        .width(208.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MenuPill(R.drawable.ic_add_course, "添加单节课") { addMenuExpanded = false; homeDialog = HomeDialog.EditCourse(null) }
                        MenuPill(R.drawable.ic_ai_import, "手动导入课表") { addMenuExpanded = false; homeDialog = HomeDialog.ImportSchedule }
                        MenuPill(R.drawable.ic_school_import, "\u6559\u52A1\u7CFB\u7EDF\u5BFC\u5165") { addMenuExpanded = false; context.startActivity(Intent(context, EduSchoolSelectActivity::class.java)) }
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
            if (screen is Screen.Home) {
                AnimatedVisibility(
                    visible = showPersonalizePanel,
                    enter = personalizePanelEnterTransition(personalizePanelOffscreenOffsetY),
                    exit = personalizePanelExitTransition(personalizePanelOffscreenOffsetY),
                    modifier = Modifier.fillMaxSize().zIndex(19f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showPersonalizePanel = false }
                            )
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PersonalizePanel(
                            modifier = Modifier.onSizeChanged { personalizePanelHeightPx = it.height },
                            state = state,
                            backdrop = chromeBackdrop,
                            mode = homeMode,
                            weekCardHeight = weekCardHeight,
                            onWeekCardHeight = {
                                weekCardHeight = it
                                viewModel.savePersonalization(state.config.copy(weekCardHeightDp = it))
                            },
                            onPickWallpaper = { wallpaperLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onSampleWallpaperColor = { homeDialog = HomeDialog.SampleWallpaperColor },
                            onUpdateConfig = { config -> viewModel.savePersonalization(config) }
                        )
                    }
                }
            } else {
                showPersonalizePanel = false
            }
            DiagnosticLogStopOverlay(
                visible = logRecording,
                config = state.config,
                backdrop = chromeBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(40f)
            )
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
                    exitPicker(
                        apply = true,
                        targetOverride = scheduleId,
                        commitTarget = true,
                        crossfadeToTarget = true,
                        onFinished = {
                            quickScheduleDraft = quickDraftFor(scheduleId)
                        }
                    )
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
            }
        )
    }
    }

    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
        QuickScheduleSettingsSheets(
            draft = quickScheduleDraft,
            config = state.config,
            backdrop = chromeBackdrop,
            onDraftChange = { quickScheduleDraft = it },
            onDismiss = { quickScheduleDraft = null },
            onDismissFinished = {
                // Re-enter through the normal home-to-picker morph. It captures the now-real
                // homepage first, so both Apply and Cancel shrink back without a fake card.
                pickerState.phase = CustomizeUiState.Home
                enterCustomizePage()
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
                val effectiveWeek = if (draft.autoCurrentWeek && draft.termStartDate.isNotBlank()) {
                    effectiveCurrentWeek(datedConfig)
                } else {
                    manualWeek
                }
                val periods = latest.allPeriods.filter { it.scheduleId == draft.scheduleId }
                    .ifEmpty { defaultPeriods(draft.scheduleId) }
                viewModel.saveConfigForSchedule(
                    draft.scheduleId,
                    datedConfig.copy(currentWeek = effectiveWeek.coerceIn(1, totalWeeks)),
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
        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
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

    courseEditorCaptureCoverBitmap?.let { cover ->
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(99f)
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

    CourseEditorContainerOverlayHost(
        request = courseEditorRequest,
        state = state,
        backdrop = courseEditorBackdrop,
        config = state.config,
        modifier = Modifier.zIndex(100f),
        onDismissRequest = { closeCourseEditor() },
        onSave = { original, edited, targetWeek ->
            if (courseWeeksChanged(original, edited)) {
                viewModel.updateCourse(edited)
                closeCourseEditor()
            } else {
                closeCourseEditor()
                homeDialog = HomeDialog.ApplyCourseEdit(original, edited, targetWeek ?: effectiveCurrentWeek(state.config))
            }
        },
        onDelete = { course, targetWeek ->
            closeCourseEditor()
            homeDialog = HomeDialog.ApplyCourseDelete(course, targetWeek ?: effectiveCurrentWeek(state.config))
        },
        motionState = courseEditorMotionState,
        onRenderedCourseIdChange = { courseEditorRenderedCourseId = it },
        onPhaseChange = {}
    )

    // Dialog-based dialogs for all other types (including EditCourse without a source card)
    renderedHomeDialog?.let { dialog ->
        if (dialog !is HomeDialog.EditCourse || dialog.course == null) {
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
            AnimatedVisibility(
                visible = homeDialogVisible,
                enter = popEnterTransition(),
                exit = popExitTransition()
            ) {
                val useAlertDialog = dialog is HomeDialog.ApplyCourseEdit || dialog is HomeDialog.ApplyCourseDelete
                val dialogContent: @Composable () -> Unit = {
                    when (dialog) {
                    is HomeDialog.EditCourse ->
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
                        backdrop = chromeBackdrop
                    )
                    HomeDialog.ImportSchedule -> NormalizedAiManualImportScreen(
                        state = state,
                        backdrop = chromeBackdrop,
                        onCancel = { dismissHomeDialog() },
                        onParsed = { homeDialog = HomeDialog.ConfirmImport(it) }
                    )
                    HomeDialog.EduImport -> Unit
                    is HomeDialog.EditWallpaper -> WallpaperEditorDialog(
                        uri = dialog.uri,
                        config = state.config,
                        backdrop = chromeBackdrop,
                        onCancel = { dismissHomeDialog() },
                        onApply = { nextConfig ->
                            viewModel.savePersonalization(nextConfig)
                            dismissHomeDialog()
                        }
                    )
                    is HomeDialog.ConfirmImport -> ConfirmScheduleScreen(
                        draft = dialog.draft,
                        backdrop = chromeBackdrop,
                        onCancel = { homeDialog = dialog.returnDialog },
                        onConfirm = { createNewSchedule -> viewModel.importDraft(dialog.draft, createNewSchedule) { dismissHomeDialog() } }
                    )
                    HomeDialog.SampleWallpaperColor -> WallpaperColorSamplerScreen(
                        state = state,
                        backdrop = chromeBackdrop,
                        onCancel = { dismissHomeDialog() },
                        onSelected = { color ->
                            viewModel.savePersonalization(state.config.copy(cardColorArgb = color))
                            dismissHomeDialog()
                        }
                    )
                    is HomeDialog.ApplyCourseEdit -> ApplyCourseEditDialog(
                        original = dialog.original,
                        edited = dialog.edited,
                        backdrop = chromeBackdrop,
                        config = state.config,
                        onSingle = {
                            viewModel.updateCourseSingleWeek(dialog.original, dialog.edited, dialog.targetWeek)
                            dismissHomeDialog()
                        },
                        onAll = {
                            viewModel.updateCourse(dialog.edited)
                            dismissHomeDialog()
                        },
                        onCancel = {
                            dismissHomeDialog()
                            openCourseEditor(dialog.original, dialog.targetWeek, null)
                        }
                    )
                    is HomeDialog.ApplyCourseDelete -> ApplyCourseDeleteDialog(
                        course = dialog.course,
                        backdrop = chromeBackdrop,
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
                if (useAlertDialog) {
                    dialogContent()
                } else {
                    CenterLiquidDialog(
                        backdrop = chromeBackdrop,
                        config = state.config,
                        modifier = Modifier
                    ) {
                        dialogContent()
                    }
                }
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl)))
            },
            onOpenBackup = {
                automaticUpdateDialog = null
                screen = Screen.Config
            },
            onRequestInstallPermission = {
                automaticInstallPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
            }
        )

        // Startup splash — covers content until config loaded, then circular reveal
        StartupRevealOverlay(
            phase = startupPhase,
            splashColor = splashColor,
            onRevealFinished = { startupPhase = StartupPhase.Entrance }
        )
    }
    }
    }

}

private val HomeTopOverlayHeight = 178.dp
private val DetailTopBarHeight = 58.dp
private val DetailTopOverlayExtra = 74.dp
private val DetailContentTopGap = 44.dp
internal val HomeInitialTopInset = 122.dp

@Composable
private fun CourseEditorBackgroundBlurLayer(
    motionState: CourseEditorMotionState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // The editor now animates a frozen home snapshot. Keeping the old live-home blur here
    // would render the entire schedule twice and can make the hidden layer hitch or tear.
    @Suppress("UNUSED_VARIABLE") val keepMotionStateStable = motionState
    Box(modifier = modifier, content = content)
}

@Composable
private fun rootTopBarLayoutHeight(screen: Screen): Dp {
    return when (screen) {
        Screen.Home -> HomeTopOverlayHeight
        Screen.Config -> detailTopOverlayHeight()
        else -> HomeInitialTopInset
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
        Screen.Home -> HomeTopOverlayHeight
        Screen.Config -> detailTopOverlayHeight()
        else -> HomeInitialTopInset
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
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidDialogSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
    content: @Composable (Backdrop?) -> Unit
) {
    GlassMiuixDetailActivityScaffold(
        title = title,
        config = config,
        onBack = onBack,
        showTopGradientBlur = showTopGradientBlur,
        isolateContentFromBackdrop = isolateContentFromBackdrop,
        compactTopBar = compactTopBar,
        content = content
    )
}

@Composable
fun DiagnosticLogStopOverlay(
    visible: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        enter = popEnterTransition(),
        exit = popExitTransition(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 28.dp)
    ) {
        val stop: () -> Unit = {
            scope.launch {
                DiagnosticLogCapture.stop(context)
                    .onSuccess { uri -> shareDiagnosticLog(context, uri) }
                    .onFailure {
                        Toast.makeText(context, "日志停止失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        if (backdrop != null) {
            LiquidButton(
                onClick = stop,
                backdrop = backdrop,
                height = 46.dp,
                surfaceColor = ComposeColor(0xFFFF453A).copy(alpha = 0.82f),
                contentPadding = PaddingValues(horizontal = 20.dp),
                blurRadius = 12.dp,
                lensHeight = 30.dp,
                lensAmount = 38.dp,
                chromaticAberration = false
            ) {
                Text(
                    "停止抓取",
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            Text(
                "停止抓取",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ComposeColor(0xFFFF453A).copy(alpha = 0.86f))
                    .clickable(onClick = stop)
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                color = ComposeColor.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(title: String, config: ScheduleConfigEntity, backdrop: Backdrop?, onBack: () -> Unit) {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + DetailTopBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBackButton(backdrop = backdrop, config = config, onClick = onBack, modifier = Modifier.size(42.dp))
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
    height: Dp = HomeTopOverlayHeight,
    modifier: Modifier = Modifier
) {
    val tintColor = if (glassUsesLightStyle(config)) ComposeColor.White else ComposeColor(0xFF111111)
    ProgressiveBackdropBlur(
        backdrop = backdrop,
        modifier = modifier,
        tintColor = tintColor,
        height = height,
        blurRadius = 18.dp,
        tintIntensity = 0.18f,
        direction = ProgressiveBlurDirection.TopToBottom,
        fallbackTintStops = listOf(
            0f to tintColor.copy(alpha = 0.42f),
            0.42f to tintColor.copy(alpha = 0.18f),
            1f to ComposeColor.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    screen: Screen,
    state: AppState,
    settingsPage: SettingsPage,
    eduImportPage: EduImportPage,
    backdrop: Backdrop?,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    homeDisplayDate: LocalDate,
    homeDisplayWeek: Int,
    beforeScheduleTerm: Boolean,
    homeShowingAnotherWeek: Boolean,
    onReturnHomeToCurrentWeek: () -> Unit,
    addMenuExpanded: Boolean,
    onAddButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    addMenuRendering: Boolean,
    onToggleAddMenu: () -> Unit,
    showPersonalize: Boolean,
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
                                SettingsPage.AiImport -> "AI 设置"
                                SettingsPage.DayAgent -> "今日 Agent"
                                SettingsPage.Schedule -> "课表详细设置"
                                SettingsPage.Notifications -> "通知设置"
                                SettingsPage.ScheduleManager -> "课表设置"
                                SettingsPage.About -> "关于"
                                SettingsPage.Changelog -> "更新日志"
                                SettingsPage.Download -> "下载新版"
                                SettingsPage.Donate -> "捐赠支持"
                            }
                            Screen.EduImport -> when (eduImportPage) {
                                EduImportPage.SelectSchool -> "选择学校"
                                is EduImportPage.Import -> "教务导入"
                            }
                            is Screen.Confirm -> "确认导入"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp
                    )
                }
            }
        },
        navigationIcon = {
            if (screen is Screen.Confirm || screen is Screen.EduImport || (screen is Screen.Config && settingsPage != SettingsPage.Root)) {
                TopBackButton(backdrop = backdrop, config = state.config, onClick = onBackHome)
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
                        selected = showPersonalize,
                        onClick = {
                            performButtonHaptic(view)
                            onTogglePersonalize()
                        }
                    )
                    HomeAddButton(
                        backdrop,
                        state.config,
                        addMenuExpanded,
                        addMenuRendering,
                        onAddButtonPositioned,
                        onClick = {
                            performButtonHaptic(view)
                            onToggleAddMenu()
                        }
                    )
                    HomeModeSwitch(backdrop, state.config, homeMode, onHomeModeChange)
                }
            }
        }
    )
}

@Composable
fun HomeAddButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    expanded: Boolean,
    menuRendering: Boolean,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onClick: () -> Unit
) {
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    val buttonFadeEase = remember { CubicBezierEasing(0.16f, 1f, 0.30f, 1f) }
    val visible = !expanded && !menuRendering
    LaunchedEffect(expanded, menuRendering) {
        if (!visible) {
            launch { alpha.animateTo(0f, tween(durationMillis = 100, easing = buttonFadeEase)) }
            scale.animateTo(0.92f, tween(durationMillis = 110, easing = buttonFadeEase))
        } else {
            scale.snapTo(0.94f)
            delay(18)
            launch { alpha.animateTo(1f, tween(durationMillis = 130, easing = buttonFadeEase)) }
            scale.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 620f))
        }
    }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; scaleX = scale.value; scaleY = scale.value }) {
        HomeIconButton(
            backdrop = backdrop,
            config = config,
            iconRes = R.drawable.ic_add_course,
            contentDescription = "添加",
            selected = expanded,
            accentColor = ComposeColor(0xFF0A84FF),
            onClick = onClick,
            onButtonPositioned = onPositioned
        )
    }
}

@Composable
fun Modifier.popIn(): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(targetValue = if (visible) 1f else 0.92f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f), label = "pop-in-scale")
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f), label = "pop-in-alpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

private fun popEnterTransition(): EnterTransition =
    fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)) +
        scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f))

private fun popExitTransition(): ExitTransition =
    fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 560f)) +
        scaleOut(targetScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 560f))

private fun personalizePanelEnterTransition(offscreenOffsetY: Int): EnterTransition {
    val flowEase = CubicBezierEasing(0.18f, 0.82f, 0.24f, 1f)
    return fadeIn(animationSpec = tween(durationMillis = 190, easing = flowEase)) +
        expandIn(
            expandFrom = Alignment.TopCenter,
            initialSize = ::personalizePanelCollapsedSize,
            animationSpec = tween(durationMillis = 300, easing = flowEase)
        ) +
        scaleIn(
            initialScale = 0.02f,
            transformOrigin = TransformOrigin(0.5f, 0.02f),
            animationSpec = tween(durationMillis = 300, easing = flowEase)
        ) +
        slideInVertically(
            initialOffsetY = { offscreenOffsetY },
            animationSpec = tween(durationMillis = 300, easing = flowEase)
        )
}

private fun personalizePanelExitTransition(offscreenOffsetY: Int): ExitTransition {
    val ease = CubicBezierEasing(0.76f, 0f, 0.82f, 0.18f)
    return fadeOut(animationSpec = tween(durationMillis = 260, easing = ease)) +
        shrinkOut(
            shrinkTowards = Alignment.TopCenter,
            targetSize = ::personalizePanelCollapsedSize,
            animationSpec = tween(durationMillis = 260, easing = ease)
        ) +
        scaleOut(
            targetScale = 0.02f,
            transformOrigin = TransformOrigin(0.5f, 0.02f),
            animationSpec = tween(durationMillis = 260, easing = ease)
        ) +
        slideOutVertically(
            targetOffsetY = { offscreenOffsetY },
            animationSpec = tween(durationMillis = 260, easing = ease)
        )
}

private fun personalizePanelCollapsedSize(fullSize: IntSize): IntSize =
    IntSize(
        width = (fullSize.width * 0.075f).roundToInt().coerceAtLeast(1),
        height = (fullSize.height * 0.012f).roundToInt().coerceAtLeast(1)
    )

private fun courseEditEnterTransition(): EnterTransition =
    fadeIn(animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f)) +
        scaleIn(
            initialScale = 0.62f,
            transformOrigin = TransformOrigin(0.5f, 0.68f),
            animationSpec = spring(dampingRatio = 0.70f, stiffness = 640f)
        ) +
        slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f)
        )

private fun courseEditExitTransition(): ExitTransition =
    fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 620f)) +
        scaleOut(
            targetScale = 0.76f,
            transformOrigin = TransformOrigin(0.5f, 0.68f),
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f)
        ) +
        slideOutVertically(
            targetOffsetY = { it / 8 },
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f)
        )

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun CourseBoundsSource(
    courseId: Long,
    visible: Boolean,
    sharedScope: SharedTransitionScope?,
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable (Modifier) -> Unit
) {
    content(
        modifier.graphicsLayer {
            alpha = if (visible) 1f else 0f
        }
    )
}

@Composable
fun TopBackButton(backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit, modifier: Modifier = Modifier.padding(start = 8.dp).size(42.dp)) {
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
            chromaticAberration = false
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
    accentColor: ComposeColor = ComposeColor.Unspecified,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onButtonPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = adaptiveGlass.lightGlass
    val baseSurfaceColor = if (lightGlass) ComposeColor.White else ComposeColor(0xFF121212)
    val buttonSurfaceColor = if (accentColor.isSpecified) {
        accentColor.copy(alpha = if (lightGlass) 0.28f else 0.32f)
    } else {
        baseSurfaceColor.copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass))
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier
                .padding(end = 7.dp)
                .size(42.dp)
                .then(
                    if (onButtonPositioned != null) {
                        Modifier.onGloballyPositioned { onButtonPositioned(it.boundsInRoot()) }
                    } else {
                        Modifier
                    }
                ),
            height = 42.dp,
            tint = if (accentColor.isSpecified) accentColor else ComposeColor.Unspecified,
            surfaceColor = buttonSurfaceColor,
            contentPadding = PaddingValues(0.dp),
            blurRadius = HomeHeaderGlassBlur,
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false
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
            modifier = modifier
                .padding(end = 7.dp)
                .size(42.dp)
                .then(
                    if (onButtonPositioned != null) {
                        Modifier.onGloballyPositioned { onButtonPositioned(it.boundsInRoot()) }
                    } else {
                        Modifier
                    }
                ),
            selected = selected,
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun MenuPill(iconRes: Int, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.16f else 0f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

data class AddMenuAction(
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun LiquidAddMenu(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    modifier: Modifier = Modifier,
    anchorBounds: androidx.compose.ui.geometry.Rect? = null,
    expanded: Boolean = true
) {
    MorphingLiquidAddMenu(
        backdrop = backdrop,
        config = config,
        actions = actions,
        modifier = modifier,
        anchorBounds = anchorBounds,
        expanded = expanded
    )
}

@Composable
fun MorphingLiquidAddMenu(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    modifier: Modifier = Modifier,
    anchorBounds: androidx.compose.ui.geometry.Rect? = null,
    expanded: Boolean = true
) {
    var animatedExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(16)
            animatedExpanded = true
        } else {
            animatedExpanded = false
        }
    }
    val transition = androidx.compose.animation.core.updateTransition(animatedExpanded, label = "morph-add-menu")
    val addMenuEase = remember { CubicBezierEasing(0.16f, 1f, 0.30f, 1f) }
    val addMenuSettleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val itemHeight = 48.dp
    val itemSpacing = 4.dp
    val menuPadding = 8.dp
    val expandedHeight = menuPadding * 2 + itemHeight * actions.size + itemSpacing * (actions.size - 1).coerceAtLeast(0)
    val sinkOffset by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    0.dp at 0
                    49.dp at 150 using addMenuEase
                    46.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    46.dp at 0
                    (-3).dp at 150 using addMenuEase
                    0.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            }
        },
        label = "add-menu-sink"
    ) { if (it) 46.dp else 0.dp }
    val width by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    42.dp at 0
                    42.dp at 42
                    212.dp at 150 using addMenuEase
                    202.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    202.dp at 0
                    38.dp at 150 using addMenuEase
                    42.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            }
        },
        label = "add-menu-width"
    ) { if (it) 202.dp else 42.dp }
    val height by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    42.dp at 0
                    42.dp at 42
                    (expandedHeight + 8.dp) at 150 using addMenuEase
                    expandedHeight at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    expandedHeight at 0
                    38.dp at 150 using addMenuEase
                    42.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            }
        },
        label = "add-menu-height"
    ) { if (it) expandedHeight else 42.dp }
    val radius by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    50.dp at 0
                    24.dp at 150 using addMenuEase
                    26.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    26.dp at 0
                    52.dp at 150 using addMenuEase
                    50.dp at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            }
        },
        label = "add-menu-radius"
    ) { if (it) 26.dp else 50.dp }
    val iconAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 90, easing = addMenuEase) },
        label = "add-menu-icon-alpha"
    ) { if (it) 0f else 1f }
    val contentAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 175
                    0f at 0
                    0f at 48
                    1f at 175 using addMenuEase
                }
            } else {
                tween(durationMillis = 85, easing = addMenuEase)
            }
        },
        label = "add-menu-content-alpha"
    ) { if (it) 1f else 0f }
    val dynamicBlur by transition.animateDp(
        transitionSpec = { tween(durationMillis = 175, easing = addMenuEase) },
        label = "add-menu-blur"
    ) { if (it) 14.dp else 3.dp }
    val menuReboundScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    1f at 0
                    1.018f at 150 using addMenuEase
                    1f at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = AddMenuMorphDurationMillis
                    1f at 0
                    0.985f at 150 using addMenuEase
                    1f at AddMenuMorphDurationMillis using addMenuSettleEase
                }
            }
        },
        label = "add-menu-rebound-scale"
    ) { if (it) 1.0001f else 1f }
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var touching by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (touching || highlightedIndex >= 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "morph-add-menu-press"
    )
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemStepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val menuPaddingPx = with(density) { menuPadding.toPx() }
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
    val expansionProgress = contentAlpha.coerceIn(0f, 1f)
    val closedTintColor = ComposeColor(0xFF0A84FF)
    val closedSurfaceColor = closedTintColor.copy(alpha = if (lightGlass) 0.20f else 0.24f)
    val expandedSurfaceColor = (if (lightGlass) ComposeColor.White else ComposeColor(0xFF050505))
        .copy(alpha = if (lightGlass) 0.16f else 0.26f)
    val closedBackdropAlpha = 1f - expansionProgress
    val expandedBackdropAlpha = expansionProgress
    fun hitIndex(y: Float): Int {
        val localY = y - menuPaddingPx
        if (localY < 0f || contentAlpha < 0.6f) return -1
        val index = (localY / itemStepPx).toInt()
        val inItem = localY - index * itemStepPx <= itemHeightPx
        return index.takeIf { it in actions.indices && inItem } ?: -1
    }
    val dragModifier = Modifier.pointerInput(actions, contentAlpha) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                touching = true
                highlightedIndex = hitIndex(down.position.y)
                down.consume()
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    highlightedIndex = hitIndex(change.position.y)
                    if (change.changedToUpIgnoreConsumed()) {
                        val index = highlightedIndex
                        touching = false
                        highlightedIndex = -1
                        if (index in actions.indices) actions[index].onClick()
                        released = true
                    }
                    change.consume()
                }
            }
        }
    }
    val shape = RoundedCornerShape(radius)
    val anchorDensity = LocalDensity.current
    val anchorCenter = anchorBounds?.center
    val anchorOffset = if (anchorCenter != null) {
        with(anchorDensity) {
            IntOffset(
                x = (anchorCenter.x - width.toPx() / 2f).roundToInt(),
                y = (anchorCenter.y - 21.dp.toPx()).roundToInt()
            )
        }
    } else {
        IntOffset.Zero
    }
    val containerModifier = modifier
        .offset { anchorOffset }
        .offset { IntOffset(0, sinkOffset.roundToPx()) }
        .graphicsLayer {
            scaleX = menuReboundScale
            scaleY = menuReboundScale
        }
        .width(width)
        .height(height)
        .clip(shape)
        .then(dragModifier)

    Box(
        modifier = if (backdrop != null) {
            containerModifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(((dynamicBlur * 0.65f) + 3.dp * pressProgress).toPx())
                    lens(
                        (12.dp + 6.dp * contentAlpha + 4.dp * pressProgress).toPx(),
                        (HomeHeaderGlassLensAmount + 6.dp * contentAlpha + 6.dp * pressProgress).toPx(),
                        depthEffect = true,
                        chromaticAberration = false
                    )
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = HomeHeaderGlassHighlightAlpha * closedBackdropAlpha +
                            0.04f * expandedBackdropAlpha +
                            0.08f * pressProgress
                    )
                },
                shadow = {
                    Shadow(
                        alpha = HomeHeaderGlassShadowAlpha * closedBackdropAlpha +
                            (if (lightGlass) 0.10f else 0.18f) * expandedBackdropAlpha +
                            0.08f * pressProgress
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp + 3.dp * contentAlpha,
                        alpha = HomeHeaderGlassInnerShadowAlpha * closedBackdropAlpha +
                            0.10f * expandedBackdropAlpha +
                            0.08f * pressProgress
                    )
                },
                layerBlock = {
                    val scale = 1f + 0.016f * pressProgress
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(closedTintColor.copy(alpha = 0.18f * closedBackdropAlpha), blendMode = BlendMode.Hue)
                    drawRect(closedTintColor.copy(alpha = 0.22f * closedBackdropAlpha))
                    drawRect(closedSurfaceColor.copy(alpha = closedSurfaceColor.alpha * closedBackdropAlpha))
                    drawRect(expandedSurfaceColor.copy(alpha = expandedSurfaceColor.alpha * expandedBackdropAlpha))
                    drawRect(ComposeColor.Black.copy(alpha = (if (lightGlass) 0.018f else 0.055f) * expandedBackdropAlpha))
                }
            )
        } else {
            containerModifier.background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
        },
        contentAlignment = Alignment.TopCenter
    ) {
        Icon(
            painterResource(R.drawable.ic_add_course),
            contentDescription = "添加",
            tint = ComposeColor(0xFF0A84FF),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(alpha = iconAlpha, rotationZ = 45f * contentAlpha)
                .size(20.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = contentAlpha)
                .padding(menuPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                actions.forEachIndexed { index, action ->
                    AddMenuLiquidItem(
                        config = config,
                        action = action,
                        highlighted = highlightedIndex == index,
                        itemHeight = itemHeight
                    )
                }
            }
        }
    }
}

@Composable
fun LegacyLiquidAddMenu(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    modifier: Modifier = Modifier
) {
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var touching by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (touching || highlightedIndex >= 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "add-menu-press"
    )
    val itemHeight = 48.dp
    val itemSpacing = 4.dp
    val menuPadding = 8.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemStepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val menuPaddingPx = with(density) { menuPadding.toPx() }
    val menuShape = RoundedCornerShape(26.dp)
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
    fun hitIndex(y: Float): Int {
        val localY = y - menuPaddingPx
        if (localY < 0f) return -1
        val index = (localY / itemStepPx).toInt()
        val inItem = localY - index * itemStepPx <= itemHeightPx
        return index.takeIf { it in actions.indices && inItem } ?: -1
    }
    val dragModifier = Modifier.pointerInput(actions) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                touching = true
                highlightedIndex = hitIndex(down.position.y)
                down.consume()
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    highlightedIndex = hitIndex(change.position.y)
                    if (change.changedToUpIgnoreConsumed()) {
                        val index = highlightedIndex
                        touching = false
                        highlightedIndex = -1
                        if (index in actions.indices) actions[index].onClick()
                        released = true
                    }
                    change.consume()
                }
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(menuPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            actions.forEachIndexed { index, action ->
                AddMenuLiquidItem(
                    config = config,
                    action = action,
                    highlighted = highlightedIndex == index,
                    itemHeight = itemHeight
                )
            }
        }
    }

    if (backdrop != null) {
        Box(
            modifier = modifier
                .width(206.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { menuShape },
                    effects = {
                        blur(14.dp.toPx())
                        lens(
                            (28.dp + 8.dp * pressProgress).toPx(),
                            (34.dp + 10.dp * pressProgress).toPx(),
                            chromaticAberration = false
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.12f + 0.10f * pressProgress) },
                    shadow = { Shadow(alpha = (if (lightGlass) 0.18f else 0.34f) + 0.10f * pressProgress) },
                    innerShadow = { InnerShadow(radius = 14.dp + 4.dp * pressProgress, alpha = 0.22f + 0.10f * pressProgress) },
                    layerBlock = {
                        val scale = 1f + 0.018f * pressProgress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect((if (lightGlass) ComposeColor.White else ComposeColor(0xFF050505)).copy(alpha = if (lightGlass) 0.28f else 0.46f))
                        drawRect(ComposeColor.Black.copy(alpha = if (lightGlass) 0.06f else 0.16f))
                    }
                )
                .clip(menuShape)
                .then(dragModifier),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                content()
            }
        }
    } else {
        Box(
            modifier = modifier
                .width(206.dp)
                .clip(menuShape)
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                .then(dragModifier),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                content()
            }
        }
    }
}

@Composable
fun AddMenuLiquidItem(
    config: ScheduleConfigEntity,
    action: AddMenuAction,
    highlighted: Boolean,
    itemHeight: Dp
) {
    val baseText = glassForegroundColor(config)
    val textColor = if (highlighted) ComposeColor(0xFF0A84FF) else baseText
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
                .background(if (highlighted) ComposeColor(0xFF0A84FF).copy(alpha = 0.10f) else ComposeColor.Transparent)
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

@Composable
fun FloatingDock(selected: Screen, backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier, onHome: () -> Unit, onConfig: () -> Unit) {
    val lightGlass = LocalAdaptiveGlass.current.lightGlass
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
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
            .padding(horizontalPadding),
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
                    surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.075f) else ComposeColor(0xFF121212).copy(alpha = 0.075f),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizePanel(
    modifier: Modifier = Modifier,
    state: AppState,
    backdrop: Backdrop?,
    mode: HomeMode,
    weekCardHeight: Float,
    onWeekCardHeight: (Float) -> Unit,
    onPickWallpaper: () -> Unit,
    onSampleWallpaperColor: () -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit
) {
    val adaptiveHeight = if (state.periods.size >= 10) 72f else 80f
    var wallpaperBlurDisplay by remember { mutableFloatStateOf(wallpaperBlurPercent(state.config.wallpaperBlur)) }
    var wallpaperBrightnessDisplay by remember { mutableFloatStateOf(state.config.wallpaperBrightness.coerceIn(0.35f, 1f)) }
    var cardAlphaDisplay by remember { mutableFloatStateOf(state.config.cardAlpha.coerceIn(0f, 1f)) }
    var courseCardBlurDisplay by remember { mutableFloatStateOf(state.config.courseCardBlur.coerceIn(0f, 10f) / 10f * 100f) }
    var courseCardFontDisplay by remember { mutableFloatStateOf(state.config.courseCardFontScale) }
    var sliderTouchActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.config.wallpaperBlur) {
        wallpaperBlurDisplay = wallpaperBlurPercent(state.config.wallpaperBlur)
    }
    LaunchedEffect(state.config.wallpaperBrightness) { wallpaperBrightnessDisplay = state.config.wallpaperBrightness.coerceIn(0.35f, 1f) }
    LaunchedEffect(state.config.cardAlpha) { cardAlphaDisplay = state.config.cardAlpha.coerceIn(0f, 1f) }
    LaunchedEffect(state.config.courseCardBlur) { courseCardBlurDisplay = state.config.courseCardBlur.coerceIn(0f, 10f) / 10f * 100f }
    LaunchedEffect(state.config.courseCardFontScale) { courseCardFontDisplay = state.config.courseCardFontScale }
    @Composable
    fun PersonalizeSection(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ComposeColor.Black.copy(alpha = 0.10f))
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("首页壁纸", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    LiquidMenuButton(
                        backdrop,
                        "\u9009\u62E9\u58C1\u7EB8",
                        onClick = onPickWallpaper,
                        textColorOverride = ComposeColor.White,
                        surfaceColorOverride = ComposeColor(0xFFFFB340).copy(alpha = 0.62f)
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
                            surfaceColorOverride = ComposeColor(0xFFFF453A).copy(alpha = 0.62f)
                        )
                    }
                }
                Text("壁纸模糊 ${wallpaperBlurDisplay.roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(
                    value = wallpaperBlurPercent(state.config.wallpaperBlur),
                    onValueChange = { percent ->
                        onUpdateConfig(state.config.copy(wallpaperBlur = wallpaperBlurDp(percent)))
                    },
                    valueRange = 0f..100f,
                    backdrop = backdrop,
                    onLiveValueChange = { wallpaperBlurDisplay = it },
                    snapValue = 0f,
                    onSliderTouchActiveChange = { sliderTouchActive = it }
                )
                Text("壁纸亮度 ${(wallpaperBrightnessDisplay.coerceIn(0.35f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(state.config.wallpaperBrightness.coerceIn(0.35f, 1f), { onUpdateConfig(state.config.copy(wallpaperBrightness = it)) }, 0.35f..1f, backdrop, onLiveValueChange = { wallpaperBrightnessDisplay = it }, snapValue = 1f, onSliderTouchActiveChange = { sliderTouchActive = it })
            }
            PersonalizeSection {
                if (mode == HomeMode.Week) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("周视图行高", style = MaterialTheme.typography.labelLarge)
                        Text("${weekCardHeight.toInt()}dp", style = MaterialTheme.typography.labelMedium)
                        LiquidMenuButton(
                            backdrop,
                            "\u81EA\u9002\u5E94",
                            onClick = { onWeekCardHeight(adaptiveHeight) },
                            textColorOverride = ComposeColor.White,
                            surfaceColorOverride = ComposeColor(0xFF0A84FF).copy(alpha = 0.58f)
                        )
                    }
                    LiquidControlSlider(weekCardHeight, onWeekCardHeight, 44f..180f, backdrop, snapValue = adaptiveHeight, onSliderTouchActiveChange = { sliderTouchActive = it })
                }
                Text("课程卡片颜色", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0xFFD6E9FF, 0xFFBFE0FF, 0xFF9ED4FF, 0xFFFFE1E8,
                        0xFFFFC4D6, 0xFFD8F3DC, 0xFFB7E4C7, 0xFFFFF0C2,
                        0xFFFFD166, 0xFFE8D7FF, 0xFFD7C0FF
                    ).forEach { color ->
                        val selected = state.config.cardColorArgb == color
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(50),
                            color = ComposeColor(color.toInt()),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                            ),
                            onClick = { onUpdateConfig(state.config.copy(cardColorArgb = color)) }
                        ) {}
                    }
                    val multicolorSelected = state.config.cardColorArgb == MulticolorCourseCardArgb
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(50),
                        color = ComposeColor.Transparent,
                        border = BorderStroke(
                            if (multicolorSelected) 2.dp else 1.dp,
                            if (multicolorSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                        ),
                        onClick = { onUpdateConfig(state.config.copy(cardColorArgb = MulticolorCourseCardArgb)) }
                    ) {
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
                            ),
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }
                LiquidMenuButton(backdrop, "从壁纸取色", onClick = onSampleWallpaperColor)
                val alphaLabel = if (state.config.courseCardGlassEnabled) "课程卡片着色强度" else "课程卡片不透明度"
                Text("$alphaLabel ${(cardAlphaDisplay * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(state.config.cardAlpha.coerceIn(0f, 1f), { onUpdateConfig(state.config.copy(cardAlpha = it)) }, 0f..1f, backdrop, onLiveValueChange = { cardAlphaDisplay = it }, snapValue = 0.5f, onSliderTouchActiveChange = { sliderTouchActive = it })
                Text("课程卡片模糊 ${courseCardBlurDisplay.toInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(
                    value = state.config.courseCardBlur.coerceIn(0f, 10f) / 10f * 100f,
                    onValueChange = { onUpdateConfig(state.config.copy(courseCardBlur = it / 100f * 10f)) },
                    valueRange = 0f..100f,
                    backdrop = backdrop,
                    onLiveValueChange = { courseCardBlurDisplay = it },
                    snapValue = 35f,
                    onSliderTouchActiveChange = { sliderTouchActive = it }
                )
                Text("课程卡片字体 ${(courseCardFontDisplay * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(state.config.courseCardFontScale, { onUpdateConfig(state.config.copy(courseCardFontScale = it)) }, 0.80f..1.35f, backdrop, onLiveValueChange = { courseCardFontDisplay = it }, snapValue = 1f, onSliderTouchActiveChange = { sliderTouchActive = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val topClearance = HomeInitialTopInset + 8.dp
        val heightRatio = when {
            maxHeight < 520.dp -> 0.74f
            maxHeight < 700.dp -> 0.72f
            else -> 0.68f
        }
        val centeredSafeHeight = (maxHeight - topClearance * 2).coerceAtLeast(280.dp)
        val panelMaxHeight = (maxHeight * heightRatio)
            .coerceAtMost(centeredSafeHeight)
            .coerceAtMost(680.dp)
        val panelModifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(max = panelMaxHeight)
        val scrollState = rememberScrollState()
        val contentModifier = Modifier
            .heightIn(max = panelMaxHeight)
            .verticalScroll(scrollState, enabled = !sliderTouchActive)
        if (backdrop != null) {
            val lightGlass = glassUsesLightStyle(state.config)
            val panelTextColor = personalizePanelForegroundColor(state.config)
            LiquidPanel(
                backdrop = backdrop,
                modifier = panelModifier,
                surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.18f) else ComposeColor(0xFF121212).copy(alpha = 0.30f)
            ) {
                CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                    PanelContent(contentModifier)
                }
            }
        } else {
            val panelTextColor = personalizePanelForegroundColor(state.config)
            GlassDialogSurface(
                backdrop = null,
                config = state.config,
                modifier = panelModifier
            ) {
                CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                    PanelContent(contentModifier)
                }
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
    roundIcon: Boolean = (role == DialogButtonRole.Cancel && label == "取消") ||
        (role == DialogButtonRole.Confirm && label == "保存")
) {
    val useRoundIcon = roundIcon && role != DialogButtonRole.Neutral
    val resolvedIconRes = iconRes ?: when {
        useRoundIcon && role == DialogButtonRole.Cancel -> R.drawable.ic_close_light
        useRoundIcon && role == DialogButtonRole.Confirm -> R.drawable.ic_check
        else -> null
    }
    val textColor = when (role) {
        DialogButtonRole.Confirm -> ComposeColor.White
        DialogButtonRole.Cancel -> if (useRoundIcon) ComposeColor.White else ComposeColor(0xFFFF453A)
        DialogButtonRole.Neutral -> MaterialTheme.colorScheme.primary
    }
    val surfaceColor = when (role) {
        DialogButtonRole.Confirm -> ComposeColor(0xFF0A84FF).copy(alpha = 0.82f)
        DialogButtonRole.Cancel -> ComposeColor(0xFFFF453A).copy(alpha = if (useRoundIcon) 0.78f else 0.16f)
        DialogButtonRole.Neutral -> ComposeColor.Transparent
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = if (useRoundIcon) modifier.size(42.dp) else modifier,
            height = if (useRoundIcon) 42.dp else 40.dp,
            surfaceColor = surfaceColor,
            contentPadding = if (useRoundIcon) PaddingValues(0.dp) else PaddingValues(horizontal = 15.dp),
            blurRadius = 3.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false
        ) {
            resolvedIconRes?.let {
                Icon(painterResource(it), contentDescription = label, modifier = Modifier.size(20.dp), tint = textColor)
            }
            if (!useRoundIcon) {
                Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    } else {
        Row(
            modifier = (if (useRoundIcon) modifier.size(42.dp) else modifier)
                .clip(RoundedCornerShape(50))
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(if (role == DialogButtonRole.Neutral) 0f else 0.16f)))
                .clickable(onClick = onClick)
                .then(if (useRoundIcon) Modifier else Modifier.padding(horizontal = 15.dp, vertical = 10.dp)),
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
                    Text(placeholder, color = textColor.copy(alpha = 0.52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true
) {
    val config = defaultConfig()
    DialogCapsuleField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "",
        config = config,
        modifier = modifier.graphicsLayer(alpha = if (enabled) 1f else 0.48f),
        keyboardType = keyboardOptions.keyboardType,
        minLines = minLines
    )
}

@Composable
fun <T> DialogOptionPicker(
    title: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig()
) {
    val textColor = glassForegroundColor(config)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = textColor)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(values.size) { index ->
                val value = values[index]
                val active = value == selected
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = label(value),
                    onClick = { onSelected(value) },
                    role = if (active) DialogButtonRole.Confirm else DialogButtonRole.Neutral
                )
            }
        }
    }
}

class SettingsDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val customizeScheduleId = intent.getIntExtra(ScheduleCustomizeIdExtra, -1).takeIf { it > 0 }
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
                val editEntrySnapshot = remember(customizeScheduleId) {
                    customizeScheduleId?.let { BitmapFactory.decodeFile(ScheduleSnapshotStore.file(this, it).absolutePath) }
                }
                var editEntrySnapshotVisible by remember(editEntrySnapshot) { mutableStateOf(editEntrySnapshot != null) }
                LaunchedEffect(editEntrySnapshot) {
                    if (editEntrySnapshot != null) {
                        withFrameNanos { }
                        withFrameNanos { }
                        editEntrySnapshotVisible = false
                    }
                }
                Box(Modifier.fillMaxSize()) {
                DetailActivityScaffold(
                    title = section.title(),
                    config = state.config,
                    onBack = { finish() }
                ) { backdrop ->
                    when (section) {
                        SettingsPage.General -> GeneralSettingsScreen(state, backdrop, viewModel::savePersonalization)
                        SettingsPage.AiImport -> AiImportSettingsScreen(state, backdrop)
                        SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
                        SettingsPage.Schedule -> ScheduleConfigScreen(
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
                            onPreviewLiveUpdate = viewModel::previewLiveUpdate
                        )
                        SettingsPage.Notifications -> ScheduleConfigScreen(state, backdrop, SettingsSection.Notifications, viewModel::saveConfig, viewModel::previewLiveUpdate)
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
                                onConfirm = { createNewSchedule -> viewModel.importDraft(pendingDraft!!, createNewSchedule) { finish() } }
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
    visibilityThreshold: Float = 0.01f
) {
    val haptic = LocalHapticFeedback.current
    val safeSnapValue = snapValue?.coerceIn(valueRange)
    @Composable
    fun SliderWithSnapMarker(currentValue: Float, onSnapClick: () -> Unit, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val latestCurrentValue by rememberUpdatedState(currentValue)
        val latestTouchActiveChange by rememberUpdatedState(onSliderTouchActiveChange)
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(valueRange) {
                    val thumbInsetPx = with(density) { 10.dp.toPx() }
                    val thumbHitRadiusPx = with(density) { 18.dp.toPx() }
                    val dragLockThresholdPx = with(density) { 4.dp.toPx() }
                    try {
                        awaitPointerEventScope {
                            var pressed = false
                            var downTimeMillis = 0L
                            var scrollLocked = false
                            var thumbPress = false
                            var downX = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val nextPressed = event.changes.any { it.pressed }
                                val eventTimeMillis = event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L
                                if (nextPressed) {
                                    if (!pressed) {
                                        pressed = true
                                        downTimeMillis = eventTimeMillis
                                        val firstPressed = event.changes.firstOrNull { it.pressed }
                                        thumbPress = firstPressed?.let { change ->
                                            val thumbFraction = ((latestCurrentValue.coerceIn(valueRange) - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                                            val thumbCenterX = thumbInsetPx + (size.width - thumbInsetPx * 2f).coerceAtLeast(1f) * thumbFraction
                                            downX = change.position.x
                                            abs(change.position.x - thumbCenterX) <= thumbHitRadiusPx
                                        } ?: false
                                    }
                                    val currentX = event.changes.firstOrNull { it.pressed }?.position?.x ?: downX
                                    val draggedThumb = abs(currentX - downX) >= dragLockThresholdPx
                                    if (thumbPress && !scrollLocked && (eventTimeMillis - downTimeMillis >= 80L || draggedThumb)) {
                                        scrollLocked = true
                                        latestTouchActiveChange(true)
                                    }
                                } else if (pressed) {
                                    pressed = false
                                    downTimeMillis = 0L
                                    thumbPress = false
                                    downX = 0f
                                    if (scrollLocked) {
                                        scrollLocked = false
                                        latestTouchActiveChange(false)
                                    }
                                }
                            }
                        }
                    } finally {
                        latestTouchActiveChange(false)
                    }
                }
        ) {
            content()
            val snap = safeSnapValue
            if (snap != null) {
                val fraction = ((snap - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val trackWidth = maxWidth
                val markerClickable = abs(currentValue.coerceIn(valueRange) - snap) > visibilityThreshold
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = ((trackWidth - 20.dp) * fraction) - 4.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (markerClickable) {
                                    Modifier.pointerInput(onSnapClick) {
                                        detectTapGestures(onTap = { onSnapClick() })
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ComposeColor.White.copy(alpha = 0.78f))
                        )
                    }
                }
            }
        }
    }
    if (backdrop != null) {
        var localValue by remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
        var localEditPending by remember(valueRange) { mutableStateOf(false) }
        val commitScope = rememberCoroutineScope()
        var commitJob by remember(valueRange) { mutableStateOf<Job?>(null) }
        fun commitAfterVisualSettle() {
            if (!localEditPending) return
            val settledValue = localValue.coerceIn(valueRange)
            commitJob?.cancel()
            commitJob = commitScope.launch {
                withFrameNanos { }
                withFrameNanos { }
                if (localEditPending && abs(localValue - settledValue) <= visibilityThreshold) {
                    onValueChange(settledValue)
                    localEditPending = false
                }
            }
        }
        LaunchedEffect(value, valueRange) {
            if (!localEditPending) {
                val coerced = value.coerceIn(valueRange)
                if (abs(localValue - coerced) > visibilityThreshold) {
                    localValue = coerced
                    onLiveValueChange?.invoke(coerced)
                }
            }
        }
        SliderWithSnapMarker(
            currentValue = localValue,
            onSnapClick = {
                val snap = safeSnapValue ?: return@SliderWithSnapMarker
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                localValue = snap
                onLiveValueChange?.invoke(snap)
                localEditPending = true
                commitAfterVisualSettle()
            }
        ) {
            LiquidSlider(
                value = { localValue },
                onValueChange = {
                    commitJob?.cancel()
                    localValue = it.coerceIn(valueRange)
                    onLiveValueChange?.invoke(localValue)
                    localEditPending = true
                },
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                onValueChangeFinished = { commitAfterVisualSettle() }
            )
        }
    } else {
        var localValue by remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
        var localEditPending by remember(valueRange) { mutableStateOf(false) }
        val commitScope = rememberCoroutineScope()
        var commitJob by remember(valueRange) { mutableStateOf<Job?>(null) }
        fun commitAfterVisualSettle() {
            if (!localEditPending) return
            val settledValue = localValue.coerceIn(valueRange)
            commitJob?.cancel()
            commitJob = commitScope.launch {
                withFrameNanos { }
                withFrameNanos { }
                if (localEditPending && abs(localValue - settledValue) <= visibilityThreshold) {
                    onValueChange(settledValue)
                    localEditPending = false
                }
            }
        }
        LaunchedEffect(value, valueRange) {
            if (!localEditPending) {
                val coerced = value.coerceIn(valueRange)
                if (abs(localValue - coerced) > visibilityThreshold) {
                    localValue = coerced
                    onLiveValueChange?.invoke(coerced)
                }
            }
        }
        SliderWithSnapMarker(
            currentValue = localValue,
            onSnapClick = {
                val snap = safeSnapValue ?: return@SliderWithSnapMarker
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                localValue = snap
                onLiveValueChange?.invoke(snap)
                localEditPending = true
                commitAfterVisualSettle()
            }
        ) {
            Slider(
                value = localValue,
                onValueChange = {
                    commitJob?.cancel()
                    localValue = it.coerceIn(valueRange)
                    onLiveValueChange?.invoke(localValue)
                    localEditPending = true
                },
                onValueChangeFinished = { commitAfterVisualSettle() },
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth()
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
    GlassMiuixSettingsTheme(pageConfig) {
        when (page) {
            SettingsPage.Root -> SettingsRootScreen(pageState, backdrop, onPageChange)
            SettingsPage.General -> GeneralSettingsScreen(state, backdrop, onUpdateConfig)
            SettingsPage.AiImport -> AiImportSettingsScreen(state, backdrop)
            SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
            SettingsPage.Schedule -> ScheduleConfigScreen(state, backdrop, SettingsSection.Schedule, onSave, onPreviewLiveUpdate)
            SettingsPage.Notifications -> ScheduleConfigScreen(state, backdrop, SettingsSection.Notifications, onSave, onPreviewLiveUpdate)
            SettingsPage.ScheduleManager -> ScheduleManagerScreen(state, backdrop, onCreateSchedule, onActivateSchedule, onRenameSchedule, onDeleteSchedule)
            SettingsPage.About -> AboutSettingsScreen(pageState, backdrop)
            SettingsPage.Changelog -> ChangelogSettingsScreen(pageState, backdrop) {}
            SettingsPage.Download -> DownloadUpdateScreen(pageState, backdrop)
            SettingsPage.Donate -> DonateSettingsScreen(pageState, backdrop)
        }
    }
}

@Composable
fun SettingsRootScreen(state: AppState, backdrop: Backdrop?, onPageChange: (SettingsPage) -> Unit) {
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
                    modifier = Modifier.fillMaxWidth(),
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
                    SettingsNavigationRow("通用设置", "深色模式与系统外观", onClick = { onPageChange(SettingsPage.General) })
                    SettingsDivider()
                    SettingsNavigationRow(
                        "当前课表详细设置",
                        "编辑当前课表的周数、节次与显示规则",
                        onClick = { onPageChange(SettingsPage.Schedule) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow("通知设置", "上课提醒与实时活动", onClick = { onPageChange(SettingsPage.Notifications) })
                }
            }
        }
        item {
            GlassPreferenceSection("智能助手") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow("AI 设置", "配置智能功能共用的服务商、模型和 API Key。", onClick = { onPageChange(SettingsPage.AiImport) })
                    SettingsDivider()
                    SettingsNavigationRow("今日 Agent", "管理日视图助手、每日文案与天气。", onClick = { onPageChange(SettingsPage.DayAgent) })
                }
            }
        }
        item {
            GlassPreferenceSection("其他") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow("关于", "软件信息与开源引用", onClick = { onPageChange(SettingsPage.About) })
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
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl)))
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
            actions = listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Available -> {
            val release = dialog.release
            val notes = release.notes.trim().take(520).ifBlank { "该版本没有填写更新说明。" }
            LiquidAlertDialog(
                title = "发现新版本 ${release.name}",
                message = "$notes\n\n当前将从 Gitee 下载 APK，安装前仍会由系统向你确认。",
                actions = listOf(
                    LiquidAlertAction("稍后", LiquidAlertActionStyle.Secondary, onDismiss),
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
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary, onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Downloading -> LiquidAlertDialog(
            title = "正在下载 ${dialog.release.name}",
            message = "正在从 Gitee 下载 APK，请保持网络连接。下载完成后将打开系统安装确认页面。",
            actions = listOf(LiquidAlertAction("请稍候", LiquidAlertActionStyle.Secondary) {}),
            backdrop = backdrop,
            config = config,
            onDismissRequest = {}
        )
        is SettingsUpdateDialog.NoApk -> LiquidAlertDialog(
            title = "Release 中没有 APK",
            message = "已找到 ${dialog.release.name}，但该 Release 没有附带 APK 安装包。可以查看发行版，或使用备用下载页。",
            actions = listOf(
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onOpenBackup),
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
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onOpenBackup),
                LiquidAlertAction("重试", LiquidAlertActionStyle.Primary, onRetry)
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        SettingsUpdateDialog.InstallPermissionRequired -> LiquidAlertDialog(
            title = "允许安装更新",
            message = "Android 需要你先允许 SleepDown 安装来自 Gitee 的更新。授权返回后会继续打开系统安装确认页面。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onDismiss),
                LiquidAlertAction("去授权", LiquidAlertActionStyle.Primary, onRequestInstallPermission)
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
                SettingsInfoRow("1.0.3", "重构多课表管理页，提供堆叠式卡片效果和更灵动的无缝动画；新增从快速设置按钮连续展开至详细设置页的 Morph 动画，并优化返回衔接、快照层级与交互性能；继续优化液态玻璃参数、层次和文字可读性；课表日期选择器改用 MIUIX 样式，优化课程编辑弹窗顶栏布局。")
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
    runCatching { removeJavascriptInterface("AndroidBridgePromise") }
    runCatching { removeJavascriptInterface("AndroidBridge") }
    runCatching { destroy() }
}

@SuppressLint("JavascriptInterface")
internal fun WebView.addEduImportBridge(bridge: EduImportBridge) {
    addJavascriptInterface(bridge, "AndroidBridgePromise")
    addJavascriptInterface(bridge, "AndroidBridge")
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
        url.startsWith("data:", ignoreCase = true) -> context.handleSleepDownDataUrl(url)
        else -> context.handleSleepDownWebDownload(url, userAgent, contentDisposition, mimeType, this.url)
    }
}

private fun Context.handleSleepDownWebDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
    referer: String? = null
) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
