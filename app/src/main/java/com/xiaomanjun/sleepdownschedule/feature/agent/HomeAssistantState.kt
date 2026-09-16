package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.xiaomanjun.sleepdownschedule.glass.ui.topAssistantBezierSpec
import kotlin.math.abs

internal enum class HomeAssistantStage { Hidden, Reminder, Conversation }

@Stable
internal class HomeAssistantState(private val scope: CoroutineScope) {
    var stage by mutableStateOf(HomeAssistantStage.Hidden)
        private set
    var pullPixels by mutableFloatStateOf(0f)
        private set
    var armed by mutableStateOf(false)
        private set
    var dragging by mutableStateOf(false)
        private set
    var pullingHome by mutableStateOf(false)
        private set
    var editing by mutableStateOf(false)
    var reminder by mutableStateOf<AssistantReminder?>(null)
        private set
    var sourceBounds by mutableStateOf<Rect?>(null)
    var reminderBounds by mutableStateOf<Rect?>(null)
    var reminderGeneration by mutableIntStateOf(0)
        private set
    var reminderHidden by mutableStateOf(false)
    var closing by mutableStateOf(false)
    var conversationStartsFullScreen by mutableStateOf(false)
        private set
    internal val controlBounds = mutableMapOf<Any, LayoutCoordinates>()
    fun canPullAt(position: Offset): Boolean = controlBounds.values.none {
        it.isAttached && it.boundsInRoot().contains(position)
    }
    private var returnJob: Job? = null
    private var thresholdNotified = false
    val visible: Boolean get() = stage != HomeAssistantStage.Hidden

    fun beginPull() {
        returnJob?.cancel()
        pullingHome = stage == HomeAssistantStage.Hidden
        dragging = true
        armed = false
        thresholdNotified = false
    }

    fun pull(distance: Float, density: Float): Boolean {
        pullPixels = assistantPullDistance(distance, 150f * density)
        val crossed = !thresholdNotified && pullPixels >= 72f * density
        if (crossed) thresholdNotified = true
        armed = pullPixels >= 72f * density
        return crossed
    }

    fun release(commit: Boolean = armed) {
        dragging = false
        if (commit) openConversation()
        armed = false
        returnJob?.cancel()
        returnJob = scope.launch {
            AnimationState(pullPixels).animateTo(0f, topAssistantBezierSpec(pullPixels, 0f, vertical = true)) {
                pullPixels = value
            }
        }
        if (!commit && stage == HomeAssistantStage.Reminder) reminderGeneration++
    }

    fun showReminder(event: AssistantReminder) {
        if (stage == HomeAssistantStage.Conversation) return
        reminder = event
        reminderHidden = false
        closing = false
        stage = HomeAssistantStage.Reminder
        reminderGeneration++
    }

    fun openConversation() {
        conversationStartsFullScreen = stage == HomeAssistantStage.Reminder
        sourceBounds = reminderBounds.takeIf { stage == HomeAssistantStage.Reminder }
        closing = false
        stage = HomeAssistantStage.Conversation
    }

    fun reset() {
        returnJob?.cancel()
        stage = HomeAssistantStage.Hidden
        reminder = null
        sourceBounds = null
        reminderBounds = null
        reminderHidden = false
        closing = false
        armed = false
        dragging = false
        pullPixels = 0f
        pullingHome = false
        conversationStartsFullScreen = false
    }
}

internal val LocalHomeAssistant = staticCompositionLocalOf<HomeAssistantState?> { null }

/** Exclude the control's entire gesture, even after the finger leaves its hit area. */
internal fun Modifier.excludeHomeAssistantPull(): Modifier = composed {
    val assistant = LocalHomeAssistant.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(assistant, key) { onDispose { assistant.controlBounds.remove(key) } }
    onGloballyPositioned { assistant.controlBounds[key] = it }
}

/** Direction locks before consuming; taps, upward scrolling, horizontal paging and multi-touch pass through. */
internal fun Modifier.assistantPullGesture(
    enabled: Boolean,
    canStart: (Offset) -> Boolean = { true },
    onStart: () -> Unit = {},
    onDistance: (Float) -> Unit,
    onRelease: (Boolean) -> Unit
): Modifier = composed {
    val latestCanStart by rememberUpdatedState(canStart)
    val latestStart by rememberUpdatedState(onStart)
    val latestDistance by rememberUpdatedState(onDistance)
    val latestRelease by rememberUpdatedState(onRelease)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!latestCanStart(down.position)) return@awaitEachGesture
            var total = Offset.Zero
            var owned = false
            var ended = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (owned) {
                            change.consume()
                            latestRelease(true)
                            ended = true
                        }
                        break
                    }
                    if (!owned && (change.isConsumed ||
                            change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis)) break
                    total += change.positionChange()
                    if (!owned) {
                        if (total.y < -viewConfiguration.touchSlop ||
                            abs(total.x) > viewConfiguration.touchSlop && abs(total.x) >= abs(total.y)) break
                        if (total.y > viewConfiguration.touchSlop && total.y > abs(total.x) * 1.3f) {
                            owned = true
                            latestStart()
                        }
                    }
                    if (owned) {
                        change.consume()
                        latestDistance((total.y - viewConfiguration.touchSlop).coerceAtLeast(0f))
                    }
                }
            } finally {
                if (owned && !ended) latestRelease(false)
            }
        }
    }
}
