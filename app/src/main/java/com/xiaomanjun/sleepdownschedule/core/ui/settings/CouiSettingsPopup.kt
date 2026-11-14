package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity

/** COUI 负责设置行，原改版 Miuix 负责锚点展开、玻璃面板和滑动选择。 */
@Composable
internal fun CouiSettingsDropdownPreference(
    items: List<String>, selectedIndex: Int, title: String, modifier: Modifier,
    summary: String?, badge: String?, padding: PaddingValues, maxHeight: Dp,
    enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity,
    onExpandedChange: (Boolean) -> Unit, onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var transferred by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(expanded) {
        if (expanded) {
            repeat(2) { withFrameNanos {} }
            transferred = true
        }
    }
    fun expand(value: Boolean) {
        if (expanded != value) { expanded = value; onExpandedChange(value) }
    }
    val trigger: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = items.getOrNull(selectedIndex).orEmpty(), kind = CouiTextKind.PreferenceValue,
                color = CouiTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp))
            Icon(CouiIcons.Popup, null, Modifier.padding(start = 4.dp).size(14.dp, 24.dp),
                tint = CouiTheme.colorScheme.onSurfaceVariant)
        }
    }
    Box(modifier) {
        Item(enabled = enabled && items.isNotEmpty(),
            contentPadding = padding,
            onClick = {
                expand(!expanded)
                if (expanded) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
            trailing = {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.graphicsLayer { alpha = if (transferred) 0f else 1f }) { trigger() }
                    SettingsMiuixDropdownPopup(items = items, selectedIndex = selectedIndex,
                        show = expanded, backdrop = backdrop, config = config, maxHeight = maxHeight,
                        onDismiss = { expand(false) }, onDismissFinished = { transferred = false },
                        onSelected = onSelected, collapseContent = trigger)
                }
            }, summary = summary?.takeIf { it.isNotBlank() }?.let { {
                Text(text = it, kind = CouiTextKind.PreferenceSummary,
                    color = CouiTheme.colorScheme.onSurfaceVariant)
            } }) {
            Row {
                Text(text = title, kind = CouiTextKind.PreferenceTitle, modifier = Modifier.weight(1f, false))
                badge?.let { PreferenceBadge(it) }
            }
        }
    }
}
