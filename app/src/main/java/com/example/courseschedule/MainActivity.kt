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
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.BoundsTransform
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
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
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsState()
            CourseScheduleTheme(config = state.config) {
                CourseScheduleAppUi(viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (hideFromRecentsEnabled) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { it.setExcludeFromRecents(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hideFromRecentsEnabled) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { it.setExcludeFromRecents(false) }
        }
    }
}

@Composable
fun CourseScheduleTheme(config: ScheduleConfigEntity = defaultConfig(), content: @Composable () -> Unit) {
    val darkTheme = appUsesDarkTheme(config)
    val view = LocalView.current
    LaunchedEffect(darkTheme, view) {
        val window = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (darkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (darkTheme) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
    val blue = ComposeColor(0xFF007AFF)
    val blueContainer = ComposeColor(0xFFD6E9FF)
    val darkBlueContainer = ComposeColor(0xFF003A66)
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = blue,
                onPrimary = ComposeColor.White,
                primaryContainer = darkBlueContainer,
                onPrimaryContainer = ComposeColor(0xFFD6E9FF),
                secondary = blue,
                secondaryContainer = darkBlueContainer,
                tertiary = blue,
                tertiaryContainer = darkBlueContainer,
                background = ComposeColor(0xFF000000),
                surface = ComposeColor(0xFF111111),
                surfaceVariant = ComposeColor(0xFF1C1C1E),
                surfaceContainerHigh = ComposeColor(0xFF1C1C1E)
            )
        } else {
            lightColorScheme(
                primary = blue,
                onPrimary = ComposeColor.White,
                primaryContainer = blueContainer,
                onPrimaryContainer = ComposeColor(0xFF003A66),
                secondary = blue,
                secondaryContainer = blueContainer,
                tertiary = blue,
                tertiaryContainer = blueContainer,
                background = ComposeColor.White,
                surface = ComposeColor.White,
                surfaceVariant = ComposeColor(0xFFF2F2F7),
                surfaceContainerHigh = ComposeColor.White
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

class ScheduleViewModel(private val app: Application, private val repository: ScheduleRepository) : AndroidViewModel(app) {
    val state: StateFlow<AppState> = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())
    val snackbar = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            rescheduleToday()
        }
    }

    fun addCourse(course: CourseEntity) = viewModelScope.launch {
        repository.addCourse(course)
        rescheduleToday()
//        snackbar.value = "课程已保存"
    }

    fun updateCourse(course: CourseEntity) = viewModelScope.launch {
        repository.updateCourse(course)
        rescheduleToday()
        snackbar.value = "课程已更新"
    }

    fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) = viewModelScope.launch {
        repository.updateCourseSingleWeek(original, edited, targetWeek)
        rescheduleToday()
        snackbar.value = "课程已更新"
    }

    fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) = viewModelScope.launch {
        repository.updateRelatedCourses(original, edited)
        rescheduleToday()
        snackbar.value = "课程已更新"
    }

    fun deleteCourse(course: CourseEntity) = viewModelScope.launch {
        repository.deleteCourse(course)
        rescheduleToday()
        snackbar.value = "课程已删除"
    }

    fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) = viewModelScope.launch {
        repository.deleteCourseSingleWeek(course, targetWeek)
        rescheduleToday()
        snackbar.value = "课程已删除"
    }

    fun importDraft(draft: ImportDraft, createNewSchedule: Boolean = false, onDone: () -> Unit) = viewModelScope.launch {
        repository.importDraft(draft, createNewSchedule)
        rescheduleToday()
        snackbar.value = if (createNewSchedule) "已导入到新课表" else "课程表已导入"
        onDone()
    }

    fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) = viewModelScope.launch {
        repository.saveConfig(config, periods)
        rescheduleToday()
        snackbar.value = "设置已保存"
    }

    fun saveConfigForSchedule(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) = viewModelScope.launch {
        repository.saveConfigForSchedule(scheduleId, config, periods)
        rescheduleToday()
        snackbar.value = "设置已保存"
    }

    fun savePersonalization(config: ScheduleConfigEntity) = viewModelScope.launch {
        repository.saveConfigOnly(config)
    }

    fun createSchedule(name: String = "\u65B0\u8BFE\u8868") = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.createSchedule name=$name")
        val scheduleId = repository.createSchedule(name)
        Log.d("ScheduleManager", "repository.createSchedule created id=$scheduleId")
        repository.activateSchedule(scheduleId)
        rescheduleToday()
        TodayCoursesWidgetProvider.refreshAll(app)
    }

    fun activateSchedule(scheduleId: Int, finish: (() -> Unit)? = null) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.activateSchedule id=$scheduleId")
        repository.activateSchedule(scheduleId)
        rescheduleToday()
        TodayCoursesWidgetProvider.refreshAll(app)
        finish?.invoke()
    }

    fun renameSchedule(scheduleId: Int, name: String) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.renameSchedule id=$scheduleId name=$name")
        repository.renameSchedule(scheduleId, name)
    }

    fun deleteSchedule(scheduleId: Int) = viewModelScope.launch {
        Log.d("ScheduleManager", "viewModel.deleteSchedule id=$scheduleId")
        repository.deleteSchedule(scheduleId)
        rescheduleToday()
        TodayCoursesWidgetProvider.refreshAll(app)
    }

    fun clearSnackbar() {
        snackbar.value = null
    }

    fun previewLiveUpdate() {
        NotificationScheduler.showLiveUpdatePreview(app)
        snackbar.value = "已发送实时活动预览"
    }

    fun refreshNotifications() = viewModelScope.launch {
        rescheduleToday()
    }

    private suspend fun rescheduleToday() = withContext(Dispatchers.IO) {
        val snapshot = repository.snapshot()
        NotificationScheduler.refreshToday(app, snapshot.courses, snapshot.config, snapshot.periods)
        TodayCoursesWidgetProvider.refreshAll(app)
    }
}

class ScheduleViewModelFactory(private val app: Application, private val repository: ScheduleRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(app, repository) as T
}

sealed interface Screen {
    data object Home : Screen
    data object Config : Screen
    data object EduImport : Screen
    data class Confirm(val draft: ImportDraft) : Screen
}

enum class HomeMode { Day, Week }
enum class SettingsSection { Schedule, Notifications }
enum class SettingsPage { Root, General, Schedule, Notifications, ScheduleManager, About, Changelog, Download, Donate }
sealed interface EduImportPage {
    data object SelectSchool : EduImportPage
    data class Import(val adapter: EduAdapter) : EduImportPage
}

private const val SettingsDetailPageExtra = "settings_page"
private const val EduAdapterExtra = "edu_adapter"

private fun SettingsPage.title(): String = when (this) {
    SettingsPage.Root -> "设置"
    SettingsPage.General -> "通用设置"
    SettingsPage.Schedule -> "课表设置"
    SettingsPage.Notifications -> "通知设置"
    SettingsPage.ScheduleManager -> "课表设置"
    SettingsPage.About -> "关于"
    SettingsPage.Changelog -> "更新日志"
    SettingsPage.Download -> "下载新版"
    SettingsPage.Donate -> "捐赠支持"
}

private val DayDockScrollPadding = 104.dp
private val WeekDockScrollPadding = 132.dp
private val DockScrollPadding = 132.dp

sealed interface HomeDialog {
    data object ImportSchedule : HomeDialog
    data object EduImport : HomeDialog
    data class ConfirmImport(val draft: ImportDraft) : HomeDialog
    data class EditWallpaper(val uri: Uri) : HomeDialog
    data object SampleWallpaperColor : HomeDialog
    data class EditCourse(val course: CourseEntity?, val targetWeek: Int? = null) : HomeDialog
    data class ApplyCourseEdit(val original: CourseEntity, val edited: CourseEntity, val targetWeek: Int) : HomeDialog
    data class ApplyCourseDelete(val course: CourseEntity, val targetWeek: Int) : HomeDialog
}

