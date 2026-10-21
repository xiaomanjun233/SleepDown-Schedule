package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeButtonAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

private class NativeFab(val root: ViewGroup, val main: View, val icon: ImageView, val setClick: Method,
    private val source: ColorOsNativeUi.Source, private var accent: Int, private var neutral: Boolean,
    private var foreground: Int?) {
    fun dispose() { setClick.invoke(root, null) }

    fun refreshAppearance() {
        root.javaClass.getMethod("onConfigurationChanged", android.content.res.Configuration::class.java)
            .invoke(root, root.context.resources.configuration)
        updateAccent(accent, force = true)
    }

    fun refreshMaterial() { updateAccent(accent, force = true) }

    fun updateAccent(color: Int, neutral: Boolean = this.neutral, foreground: Int? = this.foreground, force: Boolean = false) {
        if (!force && accent == color && this.neutral == neutral && this.foreground == foreground) return
        accent = color
        this.foreground = foreground
        val restorePreset = this.neutral && !neutral
        this.neutral = neutral
        if (restorePreset) root.javaClass.getMethod("onConfigurationChanged", android.content.res.Configuration::class.java)
            .invoke(root, root.context.resources.configuration)
        icon.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        icon.drawable?.setTint(android.graphics.Color.WHITE)
        root.javaClass.getMethod("setMainFloatingButtonBackgroundColor", ColorStateList::class.java)
            .invoke(root, ColorStateList.valueOf(color))
        applyMaterial()
    }

    fun applyMaterial() {
        if (!neutral || !main.isAttachedToWindow || !source.blurEnabled()) return
        val base = source.load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
        val params = source.accentButtonBlurParams(root.context, accent)
        source.load("com.coui.appcompat.COUIMaterialEffect")
            .getMethod("setViewBlurEffect", View::class.java, base)
            .invoke(null, main, params)
        val seamless = root.javaClass.getDeclaredField("mSeamlessImpl").apply { isAccessible = true }.get(root)
        seamless.javaClass.getMethod("setMaterialBlurParams", params.javaClass).invoke(seamless, params)
    }
}

private fun createNativeFab(
    source: ColorOsNativeUi.Source, context: Context, host: Context, accent: Int,
    neutral: Boolean, foreground: Int?, onClick: (() -> Unit)?
): NativeFab {
    val type = source.load(ColorOsNativeUi.FloatingButton)
    val root = type.getConstructor(Context::class.java).newInstance(context) as ViewGroup
    val main = type.getMethod("getMainView").invoke(root) as View
    val icon = type.getMethod("getMainFloatingButton").invoke(root) as ImageView
    val plus = requireNotNull(ContextCompat.getDrawable(host, R.drawable.ic_add_course)).mutate()
    plus.setTint(android.graphics.Color.WHITE)
    type.getMethod("setMainFabDrawable", android.graphics.drawable.Drawable::class.java).invoke(root, plus)
    // SleepDown's existing menu opens after the native click; the FAB's own action list stays closed.
    type.getMethod("setIsFloatingButtonExpandEnable", Boolean::class.javaPrimitiveType).invoke(root, false)
    val listenerName = "${ColorOsNativeUi.FloatingButton}\$OnFloatingButtonClickListener"
    val setClick = type.getMethod("setFloatingButtonClickListener", source.load(listenerName))
    setClick.invoke(root, source.listener(listenerName) { name, _ ->
        if (name == "onClick") onClick?.invoke()
        null
    })
    icon.contentDescription = "添加与导入"
    icon.isClickable = onClick != null
    icon.isFocusable = onClick != null
    root.clipChildren = false
    root.clipToPadding = false
    return NativeFab(root, main, icon, setClick, source, accent, neutral, foreground).also { fab ->
        fab.updateAccent(accent, force = true)
        source.bindFollowLightIntensity(root, afterMaterialApplied = fab::refreshMaterial)
    }
}

/** The menu's return endpoint uses the same native FAB, with no touch or Kyant feedback. */
@Composable
internal fun ColorOsHomeFloatingButtonVisual(dark: Boolean, modifier: Modifier): Boolean {
    val style = LocalHomeMaterialStyle.current
    val effectiveDark = style?.dark ?: dark
    val native = rememberNativeContext(effectiveDark) ?: return false
    val host = LocalContext.current
    val accent = dockAccent(native.first, native.second, LocalHomeButtonAccent.current)
    val fab = remember(native, host) {
        ColorOsNativeUi.prepare("create the native FAB return endpoint") {
            createNativeFab(native.first, native.second, host, accent, style?.weatherMaterial == true, style?.foreground, null)
        }
    } ?: return false
    key(native) { AndroidView(factory = { fab.root }, modifier = modifier, update = {
        if (native.first.updateTheme(native.second, effectiveDark)) fab.refreshAppearance()
        fab.updateAccent(accent, neutral = style?.weatherMaterial == true, foreground = style?.foreground)
    }) }
    DisposableEffect(fab) { onDispose { fab.dispose() } }
    return true
}

private class NativeParallelDock(
    val frame: FrameLayout, val dock: NativeDock, val fab: NativeFab,
    val show: Method, val hide: Method, val unbind: Method, var expanded: Boolean
) {
    fun expand(wanted: Boolean, animate: Boolean) {
        if (expanded == wanted) return
        (if (wanted) show else hide).invoke(dock.view, animate)
        expanded = wanted
    }

    fun dispose() {
        dock.dispose()
        unbind.invoke(dock.view)
        fab.dispose()
    }
}

