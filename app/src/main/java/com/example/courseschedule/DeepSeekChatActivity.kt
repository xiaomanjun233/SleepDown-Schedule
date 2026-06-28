package com.example.courseschedule

// DeepSeek chat test page removed.

/*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class DeepSeekChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsState(AppState())
            CourseScheduleTheme(config = state.config) {
                DeepSeekChatPage(config = state.config, onClose = { finish() })
            }
        }
    }
}

private data class DeepSeekChatBubble(
    val role: String,
    val content: String,
    val reasoning: String = ""
)

@Composable
private fun DeepSeekChatPage(config: ScheduleConfigEntity, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = appUsesDarkTheme(config)
    val textColor = if (dark) Color.White else Color(0xFF111111)
    val background = if (dark) Color(0xFF050506) else Color(0xFFF6F7FB)
    val messages = remember { mutableStateListOf<DeepSeekChatBubble>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var thinkingEnabled by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(currentDeepSeekChatLabel(context)) }

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isBlank() || sending) return
        input = ""
        messages += DeepSeekChatBubble("user", text)
        sending = true
        status = "正在请求 DeepSeek..."
        val turns = messages
            .filter { it.role == "user" || (it.role == "assistant" && it.content.isNotBlank()) }
            .map { AiChatTurn(role = it.role, content = it.content) }
        scope.launch {
            sendDeepSeekChatMessage(context, turns, thinkingEnabled)
                .onSuccess { result ->
                    messages += DeepSeekChatBubble(
                        role = "assistant",
                        content = result.content.ifBlank { "（本轮没有最终正文，只返回了思考过程）" },
                        reasoning = result.reasoning
                    )
                    status = currentDeepSeekChatLabel(context)
                }
                .onFailure {
                    val rawBody = it.aiRawResponseBody().orEmpty()
                    messages += DeepSeekChatBubble(
                        role = "assistant",
                        content = it.message ?: "请求失败",
                        reasoning = extractAiReasoningForDisplay(rawBody)
                    )
                    status = "请求失败"
                }
            sending = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .imePadding()
    ) {
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
                    "DeepSeek 对话测试",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    DeepSeekChatCard(
                        title = "小测试台",
                        body = "直接输入一句话就能和当前保存的 DeepSeek API 对话。默认开启 thinking；若模型返回 reasoning_content，会和最终正文分开展示。",
                        textColor = textColor,
                        dark = dark,
                        accent = Color(0xFF0A84FF)
                    )
                }
            }
            items(messages.size) { index ->
                val message = messages[index]
                if (message.reasoning.isNotBlank()) {
                    DeepSeekChatCard(
                        title = "思考过程",
                        body = message.reasoning,
                        textColor = textColor,
                        dark = dark,
                        accent = Color(0xFFFF9F0A)
                    )
                }
                DeepSeekChatCard(
                    title = if (message.role == "user") "你" else "DeepSeek 正文",
                    body = message.content,
                    textColor = textColor,
                    dark = dark,
                    accent = if (message.role == "user") Color(0xFF64D2FF) else Color(0xFF30D158)
                )
            }
            if (sending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("等待 DeepSeek 返回...", color = textColor.copy(alpha = 0.72f))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Thinking",
                    modifier = Modifier.weight(1f),
                    color = textColor.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    placeholder = { Text("输入测试消息") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                )
                Button(onClick = { send() }, enabled = input.isNotBlank() && !sending) {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
private fun DeepSeekChatCard(
    title: String,
    body: String,
    textColor: Color,
    dark: Boolean,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.86f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor)
        }
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.80f),
            lineHeight = 18.sp
        )
    }
}
*/
