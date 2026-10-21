package com.xiaomanjun.sleepdownschedule.feature.home.day

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.FocusCroppedWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPreviewOrientation
import com.xiaomanjun.sleepdownschedule.core.wallpaper.calculateFocusCropRect
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadWallpaperBitmap
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperCropState
import com.xiaomanjun.sleepdownschedule.feature.course.editor.NormalizedDialogHeader
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperColorSamplerScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val config = state.config
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        config.wallpaperUri,
        config.defaultWallpaperStyle,
        useDarkDefaultWallpaper
    ) {
        value = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)
        }
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var sampledColor by remember(config.cardColorArgb) { mutableStateOf(config.cardColorArgb) }
    val sampledComposeColor = ComposeColor(sampledColor.toInt())
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NormalizedDialogHeader(
            title = "壁纸取色",
            onCancel = onCancel,
            onSave = { onSelected(sampledColor) },
            backdrop = backdrop,
            config = config
        )
        Text(
            "点击壁纸预览中的位置，课程卡片会使用该处颜色。",
            color = glassForegroundColor(config).copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedRectangle(22.dp))
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                .onSizeChanged { previewSize = it }
                .pointerInput(bitmap, previewSize) {
                    detectTapGestures { tap ->
                        val sourceBitmap = bitmap ?: return@detectTapGestures
                        val orientation = if (previewSize.height >= previewSize.width) WallpaperPreviewOrientation.Portrait else WallpaperPreviewOrientation.Landscape
                        val cropState = if (!config.wallpaperUri.isNullOrBlank()) config.wallpaperCropState(orientation) else WallpaperCropState()
                        val color = sampleCroppedBitmapColor(sourceBitmap, previewSize, tap.x, tap.y, cropState)
                        if (color != null) sampledColor = color
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val wallpaperBitmap = bitmap
            if (wallpaperBitmap != null) {
                FocusCroppedWallpaper(
                    bitmap = wallpaperBitmap,
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    useSavedCrop = !config.wallpaperUri.isNullOrBlank(),
                    orientation = if (previewSize.width > previewSize.height) WallpaperPreviewOrientation.Landscape
                        else WallpaperPreviewOrientation.Portrait
                )
            } else {
                Text("当前没有可取色的壁纸", color = glassForegroundColor(config))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(46.dp),
                shape = Capsule(),
                color = sampledComposeColor,
                border = BorderStroke(2.dp, readableOn(sampledComposeColor).copy(alpha = 0.72f))
            ) {}
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0xFFD6E9FF, 0xFFBFE0FF, 0xFF9ED4FF, 0xFFFFE1E8,
                0xFFFFC4D6, 0xFFD8F3DC, 0xFFB7E4C7, 0xFFFFF0C2,
                0xFFFFD166, 0xFFE8D7FF, 0xFFD7C0FF, 0xFFE8EAED
            ).forEach { color ->
                val selected = sampledColor == color
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = Capsule(),
                    color = ComposeColor(color.toInt()),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                    ),
                    onClick = { sampledColor = color }
                ) {}
            }
        }
    }
}

fun sampleCroppedBitmapColor(
    bitmap: Bitmap,
    previewSize: IntSize,
    tapX: Float,
    tapY: Float,
    cropState: WallpaperCropState
): Long? {
    if (previewSize.width <= 0 || previewSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return null
    val rect = calculateFocusCropRect(
        bitmap.width,
        bitmap.height,
        previewSize.width.toFloat(),
        previewSize.height.toFloat(),
        cropState
    )
    if (rect == androidx.compose.ui.geometry.Rect.Zero || rect.width <= 0f || rect.height <= 0f) return null
    val bitmapX = ((tapX - rect.left) * bitmap.width / rect.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val bitmapY = ((tapY - rect.top) * bitmap.height / rect.height).roundToInt().coerceIn(0, bitmap.height - 1)
    return bitmap[bitmapX, bitmapY].toLong() and 0xFFFFFFFFL
}
