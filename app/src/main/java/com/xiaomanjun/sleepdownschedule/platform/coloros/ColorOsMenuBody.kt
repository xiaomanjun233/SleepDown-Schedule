package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*

/** Shared with the real overflow window, so tint/selection rules cannot drift between hosts. */
internal fun ColorOsNativeUi.Source.createMenuItem(context: Context, host: Context, item: NativeMenuItem): Any {
    val type = load("com.coui.appcompat.poplist.PopupListItem")
    val foreground = item.foreground?.let(ColorStateList::valueOf) ?: color(context, "couiColorLabelPrimary")
    val icon = item.iconRes?.let { ContextCompat.getDrawable(host, colorOsHomeIconResource(it))?.mutate() }
    icon?.setTintList(item.iconTint?.let(ColorStateList::valueOf) ?: foreground)
    return type.getConstructor(Drawable::class.java, String::class.java, Boolean::class.javaPrimitiveType)
        .newInstance(icon, item.title, item.enabled).also {
            type.getMethod("setForceTint", Int::class.javaPrimitiveType).invoke(it, 0)
            type.getMethod("setTitleColorList", ColorStateList::class.java).invoke(it, foreground)
            type.getMethod("setCheckable", Boolean::class.javaPrimitiveType).invoke(it, item.checkable)
            type.getMethod("setChecked", Boolean::class.javaPrimitiveType).invoke(it, item.selected)
            item.description?.let { value -> type.getMethod("setDescription", String::class.java).invoke(it, value) }
            if (item.children.isNotEmpty()) type.getMethod("setSubMenuItemList", ArrayList::class.java)
                .invoke(it, ArrayList(item.children.map { child -> createMenuItem(context, host, child) }))
        }
}

/** RoundFrameLayout's standard menu colors/radius, with the same optional home-neutral tint. */
internal fun ColorOsNativeUi.Source.menuBlurParams(context: Context, neutral: Boolean): Any {
    val base = load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
    val preset = weatherButtonBlurParams(context)
    val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val theme = if (dark) "dark" else "light"
    fun layer(method: String, resourceName: String): String {
        val color = if (neutral) base.getMethod(method, Context::class.java).invoke(preset, context) as Int
            else context.getColor(resource(resourceName, "color"))
        return "#" + Integer.toHexString(color).padStart(8, '0')
    }
    return load("com.coui.appcompat.COUIMaterialBlurEffect\$BlurEffectParams")
        .getConstructor(Float::class.javaPrimitiveType, String::class.java, android.graphics.BlendMode::class.java,
            String::class.java, android.graphics.BlendMode::class.java)
        .newInstance(160f, layer("getColorLayer1", "coui_popup_list_blend_blur_$theme"),
            base.getMethod("getBlendModeLayer1").invoke(preset),
            layer("getColorLayer2", "coui_popup_list_mix_blur_$theme"),
            base.getMethod("getBlendModeLayer2").invoke(preset))
}

internal fun ColorOsNativeUi.Source.applyMenuMaterial(context: Context, wrapper: View, neutral: Boolean) {
    val params = menuBlurParams(context, neutral)
    val base = load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
    val ui = load("com.coui.appcompat.uiutil.UIUtil")
    fun layer(method: String): Any = ui.getMethod("colorToFloats", Int::class.javaPrimitiveType)
        .invoke(null, base.getMethod(method, Context::class.java).invoke(params, context))
    val builder = wrapper.javaClass.getDeclaredField("mBackgroundBlurBuilder")
        .apply { isAccessible = true }.get(wrapper)
    val type = builder.javaClass
    type.getMethod("setMode", Int::class.javaPrimitiveType).invoke(builder, 4)
    type.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(builder,
        (base.getMethod("getBlurRadius", Context::class.java).invoke(params, context) as Float).toInt())
    for (theme in listOf("Light", "Dark")) {
        type.getMethod("setMixColor$theme", FloatArray::class.java).invoke(builder, layer("getColorLayer2"))
        type.getMethod("setBlendColor$theme", FloatArray::class.java).invoke(builder, layer("getColorLayer1"))
    }
    if (wrapper.isAttachedToWindow) type.getMethod("refreshBlurParams").invoke(builder)
}

