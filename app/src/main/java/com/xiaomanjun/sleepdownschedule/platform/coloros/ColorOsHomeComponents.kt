package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenu
import java.lang.reflect.Method

private const val NavigationItem =
    "com.coui.appcompat.bottomfloatingnavigationbar.COUIBottomFloatingNavigationBarItem"
private const val NavigationMaterial =
    "com.coui.appcompat.bottomfloatingnavigationbar.BaseBottomFloatingMenuView\$MaterialEffectState"

internal class NativeDock(
    val view: ViewGroup,
    val getSelected: Method,
    val setSelected: Method,
    val setListener: Method,
    val listener: Any,
    private val icons: List<Drawable>,
    private val source: ColorOsNativeUi.Source,
    private val context: Context,
    private var accent: Int,
    private var neutral: Boolean,
    private var foreground: Int?
) {
    fun refreshAppearance() {
        source.cancelFollowLights(view)
        // refresh() leaves the expansion view, metaball chain and active springs bound.
        view.javaClass.getMethod("refresh").invoke(view)
        source.refreshFollowLightIntensity(view)
        source.cancelFollowLights(view)
        updateAccent(accent, force = true)
        Log.i("SleepDownCOUI", "Dock appearance refreshed in place: view=${System.identityHashCode(view)}, " +
            "night=${context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK}")
    }

    fun updateAccent(color: Int, neutral: Boolean = this.neutral, foreground: Int? = this.foreground, force: Boolean = false) {
        if (!force && color == accent && neutral == this.neutral && foreground == this.foreground) return
        accent = color
        this.foreground = foreground
        val restorePreset = this.neutral && !neutral
        this.neutral = neutral
        if (restorePreset) view.javaClass.getMethod("refresh").invoke(view)
        val colors = dockIconColors(source, context, color, foreground)
        icons.forEach { it.setTintList(colors) }
        view.javaClass.getMethod("setItemsTitleTextColor", ColorStateList::class.java).invoke(view, colors)
        tintLight()
        applyMaterial()
    }

    fun applyMaterial() {
        if (!neutral || !view.isAttachedToWindow || !source.blurEnabled()) return
        val base = source.load("com.coui.appcompat.bottomfloatingnavigationbar.BaseBottomFloatingMenuView")
        val paramsType = source.load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
        val apply = source.load("com.coui.appcompat.COUIMaterialEffect")
            .getMethod("setViewBlurEffect", View::class.java, paramsType)
        val params = source.dockBlurParams(context, neutral)
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (base.isInstance(child)) apply.invoke(null, child, params)
        }
    }

    fun tintLight() {
        val base = "com.coui.appcompat.bottomfloatingnavigationbar.BaseBottomFloatingMenuView"
        val menuType = source.load(base)
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (menuType.isInstance(child)) source.tintSpotLight(child, base, "mSpotLightEffectDrawable", accent)
        }
    }

    fun select(index: Int) {
        val wanted = index + 1
        if (getSelected.invoke(view) == wanted) return
        source.cancelFollowLights(view)
        // State synchronization is separate from the native user's touch/slide callbacks.
        setListener.invoke(view, null)
        try {
            setSelected.invoke(view, wanted)
        } finally {
            setListener.invoke(view, listener)
        }
    }

    fun expansionMoving(): Boolean = listOf("mExpansionViewAlphaAnim", "mExpansionViewScaleAnim",
        "mExpansionViewTranslationXAnim").any { name ->
        val animation = view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view)
        animation != null && animation.javaClass.getMethod("isRunning").invoke(animation) == true
    }

    fun dispose() { setListener.invoke(view, null) }
}

private fun dockIconColors(source: ColorOsNativeUi.Source, context: Context, accent: Int, foreground: Int?): ColorStateList {
    val variant = foreground ?: source.color(context, "couiColorLabelPrimaryVariant").defaultColor
    val disabled = source.color(context, "couiColorLabelTertiary").defaultColor
    return ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_checked), intArrayOf(android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(disabled, accent, accent, variant, variant))
}

