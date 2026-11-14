package com.itos.coui.navigation

import android.graphics.BlendMode
import androidx.annotation.RequiresApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.CouiPressMotion
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiIcon as Icon
import com.itos.coui.theme.CouiText as Text

/** 导航胶囊只消费图标与标题；选中态、强调色和触觉策略由 [CouiDockStyle] 提供。 */
data class CouiDockItem(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * 独立导航胶囊的外观参数。附件、窗口安全区、宿主布局和导航提交不在库内决定。
 * 默认值与 [NavigationBar] 的中间菜单主体一致；宿主可按墙纸/品牌覆盖颜色。
 */
data class CouiDockStyle(
    val dark: Boolean,
    val selectedColor: Color,
    val unselectedColor: Color,
    val material: CouiFloatingMaterial,
    val shadow: CouiFloatingShadow,
    val shape: Shape = CouiCapsuleShape,
    val itemHeight: Dp = 48.dp,
    val iconSize: Dp = 24.dp,
    val labelSize: TextUnit = 10.sp,
    val labelLineHeight: TextUnit = 12.sp,
    val labelWeight: FontWeight = FontWeight.Medium,
    val horizontalPadding: Dp = 12.dp,
    val itemSpacing: Dp = 8.dp,
    val iconTopPadding: Dp = 4.dp,
    val iconTitleGap: Dp = 2.dp,
    /** null 表示沿用 COUI 预设；浅色/深色可分别覆盖。 */
    val lightFollowLightIntensity: Float? = null,
    val darkFollowLightIntensity: Float? = null,
    /** 跟手光颜色独立于材质边缘/内光晕，默认与预设一致。 */
    val spotLightColor: Color = Color.White,
) {
    internal fun followLightIntensity(): Float? =
        if (dark) darkFollowLightIntensity else lightFollowLightIntensity

    companion object {
        /**
         * COUI 17 底栏预设的宿主可直接复用的材质/投影；颜色层保持系统预设，
         * sampleBrightness 只用于浅色壁纸下的采样输入压暗。
         */
        @RequiresApi(34)
        fun reference(
            dark: Boolean,
            selectedColor: Color,
            unselectedColor: Color,
            density: Float,
            lightColor: Color = Color.White,
            themeTint: Color = Color.Transparent,
            sampleBrightness: Float = 1f,
            contourGlowEnabled: Boolean = true,
            lightFollowLightIntensity: Float? = null,
            darkFollowLightIntensity: Float? = null,
            spotLightColor: Color = Color.White,
        ): CouiDockStyle {
            val material = CouiFloatingMaterial(
                blurRadius = 25f,
                firstColor = if (dark) Color(0x80666666) else Color(0xB3666666),
                firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
                secondColor = if (dark) Color(0xB3262626) else Color(0x99F5F5F5),
                secondBlendMode = BlendMode.LUMINOSITY,
                edgeAlpha = if (dark) 0.8f else 0.25f,
                innerGlowAlpha = if (dark) 0.03f else 0.1f,
                lightColor = lightColor,
                themeTint = themeTint,
                sampleBrightness = sampleBrightness,
            )
            fun px(dp: Float, density: Float): Float = (dp * density).toInt().toFloat()
            fun size(dp: Float, density: Float): Float {
                val value = dp * density
                return (if (value >= 0f) value + 0.5f else value - 0.5f).toInt().toFloat()
            }
            // 固定参数来自上游底栏中间主体；长度单位为像素，由宿主传入当前密度换算。
            val projectionColor = when {
                contourGlowEnabled -> Color(0x2D000000)
                dark -> Color(0x78000000)
                else -> Color(0x28000000)
            }
            val accessoryShadow = CouiFloatingShadow(
                elevation = size(if (contourGlowEnabled) 24f else 350f, density),
                color = projectionColor,
                lightY = size(if (contourGlowEnabled) -320f else -160f, density),
                lightZ = if (contourGlowEnabled) 0f else size(10000f, density),
                lightRadius = size(if (contourGlowEnabled) 1600f else 2000f, density),
                causticColor = lightColor.copy(alpha = 0x42 / 255f),
                layout = Rect(0f, 0f, 0f, px(11.2f, density)),
                sdf = px(20f, density),
            )
            return CouiDockStyle(
                dark = dark,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                material = material,
                shadow = accessoryShadow.copy(
                    color = if (contourGlowEnabled) Color(0x23000000) else projectionColor,
                    layout = Rect(0f, 0f, 0f, px(4.9f, density)),
                    sdf = px(10.4f, density),
                    smoothCorner = true,
                ),
                lightFollowLightIntensity = lightFollowLightIntensity,
                darkFollowLightIntensity = darkFollowLightIntensity,
                spotLightColor = spotLightColor,
            )
        }
    }
}

/**
 * 页面底栏的独立胶囊主体。它采样宿主的完整页面源，不创建左右附件、融合通道、
 * 窗口 inset 或页面状态；拖动/点击只提交一次选择，程序同步不会回调。
 */
@RequiresApi(34)
@Composable
fun CouiDockCapsule(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    items: List<CouiDockItem>,
    selectedIndex: Int,
    style: CouiDockStyle,
    onSelection: (Int) -> Unit,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    require(renderer.standalone) { "独立导航胶囊必须使用 CouiFloatingRenderer.standalone()" }
    val contour = LocalCouiContourGlowEnabled.current
    val contentLayer: GraphicsLayer = rememberGraphicsLayer()
    val motion = rememberCouiTouchMotion(true)
    val press = rememberCouiPressMotion()
    var boundsSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { boundsSize = it }
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = style.material,
                shadow = style.shadow,
                shape = style.shape,
                contentLayer = contentLayer,
                contourGlowEnabled = contour,
            ) {
                CouiFloatingSurface(
                    bounds = Rect(
                        Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat()),
                    ),
                    scaleX = motion.scaleX.value + press.scale.value - 1f,
                    scaleY = motion.scaleY.value + press.scale.value - 1f,
                )
            },
    ) {
        CouiDockMenuContent(
            modifier = Modifier.fillMaxSize(),
            contentLayer = contentLayer,
            style = style,
            items = items,
            selectedIndex = selectedIndex,
            onSelection = onSelection,
            motion = motion,
            press = press,
        )
    }
}

