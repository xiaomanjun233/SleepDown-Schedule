package com.example.courseschedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DatePickerDialog
import android.app.Application
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
import androidx.core.net.toUri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.os.Build
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.SolidColor
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import java.time.LocalTime
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URL
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
    var appIconMode by remember(context) {
        mutableStateOf(AppIconManager.currentMode(context))
    }
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
                    SettingsAppIconModeRow(
                        selected = appIconMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { mode ->
                            appIconMode = mode
                            AppIconManager.setMode(context, mode)
                        }
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
                    subtitle = "以任意方式离开应用后，都从最近任务列表中隐藏本应用。",
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
    }
}

@Composable
fun AiImportSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {}
) {
    val topPadding = detailContentTopPadding()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassPreferenceSection("模型服务") {
                AiImportSettingsSection(
                    state = state,
                    backdrop = backdrop,
                    exitCommitRequest = exitCommitRequest,
                    onExitCommitFinished = onExitCommitFinished
                )
            }
        }
    }
}

@Composable
fun DayAgentSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DayAgentPreferences.isEnabled(context)) }
    var weatherEnabled by remember { mutableStateOf(DayAgentPreferences.isWeatherEnabled(context)) }
    var memoryEnabled by remember { mutableStateOf(DayAgentPreferences.isMemoryEnabled(context)) }
    var memoryText by remember { mutableStateOf(DayAgentPreferences.memory(context)) }
    var memoryDraft by remember { mutableStateOf(memoryText) }
    var showMemoryEditor by remember { mutableStateOf(false) }
    var historyRetentionDays by remember { mutableIntStateOf(AiImportHistoryStore.retentionDays(context)) }
    var historyMessage by remember { mutableStateOf<String?>(null) }
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val topPadding = detailContentTopPadding()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassPreferenceSection("今日助手") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "今日助手",
                        "仅在日视图的今天显示，集中展示课程状态、倒计时、天气与预警；点击卡片可进入助手对话。"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "启用今日助手",
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
                        title = "天气提醒",
                        subtitle = "使用设备粗略位置查询天气。",
                        checked = weatherEnabled,
                        backdrop = backdrop,
                        enabled = enabled,
                        onCheckedChange = {
                            weatherEnabled = it
                            DayAgentPreferences.saveOptions(
                                context,
                                DayAgentPreferences.isDailyAiEnabled(context),
                                it
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "助手记忆",
                        subtitle = "跨天记住你明确表达的长期偏好与背景。",
                        checked = memoryEnabled,
                        backdrop = backdrop,
                        onCheckedChange = {
                            memoryEnabled = it
                            DayAgentPreferences.setMemoryEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "查看与编辑记忆",
                        subtitle = memoryText
                            .lineSequence()
                            .firstOrNull { it.isNotBlank() }
                            ?.take(42)
                            ?: "当前没有已保存的记忆",
                        badgeText = if (memoryEnabled) "已启用" else "已关闭",
                        onClick = {
                            memoryDraft = DayAgentPreferences.memory(context)
                            showMemoryEditor = true
                        }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("导入历史") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    MiuixOverlayDropdownPreference(
                        items = listOf("7 天后", "30 天后", "90 天后", "手动删除"),
                        selectedIndex = AiImportHistoryStore.retentionOptions.indexOf(historyRetentionDays).coerceAtLeast(1),
                        title = "自动清理导入历史",
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        maxHeight = 260.dp,
                        onExpandedChange = {},
                        onSelectedIndexChange = { index ->
                            historyRetentionDays = AiImportHistoryStore.retentionOptions[
                                index.coerceIn(AiImportHistoryStore.retentionOptions.indices)
                            ]
                            AiImportHistoryStore.setRetentionDays(context, historyRetentionDays)
                            historyMessage = if (historyRetentionDays == 0) {
                                "导入历史将由你手动删除"
                            } else {
                                "导入历史将在 $historyRetentionDays 天后自动删除"
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "清空导入历史",
                        subtitle = "删除本机保存的最近导入上下文，不影响已经导入的课表。",
                        buttonText = "清空",
                        iconRes = R.drawable.ic_delete_history,
                        backdrop = backdrop,
                        onClick = {
                            AiImportHistoryStore.clear(context)
                            historyMessage = "导入历史已清空"
                        }
                    )
                    historyMessage?.let {
                        SettingsDivider()
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showMemoryEditor,
        title = "助手记忆",
        onDismissRequest = { showMemoryEditor = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = popupBackdrop,
            config = state.config,
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "这里保存的是助手可跨天使用的简短长期记忆。你可以直接修改；关闭记忆后内容会保留，但不会再注入对话或由助手更新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MaterialOutlinedTextField(
                value = memoryDraft,
                onValueChange = { memoryDraft = it.take(1200) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp),
                placeholder = {
                    Text("例如：默认只修改本周；跨校区课程之间预留 30 分钟。")
                },
                minLines = 6,
                maxLines = 10,
                shape = RoundedCornerShape(24.dp)
            )
            Text(
                "${memoryDraft.length}/1200",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickSheetLiquidAction(
                    label = "清空",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                ) {
                    memoryDraft = ""
                }
                QuickSheetLiquidAction(
                    label = "取消",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                ) {
                    showMemoryEditor = false
                }
                QuickSheetLiquidAction(
                    label = "保存",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                ) {
                    DayAgentPreferences.saveMemory(context, memoryDraft)
                    memoryText = DayAgentPreferences.memory(context)
                    memoryDraft = memoryText
                    showMemoryEditor = false
                }
            }
        }
    }
}

@Composable
fun AiImportSettingsSection(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(AiImportSettingsStore.load(context)) }
    var selectedProviderId by remember(saved.profile.id) { mutableStateOf(saved.profile.id) }
    var customProviderName by remember(saved.profile.id) { mutableStateOf(saved.profile.displayName) }
    var baseUrl by remember(saved.profile.baseUrl) { mutableStateOf(saved.profile.baseUrl) }
    var model by remember(saved.profile.defaultModel) { mutableStateOf(saved.profile.defaultModel) }
    var availableModelsText by remember(saved.profile.id) {
        mutableStateOf(
            saved.profile.availableModels
                .ifEmpty { AiProviderPresets.modelOptions(saved.profile.id).map(AiModelOption::model) }
                .joinToString("\n")
        )
    }
    var responsesEnabled by remember(saved.profile.id) {
        mutableStateOf(saved.profile.endpointStyle == AiEndpointStyle.RESPONSES)
    }
    var reasoningEffort by remember(saved.profile.id) { mutableStateOf(saved.profile.reasoningEffort) }
    var apiKeyInput by remember(saved.apiKey) { mutableStateOf("") }
    var structuredOutputMode by remember(saved.profile.structuredOutputMode) { mutableStateOf(saved.profile.structuredOutputMode) }
    var inputMode by remember(saved.profile.inputMode) { mutableStateOf(saved.profile.inputMode) }
    var supportsVision by remember(saved.profile.supportsVision) { mutableStateOf(saved.profile.supportsVision) }
    var supportsFileUpload by remember(saved.profile.supportsFileUpload) { mutableStateOf(saved.profile.supportsFileUpload) }
    var supportsPdfDirect by remember(saved.profile.supportsPdfDirect) { mutableStateOf(saved.profile.supportsPdfDirect) }
    var message by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showDeleteProviderConfirm by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var providerListRevision by remember { mutableIntStateOf(0) }
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
    val presets = remember(providerListRevision) { AiImportSettingsStore.selectableProfiles(context) }
    val selectedPreset = saved.profile.takeIf { it.id == selectedProviderId }
        ?: presets.firstOrNull { it.id == selectedProviderId }
        ?: AiProviderPresets.byId(selectedProviderId)
    val isCustomProvider = AiProviderPresets.isCustomId(selectedProviderId)
    val isManagedFreeProvider = AiProviderPresets.isManagedFreeId(selectedProviderId)
    val effectiveCustomProviderName = customProviderName.trim().ifBlank { selectedPreset.displayName }
    val customProviderDisplayName = effectiveCustomProviderName.ifBlank { "未命名自定义接口" }
    val pickerPresets = if (isCustomProvider) {
        val updatedPresets = presets.map { preset ->
            if (preset.id == selectedProviderId) preset.copy(displayName = customProviderDisplayName) else preset
        }
        if (updatedPresets.any { it.id == selectedProviderId }) {
            updatedPresets
        } else {
            updatedPresets + selectedPreset.copy(displayName = customProviderDisplayName)
        }
    } else {
        presets
    }
    val aiDisabled = selectedProviderId == AiProviderPresets.none.id
    val configuredModelIds = parseAiModelIdList(availableModelsText, model)
    val modelProfile = (if (isManagedFreeProvider) AiProviderPresets.dailyFree else selectedPreset).copy(
        defaultModel = model.trim(),
        capabilities = selectedPreset.capabilities.copy(supportsImageInput = supportsVision),
        supportsVision = supportsVision,
        availableModels = configuredModelIds
    )
    val modelOptions = AiProviderPresets.modelOptions(modelProfile)
    val modelEditable = !isManagedFreeProvider && (modelOptions.isEmpty() || modelUsesCustomInput)
    val selectedModelOptionIndex = if (modelUsesCustomInput) {
        modelOptions.size
    } else {
        modelOptions.indexOfFirst { it.model == model }.coerceAtLeast(0)
    }
    val onModelOptionSelected: (Int) -> Unit = { index ->
        if (index >= modelOptions.size) {
            modelUsesCustomInput = true
            model = customModelInput
        } else {
            val selectedOption = modelOptions[index.coerceIn(modelOptions.indices)]
            val nextModel = selectedOption.model
            modelUsesCustomInput = false
            model = nextModel
            supportsVision = selectedOption.supportsImageInput
        }
    }
    val normalizedBaseUrl = normalizeAiBaseUrlForProvider(selectedProviderId, baseUrl)
    val usesOpenAICompatibleSite = selectedProviderId == AiProviderPresets.openAI.id &&
        !normalizedBaseUrl.equals("https://api.openai.com/v1", ignoreCase = true)
    val modelSupportsResponses = AiProviderPresets.supportsResponses(modelProfile)
    val reasoningOptions = AiProviderPresets.reasoningEfforts(modelProfile)
    val effectiveReasoningEffort = reasoningEffort.takeIf { it in reasoningOptions }
        ?: reasoningOptions.firstOrNull()
        ?: AiReasoningEffort.MEDIUM
    val endpointStyle = if (responsesEnabled && modelSupportsResponses) {
        AiEndpointStyle.RESPONSES
    } else AiEndpointStyle.CHAT_COMPLETIONS
    val selectedModelSupportsVision = modelOptions.firstOrNull {
        it.model.equals(model.trim(), ignoreCase = true)
    }?.supportsImageInput ?: supportsVision
    val editableProfile = selectedPreset.copy(
        displayName = if (isCustomProvider) effectiveCustomProviderName else selectedPreset.displayName,
        baseUrl = normalizedBaseUrl,
        defaultModel = model.trim(),
        providerType = selectedPreset.providerType,
        capabilities = selectedPreset.capabilities.copy(
            supportsImageInput = selectedModelSupportsVision,
            supportsPdfFileInput = supportsPdfDirect && !usesOpenAICompatibleSite,
            supportsFileUpload = supportsFileUpload,
            supportsJsonSchema = structuredOutputMode == StructuredOutputMode.JSON_SCHEMA,
            supportsJsonMode = structuredOutputMode != StructuredOutputMode.PROMPT_ONLY,
            supportsResponses = selectedPreset.capabilities.supportsResponses
        ),
        endpointStyle = endpointStyle,
        structuredOutputMode = structuredOutputMode,
        inputMode = inputMode,
        supportsVision = selectedModelSupportsVision,
        supportsFileUpload = supportsFileUpload,
        supportsPdfDirect = supportsPdfDirect && !usesOpenAICompatibleSite,
        availableModels = configuredModelIds,
        reasoningEffort = effectiveReasoningEffort
    )
    val profile = if (isManagedFreeProvider) {
        AiProviderPresets.dailyFree.copy(reasoningEffort = effectiveReasoningEffort)
    } else {
        editableProfile
    }
    fun hasCustomProviderDraft(apiKey: String): Boolean = customProviderDraftHasContent(
        name = customProviderName,
        baseUrl = baseUrl,
        model = model.takeUnless {
            customProviderName.isBlank() && baseUrl.isBlank() && apiKey.isBlank() &&
                configuredModelIds == AiProviderPresets.codexCompatibleModelIds
        }.orEmpty(),
        apiKey = apiKey
    )
    fun reload() {
        saved = AiImportSettingsStore.load(context)
        selectedProviderId = saved.profile.id
        customProviderName = saved.profile.displayName
        baseUrl = saved.profile.baseUrl
        model = saved.profile.defaultModel
        availableModelsText = saved.profile.availableModels
            .ifEmpty { AiProviderPresets.modelOptions(saved.profile.id).map(AiModelOption::model) }
            .joinToString("\n")
        responsesEnabled = saved.profile.endpointStyle == AiEndpointStyle.RESPONSES
        reasoningEffort = saved.profile.reasoningEffort
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
        // Preserve the current provider draft before switching. In particular, the
        // custom compatible endpoint must not fall back to its empty preset whenever
        // the user temporarily selects another provider.
        val outgoingKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            !isCustomProvider ||
            hasCustomProviderDraft(outgoingKey)
        ) {
            AiImportSettingsStore.saveProvider(context, AiImportSettings(profile, outgoingKey))
        }
        providerListRevision++
        val providerSettings = AiImportSettingsStore.loadProvider(context, providerId)
        val providerModelOptions = AiProviderPresets.modelOptions(providerSettings.profile.id)
        val providerUsesPresetModel = providerModelOptions.any { it.model == providerSettings.profile.defaultModel }
        saved = providerSettings
        selectedProviderId = providerSettings.profile.id
        customProviderName = providerSettings.profile.displayName
        baseUrl = providerSettings.profile.baseUrl
        model = providerSettings.profile.defaultModel
        availableModelsText = providerSettings.profile.availableModels
            .ifEmpty { AiProviderPresets.modelOptions(providerSettings.profile.id).map(AiModelOption::model) }
            .joinToString("\n")
        responsesEnabled = providerSettings.profile.endpointStyle == AiEndpointStyle.RESPONSES
        reasoningEffort = providerSettings.profile.reasoningEffort
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
    fun addCustomProvider() {
        val outgoingKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            !isCustomProvider ||
            hasCustomProviderDraft(outgoingKey)
        ) {
            AiImportSettingsStore.saveProvider(context, AiImportSettings(profile, outgoingKey))
        }
        val newProfile = AiImportSettingsStore.createCustomProvider()
        saved = AiImportSettings(newProfile, "")
        selectedProviderId = newProfile.id
        customProviderName = ""
        baseUrl = ""
        model = newProfile.defaultModel
        availableModelsText = newProfile.availableModels.joinToString("\n")
        responsesEnabled = false
        reasoningEffort = newProfile.reasoningEffort
        apiKeyInput = ""
        structuredOutputMode = newProfile.structuredOutputMode
        inputMode = newProfile.inputMode
        supportsVision = newProfile.supportsVision
        supportsFileUpload = newProfile.supportsFileUpload
        supportsPdfDirect = newProfile.supportsPdfDirect
        testResult = null
        modelUsesCustomInput = true
        customModelInput = newProfile.defaultModel
        providerMenuExpanded = false
        providerListRevision++
        message = "填写接口名称或连接信息后才会加入列表"
    }
    fun save(showMessage: Boolean = true): Boolean {
        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
        if (!aiDisabled && (profile.baseUrl.isBlank() || profile.defaultModel.isBlank())) {
            message = "请先填写接口地址和模型名称"
            return false
        }
        AiImportSettingsStore.save(context, AiImportSettings(profile, nextKey.takeUnless { aiDisabled }.orEmpty()))
        reload()
        if (showMessage) {
            message = if (aiDisabled) "AI 功能已关闭" else "AI 设置已保存"
        }
        return true
    }
    fun persistForExit() {
        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            isCustomProvider &&
            !hasCustomProviderDraft(nextKey)
        ) {
            return
        }
        val nextSettings = AiImportSettings(
            profile,
            nextKey.takeUnless { aiDisabled }.orEmpty()
        )
        if (aiDisabled || (profile.baseUrl.isNotBlank() && profile.defaultModel.isNotBlank())) {
            AiImportSettingsStore.save(context, nextSettings)
        } else {
            // Preserve incomplete input as a provider-scoped draft without making
            // the invalid profile the active service.
            AiImportSettingsStore.saveProvider(context, nextSettings)
        }
    }
    val latestPersistForExit by rememberUpdatedState<(Unit) -> Unit>({ persistForExit() })
    DisposableEffect(Unit) {
        // Saving during disposal leaves Android's Activity back dispatcher free to
        // drive the predictive-back gesture instead of waiting for a save callback.
        onDispose { latestPersistForExit(Unit) }
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0) {
            persistForExit()
            onExitCommitFinished(true)
        }
    }

    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        SettingsInfoRow(
            "AI 设置",
            "配置今日助手、AI 对话、教务课表解析等智能功能共用的模型服务。API Key 按服务商分别加密保存在本机，不会写入课表数据库或诊断日志。选择“无”可停用所有联网 AI 能力，本地课表功能不受影响。"
        )
        AiProviderPickerRow(
            value = if (isCustomProvider) customProviderDisplayName else selectedPreset.displayName,
            expanded = providerMenuExpanded,
            presets = pickerPresets,
            selectedProviderId = selectedProviderId,
            backdrop = backdrop,
            config = state.config,
            onExpandedChange = { providerMenuExpanded = it },
            onSelected = { selectProvider(it) },
            onAddCustomProvider = { addCustomProvider() }
        )
        if (aiDisabled) {
            SettingsDivider()
            SettingsInfoRow(
                "AI 功能已停用",
                "今日助手将使用本地时间与课程模板，AI 对话和 AI 教务解析入口不会发起模型请求。已保存的其他服务商 Key 会保留，重新选择后可继续使用。"
            )
        } else {
        if (isManagedFreeProvider) {
            SettingsDivider()
            SettingsInfoRow(
                "每日免费 AI",
                "每天由 SleepDown 提供免费的 AI 额度，用于 AI 功能的使用。该额度池由所有用户共享，用完即止。"
            )
            SettingsDivider()
            MiuixOverlayDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
            SettingsDivider()
        } else {
        SettingsDivider()
        if (isCustomProvider) {
            SettingsTextFieldRow(
                title = "接口名称",
                value = customProviderName,
                onValueChange = { customProviderName = it },
                placeholder = "自定义接口"
            )
            SettingsDivider()
            AiCompatibleModelsEditor(
                value = availableModelsText,
                onValueChange = { next ->
                    availableModelsText = next
                    val ids = parseAiModelIdList(next, "")
                    if (ids.isNotEmpty() && ids.none { it.equals(model, ignoreCase = true) }) {
                        model = ids.first()
                        modelUsesCustomInput = false
                    }
                }
            )
            SettingsDivider()
        }
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
            val modelLabels = modelOptions.map { it.label } + "自定义"
            if (modelLabels.size > 3) {
                MiuixOverlayDropdownPreference(
                    items = modelLabels,
                    selectedIndex = selectedModelOptionIndex,
                    title = "常用模型",
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    maxHeight = 318.dp,
                    onExpandedChange = {},
                    onSelectedIndexChange = onModelOptionSelected
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("常用模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    LiquidOptionTabs(
                        selectedIndex = selectedModelOptionIndex,
                        labels = modelLabels,
                        backdrop = backdrop,
                        config = state.config,
                        width = 320.dp,
                        onSelected = onModelOptionSelected
                    )
                }
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
        SettingsToggleRow(
            title = "Responses API",
            subtitle = if (modelSupportsResponses) {
                "开启后 AI 导入与今日助手的所有请求统一使用 /responses。"
            } else {
                "当前模型没有已知的 Responses 能力，将继续使用 Chat Completions。"
            },
            checked = responsesEnabled && modelSupportsResponses,
            backdrop = backdrop,
            enabled = modelSupportsResponses,
            onCheckedChange = { responsesEnabled = it }
        )
        SettingsDivider()
        if (responsesEnabled && modelSupportsResponses) {
            MiuixOverlayDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
            SettingsDivider()
        }
        if (isCustomProvider) {
            SettingsToggleRow(
                title = "文件上传",
                subtitle = "允许今日助手向该兼容接口发送图片附件。",
                checked = supportsFileUpload,
                backdrop = backdrop,
                onCheckedChange = {
                    supportsFileUpload = it
                    supportsVision = it
                }
            )
            SettingsDivider()
        }
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
        }
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
        if (!isManagedFreeProvider) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                if (isCustomProvider) {
                    SettingsActionButton(
                        "删除接口",
                        backdrop,
                        onClick = { showDeleteProviderConfirm = true },
                        modifier = Modifier.weight(1f),
                        destructive = true
                    )
                }
            }
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
    if (showDeleteProviderConfirm) {
        LiquidAlertDialog(
            title = "删除自定义接口？",
            message = "将删除此接口的名称、地址、模型和本机保存的 API Key，不会影响其他接口。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) {
                    showDeleteProviderConfirm = false
                },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    val deletedName = effectiveCustomProviderName
                    AiImportSettingsStore.deleteCustomProvider(context, selectedProviderId)
                    providerListRevision++
                    showDeleteProviderConfirm = false
                    reload()
                    message = "已删除 $deletedName"
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showDeleteProviderConfirm = false }
        )
    }
}

