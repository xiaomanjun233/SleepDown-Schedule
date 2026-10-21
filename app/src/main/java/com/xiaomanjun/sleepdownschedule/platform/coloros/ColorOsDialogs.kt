package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Activity
import android.app.Dialog as AndroidDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeAlertRequest
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Native COUI owns the window, surface, scrolling message, button layout and touch feedback. */
@Composable
internal fun ColorOsAlertDialog(request: NativeAlertRequest): Boolean {
    val native = rememberNativeContext(request.dark) ?: return false
    val source = native.first
    val context = native.second
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pickerStyle = remember(native, density) { source.pickerStyle(context, density) }
    val host = LocalContext.current
    val owner = LocalView.current
    val parentComposition = rememberCompositionContext()
    val latest = rememberUpdatedState(request)
    val primaryColor = rememberUpdatedState(MaterialTheme.colorScheme.primary.toArgb())
    val hostSize = rememberUpdatedState(
        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogHostSize.current
            ?: LocalWindowInfo.current.containerSize)
    val actionLayout = request.actions.map { it.style }
    var showFailed by remember(native, actionLayout) { mutableStateOf(false) }
    val control = remember(native, actionLayout, request.messageContent != null, request.deferDismissUntilHost) {
        ColorOsNativeUi.prepare("create a system alert") {
            source.updateTheme(context, request.dark)
            val builderType = source.load("com.coui.appcompat.dialog.COUIAlertDialogBuilder")
            // Use COUI's default dialog, including its own placement and window animation style.
            val builder = builderType.getConstructor(Context::class.java).newInstance(context)
            lateinit var alert: NativeAlert
            if (request.title.isNotBlank()) builderType.getMethod("setTitle", CharSequence::class.java).invoke(builder, request.title)
            if (request.message.isNotBlank()) builderType.getMethod("setMessage", CharSequence::class.java)
                .invoke(builder, request.message)
            builderType.getMethod("setBlurBackgroundDrawable", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            check(request.actions.size <= 3) { "System alerts support up to three actions" }
            val buttonSlots = colorOsAlertSlots(request.actions.map { it.style })
            val recommended = buttonSlots.indexOf(ColorOsAlertSlot.Positive)
            val recommendType = source.load("com.coui.appcompat.button.COUIButton\$ButtonType")
            builderType.getMethod("setRecommendButtonType", recommendType).invoke(builder,
                recommendType.getField(if (request.actions.getOrNull(recommended)?.style == LiquidAlertActionStyle.Primary)
                    "PRIMARY" else "SECONDARY").get(null))
            val slots = buttonSlots.map { slot ->
                when (slot) {
                    ColorOsAlertSlot.Positive -> DialogInterface.BUTTON_POSITIVE
                    ColorOsAlertSlot.Negative -> DialogInterface.BUTTON_NEGATIVE
                    ColorOsAlertSlot.Neutral -> DialogInterface.BUTTON_NEUTRAL
                }
            }
            request.actions.forEachIndexed { index, action ->
                val method = when (slots[index]) {
                    DialogInterface.BUTTON_NEGATIVE -> "setNegativeButton"
                    DialogInterface.BUTTON_NEUTRAL -> "setNeutralButton"
                    else -> "setPositiveButton"
                }
                builderType.getMethod(method, CharSequence::class.java, DialogInterface.OnClickListener::class.java)
                    .invoke(builder, action.label, null)
            }
            val compose = if (request.messageContent != null) NativeDialogComposeView(host) {
                checkNotNull(alert.dialog.window)
            }.apply {
                id = View.generateViewId()
                setViewTreeLifecycleOwner(owner.findViewTreeLifecycleOwner())
                setViewTreeSavedStateRegistryOwner(owner.findViewTreeSavedStateRegistryOwner())
                setViewTreeViewModelStoreOwner(owner.findViewTreeViewModelStoreOwner())
                setParentCompositionContext(parentComposition)
                setContent {
                    val current = latest.value
                    CompositionLocalProvider(LocalNativeDialogSurface provides true,
                        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativePickerStyle provides pickerStyle,
                        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogHostSize provides hostSize.value,
                        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogWindow provides alert.dialog.window) {
                        Box(Modifier.fillMaxWidth().heightIn(max = current.messageMaxHeight)
                            .padding(current.contentPadding)
                            .then(if (current.scrollableMessageContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                            current.messageContent?.invoke()
                        }
                    }
                }
                builderType.getMethod("setView", View::class.java).invoke(builder, this)
            } else null
            val dialog = builderType.getMethod("create").invoke(builder) as AndroidDialog
            host.activityOrNull()?.let(dialog::setOwnerActivity)
            dialog.window?.let { window ->
                // WindowManager must resolve COUI's animation ID in the APK that owns it.
                // The Activity token, app context and process UID remain SleepDown's.
                window.attributes = window.attributes.apply { packageName = source.packageName }
            }
            NativeAlert(dialog, builder, compose, slots, source, context, { primaryColor.value }) { latest.value }
                .also { alert = it }
        }
    } ?: return false
    if (showFailed) return false
    DisposableEffect(control) {
        if (ColorOsNativeUi.prepare("show a system alert") { control.show(); true } != true) {
            control.dispose()
            showFailed = true
        }
        onDispose { control.dispose() }
    }
    SideEffect { if (!showFailed) control.update() }
    return true
}

/** AndroidComposeView's immediate parent exposes the real dialog window for IME routing. */
private class NativeDialogComposeView(context: Context, private val windowProvider: () -> Window) :
    AbstractComposeView(context), androidx.compose.ui.window.DialogWindowProvider {
    override val window: Window get() = windowProvider()
    private var body by mutableStateOf<(@Composable () -> Unit)?>(null)
    override fun onCheckIsTextEditor() = true
    fun setContent(content: @Composable () -> Unit) { body = content }
    @Composable override fun Content() { body?.invoke() }
}

private class NativeAlert(
    val dialog: AndroidDialog,
    private val builder: Any,
    private val compose: AbstractComposeView?,
    private val slots: List<Int>,
    private val source: ColorOsNativeUi.Source,
    private val context: Context,
    private val primaryColor: () -> Int,
    private val request: () -> NativeAlertRequest
) {
    private var disposed = false
    private var pending: (() -> Unit)? = null
    private var dismissGuard: NativeFormDismissGuard? = null
    private var displayedTitle: String? = null
    private var displayedMessage: String? = null
    private var actionsBound = false
    private var displayedDark = request().dark
    private var actionAppearance: Pair<Boolean, Int>? = null
    private var appliedWidth: Int? = null

    fun show() {
        dialog.setOnCancelListener { pending = request().onDismissRequest }
        dialog.setOnDismissListener {
            if (!disposed) {
                val completion = pending ?: request().onDismissRequest
                pending = null
                completion()
                request().onDismissFinished?.invoke()
            }
        }
        if (request().deferDismissUntilHost) {
            // Outside/back requests may open an unsaved-changes confirmation instead of closing.
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
        }
        dialog.show()
        // COUIAlertDialogBuilder.show() performs this after Dialog.show(), before the first
        // window traversal. create()+show() alone never installs its blur attach listener or
        // initializes the COUIButtonBarLayout. Keep our pre-show ownership/dismiss configuration.
        builder.javaClass.getDeclaredMethod("disabledTitleScroll", source.load("androidx.appcompat.app.AlertDialog"))
            .apply { isAccessible = true }.invoke(builder, dialog)
        builder.javaClass.getMethod("updateViewAfterShown").invoke(builder)
        dialog.window?.let { window ->
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshotRegistry.register(window) {
                val blur = builder.javaClass.getMethod("getBackgroundBlurBuilder").invoke(builder)
                if (blur.javaClass.getMethod("isBlurryState").invoke(blur) != true) null else {
                    val target = blur.javaClass.getDeclaredField("mTargetView").apply { isAccessible = true }.get(blur) as View
                    val position = IntArray(2).also(target::getLocationOnScreen)
                    val origin = IntArray(2).also(window.decorView::getLocationOnScreen)
                    val radius = blur.javaClass.getDeclaredField("mCornerRadiusTopLeft")
                        .apply { isAccessible = true }.getFloat(blur)
                    val x = (position[0] - origin[0]).toFloat()
                    val y = (position[1] - origin[1]).toFloat()
                    com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogMaterialFrame(
                        androidx.compose.ui.geometry.Rect(x, y, x + target.width, y + target.height), radius, request().dark)
                }
            }
        }
        if (compose != null) {
            // AlertController checks View.onCheckIsTextEditor before the Compose editor exists
            // and sets ALT_FOCUSABLE_IM. Clear it after COUI installs the custom panel so this
            // window can own an input connection; keep its native material and placement.
            dialog.window?.apply {
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        if (compose != null && request().title.isBlank() && slots.isEmpty()) {
            // These forms own their complete header/actions and balanced Compose padding.
            dialog.findViewById<View>(source.resource("customPanel", "id"))?.let {
                it.setPadding(it.paddingLeft, 0, it.paddingRight, 0)
            }
        }
        val primaryIndex = request().actions.indexOfFirst { it.style == LiquidAlertActionStyle.Primary }
        if (slots.isNotEmpty()) {
            val primary = dialog.javaClass.getMethod("getButton", Int::class.javaPrimitiveType)
                .invoke(dialog, slots[primaryIndex.coerceAtLeast(0)]) as TextView
            val barType = source.load("com.coui.appcompat.buttonBar.COUIButtonBarLayout")
            val bar = requireNotNull(generateSequence(primary.parent) { it.parent }.firstOrNull(barType::isInstance))
            val buttonType = source.load("com.coui.appcompat.button.COUIButton\$ButtonType")
            // Three-action alerts put Primary in the first (neutral) slot. Keep COUI's own
            // recommendation identity aligned too, so later material refreshes use that slot.
            barType.getMethod("setRecommendButtonId", Int::class.javaPrimitiveType)
                .invoke(bar, if (primaryIndex >= 0) primary.id else View.NO_ID)
            barType.getMethod("setRecommendButtonType", buttonType).invoke(bar,
                buttonType.getField(if (primaryIndex >= 0) "PRIMARY" else "SECONDARY").get(null))
        }
        request().actions.forEachIndexed { index, action ->
            val button = dialog.javaClass.getMethod("getButton", Int::class.javaPrimitiveType)
                .invoke(dialog, slots[index]) as TextView
            source.configureCapsuleButton(button, primary = action.style == LiquidAlertActionStyle.Primary)
        }
        dialog.window?.decorView?.let {
            source.bindFollowLightIntensity(it, afterMaterialApplied = { applyActionAppearance(force = true) })
        }
        if (request().deferDismissUntilHost) {
            dismissGuard = NativeFormDismissGuard(dialog, source) { request().onDismissRequest() }
        }
        update()
        Log.i("SleepDownCOUI", "System dialog shown: buttons=${slots.size}, customContent=${compose != null}, " +
            "gravity=${dialog.window?.attributes?.gravity}, animation=${dialog.window?.attributes?.windowAnimations}, " +
            "animationPackage=${dialog.window?.attributes?.packageName}")
        dialog.window?.decorView?.post {
            if (!disposed && dialog.isShowing) ColorOsNativeUi.prepare("inspect attached dialog material") {
                val blur = builder.javaClass.getDeclaredField("mBackgroundBlurBuilder")
                    .apply { isAccessible = true }.get(builder)
                val blurry = blur.javaClass.getMethod("isBlurryState").invoke(blur)
                Log.i("SleepDownCOUI", "System dialog material attached: windowBlur=$blurry, " +
                    "hardware=${dialog.window?.decorView?.isHardwareAccelerated}")
            }
        }
    }

    fun update() {
        if (disposed || !dialog.isShowing) return
        val current = request()
        if (!current.visible) { pending = {}; dialog.dismiss(); return }
        if (displayedDark != current.dark) {
            source.updateTheme(context, current.dark)
            dialog.window?.decorView?.dispatchConfigurationChanged(context.resources.configuration)
            val blur = builder.javaClass.getDeclaredField("mBackgroundBlurBuilder")
                .apply { isAccessible = true }.get(builder)
            blur.javaClass.getMethod("refreshBlurParams").invoke(blur)
            displayedDark = current.dark
            actionAppearance = null
        }
        current.windowWidth?.let { width ->
            val pixels = (width.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            if (appliedWidth != pixels || dialog.window?.attributes?.width != pixels) {
                // Keep COUI's later configuration refresh on the same width as our form.
                // The root's native transparent insets still own the visible frame margins.
                builder.javaClass.getDeclaredField("mContentMaxWidth").apply { isAccessible = true }
                    .setInt(builder, pixels)
                dialog.findViewById<View>(source.resource("parentPanel", "id"))?.let { panel ->
                    panel.javaClass.getMethod("setMaxWidth", Int::class.javaPrimitiveType).invoke(panel, pixels)
                }
                dialog.window?.setLayout(pixels, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                appliedWidth = pixels
            }
        }
        if (displayedTitle != current.title && (current.title.isNotBlank() || displayedTitle != null)) {
            dialog.setTitle(current.title)
            displayedTitle = current.title
        }
        if (compose == null && displayedMessage != current.message && (current.message.isNotBlank() || displayedMessage != null)) {
            dialog.javaClass.getMethod("setMessage", CharSequence::class.java).invoke(dialog, current.message)
            displayedMessage = current.message
        }
        current.actions.forEachIndexed { index, action ->
            val button = dialog.javaClass.getMethod("getButton", Int::class.javaPrimitiveType)
                .invoke(dialog, slots[index]) as TextView
            if (button.text.toString() != action.label) button.text = action.label
            if (button.isEnabled != action.enabled) {
                button.isEnabled = action.enabled
                actionAppearance = null
            }
            // Replace only click dispatch; native press/spotlight remains on the original COUIButton.
            if (!actionsBound) button.setOnClickListener {
                val active = request().actions[index]
                if (active.enabled) {
                    if (active.dismissOnClick) { pending = active.onClick; dialog.dismiss() }
                    else active.onClick()
                }
            }
        }
        applyActionAppearance()
        actionsBound = true
    }

    private fun applyActionAppearance(force: Boolean = false) {
        if (disposed || !dialog.isShowing) return
        val appearance = request().dark to primaryColor()
        if (!force && actionAppearance == appearance) return
        request().actions.forEachIndexed { index, action ->
            val button = dialog.javaClass.getMethod("getButton", Int::class.javaPrimitiveType)
                .invoke(dialog, slots[index]) as TextView
            when (action.style) {
                LiquidAlertActionStyle.Primary -> {
                    source.applyCapsuleAccent(button, primaryColor())
                    button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(0x61FFFFFF, Color.WHITE)))
                }
                LiquidAlertActionStyle.Destructive -> button.setTextColor(source.color(context, "couiColorError"))
                // Slot defaults may carry COUI's warning color. Semantics, not slot position,
                // decide the foreground: sharing ICS and cancellation are ordinary actions.
                LiquidAlertActionStyle.Secondary, LiquidAlertActionStyle.Cancel -> button.setTextColor(
                    ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(source.color(context, "couiColorLabelTertiary").defaultColor,
                            source.color(context, "couiColorLabelPrimary").defaultColor)))
            }
            if (action.style != LiquidAlertActionStyle.Primary) source.applyTransparentCapsule(button)
        }
        actionAppearance = appearance
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        dialog.window?.let(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshotRegistry::remove)
        pending = null
        dismissGuard?.dispose()
        dismissGuard = null
        dialog.setOnCancelListener(null)
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        compose?.disposeComposition()
    }
}

/** Preserve native inside touch dispatch; only ask the form owner about outside/back dismissal. */
private class NativeFormDismissGuard(dialog: AndroidDialog, source: ColorOsNativeUi.Source,
    private val onDismissRequest: () -> Unit) {
    private val window = checkNotNull(dialog.window)
    private val delegate = checkNotNull(window.callback)
    private val panelId = source.resource("parentPanel", "id")
    private val bounds = Rect()
    private var disposed = false
    private val touchGate = NativeDialogTouchGate(onDismissRequest)
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private val callback = Proxy.newProxyInstance(delegate.javaClass.classLoader,
        arrayOf(Window.Callback::class.java)) { _, method, args ->
        if (!disposed && method.name == "dispatchTouchEvent") {
            val event = args?.firstOrNull() as? MotionEvent
            val panel = window.decorView.findViewById<View>(panelId)
            if (event != null && panel != null && panel.getGlobalVisibleRect(bounds)) {
                if (touchGate.consume(event, bounds)) return@newProxyInstance true
            }
        }
        try { method.invoke(delegate, *(args ?: emptyArray())) }
        catch (error: InvocationTargetException) { throw error.targetException }
    } as Window.Callback

    init {
        window.callback = callback
        dialog.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onDismissRequest()
                true
            } else false
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = android.window.OnBackInvokedCallback { if (!disposed) onDismissRequest() }.also {
                window.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        }
    }

    fun dispose() {
        disposed = true
        if (window.callback === callback) window.callback = delegate
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let {
            window.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        backCallback = null
    }
}

@Composable
internal fun ColorOsNameDialog(title: String, initialName: String, dark: Boolean, maxLength: Int?,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit): Boolean {
    val native = rememberNativeContext(dark) ?: return false
    var value by remember(initialName) { mutableStateOf(initialName) }
    val edit = remember(native, initialName, maxLength, dark) {
        ColorOsNativeUi.prepare("create a native schedule name field") {
            native.first.updateTheme(native.second, dark)
            (native.first.load("com.coui.appcompat.edittext.COUIEditText").getConstructor(Context::class.java)
                .newInstance(native.second) as EditText).apply {
                hint = "课表名称"
                contentDescription = "课表名称"
                setSingleLine(true)
                maxLength?.let { filters = arrayOf(android.text.InputFilter.LengthFilter(it)) }
                setText(value)
                setSelection(text.length)
            }
        }
    } ?: return false
    val content: @Composable () -> Unit = {
        val current = rememberUpdatedState(value)
        DisposableEffect(edit) {
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.toString() != current.value) value = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            }
            edit.addTextChangedListener(watcher)
            onDispose { edit.removeTextChangedListener(watcher) }
        }
        AndroidView(factory = { edit }, modifier = Modifier.fillMaxWidth())
    }
    return ColorOsAlertDialog(NativeAlertRequest(title, "", listOf(
        LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismiss),
        LiquidAlertAction("完成", LiquidAlertActionStyle.Primary, onClick = { onConfirm(value.trim()) })
    ), dark, onDismiss, 120.dp, false, content))
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
