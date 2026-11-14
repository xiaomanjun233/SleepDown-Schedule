package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater

@Composable
internal fun AutoUpdateSettingsScreen(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
) {
    val context = LocalContext.current
    var automaticCheck by remember(config.autoCheckUpdates) { mutableStateOf(config.autoCheckUpdates) }
    var includeBeta by remember(context) { mutableStateOf(GiteeAppUpdater.includesBeta(context)) }
    SleepDownSecondaryPageList(
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = detailContentBottomPadding(),
    ) {
        item(key = "app-update-preferences") {
            SettingsGroup(backdrop, config) {
                SettingsToggleRow(
                    title = "自动检查更新",
                    subtitle = "每天首次打开应用时检查 Gitee 上的新版本",
                    checked = automaticCheck,
                    backdrop = backdrop,
                    onCheckedChange = {
                        automaticCheck = it
                        onUpdateConfig(config.copy(autoCheckUpdates = it))
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "接收 Beta 版更新",
                    subtitle = if (includeBeta) "更新渠道：正式版与 Beta 版；同版本正式版发布后也会提示"
                        else "更新渠道：仅正式版；开启后可提前体验 Beta 版",
                    checked = includeBeta,
                    backdrop = backdrop,
                    onCheckedChange = {
                        includeBeta = it
                        GiteeAppUpdater.setIncludesBeta(context, it)
                    },
                )
            }
        }
    }
}