@Composable
internal fun ColorOsMenuSurface(request: NativeMenuSurfaceRequest, content: @Composable () -> Unit): Boolean {
    // RoundFrameLayout uses ViewRootManager/BackgroundBlurDrawable: it samples behind a
    // separate popup window. Embedded menus must sample preceding content in this window.
    return ColorOsMaterialSurface(request.surface,
        if (request.neutral) ColorOsSurfaceStyle.NeutralMenu else ColorOsSurfaceStyle.Menu,
        request.touchSource, request.motion, content)
}

/** Keep SleepDown's labels and geometry; COUITouchListView owns press/slide selection. */
@Composable
internal fun ColorOsMenuList(request: NativeMenuListRequest): Boolean {
    val native = rememberNativeContext(request.dark) ?: return false
    val host = LocalContext.current
    val latest = rememberUpdatedState(request)
    val density = LocalDensity.current
    val rowHeightPx = with(density) { request.rowHeight.roundToPx() }
    val rowGapPx = with(density) { request.rowGap.roundToPx() }
    val appearance = request.items.map { it.copy(onClick = {}) }
    val list = remember(native, host, appearance, request.dark, rowHeightPx, rowGapPx) {
        ColorOsNativeUi.prepare("create embedded native menu list") {
            val source = native.first
            val context = native.second
            source.updateTheme(context, request.dark)
            val nativeAdapter = source.load("com.coui.appcompat.poplist.DefaultAdapter")
                .getConstructor(Context::class.java, List::class.java).newInstance(context,
                    request.items.map { source.createMenuItem(context, host, it) }) as BaseAdapter
            val adapter = object : BaseAdapter() {
                override fun getCount() = nativeAdapter.count
                override fun getItem(position: Int) = nativeAdapter.getItem(position)
                override fun getItemId(position: Int) = nativeAdapter.getItemId(position)
                override fun getItemViewType(position: Int) = nativeAdapter.getItemViewType(position)
                override fun getViewTypeCount() = nativeAdapter.viewTypeCount
                override fun areAllItemsEnabled() = false
                override fun isEnabled(position: Int) = position % 2 == 0 &&
                    latest.value.enabled && latest.value.items.getOrNull(position / 2)?.enabled == true
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                    nativeAdapter.getView(position, convertView, parent).apply {
                        // DefaultAdapter interleaves data rows (even) and separators (odd).
                        // Preserve its mask drawable, but let Compose draw our original labels.
                        minimumHeight = 0
                        setPadding(paddingLeft, 0, paddingRight, 0)
                        layoutParams = android.widget.AbsListView.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            if (position % 2 == 0) rowHeightPx else rowGapPx)
                        if (position % 2 == 0) {
                            // DefaultAdapter disables round style. Our inset action rows need
                            // its native capsule mask, including the touch-select animation.
                            background.javaClass.getMethod("setIsRoundStyle", Boolean::class.javaPrimitiveType)
                                .invoke(background, true)
                        }
                        if (this is android.view.ViewGroup) for (i in 0 until childCount) getChildAt(i).alpha = 0f
                        contentDescription = latest.value.items.getOrNull(position / 2)?.title
                        setOnClickListener(null)
                        isClickable = false
                        if (convertView == null && position % 2 == 0)
                            source.bindFollowLightIntensity(this, multiplier = 1.7f)
                    }
            }
            val type = source.load("com.coui.appcompat.poplist.COUITouchListView")
            (type.getConstructor(Context::class.java).newInstance(context) as android.widget.ListView).apply {
                divider = null
                dividerHeight = 0
                selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                this.adapter = adapter
                nativeAdapter.javaClass.getMethod("setListView", android.widget.ListView::class.java).invoke(nativeAdapter, this)
                type.getMethod("allowDispatchEvent", Boolean::class.javaPrimitiveType).invoke(this, true)
                type.getMethod("setIsNeedVibrate", Boolean::class.javaPrimitiveType).invoke(this, true)
                source.bindFollowLightIntensity(this, multiplier = 1.7f)
                onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, index, _ ->
                    if (index % 2 == 0) latest.value.takeIf { it.enabled }?.items?.getOrNull(index / 2)
                        ?.takeIf { it.enabled }?.onClick?.invoke()
                }
            }
        }
    } ?: return false
    val externalFeedback = remember(list) { mutableMapOf<View, NativeMenuFeedback>() }
    Box(request.modifier) {
        key(list) { AndroidView(factory = { list }, modifier = Modifier.matchParentSize(), update = {
            if (it.isEnabled != request.enabled) it.isEnabled = request.enabled
            for (childIndex in 0 until it.childCount) {
                val position = it.firstVisiblePosition + childIndex
                if (position % 2 == 0) {
                    val row = it.getChildAt(childIndex)
                    externalFeedback.getOrPut(row) { NativeMenuFeedback(row) }
                        .setPressed(request.externalHighlightedIndex == position / 2 && request.enabled)
                }
            }
        }) }
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(request.rowGap)) {
            request.items.indices.forEach { index -> request.rowContent(index) }
        }
    }
    DisposableEffect(list) { onDispose { list.onItemClickListener = null } }
    return true
}

