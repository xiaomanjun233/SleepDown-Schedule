package com.example.courseschedule

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun WallpaperEditorDialog(
    uri: Uri,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onApply: (ScheduleConfigEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriText = remember(uri) { uri.toString() }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var sourceSize by remember(uri) { mutableStateOf<WallpaperSourceSize?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadSampledBitmap(context.applicationContext, uri, maxDimension = 1600) }
    }
    LaunchedEffect(uri) {
        sourceSize = withContext(Dispatchers.IO) { readWallpaperSourceSize(context.applicationContext, uri) }
    }
    val initialPortrait = remember(uriText, config.wallpaperUri) {
        if (config.wallpaperUri == uriText) config.wallpaperCropState(WallpaperPreviewOrientation.Portrait) else WallpaperCropState()
    }
    val initialLandscape = remember(uriText, config.wallpaperUri) {
        if (config.wallpaperUri == uriText) config.wallpaperCropState(WallpaperPreviewOrientation.Landscape) else WallpaperCropState()
    }
    var selectedOrientation by remember { mutableStateOf(WallpaperPreviewOrientation.Portrait) }
    var portraitCrop by remember(uriText) { mutableStateOf(initialPortrait) }
    var landscapeCrop by remember(uriText) { mutableStateOf(initialLandscape) }
    val activeCrop = if (selectedOrientation == WallpaperPreviewOrientation.Portrait) portraitCrop else landscapeCrop
    fun updateActiveCrop(next: WallpaperCropState) {
        if (selectedOrientation == WallpaperPreviewOrientation.Portrait) portraitCrop = next else landscapeCrop = next
    }
    fun applyWallpaper() {
        scope.launch {
            val appContext = context.applicationContext
            val savedUri = withContext(Dispatchers.IO) { persistWallpaperSource(appContext, uri) ?: uri }
            val savedUriText = savedUri.toString()
            val savedSourceSize = sourceSize ?: withContext(Dispatchers.IO) { readWallpaperSourceSize(appContext, savedUri) }
            val lightText = withContext(Dispatchers.IO) { wallpaperPrefersLightText(appContext, savedUriText) }
            onApply(
                config.copy(
                    wallpaperUri = savedUriText,
                    wallpaperPortraitCenterX = portraitCrop.centerX,
                    wallpaperPortraitCenterY = portraitCrop.centerY,
                    wallpaperPortraitScale = portraitCrop.scale,
                    wallpaperLandscapeCenterX = landscapeCrop.centerX,
                    wallpaperLandscapeCenterY = landscapeCrop.centerY,
                    wallpaperLandscapeScale = landscapeCrop.scale,
                    wallpaperSourceWidth = savedSourceSize?.width ?: bitmap?.width,
                    wallpaperSourceHeight = savedSourceSize?.height ?: bitmap?.height,
                    homeTextLight = lightText
                )
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NormalizedDialogHeader(
            title = "设置首页壁纸",
            onCancel = onCancel,
            onSave = ::applyWallpaper,
            backdrop = backdrop,
            config = config
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            DialogLiquidButton(
                backdrop = backdrop,
                label = "竖屏显示",
                onClick = { selectedOrientation = WallpaperPreviewOrientation.Portrait },
                modifier = Modifier.weight(1f),
                role = if (selectedOrientation == WallpaperPreviewOrientation.Portrait) DialogButtonRole.Confirm else DialogButtonRole.Neutral
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = "横屏显示",
                onClick = { selectedOrientation = WallpaperPreviewOrientation.Landscape },
                modifier = Modifier.weight(1f),
                role = if (selectedOrientation == WallpaperPreviewOrientation.Landscape) DialogButtonRole.Confirm else DialogButtonRole.Neutral
            )
        }
        WallpaperCropPreview(
            bitmap = bitmap,
            orientation = selectedOrientation,
            cropState = activeCrop,
            config = config,
            onCropChange = ::updateActiveCrop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f)
        )
        Text(
            "请分别调整竖屏和横屏显示区域，横竖屏切换时会自动使用对应设置。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = glassForegroundColor(config).copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
        LiquidMenuButton(
            backdrop = backdrop,
            label = "重置当前方向",
            onClick = { updateActiveCrop(WallpaperCropState()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun WallpaperCropPreview(
    bitmap: Bitmap?,
    orientation: WallpaperPreviewOrientation,
    cropState: WallpaperCropState,
    config: ScheduleConfigEntity,
    onCropChange: (WallpaperCropState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspect = remember(context, orientation) { calculateWallpaperPreviewAspect(context, orientation) }
    val latestCropState by rememberUpdatedState(cropState)
    val latestOnCropChange by rememberUpdatedState(onCropChange)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxPreviewHeight = if (orientation == WallpaperPreviewOrientation.Portrait) maxHeight else maxHeight.coerceAtMost(260.dp)
        val widthByHeight = maxPreviewHeight * aspect
        val previewWidth = if (widthByHeight <= maxWidth) widthByHeight else maxWidth
        val previewHeight = previewWidth / aspect
        var previewSize by remember { mutableStateOf(IntSize.Zero) }
        LaunchedEffect(bitmap, previewSize, cropState) {
            val source = bitmap ?: return@LaunchedEffect
            if (previewSize.width > 0 && previewSize.height > 0) {
                val clamped = clampCropState(cropState, source.width, source.height, previewSize.width, previewSize.height)
                if (clamped != cropState) onCropChange(clamped)
            }
        }
        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(if (appUsesDarkTheme(config)) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                .onSizeChanged { previewSize = it }
                .pointerInput(bitmap, previewSize) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val source = bitmap ?: return@detectTransformGestures
                        if (previewSize.width <= 0 || previewSize.height <= 0) return@detectTransformGestures
                        latestOnCropChange(
                            updateCropStateForGesture(
                                state = latestCropState,
                                pan = pan,
                                zoom = zoom,
                                bitmapWidth = source.width,
                                bitmapHeight = source.height,
                                containerWidth = previewSize.width,
                                containerHeight = previewSize.height
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                FocusCroppedBitmap(bitmap = bitmap, cropState = cropState, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.08f)))
            } else {
                Text("无法读取图片", color = glassForegroundColor(config))
            }
        }
    }
}

@Composable
fun FocusCroppedBitmap(
    bitmap: Bitmap,
    cropState: WallpaperCropState,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        drawFocusCroppedBitmap(bitmap, imageBitmap, cropState)
    }
}

@Composable
fun FocusCroppedWallpaper(
    bitmap: Bitmap,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    useSavedCrop: Boolean = true
) {
    BoxWithConstraints(modifier = modifier) {
        val orientation = if (maxHeight >= maxWidth) WallpaperPreviewOrientation.Portrait else WallpaperPreviewOrientation.Landscape
        val cropState = if (useSavedCrop) config.wallpaperCropState(orientation) else WallpaperCropState()
        FocusCroppedBitmap(bitmap = bitmap, cropState = cropState, modifier = Modifier.fillMaxSize(), alpha = alpha)
    }
}

private fun DrawScope.drawFocusCroppedBitmap(
    bitmap: Bitmap,
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    cropState: WallpaperCropState
) {
    val rect = calculateFocusCropRect(bitmap.width, bitmap.height, size.width, size.height, cropState)
    if (rect == androidx.compose.ui.geometry.Rect.Zero) return
    drawImage(
        image = imageBitmap,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1))
    )
}