private var splashEntranceDone = false
private val LocalEditingCourseId = compositionLocalOf<Long?> { null }
private val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
internal var hideFromRecentsEnabled = false

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CourseScheduleAppUi(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.snackbar.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var homeMode by remember { mutableStateOf(if (state.config.defaultHomeMode == HomeStartMode.DAY) HomeMode.Day else HomeMode.Week) }
    LaunchedEffect(state.config.defaultHomeMode) {
        homeMode = if (state.config.defaultHomeMode == HomeStartMode.DAY) HomeMode.Day else HomeMode.Week
    }
    var homeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    var renderedHomeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    var homeDialogVisible by remember { mutableStateOf(false) }
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
    var addButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var courseEditSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var showScheduleEntryPill by remember { mutableStateOf(false) }
    val editingCourseId: Long? = if (homeDialogVisible) (renderedHomeDialog as? HomeDialog.EditCourse)?.course?.id else null
    LaunchedEffect(addMenuExpanded) {
        if (addMenuExpanded) {
            renderAddMenu = true
        } else if (renderAddMenu) {
            delay(210)
            renderAddMenu = false
        }
    }
    var showPersonalizePanel by remember { mutableStateOf(false) }
    var homeContentUnderTopBar by remember { mutableStateOf(false) }
    val adaptiveWeekCardHeight = if (state.periods.size >= 10) 72f else 80f
    var weekCardHeight by remember(state.periods.size, state.config.weekCardHeightDp) { mutableFloatStateOf(state.config.weekCardHeightDp ?: adaptiveWeekCardHeight) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    val logRecording by DiagnosticLogCapture.recording.collectAsState()
    val wallpaperImages by rememberHomeWallpaperImages(state.config)
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
        waitForPrewarmFrames { startupPhase = StartupPhase.Reveal }
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
    val startupAnimation = when (startupPhase) {
        StartupPhase.Reveal -> "StartupReveal"
        StartupPhase.Entrance -> "HomeFlyInEntrance"
        StartupPhase.Settle -> "HomeFlyInEntrance"
        else -> if (homeDialogVisible) "DialogOpen" else "Idle"
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
    StartupJankStats(
        phase = startupPhase,
        screen = if (screen is Screen.Home) "Home" else if (screen is Screen.Config) "Settings" else "Other",
        animation = startupAnimation
    )

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
    val homeCurrentWeek = effectiveCurrentWeek(state.config)
    var homeDisplayWeek by remember(state.config.id, state.config.totalWeeks, homeCurrentWeek) { mutableIntStateOf(homeCurrentWeek) }
    var homeDisplayDate by remember(state.config.id) { mutableStateOf(todayDate) }
    LaunchedEffect(state.config.id, state.config.totalWeeks, homeCurrentWeek, state.config.autoCurrentWeek) {
        homeDisplayWeek = homeDisplayWeek.coerceIn(1, state.config.totalWeeks.coerceAtLeast(1))
        if (state.config.autoCurrentWeek) homeDisplayWeek = homeCurrentWeek
    }
    val homeTitleWeek = if (homeMode == HomeMode.Day) {
        effectiveCurrentWeek(state.config, homeDisplayDate)
    } else {
        homeDisplayWeek
    }
    val returnHomeToCurrentDateAndWeek = {
        homeDisplayDate = LocalDate.now()
        homeDisplayWeek = homeCurrentWeek
    }

    LaunchedEffect(state.config.hideFromRecents) {
        hideFromRecentsEnabled = state.config.hideFromRecents
    }

    var initialLifecycleStartSeen by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (initialLifecycleStartSeen) {
                    viewModel.refreshNotifications()
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
    BackHandler(enabled = addMenuExpanded) {
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
        LocalStartupPhase provides startupPhase,
        LocalGlassQuality provides glassQuality,
        LocalStartupEntranceSpec provides startupEntranceSpec
    ) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        Scaffold(
            containerColor = ComposeColor.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopBarEntranceContainer(
                    phase = startupPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (screen is Screen.Home) HomeTopOverlayHeight else HomeInitialTopInset)
                ) {
                    if (screen is Screen.Home) {
                        AnimatedVisibility(
                            visible = homeContentUnderTopBar,
                            modifier = Modifier.align(Alignment.TopCenter),
                            enter = fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)),
                            exit = fadeOut(animationSpec = spring(dampingRatio = 0.95f, stiffness = 560f))
                        ) {
                            HomeTopGradientBlur(
                                config = state.config,
                                backdrop = chromeBackdrop,
                                modifier = Modifier
                            )
                        }
                    }
                    AppTopBar(
                        screen = screen,
                        state = state,
                        settingsPage = SettingsPage.Root,
                        eduImportPage = EduImportPage.SelectSchool,
                        backdrop = chromeBackdrop,
                        homeMode = homeMode,
                        onHomeModeChange = { homeMode = it },
                        homeDisplayDate = homeDisplayDate,
                        homeDisplayWeek = homeTitleWeek,
                        onReturnHomeToCurrentWeek = returnHomeToCurrentDateAndWeek,
                        addMenuExpanded = addMenuExpanded,
                        onAddButtonPositioned = { addButtonBounds = it },
                        addMenuRendering = renderAddMenu,
                        onToggleAddMenu = {
                            val next = !addMenuExpanded
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
                        if (!state.loaded) {
                            HomeWallpaperLoadingMask(state.config)
                        } else {
                            HomeWallpaper(state.config, wallpaperImages, startupPhase)
                            if (state.config.hasAnyWallpaper() && wallpaperImages.source == null) {
                                HomeWallpaperLoadingMask(state.config)
                            } else if (!state.config.hasAnyWallpaper()) {
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
                                    state = state,
                                    mode = homeMode,
                                    weekCardHeight = weekCardHeight.dp,
                                    displayWeek = homeDisplayWeek,
                                    displayDate = homeDisplayDate,
                                    backdrop = backgroundBackdrop,
                                    weekHeaderBackdrop = backgroundBackdrop,
                                    onSwipeWeek = { delta -> homeDisplayWeek = (homeDisplayWeek + delta).coerceIn(1, state.config.totalWeeks.coerceAtLeast(1)) },
                                    onSwipeDay = { delta -> homeDisplayDate = homeDisplayDate.plusDays(delta.toLong()) },
                                    onContentUnderTopBarChange = { homeContentUnderTopBar = it },
                                    onCourseClick = { course, week, sourceBounds ->
                                        courseEditSourceBounds = sourceBounds
                                        homeDialog = HomeDialog.EditCourse(course, week)
                                    },
                                    onScheduleLongPress = {
                                        addMenuExpanded = false
                                        showPersonalizePanel = false
                                        showScheduleEntryPill = true
                                    }
                                )
                            }
                            Screen.Config -> Box(Modifier.fillMaxSize().padding(padding)) {
                                SettingsScreen(
                                        page = SettingsPage.Root,
                                        state = state,
                                        backdrop = backgroundBackdrop,
                                        onPageChange = {
                                            context.startActivity(
                                                Intent(context, SettingsDetailActivity::class.java)
                                                    .putExtra(SettingsDetailPageExtra, it.name)
                                            )
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
                                config = state.config,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            if (screen is Screen.Home && renderAddMenu) {
                AnimatedVisibility(
                    visible = addMenuExpanded,
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
                        expanded = addMenuExpanded,
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
            if (screen is Screen.Home) {
                ScheduleManagerEntryPill(
                    visible = showScheduleEntryPill,
                    backdrop = chromeBackdrop,
                    config = state.config,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(22f),
                    onClick = {
                        showScheduleEntryPill = false
                        val intent = Intent(context, ScheduleManagerActivity::class.java)
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            activity.startActivityWithScheduleDepthTransition(intent)
                        } else {
                            context.startActivity(intent)
                        }
                    },
                    onDismiss = { showScheduleEntryPill = false }
                )
            } else {
                showScheduleEntryPill = false
            }
            if (screen is Screen.Home || screen is Screen.Config) {
                DockEntranceContainer(
                    phase = startupPhase,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    FloatingDock(
                        selected = screen,
                        backdrop = chromeBackdrop,
                        config = state.config,
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
                    enter = popEnterTransition(),
                    exit = popExitTransition(),
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
                            modifier = Modifier,
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
    }

    // Inline EditCourse overlay \u2014 enables shared element transition from course card
    val isInlineEditVisible = renderedHomeDialog is HomeDialog.EditCourse && (renderedHomeDialog as HomeDialog.EditCourse).course != null
    BackHandler(enabled = isInlineEditVisible && homeDialogVisible) {
        dismissHomeDialog()
    }
    if (isInlineEditVisible) {
        val editDialog = renderedHomeDialog as HomeDialog.EditCourse
        val editCourse = editDialog.course!!
        val sharedScope = LocalSharedTransitionScope.current.takeIf { editingCourseId != null && editCourse.id > 0L }
        val useSharedBounds = startupPhase == StartupPhase.FullQuality && sharedScope != null
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
        ) {
            AnimatedVisibility(
                visible = homeDialogVisible,
                enter = fadeIn(animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f)),
                exit = fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 620f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { dismissHomeDialog() },
                    contentAlignment = Alignment.Center
                ) {
                    CourseEditSharedDialog(
                        visible = homeDialogVisible,
                        course = editCourse,
                        useSharedBounds = useSharedBounds,
                        sharedScope = sharedScope,
                        sourceBounds = courseEditSourceBounds,
                        backdrop = chromeBackdrop,
                        config = state.config,
                        state = state,
                        onCancel = { dismissHomeDialog() },
                        onSave = {
                            if (courseWeeksChanged(editCourse, it)) {
                                viewModel.updateCourse(it)
                                dismissHomeDialog()
                            } else {
                                homeDialog = HomeDialog.ApplyCourseEdit(editCourse, it, editDialog.targetWeek ?: effectiveCurrentWeek(state.config))
                            }
                        },
                        onDelete = {
                            homeDialog = HomeDialog.ApplyCourseDelete(it, editDialog.targetWeek ?: effectiveCurrentWeek(state.config))
                        }
                    )
                }
            }
        }
    }

    // Dialog-based dialogs for all other types (including EditCourse without a source card)
    renderedHomeDialog?.let { dialog ->
        if (dialog !is HomeDialog.EditCourse || dialog.course == null) {
        Dialog(onDismissRequest = { dismissHomeDialog() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            AnimatedVisibility(
                visible = homeDialogVisible,
                enter = popEnterTransition(),
                exit = popExitTransition()
            ) {
                val useKyantDialog = dialog is HomeDialog.ApplyCourseEdit || dialog is HomeDialog.ApplyCourseDelete
                val dialogContent: @Composable ColumnScope.() -> Unit = {
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
                    HomeDialog.ImportSchedule -> NormalizedDialogScaffold(title = "\u624B\u52A8\u5BFC\u5165\u6574\u5F20\u8BFE\u8868", onCancel = { dismissHomeDialog() }, backdrop = chromeBackdrop, config = state.config) {
                        NormalizedAiManualImportScreen(state, backdrop = chromeBackdrop, onParsed = { homeDialog = HomeDialog.ConfirmImport(it) })
                    }
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
                        onCancel = { homeDialog = HomeDialog.ImportSchedule },
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
                        onCancel = { homeDialog = HomeDialog.EditCourse(dialog.original, dialog.targetWeek) }
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
                        onCancel = { homeDialog = HomeDialog.EditCourse(dialog.course, dialog.targetWeek) }
                    )
                    }
                }
                if (useKyantDialog) {
                    KyantLiquidDialog(
                        backdrop = chromeBackdrop,
                        config = state.config
                    ) {
                        dialogContent()
                    }
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
private val HomeInitialTopInset = 122.dp

@Composable
private fun detailContentTopPadding(): Dp {
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
    val shape = RoundedCornerShape(32.dp)
    val textColor = glassForegroundColor(config)
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) {
        ComposeColor.White.copy(alpha = 0.18f)
    } else {
        ComposeColor(0xFF121212).copy(alpha = 0.28f)
    }
    val panelModifier = modifier
        .fillMaxWidth(0.92f)
        .heightIn(max = 600.dp)
    val contentBlock: @Composable () -> Unit = {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = panelModifier,
            shape = shape,
            surfaceColor = surfaceColor
        ) {
            Box(
                Modifier
                    .clip(shape)
                    .background(ComposeColor.Black.copy(alpha = if (lightGlass) 0.12f else 0.20f))
            ) {
                contentBlock()
            }
        }
    } else {
        Box(
            modifier = panelModifier
                .clip(shape)
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
        ) {
            contentBlock()
        }
    }
}

@Composable
fun KyantLiquidDialog(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val lightGlass = glassUsesLightStyle(config)
    val contentColor = if (lightGlass) ComposeColor.Black else ComposeColor.White
    val containerColor = if (lightGlass) {
        ComposeColor(0xFFFAFAFA).copy(alpha = 0.60f)
    } else {
        ComposeColor(0xFF121212).copy(alpha = 0.40f)
    }
    val panelModifier = modifier
        .fillMaxWidth(0.88f)
        .heightIn(max = 560.dp)
    if (backdrop != null) {
        Column(
            modifier = panelModifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(48.dp) },
                effects = {
                    colorControls(
                        brightness = if (lightGlass) 0.2f else 0f,
                        saturation = 1.5f
                    )
                    vibrancy()
                    blur(if (lightGlass) 8.dp.toPx() else 5.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = { drawRect(containerColor) }
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        Column(
            modifier = panelModifier
                .clip(RoundedCornerShape(48.dp))
                .background(containerColor)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}

@Composable
fun DetailActivityScaffold(
    title: String,
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    showTopGradientBlur: Boolean = true,
    isolateContentFromBackdrop: Boolean = false,
    content: @Composable (Backdrop?) -> Unit
) {
    val pageConfig = settingsVisualConfig(config)
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp() }
    val topBarBottom = statusTop + DetailTopBarHeight
    val overlayHeight = topBarBottom + DetailTopOverlayExtra
    val logRecording by DiagnosticLogCapture.recording.collectAsState()
    Box(Modifier.fillMaxSize().background(settingsPageBackground(pageConfig))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(settingsPageBackground(pageConfig))
                .layerBackdrop(backgroundBackdrop)
        )
        if (isolateContentFromBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                content(null)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
            ) {
                content(backgroundBackdrop)
            }
        }
        if (showTopGradientBlur) {
            HomeTopGradientBlur(
                config = pageConfig,
                backdrop = chromeBackdrop,
                height = overlayHeight,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            DetailTopBar(title = title, config = pageConfig, backdrop = chromeBackdrop, onBack = onBack)
        }
        DiagnosticLogStopOverlay(
            visible = logRecording,
            config = pageConfig,
            backdrop = chromeBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(40f)
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopGradientBlur(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    height: Dp = HomeTopOverlayHeight,
    modifier: Modifier = Modifier
) {
    val tintColor = if (glassUsesLightStyle(config)) ComposeColor.White else ComposeColor(0xFF111111)
    val fallbackTint = Brush.verticalGradient(
        0f to tintColor.copy(alpha = 0.42f),
        0.42f to tintColor.copy(alpha = 0.18f),
        1f to ComposeColor.Transparent
    )
    val baseModifier = modifier.fillMaxWidth().height(height)
    Box(
        modifier = if (backdrop != null) {
            baseModifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(28f.dp.toPx())
                    runtimeShaderEffect(
                        "HomeTopProgressiveBlur",
                        """
uniform shader content;

uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.18, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.24, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}""",
                        "content"
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tintColor)
                        setFloatUniform("tintIntensity", 0.18f)
                    }
                }
            )
        } else {
            baseModifier.background(fallbackTint)
        }
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
    onReturnHomeToCurrentWeek: () -> Unit,
    addMenuExpanded: Boolean,
    onAddButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    addMenuRendering: Boolean,
    onToggleAddMenu: () -> Unit,
    showPersonalize: Boolean,
    onTogglePersonalize: () -> Unit,
    onBackHome: () -> Unit
) {
    val homeTextColor = homeForegroundColor(state.config)
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
                                SettingsPage.Schedule -> "课表设置"
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
                    HomeIconButton(backdrop, state.config, R.drawable.ic_tune, "个性化", selected = showPersonalize, onClick = onTogglePersonalize)
                    HomeAddButton(backdrop, state.config, addMenuExpanded, addMenuRendering, onAddButtonPositioned, onToggleAddMenu)
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
            tint = ComposeColor(0xFF0A84FF),
            modifier = Modifier.onGloballyPositioned { onPositioned(it.boundsInRoot()) },
            onClick = onClick
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CourseBoundsSource(
    courseId: Long,
    visible: Boolean,
    sharedScope: SharedTransitionScope?,
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable (Modifier) -> Unit
) {
    if (sharedScope == null || courseId <= 0L) {
        content(modifier)
        return
    }
    Box(modifier = modifier) {
        with(sharedScope) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                exit = fadeOut(animationSpec = tween(durationMillis = 90)),
                modifier = Modifier.fillMaxSize()
            ) {
                val sharedState = rememberSharedContentState(key = "course_bounds_${courseId}")
                content(
                    Modifier
                        .fillMaxSize()
                        .sharedBounds(
                            sharedContentState = sharedState,
                            animatedVisibilityScope = this,
                            enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
                            boundsTransform = BoundsTransform { _, _ ->
                                spring(dampingRatio = 0.78f, stiffness = 520f)
                            },
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            clipInOverlayDuringTransition = OverlayClip(shape)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CourseEditSharedDialog(
    visible: Boolean,
    course: CourseEntity,
    useSharedBounds: Boolean,
    sharedScope: SharedTransitionScope?,
    sourceBounds: Rect?,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    state: AppState,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit
) {
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(visible, course.id) {
        if (visible) {
            contentAlpha.snapTo(0f)
            delay(100)
            contentAlpha.animateTo(1f, tween(durationMillis = 120))
        } else {
            contentAlpha.animateTo(0f, tween(durationMillis = 80))
        }
    }
    val fallbackModifier = if (useSharedBounds) {
        Modifier
    } else {
        Modifier.courseEditSourceTransform(sourceBounds, visible)
    }
    val dialogContent: @Composable () -> Unit = {
        CenterLiquidDialog(
            backdrop = backdrop,
            config = config,
            modifier = fallbackModifier
        ) {
            Box(Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                NormalizedCourseEditorScreen(
                    state = state,
                    initialCourse = course,
                    onCancel = onCancel,
                    onSave = onSave,
                    onDelete = onDelete,
                    backdrop = backdrop
                )
            }
        }
    }
    if (useSharedBounds && sharedScope != null) {
        with(sharedScope) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                exit = fadeOut(animationSpec = tween(durationMillis = 90))
            ) {
                val sharedState = rememberSharedContentState(key = "course_bounds_${course.id}")
                Box(
                    modifier = Modifier
                        .sharedBounds(
                            sharedContentState = sharedState,
                            animatedVisibilityScope = this,
                            enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
                            boundsTransform = BoundsTransform { _, _ ->
                                spring(dampingRatio = 0.78f, stiffness = 520f)
                            },
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(26.dp))
                        )
                ) {
                    dialogContent()
                }
            }
        }
    } else {
        dialogContent()
    }
}

@Composable
fun Modifier.courseEditSourceTransform(sourceBounds: Rect?, visible: Boolean): Modifier {
    if (sourceBounds == null) return this
    var dialogBounds by remember { mutableStateOf<Rect?>(null) }
    val dialogView = LocalView.current
    val progress by animateFloatAsState(
        targetValue = if (visible && dialogBounds != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
        label = "course-edit-source-progress"
    )
    return this
        .onGloballyPositioned { coordinates ->
            val wb = coordinates.boundsInWindow()
            val loc = IntArray(2)
            dialogView.getLocationOnScreen(loc)
            dialogBounds = if (loc[0] == 0 && loc[1] == 0) wb
            else wb.translate(Offset(loc[0].toFloat(), loc[1].toFloat()))
        }
        .drawWithContent {
            val target = dialogBounds
            if (target == null || progress >= 1f) {
                drawContent()
                return@drawWithContent
            }
            // Map card bounds to dialog-local coordinates
            val srcLeft = sourceBounds.left - target.left
            val srcTop = sourceBounds.top - target.top
            val srcRight = sourceBounds.right - target.left
            val srcBottom = sourceBounds.bottom - target.top
            // Animate clip rect from card position to full dialog bounds
            val e = progress
            val clipLeft = srcLeft + (0f - srcLeft) * e
            val clipTop = srcTop + (0f - srcTop) * e
            val clipRight = srcRight + (target.width - srcRight) * e
            val clipBottom = srcBottom + (target.height - srcBottom) * e
            val srcRadius = 12.dp.toPx()
            val dstRadius = 26.dp.toPx()
            val clipRadius = srcRadius + (dstRadius - srcRadius) * e
            val clipPath = Path().apply {
                addRoundRect(RoundRect(Rect(clipLeft, clipTop, clipRight, clipBottom), CornerRadius(clipRadius)))
            }
            clipPath(clipPath) {
                this@drawWithContent.drawContent()
            }
        }
}

@Composable
fun TopBackButton(backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit, modifier: Modifier = Modifier.padding(start = 8.dp).size(42.dp)) {
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            surfaceColor = if (glassUsesLightStyle(config)) ComposeColor.White.copy(alpha = 0.26f) else ComposeColor(0xFF121212).copy(alpha = 0.28f),
            contentPadding = PaddingValues(0.dp),
            blurRadius = 10.dp,
            lensHeight = 30.dp,
            lensAmount = 38.dp,
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
    tint: ComposeColor = ComposeColor.Unspecified,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lightGlass = glassUsesLightStyle(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.padding(end = 7.dp).size(42.dp),
            tint = tint,
            surfaceColor = when {
                selected && lightGlass -> ComposeColor.Black.copy(alpha = 0.025f)
                selected -> ComposeColor.White.copy(alpha = 0.055f)
                lightGlass -> ComposeColor.White.copy(alpha = 0.070f)
                else -> ComposeColor(0xFF121212).copy(alpha = 0.085f)
            },
            contentPadding = PaddingValues(0.dp),
            blurRadius = 2.5.dp,
            lensHeight = 12.dp,
            lensAmount = 24.dp,
            chromaticAberration = false
        ) {
            Icon(painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier.padding(end = 7.dp).size(42.dp),
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
    val sinkOffset by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 190
                    0.dp at 0
                    49.dp at 150 using addMenuEase
                    46.dp at 190 using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = 190
                    46.dp at 0
                    (-3).dp at 150 using addMenuEase
                    0.dp at 190 using addMenuSettleEase
                }
            }
        },
        label = "add-menu-sink"
    ) { if (it) 46.dp else 0.dp }
    val width by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 190
                    42.dp at 0
                    42.dp at 42
                    212.dp at 150 using addMenuEase
                    202.dp at 190 using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = 190
                    202.dp at 0
                    38.dp at 150 using addMenuEase
                    42.dp at 190 using addMenuSettleEase
                }
            }
        },
        label = "add-menu-width"
    ) { if (it) 202.dp else 42.dp }
    val height by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 190
                    42.dp at 0
                    42.dp at 42
                    168.dp at 150 using addMenuEase
                    160.dp at 190 using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = 190
                    160.dp at 0
                    38.dp at 150 using addMenuEase
                    42.dp at 190 using addMenuSettleEase
                }
            }
        },
        label = "add-menu-height"
    ) { if (it) 160.dp else 42.dp }
    val radius by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 190
                    50.dp at 0
                    24.dp at 150 using addMenuEase
                    26.dp at 190 using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = 190
                    26.dp at 0
                    52.dp at 150 using addMenuEase
                    50.dp at 190 using addMenuSettleEase
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
    ) { if (it) 14.dp else 5.dp }
    val menuReboundScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 190
                    1f at 0
                    1.018f at 150 using addMenuEase
                    1f at 190 using addMenuSettleEase
                }
            } else {
                keyframes {
                    durationMillis = 190
                    1f at 0
                    0.985f at 150 using addMenuEase
                    1f at 190 using addMenuSettleEase
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
    val itemHeight = 48.dp
    val itemSpacing = 4.dp
    val menuPadding = 8.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemStepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val menuPaddingPx = with(density) { menuPadding.toPx() }
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
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
        .offset(y = sinkOffset)
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
                        (22.dp + 8.dp * contentAlpha + 6.dp * pressProgress).toPx(),
                        depthEffect = true,
                        chromaticAberration = false
                    )
                },
                highlight = { Highlight.Default.copy(alpha = 0.04f + 0.08f * pressProgress) },
                shadow = { Shadow(alpha = (if (lightGlass) 0.10f else 0.18f) + 0.08f * pressProgress) },
                innerShadow = { InnerShadow(radius = 8.dp + 3.dp * contentAlpha, alpha = 0.10f + 0.08f * pressProgress) },
                layerBlock = {
                    val scale = 1f + 0.016f * pressProgress
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect((if (lightGlass) ComposeColor.White else ComposeColor(0xFF050505)).copy(alpha = if (lightGlass) 0.16f else 0.26f))
                    drawRect(ComposeColor.Black.copy(alpha = if (lightGlass) 0.018f else 0.055f))
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
    val lightGlass = glassUsesLightStyle(config)
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
    val bottomOffset = (bottomInset + 8.dp).coerceAtLeast(8.dp)
    val dockTextColor = glassForegroundColor(config)
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
                    blurRadius = 2.dp,
                    containerAlpha = 0.075f,
                    lensHeight = 12.dp,
                    lensAmount = 24.dp,
                    indicatorWidthOverflow = 24.dp,
                    indicatorHeightOverflow = 6.dp,
                    chromaticAberrationEnabled = false,
                    isLightThemeOverride = lightGlass
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
            enter = fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 560f)) +
                scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 620f)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = 0.70f, stiffness = 640f)),
            exit = fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 620f)) +
                scaleOut(targetScale = 0.88f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f)) +
                slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f))
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
    var wallpaperBlurDisplay by remember { mutableFloatStateOf(state.config.wallpaperBlur) }
    var wallpaperBrightnessDisplay by remember { mutableFloatStateOf(state.config.wallpaperBrightness.coerceIn(0.35f, 1f)) }
    var cardAlphaDisplay by remember { mutableFloatStateOf(state.config.cardAlpha) }
    var courseCardBlurDisplay by remember { mutableFloatStateOf(state.config.courseCardBlur.coerceIn(0f, 10f) / 10f * 100f) }
    var courseCardFontDisplay by remember { mutableFloatStateOf(state.config.courseCardFontScale) }
    var sliderTouchActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.config.wallpaperBlur) { wallpaperBlurDisplay = state.config.wallpaperBlur }
    LaunchedEffect(state.config.wallpaperBrightness) { wallpaperBrightnessDisplay = state.config.wallpaperBrightness.coerceIn(0.35f, 1f) }
    LaunchedEffect(state.config.cardAlpha) { cardAlphaDisplay = state.config.cardAlpha }
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
                Text("壁纸模糊 ${wallpaperBlurDisplay.toInt()}dp", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(state.config.wallpaperBlur, { onUpdateConfig(state.config.copy(wallpaperBlur = it)) }, 0f..30f, backdrop, onLiveValueChange = { wallpaperBlurDisplay = it }, snapValue = 0f, onSliderTouchActiveChange = { sliderTouchActive = it })
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
                        0xFFFFD166, 0xFFE8D7FF, 0xFFD7C0FF, 0xFFE8EAED
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
                }
                LiquidMenuButton(backdrop, "从壁纸取色", onClick = onSampleWallpaperColor)
                val alphaLabel = if (state.config.courseCardGlassEnabled) "课程卡片着色强度" else "课程卡片不透明度"
                Text("$alphaLabel ${(cardAlphaDisplay * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                LiquidControlSlider(state.config.cardAlpha, { onUpdateConfig(state.config.copy(cardAlpha = it)) }, 0.35f..1f, backdrop, onLiveValueChange = { cardAlphaDisplay = it }, snapValue = 0.5f, onSliderTouchActiveChange = { sliderTouchActive = it })
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
            blurRadius = if (useRoundIcon) 7.dp else 9.dp,
            lensHeight = if (useRoundIcon) 18.dp else 26.dp,
            lensAmount = if (useRoundIcon) 24.dp else 32.dp,
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
    minLines: Int = 1
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
            .clip(RoundedCornerShape(if (minLines == 1) 50 else 10))
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
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
            val state by viewModel.state.collectAsState()
            CourseScheduleTheme(config = state.config) {
                val scheduleEditState = remember(state, customizeScheduleId) {
                    if (customizeScheduleId != null) scheduleConfigStateForEdit(state, customizeScheduleId) else state
                }
                DetailActivityScaffold(
                    title = section.title(),
                    config = state.config,
                    onBack = { finish() }
                ) { backdrop ->
                    when (section) {
                        SettingsPage.General -> GeneralSettingsScreen(state, backdrop, viewModel::savePersonalization)
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
                                    Intent(this, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Download.name)
                                )
                            },
                            onDonate = {
                                startActivity(
                                    Intent(this, SettingsDetailActivity::class.java)
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
            val state by viewModel.state.collectAsState()
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
            val state by viewModel.state.collectAsState()
            val adapter = remember { eduAdapterFromIntentKey(intent.getStringExtra(EduAdapterExtra)) }
            var pendingDraft by remember { mutableStateOf<ImportDraft?>(null) }
            CourseScheduleTheme(config = state.config) {
                if (pendingDraft == null) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(adapter?.school?.name ?: "教务导入") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        if (adapter == null) {
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                MissingCourseScreen(onBack = { finish() })
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                EduImportActivityScreen(
                                    state = state,
                                    adapter = adapter,
                                    backdrop = null,
                                    useDetailTopPadding = false,
                                    onParsed = { draft -> pendingDraft = draft }
                                )
                            }
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
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(currentValue, valueRange, onSliderTouchActiveChange) {
                    val thumbInsetPx = with(density) { 10.dp.toPx() }
                    val thumbHitRadiusPx = with(density) { 18.dp.toPx() }
                    val dragLockThresholdPx = with(density) { 4.dp.toPx() }
                    val thumbFraction = ((currentValue.coerceIn(valueRange) - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    val thumbCenterX = thumbInsetPx + (size.width - thumbInsetPx * 2f).coerceAtLeast(1f) * thumbFraction
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
                                            downX = change.position.x
                                            abs(change.position.x - thumbCenterX) <= thumbHitRadiusPx
                                        } ?: false
                                    }
                                    val currentX = event.changes.firstOrNull { it.pressed }?.position?.x ?: downX
                                    val draggedThumb = abs(currentX - downX) >= dragLockThresholdPx
                                    if (thumbPress && !scrollLocked && (eventTimeMillis - downTimeMillis >= 80L || draggedThumb)) {
                                        scrollLocked = true
                                        onSliderTouchActiveChange(true)
                                    }
                                } else if (pressed) {
                                    pressed = false
                                    downTimeMillis = 0L
                                    thumbPress = false
                                    downX = 0f
                                    if (scrollLocked) {
                                        scrollLocked = false
                                        onSliderTouchActiveChange(false)
                                    }
                                }
                            }
                        }
                    } finally {
                        onSliderTouchActiveChange(false)
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
                            .then(if (markerClickable) Modifier.clickable(onClick = onSnapClick) else Modifier),
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
        LaunchedEffect(value, valueRange) {
            if (!localEditPending) {
                val coerced = value.coerceIn(valueRange)
                if (abs(localValue - coerced) > visibilityThreshold) {
                    localValue = coerced
                    onLiveValueChange?.invoke(coerced)
                }
            }
        }
        LaunchedEffect(localValue, localEditPending) {
            if (localEditPending) {
                delay(220)
                val committedValue = localValue.coerceIn(valueRange)
                onValueChange(committedValue)
                localEditPending = false
            }
        }
        SliderWithSnapMarker(
            currentValue = localValue,
            onSnapClick = {
                val snap = safeSnapValue ?: return@SliderWithSnapMarker
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                localValue = snap
                onLiveValueChange?.invoke(snap)
                localEditPending = false
                onValueChange(snap)
            }
        ) {
            LiquidSlider(
                value = { localValue },
                onValueChange = {
                    localValue = it.coerceIn(valueRange)
                    onLiveValueChange?.invoke(localValue)
                    localEditPending = true
                },
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        SliderWithSnapMarker(
            currentValue = value,
            onSnapClick = {
                val snap = safeSnapValue ?: return@SliderWithSnapMarker
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onLiveValueChange?.invoke(snap)
                onValueChange(snap)
            }
        ) {
            Slider(
            value = value,
            onValueChange = {
                val bounded = it.coerceIn(valueRange)
                onLiveValueChange?.invoke(bounded)
                onValueChange(bounded)
            },
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
fun HomeDateTitle(
    state: AppState,
    displayDate: LocalDate,
    displayWeek: Int,
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
            "第${displayWeek}周",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.78f),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun HomeModeSwitch(backdrop: Backdrop?, config: ScheduleConfigEntity, mode: HomeMode, onModeChange: (HomeMode) -> Unit) {
    val lightGlass = glassUsesLightStyle(config)
    if (backdrop != null) {
        LiquidBottomTabs(
            selectedTabIndex = { if (mode == HomeMode.Day) 0 else 1 },
            onTabSelected = { index -> onModeChange(if (index == 0) HomeMode.Day else HomeMode.Week) },
            backdrop = backdrop,
            tabsCount = 2,
            modifier = Modifier.padding(end = 12.dp).width(104.dp),
            containerHeight = 42.dp,
            indicatorHeight = 34.dp,
            horizontalPadding = 4.dp,
            blurRadius = 2.5.dp,
            containerAlpha = 0.12f,
            lensHeight = 12.dp,
            lensAmount = 24.dp,
            indicatorWidthOverflow = 8.dp,
            indicatorHeightOverflow = 4.dp,
            isLightThemeOverride = lightGlass
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
    } else {
        GlassPill(backdrop = null, config = config, modifier = Modifier.padding(end = 12.dp).height(42.dp).padding(4.dp)) {
            Row(Modifier.width(104.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    state: AppState,
    mode: HomeMode,
    weekCardHeight: Dp,
    displayWeek: Int,
    displayDate: LocalDate,
    backdrop: Backdrop?,
    weekHeaderBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit,
    onScheduleLongPress: () -> Unit = {},
) {
    val cardColor = remember(state.config.cardColorArgb, state.config.cardAlpha) {
        ComposeColor(state.config.cardColorArgb.toInt()).copy(alpha = state.config.cardAlpha)
    }
    val textColor = homeForegroundColor(state.config)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onScheduleLongPress) {
                detectTapGestures(onLongPress = { onScheduleLongPress() })
            }
    ) {
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
                HomeMode.Day -> DayScheduleScreen(
                    state = state,
                    displayDate = displayDate,
                    displayWeek = effectiveCurrentWeek(state.config, displayDate),
                    cardColor = cardColor,
                    textColor = textColor,
                    backdrop = backdrop,
                    onSwipeDay = onSwipeDay,
                    onContentUnderTopBarChange = onContentUnderTopBarChange,
                    onCourseClick = onCourseClick
                )
                HomeMode.Week -> SinglePillWeekScheduleScreen(
                    state = state,
                    displayWeek = displayWeek,
                    cardHeight = weekCardHeight,
                    cardColor = cardColor,
                    textColor = textColor,
                    backdrop = backdrop,
                    headerBackdrop = weekHeaderBackdrop,
                    onSwipeWeek = onSwipeWeek,
                    onContentUnderTopBarChange = onContentUnderTopBarChange,
                    onCourseClick = { course, week, sourceBounds -> onCourseClick(course, week, sourceBounds) }
                )
            }
        }
    }
}

@Composable
fun rememberHomeWallpaperBitmap(config: ScheduleConfigEntity): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val wallpaperKey = "${config.wallpaperUri}|${config.defaultWallpaperStyle}|$useDarkDefaultWallpaper"
    val cachedBitmap = remember { mutableStateOf<Bitmap?>(null) }
    return produceState<Bitmap?>(initialValue = cachedBitmap.value, wallpaperKey) {
        val loaded = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context.applicationContext, config, useDarkDefaultWallpaper)
        }
        cachedBitmap.value = loaded
        value = loaded
    }
}

@Composable
fun HomeWallpaper(config: ScheduleConfigEntity, images: HomeWallpaperImages, phase: StartupPhase) {
    val targetBitmap = images.source
    var visibleBitmap by remember { mutableStateOf<Bitmap?>(targetBitmap) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crossfadeTarget by remember { mutableFloatStateOf(1f) }
    val crossfadeAlpha by animateFloatAsState(
        targetValue = crossfadeTarget,
        animationSpec = tween(durationMillis = 140),
        label = "wallpaper-crossfade"
    )
    LaunchedEffect(targetBitmap) {
        if (targetBitmap != visibleBitmap) {
            previousBitmap = visibleBitmap
            visibleBitmap = targetBitmap
            crossfadeTarget = 0f
            withFrameNanos { }
            crossfadeTarget = 1f
            delay(160)
            previousBitmap = null
        }
    }
    val uri = config.wallpaperUri
    if (!uri.isNullOrBlank()) {
        val bitmap = visibleBitmap ?: return
        bitmap.let {
            Box(modifier = Modifier.fillMaxSize()) {
                previousBitmap?.let { old ->
                    HomeWallpaperLayer(
                        bitmap = old,
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = true,
                        blurRadius = images.blurBucket.dp
                    )
                }
                HomeWallpaperLayer(
                    bitmap = it,
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = true,
                    blurRadius = images.blurBucket.dp
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
                        bitmap = old,
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = false,
                        blurRadius = images.blurBucket.dp
                    )
                }
                HomeWallpaperLayer(
                    bitmap = it,
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = false,
                    blurRadius = images.blurBucket.dp
                )
                WallpaperToneOverlay(config)
            }
        }
        return
    }
}

@Composable
private fun HomeWallpaperLayer(
    bitmap: Bitmap,
    config: ScheduleConfigEntity,
    alpha: Float,
    useSavedCrop: Boolean,
    blurRadius: Dp
) {
    val layerModifier = if (blurRadius > 0.dp) {
        Modifier
            .fillMaxSize()
            .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .graphicsLayer(alpha = alpha)
    } else {
        Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = alpha)
    }
    FocusCroppedWallpaper(
        bitmap = bitmap,
        config = config,
        modifier = layerModifier,
        useSavedCrop = useSavedCrop
    )
}

private fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean {
    return !wallpaperUri.isNullOrBlank() || defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN
}

@Composable
private fun HomeWallpaperLoadingMask(config: ScheduleConfigEntity) {
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
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogLiquidButton(backdrop, "取消", onCancel, role = DialogButtonRole.Cancel)
            Text("应用修改", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = glassForegroundColor(config))
            Spacer(Modifier.width(42.dp))
        }
        Text(
            "要将“${original.name}”的修改应用到哪里？",
            style = MaterialTheme.typography.bodyMedium,
            color = glassForegroundColor(config)
        )
        Text(
            "仅单次只修改当前周；应用全部会修改这门课的所有周。",
            style = MaterialTheme.typography.bodySmall,
            color = glassForegroundColor(config).copy(alpha = 0.72f),
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DialogLiquidButton(
                backdrop = backdrop,
                label = "仅单次",
                onClick = onSingle,
                modifier = Modifier.weight(1f),
                role = DialogButtonRole.Neutral
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = "应用全部",
                onClick = onAll,
                modifier = Modifier.weight(1f),
                role = DialogButtonRole.Confirm
            )
        }
    }
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
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogLiquidButton(backdrop, "取消", onCancel, role = DialogButtonRole.Cancel)
            Text("应用删除", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = glassForegroundColor(config))
            Spacer(Modifier.width(42.dp))
        }
        Text(
            "要将“${course.name}”从哪里删除？",
            style = MaterialTheme.typography.bodyMedium,
            color = glassForegroundColor(config)
        )
        Text(
            "仅单次只删除当前周；应用全部会删除这门课的所有周。",
            style = MaterialTheme.typography.bodySmall,
            color = glassForegroundColor(config).copy(alpha = 0.72f),
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DialogLiquidButton(
                backdrop = backdrop,
                label = "仅单次",
                onClick = onSingle,
                modifier = Modifier.weight(1f),
                role = DialogButtonRole.Neutral
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = "应用全部",
                onClick = onAll,
                modifier = Modifier.weight(1f),
                role = DialogButtonRole.Cancel
            )
        }
    }
}

