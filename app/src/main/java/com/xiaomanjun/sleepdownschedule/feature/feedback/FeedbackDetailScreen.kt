package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.theme.CouiTheme
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val FeedbackItemExtra = "feedback_detail_item"

internal fun Intent.putFeedbackDetailItem(item: FeedbackItem): Intent =
    putExtra(FeedbackItemExtra, Json.encodeToString(FeedbackItem.serializer(), item))

internal fun Intent.feedbackDetailItem(): FeedbackItem? =
    getStringExtra(FeedbackItemExtra)?.let { payload ->
        runCatching { Json.decodeFromString(FeedbackItem.serializer(), payload) }.getOrNull()
    }

@Composable
internal fun FeedbackDetailPage(
    item: FeedbackItem,
    config: ScheduleConfigEntity,
    client: FeedbackService,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable(item.id) { mutableStateOf(false) }
    var deleting by remember(item.id) { mutableStateOf(false) }
    BackHandler(enabled = deleting) {}
    FeedbackDetailScreen(item, config, deleting,
        onBack = { if (!deleting) onBack() },
        onDelete = { if (item.owned && !deleting) confirming = true },
    )
    if (confirming) {
        LiquidAlertDialog(
            title = "删除这条反馈？",
            message = "删除后无法恢复。已公开的条目也会从列表移除。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { confirming = false },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    confirming = false
                    deleting = true
                    scope.launch {
                        try {
                            client.delete(item.id)
                            Toast.makeText(context, "反馈已删除", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Toast.makeText(context, feedbackError(error), Toast.LENGTH_SHORT).show()
                        } finally {
                            deleting = false
                        }
                    }
                },
            ),
            config = config,
            onDismissRequest = { confirming = false },
        )
    }
}

@Composable
private fun FeedbackDetailScreen(
    item: FeedbackItem,
    config: ScheduleConfigEntity,
    deleting: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = appUsesDarkTheme(config)
    val statusColor = feedbackStatusColor(item, dark)
    CouiSettingsPageScaffold(
        title = "反馈详情",
        config = config,
        showBack = true,
        onBack = onBack,
        topBarActions = { backdrop ->
            if (item.owned && !deleting) {
                CouiSettingsToolbarButton(backdrop, onClick = onDelete) {
                    Icon(CouiIcons.Delete, "删除反馈", Modifier.size(20.dp), tint = CouiTheme.colorScheme.onSurface)
                }
            }
        },
    ) { padding, backdrop ->
        SleepDownSecondaryPageList(
            modifier = Modifier.fillMaxSize(),
            style = SleepDownSecondaryPageStyle.Content,
            contentTopPadding = padding.calculateTopPadding() + 8.dp,
            contentBottomPadding = padding.calculateBottomPadding() + 16.dp,
        ) {
            item(key = "feedback-detail") {
                SettingsGroup(backdrop, config) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FeedbackStatusBadge(item, statusColor)
                            Text(listOf(item.kindLabel, item.releaseVersion).filter(String::isNotBlank).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                        FeedbackProgress(item.stage, statusColor, dark)
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        SelectionContainer { Text(item.description, style = MaterialTheme.typography.bodyLarge) }
                        if (item.owned && item.contact.isNotBlank()) {
                            Text("联系方式 · ${item.contact}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (item.owned && item.note.isNotBlank()) item(key = "feedback-reply") {
                SettingsGroup(backdrop, config) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("开发者回复", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(item.note, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
    }
}
