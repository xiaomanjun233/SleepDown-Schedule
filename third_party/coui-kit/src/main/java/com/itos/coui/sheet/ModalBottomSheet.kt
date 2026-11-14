package com.itos.coui.sheet

import android.graphics.drawable.ColorDrawable
import android.graphics.BlendMode
import android.os.Build
import android.view.WindowManager
import android.view.WindowInsetsController
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogColors
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiMaterialBlendMode
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.menu.LocalCouiMenuBackdrop
import com.itos.coui.menu.LocalCouiMenuVisibleBounds
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.shape.CouiShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SheetEase = CubicBezierEasing(.33f, 0f, .67f, 1f)

object CouiSheetDefaults {
    val cornerRadius = 32.dp
    val maxWidth = 540.dp
    internal val largeScreenMaxHeight = 800.dp
    val topSpacing = 40.dp
    val handleHeight = 16.dp
    val bottomPadding = 60.dp
    val toolbarTopSpacing = 11.dp
    val toolbarHeight = 50.dp
    val toolbarHorizontalPadding = 16.dp
    internal val toolbarTitleGap = 4.dp
    internal val toolbarTitleVerticalPadding = 12.dp
    internal val toolbarTitleMinHeight = 26.dp
    fun colors(dark: Boolean): CouiDialogColors = CouiDialogDefaults.colors(dark).copy(
        surface = Color(if (dark) 0xFF383838 else 0xFFFFFFFF))
    /** Clock 17 的 bottomdialog 资源，与警告窗口共用模糊/混色后端。 */
    fun material(dark: Boolean) = CouiDialogMaterial(
        mix = Color(if (dark) 0xA4515151 else 0xDCC8C8C8),
        blend = Color(if (dark) 0xB30F0F0F else 0x376E6E6E),
        blurRadius = 400,
        blendMode = if (dark) CouiMaterialBlendMode.Overlay else CouiMaterialBlendMode.ColorDodge,
    )

    /** Panel 内工具栏走 Drawable 材质，使用 CAPSULE_8 / CIRCLE_4 的窗口内轮廓。 */
    fun toolbarMaterial(dark: Boolean, capsule: Boolean) = CouiFloatingMaterial(
        firstColor = Color(if (dark) 0xFF333333 else 0xFFF0F0F0),
        firstBlendMode = BlendMode.SRC_OVER,
        secondColor = Color.Transparent,
        secondBlendMode = BlendMode.SRC_OVER,
        blurRadius = 0f, strokeType = if (capsule) 1 else 2,
        edgeAlpha = if (dark) .15f else .8f, edgeWidthDp = 1.8f,
        innerGlowAlpha = if (dark) .01f else .1f,
        innerGlowScale = 1f,
    )
}

private fun panelWidth(windowWidth: Dp, windowHeight: Dp): Dp {
    if (windowWidth < 600.dp) return minOf(windowWidth, CouiSheetDefaults.maxWidth)
    // The stock panel spans six columns. Wide landscape windows use the expanded grid.
    val expandedGrid = windowWidth >= 840.dp && windowHeight >= 480.dp &&
        !(windowHeight >= 900.dp && windowHeight > windowWidth && windowWidth < 960.dp)
    val columns = if (expandedGrid) 12 else 8
    val margin = if (expandedGrid) 40.dp else 24.dp
    val gutter = if (expandedGrid) 12.dp else 8.dp
    val columnWidth = (windowWidth - margin * 2 - gutter * (columns - 1)) / columns
    return (columnWidth * 6 + gutter * 5).coerceIn(1.dp, CouiSheetDefaults.maxWidth)
}

private val SheetShape = object : Shape {
    private val rounded = CouiShape(CouiSheetDefaults.cornerRadius, 3f)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val roundedPath = Path().apply {
            addOutline(rounded.createOutline(size, layoutDirection, density))
        }
        val bottom = Path().apply {
            addRect(Rect(0f, size.height / 2f, size.width, size.height))
        }
        // 追加反向轮廓会在 NonZero 填充中挖空下半部；底部直边需要真正的几何并集。
        return Outline.Generic(Path.combine(PathOperation.Union, roundedPath, bottom))
    }
}

