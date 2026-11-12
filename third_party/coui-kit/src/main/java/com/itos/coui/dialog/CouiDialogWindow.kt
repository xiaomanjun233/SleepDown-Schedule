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
import android.view.animation.AnimationUtils
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElementIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.os.trace
import com.itos.coui.R
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiSamplingCoordinates
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.rememberCouiMaterialEffect
import com.itos.coui.menu.LocalCouiMenuBackdrop
import com.itos.coui.menu.ProvideCouiMenuBackdrop
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
@OptIn(ExperimentalLayoutApi::class)
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
    visible: Boolean? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val hostView = LocalView.current
    val opacity = remember { Animatable(0f) }
    val scale = remember { Animatable(.8f) }
    val translation = remember { Animatable(0f, visibilityThreshold = .5f) }
    var retained by remember { mutableStateOf(false) }
    var entranceRequested by remember { mutableStateOf(visible == true) }
    var motionStarted by remember { mutableStateOf(false) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val latestFinished = rememberUpdatedState(onDismissFinished)
    LaunchedEffect(visible) {
        if (visible == null) return@LaunchedEffect
        if (visible) {
            entranceRequested = true
            val fresh = !retained
            retained = true
            if (fresh || !motionStarted) {
                // Only a fresh window needs the initial layout. Reversals keep their current
                // translation, scale and opacity and can start in the next animation frame.
                snapshotFlow { windowSize }.first { it.height > 0 }
                scale.snapTo(.8f)
                opacity.snapTo(0f)
                translation.snapTo(windowSize.height.toFloat())
                withFrameNanos { }
                motionStarted = true
            }
        } else if (!motionStarted) {
            val wasRetained = retained || entranceRequested
            retained = false
            entranceRequested = false
            if (wasRetained) latestFinished.value?.invoke()
            return@LaunchedEffect
        }
        coroutineScope {
            if (placement == CouiDialogPlacement.Bottom) {
                // 原厂 TranslateAnimation 的进入是 100% self，退出是 100% parent。
                // WindowState.startAnimation 以 DisplayInfo.appHeight 初始化 parentHeight。
                val target = if (visible) 0f else {
                    val appSize = android.graphics.Point()
                    @Suppress("DEPRECATION")
                    checkNotNull(hostView.display).getSize(appSize)
                    appSize.y.toFloat()
                }
                translation.animateTo(target, tween(CouiDialogDefaults.enterDuration,
                    easing = if (visible) CouiDialogDefaults.enterEasing else CouiDialogDefaults.exitEasing))
            } else {
                if (visible) launch {
                    scale.animateTo(1f, tween(CouiDialogDefaults.enterDuration, easing = CouiDialogDefaults.enterEasing))
                }
                opacity.animateTo(if (visible) 1f else 0f, tween(
                    if (visible) CouiDialogDefaults.enterDuration else CouiDialogDefaults.centerExitDuration,
                    easing = if (visible) CouiDialogDefaults.enterEasing else CouiDialogDefaults.exitEasing))
            }
        }
        if (!visible) {
            retained = false
            motionStarted = false
            entranceRequested = false
            latestFinished.value?.invoke()
        }
    }
    if (visible != null && !visible && !retained) return
    // 动画只使图层和背景采样失效，不逐帧重组窗口、重写 Window 参数和业务正文。
    val frameScale: () -> Float = { if (visible == null || placement == CouiDialogPlacement.Bottom) 1f else scale.value }
    val frameTranslation: () -> Float? = { if (visible == null || placement == CouiDialogPlacement.Center) 0f
        else if (motionStarted) translation.value else null }
    val frameAlpha: () -> Float = { if (visible == null || placement == CouiDialogPlacement.Bottom) 1f else opacity.value }
    val windowBackdrop = backdrop ?: LocalCouiMenuBackdrop.current
    if (material != null) {
        check(Build.VERSION.SDK_INT >= 33) { "弹窗项目材质要求 Android 13 或更高版本" }
        checkNotNull(windowBackdrop) { "弹窗项目材质需要宿主提供不包含弹窗自身的窗口背景源" }
    }
    if (material != null) windowBackdrop?.RetainWhileComposed()
    val context = LocalContext.current
    val wideHost = isWideDialogHost()
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
    val ownsImePosition = visible != null && softInputMode != null
    val windowReference = remember { mutableStateOf<Window?>(null) }
    // 在组合阶段读取，首个 Dialog 子组合发布 Window 后会触发 owner 再次应用策略。
    val ownedWindow = windowReference.value
    fun configureWindow(window: Window) {
        val attributes = window.attributes
        val gravity = if (placement == CouiDialogPlacement.Bottom) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
        val animations = if (visible != null) 0 else if (placement == CouiDialogPlacement.Bottom)
            R.style.CouiDialogAnimation_Bottom else R.style.CouiDialogAnimation_Center
        val softInput = softInputMode?.let { mode -> if (ownsImePosition)
            (mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else mode } ?: attributes.softInputMode
        var flags = attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        // 撤销压暗请求才会触发系统淡出；保留标记并把强度设为 0 会跳过动画。
        flags = if (visible == false) flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            else flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        if (attributes.gravity != gravity || attributes.windowAnimations != animations ||
            attributes.softInputMode != softInput || attributes.flags != flags || attributes.dimAmount != colors.dimAmount) {
            attributes.gravity = gravity
            attributes.windowAnimations = animations
            attributes.softInputMode = softInput
            attributes.flags = flags
            attributes.dimAmount = colors.dimAmount
            trace("COUI Dialog.configureWindow") { window.attributes = attributes }
            com.itos.coui.diagnostic.CouiRenderDiagnostics.event("dialog.window.configure")
        }
    }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    ComposeDialog(onDismissRequest = onDismissRequest, properties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside && !touchExploration,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
        decorFitsSystemWindows = properties.decorFitsSystemWindows && !ownsImePosition,
        windowTitle = properties.windowTitle,
        windowType = properties.windowType,
        windowToken = properties.windowToken,
    )) {
        DisposableEffect(Unit) {
            onDispose { windowSize = IntSize.Zero }
        }
        val window = checkNotNull((LocalView.current.parent as? DialogWindowProvider)?.window) { "未找到对话框所属 Window" }
        DisposableEffect(window) {
            trace<Unit>("COUI Dialog.initWindow") {
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(0)
            }
            onDispose { }
        }
        SideEffect {
            windowReference.value = window
            configureWindow(window)
        }
        val configuration = LocalConfiguration.current
        val maxHeight = when {
            wideHost && placement == CouiDialogPlacement.Center -> CouiDialogDefaults.wideCenterMaxHeight
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> CouiDialogDefaults.landscapeMaxHeight
            else -> CouiDialogDefaults.maxHeight
        }
        // 原厂 maxHeight 包含基础底部 InsetDrawable 留白；调整导航留白不改变卡片本身的上限。
        val maxCardHeight = if (placement == CouiDialogPlacement.Bottom)
            (maxHeight - CouiDialogDefaults.bottomMargin).coerceAtLeast(0.dp) else maxHeight
        val contourEnabled = LocalCouiContourGlowEnabled.current
        val edge = remember(material != null, contourEnabled) {
            if (material != null && contourEnabled && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Dialog) else null
        }
        val radiusPx = if (edge != null) with(LocalDensity.current) { shape.resolveCouiCornerRadius(cornerRadius).toPx() } else 0f
        val edgeWeight = (shape as? CouiShape)?.cornerWeight ?: CouiDialogDefaults.edgeWeight
        val imeOffset = if (ownsImePosition) {
            val target = WindowInsets.imeAnimationTarget.getBottom(density).toFloat()
            val offset = remember { Animatable(target, visibilityThreshold = .5f) }
            // WindowState.MoveAnimationSpec 使用系统的 quint 插值器和 mediumAnimTime。
            val easing = remember(context) {
                val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.decelerate_quint)
                Easing { interpolator.getInterpolation(it) }
            }
            val duration = context.resources.getInteger(android.R.integer.config_mediumAnimTime)
            LaunchedEffect(target, visible) {
                // 退场时固定避让位置，避免键盘收起与卡片退出叠加两次下移。
                if (visible != false) offset.animateTo(target, tween(duration, easing = easing))
            }
            offset.value
        } else 0f
        var outerCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
        var cardCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
        // 实时输入弹窗固定窗口，只移动内部卡片；窗口合成层的位置无法用于当前帧采样。
        val viewport = if (ownsImePosition) Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        else Modifier.widthIn(max = CouiDialogDefaults.maxWidth).fillMaxWidth()
        ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { windowSource ->
            BoxWithConstraints(viewport.then(windowSource).onGloballyPositioned {
                outerCoordinates = it
            }
                .pointerInput(properties.dismissOnClickOutside, touchExploration) {
                    if (properties.dismissOnClickOutside && !touchExploration) detectTapGestures { point ->
                        val outer = outerCoordinates
                        val card = cardCoordinates
                        if (outer != null && card != null && outer.isAttached && card.isAttached &&
                            !Rect(0f, 0f, card.size.width.toFloat(), card.size.height.toFloat())
                                .contains(card.localPositionOf(outer, point))) dismiss.value()
                    }
                }, contentAlignment = if (ownsImePosition && placement == CouiDialogPlacement.Bottom)
                    Alignment.BottomCenter else Alignment.Center) {
                // Edge-to-edge 输入窗口没有 decor 的底部避让，不能复用已减导航高度的普通窗口 margin。
                val padding = if (placement != CouiDialogPlacement.Bottom) 0.dp
                    else if (ownsImePosition) with(density) {
                        CouiDialogDefaults.inputBottomPaddingPx(navigationBarHeight, imeOffset, density).toDp()
                    } else bottomPadding
                val availableHeight =
                    (this.maxHeight - with(density) { imeOffset.toDp() } - padding).coerceAtLeast(0.dp)
                val imeTranslation = if (placement == CouiDialogPlacement.Bottom) imeOffset else imeOffset / 2f
                // maxWidth 包含左右留白；入场距离只取卡片外框高度，不取全屏窗口高度。
                Box(Modifier.widthIn(max = CouiDialogDefaults.maxWidth).fillMaxWidth()
                    .onGloballyPositioned {
                        if (windowSize == IntSize.Zero) trace("COUI Dialog.firstLayout") { windowSize = it.size }
                        else windowSize = it.size
                    }
                    .padding(horizontal = CouiDialogDefaults.horizontalMargin).padding(bottom = padding)) {
                    DialogCard(modifier.fillMaxWidth().heightIn(max = minOf(maxCardHeight, availableHeight)),
                        material, windowBackdrop, colors, shape, edge, dark, radiusPx, edgeWeight,
                        frameScale, { frameTranslation()?.minus(imeTranslation) }, frameAlpha,
                        with(density) { bottomPadding.toPx() }, { cardCoordinates = it }, content)
                }
            }
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
    frameScale: () -> Float,
    frameTranslationY: () -> Float?,
    frameAlpha: () -> Float,
    bottomPaddingPx: Float,
    onCardPositioned: (LayoutCoordinates) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val blur = if (material != null) rememberCouiKawaseBlur(checkNotNull(backdrop), colors.surface, material.blurRadius) else null
    val materialEffect = if (Build.VERSION.SDK_INT >= 33 && material != null)
        rememberCouiMaterialEffect(material.mix, material.blend, material.blendMode) else null
    val composeMaterialEffect = remember(materialEffect) { materialEffect?.asComposeRenderEffect() }
    val layer = rememberGraphicsLayer()
    val recordingDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val edgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val samplingCoordinates = remember { CouiSamplingCoordinates() }
    // 外层只记录到位后的锚点；动画仅作用于内层，采样显式应用同一变换的逆矩阵。
    Box(modifier.onGloballyPositioned(samplingCoordinates::update)) {
    Box(Modifier.fillMaxWidth().graphicsLayer {
        val scale = frameScale()
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        translationX = size.width * (1f - scale) / 2f
        translationY = size.height * (1f - scale) / 2f + (frameTranslationY() ?: (size.height + bottomPaddingPx))
        alpha = frameAlpha()
    }.onGloballyPositioned(onCardPositioned).clip(shape).then(Modifier.drawBehind {
        val scale = frameScale()
        val sampleTranslation = Offset(size.width * (1f - scale) / 2f,
            size.height * (1f - scale) / 2f + (frameTranslationY() ?: (size.height + bottomPaddingPx)))
        val target = samplingCoordinates.coordinates
        if (Build.VERSION.SDK_INT >= 33 && blur != null && materialEffect != null &&
            target != null && target.isAttached && size.width > 0f && size.height > 0f) {
            layer.renderEffect = composeMaterialEffect
            layer.record(recordingDensity, layoutDirection,
                IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                // Sample the moving window with its inverse transform, but keep the Kawase
                // passes on one grid instead of rebuilding all nine as the window grows.
                trace("COUI Dialog.recordKawase") {
                    if (!blur.drawScene(this, target, scale, sampleTranslation)) {
                        blur.draw(this, target, scale, sampleTranslation, stableSampleGrid = true)
                    }
                }
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
    }), content = content)
    }
}
