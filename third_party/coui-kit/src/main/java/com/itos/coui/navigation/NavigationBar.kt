package com.itos.coui.navigation

import android.graphics.BlendMode
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import com.itos.coui.theme.CouiIcon as Icon
import com.itos.coui.theme.CouiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurfaces
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.CouiPressMotion
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.shape.CouiCapsuleShape

/** [selectedIcon] 用于选中和拖动预选，省略时两种状态共用 [icon]。 */
data class CouiNavigationItem(val title: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * 只负责底栏装配，页面状态和导航提交仍由宿主持有。
 * 附件点击回调为空时只显示图标并保留拖拽，不创建点击或 Button 语义。
 */
@RequiresApi(34)
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "底栏需要显式 backdrop 或 COUI Scaffold 的独立栏位"
    },
    renderer: CouiFloatingRenderer,
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    leftIcon: ImageVector? = null,
    leftDescription: String? = null,
    rightIcon: ImageVector? = null,
    rightDescription: String? = null,
    leftVisible: Boolean = false,
    rightVisible: Boolean = false,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    rightContainerColor: Color = Color.Unspecified,
    dark: Boolean = CouiTheme.isDark,
    lightColor: Color = Color.White,
    themeTint: Color = CouiTheme.materialTintFor(dark),
    circleShape: Shape = CouiTopAppBarButtonDefaults.circleShape,
    capsuleShape: Shape = CouiCapsuleShape,
    selectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 230f / 255f),
    unselectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 184f / 255f),
    onSelection: (Int) -> Unit,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    require(!leftVisible || (leftIcon != null && leftDescription != null)) {
        "显示左附件时必须提供图标和描述"
    }
    require(!rightVisible || (rightIcon != null && rightDescription != null)) {
        "显示右附件时必须提供图标和描述"
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftExpansion = remember(density, layoutDirection) { CouiFabExpansion(leftVisible) }
    val rightExpansion = remember(density, layoutDirection) { CouiFabExpansion(rightVisible) }
    // 融合完成会先隐藏附件, 点击不能继续等待缩放弹簧结束。
    val leftInteractive = leftExpansion.present && leftExpansion.interactive && leftIcon != null
    val rightInteractive = rightExpansion.present && rightExpansion.interactive && rightIcon != null
    DisposableEffect(leftExpansion, rightExpansion) {
        onDispose {
            leftExpansion.dispose()
            rightExpansion.dispose()
        }
    }
    val contour = LocalCouiContourGlowEnabled.current
    val material = remember(dark, lightColor, themeTint) {
        CouiFloatingMaterial(
            firstColor = if (dark) Color(0x80666666) else Color(0xA3666666),
            firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
            secondColor = if (dark) Color(0xB3262626) else Color(0x80F0F0F0),
            edgeAlpha = if (dark) 0.8f else 0.25f,
            innerGlowAlpha = if (dark) 0.03f else 0.1f,
            lightColor = lightColor,
            themeTint = themeTint,
        )
    }
    val foreground = if (dark) Color.White else Color.Black
    val coloredFab = if (rightContainerColor.isSpecified) rightContainerColor else CouiTheme.colorSchemeFor(dark).accent
    val materials = remember(material, coloredFab, dark, density) {
        val extension = with(density) { 4.dp.roundToPx() }
        listOf(material.copy(index = 2, extension = extension), material.copy(index = 1, extension = extension),
            CouiFabDefaults.material(coloredFab, dark, extension))
    }
    val shadows = remember(coloredFab, density, lightColor, dark, contour) {
        fun pixelOffset(dp: Float): Float = (dp * density.density).toInt().toFloat()
        fun pixelSize(dp: Float): Float {
            val px = dp * density.density
            return (if (px >= 0f) px + 0.5f else px - 0.5f).toInt().toFloat()
        }
        val projectionColor = when {
            contour -> Color(0x2D000000)
            dark -> Color(0x78000000)
            else -> Color(0x28000000)
        }
        val accessoryShadow = CouiFloatingShadow(
            elevation = pixelSize(if (contour) 24f else 350f), color = projectionColor,
            lightY = pixelSize(if (contour) -320f else -160f),
            lightZ = if (contour) 0f else pixelSize(10000f),
            lightRadius = pixelSize(if (contour) 1600f else 2000f),
            causticColor = lightColor.copy(alpha = 0x42 / 255f),
            layout = Rect(0f, 0f, 0f, pixelOffset(11.2f)), sdf = pixelOffset(20f))
        listOf(
            accessoryShadow.copy(color = if (contour) Color(0x23000000) else projectionColor),
            accessoryShadow.copy(layout = Rect(0f, 0f, 0f, pixelOffset(4.9f)), sdf = pixelOffset(10.4f), smoothCorner = true),
            CouiFabDefaults.shadow(coloredFab, density.density, contour),
        )
    }
    val shapes = remember(circleShape, capsuleShape) { listOf(circleShape, capsuleShape, circleShape) }
    val contentLayers = List(3) { rememberGraphicsLayer() }
    val menuMotion = rememberCouiTouchMotion(true)
    val leftMotion = rememberCouiTouchMotion(leftExpansion.touchMotionEnabled)
    val rightMotion = rememberCouiTouchMotion(rightExpansion.touchMotionEnabled)
    val menuPress = rememberCouiPressMotion()
    val leftPress = rememberCouiPressMotion()
    val rightPress = rememberCouiPressMotion()
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuBounds by remember { mutableStateOf(Rect.Zero) }
    var leftBounds by remember { mutableStateOf(Rect.Zero) }
    var rightBounds by remember { mutableStateOf(Rect.Zero) }
    fun menuSurface(): CouiFloatingSurface = surface(menuBounds, menuMotion, menuPress)
    fun leftSurface(): CouiFloatingSurface = leftExpansion.surface(
        surface(leftBounds, leftMotion, leftPress, cornerWeight = 2f,
            touchMotionEnabled = leftExpansion.touchMotionEnabled),
    )
    fun rightSurface(): CouiFloatingSurface = rightExpansion.surface(
        surface(rightBounds, rightMotion, rightPress, cornerWeight = 2f,
            touchMotionEnabled = rightExpansion.touchMotionEnabled),
    )
    LaunchedEffect(leftExpansion, leftVisible, menuBounds, leftBounds) {
        leftExpansion.updateTarget(leftVisible, menuBounds, leftBounds,
            collapseToLeft = layoutDirection == LayoutDirection.Ltr)
    }
    LaunchedEffect(rightExpansion, rightVisible, menuBounds, rightBounds) {
        rightExpansion.updateTarget(rightVisible, menuBounds, rightBounds,
            collapseToLeft = layoutDirection == LayoutDirection.Rtl)
    }
    LaunchedEffect(leftExpansion, menuMotion, leftMotion, menuPress, leftPress) {
        snapshotFlow { couiFabOverlapRatio(leftSurface().couiDrawingBounds(), menuSurface().couiDrawingBounds()) }
            .collect { leftExpansion.finishCollapseIfOverlapped(it) }
    }
    LaunchedEffect(rightExpansion, menuMotion, rightMotion, menuPress, rightPress) {
        snapshotFlow { couiFabOverlapRatio(rightSurface().couiDrawingBounds(), menuSurface().couiDrawingBounds()) }
            .collect { rightExpansion.finishCollapseIfOverlapped(it) }
    }
    var previewSelection by remember { mutableIntStateOf(selectedIndex) }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onSelection)
    LaunchedEffect(selectedIndex) { previewSelection = selectedIndex }
    val maximumMenuWidth = 24.dp + 70.dp * items.size + 8.dp * (items.size - 1)
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else 8.dp
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = maximumMenuWidth + 136.dp).fillMaxWidth()) {
            val menuWidth = (maxWidth - 136.dp).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .onGloballyPositioned { rowCoordinates = it }
                    .couiFloatingSurfaces(backdrop, renderer, materials, shadows, shapes, contentLayers, contour) {
                        val left = leftSurface()
                        val menu = menuSurface()
                        val right = rightSurface()
                        listOf(
                            left.copy(alpha = left.alpha * couiFabOverlapAlpha(
                                couiFabOverlapRatio(left.couiDrawingBounds(), menu.couiDrawingBounds()),
                            )),
                            menu,
                            right.copy(alpha = right.alpha * couiFabOverlapAlpha(
                                couiFabOverlapRatio(right.couiDrawingBounds(), menu.couiDrawingBounds()),
                            )),
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingNode(
                    width = 56.dp, motion = leftMotion, press = leftPress, dark = dark,
                    shape = circleShape,
                    lightColor = lightColor,
                    contentLayer = contentLayers[0],
                    interactive = leftInteractive,
                    expansion = leftExpansion,
                    onBounds = { node -> rowCoordinates?.let { leftBounds = it.localBoundingBoxOf(node, false) } },
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    if (leftIcon != null) Icon(imageVector = leftIcon, contentDescription = if (leftInteractive) leftDescription else null, modifier = Modifier.fillMaxSize()
                        .then(if (leftInteractive && onLeftClick != null) Modifier.clickable(
                            interactionSource = interactionSource, indication = null, role = Role.Button,
                            onClick = onLeftClick,
                        ) else Modifier).padding(16.dp), tint = foreground)
                }
                FloatingNode(
                    width = menuWidth, motion = menuMotion, press = menuPress, dark = dark,
                    shape = capsuleShape,
                    lightColor = lightColor,
                    contentLayer = contentLayers[1],
                    onBounds = { node -> rowCoordinates?.let { menuBounds = it.localBoundingBoxOf(node, false) } },
                ) {
                    val itemBounds = remember(items.size) { mutableMapOf<Int, Rect>() }
                    var menuCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    Row(
                        Modifier.fillMaxSize().selectableGroup().onGloballyPositioned { menuCoordinates = it }
                            .pointerInput(items.size) {
                                var active: PointerId? = null
                                var pending: Int? = null
                                var downItem: Int? = null
                                fun hit(position: Offset): Int? = itemBounds.entries.firstOrNull { it.value.contains(position) }?.key
                                fun finish(cancelled: Boolean, position: Offset?) {
                                    val target = couiNavigationReleaseTarget(
                                        previewSelection, pending, downItem, position?.let(::hit), cancelled,
                                    )
                                    if (target != null) {
                                        previewSelection = target
                                        onSelect(target)
                                    }
                                    active = null
                                    pending = null
                                    downItem = null
                                }
                                try {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.isCouiTouchCancelled()) {
                                                finish(true, null)
                                                continue
                                            }
                                            val tracked = event.changes.firstOrNull { it.id == active }
                                            val pointer = tracked?.takeIf { it.pressed }
                                                ?: event.changes.firstOrNull { it.pressed }
                                                ?: tracked ?: continue
                                            if (active == null) {
                                                downItem = hit(pointer.position)
                                            } else if (pointer.pressed && pointer.position != pointer.previousPosition) {
                                                hit(pointer.position)?.let { index ->
                                                    if (previewSelection != index) {
                                                        previewSelection = index
                                                        pending = index
                                                    }
                                                }
                                            }
                                            active = pointer.id
                                            if (!pointer.pressed) finish(false, pointer.position)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } finally {
                                    // 节点移除不等于原厂 ACTION_CANCEL, 不能在离开页面时触发导航。
                                    previewSelection = currentSelection
                                }
                            }.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, item ->
                            val isSelected = previewSelection == index
                            val itemContentColor = if (isSelected) selectedColor else unselectedColor
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .onGloballyPositioned { node -> menuCoordinates?.let { itemBounds[index] = it.localBoundingBoxOf(node, false) } }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Tab
                                        selected = isSelected
                                        onClick {
                                            if (index != currentSelection) onSelect(index)
                                            true
                                        }
                                    }.onKeyEvent {
                                        if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                                            if (index != currentSelection) onSelect(index)
                                            true
                                        } else false
                                    }.focusable(interactionSource = interactionSource),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    Modifier.fillMaxSize().couiNavigationFeedback(dark, interactionSource).padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Icon(modifier = Modifier.size(24.dp), imageVector = if (isSelected) item.selectedIcon else item.icon,
                                        contentDescription = null, tint = itemContentColor)
                                    Spacer(Modifier.height(2.dp))
                                    ProvideCouiContent {
                                        Text(text = item.title, color = itemContentColor, fontSize = 10.sp,
                                            lineHeight = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                FloatingNode(
                    width = 56.dp, motion = rightMotion, press = rightPress, dark = dark,
                    shape = circleShape,
                    contentLayer = contentLayers[2],
                    coloredFab = true,
                    interactive = rightInteractive,
                    expansion = rightExpansion,
                    onBounds = { node -> rowCoordinates?.let { rightBounds = it.localBoundingBoxOf(node, false) } },
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    if (rightIcon != null) Icon(imageVector = rightIcon, contentDescription = if (rightInteractive) rightDescription else null, modifier = Modifier.fillMaxSize()
                        .then(if (rightInteractive && onRightClick != null) Modifier.couiFabFeedback(dark, interactionSource).clickable(
                            interactionSource = interactionSource, indication = null, role = Role.Button,
                            onClick = onRightClick,
                        ) else Modifier).padding(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FloatingNode(
    width: Dp,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    dark: Boolean,
    shape: Shape,
    contentLayer: GraphicsLayer,
    coloredFab: Boolean = false,
    lightColor: Color = Color.White,
    interactive: Boolean = true,
    expansion: CouiFabExpansion? = null,
    onBounds: (LayoutCoordinates) -> Unit,
    content: @Composable () -> Unit,
) {
    val touchMotionEnabled = interactive && (expansion?.touchMotionEnabled ?: true)
    LaunchedEffect(press, touchMotionEnabled) {
        if (!touchMotionEnabled) press.release()
    }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = if (coloredFab) SpotlightPreset.OpaqueSmall1 else SpotlightPreset.OpaqueSmall2,
        dark = dark,
        color = if (coloredFab) Color.White else lightColor,
    )
    Box(Modifier.width(width).height(56.dp).onGloballyPositioned(onBounds)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = motion.translationX.value + (expansion?.offsetX() ?: 0f)
                translationY = motion.translationY.value
                scaleX = (if (touchMotionEnabled) motion.scaleX.value + press.scale.value - 1f else 1f) *
                    (expansion?.snappedScale(size.width) ?: 1f)
                scaleY = (if (touchMotionEnabled) motion.scaleY.value + press.scale.value - 1f else 1f) *
                    (expansion?.snappedScale(size.height) ?: 1f)
            }.then(
                // 禁用 FAB 时移除输入节点; 空的 pointerInput 仍会挡住下方导航项。
                if (interactive) Modifier.couiTouchMotion(motion, touchMotionEnabled,
                    cancelOnConsumedMove = false, consumeDrag = true)
                    .couiPressMotion(press, touchMotionEnabled).then(lightInput)
                else Modifier.clearAndSetSemantics {},
            ).drawWithContent {
                contentLayer.record { this@drawWithContent.drawContent() }
            }.clip(shape)
                .drawBehind { drawLight?.invoke(this) },
        ) { content() }
    }
}

private fun surface(
    bounds: Rect, motion: CouiTouchMotion, press: CouiPressMotion,
    cornerWeight: Float = 1f,
    touchMotionEnabled: Boolean = true,
): CouiFloatingSurface = CouiFloatingSurface(
    bounds.translate(Offset(motion.translationX.value, motion.translationY.value)),
    if (touchMotionEnabled) motion.scaleX.value + press.scale.value - 1f else 1f,
    if (touchMotionEnabled) motion.scaleY.value + press.scale.value - 1f else 1f, cornerWeight,
)
