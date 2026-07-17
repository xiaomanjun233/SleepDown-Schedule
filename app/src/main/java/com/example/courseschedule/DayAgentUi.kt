package com.example.courseschedule

import android.Manifest
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
import com.kyant.backdrop.catalog.components.liquidButtonInteraction
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun TodayAgentHost(
    state: AppState,
    date: LocalDate,
    backdrop: Backdrop?,
    textColor: Color,
    collapsed: Boolean,
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
            message = "今日 Agent 会在日视图整理课程、课间空档与天气，并可使用已配置的 AI 每天生成一次个性化文案。动态倒计时均在本地计算；你可以随时在今日 Agent 设置中关闭。",
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
            dailyAiEnabled = DayAgentPreferences.isDailyAiEnabled(context),
            weatherEnabled = DayAgentPreferences.isWeatherEnabled(context),
            hasApiKey = hasApiKey,
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
    dailyAiEnabled: Boolean,
    weatherEnabled: Boolean,
    hasApiKey: Boolean,
    onAgentAction: (AgentValidatedAction) -> Unit
) {
    val context = LocalContext.current
    val scheduleId = state.config.id
    val scheduleName = state.schedules.firstOrNull { it.id == scheduleId }?.name
    val repository = remember(context) { DayAgentRepository(context.applicationContext) }
    val weatherRepository = remember(context) { DayAgentWeatherRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val session by repository.observeSession(scheduleId, date)
        .collectAsStateWithLifecycle(initialValue = repository.cachedSession(scheduleId, date))
    val messages by repository.observeMessages(scheduleId, date)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var weather by remember(date, weatherEnabled) {
        mutableStateOf(if (weatherEnabled) DayAgentWeatherStore.load(context) else null)
    }
    var now by remember(date) { mutableStateOf(LocalDateTime.now(ShanghaiZone)) }
    var dialogOpen by remember(date, scheduleId) { mutableStateOf(false) }
    var pendingQuestion by remember(date, scheduleId) { mutableStateOf<String?>(null) }
    var generationError by remember(date, scheduleId) { mutableStateOf<String?>(null) }
    var cardBounds by remember(date, scheduleId) { mutableStateOf<Rect?>(null) }

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

    LaunchedEffect(scheduleId, date, dailyAiEnabled, weatherEnabled) {
        // Keep initialization work out of the day/week transition's frame budget.
        delay(280)
        repository.cleanup(date)
        weather = if (weatherEnabled) weatherRepository.getWeather() else null
        val initialFacts = buildDayAgentFacts(
            state.courses,
            state.periods,
            state.config,
            date,
            weather,
            scheduleName,
            settingContext = context
        )
        if (dailyAiEnabled) {
            repository.ensureDailyPack(scheduleId, initialFacts).onFailure { generationError = it.message }
        }
    }

    val facts = remember(state.courses, state.periods, state.config, scheduleName, date, weather, now) {
        buildDayAgentFacts(state.courses, state.periods, state.config, date, weather, scheduleName, now, context)
    }
    val pack = DailyAgentPack.decodeOrDefault(session?.dailyPackJson)
    val rendered = TodayAgentTimelineEngine.render(pack, facts)
    val foreground = LocalAdaptiveGlass.current.contentColor
    val status = when (session?.generationStatus) {
        "GENERATING" -> "正在生成今日文案"
        "READY" -> "${session?.providerId} · 今日已生成"
        "FAILED" -> "使用本地文案"
        else -> when {
            !dailyAiEnabled -> "本地时间引擎"
            AiImportSettingsStore.load(context).apiKey.isBlank() -> "配置 AI 后生成个性化建议"
            else -> "本地时间引擎"
        }
    }

    GlassSurface(
        backdrop = backdrop,
        config = state.config,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardBounds = it.boundsInWindow() }
             .liquidButtonInteraction(
                 onClick = {
                     if (hasApiKey) {
                         pendingQuestion = null
                         dialogOpen = true
                     }
                 },
                 isInteractive = hasApiKey,
                 showHighlight = false
             ),
        shape = RoundedCornerShape(if (collapsed) 26.dp else 28.dp),
        tokens = GlassTokens.dialog(intensity = 0.82f).copy(blur = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color(state.config.cardColorArgb).copy(alpha = 0.10f))
                .padding(horizontal = 16.dp, vertical = if (collapsed) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = Color(0xFF168CFF), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("今日助手", color = foreground, fontWeight = FontWeight.SemiBold)
                    if (!collapsed) Text(status, color = foreground.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                }
                if (session?.generationStatus == "GENERATING") {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF168CFF))
                } else if (dailyAiEnabled && AiImportSettingsStore.load(context).apiKey.isNotBlank()) {
                    AgentSimplePressSurface(
                        backdrop = backdrop,
                        config = state.config,
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        tokens = GlassTokens.pill(intensity = 0.90f).copy(blur = 4.dp, surfaceAlpha = 0.22f),
                        onClick = {
                            scope.launch {
                                generationError = null
                                repository.ensureDailyPack(scheduleId, facts, force = true)
                                    .onFailure { generationError = it.message }
                            }
                        }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_agent_refresh),
                                contentDescription = "重新生成",
                                modifier = Modifier.size(17.dp),
                                tint = foreground.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = if (collapsed) rendered.compactText else rendered.text,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "today-agent-copy"
            ) { copy ->
                Text(copy, color = foreground, style = if (collapsed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            }
            AnimatedVisibility(!collapsed && hasApiKey, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rendered.quickQuestions.take(2).forEach { question ->
                            AgentSuggestionPill(question, backdrop, state.config, Modifier.weight(1f)) {
                                pendingQuestion = question
                                dialogOpen = true
                            }
                        }
                    }
                    if (weatherEnabled && weather == null && !weatherRepository.hasLocationPermission()) {
                        Text(
                            "启用粗略位置以显示天气",
                            modifier = Modifier.clickable { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            color = Color(0xFF168CFF),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    generationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
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
            initialText = rendered.text,
            initialQuestion = pendingQuestion,
            sourceBounds = cardBounds,
            onAgentAction = onAgentAction,
            onDismiss = {
                dialogOpen = false
                pendingQuestion = null
            }
        )
    }
}

@Composable
private fun AgentSuggestionPill(
    text: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AgentSimplePressSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tokens = GlassTokens.pill(intensity = 0.92f).copy(blur = 4.dp, surfaceAlpha = 0.22f),
        onClick = onClick
    ) {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            color = LocalAdaptiveGlass.current.contentColor,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
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
    onAgentAction: (AgentValidatedAction) -> Unit,
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
    var answerBounds by remember { mutableStateOf<Rect?>(null) }
    var dialogWindow by remember { mutableStateOf<Window?>(null) }
    var appliedActionKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val expansion = remember { Animatable(0f) }
    val conversationListState = rememberLazyListState()
    val conversationBackdrop = rememberLayerBackdrop()
    val inputBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop, conversationBackdrop)
    } else {
        conversationBackdrop
    }
    val foreground = LocalAdaptiveGlass.current.contentColor
    val configuration = LocalConfiguration.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val answerTopPadding = statusBarTop + (configuration.screenHeightDp.dp * 0.018f).coerceIn(10.dp, 20.dp)
    val answerMaxHeight = (configuration.screenHeightDp.dp * 0.58f).coerceIn(280.dp, 560.dp)

    fun updateWindowBlur(progress: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialogWindow?.let { window ->
                window.attributes = window.attributes.apply {
                    blurBehindRadius = (56f * progress.coerceIn(0f, 1f)).toInt()
                }
            }
        }
    }

    fun dismissAnimated() {
        scope.launch {
            expansion.animateTo(0f, tween(210, easing = FastOutSlowInEasing)) {
                updateWindowBlur(value)
            }
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

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            dialogWindow = window
            val previousBlurRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.attributes?.blurBehindRadius
            } else {
                null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window?.let { it.attributes = it.attributes.apply { blurBehindRadius = 0 } }
            }
            onDispose {
                dialogWindow = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window?.let { it.attributes = it.attributes.apply { blurBehindRadius = previousBlurRadius ?: 0 } }
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
         Box(
             Modifier
                 .fillMaxSize()
                 .graphicsLayer { alpha = expansion.value }
                 .background(Color.Black.copy(alpha = 0.30f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismissAnimated
                )
        ) {
            GlassSurface(
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                     .padding(start = 14.dp, top = answerTopPadding, end = 14.dp)
                     .heightIn(min = 150.dp, max = answerMaxHeight)
                     .layerBackdrop(conversationBackdrop)
                     .onGloballyPositioned { answerBounds = it.boundsInWindow() }
                    .graphicsLayer {
                        val target = answerBounds
                        val source = sourceBounds
                        val p = expansion.value
                        if (target == null) {
                            alpha = 0f
                        } else {
                            alpha = p
                            if (source != null && source.width > 0f && source.height > 0f) {
                                val startScaleX = (source.width / target.width).coerceIn(0.16f, 1f)
                                val startScaleY = (source.height / target.height).coerceIn(0.12f, 1f)
                                scaleX = startScaleX + (1f - startScaleX) * p
                                scaleY = startScaleY + (1f - startScaleY) * p
                                translationX = (source.center.x - target.center.x) * (1f - p)
                                translationY = (source.center.y - target.center.y) * (1f - p)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(28.dp),
                tokens = GlassTokens.dialog(intensity = 0.90f).copy(blur = 5.dp)
            ) {
                 Column(
                     Modifier
                         .background(Color.Black.copy(alpha = if (appUsesDarkTheme(state.config)) 0.18f else 0.08f))
                         .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                     verticalArrangement = Arrangement.spacedBy(10.dp)
                 ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦ 今日助手", color = foreground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${AiImportSettingsStore.load(LocalContext.current).profile.displayName}", color = foreground.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
                    }
                     LazyColumn(
                         state = conversationListState,
                         modifier = Modifier.weight(1f, fill = false),
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
                                        AgentSimplePressSurface(
                                            backdrop = backdrop,
                                            config = state.config,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp),
                                            tokens = GlassTokens.pill(intensity = 0.92f).copy(blur = 4.dp, surfaceAlpha = 0.22f),
                                            onClick = {
                                                if (actionKey !in appliedActionKeys) {
                                                    onAgentAction(action)
                                                    appliedActionKeys = appliedActionKeys + actionKey
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (actionKey in appliedActionKeys) "已执行：${action.summary}"
                                                else agentActionButtonLabel(action),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                color = if (actionKey in appliedActionKeys) foreground.copy(alpha = 0.62f)
                                                else if (action.type == AgentValidatedActionType.DELETE) Color(0xFFFF453A)
                                                else Color(0xFF168CFF),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (streamingText.isNotBlank()) item {
                            AgentMarkdownText(
                                parseAgentActions(streamingText, facts).displayText,
                                foreground,
                                MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (sending && streamingText.isBlank()) item { Text("正在思考…", color = foreground.copy(alpha = 0.6f)) }
                        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) } }
                    }
                }
            }

             GlassSurface(
                     backdrop = inputBackdrop,
                    config = state.config,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        LiquidButton(
                            onClick = {
                                if (sending) {
                                    requestJob?.cancel()
                                    requestJob = null
                                    sending = false
                                    streamingText = ""
                                } else {
                                    send()
                                }
                            },
                            backdrop = inputBackdrop,
                            modifier = Modifier.size(40.dp),
                            height = 40.dp,
                            contentPadding = PaddingValues(0.dp),
                            blurRadius = 4.dp,
                            lensHeight = 12.dp,
                            lensAmount = 24.dp,
                            surfaceColor = Color(0xFF168CFF).copy(alpha = 0.82f),
                            chromaticAberration = false
                        ) {
                            Text(if (sending) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
        LaunchedEffect(answerBounds) {
             if (answerBounds != null && expansion.value == 0f) {
                 expansion.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) {
                     updateWindowBlur(value)
                 }
             }
        }
        LaunchedEffect(messages.size, streamingText.length, sending, error) {
            delay(24)
            val lastIndex = conversationListState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) conversationListState.scrollToItem(lastIndex)
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
            initialQuestion?.takeIf { it.isNotBlank() }?.let(::send)
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
