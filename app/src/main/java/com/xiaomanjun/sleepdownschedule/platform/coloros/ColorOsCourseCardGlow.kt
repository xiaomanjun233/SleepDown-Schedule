package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil

/** One card's native caustic. Its padded host owns the projection, never the scrolling page. */
internal class ColorOsCourseCardGlow(context: Context, source: ColorOsNativeUi.Source) {
    private val effect = source.load("com.coui.appcompat.COUIMaterialEffect")
    private val setElevation = source.load("com.coui.appcompat.uiutil.ShadowUtils")
        .getMethod("setElevationToView", View::class.java, Int::class.javaPrimitiveType)
    private val clipShadow = source.load("com.coui.appcompat.uiutil.UIUtil")
        .getMethod("setViewShadowClippingEnabled", View::class.java, Boolean::class.javaPrimitiveType)
    private val setColor = effect.getMethod("setOutlineCausticShadowColor", View::class.java, Int::class.javaPrimitiveType)
    private val setLayout = effect.getMethod("setOutlineCausticShadowLayout", View::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val setParams = effect.getMethod("setOutlineCausticShadowParams", View::class.java, Float::class.javaPrimitiveType)
    private val clear = effect.getMethod("clearCausticShadow", View::class.java)
    private val color = context.getColor(source.resource("coui_caustic_color_2", "color"))
    private fun dimension(name: String) = context.resources.getDimensionPixelOffset(source.resource(name, "dimen"))
    private val left = dimension("coui_caustic_layout_l_level4")
    private val top = dimension("coui_caustic_layout_t_level4")
    private val right = dimension("coui_caustic_layout_r_level4")
    private val bottom = dimension("coui_caustic_layout_b_level4")
    private val sdf = dimension("coui_caustic_layout_sdf_level4").toFloat()
    // Reserve signed native layout offsets and the SDF feather without changing card layout.
    val outsets = Rect(abs(left) + ceil(sdf).toInt(), abs(top) + ceil(sdf).toInt(),
        abs(right) + ceil(sdf).toInt(), abs(bottom) + ceil(sdf).toInt())
    private var applied = false

    fun update(view: View, enabled: Boolean) {
        if (applied == enabled) return
        if (enabled) {
            setElevation.invoke(null, view, 7)
            // The preset also installs a normal black elevation shadow. Keep only the caustic
            // color so a clipped ordinary shadow cannot become the list's dark border.
            view.outlineAmbientShadowColor = Color.TRANSPARENT
            view.outlineSpotShadowColor = Color.TRANSPARENT
            clipShadow.invoke(null, view, true)
            setColor.invoke(null, view, color)
            setLayout.invoke(null, view, left, top, right, bottom)
            setParams.invoke(null, view, sdf)
        } else {
            clear.invoke(null, view)
            view.elevation = 0f
        }
        applied = enabled
    }
}
