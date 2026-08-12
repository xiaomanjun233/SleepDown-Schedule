package com.xiaomanjun.sleepdownschedule

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

internal fun shouldUsePlatformBlurEffect(
    radiusX: Float,
    radiusY: Float,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean = sdkInt >= Build.VERSION_CODES.S && radiusX > 0.01f && radiusY > 0.01f

internal fun platformBlurRenderEffect(
    radiusX: Float,
    radiusY: Float = radiusX
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    if (radiusX <= 0.01f || radiusY <= 0.01f) return null
    return Api31RenderEffect.createBlur(radiusX, radiusY)
}

private object Api31RenderEffect {
    @RequiresApi(Build.VERSION_CODES.S)
    fun createBlur(radiusX: Float, radiusY: Float): RenderEffect =
        AndroidRenderEffect.createBlurEffect(
            radiusX,
            radiusY,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
}
