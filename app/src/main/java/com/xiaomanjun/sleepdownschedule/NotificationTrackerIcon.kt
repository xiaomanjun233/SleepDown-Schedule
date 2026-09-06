package com.xiaomanjun.sleepdownschedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon

/**
 * The Android 16 ProgressStyle default tracker renders as a paper-plane glyph; a plain
 * white dot reads better on the blue progress bar used by our live updates.
 */
internal fun whiteDotProgressTrackerIcon(context: Context): Icon {
    cachedWhiteDotIcon?.let { return it }
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawCircle(
        size / 2f,
        size / 2f,
        size * 0.30f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    )
    return Icon.createWithBitmap(bitmap).also { cachedWhiteDotIcon = it }
}

private var cachedWhiteDotIcon: Icon? = null
