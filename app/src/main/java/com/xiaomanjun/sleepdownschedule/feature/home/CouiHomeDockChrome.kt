package com.xiaomanjun.sleepdownschedule.feature.home

import android.os.Build
import android.os.Trace
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.os.trace
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.navigation.CouiDockBar
import com.itos.coui.navigation.CouiDockDefaults
import com.itos.coui.navigation.CouiDockItem
import com.itos.coui.navigation.CouiDockPlusStyle
import com.itos.coui.navigation.CouiDockStyle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIcon
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.DockImportSource
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import com.xiaomanjun.sleepdownschedule.platform.coloros.findActivityOrNull
import com.xiaomanjun.sleepdownschedule.platform.coloros.rememberCouiDockAccent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * 独立的 COUI 导航胶囊 + FAB。两个主体共享采样、材质、阴影和融球链路；
 * 加号仍复用现有菜单、返回层和位置联动，宿主负责安全区与收放时机。
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
    onSelect: (Int) -> Unit,
    onOpenMenu: (DockImportSource) -> Unit,
): Boolean {
    if (Build.VERSION.SDK_INT < 34) {
        android.util.Log.i("SleepDownCOUI",
            "COUI dock skipped: API ${Build.VERSION.SDK_INT} < 34; keeping the existing dock")
        return false
    }
    val renderer = remember {
        try {
            trace("SleepDown Dock.createRenderer") { CouiFloatingRenderer.pair() }
        } catch (failure: IllegalArgumentException) {
            android.util.Log.w("SleepDownCOUI", "Cannot compile the COUI Dock shader", failure)
            null
        }
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
            lightFollowLightIntensity = 2f,
            // Native menu dark preset is 0.15; its 2× range is 0.3, not the light 2.0.
            darkFollowLightIntensity = 0.3f,
            spotLightColor = selectedColor,
        )
    }
    val plusStyle = remember(effectiveDark, selectedColor, density, contour) {
        CouiDockPlusStyle.reference(
            accent = selectedColor,
            dark = effectiveDark,
            density = density.density,
            contourGlowEnabled = contour,
            icon = requireNotNull(homeActionIcon(R.drawable.ic_add_course)),
            iconDescription = "导入课表",
            // OpaqueSmall1 preset light end is 0.6; native 2× is 1.2.
            lightFollowLightIntensity = 1.2f,
            // Colored FAB keeps its own dark preset (0.5); don't force the 2× dark range.
            darkFollowLightIntensity = 0.5f,
        ).let { base -> base.copy(material = com.itos.coui.floatingactionbutton.CouiFabDefaults.translucentMaterial(
            selectedColor, effectiveDark, base.material.extension)) }
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
    val systemBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val bottomInset = with(density) { systemBottom.toDp() }
    val imeCompensation = dockImeCompensationPx(WindowInsets.ime.getBottom(density), systemBottom)

    val hostContext = LocalContext.current
    val composeRoot = LocalView.current
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val opening = remember { AtomicBoolean(false) }
    val returnLayer = rememberGraphicsLayer()
    val liveLayer = rememberGraphicsLayer()
    var dockOrigin by remember { mutableStateOf(Offset.Zero) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var liveView by remember { mutableStateOf<LiveDockImportSourceView?>(null) }
    fun releaseSource() {
        liveView?.release()
        liveView = null
        sourceBounds = null
    }
    DisposableEffect(Unit) { onDispose { releaseSource() } }
    val latestEnabled by rememberUpdatedState(plusEnabled)
    val latestOpen by rememberUpdatedState(onOpenMenu)
    fun openPlusMenu(bounds: Rect) {
        if (!latestEnabled || sourceBounds != null || !opening.compareAndSet(false, true)) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            var handedOff = false
            try {
                val activity = hostContext.findActivityOrNull() ?: return@launch
                sourceBounds = bounds
                withFrameNanos { }
                withFrameNanos { }
                val rootLocation = IntArray(2).also(composeRoot::getLocationInWindow)
                val cookie = System.identityHashCode(returnLayer)
                Trace.beginAsyncSection("SleepDown Dock.capturePlus", cookie)
                val bitmap = try {
                    captureGlassWindowRegion(activity.window, bounds.translate(rootLocation[0].toFloat(), rootLocation[1].toFloat()))
                } finally {
                    Trace.endAsyncSection("SleepDown Dock.capturePlus", cookie)
                }
                if (bitmap != null) returnLayer.record(density, layoutDirection, IntSize(bitmap.width, bitmap.height)) {
                    val outline = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
                    clipPath(outline) { drawImage(bitmap.asImageBitmap()) }
                }
                if (!latestEnabled) return@launch
                liveView = LiveDockImportSourceView.attach(
                    activity, composeRoot, bounds, liveLayer, bounds.translate(-dockOrigin), density, layoutDirection
                )
                handedOff = true
                latestOpen(DockImportSource(
                    bounds, returnLayer, liveView, ownsView = liveView != null, release = ::releaseSource
                ))
            } finally {
                if (!handedOff) releaseSource()
                opening.set(false)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = (maxWidth - 36.dp).coerceAtLeast(0.dp)
        val dockWidth = minOf(
            CouiDockDefaults.menuWidth,
            (available - CouiDockDefaults.plusSlot).coerceAtLeast(120.dp),
        )
        CouiDockBar(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 8.dp)
                .graphicsLayer { translationY = imeCompensation.toFloat() }
                .onGloballyPositioned { dockOrigin = it.positionInRoot() }
                .drawWithContent {
                    val source = sourceBounds?.translate(-dockOrigin)
                    if (source == null) drawContent() else {
                        liveLayer.record {
                            withRecordingDensity(density) { this@drawWithContent.drawContent() }
                        }
                        if (buttonHidden) {
                            clipRect(source.left, source.top, source.right, source.bottom, ClipOp.Difference) {
                                drawLayer(liveLayer)
                            }
                        } else drawLayer(liveLayer)
                    }
                },
            backdrop = backdrop,
            renderer = renderer,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, 2),
            style = dockStyle,
            plusStyle = plusStyle,
            plusVisible = plusVisible,
            // During the handoff the same commands still feed the attached Android View.
            plusEnabled = plusEnabled || (plusVisible && sourceBounds != null),
            menuWidth = dockWidth,
            onSelection = { trace("SleepDown Dock.select") { onSelect(it) } },
            onPlusClick = ::openPlusMenu,
        )
    }
    return true
}
