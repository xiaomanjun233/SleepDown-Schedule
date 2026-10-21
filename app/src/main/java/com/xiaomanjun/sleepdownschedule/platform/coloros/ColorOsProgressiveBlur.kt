package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.graphics.RenderEffect as AndroidRenderEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeProgressiveBlur

/** Same Oplus operator and speed as AppBarBlurHelper, without its page-color drawable. */
@Composable
internal fun rememberColorOsProgressiveBlur(): NativeProgressiveBlur? {
    val host = LocalContext.current
    val source = rememberColorOsSource() ?: return null
    val factory = remember(source) {
        ColorOsNativeUi.prepare("prepare native progressive blur operator") {
            val helper = source.load("com.coui.appcompat.toolbar.AppBarBlurHelper")
            val params = helper.getDeclaredMethod("params").apply { isAccessible = true }
                .invoke(helper.getField("INSTANCE").get(null))
            val speed = params.javaClass.getMethod("getGradientBlurSpeed").invoke(params) as Float
            val create = source.load("com.oplus.graphics.OplusRenderEffect").getMethod("createGradientBlurEffect",
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            data class Key(val start: Float, val end: Float, val vertical: Boolean)
            val cache = object : LinkedHashMap<Key, RenderEffect>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RenderEffect>?) = size > 32
            }
            NativeProgressiveBlur { start, end, vertical ->
                if (start <= 0f && end <= 0f) null else cache.getOrPut(Key(start, end, vertical)) {
                    (create.invoke(null, start, end, vertical, speed, 0, 0, 0) as AndroidRenderEffect)
                        .asComposeRenderEffect()
                }
            }.also { it.create(1f, 0f, true) }
        }
    } ?: return null
    var enabled by remember(source) { mutableStateOf(source.blurEnabled()) }
    DisposableEffect(source) {
        val managerType = source.load("com.coui.appcompat.COUIMaterialStateManager")
        val manager = managerType.getField("INSTANCE").get(null)
        val listenerType = source.load("com.coui.appcompat.OnMaterialStateChangeListener")
        val listener = source.listener(listenerType.name) { name, _ ->
            if (name == "onMaterialStateChange") enabled = source.blurEnabled()
            null
        }
        managerType.getMethod("addOnMaterialStateChangedListener", listenerType).invoke(manager, listener)
        onDispose { managerType.getMethod("removeOnMaterialStateChangedListener", listenerType).invoke(manager, listener) }
    }
    val disabled = remember { NativeProgressiveBlur { _, _, _ -> null } }
    return if (enabled) factory else disabled
}
