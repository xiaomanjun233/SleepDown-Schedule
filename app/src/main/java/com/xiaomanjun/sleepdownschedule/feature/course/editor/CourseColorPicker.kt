package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.kyant.shapes.Capsule
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.model.DefaultCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.CoursePaletteGrid

@Composable
internal fun CourseColorPicker(
    show: Boolean,
    selectedColorArgb: Long?,
    automaticColorArgb: Long,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    renderInRootScaffold: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onColorSelected: (Long?) -> Unit
) {
    val palette = LocalCourseCardPalette.current.ifEmpty { DefaultCourseCardPalette }
    var pendingColor by remember(show, selectedColorArgb) { mutableStateOf(selectedColorArgb) }
    SleepDownPickerDialog(
        show = show,
        title = "课程颜色",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        backdrop = backdrop,
        config = config,
        renderInRootScaffold = renderInRootScaffold,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        contentSpacing = 14.dp,
        scrollableContent = true,
        couiContentPadding = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CouiPickerFormPadding,
        blurRadius = 10.dp,
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismissRequest),
            LiquidAlertAction("确定", LiquidAlertActionStyle.Primary) {
                onColorSelected(pendingColor)
                onDismissRequest()
            }
        )
    ) {
        val foreground = LocalContentColor.current
        CompositionLocalProvider(LocalContentColor provides foreground) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("当前课程色板", color = foreground.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    QuickSheetLiquidAction(label = "自动", enabled = true, backdrop = backdrop,
                        config = config, primary = pendingColor == null,
                        modifier = Modifier.width(68.dp), height = 36.dp) {
                        pendingColor = null
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(palette, key = { it }) { colorArgb ->
                        val selected = pendingColor == colorArgb
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = Capsule(),
                            color = Color(colorArgb.toInt()),
                            border = BorderStroke(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else foreground.copy(alpha = 0.28f)
                            ),
                            onClick = {
                                pendingColor = colorArgb
                            }
                        ) {}
                    }
                }
                CoursePaletteGrid(Color((pendingColor ?: automaticColorArgb).toInt())) { color ->
                    pendingColor = color.toArgb().toLong() and 0xFFFFFFFFL
                }

            }
        }
    }
}

@Composable
internal fun CourseColorPaletteButton(
    backdrop: Backdrop?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 34.dp
) {
    val surfaceColor = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.90f)
    val iconColor = if (selected) Color.White else Color(0xFF1A1A1A)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.size(size),
            height = size,
            contentPadding = PaddingValues(0.dp),
            surfaceColor = surfaceColor,
            blurRadius = 8.dp,
            lensHeight = 18.dp,
            lensAmount = 22.dp,
            chromaticAberration = false
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "打开调色盘",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.56f)
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = Capsule(),
            color = surfaceColor,
            onClick = onClick
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "打开调色盘",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.56f)
                )
            }
        }
    }
}
