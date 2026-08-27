package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*

import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

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
        backdrop = backdrop,
        config = config,
        enableWindowDim = true,
    ) {
        Text(
            text = "选择要查看的教学周",
            modifier = Modifier.fillMaxWidth(),
            color = textColor.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        top.yukonga.miuix.kmp.basic.NumberPicker(
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
                .height(138.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
        ) {
            QuickSheetLiquidAction(
                label = "取消",
                enabled = true,
                backdrop = backdrop,
                config = config,
                modifier = Modifier.weight(1f),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                confirmedWeek = null
                onDismissRequest()
            }
            QuickSheetLiquidAction(
                label = "跳转",
                enabled = true,
                backdrop = backdrop,
                config = config,
                modifier = Modifier.weight(1f),
                primary = true,
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                confirmedWeek = selectedWeek
                onDismissRequest()
            }
        }
    }
}