/** 胶囊和融合底栏共用的菜单内容；外层负责独立或双主体表面。 */
@Composable
internal fun CouiDockMenuContent(
    modifier: Modifier,
    contentLayer: GraphicsLayer,
    style: CouiDockStyle,
    items: List<CouiDockItem>,
    selectedIndex: Int,
    onSelection: (Int) -> Unit,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
) {
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.OpaqueSmall2,
        dark = style.dark,
        intensity = style.followLightIntensity(),
        color = style.spotLightColor,
    )
    var previewSelection by remember { mutableIntStateOf(selectedIndex) }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onSelection)
    LaunchedEffect(selectedIndex) { previewSelection = selectedIndex }
    val itemBounds = remember(items.size) { mutableMapOf<Int, Rect>() }
    var menuCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier.graphicsLayer {
            translationX = motion.translationX.value
            translationY = motion.translationY.value
            scaleX = motion.scaleX.value + press.scale.value - 1f
            scaleY = motion.scaleY.value + press.scale.value - 1f
        }.couiTouchMotion(motion, true, cancelOnConsumedMove = false, consumeDrag = true)
            .couiPressMotion(press).then(lightInput)
            .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
            .clip(style.shape).drawBehind { drawLight?.invoke(this) },
    ) {
        Row(
            Modifier.fillMaxSize().selectableGroup()
                .onGloballyPositioned { menuCoordinates = it }
                .pointerInput(items.size) {
                    var active: PointerId? = null
                    var pending: Int? = null
                    var downItem: Int? = null
                    fun hit(position: Offset): Int? =
                        itemBounds.entries.firstOrNull { it.value.contains(position) }?.key
                    fun finish(cancelled: Boolean, position: Offset?) {
                        val target = couiNavigationReleaseTarget(
                            previewSelection, pending, downItem, position?.let(::hit), cancelled,
                        )
                        if (target != null) {
                            previewSelection = target
                            // 程序同步或拖回当前项都不是新的导航，不能重复提交。
                            if (target != currentSelection) onSelect(target)
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
                        // 节点移除不等于原厂 ACTION_CANCEL, 离开页面时不提交导航。
                        previewSelection = currentSelection
                    }
                }.padding(horizontal = style.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(style.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = previewSelection == index
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.weight(1f).height(style.itemHeight)
                        .onGloballyPositioned { node ->
                            menuCoordinates?.let { itemBounds[index] = it.localBoundingBoxOf(node, false) }
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = isSelected
                            onClick {
                                if (index != currentSelection) onSelect(index)
                                true
                            }
                        }.onKeyEvent {
                            if (it.type == KeyEventType.KeyUp &&
                                (it.key == Key.Enter || it.key == Key.Spacebar)
                            ) {
                                if (index != currentSelection) onSelect(index)
                                true
                            } else false
                        }.focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.fillMaxSize()
                            .couiNavigationFeedback(style.dark, interactionSource)
                            .padding(top = style.iconTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Icon(
                            modifier = Modifier.size(style.iconSize),
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = if (isSelected) style.selectedColor else style.unselectedColor,
                        )
                        Spacer(Modifier.height(style.iconTitleGap))
                        Text(
                            text = item.title,
                            color = if (isSelected) style.selectedColor else style.unselectedColor,
                            fontSize = style.labelSize,
                            lineHeight = style.labelLineHeight,
                            fontWeight = style.labelWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
