package com.xiaomanjun.sleepdownschedule.feature.importing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/** Samples only the visible page; no sampled webpage pixels leave this in-memory buffer. */
internal class WebTopEdgeSampler(
    private val sampleHeightPx: Int,
    private val visibleWebView: () -> WebView?,
    private val onColor: (Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val bitmap = Bitmap.createBitmap(64, 24, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val pixels = IntArray(64 * 24)
    private val extractor = WebThemeColorExtractor()
    private var pending = false
    private var disposed = false
    private val sample = Runnable {
        pending = false
        val target = visibleWebView()
        if (!disposed && target != null && target.isAttachedToWindow && target.isShown &&
            target.width > 0 && target.height > 0
        ) {
            val bandHeight = sampleHeightPx.coerceIn(1, target.height)
            bitmap.eraseColor(Color.TRANSPARENT)
            val checkpoint = canvas.save()
            try {
                canvas.scale(bitmap.width.toFloat() / target.width, bitmap.height.toFloat() / bandHeight)
                target.draw(canvas)
            } finally {
                canvas.restoreToCount(checkpoint)
            }
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            extractor.extract(pixels)?.let(onColor)
        }
    }

    fun schedule() {
        // Coalesce instead of postponing on every scroll event: continuous scrolling still samples.
        if (disposed || pending) return
        pending = true
        handler.postDelayed(sample, 96L)
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacks(sample)
        bitmap.recycle()
    }
}
