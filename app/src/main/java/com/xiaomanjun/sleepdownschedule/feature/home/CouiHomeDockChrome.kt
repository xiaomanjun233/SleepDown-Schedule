package com.xiaomanjun.sleepdownschedule.feature.home

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.navigation.CouiDockCapsule
import com.itos.coui.navigation.CouiDockItem
import com.itos.coui.navigation.CouiDockStyle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIcon
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownFloatingAddButton
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.platform.coloros.ColorOsNativeUi
import com.xiaomanjun.sleepdownschedule.platform.coloros.NativeFab
import com.xiaomanjun.sleepdownschedule.platform.coloros.createNativeFab
import com.xiaomanjun.sleepdownschedule.platform.coloros.findActivityOrNull
import com.xiaomanjun.sleepdownschedule.platform.coloros.rememberColorOsStrokeContext
import com.xiaomanjun.sleepdownschedule.platform.coloros.rememberCouiDockAccent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * 独立的 COUI 导航胶囊。加号仍是现有平台实现（ColorOS 原生 FAB，其他设备回退到
 * 现有 Compose 加号），没有绑定融合链；本步只保留菜单开合与设置页收起联动。
 * 返回 false 表示 Android 14 以下或 renderer 创建失败，由调用方继续既有路由。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CouiHomeDock(
    selectedIndex: Int,
    dark: Boolean,
    dockDark: Boolean,
    dockForeground: Int?,
    dockSampleBrightness: Float,
    buttonHidden: Boolean,
    config: ScheduleConfigEntity,
    backdrop: CouiBackdrop,
    plusBackdrop: Backdrop,
    onSelect: (Int) -> Unit,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
): Boolean {
    if (Build.VERSION.SDK_INT < 34) {
        android.util.Log.i("SleepDownCOUI",
            "COUI dock skipped: API ${Build.VERSION.SDK_INT} < 34; keeping the existing dock")
        return false
    }
    val renderer = remember {
        ColorOsNativeUi.prepare("create the COUI dock renderer") { CouiFloatingRenderer.standalone() }
    } ?: run {
        android.util.Log.w("SleepDownCOUI",
            "COUI dock skipped: renderer creation failed; keeping the existing dock")
        return false
    }
    val style = LocalHomeMaterialStyle.current
    val effectiveDark = style?.dark ?: dockDark
    val density = LocalDensity.current
    val contour = LocalCouiContourGlowEnabled.current
    val accent = LocalHomeDockAccent.current ?: rememberCouiDockAccent(config, dark, effectiveDark)
    val foreground = (style?.foreground ?: dockForeground)?.let(::Color)
        ?: if (effectiveDark) Color.White else Color.Black
    val selectedColor = Color(accent)
    val unselectedColor = foreground.copy(alpha = 184f / 255f)
    val sampleBrightness = style?.sampleBrightness ?: dockSampleBrightness
    val dockStyle = remember(
        effectiveDark, accent, foreground, sampleBrightness, contour, density.density
    ) {
        CouiDockStyle.reference(
            dark = effectiveDark,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            density = density.density,
            sampleBrightness = sampleBrightness,
            contourGlowEnabled = contour,
            followLightIntensity = 2f,
            spotLightColor = selectedColor,
        )
    }
    val items = remember {
        listOf(
            CouiDockItem("今日", requireNotNull(homeActionIcon(R.drawable.ic_day_view))),
            CouiDockItem("课程表", requireNotNull(homeActionIcon(R.drawable.ic_week_view))),
            CouiDockItem("设置", requireNotNull(homeActionIcon(R.drawable.ic_settings))),
        )
    }
    val plusVisible = selectedIndex < 2
    val plusEnabled = plusVisible && !buttonHidden
    val plusSlot by animateDpAsState(
        targetValue = if (plusVisible) 68.dp else 0.dp,
        animationSpec = tween(220),
        label = "CouiDockPlusSlot",
    )
    val plusAlpha by animateFloatAsState(
        targetValue = if (plusEnabled) 1f else 0f,
        animationSpec = tween(180),
        label = "CouiDockPlusAlpha",
    )
    val systemBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val bottomInset = with(density) { systemBottom.toDp() }
    val imeCompensation = dockImeCompensationPx(WindowInsets.ime.getBottom(density), systemBottom)
    val surfaceColor = homeDockImportButtonGlassColor(!effectiveDark)
    val blurRadius = homeChromeBlur(1.3.dp, config)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = (maxWidth - 36.dp).coerceAtLeast(0.dp)
        val dockWidth = minOf(250.dp, (available - 68.dp).coerceAtLeast(120.dp))
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 8.dp)
                .graphicsLayer { translationY = imeCompensation.toFloat() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CouiDockCapsule(
                    modifier = Modifier.width(dockWidth).height(56.dp),
                    backdrop = backdrop,
                    renderer = renderer,
                    items = items,
                    selectedIndex = selectedIndex.coerceIn(0, 2),
                    style = dockStyle,
                    onSelection = onSelect,
                )
                if (plusSlot > 0.dp) {
                    Spacer(Modifier.width((plusSlot - 56.dp).coerceAtLeast(0.dp)))
                    Box(
                        Modifier.width(minOf(plusSlot, 56.dp)).height(56.dp)
                            // Clip only while the slot collapses; the native FAB paints its shadow outside.
                            .then(if (plusSlot < 56.dp) Modifier.clipToBounds() else Modifier)
                            .graphicsLayer { alpha = plusAlpha },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(Modifier.size(56.dp)) {
                            CouiDockPlus(
                                enabled = plusEnabled,
                                effectiveDark = effectiveDark,
                                materialStyle = style,
                                accent = accent,
                                backdrop = plusBackdrop,
                                surfaceColor = surfaceColor,
                                blurRadius = blurRadius,
                                onOpenMenu = onOpenMenu,
                            )
                        }
                    }
                }
            }
        }
    }
    return true
}

