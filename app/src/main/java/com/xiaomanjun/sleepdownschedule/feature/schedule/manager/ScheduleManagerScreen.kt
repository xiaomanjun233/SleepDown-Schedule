package com.xiaomanjun.sleepdownschedule.feature.schedule.manager

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.SettingsDetailActivity
import com.xiaomanjun.sleepdownschedule.app.ui.SettingsDetailPageExtra
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CenterLiquidDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogCapsuleField
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidDialogHeader
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity

@Composable
fun ScheduleManagerScreen(
    state: AppState,
    backdrop: Backdrop?,
    onCreateSchedule: (String) -> Unit,
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit,
    onRenameSchedule: (Int, String) -> Unit,
    onDeleteSchedule: (Int) -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val topPadding = detailContentTopPadding()
    val coursesPerSchedule = remember(state.allCourses) {
        state.allCourses.groupBy { it.scheduleId }.mapValues { it.value.size }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "多课表管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                SettingsActionButton("新建课表", backdrop, onClick = { showCreateDialog = true })
            }
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                state.schedules.forEachIndexed { idx, profile ->
                    val courseCount = coursesPerSchedule[profile.id] ?: 0
                    val isActive = profile.isActive
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                context.openRegisteredActivity(
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(context, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Schedule.name)
                                        .putExtra(ScheduleCustomizeIdExtra, profile.id)
                                )
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isActive) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedRectangle(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "$courseCount 门课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isActive) {
                                SettingsActionButton("设为", backdrop, onClick = {
                                    onActivateSchedule(profile.id, null)
                                })
                            }
                            SettingsActionButton("重命名", backdrop, onClick = {
                                renameTarget = Pair(profile.id, profile.name)
                            })
                            if (!isActive && state.schedules.size > 1) {
                                SettingsActionButton("删除", backdrop, onClick = {
                                    deleteTarget = Pair(profile.id, profile.name)
                                }, destructive = true)
                            }
                        }
                    }
                    if (idx != state.schedules.lastIndex) SettingsDivider()
                }
            }
        }
    }

    if (showCreateDialog) {
        ScheduleNameDialog(
            title = "新建课表",
            initialName = "",
            backdrop = backdrop,
            config = state.config,
            onConfirm = { name ->
                showCreateDialog = false
                if (name.isNotBlank()) onCreateSchedule(name)
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { (id, name) ->
        ScheduleNameDialog(
            title = "重命名课表",
            initialName = name,
            backdrop = backdrop,


            config = state.config,
            onConfirm = { newName ->
                renameTarget = null
                if (newName.isNotBlank()) onRenameSchedule(id, newName)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { (id, name) ->
        LiquidAlertDialog(
            title = "删除课表",
            message = "确定要删除「$name」吗？该课表下的所有课程都会被删除，且无法恢复。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { deleteTarget = null },
                LiquidAlertAction("确认删除", LiquidAlertActionStyle.Destructive) {
                    val targetId = id
                    deleteTarget = null
                    onDeleteSchedule(targetId)
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { deleteTarget = null }
        )
    }
}

@Composable
fun ScheduleNameDialog(
    title: String,
    initialName: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (LocalHomeMaterialControls.current.NameDialog(
            title, initialName, appUsesDarkTheme(config), null, onDismiss, onConfirm)) return
    var name by remember { mutableStateOf(initialName) }
    val textColor = glassForegroundColor(config)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CenterLiquidDialog(backdrop = backdrop, config = config) {
            LiquidDialogHeader(title, onDismiss, backdrop, config, onConfirm = { onConfirm(name.trim()) })
            DialogCapsuleField(name, { name = it }, "课表名称", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}
