package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.itos.coui.button.CouiButton
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiButtonStyle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LiquidOptionTabs
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class FeedbackView(val label: String) {
    Roadmap("开发进度"), Mine("我的反馈"), Submit("提交反馈"),
}

private sealed interface FeedbackList {
    data object Loading : FeedbackList
    data class Loaded(val page: FeedbackPage) : FeedbackList
    data class Failed(val message: String) : FeedbackList
}

@Composable
internal fun FeedbackScreen(config: ScheduleConfigEntity, backdrop: Backdrop?) {
    val context = LocalContext.current
    val client = remember(context.applicationContext) { FeedbackClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var view by rememberSaveable { mutableStateOf(FeedbackView.Roadmap) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var openedItem by remember { mutableStateOf<FeedbackItem?>(null) }
    var draftKind by rememberSaveable { mutableStateOf(FeedbackKind.Bug) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftDescription by rememberSaveable { mutableStateOf("") }
    var draftId by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitError by rememberSaveable { mutableStateOf("") }

    val list by key(view, page, refresh) {
        produceState<FeedbackList>(FeedbackList.Loading, client) {
            if (view == FeedbackView.Submit) return@produceState
            value = try {
                FeedbackList.Loaded(client.list(own = view == FeedbackView.Mine, page = page))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                FeedbackList.Failed(feedbackError(error))
            }
        }
    }
    LaunchedEffect(view, page) { listState.scrollToItem(0) }

    fun changeDraft() {
        draftId = ""
        submitError = ""
    }

    val title = draftTitle.trim()
    val titleLength = title.codePointCount(0, title.length)
    val description = draftDescription.trim()
    val descriptionLength = description.codePointCount(0, description.length)
    val canSubmit = !sending && titleLength <= 80 && descriptionLength in 5..2000

    fun submit() {
        if (sending || !canSubmit) return
        val id = draftId.ifBlank { UUID.randomUUID().toString().also { draftId = it } }
        val kind = draftKind
        sending = true
        submitError = ""
        scope.launch {
            try {
                client.submit(id, kind, title, description)
                draftTitle = ""
                draftDescription = ""
                draftId = ""
                page = 0
                view = FeedbackView.Mine
                refresh++
                Toast.makeText(context, "反馈已提交", Toast.LENGTH_SHORT).show()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (error is FeedbackRequestException && error.status == 409) draftId = ""
                submitError = feedbackError(error)
            } finally {
                sending = false
            }
        }
    }

    val topPadding = detailContentTopPadding()
    val bottomPadding = detailContentBottomPadding()
    val dark = appUsesDarkTheme(config)
    Column(Modifier.fillMaxSize().padding(top = topPadding).imePadding()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidOptionTabs(
                view.ordinal, FeedbackView.entries.map { it.label }, backdrop, config,
                width = maxWidth.coerceAtMost(420.dp), followAppTheme = true,
            ) { index ->
                if (!sending && FeedbackView.entries[index] != view) {
                    view = FeedbackView.entries[index]
                    page = 0
                }
            }
        }
        SleepDownSecondaryPageList(
            contentTopPadding = 10.dp,
            contentBottomPadding = bottomPadding + 12.dp,
            modifier = Modifier.weight(1f),
            state = listState,
            style = SleepDownSecondaryPageStyle.Content,
        ) {
            if (view == FeedbackView.Submit) {
                item(key = "feedback-form") {
                    SettingsGroup(backdrop, config) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                LiquidOptionTabs(
                                    draftKind.ordinal, FeedbackKind.entries.map { it.label }, backdrop, config,
                                    width = maxWidth.coerceAtMost(280.dp), followAppTheme = true,
                                ) { index ->
                                    if (!sending && draftKind != FeedbackKind.entries[index]) {
                                        draftKind = FeedbackKind.entries[index]
                                        changeDraft()
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = { if (it.length <= 160) { draftTitle = it; changeDraft() } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("标题（选填）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                enabled = !sending,
                                isError = titleLength > 80,
                                supportingText = if (titleLength > 80) ({ Text("标题最多 80 字") }) else null,
                            )
                            OutlinedTextField(
                                value = draftDescription,
                                onValueChange = { if (it.length <= 4000) { draftDescription = it; changeDraft() } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("具体描述") },
                                placeholder = { Text("说说在哪一步遇到了问题，或希望怎样改进。") },
                                minLines = 4,
                                maxLines = 8,
                                enabled = !sending,
                                isError = descriptionLength > 2000,
                                supportingText = { Text("$descriptionLength / 2000 字，至少填写 5 字") },
                            )
                            Text("原始反馈仅你和管理员可见。", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (submitError.isNotEmpty()) Text(submitError, color = MaterialTheme.colorScheme.error)
                            CouiButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = if (sending) "提交中…" else "提交反馈",
                                onClick = ::submit,
                                dark = dark,
                                enabled = canSubmit,
                                style = CouiButtonStyle.Primary,
                                colors = CouiButtonDefaults.primaryColors(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            } else when (val result = list) {
                FeedbackList.Loading -> item(key = "feedback-loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp))
                    }
                }
                is FeedbackList.Failed -> item(key = "feedback-error") {
                    FeedbackListMessage(result.message, "重试") { refresh++ }
                }
                is FeedbackList.Loaded -> {
                    if (result.page.items.isEmpty()) item(key = "feedback-empty") {
                        val message = if (view == FeedbackView.Mine) "暂无此页反馈" else "暂无此页公开开发进度"
                        FeedbackListMessage(message, if (page > 0) "返回第一页" else "刷新") {
                            if (page > 0) page = 0 else refresh++
                        }
                    }
                    items(result.page.items, key = { it.id }) { item ->
                        SettingsGroup(backdrop, config) {
                            val firstLine = item.description.lineSequence().firstOrNull().orEmpty()
                            val preview = if (firstLine.length > 72) firstLine.take(71) + "…" else firstLine
                            SettingsNavigationRow(
                                title = item.title,
                                subtitle = listOf(item.kindLabel, item.releaseVersion, preview)
                                    .filter(String::isNotBlank).joinToString(" · "),
                                badgeText = item.statusLabel,
                                onClick = { openedItem = item },
                            )
                        }
                    }
                    if (result.page.items.isNotEmpty()) item(key = "feedback-pagination") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一页") }
                            TextButton(onClick = { refresh++ }) { Text("刷新") }
                            TextButton(onClick = { page++ }, enabled = (page + 1) * FeedbackClient.PageSize < result.page.total) {
                                Text("下一页")
                            }
                        }
                    }
                }
            }
        }
    }

    openedItem?.let { item ->
        val close = { openedItem = null }
        val detail = buildString {
            append(listOf(item.kindLabel, item.statusLabel, item.releaseVersion).filter(String::isNotBlank).joinToString(" · "))
            append("\n\n").append(item.description)
            if (item.note.isNotBlank()) append("\n\n开发者回复\n").append(item.note)
        }
        LiquidAlertDialog(
            title = item.title,
            message = detail,
            actions = listOf(LiquidAlertAction("关闭", LiquidAlertActionStyle.Primary, onClick = close)),
            config = config,
            onDismissRequest = close,
        )
    }
}

@Composable
private fun FeedbackListMessage(message: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun feedbackError(error: Exception): String =
    (error as? FeedbackRequestException)?.message ?: "暂时无法连接反馈服务，请重试"
