package com.xiaomanjun.sleepdownschedule.feature.home

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionSourceView
import com.xiaomanjun.sleepdownschedule.transition.captureTransitionPress
import com.xiaomanjun.sleepdownschedule.core.ui.material.rememberGlowDockAccent

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
    val accent = LocalHomeDockAccent.current ?: rememberGlowDockAccent(config, dark, effectiveDark)
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

    val activity = LocalContext.current.findActivity()
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val liveLayer = rememberGraphicsLayer()
    val plusView = remember(activity, liveLayer) {
        activity?.let { LiveTransitionSourceView(it, liveLayer, density, layoutDirection) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = (maxWidth - 36.dp).coerceAtLeast(0.dp)
        val dockWidth = minOf(
            CouiDockDefaults.menuWidth,
            (available - CouiDockDefaults.plusSlot).coerceAtLeast(120.dp),
        )
        Box(Modifier.align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + 8.dp)
            .graphicsLayer { translationY = imeCompensation.toFloat() }) {
            if (plusVisible && plusView != null) {
                AndroidView(
                    factory = { plusView },
                    modifier = Modifier.align(Alignment.CenterEnd).size(CouiDockDefaults.plusSize),
                    update = { it.updatePresentation(density, layoutDirection, render = !buttonHidden) },
                )
            }
            CouiDockBar(
                modifier = Modifier.drawWithContent {
                    val button = plusView?.takeIf { plusVisible && it.isAttachedToWindow && it.width > 0 }
                    if (button == null && !buttonHidden) {
                        drawContent()
                    } else {
                        val width = button?.width?.toFloat() ?: CouiDockDefaults.plusSize.toPx()
                        val left = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
                        if (button != null) {
                            // Record the pair once. The original FAB View presents its part of
                            // that same layer while Compose presents the remaining pixels.
                            liveLayer.record {
                                withRecordingDensity(density) { this@drawWithContent.drawContent() }
                            }
                            button.updateCrop(left)
                            clipRect(left, 0f, left + width, size.height, ClipOp.Difference) {
                                drawLayer(liveLayer)
                            }
                        } else {
                            clipRect(left, 0f, left + width, size.height, ClipOp.Difference) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                },
                backdrop = backdrop,
                renderer = renderer,
                items = items,
                selectedIndex = selectedIndex.coerceIn(0, 2),
                style = dockStyle,
                plusStyle = plusStyle,
                plusVisible = plusVisible,
                plusEnabled = plusEnabled,
                menuWidth = dockWidth,
                showPlusSurface = { plusView?.showsOverflow != false },
                onSelection = { trace("SleepDown Dock.select") { onSelect(it) } },
                plusInputModifier = if (plusEnabled) Modifier.captureTransitionPress(plusView, activity?.window)
                    else Modifier,
                onPlusClick = { bounds ->
                    if (plusEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenMenu(DockImportSource(bounds, view = plusView))
                    }
                },
            )
        }
    }
    return true
}
