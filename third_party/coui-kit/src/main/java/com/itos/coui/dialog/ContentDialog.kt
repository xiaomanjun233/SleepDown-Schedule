package com.itos.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.view.WindowManager
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalConfiguration
import com.itos.coui.backdrop.CouiBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.theme.CouiTheme

/** SleepDown 本地公开入口：复用上游窗口、轮廓、安全区与动效，正文由宿主装配。 */
@Composable
fun ContentDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    placement: CouiDialogPlacement = adaptiveDialogPlacement(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    dark: Boolean = CouiTheme.isDark,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    visible: Boolean? = null,
    onDismissFinished: (() -> Unit)? = null,
    title: String? = null,
    titleAction: (@Composable () -> Unit)? = null,
    confirmAction: CouiDialogAction? = null,
    dismissAction: CouiDialogAction? = null,
    neutralAction: CouiDialogAction? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollable: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    adjustForIme: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalCouiDialogSoftInputMode provides if (adjustForIme)
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE else LocalCouiDialogSoftInputMode.current) {
    CouiDialogWindow(modifier = modifier, onDismissRequest = onDismissRequest,
        colors = colors, dark = dark, placement = placement, properties = properties,
        material = material, backdrop = backdrop, visible = visible, onDismissFinished = onDismissFinished) {
        Column(Modifier.fillMaxWidth().semantics { if (visible == false) hideFromAccessibility() }
            .pointerInput(visible) { if (visible == false) awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            } }) {
            if (!title.isNullOrBlank()) DialogHeading(title, colors, titleAction)
            Box(Modifier.weight(1f, fill = false).then(if (scrollable)
                Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(contentPadding), content = content)
            footer?.invoke()
            if (confirmAction != null) DialogActions(confirmAction, dismissAction, neutralAction,
                colors, dark, material != null) { }
        }
    }
    }
}
