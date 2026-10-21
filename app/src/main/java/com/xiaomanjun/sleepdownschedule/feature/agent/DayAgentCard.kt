package com.xiaomanjun.sleepdownschedule.feature.agent

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentActionHandler
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlan
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.glass.ui.verticalGlassAccent
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal data class DayAgentCardVisual(
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
    val cardIsDark: Boolean,
    val countdownSeconds: Long? = null,
    val transitionKey: String? = null
)

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
    activateAvailableAi: () -> Boolean,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onPrepareOpen: suspend () -> Unit,
    onAgentDismissed: () -> Unit,
    onAgentAction: AgentActionHandler,
    onImportFile: ((android.net.Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val scheduleId = state.config.id
    val scheduleName = state.schedules.firstOrNull { it.id == scheduleId }?.name
    val repository = remember(context) { DayAgentRepository(context.applicationContext) }
    val weatherRepository = remember(context) { DayAgentWeatherRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val inlineTabletConversation = rememberHomeAdaptiveMetrics().isTabletLandscape
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

    val backgroundFrozen = com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen.current.value
    val staticFacts = remember(state.courses, state.periods, state.config, scheduleName, date, weather) {
        DayAgentRenderCache.facts(state, date, weather, scheduleName, context)
    }
    LaunchedEffect(date, backgroundFrozen, staticFacts.today, staticFacts.periodDefinitions, isActive, dialogOpen) {
        if (backgroundFrozen || !isActive || dialogOpen) return@LaunchedEffect
        while (true) {
            val current = LocalDateTime.now()
            now = current
            val interval = if (agentNextBoundaryWithinTenMinutes(
                    staticFacts.today, staticFacts.periodDefinitions, current
                )) 1_000L else 60_000L
            delay(interval - (System.currentTimeMillis() % interval))
        }
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
    val presentation = rememberDayAgentPresentation(
        facts, collapsed, cardIsDark, weatherEnabled, weatherRepository.hasLocationPermission(),
        hasApiKey, showApiKeyHint
    )
    val cardVisual = presentation.visual
    val activityAccent = presentation.accent
    val conversationInitialText = presentation.initialText

    fun openConversation(question: String?) {
        if (dialogOpen || dialogOpening) return
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

    val cardShape = RoundedRectangle(if (collapsed) 26.dp else 28.dp)
    val cardTokens = GlassTokens.dialog(intensity = 1.12f).copy(
        blur = if (cardIsDark) 8.dp else 10.dp,
        lensHeight = 20.dp,
        lensAmount = 40.dp,
        surfaceAlpha = if (cardIsDark) 0.46f else 0.50f,
        borderAlpha = 0.34f,
        highlightAlpha = 0.075f,
        depthEffect = true,
        chromaticAberration = false
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
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
                    if (activateAvailableAi()) {
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
            .homeCountdownShockwave()
    ) {
        // Keep one material shell. Native content uses COUI's light instead of the legacy dim.
        GlassSurface(
            nativeMaterial = true,
            nativeDockMaterial = true,
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            tokens = cardTokens
        ) {
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
                animated = isActive && !backgroundFrozen && !dialogOpen,
                onWeatherClick = if (
                    weatherEnabled &&
                    weather == null &&
                    !weatherRepository.hasLocationPermission()
                ) {
                    { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                } else null
            )
            if (sourceCardPressed && !com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive.current) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Color.Black.copy(alpha = 0.13f),
                            cardShape
                        )
                )
            }
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
            onImportFile = onImportFile,
            onOverlayReady = {
                if (!inlineTabletConversation) sourceCardHidden = true
            },
            onPrepareDismiss = {},
            onSourceHandoff = {
                if (!inlineTabletConversation) sourceCardHidden = false
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

internal fun weatherEmoji(summary: String): String {
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

internal fun weatherAlertText(weather: AgentWeatherSnapshot): String? {
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
internal fun DayAgentCardVisualContent(
    visual: DayAgentCardVisual,
    foreground: Color,
    activityAccent: Color,
    modifier: Modifier = Modifier,
    onWeatherClick: (() -> Unit)? = null,
    decorated: Boolean = true,
    animated: Boolean = false
) {
    if (!animated) {
        DayAgentCardVisualCore(visual, foreground, activityAccent, modifier, onWeatherClick, decorated)
        return
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cinematic = LocalDayAgentCountdownCinematic.current
    val burst = remember { Animatable(1f) }
    var displayedVisual by remember { mutableStateOf(visual) }
    var outgoing by remember { mutableStateOf<DayAgentCardVisual?>(null) }
    var lastHapticKey by remember { mutableStateOf<Pair<String?, Long?>?>(null) }
    LaunchedEffect(visual) {
        val previous = displayedVisual
        val seconds = visual.countdownSeconds
        val hapticKey = visual.transitionKey to seconds
        if (hapticKey != lastHapticKey) {
            lastHapticKey = hapticKey
            when {
                seconds == 60L -> haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                cinematic == null && seconds != null && seconds in 1L..3L -> haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }
        }
        if (previous.transitionKey != visual.transitionKey) {
            if (previous.countdownSeconds != null && previous.countdownSeconds <= 1L) {
                burst.snapTo(0f)
                outgoing = previous
                if (cinematic == null) haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            } else {
                outgoing = null
            }
        }
        displayedVisual = visual
    }
    val outgoingKey = outgoing?.transitionKey
    LaunchedEffect(outgoingKey) {
        if (outgoingKey != null) {
            try {
                burst.animateTo(1f, tween(1250, easing = LinearEasing))
            } finally {
                if (outgoing?.transitionKey == outgoingKey) outgoing = null
            }
        }
    }
    Box(modifier) {
        if (decorated) {
            val shape = RoundedRectangle(if (displayedVisual.collapsed) 26.dp else 28.dp)
            Box(Modifier.matchParentSize().clip(shape)
                .dayAgentCardSurface(displayedVisual, activityAccent, shape))
        }
        outgoing?.let { old ->
            DayAgentCardVisualCore(old, foreground, activityAccent,
                Modifier.fillMaxWidth().graphicsLayer {
                    val p = burst.value.coerceIn(0f, 1f)
                    alpha = 1f - agentSmoothStep(0.72f, 0.94f, p)
                    val blur = 7.dp.toPx() * agentSmoothStep(0.72f, 0.94f, p)
                    renderEffect = if (blur > 0.01f) BlurEffect(blur, blur, TileMode.Clamp) else null
                }, null, decorated, shockwaveProgress = { burst.value }, drawSurface = false)
        }
        DayAgentCardVisualCore(displayedVisual, foreground, activityAccent,
            Modifier.fillMaxWidth().graphicsLayer {
                val p = if (outgoing == null) 1f else burst.value.coerceIn(0f, 1f)
                alpha = agentSmoothStep(0.76f, 0.98f, p)
                val blur = 8.dp.toPx() * (1f - agentSmoothStep(0.76f, 0.98f, p))
                renderEffect = if (blur > 0.01f) BlurEffect(blur, blur, TileMode.Clamp) else null
            }, onWeatherClick, decorated, animateCountdown = outgoing == null,
            drawSurface = false)
        if (outgoing != null) Canvas(Modifier.matchParentSize().clip(
            RoundedRectangle(if (displayedVisual.collapsed) 26.dp else 28.dp)
        )) {
            val p = burst.value.coerceIn(0f, 1f)
            val origin = Offset(size.width * 0.82f, 27.dp.toPx())
            val travel = 82.dp.toPx() * p
            repeat(32) { index ->
                val angle = index * (2.0 * Math.PI / 32.0) + (index % 3) * 0.13
                val distance = travel * (0.58f + (index % 5) * 0.10f)
                val center = Offset(
                    origin.x + kotlin.math.cos(angle).toFloat() * distance,
                    origin.y + kotlin.math.sin(angle).toFloat() * distance
                )
                drawCircle(
                    color = if (index % 3 == 0) Color.White else activityAccent,
                    radius = (1.2.dp.toPx() + (index % 4) * 0.7.dp.toPx()) * (1f - p * 0.55f),
                    center = center,
                    alpha = (1f - p).coerceIn(0f, 1f)
                )
            }
            drawCircle(activityAccent,
                radius = size.width * agentSmoothStep(0f, 0.21f, p), center = origin,
                alpha = 0.55f * (1f - agentSmoothStep(0.04f, 0.36f, p)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun Modifier.dayAgentCardSurface(
    visual: DayAgentCardVisual,
    activityAccent: Color,
    shape: Shape
): Modifier = background(
    if (visual.cardIsDark) Color.Black.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.30f),
    shape
).verticalGlassAccent(
    accentColor = activityAccent,
    shape = shape,
    lightGlass = !visual.cardIsDark,
    intensity = 1f,
    expanded = true
)

@Composable
private fun DayAgentCardVisualCore(
    visual: DayAgentCardVisual,
    foreground: Color,
    activityAccent: Color,
    modifier: Modifier,
    onWeatherClick: (() -> Unit)?,
    decorated: Boolean,
    animateCountdown: Boolean = false,
    shockwaveProgress: (() -> Float)? = null,
    drawSurface: Boolean = true
) {
    val shape = RoundedRectangle(if (visual.collapsed) 26.dp else 28.dp)
    fun shock(x: Float, y: Float, arrival: Float, origin: Boolean = false): Modifier =
        if (shockwaveProgress == null) Modifier else Modifier.graphicsLayer {
            val p = shockwaveProgress().coerceIn(0f, 1f)
            val wave = ((p - arrival) / 0.44f).coerceIn(0f, 1f)
            val push = if (wave < 0.40f) {
                agentSmoothStep(0f, 0.40f, wave)
            } else {
                1f - agentSmoothStep(0.40f, 1f, wave)
            }
            translationX = x.dp.toPx() * push
            translationY = y.dp.toPx() * push
            if (origin) {
                alpha = 1f - agentSmoothStep(0.02f, 0.28f, p)
                scaleX = 1f + 0.12f * p
                scaleY = scaleX
            }
        }
    Column(
        modifier = modifier
            .clip(shape)
            .then(if (decorated && drawSurface) Modifier.dayAgentCardSurface(
                visual, activityAccent, shape
            ) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (visual.collapsed) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    visual.activityLabel,
                    modifier = shock(-22f, -6f, 0.15f),
                    color = activityAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                visual.courseName?.let { courseName ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        courseName,
                        modifier = Modifier.weight(1f).then(shock(-16f, -5f, 0.08f)),
                        color = foreground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                if (visual.countdownText.isNotBlank()) {
                    if (animateCountdown && visual.countdownSeconds != null) {
                        AgentCountdownNumber(visual.countdownText, visual.countdownSeconds,
                            activityAccent, visual.transitionKey)
                    } else {
                        Text(
                            visual.countdownText,
                            modifier = shock(16f, -9f, 0f, origin = true),
                            color = activityAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
            if (visual.courseName != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        visual.locationText,
                        modifier = Modifier.weight(1f).then(shock(-16f, 14f, 0.12f)),
                        color = foreground.copy(alpha = if (decorated) 0.56f else 0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        visual.focusTimeText,
                        modifier = shock(8f, 14f, 0.04f),
                        color = foreground.copy(alpha = if (decorated) 0.56f else 0.82f),
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
                modifier = shock(-13f, 9f, 0.14f),
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
                        .then(if (onWeatherClick != null) Modifier.clickable(onClick = onWeatherClick) else Modifier)
                        .then(shock(-14f, 10f, 0.19f)),
                    color = foreground.copy(alpha = if (decorated) 0.58f else 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                visual.trailingStatus?.let { status ->
                    Spacer(Modifier.width(6.dp))
                     Text(
                         text = status,
                         modifier = shock(10f, 10f, 0.10f),
                         color = if (visual.weatherAlert) {
                            if (visual.cardIsDark) Color(0xFFFFB86B) else Color(0xFFB84D00)
                        } else {
                            foreground.copy(alpha = if (decorated) 0.52f else 0.78f)
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