@Composable
internal fun ColorOsMenuRow(request: NativeMenuRowRequest): Boolean {
    val native = rememberNativeContext(request.dark) ?: return false
    val host = LocalContext.current
    val binding = remember(native, host, request.item, request.first, request.last, request.dark, request.contentVisible) {
        ColorOsNativeUi.prepare("create native popup menu item") {
            val source = native.first
            val context = native.second
            source.updateTheme(context, request.dark)
            val item = source.createMenuItem(context, host, request.item)
            val adapter = source.load("com.coui.appcompat.poplist.DefaultAdapter")
                .getConstructor(Context::class.java, List::class.java).newInstance(context, listOf(item)) as BaseAdapter
            // Input stays with the host's existing slide-selection and cascading controller.
            val container = object : FrameLayout(context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
            }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            val row = adapter.getView(0, null, container)
            if (!request.contentVisible && row is android.view.ViewGroup) {
                for (index in 0 until row.childCount) row.getChildAt(index).alpha = 0f
            }
            val edge = context.resources.getDimensionPixelSize(source.resource("coui_popup_list_padding_vertical", "dimen"))
            val trimTop = if (request.first) 0 else edge
            val trimBottom = if (request.last) 0 else edge
            row.setPaddingRelative(row.paddingStart, (row.paddingTop - trimTop).coerceAtLeast(0),
                row.paddingEnd, (row.paddingBottom - trimBottom).coerceAtLeast(0))
            row.minimumHeight = (row.minimumHeight - trimTop - trimBottom).coerceAtLeast(0)
            container.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
            source.bindFollowLightIntensity(row, multiplier = 1.7f)
            val indicator = if (request.item.children.isNotEmpty()) findMenuIndicator(row) else null
            Triple(container, row, indicator)
        }
    } ?: return false
    val latest = rememberUpdatedState(request)
    val feedback = remember(binding) { NativeMenuFeedback(binding.second) }
    DisposableEffect(feedback) { onDispose { feedback.setPressed(false) } }
    // Popup hosts ask for intrinsic sizes. AndroidView alone cannot answer those queries.
    val policy = remember(binding) { NativeMenuMeasurePolicy(binding.first) }
    Layout(content = {
        key(binding.first) { AndroidView(factory = { binding.first }, update = {
            feedback.setPressed(request.pressed && request.item.enabled)
        }, modifier = Modifier.drawWithContent {
            binding.third?.rotation = latest.value.indicatorRotation()
            drawContent()
        }) }
    }, modifier = latest.value.modifier, measurePolicy = policy)
    return true
}

/** COUITouchListView changes row selection through StatefulDrawable, not View.pressed. */
private class NativeMenuFeedback(private val row: View) {
    private val drawable = requireNotNull(row.background)
    private val enter = drawable.javaClass.getMethod("setTouchSelectEntered")
    private val exit = drawable.javaClass.getMethod("setTouchSelectExited")
    private var pressed = false

    fun setPressed(value: Boolean) {
        if (pressed == value) return
        pressed = value
        row.isPressed = value
        (if (value) enter else exit).invoke(drawable)
    }
}

private fun findMenuIndicator(view: View): CheckBox? {
    if (view is CheckBox) return view
    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
        findMenuIndicator(view.getChildAt(index))?.let { return it }
    }
    return null
}

internal class NativeMenuMeasurePolicy(private val view: View) : MeasurePolicy {
    private fun nativeSize(width: Int): Pair<Int, Int> {
        view.measure(View.MeasureSpec.makeMeasureSpec(if (width == Constraints.Infinity) 0 else width,
            if (width == Constraints.Infinity) View.MeasureSpec.UNSPECIFIED else View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return view.measuredWidth to view.measuredHeight
    }
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val child = measurables.single().measure(constraints)
        return layout(child.width, child.height) { child.place(0, 0) }
    }
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = nativeSize(Constraints.Infinity).first
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = nativeSize(Constraints.Infinity).first
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = nativeSize(width).second
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = nativeSize(width).second
}