/** Native bar + native FAB are siblings; COUI owns their spring motion and metaball chain. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorOsParallelHomeDock(
    selectedIndex: Int, dark: Boolean, buttonHidden: Boolean, modifier: Modifier,
    onSelect: (Int) -> Unit, onOpenMenu: (Rect, GraphicsLayer) -> Unit
): Boolean {
    val style = LocalHomeMaterialStyle.current
    val effectiveDark = style?.dark ?: dark
    val native = rememberNativeContext(effectiveDark, retainOnThemeChange = true) ?: return false
    val source = native.first
    val context = native.second
    val host = LocalContext.current
    val composeRoot = LocalView.current
    val density = LocalDensity.current
    val select = rememberUpdatedState(onSelect)
    val open = rememberUpdatedState(onOpenMenu)
    val canOpen = rememberUpdatedState(selectedIndex < 2 && !buttonHidden)
    val returnLayer = rememberGraphicsLayer()
    val accent = dockAccent(source, context, LocalHomeDockAccent.current)
    val fabColor = dockAccent(source, context, LocalHomeButtonAccent.current)
    val returnRecorded = remember(native, effectiveDark, fabColor, style, density.density) { AtomicBoolean(false) }
    val group = remember(native, host, composeRoot) {
        ColorOsNativeUi.prepare("create the native 3+1 dock") {
            val tabs = listOf(R.drawable.ic_day_view to "今日", R.drawable.ic_week_view to "课程表",
                R.drawable.ic_settings to "设置")
            val dock = createNativeHomeDock(source, context, host, tabs, selectedIndex, accent,
                neutral = style?.weatherMaterial == true, foreground = style?.foreground) { select.value(it) }
            lateinit var fab: NativeFab
            fab = createNativeFab(source, context, host, fabColor, style?.weatherMaterial == true, style?.foreground) {
                // A moving/partially merged source cannot also become the menu's fixed anchor.
                if (canOpen.value && !dock.expansionMoving() && !dock.view.isLayoutRequested) {
                    val location = IntArray(2)
                    val rootLocation = IntArray(2)
                    fab.main.getLocationOnScreen(location)
                    composeRoot.getLocationOnScreen(rootLocation)
                    val left = (location[0] - rootLocation[0]).toFloat()
                    val top = (location[1] - rootLocation[1]).toFloat()
                    open.value(Rect(left, top, left + fab.main.width, top + fab.main.height), returnLayer)
                }
            }
            val frame = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
            frame.addView(dock.view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            frame.addView(fab.root, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
            val type = dock.view.javaClass
            type.getMethod("bindExpansionView", View::class.java).invoke(dock.view, fab.root)
            val show = type.getMethod("showExpansionView", Boolean::class.javaPrimitiveType)
            val hide = type.getMethod("hideExpansionView", Boolean::class.javaPrimitiveType)
            val expanded = selectedIndex < 2
            // Appearance changes can recreate the bar while Settings is already selected.
            (if (expanded) show else hide).invoke(dock.view, false)
            NativeParallelDock(frame, dock, fab, show, hide, type.getMethod("unbindExpansionView"), expanded)
        }
    } ?: return false
    val height = with(density) {
        context.resources.getDimensionPixelSize(source.resource("coui_bottom_floating_navigation_bar_height", "dimen")).toDp()
    }
    val fabSize = with(density) {
        context.resources.getDimensionPixelSize(source.resource("coui_floating_button_size", "dimen")).toDp()
    }
    val systemBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val bottomInset = with(density) { systemBottom.toDp() }
    val compensation = dockImeCompensationPx(WindowInsets.ime.getBottom(density), systemBottom)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = bottomInset + 8.dp)
            .graphicsLayer { translationY = compensation.toFloat() }) {
            // Record only the resting native FAB for the existing menu handoff; do not paint this copy.
            Box(Modifier.size(fabSize).clearAndSetSemantics {}.drawWithContent {
                if (!returnRecorded.get()) {
                    returnLayer.record {
                        withRecordingDensity(density) { this@drawWithContent.drawContent() }
                    }
                    returnRecorded.set(true)
                }
            }) { ColorOsHomeFloatingButtonVisual(dark, Modifier.fillMaxSize()) }
            key(group) {
                AndroidView(factory = { group.frame },
                    modifier = Modifier.fillMaxWidth().height(height).excludeParentPullGesture()
                        .semantics { testTag = "parallel_home_dock" },
                    update = {
                        if (source.updateTheme(context, effectiveDark)) {
                            group.dock.refreshAppearance()
                            group.fab.refreshAppearance()
                        }
                        group.dock.updateAccent(accent, neutral = style?.weatherMaterial == true, foreground = style?.foreground)
                        group.fab.updateAccent(fabColor, neutral = style?.weatherMaterial == true, foreground = style?.foreground)
                        group.dock.select(selectedIndex.coerceIn(0, 2))
                        group.expand(selectedIndex < 2, animate = true)
                        // Preserve COUI's overlap alpha on the main view while hiding the menu source.
                        group.fab.root.alpha = if (buttonHidden) 0f else 1f
                        group.fab.icon.isClickable = !buttonHidden
                        group.fab.icon.isFocusable = !buttonHidden
                    })
            }
        }
    }
    DisposableEffect(group) { onDispose { group.dispose() } }
    return true
}