private fun courseWeeksChanged(original: CourseEntity, edited: CourseEntity): Boolean {
    return original.weeks.sorted() != edited.weeks.sorted() || original.weekParity != edited.weekParity
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
    val bitmap = remember(config.wallpaperUri, config.defaultWallpaperStyle, useDarkDefaultWallpaper) {
        loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)
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
                        bitmap ?: return@detectTapGestures
                        val orientation = if (previewSize.height >= previewSize.width) WallpaperPreviewOrientation.Portrait else WallpaperPreviewOrientation.Landscape
                        val cropState = if (!config.wallpaperUri.isNullOrBlank()) config.wallpaperCropState(orientation) else WallpaperCropState()
                        val color = sampleCroppedBitmapColor(bitmap, previewSize, tap.x, tap.y, cropState)
                        if (color != null) sampledColor = color
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                FocusCroppedWallpaper(
                    bitmap = bitmap,
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
    return when {
        config.wallpaperBrightness < 0.72f -> ComposeColor.White
        config.homeTextLight -> ComposeColor.White
        else -> ComposeColor.Black
    }
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
fun DayScheduleScreen(
    state: AppState,
    displayDate: LocalDate,
    displayWeek: Int,
    cardColor: ComposeColor,
    textColor: ComposeColor,
    backdrop: Backdrop?,
    onSwipeDay: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(displayDate) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            horizontalDrag <= -80f -> onSwipeDay(1)
                            horizontalDrag >= 80f -> onSwipeDay(-1)
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = displayDate,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (
                    fadeIn(tween(170, delayMillis = 35)) +
                        slideInHorizontally(tween(240)) { direction * it / 4 }
                    ) togetherWith (
                    fadeOut(tween(120)) +
                        slideOutHorizontally(tween(220)) { -direction * it / 5 }
                    ) using SizeTransform(clip = false)
            },
            label = "day-date-switch"
        ) { targetDate ->
            val targetWeek = effectiveCurrentWeek(state.config, targetDate)
            val targetWeekday = targetDate.dayOfWeek.toChineseWeekday()
            val dayCourses = remember(state.courses, targetWeek, targetWeekday) {
                state.courses.filter { course ->
                    course.weekday == targetWeekday &&
                        targetWeek in course.weeks &&
                        parityMatches(course.weekParity, targetWeek)
                }.sortedWith(compareBy<CourseEntity> { it.periods.minOrNull() ?: Int.MAX_VALUE }.thenBy { it.name })
            }
            val listState = rememberLazyListState()
            val contentUnderTopBar by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            LaunchedEffect(contentUnderTopBar) {
                onContentUnderTopBarChange(contentUnderTopBar)
            }
            LazyColumn(
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
                if (dayCourses.isEmpty()) item { Text("这一天没有课程", color = textColor) }
                itemsIndexed(dayCourses, key = { _, it -> it.id }) { index, course ->
                    DayTimelineCourse(course, targetWeek, state.periods, cardColor, backdrop, state.config, onCourseClick, entranceIndex = index)
                }
            }
        }
    }
}

