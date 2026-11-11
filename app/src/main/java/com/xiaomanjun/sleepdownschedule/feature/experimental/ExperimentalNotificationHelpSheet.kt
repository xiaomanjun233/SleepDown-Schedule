package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.sheet.ModalBottomSheet
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiLiquidMaterial
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsEditorSheetToolbar
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

@Composable
internal fun ExperimentalNotificationHelpSheet(
    visible: Boolean,
    config: ScheduleConfigEntity,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.(Backdrop) -> Unit,
) {
    val dark = appUsesDarkTheme(config)
    val windowBackdrop = rememberCouiWindowBackdrop()
    val material = if (Build.VERSION.SDK_INT >= 33 && windowBackdrop != null &&
        LocalCouiMaterialEnabled.current && !LocalCouiLiquidMaterial.current
    ) CouiSheetDefaults.material(dark) else null
    ModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "教程与诊断",
        dismissDescription = "关闭教程与诊断",
        dark = dark,
        sheetHeight = LocalConfiguration.current.screenHeightDp.dp,
        showDragHandle = false,
        colors = CouiSheetDefaults.colors(dark).copy(surface = settingsPageBackground(config)),
        material = material,
        backdrop = windowBackdrop,
        isolateContent = false,
    ) {
        val sheetScene = rememberCenteredDialogSceneBackdrop("experimental-notification-help")
        Column(Modifier.fillMaxSize().centeredDialogSceneProducer(sheetScene)) {
            SettingsEditorSheetToolbar(
                title = "教程与诊断",
                dark = dark,
                backdrop = sheetScene,
                enabled = visible,
                onCancel = onDismiss,
                actionLabel = "刷新",
                onAction = onRefresh,
                actionEnabled = !refreshing,
                cancelLabel = "关闭",
            )
            CompositionLocalProvider(
                LocalCenteredDialogSceneBackdrop provides sheetScene,
                LocalSettingsPopupBackdrop provides sheetScene,
            ) {
                SleepDownSecondaryPageList(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentTopPadding = 4.dp,
                    contentBottomPadding = 16.dp,
                ) { content(sheetScene) }
            }
        }
    }
}