internal fun createNativeHomeDock(
    source: ColorOsNativeUi.Source,
    context: Context,
    host: Context,
    tabs: List<Pair<Int, String>>,
    selectedIndex: Int,
    accent: Int,
    neutral: Boolean = false,
    foreground: Int? = null,
    onSelect: (Int) -> Unit
): NativeDock {
    val type = source.load(ColorOsNativeUi.FloatingNavigation)
    val bar = type.getConstructor(Context::class.java).newInstance(context) as ViewGroup
    val builderType = source.load("$NavigationItem\$Builder")
    // COUI tints its labels, but accepts custom icons without applying a tint to them.
    val iconColors = dockIconColors(source, context, accent, foreground)
    val icons = mutableListOf<Drawable>()
    val items = tabs.mapIndexed { index, (icon, title) ->
        val builder = builderType.getConstructor().newInstance()
        builderType.getMethod("setId", Int::class.javaPrimitiveType).invoke(builder, index + 1)
        builderType.getMethod("setTitle", String::class.java).invoke(builder, title)
        builderType.getMethod("setDescription", CharSequence::class.java).invoke(builder, title)
        val currentIcon = when (icon) {
            R.drawable.ic_day_view -> R.drawable.ic_coloros_dock_today
            R.drawable.ic_week_view -> R.drawable.ic_coloros_dock_week
            // This XML has the same path as the original Compose Icons.Default.Settings.
            else -> icon
        }
        val drawable = requireNotNull(ContextCompat.getDrawable(host, currentIcon)).mutate()
        drawable.setTintList(iconColors)
        icons += drawable
        builderType.getMethod("setIcon", android.graphics.drawable.Drawable::class.java)
            .invoke(builder, drawable)
        builderType.getMethod("build").invoke(builder)
    }
    type.getMethod("addItems", List::class.java).invoke(bar, items)
    type.getMethod("setSelectedItemId", Int::class.javaPrimitiveType)
        .invoke(bar, selectedIndex.coerceIn(tabs.indices) + 1)
    val listenerTypeName = "${ColorOsNativeUi.FloatingNavigation}\$OnItemSelectedListener"
    val setListener = type.getMethod("setOnItemSelectedListener", source.load(listenerTypeName))
    val listener = source.listener(listenerTypeName) { name, arguments ->
        if (name == "onNavigationItemSelected") {
            val item = requireNotNull(arguments[1])
            val id = item.javaClass.getMethod("getId").invoke(item) as Int
            if (id - 1 in tabs.indices) onSelect(id - 1)
        }
        null
    }
    setListener.invoke(bar, listener)
    source.material(bar, NavigationMaterial)
    type.getMethod("setItemsTitleTextColor", ColorStateList::class.java).invoke(bar, iconColors)
    bar.clipChildren = false
    bar.clipToPadding = false
    return NativeDock(bar, type.getMethod("getSelectedItemId"),
        type.getMethod("setSelectedItemId", Int::class.javaPrimitiveType), setListener, listener,
        icons, source, context, accent, neutral, foreground).also { dock ->
        source.bindFollowLightIntensity(bar, afterMaterialApplied = {
            dock.applyMaterial()
            dock.tintLight()
        })
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { view.post { dock.tintLight() } }
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}

@Composable
internal fun colorOsOrdinaryDockWidth(): androidx.compose.ui.unit.Dp {
    val native = rememberNativeContext(dark = false) ?: return 140.dp
    val source = native.first
    val resources = native.second.resources
    fun dimension(name: String) = resources.getDimensionPixelSize(source.resource(name, "dimen"))
    // Two full-size native cells, their gap, the menu inset and the bar's outer safe inset.
    val pixels = 2 * dimension("coui_bottom_floating_navigation_bar_item_max_width") +
        dimension("coui_bottom_floating_navigation_bar_item_space") +
        2 * dimension("coui_bottom_floating_navigation_bar_item_bg_horizontal_padding") +
        2 * dimension("coui_bottom_floating_navigation_bar_padding_horizontal")
    return with(LocalDensity.current) { pixels.toDp() }
}

/** Returns false only when native preparation failed, so the caller can use its existing UI. */
@Composable
internal fun ColorOsHomeDock(
    tabs: List<Pair<Int, String>>,
    selectedIndex: Int,
    dark: Boolean,
    modifier: Modifier,
    onSelect: (Int) -> Unit
): Boolean {
    val style = LocalHomeMaterialStyle.current
    val effectiveDark = style?.dark ?: dark
    val native = rememberNativeContext(effectiveDark, retainOnThemeChange = true) ?: return false
    val host = LocalContext.current
    val callback = rememberUpdatedState(onSelect)
    val source = native.first
    val context = native.second
    val accent = dockAccent(source, context, LocalHomeDockAccent.current)
    val height = with(LocalDensity.current) {
        context.resources.getDimensionPixelSize(source.resource("coui_bottom_floating_navigation_bar_height", "dimen")).toDp()
    }
    val dock = remember(source, context, tabs) {
        ColorOsNativeUi.prepare("create the floating navigation bar") {
            createNativeHomeDock(source, context, host, tabs, selectedIndex, accent,
                neutral = style?.weatherMaterial == true, foreground = style?.foreground) { callback.value(it) }
        }
    } ?: return false
    key(context, tabs) {
        AndroidView(factory = { dock.view }, modifier = modifier.height(height), update = {
            if (source.updateTheme(context, effectiveDark)) dock.refreshAppearance()
            dock.updateAccent(accent, neutral = style?.weatherMaterial == true, foreground = style?.foreground)
            dock.select(selectedIndex.coerceIn(tabs.indices))
        })
    }
    DisposableEffect(dock) {
        onDispose { dock.dispose() }
    }
    return true
}

@Composable
internal fun ColorOsHomeIconButton(
    iconRes: Int,
    contentDescription: String,
    dark: Boolean,
    interactive: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    toolbarNavigation: Boolean = false,
    enabled: Boolean = true
): Boolean {
    val style = if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface.current) null
        else LocalHomeMaterialStyle.current
    val effectiveDark = style?.dark ?: dark
    val backdropBlurred = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalToolbarBackdropBlurred.current
    val native = rememberNativeContext(effectiveDark) ?: return false
    val host = LocalContext.current
    val source = native.first
    val context = native.second
    val callback = rememberUpdatedState(onClick)
    val button = remember(source, context, iconRes, toolbarNavigation) {
        ColorOsNativeUi.prepare("create the home icon button") {
            val type = source.load(ColorOsNativeUi.ToolbarMaterial)
            val drawSpotLight = type.getMethod("drawSpotLight", Canvas::class.java)
            var effect: Any? = null
            val icon = requireNotNull(ContextCompat.getDrawable(host, colorOsHomeIconResource(iconRes))).mutate()
            icon.setTintList(source.color(context, "couiColorLabelPrimary"))
            val iconSize = (20 * context.resources.displayMetrics.density).toInt()
            val sizedIcon = object : DrawableWrapper(icon) {
                override fun getIntrinsicWidth(): Int = iconSize
                override fun getIntrinsicHeight(): Int = iconSize
            }
            val view: View = if (toolbarNavigation) {
                object : ImageButton(context) {
                    override fun draw(canvas: Canvas) {
                        super.draw(canvas)
                        effect?.let { drawSpotLight.invoke(it, canvas) }
                    }
                }.apply {
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(0, 0, 0, 0)
                    scaleType = ImageView.ScaleType.CENTER
                    setImageDrawable(sizedIcon)
                }.also { effect = type.getMethod("forToolbarButton", View::class.java).invoke(null, it) }
            } else {
                // Use Gallery's complete native icon item. It owns measurement, material
                // attachment, configuration refresh and spotlight drawing, not only a delegate.
                val itemViewType = source.load("com.coui.appcompat.toolbar.COUIActionMenuItemView")
                val itemView = itemViewType.getConstructor(Context::class.java).newInstance(context) as android.widget.TextView
                itemView.layoutParams = ViewGroup.MarginLayoutParams(iconSize * 2, iconSize * 2)
                val menuType = source.load("androidx.appcompat.view.menu.MenuBuilder")
                val menu = menuType.getConstructor(Context::class.java).newInstance(context)
                val item = menuType.getMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, CharSequence::class.java).invoke(menu, 0, 1, 0, contentDescription) as android.view.MenuItem
                item.setIcon(sizedIcon)
                item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                itemViewType.getMethod("initialize", source.load("androidx.appcompat.view.menu.MenuItemImpl"),
                    Int::class.javaPrimitiveType).invoke(itemView, item, 0)
                itemView.text = null
                // The deprecated setIfInBlurWindow(false) actually enables drawable-only mode
                // and forces material off. Use the explicit API for independent View material.
                itemViewType.getMethod("setItemIsDrawableMaterialEffect", Boolean::class.javaPrimitiveType)
                    .invoke(itemView, false)
                effect = itemViewType.getMethod("getEffectDelegate").invoke(itemView)
                itemView
            }
            val state = source.load("com.coui.appcompat.toolbar.COUIToolbar\$MaterialEffectState")
            val follow = state.getMethod("getValue").invoke(state.getField("FOLLOW_SYSTEM").get(null))
            type.getMethod("setUseMaterialEffectAttr", Int::class.javaPrimitiveType).invoke(effect, follow)
            val materialType = source.load("com.coui.appcompat.toolbar.COUIMaterialEffectType")
                // TYPE_DEFAULT is a tint-only preset with a zero blur radius in ColorOS 17.
                .getField("TYPE_DEFAULT_BLUR").getInt(null)
            type.getMethod("setMaterialEffectType", Int::class.javaPrimitiveType).invoke(effect, materialType)
            NativeIconButton(view, sizedIcon, requireNotNull(effect), type.getMethod("detach"), source).also { button ->
                button.updateHomeStyle(style?.weatherMaterial == true, style?.foreground, backdropBlurred)
                source.bindFollowLightIntensity(view, effect)
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) { view.post { button.refreshAppearance() } }
                    override fun onViewDetachedFromWindow(view: View) = Unit
                })
            }
        }
    } ?: return false
    val overflowEntries = LocalHomeOverflowMenu.current
        ?.takeIf { iconRes == R.drawable.ic_more_horizontal }
    val menuEntries = rememberUpdatedState(overflowEntries?.value.orEmpty())
    val menu = remember(button, overflowEntries != null, effectiveDark, style) {
        if (overflowEntries == null) null else ColorOsNativeUi.prepare("create the native overflow menu") {
            // Popup parses its surface/text colors once in its constructor. A separate context
            // follows the same neutral theme and material as the home button.
            NativeOverflowMenu(source, source.themedContext(host, effectiveDark), host,
                neutral = style?.weatherMaterial == true, foreground = style?.foreground)
        }
    }
    key(context, iconRes) {
        AndroidView(factory = { button.view }, modifier = modifier, update = { view ->
            button.updateHomeStyle(style?.weatherMaterial == true, style?.foreground, backdropBlurred)
            if (source.updateTheme(context, effectiveDark)) button.refreshAppearance()
            view.contentDescription = contentDescription
            view.isSelected = selected
            view.isEnabled = enabled
            // Resting return-anchor copies retain the normal material, without accepting input.
            view.setOnClickListener(if (interactive) View.OnClickListener {
                if (menu != null) menu.show(view, menuEntries.value) else callback.value()
            } else null)
            view.isClickable = interactive
            view.isFocusable = interactive
        })
    }
    DisposableEffect(button) { onDispose { button.dispose() } }
    DisposableEffect(menu) { onDispose { menu?.dispose() } }
    return true
}

