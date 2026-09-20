package com.xiaomanjun.sleepdownschedule.feature.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.TopAssistantSystemBars
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeAssistantHost(
    controller: HomeAssistantState,
    state: AppState,
    available: Boolean,
    remindersAllowed: Boolean,
    backdrop: Backdrop?,
    backgroundMotion: DayAgentBackgroundMotionState,
    onAgentAction: AgentActionHandler,
    onImportFile: (Uri) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val screenCornerPx = deviceScreenCornerRadiusPx()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var clockVersion by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val preferencesVersion by DayAgentPreferences.changes.collectAsStateWithLifecycle(0L)
    val enabled = remember(preferencesVersion) {
        DayAgentPreferences.isEnabled(context) && DayAgentPreferences.isWeekAssistantEnabled(context)
    }
    val decided = remember(preferencesVersion) { DayAgentPreferences.hasDecision(context) }
    val weatherEnabled = remember(preferencesVersion) { DayAgentPreferences.isWeatherEnabled(context) }
    val repository = remember(context) { DayAgentRepository(context.applicationContext) }
    val weatherRepository = remember(context) { DayAgentWeatherRepository(context.applicationContext) }
    var weather by remember(weatherEnabled) { mutableStateOf(if (weatherEnabled) DayAgentWeatherStore.load(context) else null) }
    val aiVersion by AiImportSettingsStore.changes.collectAsStateWithLifecycle()
    val hasAi = remember(aiVersion) { AiImportSettingsStore.resolveAvailableSettings(context) != null ||
        AiImportSettingsStore.load(context).profile.id == AiProviderPresets.dailyFree.id }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                foreground = true
                now = LocalDateTime.now()
                clockVersion++
            }
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                if (controller.stage != HomeAssistantStage.Conversation) controller.reset()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = LocalDateTime.now()
                clockVersion++
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(available, enabled, decided, remindersAllowed) {
        if (!available || decided && !enabled) controller.reset()
        else if (!remindersAllowed && controller.stage == HomeAssistantStage.Reminder) controller.closing = true
    }
    LaunchedEffect(controller.visible) {
        if (!controller.visible) {
            backgroundMotion.progress.snapTo(0f)
            backgroundMotion.backgroundZoom.snapTo(1f)
        }
    }
    DisposableEffect(state.config.id) { onDispose { controller.reset() } }
    LaunchedEffect(foreground, available, controller.visible, clockVersion) {
        if (!foreground || !available) return@LaunchedEffect
        while (true) {
            now = LocalDateTime.now()
            val next = if (controller.visible) now.plusMinutes(1).withSecond(0).withNano(0)
                else now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1L))
        }
    }
    val date = now.toLocalDate()
    val facts = remember(state.courses, state.periods, state.config, state.schedules, date, weather, now) {
        DayAgentRenderCache.facts(state, date, weather, state.schedules.firstOrNull { it.id == state.config.id }?.name, context)
            .copy(now = now)
    }
    LaunchedEffect(controller.visible, weatherEnabled, date) {
        if (controller.visible && weatherEnabled) weather = weatherRepository.getWeather()
        if (controller.visible && DayAgentRenderCache.shouldCleanup(date)) repository.cleanup(date)
    }
    val events = remember(facts.today, facts.tomorrow) { assistantReminders(facts.today + facts.tomorrow) }
    LaunchedEffect(events, available, remindersAllowed, enabled, foreground, clockVersion, controller.stage, controller.dragging) {
        if (!available || !remindersAllowed || !enabled || !foreground || controller.dragging ||
            controller.stage == HomeAssistantStage.Conversation) return@LaunchedEffect
        val ledger = context.getSharedPreferences("home_assistant_reminders", Context.MODE_PRIVATE)
        val dayKey = "events:$date"
        while (true) {
            val current = LocalDateTime.now()
            val previousDayKey = "events:${date.minusDays(1)}"
            val seen = ledger.getStringSet(dayKey, emptySet()).orEmpty() +
                ledger.getStringSet(previousDayKey, emptySet()).orEmpty()
            val due = dueAssistantReminders(events, current, seen)
            if (due.isNotEmpty()) {
                now = current
                controller.showReminder(due.first())
                ledger.edit {
                    ledger.all.keys.filter { it != dayKey && it != previousDayKey }.forEach(::remove)
                    putStringSet(dayKey, ledger.getStringSet(dayKey, emptySet()).orEmpty() + due.map { it.key })
                }
            }
            val next = events.firstOrNull { it.at.isAfter(current) }?.at ?: break
            delay(Duration.between(current, next).toMillis().coerceAtLeast(1L))
        }
    }
    var hideStatusBar by remember { mutableStateOf(false) }
    LaunchedEffect(controller.visible, controller.closing, foreground) {
        if (controller.closing) delay(320)
        hideStatusBar = controller.visible && !controller.closing && foreground
    }
    if (controller.visible) TopAssistantSystemBars(hidden = hideStatusBar)

    if (controller.stage == HomeAssistantStage.Conversation && !decided) {
        LiquidAlertDialog(
            title = "启用AI助理？",
            message = "下拉首页即可与助手对话。课程提醒与倒计时在本机计算，对话和文件解析使用你选择的 AI 服务。",
            actions = listOf(
                LiquidAlertAction("暂不启用", LiquidAlertActionStyle.Secondary) {
                    DayAgentPreferences.setEnabled(context, false); controller.reset()
                },
                LiquidAlertAction("启用", LiquidAlertActionStyle.Primary) { DayAgentPreferences.setEnabled(context, true) }
            ),
            backdrop = backdrop, config = state.config,
            onDismissRequest = { controller.reset() }
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
        val insets = ViewCompat.getRootWindowInsets(view)
        val topCutout = insets?.displayCutout?.boundingRects?.filter {
            it.top <= (insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top)
        }?.firstOrNull()
        // Capture the physical cutout before hiding system bars. Some OEMs report no cutout
        // while immersive, which otherwise moves the closing target halfway up the status bar.
        val anchor = remember(controller.visible, maxWidth, maxHeight, density.density) { with(density) {
            val centerX = topCutout?.exactCenterX() ?: maxWidth.toPx() / 2f
            val centerY = topCutout?.exactCenterY() ?: safeTop.toPx() / 2f
            val halfWidth = maxOf(topCutout?.width()?.toFloat() ?: 0f, 36.dp.toPx()) / 2f
            Rect(centerX - halfWidth, (centerY - 12.dp.toPx()).coerceAtLeast(0f), centerX + halfWidth, centerY + 12.dp.toPx())
        } }
        val closingAnchor = with(density) {
            // A fuller, slightly lower docking cap keeps the final return leg visible.
            Rect(anchor.left - 4.dp.toPx(), anchor.top + 1.dp.toPx(),
                anchor.right + 4.dp.toPx(), anchor.bottom + 7.dp.toPx())
        }
        if (controller.stage == HomeAssistantStage.Hidden && controller.pullPixels > 0f) {
            Text(
                if (controller.armed) "松开使用助手" else "继续下拉使用助手",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = safeTop + 18.dp)
                    .graphicsLayer { alpha = (controller.pullPixels / with(density) { 44.dp.toPx() }).coerceIn(0f, 1f) }
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        val presentation = rememberDayAgentPresentation(
            facts, false, true, weatherEnabled, weatherRepository.hasLocationPermission(), hasAi, !hasAi,
            focusOverride = controller.reminder?.slot
        )
        val edgeInset = 8.dp
        val width = if (maxWidth >= 600.dp && maxHeight >= 480.dp) maxWidth * 0.5f else maxWidth - edgeInset * 2f
        val reminderMaxHeight = maxHeight - edgeInset * 2f
        val handleHeight = 24.dp
        var reminderContentHeightPx by remember { mutableIntStateOf(0) }
        val height = with(density) { reminderContentHeightPx.toDp() }.coerceAtLeast(handleHeight)
        val target = with(density) {
            Rect((maxWidth - width).toPx() / 2f, edgeInset.toPx(), (maxWidth + width).toPx() / 2f, (height + edgeInset).toPx())
        }
        val noticeMotion = rememberTopAssistantMotion()
        var reminderTouched by remember { mutableStateOf(false) }
        fun reminderGeometry(): Rect {
            val rect = topAssistantMorphRect(anchor, target, noticeMotion.drop.value, noticeMotion.spread.value)
            return rect.copy(bottom = rect.bottom + if (controller.pullingHome) 0f else controller.pullPixels)
        }
        LaunchedEffect(controller.stage, controller.closing) {
            if (controller.stage == HomeAssistantStage.Reminder) {
                noticeMotion.animateTo(if (controller.closing) 0f else 1f, overshoot = !controller.closing)
                if (controller.closing) controller.reset()
            } else if (controller.stage == HomeAssistantStage.Hidden) noticeMotion.snapTo(0f)
        }
        val accessibility = LocalAccessibilityManager.current
        val displayMillis = accessibility?.calculateRecommendedTimeoutMillis(6_000L, true, true, true) ?: 6_000L
        LaunchedEffect(controller.stage, controller.reminderGeneration) {
            if (controller.stage != HomeAssistantStage.Reminder) return@LaunchedEffect
            snapshotFlow { controller.dragging || reminderTouched || noticeMotion.drop.isRunning }.collectLatest { touching ->
                if (!touching) {
                    delay(displayMillis)
                    controller.closing = true
                }
            }
        }
        if (controller.reminder != null && !controller.reminderHidden) {
            BackHandler(controller.stage == HomeAssistantStage.Reminder) { controller.closing = true }
            TopAssistantSurface(
                backdrop, state.config, RoundedRectangle(with(density) { (screenCornerPx - edgeInset.toPx()).coerceAtLeast(16.dp.toPx()).toDp() }),
                modifier = Modifier.offset {
                    val rect = reminderGeometry()
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }.assistantMorphBounds(::reminderGeometry)
                    .graphicsLayer {
                        alpha = if (controller.closing) topAssistantDockAlpha(noticeMotion.drop.value) else 1f
                    }
                    .onGloballyPositioned { controller.reminderBounds = it.boundsInRoot() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            reminderTouched = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                            } finally { reminderTouched = false }
                        }
                    }
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("关闭提醒") {
                            controller.closing = true
                            true
                        })
                    }
                    .clickable(enabled = controller.stage == HomeAssistantStage.Reminder && !controller.closing,
                        onClickLabel = "进入助手全屏") { controller.openConversation() }
                    .assistantPullGesture(
                        enabled = controller.stage == HomeAssistantStage.Reminder && !controller.closing,
                        onStart = controller::beginPull,
                        onDistance = { if (controller.pull(it, density.density)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onRelease = { controller.release(it && controller.armed) }
                    )
            ) {
                Column(Modifier.fixedAssistantContentWidth(width, reminderMaxHeight)
                    .onSizeChanged { reminderContentHeightPx = it.height }
                    .padding(top = (safeTop - edgeInset - 14.dp).coerceAtLeast(0.dp)).graphicsLayer {
                    alpha = ((noticeMotion.drop.value - 0.20f) / 0.55f).coerceIn(0f, 1f)
                }) {
                    DayAgentCardVisualContent(presentation.visual, Color.White, presentation.accent, decorated = false)
                    Box(Modifier.fillMaxWidth().height(handleHeight), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp, 4.dp).clip(Capsule()).background(Color.White.copy(alpha = 0.65f)))
                    }
                }
            }
        }
        if (controller.stage == HomeAssistantStage.Conversation && enabled) {
            key(state.config.id, date) {
                val messages by remember(repository, state.config.id, date) {
                    repository.observeMessages(state.config.id, date)
                }.collectAsStateWithLifecycle(emptyList())
                DayAgentConversationDialog(
                    state, backdrop, facts, messages, repository, presentation.initialText, null,
                    controller.sourceBounds ?: anchor,
                    with(density) { (screenCornerPx - edgeInset.toPx()).coerceAtLeast(16.dp.toPx()).toDp() },
                    presentation.visual, Color.White, presentation.accent,
                    GlassTokens.dialog(), backgroundMotion,
                    onAgentAction = onAgentAction,
                    onOverlayReady = { controller.reminderHidden = true },
                    onPrepareDismiss = { controller.closing = true },
                    onSourceHandoff = {},
                    onDismiss = controller::reset,
                    homePresentation = true,
                    homeAnchorBounds = closingAnchor,
                    homeInitiallyFullScreen = controller.conversationStartsFullScreen,
                    onImportFile = onImportFile
                )
            }
        }
    }
}