@Composable
fun DayTimelineCourse(course: CourseEntity, currentWeek: Int, periods: List<PeriodEntity>, cardColor: ComposeColor, backdrop: Backdrop?, config: ScheduleConfigEntity, onCourseClick: (CourseEntity, Int, Rect?) -> Unit, entranceIndex: Int = 0) {
    val timePillColor = deepenColor(ComposeColor(config.cardColorArgb.toInt()), 0.16f)
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
fun WeekScheduleScreen(state: AppState, displayWeek: Int, cardHeight: Dp, cardColor: ComposeColor, textColor: ComposeColor, backdrop: Backdrop?, onSwipeWeek: (Int) -> Unit, onCourseClick: (CourseEntity, Int, Rect?) -> Unit) {
    val weekdays = (1..7).toList()
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.toChineseWeekday() - 1).toLong()).plusWeeks((displayWeek - effectiveCurrentWeek(state.config)).toLong())
    val visibleCourses = remember(state, displayWeek) {
        state.courses.filter { course ->
            displayWeek in course.weeks && parityMatches(course.weekParity, displayWeek)
        }
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
                        courses = visibleCourses.filter { it.weekday == day },
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
    headerBackdrop: Backdrop? = backdrop,
    onSwipeWeek: (Int) -> Unit,
    onContentUnderTopBarChange: (Boolean) -> Unit,
    onCourseClick: (CourseEntity, Int, Rect?) -> Unit
) {
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = today
        .minusDays((today.dayOfWeek.toChineseWeekday() - 1).toLong())
        .plusWeeks((displayWeek - effectiveCurrentWeek(state.config)).toLong())
    val visibleCourses = remember(state.courses, displayWeek) {
        state.courses.filter { course ->
            displayWeek in course.weeks && parityMatches(course.weekParity, displayWeek)
        }
    }
    val weekdays = remember(visibleCourses, state.config.hideEmptyWeekends) {
        val weekendHasCourse = visibleCourses.any { it.weekday == 6 || it.weekday == 7 }
        if (state.config.hideEmptyWeekends && !weekendHasCourse) (1..5).toList() else (1..7).toList()
    }
    var previousDisplayWeek by remember { mutableIntStateOf(displayWeek) }
    var weekMotionDirection by remember { mutableIntStateOf(0) }
    val outgoingCourses = remember { mutableStateOf<List<CourseEntity>?>(null) }
    val outgoingWeekdays = remember { mutableStateOf<List<Int>>(emptyList()) }
    val outgoingWeekKey = remember { mutableIntStateOf(displayWeek) }
    val outgoingDirection = remember { mutableIntStateOf(0) }
    val incomingLayerOffset = remember { Animatable(0f) }
    val outgoingLayerOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    LaunchedEffect(displayWeek) {
        val direction = (displayWeek - previousDisplayWeek).coerceIn(-1, 1)
        if (direction != 0) {
            val oldWeek = previousDisplayWeek
            val oldCourses = state.courses.filter { course ->
                oldWeek in course.weeks && parityMatches(course.weekParity, oldWeek)
            }
            val oldWeekendHasCourse = oldCourses.any { it.weekday == 6 || it.weekday == 7 }
            outgoingCourses.value = oldCourses
            outgoingWeekdays.value = if (state.config.hideEmptyWeekends && !oldWeekendHasCourse) (1..5).toList() else (1..7).toList()
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
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    val contentUnderTopBar by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    LaunchedEffect(contentUnderTopBar) {
        onContentUnderTopBarChange(contentUnderTopBar)
    }

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
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(rowHeaderWidth - 4.dp)
                                    .fillMaxHeight(),
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
                                val isToday = day == today.dayOfWeek.toChineseWeekday()
                                val date = weekStart.plusDays((day - 1).toLong())
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
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.width(rowHeaderWidth)) {
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

                    Box(modifier = Modifier.weight(1f)) {
                        outgoingCourses.value?.let { oldCourses ->
                            WeekCourseColumnsLayer(
                                courses = oldCourses,
                                weekdays = outgoingWeekdays.value,
                                periods = state.periods,
                                cardHeight = cardHeight,
                                cardColor = cardColor,
                                backdrop = backdrop,
                                config = state.config,
                                weekMotionDirection = outgoingDirection.intValue,
                                outgoing = true,
                                layerOffset = outgoingLayerOffset,
                                onCourseClick = { course, sourceBounds -> onCourseClick(course, outgoingWeekKey.intValue, sourceBounds) }
                            )
                        }
                        WeekCourseColumnsLayer(
                            courses = visibleCourses,
                            weekdays = weekdays,
                            periods = state.periods,
                            cardHeight = cardHeight,
                            cardColor = cardColor,
                            backdrop = backdrop,
                            config = state.config,
                            weekMotionDirection = weekMotionDirection,
                            outgoing = false,
                            layerOffset = incomingLayerOffset,
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
    val weekdays = (1..7).toList()
    val rowHeaderWidth = 56.dp
    val today = LocalDate.now()
    val weekStart = today
        .minusDays((today.dayOfWeek.toChineseWeekday() - 1).toLong())
        .plusWeeks((displayWeek - effectiveCurrentWeek(state.config)).toLong())
    val visibleCourses = remember(state.courses, displayWeek) {
        state.courses.filter { course ->
            displayWeek in course.weeks && parityMatches(course.weekParity, displayWeek)
        }
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
                            courses = visibleCourses.filter { it.weekday == day },
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
    val surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.18f) else ComposeColor.Black.copy(alpha = 0.14f)
    val textColor = glassForegroundColor(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer(alpha = if (enabled) 1f else 0.35f),
            isInteractive = enabled,
            surfaceColor = surfaceColor,
            height = 34.dp,
            contentPadding = PaddingValues(0.dp),
            blurRadius = 4.dp,
            lensHeight = 34.dp,
            lensAmount = 42.dp,
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
fun WeekHeaderPill(backdrop: Backdrop?, config: ScheduleConfigEntity, selected: Boolean, content: @Composable () -> Unit) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(50),
        tokens = GlassTokens.pill(intensity = 0.95f),
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
    config: ScheduleConfigEntity,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    onCourseClick: (CourseEntity, Rect?) -> Unit
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
                config = config,
                weekMotionDirection = weekMotionDirection,
                weekMotionOutgoing = weekMotionOutgoing,
                dayIndex = dayIndex,
                periodIndex = period.periodIndex,
                layerOffset = layerOffset,
                layerTravel = layerTravel,
                onCourseClick = onCourseClick
            )
            periodCursor += span
        }
    }
}

@Composable
fun WeekCourseColumnsLayer(
    courses: List<CourseEntity>,
    weekdays: List<Int>,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    cardColor: ComposeColor,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    weekMotionDirection: Int,
    outgoing: Boolean,
    layerOffset: Animatable<Float, AnimationVector1D>,
    onCourseClick: (CourseEntity, Rect?) -> Unit
) {
    val density = LocalDensity.current
    val travel = with(density) { (LocalConfiguration.current.screenWidthDp.dp + 96.dp).toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = layerOffset.value }
    ) {
        weekdays.forEach { day ->
            Column(modifier = Modifier.weight(1f)) {
                WeekDayColumn(
                    courses = courses.filter { it.weekday == day },
                    periods = periods,
                    cardHeight = cardHeight,
                    cardColor = cardColor,
                    emptyBackground = ComposeColor.Transparent,
                    backdrop = backdrop,
                    config = config,
                    weekMotionDirection = weekMotionDirection,
                    weekMotionOutgoing = outgoing,
                    dayIndex = day,
                    layerOffset = layerOffset,
                    layerTravel = travel,
                    onCourseClick = onCourseClick
                )
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
    config: ScheduleConfigEntity,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    periodIndex: Int = 1,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    onCourseClick: (CourseEntity, Rect?) -> Unit
) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(2.dp)
            .background(background)
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
                    config = config,
                    weekMotionDirection = weekMotionDirection,
                    weekMotionOutgoing = weekMotionOutgoing,
                    dayIndex = dayIndex,
                    periodIndex = periodIndex,
                    layerOffset = layerOffset,
                    layerTravel = layerTravel,
                    stackIndex = stackIndex,
                    onCourseClick = onCourseClick
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
    config: ScheduleConfigEntity,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    periodIndex: Int = 1,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    stackIndex: Int = 0,
    onCourseClick: (CourseEntity, Rect?) -> Unit
) {
    val locationText = course.location.orEmpty()
    val hasLocation = locationText.isNotBlank()
    val hasTeacher = !course.teacher.isNullOrBlank()
    val courseTextColor = readableOn(cardColor)
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
    val cardView = LocalView.current
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
    val tailModifier = Modifier
        .graphicsLayer {
            val tailX = layerOffset?.let { offset ->
                val progress = (kotlin.math.abs(offset.value) / layerTravel.coerceAtLeast(1f)).coerceIn(0f, 1f)
                tailBase * progress * tailDirection
            } ?: 0f
            translationX = tailX
        }
        .onGloballyPositioned { coordinates ->
            val wb = coordinates.boundsInWindow()
            val loc = IntArray(2)
            cardView.getLocationOnScreen(loc)
            ownBounds = if (loc[0] == 0 && loc[1] == 0) wb
            else wb.translate(Offset(loc[0].toFloat(), loc[1].toFloat()))
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editingId != course.id,
        sharedScope = sharedScope,
        modifier = baseModifier,
        shape = RoundedCornerShape(8.dp)
    ) { sharedModifier ->
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        modifier = sharedModifier.then(startupModifier).then(tailModifier),
        shape = RoundedCornerShape(8.dp),
        onClick = { onCourseClick(course, ownBounds) }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val heightDp = maxHeight.value
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

            val canShowTeacher = hasTeacher && heightDp >= 104f
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

            Column(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    course.name,
                    fontSize = nameFont,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = courseTextColor,
                    maxLines = nameLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasLocation && locationLines > 0) {
                    Text(
                        locationText,
                        fontSize = locationFont,
                        lineHeight = locationLineHeight,
                        fontWeight = FontWeight.Medium,
                        color = courseTextColor.copy(alpha = 0.78f),
                        maxLines = locationLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (canShowTeacher) {
                    Text(
                        course.teacher,
                        fontSize = teacherFont,
                        lineHeight = teacherLineHeight,
                        fontWeight = FontWeight.Normal,
                        color = courseTextColor.copy(alpha = 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    }
}

private fun scaledWeekText(value: TextUnit, fontScale: Float): TextUnit {
    return (value.value / fontScale.coerceAtLeast(1f)).sp
}

private fun androidx.compose.ui.text.TextStyle.scaledCourseCardStyle(scale: Float): androidx.compose.ui.text.TextStyle {
    val safeScale = scale.coerceIn(0.80f, 1.35f)
    val scaledFontSize = if (fontSize == TextUnit.Unspecified) fontSize else (fontSize.value * safeScale).sp
    val scaledLineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else (lineHeight.value * safeScale).sp
    return copy(fontSize = scaledFontSize, lineHeight = scaledLineHeight)
}

@Composable
fun CourseCard(course: CourseEntity, periods: List<PeriodEntity>, showTime: Boolean = true, showWeeks: Boolean = true, cardColor: ComposeColor = MaterialTheme.colorScheme.surfaceVariant, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), onClick: ((Rect?) -> Unit)? = null, entranceIndex: Int? = null, enableSharedTransition: Boolean = true) {
    val textColor = readableOn(cardColor)
    var ownBounds by remember { mutableStateOf<Rect?>(null) }
    val cardView = LocalView.current
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
            val wb = coordinates.boundsInWindow()
            val loc = IntArray(2)
            cardView.getLocationOnScreen(loc)
            ownBounds = if (loc[0] == 0 && loc[1] == 0) wb
            else wb.translate(Offset(loc[0].toFloat(), loc[1].toFloat()))
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editId != course.id,
        sharedScope = sharedScope,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) { sharedModifier ->
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        modifier = sharedModifier.then(entranceModifier),
        shape = RoundedCornerShape(16.dp),
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
    val cardColor = ComposeColor(config.cardColorArgb.toInt()).copy(alpha = config.cardAlpha.coerceIn(0.28f, 1f))
    val textColor = readableOn(cardColor)
    CourseGlassCard(
        backdrop = null,
        config = config,
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

@Composable
fun CourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?
) {
    var name by remember(initialCourse) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher.orEmpty()) }
    var location by remember(initialCourse) { mutableStateOf(initialCourse?.location.orEmpty()) }
    var weekday by remember(initialCourse) { mutableStateOf(initialCourse?.weekday ?: 1) }
    val rawPeriodValues = state.periods.map { it.periodIndex }
    val coursePeriodValues = initialCourse?.periods.orEmpty()
    val periodValues = (rawPeriodValues + coursePeriodValues).distinct().sorted()
    var periodStart by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1)) }
    var periodEnd by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.maxOrNull() ?: periodStart) }
    var weekStart by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.minOrNull() ?: 1) }
    var weekEnd by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.maxOrNull() ?: state.config.totalWeeks) }
    var parity by remember(initialCourse) { mutableStateOf(initialCourse?.weekParity ?: WeekParity.ALL) }
    var note by remember(initialCourse) { mutableStateOf(initialCourse?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var periodInputValid by remember(initialCourse, periodValues) { mutableStateOf(true) }
    val selectedPeriods = if (periodStart <= periodEnd) periodValues.filter { it in periodStart..periodEnd } else emptyList()
    val selectedWeeks = (minOf(weekStart, weekEnd)..maxOf(weekStart, weekEnd)).toList()
    val dialogTextColor = glassForegroundColor(state.config)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NormalizedDialogHeader(
                title = if (initialCourse == null) "添加单节课" else "编辑单节课",
                onCancel = onCancel,
                backdrop = backdrop,
                config = state.config,
                onSave = {
                    when {
                        name.isBlank() -> error = "课程名称不能为空"
                        !periodInputValid -> error = "请先修正节次范围"
                        selectedPeriods.isEmpty() -> error = "请选择节次"
                        selectedWeeks.isEmpty() -> error = "请选择周次"
                        else -> onSave(
                            CourseEntity(
                                id = initialCourse?.id ?: 0,
                                name = name.trim(),
                                teacher = teacher.ifBlank { null },
                                location = location.ifBlank { null },
                                weekday = weekday,
                                periods = selectedPeriods,
                                weeks = selectedWeeks,
                                weekParity = parity,
                                note = note.ifBlank { null },
                                scheduleId = initialCourse?.scheduleId ?: 0
                            )
                        )
                    }
                }
            )
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { OutlinedTextField(location, { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { WheelPicker("星期", (1..7).toList(), weekday, { weekday = it }) { "周" + weekdayLabel(it) } }
        item {
            RangeWheelPicker(
                title = "节次",
                values = periodValues,
                start = periodStart,
                end = periodEnd,
                onStart = { periodStart = it },
                onEnd = { periodEnd = it },
                enforceOrderedInput = true,
                onInputValidChange = { periodInputValid = it }
            ) { "第" + it + "节" }
        }
        item {
            RangeWheelPicker(
                title = "周次",
                values = (1..state.config.totalWeeks).toList(),
                start = weekStart,
                end = weekEnd,
                onStart = { weekStart = it; if (weekEnd < it) weekEnd = it },
                onEnd = { weekEnd = it; if (weekStart > it) weekStart = it }
            ) { "第" + it + "周" }
        }
        item { WheelPicker("单双周", WeekParity.entries, parity, { parity = it }) { parityLabel(it) } }
        item { OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        if (initialCourse != null) {
            item { LiquidMenuButton(backdrop, "删除课程", destructive = true, onClick = { onDelete(initialCourse) }) }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
fun DialogHeader(title: String, onCancel: () -> Unit, onSave: () -> Unit, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig()) {
    val textColor = glassForegroundColor(config)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        DialogLiquidButton(backdrop, "取消", onCancel, role = DialogButtonRole.Cancel)
        Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = textColor)
        DialogLiquidButton(backdrop, "\u4FDD\u5B58", onSave, role = DialogButtonRole.Confirm)
    }
}

@Composable
fun DialogScaffold(title: String, onCancel: () -> Unit, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), content: @Composable () -> Unit) {
    val textColor = glassForegroundColor(config)
    Column(modifier = Modifier.heightIn(max = 650.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogLiquidButton(backdrop, "取消", onCancel, role = DialogButtonRole.Cancel)
            Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = textColor)
            Spacer(Modifier.width(42.dp))
        }
        content()
    }
}

@Composable
fun NormalizedDialogHeader(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val textColor = glassForegroundColor(config)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        DialogLiquidButton(backdrop, "\u53D6\u6D88", onCancel, role = DialogButtonRole.Cancel)
        Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = textColor)
        DialogLiquidButton(backdrop, "\u4FDD\u5B58", onSave, role = DialogButtonRole.Confirm)
    }
}

@Composable
fun NormalizedDialogScaffold(
    title: String,
    onCancel: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    content: @Composable () -> Unit
) {
    val textColor = glassForegroundColor(config)
    Column(modifier = Modifier.heightIn(max = 650.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogLiquidButton(backdrop, "\u53D6\u6D88", onCancel, role = DialogButtonRole.Cancel)
            Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = textColor)
            Spacer(Modifier.width(42.dp))
        }
        content()
    }
}