private fun parseAiModelIdList(value: String, defaultModel: String): List<String> =
    (value.split(Regex("[\\r\\n,，;；]+")) + defaultModel)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)

@Composable
private fun AiCompatibleModelsEditor(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("兼容站模型列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            "每行一个模型 ID。右下角菜单会从这里读取，第一项作为新建接口的默认模型。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp, max = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 10
        )
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
    onSelected: (String) -> Unit,
    onAddCustomProvider: () -> Unit
) {
    if (LocalGlassMiuixEnabled.current) {
        val selectedIndex = presets.indexOfFirst { it.id == selectedProviderId }.coerceAtLeast(0)
        MiuixOverlayDropdownPreference(
            items = presets.map { it.displayName } + "新增自定义接口",
            selectedIndex = selectedIndex,
            title = "服务商",
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            maxHeight = 318.dp,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = { index ->
                if (index == presets.size) {
                    onAddCustomProvider()
                } else {
                    presets.getOrNull(index)?.let { onSelected(it.id) }
                }
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
                                    presets.forEach { preset ->
                                        AiProviderMenuRow(
                                            name = preset.displayName,
                                            selected = preset.id == selectedProviderId,
                                            onClick = { onSelected(preset.id) }
                                        )
                                        SettingsDivider()
                                    }
                                    AiProviderMenuRow(
                                        name = "新增自定义接口",
                                        selected = false,
                                        accent = true,
                                        onClick = onAddCustomProvider
                                    )
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
                                presets.forEach { preset ->
                                    AiProviderMenuRow(
                                        name = preset.displayName,
                                        selected = preset.id == selectedProviderId,
                                        onClick = { onSelected(preset.id) }
                                    )
                                    SettingsDivider()
                                }
                                AiProviderMenuRow(
                                    name = "新增自定义接口",
                                    selected = false,
                                    accent = true,
                                    onClick = onAddCustomProvider
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
private fun AiProviderMenuRow(
    name: String,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
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
            fontWeight = if (selected || accent) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected || accent) MaterialTheme.colorScheme.primary else LocalContentColor.current
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

@Composable
fun SettingsAppIconModeRow(
    selected: AppIconMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (AppIconMode) -> Unit
) {
    val options = AppIconMode.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val control: @Composable () -> Unit = {
        LiquidOptionTabs(
            selectedIndex = selectedIndex,
            labels = options.map { it.label },
            backdrop = backdrop,
            config = config,
            width = 168.dp,
            onSelected = { index ->
                options.getOrNull(index)?.let(onSelected)
            }
        )
    }
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = "应用图标",
            summary = "选择浅色、深色或跟随应用深色模式",
            controlWidth = 168.dp,
            content = control
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
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "应用图标",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "浅色、深色或跟随深色模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        control()
    }
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
    highContrast: Boolean = false,
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
                blurRadius = if (highContrast) 7.dp else 4.dp,
                containerAlpha = if (highContrast) 0.62f else 0.4f,
                lensHeight = 24.dp,
                lensAmount = 24.dp,
                indicatorWidthOverflow = 0.dp,
                indicatorHeightOverflow = 0.dp,
                pressedContentScale = 1.04f,
                chromaticAberrationEnabled = !highContrast,
                isLightThemeOverride = glassUsesLightStyle(config),
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
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val neutralSelection = MaterialTheme.colorScheme.onSurface
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            summary = subtitle,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    neutralSelection.copy(alpha = if (selected) 0.10f else 0f)
                ),
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
            .background(
                neutralSelection.copy(
                    alpha = when {
                        pressed -> 0.12f
                        selected -> 0.10f
                        else -> 0f
                    }
                )
            )
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
    enabled: Boolean = true,
    placeholder: String = ""
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
                    modifier = Modifier.width(170.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    textAlign = TextAlign.End
                                )
                            }
                            innerTextField()
                        }
                    }
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
            modifier = Modifier.width(170.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            textAlign = TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
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
fun SettingsPickerValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
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
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        return
    }
    Row(
        modifier = modifier
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
        Text(value.ifBlank { "未设置" }, modifier = Modifier.offset(y = 1.dp).widthIn(min = 88.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
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
fun SettingsDatePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
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
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showPicker,
        title = "选择日期",
        onDismissRequest = { showPicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = popupBackdrop,
            config = config,
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        val maxDay = java.time.YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
        LaunchedEffect(maxDay) {
            if (pickerDay > maxDay) pickerDay = maxDay
        }
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            // NumberPicker defaults to MIUIX title1. Three equal columns make a four digit year
            // ellipsize on narrow dialogs or when display/font scaling is raised. Keep the picker
            // readable without changing the dialog width: reserve more width for the year and cap
            // only this dense numeric control's effective size at the extreme DPI combinations.
            val compactPicker = maxWidth < 300.dp || fontScale > 1.12f
            val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                fontSize = when {
                    maxWidth < 270.dp || fontScale > 1.32f -> 21.sp
                    compactPicker -> 24.sp
                    else -> 28.sp
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactPicker) 4.dp else 8.dp)
            ) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = pickerYear,
                    onValueChange = { pickerYear = it },
                    range = 2000..2100,
                    visibleItemCount = 3,
                    label = { "${it}年" },
                    textStyle = pickerTextStyle,
                    modifier = Modifier.weight(if (compactPicker) 1.65f else 1.5f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = pickerMonth,
                    onValueChange = { pickerMonth = it },
                    range = 1..12,
                    visibleItemCount = 3,
                    label = { "${it}月" },
                    textStyle = pickerTextStyle,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = pickerDay.coerceAtMost(maxDay),
                    onValueChange = { pickerDay = it },
                    range = 1..maxDay,
                    visibleItemCount = 3,
                    label = { "${it}日" },
                    textStyle = pickerTextStyle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickSheetLiquidAction(
                "取消", true, popupBackdrop, config,
                modifier = Modifier.weight(1f), height = 50.dp
            ) { showPicker = false }
            QuickSheetLiquidAction(
                "确定", true, popupBackdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = 50.dp
            ) {
                onValueChange(formatScheduleDate(LocalDate.of(pickerYear, pickerMonth, pickerDay.coerceAtMost(maxDay))))
                showPicker = false
            }
        }
        }
    }
}

