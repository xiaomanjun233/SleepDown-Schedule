package com.example.courseschedule

import android.Manifest
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

internal val LocalAgentBackgroundCaptureMask = staticCompositionLocalOf<() -> Boolean> { { false } }

private data class AgentSourceHandoffTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
)

/**
 * The day page is disposed while the week page is entering. Keep the expensive, immutable Agent
 * projection outside that short-lived composition so switching back does not decode the generated
 * JSON and scan/sort the whole timetable inside the transition frame budget again.
 */
private object DayAgentRenderCache {
    private data class FactsKey(
        val scheduleId: Int,
        val date: LocalDate,
        val coursesHash: Int,
        val periodsHash: Int,
        val configHash: Int,
        val scheduleName: String?,
        val weatherHash: Int
    )

    private val facts = object : LinkedHashMap<FactsKey, DayAgentFacts>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FactsKey, DayAgentFacts>?): Boolean =
            size > 6
    }
    private val packs = object : LinkedHashMap<String, DailyAgentPack>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DailyAgentPack>?): Boolean =
            size > 6
    }
    private data class RenderKey(
        val packIdentity: String?,
        val sourceHash: String,
        val minute: LocalDateTime,
        val weatherHash: Int
    )
    private val renderedMessages = object : LinkedHashMap<RenderKey, RenderedAgentMessage>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RenderKey, RenderedAgentMessage>?): Boolean =
            size > 12
    }
    private var lastCleanupDate: LocalDate? = null

    @Synchronized
    fun facts(
        state: AppState,
        date: LocalDate,
        weather: AgentWeatherSnapshot?,
        scheduleName: String?,
        context: android.content.Context
    ): DayAgentFacts {
        val key = FactsKey(
            scheduleId = state.config.id,
            date = date,
            coursesHash = state.courses.hashCode(),
            periodsHash = state.periods.hashCode(),
            configHash = state.config.hashCode(),
            scheduleName = scheduleName,
            weatherHash = weather.hashCode()
        )
        return facts.getOrPut(key) {
            buildDayAgentFacts(
                state.courses,
                state.periods,
                state.config,
                date,
                weather,
                scheduleName,
                settingContext = context.applicationContext
            )
        }
    }

    @Synchronized
    fun pack(json: String?): DailyAgentPack {
        if (json.isNullOrBlank()) return DailyAgentPack()
        return packs.getOrPut(json) { DailyAgentPack.decodeOrDefault(json) }
    }

    @Synchronized
    fun rendered(json: String?, pack: DailyAgentPack, facts: DayAgentFacts): RenderedAgentMessage {
        val minute = facts.now.withSecond(0).withNano(0)
        val key = RenderKey(
            packIdentity = json,
            sourceHash = facts.sourceHash,
            minute = minute,
            weatherHash = facts.weather.hashCode()
        )
        return renderedMessages.getOrPut(key) { TodayAgentTimelineEngine.render(pack, facts) }
    }

    @Synchronized
    fun shouldCleanup(date: LocalDate): Boolean {
        if (lastCleanupDate == date) return false
        lastCleanupDate = date
        return true
    }
}

@Stable
class DayAgentBackgroundMotionState {
    val progress = Animatable(0f)
    // Same idea as the course editor: the background depth (scale + blur) runs on a slower,
    // longer timeline than the card morph so it keeps settling after the card has opened or
    // closed, reading as inertial pull on the home surface. Here the surface recedes (1 -> 0.92)
    // instead of pushing in, matching the existing depth semantics.
    val backgroundZoom = Animatable(1f)
}

// Range and durations for the inertial background zoom. The zoom shares one curve with the
// blur so the two never drift apart, exactly like CourseEditorContainerOverlay. The surface
// pushes in (1 -> 1.08) as the agent opens — same direction and magnitude as the course editor.
internal const val DayAgentBackgroundZoomRestScale = 1.08f
internal const val DayAgentBackgroundZoomOpenDurationMillis = 620
internal const val DayAgentBackgroundZoomCloseDurationMillis = 620

private val AgentMorphPositionEasing = CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
private val AgentMorphSizeEasing = CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
private val AgentMorphClosePositionEasing = CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)
private const val AgentMorphOpenDurationMillis = 520
private const val AgentMorphCloseDurationMillis = 520

private data class DayAgentCardVisual(
    val activityLabel: String,
    val courseName: String?,
    val countdownText: String,
    val locationText: String,
    val focusTimeText: String,
    val courseCountText: String,
    val weatherText: String,
    val trailingStatus: String?,
    val weatherAlert: Boolean,
    val collapsed: Boolean,
    val cardIsDark: Boolean
)

@Composable
internal fun rememberDayAgentBackgroundMotionState(): DayAgentBackgroundMotionState =
    remember { DayAgentBackgroundMotionState() }

