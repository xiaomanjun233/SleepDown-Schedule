package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.PopupWindow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuItem

internal class NativeOverflowMenu(private val source: ColorOsNativeUi.Source, private val context: Context,
    private val host: Context, private val neutral: Boolean, private val foreground: Int?,
    private val onDismiss: (() -> Unit)? = null, private val alignToAnchorEdge: Boolean = false) {
    private val type = source.load("com.coui.appcompat.poplist.COUIPopupListWindow")
    private val window = type.getConstructor(Context::class.java).newInstance(context) as PopupWindow
    private var entries: List<NativeMenuItem> = emptyList()
    private var activeParent = -1
    private var collapseOnSelection = true
    private var pending: (() -> Unit)? = null
    private var anchor: View? = null
    private var dismissed = true
    private var dismissWaiter: (() -> Unit)? = null
    private val animationListenerType = source.load("${type.name}\$OnMainMenuAnimationListener")

    init {
        source.bindFollowLightIntensity(window.contentView, multiplier = 1.7f, afterMaterialApplied = {
            applyButtonMaterial()
        })
        type.getMethod("setUseBackgroundBlur", Boolean::class.javaPrimitiveType).invoke(window, true)
        // WindowManager resolves animation resources against our package, not the vendor APK.
        // The menu root already owns COUI's enter/exit springs; avoid a second window animation.
        window.animationStyle = 0
        window.setOnDismissListener { anchor?.post { runPendingAction() } }
        type.getMethod("setMenuWidth", Int::class.javaPrimitiveType).invoke(window,
            context.resources.getDimensionPixelSize(source.resource("coui_popup_list_window_width_with_icon", "dimen")))
        type.getMethod("setMenuMaxWidth", Int::class.javaPrimitiveType).invoke(window,
            context.resources.getDimensionPixelSize(source.resource("coui_popup_list_window_max_width", "dimen")))
        type.getMethod("setOnItemClickListener", AdapterView.OnItemClickListener::class.java)
            .invoke(window, AdapterView.OnItemClickListener { _, _, position, _ ->
                val item = entries.getOrNull(position) ?: return@OnItemClickListener
                if (item.children.isNotEmpty()) activeParent = position else select(item)
            })
        type.getMethod("setSubMenuClickListener", AdapterView.OnItemClickListener::class.java)
            .invoke(window, AdapterView.OnItemClickListener { _, _, position, _ ->
                entries.getOrNull(activeParent)?.children?.getOrNull(position)?.let(::select)
            })
        type.getMethod("setOnMainMenuAnimationListener", animationListenerType).invoke(window,
            source.listener(animationListenerType.name) { name, _ ->
                Log.i("SleepDownCOUI", "Overflow animation: $name")
                // An enter spring can be canceled by exit; keep a selected action until actual exit.
                if (name == "onMainMenuExited") anchor?.post { runPendingAction() }
                null
            })
        window.contentView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { anchor?.post { runPendingAction() } }
        })
    }

    private fun applyButtonMaterial() {
        for (field in listOf("mMainMenuWrapper", "mSubMenuWrapper")) {
            val wrapper = type.getDeclaredField(field).apply { isAccessible = true }.get(window) as View
            source.applyMenuMaterial(context, wrapper, neutral)
        }
        Log.i("SleepDownCOUI", "Overflow shared menu material applied; native window blur retained")
    }

    fun show(anchor: View, entries: List<AddMenuAction>) {
        showItems(anchor, entries.map { action ->
            NativeMenuItem(action.label, action.iconRes,
                foreground = action.textTint?.takeIf { it.isSpecified }?.toArgb() ?: foreground,
                iconTint = action.iconTint?.takeIf { it.isSpecified }?.toArgb() ?: foreground,
                onClick = action.onClick)
        })
    }

    private fun select(item: NativeMenuItem) {
        if (!item.enabled || !window.isShowing || pending != null) return
        if (collapseOnSelection) { pending = item.onClick; window.dismiss() }
        else item.onClick()
    }

    fun showItems(anchor: View, entries: List<NativeMenuItem>, collapseOnSelection: Boolean = true) {
        // COUI's isShowing() becomes false at the start of exit, while its root is still attached.
        if (window.isShowing || window.contentView.isAttachedToWindow || pending != null || entries.isEmpty()) return
        this.anchor = anchor
        dismissed = false
        this.entries = entries
        this.collapseOnSelection = collapseOnSelection
        activeParent = -1
        val items = entries.map { source.createMenuItem(context, host, it) }
        type.getMethod("setItemList", List::class.java).invoke(window, items)
        if (alignToAnchorEdge) {
            // Resolve native width and safe bounds before the first visible animation frame.
            // COUI's normal X rule centers on the anchor; launcher shortcuts align an edge.
            type.getMethod("setGlobalOffset", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(window, 0, 0)
            type.getMethod("prepareShowMainMenu", View::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(window, anchor, Int.MIN_VALUE, Int.MIN_VALUE, true)
            val helper = type.getDeclaredField("mLocateHelper").apply { isAccessible = true }.get(window)
            val domain = helper.javaClass.getMethod("getDomain").invoke(helper)
            val available = android.graphics.Rect()
            domain.javaClass.getMethod("getAvailableRect", android.graphics.Rect::class.java).invoke(domain, available)
            val anchorRect = domain.javaClass.getDeclaredField("mAnchor").apply { isAccessible = true }
                .get(domain) as android.graphics.Rect
            val menuRect = domain.javaClass.getDeclaredField("mMainMenu").apply { isAccessible = true }
                .get(domain) as android.graphics.Rect
            val left = shortcutMenuLeft(anchorRect.left, anchorRect.right, menuRect.width(),
                available.left, available.right, anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL)
            type.getMethod("setGlobalOffset", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(window, left - menuRect.left, 0)
            type.getMethod("show", View::class.java, Boolean::class.javaPrimitiveType).invoke(window, anchor, true)
        } else type.getMethod("show", View::class.java).invoke(window, anchor)
    }

    private fun runPendingAction() {
        if (dismissed) return
        dismissed = true
        val action = pending
        pending = null
        val waiter = dismissWaiter
        dismissWaiter = null
        waiter?.invoke()
        if (anchor?.isAttachedToWindow == true) {
            if (action != null) action() else onDismiss?.invoke()
        }
    }

    fun dismiss(afterDismiss: () -> Unit) {
        if (dismissed) { afterDismiss(); return }
        dismissWaiter = afterDismiss
        window.dismiss()
    }

    fun dispose() {
        pending = null
        dismissed = true
        dismissWaiter?.invoke()
        dismissWaiter = null
        type.getMethod("setOnMainMenuAnimationListener", animationListenerType).invoke(window, null)
        type.getMethod("forceDismiss").invoke(window)
        anchor = null
    }
}
