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

@Composable
fun NormalizedAiManualImportScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var aiParsing by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) }
    var aiSettings by remember { mutableStateOf(AiImportSettingsStore.load(context)) }
    val textColor = glassForegroundColor(state.config)
    val aiFileUploadVisible = aiSettings.profile.id != AiProviderPresets.deepSeek.id
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
                        return@fileLoaded
                    }
                    if (settings.profile.id == AiProviderPresets.deepSeek.id) {
                        error = "当前 DeepSeek 仅支持文本和 ICS 导入。PDF 或图片请切换 OpenAI / MiMo / 自定义视觉模型。"
                        return@fileLoaded
                    }
                    AiEduImportProgressSession.clearActions()
                    AiEduImportProgressSession.update(
                        AiEduImportProgress(
                            routeLabel = "AI 手动导入",
                            steps = listOf("准备读取文件"),
                            requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n输入：用户选择的 PDF 或图片文件\n密钥：已从本机安全存储读取，未显示"
                        )
                    )
                    context.startActivity(Intent(context, AiEduImportProgressActivity::class.java))
                    val fileSummary = buildString {
                        appendLine("文件名：${file.displayName}")
                        appendLine("类型：${file.mimeType}")
                        appendLine("大小：${formatAiImportFileSize(file.bytes.size)}")
                        appendLine("处理：OpenAI 官方配置会优先使用原生 PDF 输入；MiMo / 视觉模型会将 PDF 渲染为图片；DeepSeek 不显示文件上传入口。")
                    }
                    routeMessage = "正在使用 ${settings.profile.displayName} 解析 ${file.displayName}..."
                    AiEduImportProgressSession.update(
                        AiEduImportProgress(
                            routeLabel = "AI 手动导入",
                            steps = listOf("准备读取文件", "已读取文件", "正在发送给 AI 解析"),
                            requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}\n类型：${file.mimeType}\n大小：${formatAiImportFileSize(file.bytes.size)}\n提示词：已附加完整 SleepDown JSON 解析协议",
                            pageText = fileSummary
                        )
                    )
                    AiScheduleImportService(context).parseScheduleFile(file, settings)
                        .onSuccess { aiResult ->
                            routeMessage = aiResult.routeMessage
                            val output = aiResult.output.ifBlank { aiResult.rawOutput }
                            AiEduImportProgressSession.update(
                                AiEduImportProgress(
                                    routeLabel = "AI 手动导入",
                                    steps = listOf("准备读取文件", "已读取文件", "已发送给 AI", "AI 已返回可见文本，开始本地校验"),
                                    requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}\n处理路线：${aiResult.routeMessage}",
                                    pageText = fileSummary,
                                    reasoningOutput = aiResult.reasoningOutput.take(20_000),
                                    aiOutput = aiResult.rawOutput.take(80_000)
                                )
                            )
                            ScheduleImportParser.parse(output, state.config)
                                .onSuccess { draft ->
                                    error = null
                                    AiEduImportProgressSession.update(
                                        AiEduImportProgress(
                                            routeLabel = "AI 手动导入",
                                            steps = listOf("准备读取文件", "已读取文件", "已发送给 AI", "AI 已返回可见文本", "本地校验通过，进入导入预览"),
                                            requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}\n处理路线：${aiResult.routeMessage}",
                                            pageText = fileSummary,
                                            reasoningOutput = aiResult.reasoningOutput.take(20_000),
                                            aiOutput = aiResult.rawOutput.take(80_000),
                                            finished = true
                                        )
                                    )
                                    onParsed(draft)
                                }
                                .onFailure {
                                    error = "AI 已返回内容，但本地解析失败：${it.message ?: "未知错误"}"
                                    AiEduImportProgressSession.update(
                                        AiEduImportProgress(
                                            routeLabel = "AI 手动导入",
                                            steps = listOf("准备读取文件", "已读取文件", "已发送给 AI", "AI 已返回可见文本", "本地校验失败"),
                                            requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}\n处理路线：${aiResult.routeMessage}",
                                            pageText = fileSummary,
                                            reasoningOutput = aiResult.reasoningOutput.take(20_000),
                                            aiOutput = aiResult.rawOutput.take(80_000),
                                            error = it.message ?: "AI 返回内容无法解析",
                                            finished = true
                                        )
                                    )
                                }
                        }
                        .onFailure {
                            error = it.message ?: "AI 文件解析失败"
                            val rawBody = it.aiRawResponseBody().orEmpty()
                            AiEduImportProgressSession.update(
                                AiEduImportProgress(
                                    routeLabel = "AI 手动导入",
                                    steps = listOf("准备读取文件", "已读取文件", "AI 请求失败"),
                                    requestPreview = "服务商：${settings.profile.displayName}\n模型：${settings.profile.defaultModel}\n文件：${file.displayName}",
                                    pageText = fileSummary,
                                    reasoningOutput = extractAiReasoningForDisplay(rawBody).take(20_000),
                                    aiOutput = sanitizeAiOutputForDisplay(rawBody).take(80_000),
                                    error = it.message ?: "AI 文件解析失败",
                                    finished = true
                                )
                            )
                        }
                }
                .onFailure {
                    error = it.message ?: "文件读取失败"
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
            aiParsing = false
        }
    }
    fun parseDraft() {
        val result = ScheduleImportParser.parse(jsonText, state.config)
        result.onSuccess { error = null; onParsed(it) }.onFailure { error = it.message ?: "口令解析失败" }
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
        fileUploadVisible = aiFileUploadVisible,
        selectedFileName = selectedFileName,
        routeMessage = routeMessage,
        error = error,
        aiParsing = aiParsing,
        onPrimaryAction = {
            when (selectedMode) {
                0 -> parseDraft()
                1 -> icsFileLauncher.launch(
                    arrayOf("text/calendar", "application/ics", "application/octet-stream")
                )
                else -> if (!aiParsing) {
                    if (aiFileUploadVisible) {
                        fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                    } else {
                        error = "当前 DeepSeek 不支持 PDF 或图片输入，请切换 OpenAI、MiMo 或自定义视觉模型。"
                    }
                }
            }
        }
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
    fileUploadVisible: Boolean,
    selectedFileName: String?,
    routeMessage: String?,
    error: String?,
    aiParsing: Boolean,
    onPrimaryAction: () -> Unit
) {
    val textColor = glassForegroundColor(state.config)
    Column(Modifier.fillMaxSize()) {
        LiquidDialogHeader("手动导入课表", onCancel, backdrop, state.config)
        if (selectedMode == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ComposeColor.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.18f else 0.08f))
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
                    .background(ComposeColor.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.18f else 0.08f))
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
                DialogLiquidButton(backdrop, "刷新", onRefreshSettings)
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            LiquidOptionTabs(
                selectedIndex = selectedMode,
                labels = listOf("粘贴口令", "导入 ICS", "PDF/图片"),
                backdrop = backdrop,
                config = state.config,
                width = maxWidth,
                onSelected = onModeSelected
            )
        }
        LiquidDialogBody {
            if (selectedMode == 0) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogLiquidButton(backdrop, "复制提示词", onCopyPrompt, modifier = Modifier.weight(1f))
                        DialogLiquidButton(backdrop, "清理格式", onCleanText, modifier = Modifier.weight(1f))
                    }
                    DialogCapsuleField(
                        value = jsonText,
                        onValueChange = onJsonTextChange,
                        placeholder = "粘贴 SleepDown 课表口令或 AI 返回内容",
                        config = state.config,
                        minLines = 8,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (selectedMode == 1) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "选择标准 .ics 日历文件后，应用会在本机识别课程时间、重复规则和时区，并直接进入导入预览。",
                        color = textColor.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                    selectedFileName?.let { Text("已选择：$it", color = textColor) }
                    routeMessage?.let {
                        Text(it, color = textColor.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (fileUploadVisible) {
                            "选择 PDF 或课表图片，文件将使用当前模型解析。ICS 请使用独立的本地导入栏。"
                        } else {
                            "当前 DeepSeek 不支持 PDF 或图片输入，请切换 OpenAI、MiMo 或自定义视觉模型。"
                        },
                        color = textColor.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                    selectedFileName?.let { Text("已选择：$it", color = textColor) }
                    routeMessage?.let {
                        Text(it, color = textColor.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
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
            DialogLiquidButton(
                backdrop = backdrop,
                label = when {
                    selectedMode == 0 -> "解析并预览"
                    selectedMode == 1 -> "选择 ICS 文件"
                    aiParsing -> "解析中..."
                    else -> "选择 PDF/图片并解析"
                },
                role = DialogButtonRole.Confirm,
                iconRes = R.drawable.ic_download,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPrimaryAction
            )
        }
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

    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
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
    val aiEduAdapters = remember(adapters) {
        adapters.filter { it.isAiEduImportTool() }
    }
    val pinnedAdapters = remember(adapters) {
        adapters.filter { !it.isAiEduImportTool() && (it.isGeneralEduTool() || it.isEduTestTool()) }
            .sortedWith(compareBy<EduAdapter> { it.school.id }.thenBy { it.adapterName })
    }
    val indexedAdapters = remember(adapters) {
        adapters.filterNot { it.isAiEduImportTool() || it.isGeneralEduTool() || it.isEduTestTool() }
    }
    val grouped = remember(indexedAdapters) {
        indexedAdapters.groupBy { it.school.initial.ifBlank { "#" }.uppercase() }.toSortedMap()
    }
    val letters = remember(grouped) { grouped.keys.toList() }
    val sectionPositions = remember(grouped, aiEduAdapters, pinnedAdapters) {
        val aiSectionSize = if (aiEduAdapters.isEmpty()) 0 else 1 + aiEduAdapters.size
        val pinnedSectionSize = if (pinnedAdapters.isEmpty()) 0 else 1 + pinnedAdapters.size
        var index = 1 + aiSectionSize + pinnedSectionSize
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
                if (aiEduAdapters.isNotEmpty()) {
                    item(key = "ai-edu-title") { GlassPreferenceCategory("AI教务导入") }
                    items(aiEduAdapters, key = { "ai-${it.adapterId}" }) { adapter ->
                        SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(adapter.school.name, adapter.adapterName, onClick = { onSelect(adapter) })
                        }
                    }
                }
                if (pinnedAdapters.isNotEmpty()) {
                    item(key = "general-edu-title") { GlassPreferenceCategory("通用教务") }
                    items(pinnedAdapters, key = { "pinned-${it.adapterId}" }) { adapter ->
                        SettingsGroup(backdrop = null, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(adapter.school.name, adapter.adapterName, onClick = { onSelect(adapter) })
                        }
                    }
                }
                grouped.forEach { (letter, list) ->
                    item(key = "section-$letter") { GlassPreferenceCategory(letter) }
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
            if (adapter.requiresManualEduUrl()) "" else adapter.importUrl.ifBlank { "https://" }
        )
    }
    var showGeneralUrlDialog by remember(adapter) { mutableStateOf(adapter.requiresManualEduUrl()) }
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
                    helperText = if (adapter.isAiEduImportTool()) {
                        "AI教务导入需要先打开学校教务系统网址。登录后进入课表页面，后续可使用 AI 解析当前页面。"
                    } else {
                        "通用教务需要先填写学校教务系统网址，进入后可继续在顶部网址栏修改。"
                    },
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
        onMessage = { message = it },
        onAiParsed = onParsed
    )
}

@Composable
fun GeneralEduUrlDialog(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    initialUrl: String,
    helperText: String = "通用教务需要先填写学校教务系统网址，进入后可继续在顶部网址栏修改。",
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl.ifBlank { "https://" }) }
    var error by remember { mutableStateOf<String?>(null) }
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

