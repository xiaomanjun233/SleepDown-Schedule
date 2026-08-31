package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.agent.*

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
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import android.net.http.SslError
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.WindowInsetsController
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.attachOpeningSourceSnapshotHandoff
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
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

private fun formatAiImportFileSize(bytes: Int): String {
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / 1024f / 1024f)
        bytes >= 1024 -> "%.1f KB".format(Locale.US, bytes / 1024f)
        else -> "$bytes B"
    }
}

/**
 * A Home graphics-layer frame is rooted at the Home content node, while history buttons report
 * bounds in window coordinates. Keep the producer origin with the bitmap so source crops are
 * always made in the same coordinate space.
 */
data class AiImportHistoryBackgroundCapture(
    val bitmap: Bitmap,
    val rootLeftInWindow: Float,
    val rootTopInWindow: Float
)

private val WakeUpLabelledKey = Regex(
    "分享口令(?:为)?\\s*[「“\\\"]?\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\\\"]?",
    setOf(RegexOption.IGNORE_CASE)
)
private val WakeUpBareKey = Regex("^[A-Za-z0-9_-]{8,200}$")
private val StarLinkStructuredCode = Regex(
    "(?:星链|StarLink|输入[:：])[^A-Za-z0-9-]*([A-Za-z0-9-]{5,20})",
    setOf(RegexOption.IGNORE_CASE)
)
private val StarLinkBareCode = Regex("^[A-Za-z0-9-]{5,20}$")

private data class ManualShiguangToolRequest(
    val adapterId: String,
    val label: String,
    val input: String
)

private fun extractWakeUpShareKey(value: String): String? {
    val text = value.trim()
    if (text.isBlank()) return null
    WakeUpLabelledKey.find(text)?.groupValues?.getOrNull(1)?.let { return it }
    if (text.contains("wakeup", ignoreCase = true)) {
        Regex("[A-Za-z0-9_-]{8,200}").findAll(text).lastOrNull()?.value?.let { return it }
    }
    return text.takeIf(WakeUpBareKey::matches)
}

private fun extractStarLinkShareCode(value: String): String? {
    val text = value.trim()
    if (text.isBlank()) return null
    return StarLinkStructuredCode.find(text)?.groupValues?.getOrNull(1)
        ?: text.takeIf(StarLinkBareCode::matches)
}

@Composable
fun NormalizedAiManualImportScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    captureHistoryBackground: suspend () -> AiImportHistoryBackgroundCapture? = { null },
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    val transitionDensity = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var aiParsing by remember { mutableStateOf(false) }
    var manualToolRequest by remember { mutableStateOf<ManualShiguangToolRequest?>(null) }
    var manualToolWebView by remember { mutableStateOf<WebView?>(null) }
    var manualToolBridge by remember { mutableStateOf<ShiguangBridgeHost?>(null) }
    var manualBridgeInteraction by remember { mutableStateOf<EduBridgeInteractionRequest?>(null) }
    var showAiTokenRepairPrompt by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) }
    var aiSettings by remember { mutableStateOf(AiImportSettingsStore.load(context)) }
    var historySourceHidden by remember { mutableStateOf(false) }
    var historyEntries by remember { mutableStateOf(AiImportHistoryStore.load(context)) }
    val taskProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    LaunchedEffect(taskProgress?.taskId, taskProgress?.finished) {
        val completed = taskProgress?.takeIf { it.taskId.isNotBlank() && it.finished } ?: return@LaunchedEffect
        aiParsing = false
        routeMessage = completed.liveSummary.ifBlank { completed.steps.lastOrNull().orEmpty() }
        error = completed.error
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                historyEntries = AiImportHistoryStore.load(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun releaseManualToolWebView() {
        manualToolBridge?.bindWebView(null)
        manualToolWebView?.let { released ->
            released.uninstallShiguangRuntime()
            released.releaseSleepDownWebView(clearResourceCache = false)
        }
        manualToolWebView = null
        manualToolBridge = null
        manualBridgeInteraction = null
    }
    DisposableEffect(Unit) {
        onDispose {
            manualToolBridge?.bindWebView(null)
            manualToolWebView?.let { released ->
                released.uninstallShiguangRuntime()
                released.releaseSleepDownWebView(clearResourceCache = false)
            }
        }
    }
    LaunchedEffect(manualToolRequest) {
        val request = manualToolRequest ?: return@LaunchedEffect
        releaseManualToolWebView()
        val adapter = ShiguangWarehouse.loadAdapters(context).firstOrNull {
            it.school.id == "GLOBAL_TOOLS" && it.adapterId.equals(request.adapterId, ignoreCase = true)
        }
        if (adapter == null) {
            aiParsing = false
            manualToolRequest = null
            error = "未找到拾光 ${request.label} 官方适配器"
            return@LaunchedEffect
        }
        val source = runCatching { ShiguangWarehouse.resolveScript(context, adapter) }
            .getOrElse {
                aiParsing = false
                manualToolRequest = null
                error = "拾光 ${request.label} 官方脚本读取失败：${it.message ?: "未知错误"}"
                return@LaunchedEffect
            }
        lateinit var target: WebView
        val bridge = ShiguangBridgeHost(
            context = context,
            onDraft = { draft ->
                if (manualToolWebView === target) releaseManualToolWebView()
                manualToolRequest = null
                aiParsing = false
                error = null
                routeMessage = "${request.label} 口令已解析，正在进入导入预览。"
                onParsed(draft)
            },
            onMessage = { message ->
                routeMessage = message
                if (message.contains("失败") || message.contains("无效")) {
                    error = message
                    aiParsing = false
                    manualToolRequest = null
                    if (manualToolWebView === target) releaseManualToolWebView()
                }
            },
            onInteractionRequest = { manualBridgeInteraction = it }
        )
        manualToolBridge = bridge
        bridge.beginTask(state.config, state.periods, initialPromptAnswer = request.input)
        var scriptStarted = false
        target = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            configureEduImportSecurity()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            installShiguangRuntime(bridge)
            bridge.bindWebView(this)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.injectShiguangRuntime(desktopMode = false)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    view.injectShiguangRuntime(desktopMode = false)
                    if (!scriptStarted) {
                        scriptStarted = true
                        view.evaluateJavascript(source, null)
                    }
                }
            }
        }
        manualToolWebView = target
        routeMessage = "正在使用拾光 ${request.label} 官方适配器读取口令…"
        target.loadUrl(adapter.importUrl.ifBlank { "about:blank" })
    }
    val icsFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) launcher@{ uri ->
        if (uri == null) return@launcher
        selectedFileName = null
        routeMessage = null
        error = null
        scope.launch {
            loadAiImportFile(context, uri)
                .onSuccess { file ->
                    selectedFileName = file.displayName
                    if (!file.isIcs) {
                        error = "所选文件不是有效的 ICS 日历文件"
                        return@onSuccess
                    }
                    routeMessage = "ICS 将在本机解析，不会调用 AI，也不会消耗模型额度。"
                    IcsScheduleCodec.parse(file.bytes, state.config)
                        .onSuccess { draft ->
                            error = null
                            onParsed(draft)
                        }
                        .onFailure { error = it.message ?: "ICS 解析失败" }
                }
                .onFailure { error = it.message ?: "ICS 文件读取失败" }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) launcher@{ uri ->
        if (uri == null) return@launcher
        val settings = AiImportSettingsStore.load(context)
        aiSettings = settings
        selectedFileName = null
        routeMessage = null
        error = null
        aiParsing = true
        scope.launch {
            loadAiImportFile(context, uri)
                .onSuccess fileLoaded@{ file ->
                    selectedFileName = file.displayName
                    if (file.isIcs) {
                        error = "请在“导入 ICS”栏选择日历文件"
                        aiParsing = false
                        return@fileLoaded
                    }
                    val localTextPreviewResult = withContext(Dispatchers.Default) {
                        runCatching { extractAiImportTextPreview(file) }
                    }
                    if (file.isLocalTextDocument && localTextPreviewResult.isFailure) {
                        error = localTextPreviewResult.exceptionOrNull()?.message ?: "文件文字提取失败"
                        aiParsing = false
                        return@fileLoaded
                    }
                    val localTextPreview = localTextPreviewResult.getOrNull()
                    val fileSummary = buildString {
                        appendLine("文件名：${file.displayName}")
                        appendLine("类型：${file.mimeType}")
                        appendLine("大小：${formatAiImportFileSize(file.bytes.size)}")
                        if (localTextPreview != null) {
                            appendLine("本地读取：${localTextPreview.formatLabel}")
                            appendLine()
                            appendLine("本地提取预览：")
                            append(localTextPreview.text.take(60_000))
                        }
                    }
                    val previewResult = withContext(Dispatchers.Default) {
                        runCatching { renderAiImportPreviewImages(context, file) }
                    }
                    if (file.isPdf && localTextPreview == null && previewResult.isFailure) {
                        error = previewResult.exceptionOrNull()?.message ?: "PDF 页面读取失败"
                        aiParsing = false
                        return@fileLoaded
                    }
                    val previewImages = previewResult.getOrDefault(emptyList())
                    if (previewImages.isNotEmpty() && !AiProviderPresets.supportsImageInput(settings.profile)) {
                        error = if (settings.profile.id == AiProviderPresets.deepSeek.id) {
                            "当前 DeepSeek 模型只接收文字。请在 AI 设置中选择 V4 Flash Vision Exp，或改用带文字的 PDF/XLSX/CSV。"
                        } else {
                            "当前模型不支持图片输入，请换视觉模型，或上传可提取文字的 PDF/XLSX/CSV。"
                        }
                        aiParsing = false
                        return@fileLoaded
                    }
                    routeMessage = when {
                        localTextPreview != null -> "已在本机提取文字；确认后只发送文字，不上传原文件。"
                        file.isPdf -> "PDF 未提取到足够文字；确认后将发送逐页转换的图片。"
                        file.isImage -> "图片已在本机压缩暂存；确认后才会发送给视觉模型。"
                        else -> "文件已在本机暂存；确认后才会发送给 AI。"
                    }
                    AiEduImportProgressSession.clearActions()
                    AiEduImportProgressSession.update(
                        AiEduImportProgress(
                            routeLabel = "AI 手动导入",
                            steps = listOf("已读取文件，等待确认"),
                            userPrompt = "帮我按规则导入这份课表",
                            attachmentTitle = file.displayName,
                            pageText = fileSummary,
                            screenshotPreviews = previewImages,
                            awaitingConfirmation = true,
                            confirmActionLabel = "确认发送并解析",
                            cancelActionLabel = "取消"
                        )
                    )
                    AiEduImportProgressSession.setActions(
                        onConfirm = {
                            aiParsing = true
                            routeMessage = "正在使用 ${settings.profile.displayName} 解析 ${file.displayName}..."
                            val initial = checkNotNull(AiEduImportProgressSession.progress.value).copy(
                                steps = listOf("已读取文件", "已确认发送"),
                                requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}"
                            )
                            AiImportTaskManager.startFileImport(
                                context = context,
                                file = file,
                                settings = settings,
                                scheduleConfig = state.config,
                                initialProgress = initial
                            )
                        },
                        onCancel = {
                            aiParsing = false
                            routeMessage = "已取消，文件没有发送给 AI。"
                        }
                    )
                    context.openRegisteredActivity(
                        TransitionRouteId.ImportToAiProgress,
                        Intent(context, AiEduImportProgressActivity::class.java)
                    )
                }
                .onFailure {
                    error = it.message ?: "文件读取失败"
                    aiParsing = false
                    AiEduImportProgressSession.update(
                        AiEduImportProgress(
                            routeLabel = "AI 手动导入",
                            steps = listOf("准备读取文件", "文件读取失败"),
                            requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}",
                            error = it.message ?: "文件读取失败",
                            finished = true
                        )
                    )
                }
        }
    }
    fun parseDraft() {
        val result = ScheduleImportParser.parse(jsonText, state.config)
        result.onSuccess {
            error = null
            onParsed(it)
        }.onFailure {
            val starLinkCode = extractStarLinkShareCode(jsonText)
            if (starLinkCode != null) {
                error = null
                aiParsing = true
                manualToolRequest = ManualShiguangToolRequest(
                    adapterId = "StarLink",
                    label = "星链",
                    input = jsonText
                )
                return@onFailure
            }
            val wakeUpKey = extractWakeUpShareKey(jsonText)
            if (wakeUpKey != null) {
                error = null
                aiParsing = true
                manualToolRequest = ManualShiguangToolRequest(
                    adapterId = "WakeUp",
                    label = "WakeUp",
                    input = jsonText
                )
                return@onFailure
            }
            val latestSettings = AiImportSettingsStore.load(context)
            aiSettings = latestSettings
            if (latestSettings.apiKey.isNotBlank() && jsonText.isNotBlank()) {
                error = null
                showAiTokenRepairPrompt = true
            } else {
                error = it.message ?: "口令解析失败"
            }
        }
    }
    fun repairTokenWithAi() {
        if (aiParsing) return
        val settings = AiImportSettingsStore.load(context)
        aiSettings = settings
        showAiTokenRepairPrompt = false
        aiParsing = true
        routeMessage = "课表口令已暂存，确认后才会发送给 AI。"
        AiEduImportProgressSession.clearActions()
        AiEduImportProgressSession.update(
            AiEduImportProgress(
                routeLabel = "AI 手动导入",
                steps = listOf("本地口令校验未通过", "已暂存，等待确认"),
                userPrompt = "帮我按规则整理并导入这份课表",
                attachmentTitle = "课表口令文本",
                pageText = jsonText.take(40_000),
                awaitingConfirmation = true,
                confirmActionLabel = "确认发送并解析",
                cancelActionLabel = "取消"
            )
        )
        AiEduImportProgressSession.setActions(
            onConfirm = {
                routeMessage = "正在使用 ${settings.profile.displayName} 整理课表口令..."
                val repairInput = buildString {
                    appendLine("下面是一个格式不完整或不规范的 SleepDown 课程表口令。")
                    appendLine("请理解其中的课程信息，严格按照 SleepDown 课表导入协议整理并只返回可导入结果。")
                    appendLine()
                    append(jsonText)
                }
                val initial = checkNotNull(AiEduImportProgressSession.progress.value).copy(
                    steps = listOf("本地口令校验未通过", "已确认发送"),
                    requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n输入：用户粘贴的非标准课表口令"
                )
                AiImportTaskManager.startTextImport(
                    context = context,
                    text = repairInput,
                    sourceName = "手动粘贴的非标准课表口令",
                    settings = settings,
                    scheduleConfig = state.config,
                    initialProgress = initial
                )
            },
            onCancel = {
                aiParsing = false
                routeMessage = "已取消，课表口令没有发送给 AI。"
            }
        )
        context.openRegisteredActivity(
            TransitionRouteId.ImportToAiProgress,
            Intent(context, AiEduImportProgressActivity::class.java)
        )
    }
    AiManualImportDialogContent(
        state = state,
        backdrop = backdrop,
        onCancel = onCancel,
        selectedMode = selectedMode,
        onModeSelected = {
            selectedMode = it
            error = null
        },
        aiSettings = aiSettings,
        onRefreshSettings = { aiSettings = AiImportSettingsStore.load(context) },
        jsonText = jsonText,
        onJsonTextChange = { jsonText = it },
        onCopyPrompt = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SleepDown 课表口令提示词", SchedulePromptBuilder.buildTokenPrompt()))
        },
        onCleanText = { jsonText = ScheduleImportParser.cleanMarkdown(jsonText) },
        selectedFileName = selectedFileName,
        routeMessage = routeMessage,
        error = error,
        aiParsing = aiParsing,
        historyEntries = historyEntries,
        historySourceHidden = historySourceHidden,
        onOpenHistory = { sourceBounds ->
            scope.launch {
                // TODO(OPLUS_DEFERRED_20260823): Retained for investigation only. The signed
                // PLJ110 acceptance build still flashed blank on this native OPEN path.
                val captured = captureHistoryBackground()
                val sourceSnapshot = captured?.cropToAiHistorySource(sourceBounds)
                // A malformed crop cannot represent the real source button. Keeping the source
                // mounted is preferable to hiding it and exposing a black/uncurved Activity shell.
                if (sourceSnapshot == null) return@launch
                val activity = context as? ComponentActivity ?: return@launch
                val anchor = TransitionAnchorFrame(
                    boundsInWindow = sourceBounds,
                    cornerRadiusPx = with(transitionDensity) { 21.dp.toPx() },
                    bitmap = sourceSnapshot
                )
                val releaseOpeningSource =
                    activity.attachOpeningSourceSnapshotHandoff(anchor) ?: return@launch
                historySourceHidden = true
                withFrameNanos { }
                withFrameNanos { }
                val backgroundSnapshot = captureHistoryBackground()?.bitmap
                // The detail Activity needs both halves of the handoff. Starting it without
                // the clean source-page frame regresses to the black fallback shell that the
                // real source was just hidden to avoid.
                if (backgroundSnapshot == null) {
                    releaseOpeningSource()
                    historySourceHidden = false
                    return@launch
                }
                val intent = Intent(context, AiImportHistoryActivity::class.java)
                val launchResult = ActivityTransitionCoordinator.open(
                    activity = activity,
                    routeId = TransitionRouteId.ManualImportToHistory,
                    intent = intent,
                    payload = TransitionPayload(
                        openingAnchor = anchor,
                        returnAnchorProvider = StaticTransitionAnchorProvider(anchor),
                        backgroundBitmap = backgroundSnapshot,
                        onOpeningSourceHandoff = releaseOpeningSource,
                        nativeSourceLeashAlphaOutOnOpen = true,
                        onSourceReleased = { historySourceHidden = false }
                    )
                )
                if (launchResult is TransitionLaunchResult.Failed) {
                    releaseOpeningSource()
                    historySourceHidden = false
                    return@launch
                }
            }
        },
        onOpenHistoryEntry = { entry, sourceBounds ->
            scope.launch {
                // Both history entry points use the same Activity destination. Release focus
                // before recording so the IME is not baked into the transition underlay.
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                withFrameNanos { }
                val captured = captureHistoryBackground() ?: return@launch
                val sourceSnapshot = captured.cropToAiHistorySource(sourceBounds) ?: return@launch
                val activity = context as? ComponentActivity ?: return@launch
                val anchor = TransitionAnchorFrame(
                    boundsInWindow = sourceBounds,
                    cornerRadiusPx = with(transitionDensity) { 18.dp.toPx() },
                    bitmap = sourceSnapshot
                )
                ActivityTransitionCoordinator.open(
                    activity = activity,
                    routeId = TransitionRouteId.AiHistoryToDetail,
                    intent = Intent(context, AiImportHistoryDetailActivity::class.java)
                        .putExtra(AiImportHistoryDetailActivityHost.EntryIdExtra, entry.id),
                    payload = TransitionPayload(
                        openingAnchor = anchor,
                        returnAnchorProvider = StaticTransitionAnchorProvider(anchor),
                        backgroundBitmap = captured.bitmap
                    )
                )
            }
        },
        onPrimaryAction = {
            when (selectedMode) {
                0 -> parseDraft()
                1 -> icsFileLauncher.launch(
                    arrayOf("text/calendar", "application/ics", "application/octet-stream")
                )
                else -> if (!aiParsing) {
                    // Some document providers report office files as application/octet-stream;
                    // validate the real extension/MIME after selection instead of hiding them here.
                    fileLauncher.launch(arrayOf("*/*"))
                }
            }
        }
    )
    manualToolBridge?.let { bridge ->
        EduBridgeInteractionDialog(
            request = manualBridgeInteraction,
            bridge = bridge,
            state = state,
            backdrop = backdrop,
            onFinished = {
                val completed = manualBridgeInteraction
                manualBridgeInteraction = null
                if (completed is EduBridgeInteractionRequest.Alert &&
                    completed.title.contains("失败")) {
                    error = completed.message
                    aiParsing = false
                    manualToolRequest = null
                    releaseManualToolWebView()
                }
            }
        )
    }
    if (showAiTokenRepairPrompt) {
        LiquidAlertDialog(
            title = "口令格式不完整",
            message = "本地校验无法识别这段口令。是否使用当前配置的 ${aiSettings.profile.displayName} 整理课程信息后再次导入？",
            actions = listOf(
                LiquidAlertAction(
                    "取消",
                    LiquidAlertActionStyle.Secondary,
                    onClick = { showAiTokenRepairPrompt = false }
                ),
                LiquidAlertAction(
                    "交给 AI 整理",
                    LiquidAlertActionStyle.Primary,
                    onClick = { repairTokenWithAi() }
                )
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showAiTokenRepairPrompt = false }
        )
    }
}

