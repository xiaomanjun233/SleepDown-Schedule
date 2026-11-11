package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.itos.coui.sheet.rememberCouiSheetSurfaceBackdrop
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.effect.CouiFloatingMaterial
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.sheet.SheetToolbar
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.TopAppBarButton
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogButtonRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton

@Composable
internal fun SettingsEditorSheetToolbar(
    title: String,
    dark: Boolean,
    backdrop: Backdrop,
    enabled: Boolean,
    onCancel: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    cancelLabel: String = "取消",
) {
    if (Build.VERSION.SDK_INT >= 34) {
        val sampledMaterial: (Boolean) -> CouiFloatingMaterial = remember(dark) {
            { capsule -> CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule).copy(blurRadius = 25f) }
        }
        val sampledContent = if (!AppMaterialPreferences.isSuperPerformance)
            com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop(backdrop) else null
        val buttonBackdrop = sampledContent ?: rememberCouiSheetSurfaceBackdrop()
        SheetToolbar(title = title, dark = dark, navigation = { style ->
            TopAppBarButton(onClick = onCancel, enabled = enabled, text = true, style = if (sampledContent != null) style.copy(material = sampledMaterial) else style,
                backdrop = buttonBackdrop) {
                Text(cancelLabel, color = LocalCouiContentColor.current, style = LocalCouiTextStyle.current)
            }
        }, actions = { style ->
            TopAppBarButton(onClick = onAction, enabled = enabled && actionEnabled, text = true, style = if (sampledContent != null) style.copy(material = sampledMaterial) else style,
                backdrop = buttonBackdrop) {
                Text(actionLabel, color = LocalCouiContentColor.current, style = LocalCouiTextStyle.current)
            }
        })
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogLiquidButton(backdrop, cancelLabel, onCancel, monochromeNeutral = true, enabled = enabled)
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            DialogLiquidButton(backdrop, actionLabel, onAction, role = DialogButtonRole.Confirm, enabled = enabled && actionEnabled)
        }
    }
}
