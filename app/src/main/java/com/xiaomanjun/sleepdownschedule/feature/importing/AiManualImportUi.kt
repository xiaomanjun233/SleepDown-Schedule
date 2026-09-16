package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import java.io.File
import java.net.URL
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


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
    initialFileUri: Uri? = null,
    onInitialFileConsumed: () -> Unit = {},
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    val transitionDensity = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val backgroundPermissionGate = rememberAiImportBackgroundPermissionGate()
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
        bridge.beginTask(
            state.config,
            state.periods,
            initialPromptAnswer = request.input,
            mergeOverlappingTimeSlots = request.adapterId.equals("WakeUp", ignoreCase = true)
        )
        var scriptStarted = false
        target = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            configureEduImportSecurity()
            enableSystemCredentialAutofill()
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
    fun prepareImportFile(uri: Uri) {
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
                        IcsScheduleCodec.parse(file.bytes, state.config)
                            .onSuccess(onParsed)
                            .onFailure { error = it.message ?: "ICS 解析失败" }
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
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::prepareImportFile)
    }
    LaunchedEffect(initialFileUri) {
        initialFileUri?.let { uri ->
            onInitialFileConsumed()
            prepareImportFile(uri)
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
                    backgroundPermissionGate.continueWithPermissionTip {
                        // Some document providers report office files as application/octet-stream;
                        // validate the real extension/MIME after selection instead of hiding them here.
                        fileLauncher.launch(arrayOf("*/*"))
                    }
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
                    onClick = {
                        backgroundPermissionGate.continueWithPermissionTip(::repairTokenWithAi)
                    }
                )
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showAiTokenRepairPrompt = false }
        )
    }
    AiImportBackgroundPermissionTip(
        state = backgroundPermissionGate,
        backdrop = backdrop,
        config = state.config
    )
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
                    .clip(RoundedRectangle(22.dp))
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
                    .clip(RoundedRectangle(22.dp))
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
                                    .clip(RoundedRectangle(18.dp))
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