@Composable
private fun AiManualImportDialogContent(
    state: AppState,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
    aiSettings: AiImportSettings,
    onRefreshSettings: () -> Unit,
    jsonText: String,
    onJsonTextChange: (String) -> Unit,
    onCopyPrompt: () -> Unit,
    onCleanText: () -> Unit,
    selectedFileName: String?,
    routeMessage: String?,
    error: String?,
    aiParsing: Boolean,
    historyEntries: List<AiImportHistoryEntry>,
    historySourceHidden: Boolean,
    onOpenHistory: (Rect) -> Unit,
    onOpenHistoryEntry: (AiImportHistoryEntry, Rect) -> Unit,
    onPrimaryAction: () -> Unit
) {
    // This destination is rendered directly on the Home glass/backdrop. Its complete foreground
    // domain must therefore follow that glass, independently from the application day/night mode.
    val textColor = glassForegroundColor(state.config)
    val dialogLightStyle = glassUsesLightStyle(state.config)
    val windowSize = currentWindowSizeDp()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeHeight = (
        windowSize.height -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            32.dp
        ).coerceAtLeast(280.dp)
    // All three modes share one stable middle-sized shell. Changing import mode now swaps only
    // the inner content; the platform dialog and its expensive backdrop are never remeasured.
    val panelHeight = minOf(safeHeight, 500.dp)
    val mode = selectedMode
    CompositionLocalProvider(LocalContentColor provides textColor) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        LiquidDialogHeader(
            title = "手动导入课表",
            onDismiss = onCancel,
            backdrop = backdrop,
            config = state.config,
            buttonBlurRadius = 4.dp,
            buttonLightStyleOverride = dialogLightStyle,
            onConfirm = onPrimaryAction
        )
        if (mode == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ComposeColor.Black.copy(alpha = if (dialogLightStyle) 0.035f else 0.18f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ICS 本地导入", color = textColor, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "无需 API Key，不会调用 AI",
                        color = textColor.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ComposeColor.Black.copy(alpha = if (dialogLightStyle) 0.035f else 0.18f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前 AI", color = textColor.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${aiSettings.profile.displayName} / ${aiSettings.profile.defaultModel}",
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DialogLiquidButton(
                    backdrop,
                    "刷新",
                    onRefreshSettings,
                    monochromeNeutral = true,
                    lightStyleOverride = dialogLightStyle
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            LiquidOptionTabs(
                selectedIndex = selectedMode,
                labels = listOf("粘贴口令", "导入 ICS", "文件上传"),
                backdrop = backdrop,
                config = state.config,
                width = maxWidth,
                highContrast = true,
                followAppTheme = false,
                onSelected = onModeSelected
            )
        }
        if (mode == 0) {
            LiquidDialogBody {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogLiquidButton(
                            backdrop,
                            "复制提示词",
                            onCopyPrompt,
                            modifier = Modifier.weight(1f),
                            monochromeNeutral = true,
                            lightStyleOverride = dialogLightStyle
                        )
                        DialogLiquidButton(
                            backdrop,
                            "清理格式",
                            onCleanText,
                            modifier = Modifier.weight(1f),
                            monochromeNeutral = true,
                            lightStyleOverride = dialogLightStyle
                        )
                    }
                    DialogCapsuleField(
                        value = jsonText,
                        onValueChange = onJsonTextChange,
                        placeholder = "粘贴 SleepDown / WakeUp / 星链口令或 AI 返回内容",
                        config = state.config,
                        minLines = 5,
                        cornerRadius = 16.dp,
                        fieldTextColor = textColor,
                        fieldLightStyleOverride = dialogLightStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            LiquidDialogBody {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                if (mode == 1) {
                                    "选择标准 .ics 日历文件后，应用会在本机识别课程时间、重复规则和时区，并直接进入导入预览。"
                                } else {
                                    "支持 PDF、图片、XLSX、CSV、DOCX、PPTX、ODS、TXT、Markdown、JSON、XML 和 HTML。"
                                },
                                color = textColor.copy(alpha = 0.86f),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 21.sp
                            )
                            selectedFileName?.let { Text("已选择：$it", color = textColor) }
                            routeMessage?.let {
                                Text(it, color = textColor.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Text(
                            "最近导入",
                            modifier = Modifier.padding(top = 8.dp),
                            color = textColor.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (historyEntries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "暂无导入记录",
                                    color = textColor.copy(alpha = 0.50f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        items(historyEntries.take(3), key = { it.id }) { entry ->
                            var entryBounds by remember(entry.id) { mutableStateOf(Rect.Zero) }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(textColor.copy(alpha = 0.055f))
                                    .onGloballyPositioned { entryBounds = it.boundsInWindow() }
                                    .clickable(
                                        enabled = true,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (entryBounds.width > 1f) onOpenHistoryEntry(entry, entryBounds)
                                    }
                            ) {
                                AiImportHistoryRowContent(
                                    entry = entry,
                                    modifier = Modifier.fillMaxWidth(),
                                    textColor = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
        error?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        LiquidDialogFooter {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                var historyButtonBounds by remember { mutableStateOf(Rect.Zero) }
                if (historySourceHidden) {
                    Spacer(Modifier.weight(1f).height(40.dp))
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .onGloballyPositioned { historyButtonBounds = it.boundsInWindow() }
                    ) {
                        DialogLiquidButton(
                            backdrop = backdrop,
                            label = "导入历史",
                            role = DialogButtonRole.Cancel,
                            iconRes = R.drawable.ic_history,
                            modifier = Modifier.fillMaxWidth(),
                            destructiveFilled = true,
                            onClick = {
                                if (historyButtonBounds.width > 1f) onOpenHistory(historyButtonBounds)
                            }
                        )
                    }
                }
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = when {
                        aiParsing -> "解析中..."
                        mode == 0 -> "解析并预览"
                        mode == 1 -> "选择 ICS 文件"
                        else -> "上传文件"
                    },
                    role = DialogButtonRole.Confirm,
                    iconRes = R.drawable.ic_download,
                    modifier = Modifier.weight(1f),
                    onClick = onPrimaryAction
                )
            }
        }
    }
    }
}

@Composable
fun DonateSettingsScreen(
	state: AppState,
	backdrop: Backdrop?
) {
	val scope = rememberCoroutineScope()
	val remoteConfigState by SleepDownRemoteConfig.state.collectAsStateWithLifecycle()
	val donationSection = remoteConfigState.bootstrap?.donations
	LaunchedEffect(Unit) {
		SleepDownRemoteConfig.refresh(scope, force = true)
	}
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
                painter = painterResource(R.drawable.donate_reward),
                contentDescription = "微信赞赏码",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
		if (donationSection?.published == true) {
			item(key = "donation-thanks-inline") {
				DonationThanksPanel(
					state = state,
					backdrop = backdrop,
					section = donationSection
				)
			}
		}
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("使用方式", "打开微信扫一扫，识别上方赞赏码即可。谢谢你愿意支持这个小小的课程表继续变好。")
            }
        }
    }
}

@Composable
fun EduSchoolPickerScreen(
    state: AppState,
    backdrop: Backdrop? = null,
    warehouseGeneration: Int = 0,
    onSelect: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    val adapters = remember(warehouseGeneration) {
        runCatching { ShiguangWarehouse.loadAdapters(context) }
            .getOrDefault(emptyList())
            .filterNot(EduAdapter::isGeneralEduTool)
    }
    var adapterChoices by remember { mutableStateOf<List<EduAdapter>?>(null) }
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
                    it.adapterName.contains(keyword, ignoreCase = true) ||
                    it.description.contains(keyword, ignoreCase = true) ||
                    it.maintainer.contains(keyword, ignoreCase = true)
        }
    }
    EduSchoolIndexedSelectScreen(
        state = state,
        backdrop = backdrop,
        adapters = filtered,
        query = query,
        onQueryChange = { query = it },
        onSelect = { school ->
            val matches = adapters.filter { it.school.id == school.id }
            if (matches.size == 1) {
                onSelect(matches.single())
            } else if (matches.isNotEmpty()) {
                adapterChoices = matches
            }
        }
    )
    adapterChoices?.let { choices ->
        var selectedAdapterId by remember(choices) { mutableStateOf<String?>(null) }
        val selectedAdapter = choices.firstOrNull { it.adapterId == selectedAdapterId }
        LiquidAlertDialog(
            title = choices.first().school.name,
            message = "",
            actions = listOf(
                LiquidAlertAction(
                    label = "取消",
                    style = LiquidAlertActionStyle.Secondary,
                    onClick = { adapterChoices = null }
                ),
                LiquidAlertAction(
                    label = "确认",
                    style = LiquidAlertActionStyle.Primary,
                    enabled = selectedAdapter != null,
                    onClick = {
                        selectedAdapter?.let { adapter ->
                            adapterChoices = null
                            onSelect(adapter)
                        }
                    }
                )
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { adapterChoices = null },
            messageContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.forEach { adapter ->
                        EduAdapterChoiceCard(
                            adapter = adapter,
                            selected = adapter.adapterId == selectedAdapterId,
                            config = state.config,
                            onClick = { selectedAdapterId = adapter.adapterId }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun EduAdapterChoiceCard(
    adapter: EduAdapter,
    selected: Boolean,
    config: ScheduleConfigEntity,
    onClick: () -> Unit
) {
    val foreground = sleepDownPanelForegroundColor(config)
    val dark = appUsesDarkTheme(config)
    val accent = ComposeColor(0xFF0A84FF)
    val details = buildList {
        add(eduAdapterCategoryLabel(adapter.category))
        adapter.maintainer.takeIf(String::isNotBlank)?.let { add(it) }
        adapter.description.takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString(" · ")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        shape = RoundedRectangle(
            cornerRadius = SleepDownDesignTokens.CenteredDialog.Corner -
                SleepDownDesignTokens.CenteredDialog.ContentPadding -
                SleepDownDesignTokens.CenteredDialog.AlertTextHorizontalInset,
            style = RoundedCornerStyle.Continuous
        ),
        color = if (selected) {
            accent.copy(alpha = if (dark) 0.24f else 0.13f)
        } else {
            foreground.copy(alpha = if (dark) 0.09f else 0.055f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent.copy(alpha = 0.88f) else foreground.copy(alpha = 0.14f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adapter.adapterName,
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = foreground.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(Capsule())
                    .background(
                        if (selected) accent else foreground.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(Capsule())
                            .background(ComposeColor.White)
                    )
                }
            }
        }
    }
}

private data class EduSchoolAdapterGroup(
    val school: EduSchool,
    val adapters: List<EduAdapter>
)

@Composable
fun EduSchoolIndexedSelectScreen(
    state: AppState,
    backdrop: Backdrop?,
    adapters: List<EduAdapter>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (EduSchool) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val topPadding = detailContentTopPadding()
    val schoolGroups = remember(adapters) {
        adapters
            .groupBy { it.school.id }
            .values
            .map { schoolAdapters ->
                EduSchoolAdapterGroup(
                    school = schoolAdapters.first().school,
                    adapters = schoolAdapters.sortedBy(EduAdapter::adapterName)
                )
            }
    }
    val aiEduSchools = remember(schoolGroups) {
        schoolGroups.filter { group -> group.adapters.any(EduAdapter::isAiEduImportTool) }
    }
    val pinnedSchools = remember(schoolGroups) {
        schoolGroups.filter { group ->
            group !in aiEduSchools && group.adapters.any {
                it.isGeneralEduTool()
            }
        }
            .sortedBy { it.school.id }
    }
    val indexedSchools = remember(schoolGroups) {
        schoolGroups.filterNot { it in aiEduSchools || it in pinnedSchools }
    }
    val grouped = remember(indexedSchools) {
        indexedSchools
            .groupBy { it.school.initial.ifBlank { "#" }.uppercase() }
            .toSortedMap()
    }
    val letters = remember(grouped) { grouped.keys.toList() }
    val sectionPositions = remember(grouped, aiEduSchools, pinnedSchools) {
        val aiSectionSize = if (aiEduSchools.isEmpty()) 0 else 2
        val pinnedSectionSize = if (pinnedSchools.isEmpty()) 0 else 2
        var index = aiSectionSize + pinnedSectionSize
        buildMap {
            grouped.forEach { (letter, _) ->
                put(letter, index)
                index += 2
            }
        }
    }
    val listBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "edu-school-list-content"
    )
    // Floating controls are siblings after the producer. Combining the activity underlay with the
    // list producer lets them sample the title, category labels and cards without sampling either
    // themselves or the keyboard-driven overlay host.
    val overlayBackdrop = if (backdrop != null) {
        rememberGlassCombinedBackdrop(backdrop, listBackdrop)
    } else {
        listBackdrop
    }
    val density = LocalDensity.current
    val imeLift = with(density) {
        (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density))
            .coerceAtLeast(0)
            .toDp()
    }
    val floatingOverlayHost = LocalDetailActivityFloatingOverlayHost.current
    val currentSearchQuery = rememberUpdatedState(query)
    val currentSearchOnChange = rememberUpdatedState(onQueryChange)
    val currentSearchBackdrop = rememberUpdatedState(overlayBackdrop)
    val currentSearchConfig = rememberUpdatedState(state.config)
    val currentSearchImeLift = rememberUpdatedState(imeLift)
    val currentRailListState = rememberUpdatedState(listState)
    val currentRailLetters = rememberUpdatedState(letters)
    val currentRailPositions = rememberUpdatedState(sectionPositions)
    val currentRailTopPadding = rememberUpdatedState(topPadding)
    val currentRailScope = rememberUpdatedState(scope)
    val currentRailHaptic = rememberUpdatedState(haptic)
    val floatingSearchDock: (@Composable () -> Unit)? = if (floatingOverlayHost != null) {
        remember(floatingOverlayHost) {
            @Composable {
                EduSchoolSearchDock(
                    value = currentSearchQuery.value,
                    onValueChange = currentSearchOnChange.value,
                    backdrop = currentSearchBackdrop.value,
                    config = currentSearchConfig.value,
                    imeLift = currentSearchImeLift.value
                )
                // The alphabet rail floats at the same root level as the top bar, as a sibling of
                // the Miuix Scaffold instead of a page child. Its gradient blur therefore keeps a
                // full-window envelope and can no longer be cropped by the card/list subtrees.
                EduAlphabetRailDock(
                    listState = currentRailListState.value,
                    letters = currentRailLetters.value,
                    sectionPositions = currentRailPositions.value,
                    backdrop = currentSearchBackdrop.value,
                    config = currentSearchConfig.value,
                    topPadding = currentRailTopPadding.value,
                    imeLift = currentSearchImeLift.value,
                    scope = currentRailScope.value,
                    haptic = currentRailHaptic.value
                )
            }
        }
    } else {
        null
    }
    DisposableEffect(floatingOverlayHost, floatingSearchDock) {
        val host = floatingOverlayHost
        if (host != null && floatingSearchDock != null) {
            host.content = floatingSearchDock
        }
        onDispose {
            if (host != null && host.content === floatingSearchDock) {
                host.content = null
            }
        }
    }
    val rootOverlayMounted = floatingOverlayHost != null &&
        floatingOverlayHost.content === floatingSearchDock
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentImeLift = rememberUpdatedState(imeLift)
    val keyboardDismissRequested = remember { mutableStateOf(false) }
    LaunchedEffect(imeLift) {
        if (imeLift <= 1.dp) {
            keyboardDismissRequested.value = false
        }
    }
    val keyboardDismissScrollConnection = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            private fun dismissKeyboard() {
                if (!keyboardDismissRequested.value) {
                    keyboardDismissRequested.value = true
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    currentImeLift.value > 1.dp &&
                    source == NestedScrollSource.UserInput &&
                    available.y != 0f
                ) {
                    // The first drag belongs to dismissing the IME. Consume it so the list does
                    // not visibly scroll underneath the keyboard during the dismissal frame.
                    dismissKeyboard()
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentImeLift.value > 1.dp) {
                    dismissKeyboard()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    // The OS IME inset is the single source of truth for the search dock's vertical motion. The
    // field must rise with the keyboard itself; the split action below derives from that same
    // inset instead of starting a second focus-driven opening animation.
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropProducer(listBackdrop)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.nestedScroll(keyboardDismissScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding + 12.dp,
                    bottom = DockScrollPadding
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (adapters.isEmpty()) {
                    item { Text("没有找到学校适配资源", color = MaterialTheme.colorScheme.error) }
                } else {
                    if (aiEduSchools.isNotEmpty()) {
                        item(key = "ai-edu-title") { GlassPreferenceCategory("AI教务导入") }
                        item(key = "ai-edu-card") {
                            EduSchoolGroupCard(
                                schools = aiEduSchools,
                                state = state,
                                backdrop = backdrop,
                                onSelect = onSelect
                            )
                        }
                    }
                    if (pinnedSchools.isNotEmpty()) {
                        item(key = "general-edu-title") { GlassPreferenceCategory("导入工具") }
                        item(key = "general-edu-card") {
                            EduSchoolGroupCard(
                                schools = pinnedSchools,
                                state = state,
                                backdrop = backdrop,
                                onSelect = onSelect
                            )
                        }
                    }
                    grouped.forEach { (letter, list) ->
                        item(key = "section-$letter") { GlassPreferenceCategory(letter) }
                        item(key = "section-card-$letter") {
                            EduSchoolGroupCard(
                                schools = list,
                                state = state,
                                backdrop = backdrop,
                                onSelect = onSelect
                            )
                        }
                    }
                }
            }
        }
        // In the detail scaffold this dock is mounted by the root-level sibling host below the
        // Miuix Scaffold. The fallback keeps previews/other callers usable before that host exists.
        if (!rootOverlayMounted) {
            EduSchoolSearchDock(
                value = query,
                onValueChange = onQueryChange,
                backdrop = overlayBackdrop,
                config = state.config,
                imeLift = imeLift
            )
            EduAlphabetRailDock(
                listState = listState,
                letters = letters,
                sectionPositions = sectionPositions,
                backdrop = overlayBackdrop,
                config = state.config,
                topPadding = topPadding,
                imeLift = imeLift,
                scope = scope,
                haptic = haptic
            )
        }
    }
}


@Composable
private fun EduAlphabetRailDock(
    listState: LazyListState,
    letters: List<String>,
    sectionPositions: Map<String, Int>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    topPadding: Dp,
    imeLift: Dp,
    scope: CoroutineScope,
    haptic: HapticFeedback
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current
    val navigationBarBottom = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    // Center the rail only in the usable list window: the measured top bar and the entire search
    // dock (including its safe-area/IME lift) are excluded from the centering bounds.
    val alphabetRailBottomExclusion = 44.dp + 18.dp + navigationBarBottom + imeLift
    var railDragging by remember { mutableStateOf(false) }
    var railPointerIndex by remember { mutableIntStateOf(-1) }
    val railScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || railDragging }
    }
    // Keep the rail visible briefly after the list settles so it does not pop away the moment
    // the finger leaves the screen.
    var showAlphabetRail by remember { mutableStateOf(false) }
    var alphabetRailExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(railScrolling) {
        if (railScrolling) {
            showAlphabetRail = true
            alphabetRailExpanded = true
        } else {
            delay(760)
            // Let the full glass shell perform its fade/motion-blur exit first. Collapse the
            // width only after the visibility transition has left the composition, otherwise the
            // blur would be applied to a 10dp sliver and the rail would look lopsided.
            showAlphabetRail = false
            delay(240)
            alphabetRailExpanded = false
        }
    }
    val visibleSectionIndex by remember(letters, sectionPositions) {
        derivedStateOf {
            if (!listState.canScrollForward) {
                letters.lastIndex.coerceAtLeast(0)
            } else {
                val firstVisible = listState.firstVisibleItemIndex
                letters.indexOfLast { letter ->
                    (sectionPositions[letter] ?: Int.MAX_VALUE) <= firstVisible
                }.coerceAtLeast(0)
            }
        }
    }
    val activeAlphabetIndex = railPointerIndex.takeIf { it >= 0 } ?: visibleSectionIndex
    val alphabetRailWidth by animateDpAsState(
        targetValue = if (alphabetRailExpanded) 58.dp else 10.dp,
        animationSpec = tween(180),
        label = "edu-alphabet-width"
    )
    val alphabetContentAlpha by animateFloatAsState(
        targetValue = if (alphabetRailExpanded) 1f else 0f,
        animationSpec = tween(130),
        label = "edu-alphabet-content"
    )
    val alphabetIndicatorProgress by animateFloatAsState(
        targetValue = activeAlphabetIndex
            .coerceIn(0, letters.lastIndex.coerceAtLeast(0))
            .toFloat(),
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 560f,
            visibilityThreshold = 0.001f
        ),
        label = "edu-alphabet-indicator"
    )
    // Root-level dock: the whole window is the layout envelope, so the gradient blur can fade
    // out toward the page without being cropped by any card/list/offscreen boundary.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topPadding,
                    bottom = alphabetRailBottomExclusion
                )
                .clipToBounds()
        ) {
            AnimatedVisibility(
                visible = showAlphabetRail,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 0.dp),
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(240))
            ) {
                val railMotionProgress by transition.animateFloat(
                    transitionSpec = { tween(240) },
                    label = "edu-alphabet-motion"
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
                val railTint = if (appUsesDarkTheme(config)) {
                    ComposeColor(0xFF111318)
                } else {
                    ComposeColor.White
                }
                Box(
                    modifier = Modifier
                        .width(96.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // The veil owns a fixed 2D envelope. It is measured from the alphabet content
                    // plus top/bottom fade space, but never participates in touch handling or the
                    // rail's 10dp -> 58dp content-width animation.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                        .progressiveBackdropBlur(
                            backdrop = backdrop,
                            tintColor = railTint,
                            blurRadius = 8.dp,
                            tintIntensity = if (appUsesDarkTheme(config)) 0.08f else 0.18f,
                            domain = GlassBackdropDomain.ChromeCombined,
                            direction = ProgressiveBlurDirection.RailThreeWay,
                            topMaskFadeStart = 0.04f,
                            topMaskFadeEnd = 0.96f,
                            topTintFadeStart = 0.12f,
                            topTintFadeEnd = 0.98f,
                            fallbackTintStops = listOf(
                                0f to railTint.copy(alpha = 0.40f),
                                0.58f to railTint.copy(alpha = 0.08f),
                                1f to ComposeColor.Transparent
                            )
                        )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .pointerInput(letters, sectionPositions, haptic) {
                                    awaitPointerEventScope {
                                        var lastIndex = -1
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.firstOrNull { it.pressed }
                                            if (pressed == null) {
                                                railDragging = false
                                                railPointerIndex = -1
                                                lastIndex = -1
                                                continue
                                            }
                                            railDragging = true
                                            val itemHeight = size.height / letters.size.toFloat()
                                            val index = (pressed.position.y / itemHeight).toInt()
                                                .coerceIn(0, letters.lastIndex)
                                            if (index == lastIndex) continue
                                            lastIndex = index
                                            railPointerIndex = index
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            sectionPositions[letters[index]]?.let { position ->
                                                scope.launch { listState.scrollToItem(position) }
                                            }
                                        }
                                    }
                                }
                                .width(alphabetRailWidth)
                                .then(
                                    if (railMotionProgress > 0.001f && railMotionProgress < 0.999f) {
                                        Modifier.graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                            renderEffect = platformBlurRenderEffect(
                                                detailMotionBlurRadiusDp(railMotionProgress) * density.density
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Column(
                                modifier = Modifier
                                    .graphicsLayer { alpha = alphabetContentAlpha }
                                    .padding(horizontal = 5.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                letters.forEachIndexed { index, letter ->
                                    val highlight = (1f - abs(
                                        index.toFloat() - alphabetIndicatorProgress
                                    )).coerceIn(0f, 1f)
                                    Text(
                                        letter,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(50))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                sectionPositions[letter]?.let { position ->
                                                    scope.launch { listState.animateScrollToItem(position) }
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = lerp(
                                            sleepDownPanelForegroundColor(config).copy(
                                                alpha = if (appUsesDarkTheme(config)) 0.52f else 0.68f
                                            ),
                                            ComposeColor(0xFF0A84FF),
                                            highlight
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
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

@Composable
private fun EduSchoolGroupCard(
    schools: List<EduSchoolAdapterGroup>,
    state: AppState,
    backdrop: Backdrop?,
    onSelect: (EduSchool) -> Unit
) {
    SettingsGroup(
        backdrop = backdrop,
        config = state.config,
        modifier = Modifier.fillMaxWidth()
    ) {
        schools.forEach { group ->
            SettingsNavigationRow(
                title = group.school.name,
                subtitle = group.adapters.joinToString(" / ") { it.adapterName },
                onClick = { onSelect(group.school) }
            )
        }
    }
}

private fun eduAdapterCategoryLabel(category: String): String = when (category.uppercase()) {
    "BACHELOR_AND_ASSOCIATE" -> "本科 / 专科教务"
    "POSTGRADUATE" -> "研究生教务"
    "GENERAL_TOOL" -> "通用工具"
    else -> category.ifBlank { "未注明" }
}

private val EduSearchVerticalOverscan = 12.dp

@Composable
private fun EduSchoolSearchDock(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    imeLift: Dp
) {
    val density = LocalDensity.current
    val entranceProgress = remember { Animatable(0f) }
    val searchDockBottomPadding = 18.dp
    val navigationBarBottom = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    val searchDockTint = if (appUsesDarkTheme(config)) {
        ComposeColor(0xFF1A1A1D)
    } else {
        ComposeColor.White
    }
    val searchDockGradientHeight = 168.dp + imeLift

    LaunchedEffect(Unit) {
        entranceProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 260f,
                visibilityThreshold = 0.001f
            )
        )
    }

    // This is a root-level floating dock. The scrim is a separate sibling underneath the glass
    // controls, so it stays present when the field splits into the action capsule.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(searchDockGradientHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            searchDockTint.copy(alpha = 0f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.42f else 0.36f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.76f else 0.70f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.94f else 0.92f)
                        )
                    )
                )
        )
        SchoolSearchField(
            value = value,
            onValueChange = onValueChange,
            backdrop = backdrop,
            config = config,
            keyboardLift = imeLift,
            bottomOffset = searchDockBottomPadding + navigationBarBottom + imeLift,
            entranceProgress = entranceProgress.value,
            modifier = Modifier
                // Give the control the whole window as its layout envelope. Only its two visible
                // capsules are positioned near the bottom; no 44/60dp parent can crop the Kyant
                // shadow, refraction or press expansion anymore.
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .zIndex(1f)
        )
    }
}

@Composable
fun SchoolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    keyboardLift: Dp = 0.dp,
    bottomOffset: Dp = 0.dp,
    entranceProgress: Float = 1f,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val foreground = sleepDownPanelForegroundColor(config)
    val lightSearchGlass = !appUsesDarkTheme(config)
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // The IME inset remains the only trigger. Keep the eased transition fast enough to follow the
    // keyboard, while still letting the cancel/search capsule finish separating after the lift.
    val keyboardSplitTarget = keyboardLift > 20.dp
    val splitTransition = updateTransition(
        targetState = keyboardSplitTarget,
        label = "edu-search-split"
    )
    val splitProgress by splitTransition.animateFloat(
        transitionSpec = {
                if (targetState) {
                    tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.20f, 0.82f, 0.18f, 1.0f)
                )
            } else {
                tween(
                    durationMillis = 300,
                    easing = CubicBezierEasing(0.32f, 0.0f, 0.18f, 1.0f)
                )
            }
        },
        label = "edu-search-split-progress"
    ) { opened -> if (opened) 1f else 0f }
    val splitMotionBlurModifier = if (splitProgress > 0.001f && splitProgress < 0.999f) {
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = platformMotionBlurRenderEffect(
                detailMotionBlurRadiusDp(splitProgress) * density.density
            )
        }
    } else {
        // A zero-radius RenderEffect still keeps an offscreen texture alive on some devices and
        // exposes its rectangular allocation boundary. Stable open/closed states stay layer-free.
        Modifier
    }
    val searchBlurRadius = 5.dp
    val searchLensHeight = 14.dp
    val searchLensAmount = 24.dp
    val searchButtonHeight = 44.dp
    val searchSurfaceColor = if (appUsesDarkTheme(config)) {
        // 深色模式：搜索胶囊采用黑色玻璃基底。
        ComposeColor(0xFF16181D).copy(alpha = 0.78f)
    } else {
        ComposeColor.White.copy(alpha = 0.22f)
    }
    val closeSearch: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    BackHandler(enabled = focused) { closeSearch() }
    val field: @Composable () -> Unit = {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = foreground),
            cursorBrush = SolidColor(ComposeColor(0xFF9E9E9E)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { closeSearch() }),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            "搜索学校或导入工具…",
                            color = foreground.copy(alpha = 0.50f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        val horizontalInset = 16.dp
        val pressExpansion = 1.5.dp
        val pressTranslationAllowance = 2.5.dp
        val maxPressOverscan = (horizontalInset - 1.dp).coerceAtLeast(0.dp)
        val showAction = focused || splitProgress > 0.001f
        val actionWidth = 82.dp * splitProgress
        val actionGap = 8.dp * splitProgress
        val visualDockWidth = (maxWidth - horizontalInset * 2f).coerceAtLeast(120.dp)
        val fieldWidth = (visualDockWidth - actionWidth - actionGap).coerceAtLeast(120.dp)
        // LiquidButton's press value is a vertical expansion, so a wide capsule grows farther
        // horizontally than 1.5dp. Reserve that real scale range plus the pointer translation.
        val pressScaleFactor = pressExpansion.value / 44f
        val fieldPressOverscan = (
            fieldWidth * pressScaleFactor + pressTranslationAllowance
        ).coerceAtMost(maxPressOverscan)
        val actionPressOverscan = (
            actionWidth * pressScaleFactor + pressTranslationAllowance
        ).coerceAtMost(maxPressOverscan)
        val fieldSlotWidth = fieldWidth + fieldPressOverscan * 2f
        val actionSlotWidth = if (showAction) {
            actionWidth + actionPressOverscan * 2f
        } else {
            0.dp
        }
        val renderEnvelopeHeight = searchButtonHeight + EduSearchVerticalOverscan * 2f
        val entranceTranslation = 52.dp * (1f - entranceProgress.coerceIn(0f, 1f))
        // The visible 44dp capsule keeps the established bottom position. The additional envelope
        // extends equally above and below it without participating in page/card measurement.
        val renderEnvelopeBottomOffset =
            (bottomOffset - EduSearchVerticalOverscan).coerceAtLeast(0.dp)
        val actionLabel = if (value.isBlank()) "取消" else "搜索"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(fieldSlotWidth)
                    .height(renderEnvelopeHeight)
                    .offset(
                        x = horizontalInset - fieldPressOverscan,
                        y = -renderEnvelopeBottomOffset + entranceTranslation
                    )
                    .graphicsLayer {
                        alpha = entranceProgress.coerceIn(0f, 1f)
                        clip = false
                    }
                    // RenderEffect necessarily allocates an offscreen texture. Applying it to the
                    // oversized slot instead of the 44dp LiquidButton keeps the real glass shadow,
                    // refraction and press expansion inside that texture rather than clipping them.
                    .then(splitMotionBlurModifier),
                contentAlignment = Alignment.Center
            ) {
                if (backdrop != null) {
                    LiquidButton(
                        onClick = {},
                        backdrop = backdrop,
                        modifier = Modifier
                            .width(fieldWidth),
                        height = searchButtonHeight,
                        contentPadding = PaddingValues(horizontal = 15.dp),
                        blurRadius = searchBlurRadius,
                        lensHeight = searchLensHeight,
                        lensAmount = searchLensAmount,
                        chromaticAberration = false,
                        surfaceColor = searchSurfaceColor,
                        // Keep the real glass blur/lens chain, but do not add a dark perimeter
                        // shadow around the capsule. The sibling scrim below supplies the dock tint.
                        shadowEnabled = lightSearchGlass,
                        highlightEnabled = true,
                        clickTargetEnabled = false,
                        // The button's own Kyant layer must also allow the press transform to
                        // extend beyond its measured capsule; an outer unclipped host alone is
                        // not enough because drawBackdrop otherwise clips at the button bounds.
                        clipToBounds = false,
                        pressExpansion = pressExpansion
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            field()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(fieldWidth)
                            .height(searchButtonHeight)
                            .clip(Capsule())
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        field()
                    }
                }
            }
            if (showAction) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(actionSlotWidth)
                        .height(renderEnvelopeHeight)
                        .offset(
                            x = horizontalInset + fieldWidth + actionGap - actionPressOverscan,
                            y = -renderEnvelopeBottomOffset + entranceTranslation
                        )
                        .graphicsLayer {
                            alpha = entranceProgress.coerceIn(0f, 1f)
                            clip = false
                        }
                        .then(splitMotionBlurModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = closeSearch,
                            backdrop = backdrop,
                            modifier = Modifier
                                .width(actionWidth)
                                .graphicsLayer { alpha = splitProgress },
                            height = searchButtonHeight,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            blurRadius = searchBlurRadius,
                            lensHeight = searchLensHeight,
                            lensAmount = searchLensAmount,
                            chromaticAberration = false,
                            surfaceColor = searchSurfaceColor,
                            shadowEnabled = lightSearchGlass,
                            highlightEnabled = true,
                            clipToBounds = false,
                            pressExpansion = pressExpansion
                        ) {
                            Text(
                                actionLabel,
                                color = foreground,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(actionWidth)
                                .height(searchButtonHeight)
                                .graphicsLayer { alpha = splitProgress }
                                .clip(Capsule())
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = closeSearch),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(actionLabel, color = foreground, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun ShiguangBridgeHost.resolveEduBridgeInteraction(requestId: String, valueExpression: String) {
    evaluateJavascript(
        "window._shiguangNativeCallback && window._shiguangNativeCallback(" +
            JSONObject.quote(requestId) + ", true, " + valueExpression + ");"
    )
}

private fun decodeEduBridgeValidationResult(encoded: String?): String? {
    if (encoded.isNullOrBlank() || encoded == "null") return null
    return runCatching {
        val value = JSONArray("[$encoded]").opt(0)
        when (value) {
            null, JSONObject.NULL -> null
            else -> value.toString().takeIf { it.isNotBlank() }
        }
    }.getOrElse { "输入校验失败，请重试" }
}

private fun validateEduBridgePrompt(
    bridge: ShiguangBridgeHost,
    request: EduBridgeInteractionRequest.Prompt,
    value: String,
    onResult: (String?) -> Unit
) {
    val validator = request.validator
    if (validator.isNullOrBlank()) {
        onResult(null)
        return
    }
    val script = """
        (function () {
            try {
                var result = ${validator}(${JSONObject.quote(value)});
                if (result === false || result == null || result === "") return null;
                return String(result);
            } catch (error) {
                return error && error.message ? error.message : String(error);
            }
        })();
    """.trimIndent()
    if (!bridge.evaluateJavascript(script) { onResult(decodeEduBridgeValidationResult(it)) }) {
        onResult("网页已关闭，请返回后重试")
    }
}

@Composable
private fun EduBridgeInteractionDialog(
    request: EduBridgeInteractionRequest?,
    bridge: ShiguangBridgeHost,
    state: AppState,
    backdrop: Backdrop?,
    onFinished: () -> Unit
) {
    when (request) {
        null -> Unit
        is EduBridgeInteractionRequest.Alert -> {
            LiquidAlertDialog(
                title = request.title,
                message = request.message,
                actions = listOf(
                    LiquidAlertAction(
                        label = request.confirmText,
                        style = LiquidAlertActionStyle.Primary
                    ) {
                        bridge.resolveEduBridgeInteraction(request.requestId, "true")
                        onFinished()
                    }
                ),
                backdrop = backdrop,
                config = state.config,
                onDismissRequest = {
                    bridge.resolveEduBridgeInteraction(request.requestId, "false")
                    onFinished()
                }
            )
        }
        is EduBridgeInteractionRequest.Prompt -> {
            var value by remember(request.requestId) { mutableStateOf(request.defaultValue) }
            var validationError by remember(request.requestId) { mutableStateOf<String?>(null) }
            fun cancel() {
                bridge.resolveEduBridgeInteraction(request.requestId, "null")
                onFinished()
            }
            fun submit() {
                validateEduBridgePrompt(bridge, request, value) { error ->
                    validationError = error
                    if (error == null) {
                        bridge.resolveEduBridgeInteraction(request.requestId, JSONObject.quote(value))
                        onFinished()
                    }
                }
            }
            Dialog(
                onDismissRequest = ::cancel,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                CenterLiquidDialog(
                    backdrop = backdrop,
                    config = state.config,
                    size = LiquidDialogSize.Compact
                ) {
                    LiquidDialogHeader(
                        title = request.title,
                        onDismiss = ::cancel,
                        backdrop = backdrop,
                        config = state.config,
                        onConfirm = ::submit
                    )
                    if (request.message.isNotBlank()) {
                        Text(
                            request.message,
                            modifier = Modifier.padding(horizontal = 18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.72f)
                        )
                    }
                    DialogCapsuleField(
                        value = value,
                        onValueChange = {
                            value = it
                            validationError = null
                        },
                        placeholder = request.message.ifBlank { "请输入" },
                        config = state.config,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    validationError?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        is EduBridgeInteractionRequest.SingleSelection -> {
            var selectedIndex by remember(request.requestId) {
                mutableIntStateOf(request.defaultIndex)
            }
            fun cancel() {
                bridge.resolveEduBridgeInteraction(request.requestId, "null")
                onFinished()
            }
            fun submit() {
                if (selectedIndex !in request.options.indices) return
                bridge.resolveEduBridgeInteraction(request.requestId, selectedIndex.toString())
                onFinished()
            }
            Dialog(
                onDismissRequest = ::cancel,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                CenterLiquidDialog(
                    backdrop = backdrop,
                    config = state.config,
                    size = LiquidDialogSize.Compact
                ) {
                    LiquidDialogHeader(
                        title = request.title,
                        onDismiss = ::cancel,
                        backdrop = backdrop,
                        config = state.config,
                        onConfirm = if (selectedIndex in request.options.indices) ::submit else null
                    )
                    if (request.options.isEmpty()) {
                        Text(
                            "没有可选项",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                            textAlign = TextAlign.Center,
                            color = LocalContentColor.current.copy(alpha = 0.64f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(request.options) { index, option ->
                                val selected = index == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (selected) 0.14f else 0f
                                            )
                                        )
                                        .clickable { selectedIndex = index }
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        option,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LocalContentColor.current
                                    )
                                    if (selected) {
                                        Text(
                                            "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun EduImportActivityScreen(
    state: AppState,
    adapter: EduAdapter,
    backdrop: Backdrop?,
    webContentBackdrop: LayerBackdrop,
    useDetailTopPadding: Boolean = true,
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var bridgeInteraction by remember { mutableStateOf<EduBridgeInteractionRequest?>(null) }
    var currentUrl by remember(adapter) {
        mutableStateOf(
            if (adapter.requiresManualEduUrl()) "" else adapter.importUrl.ifBlank { "about:blank" }
        )
    }
    var showGeneralUrlDialog by remember(adapter) { mutableStateOf(adapter.requiresManualEduUrl()) }
    val bridge = remember(adapter) {
        ShiguangBridgeHost(
            context = context,
            onDraft = onParsed,
            onMessage = { message = it },
            onInteractionRequest = { bridgeInteraction = it }
        )
    }
    EduBridgeInteractionDialog(
        request = bridgeInteraction,
        bridge = bridge,
        state = state,
        backdrop = backdrop,
        onFinished = { bridgeInteraction = null }
    )
    if (showGeneralUrlDialog) {
        Dialog(onDismissRequest = { showGeneralUrlDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CenterLiquidDialog(backdrop = backdrop, config = state.config) {
                GeneralEduUrlDialog(
                    config = state.config,
                    backdrop = backdrop,
                    adapter = adapter,
                    initialUrl = currentUrl,
                    helperText = if (adapter.isAiEduImportTool()) {
                        "AI教务导入需要先打开学校教务系统网址。登录后进入课表页面，后续可使用 AI 解析当前页面。"
                    } else {
                        "通用教务需要先填写学校教务系统网址，进入后可继续在下方地址栏修改。"
                    },
                    onCancel = { showGeneralUrlDialog = false },
                    onConfirm = {
                        EduLoginHistoryStore.remember(context, adapter, it)
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
        webContentBackdrop = webContentBackdrop,
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
    adapter: EduAdapter,
    initialUrl: String,
    helperText: String = "通用教务需要先填写学校教务系统网址，进入后可继续在下方地址栏修改。",
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    val dialogForeground = sleepDownPanelForegroundColor(config)
    var url by remember(initialUrl) { mutableStateOf(initialUrl.ifBlank { "https://" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember(adapter.adapterId) { mutableStateOf(EduLoginHistoryStore.load(context)) }
    fun submit() {
        val normalized = normalizeEduUrl(url)
        if (normalized.isBlank()) error = "请输入教务系统网址" else onConfirm(normalized)
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiquidDialogHeader("输入教务网址", onCancel, backdrop, config, onConfirm = ::submit)
        DialogCapsuleField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://example.edu.cn",
            config = config,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardType = KeyboardType.Uri
        )
        Text(
            error ?: helperText,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (error == null) dialogForeground.copy(alpha = 0.72f) else MaterialTheme.colorScheme.error,
            lineHeight = 18.sp
        )
        if (history.isNotEmpty()) {
            Text(
                "最近使用",
                modifier = Modifier.padding(start = 18.dp, top = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                color = dialogForeground.copy(alpha = 0.76f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                history.take(4).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(dialogForeground.copy(alpha = 0.08f))
                            .clickable {
                                EduLoginHistoryStore.restoreCookies(entry)
                                url = entry.url
                                onConfirm(entry.url)
                            }
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                color = dialogForeground
                            )
                            Text(
                                entry.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogForeground.copy(alpha = 0.58f)
                            )
                        }
                        IconButton(onClick = {
                            EduLoginHistoryStore.remove(context, entry.id)
                            history = EduLoginHistoryStore.load(context)
                        }) {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_delete),
                                contentDescription = "删除登录记录",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Text(
                "网址与登录 Cookie 使用设备密钥加密，仅保存在本机。",
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = dialogForeground.copy(alpha = 0.52f)
            )
        }
    }
}

private val AiEduPageExtractScript = """
(function () {
  var seen = [];
  function pushUnique(list, value) {
    value = (value || "").replace(/\s+/g, " ").trim();
    if (!value || value.length < 2) return;
    var key = value.slice(0, 500);
    if (seen.indexOf(key) >= 0) return;
    seen.push(key);
    list.push(value);
  }
  function textOf(node) {
    if (!node) return "";
    return (node.innerText || node.textContent || "").replace(/\s+/g, " ").trim();
  }
  function collectShadowText(root, depth) {
    if (!root || depth > 3) return "";
    var parts = [];
    try {
      Array.prototype.slice.call(root.querySelectorAll("*")).slice(0, 500).forEach(function (node) {
        if (node.shadowRoot) {
          pushUnique(parts, textOf(node.shadowRoot));
          var nested = collectShadowText(node.shadowRoot, depth + 1);
          if (nested) pushUnique(parts, nested);
        }
      });
    } catch (e) {
      pushUnique(parts, "Shadow DOM read failed: " + (e && e.message ? e.message : e));
    }
    return parts.join("\n");
  }
  function tableText(table, index) {
    var rows = Array.prototype.slice.call(table.querySelectorAll("tr")).slice(0, 160);
    var body = rows.map(function (row) {
      return Array.prototype.slice.call(row.querySelectorAll("th,td"))
        .map(textOf)
        .filter(Boolean)
        .join(" | ");
    }).filter(Boolean).join("\n");
    return body ? ("表格 " + (index + 1) + "\n" + body) : "";
  }
  var tables = Array.prototype.slice.call(document.querySelectorAll("table"))
    .slice(0, 48)
    .map(tableText)
    .filter(Boolean)
    .join("\n\n");
  var containerSelectors = [
    "[class*='kb']", "[id*='kb']", "[class*='course']", "[id*='course']",
    "[class*='schedule']", "[id*='schedule']", "[class*='timetable']", "[id*='timetable']",
    "[class*='lesson']", "[id*='lesson']", "[class*='calendar']", "[id*='calendar']",
    ".el-table", ".ant-table", ".layui-table", ".ivu-table", "[role='grid']"
  ];
  var containers = [];
  containerSelectors.forEach(function (selector) {
    try {
      Array.prototype.slice.call(document.querySelectorAll(selector)).slice(0, 20).forEach(function (node) {
        pushUnique(containers, selector + "\n" + textOf(node).slice(0, 8000));
      });
    } catch (e) {}
  });
  var formState = [];
  Array.prototype.slice.call(document.querySelectorAll("select,input,textarea,button,[role='button']")).slice(0, 120).forEach(function (node, index) {
    var label = node.getAttribute("aria-label") || node.getAttribute("placeholder") || node.name || node.id || node.className || node.tagName;
    var value = "";
    if (node.tagName === "SELECT") {
      value = Array.prototype.slice.call(node.selectedOptions || []).map(function (option) { return option.text || option.value || ""; }).join(",");
    } else {
      value = node.value || textOf(node);
    }
    pushUnique(formState, (index + 1) + ". " + label + " = " + value);
  });
  var iframeText = [];
  Array.prototype.slice.call(document.querySelectorAll("iframe,frame")).slice(0, 12).forEach(function (frame, index) {
    try {
      var doc = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
      if (doc && doc.body) {
        pushUnique(iframeText, "Frame " + (index + 1) + "\n" + textOf(doc.body).slice(0, 12000));
      } else {
        pushUnique(iframeText, "Frame " + (index + 1) + ": empty or inaccessible");
      }
    } catch (e) {
      pushUnique(iframeText, "Frame " + (index + 1) + ": inaccessible (" + (e && e.message ? e.message : e) + ")");
    }
  });
  var shadowText = collectShadowText(document, 0);
  var bodyText = textOf(document.body).slice(0, 70000);
  return JSON.stringify({
    title: document.title || "",
    url: location.href || "",
    tables: tables,
    containers: containers.join("\n\n"),
    formState: formState.join("\n"),
    iframeText: iframeText.join("\n\n"),
    shadowText: shadowText,
    text: bodyText
  });
})()
""".trimIndent()

private fun decodeAiEduPageSnapshot(encoded: String?): String {
    val decoded = JSONArray("[$encoded]").getString(0)
    val snapshot = JSONObject(decoded)
    val title = snapshot.optString("title")
    val url = snapshot.optString("url")
    val tables = snapshot.optString("tables")
    val containers = snapshot.optString("containers")
    val formState = snapshot.optString("formState")
    val iframeText = snapshot.optString("iframeText")
    val shadowText = snapshot.optString("shadowText")
    val text = snapshot.optString("text")
    return buildString {
        if (title.isNotBlank()) appendLine("页面标题：$title")
        if (url.isNotBlank()) appendLine("页面地址：$url")
        if (tables.isNotBlank()) {
            appendLine("页面表格：")
            appendLine(tables)
        }
        if (containers.isNotBlank()) {
            appendLine("Page schedule-like containers:")
            appendLine(containers)
        }
        if (formState.isNotBlank()) {
            appendLine("Page form state:")
            appendLine(formState)
        }
        if (iframeText.isNotBlank()) {
            appendLine("Page frames:")
            appendLine(iframeText)
        }
        if (shadowText.isNotBlank()) {
            appendLine("Page shadow DOM:")
            appendLine(shadowText)
        }
        if (text.isNotBlank()) {
            appendLine("页面正文：")
            appendLine(text)
        }
    }.trim()
}

private data class EduPageCaptureIssue(
    val step: String,
    val message: String
)

private fun inspectEduPageCapture(pageText: String): EduPageCaptureIssue? {
    val compact = pageText.replace(Regex("\\s+"), "")
    if (compact.length < 80) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：页面文本过少",
            message = "抓不到课表页：当前页面可提取文本太少。请先在内置页面登录并进入具体课表查询结果页。"
        )
    }

    val lower = pageText.lowercase()
    val loginSignals = listOf(
        "登录", "登陆", "密码", "验证码", "统一身份认证", "账号", "学号",
        "login", "password", "captcha", "cas"
    ).count { lower.contains(it.lowercase()) }
    val scheduleSignals = listOf(
        "课表", "课程", "节次", "星期", "周一", "周二", "周三", "周四", "周五", "周六", "周日",
        "教师", "教室", "上课", "校区", "学年", "学期", "教学班", "周数", "周次"
    ).count { pageText.contains(it) }
    val timeSignals = Regex("""\b\d{1,2}:\d{2}\b""").findAll(pageText).take(3).count()

    if (loginSignals >= 2 && scheduleSignals == 0) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：仍在登录或认证页",
            message = "抓不到课表页：当前页面像登录/认证页。请先完成登录，再进入课表页面后重试。"
        )
    }

    if (scheduleSignals < 2 && timeSignals < 2) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：当前页不像课表",
            message = "抓不到课表页：当前页面没有明显课程、节次或时间表信息。这个学校可能需要先点进具体课表查询结果页，或当前拾光适配器抓不到课表页。"
        )
    }

    return null
}

private fun aiEduRequestPreview(settings: AiImportSettings, pageTextLength: Int): String {
    val baseUrl = normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl)
    val useResponses = AiProviderPresets.shouldUseResponses(settings.profile)
    val endpoint = baseUrl.trimEnd('/') + if (useResponses) "/responses" else "/chat/completions"
    val outputMode = if (settings.profile.id == AiProviderPresets.deepSeek.id) {
        StructuredOutputMode.PROMPT_ONLY
    } else {
        settings.profile.structuredOutputMode
    }
    return buildString {
        appendLine("服务商：${settings.profile.displayName}")
        appendLine("接口：$endpoint")
        appendLine("模型：${settings.profile.defaultModel}")
        appendLine("请求协议：${if (useResponses) "Responses" else "Chat Completions"}")
        if (useResponses) appendLine("思考强度：${settings.profile.reasoningEffort.label}")
        appendLine("结构化输出：${outputMode.name}")
        if (settings.profile.id == AiProviderPresets.deepSeek.id && !useResponses) {
            appendLine("DeepSeek thinking：enabled / high（保留推理能力，正文与思考分开展示）")
            appendLine("DeepSeek max_tokens：393216；MiMo max_completion_tokens：131072（避免思考过程或长 JSON 耗尽输出额度）")
        }
        appendLine("输入文本：$pageTextLength 字符")
        appendLine("提示词：已附加完整 SleepDown JSON 解析协议与字段示例")
        append("密钥：已从本机安全存储读取，未显示")
    }
}

private fun eduDesktopUserAgent(context: Context): String {
    val current = WebSettings.getDefaultUserAgent(context)
    val webKit = Regex("AppleWebKit/[^\\s]+", RegexOption.IGNORE_CASE).find(current)?.value
    val chromium = Regex("(?:Chrome|Chromium)/[0-9.]+", RegexOption.IGNORE_CASE).find(current)?.value
    val safari = Regex("Safari/[^\\s]+", RegexOption.IGNORE_CASE).find(current)?.value
    if (webKit == null || chromium == null || safari == null) {
        return current
            .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
            .replace("; wv", "")
            .replace(" Mobile ", " ")
    }
    return "Mozilla/5.0 (X11; Linux x86_64) $webKit (KHTML, like Gecko) $chromium $safari"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EduImportBrowserScreen(
    state: AppState,
    adapter: EduAdapter,
    webContentBackdrop: LayerBackdrop,
    message: String?,
    webView: WebView?,
    onWebView: (WebView?) -> Unit,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    bridge: ShiguangBridgeHost,
    useDetailTopPadding: Boolean = true,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buttonBackdrop = webContentBackdrop
    var addressText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var aiParsing by remember { mutableStateOf(false) }
    var aiProgress by remember { mutableStateOf<AiEduImportProgress?>(null) }
    var isScreenCapturing by remember { mutableStateOf(false) }
    var screenCaptureStatus by remember { mutableStateOf<String?>(null) }
    var popupWebView by remember(adapter) { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember(adapter) { mutableIntStateOf(0) }
    var rendererRestoreUrl by remember(adapter) { mutableStateOf<String?>(null) }
    var webTopEdgeBitmap by remember(adapter) { mutableStateOf<Bitmap?>(null) }
    val topEdgeSampleHandler = remember(adapter) { Handler(Looper.getMainLooper()) }
    val topEdgeSampleToken = remember(adapter) { Any() }
    val requestInterceptor = remember(adapter) { ShiguangWebRequestInterceptor() }
    val taskProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    LaunchedEffect(taskProgress?.taskId, taskProgress?.finished) {
        val completed = taskProgress?.takeIf {
            it.taskId.isNotBlank() && it.routeLabel == "AI教务导入" && it.finished
        } ?: return@LaunchedEffect
        aiProgress = completed
        aiParsing = false
        onMessage(completed.error ?: completed.liveSummary.ifBlank { completed.steps.lastOrNull().orEmpty() })
    }
    val topPadding = if (useDetailTopPadding) detailContentTopPadding() else 0.dp
    val normalizedUrl = remember(addressText) {
        normalizeEduUrl(addressText)
    }

    fun scheduleWebTopEdgeSample(target: WebView) {
        topEdgeSampleHandler.removeCallbacksAndMessages(topEdgeSampleToken)
        topEdgeSampleHandler.postAtTime(
            {
                val width = target.width
                if (width > 0 && target.height > 0) {
                    Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888).also { bitmap ->
                        target.draw(AndroidCanvas(bitmap))
                        webTopEdgeBitmap = bitmap
                    }
                }
            },
            topEdgeSampleToken,
            android.os.SystemClock.uptimeMillis() + 48L
        )
    }

    fun loadAddress() {
        if (normalizedUrl.isBlank()) {
            onMessage("请输入教务系统网址")
            return
        }
        onUrlChange(normalizedUrl)
        (popupWebView ?: webView)?.loadUrl(normalizedUrl)
    }

    fun appendAiStep(step: String) {
        val next = (aiProgress ?: AiEduImportProgress()).let {
            it.copy(steps = it.steps + step)
        }
        aiProgress = next
        AiEduImportProgressSession.update(next)
    }

    fun setAiProgress(progress: AiEduImportProgress?) {
        aiProgress = progress
        AiEduImportProgressSession.update(progress)
    }

    fun runAiEduImport(forceFallback: Boolean = false) {
        val target = popupWebView ?: webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        if (aiParsing) return
        val routeLabel = "AI教务导入"
        fun cancelAiImport(message: String = "已取消 AI 教务导入") {
            AiEduImportProgressSession.clearActions()
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + "用户取消",
                awaitingConfirmation = false,
                finished = true
            ))
            onMessage(message)
            aiParsing = false
        }

        fun sendCaptureToAi(capture: EduPageCaptureResult, settings: AiImportSettings, includePageText: Boolean = true) {
            val pageUrl = target.url?.takeIf { it.isNotBlank() } ?: currentUrl
            val aiPageText = buildString {
                appendLine("当前教务页面地址：${pageUrl.ifBlank { "未知" }}")
                appendLine("请根据该网址识别学校或教务系统来源，并在模型具备相关能力时参考该学校公开的作息/排课时间。")
                appendLine()
                if (includePageText) {
                    append(capture.text)
                } else {
                    appendLine("用户选择只发送截图，DOM 文本未发送；请以截图中的课表结构、表头、时间轴和课程块为准。")
                }
            }
            AiEduImportProgressSession.clearActions()
            setAiProgress(aiProgress?.copy(
                awaitingConfirmation = false,
                requestSent = false,
                confirmActionLabel = "",
                secondaryConfirmActionLabel = "",
                screenModeActionLabel = "",
                steps = aiProgress?.steps.orEmpty() + (if (includePageText) {
                    "用户确认发送给 AI"
                } else {
                    "用户确认只发送截图给 AI"
                })
            ))
            val initial = checkNotNull(aiProgress).copy(
                requestPreview = aiEduRequestPreview(settings, aiPageText.length)
            )
            onMessage("AI 正在解析当前教务页面...")
            AiImportTaskManager.startCapturedPageImport(
                context = context,
                text = aiPageText,
                screenshots = capture.screenshots,
                sourceName = routeLabel,
                warnings = capture.warnings,
                settings = settings,
                scheduleConfig = state.config,
                initialProgress = initial
            )
        }

        fun prepareCapturePreview(capture: EduPageCaptureResult, settings: AiImportSettings, screenMode: Boolean) {
            val supportsVision = AiProviderPresets.supportsImageInput(settings.profile)
            val pageIssue = inspectEduPageCapture(capture.text)
            val isLoginPage = pageIssue?.step?.contains("登录") == true
            val pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(60_000)
            if (isLoginPage) {
                AiEduImportProgressSession.clearActions()
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + pageIssue.step,
                    pageText = pageText,
                    error = pageIssue.message,
                    finished = true
                ))
                onMessage(pageIssue.message)
                aiParsing = false
                return
            }
            if (capture.screenshots.isNotEmpty() && !supportsVision) {
                AiEduImportProgressSession.clearActions()
                val message = "已生成截图兜底，但当前模型不支持视觉输入。请换视觉模型，或手动进入可复制文本课表页。"
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + "当前模型不支持识屏",
                    pageText = pageText,
                    error = message,
                    finished = true
                ))
                onMessage(message)
                aiParsing = false
                return
            }
            val warningStep = when {
                pageIssue != null && supportsVision -> "页面文本不够像课表，请确认是否改用识屏模式"
                pageIssue != null -> pageIssue.step
                capture.screenshots.isNotEmpty() -> "已准备页面文本和识屏截图，等待确认发送"
                else -> "已抓取页面文本，等待确认发送"
            }
            val confirmLabel = when {
                capture.screenshots.isNotEmpty() -> "发送截图+文本"
                else -> "确认发送文本"
            }
            val secondaryConfirmLabel = if (capture.screenshots.isNotEmpty()) "只发送截图" else ""
            val screenLabel = if (!screenMode && supportsVision) "进入高清识屏" else ""
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + warningStep,
                pageText = pageText,
                hasReadablePageText = capture.text.isNotBlank(),
                screenshotPreviews = capture.screenshots.take(6),
                userPrompt = "帮我按规则导入当前页面的课表",
                attachmentTitle = when {
                    capture.screenshots.isNotEmpty() && capture.text.isNotBlank() -> "课表页面内容"
                    capture.screenshots.isNotEmpty() -> "课表页面截图"
                    else -> "课表页面文字"
                },
                requestSent = false,
                requestPreview = aiEduRequestPreview(settings, capture.text.length),
                awaitingConfirmation = true,
                confirmActionLabel = confirmLabel,
                secondaryConfirmActionLabel = secondaryConfirmLabel,
                screenModeActionLabel = screenLabel,
                cancelActionLabel = "返回重抓",
                finished = false,
                error = null
            ))
            AiEduImportProgressSession.setActions(
                onConfirm = { sendCaptureToAi(capture, settings, includePageText = true) },
                onSecondaryConfirm = if (secondaryConfirmLabel.isNotBlank()) {
                    { sendCaptureToAi(capture, settings, includePageText = false) }
                } else null,
                onScreenMode = if (screenLabel.isNotBlank()) {
                    {
                        setAiProgress(aiProgress?.copy(
                            steps = aiProgress?.steps.orEmpty() + "用户选择识屏模式",
                            awaitingConfirmation = false,
                            secondaryConfirmActionLabel = "",
                            screenModeActionLabel = ""
                        ))
                        scope.launch {
                            delay(420)
                            isScreenCapturing = true
                            screenCaptureStatus = "正在识屏截取，请保持页面不动..."
                            withFrameNanos { }
                            withFrameNanos { }
                            delay(120)
                            val screenCapture = runCatching {
                                try {
                                    captureEduPage(
                                        webView = target,
                                        maxScreenshots = 6,
                                        forceScreenshots = true,
                                        onScreenshotProgress = { index, total ->
                                            screenCaptureStatus = "正在截取第 $index/$total 段"
                                        }
                                    )
                                } finally {
                                    isScreenCapturing = false
                                    screenCaptureStatus = null
                                }
                            }.getOrElse {
                                context.openRegisteredActivity(
                                    TransitionRouteId.ImportToAiProgress,
                                    Intent(context, AiEduImportProgressActivity::class.java)
                                )
                                AiEduImportProgressSession.clearActions()
                                setAiProgress(aiProgress?.copy(
                                    steps = aiProgress?.steps.orEmpty() + "识屏截图失败",
                                    error = it.message ?: "识屏截图失败",
                                    finished = true
                                ))
                                onMessage(it.message ?: "识屏截图失败")
                                aiParsing = false
                                return@launch
                            }
                            context.openRegisteredActivity(
                                TransitionRouteId.ImportToAiProgress,
                                Intent(context, AiEduImportProgressActivity::class.java)
                            )
                            prepareCapturePreview(screenCapture, settings, screenMode = true)
                        }
                    }
                } else null,
                onCancel = { cancelAiImport() }
            )
            onMessage(
                if (capture.screenshots.isNotEmpty()) "已进入识屏预览，确认后才会发送给 AI。"
                else "已抓取页面文本，请确认后再发送给 AI。"
            )
        }

        AiEduImportProgressSession.clearActions()
        setAiProgress(AiEduImportProgress(steps = listOf("准备读取当前页面")))
        context.openRegisteredActivity(
            TransitionRouteId.ImportToAiProgress,
            Intent(context, AiEduImportProgressActivity::class.java)
        )
        setAiProgress(aiProgress?.copy(routeLabel = routeLabel))
        onMessage("正在分层抓取当前页面...")
        aiParsing = true
        scope.launch {
            val capture = runCatching { captureEduPage(target, allowScreenshotFallback = false) }.getOrElse {
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + "页面抓取失败",
                    error = it.message ?: "页面抓取失败",
                    finished = true
                ))
                onMessage(it.message ?: "页面抓取失败")
                aiParsing = false
                return@launch
            }
            val captureStep = when (capture.mode) {
                EduPageCaptureMode.TEXT_ONLY -> "已完成 DOM 深度抓取（${capture.text.length} 字符）"
                EduPageCaptureMode.TEXT_PLUS_SCREENSHOT -> "已完成 DOM 抓取并生成截图兜底（${capture.screenshots.size} 张）"
                EduPageCaptureMode.SCREENSHOT_ONLY -> "DOM 文本不足，已生成截图兜底（${capture.screenshots.size} 张）"
            }
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + captureStep,
                pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(60_000)
            ))
            if (capture.warnings.isNotEmpty()) {
                appendAiStep("页面诊断：${capture.warnings.joinToString("；").take(120)}")
            }
            val settings = AiImportSettingsStore.load(context)
            appendAiStep(
                if (capture.screenshots.isEmpty()) "已准备页面文本预览，等待用户确认"
                else "已生成页面截图兜底，等待用户确认"
            )
            prepareCapturePreview(capture, settings, screenMode = capture.screenshots.isNotEmpty())
        }
    }

    fun runOriginalImportScript() {
        val target = popupWebView ?: webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        target.evaluateJavascript(AiEduPageExtractScript) { encoded ->
            runCatching { inspectEduPageCapture(decodeAiEduPageSnapshot(encoded)) }
                .getOrNull()
                ?.let { onMessage("${it.message}；仍将尝试执行原有拾光导入脚本。") }
        }
        scope.launch {
            runCatching { ShiguangWarehouse.resolveScript(context, adapter) }
                .onSuccess { script ->
                    bridge.bindWebView(target)
                    bridge.beginTask(state.config, state.periods)
                    target.injectShiguangRuntime(desktopMode)
                    onMessage("正在执行拾光官方适配器")
                    target.evaluateJavascript(script, null)
                }
                .onFailure { onMessage("拾光仓库脚本加载失败：${it.message ?: "找不到该学校的导入脚本"}") }
        }
    }

    fun updateNavigationState(target: WebView?) {
        canGoBack = target?.canGoBack() == true
        canGoForward = target?.canGoForward() == true
    }

    fun applyEduWebMode(target: WebView, desktop: Boolean) {
        with(target.settings) {
            userAgentString = if (desktop) {
                eduDesktopUserAgent(context)
            } else {
                null
            }
            useWideViewPort = true
            loadWithOverviewMode = !desktop
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            textZoom = 100
        }
        target.setInitialScale(if (desktop) 100 else 0)
    }

    fun closePopupWebView() {
        popupWebView?.let { popup ->
            popup.uninstallShiguangRuntime()
            (popup.parent as? ViewGroup)?.removeView(popup)
            popup.releaseSleepDownWebView(clearResourceCache = false)
        }
        popupWebView = null
        webView?.let { primary ->
            bridge.bindWebView(primary)
            addressText = primary.url.orEmpty().ifBlank { currentUrl }
            onUrlChange(addressText)
            updateNavigationState(primary)
            scheduleWebTopEdgeSample(primary)
        }
    }

    fun handleExternalNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme == "http" || scheme == "https") return false
        val intent = try {
            if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, uri)
        } catch (_: java.net.URISyntaxException) {
            onMessage("无法识别此页面请求的外部链接")
            return true
        }
        val safeIntent = intent.apply {
            component = null
            selector = null
        }
        if (safeIntent.resolveActivity(context.packageManager) == null) {
            onMessage("未找到可处理 ${scheme.ifBlank { "此" }} 链接的应用")
            return true
        }
        context.startActivity(safeIntent)
        return true
    }

    fun configureEduWebView(target: WebView, isPopup: Boolean) {
        target.apply webView@ {
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setOnScrollChangeListener { _, _, _, _, _ ->
                scheduleWebTopEdgeSample(this)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            configureEduImportSecurity()
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(!isPopup)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            applyEduWebMode(this, desktopMode)
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
            }
            installShiguangRuntime(bridge)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    return handleExternalNavigation(request.url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    view?.injectShiguangRuntime(desktopMode)
                    val visiblePage = if (isPopup) popupWebView === view else popupWebView == null
                    if (visiblePage) updateNavigationState(view)
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.injectShiguangRuntime(desktopMode)
                    view?.let(::scheduleWebTopEdgeSample)
                    val visiblePage = if (isPopup) popupWebView === view else popupWebView == null
                    if (visiblePage) updateNavigationState(view)
                    if (visiblePage && !url.isNullOrBlank()) {
                        addressText = url
                        onUrlChange(url)
                        EduLoginHistoryStore.remember(
                            context,
                            adapter,
                            url,
                            CookieManager.getInstance().getCookie(url)
                        )
                        CookieManager.getInstance().flush()
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return request?.let { requestInterceptor.intercept(it, desktopMode) }
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onMessage("页面加载失败：${error?.description ?: "网络连接异常"}")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        onMessage("页面返回 HTTP ${errorResponse?.statusCode ?: "错误"}")
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    onMessage("网站证书校验失败，已停止加载")
                    super.onReceivedSslError(view, handler, error)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val restoreUrl = view?.url?.takeIf { it.isNotBlank() }
                        ?: addressText.takeIf { it.isNotBlank() }
                        ?: currentUrl
                    view?.uninstallShiguangRuntime()
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                    popupWebView?.takeIf { it !== view }?.let { popup ->
                        popup.uninstallShiguangRuntime()
                        (popup.parent as? ViewGroup)?.removeView(popup)
                        popup.destroy()
                    }
                    popupWebView = null
                    if (isPopup) {
                        webView?.let { primary ->
                            primary.uninstallShiguangRuntime()
                            (primary.parent as? ViewGroup)?.removeView(primary)
                            primary.destroy()
                        }
                    }
                    onWebView(null)
                    bridge.bindWebView(null)
                    rendererRestoreUrl = restoreUrl
                    webViewGeneration += 1
                    onMessage(
                        if (detail?.didCrash() == true) "网页渲染器异常，已恢复当前页面"
                        else "网页渲染器已被系统回收，正在恢复"
                    )
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    if (isPopup) return false
                    val message = resultMsg ?: return false
                    val transport = message.obj as? WebView.WebViewTransport ?: return false
                    closePopupWebView()
                    val child = WebView(context)
                    configureEduWebView(child, isPopup = true)
                    popupWebView = child
                    transport.webView = child
                    message.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    if (window === popupWebView) closePopupWebView()
                }
            }
            enableSleepDownDownloads()
        }
    }

    fun createEduWebView(context: Context): WebView {
        return WebView(context).apply {
            configureEduWebView(this, isPopup = false)
            onWebView(this)
            bridge.bindWebView(this)
            updateNavigationState(this)
            val initialUrl = rendererRestoreUrl ?: normalizedUrl
            if (initialUrl.isNotBlank()) loadUrl(initialUrl)
        }
    }

    BackHandler(enabled = popupWebView != null) {
        closePopupWebView()
    }
    DisposableEffect(Unit) {
        onDispose {
            topEdgeSampleHandler.removeCallbacksAndMessages(topEdgeSampleToken)
            popupWebView?.let { popup ->
                popup.uninstallShiguangRuntime()
                (popup.parent as? ViewGroup)?.removeView(popup)
                popup.releaseSleepDownWebView(clearResourceCache = false)
            }
            popupWebView = null
        }
    }

    // Keep the WebView at its natural position below the compact bar. A one-pixel sample of the
    // current WebView top edge is stretched only through the producer's top-bar region, so the
    // gradient glass keeps the page color without moving or clipping the page header itself.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropProducer(webContentBackdrop)
                .background(MaterialTheme.colorScheme.background)
        ) {
            webTopEdgeBitmap?.let { edge ->
                Image(
                    bitmap = edge.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topPadding),
                    contentScale = ContentScale.FillBounds
                )
            }
            key(webViewGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
                    factory = { createEduWebView(it) },
                    // Navigation remains event-driven; observed page URLs never feed back into
                    // loadUrl from recomposition.
                    update = {},
                    onRelease = { released ->
                        if (released === webView) {
                            onWebView(null)
                            bridge.bindWebView(null)
                        }
                        released.uninstallShiguangRuntime()
                        released.releaseSleepDownWebView()
                    }
                )
            }
            popupWebView?.let { popup ->
                key(popup) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            },
                        factory = { popup },
                        update = {},
                        onRelease = { released ->
                            if (released === popupWebView) {
                                popupWebView = null
                                released.uninstallShiguangRuntime()
                                released.releaseSleepDownWebView(clearResourceCache = false)
                            }
                        }
                    )
                }
            }
        }

        val visibleMessage = screenCaptureStatus ?: message
        visibleMessage?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding + 10.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isScreenCapturing) {
            EduBrowserDock(
                config = state.config,
                backdrop = buttonBackdrop,
                address = addressText,
                onAddressChange = { addressText = it },
                onGo = { loadAddress() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = {
                    val target = popupWebView ?: webView
                    target?.goBack()
                    updateNavigationState(target)
                },
                onForward = {
                    val target = popupWebView ?: webView
                    target?.goForward()
                    updateNavigationState(target)
                },
                originalImportAvailable = !adapter.isAiEduImportTool(),
                aiImportRunning = aiParsing,
                onOriginalImport = { runOriginalImportScript() },
                onAiImport = { runAiEduImport(forceFallback = !adapter.isAiEduImportTool()) },
                desktopMode = desktopMode,
                onRefresh = {
                    (popupWebView ?: webView)?.reload()
                    onMessage("已刷新页面")
                },
                onToggleDesktopMode = {
                    desktopMode = !desktopMode
                    (popupWebView ?: webView)?.let { target ->
                        // Changing UA during an active load makes WebView restart that load itself.
                        // Stop first, apply the complete mode, then perform one explicit reload.
                        target.stopLoading()
                        applyEduWebMode(target, desktopMode)
                        target.reload()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun ConfirmScheduleScreen(
    draft: ImportDraft,
    warning: String? = null,
    backdrop: Backdrop? = null,
    onCancel: () -> Unit,
    onDraftChanged: ((ImportDraft) -> Unit)? = null,
    onConfirm: (Boolean) -> Unit
) {
    val historyContext = LocalContext.current
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
    if (previewDraft.source == ImportDraftSource.AI_EDU) {
        LaunchedEffect(previewDraft) {
            AiImportHistoryStore.record(historyContext, previewDraft, AiEduImportProgressSession.progress.value)
        }
        AiImportChatPreview(
            draft = previewDraft,
            warning = warning,
            backdrop = backdrop,
            onCancel = onCancel,
            onDraftChanged = onDraftChanged,
            onConfirm = onConfirm
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                LiquidAlertActions(
                    actions = listOf(
                        LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary) { onConfirm(true) },
                        LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive) { onConfirm(false) },
                        LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onCancel)
                    ),
                    backdrop = backdrop,
                    config = previewDraft.config
                )
            }
        }
    }
}

private fun AiImportHistoryBackgroundCapture.cropToAiHistorySource(bounds: Rect): Bitmap? =
    runCatching {
        val left = (bounds.left - rootLeftInWindow).roundToInt()
        val top = (bounds.top - rootTopInWindow).roundToInt()
        val cropWidth = bounds.width.roundToInt()
        val cropHeight = bounds.height.roundToInt()
        // Do not clamp an invalid window/root conversion to an edge pixel. A partial source is
        // worse than no transition because it makes the button disappear before a malformed
        // black shell takes over.
        if (left < 0 || top < 0 || cropWidth <= 0 || cropHeight <= 0 ||
            left + cropWidth > bitmap.width || top + cropHeight > bitmap.height
        ) {
            return@runCatching null
        }
        Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }.getOrNull()

@Composable
private fun AiImportChatPreview(
    draft: ImportDraft,
    warning: String?,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onDraftChanged: ((ImportDraft) -> Unit)?,
    onConfirm: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val progress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    val sessionDraft by AiEduImportProgressSession.previewDraft.collectAsStateWithLifecycle()
    val textColor = glassForegroundColor(settingsVisualConfig(draft.config))
    var traceExpanded by remember { mutableStateOf(false) }
    var revisionText by remember(draft) { mutableStateOf("") }
    var revising by remember { mutableStateOf(false) }
    var ownedRevisionTaskId by remember { mutableStateOf<String?>(null) }
    var revisionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownedRevisionTaskId, progress?.taskId, progress?.finished, progress?.error, sessionDraft) {
        val taskId = ownedRevisionTaskId ?: return@LaunchedEffect
        val taskProgress = progress?.takeIf { it.taskId == taskId && it.finished } ?: return@LaunchedEffect
        revisionError = taskProgress.error
        if (taskProgress.error == null) {
            sessionDraft?.let { revised ->
                revisionText = ""
                onDraftChanged?.invoke(revised)
            }
        }
        revising = false
        ownedRevisionTaskId = null
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ComposeColor(0xFF0A84FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "AI",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "我已整理出 ${draft.courses.size} 门课程，并完成节次、周次和时间校验。请检查下面的预览，确认后才会写入课表。",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        warning?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            progress?.let { importProgress ->
                val statuses = aiEduAgentRunStatuses(importProgress)
                if (statuses.isNotEmpty()) {
                    item {
                        AgentRunTrace(
                            statuses = statuses,
                            expanded = traceExpanded,
                            foreground = textColor,
                            active = false,
                            onToggle = { traceExpanded = !traceExpanded }
                        )
                    }
                }
            }
            item {
                Text(
                    "导入预览 · ${draft.config.totalWeeks} 周 · ${draft.periods.size} 个节次",
                    color = textColor.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (draft.courses.isEmpty()) {
                item { Text("没有解析到课程", color = MaterialTheme.colorScheme.error) }
            } else {
                itemsIndexed(
                    draft.courses,
                    key = { index, course ->
                        "ai_preview_${index}_${course.name}_${course.weekday}_${course.periods.joinToString("_")}"
                    }
                ) { _, course ->
                    ImportPreviewCourseCard(course, draft.periods, draft.config)
                }
            }
            revisionError?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onDraftChanged != null) {
                AiEduRevisionComposer(
                    value = revisionText,
                    enabled = !revising,
                    textColor = textColor,
                    config = draft.config,
                    backdrop = backdrop,
                    onValueChange = { revisionText = it.take(500) },
                    onSend = {
                        val instruction = revisionText.trim()
                        if (instruction.isNotEmpty() && !revising) {
                            revising = true
                            revisionError = null
                            val settings = AiImportSettingsStore.load(context)
                            val baseProgress = progress ?: AiEduImportProgress(routeLabel = "AI 手动导入")
                            ownedRevisionTaskId = AiImportTaskManager.startRevision(
                                context = context,
                                baseDraft = draft,
                                instruction = instruction,
                                baseProgress = baseProgress,
                                settings = settings,
                                historicalEntryId = null
                            )
                        }
                    }
                )
            }
            LiquidAlertActions(
                actions = listOf(
                    LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary) { onConfirm(true) },
                    LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive) { onConfirm(false) },
                    LiquidAlertAction("返回检查", LiquidAlertActionStyle.Secondary, onClick = onCancel)
                ),
                backdrop = backdrop,
                config = draft.config
            )
        }
    }
}

