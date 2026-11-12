package com.itos.coui.navigation

import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainWidth
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingPair
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.motion.CouiPressMotion
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.theme.CouiIcon as Icon
import kotlin.math.PI
import kotlin.math.roundToInt

/** 与 Demo/CouiBottomBar 相同的附件弹簧：response 0.55s、damping 0.65。 */
private const val CouiDockFabExpansionResponse = 0.55f
private const val CouiDockFabExpansionDamping = 1f - 0.35f
private val CouiDockFabExpansionStiffness =
    ((2.0 * PI / CouiDockFabExpansionResponse) * (2.0 * PI / CouiDockFabExpansionResponse)).toFloat()

object CouiDockDefaults {
    val menuWidth = 232.dp
    val plusSize = 56.dp
    val plusGap = 12.dp
    val plusSlot = plusSize + plusGap
    val barHeight = 56.dp

    fun <T> attachmentSpring() = spring<T>(
        dampingRatio = CouiDockFabExpansionDamping,
        stiffness = CouiDockFabExpansionStiffness,
    )
}

/** 右附件 FAB 的颜色、图标和跟手光；材质/投影来自 COUI FabDefaults。 */
data class CouiDockPlusStyle(
    val dark: Boolean,
    val material: CouiFloatingMaterial,
    val shadow: CouiFloatingShadow,
    val icon: ImageVector,
    val iconDescription: String,
    val shape: Shape = CouiTopAppBarButtonDefaults.circleShape,
    val iconTint: Color = Color.White,
    val spotLightColor: Color = Color.White,
    val lightFollowLightIntensity: Float? = null,
    val darkFollowLightIntensity: Float? = null,
) {
    internal fun followLightIntensity(): Float? =
        if (dark) darkFollowLightIntensity else lightFollowLightIntensity

    companion object {
        @RequiresApi(34)
        fun reference(
            accent: Color,
            dark: Boolean,
            density: Float,
            contourGlowEnabled: Boolean,
            icon: ImageVector,
            iconDescription: String,
            shape: Shape = CouiTopAppBarButtonDefaults.circleShape,
            lightFollowLightIntensity: Float? = null,
            darkFollowLightIntensity: Float? = null,
            spotLightColor: Color = Color.White,
        ): CouiDockPlusStyle {
            val extension = (4f * density).roundToInt()
            return CouiDockPlusStyle(
                dark = dark,
                material = CouiFabDefaults.material(accent, dark, extension),
                shadow = CouiFabDefaults.shadow(accent, density, contourGlowEnabled),
                icon = icon,
                iconDescription = iconDescription,
                shape = shape,
                spotLightColor = spotLightColor,
                lightFollowLightIntensity = lightFollowLightIntensity,
                darkFollowLightIntensity = darkFollowLightIntensity,
            )
        }
    }
}

/**
 * 导航胶囊 + 右附件 FAB 的双主体入口。两个表面共享采样、材质和融球链路；
 * 加号位置、返回锚点和菜单仍由宿主决定。要求 API 34 与 pair renderer。
 */
