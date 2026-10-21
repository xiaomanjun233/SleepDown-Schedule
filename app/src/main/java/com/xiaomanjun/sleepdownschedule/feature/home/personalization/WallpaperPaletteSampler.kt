package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
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
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogHostSize
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.wallpaper.FocusCroppedWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPreviewOrientation
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadWallpaperBitmap
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperCropState
import com.xiaomanjun.sleepdownschedule.feature.home.day.sampleCroppedBitmapColor
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperPaletteSampler(
    mode: CourseCardColorMode,
    colors: List<Long>,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onColorChange: (Int, Long) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowHeight = with(density) {
        (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogHostSize.current
            ?: LocalWindowInfo.current.containerSize).height.toDp()
    }
    // Use the page's available height, never the animated wrap-content dialog height. Otherwise
    // each resize changes the preview, triggers new sampling, and starts another dialog resize.
    // Miuix caps a centered dialog at two thirds of the current window. Reserve the measured
    // title, copy, actions, shell insets and compact gaps first, then let the wallpaper preview
    // consume only the remaining space. This keeps both actions inside the shell on phones with
    // large display or font scaling while retaining a useful sampling area on taller screens.
    val previewHeight = (windowHeight * (2f / 3f) - 210.dp).coerceIn(180.dp, 300.dp)
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
        val safePoint = Offset(point.x.coerceIn(0.04f, 0.96f), point.y.coerceIn(0.04f, 0.96f))
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
        val source = bitmap ?: return@LaunchedEffect
        if (previewSize.width <= 0 || previewSize.height <= 0) return@LaunchedEffect
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
                .width(210.dp)
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
            samplePoints.forEachIndexed { index, point ->
                val markerColor = ComposeColor(colors.getOrElse(index) { colors.firstOrNull() ?: 0xFFD6E9FFL }.toInt())
                Surface(
                    modifier = Modifier
                        .offset {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickSheetLiquidAction(
            label = "取消",
            enabled = true,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.weight(1f),
            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
            onClick = onCancel
        )
        QuickSheetLiquidAction(
            label = "确认",
            enabled = bitmap != null,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.weight(1f),
            primary = true,
            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
            onClick = onConfirm
        )
    }
}