@Composable
fun SettingsTimePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true,
    minimumMinute: Int = 0,
    maximumMinute: Int = LastMinuteOfDay
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val initialTime = remember(value, showPicker) {
        runCatching { ScheduleImportParser.parseTimeForUi(value) }.getOrNull()
            ?: java.time.LocalTime.of(8, 0)
    }
    var pickerHour by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.hour) }
    var pickerMinute by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.minute) }
    val safeMinimum = minimumMinute.coerceIn(0, LastMinuteOfDay)
    val safeMaximum = maximumMinute.coerceIn(safeMinimum, LastMinuteOfDay)
    val selectedMinute = (pickerHour * 60 + pickerMinute).coerceIn(safeMinimum, safeMaximum)
    val selectedHour = selectedMinute / 60
    val allowedMinuteRange = minuteRangeForHour(selectedHour, safeMinimum, safeMaximum)
    SettingsPickerValueRow(title = title, value = value, enabled = enabled, onClick = { showPicker = true })
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showPicker,
        title = "选择时间",
        onDismissRequest = { showPicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = popupBackdrop,
            config = config,
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selectedHour,
                    onValueChange = { hour ->
                        val minute = pickerMinute.coerceIn(minuteRangeForHour(hour, safeMinimum, safeMaximum))
                        pickerHour = hour
                        pickerMinute = minute
                    },
                    range = (safeMinimum / 60)..(safeMaximum / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selectedMinute % 60,
                    onValueChange = { pickerMinute = it },
                    range = allowedMinuteRange,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickSheetLiquidAction(
                    "取消", true, popupBackdrop, config,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) { showPicker = false }
                QuickSheetLiquidAction(
                    "确定", true, popupBackdrop, config, primary = true,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) {
                    onValueChange("%02d:%02d".format(selectedMinute / 60, selectedMinute % 60))
                    showPicker = false
                }
            }
        }
    }
}

private fun minuteRangeForHour(hour: Int, minimumMinute: Int, maximumMinute: Int): IntRange {
    val lower = if (hour == minimumMinute / 60) minimumMinute % 60 else 0
    val upper = if (hour == maximumMinute / 60) maximumMinute % 60 else 59
    return lower.coerceAtMost(upper)..upper
}