@RequiresApi(34)
@Composable
fun CouiDockBar(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    items: List<CouiDockItem>,
    selectedIndex: Int,
    style: CouiDockStyle,
    plusStyle: CouiDockPlusStyle,
    plusVisible: Boolean,
    plusEnabled: Boolean,
    menuWidth: Dp = CouiDockDefaults.menuWidth,
    showPlusSurface: () -> Boolean = { true },
    onSelection: (Int) -> Unit,
    plusInputModifier: Modifier = Modifier,
    onPlusClick: (Rect) -> Unit,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    require(renderer.surfaceCount == 2) { "融合底栏必须使用 CouiFloatingRenderer.pair()" }
    val density = LocalDensity.current
    val contour = LocalCouiContourGlowEnabled.current
    val menuContentLayer = rememberGraphicsLayer()
    val plusContentLayer = rememberGraphicsLayer()
    val contentLayers = remember(menuContentLayer, plusContentLayer) {
        listOf(menuContentLayer, plusContentLayer)
    }
    val menuMotion = rememberCouiTouchMotion(true)
    val menuPress = rememberCouiPressMotion()
    val plusMotion = rememberCouiTouchMotion(plusEnabled)
    val plusPress = rememberCouiPressMotion()
    var plusBounds by remember { mutableStateOf(Rect.Zero) }
    val expansionSpec = CouiDockDefaults.attachmentSpring<Dp>()
    val targetPlusSlot = if (plusVisible) CouiDockDefaults.plusSlot else 0.dp
    val rawPlusSlot = animateDpAsState(
        targetValue = targetPlusSlot,
        animationSpec = expansionSpec,
        label = "CouiDockPlusSlot",
    )
    // The COUI expansion spring overshoots; the dock width must not cross the menu width.
    val targetPlusAlpha = if (plusEnabled) 1f else 0f
    val plusAlpha = animateFloatAsState(
        targetValue = targetPlusAlpha,
        animationSpec = CouiDockDefaults.attachmentSpring(),
        label = "CouiDockPlusAlpha",
    )
    val plusScale = animateFloatAsState(
        targetValue = if (plusVisible) 1f else 0.5f,
        animationSpec = CouiDockDefaults.attachmentSpring(),
        label = "CouiDockPlusScale",
    )
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val plusSizePx = with(density) { CouiDockDefaults.plusSize.toPx() }
    val heightPx = with(density) { CouiDockDefaults.barHeight.toPx() }
    fun plusSlot() = rawPlusSlot.value.coerceAtLeast(0.dp)
    fun plusX() = menuWidthPx + with(density) { plusSlot().toPx() } - plusSizePx
    fun alpha() = if (showPlusSurface()) plusAlpha.value.coerceIn(0f, 1f) else 0f
    val extension = with(density) { 4.dp.roundToPx() }
    val materials = remember(style.material, plusStyle.material, extension) {
        listOf(
            style.material.copy(index = 1, extension = extension),
            // The FAB is the dock's right attachment; keep its sampled blur equal to the capsule.
            plusStyle.material.copy(
                index = 2,
                extension = extension,
                blurRadius = style.material.blurRadius,
            ),
        )
    }
    val shadows = remember(style.shadow, plusStyle.shadow) { listOf(style.shadow, plusStyle.shadow) }
    val shapes = remember(style.shape, plusStyle.shape) { listOf(style.shape, plusStyle.shape) }

    fun menuSurface() = CouiFloatingSurface(
        bounds = Rect(
            Offset(menuMotion.translationX.value, menuMotion.translationY.value),
            Size(menuWidthPx, heightPx),
        ),
        scaleX = menuMotion.scaleX.value + menuPress.scale.value - 1f,
        scaleY = menuMotion.scaleY.value + menuPress.scale.value - 1f,
    )

    fun plusSurface() = CouiFloatingSurface(
        bounds = Rect(
            Offset(plusX() + plusMotion.translationX.value, plusMotion.translationY.value),
            Size(plusSizePx, heightPx),
        ),
        scaleX = (plusMotion.scaleX.value + plusPress.scale.value - 1f) * plusScale.value.coerceAtLeast(0f),
        scaleY = (plusMotion.scaleY.value + plusPress.scale.value - 1f) * plusScale.value.coerceAtLeast(0f),
        cornerWeight = 2f,
        alpha = alpha(),
        visible = alpha() > 0.001f,
    )

    Box(
        modifier = modifier.layout { measurable, constraints ->
            val width = constraints.constrainWidth((menuWidth + plusSlot()).roundToPx())
            val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(child.width, child.height) { child.place(0, 0) }
        }.height(CouiDockDefaults.barHeight)
            .couiFloatingPair(
                backdrop = backdrop,
                renderer = renderer,
                materials = materials,
                shadows = shadows,
                shapes = shapes,
                contentLayers = contentLayers,
                contourGlowEnabled = contour,
            ) { listOf(menuSurface(), plusSurface()) },
    ) {
        Box(Modifier.size(menuWidth, CouiDockDefaults.barHeight)) {
            CouiDockMenuContent(
                modifier = Modifier.fillMaxSize(),
                contentLayer = menuContentLayer,
                style = style,
                items = items,
                selectedIndex = selectedIndex,
                onSelection = onSelection,
                motion = menuMotion,
                press = menuPress,
            )
        }
        Box(
            Modifier.offset { IntOffset(plusX().roundToInt(), 0) }.size(CouiDockDefaults.plusSize)
                .graphicsLayer { this.alpha = alpha() }
                .onGloballyPositioned { plusBounds = it.boundsInRoot() }
                .then(plusInputModifier),
        ) {
            CouiDockPlusContent(
                modifier = Modifier.fillMaxSize(),
                contentLayer = plusContentLayer,
                style = plusStyle,
                enabled = plusEnabled && plusVisible,
                scale = { plusScale.value },
                motion = plusMotion,
                press = plusPress,
                onOpenMenu = { onPlusClick(plusBounds) },
            )
        }
    }
}

/** 右附件的内容层；表面、位置、可见性由 [CouiDockBar] 统一管理。 */
@Composable
internal fun CouiDockPlusContent(
    modifier: Modifier,
    contentLayer: GraphicsLayer,
    style: CouiDockPlusStyle,
    enabled: Boolean,
    scale: () -> Float,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    onOpenMenu: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        preset = SpotlightPreset.OpaqueSmall1,
        dark = style.dark,
        intensity = style.followLightIntensity(),
        color = style.spotLightColor,
    )
    Box(
        modifier.graphicsLayer {
            translationX = motion.translationX.value
            translationY = motion.translationY.value
            val revealScale = scale().coerceAtLeast(0f)
            scaleX = (motion.scaleX.value + press.scale.value - 1f) * revealScale
            scaleY = (motion.scaleY.value + press.scale.value - 1f) * revealScale
        }.couiTouchMotion(motion, enabled, cancelOnConsumedMove = false, consumeDrag = true)
            .couiPressMotion(press, enabled).then(lightInput)
            .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
            .clip(style.shape).drawBehind { drawLight?.invoke(this) }
            .then(if (enabled) Modifier.couiFabFeedback(style.dark, interactionSource) else Modifier)
            .then(if (enabled) Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onOpenMenu,
            ) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = style.icon,
            contentDescription = style.iconDescription,
            tint = style.iconTint,
        )
    }
}
