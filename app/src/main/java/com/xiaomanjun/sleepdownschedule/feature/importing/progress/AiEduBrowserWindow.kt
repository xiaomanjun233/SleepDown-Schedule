package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.softBlurEnter
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduBrowserSessions
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import kotlin.math.roundToInt

/** The very same document used by EDU_WORKSPACE, attached only while this AI page is visible. */
@Composable
internal fun AiEduBrowserWindow(taskId: String?, config: ScheduleConfigEntity, backdrop: Backdrop?, modifier: Modifier = Modifier) {
    val binding by AiEduBrowserSessions.current.collectAsStateWithLifecycle()
    val session = binding?.takeIf { it.taskId == taskId }?.session ?: return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var expanded by remember(session) { mutableStateOf(true) }
    val motion = remember(session) { EduBrowserWindowMotion() }
    var dragOrigin by remember(session) { mutableStateOf(Offset.Zero) }
    var unavailable by remember(session) { mutableStateOf(false) }
    if (!lifecycle.isAtLeast(Lifecycle.State.STARTED)) return
    val activity by session.activity.collectAsStateWithLifecycle()
    val colors = LocalAiImportTextColors.current
    val density = LocalDensity.current
    val maxFlingSpeed = with(density) { 4000.dp.toPx() }
    BoxWithConstraints(modifier.padding(12.dp)) {
        if (maxWidth < 80.dp || maxHeight < 56.dp) return@BoxWithConstraints
        val expandedWidth = minOf(380.dp, maxWidth * 0.86f)
        val bodyHeight = minOf(280.dp, (maxHeight - 48.dp) * 0.6f)
        val width by animateDpAsState(if (expanded) expandedWidth else minOf(208.dp, maxWidth),
            spring(dampingRatio = 0.9f, stiffness = 420f), label = "edu-window-width")
        val visibleBodyHeight by animateDpAsState(if (expanded) bodyHeight else 0.dp,
            spring(dampingRatio = 0.9f, stiffness = 420f), label = "edu-window-body")
        val maxX = with(density) { (maxWidth - width).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - 48.dp - visibleBodyHeight).toPx().coerceAtLeast(0f) }
        SideEffect { motion.updateBounds(Offset(maxX, maxY)) }
        DisposableEffect(motion) { onDispose { motion.stop() } }
        LaunchedEffect(motion.active) {
            if (motion.active) {
                var previous = withFrameNanos { it }
                while (motion.active) {
                    val now = withFrameNanos { it }
                    motion.advance((now - previous) / 1_000_000_000f)
                    previous = now
                }
            }
        }
        GlassSurface(backdrop, config, shape = RoundedRectangle(20.dp), nativeMaterial = true,
            nativeDark = appUsesDarkTheme(config), debugLabel = "AiEduBrowserWindow",
            modifier = Modifier.offset { IntOffset(motion.position.x.roundToInt(), motion.position.y.roundToInt()) }
                .pointerInput(session, motion) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        motion.stop()
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    }
                }
                .width(width).clip(RoundedCornerShape(20.dp)).softBlurEnter(session)
                .semantics { contentDescription = "教务调试窗口" }) {
            Column {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { dragOrigin = it.positionInRoot() }
                        .pointerInput(session, motion, maxFlingSpeed) {
                        val tracker = VelocityTracker()
                        detectDragGestures(null, onDragStart = { down, slop, _ ->
                            motion.beginDrag()
                            tracker.resetTracking()
                            tracker.addPosition(down.uptimeMillis, dragOrigin + down.position)
                            tracker.addPosition(slop.uptimeMillis, dragOrigin + slop.position)
                        }, onDragEnd = { up ->
                            tracker.addPosition(up.uptimeMillis, dragOrigin + up.position)
                            val velocity = tracker.calculateVelocity()
                            motion.release(Offset(velocity.x, velocity.y), maxFlingSpeed)
                        }, onDragCancel = { motion.cancelDrag() }) { change, delta ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, dragOrigin + change.position)
                            motion.dragBy(delta)
                        }
                    }.semantics {
                        contentDescription = "拖动教务窗口"
                        customActions = listOf(
                            CustomAccessibilityAction("移到左上角") { motion.moveToFraction(Offset.Zero); true },
                            CustomAccessibilityAction("移到右下角") { motion.moveToFraction(Offset(1f, 1f)); true })
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_web_mode), null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.address.ifBlank { "教务网页" }, style = AiImportTextStyles.SmallTitle,
                                color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(activity, style = AiImportTextStyles.Caption, color = colors.secondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(if (expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                        if (expanded) "收起教务网页" else "展开教务网页", tint = colors.primary,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { motion.stop(); expanded = !expanded }.padding(11.dp))
                }
                // Keep a real, nonzero viewport mounted when collapsed. Only its visible slot shrinks;
                // the model can continue DOM/fetch/script work without recreating or pausing the page.
                Box(Modifier.fillMaxWidth().height(visibleBodyHeight).clipToBounds()) {
                    AndroidView(factory = { context -> FrameLayout(context).also { unavailable = !session.attach(it) } },
                        modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(expandedWidth, bodyHeight),
                        update = { it.importantForAccessibility = if (expanded) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS },
                        onRelease = { session.restore(it) })
                    if (unavailable) Text("网页已关闭或正在其他窗口中显示", color = colors.secondary,
                        style = AiImportTextStyles.Secondary, modifier = Modifier.fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(16.dp))
                }
            }
        }
    }
}
