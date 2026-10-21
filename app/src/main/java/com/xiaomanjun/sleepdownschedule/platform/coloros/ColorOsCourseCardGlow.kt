package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.view.View

/** Dock caustic recipe, owned by one course surface and cleared with its native material. */
internal class ColorOsCourseCardGlow(context: Context, source: ColorOsNativeUi.Source) {
    private val effect = source.load("com.coui.appcompat.COUIMaterialEffect")
    private val setElevation = source.load("com.coui.appcompat.uiutil.ShadowUtils")
        .getMethod("setElevationToView", View::class.java, Int::class.javaPrimitiveType)
    private val setColor = effect.getMethod("setOutlineCausticShadowColor", View::class.java, Int::class.javaPrimitiveType)
    private val setLayout = effect.getMethod("setOutlineCausticShadowLayout", View::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val setParams = effect.getMethod("setOutlineCausticShadowParams", View::class.java, Float::class.javaPrimitiveType)
    private val clear = effect.getMethod("clearCausticShadow", View::class.java)
    private val color = context.getColor(source.resource("coui_caustic_color_2", "color"))
    private fun dimension(context: Context, source: ColorOsNativeUi.Source, name: String): Int =
        context.resources.getDimensionPixelOffset(source.resource(name, "dimen"))
    private val left = dimension(context, source, "coui_caustic_layout_l_level4")
    private val top = dimension(context, source, "coui_caustic_layout_t_level4")
    private val right = dimension(context, source, "coui_caustic_layout_r_level4")
    private val bottom = dimension(context, source, "coui_caustic_layout_b_level4")
    private val sdf = dimension(context, source, "coui_caustic_layout_sdf_level4").toFloat()
    private var applied = false

    fun update(view: View, enabled: Boolean) {
        if (applied == enabled) return
        if (enabled) {
            setElevation.invoke(null, view, 7)
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
