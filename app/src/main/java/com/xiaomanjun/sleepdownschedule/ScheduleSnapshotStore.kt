package com.xiaomanjun.sleepdownschedule

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Disk is only a second-level visual cache; Room entities remain bitmap-free. */
object ScheduleSnapshotStore {
    // v3 invalidates snapshots produced before the picker moved to an isolated Popup layer.
    private fun directory(context: Context): File = File(context.filesDir, "schedule_snapshots_v3").apply { mkdirs() }

    fun file(context: Context, scheduleId: Int): File = File(directory(context), "schedule-$scheduleId.jpg")

    suspend fun load(context: Context, scheduleId: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file(context, scheduleId).absolutePath) }.getOrNull()
    }

    suspend fun save(context: Context, scheduleId: Int, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            val destination = file(context, scheduleId)
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            FileOutputStream(temporary).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                output.fd.sync()
            }
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    suspend fun delete(context: Context, scheduleId: Int) = withContext(Dispatchers.IO) {
        runCatching { file(context, scheduleId).delete() }
    }

    suspend fun cleanupUnreferenced(context: Context, referencedScheduleIds: Collection<Int>) =
        withContext(Dispatchers.IO) {
            val referenced = referencedScheduleIds.toSet()
            directory(context).listFiles().orEmpty().forEach { snapshot ->
                if (!snapshot.isFile) return@forEach
                val scheduleId = snapshot.name
                    .removePrefix("schedule-")
                    .substringBefore('.')
                    .toIntOrNull()
                if (scheduleId == null || scheduleId !in referenced) {
                    runCatching { snapshot.delete() }
                }
            }
        }

    fun createEmptySchedulePlaceholder(context: Context, width: Int, height: Int, dark: Boolean): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val output = createBitmap(safeWidth, safeHeight)
        val canvas = Canvas(output)
        val wallpaper = BitmapFactory.decodeResource(
            context.resources,
            if (dark) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        )
        if (wallpaper != null) {
            val scale = maxOf(safeWidth.toFloat() / wallpaper.width, safeHeight.toFloat() / wallpaper.height)
            val sourceWidth = (safeWidth / scale).toInt().coerceAtMost(wallpaper.width)
            val sourceHeight = (safeHeight / scale).toInt().coerceAtMost(wallpaper.height)
            val left = ((wallpaper.width - sourceWidth) / 2).coerceAtLeast(0)
            val top = ((wallpaper.height - sourceHeight) / 2).coerceAtLeast(0)
            canvas.drawBitmap(wallpaper, Rect(left, top, left + sourceWidth, top + sourceHeight), Rect(0, 0, safeWidth, safeHeight), null)
        } else {
            canvas.drawColor(if (dark) Color.rgb(8, 8, 10) else Color.rgb(242, 246, 252))
        }
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(if (dark) 92 else 72, 255, 255, 255) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(72, 255, 255, 255); strokeWidth = safeWidth / 360f }
        val margin = safeWidth * 0.045f
        val topBarTop = safeHeight * 0.055f
        canvas.drawRoundRect(RectF(margin, topBarTop, safeWidth - margin, topBarTop + safeHeight * 0.065f), safeWidth * 0.04f, safeWidth * 0.04f, glass)
        val gridTop = safeHeight * 0.20f
        val gridBottom = safeHeight * 0.88f
        canvas.drawRoundRect(RectF(margin, gridTop, safeWidth - margin, gridBottom), safeWidth * 0.035f, safeWidth * 0.035f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(36, 255, 255, 255) })
        repeat(6) { column ->
            val x = margin + (safeWidth - margin * 2) * column / 5f
            canvas.drawLine(x, gridTop, x, gridBottom, line)
        }
        repeat(9) { row ->
            val y = gridTop + (gridBottom - gridTop) * row / 8f
            canvas.drawLine(margin, y, safeWidth - margin, y, line)
        }
        canvas.drawRoundRect(
            RectF(safeWidth * 0.34f, safeHeight * 0.91f, safeWidth * 0.66f, safeHeight * 0.965f),
            safeWidth * 0.04f,
            safeWidth * 0.04f,
            glass
        )
        return output
    }
}