@Composable
private fun ConstrainedPeriodTimePickers(
    startMinute: Int,
    endMinute: Int,
    bounds: PeriodTimePickerBounds,
    onSelectionChange: (PeriodTimeSelection) -> Unit,
    textStyle: TextStyle,
    showSectionLabels: Boolean,
    modifier: Modifier = Modifier
) {
    val selection = constrainPeriodTimeSelection(startMinute, endMinute, bounds)
    LaunchedEffect(selection, startMinute, endMinute) {
        if (selection.startMinute != startMinute || selection.endMinute != endMinute) {
            onSelectionChange(selection)
        }
    }
    val latestStart = selection.endMinute - 1
    val earliestEnd = selection.startMinute + 1
    val startHour = selection.startMinute / 60
    val endHour = selection.endMinute / 60
    val startMinuteRange = minuteRangeForHour(startHour, bounds.minimumStartMinute, latestStart)
    val endMinuteRange = minuteRangeForHour(endHour, earliestEnd, bounds.maximumEndMinute)

    fun updateStart(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                candidate,
                selection.endMinute,
                bounds,
                PeriodTimeSelectionAnchor.START
            )
        )
    }

    fun updateEnd(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                selection.startMinute,
                candidate,
                bounds,
                PeriodTimeSelectionAnchor.END
            )
        )
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (showSectionLabels) 14.dp else 4.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("开始时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = startHour,
                    onValueChange = { hour ->
                        val minute = (selection.startMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, bounds.minimumStartMinute, latestStart))
                        updateStart(hour * 60 + minute)
                    },
                    range = (bounds.minimumStartMinute / 60)..(latestStart / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.startMinute % 60,
                    onValueChange = { updateStart(startHour * 60 + it) },
                    range = startMinuteRange,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("结束时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = endHour,
                    onValueChange = { hour ->
                        val minute = (selection.endMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, earliestEnd, bounds.maximumEndMinute))
                        updateEnd(hour * 60 + minute)
                    },
                    range = (earliestEnd / 60)..(bounds.maximumEndMinute / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.endMinute % 60,
                    onValueChange = { updateEnd(endHour * 60 + it) },
                    range = endMinuteRange,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    if (LocalCollapsibleSettingsInfoRows.current) {
        CollapsibleChangelogRow(title, body)
        return
    }
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

internal val LocalCollapsibleSettingsInfoRows = compositionLocalOf { false }

private val changelogReleaseDates = mapOf(
    "1.1.3" to "2026-08-08",
    "1.1.2" to "2026-08-05",
    "1.1.1" to "2026-08-01",
    "1.1.0" to "2026-07-30",
    "1.0.9" to "2026-07-26",
    "1.0.8" to "2026-07-22",
    "1.0.7" to "2026-07-22",
    "1.0.6" to "2026-07-20",
    "1.0.5" to "2026-07-20",
    "1.0.4" to "2026-07-18",
    "1.0.3" to "2026-07-18",
    "1.0.2" to "2026-07-17",
    "1.0.1" to "2026-07-15",
    "1.10 beta" to "2026-07-09",
    "1.09 beta" to "2026-06-28",
    "1.08 beta" to "2026-06-18",
    "1.05 beta" to "2026-06-07",
    "1.04 beta" to "2026-05-30"
)

@Composable
private fun CollapsibleChangelogRow(version: String, body: String) {
    val isCurrentVersion = version == BuildConfig.VERSION_NAME
    val releaseDate = changelogReleaseDates[version]
    val entries = remember(body) {
        body.split(Regex("[；。]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    var expanded by remember(version) { mutableStateOf(isCurrentVersion) }
    val gentleExpansionEasing = remember {
        CubicBezierEasing(0.20f, 0f, 0f, 1f)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(280, easing = gentleExpansionEasing),
        label = "changelog-arrow-$version"
    )
    val details: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(420, easing = gentleExpansionEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(280, delayMillis = 60)),
            exit = shrinkVertically(
                animationSpec = tween(320, easing = gentleExpansionEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(220))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = version,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                releaseDate?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "折叠 $version" else "展开 $version",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
            },
            bottomAction = details,
            onClick = { expanded = !expanded }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    version,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                releaseDate?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "折叠 $version" else "展开 $version",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
            }
            details()
        }
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
fun SettingsActionButton(
    label: String,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    monochrome: Boolean = false
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val monochromeSurface = if (darkTheme) ComposeColor.Black else ComposeColor.White
    val tint = when {
        destructive -> ComposeColor(0xFFFF453A)
        monochrome -> monochromeSurface
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = if (monochrome) {
        if (darkTheme) ComposeColor.White else ComposeColor.Black
    } else {
        ComposeColor.White
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            tint = tint,
            surfaceColor = tint.copy(
                alpha = when {
                    destructive -> 0.86f
                    monochrome && darkTheme -> 0.58f
                    monochrome -> 0.74f
                    else -> 0.84f
                }
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 4.dp,
            lensHeight = 14.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            Text(
                label,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Text(
            label,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(
                    tint.copy(
                        alpha = when {
                            destructive -> 0.90f
                            monochrome && darkTheme -> 0.72f
                            monochrome -> 0.88f
                            else -> 0.88f
                        }
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            color = textColor,
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
    morningPeriodCount: Int,
    noonPeriodCount: Int,
    afternoonPeriodCount: Int,
    eveningPeriodCount: Int,
    onPeriodCountsChange: (Int, Int, Int, Int) -> Unit,
    schemeDraft: SchedulePeriodSchemesDraft?,
    onSchemeDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    detectedWeekDescription: String,
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
            morningPeriodCount = morningPeriodCount,
            noonPeriodCount = noonPeriodCount,
            afternoonPeriodCount = afternoonPeriodCount,
            eveningPeriodCount = eveningPeriodCount,
            onPeriodCountsChange = onPeriodCountsChange,
            schemeDraft = schemeDraft,
            onSchemeDraftChange = onSchemeDraftChange,
            onAutoMatchPeriodEnds = onAutoMatchPeriodEnds,
            periods = periods,
            onPeriodsChange = onPeriodsChange,
            detectedWeek = detectedWeek,
            detectedWeekDescription = detectedWeekDescription,
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
                    SettingsToggleRow("自动计算当前周", detectedWeekDescription, autoCurrentWeek, backdrop, onCheckedChange = onAutoCurrentWeekChange)
                    SettingsDivider()
                    SettingsDatePickerRow("学期开始日期", termStartDate, onTermStartDateChange, backdrop, state.config)
                }
            }
            item { Text("节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    val sortedPeriods = periods.sortedBy { it.periodIndex }
                    sortedPeriods.forEachIndexed { idx, period ->
                        val bounds = periodTimePickerBounds(
                            previousEnd = sortedPeriods.getOrNull(idx - 1)?.endTime,
                            nextStart = sortedPeriods.getOrNull(idx + 1)?.startTime
                        )
                        val startMinute = parseMinuteOfDay(period.startTime) ?: bounds.minimumStartMinute
                        val endMinute = parseMinuteOfDay(period.endTime) ?: (startMinute + 1)
                        SettingsValueRow("第 ${period.periodIndex} 节", "")
                        SettingsTimePickerRow("开始时间", period.startTime, { value ->
                            onPeriodsChange(periods.map { if (it.periodIndex == period.periodIndex) it.copy(startTime = value) else it })
                        }, backdrop, state.config,
                            minimumMinute = bounds.minimumStartMinute,
                            maximumMinute = minOf(endMinute - 1, bounds.maximumEndMinute - 1)
                                .coerceAtLeast(bounds.minimumStartMinute)
                        )
                        SettingsTimePickerRow("结束时间", period.endTime, { value ->
                            onPeriodsChange(periods.map { if (it.periodIndex == period.periodIndex) it.copy(endTime = value) else it })
                        }, backdrop, state.config,
                            minimumMinute = maxOf(startMinute + 1, bounds.minimumStartMinute + 1)
                                .coerceAtMost(bounds.maximumEndMinute),
                            maximumMinute = bounds.maximumEndMinute
                        )
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
                        }, modifier = Modifier.weight(1f), monochrome = true)
                        SettingsActionButton("自启动设置", backdrop, onClick = {
                            openKeepAliveSettings(appContext)
                        }, modifier = Modifier.weight(1f), monochrome = true)
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
    morningPeriodCount: Int,
    noonPeriodCount: Int,
    afternoonPeriodCount: Int,
    eveningPeriodCount: Int,
    onPeriodCountsChange: (Int, Int, Int, Int) -> Unit,
    schemeDraft: SchedulePeriodSchemesDraft?,
    onSchemeDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    detectedWeekDescription: String,
    dirty: Boolean,
    error: String?,
    onReset: () -> Unit,
    onSave: () -> Unit,
    topPadding: Dp = detailContentTopPadding()
) {
    var longBreaks by remember { mutableStateOf(emptyList<Pair<Int, Int>>()) }
    var showLongBreakEditor by remember { mutableStateOf(false) }
    var editingLongBreakIndex by remember { mutableIntStateOf(-1) }
    var lbAfter by remember { mutableIntStateOf(1) }
    var lbMinutes by remember { mutableIntStateOf(15) }
    // 节次时间编辑（四列时间选择器）
    var showPeriodTimePicker by remember { mutableStateOf(false) }
    var editingPeriodIndex by remember { mutableIntStateOf(-1) }
    var pickerStartHour by remember { mutableIntStateOf(8) }
    var pickerStartMinute by remember { mutableIntStateOf(0) }
    var pickerEndHour by remember { mutableIntStateOf(8) }
    var pickerEndMinute by remember { mutableIntStateOf(45) }
    val currentPeriods by rememberUpdatedState(periods)
    val currentLongBreaks by rememberUpdatedState(longBreaks)
    var showAutoMatchConfirm by remember { mutableStateOf(false) }
    val onAutoMatchAction = {
        val cd = classDurationMinutes.toIntOrNull()
        val bd = breakDurationMinutes.toIntOrNull()
        if (cd != null && bd != null) {
            onPeriodsChange(autoMatchPeriodTimes(periods, cd, bd, longBreaks))
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("当前周", currentWeek, { onCurrentWeekChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = !autoCurrentWeek)
                SettingsDivider()
                SettingsToggleRow(
                    title = "自动计算当前周",
                    subtitle = detectedWeekDescription,
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
                SettingsDatePickerRow("学期开始日期", termStartDate, onTermStartDateChange, backdrop, state.config)
            }
        }
        item { Text("节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        if (schemeDraft != null) {
            item(key = "period-scheme-editor") {
                PeriodSchemeEditor(
                    state = state,
                    backdrop = backdrop,
                    config = state.config.copy(
                        morningPeriodCount = morningPeriodCount,
                        noonPeriodCount = noonPeriodCount,
                        afternoonPeriodCount = afternoonPeriodCount,
                        eveningPeriodCount = eveningPeriodCount
                    ),
                    draft = schemeDraft,
                    onDraftChange = onSchemeDraftChange,
                    onCountsChange = onPeriodCountsChange
                )
            }
        }
        if (schemeDraft == null) {
        // 上方卡片：课时/课间/自动匹配/大课间
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsTextFieldRow("单节课分钟数", classDurationMinutes, { onClassDurationMinutesChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("课间分钟数", breakDurationMinutes, { onBreakDurationMinutesChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动匹配", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.offset(y = 1.dp))
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = { showAutoMatchConfirm = true },
                            backdrop = backdrop,
                            modifier = Modifier.height(34.dp),
                            height = 34.dp,
                            surfaceColor = ComposeColor(0xFF0A84FF).copy(alpha = 0.88f),
                            tint = ComposeColor(0xFF0A84FF),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            blurRadius = 4.dp,
                            lensHeight = 12.dp,
                            lensAmount = 16.dp,
                            chromaticAberration = false
                        ) {
                            Text("自动匹配", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SettingsActionButton("自动匹配", null, onClick = { showAutoMatchConfirm = true })
                    }
                }
                // 大课间列表
                if (longBreaks.isNotEmpty()) {
                    longBreaks.forEachIndexed { idx, (after, mins) ->
                        SettingsDivider()
                        SettingsPickerValueRow(
                            "大课间",
                            "第 $after 节后 · ${mins} 分钟",
                            onClick = {
                                editingLongBreakIndex = idx
                                lbAfter = after
                                lbMinutes = mins
                                showLongBreakEditor = true
                            }
                        )
                    }
                }
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = {
                                lbAfter = (periods.maxOfOrNull { it.periodIndex } ?: 2).coerceAtLeast(1)
                                lbMinutes = 15
                                editingLongBreakIndex = -1
                                showLongBreakEditor = true
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            height = 42.dp,
                            surfaceColor = ComposeColor(0xFF0A84FF).copy(alpha = 0.88f),
                            tint = ComposeColor(0xFF0A84FF),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            blurRadius = 4.dp,
                            lensHeight = 12.dp,
                            lensAmount = 16.dp,
                            chromaticAberration = false
                        ) {
                            Text("+ 大课间", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SettingsActionButton("+ 大课间", null, onClick = {
                            lbAfter = (periods.maxOfOrNull { it.periodIndex } ?: 2).coerceAtLeast(1)
                            lbMinutes = 15
                            editingLongBreakIndex = -1
                            showLongBreakEditor = true
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        // 下方卡片：节次时间线
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                periods.sortedBy { it.periodIndex }.forEachIndexed { idx, period ->
                    if (idx > 0) SettingsDivider()
                    SettingsPickerValueRow(
                        "第 ${period.periodIndex} 节",
                        "${period.startTime} - ${period.endTime}",
                        onClick = {
                            editingPeriodIndex = period.periodIndex
                            val start = runCatching { ScheduleImportParser.parseTimeForUi(period.startTime) }.getOrNull() ?: LocalTime.of(8, 0)
                            val end = runCatching { ScheduleImportParser.parseTimeForUi(period.endTime) }.getOrNull() ?: LocalTime.of(8, 45)
                            pickerStartHour = start.hour
                            pickerStartMinute = start.minute
                            pickerEndHour = end.hour
                            pickerEndMinute = end.minute
                            showPeriodTimePicker = true
                        }
                    )
                }
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = {
                                val next = (periods.maxOfOrNull { it.periodIndex } ?: 0) + 1
                                onPeriodsChange(periods + PeriodEntity(next, "08:00", "08:45"))
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            height = 42.dp,
                            surfaceColor = ComposeColor(0xFF0A84FF).copy(alpha = 0.88f),
                            tint = ComposeColor(0xFF0A84FF),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            blurRadius = 4.dp,
                            lensHeight = 12.dp,
                            lensAmount = 16.dp,
                            chromaticAberration = false
                        ) {
                            Text("+ 节次", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SettingsActionButton("+ 节次", null, onClick = {
                            val next = (periods.maxOfOrNull { it.periodIndex } ?: 0) + 1
                            onPeriodsChange(periods + PeriodEntity(next, "08:00", "08:45"))
                        }, modifier = Modifier.weight(1f))
                    }
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = {
                                if (periods.isNotEmpty()) onPeriodsChange(periods.dropLast(1))
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            height = 42.dp,
                            surfaceColor = ComposeColor(0xFFFF453A).copy(alpha = 0.88f),
                            tint = ComposeColor(0xFFFF453A),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            blurRadius = 4.dp,
                            lensHeight = 12.dp,
                            lensAmount = 16.dp,
                            chromaticAberration = false
                        ) {
                            Text("删除末节", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SettingsActionButton("删除末节", null, onClick = {
                            if (periods.isNotEmpty()) onPeriodsChange(periods.dropLast(1))
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
    // 节次时间编辑弹窗（四列时间选择器）
    val periodPickerBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showPeriodTimePicker,
        title = "编辑第 ${editingPeriodIndex} 节时间",
        onDismissRequest = { showPeriodTimePicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = periodPickerBackdrop,
            config = state.config,
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val compactPickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(fontSize = 22.sp)
            val sortedPeriods = currentPeriods.sortedBy { it.periodIndex }
            val editingPosition = sortedPeriods.indexOfFirst { it.periodIndex == editingPeriodIndex }
            val pickerBounds = periodTimePickerBounds(
                previousEnd = sortedPeriods.getOrNull(editingPosition - 1)?.endTime,
                nextStart = sortedPeriods.getOrNull(editingPosition + 1)?.startTime
            )
            ConstrainedPeriodTimePickers(
                startMinute = pickerStartHour * 60 + pickerStartMinute,
                endMinute = pickerEndHour * 60 + pickerEndMinute,
                bounds = pickerBounds,
                onSelectionChange = { selection ->
                    pickerStartHour = selection.startMinute / 60
                    pickerStartMinute = selection.startMinute % 60
                    pickerEndHour = selection.endMinute / 60
                    pickerEndMinute = selection.endMinute % 60
                },
                textStyle = compactPickerStyle,
                showSectionLabels = false,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickSheetLiquidAction(
                    "取消", true, periodPickerBackdrop, state.config,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) { showPeriodTimePicker = false }
                QuickSheetLiquidAction(
                    "删除", true, periodPickerBackdrop, state.config, destructive = true,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) {
                    onPeriodsChange(currentPeriods.filter { it.periodIndex != editingPeriodIndex })
                    showPeriodTimePicker = false
                }
                QuickSheetLiquidAction(
                    "确定", true, periodPickerBackdrop, state.config, primary = true,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) {
                    val selection = constrainPeriodTimeSelection(
                        pickerStartHour * 60 + pickerStartMinute,
                        pickerEndHour * 60 + pickerEndMinute,
                        pickerBounds
                    )
                    val startStr = "%02d:%02d".format(selection.startMinute / 60, selection.startMinute % 60)
                    val endStr = "%02d:%02d".format(selection.endMinute / 60, selection.endMinute % 60)
                    onPeriodsChange(currentPeriods.map {
                        if (it.periodIndex == editingPeriodIndex) it.copy(startTime = startStr, endTime = endStr) else it
                    })
                    showPeriodTimePicker = false
                }
            }
        }
    }
    // 大课间编辑弹窗
    val lbPickerBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showLongBreakEditor,
        title = "编辑大课间",
        onDismissRequest = { showLongBreakEditor = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = lbPickerBackdrop,
            config = state.config,
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("大课间位置", style = MaterialTheme.typography.titleSmall, color = LocalContentColor.current, modifier = Modifier.padding(horizontal = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = lbAfter,
                    onValueChange = { lbAfter = it },
                    range = 1..(periods.maxOfOrNull { it.periodIndex } ?: 12).coerceAtLeast(1),
                    visibleItemCount = 3,
                    label = { "第${it}节后" },
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = lbMinutes,
                    onValueChange = { lbMinutes = it },
                    range = 5..60,
                    visibleItemCount = 3,
                    label = { "${it}分钟" },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickSheetLiquidAction(
                    "取消", true, lbPickerBackdrop, state.config,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) { showLongBreakEditor = false }
                QuickSheetLiquidAction(
                    "删除", true, lbPickerBackdrop, state.config, destructive = true,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) {
                    if (editingLongBreakIndex >= 0) {
                        longBreaks = longBreaks.toMutableList().also { it.removeAt(editingLongBreakIndex) }
                    }
                    showLongBreakEditor = false
                }
                QuickSheetLiquidAction(
                    "确定", true, lbPickerBackdrop, state.config, primary = true,
                    modifier = Modifier.weight(1f), height = 50.dp
                ) {
                    val list = longBreaks.toMutableList()
                    if (editingLongBreakIndex >= 0) {
                        list[editingLongBreakIndex] = lbAfter to lbMinutes
                    } else {
                        list.add(lbAfter to lbMinutes)
                    }
                    longBreaks = list
                    showLongBreakEditor = false
                }
            }
        }
    }
    if (showAutoMatchConfirm) {
        LiquidAlertDialog(
            title = "确认自动匹配",
            message = "自动匹配将基于第一节课的开始时间和课时/课间设置重新计算所有节次时间，手动修改的节次会被覆盖。是否继续？",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { showAutoMatchConfirm = false }),
                LiquidAlertAction("确定", LiquidAlertActionStyle.Primary, onClick = {
                    onAutoMatchAction()
                    showAutoMatchConfirm = false
                })
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showAutoMatchConfirm = false }
        )
    }
}

@Composable
private fun PeriodTimelineSeparator(label: String, tint: ComposeColor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.18f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
    }
}

@Composable
private fun PeriodEditorActionButton(
    label: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    tint: ComposeColor = ComposeColor(0xFF0A84FF),
    onClick: () -> Unit
) {
    val surface = tint.copy(alpha = if (appUsesDarkTheme(config)) 0.82f else 0.90f)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.height(44.dp),
            height = 44.dp,
            surfaceColor = surface,
            tint = tint,
            contentPadding = PaddingValues(horizontal = 12.dp),
            blurRadius = 3.dp,
            lensHeight = 14.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            Text(label, color = ComposeColor.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        Box(
            modifier = modifier
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = ComposeColor.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun PeriodSchemeEditor(
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    draft: SchedulePeriodSchemesDraft,
    onDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onCountsChange: (Int, Int, Int, Int) -> Unit
) {
    val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: draft.schemes.first()
    var localError by remember { mutableStateOf<String?>(null) }
    var editingPeriod by remember { mutableIntStateOf(-1) }
    var editingPart by remember { mutableStateOf<PeriodDayPart?>(null) }
    var pickerStartHour by remember { mutableIntStateOf(8) }
    var pickerStartMinute by remember { mutableIntStateOf(0) }
    var pickerEndHour by remember { mutableIntStateOf(8) }
    var pickerEndMinute by remember { mutableIntStateOf(45) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }
    var showCountPicker by remember { mutableStateOf(false) }
    var countPickerMorning by remember { mutableIntStateOf(config.morningPeriodCount.coerceAtLeast(1)) }
    var countPickerNoon by remember { mutableIntStateOf(config.noonPeriodCount.coerceAtLeast(1)) }
    var countPickerAfternoon by remember { mutableIntStateOf(config.afternoonPeriodCount.coerceAtLeast(1)) }
    var countPickerEvening by remember { mutableIntStateOf(config.eveningPeriodCount.coerceAtLeast(1)) }
    var countPickerTotal by remember { mutableIntStateOf(config.totalPeriodCount().coerceAtLeast(1)) }
    var showSegmentStartPicker by remember { mutableStateOf(false) }
    var morningStartMinute by remember { mutableIntStateOf(8 * 60) }
    var noonStartMinute by remember { mutableIntStateOf(12 * 60) }
    var afternoonStartMinute by remember { mutableIntStateOf(14 * 60) }
    var eveningStartMinute by remember { mutableIntStateOf(19 * 60) }
    var breakAfter by remember { mutableIntStateOf(1) }
    var breakPickerPosition by remember { mutableIntStateOf(0) }
    var breakMinutes by remember { mutableIntStateOf(20) }
    var pendingAutoSwitch by remember { mutableStateOf<PeriodSchemeDraft?>(null) }
    var pendingAutoOverrides by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var timeConflictMessage by remember { mutableStateOf<String?>(null) }
    val specialBreakCandidates = PeriodDayPart.entries.flatMap { part ->
        config.periodRange(part).toList().dropLast(1)
    }

    fun updateActive(transform: (PeriodSchemeDraft) -> PeriodSchemeDraft) {
        onDraftChange(draft.copy(schemes = draft.schemes.map { if (it.scheme.id == active.scheme.id) transform(it) else it }))
    }

    fun shiftedTimesForInsert(item: PeriodSchemeDraft, after: Int, newConfig: ScheduleConfigEntity): PeriodSchemeDraft {
        val shifted = item.times.map {
            if (it.periodIndex > after) it.copy(periodIndex = it.periodIndex + 1) else it
        }.toMutableList()
        if (item.scheme.mode == PeriodSchemeMode.MANUAL) {
            val previous = shifted.firstOrNull { it.periodIndex == after }
            val start = previous?.endTime?.let { runCatching { LocalTime.parse(it).plusMinutes(item.scheme.breakDurationMinutes.toLong()) }.getOrNull() }
                ?: when {
                    after < newConfig.morningPeriodCount -> LocalTime.parse(item.scheme.morningStartTime)
                    after < newConfig.morningPeriodCount + newConfig.noonPeriodCount -> LocalTime.parse(item.scheme.noonStartTime)
                    after < newConfig.morningPeriodCount + newConfig.noonPeriodCount + newConfig.afternoonPeriodCount -> LocalTime.parse(item.scheme.afternoonStartTime)
                    else -> LocalTime.parse(item.scheme.eveningStartTime)
                }
            val end = start.plusMinutes(item.scheme.classDurationMinutes.toLong())
            shifted += PeriodSchemeTimeEntity(item.scheme.id, after + 1, start.toString(), end.toString())
        }
        return item.copy(
            times = shifted.sortedBy { it.periodIndex },
            specialBreaks = item.specialBreaks.mapKeys { (index, _) -> if (index > after) index + 1 else index },
            overriddenPeriods = item.overriddenPeriods.map { if (it > after) it + 1 else it }.toSet()
        ).let { if (it.scheme.mode == PeriodSchemeMode.AUTO_MATCH) it.copy(times = resolveSchemeTimes(newConfig, it)) else it }
    }

    fun addPeriod(part: PeriodDayPart) {
        val range = config.periodRange(part)
        val after = when {
            !range.isEmpty() -> range.last
            part == PeriodDayPart.MORNING -> 0
            part == PeriodDayPart.NOON -> config.morningPeriodCount
            part == PeriodDayPart.AFTERNOON -> config.morningPeriodCount + config.noonPeriodCount
            else -> config.morningPeriodCount + config.noonPeriodCount + config.afternoonPeriodCount
        }
        val newConfig = when (part) {
            PeriodDayPart.MORNING -> config.copy(morningPeriodCount = config.morningPeriodCount + 1)
            PeriodDayPart.NOON -> config.copy(noonPeriodCount = config.noonPeriodCount + 1)
            PeriodDayPart.AFTERNOON -> config.copy(afternoonPeriodCount = config.afternoonPeriodCount + 1)
            PeriodDayPart.EVENING -> config.copy(eveningPeriodCount = config.eveningPeriodCount + 1)
        }
        onCountsChange(newConfig.morningPeriodCount, newConfig.noonPeriodCount, newConfig.afternoonPeriodCount, newConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = draft.schemes.map { shiftedTimesForInsert(it, after, newConfig) },
                topologyOperations = draft.topologyOperations + PeriodTopologyOperation.AddAfter(after)
            )
        )
    }

    fun deletePeriod(index: Int) {
        val part = PeriodDayPart.entries.firstOrNull { index in config.periodRange(it) } ?: return
        val newConfig = when (part) {
            PeriodDayPart.MORNING -> config.copy(morningPeriodCount = (config.morningPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.NOON -> config.copy(noonPeriodCount = (config.noonPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.AFTERNOON -> config.copy(afternoonPeriodCount = (config.afternoonPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.EVENING -> config.copy(eveningPeriodCount = (config.eveningPeriodCount - 1).coerceAtLeast(0))
        }
        if (newConfig.totalPeriodCount() == 0) {
            localError = "至少需要保留一个节次"
            return
        }
        onCountsChange(newConfig.morningPeriodCount, newConfig.noonPeriodCount, newConfig.afternoonPeriodCount, newConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = draft.schemes.map { item ->
                    item.copy(
                        times = item.times.filter { it.periodIndex != index }.map { if (it.periodIndex > index) it.copy(periodIndex = it.periodIndex - 1) else it },
                        specialBreaks = item.specialBreaks.filterKeys { it != index }.mapKeys { (key, _) -> if (key > index) key - 1 else key },
                        overriddenPeriods = item.overriddenPeriods.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
                    )
                },
                topologyOperations = draft.topologyOperations + PeriodTopologyOperation.Delete(index)
            )
        )
    }

    fun changePartCounts(requestedMorning: Int, requestedNoon: Int, requestedAfternoon: Int, requestedEvening: Int) {
        val targets = mapOf(
            PeriodDayPart.MORNING to requestedMorning.coerceIn(0, 40),
            PeriodDayPart.NOON to requestedNoon.coerceIn(0, 40),
            PeriodDayPart.AFTERNOON to requestedAfternoon.coerceIn(0, 40),
            PeriodDayPart.EVENING to requestedEvening.coerceIn(0, 40)
        )
        if (targets.values.sum() == 0) {
            localError = "上午、中午、下午、晚上至少需要启用一个时段"
            return
        }
        var workingConfig = config
        var workingSchemes = draft.schemes
        val operations = mutableListOf<PeriodTopologyOperation>()
        PeriodDayPart.entries.forEach { part ->
            val oldCount = workingConfig.periodCount(part)
            val targetCount = targets.getValue(part)
            if (targetCount > oldCount) repeat(targetCount - oldCount) {
                val range = workingConfig.periodRange(part)
                val after = when {
                    !range.isEmpty() -> range.last
                    part == PeriodDayPart.MORNING -> 0
                    part == PeriodDayPart.NOON -> workingConfig.morningPeriodCount
                    part == PeriodDayPart.AFTERNOON -> workingConfig.morningPeriodCount + workingConfig.noonPeriodCount
                    else -> workingConfig.morningPeriodCount + workingConfig.noonPeriodCount + workingConfig.afternoonPeriodCount
                }
                workingConfig = when (part) {
                    PeriodDayPart.MORNING -> workingConfig.copy(morningPeriodCount = workingConfig.morningPeriodCount + 1)
                    PeriodDayPart.NOON -> workingConfig.copy(noonPeriodCount = workingConfig.noonPeriodCount + 1)
                    PeriodDayPart.AFTERNOON -> workingConfig.copy(afternoonPeriodCount = workingConfig.afternoonPeriodCount + 1)
                    PeriodDayPart.EVENING -> workingConfig.copy(eveningPeriodCount = workingConfig.eveningPeriodCount + 1)
                }
                workingSchemes = workingSchemes.map { shiftedTimesForInsert(it, after, workingConfig) }
                operations += PeriodTopologyOperation.AddAfter(after)
            }
            if (targetCount < oldCount) repeat(oldCount - targetCount) {
                val range = workingConfig.periodRange(part)
                val index = range.last
                workingSchemes = workingSchemes.map { item ->
                    item.copy(
                        times = item.times.filter { it.periodIndex != index }.map { if (it.periodIndex > index) it.copy(periodIndex = it.periodIndex - 1) else it },
                        specialBreaks = item.specialBreaks.filterKeys { it != index }.mapKeys { (key, _) -> if (key > index) key - 1 else key },
                        overriddenPeriods = item.overriddenPeriods.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
                    )
                }
                workingConfig = when (part) {
                    PeriodDayPart.MORNING -> workingConfig.copy(morningPeriodCount = workingConfig.morningPeriodCount - 1)
                    PeriodDayPart.NOON -> workingConfig.copy(noonPeriodCount = workingConfig.noonPeriodCount - 1)
                    PeriodDayPart.AFTERNOON -> workingConfig.copy(afternoonPeriodCount = workingConfig.afternoonPeriodCount - 1)
                    PeriodDayPart.EVENING -> workingConfig.copy(eveningPeriodCount = workingConfig.eveningPeriodCount - 1)
                }
                operations += PeriodTopologyOperation.Delete(index)
            }
        }
        onCountsChange(workingConfig.morningPeriodCount, workingConfig.noonPeriodCount, workingConfig.afternoonPeriodCount, workingConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = workingSchemes.map { item ->
                    if (item.scheme.mode == PeriodSchemeMode.AUTO_MATCH) item.copy(times = resolveSchemeTimes(workingConfig, item)) else item
                },
                topologyOperations = draft.topologyOperations + operations
            )
        )
    }

    fun changePartCount(part: PeriodDayPart, requestedCount: Int) {
        changePartCounts(
            requestedMorning = if (part == PeriodDayPart.MORNING) requestedCount else config.morningPeriodCount,
            requestedNoon = if (part == PeriodDayPart.NOON) requestedCount else config.noonPeriodCount,
            requestedAfternoon = if (part == PeriodDayPart.AFTERNOON) requestedCount else config.afternoonPeriodCount,
            requestedEvening = if (part == PeriodDayPart.EVENING) requestedCount else config.eveningPeriodCount
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            val selectedIndex = draft.schemes.indexOfFirst { it.scheme.id == draft.activeSchemeId }.coerceAtLeast(0)
            MiuixOverlayDropdownPreference(
                items = draft.schemes.map { it.scheme.name },
                selectedIndex = selectedIndex,
                title = "当前作息方案",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 318.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    draft.schemes.getOrNull(index)?.let { item ->
                        onDraftChange(draft.copy(activeSchemeId = item.scheme.id))
                    }
                }
            )
            SettingsDivider()
            SettingsTextFieldRow(
                "方案名称",
                active.scheme.name,
                { name -> updateActive { it.copy(scheme = it.scheme.copy(name = name.ifBlank { "未命名作息" })) } }
            )
            SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodEditorActionButton("新建", backdrop, state.config, modifier = Modifier.weight(1f), onClick = {
                    val tempId = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1L
                    val newScheme = PeriodSchemeEntity(
                        id = tempId,
                        scheduleId = config.id,
                        name = "新作息",
                        mode = PeriodSchemeMode.AUTO_MATCH,
                        classDurationMinutes = active.scheme.classDurationMinutes,
                        breakDurationMinutes = active.scheme.breakDurationMinutes,
                        morningStartTime = active.scheme.morningStartTime,
                        noonStartTime = active.scheme.noonStartTime,
                        afternoonStartTime = active.scheme.afternoonStartTime,
                        eveningStartTime = active.scheme.eveningStartTime
                    )
                    val newItem = PeriodSchemeDraft(newScheme, emptyList()).let {
                        it.copy(times = resolveSchemeTimes(config, it))
                    }
                    onDraftChange(draft.copy(schemes = draft.schemes + newItem, activeSchemeId = tempId))
                })
                PeriodEditorActionButton("复制", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFF6750A4), onClick = {
                    val tempId = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1L
                    val copy = active.copy(
                        scheme = active.scheme.copy(id = tempId, name = "${active.scheme.name} 副本", isActive = false),
                        times = active.times.map { it.copy(schemeId = tempId) }
                    )
                    onDraftChange(draft.copy(schemes = draft.schemes + copy, activeSchemeId = tempId))
                })
                PeriodEditorActionButton("删除", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFFFF453A), onClick = {
                    if (draft.schemes.size <= 1) localError = "至少需要保留一套作息方案"
                    else {
                        val removedIndex = draft.schemes.indexOfFirst { it.scheme.id == active.scheme.id }
                        val remaining = draft.schemes.filterNot { it.scheme.id == active.scheme.id }
                        val adjacent = remaining[removedIndex.coerceAtMost(remaining.lastIndex)]
                        onDraftChange(draft.copy(schemes = remaining, activeSchemeId = adjacent.scheme.id))
                    }
                })
            }
        }

        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                LiquidOptionTabs(
                    selectedIndex = if (active.scheme.mode == PeriodSchemeMode.MANUAL) 0 else 1,
                    labels = listOf("手动模式", "自动匹配"),
                    backdrop = backdrop,
                    config = state.config,
                    width = maxWidth,
                    onSelected = { selected ->
                        if (selected == 0 && active.scheme.mode != PeriodSchemeMode.MANUAL) {
                            val frozen = resolveSchemeTimes(config, active)
                            updateActive { it.copy(scheme = it.scheme.copy(mode = PeriodSchemeMode.MANUAL), times = frozen) }
                        } else if (selected == 1 && active.scheme.mode != PeriodSchemeMode.AUTO_MATCH) {
                            val times = active.times.sortedBy { it.periodIndex }
                            val partForIndex: (Int) -> PeriodDayPart? = { index ->
                                PeriodDayPart.entries.firstOrNull { index in config.periodRange(it) }
                            }
                            val detectedSpecialBreaks = times.zipWithNext().mapNotNull { (left, right) ->
                                if (partForIndex(left.periodIndex) != partForIndex(right.periodIndex)) return@mapNotNull null
                                val leftEnd = runCatching { LocalTime.parse(left.endTime) }.getOrNull() ?: return@mapNotNull null
                                val rightStart = runCatching { LocalTime.parse(right.startTime) }.getOrNull() ?: return@mapNotNull null
                                val gap = java.time.Duration.between(leftEnd, rightStart).toMinutes().toInt()
                                if (gap >= 0 && gap != active.scheme.breakDurationMinutes) left.periodIndex to gap else null
                            }.toMap()
                            val autoCandidate = active.copy(
                                scheme = active.scheme.copy(
                                    mode = PeriodSchemeMode.AUTO_MATCH,
                                    morningStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.MORNING) }?.startTime ?: active.scheme.morningStartTime,
                                    noonStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.NOON) }?.startTime ?: active.scheme.noonStartTime,
                                    afternoonStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.AFTERNOON) }?.startTime ?: active.scheme.afternoonStartTime,
                                    eveningStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.EVENING) }?.startTime ?: active.scheme.eveningStartTime
                                ),
                                specialBreaks = detectedSpecialBreaks,
                                overriddenPeriods = emptySet()
                            )
                            val generatedByIndex = resolveSchemeTimes(config, autoCandidate).associateBy { it.periodIndex }
                            val changedIndices = times.filter { manual ->
                                val generated = generatedByIndex[manual.periodIndex]
                                generated == null || generated.startTime != manual.startTime || generated.endTime != manual.endTime
                            }.mapTo(mutableSetOf()) { it.periodIndex }
                            if (changedIndices.isEmpty()) {
                                updateActive { autoCandidate.copy(times = generatedByIndex.values.sortedBy { it.periodIndex }) }
                            } else {
                                pendingAutoSwitch = autoCandidate
                                pendingAutoOverrides = changedIndices
                            }
                        }
                    }
                )
            }
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                SettingsDivider()
                SettingsTextFieldRow("单节课分钟数", active.scheme.classDurationMinutes.toString(), { value ->
                    value.toIntOrNull()?.let { minutes -> updateActive { it.copy(scheme = it.scheme.copy(classDurationMinutes = minutes.coerceIn(1, 300))) } }
                }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("普通课间分钟数", active.scheme.breakDurationMinutes.toString(), { value ->
                    value.toIntOrNull()?.let { minutes -> updateActive { it.copy(scheme = it.scheme.copy(breakDurationMinutes = minutes.coerceIn(0, 300))) } }
                }, KeyboardType.Number)
            }
            PeriodDayPart.entries.forEach { part ->
                val title = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                val count = config.periodCount(part)
                val range = config.periodRange(part)
                SettingsDivider()
                SettingsToggleRow(
                    title = "启用$title",
                    subtitle = if (count == 0) "已关闭" else "${count} 节 · 第 ${range.first}-${range.last} 节",
                    checked = count > 0,
                    backdrop = backdrop,
                    onCheckedChange = { enabled -> changePartCount(part, if (enabled) 1 else 0) }
                )
            }
            SettingsDivider()
            SettingsPickerValueRow(
                title = "节数分配",
                value = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.joinToString(" · ") { part ->
                    val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                    "$name ${config.periodCount(part)}"
                },
                onClick = {
                    countPickerMorning = config.morningPeriodCount.coerceAtLeast(1)
                    countPickerNoon = config.noonPeriodCount.coerceAtLeast(1)
                    countPickerAfternoon = config.afternoonPeriodCount.coerceAtLeast(1)
                    countPickerEvening = config.eveningPeriodCount.coerceAtLeast(1)
                    countPickerTotal = config.totalPeriodCount().coerceAtLeast(1)
                    showCountPicker = true
                }
            )
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                SettingsDivider()
                SettingsPickerValueRow(
                    title = "时段起点",
                    value = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.joinToString(" · ") { part ->
                        when (part) {
                            PeriodDayPart.MORNING -> active.scheme.morningStartTime
                            PeriodDayPart.NOON -> active.scheme.noonStartTime
                            PeriodDayPart.AFTERNOON -> active.scheme.afternoonStartTime
                            PeriodDayPart.EVENING -> active.scheme.eveningStartTime
                        }
                    },
                    onClick = {
                        morningStartMinute = runCatching { LocalTime.parse(active.scheme.morningStartTime) }.getOrDefault(LocalTime.of(8, 0)).let { it.hour * 60 + it.minute }
                        noonStartMinute = runCatching { LocalTime.parse(active.scheme.noonStartTime) }.getOrDefault(LocalTime.of(12, 0)).let { it.hour * 60 + it.minute }
                        afternoonStartMinute = runCatching { LocalTime.parse(active.scheme.afternoonStartTime) }.getOrDefault(LocalTime.of(14, 0)).let { it.hour * 60 + it.minute }
                        eveningStartMinute = runCatching { LocalTime.parse(active.scheme.eveningStartTime) }.getOrDefault(LocalTime.of(19, 0)).let { it.hour * 60 + it.minute }
                        showSegmentStartPicker = true
                    }
                )
            }
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                active.specialBreaks.toSortedMap().forEach { (after, minutes) ->
                    SettingsDivider()
                    SettingsPickerValueRow("特殊课间", "第 $after 节后 · ${minutes} 分钟", onClick = {
                        breakAfter = after
                        breakPickerPosition = specialBreakCandidates.indexOf(after).coerceAtLeast(0)
                        breakMinutes = minutes
                        showBreakPicker = true
                    })
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodEditorActionButton("+特殊课间", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFF6750A4), onClick = {
                        val candidate = specialBreakCandidates.firstOrNull { it !in active.specialBreaks }
                        if (candidate == null) {
                            localError = "当前时段内没有可插入特殊课间的位置"
                        } else {
                            breakAfter = candidate
                            breakPickerPosition = specialBreakCandidates.indexOf(candidate)
                            breakMinutes = 20
                            showBreakPicker = true
                        }
                    })
                    PeriodEditorActionButton("应用自动匹配", backdrop, state.config, modifier = Modifier.weight(1.35f), onClick = {
                        updateActive { item -> item.copy(times = resolveSchemeTimes(config, item)) }
                        localError = null
                    })
                }
            }
        }

        val resolved = resolveSchemeTimes(config, active)
        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            resolved.forEachIndexed { position, time ->
                if (position > 0) {
                    val previous = resolved[position - 1]
                    val dayPartLabel = when {
                        config.noonPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + 1 -> "中午时段"
                        config.afternoonPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + config.noonPeriodCount + 1 -> "下午时段"
                        config.eveningPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + config.noonPeriodCount + config.afternoonPeriodCount + 1 -> "晚上时段"
                        else -> null
                    }
                    val specialBreak = active.specialBreaks[previous.periodIndex]
                    when {
                        dayPartLabel != null -> PeriodTimelineSeparator(dayPartLabel, ComposeColor(0xFF0A84FF))
                        specialBreak != null -> PeriodTimelineSeparator("特殊课间 · ${specialBreak}分钟", ComposeColor(0xFF6750A4))
                        else -> SettingsDivider()
                    }
                }
                SettingsPickerValueRow(
                    if (time.periodIndex in active.overriddenPeriods) "第 ${time.periodIndex} 节 · 已覆盖" else "第 ${time.periodIndex} 节",
                    "${time.startTime} - ${time.endTime}",
                    onClick = {
                        editingPart = null
                        editingPeriod = time.periodIndex
                        val start = LocalTime.parse(time.startTime)
                        val end = LocalTime.parse(time.endTime)
                        pickerStartHour = start.hour; pickerStartMinute = start.minute
                        pickerEndHour = end.hour; pickerEndMinute = end.minute
                        showTimePicker = true
                    }
                )
            }
        }

        localError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
    }

    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val enabledParts = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showCountPicker,
        title = "节数分配",
        onDismissRequest = { showCountPicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(popupBackdrop, state.config, 28.dp, centered = true)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("总节次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = countPickerTotal,
                onValueChange = { changed ->
                    countPickerTotal = changed
                    var remaining = changed
                    enabledParts.forEachIndexed { index, part ->
                        val slotsAfter = enabledParts.lastIndex - index
                        val current = when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning
                            PeriodDayPart.NOON -> countPickerNoon
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon
                            PeriodDayPart.EVENING -> countPickerEvening
                        }
                        val allocated = if (index == enabledParts.lastIndex) remaining else current.coerceIn(1, (remaining - slotsAfter).coerceAtLeast(1))
                        when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning = allocated
                            PeriodDayPart.NOON -> countPickerNoon = allocated
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = allocated
                            PeriodDayPart.EVENING -> countPickerEvening = allocated
                        }
                        remaining -= allocated
                    }
                },
                range = enabledParts.size.coerceAtLeast(1)..40,
                visibleItemCount = 3,
                label = { "${it}节" },
                modifier = Modifier.fillMaxWidth().height(112.dp)
            )
            Text("时段分配", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / enabledParts.size.coerceAtLeast(1)
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 100.dp || fontScale > 1.3f -> 17.sp
                        columnWidth < 140.dp || fontScale > 1.1f -> 20.sp
                        else -> 28.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    enabledParts.forEach { part ->
                        val value = when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning
                            PeriodDayPart.NOON -> countPickerNoon
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon
                            PeriodDayPart.EVENING -> countPickerEvening
                        }
                        val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (part == enabledParts.last()) {
                                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                    Text("${value}节", style = pickerStyle, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                val otherBeforeLast = enabledParts.dropLast(1).filterNot { it == part }.sumOf { other ->
                                    when (other) {
                                        PeriodDayPart.MORNING -> countPickerMorning
                                        PeriodDayPart.NOON -> countPickerNoon
                                        PeriodDayPart.AFTERNOON -> countPickerAfternoon
                                        PeriodDayPart.EVENING -> countPickerEvening
                                    }
                                }
                                val maxCount = (countPickerTotal - otherBeforeLast - 1).coerceAtLeast(1)
                                top.yukonga.miuix.kmp.basic.NumberPicker(
                                    value = value.coerceIn(1, maxCount),
                                    onValueChange = { changed ->
                                        when (part) {
                                            PeriodDayPart.MORNING -> countPickerMorning = changed
                                            PeriodDayPart.NOON -> countPickerNoon = changed
                                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = changed
                                            PeriodDayPart.EVENING -> countPickerEvening = changed
                                        }
                                        val allocated = enabledParts.dropLast(1).sumOf { beforeLast ->
                                            when (beforeLast) {
                                                PeriodDayPart.MORNING -> countPickerMorning
                                                PeriodDayPart.NOON -> countPickerNoon
                                                PeriodDayPart.AFTERNOON -> countPickerAfternoon
                                                PeriodDayPart.EVENING -> countPickerEvening
                                            }
                                        }
                                        when (enabledParts.last()) {
                                            PeriodDayPart.MORNING -> countPickerMorning = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.NOON -> countPickerNoon = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.EVENING -> countPickerEvening = (countPickerTotal - allocated).coerceAtLeast(1)
                                        }
                                    },
                                    range = 1..maxCount,
                                    visibleItemCount = 3,
                                    label = { "${it}节" },
                                    textStyle = pickerStyle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = 48.dp) {
                    showCountPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = 48.dp) {
                    changePartCounts(
                        if (PeriodDayPart.MORNING in enabledParts) countPickerMorning else 0,
                        if (PeriodDayPart.NOON in enabledParts) countPickerNoon else 0,
                        if (PeriodDayPart.AFTERNOON in enabledParts) countPickerAfternoon else 0,
                        if (PeriodDayPart.EVENING in enabledParts) countPickerEvening else 0
                    )
                    showCountPicker = false
                }
            }
        }
    }

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showSegmentStartPicker,
        title = "时段起点",
        onDismissRequest = { showSegmentStartPicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(popupBackdrop, state.config, 28.dp, centered = true)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            fun formatStartMinute(value: Int) = "%02d:%02d".format(value / 60, value % 60)
            val requestedStarts = mapOf(
                PeriodDayPart.MORNING to morningStartMinute,
                PeriodDayPart.NOON to noonStartMinute,
                PeriodDayPart.AFTERNOON to afternoonStartMinute,
                PeriodDayPart.EVENING to eveningStartMinute
            )
            val constrainedStarts = constrainAutomaticPartStarts(config, active, requestedStarts)
            val partSpans = enabledParts.associateWith { automaticPartSpanMinutes(config, active, it) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / enabledParts.size.coerceAtLeast(1)
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 100.dp || fontScale > 1.3f -> 16.sp
                        columnWidth < 140.dp || fontScale > 1.1f -> 19.sp
                        else -> 24.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    enabledParts.forEach { part ->
                        val partPosition = enabledParts.indexOf(part)
                        val previousPart = enabledParts.getOrNull(partPosition - 1)
                        val minimumMinute = previousPart?.let {
                            constrainedStarts.getValue(it) + partSpans.getValue(it)
                        }?.coerceIn(0, LastMinuteOfDay) ?: 0
                        val remainingSpan = enabledParts.drop(partPosition).sumOf { partSpans.getValue(it) }
                        val maximumMinute = (LastMinuteOfDay - remainingSpan)
                            .coerceAtLeast(minimumMinute)
                            .coerceAtMost(LastMinuteOfDay)
                        val value = constrainedStarts.getValue(part).coerceIn(minimumMinute, maximumMinute)
                        val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = value,
                                onValueChange = { changed ->
                                    when (part) {
                                        PeriodDayPart.MORNING -> morningStartMinute = changed
                                        PeriodDayPart.NOON -> noonStartMinute = changed
                                        PeriodDayPart.AFTERNOON -> afternoonStartMinute = changed
                                        PeriodDayPart.EVENING -> eveningStartMinute = changed
                                    }
                                },
                                range = minimumMinute..maximumMinute,
                                visibleItemCount = 3,
                                label = { minute -> "%02d:%02d".format(minute / 60, minute % 60) },
                                textStyle = pickerStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = 48.dp) {
                    showSegmentStartPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = 48.dp) {
                    val candidate = normalizeAutoSchemeStarts(config, active.copy(
                        scheme = active.scheme.copy(
                            morningStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.MORNING] ?: morningStartMinute),
                            noonStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.NOON] ?: noonStartMinute),
                            afternoonStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.AFTERNOON] ?: afternoonStartMinute),
                            eveningStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.EVENING] ?: eveningStartMinute)
                        )
                    ))
                    val conflict = validateResolvedPeriodTimes(candidate.times)
                    if (conflict != null) timeConflictMessage = conflict else {
                        updateActive { candidate }
                        showSegmentStartPicker = false
                    }
                }
            }
        }
    }

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showTimePicker,
        title = if (editingPeriod > 0) "编辑第 $editingPeriod 节" else "设置时段起点",
        onDismissRequest = { showTimePicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(popupBackdrop, state.config, 28.dp, centered = true)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val resolvedPickerTimes = resolveSchemeTimes(config, active).sortedBy { it.periodIndex }
            val editingPosition = resolvedPickerTimes.indexOfFirst { it.periodIndex == editingPeriod }
            val pickerBounds = periodTimePickerBounds(
                previousEnd = resolvedPickerTimes.getOrNull(editingPosition - 1)?.endTime,
                nextStart = resolvedPickerTimes.getOrNull(editingPosition + 1)?.startTime
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnCount = if (editingPeriod > 0) 4 else 2
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / columnCount
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 68.dp || fontScale > 1.3f -> 17.sp
                        columnWidth < 84.dp || fontScale > 1.12f -> 20.sp
                        else -> 25.sp
                    }
                )
                if (editingPeriod > 0) {
                    ConstrainedPeriodTimePickers(
                        startMinute = pickerStartHour * 60 + pickerStartMinute,
                        endMinute = pickerEndHour * 60 + pickerEndMinute,
                        bounds = pickerBounds,
                        onSelectionChange = { selection ->
                            pickerStartHour = selection.startMinute / 60
                            pickerStartMinute = selection.startMinute % 60
                            pickerEndHour = selection.endMinute / 60
                            pickerEndMinute = selection.endMinute % 60
                        },
                        textStyle = pickerStyle,
                        showSectionLabels = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        top.yukonga.miuix.kmp.basic.NumberPicker(value = pickerStartHour, onValueChange = { pickerStartHour = it }, range = 0..23, visibleItemCount = 3, label = { "%02d时".format(it) }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                        top.yukonga.miuix.kmp.basic.NumberPicker(value = pickerStartMinute, onValueChange = { pickerStartMinute = it }, range = 0..59, visibleItemCount = 3, label = { "%02d分".format(it) }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = 48.dp) { showTimePicker = false }
                if (editingPeriod > 0 && editingPeriod in active.overriddenPeriods) {
                    QuickSheetLiquidAction("恢复自动", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = 48.dp) {
                        updateActive { it.copy(overriddenPeriods = it.overriddenPeriods - editingPeriod).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                        showTimePicker = false
                    }
                }
                if (editingPeriod > 0) {
                    QuickSheetLiquidAction("删除", true, popupBackdrop, state.config, destructive = true, modifier = Modifier.weight(1f), height = 48.dp) {
                        deletePeriod(editingPeriod); showTimePicker = false
                    }
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = 48.dp) {
                    if (editingPeriod > 0) {
                        val selection = constrainPeriodTimeSelection(
                            pickerStartHour * 60 + pickerStartMinute,
                            pickerEndHour * 60 + pickerEndMinute,
                            pickerBounds
                        )
                        val start = "%02d:%02d".format(selection.startMinute / 60, selection.startMinute % 60)
                        val end = "%02d:%02d".format(selection.endMinute / 60, selection.endMinute % 60)
                        val updated = active.times.filterNot { it.periodIndex == editingPeriod } +
                            PeriodSchemeTimeEntity(active.scheme.id, editingPeriod, start, end)
                        val candidate = active.copy(
                            times = updated.sortedBy { it.periodIndex },
                            overriddenPeriods = if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) active.overriddenPeriods + editingPeriod else active.overriddenPeriods
                        )
                        val conflict = validateResolvedPeriodTimes(resolveSchemeTimes(config, candidate))
                        if (conflict != null) {
                            timeConflictMessage = conflict
                        } else {
                            updateActive { candidate }
                            showTimePicker = false
                        }
                    } else editingPart?.let { part ->
                        val start = "%02d:%02d".format(pickerStartHour, pickerStartMinute)
                        val scheme = when (part) {
                            PeriodDayPart.MORNING -> active.scheme.copy(morningStartTime = start)
                            PeriodDayPart.NOON -> active.scheme.copy(noonStartTime = start)
                            PeriodDayPart.AFTERNOON -> active.scheme.copy(afternoonStartTime = start)
                            PeriodDayPart.EVENING -> active.scheme.copy(eveningStartTime = start)
                        }
                        val candidate = active.copy(scheme = scheme).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) }
                        val conflict = validateResolvedPeriodTimes(candidate.times)
                        if (conflict != null) {
                            timeConflictMessage = conflict
                        } else {
                            updateActive { candidate }
                            showTimePicker = false
                        }
                    }
                }
            }
        }
    }

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = showBreakPicker,
        title = "特殊课间",
        onDismissRequest = { showBreakPicker = false },
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(popupBackdrop, state.config, 28.dp, centered = true)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / 2
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 120.dp || fontScale > 1.3f -> 19.sp
                        columnWidth < 150.dp || fontScale > 1.12f -> 22.sp
                        else -> 27.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = breakPickerPosition.coerceIn(0, specialBreakCandidates.lastIndex.coerceAtLeast(0)),
                        onValueChange = { position ->
                            breakPickerPosition = position
                            specialBreakCandidates.getOrNull(position)?.let { breakAfter = it }
                        },
                        range = 0..specialBreakCandidates.lastIndex.coerceAtLeast(0),
                        visibleItemCount = 3,
                        label = { position -> specialBreakCandidates.getOrNull(position)?.let { "第${it}节后" } ?: "无可用位置" },
                        textStyle = pickerStyle,
                        modifier = Modifier.weight(1f)
                    )
                    top.yukonga.miuix.kmp.basic.NumberPicker(value = breakMinutes, onValueChange = { breakMinutes = it }, range = 0..120, visibleItemCount = 3, label = { "${it}分钟" }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickSheetLiquidAction("删除", true, popupBackdrop, state.config, destructive = true, modifier = Modifier.weight(1f), height = 48.dp) {
                    updateActive { it.copy(specialBreaks = it.specialBreaks - breakAfter).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                    showBreakPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = 48.dp) {
                    updateActive { it.copy(specialBreaks = it.specialBreaks + (breakAfter to breakMinutes)).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                    showBreakPicker = false
                }
            }
        }
    }

    pendingAutoSwitch?.let { candidate ->
        LiquidAlertDialog(
            title = "保留手动调整？",
            message = "检测到 ${pendingAutoOverrides.size} 个节次与自动匹配结果不同。你可以把它们保留为局部微调，之后自动匹配只重算其余节次；也可以按当前自动参数重新生成整套时间。",
            actions = listOf(
                LiquidAlertAction("取消切换", LiquidAlertActionStyle.Secondary) {
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                },
                LiquidAlertAction("重新匹配", LiquidAlertActionStyle.Destructive) {
                    val rebuilt = candidate.copy(
                        overriddenPeriods = emptySet(),
                        times = resolveSchemeTimes(config, candidate.copy(overriddenPeriods = emptySet()))
                    )
                    updateActive { rebuilt }
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                },
                LiquidAlertAction("保留为微调", LiquidAlertActionStyle.Primary) {
                    val preserved = candidate.copy(
                        times = active.times,
                        overriddenPeriods = pendingAutoOverrides
                    )
                    updateActive { preserved }
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                }
            ),
            backdrop = popupBackdrop,
            config = state.config,
            onDismissRequest = {
                pendingAutoSwitch = null
                pendingAutoOverrides = emptySet()
            }
        )
    }

    timeConflictMessage?.let { conflict ->
        LiquidAlertDialog(
            title = "节次时间冲突",
            message = "$conflict。请调整当前节次或相邻节次后再确认。",
            actions = listOf(
                LiquidAlertAction("继续调整", LiquidAlertActionStyle.Primary) {
                    timeConflictMessage = null
                }
            ),
            backdrop = popupBackdrop,
            config = state.config,
            onDismissRequest = { timeConflictMessage = null }
        )
    }
}