@Composable
fun NormalizedCourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?
) {
    var name by remember(initialCourse) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher.orEmpty()) }
    var location by remember(initialCourse) { mutableStateOf(initialCourse?.location.orEmpty()) }
    var weekday by remember(initialCourse) { mutableIntStateOf(initialCourse?.weekday ?: 1) }
    val rawPeriodValues = state.periods.map { it.periodIndex }
    val coursePeriodValues = initialCourse?.periods.orEmpty()
    val periodValues = (rawPeriodValues + coursePeriodValues).distinct().sorted()
    var periodStart by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1)) }
    var periodEnd by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.maxOrNull() ?: periodStart) }
    var weekStart by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.minOrNull() ?: 1) }
    var weekEnd by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.maxOrNull() ?: state.config.totalWeeks) }
    var parity by remember(initialCourse) { mutableStateOf(initialCourse?.weekParity ?: WeekParity.ALL) }
    var note by remember(initialCourse) { mutableStateOf(initialCourse?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var periodInputValid by remember(initialCourse, periodValues) { mutableStateOf(true) }
    val selectedPeriods = if (periodStart <= periodEnd) periodValues.filter { it in periodStart..periodEnd } else emptyList()
    val selectedWeeks = (minOf(weekStart, weekEnd)..maxOf(weekStart, weekEnd)).toList()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DialogHeader(
                title = if (initialCourse == null) "\u6DFB\u52A0\u5355\u8282\u8BFE" else "\u7F16\u8F91\u5355\u8282\u8BFE",
                onCancel = onCancel,
                backdrop = backdrop,
                config = state.config,
                onSave = {
                    when {
                        name.isBlank() -> error = "\u8BFE\u7A0B\u540D\u79F0\u4E0D\u80FD\u4E3A\u7A7A"
                        !periodInputValid -> error = "\u8BF7\u5148\u4FEE\u6B63\u8282\u6B21\u8303\u56F4"
                        selectedPeriods.isEmpty() -> error = "\u8BF7\u9009\u62E9\u8282\u6B21"
                        selectedWeeks.isEmpty() -> error = "\u8BF7\u9009\u62E9\u5468\u6B21"
                        else -> onSave(
                            CourseEntity(
                                id = initialCourse?.id ?: 0,
                                name = name.trim(),
                                teacher = teacher.ifBlank { null },
                                location = location.ifBlank { null },
                                weekday = weekday,
                                periods = selectedPeriods,
                                weeks = selectedWeeks,
                                weekParity = parity,
                                note = note.ifBlank { null },
                                scheduleId = initialCourse?.scheduleId ?: 0
                            )
                        )
                    }
                }
            )
        }
        item { DialogCapsuleField(name, { name = it }, "\u8BFE\u7A0B\u540D\u79F0", state.config, Modifier.fillMaxWidth()) }
        item { DialogCapsuleField(teacher, { teacher = it }, "\u6559\u5E08", state.config, Modifier.fillMaxWidth()) }
        item { DialogCapsuleField(location, { location = it }, "\u5730\u70B9", state.config, Modifier.fillMaxWidth()) }
        item { WheelPicker("\u661F\u671F", (1..7).toList(), weekday, { weekday = it }, backdrop, state.config) { "\u5468" + weekdayLabel(it) } }
        item {
            DialogRangePicker(
                title = "\u8282\u6B21",
                values = periodValues,
                start = periodStart,
                end = periodEnd,
                onStart = { periodStart = it },
                onEnd = { periodEnd = it },
                backdrop = backdrop,
                config = state.config,
                enforceOrderedInput = true,
                onInputValidChange = { periodInputValid = it }
            ) { "\u7B2C" + it + "\u8282" }
        }
        item {
            DialogRangePicker(
                title = "\u5468\u6B21",
                values = (1..state.config.totalWeeks).toList(),
                start = weekStart,
                end = weekEnd,
                onStart = { weekStart = it; if (weekEnd < it) weekEnd = it },
                onEnd = { weekEnd = it; if (weekStart > it) weekStart = it },
                backdrop = backdrop,
                config = state.config
            ) { "\u7B2C" + it + "\u5468" }
        }
        item { WheelPicker("\u5355\u53CC\u5468", WeekParity.entries, parity, { parity = it }, backdrop, state.config) { parityLabel(it) } }
        item { DialogCapsuleField(note, { note = it }, "\u5907\u6CE8", state.config, Modifier.fillMaxWidth()) }
        if (initialCourse != null) {
            item { DialogLiquidButton(backdrop, "\u5220\u9664\u8BFE\u7A0B", { onDelete(initialCourse) }, role = DialogButtonRole.Cancel) }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
fun <T> WheelPicker(
    title: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig(),
    label: (T) -> String
) {
    DialogOptionPicker(title, values, selected, onSelected, label, backdrop, config)
}

@Composable
fun RangeWheelPicker(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig(),
    enforceOrderedInput: Boolean = false,
    onInputValidChange: (Boolean) -> Unit = {},
    label: (Int) -> String
) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    fun clamp(value: String): Int? {
        val parsed = value.toIntOrNull() ?: return null
        return parsed.coerceIn(values.firstOrNull() ?: parsed, values.lastOrNull() ?: parsed)
    }
    val draftStart = clamp(startText)
    val draftEnd = clamp(endText)
    val inputComplete = draftStart != null && draftEnd != null
    val orderedInvalid = enforceOrderedInput && draftStart != null && draftEnd != null && draftEnd < draftStart
    val inputValid = inputComplete && !orderedInvalid
    LaunchedEffect(inputValid) { onInputValidChange(inputValid) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startText,
                onValueChange = {
                    startText = it.filter(Char::isDigit)
                    clamp(startText)?.let(onStart)
                },
                label = { Text("开始") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endText,
                onValueChange = {
                    endText = it.filter(Char::isDigit)
                    clamp(endText)?.let(onEnd)
                },
                label = { Text("结束") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        if (orderedInvalid) {
            Text("当前结束节早于开始节", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        } else {
            val previewStart = draftStart ?: start
            val previewEnd = draftEnd ?: end
            Text("${label(minOf(previewStart, previewEnd))} - ${label(maxOf(previewStart, previewEnd))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun DialogRangePicker(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enforceOrderedInput: Boolean = false,
    onInputValidChange: (Boolean) -> Unit = {},
    label: (Int) -> String
) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    fun clamp(value: String): Int? {
        val parsed = value.toIntOrNull() ?: return null
        return parsed.coerceIn(values.firstOrNull() ?: parsed, values.lastOrNull() ?: parsed)
    }
    val draftStart = clamp(startText)
    val draftEnd = clamp(endText)
    val inputComplete = draftStart != null && draftEnd != null
    val orderedInvalid = enforceOrderedInput && draftStart != null && draftEnd != null && draftEnd < draftStart
    val inputValid = inputComplete && !orderedInvalid
    LaunchedEffect(inputValid) { onInputValidChange(inputValid) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DialogCapsuleField(
                value = startText,
                onValueChange = {
                    startText = it.filter(Char::isDigit)
                    clamp(startText)?.let(onStart)
                },
                placeholder = "\u5F00\u59CB",
                config = config,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            DialogCapsuleField(
                value = endText,
                onValueChange = {
                    endText = it.filter(Char::isDigit)
                    clamp(endText)?.let(onEnd)
                },
                placeholder = "\u7ED3\u675F",
                config = config,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }
        if (orderedInvalid) {
            Text(
                "\u5F53\u524D\u7ED3\u675F\u8282\u65E9\u4E8E\u5F00\u59CB\u8282",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val previewStart = draftStart ?: start
            val previewEnd = draftEnd ?: end
            Text(
                "${label(minOf(previewStart, previewEnd))} - ${label(maxOf(previewStart, previewEnd))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MultiWheelPicker(title: String, values: List<Int>, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, label: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(values.size) { index ->
                val value = values[index]
                val active = value in selected
                Text(
                    label(value),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else ComposeColor.Transparent)
                        .clickable { onSelected(if (active) selected - value else selected + value) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MissingCourseScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("请先配置课程表。")
        Button(onClick = onBack) { Text("返回") }
    }
}

@Composable
fun NormalizedAiManualImportScreen(state: AppState, backdrop: Backdrop?, onParsed: (ImportDraft) -> Unit) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val textColor = glassForegroundColor(state.config)
    fun parseDraft() {
        val result = ScheduleImportParser.parse(jsonText, state.config)
        result.onSuccess { error = null; onParsed(it) }.onFailure { error = it.message ?: "口令解析失败" }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("\u5C06\u4F60\u7684\u8BFE\u8868 PDF \u8FDE\u540C\u63D0\u793A\u8BCD\u4E00\u8D77\u53D1\u7ED9\u4EFB\u610F AI\uFF0C\u7136\u540E\u5C06\u8FD4\u56DE\u7684 SleepDown \u8BFE\u8868\u53E3\u4EE4\u590D\u5236\u5230\u8F93\u5165\u6846\u5185\u3002", color = textColor)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogLiquidButton(
                backdrop = backdrop,
                label = "\u590D\u5236\u63D0\u793A\u8BCD",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SleepDown \u8BFE\u8868\u53E3\u4EE4\u63D0\u793A\u8BCD", SchedulePromptBuilder.buildTokenPrompt()))
                },
                role = DialogButtonRole.Neutral
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = "\u6E05\u7406\u683C\u5F0F",
                onClick = { jsonText = ScheduleImportParser.cleanMarkdown(jsonText) },
                role = DialogButtonRole.Neutral
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = "\u89E3\u6790\u5E76\u9884\u89C8",
                role = DialogButtonRole.Confirm,
                iconRes = R.drawable.ic_download,
                onClick = { parseDraft() }
            )
        }
        DialogCapsuleField(
            value = jsonText,
            onValueChange = { jsonText = it },
            placeholder = "AI \u8FD4\u56DE\u7684 SleepDown \u8BFE\u8868\u53E3\u4EE4",
            config = state.config,
            minLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun AiManualImportScreen(state: AppState, onParsed: (ImportDraft) -> Unit) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val textColor = glassForegroundColor(state.config)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("复制 Prompt 到 AI，粘贴返回的 JSON 后导入。", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidMenuButton(null, "复制 Prompt", onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("课表导入 Prompt", SchedulePromptBuilder.build()))
            })
            LiquidMenuButton(null, "清理格式", onClick = { jsonText = ScheduleImportParser.cleanMarkdown(jsonText) })
        }
        OutlinedTextField(jsonText, { jsonText = it }, label = { Text("AI 返回 JSON") }, minLines = 12, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LiquidMenuButton(null, "解析并预览", modifier = Modifier.fillMaxWidth(), onClick = {
            val result = ScheduleImportParser.parse(jsonText, state.config)
            result.onSuccess { error = null; onParsed(it) }.onFailure { error = it.message ?: "JSON 解析失败" }
        })
    }
}

@Composable
fun DonateSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val topPadding = detailContentTopPadding()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("感谢支持", "SleepDown 课程表会继续保持简洁、免费和尽量少打扰。你的捐赠会用于测试设备、应用维护和后续功能适配。捐赠完全自愿，不会影响任何功能使用。")
            }
        }
        item {
            Image(
                painter = painterResource(R.drawable.donate_alipay),
                contentDescription = "支付宝捐赠二维码",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("使用方式", "打开支付宝扫一扫，识别上方二维码即可。谢谢你愿意支持这个小小的课程表继续变好。")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EduImportFlowScreen(page: EduImportPage, state: AppState, onPageChange: (EduImportPage) -> Unit, onParsed: (ImportDraft) -> Unit) {
    val context = LocalContext.current
    val adapters = remember { runCatching { ShiguangWarehouse.loadAdapters(context) }.getOrDefault(emptyList()) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showEmbeddedEduPage by remember { mutableStateOf(false) }
    val selectedAdapter = (page as? EduImportPage.Import)?.adapter
    val bridge = remember(state.config, selectedAdapter) {
        EduImportBridge(
            context = context,
            adapter = selectedAdapter,
            baseConfig = { state.config },
            basePeriods = { state.periods },
            onDraft = onParsed,
            onMessage = { message = it }
        )
    }
    val filtered = remember(query, adapters) {
        val keyword = query.trim()
        val sorted = adapters.sortedWith(
            compareBy<EduAdapter> { it.school.initial.ifBlank { "#" } }
                .thenBy { it.school.name }
                .thenBy { it.adapterName }
        )
        if (keyword.isBlank()) sorted else sorted.filter {
            it.displayName.contains(keyword, ignoreCase = true) ||
                    it.school.id.contains(keyword, ignoreCase = true) ||
                    it.adapterId.contains(keyword, ignoreCase = true) ||
                    it.school.name.contains(keyword, ignoreCase = true) ||
                    it.adapterName.contains(keyword, ignoreCase = true)
        }
    }

    if (selectedAdapter == null) {
        EduSchoolIndexedSelectScreen(
            state = state,
            adapters = filtered,
            query = query,
            onQueryChange = { query = it },
            onSelect = {
                message = null
                webView = null
                showEmbeddedEduPage = false
                onPageChange(EduImportPage.Import(it))
            }
        )
    } else {
        EduImportWebScreen(
            state = state,
            adapter = selectedAdapter,
            message = message,
            webView = webView,
            onWebView = { webView = it },
            showEmbeddedPage = showEmbeddedEduPage,
            onShowEmbeddedPage = { showEmbeddedEduPage = true },
            bridge = bridge,
            onMessage = { message = it }
        )
    }
}

@Composable
fun EduSchoolPickerScreen(
    state: AppState,
    onSelect: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    val adapters = remember { runCatching { ShiguangWarehouse.loadAdapters(context) }.getOrDefault(emptyList()) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, adapters) {
        val keyword = query.trim()
        val sorted = adapters.sortedWith(
            compareBy<EduAdapter> { it.school.initial.ifBlank { "#" } }
                .thenBy { it.school.name }
                .thenBy { it.adapterName }
        )
        if (keyword.isBlank()) sorted else sorted.filter {
            it.displayName.contains(keyword, ignoreCase = true) ||
                    it.school.id.contains(keyword, ignoreCase = true) ||
                    it.adapterId.contains(keyword, ignoreCase = true) ||
                    it.school.name.contains(keyword, ignoreCase = true) ||
                    it.adapterName.contains(keyword, ignoreCase = true)
        }
    }
    EduSchoolIndexedSelectScreen(
        state = state,
        adapters = filtered,
        query = query,
        onQueryChange = { query = it },
        onSelect = onSelect
    )
}

@Composable
fun EduSchoolIndexedSelectScreen(
    state: AppState,
    adapters: List<EduAdapter>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (EduAdapter) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    val pinnedAdapters = remember(adapters) {
        adapters.filter { it.isGeneralEduTool() || it.isEduTestTool() }
            .sortedWith(compareBy<EduAdapter> { it.school.id }.thenBy { it.adapterName })
    }
    val indexedAdapters = remember(adapters) { adapters.filterNot { it.isGeneralEduTool() || it.isEduTestTool() } }
    val grouped = remember(indexedAdapters) {
        indexedAdapters.groupBy { it.school.initial.ifBlank { "#" }.uppercase() }.toSortedMap()
    }
    val letters = remember(grouped) { grouped.keys.toList() }
    val sectionPositions = remember(grouped, pinnedAdapters) {
        var index = 2 + if (pinnedAdapters.isEmpty()) 0 else 1 + pinnedAdapters.size
        buildMap {
            grouped.forEach { (letter, list) ->
                put(letter, index)
                index += 1 + list.size
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 38.dp, top = topPadding + 8.dp, bottom = DockScrollPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { SchoolSearchField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth()) }
            if (adapters.isEmpty()) {
                item { Text("没有找到学校适配资源", color = MaterialTheme.colorScheme.error) }
            } else {
                if (pinnedAdapters.isNotEmpty()) {
                    item(key = "general-edu-title") {
                        Text("通用教务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                    }
                    items(pinnedAdapters, key = { "pinned-${it.adapterId}" }) { adapter ->
                        SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(adapter.school.name, adapter.adapterName, onClick = { onSelect(adapter) })
                        }
                    }
                }
                grouped.forEach { (letter, list) ->
                    item(key = "section-$letter") {
                        Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                    }
                    items(list, key = { it.adapterId }) { adapter ->
                        SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(adapter.school.name, adapter.adapterName, onClick = { onSelect(adapter) })
                        }
                    }
                }
            }
        }
        if (letters.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .pointerInput(letters, sectionPositions) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.firstOrNull { it.pressed } ?: continue
                                val itemHeight = size.height / letters.size.toFloat()
                                val index = (pressed.position.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                                sectionPositions[letters[index]]?.let { position ->
                                    scope.launch { listState.scrollToItem(position) }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                letters.forEach { letter ->
                    Text(
                        letter,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                sectionPositions[letter]?.let { position ->
                                    scope.launch { listState.animateScrollToItem(position) }
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SchoolSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text("搜索学校", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun EduImportActivityScreen(
    state: AppState,
    adapter: EduAdapter,
    backdrop: Backdrop?,
    useDetailTopPadding: Boolean = true,
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(adapter) {
        mutableStateOf(
            if (adapter.isGeneralEduTool()) "" else adapter.importUrl.ifBlank { "https://" }
        )
    }
    var showGeneralUrlDialog by remember(adapter) { mutableStateOf(adapter.isGeneralEduTool()) }
    val bridge = remember(state.config, adapter) {
        EduImportBridge(
            context = context,
            adapter = adapter,
            baseConfig = { state.config },
            basePeriods = { state.periods },
            onDraft = onParsed,
            onMessage = { message = it }
        )
    }
    if (showGeneralUrlDialog) {
        Dialog(onDismissRequest = { showGeneralUrlDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CenterLiquidDialog(backdrop = backdrop, config = state.config) {
                GeneralEduUrlDialog(
                    config = state.config,
                    backdrop = backdrop,
                    initialUrl = currentUrl,
                    onCancel = { showGeneralUrlDialog = false },
                    onConfirm = {
                        currentUrl = it
                        showGeneralUrlDialog = false
                        webView?.loadUrl(it)
                    }
                )
            }
        }
    }
    EduImportBrowserScreen(
        state = state,
        adapter = adapter,
        backdrop = backdrop,
        message = message,
        webView = webView,
        onWebView = { webView = it },
        currentUrl = currentUrl,
        onUrlChange = { currentUrl = it },
        bridge = bridge,
        useDetailTopPadding = useDetailTopPadding,
        onMessage = { message = it }
    )
}

@Composable
fun GeneralEduUrlDialog(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    initialUrl: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl.ifBlank { "https://" }) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogLiquidButton(backdrop, "取消", onCancel, role = DialogButtonRole.Cancel)
            Text("输入教务网址", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = glassForegroundColor(config))
            DialogLiquidButton(
                backdrop = backdrop,
                label = "打开",
                onClick = {
                    val normalized = normalizeEduUrl(url)
                    if (normalized.isBlank()) {
                        error = "请输入教务系统网址"
                    } else {
                        onConfirm(normalized)
                    }
                },
                role = DialogButtonRole.Confirm
            )
        }
        DialogCapsuleField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://example.edu.cn",
            config = config,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Uri
        )
        Text(
            error ?: "通用教务需要先填写学校教务系统网址，进入后可继续在顶部网址栏修改。",
            style = MaterialTheme.typography.bodySmall,
            color = if (error == null) glassForegroundColor(config).copy(alpha = 0.72f) else MaterialTheme.colorScheme.error,
            lineHeight = 18.sp
        )
    }
}

fun normalizeEduUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isBlank() || trimmed == "https://" -> ""
        trimmed.startsWith("file://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
}

@Composable
fun EduSchoolSelectScreen(
    state: AppState,
    adapters: List<EduAdapter>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsTextFieldRow("搜索", query, onQueryChange)
            }
        }
        if (adapters.isEmpty()) {
            item { Text("没有找到学校适配资源", color = MaterialTheme.colorScheme.error) }
        } else {
            item {
                SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    adapters.forEachIndexed { index, adapter ->
                        SettingsNavigationRow(adapter.school.name, adapter.adapterName, onClick = { onSelect(adapter) })
                        if (index != adapters.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EduImportBrowserScreen(
    state: AppState,
    adapter: EduAdapter,
    backdrop: Backdrop?,
    message: String?,
    webView: WebView?,
    onWebView: (WebView) -> Unit,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    bridge: EduImportBridge,
    useDetailTopPadding: Boolean = true,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val fallbackBackdrop = rememberLayerBackdrop()
    val buttonBackdrop = backdrop ?: fallbackBackdrop
    var addressText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastRequestedUrl by remember { mutableStateOf<String?>(null) }
    var desktopMode by remember { mutableStateOf(false) }
    var waitingBrowserReturn by remember { mutableStateOf(false) }
    val topPadding = if (useDetailTopPadding) detailContentTopPadding() else 0.dp
    val normalizedUrl = remember(addressText) {
        normalizeEduUrl(addressText)
    }

    fun loadAddress() {
        if (normalizedUrl.isBlank()) {
            onMessage("请输入教务系统网址")
            return
        }
        onUrlChange(normalizedUrl)
        webView?.loadUrl(normalizedUrl)
    }

    fun openInSystemBrowser() {
        val targetUrl = webView?.url?.takeIf { it.isNotBlank() } ?: normalizedUrl
        if (targetUrl.isBlank()) {
            onMessage("请输入教务系统网址")
            return
        }
        waitingBrowserReturn = true
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .build()
                .launchUrl(context, Uri.parse(targetUrl))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            }.onFailure {
                onMessage("无法打开系统浏览器")
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, waitingBrowserReturn, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingBrowserReturn) {
                waitingBrowserReturn = false
                webView?.reload()
                onMessage("已返回 App，页面已刷新，可继续执行导入")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun runImportScript() {
        val target = webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        runCatching { ShiguangWarehouse.loadScript(context, adapter) }
            .onSuccess {
                onMessage("已执行导入脚本")
                target.evaluateJavascript(
                    """
                    console.log('SleepDown bridge check', !!window.AndroidBridgePromise, typeof window.AndroidBridgePromise?.showAlert, typeof window.AndroidBridge?.notifyTaskCompletion);
                    try { $it } catch (e) { console.error('SleepDown import script error', e && (e.stack || e.message || e)); throw e; }
                    """.trimIndent(),
                    null
                )
            }
            .onFailure { onMessage(it.message ?: "脚本加载失败") }
    }

    fun updateNavigationState(target: WebView?) {
        canGoBack = target?.canGoBack() == true
        canGoForward = target?.canGoForward() == true
    }

    fun applyEduWebMode(target: WebView, desktop: Boolean) {
        with(target.settings) {
            userAgentString = if (desktop) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"
            } else {
                null
            }
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
        }
        if (desktop) target.setInitialScale(80)
    }

    fun createEduWebView(context: Context): WebView {
        return WebView(context).apply {
            val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WebView.getCurrentWebViewPackage() else null
            Log.d("SleepDownWebView", "provider=${provider?.packageName}/${provider?.versionName}")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applyEduWebMode(this, desktopMode)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    updateNavigationState(view)
                    if (!url.isNullOrBlank()) {
                        addressText = url
                        onUrlChange(url)
                    }
                }
            }
            webChromeClient = WebChromeClient()
            enableSleepDownDownloads()
            addEduImportBridge(bridge)
            onWebView(this)
            updateNavigationState(this)
            if (normalizedUrl.isNotBlank()) loadUrl(normalizedUrl)
            if (normalizedUrl.isNotBlank()) lastRequestedUrl = normalizedUrl
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = addressText,
                onValueChange = { addressText = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (appUsesDarkTheme(state.config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (addressText.isBlank() || addressText == "https://") {
                            Text("输入教务系统网址", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            )
            LiquidMenuButton(null, "打开", onClick = { loadAddress() })
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    FrameLayout(it).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(createEduWebView(it))
                    }
                },
                update = { container ->
                    val target = container.getChildAt(0) as? WebView ?: return@AndroidView
                    applyEduWebMode(target, desktopMode)
                    if (normalizedUrl.isNotBlank() && lastRequestedUrl != normalizedUrl) {
                        lastRequestedUrl = normalizedUrl
                        target.loadUrl(normalizedUrl)
                    }
                },
                onRelease = { container ->
                    (container.getChildAt(0) as? WebView)?.releaseSleepDownWebView()
                    container.removeAllViews()
                }
            )
            message?.let {
                Text(
                    it,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 22.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EduWebNavButton(
                    backdrop = buttonBackdrop,
                    iconRes = R.drawable.ic_arrow_back,
                    contentDescription = "网页后退",
                    enabled = canGoBack,
                    onClick = {
                        webView?.goBack()
                        updateNavigationState(webView)
                    }
                )
                EduWebNavButton(
                    backdrop = buttonBackdrop,
                    iconRes = R.drawable.ic_arrow_back,
                    contentDescription = "网页前进",
                    enabled = canGoForward,
                    flipHorizontal = true,
                    onClick = {
                        webView?.goForward()
                        updateNavigationState(webView)
                    }
                )
                LiquidMenuButton(
                    null,
                    if (desktopMode) "电脑" else "手机",
                    onClick = {
                        desktopMode = !desktopMode
                        webView?.let { target ->
                            applyEduWebMode(target, desktopMode)
                            target.reload()
                        }
                    }
                )
                LiquidMenuButton(null, "浏览器", onClick = { openInSystemBrowser() })
            }
            LiquidButton(
                onClick = { runImportScript() },
                backdrop = buttonBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 22.dp, bottom = 16.dp)
                    .size(58.dp),
                tint = ComposeColor(0xFF0A84FF),
                surfaceColor = ComposeColor(0xFF0A84FF).copy(alpha = 0.34f),
                contentPadding = PaddingValues(0.dp),
                blurRadius = 8.dp,
                lensHeight = 34.dp,
                lensAmount = 42.dp,
                chromaticAberration = false
            ) {
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = "执行导入",
                    tint = ComposeColor.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EduImportWebScreen(
    state: AppState,
    adapter: EduAdapter,
    message: String?,
    webView: WebView?,
    onWebView: (WebView) -> Unit,
    showEmbeddedPage: Boolean,
    onShowEmbeddedPage: () -> Unit,
    bridge: EduImportBridge,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(adapter.school.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
            SettingsValueRow("适配器", adapter.adapterName)
            SettingsDivider()
            SettingsValueRow("维护者", adapter.maintainer.ifBlank { "-" })
            SettingsDivider()
            SettingsValueRow("登录地址", adapter.importUrl)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton("用浏览器打开", null, onClick = {
                if (adapter.importUrl.isBlank()) {
                    onMessage("请先输入教务系统网址")
                    return@SettingsActionButton
                }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adapter.importUrl)))
                }.onFailure {
                    onMessage(it.message ?: "无法打开浏览器")
                }
            })
            SettingsActionButton("使用内置页面", null, onClick = {
                onMessage("已打开内置页面，登录完成后执行导入脚本。")
                onShowEmbeddedPage()
            })
        }
        SettingsActionButton("执行导入脚本", null, onClick = {
            runCatching { ShiguangWarehouse.loadScript(context, adapter) }
                .onSuccess {
                    onMessage("已执行导入脚本")
                    webView?.evaluateJavascript(it, null) ?: onMessage("请先打开内置页面")
                }
                .onFailure { onMessage(it.message ?: "脚本加载失败") }
        }, modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (showEmbeddedPage) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    WebView(it).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        enableSleepDownDownloads()
                        addEduImportBridge(bridge)
                        onWebView(this)
                        if (adapter.importUrl.isNotBlank()) loadUrl(adapter.importUrl)
                    }
                },
                update = {},
                onRelease = { it.releaseSleepDownWebView() }
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun EduWebNavButton(
    backdrop: Backdrop,
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    flipHorizontal: Boolean = false,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.42f),
        isInteractive = enabled,
        surfaceColor = ComposeColor.White.copy(alpha = 0.16f),
        contentPadding = PaddingValues(0.dp),
        blurRadius = 8.dp,
        lensHeight = 30.dp,
        lensAmount = 38.dp,
        chromaticAberration = false
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer(scaleX = if (flipHorizontal) -1f else 1f)
        )
    }
}

@Composable
fun EduImportScreen(state: AppState, onParsed: (ImportDraft) -> Unit) {
    val context = LocalContext.current
    val adapters = remember { runCatching { ShiguangWarehouse.loadAdapters(context) }.getOrDefault(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember(adapters) { mutableStateOf(adapters.firstOrNull()) }
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember(state.config, selected) {
        EduImportBridge(
            context = context,
            adapter = selected,
            baseConfig = { state.config },
            basePeriods = { state.periods },
            onDraft = onParsed,
            onMessage = { message = it }
        )
    }
    val filtered = remember(query, adapters) {
        val keyword = query.trim()
        if (keyword.isBlank()) adapters else adapters.filter {
            it.displayName.contains(keyword, ignoreCase = true) ||
                    it.school.id.contains(keyword, ignoreCase = true) ||
                    it.adapterId.contains(keyword, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (adapters.isEmpty()) {
            Text("没有找到 shiguang_warehouse 适配资源", color = MaterialTheme.colorScheme.error)
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索学校或适配器") },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.adapterId }) { adapter ->
                val active = adapter == selected
                Text(
                    adapter.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selected = adapter }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        selected?.let { adapter ->
            Text(adapter.importUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidMenuButton(null, "打开登录页", onClick = {
                    message = "已在内置页面打开，请登录后再执行导入脚本。"
                    webView?.loadUrl(adapter.importUrl)
                })
                LiquidMenuButton(null, "执行导入脚本", onClick = {
                    runCatching { ShiguangWarehouse.loadScript(context, adapter) }
                        .onSuccess {
                            message = "已执行导入脚本"
                            webView?.evaluateJavascript(it, null)
                        }
                        .onFailure { message = it.message ?: "脚本加载失败" }
                })
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    enableSleepDownDownloads()
                    addEduImportBridge(bridge)
                    webView = this
                    selected?.importUrl?.let(::loadUrl)
                }
            },
            update = {},
            onRelease = { it.releaseSleepDownWebView() }
        )
    }
}

@Composable
fun ConfirmScheduleScreen(draft: ImportDraft, warning: String? = null, onCancel: () -> Unit, onConfirm: (Boolean) -> Unit) {
    val previewDraft = remember(draft) {
        draft.copy(
            periods = draft.periods.distinctBy { it.periodIndex }.sortedBy { it.periodIndex },
            courses = draft.courses.map {
                it.copy(
                    periods = it.periods.distinct().sorted(),
                    weeks = it.weeks.distinct().sorted()
                )
            }
        )
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = DockScrollPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("即将导入 " + previewDraft.courses.size + " 门课程，请确认后写入课表。") }
        warning?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        item { Text("总周数 " + previewDraft.config.totalWeeks + " 周，节次数 " + previewDraft.periods.size) }
        if (previewDraft.courses.isEmpty()) {
            item { Text("没有解析到课程", color = MaterialTheme.colorScheme.error) }
        } else {
            itemsIndexed(
                previewDraft.courses,
                key = { index, course ->
                    "preview_${index}_${course.name}_${course.weekday}_${course.periods.joinToString("_")}_${course.weeks.take(3).joinToString("_")}"
                }
            ) { _, course ->
                ImportPreviewCourseCard(course, previewDraft.periods, previewDraft.config)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("请选择导入方式：", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogLiquidButton(null, "覆盖当前课表", { onConfirm(false) }, modifier = Modifier.weight(1f), role = DialogButtonRole.Confirm)
                    DialogLiquidButton(null, "创建新课表", { onConfirm(true) }, modifier = Modifier.weight(1f), role = DialogButtonRole.Confirm)
                }
                DialogLiquidButton(null, "取消", onCancel, modifier = Modifier.fillMaxWidth(), role = DialogButtonRole.Cancel, roundIcon = false)
            }
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
    when (page) {
        SettingsPage.Root -> SettingsRootScreen(pageState, backdrop, onPageChange)
        SettingsPage.General -> GeneralSettingsScreen(state, backdrop, onUpdateConfig)
        SettingsPage.Schedule -> ScheduleConfigScreen(state, backdrop, SettingsSection.Schedule, onSave, onPreviewLiveUpdate)
        SettingsPage.Notifications -> ScheduleConfigScreen(state, backdrop, SettingsSection.Notifications, onSave, onPreviewLiveUpdate)
        SettingsPage.ScheduleManager -> ScheduleManagerScreen(state, backdrop, onCreateSchedule, onActivateSchedule, onRenameSchedule, onDeleteSchedule)
        SettingsPage.About -> AboutSettingsScreen(pageState, backdrop)
        SettingsPage.Changelog -> ChangelogSettingsScreen(pageState, backdrop) {}
        SettingsPage.Download -> DownloadUpdateScreen(pageState, backdrop)
        SettingsPage.Donate -> DonateSettingsScreen(pageState, backdrop)
    }
}

@Composable
fun SettingsRootScreen(state: AppState, backdrop: Backdrop?, onPageChange: (SettingsPage) -> Unit) {
    val context = LocalContext.current
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

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 34.dp, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onPageChange(SettingsPage.Changelog) }
                )
                Text(appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("版本 $versionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("开发者：小漫君", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsNavigationRow("通用设置", "深色模式与系统外观", onClick = { onPageChange(SettingsPage.General) })
                SettingsDivider()
                SettingsNavigationRow("课表设置", "管理多个课表", onClick = { context.startActivity(Intent(context, ScheduleManagerActivity::class.java)) })
                SettingsDivider()
                SettingsNavigationRow("通知设置", "上课提醒与实时活动", onClick = { onPageChange(SettingsPage.Notifications) })
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsNavigationRow("关于", "软件信息与开源引用", onClick = { onPageChange(SettingsPage.About) })
            }
        }
    }
}

@Composable
fun ScheduleManagerScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCreateSchedule: (String) -> Unit,
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit,
    onRenameSchedule: (Int, String) -> Unit,
    onDeleteSchedule: (Int) -> Unit
) {
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
                            ) { onActivateSchedule(profile.id, null) }
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
        Dialog(onDismissRequest = { deleteTarget = null }) {
            NormalizedDialogScaffold(
                title = "删除课表",
                onCancel = { deleteTarget = null },
                backdrop = backdrop,
                config = state.config
            ) {
                Text(
                    "确定要删除「$name」吗？\n该课表下的所有课程都会被删除，且无法恢复。",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsActionButton("取消", backdrop, onClick = { deleteTarget = null }, modifier = Modifier.weight(1f))
                    SettingsActionButton("确认删除", backdrop, onClick = {
                        val targetId = id
                        deleteTarget = null
                        onDeleteSchedule(targetId)
                    }, modifier = Modifier.weight(1f), destructive = true)
                }
            }
        }
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
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.heightIn(max = 650.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DialogLiquidButton(backdrop, "取消", onDismiss, role = DialogButtonRole.Cancel)
                Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = textColor)
                DialogLiquidButton(backdrop, "确定", { onConfirm(name.trim()) }, role = DialogButtonRole.Confirm)
            }
            DialogCapsuleField(name, { name = it }, "课表名称", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
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
                SettingsNavigationRow("捐赠支持", "如果 SleepDown 课程表帮到了你，可以请小漫君喝杯奶茶。", onClick = onDonate)
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
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
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsNavigationRow("下载新版", "打开蓝奏云下载页，密码：i224", onClick = onDownload)
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
                    settings.databaseEnabled = true
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

private fun WebView.releaseSleepDownWebView() {
    runCatching { stopLoading() }
    runCatching { clearHistory() }
    runCatching { removeJavascriptInterface("AndroidBridgePromise") }
    runCatching { removeJavascriptInterface("AndroidBridge") }
    runCatching { destroy() }
}

@SuppressLint("JavascriptInterface")
private fun WebView.addEduImportBridge(bridge: EduImportBridge) {
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

private fun WebView.enableSleepDownDownloads() {
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

@Composable
fun GeneralSettingsScreen(state: AppState, backdrop: Backdrop?, onUpdateConfig: (ScheduleConfigEntity) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    var draft by remember { mutableStateOf(state.config) }
    var lastSaved by remember { mutableStateOf(state.config) }
    LaunchedEffect(state.config) {
        if (draft == lastSaved) {
            draft = state.config
        }
        lastSaved = state.config
    }
    val dirty = draft.followSystemDarkMode != state.config.followSystemDarkMode ||
        draft.darkMode != state.config.darkMode ||
        draft.dockAlignment != state.config.dockAlignment ||
        draft.defaultWallpaperStyle != state.config.defaultWallpaperStyle ||
        draft.defaultHomeMode != state.config.defaultHomeMode ||
        draft.liveUpdateActionsEnabled != state.config.liveUpdateActionsEnabled ||
        draft.hideFromRecents != state.config.hideFromRecents
    val visualConfig = settingsVisualConfig(draft)
    fun resetDraft() {
        draft = state.config
        lastSaved = state.config
    }
    fun saveDraft() {
        val next = state.config.copy(
            followSystemDarkMode = draft.followSystemDarkMode,
            darkMode = draft.darkMode,
            dockAlignment = draft.dockAlignment,
            defaultWallpaperStyle = draft.defaultWallpaperStyle,
            defaultHomeMode = draft.defaultHomeMode,
            liveUpdateActionsEnabled = draft.liveUpdateActionsEnabled,
            hideFromRecents = draft.hideFromRecents
        )
        draft = next
        lastSaved = next
        onUpdateConfig(next)
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (dirty) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    SettingsActionButton("重置", backdrop, onClick = { resetDraft() }, destructive = true)
                    Spacer(Modifier.width(8.dp))
                    SettingsActionButton("保存", backdrop, onClick = { saveDraft() })
                }
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "跟随系统",
                    subtitle = "开启后将跟随系统切换浅色或深色模式。",
                    checked = draft.followSystemDarkMode,
                    backdrop = backdrop,
                    onCheckedChange = { draft = draft.copy(followSystemDarkMode = it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "深色模式",
                    subtitle = if (draft.followSystemDarkMode) "当前由系统外观决定。" else "手动切换应用外观。",
                    checked = draft.darkMode,
                    backdrop = backdrop,
                    enabled = !draft.followSystemDarkMode,
                    onCheckedChange = { draft = draft.copy(darkMode = it, followSystemDarkMode = false) }
                )
                SettingsDivider()
                SettingsDockAlignmentRow(
                    selected = draft.dockAlignment,
                    backdrop = backdrop,
                    config = visualConfig,
                    onSelected = { draft = draft.copy(dockAlignment = it) }
                )
                SettingsDivider()
                SettingsHomeStartModeRow(
                    selected = draft.defaultHomeMode,
                    backdrop = backdrop,
                    config = visualConfig,
                    onSelected = { draft = draft.copy(defaultHomeMode = it) }
                )
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsDefaultWallpaperRow(
                    selected = draft.defaultWallpaperStyle,
                    backdrop = backdrop,
                    config = visualConfig,
                    onSelected = { draft = draft.copy(defaultWallpaperStyle = it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "实时活动按钮",
                    subtitle = "在实时活动中显示取消提醒和勿扰按钮。",
                    checked = draft.liveUpdateActionsEnabled,
                    backdrop = backdrop,
                    onCheckedChange = { draft = draft.copy(liveUpdateActionsEnabled = it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "隐藏后台卡片",
                    subtitle = "返回桌面后，从最近任务列表中移除本应用，更无感。",
                    checked = draft.hideFromRecents,
                    backdrop = backdrop,
                    onCheckedChange = { draft = draft.copy(hideFromRecents = it) }
                )
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsActionRow(
                    title = "抓取日志",
                    subtitle = "点击后开始记录，复现问题后点悬浮按钮停止。",
                    buttonText = "开始",
                    iconRes = R.drawable.ic_download,
                    backdrop = backdrop,
                    onClick = {
                        scope.launch {
                            DiagnosticLogCapture.start(context, state.config)
                                .onSuccess {
                                    Toast.makeText(context, "已开始抓取日志", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "日志抓取失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                )
            }
        }
    }
}

private suspend fun createDiagnosticLogUri(context: Context, config: ScheduleConfigEntity): Uri {
    return withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "sleepdown_log_$timestamp.txt")
        val header = buildString {
            appendLine("SleepDown课程表 beta 诊断日志")
            appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("品牌: ${Build.BRAND}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
            appendLine("构建: ${Build.DISPLAY}")
            appendLine("通知模式: ${config.notificationMode}")
            appendLine("实时活动按钮: ${config.liveUpdateActionsEnabled}")
            appendLine("实时活动缩略态: ${config.liveUpdateChipTextMode}")
            appendLine("Promoted 通知权限: ${promotedNotificationStatus(context)}")
            appendLine("忽略电池优化: ${batteryOptimizationStatus(context)}")
            appendLine()
            appendLine("===== logcat -d -v time =====")
        }
        val logcat = runCatching {
            ProcessBuilder("logcat", "-d", "-v", "time")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "logcat 读取失败: ${it.message ?: it::class.java.simpleName}" }
        file.writeText(header + logcat)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

private object DiagnosticLogCapture {
    val recording = MutableStateFlow(false)
    private val lock = Any()
    private var process: Process? = null
    private var worker: Thread? = null
    private var outputFile: File? = null

    suspend fun start(context: Context, config: ScheduleConfigEntity): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                synchronized(lock) {
                    if (process != null) error("日志正在抓取中")
                }
                runCatching {
                    ProcessBuilder("logcat", "-c").start().waitFor()
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(appContext.cacheDir, "sleepdown_log_$timestamp.txt")
                file.writeText(diagnosticLogHeader(appContext, config, "===== logcat -v time ====="))
                val newProcess = ProcessBuilder("logcat", "-v", "time")
                    .redirectErrorStream(true)
                    .start()
                val newWorker = Thread {
                    runCatching {
                        java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
                            newProcess.inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    writer.appendLine(line)
                                }
                            }
                        }
                    }
                }
                synchronized(lock) {
                    process = newProcess
                    worker = newWorker
                    outputFile = file
                    recording.value = true
                }
                newWorker.isDaemon = true
                newWorker.start()
            }
        }
    }

    suspend fun stop(context: Context): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val stoppedProcess: Process?
                val stoppedWorker: Thread?
                val file: File
                synchronized(lock) {
                    stoppedProcess = process
                    stoppedWorker = worker
                    file = outputFile ?: error("没有正在抓取的日志")
                    process = null
                    worker = null
                    outputFile = null
                    recording.value = false
                }
                stoppedProcess?.destroy()
                runCatching { stoppedProcess?.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS) }
                if (stoppedProcess?.isAlive == true) {
                    stoppedProcess.destroyForcibly()
                }
                runCatching { stoppedWorker?.join(1000) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }
}

private fun diagnosticLogHeader(context: Context, config: ScheduleConfigEntity, marker: String): String {
    return buildString {
        appendLine("SleepDown课程表 beta 诊断日志")
        appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("品牌: ${Build.BRAND}")
        appendLine("系统: Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
        appendLine("构建: ${Build.DISPLAY}")
        appendLine("通知模式: ${config.notificationMode}")
        appendLine("实时活动按钮: ${config.liveUpdateActionsEnabled}")
        appendLine("实时活动缩略态: ${config.liveUpdateChipTextMode}")
        appendLine("Promoted 通知权限: ${promotedNotificationStatus(context)}")
        appendLine("忽略电池优化: ${batteryOptimizationStatus(context)}")
        appendLine()
        appendLine(marker)
    }
}

private fun promotedNotificationStatus(context: Context): String {
    if (Build.VERSION.SDK_INT < 36) return "系统不支持运行时查询"
    val manager = context.getSystemService(NotificationManager::class.java) ?: return "NotificationManager 不可用"
    return runCatching {
        val allowed = manager.javaClass
            .getMethod("canPostPromotedNotifications")
            .invoke(manager) as? Boolean
        allowed?.toString() ?: "未知"
    }.getOrElse { "查询失败: ${it.message ?: it::class.java.simpleName}" }
}

private fun batteryOptimizationStatus(context: Context): String {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return "PowerManager 不可用"
    return runCatching {
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) "已忽略" else "未忽略"
    }.getOrElse { "查询失败: ${it.message ?: it::class.java.simpleName}" }
}

private fun shareDiagnosticLog(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "SleepDown课程表诊断日志")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "分享诊断日志"))
}

@Composable
fun SettingsDockAlignmentRow(
    selected: DockAlignment,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DockAlignment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Dock 栏位置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("调整首页底部切换栏对齐方式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DockAlignmentTabs(
            selected = selected,
            backdrop = backdrop,
            config = config,
            onSelected = onSelected
        )
    }
}

@Composable
fun SettingsHomeStartModeRow(
    selected: HomeStartMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (HomeStartMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("默认首页视图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("选择每次打开应用时显示日视图或周视图", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LiquidOptionTabs(
            selectedIndex = if (selected == HomeStartMode.DAY) 0 else 1,
            labels = listOf("日", "周"),
            backdrop = backdrop,
            config = config,
            width = 104.dp,
            onSelected = { index -> onSelected(if (index == 0) HomeStartMode.DAY else HomeStartMode.WEEK) }
        )
    }
}

@Composable
fun DockAlignmentTabs(
    selected: DockAlignment,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DockAlignment) -> Unit
) {
    val options = DockAlignment.values()
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val labels = mapOf(
        DockAlignment.LEFT to "左",
        DockAlignment.CENTER to "中",
        DockAlignment.RIGHT to "右"
    )
    if (backdrop != null) {
        CompositionLocalProvider(LocalContentColor provides glassForegroundColor(config)) {
            LiquidOptionTabs(
                selectedIndex = selectedIndex,
                labels = options.map { labels.getValue(it) },
                backdrop = backdrop,
                config = config,
                width = 132.dp,
                onSelected = { index -> onSelected(options[index.coerceIn(options.indices)]) }
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { alignment ->
                SettingsFallbackChip(labels.getValue(alignment), selected == alignment) { onSelected(alignment) }
            }
        }
    }
}

@Composable
fun LiquidOptionTabs(
    selectedIndex: Int,
    labels: List<String>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    width: Dp,
    onSelected: (Int) -> Unit
) {
    if (backdrop != null) {
        CompositionLocalProvider(LocalContentColor provides glassForegroundColor(config)) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex.coerceIn(labels.indices) },
                onTabSelected = { index -> onSelected(index.coerceIn(labels.indices)) },
                backdrop = backdrop,
                tabsCount = labels.size,
                modifier = Modifier.width(width),
                containerHeight = 42.dp,
                indicatorHeight = 34.dp,
                horizontalPadding = 4.dp,
                lensHeight = 30.dp,
                lensAmount = 34.dp,
                indicatorWidthOverflow = 8.dp,
                indicatorHeightOverflow = 6.dp,
                isLightThemeOverride = glassUsesLightStyle(config)
            ) {
                labels.forEachIndexed { index, label ->
                    LiquidBottomTab(onClick = { onSelected(index) }) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                SettingsFallbackChip(label, selectedIndex == index) { onSelected(index) }
            }
        }
    }
}

@Composable
fun SettingsDefaultWallpaperRow(
    selected: DefaultWallpaperStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DefaultWallpaperStyle) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Text("默认壁纸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("未设置自定义壁纸时使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        DefaultWallpaperTabs(selected = selected, backdrop = backdrop, config = config, onSelected = onSelected)
    }
}

@Composable
private fun SettingsFallbackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DefaultWallpaperTabs(
    selected: DefaultWallpaperStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DefaultWallpaperStyle) -> Unit
) {
    LiquidOptionTabs(
        selectedIndex = if (selected == DefaultWallpaperStyle.KANBAN) 0 else 1,
        labels = listOf("看板娘", "无"),
        backdrop = backdrop,
        config = config,
        width = 140.dp,
        onSelected = { index -> onSelected(if (index == 0) DefaultWallpaperStyle.KANBAN else DefaultWallpaperStyle.NONE) }
    )
}

@Composable
fun LegacyGeneralSettingsScreen(state: AppState, backdrop: Backdrop?, onUpdateConfig: (ScheduleConfigEntity) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "跟随系统",
                    subtitle = "开启后将跟随系统切换浅色或深色模式。",
                    checked = state.config.followSystemDarkMode,
                    backdrop = backdrop,
                    onCheckedChange = { onUpdateConfig(state.config.copy(followSystemDarkMode = it)) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "深色模式",
                    subtitle = if (state.config.followSystemDarkMode) "当前由系统外观决定。" else "手动切换应用外观。",
                    checked = state.config.darkMode,
                    backdrop = backdrop,
                    enabled = !state.config.followSystemDarkMode,
                    onCheckedChange = { onUpdateConfig(state.config.copy(darkMode = it, followSystemDarkMode = false)) }
                )
            }
        }
    }
}

@Composable
fun SettingsGroup(backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    val darkTheme = appUsesDarkTheme(config)
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            surfaceColor = if (darkTheme) ComposeColor(0xFF1C1C1E).copy(alpha = 0.78f) else ComposeColor.White.copy(alpha = 0.94f)
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    } else {
        Column(
            modifier = modifier
                .clip(shape)
                .background(if (darkTheme) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.12f else 0f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(">", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, backdrop: Backdrop?, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (enabled) {
            LiquidControlToggle(checked, onCheckedChange, backdrop)
        } else {
            LiquidControlToggle(checked, {}, backdrop)
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    iconRes: Int,
    backdrop: Backdrop?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        DialogLiquidButton(
            backdrop = backdrop,
            label = buttonText,
            onClick = onClick,
            role = DialogButtonRole.Confirm,
            iconRes = iconRes
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    )
}

@Composable
fun SettingsTextFieldRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val isTimePicker = keyboardType == KeyboardType.Text && value.matches(Regex("\\d{1,2}:\\d{2}"))
    val isDatePicker = keyboardType == KeyboardType.Text && (
            value.matches(Regex("\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}")) ||
                    title.contains("日期")
            )
    if (isTimePicker || isDatePicker) {
        SettingsPickerValueRow(
            title = title,
            value = value,
            enabled = enabled,
            onClick = {
                if (isTimePicker) {
                    showNativeTimePicker(context, value, onValueChange)
                } else {
                    showNativeDatePicker(context, value, onValueChange)
                }
            }
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(170.dp)
        )
    }
}

@Composable
fun SettingsValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        Text(value, modifier = Modifier.offset(y = 1.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsPickerValueRow(title: String, value: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        Text(value.ifBlank { "未设置" }, modifier = Modifier.offset(y = 1.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun showNativeDatePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val date = parseScheduleDate(currentValue) ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onPicked(formatScheduleDate(LocalDate.of(year, month + 1, day)))
        },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

fun showNativeTimePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val time = runCatching { ScheduleImportParser.parseTimeForUi(currentValue) }.getOrNull() ?: java.time.LocalTime.of(8, 0)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onPicked("%02d:%02d".format(hour, minute))
        },
        time.hour,
        time.minute,
        true
    ).show()
}

@Composable
fun SettingsDatePickerRow(title: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    SettingsPickerValueRow(
        title = title,
        value = value,
        enabled = enabled,
        onClick = { showNativeDatePicker(context, value, onValueChange) }
    )
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

@Composable
fun SettingsChoiceRow(title: String, selected: NotificationMode, backdrop: Backdrop?, config: ScheduleConfigEntity, onSelected: (NotificationMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        val modes = NotificationMode.entries
        LiquidOptionTabs(
            selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
            labels = modes.map {
                when (it) {
                    NotificationMode.STANDARD -> "普通通知"
                    NotificationMode.LIVE_UPDATE -> "实时活动"
                }
            },
            backdrop = backdrop,
            config = config,
            width = 188.dp,
            onSelected = { index -> onSelected(modes[index.coerceIn(modes.indices)]) }
        )
    }
}

@Composable
fun SettingsLiveUpdateChipTextRow(
    selected: LiveUpdateChipTextMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (LiveUpdateChipTextMode) -> Unit
) {
    val options = listOf(
        LiveUpdateChipTextMode.LOCATION,
        LiveUpdateChipTextMode.COUNTDOWN,
        LiveUpdateChipTextMode.SHORT
    )
    val labels = listOf("地点", "倒计时", "短标")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("岛上缩略态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        LiquidOptionTabs(
            selectedIndex = options.indexOf(selected).coerceAtLeast(0),
            labels = labels,
            backdrop = backdrop,
            config = config,
            width = 268.dp,
            onSelected = { index -> onSelected(options[index.coerceIn(options.indices)]) }
        )
        Text(
            "可选择缩略态显示上课地点、剩余时间或短标签；原生安卓机型请选择短标签或者倒计时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun SettingsActionButton(label: String, backdrop: Backdrop?, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val tint = if (destructive) ComposeColor(0xFFFF453A) else MaterialTheme.colorScheme.primary
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            surfaceColor = tint.copy(alpha = if (destructive) 0.18f else 0.12f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 8.dp,
            lensHeight = 26.dp,
            lensAmount = 30.dp,
            chromaticAberration = false
        ) {
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Text(
            label,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = if (destructive) 0.20f else 0.14f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ScheduleSettingsContent(
    section: SettingsSection,
    state: AppState,
    backdrop: Backdrop?,
    totalWeeks: String,
    onTotalWeeksChange: (String) -> Unit,
    currentWeek: String,
    onCurrentWeekChange: (String) -> Unit,
    leadMinutes: String,
    onLeadMinutesChange: (String) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notificationMode: NotificationMode,
    onNotificationModeChange: (NotificationMode) -> Unit,
    liveUpdateChipTextMode: LiveUpdateChipTextMode,
    onLiveUpdateChipTextModeChange: (LiveUpdateChipTextMode) -> Unit,
    autoCurrentWeek: Boolean,
    onAutoCurrentWeekChange: (Boolean) -> Unit,
    hideEmptyWeekends: Boolean,
    onHideEmptyWeekendsChange: (Boolean) -> Unit,
    termStartDate: String,
    onTermStartDateChange: (String) -> Unit,
    classDurationMinutes: String,
    onClassDurationMinutesChange: (String) -> Unit,
    breakDurationMinutes: String,
    onBreakDurationMinutesChange: (String) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    dirty: Boolean,
    error: String?,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onPreviewLiveUpdate: () -> Unit
) {
    val appContext = LocalContext.current
    val topPadding = detailContentTopPadding()
    if (section == SettingsSection.Schedule) {
        ScheduleSettingsContentFixed(
            state = state,
            backdrop = backdrop,
            totalWeeks = totalWeeks,
            onTotalWeeksChange = onTotalWeeksChange,
            currentWeek = currentWeek,
            onCurrentWeekChange = onCurrentWeekChange,
            autoCurrentWeek = autoCurrentWeek,
            onAutoCurrentWeekChange = onAutoCurrentWeekChange,
            hideEmptyWeekends = hideEmptyWeekends,
            onHideEmptyWeekendsChange = onHideEmptyWeekendsChange,
            termStartDate = termStartDate,
            onTermStartDateChange = onTermStartDateChange,
            classDurationMinutes = classDurationMinutes,
            onClassDurationMinutesChange = onClassDurationMinutesChange,
            breakDurationMinutes = breakDurationMinutes,
            onBreakDurationMinutesChange = onBreakDurationMinutesChange,
            onAutoMatchPeriodEnds = onAutoMatchPeriodEnds,
            periods = periods,
            onPeriodsChange = onPeriodsChange,
            detectedWeek = detectedWeek,
            dirty = dirty,
            error = error,
            onReset = onReset,
            onSave = onSave,
            topPadding = topPadding
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (section == SettingsSection.Schedule) "课表设置" else "通知设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (dirty) {
                    SettingsActionButton("重置", backdrop, onClick = onReset, destructive = true)
                    Spacer(Modifier.width(8.dp))
                    SettingsActionButton("保存", backdrop, onClick = onSave)
                }
            }
        }
        if (section == SettingsSection.Schedule) {
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                    SettingsDivider()
                    SettingsTextFieldRow("当前周", currentWeek, { onCurrentWeekChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = !autoCurrentWeek)
                    SettingsDivider()
                    SettingsToggleRow("自动计算当前周", "根据学期开始日期计算，当前为第 $detectedWeek 周", autoCurrentWeek, backdrop, onCheckedChange = onAutoCurrentWeekChange)
                    SettingsDivider()
                    SettingsTextFieldRow("学期开始日期", termStartDate, onTermStartDateChange)
                }
            }
            item { Text("节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { idx, period ->
                        SettingsValueRow("第 ${period.periodIndex} 节", "")
                        SettingsTextFieldRow("开始时间", period.startTime, { value ->
                            onPeriodsChange(periods.toMutableList().also { it[idx] = period.copy(startTime = value) })
                        })
                        SettingsTextFieldRow("结束时间", period.endTime, { value ->
                            onPeriodsChange(periods.toMutableList().also { it[idx] = period.copy(endTime = value) })
                        })
                        if (idx != periods.lastIndex) SettingsDivider()
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionButton("添加节次", backdrop, onClick = {
                        val next = (periods.maxOfOrNull { it.periodIndex } ?: 0) + 1
                        onPeriodsChange(periods + PeriodEntity(next, "08:00", "08:45"))
                    })
                    SettingsActionButton("删除末节", backdrop, onClick = {
                        if (periods.isNotEmpty()) onPeriodsChange(periods.dropLast(1))
                    })
                }
            }
        } else {
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "课程提醒",
                        subtitle = if (notificationsEnabled) "将按设置提前提醒即将开始的课程。" else "关闭后不会发送课程提醒。",
                        checked = notificationsEnabled,
                        backdrop = backdrop,
                        onCheckedChange = onNotificationsEnabledChange
                    )
                    SettingsDivider()
                    SettingsTextFieldRow("提前提醒分钟", leadMinutes, { onLeadMinutesChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = notificationsEnabled)
                    SettingsDivider()
                    SettingsChoiceRow("通知样式", notificationMode, backdrop, state.config, onNotificationModeChange)
                    if (notificationMode == NotificationMode.LIVE_UPDATE) {
                        SettingsDivider()
                        SettingsLiveUpdateChipTextRow(liveUpdateChipTextMode, backdrop, state.config, onLiveUpdateChipTextModeChange)
                    }
                }
            }
            item {
                Text(
                    "实时活动目前仅支持原生安卓系统、ColorOS16、HyperOS 3.0.300以上版本、荣耀 MagicOS 10。原生安卓机型请选择短标签或者倒计时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow("保活权限", "为保证课程提醒和实时活动稳定弹出，请允许电池优化例外，并在系统权限管理中允许后台运行或自启动。不同厂商的入口可能不同。")
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsActionButton("电池优化", backdrop, onClick = {
                            runCatching {
                                appContext.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${appContext.packageName}")))
                            }.onFailure {
                                appContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${appContext.packageName}")))
                            }
                        }, modifier = Modifier.weight(1f))
                        SettingsActionButton("自启动设置", backdrop, onClick = {
                            openKeepAliveSettings(appContext)
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
            item { SettingsActionButton("测试实时活动", backdrop, onClick = onPreviewLiveUpdate, modifier = Modifier.fillMaxWidth()) }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

@Composable
fun ScheduleSettingsContentFixed(
    state: AppState,
    backdrop: Backdrop?,
    totalWeeks: String,
    onTotalWeeksChange: (String) -> Unit,
    currentWeek: String,
    onCurrentWeekChange: (String) -> Unit,
    autoCurrentWeek: Boolean,
    onAutoCurrentWeekChange: (Boolean) -> Unit,
    hideEmptyWeekends: Boolean,
    onHideEmptyWeekendsChange: (Boolean) -> Unit,
    termStartDate: String,
    onTermStartDateChange: (String) -> Unit,
    classDurationMinutes: String,
    onClassDurationMinutesChange: (String) -> Unit,
    breakDurationMinutes: String,
    onBreakDurationMinutesChange: (String) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    dirty: Boolean,
    error: String?,
    onReset: () -> Unit,
    onSave: () -> Unit,
    topPadding: Dp = detailContentTopPadding()
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = DockScrollPadding),
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
                if (dirty) {
                    SettingsActionButton("重置", backdrop, onClick = onReset, destructive = true)
                    Spacer(Modifier.width(8.dp))
                    SettingsActionButton("保存", backdrop, onClick = onSave)
                }
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("当前周", currentWeek, { onCurrentWeekChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = !autoCurrentWeek)
                SettingsDivider()
                SettingsToggleRow(
                    title = "自动计算当前周",
                    subtitle = "根据学期开始日期计算，当前为第 $detectedWeek 周",
                    checked = autoCurrentWeek,
                    backdrop = backdrop,
                    onCheckedChange = onAutoCurrentWeekChange
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "隐藏空周末",
                    subtitle = "当前周周六、周日没有课程时自动收起周末列",
                    checked = hideEmptyWeekends,
                    backdrop = backdrop,
                    onCheckedChange = onHideEmptyWeekendsChange
                )
                SettingsDivider()
                SettingsDatePickerRow("学期开始日期", termStartDate, onTermStartDateChange)
            }
        }
        item { Text("节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsTextFieldRow("单节课分钟数", classDurationMinutes, { onClassDurationMinutesChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("课间分钟数", breakDurationMinutes, { onBreakDurationMinutesChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                SettingsInfoRow("自动匹配", "点击后按单节课时长和课间时长重新生成节次时间；手动修改的课间间隔不会被自动覆盖。")
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    SettingsActionButton("自动匹配", backdrop, onClick = onAutoMatchPeriodEnds)
                }
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { idx, period ->
                    SettingsValueRow("第 ${period.periodIndex} 节", "")
                    SettingsTextFieldRow("开始时间", period.startTime, { value ->
                        onPeriodsChange(periods.toMutableList().also { it[idx] = period.copy(startTime = value) })
                    })
                    SettingsTextFieldRow("结束时间", period.endTime, { value ->
                        onPeriodsChange(periods.toMutableList().also { it[idx] = period.copy(endTime = value) })
                    })
                    if (idx != periods.lastIndex) SettingsDivider()
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("添加节次", backdrop, onClick = {
                    val next = (periods.maxOfOrNull { it.periodIndex } ?: 0) + 1
                    onPeriodsChange(periods + PeriodEntity(next, "08:00", "08:45"))
                })
                SettingsActionButton("删除末节", backdrop, onClick = {
                    if (periods.isNotEmpty()) onPeriodsChange(periods.dropLast(1))
                })
            }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

@Composable
fun ScheduleConfigScreen(state: AppState, backdrop: Backdrop?, section: SettingsSection, onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit, onPreviewLiveUpdate: () -> Unit) {
    val visualState = state.copy(config = settingsVisualConfig(state.config))
    var totalWeeks by remember { mutableStateOf(state.config.totalWeeks.toString()) }
    var currentWeek by remember { mutableStateOf(state.config.currentWeek.toString()) }
    var leadMinutes by remember { mutableStateOf(state.config.notificationLeadMinutes.toString()) }
    var notificationsEnabled by remember { mutableStateOf(state.config.notificationsEnabled) }
    var notificationMode by remember { mutableStateOf(state.config.notificationMode) }
    var liveUpdateChipTextMode by remember { mutableStateOf(state.config.liveUpdateChipTextMode) }
    var autoCurrentWeek by remember { mutableStateOf(state.config.autoCurrentWeek) }
    var hideEmptyWeekends by remember { mutableStateOf(state.config.hideEmptyWeekends) }
    var termStartDate by remember { mutableStateOf(state.config.termStartDate.orEmpty()) }
    var classDurationMinutes by remember { mutableStateOf(state.config.classDurationMinutes.toString()) }
    var breakDurationMinutes by remember { mutableStateOf(state.config.breakDurationMinutes.toString()) }
    var periods by remember { mutableStateOf(state.periods) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSavedConfig by remember { mutableStateOf(state.config) }
    var lastSavedPeriods by remember { mutableStateOf(state.periods) }
    var currentDraftScheduleId by remember { mutableIntStateOf(state.config.id) }

    fun resetConfigDraftFromState() {
        currentDraftScheduleId = state.config.id
        totalWeeks = state.config.totalWeeks.toString()
        currentWeek = state.config.currentWeek.toString()
        leadMinutes = state.config.notificationLeadMinutes.toString()
        notificationsEnabled = state.config.notificationsEnabled
        notificationMode = state.config.notificationMode
        liveUpdateChipTextMode = state.config.liveUpdateChipTextMode
        autoCurrentWeek = state.config.autoCurrentWeek
        hideEmptyWeekends = state.config.hideEmptyWeekends
        termStartDate = state.config.termStartDate.orEmpty()
        classDurationMinutes = state.config.classDurationMinutes.toString()
        breakDurationMinutes = state.config.breakDurationMinutes.toString()
        periods = state.periods
        error = null
        lastSavedConfig = state.config
        lastSavedPeriods = state.periods
    }

    fun computeDirty(): Boolean {
        return totalWeeks != lastSavedConfig.totalWeeks.toString() ||
            currentWeek != lastSavedConfig.currentWeek.toString() ||
            leadMinutes != lastSavedConfig.notificationLeadMinutes.toString() ||
            notificationsEnabled != lastSavedConfig.notificationsEnabled ||
            notificationMode != lastSavedConfig.notificationMode ||
            liveUpdateChipTextMode != lastSavedConfig.liveUpdateChipTextMode ||
            autoCurrentWeek != lastSavedConfig.autoCurrentWeek ||
            hideEmptyWeekends != lastSavedConfig.hideEmptyWeekends ||
            termStartDate != lastSavedConfig.termStartDate.orEmpty() ||
            classDurationMinutes != lastSavedConfig.classDurationMinutes.toString() ||
            breakDurationMinutes != lastSavedConfig.breakDurationMinutes.toString() ||
            periods != lastSavedPeriods
    }

    LaunchedEffect(state.config.id, state.config, state.periods) {
        if (state.config.id != currentDraftScheduleId || !computeDirty()) {
            resetConfigDraftFromState()
        }
    }
    val detectedWeek = remember(autoCurrentWeek, termStartDate, totalWeeks, currentWeek) {
        val total = totalWeeks.toIntOrNull() ?: state.config.totalWeeks
        val manual = currentWeek.toIntOrNull() ?: state.config.currentWeek
        effectiveCurrentWeek(state.config.copy(totalWeeks = total.coerceAtLeast(1), currentWeek = manual.coerceAtLeast(1), termStartDate = termStartDate.ifBlank { null }, autoCurrentWeek = true))
    }
    val displayedCurrentWeek = if (autoCurrentWeek) detectedWeek.toString() else currentWeek
    val dirty = computeDirty()

    fun resetConfigDraft() {
        resetConfigDraftFromState()
    }

    fun saveConfigDraft() {
        val total = totalWeeks.toIntOrNull()
        val current = currentWeek.toIntOrNull()
        val lead = leadMinutes.toIntOrNull()
        val classDuration = classDurationMinutes.toIntOrNull()
        val breakDuration = breakDurationMinutes.toIntOrNull()
        try {
            require(total != null && total in 1..60) { "总周数必须在 1 到 60 之间" }
            require(current != null && current in 1..total) { "当前周必须在 1 到总周数之间" }
            require(lead != null && lead in 0..180) { "提醒分钟必须在 0 到 180 之间" }
            require(classDuration != null && classDuration in 1..300) { "单节课分钟数必须在 1 到 300 之间" }
            require(breakDuration != null && breakDuration in 0..300) { "课间分钟数必须在 0 到 300 之间" }
            if (autoCurrentWeek) {
                require(termStartDate.isNotBlank()) { "开启自动计算当前周时必须填写学期开始日期" }
                val date = parseScheduleDate(termStartDate)
                require(date != null) { "学期开始日期必须是 yyyy-MM-dd 格式" }
            } else if (termStartDate.isNotBlank()) {
                require(parseScheduleDate(termStartDate) != null) { "学期开始日期必须是 yyyy-MM-dd 格式" }
            }
            val nextPeriods = periods
                .filter { it.periodIndex > 0 }
                .distinctBy { it.periodIndex }
                .sortedBy { it.periodIndex }
            require(nextPeriods.isNotEmpty()) { "至少需要保留 1 个节次" }
            nextPeriods.forEach {
                val start = ScheduleImportParser.parseTimeForUi(it.startTime)
                val end = ScheduleImportParser.parseTimeForUi(it.endTime)
                require(start < end) { "第" + it.periodIndex + "节结束时间必须晚于开始时间" }
            }
            val effectiveWeekForSave = if (autoCurrentWeek) {
                effectiveCurrentWeek(
                    state.config.copy(
                        totalWeeks = total,
                        currentWeek = current,
                        termStartDate = termStartDate.ifBlank { null },
                        autoCurrentWeek = true
                    )
                )
            } else {
                current
            }
            error = null
            val nextConfig = state.config.copy(
                totalWeeks = total,
                currentWeek = effectiveWeekForSave,
                notificationLeadMinutes = lead,
                termStartDate = termStartDate.ifBlank { null },
                autoCurrentWeek = autoCurrentWeek,
                hideEmptyWeekends = hideEmptyWeekends,
                notificationsEnabled = notificationsEnabled,
                notificationMode = notificationMode,
                liveUpdateChipTextMode = liveUpdateChipTextMode,
                classDurationMinutes = classDuration,
                breakDurationMinutes = breakDuration
            )
            currentWeek = effectiveWeekForSave.toString()
            periods = nextPeriods
            onSave(nextConfig, nextPeriods)
            lastSavedConfig = nextConfig
            lastSavedPeriods = nextPeriods
        } catch (t: Throwable) {
            error = t.message ?: "设置保存失败"
        }
    }

    ScheduleSettingsContent(
        section = section,
        state = visualState,
        backdrop = backdrop,
        totalWeeks = totalWeeks,
        onTotalWeeksChange = { totalWeeks = it },
        currentWeek = displayedCurrentWeek,
        onCurrentWeekChange = { currentWeek = it },
        leadMinutes = leadMinutes,
        onLeadMinutesChange = { leadMinutes = it },
        notificationsEnabled = notificationsEnabled,
        onNotificationsEnabledChange = { notificationsEnabled = it },
        notificationMode = notificationMode,
        onNotificationModeChange = { notificationMode = it },
        liveUpdateChipTextMode = liveUpdateChipTextMode,
        onLiveUpdateChipTextModeChange = { liveUpdateChipTextMode = it },
        autoCurrentWeek = autoCurrentWeek,
        onAutoCurrentWeekChange = { autoCurrentWeek = it },
        hideEmptyWeekends = hideEmptyWeekends,
        onHideEmptyWeekendsChange = { hideEmptyWeekends = it },
        termStartDate = termStartDate,
        onTermStartDateChange = { termStartDate = it },
        classDurationMinutes = classDurationMinutes,
        onClassDurationMinutesChange = { classDurationMinutes = it },
        breakDurationMinutes = breakDurationMinutes,
        onBreakDurationMinutesChange = { breakDurationMinutes = it },
        onAutoMatchPeriodEnds = {
            val classDuration = classDurationMinutes.toIntOrNull() ?: state.config.classDurationMinutes
            val breakDuration = breakDurationMinutes.toIntOrNull() ?: state.config.breakDurationMinutes
            periods = autoMatchPeriodTimes(periods, classDuration, breakDuration)
        },
        periods = periods,
        onPeriodsChange = { periods = it },
        detectedWeek = detectedWeek,
        dirty = dirty,
        error = error,
        onReset = { resetConfigDraft() },
        onSave = { saveConfigDraft() },
        onPreviewLiveUpdate = onPreviewLiveUpdate
    )
}

private fun autoMatchPeriodTimes(periods: List<PeriodEntity>, classDurationMinutes: Int, breakDurationMinutes: Int): List<PeriodEntity> {
    val duration = classDurationMinutes.coerceIn(1, 300).toLong()
    val breakDuration = breakDurationMinutes.coerceIn(0, 300).toLong()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    var cursor = periods.firstOrNull()?.startTime?.let {
        runCatching { ScheduleImportParser.parseTimeForUi(it) }.getOrNull()
    }
    return periods.map { period ->
        val start = cursor ?: runCatching { ScheduleImportParser.parseTimeForUi(period.startTime) }.getOrNull()
        if (start == null) return@map period
        val end = start.plusMinutes(duration)
        cursor = end.plusMinutes(breakDuration)
        period.copy(
            startTime = start.format(formatter),
            endTime = end.format(formatter)
        )
    }
}

@Composable
fun SettingsSectionSwitch(selected: SettingsSection, onSelected: (SettingsSection) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "课表设置",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected == SettingsSection.Schedule) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSelected(SettingsSection.Schedule) }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
        Text(
            "通知设置",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected == SettingsSection.Notifications) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSelected(SettingsSection.Notifications) }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

fun openKeepAliveSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
    intents.firstOrNull { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(values: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChoiceRow(values: List<Int>, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, label: (Int) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = value in selected, onClick = { onSelected(if (value in selected) selected - value else selected + value) }, label = { Text(label(value)) })
        }
    }
}

private fun weekdayLabel(weekday: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]