@Composable
private fun CouiDockPlus(
    enabled: Boolean,
    effectiveDark: Boolean,
    materialStyle: HomeMaterialStyle?,
    accent: Int,
    backdrop: Backdrop,
    surfaceColor: Color,
    blurRadius: Dp,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
) {
    val native = rememberColorOsStrokeContext(effectiveDark)
    if (native != null) {
        NativeCouiDockPlus(native, enabled, effectiveDark, materialStyle, accent, onOpenMenu)
    } else {
        ComposeCouiDockPlus(enabled, backdrop, surfaceColor, blurRadius, onOpenMenu)
    }
}

/** 原生 FAB 的点击由现有监听器转发，返回快照仍取实际合成像素。 */
@Composable
private fun NativeCouiDockPlus(
    native: Pair<ColorOsNativeUi.Source, Context>,
    enabled: Boolean,
    effectiveDark: Boolean,
    materialStyle: HomeMaterialStyle?,
    accent: Int,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
) {
    val (source, context) = native
    val host = LocalContext.current
    val composeRoot = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val opening = remember { AtomicBoolean(false) }
    val returnLayer = rememberGraphicsLayer()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOpen by rememberUpdatedState(onOpenMenu)
    val fab = remember(source, context, host, materialStyle?.weatherMaterial, materialStyle?.foreground) {
        ColorOsNativeUi.prepare("create the COUI dock plus") {
            var instance: NativeFab? = null
            val created = createNativeFab(
                source = source,
                context = context,
                host = host,
                accent = accent,
                neutral = materialStyle?.weatherMaterial == true,
                foreground = materialStyle?.foreground,
                onClick = {
                    val fab = instance ?: return@createNativeFab
                    if (!latestEnabled || opening.get() || !fab.main.isAttachedToWindow) {
                        return@createNativeFab
                    }
                    if (!opening.compareAndSet(false, true)) return@createNativeFab
                    scope.launch {
                        try {
                            val activity = host.findActivityOrNull() ?: return@launch
                            val position = IntArray(2).also(fab.main::getLocationInWindow)
                            val bitmap = captureGlassWindowRegion(
                                activity.window,
                                Rect(
                                    position[0].toFloat(),
                                    position[1].toFloat(),
                                    (position[0] + fab.main.width).toFloat(),
                                    (position[1] + fab.main.height).toFloat(),
                                )
                            ) ?: return@launch
                            returnLayer.record(density, layoutDirection,
                                IntSize(bitmap.width, bitmap.height)) {
                                val outline = Path().apply {
                                    addOval(Rect(0f, 0f, size.width, size.height))
                                }
                                clipPath(outline) { drawImage(bitmap.asImageBitmap()) }
                            }
                            if (!latestEnabled) return@launch
                            val screen = IntArray(2).also(fab.main::getLocationOnScreen)
                            val root = IntArray(2).also(composeRoot::getLocationOnScreen)
                            val left = (screen[0] - root[0]).toFloat()
                            val top = (screen[1] - root[1]).toFloat()
                            latestOpen(
                                Rect(left, top, left + fab.main.width, top + fab.main.height),
                                returnLayer,
                            )
                        } finally {
                            opening.set(false)
                        }
                    }
                },
            )
            instance = created
            created
        }
    } ?: return
    androidx.compose.runtime.key(fab) {
        AndroidView(factory = { fab.root }, modifier = Modifier.fillMaxSize(), update = {
            if (source.updateTheme(context, effectiveDark)) fab.refreshAppearance()
            fab.updateAccent(accent, neutral = materialStyle?.weatherMaterial == true,
                foreground = materialStyle?.foreground)
            fab.setEnabled(enabled)
            fab.icon.isClickable = enabled
            fab.icon.isFocusable = enabled
        })
    }
    DisposableEffect(fab) { onDispose { fab.dispose() } }
}

@Composable
private fun ComposeCouiDockPlus(
    enabled: Boolean,
    backdrop: Backdrop,
    surfaceColor: Color,
    blurRadius: Dp,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
) {
    val bounds = remember { mutableStateOf(Rect.Zero) }
    val returnLayer = rememberGraphicsLayer()
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { bounds.value = it.boundsInRoot() }
            .drawWithContent {
                returnLayer.record { this@drawWithContent.drawContent() }
                drawLayer(returnLayer)
            },
        contentAlignment = Alignment.Center,
    ) {
        SleepDownFloatingAddButton(
            backdrop = backdrop,
            contentDescription = "添加与导入",
            onClick = {
                if (enabled && bounds.value.width > 1f) onOpenMenu(bounds.value, returnLayer)
            },
            surfaceColor = surfaceColor,
            blurRadius = blurRadius,
            enabled = enabled,
        )
    }
}