@Composable
private fun AiEduRevisionComposer(
    value: String,
    enabled: Boolean,
    textColor: ComposeColor,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val content: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = false,
                maxLines = 4,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                if (enabled) "告诉 AI 哪里需要修改…" else "AI 正在修改…",
                                color = textColor.copy(alpha = 0.46f)
                            )
                        }
                        inner()
                    }
                }
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = if (enabled) "发送" else "处理中",
                role = DialogButtonRole.Confirm,
                roundIcon = false,
                modifier = Modifier.height(44.dp),
                onClick = onSend
            )
        }
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            surfaceColor = if (glassUsesLightStyle(config)) ComposeColor.White.copy(alpha = 0.22f) else ComposeColor(0xFF161618).copy(alpha = 0.40f),
            blurRadius = 12.dp,
            content = content
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content
        )
    }
}

internal fun buildAiRevisionInput(
    draft: ImportDraft,
    instruction: String,
    history: AiEduImportProgress? = null
): String {
    val compactSchedule = buildString {
        appendLine("总周数：${draft.config.totalWeeks}")
        appendLine("节次：${draft.periods.sortedBy { it.periodIndex }.joinToString("；") { "${it.periodIndex}:${it.startTime}-${it.endTime}" }}")
        draft.courses.forEachIndexed { index, course ->
            appendLine(
                "#${index + 1} ${course.name} | 教师:${course.teacher.orEmpty()} | 地点:${course.location.orEmpty()} | " +
                    "周${course.weekday} | 节:${course.periods.joinToString(",")} | 周次:${course.weeks.joinToString(",")} | ${course.weekParity} | 备注:${course.note.orEmpty()}"
            )
        }
    }.trim()
    val priorRequests = history?.conversationTurns.orEmpty()
        .map { it.userPrompt }
        .ifEmpty { listOfNotNull(history?.userPrompt?.takeIf(String::isNotBlank)) }
        .joinToString("\n") { "- $it" }
    return """
        这是当前已经通过本地校验的课表索引。每条课程前的 #编号 是稳定定位符：
        $compactSchedule

        ${if (priorRequests.isNotBlank()) "此前用户要求：\n$priorRequests\n" else ""}
        用户要求：$instruction
        只修改用户明确指出的内容，保留其他课程、周次和节次。
        直接调用 PATCH_SCHEDULE：replace_course 以 #编号完整替换一门课，add_course 新增，remove_course 删除，replace_periods 修改作息时间，set_total_weeks 修改总周数。只提交必要操作，不要回传完整课表。
        changeSummary 必须逐项说明本轮实际改变了哪些课程及字段；没有改动时明确说明原因，禁止写泛泛的“已完成修改”。
        只有用户要求复核、重新识别或核对原网页/附件，而当前课表不足以判断时，才调用 READ_ORIGINAL_IMPORT_SOURCE。不要为了普通字段修改读取原始材料。
    """.trimIndent()
}