@Composable
fun TodayAgentHost(
    state: AppState,
    date: LocalDate,
    backdrop: Backdrop?,
    textColor: Color,
    collapsed: Boolean,
    isActive: Boolean = true,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onPrepareOpen: suspend () -> Unit = {},
    onAgentDismissed: () -> Unit = {},
    onAgentAction: AgentActionHandler = { _, _ -> }
) {
    val context = LocalContext.current
    var hasApiKey by remember { mutableStateOf(AiImportSettingsStore.load(context).apiKey.isNotBlank()) }
    var enabled by remember(date, state.config.id) { mutableStateOf(DayAgentPreferences.isEnabled(context)) }
    var hasDecision by remember(date, state.config.id) { mutableStateOf(DayAgentPreferences.hasDecision(context)) }
    val preferenceVersion by DayAgentPreferences.changes.collectAsStateWithLifecycle(initialValue = 0L)

    LaunchedEffect(preferenceVersion) {
        enabled = DayAgentPreferences.isEnabled(context)
        hasDecision = DayAgentPreferences.hasDecision(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = DayAgentPreferences.isEnabled(context)
        hasDecision = DayAgentPreferences.hasDecision(context)
        hasApiKey = AiImportSettingsStore.load(context).apiKey.isNotBlank()
    }

    if (!hasDecision) {
        LiquidAlertDialog(
            title = "启用今日 Agent？",
            message = "今日助手会在日视图显示下一节课、实时倒计时与天气。课程和倒计时均在本地计算；绑定 API Key 后，点击卡片可以继续对话。你可以随时在今日助手设置中关闭。",
            actions = listOf(
                LiquidAlertAction("暂不启用", LiquidAlertActionStyle.Secondary) {
                    DayAgentPreferences.setEnabled(context, false)
                    enabled = false
                    hasDecision = true
                },
                LiquidAlertAction("启用", LiquidAlertActionStyle.Primary) {
                    DayAgentPreferences.setEnabled(context, true)
                    enabled = true
                    hasDecision = true
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = {
                DayAgentPreferences.setEnabled(context, false)
                enabled = false
                hasDecision = true
            }
        )
    }

    if (enabled) {
        TodayAgentCard(
            state = state,
            date = date,
            backdrop = backdrop,
            textColor = textColor,
            collapsed = collapsed,
            isActive = isActive,
            weatherEnabled = DayAgentPreferences.isWeatherEnabled(context),
            hasApiKey = hasApiKey,
            backgroundMotionState = backgroundMotionState,
            onPrepareOpen = onPrepareOpen,
            onAgentDismissed = onAgentDismissed,
            onAgentAction = onAgentAction
        )
    }
}

@Composable
fun TodayAgentCard(
    state: AppState,
    date: LocalDate,
    backdrop: Backdrop?,
    textColor: Color,
    collapsed: Boolean,
    isActive: Boolean,
    weatherEnabled: Boolean,
    hasApiKey: Boolean,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onPrepareOpen: suspend () -> Unit,
    onAgentDismissed: () -> Unit,
    onAgentAction: AgentActionHandler
) {
    val context = LocalContext.current
    val scheduleId = state.config.id
    val scheduleName = state.schedules.firstOrNull { it.id == scheduleId }?.name
    val repository = remember(context) { DayAgentRepository(context.applicationContext) }
    val weatherRepository = remember(context) { DayAgentWeatherRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var weather by remember(date, weatherEnabled) {
        mutableStateOf(if (weatherEnabled) DayAgentWeatherStore.load(context) else null)
    }
    var now by remember(date) { mutableStateOf(LocalDateTime.now()) }
    var dialogOpen by remember(date, scheduleId) { mutableStateOf(false) }
    var dialogOpening by remember(date, scheduleId) { mutableStateOf(false) }
    val messageFlow = remember(repository, scheduleId, date, dialogOpen) {
        if (dialogOpen) repository.observeMessages(scheduleId, date) else flowOf(emptyList())
    }
    val messages by messageFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var sourceCardHidden by remember(date, scheduleId) { mutableStateOf(false) }
    var sourceHandoffTransform by remember(date, scheduleId) {
        mutableStateOf<AgentSourceHandoffTransform?>(null)
    }
    val sourceHandoffProgress = remember(date, scheduleId) { Animatable(0f) }
    var pendingQuestion by remember(date, scheduleId) { mutableStateOf<String?>(null) }
    var cardBounds by remember(date, scheduleId) { mutableStateOf<Rect?>(null) }
    var dialogSourceBounds by remember(date, scheduleId) { mutableStateOf<Rect?>(null) }
    var showApiKeyHint by remember(date, scheduleId, hasApiKey) { mutableStateOf(!hasApiKey) }
    val suppressForAgentBackgroundCapture = LocalAgentBackgroundCaptureMask.current
    val sourceCardInteractionSource = remember { MutableInteractionSource() }
    val sourceCardPressed by sourceCardInteractionSource.collectIsPressedAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { weather = weatherRepository.getWeather(forceRefresh = true) }
        }
    }

    LaunchedEffect(date) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val staticFacts = remember(state.courses, state.periods, state.config, scheduleName, date, weather) {
        DayAgentRenderCache.facts(state, date, weather, scheduleName, context)
    }
    LaunchedEffect(date, scheduleId, hasApiKey, isActive) {
        showApiKeyHint = isActive && !hasApiKey
        if (isActive && !hasApiKey) {
            delay(5_000L)
            showApiKeyHint = false
        }
    }
    val facts = remember(staticFacts, now) {
        staticFacts.copy(now = now)
    }
    LaunchedEffect(scheduleId, date, weatherEnabled) {
        // Weather is the only asynchronous home-card content. Daily AI copy is intentionally not
        // generated or observed here; the model is used only after the user opens a conversation.
        delay(280)
        if (DayAgentRenderCache.shouldCleanup(date)) repository.cleanup(date)
        if (weatherEnabled && weather == null) weather = weatherRepository.getWeather()
    }
    val foreground = LocalAdaptiveGlass.current.contentColor
    val cardIsDark = !glassUsesLightStyle(state.config)
    val activityAccent = if (cardIsDark) Color(0xFF62B5FF) else Color(0xFF006EDC)
    val currentSlot = remember(facts.today, now) {
        facts.today.firstOrNull { !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end) }
    }
    val nextSlot = remember(facts.today, now) {
        facts.today.firstOrNull { now.toLocalTime().isBefore(it.start) }
    }
    val previewTomorrow = now.toLocalDate() == date &&
        now.toLocalTime() >= LocalTime.of(22, 0) &&
        currentSlot == null &&
        nextSlot == null &&
        facts.tomorrow.isNotEmpty()
    val focusSlot = currentSlot ?: nextSlot ?: facts.tomorrow.firstOrNull().takeIf { previewTomorrow }
    val remainingMinutes = remember(currentSlot, nextSlot, now) {
        val target = currentSlot?.end ?: nextSlot?.start
        target?.let { Duration.between(now.toLocalTime(), it).toMinutes().coerceAtLeast(0) }
    }
    val activityLabel = when {
        currentSlot != null -> "当前"
        nextSlot != null -> "下节课"
        previewTomorrow -> "明日首课"
        facts.today.isEmpty() -> "今日无课"
        else -> "课程已结束"
    }
    val countdownText = when {
        currentSlot != null && remainingMinutes != null -> "${remainingMinutes} 分钟后下课"
        nextSlot != null && remainingMinutes != null && now.toLocalTime() >= LocalTime.of(6, 0) ->
            "${remainingMinutes} 分钟后"
        previewTomorrow -> ""
        facts.today.isEmpty() -> "轻松一天"
        else -> "今日完成"
    }
    val locationText = focusSlot?.course?.let { course ->
        listOfNotNull(course.location?.takeIf(String::isNotBlank), course.teacher?.takeIf(String::isNotBlank)).joinToString(" | ")
    }.orEmpty()
    val focusTimeText = focusSlot?.let {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        "${it.start.format(formatter)} - ${it.end.format(formatter)}"
    }.orEmpty()
    val weatherText = when {
        !weatherEnabled -> "天气未启用"
        weather != null -> {
            val icon = weatherEmoji(weather!!.summary)
            val condition = weather!!.summary.substringBefore('，')
            "$icon ${weather!!.temperature}°C $condition"
        }
        !weatherRepository.hasLocationPermission() -> "📍 点击开启天气"
        else -> "天气加载中"
    }
    val weatherAlertText = weather?.let(::weatherAlertText)
    val assistantHintText = if (weatherAlertText == null) {
        when {
            hasApiKey -> "点击卡片和助手对话"
            showApiKeyHint -> "绑定 API Key 后可以启用更多智慧功能"
            else -> null
        }
    } else null
    val cardVisual = DayAgentCardVisual(
        activityLabel = activityLabel,
        courseName = focusSlot?.course?.name,
        countdownText = countdownText,
        locationText = locationText,
        focusTimeText = focusTimeText,
        courseCountText = if (previewTomorrow) {
            "明天有 ${facts.tomorrow.size} 节课"
        } else {
            "今天有 ${facts.today.size} 节课"
        },
        weatherText = weatherText,
        trailingStatus = weatherAlertText?.let { "⚠️ $it" } ?: assistantHintText,
        weatherAlert = weatherAlertText != null,
        collapsed = collapsed,
        cardIsDark = cardIsDark
    )
    val conversationInitialText = remember(facts.today, facts.tomorrow, focusSlot, weather, previewTomorrow) {
        when {
            previewTomorrow && focusSlot != null ->
                "明天有 ${facts.tomorrow.size} 节课，最早一节是${focusSlot.course.name}${locationText.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}。"
            focusSlot != null -> "今天有 ${facts.today.size} 节课，${activityLabel}是${focusSlot.course.name}${locationText.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}。"
            facts.today.isEmpty() -> "今天没有课程。"
            else -> "今天的课程已经结束。"
        }
    }

    fun openConversation(question: String?) {
        if (!hasApiKey || dialogOpen || dialogOpening) return
        dialogOpening = true
        scope.launch {
            try {
                // Keep the pager-settled barrier, but do not perform a GPU bitmap readback. The
                // overlay now redraws the real compact card shell at the measured source bounds.
                onPrepareOpen()
                var resolvedBounds = cardBounds?.takeIf { it.width > 2f && it.height > 2f }
                repeat(8) {
                    if (resolvedBounds == null) {
                        withFrameNanos { }
                        resolvedBounds = cardBounds?.takeIf { it.width > 2f && it.height > 2f }
                    }
                }
                val frozenBounds = resolvedBounds ?: return@launch
                dialogSourceBounds = frozenBounds
                sourceHandoffTransform = null
                sourceHandoffProgress.snapTo(0f)
                pendingQuestion = question
                dialogOpen = true
            } finally {
                dialogOpening = false
            }
        }
    }

    val cardShape = RoundedCornerShape(if (collapsed) 26.dp else 28.dp)
    val cardTokens = GlassTokens.dialog(intensity = 1.12f).copy(
        blur = if (cardIsDark) 10.dp else 12.dp,
        lensHeight = 18.dp,
        lensAmount = 34.dp,
        surfaceAlpha = if (cardIsDark) 0.54f else 0.58f,
        borderAlpha = 0.34f,
        highlightAlpha = 0.075f,
        depthEffect = true,
        chromaticAberration = false
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionOnScreen()
                cardBounds = Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + coordinates.size.width,
                    bottom = position.y + coordinates.size.height
                )
            }
            .drawWithContent {
                if (!suppressForAgentBackgroundCapture()) {
                    drawContent()
                }
            }
            .graphicsLayer {
                alpha = if (sourceCardHidden) 0f else 1f
                sourceHandoffTransform?.let { handoff ->
                    val settle = sourceHandoffProgress.value.coerceIn(0f, 1f)
                    scaleX = handoff.scaleX + (1f - handoff.scaleX) * settle
                    scaleY = handoff.scaleY + (1f - handoff.scaleY) * settle
                    translationX = handoff.translationX * (1f - settle)
                    translationY = handoff.translationY * (1f - settle)
                }
            }
            // Keep source geometry stable for the first/last Morph frame. The generic Liquid
            // interaction expands the card by 4dp while pressed, but layout bounds and the cached
            // source bitmap remain unscaled, which creates a visible size jump at handoff.
            .clickable(
                interactionSource = sourceCardInteractionSource,
                indication = null,
                onClick = {
                    if (hasApiKey) {
                        openConversation(null)
                    } else {
                        onAgentAction(
                            AgentPlan(
                                listOf(
                                    AgentValidatedAction(
                                        type = AgentValidatedActionType.OPEN_SETTINGS,
                                        settingsPage = "AI_IMPORT",
                                        summary = "绑定 API Key"
                                    )
                                )
                            ),
                            {}
                        )
                    }
                }
            )
    ) {
        // This is the one real glass shell. It is a sibling of the interactive content, so its
        // compact card-sized texture can safely feed the LiquidButtons without recursive capture.
        GlassSurface(
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.matchParentSize(),
            shape = cardShape,
            tokens = cardTokens
        ) {}
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = sourceHandoffTransform?.let {
                    // Keep the compact glass shell continuous at handoff, but do not squeeze the
                    // fully laid-out text into the still-oversized intermediate rectangle.
                    agentSmoothStep(
                        edge0 = 0.48f,
                        edge1 = 0.88f,
                        value = sourceHandoffProgress.value.coerceIn(0f, 1f)
                    )
                } ?: 1f
            }
        ) {
            DayAgentCardVisualContent(
                visual = cardVisual,
                foreground = foreground,
                activityAccent = activityAccent,
                modifier = Modifier.fillMaxWidth(),
                onWeatherClick = if (
                    weatherEnabled &&
                    weather == null &&
                    !weatherRepository.hasLocationPermission()
                ) {
                    { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                } else null
            )
            if (sourceCardPressed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Color.Black.copy(alpha = 0.13f),
                            RoundedCornerShape(if (collapsed) 26.dp else 28.dp)
                        )
                )
            }
        }
    }

    val frozenDialogSourceBounds = dialogSourceBounds
    if (dialogOpen && hasApiKey && frozenDialogSourceBounds != null) {
        DayAgentConversationDialog(
            state = state,
            backdrop = backdrop,
            facts = facts,
            messages = messages,
            repository = repository,
            initialText = conversationInitialText,
            initialQuestion = pendingQuestion,
            sourceBounds = frozenDialogSourceBounds,
            sourceCornerRadius = if (collapsed) 26.dp else 28.dp,
            sourceVisual = cardVisual,
            sourceForeground = foreground,
            sourceActivityAccent = activityAccent,
            sourceGlassTokens = cardTokens,
            backgroundMotionState = backgroundMotionState,
            onAgentAction = onAgentAction,
            onOverlayReady = { sourceCardHidden = true },
            onPrepareDismiss = {},
            onSourceHandoff = {
                sourceCardHidden = false
            },
            onDismiss = {
                dialogOpen = false
                sourceCardHidden = false
                pendingQuestion = null
                dialogSourceBounds = null
                sourceHandoffTransform = null
                scope.launch {
                    sourceHandoffProgress.snapTo(0f)
                }
                onAgentDismissed()
            }
        )
    }
}

