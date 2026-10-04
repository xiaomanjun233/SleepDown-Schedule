package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.data.repository.PeriodSchemeLibraryStore
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun PeriodSchemeLibraryControls(
    config: ScheduleConfigEntity, draft: SchedulePeriodSchemesDraft, backdrop: Backdrop?,
    onDraftChange: (SchedulePeriodSchemesDraft) -> Unit, onCountsChange: (Int, Int, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(emptyList<SavedPeriodScheme>()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SavedPeriodScheme?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: return
    val selected = library.firstOrNull { it.id == selectedId }
    LaunchedEffect(context) {
        try {
            library = withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.load(context) }
            loaded = true
        } catch (failure: Exception) { error = "作息库读取失败：${failure.message}" }
    }
    fun mutate(operation: () -> Unit, onSuccess: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                library = withContext(Dispatchers.IO) { operation(); PeriodSchemeLibraryStore.load(context) }
                onSuccess()
            } catch (failure: Exception) { error = failure.message ?: "作息库保存失败" }
            finally { busy = false }
        }
    }
    SettingsDivider()
    SettingsPickerValueRow("保存当前作息到库", "跨课表复用", enabled = loaded && !busy, onClick = {
        name = active.scheme.name
        error = null
        showSave = true
    })
    SettingsDivider()
    SettingsPickerValueRow("从作息库套用", "${library.size} 套", enabled = loaded && !busy, onClick = {
        selectedId = null
        renaming = false
        error = null
        showLibrary = true
    })
    (error ?: message)?.let {
        Text(it, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
    }
    SleepDownPickerDialog(showSave, "保存到作息库", { if (!busy) showSave = false }, popupBackdrop, config,
        contentPadding = PaddingValues(14.dp)) {
        Text("保存一份独立作息，可在其他课表直接套用。", style = MaterialTheme.typography.bodyMedium)
        DialogCapsuleField(name, { name = it.take(60) }, "作息名称", config)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickSheetLiquidAction("取消", !busy, popupBackdrop, config, Modifier.weight(1f)) { showSave = false }
            QuickSheetLiquidAction("保存", name.isNotBlank() && !busy, popupBackdrop, config, Modifier.weight(1f), primary = true) {
                val snapshot = runCatching { savePeriodSchemeSnapshot(UUID.randomUUID().toString(), name, config, active) }
                    .getOrElse { error = it.message; return@QuickSheetLiquidAction }
                mutate({ PeriodSchemeLibraryStore.save(context, snapshot) }) {
                    showSave = false
                    message = "已保存到作息库"
                }
            }
        }
    }
    SleepDownPickerDialog(showLibrary, if (renaming) "重命名作息" else "作息库",
        { if (!busy) showLibrary = false }, popupBackdrop, config, contentPadding = PaddingValues(14.dp)) {
        if (renaming && selected != null) {
            DialogCapsuleField(name, { name = it.take(60) }, "作息名称", config)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickSheetLiquidAction("取消", !busy, popupBackdrop, config, Modifier.weight(1f)) { renaming = false }
                QuickSheetLiquidAction("保存", name.isNotBlank() && !busy, popupBackdrop, config, Modifier.weight(1f), primary = true) {
                    mutate({ PeriodSchemeLibraryStore.save(context, selected.copy(name = name.trim())) }) { renaming = false }
                }
            }
        } else if (selected == null) {
            Text(if (library.isEmpty()) "作息库还没有内容。先将当前作息保存到库，再到其他课表套用。"
                else "套用会新增一套作息，退出详细设置时确认保存。", style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                library.forEach { saved ->
                    SettingsPickerValueRow(saved.name, "${saved.times.size} 节", enabled = !busy,
                        onClick = { selectedId = saved.id; error = null })
                }
            }
            QuickSheetLiquidAction("关闭", !busy, popupBackdrop, config, Modifier.fillMaxWidth()) { showLibrary = false }
        } else {
            SettingsPickerValueRow("返回作息列表", "", enabled = !busy, onClick = { selectedId = null })
            Text(selected.name, style = MaterialTheme.typography.titleMedium)
            Text("上午 ${selected.morningPeriodCount} · 中午 ${selected.noonPeriodCount} · 下午 ${selected.afternoonPeriodCount} · 晚上 ${selected.eveningPeriodCount}",
                style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                selected.times.forEach { Text("第 ${it.periodIndex} 节  ${it.startTime} – ${it.endTime}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
            }
            val compatible = selected.times.size == config.totalPeriodCount()
            if (!compatible) Text("此作息有 ${selected.times.size} 节，当前课表有 ${config.totalPeriodCount()} 节。请先在详细节次编辑中调整节数。",
                style = MaterialTheme.typography.bodySmall)
            LiquidAlertActions(listOf(
                LiquidAlertAction("套用到当前课表", LiquidAlertActionStyle.Primary, enabled = compatible && !busy, onClick = {
                    try {
                        val applied = applySavedPeriodScheme(selected, config, draft)
                        onCountsChange(applied.config.morningPeriodCount, applied.config.noonPeriodCount,
                            applied.config.afternoonPeriodCount, applied.config.eveningPeriodCount)
                        onDraftChange(applied.draft)
                        showLibrary = false
                        message = "已套用到草稿，退出详细设置时确认保存"
                    } catch (failure: Exception) { error = failure.message }
                }),
                LiquidAlertAction("重命名", LiquidAlertActionStyle.Secondary, enabled = !busy, onClick = {
                    name = selected.name; renaming = true
                }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, enabled = !busy, onClick = {
                    showLibrary = false; deleting = selected
                })
            ), popupBackdrop, config)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    deleting?.let { saved ->
        LiquidAlertDialog("删除库中作息", "删除“${saved.name}”？已套用到课表的作息会继续保留。",
            listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { deleting = null; showLibrary = true }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, onClick = {
                    deleting = null
                    mutate({ PeriodSchemeLibraryStore.delete(context, saved.id) }) { selectedId = null; showLibrary = true }
                })), popupBackdrop, config, { deleting = null; showLibrary = true })
    }
}