internal fun buildAiOriginalSourceContext(history: AiEduImportProgress): String = buildString {
    appendLine("以下是本次导入留存的原始上下文：")
    if (history.attachmentTitle.isNotBlank()) appendLine("附件：${history.attachmentTitle}")
    if (history.requestPreview.isNotBlank()) appendLine("请求信息：\n${history.requestPreview}")
    if (history.pageText.isNotBlank()) appendLine("原始文本：\n${history.pageText}")
    val turns = history.conversationTurns.ifEmpty {
        listOf(
            AiEduImportConversationTurn(
                userPrompt = history.userPrompt,
                reasoningOutput = history.reasoningOutput,
                aiOutput = history.aiOutput
            )
        )
    }
    turns.forEachIndexed { index, turn ->
        appendLine("第 ${index + 1} 轮用户要求：${turn.userPrompt}")
        if (turn.reasoningOutput.isNotBlank()) appendLine("第 ${index + 1} 轮推理摘要：\n${turn.reasoningOutput}")
        if (turn.aiOutput.isNotBlank()) appendLine("第 ${index + 1} 轮原始输出：\n${turn.aiOutput}")
    }
    if (history.screenshotPreviews.isNotEmpty()) {
        append("另附 ${history.screenshotPreviews.size} 张按原顺序保存的视觉材料。")
    }
}
