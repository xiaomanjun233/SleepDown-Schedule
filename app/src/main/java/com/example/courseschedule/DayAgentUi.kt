package com.example.courseschedule

import android.Manifest
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZoneId
import java.util.LinkedHashMap
import kotlin.math.roundToInt

internal val LocalAgentBackgroundCaptureMask = staticCompositionLocalOf<() -> Boolean> { { false } }

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
}

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
    onAgentAction: (AgentValidatedAction) -> Unit = {}
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
    onAgentAction: (AgentValidatedAction) -> Unit
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
    var now by remember(date) { mutableStateOf(LocalDateTime.now(ShanghaiZone)) }
    var dialogOpen by remember(date, scheduleId) { mutableStateOf(false) }
    var dialogOpening by remember(date, scheduleId) { mutableStateOf(false) }
    val messageFlow = remember(repository, scheduleId, date, dialogOpen) {
        if (dialogOpen) repository.observeMessages(scheduleId, date) else flowOf(emptyList())
    }
    val messages by messageFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var sourceCardHidden by remember(date, scheduleId) { mutableStateOf(false) }
    var pendingQuestion by remember(date, scheduleId) { mutableStateOf<String?>(null) }
    var captureRequested by remember(date, scheduleId) { mutableStateOf(false) }
    var cardBounds by remember(date, scheduleId) { mutableStateOf<Rect?>(null) }
    var sourceCardSnapshot by remember(date, scheduleId) { mutableStateOf<Bitmap?>(null) }
    var sourceHandoffCover by remember(date, scheduleId) { mutableStateOf(false) }
    var showApiKeyHint by remember(date, scheduleId, hasApiKey) { mutableStateOf(!hasApiKey) }
    val sourceHandoffAlpha = remember(date, scheduleId) { Animatable(1f) }
    val sourceHandoffImage = remember(sourceCardSnapshot) { sourceCardSnapshot?.asImageBitmap() }
    val cardGraphicsLayer = rememberGraphicsLayer()
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
            now = LocalDateTime.now(ShanghaiZone)
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
    val focusSlot = currentSlot ?: nextSlot
    val remainingMinutes = remember(currentSlot, nextSlot, now) {
        val target = currentSlot?.end ?: nextSlot?.start
        target?.let { Duration.between(now.toLocalTime(), it).toMinutes().coerceAtLeast(0) }
    }
    val activityLabel = when {
        currentSlot != null -> "当前"
        nextSlot != null -> "下节课"
        facts.today.isEmpty() -> "今日无课"
        else -> "课程已结束"
    }
    val countdownText = when {
        currentSlot != null && remainingMinutes != null -> "${remainingMinutes} 分钟后下课"
        nextSlot != null && remainingMinutes != null -> "${remainingMinutes} 分钟后"
        facts.today.isEmpty() -> "轻松一天"
        else -> "今日完成"
    }
    val locationText = focusSlot?.course?.let { course ->
        listOfNotNull(course.location?.takeIf(String::isNotBlank), course.teacher?.takeIf(String::isNotBlank)).joinToString(" | ")
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
    val conversationInitialText = remember(facts.today, focusSlot, weather) {
        when {
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
                sourceHandoffCover = false
                sourceHandoffAlpha.snapTo(1f)
                // onPrepareOpen waits until the day pager is fully settled. Capture the small
                // source card only after that barrier; an eager card snapshot taken while the
                // pager was between pages preserved a horizontally displaced glass sample even
                // though the card's final geometry itself was correct.
                onPrepareOpen()
                // Trigger a single-frame graphics layer capture in the next draw pass.
                captureRequested = true
                withFrameNanos { } // drawWithContent records the layer
                sourceCardSnapshot = runCatching {
                    cardGraphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
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
            .onGloballyPositioned { cardBounds = it.boundsInWindow() }
            .drawWithContent {
                if (!suppressForAgentBackgroundCapture()) {
                    drawContent()
                    if (captureRequested) {
                        captureRequested = false
                        cardGraphicsLayer.record { this@drawWithContent.drawContent() }
                    }
                    if (sourceHandoffCover) {
                        sourceHandoffImage?.let { cover ->
                            drawImage(
                                image = cover,
                                dstSize = IntSize(
                                    width = size.width.roundToInt().coerceAtLeast(1),
                                    height = size.height.roundToInt().coerceAtLeast(1)
                                ),
                                alpha = sourceHandoffAlpha.value
                            )
                        }
                    }
                }
            }
            .graphicsLayer {
                alpha = if (sourceCardHidden) 0f else 1f
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
                            AgentValidatedAction(
                                type = AgentValidatedActionType.OPEN_SETTINGS,
                                settingsPage = "AI_IMPORT",
                                summary = "绑定 API Key"
                            )
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
        Box {
            Column(
                modifier = Modifier
                    .clip(cardShape)
                    .background(
                        if (cardIsDark) {
                            Color.Black.copy(alpha = 0.20f)
                        } else {
                            Color.White.copy(alpha = 0.30f)
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = if (collapsed) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(activityLabel, color = activityAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        focusSlot?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it.course.name,
                                color = foreground,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (locationText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(locationText, color = foreground.copy(alpha = 0.56f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    countdownText,
                    color = activityAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            if (!collapsed) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(foreground.copy(alpha = 0.10f)))
                Text("今天 ${facts.today.size} 节课", color = foreground, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        weatherText,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (weatherEnabled && weather == null && !weatherRepository.hasLocationPermission()) {
                                    Modifier.clickable { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                                } else Modifier
                            ),
                        color = foreground.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    assistantHintText?.let { hint ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = hint,
                            modifier = Modifier.weight(if (hasApiKey) 1.15f else 1.75f),
                            color = foreground.copy(alpha = 0.52f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                weatherAlertText?.let { alert ->
                    Text(
                        text = "⚠️ $alert",
                        color = if (cardIsDark) Color(0xFFFFB86B) else Color(0xFFB84D00),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            }
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

    if (dialogOpen && hasApiKey) {
        DayAgentConversationDialog(
            state = state,
            backdrop = backdrop,
            facts = facts,
            messages = messages,
            repository = repository,
            initialText = conversationInitialText,
            initialQuestion = pendingQuestion,
            sourceBounds = cardBounds,
            sourceCardSnapshot = sourceCardSnapshot,
            sourceCornerRadius = if (collapsed) 26.dp else 28.dp,
            backgroundMotionState = backgroundMotionState,
            onAgentAction = onAgentAction,
            onOverlayReady = { sourceCardHidden = true },
            onPrepareDismiss = {
                // Warm the real glass behind a pixel-aligned local cover. The cover lives in the
                // source card's own coordinates, so Dialog/window offsets cannot affect handoff.
                sourceHandoffCover = true
                sourceCardHidden = false
            },
            onDismiss = {
                dialogOpen = false
                sourceCardHidden = false
                pendingQuestion = null
                onAgentDismissed()
                scope.launch {
                    // The real card has rendered behind the Dialog throughout the exit, so the
                    // local source cover can cross-fade immediately after window handoff.
                    sourceHandoffAlpha.animateTo(0f, tween(durationMillis = 110))
                    sourceHandoffCover = false
                    sourceCardSnapshot = null
                    sourceHandoffAlpha.snapTo(1f)
                }
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
            onClick = { if (!applied) onClick() },
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            surfaceColor = if (applied) bgColor.copy(alpha = 0.35f) else bgColor.copy(alpha = 0.88f),
            tint = if (applied) Color.Unspecified else bgColor,
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
        val background = if (applied) bgColor.copy(alpha = 0.30f) else bgColor
        Box(
            modifier = modifier
                .height(42.dp)
                .clip(RoundedCornerShape(50))
                .background(background.copy(alpha = if (applied) 0.46f else 0.94f))
                .clickable(enabled = !applied, onClick = onClick),
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
private fun DayAgentConversationDialog(
    state: AppState,
    backdrop: Backdrop?,
    facts: DayAgentFacts,
    messages: List<AgentMessageEntity>,
    repository: DayAgentRepository,
    initialText: String,
    initialQuestion: String?,
    sourceBounds: Rect?,
    sourceCardSnapshot: Bitmap?,
    sourceCornerRadius: Dp,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onAgentAction: (AgentValidatedAction) -> Unit,
    onOverlayReady: () -> Unit,
    onPrepareDismiss: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var requestJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    val dialogContext = LocalContext.current
    val providerName = remember(dialogContext) {
        AiImportSettingsStore.load(dialogContext).profile.displayName
    }
    val appliedActionKeys = remember(state.config.id) {
        mutableStateOf(DayAgentPreferences.getAppliedActions(dialogContext, state.config.id))
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
    val screenCenterXPx = with(density) { configuration.screenWidthDp.dp.toPx() / 2f }
    val startScaleX = ((sourceBounds?.width ?: targetWidthPx) / targetWidthPx).coerceIn(0.16f, 1f)
    val startScaleY = ((sourceBounds?.height ?: targetHeightPx) / targetHeightPx).coerceIn(0.12f, 1f)
    val sourceRadiusPx = with(density) { sourceCornerRadius.toPx() }
    val targetRadiusPx = with(density) { 32.dp.toPx() }
    fun dismissAnimated() {
        if (closing) return
        closing = true
        keyboard?.hide()
        // Restore the real source card underneath the still-opaque Dialog before closing. Its
        // backdrop now gets the whole exit duration to warm up instead of being mounted at p=0.
        onPrepareDismiss()
        scope.launch {
            coroutineScope {
                launch {
                    expansion.animateTo(
                        0f,
                        tween(DETAIL_SYSTEM_BACK_DURATION, easing = DetailExitEasing)
                    )
                }
                launch {
                    backgroundMotionState.progress.animateTo(
                        0f,
                        tween(BACKGROUND_EXIT_DURATION, easing = BackgroundExitEasing)
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
        input = ""
        streamingText = ""
        error = null
        sending = true
        val buffer = StringBuilder()
        requestJob = scope.launch {
            repository.sendMessage(state.config.id, facts, question) { delta ->
                val snapshot = synchronized(buffer) { buffer.append(delta).toString() }
                scope.launch { if (sending) streamingText = snapshot }
            }.onFailure { error = it.message }
            sending = false
            streamingText = ""
            requestJob = null
        }
    }

    fun toggleSend() {
        if (sending) {
            requestJob?.cancel()
            requestJob = null
            sending = false
            streamingText = ""
        } else {
            send()
        }
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
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = answerTopPadding, end = 14.dp)
                    .height(answerMaxHeight)
                     .graphicsLayer {
                         val source = sourceBounds
                         val p = expansion.value.coerceIn(0f, 1f)
                         val scaleXNow = startScaleX + (1f - startScaleX) * p
                         val scaleYNow = startScaleY + (1f - startScaleY) * p
                         if (source != null && source.width > 0f && source.height > 0f) {
                             scaleX = scaleXNow
                             scaleY = scaleYNow
                             translationX = (source.center.x - screenCenterXPx) * (1f - p)
                             translationY = (source.center.y - (targetTopPx + targetHeightPx / 2f)) * (1f - p)
                         }
                         // Keep the animated Shape read and replacement inside the layer phase.
                         // Reading expansion in composition made the complete Dialog (including
                         // Markdown, LazyColumn and every glass control) recompose every frame.
                         val visualRadiusPx = sourceRadiusPx + (targetRadiusPx - sourceRadiusPx) * p
                         shape = CourseEditorMorphCornerShape(
                             radiusX = visualRadiusPx / scaleXNow.coerceAtLeast(0.001f),
                             radiusY = visualRadiusPx / scaleYNow.coerceAtLeast(0.001f)
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
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // The outer node owns the geometry Morph. Apply its exact inverse to
                            // the live glass layer so the sampled wallpaper stays fixed in screen
                            // coordinates instead of stretching with short/tall source cards.
                            val source = sourceBounds
                            val p = expansion.value
                            if (source != null && source.width > 0f && source.height > 0f) {
                                val outerScaleX = startScaleX + (1f - startScaleX) * p
                                val outerScaleY = startScaleY + (1f - startScaleY) * p
                                val outerTranslationX = (source.center.x - screenCenterXPx) * (1f - p)
                                val outerTranslationY =
                                    (source.center.y - (targetTopPx + targetHeightPx / 2f)) * (1f - p)
                                scaleX = 1f / outerScaleX.coerceAtLeast(0.001f)
                                scaleY = 1f / outerScaleY.coerceAtLeast(0.001f)
                                translationX = -outerTranslationX / outerScaleX.coerceAtLeast(0.001f)
                                translationY = -outerTranslationY / outerScaleY.coerceAtLeast(0.001f)
                            }
                        },
                    // The outer container already owns the compensated Morph clip. Giving the
                    // lens that same inverse-scaled shape creates a second SDF outline and exposes
                    // dark corner wedges. Keep the lens on its stable final geometry instead.
                    shape = RoundedCornerShape(32.dp),
                    tokens = GlassTokens.dialog(intensity = 0.90f).copy(
                        blur = 5.dp,
                        surfaceAlpha = 0.30f
                    )
                ) {}
                  Column(
                      Modifier
                          .graphicsLayer {
                              alpha = ((expansion.value - 0.10f) / 0.50f).coerceIn(0f, 1f)
                          }
                          .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                     verticalArrangement = Arrangement.spacedBy(10.dp)
                 ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦ 今日助手", color = foreground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(providerName, color = foreground.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
                    }
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
                                 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                     Text(
                                         text = message.content,
                                         modifier = Modifier
                                             .background(Color(0xFF168CFF).copy(alpha = 0.88f), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                             .padding(horizontal = 12.dp, vertical = 8.dp),
                                         color = Color.White,
                                         style = MaterialTheme.typography.bodyMedium
                                )
                                 }
                             } else {
                                 val parsed = remember(message.content, facts.sourceHash) {
                                     parseAgentActions(message.content, facts)
                                 }
                                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                     if (parsed.displayText.isNotBlank()) {
                                         AgentMarkdownText(parsed.displayText, foreground, MaterialTheme.typography.bodyMedium)
                                     }
                                     parsed.actions.forEachIndexed { actionIndex, action ->
                                         val actionKey = "${message.id}:$actionIndex"
                                         val alreadyApplied = actionKey in appliedActionKeys.value
                                         AgentOperationLiquidButton(
                                             text = if (alreadyApplied) "已执行：${action.summary}" else agentActionButtonLabel(action),
                                             backdrop = backdrop,
                                             config = state.config,
                                             destructive = action.type == AgentValidatedActionType.DELETE,
                                             applied = alreadyApplied,
                                             modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false },
                                             onClick = {
                                                 if (actionKey !in appliedActionKeys.value) {
                                                     onAgentAction(action)
                                                     appliedActionKeys.value = appliedActionKeys.value + actionKey
                                                     DayAgentPreferences.markActionApplied(
                                                         dialogContext,
                                                         state.config.id,
                                                         actionKey
                                                     )
                                                 }
                                             }
                                         )
                                     }
                                 }
                             }
                         }
                         if (streamingText.isNotBlank()) {
                             item {
                                 AgentMarkdownText(
                                     parseAgentActions(streamingText, facts).displayText,
                                    foreground,
                                    MaterialTheme.typography.bodyMedium
                                )
                             }
                         }
                         if (sending && streamingText.isBlank()) {
                             item { Text("正在思考…", color = foreground.copy(alpha = 0.6f)) }
                         }
                         error?.let { message ->
                             item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                         }
                     }
                 }
                sourceCardSnapshot?.let { snapshot ->
                    Image(
                        bitmap = snapshot.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = (1f - expansion.value * 3f).coerceIn(0f, 1f)
                            }
                    )
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
                    shape = RoundedCornerShape(50),
                    tokens = GlassTokens.pill(intensity = 0.86f).copy(blur = 4.dp)
            ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.16f else 0.06f))
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
         LaunchedEffect(Unit) {
              backgroundMotionState.progress.snapTo(0f)
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
                         tween(DETAIL_OPEN_DURATION, easing = DetailOpenEasing)
                     )
                 }
                 launch {
                     backgroundMotionState.progress.animateTo(
                         1f,
                         tween(BACKGROUND_OPEN_DURATION, easing = BackgroundOpenEasing)
                     )
                 }
             }
             focusRequester.requestFocus()
             keyboard?.show()
             initialQuestion?.takeIf { it.isNotBlank() }?.let(::send)
         }
        LaunchedEffect(messages.size, streamingText.length, sending, error) {
            delay(24)
            val lastIndex = conversationListState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) conversationListState.scrollToItem(lastIndex)
        }
    }
}

private fun agentActionButtonLabel(action: AgentValidatedAction): String = when (action.type) {
    AgentValidatedActionType.ADD -> "确认添加：${action.edited?.name ?: action.summary}"
    AgentValidatedActionType.UPDATE -> "确认修改：${action.summary}"
    AgentValidatedActionType.DELETE -> "确认删除：${action.original?.name ?: action.summary}"
    AgentValidatedActionType.OPEN_SETTINGS -> action.summary
    AgentValidatedActionType.SET_SETTING -> "确认设置：${action.summary}"
}

private val ShanghaiZone = ZoneId.of("Asia/Shanghai")
