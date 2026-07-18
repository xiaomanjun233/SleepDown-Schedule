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
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixOverlayDropdownPreference
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
    val visualConfig = settingsVisualConfig(draft)
    fun applyChange(next: ScheduleConfigEntity) {
        draft = next
        lastSaved = next
        onUpdateConfig(next)
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "general-appearance") {
            GlassPreferenceSection("外观与布局") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "跟随系统",
                        subtitle = "开启后将跟随系统切换浅色或深色模式。",
                        checked = draft.followSystemDarkMode,
                        backdrop = backdrop,
                        onCheckedChange = { applyChange(draft.copy(followSystemDarkMode = it)) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "深色模式",
                        subtitle = if (draft.followSystemDarkMode) "当前由系统外观决定。" else "手动切换应用外观。",
                        checked = draft.darkMode,
                        backdrop = backdrop,
                        enabled = !draft.followSystemDarkMode,
                        onCheckedChange = { applyChange(draft.copy(darkMode = it, followSystemDarkMode = false)) }
                    )
                    SettingsDivider()
                    SettingsDockAlignmentRow(
                        selected = draft.dockAlignment,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(dockAlignment = it)) }
                    )
                    SettingsDivider()
                    SettingsHomeStartModeRow(
                        selected = draft.defaultHomeMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(defaultHomeMode = it)) }
                    )
                }
            }
        }
        item(key = "general-home-system") {
            GlassPreferenceSection("首页与系统") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsDefaultWallpaperRow(
                    selected = draft.defaultWallpaperStyle,
                    backdrop = backdrop,
                    config = visualConfig,
                    onSelected = { applyChange(draft.copy(defaultWallpaperStyle = it)) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "实时活动按钮",
                    subtitle = "在实时活动中显示取消提醒和勿扰按钮。",
                    checked = draft.liveUpdateActionsEnabled,
                    backdrop = backdrop,
                    onCheckedChange = { applyChange(draft.copy(liveUpdateActionsEnabled = it)) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "隐藏后台卡片",
                    subtitle = "返回桌面后，从最近任务列表中移除本应用，更无感。",
                    checked = draft.hideFromRecents,
                    backdrop = backdrop,
                    onCheckedChange = { applyChange(draft.copy(hideFromRecents = it)) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "自动检查更新",
                    subtitle = "每天首次打开应用时检查 Gitee 上的新版本。",
                    checked = draft.autoCheckUpdates,
                    backdrop = backdrop,
                    onCheckedChange = { applyChange(draft.copy(autoCheckUpdates = it)) }
                )
                }
            }
        }
        item(key = "general-diagnostics") {
            GlassPreferenceSection("诊断") {
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
}

@Composable
fun AiImportSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val topPadding = detailContentTopPadding()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassPreferenceSection("模型服务") {
                AiImportSettingsSection(state = state, backdrop = backdrop)
            }
        }
    }
}

