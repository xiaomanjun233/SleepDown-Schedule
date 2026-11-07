package com.itos.coui.segmentbutton

import android.graphics.Paint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 受控胶囊分段选择器。默认在拖过新项目时提交，commitOnRelease 仅在有效 UP 后提交草稿。
 * backdrop 必须来自不含本控件的兄弟背景节点。small 使用 32dp/2dp 紧凑预设；普通预设为 40dp/4dp。
 * Modifier 可覆盖尺寸，指示器、采样和输入均使用实际约束。取消不新增提交，已即时提交的选择由宿主保留。
 */
@RequiresApi(34)
@Composable
fun SegmentedControl(
    modifier: Modifier = Modifier,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: CouiBackdrop?,
    dark: Boolean = CouiTheme.isDark,
    enabled: Boolean = true,
    small: Boolean = false,
    commitOnRelease: Boolean = false,
    textStyle: TextStyle = CouiSegmentedControlDefaults.textStyle(),
    containerSurfaceEnabled: Boolean = true,
    surfaceModifier: Modifier? = null,
) {
    require(labels.isNotEmpty() && selectedIndex in labels.indices) { "分段标签不能为空，选中下标必须在范围内" }
    require(!containerSurfaceEnabled || backdrop != null || surfaceModifier != null) {
        "完整外壳需要背景源或宿主明确提供的表面材质"
    }
    val floatingMaterial = containerSurfaceEnabled && backdrop != null
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current = rememberUpdatedState(selectedIndex)
    val submit = rememberUpdatedState(onSelected)
    val haptic = LocalHapticFeedback.current
    val session = remember(labels) { SegmentSelectionSession(selectedIndex) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inputCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val motion = remember { CouiSegmentMotion() }
    val touch = rememberCouiTouchMotion(enabled && containerSurfaceEnabled)
    val press = rememberCouiPressMotion()
    val indicatorPress = rememberCouiPressMotion()
    val labelPresses = labels.indices.map { key(it) { rememberCouiPressMotion() } }
    val contourEnabled = LocalCouiContourGlowEnabled.current
    val contour = remember(contourEnabled) { if (contourEnabled) CouiContour(style = CouiContourStyle.Button) else null }
    val edgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val indicatorShape = remember { CouiShape(999.dp, 2f) }
    val renderer = if (floatingMaterial) remember { CouiFloatingRenderer.standalone() } else null
    val contentLayer = rememberGraphicsLayer()
    val material = remember(dark) { CouiSegmentedControlDefaults.material(dark) }
    val shadow = remember { CouiFloatingShadow(0f, Color.Transparent, 0f, 0f, 0f, Color.Transparent, Rect.Zero, 0f) }
    val (lightInput, light) = rememberCouiSpotlight(enabled && containerSurfaceEnabled, SpotlightPreset.OpaqueMedium2, dark,
        inputTarget = { _, _ -> if (session.allowLight) rootCoordinates else null })
    val measurer = rememberTextMeasurer()
    val naturalWidths = remember(labels, textStyle, density, measurer) {
        labels.map { max(with(density) { CouiSegmentedControlDefaults.minWidth.toPx() },
            measurer.measure(it, textStyle, maxLines = 1).size.width.toFloat() +
                with(density) { CouiSegmentedControlDefaults.labelPadding.toPx() * 2f }) }
    }
    LaunchedEffect(selectedIndex, session.touching) { session.sync(selectedIndex) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            session.finish(true, current.value, commitOnRelease) { }
            indicatorPress.release()
            touch.release(Offset.Zero)
            press.release()
            labelPresses.forEach { it.release() }
            motion.release()
        }
    }
    DisposableEffect(motion) { onDispose { motion.dispose() } }

    BoxWithConstraints(modifier.height(if (small) CouiSegmentedControlDefaults.smallHeight else CouiSegmentedControlDefaults.height)) {
        val padding = with(density) { (if (small) CouiSegmentedControlDefaults.smallPadding else CouiSegmentedControlDefaults.padding).toPx() }
        val desired = (naturalWidths.sum() + padding * 2f).roundToInt()
        val actualWidth = constraints.constrainWidth(desired)
        val actualHeight = constraints.maxHeight
        val geometry = remember(actualWidth, actualHeight, padding, naturalWidths, rtl, small) {
            SegmentGeometry(actualWidth.toFloat(), actualHeight.toFloat(), padding,
                segmentWidths(naturalWidths, (actualWidth - padding * 2f).coerceAtLeast(0f)), rtl, small)
        }
        LaunchedEffect(session.preview, geometry) { motion.select(session.preview, geometry, density.density) }
        var bounds by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.width(with(density) { actualWidth.toDp() }).fillMaxHeight()
            .onSizeChanged { bounds = it }.onGloballyPositioned { rootCoordinates = it }.selectableGroup()
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) session.allowLight = true
                    }
                }
            }.then(lightInput)
            .then(if (floatingMaterial) Modifier.couiFloatingSurface(checkNotNull(backdrop), checkNotNull(renderer), material, shadow, CouiCapsuleShape, contentLayer,
                contourEnabled, foregroundComposition = true, beforeContent = { clip ->
                    withTransform({
                        translate(touch.translationX.value, touch.translationY.value)
                        scale(touch.scaleX.value + press.scale.value - 1f,
                            touch.scaleY.value + press.scale.value - 1f, center)
                    }) {
                        clipPath(clip) {
                            drawSegmentIndicator(motion.rect(geometry, indicatorPress.scale.value), geometry.height,
                                indicatorShape, contour, edgePaint, dark)
                            // 直接混合已绘制材质，不能先写进透明内容层。
                            light?.invoke(this)
                        }
                    }
                }) {
                CouiFloatingSurface(Rect(Offset(touch.translationX.value, touch.translationY.value),
                    Size(bounds.width.toFloat(), bounds.height.toFloat())),
                    scaleX = touch.scaleX.value + press.scale.value - 1f,
                    scaleY = touch.scaleY.value + press.scale.value - 1f, cornerWeight = 1f)
            } else Modifier.graphicsLayer {
                if (containerSurfaceEnabled) {
                    translationX = touch.translationX.value; translationY = touch.translationY.value
                    scaleX = touch.scaleX.value + press.scale.value - 1f
                    scaleY = touch.scaleY.value + press.scale.value - 1f
                }
            }.then(if (containerSurfaceEnabled) checkNotNull(surfaceModifier) else Modifier)
                .drawWithContent {
                    drawSegmentIndicator(motion.rect(geometry, indicatorPress.scale.value), geometry.height,
                        indicatorShape, contour, edgePaint, dark)
                    light?.invoke(this)
                    drawContent()
                })) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                if (floatingMaterial) {
                    translationX = touch.translationX.value; translationY = touch.translationY.value
                    scaleX = touch.scaleX.value + press.scale.value - 1f
                    scaleY = touch.scaleY.value + press.scale.value - 1f
                }
            }.drawWithContent {
                if (floatingMaterial) contentLayer.record { this@drawWithContent.drawContent() }
                else drawContent()
            }.clip(CouiCapsuleShape)) {
                Row(Modifier.fillMaxSize().padding(horizontal = with(density) { padding.toDp() })
                    .onGloballyPositioned { inputCoordinates = it }
                    .pointerInput(geometry, labels, enabled, commitOnRelease, containerSurfaceEnabled) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val coordinates = inputCoordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                            val tracker = VelocityTracker()
                            val start = coordinates.localToWindow(down.position)
                            tracker.addPosition(down.uptimeMillis, start)
                            // 输入只有一个 owner，自身消费横拖不会误触发公共观察器的“父级接管”。
                            if (containerSurfaceEnabled) {
                                touch.begin(start, bounds)
                                press.press(bounds)
                            }
                            session.begin(current.value)
                            val downPosition = down.position + Offset(padding, 0f)
                            val indicator = motion.rect(geometry, indicatorPress.scale.value).inflate(10f)
                            val dragging = geometry.hit(downPosition.x) == current.value || indicator.contains(downPosition)
                            if (dragging) indicatorPress.press(IntSize(motion.indicatorWidth.roundToInt(), geometry.indicatorHeight.roundToInt()))
                            var pressedLabel = geometry.hit(downPosition.x)
                            pressedLabel?.let { labelPresses[it].press(IntSize(geometry.widths[it].roundToInt(), geometry.indicatorHeight.roundToInt())) }
                            var rubberOrigin = down.position.x
                            motion.resetRubberOrigin = false
                            var completed = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.isCouiTouchCancelled() || event.changes.size != 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!coordinates.isAttached) break
                                    val delta = change.position - down.position
                                    if (change.isConsumed || (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x))) break
                                    val windowPosition = coordinates.localToWindow(change.position)
                                    change.historical.forEach { tracker.addPosition(it.uptimeMillis, coordinates.localToWindow(it.position)) }
                                    tracker.addPosition(change.uptimeMillis, windowPosition)
                                    if (change.pressed && containerSurfaceEnabled) touch.move(windowPosition)
                                    val target = geometry.hit(change.position.x + padding)
                                    if (dragging && change.pressed) {
                                        if (target != null && target != session.preview) {
                                            session.select(target, commitOnRelease) { submit.value(it) }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (!motion.moving) {
                                            if (motion.resetRubberOrigin) { rubberOrigin = change.position.x; motion.resetRubberOrigin = false }
                                            motion.dragRubber(change.position.x - rubberOrigin, indicatorPress.scale.value,
                                                session.preview == 0 || session.preview == labels.lastIndex,
                                                CouiSegmentedControlDefaults.switchDistance.toPx())
                                        }
                                        if (abs(delta.x) > viewConfiguration.touchSlop) change.consume()
                                    }
                                    if (target != pressedLabel) {
                                        pressedLabel?.let { labelPresses[it].release() }
                                        if (dragging) target?.let { labelPresses[it].press(IntSize(geometry.widths[it].roundToInt(), geometry.indicatorHeight.roundToInt())) }
                                        pressedLabel = target
                                    }
                                    if (!change.pressed) {
                                        if (target != null) session.select(target, commitOnRelease) { submit.value(it) }
                                        completed = target != null
                                        val velocity = tracker.calculateVelocity()
                                        // 公共 TouchMotion 沿原厂毫秒速度及 0.3 释放比例。
                                        touch.release(Offset(velocity.x * .0003f, velocity.y * .0003f))
                                        change.consume()
                                        break
                                    }
                                }
                            } finally {
                                if (!completed) {
                                    session.allowLight = false
                                    touch.release(Offset.Zero)
                                }
                                press.release()
                                session.finish(!completed, current.value, commitOnRelease) { submit.value(it) }
                                motion.release()
                                indicatorPress.release()
                                labelPresses.forEach { it.release() }
                            }
                        }
                    }) {
                    labels.forEachIndexed { index, label ->
                        Box(Modifier.width(with(density) { geometry.widths[index].toDp() }).fillMaxHeight()
                            .graphicsLayer { scaleX = labelPresses[index].scale.value; scaleY = labelPresses[index].scale.value }
                            .semantics {
                                role = Role.Tab; selected = selectedIndex == index
                                if (!enabled) disabled()
                                if (index != selectedIndex) onClick { if (enabled && index != current.value) submit.value(index); enabled }
                            }.focusable(enabled).onKeyEvent {
                                if (enabled && it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar || it.key == Key.DirectionCenter)) {
                                    if (index != current.value) submit.value(index)
                                    true
                                } else false
                            }.padding(horizontal = CouiSegmentedControlDefaults.labelPadding), contentAlignment = Alignment.Center) {
                            BasicText(label, style = textStyle.copy(textAlign = TextAlign.Center,
                                color = Color(if (dark) 0xE6FFFFFF else 0xE6000000).let { it.copy(alpha = it.alpha * if (enabled) 1f else .38f) }),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** 原厂指示器的专属径向投影；轮廓仍复用公共 Drawable 后端。 */
private fun DrawScope.drawSegmentIndicator(rect: Rect, containerHeight: Float, shape: CouiShape,
    contour: CouiContour?, paint: Paint, dark: Boolean) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val path = Path().apply { addOutline(shape.createOutline(rect.size, layoutDirection, this@drawSegmentIndicator)) }
    translate(rect.left, rect.top) {
        clipPath(path, ClipOp.Difference) {
            val shadowSize = Size(rect.width, (containerHeight - rect.top - 2.dp.toPx()).coerceAtLeast(0f))
            if (dark) drawRoundRect(Color(0x14000000), Offset(0f, 2.dp.toPx()), shadowSize, CornerRadius(rect.height / 2f))
            else drawRoundRect(Brush.radialGradient(listOf(Color(0x0D000000), Color(0x03000000)),
                Offset(rect.width / 2f, rect.height / 2f), (rect.width / 2f).coerceAtLeast(1f)),
                Offset(0f, 2.dp.toPx()), shadowSize, CornerRadius(rect.height / 2f))
        }
        clipPath(path) {
            drawRect(Color(if (dark) 0x33FFFFFF else 0xB2FFFFFF), size = rect.size)
            contour?.let {
                paint.shader = it.shader(rect.size, density, rect.height / 2f, 2f, Color.Transparent,
                    0f, if (dark) .15f else .8f, 1f, 1.8f * density)
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRect(0f, 0f, rect.width, rect.height, paint) }
            }
        }
    }
}