data class AiEduImportProgress(
    val steps: List<String> = emptyList(),
    val routeLabel: String = "",
    val requestPreview: String = "",
    val pageText: String = "",
    val screenshotPreviews: List<RenderedPageImage> = emptyList(),
    val reasoningOutput: String = "",
    val aiOutput: String = "",
    val awaitingConfirmation: Boolean = false,
    val confirmActionLabel: String = "",
    val secondaryConfirmActionLabel: String = "",
    val screenModeActionLabel: String = "",
    val cancelActionLabel: String = "返回重抓",
    val finished: Boolean = false,
    val error: String? = null
)

enum class AiEduImportStepStatus {
    Done,
    Current,
    Pending,
    Error
}

data class AiEduImportStepRow(
    val text: String,
    val status: AiEduImportStepStatus
)

private val AiEduImportPendingSteps = listOf(
    "读取当前页面",
    "DOM 深度抓取",
    "滚动补抓页面",
    "截图兜底判断",
    "检查是否为课表页",
    "读取 AI 配置",
    "发送给 AI 解析",
    "等待 AI 返回",
    "本地校验",
    "进入导入预览"
)

fun aiEduImportStepRows(progress: AiEduImportProgress): List<AiEduImportStepRow> {
    val rows = progress.steps.mapIndexed { index, step ->
        val isLast = index == progress.steps.lastIndex
        val status = when {
            progress.error != null && isLast -> AiEduImportStepStatus.Error
            progress.finished -> AiEduImportStepStatus.Done
            isLast -> AiEduImportStepStatus.Current
            else -> AiEduImportStepStatus.Done
        }
        AiEduImportStepRow(step, status)
    }.toMutableList()
    if (!progress.finished && progress.error == null) {
        AiEduImportPendingSteps.drop(progress.steps.size).forEach { step ->
            rows += AiEduImportStepRow(step, AiEduImportStepStatus.Pending)
        }
    }
    return rows
}