/**
 * COUI 底部面板，提供一个展开位置及隐藏位置。visible 由宿主持有，必须持续调用本组件以完成退场。
 * sheetHeight 为包含 handle、内容及底部安全区的目标高度；null 按内容测量，窗口顶部总留白为 40dp。
 * 内容自行选择一个滚动容器；nested scroll 在内容到顶后接管向下拖动。顶部 handle 可直接拖动。
 * title / dismissDescription 由宿主资源提供；确认、草稿、页面导航和二级面板属于宿主。
 * 可选材质复用弹窗后端，要求 API 33 及不含面板/遮罩的窗口源。
 */
@Composable
fun ModalBottomSheet(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    dismissDescription: String,
    dark: Boolean,
    sheetHeight: Dp? = null,
    draggable: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    colors: CouiDialogColors = CouiSheetDefaults.colors(dark),
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    showDragHandle: Boolean = true,
    isolateContent: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    onShowFinished: (() -> Unit)? = null,
    contentDrawsBehindNavigationBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(sheetHeight == null || (sheetHeight.value.isFinite() && sheetHeight > 0.dp)) { "面板高度必须为有限正值" }
    val windowBackdrop = backdrop ?: LocalCouiMenuBackdrop.current
    if (material != null) {
        check(Build.VERSION.SDK_INT >= 33) { "面板材质要求 Android 13 或更高版本" }
        checkNotNull(windowBackdrop) { "面板材质需要不包含面板与遮罩的宿主窗口源" }
    }
    var mounted by remember { mutableStateOf(visible) }
    if (visible && !mounted) SideEffect { mounted = true }
    if (!visible && !mounted) return
    val configuration = LocalConfiguration.current
    val largeScreen = configuration.smallestScreenWidthDp >= 600
    val windowHeight = configuration.screenHeightDp.dp
    val requestDismiss by rememberUpdatedState(onDismissRequest)
    val currentVisible by rememberUpdatedState(visible)
    val motion = remember { CouiSheetMotion() }
    val dismissFinished by rememberUpdatedState(onDismissFinished)
    val showFinished by rememberUpdatedState(onShowFinished)
    LaunchedEffect(visible, motion.entered) {
        if (visible && motion.entered) showFinished?.invoke()
    }
    SideEffect {
        motion.onHidden = {
            if (!currentVisible) {
                mounted = false
                dismissFinished?.invoke()
            }
        }
    }
    DisposableEffect(motion) { onDispose { motion.dispose() } }
    val contour = LocalCouiContourGlowEnabled.current
    val edge = remember(material != null, contour) {
        if (material != null && contour && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Panel) else null
    }
    Dialog(onDismissRequest = { if (visible) requestDismiss() }, properties = DialogProperties(
        dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false, windowTitle = title,
    )) {
        val view = LocalView.current
        SideEffect {
            val window = checkNotNull((view.parent as? DialogWindowProvider)?.window)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setWindowAnimations(0)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT >= 30) {
                val barAppearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (dark) 0 else barAppearance, barAppearance)
            }
        }
        val density = LocalDensity.current
        val surfaceBackdrop = rememberCouiBackdrop(parent = windowBackdrop)
        var panelHeight by remember { mutableFloatStateOf(0f) }
        var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
        var geometryReady by remember { mutableStateOf(false) }
        val menuBounds: () -> IntRect? = remember(density, motion) {
            {
                panelCoordinates?.takeIf { it.isAttached }?.boundsInWindow()?.let { bounds ->
                    val inset = with(density) { 16.dp.toPx() }
                    val translation = motion.offset
                    IntRect((bounds.left + inset).roundToInt(), (bounds.top + translation).coerceAtMost(bounds.bottom).roundToInt(),
                        (bounds.right - inset).roundToInt(), bounds.bottom.roundToInt())
                }
            }
        }
        val navigationInset = WindowInsets.navigationBars.getBottom(density)
        val gestureNavigation = WindowInsets.tappableElement.getBottom(density) == 0
        val peek = with(density) { (if (gestureNavigation) 82.dp else 110.dp).toPx() }
        val bottomPadding = if (WindowInsets.ime.getBottom(density) > 0) 16.dp else
            maxOf(CouiSheetDefaults.bottomPadding, with(density) { navigationInset.toDp() })
        val canDrag = draggable && visible && motion.entered
        val canDragNow by rememberUpdatedState(canDrag)
        LaunchedEffect(canDrag) { if (!canDrag) motion.cancelDrag() }
        val nested = remember(motion) { object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (canDragNow && source == NestedScrollSource.UserInput && available.y < 0f && motion.offset > 0f)
                    Offset(0f, motion.drag(available.y, nested = true)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (canDragNow && source == NestedScrollSource.UserInput && available.y > 0f)
                    Offset(0f, motion.drag(available.y, nested = true)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!canDragNow || !motion.hasNestedDrag || motion.offset <= 0f) return Velocity.Zero
                if (motion.releaseNested(available.y)) requestDismiss()
                return Velocity(0f, available.y)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (canDragNow && motion.releaseNested(available.y)) requestDismiss()
                return Velocity.Zero
            }
        } }
        // 自身表面仍采样外层窗口；新窗口源只供内容里的后续 Popup/Dialog 使用。
        CompositionLocalProvider(LocalCouiMenuBackdrop provides windowBackdrop) {
            ProvideCouiMenuBackdrop(isolateContent = isolateContent) { source ->
                BoxWithConstraints(Modifier.fillMaxSize().then(source).imePadding()) {
                    val viewport = constraints.maxHeight.toFloat()
                    // 原厂 coui_panel_min_padding_top 是距窗口顶边的总留白，不再叠加状态栏高度。
                    val availableHeight = (maxHeight - CouiSheetDefaults.topSpacing).coerceAtLeast(1.dp)
                    val limit = if (largeScreen) minOf(availableHeight, CouiSheetDefaults.largeScreenMaxHeight)
                        else availableHeight
                    // 在组合阶段订阅测量结果，首轮 onSizeChanged 后必须重新发布运动目标。
                    val measuredHeight = panelHeight
                    LaunchedEffect(visible, measuredHeight, limit, density, peek) {
                        if (measuredHeight <= 0f) return@LaunchedEffect
                        // 首次窗口测量后先提交隐藏帧，窗口创建不占用入场弹簧的时间。
                        if (!geometryReady) { withFrameNanos { }; geometryReady = true }
                        motion.update(visible, measuredHeight, with(density) { limit.toPx() }, peek)
                    }
                    val outside = remember { MutableInteractionSource() }
                    val dismissOnScrim = if (visible && motion.entered && dismissOnClickOutside) {
                        Modifier.clickable(outside, null, onClickLabel = dismissDescription) { requestDismiss() }
                            .semantics { contentDescription = dismissDescription }
                    } else Modifier
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.dim }
                        .background(Color.Black.copy(alpha = colors.dimAmount)).then(dismissOnScrim))
                    val heightModifier = if (sheetHeight != null) Modifier.height(sheetHeight.coerceAtMost(limit)) else Modifier.heightIn(max = limit)
                    // 面板底边伸入导航区，内容仅消费一次底部 inset。底角延伸到窗口外，只露出上圆角。
                    Box(modifier.align(Alignment.BottomCenter).widthIn(max = panelWidth(maxWidth, windowHeight)).fillMaxWidth()
                        .then(heightModifier).onSizeChanged {
                            panelHeight = it.height.toFloat()
                        }
                        .onGloballyPositioned { panelCoordinates = it }) {
                    Box(Modifier.fillMaxWidth().graphicsLayer {
                            translationY = if (motion.height == 0f) viewport else motion.offset
                        }
                        .nestedScroll(nested).sheetDrag(motion, canDrag && !showDragHandle) { requestDismiss() }.pointerInput(motion) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var released = false
                                try {
                                    while (true) {
                                        // 系统取消先于子滚动容器的释放处理，避免将 CANCEL 当作向下抬手。
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.isCouiTouchCancelled()) break
                                        if (event.changes.none { it.pressed }) { released = true; break }
                                    }
                                } finally {
                                    if (!released) motion.cancelDrag()
                                }
                            }
                        }.semantics {
                            paneTitle = title
                            if (visible) dismiss { requestDismiss(); true }
                        }) {
                        CouiSheetSurface(Modifier.fillMaxWidth(), material, windowBackdrop, colors,
                            SheetShape, edge, dark,
                            with(density) { CouiSheetDefaults.cornerRadius.toPx() },
                            innerGlowAlpha = if (dark) .1f else .4f, lineAlpha = if (dark) .15f else .8f,
                            extendBottomEdge = true, contourOnTop = true, surfaceBackdrop = surfaceBackdrop,
                            sampleCoordinates = { panelCoordinates }, sampleTranslation = { Offset(0f, motion.offset) }) {
                            CompositionLocalProvider(LocalCouiMenuVisibleBounds provides menuBounds,
                                LocalCouiSheetSurfaceBackdrop provides surfaceBackdrop) {
                                val navigationPadding = if (contentDrawsBehindNavigationBar) Modifier else Modifier
                                    .padding(bottom = bottomPadding)
                                    .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                Column(Modifier.fillMaxWidth().then(if (sheetHeight != null) Modifier.fillMaxHeight() else Modifier)
                                    .consumeWindowInsets(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                                    .then(navigationPadding)) {
                                    if (showDragHandle) {
                                        var handlePressed by remember { mutableStateOf(false) }
                                        val handlePressAlpha by animateFloatAsState(
                                            targetValue = if (handlePressed) 1f else 0f,
                                            animationSpec = tween(200, easing = SheetEase),
                                            label = "拖手柄按压",
                                        )
                                        Canvas(Modifier.fillMaxWidth().height(CouiSheetDefaults.handleHeight)
                                            .pointerInput(canDrag) {
                                                if (!canDrag) return@pointerInput
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                    handlePressed = true
                                                    try {
                                                        while (true) {
                                                            val event = awaitPointerEvent(PointerEventPass.Final)
                                                            if (event.isCouiTouchCancelled() ||
                                                                event.changes.none { it.pressed }) break
                                                        }
                                                    } finally {
                                                        handlePressed = false
                                                    }
                                                }
                                            }
                                            .sheetDrag(motion, canDrag) { requestDismiss() }) {
                                            val press = handlePressAlpha
                                            if (press > 0f) {
                                                val shadowWidth = 70.dp.toPx()
                                                val shadowHeight = 20.dp.toPx()
                                                val color = Color(if (dark) 0x33FFFFFF else 0x1F000000)
                                                drawRoundRect(
                                                    color = color.copy(alpha = color.alpha * press),
                                                    topLeft = Offset((size.width - shadowWidth) / 2f,
                                                        (size.height - shadowHeight) / 2f),
                                                    size = Size(shadowWidth, shadowHeight),
                                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                                )
                                            }
                                            val width = 36.dp.toPx(); val height = 4.dp.toPx()
                                            drawRoundRect(Color(if (dark) 0x40FFFFFF else 0x29000000),
                                                Offset((size.width - width) / 2f, 12.dp.toPx()), Size(width, height), CornerRadius(height / 2f))
                                        }
                                    }
                                    content()
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun Modifier.sheetDrag(motion: CouiSheetMotion, enabled: Boolean, onDismiss: () -> Unit): Modifier =
    pointerInput(motion, enabled) {
        if (!enabled) return@pointerInput
        val tracker = VelocityTracker()
        var cancelled = false
        fun track(change: PointerInputChange) {
            // ViewDragHelper 在固定父坐标中取速度，包含阈值前移动及批量历史样本。
            val translation = Offset(0f, motion.offset)
            for (sample in change.historical) tracker.addPosition(sample.uptimeMillis, sample.position + translation)
            tracker.addPosition(change.uptimeMillis, change.position + translation)
        }
        try {
            coroutineScope {
                // Initial 先于拖动的 Main 阶段，UP 也在释放判定前完成采样；观察器不消费事件。
                launch(start = CoroutineStart.UNDISPATCHED) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tracker.resetTracking()
                        cancelled = false
                        track(down)
                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.isCouiTouchCancelled()) { cancelled = true; break }
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null) { cancelled = true; break }
                            track(change)
                            if (!change.pressed) {
                                val next = event.changes.firstOrNull { it.pressed } ?: break
                                // 与拖动检测器采用相同的主指接续，不能混入另一根手指的速度。
                                pointerId = next.id
                                tracker.resetTracking()
                                track(next)
                            }
                        }
                    }
                }
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount -> change.consume(); motion.drag(amount) },
                    onDragEnd = {
                        if (cancelled) motion.cancelDrag()
                        else if (motion.release(tracker.calculateVelocity())) onDismiss()
                    },
                    onDragCancel = motion::cancelDrag,
                )
            }
        } finally {
            motion.cancelDrag()
        }
    }
