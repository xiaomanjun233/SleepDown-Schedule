package com.itos.coui.switch

import android.content.res.Resources
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import com.itos.coui.R

/** 使用原厂 switch_loading 像素；每个开关持有独立 Drawable，避免 alpha/边界串扰。 */
internal class CouiSwitchLoadingGlyph(resources: Resources) {
    private val drawable = checkNotNull(ResourcesCompat.getDrawable(resources, R.drawable.coui_switch_loading, null)).mutate()

    fun draw(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        scale: Float, alpha: Float, rotation: Float) {
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val save = canvas.save()
        // drawLoading 在浮点滑块中心缩放/旋转，Drawable 的四条边却分别截断为整数。
        canvas.scale(scale, scale, centerX, centerY)
        canvas.rotate(rotation, centerX, centerY)
        drawable.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        drawable.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        drawable.draw(canvas)
        canvas.restoreToCount(save)
    }
}