private fun weatherEmoji(summary: String): String {
    // The suffix may contain precipitation advice/probability even when the current condition is
    // merely overcast. Only classify the actual condition segment before the first separator.
    val condition = summary.substringBefore('，').substringBefore(',').trim()
    return when {
    "雷" in condition -> "⛈️"
    "雪" in condition -> "🌨️"
    "雨" in condition -> "🌧️"
    "雾" in condition || "霾" in condition -> "🌫️"
    "阴" in condition -> "☁️"
    "云" in condition -> "⛅"
    "晴" in condition -> "☀️"
    else -> "🌤️"
    }
}

private fun weatherAlertText(weather: AgentWeatherSnapshot): String? {
    val condition = weather.summary.substringBefore('，').substringBefore(',').trim()
    val alerts = buildList {
        when {
            "雷" in condition -> add("雷暴提醒")
            "雪" in condition -> add("降雪提醒")
            "暴雨" in condition -> add("强降雨提醒")
            weather.precipitationProbability >= 60 -> add("降雨提醒")
        }
        if (weather.temperature >= 35 || weather.apparentTemperature >= 38) add("高温提醒")
        if (weather.temperature <= 5 || weather.apparentTemperature <= 2) add("低温提醒")
        if (weather.windSpeed >= 35) add("大风提醒")
    }
    return alerts.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun AgentSuggestionPill(
    text: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val foreground = LocalAdaptiveGlass.current.contentColor
    val isDarkTheme = appUsesDarkTheme(config)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 40.dp,
            surfaceColor = if (isDarkTheme) {
                Color.Black.copy(alpha = 0.16f)
            } else {
                Color.White.copy(alpha = 0.20f)
            },
            contentPadding = PaddingValues(horizontal = 15.dp),
            blurRadius = if (isDarkTheme) 6.dp else 8.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false
        ) {
            Text(
                text,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    } else {
        AgentSimplePressSurface(
            backdrop = null,
            config = config,
            modifier = modifier,
            shape = RoundedCornerShape(50),
            tokens = GlassTokens.pill(intensity = 0.92f).copy(
                blur = 0.dp,
                surfaceAlpha = if (isDarkTheme) 0.22f else 0.30f
            ),
            onClick = onClick
        ) {
            Text(
                text,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AgentOperationLiquidButton(
    text: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    destructive: Boolean,
    applied: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val textColor = when {
        applied -> Color.White.copy(alpha = 0.55f)
        else -> Color.White
    }
    val bgColor = when {
        destructive -> Color(0xFFFF453A)
        else -> Color(0xFF0A84FF)
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (!applied && enabled) onClick() },
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            surfaceColor = if (applied || !enabled) bgColor.copy(alpha = 0.35f) else bgColor.copy(alpha = 0.88f),
            tint = if (applied || !enabled) Color.Unspecified else bgColor,
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 6.dp,
            lensHeight = 14.dp,
            lensAmount = 20.dp
        ) {
            Text(
                text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val background = if (applied || !enabled) bgColor.copy(alpha = 0.30f) else bgColor
        Box(
            modifier = modifier
                .height(42.dp)
                .clip(RoundedCornerShape(50))
                .background(background.copy(alpha = if (applied) 0.46f else 0.94f))
                .clickable(enabled = !applied && enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AgentSimplePressSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape,
    tokens: GlassTokens,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        ),
        shape = shape,
        tokens = tokens
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.055f), shape)
            )
            content()
            if (pressed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.14f), shape)
                )
            }
        }
    }
}

