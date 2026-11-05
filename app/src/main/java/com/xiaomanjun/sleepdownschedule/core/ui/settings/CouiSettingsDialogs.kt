package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.*
import com.itos.coui.theme.CouiTheme
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import kotlinx.coroutines.delay

/** 系统窗口负责退出画面；交接回调等上游 bottom_exit.xml 的 250ms 完成后运行。 */
@Composable
private fun CouiSettingsDialogCompletion(show: Boolean, onFinished: (() -> Unit)?) {
    var wasShown by remember { mutableStateOf(false) }
    val latest = rememberUpdatedState(onFinished)
    val context = LocalContext.current
    LaunchedEffect(show) {
        if (show) wasShown = true
        else if (wasShown) {
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
            delay((250 * scale).toLong().coerceAtLeast(0))
            wasShown = false
            latest.value?.invoke()
        }
    }
}

@Composable
internal fun CouiSettingsContentDialog(show: Boolean, onDismiss: () -> Unit,
    onFinished: (() -> Unit)?, content: @Composable () -> Unit) {
    CouiSettingsDialogCompletion(show, onFinished)
    if (show) ContentDialog(onDismissRequest = onDismiss, placement = CouiDialogPlacement.Bottom) {
        Box(Modifier.padding(24.dp)) { content() }
    }
}

@Composable
internal fun CouiSettingsAlert(title: String, message: String, actions: List<LiquidAlertAction>,
    onDismiss: () -> Unit, scrollable: Boolean, messageMaxHeight: androidx.compose.ui.unit.Dp,
    messageContent: (@Composable () -> Unit)?) {
    var visible by remember { mutableStateOf(true) }
    var completion by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun close(action: () -> Unit) { if (visible) { completion = action; visible = false } }
    CouiSettingsDialogCompletion(visible) { completion?.invoke(); completion = null }
    if (!visible) return
    val accent = CouiTheme.colorScheme.accent
    val dark = CouiTheme.isDark
    val dialogColors = CouiDialogDefaults.themeColors()
    fun LiquidAlertAction.couiAction(): CouiDialogAction = CouiDialogAction(label,
        onClick = { if (dismissOnClick) close(onClick) else onClick() }, enabled = enabled,
        style = when (style) {
            LiquidAlertActionStyle.Primary -> CouiDialogActionStyle.Primary
            else -> CouiDialogActionStyle.Secondary
        }, colors = when (style) {
            LiquidAlertActionStyle.Primary -> CouiDialogDefaults.primaryActionColors(accent)
            LiquidAlertActionStyle.Destructive -> CouiDialogActionColors(
                androidx.compose.ui.graphics.Color(if (dark) 0xFF353535 else 0xFFEBEBEB), dialogColors.destructive,
                androidx.compose.ui.graphics.Color.Transparent, dialogColors.disabledAction)
            else -> null
        })
    val confirm = actions.firstOrNull { it.style == LiquidAlertActionStyle.Primary }
        ?: actions.firstOrNull { it.style != LiquidAlertActionStyle.Cancel } ?: actions.firstOrNull()
    val dismiss = actions.firstOrNull { it !== confirm && it.style == LiquidAlertActionStyle.Cancel }
    val neutral = actions.firstOrNull { it !== confirm && it !== dismiss }
    AlertDialog(title = title, message = message.takeIf { messageContent == null },
        onDismissRequest = { close(onDismiss) }, placement = CouiDialogPlacement.Bottom,
        confirmAction = confirm?.couiAction() ?: CouiDialogAction("关闭", { close(onDismiss) }),
        dismissAction = dismiss?.couiAction(), neutralAction = neutral?.couiAction()) {
        messageContent?.let {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)
                .then(if (scrollable) Modifier.heightIn(max = messageMaxHeight).verticalScroll(rememberScrollState()) else Modifier)) { it() }
        }
    }
}