@Composable
fun DayAgentSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DayAgentPreferences.isEnabled(context)) }
    var dailyAiEnabled by remember { mutableStateOf(DayAgentPreferences.isDailyAiEnabled(context)) }
    var weatherEnabled by remember { mutableStateOf(DayAgentPreferences.isWeatherEnabled(context)) }
    val topPadding = detailContentTopPadding()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassPreferenceSection("今日助手") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "今日 Agent",
                        "仅在日视图的今天显示。倒计时与课程状态由本地计算；个性化文案每天最多自动调用一次已配置的 AI。"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "启用今日 Agent",
                        subtitle = "显示课程、空档、天气与问答入口。",
                        checked = enabled,
                        backdrop = backdrop,
                        onCheckedChange = {
                            enabled = it
                            DayAgentPreferences.setEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "每日 AI 个性化文案",
                        subtitle = "关闭后完全使用本地模板，不自动调用 AI。",
                        checked = dailyAiEnabled,
                        backdrop = backdrop,
                        enabled = enabled,
                        onCheckedChange = {
                            dailyAiEnabled = it
                            DayAgentPreferences.saveOptions(context, it, weatherEnabled)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "天气提醒",
                        subtitle = "使用设备粗略位置查询天气。",
                        checked = weatherEnabled,
                        backdrop = backdrop,
                        enabled = enabled,
                        onCheckedChange = {
                            weatherEnabled = it
                            DayAgentPreferences.saveOptions(context, dailyAiEnabled, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AiImportSettingsSection(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(AiImportSettingsStore.load(context)) }
    var selectedProviderId by remember(saved.profile.id) { mutableStateOf(saved.profile.id) }
    var baseUrl by remember(saved.profile.baseUrl) { mutableStateOf(saved.profile.baseUrl) }
    var model by remember(saved.profile.defaultModel) { mutableStateOf(saved.profile.defaultModel) }
    var apiKeyInput by remember(saved.apiKey) { mutableStateOf("") }
    var structuredOutputMode by remember(saved.profile.structuredOutputMode) { mutableStateOf(saved.profile.structuredOutputMode) }
    var inputMode by remember(saved.profile.inputMode) { mutableStateOf(saved.profile.inputMode) }
    var supportsVision by remember(saved.profile.supportsVision) { mutableStateOf(saved.profile.supportsVision) }
    var supportsFileUpload by remember(saved.profile.supportsFileUpload) { mutableStateOf(saved.profile.supportsFileUpload) }
    var supportsPdfDirect by remember(saved.profile.supportsPdfDirect) { mutableStateOf(saved.profile.supportsPdfDirect) }
    var message by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelUsesCustomInput by remember(saved.profile.id) {
        mutableStateOf(AiProviderPresets.modelOptions(saved.profile.id).none { it.model == saved.profile.defaultModel })
    }
    var customModelInput by remember(saved.profile.id) {
        mutableStateOf(
            saved.profile.defaultModel.takeIf { savedModel ->
                AiProviderPresets.modelOptions(saved.profile.id).none { it.model == savedModel }
            }.orEmpty()
        )
    }
    val presets = AiProviderPresets.selectable
    val selectedPreset = presets.firstOrNull { it.id == selectedProviderId } ?: AiProviderPresets.openAI
    val aiDisabled = selectedProviderId == AiProviderPresets.none.id
    val modelOptions = AiProviderPresets.modelOptions(selectedProviderId)
    val modelEditable = modelOptions.isEmpty() || modelUsesCustomInput
    val selectedModelOptionIndex = if (modelUsesCustomInput) {
        modelOptions.size
    } else {
        modelOptions.indexOfFirst { it.model == model }.coerceAtLeast(0)
    }
    val normalizedBaseUrl = normalizeAiBaseUrlForProvider(selectedProviderId, baseUrl)
    val usesOpenAICompatibleSite = selectedProviderId == AiProviderPresets.openAI.id &&
        !normalizedBaseUrl.equals("https://api.openai.com/v1", ignoreCase = true)
    val endpointStyle = if (
        selectedProviderId == AiProviderPresets.openAI.id &&
        !usesOpenAICompatibleSite &&
        inputMode == AiInputMode.RESPONSES_FILE
    ) {
        AiEndpointStyle.RESPONSES
    } else {
        AiEndpointStyle.CHAT_COMPLETIONS
    }
    val profile = selectedPreset.copy(
        baseUrl = normalizedBaseUrl,
        defaultModel = model.trim(),
        providerType = if (usesOpenAICompatibleSite) AiProviderType.OpenAIChatCompatible else selectedPreset.providerType,
        capabilities = selectedPreset.capabilities.copy(
            supportsImageInput = supportsVision,
            supportsPdfFileInput = supportsPdfDirect && !usesOpenAICompatibleSite,
            supportsFileUpload = supportsFileUpload && !usesOpenAICompatibleSite,
            supportsJsonSchema = structuredOutputMode == StructuredOutputMode.JSON_SCHEMA,
            supportsJsonMode = structuredOutputMode != StructuredOutputMode.PROMPT_ONLY
        ),
        endpointStyle = endpointStyle,
        structuredOutputMode = structuredOutputMode,
        inputMode = inputMode,
        supportsVision = supportsVision,
        supportsFileUpload = supportsFileUpload && !usesOpenAICompatibleSite,
        supportsPdfDirect = supportsPdfDirect && !usesOpenAICompatibleSite
    )
    fun reload() {
        saved = AiImportSettingsStore.load(context)
        selectedProviderId = saved.profile.id
        baseUrl = saved.profile.baseUrl
        model = saved.profile.defaultModel
        apiKeyInput = ""
        structuredOutputMode = saved.profile.structuredOutputMode
        inputMode = saved.profile.inputMode
        supportsVision = saved.profile.supportsVision
        supportsFileUpload = saved.profile.supportsFileUpload
        supportsPdfDirect = saved.profile.supportsPdfDirect
        testResult = null
        val savedModelOptions = AiProviderPresets.modelOptions(saved.profile.id)
        val savedUsesPresetModel = savedModelOptions.any { it.model == saved.profile.defaultModel }
        modelUsesCustomInput = savedModelOptions.isEmpty() || !savedUsesPresetModel
        customModelInput = if (savedUsesPresetModel) "" else saved.profile.defaultModel
    }
    fun selectProvider(providerId: String) {
        val providerSettings = AiImportSettingsStore.loadProvider(context, providerId)
        val providerModelOptions = AiProviderPresets.modelOptions(providerSettings.profile.id)
        val providerUsesPresetModel = providerModelOptions.any { it.model == providerSettings.profile.defaultModel }
        saved = providerSettings
        selectedProviderId = providerSettings.profile.id
        baseUrl = providerSettings.profile.baseUrl
        model = providerSettings.profile.defaultModel
        apiKeyInput = ""
        structuredOutputMode = providerSettings.profile.structuredOutputMode
        inputMode = providerSettings.profile.inputMode
        supportsVision = providerSettings.profile.supportsVision
        supportsFileUpload = providerSettings.profile.supportsFileUpload
        supportsPdfDirect = providerSettings.profile.supportsPdfDirect
        testResult = null
        modelUsesCustomInput = providerModelOptions.isEmpty() || !providerUsesPresetModel
        customModelInput = if (providerUsesPresetModel) "" else providerSettings.profile.defaultModel
        providerMenuExpanded = false
    }
    fun save() {
        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
        if (!aiDisabled && (profile.baseUrl.isBlank() || profile.defaultModel.isBlank())) {
            message = "请先填写接口地址和模型名称"
            return
        }
        AiImportSettingsStore.save(context, AiImportSettings(profile, nextKey.takeUnless { aiDisabled }.orEmpty()))
        reload()
        message = if (aiDisabled) "AI 功能已关闭" else "AI 设置已保存"
    }

    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        SettingsInfoRow(
            "AI 设置",
            "配置今日助手、AI 对话、教务课表解析等智能功能共用的模型服务。API Key 按服务商分别加密保存在本机，不会写入课表数据库或诊断日志。选择“无”可停用所有联网 AI 能力，本地课表功能不受影响。"
        )
        AiProviderPickerRow(
            value = selectedPreset.displayName,
            expanded = providerMenuExpanded,
            presets = presets,
            selectedProviderId = selectedProviderId,
            backdrop = backdrop,
            config = state.config,
            onExpandedChange = { providerMenuExpanded = it },
            onSelected = { selectProvider(it) }
        )
        if (aiDisabled) {
            SettingsDivider()
            SettingsInfoRow(
                "AI 功能已停用",
                "今日助手将使用本地时间与课程模板，AI 对话和 AI 教务解析入口不会发起模型请求。已保存的其他服务商 Key 会保留，重新选择后可继续使用。"
            )
        } else {
        SettingsDivider()
        SettingsTextFieldRow("接口地址", baseUrl, { baseUrl = it }, KeyboardType.Uri)
        SettingsDivider()
        SettingsTextFieldRow(
            "模型",
            model,
            {
                if (modelEditable) {
                    model = it
                    customModelInput = it
                    modelUsesCustomInput = true
                }
            },
            enabled = modelEditable
        )
        SettingsDivider()
        if (modelOptions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("常用模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                LiquidOptionTabs(
                    selectedIndex = selectedModelOptionIndex,
                    labels = modelOptions.map { it.label } + "自定义",
                    backdrop = backdrop,
                    config = state.config,
                    width = 320.dp,
                    onSelected = { index ->
                        if (index >= modelOptions.size) {
                            modelUsesCustomInput = true
                            model = customModelInput
                        } else {
                            val nextModel = modelOptions[index.coerceIn(modelOptions.indices)].model
                            modelUsesCustomInput = false
                            model = nextModel
                            if (
                                nextModel.contains("vl", ignoreCase = true) ||
                                nextModel.contains("vision", ignoreCase = true) ||
                                selectedProviderId == AiProviderPresets.kimi.id
                            ) {
                                supportsVision = true
                            }
                        }
                    }
                )
            }
            SettingsDivider()
        }
        SettingsTextFieldRow(
            title = if (saved.apiKey.isBlank()) "API Key" else "API Key（已保存）",
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            keyboardType = KeyboardType.Password
        )
        SettingsDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("结构化输出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            val outputModes = listOf(StructuredOutputMode.JSON_SCHEMA, StructuredOutputMode.JSON_OBJECT, StructuredOutputMode.PROMPT_ONLY)
            LiquidOptionTabs(
                selectedIndex = outputModes.indexOf(structuredOutputMode).coerceAtLeast(0),
                labels = listOf("Schema", "JSON", "Prompt"),
                backdrop = backdrop,
                config = state.config,
                width = 320.dp,
                onSelected = { index -> structuredOutputMode = outputModes[index.coerceIn(outputModes.indices)] }
            )
        }
        SettingsDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("连接测试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(
                    "网络诊断",
                    backdrop,
                    onClick = {
                        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                        testResult = "正在诊断网络..."
                        scope.launch {
                            diagnoseAiProviderNetwork(AiImportSettings(profile, nextKey))
                                .onSuccess { testResult = it }
                                .onFailure { testResult = it.message ?: "网络诊断失败" }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsActionButton(
                    "测试连接",
                    backdrop,
                    onClick = {
                        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                        testResult = "正在测试连接..."
                        scope.launch {
                            testAiProviderConnection(AiImportSettings(profile, nextKey))
                                .onSuccess { testResult = it }
                                .onFailure { testResult = it.message ?: "连接测试失败" }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("失败") || it.contains("请先")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
        SettingsDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsActionButton("保存", backdrop, onClick = { save() }, modifier = Modifier.weight(1f))
            SettingsActionButton(
                "清除 Key",
                backdrop,
                onClick = {
                    AiImportSettingsStore.clearApiKey(context, selectedProviderId)
                    saved = AiImportSettings(profile, "")
                    apiKeyInput = ""
                    message = "API Key 已清除"
                },
                modifier = Modifier.weight(1f),
                destructive = true
            )
        }
        }
        if (aiDisabled) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                SettingsActionButton("保存", backdrop, onClick = { save() }, modifier = Modifier.fillMaxWidth())
            }
        }
        message?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("请先")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AiProviderPickerRow(
    value: String,
    expanded: Boolean,
    presets: List<AiProviderProfile>,
    selectedProviderId: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit
) {
    if (LocalGlassMiuixEnabled.current) {
        val selectedIndex = presets.indexOfFirst { it.id == selectedProviderId }.coerceAtLeast(0)
        MiuixOverlayDropdownPreference(
            items = presets.map { it.displayName },
            selectedIndex = selectedIndex,
            title = "服务商",
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            maxHeight = 318.dp,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = { index ->
                presets.getOrNull(index)?.let { onSelected(it.id) }
            }
        )
        return
    }
    val density = LocalDensity.current
    val popupOffsetY = with(density) { 54.dp.roundToPx() }
    var renderMenu by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(durationMillis = 160),
        label = "ai-provider-arrow"
    )
    LaunchedEffect(expanded) {
        if (expanded) {
            renderMenu = true
            withFrameNanos { }
            menuVisible = true
        } else {
            menuVisible = false
            delay(130)
            renderMenu = false
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onExpandedChange(!expanded) }
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "服务商",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .offset(y = 1.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1.55f)
                    .offset(y = 1.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value.ifBlank { "未设置" },
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "收起服务商" else "展开服务商",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (renderMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, popupOffsetY),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                val shape = RoundedCornerShape(24.dp)
                val contentColor = appPanelForegroundColor(config)
                val menuModifier = Modifier
                    .width(220.dp)
                    .heightIn(max = 318.dp)
                    .zIndex(10f)
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = fadeIn(tween(110)) +
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(150),
                            transformOrigin = TransformOrigin(0.92f, 0f)
                        ) +
                        slideInVertically(tween(150)) { -12 },
                    exit = fadeOut(tween(90)) + scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(110),
                        transformOrigin = TransformOrigin(0.92f, 0f)
                    )
                ) {
                    if (backdrop != null) {
                        LiquidPanel(
                            backdrop = backdrop,
                            modifier = menuModifier,
                            shape = shape,
                            surfaceColor = if (appUsesDarkTheme(config)) {
                                ComposeColor(0xFF1C1C1E).copy(alpha = 0.86f)
                            } else {
                                ComposeColor.White.copy(alpha = 0.94f)
                            }
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                Column(
                                    Modifier
                                        .padding(vertical = 6.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    presets.forEachIndexed { index, preset ->
                                        AiProviderMenuRow(
                                            name = preset.displayName,
                                            selected = preset.id == selectedProviderId,
                                            onClick = { onSelected(preset.id) }
                                        )
                                        if (index != presets.lastIndex) SettingsDivider()
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = menuModifier,
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                Modifier
                                    .padding(vertical = 6.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                presets.forEachIndexed { index, preset ->
                                    AiProviderMenuRow(
                                        name = preset.displayName,
                                        selected = preset.id == selectedProviderId,
                                        onClick = { onSelected(preset.id) }
                                    )
                                    if (index != presets.lastIndex) SettingsDivider()
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
private fun AiProviderMenuRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
        if (selected) {
            Text(
                "已选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
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

internal object DiagnosticLogCapture {
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

internal fun shareDiagnosticLog(context: Context, uri: Uri) {
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
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = "Dock 栏位置",
            summary = "调整首页底部切换栏对齐方式",
            controlWidth = 132.dp
        ) {
            DockAlignmentTabs(selected, backdrop, config, onSelected)
        }
        return
    }
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
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = "默认首页视图",
            summary = "选择每次打开应用时显示日视图或周视图",
            controlWidth = 104.dp
        ) {
            LiquidOptionTabs(
                selectedIndex = if (selected == HomeStartMode.DAY) 0 else 1,
                labels = listOf("日", "周"),
                backdrop = backdrop,
                config = config,
                width = 104.dp,
                onSelected = { index -> onSelected(if (index == 0) HomeStartMode.DAY else HomeStartMode.WEEK) }
            )
        }
        return
    }
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
    onSelected: (Int) -> Unit,
    onSelectionSettled: (Int) -> Unit = {}
) {
    if (backdrop != null) {
        CompositionLocalProvider(LocalContentColor provides appPanelForegroundColor(config)) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex.coerceIn(labels.indices) },
                onTabSelected = { index -> onSelected(index.coerceIn(labels.indices)) },
                backdrop = backdrop,
                tabsCount = labels.size,
                modifier = Modifier.width(width),
                onSelectionSettled = { index -> onSelectionSettled(index.coerceIn(labels.indices)) },
                containerHeight = 42.dp,
                indicatorHeight = 34.dp,
                horizontalPadding = 4.dp,
                blurRadius = 4.dp,
                containerAlpha = 0.4f,
                lensHeight = 24.dp,
                lensAmount = 24.dp,
                indicatorWidthOverflow = 0.dp,
                indicatorHeightOverflow = 0.dp,
                pressedContentScale = 1.04f,
                chromaticAberrationEnabled = true,
                isLightThemeOverride = !appUsesDarkTheme(config),
                useOfficialGlassParameters = true
            ) {
                labels.forEachIndexed { index, label ->
                    LiquidBottomTab(onClick = { onSelected(index) }) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                SettingsFallbackChip(label, selectedIndex == index) {
                    onSelected(index)
                    onSelectionSettled(index)
                }
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
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = "默认壁纸",
            summary = "未设置自定义壁纸时使用",
            controlWidth = 140.dp
        ) {
            DefaultWallpaperTabs(selected = selected, backdrop = backdrop, config = config, onSelected = onSelected)
        }
        return
    }
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
fun SettingsGroup(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    surfaceColorOverride: ComposeColor? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val miuixLayout = LocalGlassMiuixEnabled.current
    val shape = RoundedCornerShape(if (miuixLayout) 24.dp else 30.dp)
    val darkTheme = appUsesDarkTheme(config)
    val contentColor = if (darkTheme) ComposeColor.White else ComposeColor(0xFF111111)
    if (miuixLayout) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .then(surfaceModifier)
                    .clip(shape)
                    .background(
                        surfaceColorOverride
                            ?: if (darkTheme) ComposeColor(0xFF1C1C1E) else ComposeColor(0xFFF7F7F7)
                    ),
                content = content
            )
        }
        return
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            surfaceColor = if (darkTheme) ComposeColor(0xFF1C1C1E).copy(alpha = 0.78f) else ComposeColor.White.copy(alpha = 0.94f)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(Modifier.padding(vertical = if (miuixLayout) 0.dp else 4.dp), content = content)
            }
        }
    } else {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .clip(shape)
                    .background(if (darkTheme) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                    .padding(vertical = if (miuixLayout) 0.dp else 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            summary = subtitle,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                if (badgeText != null) {
                    Text(
                        badgeText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            },
            onClick = onClick
        )
        return
    }
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
        if (badgeText != null) {
            Text(
                badgeText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(">", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, backdrop: Backdrop?, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = title,
            summary = subtitle,
            controlWidth = 64.dp,
            controlHeight = 28.dp,
            enabled = enabled
        ) {
            if (enabled) {
                LiquidControlToggle(checked, onCheckedChange, backdrop)
            } else {
                LiquidControlToggle(checked, {}, backdrop)
            }
        }
        return
    }
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
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            summary = subtitle,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = buttonText,
                    onClick = onClick,
                    role = DialogButtonRole.Confirm,
                    iconRes = iconRes
                )
            }
        )
        return
    }
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
    if (LocalGlassMiuixEnabled.current) return
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
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    ),
                    modifier = Modifier.width(170.dp)
                )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        return
    }
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
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            endActions = {
                Text(
                    value.ifBlank { "未设置" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        return
    }
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
    var showPicker by remember { mutableStateOf(false) }
    val initialDate = remember(value, showPicker) { parseScheduleDate(value) ?: LocalDate.now() }
    var pickerYear by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.year) }
    var pickerMonth by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.monthValue) }
    var pickerDay by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.dayOfMonth) }
    SettingsPickerValueRow(
        title = title,
        value = value,
        enabled = enabled,
        onClick = { showPicker = true }
    )
    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = showPicker,
        title = title,
        startAction = {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "取消",
                onClick = { showPicker = false },
                minWidth = 64.dp,
                minHeight = 38.dp
            )
        },
        endAction = {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "确定",
                onClick = {
                    val maxDay = java.time.YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
                    onValueChange(formatScheduleDate(LocalDate.of(pickerYear, pickerMonth, pickerDay.coerceAtMost(maxDay))))
                    showPicker = false
                },
                minWidth = 64.dp,
                minHeight = 38.dp
            )
        },
        onDismissRequest = { showPicker = false },
        modifier = Modifier.heightIn(max = 330.dp)
    ) {
        val maxDay = java.time.YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
        LaunchedEffect(maxDay) {
            if (pickerDay > maxDay) pickerDay = maxDay
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = pickerYear,
                onValueChange = { pickerYear = it },
                range = 2000..2100,
                visibleItemCount = 3,
                label = { "${it}年" },
                modifier = Modifier.weight(1.25f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = pickerMonth,
                onValueChange = { pickerMonth = it },
                range = 1..12,
                visibleItemCount = 3,
                label = { "${it}月" },
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = pickerDay.coerceAtMost(maxDay),
                onValueChange = { pickerDay = it },
                range = 1..maxDay,
                visibleItemCount = 3,
                label = { "${it}日" },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            summary = body,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        return
    }
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
fun GlassPreferenceCategory(text: String, modifier: Modifier = Modifier) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixSmallTitle(
            text = text,
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(start = 6.dp, top = 8.dp, bottom = 8.dp)
        )
    } else {
        Text(
            text = text,
            modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GlassMiuixInteractivePreference(
    title: String,
    summary: String? = null,
    controlWidth: Dp,
    controlHeight: Dp = 42.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
    ) {
        MiuixBasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = controlWidth + 12.dp),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(controlWidth, controlHeight),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun GlassPreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        GlassPreferenceCategory(title)
        content()
    }
}

@Composable
fun SettingsChoiceRow(title: String, selected: NotificationMode, backdrop: Backdrop?, config: ScheduleConfigEntity, onSelected: (NotificationMode) -> Unit) {
    if (LocalGlassMiuixEnabled.current) {
        val modes = NotificationMode.entries
        GlassMiuixInteractivePreference(
            title = title,
            controlWidth = 188.dp
        ) {
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
        return
    }
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
    if (LocalGlassMiuixEnabled.current) {
        Column(Modifier.fillMaxWidth()) {
            MiuixBasicComponent(
                title = "岛上缩略态",
                summary = "可显示上课地点、剩余时间或短标签；原生安卓建议选择短标签或倒计时。",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(268.dp, 42.dp)) {
                    LiquidOptionTabs(
                        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
                        labels = labels,
                        backdrop = backdrop,
                        config = config,
                        width = 268.dp,
                        onSelected = { index -> onSelected(options[index.coerceIn(options.indices)]) }
                    )
                }
            }
        }
        return
    }
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
        if (dirty) {
            item(key = "notification-save-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            item(key = "notification-options") {
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
            item(key = "notification-compatibility") {
                Text(
                    "实时活动目前仅支持原生安卓系统、ColorOS16、HyperOS 3.0.300以上版本、荣耀 MagicOS 10。原生安卓机型请选择短标签或者倒计时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            item(key = "notification-permissions") {
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
                            openBatteryOptimizationSettings(appContext)
                        }, modifier = Modifier.weight(1f))
                        SettingsActionButton("自启动设置", backdrop, onClick = {
                            openKeepAliveSettings(appContext)
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
            item(key = "notification-preview") { SettingsActionButton("测试实时活动", backdrop, onClick = onPreviewLiveUpdate, modifier = Modifier.fillMaxWidth()) }
        }
        error?.let {
            item(key = "notification-error") { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
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
                    "课表详细设置",
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

    LaunchedEffect(
        section,
        leadMinutes,
        notificationsEnabled,
        notificationMode,
        liveUpdateChipTextMode
    ) {
        if (section == SettingsSection.Notifications && computeDirty()) {
            delay(250)
            saveConfigDraft()
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
        dirty = dirty && section != SettingsSection.Notifications,
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

@SuppressLint("BatteryLife")
private fun openBatteryOptimizationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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

internal fun weekdayLabel(weekday: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]
