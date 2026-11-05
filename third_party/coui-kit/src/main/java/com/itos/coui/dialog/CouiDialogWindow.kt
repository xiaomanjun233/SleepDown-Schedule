package com.itos.coui.dialog

import androidx.compose.ui.graphics.Color
import android.graphics.Paint
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElementIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.itos.coui.R
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.rememberCouiMaterialEffect
import com.itos.coui.menu.LocalCouiMenuBackdrop
import com.itos.coui.menu.rememberCouiKawaseBlur
import com.itos.coui.shape.CouiShape
import com.itos.coui.shape.resolveCouiCornerRadius

/** 输入预设只声明窗口策略，实际写入由窗口 owner 排在 Compose 参数更新之后。 */
internal val LocalCouiDialogSoftInputMode = staticCompositionLocalOf<Int?> { null }

/**
 * 普通警告窗口。宿主持有可见性、回调和不包含弹窗自身的窗口源。
 * 可选材质要求 API 33 与有效 backdrop；缺失时明确报错，不把 Kawase 等级当作高斯半径。
 * 只处理背景的采样、材质与轮廓，文字和按钮不进入背景滤镜。
 * 不提供 Tiny、列表、推荐按钮和拖拽面板。
 */
@Composable
internal fun CouiDialogWindow(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    colors: CouiDialogColors,
    dark: Boolean,
    placement: CouiDialogPlacement = CouiDialogPlacement.Bottom,
    shape: Shape = CouiDialogDefaults.shape,
    cornerRadius: Dp = Dp.Unspecified,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val windowBackdrop = backdrop ?: LocalCouiMenuBackdrop.current
    if (material != null) {
        check(Build.VERSION.SDK_INT >= 33) { "弹窗项目材质要求 Android 13 或更高版本" }
        checkNotNull(windowBackdrop) { "弹窗项目材质需要宿主提供不包含弹窗自身的窗口背景源" }
        windowBackdrop.RetainWhileComposed()
    }
    val context = LocalContext.current
    // 原厂读取 Activity 的导航 inset；在进入 Dialog 窗口前读取，避免重复消费后的零值。
    val density = LocalDensity.current
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)
    val gestureNavigation = WindowInsets.tappableElementIgnoringVisibility.getBottom(density) == 0
    val bottomPadding = with(density) {
        CouiDialogDefaults.bottomPaddingPx(navigationBarHeight, gestureNavigation, density).toDp()
    }
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    val dismiss = rememberUpdatedState(onDismissRequest)
    val softInputMode = LocalCouiDialogSoftInputMode.current
    val windowReference = remember { mutableStateOf<Window?>(null) }
    // 在组合阶段读取，首个 Dialog 子组合发布 Window 后会触发 owner 再次应用策略。
    val ownedWindow = windowReference.value
    fun configureWindow(window: Window) {
        window.setGravity(if (placement == CouiDialogPlacement.Bottom) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.setBackgroundBlurRadius(0)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(colors.dimAmount)
        window.setWindowAnimations(if (placement == CouiDialogPlacement.Bottom)
            R.style.CouiDialogAnimation_Bottom else R.style.CouiDialogAnimation_Center)
        softInputMode?.let(window::setSoftInputMode)
    }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside && !touchExploration,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
        decorFitsSystemWindows = properties.decorFitsSystemWindows,
        windowTitle = properties.windowTitle,
        windowType = properties.windowType,
        windowToken = properties.windowToken,
    )) {
        val window = checkNotNull((LocalView.current.parent as? DialogWindowProvider)?.window) { "未找到对话框所属 Window" }
        SideEffect {
            windowReference.value = window
            configureWindow(window)
        }
        val configuration = LocalConfiguration.current
        val maxHeight = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            CouiDialogDefaults.landscapeMaxHeight else CouiDialogDefaults.maxHeight
        // 原厂 maxHeight 包含基础底部 InsetDrawable 留白；调整导航留白不改变卡片本身的上限。
        val maxCardHeight = if (placement == CouiDialogPlacement.Bottom)
            (maxHeight - CouiDialogDefaults.bottomMargin).coerceAtLeast(0.dp) else maxHeight
        val contourEnabled = LocalCouiContourGlowEnabled.current
        val edge = remember(material != null, contourEnabled) {
            if (material != null && contourEnabled && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Dialog) else null
        }
        val radiusPx = if (edge != null) with(LocalDensity.current) { shape.resolveCouiCornerRadius(cornerRadius).toPx() } else 0f
        val edgeWeight = (shape as? CouiShape)?.cornerWeight ?: CouiDialogDefaults.edgeWeight
        var outerCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
        var cardCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
        // maxWidth 是含 InsetDrawable 左右留白的外框宽度，不是可见卡片宽度。
        Box(Modifier.widthIn(max = CouiDialogDefaults.maxWidth).fillMaxWidth().onGloballyPositioned { outerCoordinates = it }
            .pointerInput(properties.dismissOnClickOutside, touchExploration) {
                if (properties.dismissOnClickOutside && !touchExploration) detectTapGestures { point ->
                    val outer = outerCoordinates
                    val card = cardCoordinates
                    if (outer != null && card != null && outer.isAttached && card.isAttached &&
                        !Rect(0f, 0f, card.size.width.toFloat(), card.size.height.toFloat())
                            .contains(card.localPositionOf(outer, point))) dismiss.value()
                }
            }.padding(horizontal = CouiDialogDefaults.horizontalMargin)
            .padding(bottom = if (placement == CouiDialogPlacement.Bottom) bottomPadding else 0.dp),
            contentAlignment = Alignment.Center) {
                DialogCard(modifier.fillMaxWidth()
                    .heightIn(max = maxCardHeight)
                    .onGloballyPositioned { cardCoordinates = it },
                    material, windowBackdrop, colors, shape, edge, dark, radiusPx, edgeWeight, content)
        }
    }
    // 位于 Dialog 调用之后，保证晚于其 updateParameters 的 SOFT_INPUT_MODE 重置；
    // 不依赖输入内容子组合是否因为 properties.windowTitle 等外部更新而重组。
    SideEffect { ownedWindow?.let(::configureWindow) }
}