private class NativeIconButton(val view: View, val icon: Drawable, val effect: Any, val detach: Method,
    private val source: ColorOsNativeUi.Source) {
    private var neutral = false
    private var foreground: Int? = null
    private var backdropBlurred = false
    fun updateHomeStyle(wanted: Boolean, foreground: Int?, backdropBlurred: Boolean) {
        if (neutral == wanted && this.foreground == foreground && this.backdropBlurred == backdropBlurred) return
        neutral = wanted
        this.foreground = foreground
        this.backdropBlurred = backdropBlurred
        refreshAppearance()
    }
    fun refreshAppearance() {
        effect.javaClass.getMethod("refreshRipple", Context::class.java).invoke(effect, view.context)
        val params = source.toolbarButtonBlurParams(view.context, neutral, backdropBlurred)
        effect.javaClass.getMethod("setCustomBlurParams", source.load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams"))
            .invoke(effect, params)
        icon.setTintList(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(foreground ?: source.color(view.context, "couiColorPrimary").defaultColor,
                foreground ?: source.color(view.context, "couiColorLabelPrimary").defaultColor)))
        val materialState = listOf("mMaterialType", "mIsDrawableMaterialEffect", "mBlurAllowed", "mBlurOn", "mStrokeAllowed", "mStrokeOn")
            .joinToString { name ->
                val field = effect.javaClass.getDeclaredField(name).apply { isAccessible = true }
                "$name=${field.get(effect)}"
            }
        val renderEffectAvailable = source.load("com.coui.appcompat.COUIMaterialEffect")
            .getMethod("canApplyBackgroundRenderEffect").invoke(null)
        Log.i("SleepDownCOUI", "Toolbar material refreshed: type=${view.javaClass.simpleName}, " +
            "size=${view.width}x${view.height}, backgroundRenderEffect=$renderEffectAvailable, " +
            "background=${view.background?.javaClass?.simpleName}, $materialState")
    }

    fun dispose() {
        view.setOnClickListener(null)
        detach.invoke(effect)
    }
}
