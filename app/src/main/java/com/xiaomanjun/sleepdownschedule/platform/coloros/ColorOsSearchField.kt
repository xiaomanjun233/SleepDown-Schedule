package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val SearchBar = "com.coui.appcompat.searchview.COUISearchBar"

/** COUI owns focus expansion, clear, cancel, press, spotlight and material; host owns IME lift. */
@Composable
internal fun ColorOsSchoolSearchField(
    value: String, onValueChange: (String) -> Unit, dark: Boolean,
    bottomOffset: Dp, entranceProgress: Float,
    largeScreen: Boolean, modifier: Modifier
): Boolean {
    val native = rememberNativeContext(dark, retainOnThemeChange = true) ?: return false
    val source = native.first
    val context = native.second
    val callback = rememberUpdatedState(onValueChange)
    val currentQuery = rememberUpdatedState(value)
    var editing by remember { mutableStateOf(false) }
    val search = remember(native) {
        ColorOsNativeUi.prepare("create the native school search bar") {
            val type = source.load(SearchBar)
            val view = type.getConstructor(Context::class.java).newInstance(context) as ViewGroup
            source.bindFollowLightIntensity(view)
            type.getMethod("setUseMaterialEffectAttr", Int::class.javaPrimitiveType)
                .invoke(view, type.getField("FOLLOW_SYSTEM").getInt(null))
            type.getMethod("setMaterialEffectType", Int::class.javaPrimitiveType).invoke(view,
                source.load("com.coui.appcompat.toolbar.COUIMaterialEffectType").getField("TYPE_DEFAULT_BLUR").getInt(null))
            type.getMethod("setIsDrawableMaterialEffect", Boolean::class.javaPrimitiveType).invoke(view, false)
            // This flag also gates openSoftInput(), not just animation. Keep native IME ownership.
            type.getMethod("setInputMethodAnimationEnabled", Boolean::class.javaPrimitiveType).invoke(view, true)
            val edit = type.getMethod("getSearchEditText").invoke(view) as EditText
            edit.hint = "搜索学校或教务工具…"
            edit.contentDescription = "搜索学校或教务工具"
            edit.imeOptions = EditorInfo.IME_ACTION_SEARCH
            val stateType = "$SearchBar\$OnStateChangeListener"
            val listener = source.listener(stateType) { name, args ->
                if (name == "onStateChange") editing = args[1] == type.getField("STATE_EDIT").getInt(null)
                null
            }
            type.getMethod("addOnStateChangeListener", source.load(stateType)).invoke(view, listener)
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    if (query != currentQuery.value) callback.value(query)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
            NativeSearch(view, edit, listener, watcher).also { control ->
                // COUISearchBar forwards this listener to its search capsule. It does not bind
                // an expand click itself; changeState owns both native animation and IME focus.
                view.setOnClickListener { control.open() }
                edit.setText(value)
                edit.addTextChangedListener(watcher)
                edit.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEARCH) { control.finishQuery(); true } else false
                }
                (type.getMethod("getFunctionalButton").invoke(view) as TextView).setOnClickListener {
                    if (currentQuery.value.isBlank()) control.close() else control.finishQuery()
                }
            }
        }
    } ?: return false
    val density = LocalDensity.current
    val height = with(density) {
        context.resources.getDimensionPixelSize(source.resource("coui_search_bar_height", "dimen")).toDp()
    }
    BackHandler(enabled = editing) { search.close() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // COUI already reserves responsive horizontal padding (16dp on compact windows).
        // Give it the full layout envelope instead of subtracting the same inset a second time.
        val width = if (largeScreen) (maxWidth * 0.5f).coerceAtLeast(260.dp).coerceAtMost(maxWidth) else maxWidth
        AndroidView(factory = { search.view }, modifier = Modifier.align(Alignment.BottomCenter)
            .width(width).height(height)
            .offset(y = -bottomOffset + 52.dp * (1f - entranceProgress.coerceIn(0f, 1f)))
            .graphicsLayer {
                // View-level COUI blur must sample the live underlay throughout the reveal.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = entranceProgress.coerceIn(0f, 1f)
            }, update = {
                if (source.updateTheme(context, dark)) it.javaClass.getMethod("refresh").invoke(it)
                search.setQuery(value)
                search.setActionLabel(if (value.isBlank()) "取消" else "搜索")
            })
    }
    DisposableEffect(search) { onDispose { search.dispose() } }
    return true
}

private class NativeSearch(val view: ViewGroup, val edit: EditText, val listener: Any, val watcher: TextWatcher) {
    private var actionLabel: String? = null
    fun setActionLabel(label: String) {
        if (actionLabel == label) return
        view.javaClass.getMethod("setFunctionalButtonText", String::class.java).invoke(view, label)
        actionLabel = label
    }
    fun open() {
        val type = view.javaClass
        if (type.getMethod("getSearchState").invoke(view) == type.getField("STATE_EDIT").getInt(null)) {
            type.getMethod("openSoftInput", Boolean::class.javaPrimitiveType).invoke(view, true)
        } else {
            type.getMethod("changeState", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(view, type.getField("STATE_EDIT").getInt(null), true)
        }
    }

    fun setQuery(query: String) {
        if (edit.text.toString() == query) return
        edit.removeTextChangedListener(watcher)
        edit.setText(query)
        edit.setSelection(query.length)
        edit.addTextChangedListener(watcher)
    }

    fun close() {
        view.javaClass.getMethod("changeState", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(view, 0, true)
        view.javaClass.getMethod("openSoftInput", Boolean::class.javaPrimitiveType).invoke(view, false)
        edit.clearFocus()
    }

    fun finishQuery() {
        // Returning to COUI's normal state clears the query. Search submits only dismiss the IME.
        view.javaClass.getMethod("openSoftInput", Boolean::class.javaPrimitiveType).invoke(view, false)
        edit.clearFocus()
    }

    fun dispose() {
        view.setOnClickListener(null)
        edit.removeTextChangedListener(watcher)
        edit.setOnEditorActionListener(null)
        view.javaClass.getMethod("removeOnStateChangeListener", listener.javaClass.interfaces.first()).invoke(view, listener)
        (view.javaClass.getMethod("getFunctionalButton").invoke(view) as TextView).setOnClickListener(null)
    }
}
