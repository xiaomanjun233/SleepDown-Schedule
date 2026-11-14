package com.xiaomanjun.sleepdownschedule.feature.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

@Composable
internal fun FeedbackDetailScreen(
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
                    Icon(Icons.Outlined.Delete, "删除反馈", Modifier.size(22.dp))
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
