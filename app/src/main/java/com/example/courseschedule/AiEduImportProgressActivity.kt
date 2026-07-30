package com.example.courseschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val textColor = glassForegroundColor(settingsVisualConfig(config))
    val statusLabel = when {
        current.error != null -> "解析失败"
        current.finished -> "已完成"
        current.steps.isNotEmpty() -> current.steps.last()
        else -> "准备中"
    }
    val pageTitle = current.routeLabel.takeIf { it.isNotBlank() } ?: "AI 教务导入"
    val statusColor by animateColorAsState(
        targetValue = when {
            current.error != null -> Color(0xFFFF453A)
            current.finished -> textColor.copy(alpha = 0.42f)
            else -> Color(0xFF0A84FF)
        },
        animationSpec = tween(180),
        label = "ai-edu-status-color"
    )
    val messages = aiEduProgressMessages(current)
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
    DetailActivityScaffold(
        title = pageTitle,
        config = config,
        onBack = onClose,
        compactTopBar = true,
        centerCompactTitle = true
    ) { backdrop ->
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = detailContentTopPadding() + 10.dp,
                    bottom = 18.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AiEduProgressHero(
                        progress = current,
                        statusLabel = statusLabel,
                        statusColor = statusColor,
                        textColor = textColor,
                        config = config,
                        backdrop = backdrop
                    )
                }
                item {
                    AiEduStepTimeline(
                        progress = current,
                        textColor = textColor,
                        config = config,
                        backdrop = backdrop
                    )
                }
                items(messages.size) { index ->
                    AiEduMessageCard(
                        message = messages[index],
                        textColor = textColor,
                        config = config,
                        backdrop = backdrop
                    )
                }
            }
            if (current.awaitingConfirmation) {
                AiEduConfirmationPanel(
                    progress = current,
                    config = config,
                    backdrop = backdrop,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun AiEduProgressHero(
    progress: AiEduImportProgress,
    statusLabel: String,
    statusColor: Color,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: com.kyant.backdrop.Backdrop?
) {
    AiEduLiquidPanel(
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        accent = statusColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(statusColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (progress.error != null) "!" else if (progress.finished) "✓" else "AI",
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = when {
                        progress.error != null -> "本次处理未完成"
                        progress.finished -> "课表内容已处理完成"
                        progress.awaitingConfirmation -> "发送前需要你的确认"
                        else -> "正在整理课表内容"
                    },
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusLabel,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (progress.finished) {
                        "结果会保留在本页，确认无误后再返回。"
                    } else {
                        "页面仅展示可验证的处理步骤和模型摘要。"
                    },
                    color = textColor.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AiEduStepTimeline(
    progress: AiEduImportProgress,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: com.kyant.backdrop.Backdrop?
) {
    val rows = aiEduImportStepRows(progress)
    AiEduLiquidPanel(
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        accent = Color(0xFF0A84FF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "处理过程",
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            rows.forEachIndexed { index, row ->
                val rowColor = when (row.status) {
                    AiEduImportStepStatus.Done -> Color(0xFF30D158)
                    AiEduImportStepStatus.Current -> Color(0xFF0A84FF)
                    AiEduImportStepStatus.Pending -> textColor.copy(alpha = 0.28f)
                    AiEduImportStepStatus.Error -> Color(0xFFFF453A)
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(rowColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (row.status == AiEduImportStepStatus.Current) 8.dp else 6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(rowColor)
                            )
                        }
                        if (index < rows.lastIndex) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(textColor.copy(alpha = 0.10f))
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            row.text,
                            color = textColor.copy(
                                alpha = if (row.status == AiEduImportStepStatus.Pending) 0.46f else 0.86f
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            when (row.status) {
                                AiEduImportStepStatus.Done -> "已完成"
                                AiEduImportStepStatus.Current -> "正在处理"
                                AiEduImportStepStatus.Pending -> "等待执行"
                                AiEduImportStepStatus.Error -> "处理失败"
                            },
                            color = rowColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiEduConfirmationPanel(
    progress: AiEduImportProgress,
    config: ScheduleConfigEntity,
    backdrop: com.kyant.backdrop.Backdrop?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AiEduLiquidPanel(
        backdrop = backdrop,
        config = config,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        accent = Color(0xFF0A84FF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val actions = buildList<AiEduFooterAction> {
                add(
                    AiEduFooterAction(
                        label = compactAiEduActionLabel(
                            progress.confirmActionLabel.ifBlank { "确认发送给 AI" },
                            AiEduFooterActionKind.Confirm
                        ),
                        role = DialogButtonRole.Confirm
                    ) { AiEduImportProgressSession.confirm() }
                )
                progress.screenModeActionLabel.takeIf(String::isNotBlank)?.let { label ->
                    add(
                        AiEduFooterAction(
                            label = compactAiEduActionLabel(label, AiEduFooterActionKind.Screen),
                            role = DialogButtonRole.Neutral
                        ) {
                            AiEduImportProgressSession.useScreenMode()
                            (context as? ComponentActivity)?.finish()
                        }
                    )
                }
                progress.secondaryConfirmActionLabel.takeIf(String::isNotBlank)?.let { label ->
                    add(
                        AiEduFooterAction(
                            label = compactAiEduActionLabel(label, AiEduFooterActionKind.Secondary),
                            role = DialogButtonRole.Neutral
                        ) { AiEduImportProgressSession.secondaryConfirm() }
                    )
                }
                add(
                    AiEduFooterAction(
                        label = compactAiEduActionLabel(
                            progress.cancelActionLabel.ifBlank { "取消" },
                            AiEduFooterActionKind.Cancel
                        ),
                        role = DialogButtonRole.Cancel
                    ) {
                        AiEduImportProgressSession.cancel()
                        onClose()
                    }
                )
            }
            val actionRows = if (actions.size == 4) actions.chunked(2) else listOf(actions)
            actionRows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowActions.forEach { action ->
                        DialogLiquidButton(
                            backdrop = backdrop,
                            label = action.label,
                            role = action.role,
                            roundIcon = false,
                            destructiveFilled = action.role == DialogButtonRole.Cancel,
                            modifier = Modifier.weight(1f),
                            onClick = action.onClick
                        )
                    }
                }
            }
        }
    }
}

private enum class AiEduFooterActionKind {
    Confirm,
    Screen,
    Secondary,
    Cancel
}

private data class AiEduFooterAction(
    val label: String,
    val role: DialogButtonRole,
    val onClick: () -> Unit
)

private fun compactAiEduActionLabel(
    original: String,
    kind: AiEduFooterActionKind
): String = when (kind) {
    AiEduFooterActionKind.Confirm -> when {
        original.contains("截图") -> "发送截图"
        original.contains("文本") -> "发送文本"
        else -> original.removePrefix("确认").take(6)
    }
    AiEduFooterActionKind.Screen -> "识屏模式"
    AiEduFooterActionKind.Secondary ->
        if (original.contains("截图")) "只发截图" else original.take(6)
    AiEduFooterActionKind.Cancel ->
        if (original.contains("重抓")) "重新抓取" else "取消"
}

@Composable
private fun AiEduLiquidPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    accent: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val lightStyle = glassUsesLightStyle(config)
    val surfaceColor = if (lightStyle) {
        lerp(Color.White, accent, 0.10f).copy(alpha = 0.22f)
    } else {
        lerp(Color(0xFF121212), accent, 0.12f).copy(alpha = 0.34f)
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            surfaceColor = surfaceColor,
            blurRadius = 12.dp,
            content = content
        )
    } else {
        Box(
            modifier = Modifier
                .then(modifier)
                .clip(shape)
                .background(
                    if (appUsesDarkTheme(config)) {
                        lerp(Color(0xFF1C1C1E), accent, 0.10f)
                    } else {
                        lerp(Color.White, accent, 0.08f)
                    }
                ),
            content = content
        )
    }
}

@Composable
private fun AiEduInlineLiquidButton(
    label: String,
    accent: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onClick: () -> Unit
) {
    val dark = appUsesDarkTheme(config)
    val textColor = lerp(accent, if (dark) Color.White else Color.Black, if (dark) 0.22f else 0.38f)
    val modifier = Modifier.height(36.dp)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 36.dp,
            tint = accent,
            surfaceColor = accent.copy(alpha = if (dark) 0.18f else 0.14f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            blurRadius = 7.dp,
            lensHeight = 12.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = if (dark) 0.18f else 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
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
    if (progress.requestPreview.isNotBlank()) {
        messages += AiEduProgressMessage("请求摘要", progress.requestPreview, Color(0xFF64D2FF), copyable = true)
    }
    messages += AiEduProgressMessage(
        "课表解析协议",
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
            "模型处理摘要",
            progress.reasoningOutput,
            Color(0xFFFF9F0A),
            copyable = true,
            collapsible = progress.reasoningOutput.length > 1600,
            initiallyCollapsed = true
        )
    } else if (!progress.finished && progress.error == null && progress.steps.any { it.contains("AI") }) {
        messages += AiEduProgressMessage(
            "模型处理中",
            "模型正在阅读输入并组织课表结构。支持摘要的模型会在完成后显示处理摘要；应用不会展示或保存模型的原始思维链。",
            Color(0xFFFF9F0A)
        )
    }
    if (progress.aiOutput.isNotBlank()) {
        messages += AiEduProgressMessage(
            "模型返回内容",
            progress.aiOutput,
            Color(0xFF30D158),
            copyable = true,
            collapsible = progress.aiOutput.length > 2000,
            initiallyCollapsed = false
        )
    } else if (progress.error == null) {
        messages += AiEduProgressMessage(
            "模型返回内容",
            "等待模型返回最终可见文本。",
            Color(0xFF8E8E93)
        )
    }
    return messages
}

@Composable
private fun AiEduMessageCard(
    message: AiEduProgressMessage,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: com.kyant.backdrop.Backdrop?
) {
    val context = LocalContext.current
    var collapsed by remember(message.title, message.body) { mutableStateOf(message.initiallyCollapsed) }
    val shownBody = if (message.collapsible && collapsed && message.body.length > 900) {
        message.body.take(900).trimEnd() + "\n\n……已收起，点击“展开”查看全文。"
    } else {
        message.body
    }
    AiEduLiquidPanel(
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        accent = message.accent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Spacer(Modifier.width(6.dp))
                    AiEduInlineLiquidButton(
                        label = "复制",
                        accent = message.accent,
                        config = config,
                        backdrop = backdrop,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(message.title, message.body)
                            )
                            android.widget.Toast.makeText(
                                context,
                                "已复制",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                if (message.collapsible) {
                    Spacer(Modifier.width(6.dp))
                    AiEduInlineLiquidButton(
                        label = if (collapsed) "展开" else "收起",
                        accent = lerp(
                            message.accent,
                            if (appUsesDarkTheme(config)) Color.White else Color.Black,
                            0.18f
                        ),
                        config = config,
                        backdrop = backdrop,
                        onClick = { collapsed = !collapsed }
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
