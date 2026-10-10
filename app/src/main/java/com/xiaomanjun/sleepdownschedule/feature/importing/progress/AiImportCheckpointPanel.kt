package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogCapsuleField
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgress
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportCheckpoint

/** A selected, immutable artifact, separate from the running transcript and incomplete tokens. */
@Composable
internal fun AiImportCheckpointPanel(
    progress: AiEduImportProgress,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onSelect: (String) -> Result<Unit>,
    onSaveJson: (String, String) -> Result<Unit>
) {
    var editing by remember(progress.taskId) { mutableStateOf<AiImportCheckpoint?>(null) }
    var selectionError by remember(progress.taskId) { mutableStateOf<String?>(null) }
    val selected = progress.checkpoints.firstOrNull { it.id == progress.selectedCheckpointId }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("课表工作区", color = textColor, style = MaterialTheme.typography.titleMedium)
        Text(
            if (progress.checkpointSelectionLocked) "已固定所选阶段，后续结果不会覆盖。继续向 AI 提问会使用此阶段。"
            else "每个阶段都已通过本地校验。选择任意阶段预览、编辑 JSON 或确认导入。",
            color = textColor.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            progress.checkpoints.forEachIndexed { index, checkpoint ->
                val isSelected = selected?.id == checkpoint.id
                Column(
                    Modifier.widthIn(max = 220.dp).background(textColor.copy(alpha = if (isSelected) 0.13f else 0.055f), RoundedCornerShape(14.dp))
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) {
                            selectionError = onSelect(checkpoint.id).exceptionOrNull()?.message
                        }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("阶段 ${index + 1}${if (isSelected) " · 已选" else ""}", color = textColor,
                        style = MaterialTheme.typography.labelLarge)
                    Text(checkpoint.label, color = textColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${checkpoint.courseCount} 门课程 · 已校验", color = textColor.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        selected?.let { checkpoint ->
            Text("查看 / 编辑此阶段 JSON", color = textColor, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(role = Role.Button) { editing = checkpoint }.padding(vertical = 12.dp))
        }
        (selectionError ?: progress.checkpointNotice)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    editing?.let { checkpoint ->
        // Capture the opened checkpoint once. New model tokens/selection changes cannot reset edits.
        var json by remember(checkpoint.id) { mutableStateOf(checkpoint.payload) }
        var error by remember(checkpoint.id) { mutableStateOf<String?>(null) }
        LiquidAlertDialog(
            title = "编辑阶段 JSON",
            message = "保存会创建新阶段。只在本机校验；修改后继续对话才会发送给所选模型。",
            backdrop = backdrop,
            config = config,
            messageMaxHeight = 440.dp,
            onDismissRequest = { editing = null },
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { editing = null }),
                LiquidAlertAction("校验并保存阶段", LiquidAlertActionStyle.Primary, dismissOnClick = false, onClick = {
                    onSaveJson(checkpoint.id, json).fold(
                        onSuccess = { editing = null },
                        onFailure = { error = it.message ?: "JSON 校验失败，请检查后重试。" }
                    )
                })
            ),
            messageContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (progress.finished) "保存为新阶段。" else "保存为新阶段并暂停当前解析。") +
                        "只在本机校验；继续对话才会发送给所选模型。", color = textColor.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall)
                    DialogCapsuleField(
                        value = json,
                        onValueChange = { candidate ->
                            if (candidate.toByteArray(Charsets.UTF_8).size <= 256 * 1024) {
                                json = candidate
                                error = null
                            } else error = "JSON 不能超过 256 KiB，请缩小内容。"
                        },
                        placeholder = "完整课表 JSON",
                        config = config,
                        minLines = 8,
                        fieldTextColor = textColor,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        )
    }
}
