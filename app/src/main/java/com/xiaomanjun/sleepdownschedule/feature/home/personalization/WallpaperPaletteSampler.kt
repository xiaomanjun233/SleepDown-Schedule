package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalControlDialogHostSize
import com.xiaomanjun.sleepdownschedule.core.wallpaper.FocusCroppedWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPreviewOrientation
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadWallpaperBitmap
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperCropState
import com.xiaomanjun.sleepdownschedule.feature.home.day.sampleCroppedBitmapColor
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperPaletteSampler(
    mode: CourseCardColorMode,
    colors: List<Long>,
    config: ScheduleConfigEntity,
    onColorChange: (Int, Long) -> Unit,
    onWallpaperReady: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowHeight = with(density) {
        (LocalControlDialogHostSize.current ?: LocalWindowInfo.current.containerSize).height.toDp()
    }
    // Use the host height rather than the animated dialog height. The fixed footer stays visible
    // when the body needs to scroll on a short window.
    val previewHeight = (windowHeight * 0.62f - 140.dp).coerceIn(128.dp, 320.dp)
    val previewWidth = previewHeight * (9f / 16f)
    val foreground = appPanelForegroundColor(config)
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        config.wallpaperUri,
        config.defaultWallpaperStyle,
        useDarkDefaultWallpaper
    ) {
        value = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)
        }
    }
    val pointCount = if (mode == CourseCardColorMode.COLORFUL) 4 else 1
    var samplePoints by remember(mode) {
        mutableStateOf(
            if (pointCount == 1) {
                listOf(Offset(0.50f, 0.50f))
            } else {
                listOf(
                    Offset(0.28f, 0.28f),
                    Offset(0.72f, 0.30f),
                    Offset(0.32f, 0.72f),
                    Offset(0.72f, 0.74f)
                )
            }
        )
    }
    var selectedPoint by remember(mode) { mutableIntStateOf(0) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    fun updatePoint(index: Int, point: Offset) {
        if (previewSize.width <= 0 || previewSize.height <= 0) return
        val radius = with(density) { 18.dp.toPx() }
        val insetX = (radius / previewSize.width).coerceAtMost(.5f)
        val insetY = (radius / previewSize.height).coerceAtMost(.5f)
        val safePoint = Offset(point.x.coerceIn(insetX, 1f - insetX), point.y.coerceIn(insetY, 1f - insetY))
        samplePoints = samplePoints.toMutableList().also { it[index] = safePoint }
        val source = bitmap ?: return
        val cropState = if (!config.wallpaperUri.isNullOrBlank()) {
            config.wallpaperCropState(WallpaperPreviewOrientation.Portrait)
        } else {
            WallpaperCropState()
        }
        sampleCroppedBitmapColor(
            source,
            previewSize,
            safePoint.x * previewSize.width,
            safePoint.y * previewSize.height,
            cropState
        )?.let { onColorChange(index, it) }
    }

    LaunchedEffect(bitmap, previewSize, mode) {
        val source = bitmap
        if (source == null || previewSize.width <= 0 || previewSize.height <= 0) {
            onWallpaperReady(false)
            return@LaunchedEffect
        }
        val cropState = if (!config.wallpaperUri.isNullOrBlank()) {
            config.wallpaperCropState(WallpaperPreviewOrientation.Portrait)
        } else {
            WallpaperCropState()
        }
        samplePoints.take(pointCount).forEachIndexed { index, point ->
            sampleCroppedBitmapColor(
                source,
                previewSize,
                point.x * previewSize.width,
                point.y * previewSize.height,
                cropState
            )?.let { onColorChange(index, it) }
        }
        onWallpaperReady(true)
    }

    Text(
        text = if (mode == CourseCardColorMode.COLORFUL) {
            "拖动圆点，为彩色组合提取四个颜色"
        } else if (mode == CourseCardColorMode.GRADIENT) {
            "拖动圆点，提取渐变色系的基准色"
        } else {
            "拖动圆点，提取课程卡片颜色"
        },
        color = foreground.copy(alpha = 0.70f),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedRectangle(24.dp))
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF15171C) else ComposeColor(0xFFF1F3F8))
                .onSizeChanged { previewSize = it }
                .pointerInput(bitmap, previewSize, selectedPoint) {
                    detectTapGestures { tap ->
                        if (previewSize.width > 0 && previewSize.height > 0) {
                            updatePoint(
                                selectedPoint,
                                Offset(tap.x / previewSize.width, tap.y / previewSize.height)
                            )
                        }
                    }
                }
        ) {
            bitmap?.let { wallpaper ->
                FocusCroppedWallpaper(
                    bitmap = wallpaper,
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    useSavedCrop = !config.wallpaperUri.isNullOrBlank(),
                    orientation = WallpaperPreviewOrientation.Portrait
                )
            } ?: Text(
                text = "当前没有可取色的壁纸",
                color = foreground,
                modifier = Modifier.align(Alignment.Center)
            )
            if (bitmap != null) samplePoints.forEachIndexed { index, point ->
                val markerColor = ComposeColor(colors.getOrElse(index) { colors.firstOrNull() ?: 0xFFD6E9FFL }.toInt())
                Surface(
                    modifier = Modifier
                        .absoluteOffset {
                            val radiusPx = with(density) { 18.dp.roundToPx() }
                            IntOffset(
                                (point.x * previewSize.width).roundToInt() - radiusPx,
                                (point.y * previewSize.height).roundToInt() - radiusPx
                            )
                        }
                        .size(36.dp)
                        .pointerInput(index, previewSize) {
                            detectDragGestures(
                                onDragStart = { selectedPoint = index },
                                onDrag = { _, dragAmount ->
                                    if (previewSize.width > 0 && previewSize.height > 0) {
                                        selectedPoint = index
                                        val current = samplePoints[index]
                                        updatePoint(
                                            index,
                                            Offset(
                                                current.x + dragAmount.x / previewSize.width,
                                                current.y + dragAmount.y / previewSize.height
                                            )
                                        )
                                    }
                                }
                            )
                        },
                    shape = Capsule(),
                    color = markerColor,
                    border = BorderStroke(
                        if (selectedPoint == index) 3.dp else 2.dp,
                        ComposeColor.White.copy(alpha = 0.94f)
                    ),
                    onClick = { selectedPoint = index }
                ) {}
            }
        }
    }
}