@Composable
fun ScheduleConfigScreen(
    state: AppState,
    backdrop: Backdrop?,
    section: SettingsSection,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onPreviewLiveUpdate: () -> Unit,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val saveScope = rememberCoroutineScope()
    val visualState = state.copy(config = settingsVisualConfig(state.config))
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
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
    var morningPeriodCount by remember { mutableIntStateOf(state.config.morningPeriodCount) }
    var noonPeriodCount by remember { mutableIntStateOf(state.config.noonPeriodCount) }
    var afternoonPeriodCount by remember { mutableIntStateOf(state.config.afternoonPeriodCount) }
    var eveningPeriodCount by remember { mutableIntStateOf(state.config.eveningPeriodCount) }
    var periods by remember { mutableStateOf(state.periods) }
    var schemeDraft by remember(state.config.id) { mutableStateOf<SchedulePeriodSchemesDraft?>(null) }
    var lastSavedSchemeDraft by remember(state.config.id) { mutableStateOf<SchedulePeriodSchemesDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showExitSaveConfirm by remember { mutableStateOf(false) }
    var showCourseRemapConfirm by remember { mutableStateOf(false) }
    var pendingRemapSaveCompletion by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
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
        morningPeriodCount = state.config.morningPeriodCount
        noonPeriodCount = state.config.noonPeriodCount
        afternoonPeriodCount = state.config.afternoonPeriodCount
        eveningPeriodCount = state.config.eveningPeriodCount
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
            morningPeriodCount != lastSavedConfig.morningPeriodCount ||
            noonPeriodCount != lastSavedConfig.noonPeriodCount ||
            afternoonPeriodCount != lastSavedConfig.afternoonPeriodCount ||
            eveningPeriodCount != lastSavedConfig.eveningPeriodCount ||
            schemeDraft != lastSavedSchemeDraft ||
            periods != lastSavedPeriods
    }

    LaunchedEffect(state.config.id, state.config, state.periods) {
        if (state.config.id != currentDraftScheduleId || !computeDirty()) {
            resetConfigDraftFromState()
        }
    }
    LaunchedEffect(state.config.id, section) {
        if (section != SettingsSection.Schedule) {
            schemeDraft = null
            lastSavedSchemeDraft = null
            return@LaunchedEffect
        }
        runCatching { repository.loadPeriodSchemes(state.config.id) }
            .onSuccess {
                schemeDraft = it
                lastSavedSchemeDraft = it
                val active = it.schemes.firstOrNull { scheme -> scheme.scheme.id == it.activeSchemeId }
                if (active != null) {
                    // Stored scheme times are authoritative. Merely opening this page must not
                    // regenerate an AUTO_MATCH scheme with newly introduced defaults.
                    periods = active.times.sortedBy { time -> time.periodIndex }
                        .map { time -> PeriodEntity(time.periodIndex, time.startTime, time.endTime, state.config.id) }
                }
            }
            .onFailure { error = it.message ?: "作息方案加载失败" }
    }
    val detectedWeek = remember(autoCurrentWeek, termStartDate, totalWeeks, currentWeek) {
        val total = totalWeeks.toIntOrNull() ?: state.config.totalWeeks
        val manual = currentWeek.toIntOrNull() ?: state.config.currentWeek
        effectiveCurrentWeek(state.config.copy(totalWeeks = total.coerceAtLeast(1), currentWeek = manual.coerceAtLeast(1), termStartDate = termStartDate.ifBlank { null }, autoCurrentWeek = true))
    }
    val detectedWeekDescription = remember(autoCurrentWeek, termStartDate, totalWeeks, currentWeek, detectedWeek) {
        if (!autoCurrentWeek) {
            "学期状态：手动设置 · 第 ${currentWeek.toIntOrNull() ?: state.config.currentWeek} 周"
        } else {
            val total = totalWeeks.toIntOrNull() ?: state.config.totalWeeks
            val manual = currentWeek.toIntOrNull() ?: state.config.currentWeek
            val draftConfig = state.config.copy(
                totalWeeks = total.coerceAtLeast(1),
                currentWeek = manual.coerceAtLeast(1),
                termStartDate = termStartDate.ifBlank { null },
                autoCurrentWeek = true
            )
            "学期状态：${scheduleTermStatusDescription(draftConfig, LocalDate.now())}"
        }
    }
    val displayedCurrentWeek = if (autoCurrentWeek) detectedWeek.toString() else currentWeek
    val dirty = computeDirty()

    fun resetConfigDraft() {
        resetConfigDraftFromState()
        schemeDraft = lastSavedSchemeDraft
    }

    fun saveConfigDraft(
        onFinished: ((Boolean) -> Unit)? = null,
        remapConfirmed: Boolean = false
    ) {
        if (saving) {
            if (onFinished != null) {
                saveScope.launch {
                    snapshotFlow { saving }.first { !it }
                    if (computeDirty()) saveConfigDraft(onFinished, remapConfirmed)
                    else onFinished(true)
                }
            }
            return
        }
        val needsCourseRemap = schemeDraft?.topologyOperations?.isNotEmpty() == true &&
            state.courses.any { it.periods.isNotEmpty() }
        if (needsCourseRemap && !remapConfirmed) {
            pendingRemapSaveCompletion = onFinished
            showCourseRemapConfirm = true
            return
        }
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
            validateResolvedPeriodTimes(
                nextPeriods.map { PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime) }
            )?.let { throw IllegalArgumentException(it) }
            // Keep the stored manual week as a fallback. The visible automatic week
            // is derived from the date at render time and must not turn an upcoming
            // term into a persisted "week 1" merely because settings were saved.
            val storedCurrentWeek = current
            error = null
            val nextConfig = state.config.copy(
                totalWeeks = total,
                currentWeek = storedCurrentWeek,
                notificationLeadMinutes = lead,
                termStartDate = termStartDate.ifBlank { null },
                autoCurrentWeek = autoCurrentWeek,
                hideEmptyWeekends = hideEmptyWeekends,
                notificationsEnabled = notificationsEnabled,
                notificationMode = notificationMode,
                liveUpdateChipTextMode = liveUpdateChipTextMode,
                classDurationMinutes = classDuration,
                breakDurationMinutes = breakDuration
                ,morningPeriodCount = morningPeriodCount
                ,noonPeriodCount = noonPeriodCount
                ,afternoonPeriodCount = afternoonPeriodCount
                ,eveningPeriodCount = eveningPeriodCount
            )
            currentWeek = storedCurrentWeek.toString()
            periods = nextPeriods
            val currentSchemes = schemeDraft
            if (currentSchemes != null) {
                val active = currentSchemes.schemes.firstOrNull { it.scheme.id == currentSchemes.activeSchemeId }
                    ?: currentSchemes.schemes.first()
                val previousActive = lastSavedSchemeDraft?.schemes
                    ?.firstOrNull { it.scheme.id == active.scheme.id }
                val activePeriods = resolveSchemeTimesForSave(
                    config = nextConfig,
                    draft = active,
                    storedConfig = lastSavedConfig,
                    storedDraft = previousActive
                ).map {
                    PeriodEntity(it.periodIndex, it.startTime, it.endTime, nextConfig.id)
                }
                currentSchemes.schemes.forEach { item ->
                    val previous = lastSavedSchemeDraft?.schemes
                        ?.firstOrNull { it.scheme.id == item.scheme.id }
                    validateResolvedPeriodTimes(
                        resolveSchemeTimesForSave(nextConfig, item, lastSavedConfig, previous)
                    )?.let {
                        throw IllegalArgumentException("${item.scheme.name}：$it")
                    }
                }
                saving = true
                saveScope.launch {
                    runCatching { repository.saveScheduleDetail(nextConfig, currentSchemes) }
                        .onSuccess {
                            periods = activePeriods
                            onSave(nextConfig, activePeriods)
                            lastSavedConfig = nextConfig
                            lastSavedPeriods = activePeriods
                            lastSavedSchemeDraft = currentSchemes.copy(topologyOperations = emptyList())
                            schemeDraft = currentSchemes.copy(topologyOperations = emptyList())
                            onFinished?.invoke(true)
                        }
                        .onFailure {
                            error = it.message ?: "设置保存失败"
                            onFinished?.invoke(false)
                        }
                    saving = false
                }
            } else {
                onSave(nextConfig, nextPeriods)
                lastSavedConfig = nextConfig
                lastSavedPeriods = nextPeriods
                onFinished?.invoke(true)
            }
        } catch (t: Throwable) {
            error = t.message ?: "设置保存失败"
            onFinished?.invoke(false)
        }
    }

    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest <= 0) return@LaunchedEffect
        when (section) {
            SettingsSection.Schedule -> {
                if (computeDirty()) showExitSaveConfirm = true else onExitCommitFinished(true)
            }
            SettingsSection.Notifications -> {
                // The page used to finish while its 250 ms debounce coroutine was still pending,
                // cancelling the save and making the controls appear to ignore changes.
                if (computeDirty() || saving) saveConfigDraft(onExitCommitFinished)
                else onExitCommitFinished(true)
            }
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
        morningPeriodCount = morningPeriodCount,
        noonPeriodCount = noonPeriodCount,
        afternoonPeriodCount = afternoonPeriodCount,
        eveningPeriodCount = eveningPeriodCount,
        onPeriodCountsChange = { morning, noon, afternoon, evening ->
            morningPeriodCount = morning
            noonPeriodCount = noon
            afternoonPeriodCount = afternoon
            eveningPeriodCount = evening
        },
        schemeDraft = schemeDraft,
        onSchemeDraftChange = { updated ->
            schemeDraft = updated
            val active = updated.schemes.firstOrNull { it.scheme.id == updated.activeSchemeId }
            if (active != null) {
                val draftConfig = state.config.copy(
                    morningPeriodCount = morningPeriodCount,
                    noonPeriodCount = noonPeriodCount,
                    afternoonPeriodCount = afternoonPeriodCount,
                    eveningPeriodCount = eveningPeriodCount
                )
                periods = resolveSchemeTimes(draftConfig, active).map {
                    PeriodEntity(it.periodIndex, it.startTime, it.endTime, state.config.id)
                }
            }
        },
        onAutoMatchPeriodEnds = {
            val classDuration = classDurationMinutes.toIntOrNull() ?: state.config.classDurationMinutes
            val breakDuration = breakDurationMinutes.toIntOrNull() ?: state.config.breakDurationMinutes
            periods = autoMatchPeriodTimes(periods, classDuration, breakDuration)
        },
        periods = periods,
        onPeriodsChange = { periods = it },
        detectedWeek = detectedWeek,
        detectedWeekDescription = detectedWeekDescription,
        dirty = false,
        error = error,
        onReset = {},
        onSave = {},
        onPreviewLiveUpdate = onPreviewLiveUpdate
    )

    if (showExitSaveConfirm) {
        LiquidAlertDialog(
            title = "保存课表设置？",
            message = "你修改了节次结构或作息时间。保存完成后才会退出详细设置。",
            actions = listOf(
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) {
                    showExitSaveConfirm = false
                },
                LiquidAlertAction("不保存", LiquidAlertActionStyle.Destructive) {
                    showExitSaveConfirm = false
                    onExitCommitFinished(true)
                },
                LiquidAlertAction("保存并退出", LiquidAlertActionStyle.Primary) {
                    showExitSaveConfirm = false
                    saveConfigDraft(onExitCommitFinished)
                }
            ),
            backdrop = popupBackdrop,
            config = visualState.config,
            onDismissRequest = { showExitSaveConfirm = false }
        )
    }

    if (showCourseRemapConfirm) {
        val affectedCourseCount = state.courses.count { it.periods.isNotEmpty() }
        LiquidAlertDialog(
            title = "重映射课程节次？",
            message = "节次结构已经改变。保存后会按照 $affectedCourseCount 门课程在修改前作息中的实际时间，映射到新时间线中重叠或时间最接近的节次；课程名称、星期和周次不会改变。",
            actions = listOf(
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) {
                    showCourseRemapConfirm = false
                    pendingRemapSaveCompletion = null
                },
                LiquidAlertAction("确认并保存", LiquidAlertActionStyle.Primary) {
                    val completion = pendingRemapSaveCompletion
                    pendingRemapSaveCompletion = null
                    showCourseRemapConfirm = false
                    saveConfigDraft(completion, remapConfirmed = true)
                }
            ),
            backdrop = popupBackdrop,
            config = visualState.config,
            onDismissRequest = {
                showCourseRemapConfirm = false
                pendingRemapSaveCompletion = null
            }
        )
    }
}

private fun autoMatchPeriodTimes(
    periods: List<PeriodEntity>,
    classDurationMinutes: Int,
    breakDurationMinutes: Int,
    longBreaks: List<Pair<Int, Int>> = emptyList()
): List<PeriodEntity> {
    val duration = classDurationMinutes.coerceIn(1, 300).toLong()
    val defaultBreak = breakDurationMinutes.coerceIn(0, 300).toLong()
    val lbMap = longBreaks.toMap()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    var cursor = periods.firstOrNull()?.startTime?.let {
        runCatching { ScheduleImportParser.parseTimeForUi(it) }.getOrNull()
    }
    return periods.map { period ->
        val start = cursor ?: runCatching { ScheduleImportParser.parseTimeForUi(period.startTime) }.getOrNull()
        if (start == null) return@map period
        val end = start.plusMinutes(duration)
        val gap = (lbMap[period.periodIndex] ?: defaultBreak.toInt()).toLong()
        cursor = end.plusMinutes(gap)
        period.copy(
            startTime = start.format(formatter),
            endTime = end.format(formatter)
        )
    }
}

fun openKeepAliveSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
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
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun weekdayLabel(weekday: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]
