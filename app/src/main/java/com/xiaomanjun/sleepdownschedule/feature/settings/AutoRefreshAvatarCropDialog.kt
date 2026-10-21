package com.xiaomanjun.sleepdownschedule.feature.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.calculateFocusCropRect
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadWallpaperSource
import com.xiaomanjun.sleepdownschedule.feature.home.WallpaperGestureCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun AutoRefreshAvatarCropDialog(
    uri: Uri,
    show: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var crop by remember(uri) { mutableStateOf(WallpaperCropState()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            loadWallpaperSource(context.applicationContext, uri, 2048).bitmap
        }
        if (bitmap == null) error = "图片读取失败，请重新选择"
    }
    SleepDownPickerDialog(
        show = show, title = "裁切头像", backdrop = backdrop, config = config,
        onDismissRequest = { if (!saving) onDismiss() },
        onDismissFinished = onDismissFinished,
        scrollableContent = true,
        bottomActions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogLiquidButton(
                    backdrop, "取消", { if (!saving) onDismiss() }, Modifier.weight(1f),
                    monochromeNeutral = true
                )
                DialogLiquidButton(
                    backdrop, if (saving) "保存中…" else "完成",
                    onClick = {
                        val source = bitmap
                        if (source != null && !saving) {
                            saving = true
                            scope.launch {
                                try {
                                    val path = withContext(Dispatchers.IO) {
                                        val directory = File(context.filesDir, "auto_refresh").apply { mkdirs() }
                                        val destination = File.createTempFile("avatar-", ".png", directory)
                                        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                        try {
                                            val rect = calculateFocusCropRect(source.width, source.height, 512f, 512f, crop)
                                            Canvas(output).apply {
                                                clipPath(android.graphics.Path().apply {
                                                    addCircle(256f, 256f, 256f, android.graphics.Path.Direction.CW)
                                                })
                                                drawBitmap(source, null, RectF(rect.left, rect.top, rect.right, rect.bottom),
                                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                            }
                                            destination.outputStream().use {
                                                check(output.compress(Bitmap.CompressFormat.PNG, 100, it))
                                            }
                                            destination.absolutePath
                                        } catch (failure: Exception) {
                                            destination.delete()
                                            throw failure
                                        } finally {
                                            output.recycle()
                                        }
                                    }
                                    onSaved(path)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = "头像保存失败，请重试"
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f), monochromeNeutral = true
                )
            }
        }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val source = bitmap
            if (source != null) WallpaperGestureCanvas(
                bitmap = source, cropState = crop, enabled = show && !saving,
                onCropChange = { crop = it }, modifier = Modifier.fillMaxSize()
            ) else if (error == null) CircularProgressIndicator()
        }
        Text(error ?: "拖动调整位置，双指缩放预览",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