@Composable
private fun DayAgentCardVisualContent(
    visual: DayAgentCardVisual,
    foreground: Color,
    activityAccent: Color,
    modifier: Modifier = Modifier,
    onWeatherClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(if (visual.collapsed) 26.dp else 28.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (visual.cardIsDark) {
                    Color.Black.copy(alpha = 0.20f)
                } else {
                    Color.White.copy(alpha = 0.30f)
                }
            )
            .padding(horizontal = 16.dp, vertical = if (visual.collapsed) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    visual.activityLabel,
                    color = activityAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                visual.courseName?.let { courseName ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        courseName,
                        modifier = Modifier.weight(1f),
                        color = foreground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                if (visual.countdownText.isNotBlank()) {
                    Text(
                        visual.countdownText,
                        color = activityAccent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            if (visual.courseName != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        visual.locationText,
                        modifier = Modifier.weight(1f),
                        color = foreground.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        visual.focusTimeText,
                        color = foreground.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
        if (!visual.collapsed) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(foreground.copy(alpha = 0.10f)))
            Text(
                visual.courseCountText,
                color = foreground,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    visual.weatherText,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (onWeatherClick != null) Modifier.clickable(onClick = onWeatherClick) else Modifier),
                    color = foreground.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                visual.trailingStatus?.let { status ->
                    Spacer(Modifier.width(6.dp))
                     Text(
                         text = status,
                         color = if (visual.weatherAlert) {
                            if (visual.cardIsDark) Color(0xFFFFB86B) else Color(0xFFB84D00)
                        } else {
                            foreground.copy(alpha = 0.52f)
                        },
                        style = if (visual.weatherAlert) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        fontWeight = if (visual.weatherAlert) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DayAgentConversationDialog(
    state: AppState,
    backdrop: Backdrop?,
    facts: DayAgentFacts,
    messages: List<AgentMessageEntity>,
    repository: DayAgentRepository,
    initialText: String,
    initialQuestion: String?,
    sourceBounds: Rect,
    sourceCornerRadius: Dp,
    sourceVisual: DayAgentCardVisual,
    sourceForeground: Color,
    sourceActivityAccent: Color,
    sourceGlassTokens: GlassTokens,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onAgentAction: AgentActionHandler,
    onOverlayReady: () -> Unit,
    onPrepareDismiss: () -> Unit,
    onSourceHandoff: (AgentSourceHandoffTransform) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf("") }
    var imageAttachment by remember { mutableStateOf<AgentImageAttachment?>(null) }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var runStatusesExpanded by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    val dialogContext = LocalContext.current
    val backgroundRun by remember(state.config.id, facts.date) {
        DayAgentRunCoordinator.observe(state.config.id, facts.date)
    }.collectAsStateWithLifecycle()
    val streamingText = backgroundRun.streamingText
    val sending = backgroundRun.running
    val runStatuses = backgroundRun.statuses
    val error = backgroundRun.error
    val agentAiSettings = remember(dialogContext) {
        AiImportSettingsStore.load(dialogContext)
    }
    val providerName = agentAiSettings.profile.displayName
    val attachmentUploadEnabled =
        agentAiSettings.profile.id != AiProviderPresets.custom.id ||
            agentAiSettings.profile.supportsFileUpload
    val appliedActionKeys = remember(state.config.id) {
        mutableStateOf(DayAgentPreferences.getAppliedActions(dialogContext, state.config.id))
    }
    var executingActionKeys by remember(state.config.id) { mutableStateOf(emptySet<String>()) }
    var actionFeedback by remember(state.config.id) {
        mutableStateOf(emptyMap<String, AgentPlanExecutionResult>())
    }
    val expansion = remember { Animatable(0f) }
    val conversationListState = rememberLazyListState()
    val foreground = LocalAdaptiveGlass.current.contentColor
    val agentCardContentBackdrop = rememberLayerBackdrop()
    val agentInputBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop, agentCardContentBackdrop)
    } else null
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val answerTopPadding = statusBarTop + (configuration.screenHeightDp.dp * 0.018f).coerceIn(10.dp, 20.dp)
    val answerMaxHeight = (configuration.screenHeightDp.dp * 0.58f).coerceIn(280.dp, 560.dp)
    val targetWidthPx = with(density) { (configuration.screenWidthDp.dp - 28.dp).toPx() }
    val targetHeightPx = with(density) { answerMaxHeight.toPx() }
    val targetTopPx = with(density) { answerTopPadding.toPx() }
    val targetLeftPx = with(density) { 14.dp.toPx() }
    val screenCenterXPx = with(density) { configuration.screenWidthDp.dp.toPx() / 2f }
    val targetRect = remember(targetLeftPx, targetTopPx, targetWidthPx, targetHeightPx) {
        Rect(
            left = targetLeftPx,
            top = targetTopPx,
            right = targetLeftPx + targetWidthPx,
            bottom = targetTopPx + targetHeightPx
        )
    }
    val sourceRect = sourceBounds
    val sourceRadiusPx = with(density) { sourceCornerRadius.toPx() }
    val targetRadiusPx = with(density) { 32.dp.toPx() }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            attachmentError = null
            loadAiImportFile(dialogContext, uri)
                .onSuccess { file ->
                    imageAttachment = AgentImageAttachment(
                        mimeType = if (file.mimeType.startsWith("image/")) {
                            "image/jpeg"
                        } else {
                            file.mimeType
                        },
                        base64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP),
                        sourceName = file.displayName
                    )
                }
                .onFailure { attachmentError = it.message ?: "图片读取失败" }
        }
    }
    DisposableEffect(state.config.id, facts.date) {
        DayAgentRunCoordinator.setConversationVisible(
            state.config.id,
            facts.date,
            true
        )
        onDispose {
            DayAgentRunCoordinator.setConversationVisible(
                state.config.id,
                facts.date,
                false
            )
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        DayAgentRunCoordinator.setConversationVisible(
            state.config.id,
            facts.date,
            true
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        DayAgentRunCoordinator.setConversationVisible(
            state.config.id,
            facts.date,
            false
        )
    }
    fun dismissAnimated() {
        if (closing) return
        closing = true
        keyboard?.hide()
        // Give the host a closing hook, but keep the real source card hidden until the overlay
        // has fully returned. Showing it here creates a second stationary card under the Morph.
        onPrepareDismiss()
        scope.launch {
            coroutineScope {
                launch {
                    expansion.animateTo(
                        0f,
                        tween(AgentMorphCloseDurationMillis, easing = LinearEasing)
                    )
                }
                launch {
                    // Hand over while there is still a visible final return segment. The Dialog
                    // is removed immediately and the real shell completes the same geometry,
                    // avoiding the expensive cross-window composition hitch near expansion == 0.
                    while (expansion.value > 0.01f) {
                        withFrameNanos { }
                    }
                    val raw = expansion.value.coerceIn(0f, 1f)
                    val geometry = agentMorphGeometry(
                        source = sourceRect,
                        target = targetRect,
                        positionProgress = agentMorphPositionProgress(raw, closing = true),
                        sizeProgress = agentMorphSizeProgress(raw, closing = true),
                        maxArcPx = with(density) { 48.dp.toPx() }
                    )
                    onSourceHandoff(
                        AgentSourceHandoffTransform(
                            scaleX = geometry.width / sourceRect.width.coerceAtLeast(1f),
                            scaleY = geometry.height / sourceRect.height.coerceAtLeast(1f),
                            translationX = geometry.center.x - sourceRect.center.x,
                            translationY = geometry.center.y - sourceRect.center.y
                        )
                    )
                }
                launch {
                    backgroundMotionState.progress.animateTo(
                        0f,
                        tween(BACKGROUND_EXIT_DURATION, easing = BackgroundExitEasing)
                    )
                }
                // Mirror the open: the background keeps easing back after the card has
                // collapsed, so closing reads as the home surface settling home rather
                // than snapping with the card.
                launch {
                    backgroundMotionState.backgroundZoom.animateTo(
                        1f,
                        tween(
                            DayAgentBackgroundZoomCloseDurationMillis,
                            easing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1.0f)
                        )
                    )
                }
            }
            // The pixel-aligned source cover is already above the warmed card. Remove the Dialog
            // exactly when the Morph reaches its source geometry; no fixed frame delay is needed.
            onDismiss()
        }
    }

    fun send(questionOverride: String? = null) {
        val question = questionOverride?.trim().orEmpty().ifBlank { input.trim() }
        if (question.isBlank() || sending) return
        runStatusesExpanded = true
        val started = DayAgentRunCoordinator.start(
            context = dialogContext,
            scheduleId = state.config.id,
            facts = facts,
            question = question,
            imageAttachment = imageAttachment
        )
        if (started) {
            input = ""
            imageAttachment = null
            attachmentMenuExpanded = false
            attachmentError = null
        }
    }

    fun toggleSend() {
        if (sending) {
            DayAgentRunCoordinator.cancel(
                context = dialogContext,
                scheduleId = state.config.id,
                date = facts.date
            )
        } else {
            send()
        }
    }

    val streamingParts = remember(streamingText) { splitAgentReasoning(streamingText) }
    LaunchedEffect(streamingParts.answer.isNotBlank()) {
        if (streamingParts.answer.isNotBlank()) runStatusesExpanded = false
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
         val dialogView = LocalView.current
         SideEffect {
             (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                 // The hand-authored frozen-background Morph owns all depth treatment. Android's
                 // Dialog dim flag was the actual full-screen darkening seen behind the Agent.
                 clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                 setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                 setDimAmount(0f)
                 // The geometry below is the only entrance/exit animation.
                 setWindowAnimations(0)
             }
         }
         Box(
             Modifier
                 .fillMaxSize()
                 .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismissAnimated
                )
        ) {
            Box(
                modifier = Modifier
                    /*
                     * Give the glass its real animated dimensions instead of measuring the final
                     * dialog once and non-uniformly scaling it. The latter compressed text and
                     * forced a reciprocal scale on the backdrop, so its texture could never have
                     * the same zoom as the home background.
                     */
                    .offset {
                        val raw = expansion.value.coerceIn(0f, 1f)
                        val geometry = agentMorphGeometry(
                            source = sourceRect,
                            target = targetRect,
                            positionProgress = agentMorphPositionProgress(raw, closing),
                            sizeProgress = agentMorphSizeProgress(raw, closing),
                            maxArcPx = with(density) { 48.dp.toPx() }
                        )
                        IntOffset(geometry.left.roundToInt(), geometry.top.roundToInt())
                    }
                    .layout { measurable, constraints ->
                        val raw = expansion.value.coerceIn(0f, 1f)
                        val geometry = agentMorphGeometry(
                            source = sourceRect,
                            target = targetRect,
                            positionProgress = agentMorphPositionProgress(raw, closing),
                            sizeProgress = agentMorphSizeProgress(raw, closing),
                            maxArcPx = 48.dp.toPx()
                        )
                        val width = geometry.width.roundToInt().coerceAtLeast(1)
                        val height = geometry.height.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        val raw = expansion.value.coerceIn(0f, 1f)
                        val sizeProgress = agentMorphSizeProgress(raw, closing)
                        val cornerProgress = agentSmoothStep(0.04f, 0.90f, sizeProgress)
                        val visualRadiusPx =
                            sourceRadiusPx + (targetRadiusPx - sourceRadiusPx) * cornerProgress
                        shape = CourseEditorMorphCornerShape(
                            radiusX = visualRadiusPx,
                            radiusY = visualRadiusPx
                        )
                        clip = true
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    // Record only the expanded card. Inputs below sample this plus the base home
                    // layer; they must never fall through to wallpaper-only sampling.
                    .layerBackdrop(agentCardContentBackdrop)
             ) {
                GlassSurface(
                    backdrop = backdrop,
                    config = state.config,
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(32.dp),
                    // Keep the expanded conversation shell visually identical to the compact
                    // home card. Passing the resolved tokens also preserves the card's light/dark
                    // wallpaper treatment instead of maintaining a second drifting parameter set.
                    tokens = sourceGlassTokens
                ) {}
                DayAgentCardVisualContent(
                    visual = sourceVisual,
                    foreground = sourceForeground,
                    activityAccent = sourceActivityAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val raw = expansion.value.coerceIn(0f, 1f)
                            alpha = if (closing) {
                                1f - agentSmoothStep(0.32f, 0.56f, raw)
                            } else {
                                1f - agentSmoothStep(0.10f, 0.38f, raw)
                            }
                            val blurPx = 5.dp.toPx() * agentSmoothStep(0f, 0.56f, raw)
                            compositingStrategy = CompositingStrategy.Offscreen
                            renderEffect = if (blurPx > 0.01f) {
                                BlurEffect(blurPx, blurPx, TileMode.Clamp)
                            } else null
                        }
                )
                  Column(
                      Modifier
                          .graphicsLayer {
                              val raw = expansion.value.coerceIn(0f, 1f)
                              alpha = if (closing) {
                                  // Crossfade to the final compact card while the Dialog is still
                                  // the uppermost window. At the real-card handoff (0.32), no
                                  // clipped conversation content remains.
                                  agentSmoothStep(0.32f, 0.56f, raw)
                              } else {
                                  agentSmoothStep(0.10f, 0.38f, raw)
                              }
                              val blurPx = 5.dp.toPx() * (
                                  1f - agentSmoothStep(0.38f, 0.92f, raw)
                              )
                              compositingStrategy = CompositingStrategy.Offscreen
                              renderEffect = if (blurPx > 0.01f) {
                                  BlurEffect(blurPx, blurPx, TileMode.Clamp)
                              } else null
                          }
                          .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                     verticalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         Text("✦ 今日助手", color = foreground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                         Text(providerName, color = foreground.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
                     }
                      /*
                       * Once the streamed answer has been persisted it immediately joins `messages`.
                       * Keep this turn's real execution trace attached to that assistant message so
                       * the trace cannot jump below the completed answer during the Flow hand-off.
                       */
                      val tracedAssistantMessageId = messages.lastOrNull()
                          ?.takeIf { message ->
                              message.role == "assistant" && runStatuses.isNotEmpty()
                          }
                          ?.id
                      LazyColumn(
                         state = conversationListState,
                         modifier = Modifier.weight(1f),
                         contentPadding = PaddingValues(bottom = 16.dp),
                         verticalArrangement = Arrangement.spacedBy(10.dp)
                     ) {
                         if (messages.isEmpty() && streamingText.isBlank()) {
                             item { AgentMarkdownText(initialText, foreground, MaterialTheme.typography.bodyMedium) }
                         }
                          items(messages, key = { it.id }) { message ->
                              val isUser = message.role == "user"
                              if (isUser) {
                                  val userContent = remember(message.content) {
                                      parseAgentMessageContent(message.content)
                                  }
                                  // Decode off the main thread and downsample to the preview
                                  // size; the original synchronous full-size decode ran inside
                                  // composition and janked scrolling past attachment messages.
                                  val sentPreview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                                      initialValue = null,
                                      userContent.attachmentFileName
                                  ) {
                                      value = userContent.attachmentFileName?.let { fileName ->
                                          withContext(Dispatchers.IO) {
                                              runCatching {
                                                  val path = File(
                                                      dialogContext.filesDir,
                                                      "agent_attachments/$fileName"
                                                  ).absolutePath
                                                  val bounds = BitmapFactory.Options().apply {
                                                      inJustDecodeBounds = true
                                                  }
                                                  BitmapFactory.decodeFile(path, bounds)
                                                  val options = BitmapFactory.Options().apply {
                                                      inSampleSize = maxOf(
                                                          1,
                                                          maxOf(bounds.outWidth, bounds.outHeight) / 512
                                                      )
                                                  }
                                                  BitmapFactory.decodeFile(path, options)?.asImageBitmap()
                                              }.getOrNull()
                                          }
                                      }
                                  }
                                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                      Column(
                                          horizontalAlignment = Alignment.End,
                                          verticalArrangement = Arrangement.spacedBy(6.dp)
                                      ) {
                                          sentPreview?.let {
                                              Image(
                                                  bitmap = it,
                                                  contentDescription = "已发送图片",
                                                  contentScale = ContentScale.Crop,
                                                  modifier = Modifier
                                                      .width(176.dp)
                                                      .height(112.dp)
                                                      .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                                              )
                                          }
                                          if (userContent.text.isNotBlank()) {
                                              Text(
                                                  text = userContent.text,
                                                  modifier = Modifier
                                                      .background(
                                                          Color(0xFF168CFF).copy(alpha = 0.88f),
                                                          RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                                      )
                                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                                                  color = Color.White,
                                                  style = MaterialTheme.typography.bodyMedium
                                              )
                                          }
                                      }
                                  }
                              } else {
                                 val messageParts = remember(message.content) {
                                     splitAgentReasoning(message.content)
                                 }
                                  val parsed = remember(messageParts.answer, facts.sourceHash) {
                                      parseAgentActions(messageParts.answer, facts)
                                  }
                                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                      if (message.id == tracedAssistantMessageId) {
                                          AgentRunTrace(
                                              statuses = runStatuses,
                                              expanded = runStatusesExpanded,
                                              foreground = foreground,
                                              onToggle = {
                                                  runStatusesExpanded = !runStatusesExpanded
                                              }
                                          )
                                      }
                                      if (messageParts.reasoning.isNotBlank()) {
                                          AgentReasoningTrace(
                                              reasoning = messageParts.reasoning,
                                             foreground = foreground
                                         )
                                     }
                                     if (parsed.displayText.isNotBlank()) {
                                         AgentMarkdownText(parsed.displayText, foreground, MaterialTheme.typography.bodyMedium)
                                     }
                                     val courseActions = parsed.actions.filter { action ->
                                          action.type == AgentValidatedActionType.ADD ||
                                              action.type == AgentValidatedActionType.UPDATE ||
                                              action.type == AgentValidatedActionType.DELETE
                                     }
                                      val settingActions = parsed.actions.filter { action ->
                                          action.type == AgentValidatedActionType.SET_SETTING ||
                                              action.type == AgentValidatedActionType.SET_PERIOD_SETTINGS
                                     }
                                     val plans = buildList {
                                         if (courseActions.isNotEmpty()) {
                                             add("course-plan" to AgentPlan(courseActions))
                                         }
                                         if (settingActions.isNotEmpty()) {
                                             add("setting-plan" to AgentPlan(settingActions))
                                         }
                                         parsed.actions
                                             .filterNot { it in courseActions || it in settingActions }
                                              .forEachIndexed { actionIndex, action ->
                                                  add("single-$actionIndex" to AgentPlan(listOf(action)))
                                              }
                                      }
                                      plans.forEach { (planSuffix, plan) ->
                                          val actionKey = "${message.id}:$planSuffix"
                                          val alreadyApplied = actionKey in appliedActionKeys.value
                                          val executing = actionKey in executingActionKeys
                                          val preview = remember(plan, facts.sourceHash) {
                                              previewAgentPlan(
                                                  before = facts.semesterCourses,
                                                  plan = plan
                                              )
                                          }
                                          val containsCourseActions = plan.actions.any { action ->
                                              action.type == AgentValidatedActionType.ADD ||
                                                  action.type == AgentValidatedActionType.UPDATE ||
                                                  action.type == AgentValidatedActionType.DELETE
                                          }
                                          if (containsCourseActions) {
                                              Text(
                                                  text = agentPlanPreviewText(plan, preview),
                                                  color = if (preview.hasWarnings) {
                                                      Color(0xFFFFA94D)
                                                  } else foreground.copy(alpha = 0.68f),
                                                  style = MaterialTheme.typography.labelSmall
                                              )
                                          }
                                          actionFeedback[actionKey]?.let { result ->
                                              Text(
                                                  text = result.message,
                                                  color = if (result.success) {
                                                      Color(0xFF55C2FF)
                                                  } else {
                                                      Color(0xFFFF6B63)
                                                  },
                                                  style = MaterialTheme.typography.labelSmall
                                              )
                                          }
                                          val undo = actionFeedback[actionKey]?.undo
                                          if (alreadyApplied && undo != null) {
                                              AgentOperationLiquidButton(
                                                  text = "撤销本次修改",
                                                  backdrop = backdrop,
                                                  config = state.config,
                                                  destructive = false,
                                                  applied = false,
                                                  enabled = !executing,
                                                  modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false },
                                                  onClick = {
                                                      executingActionKeys += actionKey
                                                      undo { undoResult ->
                                                          executingActionKeys -= actionKey
                                                          actionFeedback =
                                                              actionFeedback + (actionKey to undoResult)
                                                           if (undoResult.success) {
                                                               appliedActionKeys.value =
                                                                   appliedActionKeys.value - actionKey
                                                               DayAgentPreferences.unmarkActionApplied(
                                                                   dialogContext,
                                                                   state.config.id,
                                                                   actionKey
                                                               )
                                                           }
                                                      }
                                                  }
                                              )
                                          }
                                          AgentOperationLiquidButton(
                                              text = when {
                                                  alreadyApplied -> "已执行并验证：${agentPlanSummary(plan)}"
                                                  executing -> "正在预演并执行…"
                                                  else -> agentPlanButtonLabel(plan)
                                              },
                                              backdrop = backdrop,
                                              config = state.config,
                                              destructive = plan.actions.any {
                                                  it.type == AgentValidatedActionType.DELETE
                                              },
                                              applied = alreadyApplied,
                                              enabled = !executing,
                                              modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false },
                                              onClick = {
                                                  if (actionKey !in appliedActionKeys.value) {
                                                      executingActionKeys += actionKey
                                                      onAgentAction(plan) { result ->
                                                          executingActionKeys -= actionKey
                                                          actionFeedback = actionFeedback + (actionKey to result)
                                                           if (result.success && result.verified) {
                                                              appliedActionKeys.value =
                                                                  appliedActionKeys.value + actionKey
                                                              DayAgentPreferences.markActionApplied(
                                                                  dialogContext,
                                                                  state.config.id,
                                                                  actionKey
                                                              )
                                                          }
                                                      }
                                                  }
                                              }
                                          )
                                     }
                                 }
                             }
                         }
                           if (runStatuses.isNotEmpty() && tracedAssistantMessageId == null) {
                               item {
                                   AgentRunTrace(
                                      statuses = runStatuses,
                                      expanded = runStatusesExpanded,
                                      foreground = foreground,
                                      onToggle = {
                                          runStatusesExpanded = !runStatusesExpanded
                                      }
                                  )
                              }
                          }
                          if (streamingParts.reasoning.isNotBlank()) {
                              item {
                                  AgentReasoningTrace(
                                      reasoning = streamingParts.reasoning,
                                      foreground = foreground,
                                      forceCollapsed = streamingParts.answer.isNotBlank()
                                  )
                              }
                          }
                           if (streamingParts.answer.isNotBlank()) {
                              item {
                                  val streamingDisplayText = remember(streamingParts.answer, facts.sourceHash) {
                                      parseAgentActions(streamingParts.answer, facts).displayText
                                  }
                                  AgentMarkdownText(
                                      streamingDisplayText,
                                     foreground,
                                     MaterialTheme.typography.bodyMedium
                                 )
                              }
                          }
                           if (sending && streamingText.isBlank() && runStatuses.isEmpty()) {
                              item {
                                  AgentRunStatusRow(
                                      status = AgentRunStatus(
                                          AgentRunStatusIcon.THINKING,
                                          "正在准备"
                                      ),
                                      foreground = foreground
                                  )
                              }
                          }
                         (error ?: attachmentError)?.let { message ->
                             item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                         }
                     }
                 }
            }

              AnimatedVisibility(
                  visible = attachmentMenuExpanded && attachmentUploadEnabled,
                  modifier = Modifier
                      .align(Alignment.BottomStart)
                      .imePadding()
                      .navigationBarsPadding()
                      .padding(start = 14.dp, bottom = 78.dp)
                      .graphicsLayer {
                          val p = expansion.value
                          alpha = ((p - 0.12f) / 0.58f).coerceIn(0f, 1f)
                          translationY = 18.dp.toPx() * (1f - p)
                      },
                 enter = fadeIn(
                     animationSpec = tween(durationMillis = 110)
                 ) + scaleIn(
                     initialScale = 0.72f,
                     transformOrigin = TransformOrigin(0.12f, 1f),
                     animationSpec = spring(
                         dampingRatio = 0.66f,
                         stiffness = 620f
                     )
                 ),
                 exit = fadeOut(
                     animationSpec = tween(durationMillis = 90)
                 ) + scaleOut(
                     targetScale = 0.82f,
                     transformOrigin = TransformOrigin(0.12f, 1f),
                     animationSpec = spring(
                         dampingRatio = 0.88f,
                         stiffness = 760f
                     )
                 )
             ) {
                 val attachmentMenuTokens = remember(sourceGlassTokens) {
                     sourceGlassTokens.copy(
                         lensHeight = 8.dp,
                         lensAmount = 14.dp,
                         surfaceAlpha = sourceGlassTokens.surfaceAlpha.coerceAtLeast(0.30f),
                         highlightAlpha = 0f,
                         innerShadowAlpha = 0.025f
                     )
                 }
                  GlassSurface(
                      backdrop = agentInputBackdrop,
                      config = state.config,
                      modifier = Modifier
                          .width(184.dp),
                      shape = RoundedCornerShape(50),
                      tokens = attachmentMenuTokens,
                      onClick = {
                          attachmentMenuExpanded = false
                          imagePicker.launch("image/*")
                      }
                  ) {
                      Row(
                          modifier = Modifier
                              .padding(horizontal = 16.dp, vertical = 14.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(12.dp)
                      ) {
                          Icon(
                              painter = painterResource(R.drawable.ic_image_attachment),
                              contentDescription = null,
                              tint = foreground,
                              modifier = Modifier.size(22.dp)
                          )
                          Text(
                              "选择图片",
                              color = foreground,
                              style = MaterialTheme.typography.bodyMedium,
                              fontWeight = FontWeight.Medium
                          )
                      }
                  }
             }

              GlassSurface(
                     backdrop = agentInputBackdrop,
                    config = state.config,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .graphicsLayer {
                            val p = expansion.value
                            alpha = ((p - 0.12f) / 0.58f).coerceIn(0f, 1f)
                            val scale = 0.94f + 0.06f * p
                            scaleX = scale
                            scaleY = scale
                            translationY = 18.dp.toPx() * (1f - p)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { focusRequester.requestFocus(); keyboard?.show() }
                        ),
                    shape = if (imageAttachment == null) {
                        RoundedCornerShape(50)
                    } else {
                        RoundedCornerShape(26.dp)
                    },
                    tokens = sourceGlassTokens
            ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.16f else 0.06f))
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
                    ) {
                         imageAttachment?.let { attachment ->
                             Row(
                                 modifier = Modifier
                                     .padding(start = 44.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
                                     .fillMaxWidth(),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Text(
                                     text = "图片 · ${attachment.sourceName}",
                                    color = foreground.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "×",
                                    color = foreground,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { imageAttachment = null }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             if (attachmentUploadEnabled) {
                                 Box(
                                     modifier = Modifier
                                         .size(40.dp)
                                         .clip(CircleShape)
                                        .clickable {
                                            keyboard?.hide()
                                            attachmentMenuExpanded = !attachmentMenuExpanded
                                        },
                                    contentAlignment = Alignment.Center
                                 ) {
                                     Icon(
                                         painter = painterResource(R.drawable.ic_add_course),
                                         contentDescription = "添加附件",
                                         tint = foreground,
                                         modifier = Modifier.size(24.dp)
                                     )
                                 }
                             }
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = foreground),
                                cursorBrush = SolidColor(Color(0xFF168CFF)),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                decorationBox = { inner ->
                                    if (input.isBlank()) Text("问问今天的安排…", color = foreground.copy(alpha = 0.5f))
                                    inner()
                                }
                            )
                            if (agentInputBackdrop != null) {
                                LiquidButton(
                                    onClick = ::toggleSend,
                                    backdrop = agentInputBackdrop,
                                    modifier = Modifier.size(40.dp),
                                    height = 40.dp,
                                    surfaceColor = Color(0xFF0A84FF).copy(alpha = 0.88f),
                                    tint = Color(0xFF0A84FF),
                                    contentPadding = PaddingValues(0.dp),
                                    blurRadius = 4.dp,
                                    lensHeight = 14.dp,
                                    lensAmount = 18.dp,
                                    chromaticAberration = false
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(if (sending) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else AgentSimplePressSurface(
                                backdrop = null,
                                config = state.config,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                tokens = GlassTokens.pill(intensity = 0.86f).copy(blur = 0.dp, surfaceAlpha = 0.22f),
                                onClick = ::toggleSend
                            ) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.16f else 0.06f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (sending) "■" else "↑", color = foreground, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
            }
        }
         LaunchedEffect(Unit) {
              backgroundMotionState.progress.snapTo(0f)
              backgroundMotionState.backgroundZoom.snapTo(1f)
              // Commit the p=0 source snapshot to the Dialog window before hiding the real
              // card below it. A second frame applies that hide while expansion is still zero,
              // eliminating the one-frame hole between the two layers.
              withFrameNanos { }
              onOverlayReady()
              withFrameNanos { }
              coroutineScope {
                 launch {
                     expansion.animateTo(
                          1f,
                          tween(AgentMorphOpenDurationMillis, easing = LinearEasing)
                     )
                 }
                 launch {
                     backgroundMotionState.progress.animateTo(
                         1f,
                         tween(BACKGROUND_OPEN_DURATION, easing = BackgroundOpenEasing)
                     )
                 }
                 // The background depth trails the card on a longer ease-out so it keeps
                 // receding after the card has opened — the inertial pull on the home surface.
                 launch {
                     backgroundMotionState.backgroundZoom.animateTo(
                         DayAgentBackgroundZoomRestScale,
                         tween(
                             DayAgentBackgroundZoomOpenDurationMillis,
                             easing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1.0f)
                         )
                     )
                 }
             }
             focusRequester.requestFocus()
             keyboard?.show()
             initialQuestion?.takeIf { it.isNotBlank() }?.let(::send)
         }
       LaunchedEffect(sending) {
           if (!sending) return@LaunchedEffect
           snapshotFlow { streamingText.length }.collect {
               withFrameNanos { }
               val lastIndex = conversationListState.layoutInfo.totalItemsCount - 1
               if (lastIndex >= 0) {
                   conversationListState.scrollToItem(lastIndex, Int.MAX_VALUE)
               }
           }
       }
       LaunchedEffect(messages.size, error) {
           withFrameNanos { }
           val lastIndex = conversationListState.layoutInfo.totalItemsCount - 1
           if (lastIndex >= 0) {
               conversationListState.scrollToItem(lastIndex, Int.MAX_VALUE)
           }
       }
    }
}

private fun agentActionButtonLabel(action: AgentValidatedAction): String = when (action.type) {
    AgentValidatedActionType.ADD -> "确认添加：${action.edited?.name ?: action.summary}"
    AgentValidatedActionType.UPDATE -> "确认修改：${action.summary}"
    AgentValidatedActionType.DELETE -> "确认删除：${action.original?.name ?: action.summary}"
    AgentValidatedActionType.OPEN_SETTINGS -> action.summary
    AgentValidatedActionType.SET_SETTING -> "确认设置：${action.summary}"
    AgentValidatedActionType.SET_PERIOD_SETTINGS -> "确认节次设置：${action.summary}"
}

private data class AgentMessageParts(
    val reasoning: String,
    val toolResults: List<String>,
    val answer: String
)

private fun splitAgentReasoning(content: String): AgentMessageParts {
    val complete = Regex(
        "<(?:think|thinking)[^>]*>([\\s\\S]*?)</(?:think|thinking)>",
        RegexOption.IGNORE_CASE
    )
    val taggedReasoning = complete.findAll(content)
        .map { it.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .joinToString("\n")
    val toolPrelude = extractAgentToolPrelude(content)
        .replace(complete, "")
        .trim()
    val reasoning = listOf(taggedReasoning, toolPrelude)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("\n")
    val withoutComplete = sanitizeAgentToolOutput(content)
        .replace(complete, "")
        .trim()
    val open = Regex("<(?:think|thinking)[^>]*>", RegexOption.IGNORE_CASE)
        .find(withoutComplete)
    return if (open != null) {
        AgentMessageParts(
            reasoning = listOf(reasoning, withoutComplete.substring(open.range.last + 1).trim())
                .filter(String::isNotBlank)
                .joinToString("\n"),
            toolResults = emptyList(),
            answer = withoutComplete.substring(0, open.range.first).trim()
        )
    } else {
        AgentMessageParts(
            reasoning = reasoning,
            toolResults = emptyList(),
            answer = withoutComplete
        )
    }
}

@Composable
private fun AgentRunTrace(
    statuses: List<AgentRunStatus>,
    expanded: Boolean,
    foreground: Color,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (expanded) {
            statuses.forEach { status ->
                AgentRunStatusRow(status = status, foreground = foreground)
            }
        } else {
            AgentRunStatusRow(
                // Folding is only a presentation choice. It must never manufacture a completed
                // state while the request is still thinking or executing a tool.
                status = statuses.lastOrNull()
                    ?: AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"),
                foreground = foreground
            )
        }
    }
}

@Composable
private fun AgentRunStatusRow(
    status: AgentRunStatus,
    foreground: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(agentRunStatusIcon(status.icon)),
                contentDescription = null,
                tint = Color(0xFF55C2FF).copy(
                    alpha = if (status.icon == AgentRunStatusIcon.THINKING) 0.68f else 0.86f
                ),
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = status.text,
            color = foreground.copy(
                alpha = if (status.icon == AgentRunStatusIcon.THINKING) 0.43f else 0.57f
            ),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun AgentReasoningTrace(
    reasoning: String,
    foreground: Color,
    forceCollapsed: Boolean = true
) {
    var expanded by remember { mutableStateOf(!forceCollapsed) }
    LaunchedEffect(forceCollapsed) {
        if (forceCollapsed) expanded = false
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AgentRunStatusRow(
            status = AgentRunStatus(
                AgentRunStatusIcon.THINKING,
                if (expanded) "收起思考过程" else "查看思考过程"
            ),
            foreground = foreground
        )
        if (expanded) {
            AgentMarkdownText(
                markdown = reasoning,
                color = foreground.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun agentRunStatusIcon(icon: AgentRunStatusIcon): Int = when (icon) {
    AgentRunStatusIcon.OVERVIEW -> R.drawable.ic_agent_overview
    AgentRunStatusIcon.SEARCH -> R.drawable.ic_agent_search
    AgentRunStatusIcon.SCHEDULE -> R.drawable.ic_agent_schedule
    AgentRunStatusIcon.PERIOD -> R.drawable.ic_agent_period
    AgentRunStatusIcon.SETTINGS -> R.drawable.ic_agent_settings
    AgentRunStatusIcon.THINKING -> R.drawable.ic_agent_thinking
}

private fun agentPlanSummary(plan: AgentPlan): String =
    if (plan.actions.size == 1) {
        plan.actions.first().summary
    } else if (plan.actions.all {
            it.type == AgentValidatedActionType.SET_SETTING ||
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS
        }) {
        if (plan.actions.all {
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
                    AgentSettingRegistry.isPeriodTimeSetting(it.settingKey)
            }) {
            "${plan.actions.size} 项节次设置"
        } else {
            "${plan.actions.size} 项设置"
        }
    } else {
        "${plan.actions.size} 项课程修改"
    }

private fun agentPlanButtonLabel(plan: AgentPlan): String =
    if (plan.actions.size == 1) {
        agentActionButtonLabel(plan.actions.first())
    } else if (plan.actions.all {
            it.type == AgentValidatedActionType.SET_SETTING ||
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS
        }) {
        if (plan.actions.all {
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
                    AgentSettingRegistry.isPeriodTimeSetting(it.settingKey)
            }) {
            "确认应用 ${plan.actions.size} 项节次设置"
        } else {
            "确认应用 ${plan.actions.size} 项设置"
        }
    } else {
        "确认执行 ${plan.actions.size} 项课程修改"
    }

private fun agentPlanPreviewText(
    plan: AgentPlan,
    preview: AgentPlanPreview
): String {
    preview.newConflicts.firstOrNull()?.let { conflict ->
        return "影响提示 · 执行后可能重叠：${conflict.first.name} 与 ${conflict.second.name}，" +
            "第${conflict.weeks.joinToString("、")}周 · " +
            "第${conflict.periods.joinToString("、")}节"
    }
    if (plan.actions.size > 1) {
        val weekText = preview.affectedWeeks.takeIf { it.isNotEmpty() }
            ?.joinToString("、", prefix = "第", postfix = "周")
            ?: "当前课表"
        return "预演 · ${plan.actions.size} 项操作 · 影响 $weekText · " +
            "${preview.changedCourseCount} 条课程记录"
    }
    val action = plan.actions.first()
    val scope = if (action.scope == AgentActionScope.CURRENT_WEEK) {
        "仅第${action.targetWeek}周"
    } else {
        "全学期"
    }
    val change = when (action.type) {
        AgentValidatedActionType.ADD ->
            "新增 ${action.edited?.name.orEmpty()} ${agentCourseSlotText(action.edited)}"
        AgentValidatedActionType.UPDATE ->
            "${agentCourseSlotText(action.original)} → ${agentCourseSlotText(action.edited)}"
        AgentValidatedActionType.DELETE ->
            "删除 ${action.original?.name.orEmpty()} ${agentCourseSlotText(action.original)}"
        else -> action.summary
    }
    return "预演 · $scope · $change"
}

private fun agentCourseSlotText(course: CourseEntity?): String {
    if (course == null) return ""
    val periods = course.periods.sorted()
    val periodText = when {
        periods.isEmpty() -> "未设置节次"
        periods.size == 1 -> "第${periods.first()}节"
        else -> "第${periods.first()}-${periods.last()}节"
    }
    val weekday = "一二三四五六日".getOrNull(course.weekday - 1)?.toString()
        ?: course.weekday.toString()
    return "周$weekday $periodText"
}

private fun agentMorphPositionProgress(rawProgress: Float, closing: Boolean): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    if (!closing) {
        return AgentMorphPositionEasing.transform(raw).coerceIn(0f, 1f)
    }
    val closingElapsed = 1f - raw
    return (1f - AgentMorphClosePositionEasing.transform(closingElapsed)).coerceIn(0f, 1f)
}

private fun agentMorphSizeProgress(rawProgress: Float, closing: Boolean): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    return if (!closing) {
        val delayed = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
        AgentMorphSizeEasing.transform(delayed).coerceIn(0f, 1f)
    } else {
        val closingElapsed = 1f - raw
        val advanced = (closingElapsed / 0.78f).coerceIn(0f, 1f)
        (1f - AgentMorphSizeEasing.transform(advanced)).coerceIn(0f, 1f)
    }
}

private fun agentMorphGeometry(
    source: Rect,
    target: Rect,
    positionProgress: Float,
    sizeProgress: Float,
    maxArcPx: Float
): Rect {
    val position = positionProgress.coerceIn(0f, 1f)
    val size = sizeProgress.coerceIn(0f, 1f)
    val deltaY = target.center.y - source.center.y
    val arcAmplitude = min(maxArcPx, abs(deltaY) * 0.22f)
    val controlX = (source.center.x + target.center.x) / 2f
    val controlY = (source.center.y + target.center.y) / 2f + sign(deltaY) * arcAmplitude
    val inverse = 1f - position
    val centerX =
        inverse * inverse * source.center.x +
            2f * inverse * position * controlX +
            position * position * target.center.x
    val centerY =
        inverse * inverse * source.center.y +
            2f * inverse * position * controlY +
            position * position * target.center.y
    val width = source.width + (target.width - source.width) * size
    val height = source.height + (target.height - source.height) * size
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

private fun agentSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
