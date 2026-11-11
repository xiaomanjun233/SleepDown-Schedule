package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.sheet.ModalBottomSheet
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiLiquidMaterial
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsEditorSheetToolbar
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
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
    val pageColor = settingsPageBackground(config)
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
        contentDrawsBehindNavigationBar = true,
        colors = CouiSheetDefaults.colors(dark).copy(surface = pageColor),
        material = material,
        backdrop = windowBackdrop,
        isolateContent = false,
    ) {
        val density = LocalDensity.current
        val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val sheetScene = rememberCenteredDialogSceneBackdrop("experimental-notification-help")
        val scrollBackdrop = if (Build.VERSION.SDK_INT >= 33 && !AppMaterialPreferences.isSuperPerformance) {
            rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "experimental-notification-help-scroll") {
                drawRect(pageColor)
                drawContent()
            }
        } else null
        val scrollProducer = if (scrollBackdrop != null) Modifier.glassBackdropProducer(scrollBackdrop) else Modifier
        var toolbarHeightPx by remember { mutableIntStateOf(0) }
        val toolbarHeight = if (toolbarHeightPx > 0) {
            with(density) { toolbarHeightPx.toDp() }
        } else {
            CouiSheetDefaults.toolbarTopSpacing + CouiSheetDefaults.toolbarHeight
        }

        Box(Modifier.fillMaxSize().centeredDialogSceneProducer(sheetScene)) {
            CompositionLocalProvider(
                LocalCenteredDialogSceneBackdrop provides sheetScene,
                LocalSettingsPopupBackdrop provides sheetScene,
            ) {
                SleepDownSecondaryPageList(
                    modifier = Modifier.fillMaxSize().then(scrollProducer),
                    contentTopPadding = toolbarHeight + 4.dp,
                    contentBottomPadding = 16.dp + navigationBarPadding,
                ) { content(sheetScene) }
            }
            ProgressiveBackdropBlur(
                backdrop = scrollBackdrop,
                modifier = Modifier.align(Alignment.TopCenter),
                tintColor = pageColor,
                height = toolbarHeight + 11.dp,
                fallbackTintStops = listOf(
                    0f to pageColor.copy(alpha = .42f),
                    .68f to pageColor.copy(alpha = .18f),
                    1f to Color.Transparent,
                ),
            )
            Box(Modifier.fillMaxWidth().onSizeChanged { toolbarHeightPx = it.height }) {
                SettingsEditorSheetToolbar(
                    title = "教程与诊断",
                    dark = dark,
                    backdrop = scrollBackdrop ?: sheetScene,
                    enabled = visible,
                    onCancel = onDismiss,
                    actionLabel = "刷新",
                    onAction = onRefresh,
                    actionEnabled = !refreshing,
                    cancelLabel = "关闭",
                )
            }
        }
    }
}
