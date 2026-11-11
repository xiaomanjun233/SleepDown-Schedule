package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor

/**
 * Miuix OverlayDialog with the app's standard glass quick-sheet style and its original built-in
 * dialog animation (no source-aware seamless surface transform).
 */
@Composable
internal fun HomeJumpWeekDialog(
    show: Boolean,
    initialWeek: Int,
    totalWeeks: Int,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    onDismissFinished: (Int?) -> Unit
) {
    val boundedTotalWeeks = totalWeeks.coerceAtLeast(1)
    var selectedWeek by remember(initialWeek, boundedTotalWeeks) {
        mutableStateOf(initialWeek.coerceIn(1, boundedTotalWeeks))
    }
    var confirmedWeek by remember { mutableStateOf<Int?>(null) }
    val latestDismissFinished by rememberUpdatedState(onDismissFinished)

    // centeredDialogBackdropModifier chooses this centered card's light/dark surface from the app
    // theme, independently of the wallpaper brightness used by homepage glass. Text and picker
    // colors must therefore invert against the card itself, not against the sampled wallpaper.
    val textColor = appPanelForegroundColor(config)
    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
        selectedTextColor = textColor,
        unselectedTextColor = textColor.copy(alpha = 0.34f),
        disabledSelectedTextColor = textColor.copy(alpha = 0.55f),
        disabledUnselectedTextColor = textColor.copy(alpha = 0.22f)
    )
    SleepDownPickerDialog(
        show = show,
        title = "跳转周数",
        onDismissRequest = {
            confirmedWeek = null
            onDismissRequest()
        },
        onDismissFinished = {
            latestDismissFinished(confirmedWeek)
        },
        config = config,

        actions = listOf(
            LiquidAlertAction("取消",
                LiquidAlertActionStyle.Cancel, enabled = true,
                dismissOnClick = false, onClick = action@{
                    confirmedWeek = null
                    onDismissRequest()
                }),
            LiquidAlertAction("跳转",
                LiquidAlertActionStyle.Primary, enabled = true,
                dismissOnClick = false, onClick = action@{
                    confirmedWeek = selectedWeek
                    onDismissRequest()
                })
        )
    ) {
        Text(
            text = "选择要查看的教学周",
            modifier = Modifier.fillMaxWidth(),
            color = textColor.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNumberPicker(
            value = selectedWeek,
            onValueChange = { selectedWeek = it },
            range = 1..boundedTotalWeeks,
            visibleItemCount = 3,
            label = { "第${it}周" },
            colors = pickerColors,
            textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                color = textColor,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold
            ),
            itemHeight = 46.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects.current.usesCouiControls)
                    com.itos.coui.picker.CouiPickerDefaults.height + com.itos.coui.picker.CouiPickerDefaults.padding * 2 else 138.dp)
        )

    }
}
