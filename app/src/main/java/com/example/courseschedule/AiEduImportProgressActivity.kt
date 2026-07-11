package com.example.courseschedule

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

object AiEduImportProgressSession {
    val progress = MutableStateFlow<AiEduImportProgress?>(null)
    var onConfirm: (() -> Unit)? = null
        private set
    var onSecondaryConfirm: (() -> Unit)? = null
        private set
    var onScreenMode: (() -> Unit)? = null
        private set
    var onCancel: (() -> Unit)? = null
        private set

    fun update(progress: AiEduImportProgress?) {
        this.progress.value = progress
    }

    fun setActions(
        onConfirm: (() -> Unit)? = null,
        onSecondaryConfirm: (() -> Unit)? = null,
        onScreenMode: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        this.onConfirm = onConfirm
        this.onSecondaryConfirm = onSecondaryConfirm
        this.onScreenMode = onScreenMode
        this.onCancel = onCancel
    }

    fun clearActions() {
        onConfirm = null
        onSecondaryConfirm = null
        onScreenMode = null
        onCancel = null
    }

    fun confirm() {
        onConfirm?.invoke()
    }

    fun secondaryConfirm() {
        onSecondaryConfirm?.invoke()
    }

    fun useScreenMode() {
        onScreenMode?.invoke()
    }

    fun cancel() {
        onCancel?.invoke()
    }
}

class AiEduImportProgressActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
                AiEduImportProgressPage(
                    config = state.config,
                    onClose = {
                        if (AiEduImportProgressSession.progress.value?.awaitingConfirmation == true) {
                            AiEduImportProgressSession.cancel()
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AiEduImportProgressPage(config: ScheduleConfigEntity, onClose: () -> Unit) {
    val progress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    val current = progress ?: AiEduImportProgress(steps = listOf("等待 AI 教务导入任务"))
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val dark = appUsesDarkTheme(config)
    val textColor = if (dark) Color.White else Color(0xFF111111)
    val statusText = when {
        current.error != null -> "解析失败"
        current.finished -> "已完成"
        current.steps.isNotEmpty() -> current.steps.last()
        else -> "准备中"
    }
    val visibleStatusText = if (current.error == null && !current.finished && current.routeLabel.isNotBlank()) {
        current.routeLabel
    } else {
        statusText
    }
    val pageTitle = current.routeLabel.takeIf { it.isNotBlank() } ?: "AI 教务导入"
    val pageSubtitle = if (current.routeLabel.contains("手动")) {
        "文件内容、提示词摘要和模型原始返回会保留在这里。"
    } else {
        if (current.routeLabel.isNotBlank()) {
            "${current.routeLabel} 路 页面内容、提示词摘要和模型原始返回会保留在这里。"
        } else {
            "页面内容、提示词摘要和模型原始返回会保留在这里。"
        }
    }
    val unusedSubTitleA = if (current.routeLabel.isNotBlank()) {
        "${current.routeLabel} 路 页面内容、提示词摘要和模型原始返回会保留在这里。"
    } else {
        "页面内容、提示词摘要和模型原始返回会保留在这里。"
    }
    val statusColor by animateColorAsState(
        targetValue = when {
            current.error != null -> Color(0xFFFF453A)
            current.finished -> textColor.copy(alpha = 0.42f)
            else -> Color(0xFF0A84FF)
        },
        animationSpec = tween(180),
        label = "ai-edu-status-color"
    )
    val capsuleScale by animateFloatAsState(
        targetValue = if (current.finished || current.error != null) 0.98f else 1f,
        animationSpec = tween(180),
        label = "ai-edu-status-scale"
    )
    val messages = aiEduProgressMessages(current)
    val unusedSubTitleB = if (current.routeLabel.isNotBlank()) {
        "${current.routeLabel} · 页面内容、提示词摘要和模型原始返回会保留在这里。"
    } else {
        "页面内容、提示词摘要和模型原始返回会保留在这里。"
    }
    BackHandler(enabled = current.awaitingConfirmation) {
        AiEduImportProgressSession.cancel()
        onClose()
    }

    LaunchedEffect(current.steps.size, current.pageText.length, current.reasoningOutput.length, current.aiOutput.length, current.error, current.finished) {
        withFrameNanos { }
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(current.finished, current.error) {
        if (current.finished && current.error == null) {
            delay(850)
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF050506) else Color(0xFFF6F7FB))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textColor.copy(alpha = if (dark) 0.12f else 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(textColor)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        pageTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pageSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer(scaleX = capsuleScale, scaleY = capsuleScale)
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusColor.copy(alpha = if (current.error != null) 0.18f else 0.16f))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        visibleStatusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (current.awaitingConfirmation) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    current.screenModeActionLabel.takeIf { it.isNotBlank() }?.let { label ->
                        AiEduActionChip(
                            label = label,
                            accent = Color(0xFFFF9F0A),
                            textColor = textColor,
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AiEduImportProgressSession.useScreenMode()
                                (context as? ComponentActivity)?.finish()
                            }
                        )
                    }
                    AiEduActionChip(
                        label = current.confirmActionLabel.ifBlank { "确认发送给 AI" },
                        accent = Color(0xFF0A84FF),
                        textColor = textColor,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        onClick = { AiEduImportProgressSession.confirm() }
                    )
                    current.secondaryConfirmActionLabel.takeIf { it.isNotBlank() }?.let { label ->
                        AiEduActionChip(
                            label = label,
                            accent = Color(0xFF30D158),
                            textColor = textColor,
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onClick = { AiEduImportProgressSession.secondaryConfirm() }
                        )
                    }
                    AiEduActionChip(
                        label = current.cancelActionLabel.ifBlank { "取消" },
                        accent = Color(0xFFFF453A),
                        textColor = textColor,
                        dark = dark,
                        modifier = Modifier.weight(0.72f),
                        onClick = {
                            AiEduImportProgressSession.cancel()
                            onClose()
                        }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages.size) { index ->
                    val message = messages[index]
                    AiEduMessageBubble(message, textColor, dark)
                }
                item {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun AiEduActionChip(
    label: String,
    accent: Color,
    textColor: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = if (dark) 0.18f else 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (accent == Color(0xFFFF453A)) accent else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class AiEduProgressMessage(
    val title: String,
    val body: String,
    val accent: Color,
    val images: List<RenderedPageImage> = emptyList(),
    val copyable: Boolean = false,
    val collapsible: Boolean = false,
    val initiallyCollapsed: Boolean = false
)

private fun aiEduProgressMessages(progress: AiEduImportProgress): List<AiEduProgressMessage> {
    val messages = mutableListOf<AiEduProgressMessage>()
    messages += AiEduProgressMessage(
        title = "执行进度",
        body = aiEduImportStepRows(progress).mapIndexed { index, row ->
            val prefix = when (row.status) {
                AiEduImportStepStatus.Done -> "已完成"
                AiEduImportStepStatus.Current -> "正在"
                AiEduImportStepStatus.Pending -> "待执行"
                AiEduImportStepStatus.Error -> "失败"
            }
            "${index + 1}. $prefix：${row.text}"
        }.joinToString("\n"),
        accent = Color(0xFF0A84FF)
    )
    if (progress.requestPreview.isNotBlank()) {
        messages += AiEduProgressMessage("请求摘要", progress.requestPreview, Color(0xFF64D2FF), copyable = true)
    }
    messages += AiEduProgressMessage(
        "提示词原文",
        aiSchedulePrompt(),
        Color(0xFFBF5AF2),
        copyable = true,
        collapsible = true,
        initiallyCollapsed = true
    )
    if (progress.pageText.isNotBlank()) {
        messages += AiEduProgressMessage(
            "页面文本预览",
            progress.pageText,
            Color(0xFFFFD60A),
            copyable = true,
            collapsible = true,
            initiallyCollapsed = true
        )
    }
    if (progress.screenshotPreviews.isNotEmpty()) {
        messages += AiEduProgressMessage(
            title = "识屏截图预览",
            body = "这是即将发送给视觉模型的 WebView 截图。请确认截图里确实包含课表区域；如果截图抓偏了，请取消后滚动到课表区域再试。",
            accent = Color(0xFFFF9F0A),
            images = progress.screenshotPreviews
        )
    }
    progress.error?.let {
        messages += AiEduProgressMessage("错误信息", it, Color(0xFFFF453A))
    }
    if (progress.reasoningOutput.isNotBlank()) {
        messages += AiEduProgressMessage(
            "思考过程",
            progress.reasoningOutput,
            Color(0xFFFF9F0A),
            copyable = true,
            collapsible = progress.reasoningOutput.length > 1600,
            initiallyCollapsed = true
        )
    } else if (!progress.finished && progress.error == null && progress.steps.any { it.contains("AI") }) {
        messages += AiEduProgressMessage(
            "正在思考",
            "模型正在阅读页面内容并组织课表结构。若当前模型支持 reasoning_content，返回后会在这里单独显示思考过程；最终正文会显示在下方，不会和思考过程混在一起。",
            Color(0xFFFF9F0A)
        )
    }
    if (progress.aiOutput.isNotBlank()) {
        messages += AiEduProgressMessage(
            "AI 原始返回",
            progress.aiOutput,
            Color(0xFF30D158),
            copyable = true,
            collapsible = progress.aiOutput.length > 2000,
            initiallyCollapsed = false
        )
    } else if (progress.error == null) {
        messages += AiEduProgressMessage(
            "AI 原始返回",
            "等待模型返回。隐藏推理过程不会显示；这里会展示模型最终返回文本。",
            Color(0xFF8E8E93)
        )
    }
    return messages
}

@Composable
private fun AiEduMessageBubble(message: AiEduProgressMessage, textColor: Color, dark: Boolean) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var collapsed by remember(message.title, message.body) { mutableStateOf(message.initiallyCollapsed) }
    val shownBody = if (message.collapsible && collapsed && message.body.length > 900) {
        message.body.take(900).trimEnd() + "\n\n……已收起，点击“展开”查看全文。"
    } else {
        message.body
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.86f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(message.accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    message.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                if (message.copyable) {
                    Text(
                        "复制",
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    clipboard.setText(AnnotatedString(message.body))
                                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = message.accent
                    )
                }
                if (message.collapsible) {
                    Text(
                        if (collapsed) "展开" else "收起",
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { collapsed = !collapsed }
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                shownBody,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.78f),
                lineHeight = 18.sp
            )
            message.images.forEach { image ->
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = null,
                    image.base64
                ) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            val bytes = Base64.decode(image.base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                val previewBitmap = bitmap
                if (previewBitmap != null) {
                    val previewHeight = if (previewBitmap.height > previewBitmap.width * 2) 520.dp else 280.dp
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "第 ${image.pageIndex + 1} 张识屏截图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
