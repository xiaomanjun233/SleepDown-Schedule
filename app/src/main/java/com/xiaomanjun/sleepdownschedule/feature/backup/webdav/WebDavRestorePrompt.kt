package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*

@Composable
internal fun WebDavRestorePrompt(config: ScheduleConfigEntity, backdrop: Backdrop?, canShow: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val state by WebDavAutomation.state.collectAsState()
    val entry = state.pending ?: return
    if (!state.restoreCheck || !canShow || entry.changeKey() == state.acknowledged) return
    LiquidAlertDialog(title = "发现更新的远端备份", message = "${entry.name}\n可前往 WebDAV 页面下载预览，确认后再恢复。本机数据尚未改变。",
        actions = listOf(
            LiquidAlertAction("稍后", LiquidAlertActionStyle.Secondary) { WebDavAutomation.acknowledge(context, entry) },
            LiquidAlertAction("查看备份", LiquidAlertActionStyle.Primary) { WebDavAutomation.acknowledge(context, entry); onOpen() }),
        backdrop = backdrop, config = config, onDismissRequest = { WebDavAutomation.acknowledge(context, entry) })
}
