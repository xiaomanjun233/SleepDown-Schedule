package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.historyConversationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AiEduImportProgressPage(
    config: ScheduleConfigEntity, onClose: () -> Unit, taskId: String? = null,
    onImportSubmitted: () -> Unit = onClose,
    onScreenModeRequested: () -> Unit = AiEduImportProgressSession::useScreenMode,
    historicalProgress: AiEduImportProgress? = null, historicalDraft: ImportDraft? = null,
    historicalEntryId: String? = null, showHistoryAction: Boolean = true,
    onImportRequested: ((ImportDraft, Boolean) -> Unit)? = null
) {
    AiImportConversationTheme(config) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val live by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
        var entries by remember { mutableStateOf(emptyList<AiImportHistoryEntry>()) }
        var selected by remember { mutableStateOf<Pair<AiImportHistoryEntry, ImportDraft>?>(null) }
        var showCurrent by remember(historicalEntryId) { mutableStateOf(historicalProgress == null) }
        val selectedId = if (showCurrent) null else selected?.first?.id ?: historicalEntryId
        LaunchedEffect(live?.taskId, live?.finished, live?.checkpointRevision) {
            entries = withContext(Dispatchers.IO) { AiImportHistoryStore.load(context) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (expanded) {
                    val colors = LocalAiImportTextColors.current
                    Column(Modifier.width(260.dp).fillMaxHeight()
                        .background(settingsPageBackground(settingsVisualConfig(config)))
                        .statusBarsPadding().padding(horizontal = 12.dp)) {
                        Text("导入历史", color = colors.primary, style = AiImportTextStyles.Title,
                            modifier = Modifier.padding(12.dp, 20.dp))
                        SidebarRow("当前导入", selectedId == null) { showCurrent = true }
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(entries, key = { it.id }) { entry ->
                                SidebarRow(entry.title, selectedId == entry.id, entry.sourceSummary) {
                                    scope.launch {
                                        val loaded = withContext(Dispatchers.IO) { AiImportHistoryStore.loadEntry(context, entry.id) }
                                        val result = loaded?.let { AiImportHistoryStore.restore(it, config) }
                                            ?: Result.failure(IllegalStateException("这条导入记录已不存在"))
                                        result.onSuccess { selected = checkNotNull(loaded) to it; showCurrent = false }
                                            .onFailure { android.widget.Toast.makeText(context, it.message ?: "历史读取失败", android.widget.Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            }
                            if (entries.isEmpty()) item {
                                Text("还没有导入记录", color = colors.secondary, style = AiImportTextStyles.Secondary,
                                    modifier = Modifier.padding(12.dp, 16.dp))
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val history = selected.takeUnless { showCurrent }
                    key(selectedId ?: "current") {
                        AiEduImportConversationPane(config, onClose,
                            taskId = if (showCurrent) taskId else null,
                            onImportSubmitted = onImportSubmitted, onScreenModeRequested = onScreenModeRequested,
                            historicalProgress = if (showCurrent) null else history?.let { historyConversationProgress(it.first, it.second) } ?: historicalProgress,
                            historicalDraft = if (showCurrent) null else history?.second ?: historicalDraft,
                            historicalEntryId = selectedId, showHistoryAction = showHistoryAction && !expanded,
                            onImportRequested = onImportRequested)
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    val colors = LocalAiImportTextColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(colors.primary.copy(alpha = if (selected) 0.075f else 0f))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(title, color = colors.primary, style = AiImportTextStyles.SmallTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.secondary, style = AiImportTextStyles.Caption,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