@Composable
private fun DialogCard(
    modifier: Modifier,
    material: CouiDialogMaterial?,
    backdrop: CouiBackdrop?,
    colors: CouiDialogColors,
    shape: Shape,
    edge: CouiContour?,
    dark: Boolean,
    radiusPx: Float,
    edgeWeight: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    val blur = if (material != null) rememberCouiKawaseBlur(checkNotNull(backdrop), colors.surface, material.blurRadius) else null
    val materialEffect = if (Build.VERSION.SDK_INT >= 33 && material != null)
        rememberCouiMaterialEffect(material.mix, material.blend, material.blendMode) else null
    val layer = rememberGraphicsLayer()
    val recordingDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val edgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null, neverEqualPolicy()) }
    Box(modifier.clip(shape).onGloballyPositioned { coordinates = it }.drawBehind {
        val target = coordinates
        if (Build.VERSION.SDK_INT >= 33 && blur != null && materialEffect != null &&
            target != null && target.isAttached && size.width > 0f && size.height > 0f) {
            layer.renderEffect = materialEffect.asComposeRenderEffect()
            layer.record(recordingDensity, layoutDirection,
                IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                blur.draw(this, target)
            }
            drawLayer(layer)
        } else drawRect(colors.surface)
        // 原厂 RootLayout.onDraw 先画白光，再由 dispatchDraw 绘制文字与按钮。
        if (Build.VERSION.SDK_INT >= 33) edge?.let { renderer ->
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, edgePaint.apply { shader = renderer.shader(size, density, radiusPx, edgeWeight,
                        Color.Transparent, if (dark) .03f else .4f, if (dark) .3f else .6f, 1f,
                        (if (dark) 2f else 4f) * density) })
            }
        }
    }, content = content)
}
