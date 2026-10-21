package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.graphics.BlendMode
import androidx.compose.foundation.layout.*

internal fun ColorOsNativeUi.Source.weatherButtonBlurParams(
    context: Context, radiusScale: Float = 1f, tintAlphaScale: Float = 1f
): Any = scaledBlurParams(context, "TYPE_CONTENT_TRANSPARENT_BUTTON", radiusScale, tintAlphaScale)

internal fun ColorOsNativeUi.Source.dockBlurParams(context: Context, neutral: Boolean): Any =
    scaledBlurParams(context, if (neutral) "TYPE_CONTENT_TRANSPARENT_BUTTON" else "TYPE_FRAMEWORK_BOTTOM_BAR", 1f)

/** Embed the alert's resource recipe in this window, without a second window-behind sampler. */
internal fun ColorOsNativeUi.Source.dialogBlurParams(context: Context): Any {
    val base = load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
    val preset = weatherButtonBlurParams(context)
    val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val theme = if (dark) "dark" else "light"
    fun layer(name: String) = "#" + Integer.toHexString(
        context.getColor(resource("coui_dialog_list_${name}_blur_$theme", "color"))).padStart(8, '0')
    val radius = context.resources.getInteger(resource("coui_dialog_blur_radius", "integer")).toFloat()
    return load("com.coui.appcompat.COUIMaterialBlurEffect\$BlurEffectParams")
        .getConstructor(Float::class.javaPrimitiveType, String::class.java, BlendMode::class.java,
            String::class.java, BlendMode::class.java)
        .newInstance(radius, layer("blend"), base.getMethod("getBlendModeLayer1").invoke(preset),
            layer("mix"), base.getMethod("getBlendModeLayer2").invoke(preset))
}

/** Keep COUI's color layers and material delegate; only lighten its second blur pass. */
internal fun ColorOsNativeUi.Source.toolbarButtonBlurParams(
    context: Context, neutral: Boolean, backdropBlurred: Boolean
): Any? = when {
    backdropBlurred -> scaledBlurParams(context,
        if (neutral) "TYPE_CONTENT_TRANSPARENT_BUTTON" else "TYPE_FRAMEWORK_TOP_BAR_BLUR", 0.35f)
    neutral -> weatherButtonBlurParams(context)
    else -> null
}

/** Capsules use the same blur budget as the return button over the page's blurred surface. */
internal fun ColorOsNativeUi.Source.capsuleBlurParams(context: Context): Any =
    scaledBlurParams(context, "TYPE_FRAMEWORK_TOP_BAR_BLUR", 0.35f)

internal fun ColorOsNativeUi.Source.capsuleBlurRadius(context: Context): Float =
    load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
        .getMethod("getBlurRadius", Context::class.java).invoke(capsuleBlurParams(context), context) as Float

private fun ColorOsNativeUi.Source.scaledBlurParams(
    context: Context, typeName: String, radiusScale: Float, tintAlphaScale: Float = 1f
): Any {
    val effect = load("com.coui.appcompat.COUIMaterialEffect")
    val type = load("com.coui.appcompat.COUIMaterialBlurEffect\$BlurEffectType")
    val base = load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
    val preset = effect.getMethod("getBlurParamsByType", Context::class.java, type)
        .invoke(null, context, type.getField(typeName).get(null))
    if (radiusScale == 1f && tintAlphaScale == 1f) return preset
    fun color(name: String): String {
        val original = base.getMethod(name, Context::class.java).invoke(preset, context) as Int
        val alpha = (android.graphics.Color.alpha(original) * tintAlphaScale).toInt().coerceIn(0, 255)
        return "#" + Integer.toHexString((original and 0x00FFFFFF) or (alpha shl 24)).padStart(8, '0')
    }
    val radius = base.getMethod("getBlurRadius", Context::class.java).invoke(preset, context) as Float
    return load("com.coui.appcompat.COUIMaterialBlurEffect\$BlurEffectParams")
        .getConstructor(Float::class.javaPrimitiveType, String::class.java, BlendMode::class.java,
            String::class.java, BlendMode::class.java)
        .newInstance(radius * radiusScale, color("getColorLayer1"),
            base.getMethod("getBlendModeLayer1").invoke(preset), color("getColorLayer2"),
            base.getMethod("getBlendModeLayer2").invoke(preset))
}

/** Same color-layer recipe as COUIFloatingButton.getNonThemeFabColor, shared by colored buttons. */
internal fun ColorOsNativeUi.Source.accentButtonBlurParams(
    context: Context, accent: Int, enabled: Boolean = true, radiusScale: Float = 0.85f,
    radiusOverride: Float? = null
): Any {
    val theme = load("com.coui.appcompat.effect.COUIMaterialThemeBlurEffect")
    val companion = theme.getField("Companion").get(null)
    val type = load("${theme.name}\$ThemeBlurEffectType")
    val fabPreset = companion.javaClass.getMethod("getBlurEffectParamsByType", Context::class.java, type)
        .invoke(companion, context, type.getField(if (enabled) "TYPE_FRAMEWORK_FAB"
            else "TYPE_CONTENT_DISABLED_PRIMARY_BUTTON").get(null))
    // Disabled controls retain COUI's disabled treatment instead of an opaque active accent.
    if (!enabled) return fabPreset
    val base = load("com.coui.appcompat.COUIMaterialBlurEffect\$BaseBlurEffectParams")
    val radius = base.getMethod("getBlurRadius", Context::class.java).invoke(fabPreset, context) as Float
    val themeParams = load("com.coui.appcompat.effect.COUIMaterialThemeBlurEffect\$Companion\$ThemeBlurEffectParams")
    return themeParams.getConstructor(Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, BlendMode::class.java, Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, BlendMode::class.java, Int::class.javaPrimitiveType)
        .newInstance(radiusOverride ?: (radius * radiusScale), accent or (0xFF shl 24),
            false, base.getMethod("getBlendModeLayer1").invoke(fabPreset),
            base.getMethod("getColorLayer2", Context::class.java).invoke(fabPreset, context), false,
            base.getMethod("getBlendModeLayer2").invoke(fabPreset), 255)
}
