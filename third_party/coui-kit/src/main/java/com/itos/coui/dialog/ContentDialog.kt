package com.itos.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.theme.CouiTheme

/** SleepDown 本地公开入口：复用上游窗口、轮廓、安全区与动效，正文由宿主装配。 */
@Composable
fun ContentDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    placement: CouiDialogPlacement = CouiDialogPlacement.Center,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable BoxScope.() -> Unit,
) {
    CouiDialogWindow(modifier = modifier, onDismissRequest = onDismissRequest,
        colors = CouiDialogDefaults.themeColors(), dark = CouiTheme.isDark,
        placement = placement, properties = properties, content = content)
}
