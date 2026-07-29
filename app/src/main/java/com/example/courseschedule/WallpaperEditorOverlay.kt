package com.example.courseschedule

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WallpaperMorphEasing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f)

@Composable
fun WallpaperEditorOverlay(
    uri: Uri,
    entrySnapshot: Bitmap?,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    visible: Boolean,
    onCancel: () -> Unit,
    onApply: (ScheduleConfigEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var sourceSize by remember(uri) { mutableStateOf<WallpaperSourceSize?>(null) }
    var orientation by remember { mutableStateOf(WallpaperPreviewOrientation.Portrait) }
    var portraitCrop by remember(uri) { mutableStateOf(WallpaperCropState()) }
    var landscapeCrop by remember(uri) { mutableStateOf(WallpaperCropState()) }
    var applying by remember { mutableStateOf(false) }
    val activeCrop = if (orientation == WallpaperPreviewOrientation.Portrait) portraitCrop else landscapeCrop

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadSampledBitmap(context.applicationContext, uri, 2048) }
    }
    LaunchedEffect(uri) {
        sourceSize = withContext(Dispatchers.IO) { readWallpaperSourceSize(context.applicationContext, uri) }
    }
    LaunchedEffect(visible) {
        progress.animateTo(
            if (visible) 1f else 0f,
            // The root dialog host keeps a dismissed entry for 320 ms.
            // Finish the reverse morph before that hand-off instead of losing its last frame.
            tween(if (visible) 520 else 300, easing = WallpaperMorphEasing)
        )
    }

    fun setCrop(next: WallpaperCropState) {
        if (orientation == WallpaperPreviewOrientation.Portrait) portraitCrop = next else landscapeCrop = next
    }
    fun switchOrientation(target: WallpaperPreviewOrientation) {
        if (orientation == target || progress.value < 0.98f) return
        orientation = target
    }
    fun save() {
        if (applying) return
        applying = true
        scope.launch {
            val appContext = context.applicationContext
            val savedUri = withContext(Dispatchers.IO) { persistWallpaperSource(appContext, uri) ?: uri }
            val savedSize = sourceSize ?: withContext(Dispatchers.IO) { readWallpaperSourceSize(appContext, savedUri) }
            val lightText = withContext(Dispatchers.IO) { wallpaperPrefersLightText(appContext, savedUri.toString()) }
            onApply(
                config.copy(
                    wallpaperUri = savedUri.toString(),
                    wallpaperPortraitCenterX = portraitCrop.centerX,
                    wallpaperPortraitCenterY = portraitCrop.centerY,
                    wallpaperPortraitScale = portraitCrop.scale,
                    wallpaperLandscapeCenterX = landscapeCrop.centerX,
                    wallpaperLandscapeCenterY = landscapeCrop.centerY,
                    wallpaperLandscapeScale = landscapeCrop.scale,
                    wallpaperSourceWidth = savedSize?.width ?: bitmap?.width,
                    wallpaperSourceHeight = savedSize?.height ?: bitmap?.height,
                    homeTextLight = lightText
                )
            )
        }
    }

    BackHandler(visible && !applying, onBack = onCancel)
    val p = progress.value.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f * p))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = {}
            )
    ) {
        val aspect = remember(context, orientation) { calculateWallpaperPreviewAspect(context, orientation) }
        val availableHeight = (maxHeight - 250.dp).coerceAtLeast(280.dp)
        val desiredWidth = minOf(
            if (orientation == WallpaperPreviewOrientation.Portrait) maxWidth * 0.70f else maxWidth * 0.88f,
            availableHeight * aspect
        ).coerceAtLeast(210.dp)
        val targetWidth by animateDpAsState(desiredWidth, tween(460, easing = WallpaperMorphEasing), label = "wallpaperTargetWidth")
        val targetHeight by animateDpAsState(desiredWidth / aspect, tween(460, easing = WallpaperMorphEasing), label = "wallpaperTargetHeight")
        val cardWidth = maxWidth + (targetWidth - maxWidth) * p
        val cardHeight = maxHeight + (targetHeight - maxHeight) * p
        // The real home is already a rounded physical screen. Starting from 0.dp
        // exposes square snapshot corners during the first half of the shrink.
        val corner = 20.dp + 14.dp * p

        val oldAlpha = (1f - ((p - 0.46f) / 0.28f)).coerceIn(0f, 1f)
        val wallpaperAlpha = ((p - 0.48f) / 0.34f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(cardWidth, cardHeight)
                .graphicsLayer {
                    shadowElevation = 30.dp.toPx() * p
                    shape = RoundedCornerShape(corner)
                    clip = true
                }
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            if (entrySnapshot != null && oldAlpha > 0f) {
                Image(
                    bitmap = remember(entrySnapshot) { entrySnapshot.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = oldAlpha },
                    contentScale = ContentScale.FillBounds
                )
            }
            if (bitmap != null && wallpaperAlpha > 0f) {
                WallpaperGestureCanvas(
                    bitmap = bitmap!!,
                    cropState = activeCrop,
                    enabled = p > 0.97f,
                    onCropChange = ::setCrop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = wallpaperAlpha }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .height(92.dp)
                .graphicsLayer { alpha = ((p - 0.50f) / 0.35f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            WallpaperHeaderButton("取消", backdrop, Color(0xFF4A4A4F).copy(alpha = 0.78f), !applying, onCancel)
            Spacer(Modifier.weight(1f))
            Text("调整壁纸", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            WallpaperHeaderButton(if (applying) "保存中" else "完成", backdrop, Color(0xFF0A84FF).copy(alpha = 0.84f), !applying, ::save)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .graphicsLayer { alpha = ((p - 0.65f) / 0.28f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                WallpaperRoundOrientationButton(true, orientation == WallpaperPreviewOrientation.Portrait, backdrop) {
                    switchOrientation(WallpaperPreviewOrientation.Portrait)
                }
                WallpaperRoundOrientationButton(false, orientation == WallpaperPreviewOrientation.Landscape, backdrop) {
                    switchOrientation(WallpaperPreviewOrientation.Landscape)
                }
            }
            Text("双指缩放·拖动调整", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun WallpaperHeaderButton(
    text: String,
    backdrop: Backdrop?,
    surface: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.width(86.dp),
            isInteractive = enabled,
            height = 40.dp,
            surfaceColor = surface,
            blurRadius = 10.dp,
            lensHeight = 26.dp,
            lensAmount = 34.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge) }
    } else {
        Surface(modifier = Modifier.size(86.dp, 40.dp), shape = RoundedCornerShape(50), color = surface, enabled = enabled, onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { Text(text, color = Color.White) }
        }
    }
}

@Composable
private fun WallpaperRoundOrientationButton(
    portrait: Boolean,
    selected: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit
) {
    val surface = if (selected) Color(0xFF0A84FF).copy(alpha = 0.86f) else Color(0xFF4A4A4F).copy(alpha = 0.76f)
    val icon: @Composable () -> Unit = {
        Canvas(Modifier.size(22.dp)) {
            val width = if (portrait) size.width * 0.52f else size.width * 0.84f
            val height = if (portrait) size.height * 0.84f else size.height * 0.52f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.size(52.dp),
            height = 52.dp,
            contentPadding = PaddingValues(0.dp),
            surfaceColor = surface,
            blurRadius = 10.dp,
            lensHeight = 30.dp,
            lensAmount = 44.dp
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() } }
    } else {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(50), color = surface, onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
    }
}

@Composable
internal fun WallpaperGestureCanvas(
    bitmap: Bitmap,
    cropState: WallpaperCropState,
    enabled: Boolean,
    onCropChange: (WallpaperCropState) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val latestCrop by rememberUpdatedState(cropState)
    val latestChange by rememberUpdatedState(onCropChange)
    val elasticScale = remember { Animatable(1f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(bitmap, containerSize, enabled) {
                if (!enabled) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    if (containerSize.width <= 0 || containerSize.height <= 0) return@detectTransformGestures
                    val proposedScale = latestCrop.scale * zoom
                    latestChange(
                        updateCropStateForGesture(
                            latestCrop,
                            pan,
                            zoom,
                            bitmap.width,
                            bitmap.height,
                            containerSize.width,
                            containerSize.height
                        )
                    )
                    if (proposedScale < 1f || proposedScale > 6f) {
                        val elastic = if (proposedScale < 1f) {
                            (1f - (1f - proposedScale) * 0.16f).coerceAtLeast(0.90f)
                        } else {
                            (1f + (proposedScale / 6f - 1f) * 0.10f).coerceAtMost(1.10f)
                        }
                        scope.launch { elasticScale.snapTo(elastic) }
                        reboundJob?.cancel()
                        reboundJob = scope.launch {
                            delay(75)
                            elasticScale.animateTo(1f, spring(dampingRatio = 0.60f, stiffness = 420f))
                        }
                    }
                }
            }
    ) {
        FocusCroppedBitmap(
            bitmap = bitmap,
            cropState = cropState,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = elasticScale.value
                scaleY = elasticScale.value
            }
        )
    }
}