private fun aiEduStepColor(status: AiEduImportStepStatus, fallback: ComposeColor): ComposeColor {
    return when (status) {
        AiEduImportStepStatus.Done -> fallback.copy(alpha = 0.42f)
        AiEduImportStepStatus.Current -> ComposeColor(0xFF0A84FF)
        AiEduImportStepStatus.Pending -> fallback.copy(alpha = 0.92f)
        AiEduImportStepStatus.Error -> ComposeColor(0xFFFF453A)
    }
}

private fun aiEduRequestPreview(settings: AiImportSettings, pageTextLength: Int): String {
    val baseUrl = normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl)
    val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
    val outputMode = if (settings.profile.id == AiProviderPresets.deepSeek.id) {
        StructuredOutputMode.PROMPT_ONLY
    } else {
        settings.profile.structuredOutputMode
    }
    return buildString {
        appendLine("服务商：${settings.profile.displayName}")
        appendLine("接口：$endpoint")
        appendLine("模型：${settings.profile.defaultModel}")
        appendLine("结构化输出：${outputMode.name}")
        if (settings.profile.id == AiProviderPresets.deepSeek.id) {
            appendLine("DeepSeek thinking：enabled / high（保留推理能力，正文与思考分开展示）")
            appendLine("DeepSeek max_tokens：393216；MiMo max_completion_tokens：131072（避免思考过程或长 JSON 耗尽输出额度）")
        }
        appendLine("输入文本：$pageTextLength 字符")
        appendLine("提示词：已附加完整 SleepDown JSON 解析协议与字段示例")
        append("密钥：已从本机安全存储读取，未显示")
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
    onMessage: (String) -> Unit,
    onAiParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fallbackBackdrop = rememberLayerBackdrop()
    val buttonBackdrop = backdrop ?: fallbackBackdrop
    var addressText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastRequestedUrl by remember { mutableStateOf<String?>(null) }
    var desktopMode by remember { mutableStateOf(false) }
    var aiParsing by remember { mutableStateOf(false) }
    var aiProgress by remember { mutableStateOf<AiEduImportProgress?>(null) }
    var isScreenCapturing by remember { mutableStateOf(false) }
    var screenCaptureStatus by remember { mutableStateOf<String?>(null) }
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
        val target = webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        if (aiParsing) return
        val routeLabel = when {
            forceFallback -> "AI 兜底扒页"
            adapter.isAiEduImportTool() -> "AI 专用教务导入"
            else -> "AI 解析当前页"
        }
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
                confirmActionLabel = "",
                secondaryConfirmActionLabel = "",
                screenModeActionLabel = "",
                steps = aiProgress?.steps.orEmpty() + (if (includePageText) {
                    "用户确认发送给 AI"
                } else {
                    "用户确认只发送截图给 AI"
                })
            ))
            scope.launch {
                if (settings.apiKey.isBlank()) {
                    setAiProgress(aiProgress?.copy(
                        steps = aiProgress?.steps.orEmpty() + "缺少 AI API Key",
                        error = "请先在设置中配置 AI API Key",
                        finished = true
                    ))
                    onMessage("请先在设置中配置 AI API Key")
                    aiParsing = false
                    return@launch
                }
                appendAiStep("已读取 AI 配置：${settings.profile.displayName} / ${settings.profile.defaultModel}")
                appendAiStep("正在发送给 AI 解析")
                onMessage("AI 正在解析当前教务页面...")
                AiScheduleImportService(context)
                    .parseScheduleCapturedPage(
                        text = aiPageText,
                        screenshots = capture.screenshots,
                        sourceName = routeLabel,
                        warnings = capture.warnings,
                        settings = settings
                    )
                    .onSuccess { result ->
                        setAiProgress(aiProgress?.copy(
                            steps = aiProgress?.steps.orEmpty() + "AI 已返回可见文本，开始本地校验",
                            reasoningOutput = result.reasoningOutput.take(20_000),
                            aiOutput = result.rawOutput.take(80_000)
                        ))
                        ScheduleImportParser.parse(result.output, state.config)
                            .onSuccess {
                                setAiProgress(aiProgress?.copy(
                                    steps = aiProgress?.steps.orEmpty() + "本地校验通过，即将进入导入预览",
                                    finished = true
                                ))
                                onMessage(result.routeMessage)
                                onAiParsed(it)
                            }
                            .onFailure {
                                setAiProgress(aiProgress?.copy(
                                    steps = aiProgress?.steps.orEmpty() + "本地校验失败",
                                    error = it.message ?: "AI 返回内容无法解析",
                                    finished = true
                                ))
                                onMessage(it.message ?: "AI 返回内容无法解析")
                            }
                    }
                    .onFailure {
                        val rawBody = it.aiRawResponseBody().orEmpty()
                        setAiProgress(aiProgress?.copy(
                            steps = aiProgress?.steps.orEmpty() + "AI 请求失败",
                            reasoningOutput = extractAiReasoningForDisplay(rawBody).take(20_000),
                            aiOutput = sanitizeAiOutputForDisplay(rawBody).take(80_000),
                            error = it.message ?: "AI 解析失败",
                            finished = true
                        ))
                        onMessage(it.message ?: "AI 解析失败")
                    }
                aiParsing = false
            }
        }

        fun prepareCapturePreview(capture: EduPageCaptureResult, settings: AiImportSettings, screenMode: Boolean) {
            val supportsVision = settings.profile.supportsVision || settings.profile.capabilities.supportsImageInput
            val pageIssue = inspectEduPageCapture(capture.text)
            val isLoginPage = pageIssue?.step?.contains("登录") == true
            val pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(12_000)
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
            val screenLabel = if (!screenMode && supportsVision) "进入识屏模式" else ""
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + warningStep,
                pageText = pageText,
                screenshotPreviews = capture.screenshots.take(6),
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
                                        maxScreenshots = 4,
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
                                context.startActivity(Intent(context, AiEduImportProgressActivity::class.java))
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
                            context.startActivity(Intent(context, AiEduImportProgressActivity::class.java))
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
        context.startActivity(Intent(context, AiEduImportProgressActivity::class.java))
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
                pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(12_000)
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
        val target = webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        target.evaluateJavascript(AiEduPageExtractScript) { encoded ->
            runCatching { inspectEduPageCapture(decodeAiEduPageSnapshot(encoded)) }
                .getOrNull()
                ?.let { onMessage("${it.message}；仍将尝试执行原有拾光导入脚本。") }
        }
        runCatching { ShiguangWarehouse.loadScript(context, adapter) }
            .onSuccess {
                val completionCountAtStart = bridge.taskCompletionCount()
                onMessage("已加载拾光仓库脚本，正在执行导入")
                target.evaluateJavascript(
                    """
                    console.log('SleepDown bridge check', !!window.AndroidBridgePromise, typeof window.AndroidBridgePromise?.showAlert, typeof window.AndroidBridge?.notifyTaskCompletion);
                    try { $it } catch (e) { console.error('SleepDown import script error', e && (e.stack || e.message || e)); throw e; }
                    """.trimIndent(),
                    null
                )
                scope.launch {
                    delay(10_000)
                    if (bridge.taskCompletionCount() == completionCountAtStart) {
                        onMessage("拾光适配器暂未返回课程数据，可以点击 AI 兜底扒页，强制读取当前页面文字后交给 AI 解析。")
                    }
                }
            }
            .onFailure { onMessage("拾光仓库脚本加载失败：${it.message ?: "找不到该学校的导入脚本"}") }
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
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
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
            if (isScreenCapturing) {
                Spacer(Modifier.weight(1f))
                Text(
                    screenCaptureStatus ?: "正在识屏截取",
                    modifier = Modifier
                        .clip(RoundedCornerShape(21.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
            } else {
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
            if (!isScreenCapturing) message?.let {
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
            if (!isScreenCapturing) {
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
                    EduWebNavButton(
                        backdrop = buttonBackdrop,
                        iconRes = R.drawable.ic_refresh,
                        contentDescription = "刷新网页",
                        enabled = true,
                        onClick = {
                            webView?.reload()
                            onMessage("已刷新页面")
                        }
                    )
                    EduWebModeButton(
                        backdrop = buttonBackdrop,
                        label = if (desktopMode) "电脑" else "手机",
                        onClick = {
                            desktopMode = !desktopMode
                            webView?.let { target ->
                                applyEduWebMode(target, desktopMode)
                                target.reload()
                            }
                        }
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 22.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    EduWebImportButton(
                        backdrop = buttonBackdrop,
                        iconRes = R.drawable.ic_ai_import,
                        contentDescription = if (adapter.isAiEduImportTool()) "AI专用教务导入" else "AI兜底扒页",
                        tint = ComposeColor(0xFF0A84FF),
                        alpha = if (aiParsing) 0.58f else 1f,
                        onClick = { runAiEduImport(forceFallback = !adapter.isAiEduImportTool()) }
                    )
                    if (!adapter.isAiEduImportTool()) {
                        EduWebImportButton(
                            backdrop = buttonBackdrop,
                            iconRes = R.drawable.ic_download,
                            contentDescription = "执行原有导入脚本",
                            tint = ComposeColor(0xFF0A84FF),
                            onClick = { runOriginalImportScript() }
                        )
                    }
                }
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
    val topPadding = LocalGlassSettingsContentTopPadding.current ?: 16.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp),
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
fun EduWebModeButton(
    backdrop: Backdrop,
    label: String,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.size(50.dp),
        surfaceColor = ComposeColor.White.copy(alpha = 0.16f),
        contentPadding = PaddingValues(0.dp),
        blurRadius = 8.dp,
        lensHeight = 30.dp,
        lensAmount = 38.dp,
        chromaticAberration = false
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalContentColor.current,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun EduWebImportButton(
    backdrop: Backdrop,
    iconRes: Int,
    contentDescription: String,
    tint: ComposeColor,
    alpha: Float = 1f,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier
            .size(58.dp)
            .graphicsLayer(alpha = alpha),
        tint = tint,
        surfaceColor = tint.copy(alpha = 0.34f),
        contentPadding = PaddingValues(0.dp),
        blurRadius = 8.dp,
        lensHeight = 34.dp,
        lensAmount = 42.dp,
        chromaticAberration = false
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ComposeColor.White,
            modifier = Modifier.size(25.dp)
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
fun ConfirmScheduleScreen(
    draft: ImportDraft,
    warning: String? = null,
    backdrop: Backdrop? = null,
    onCancel: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
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
                        LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onCancel)
                    ),
                    backdrop = backdrop,
                    config = previewDraft.config
                )
            }
        }
    }
}
