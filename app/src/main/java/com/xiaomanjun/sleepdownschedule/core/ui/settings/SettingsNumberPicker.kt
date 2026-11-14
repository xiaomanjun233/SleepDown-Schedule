package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.itos.coui.picker.WheelPicker
import com.itos.coui.picker.CouiPickerDefaults
import top.yukonga.miuix.kmp.basic.NumberPickerColors
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 保留数值、边界与提交语义；设置窗口使用 COUI 滚轮。 */
@Composable
internal fun SettingsNumberPicker(value: Int, onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier, range: IntRange, enabled: Boolean = true,
    wrapAround: Boolean = false, visibleItemCount: Int = 3,
    label: (Int) -> String = Int::toString,
    colors: NumberPickerColors = NumberPickerDefaults.colors(),
    textStyle: TextStyle = MiuixTheme.textStyles.title1,
    itemHeight: Dp = NumberPickerDefaults.ItemHeight) {
    if (com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects.current.usesCouiControls) {
        Box(modifier.fillMaxWidth().padding(vertical = CouiPickerDefaults.padding), contentAlignment = Alignment.Center) {
        WheelPicker(modifier = Modifier.width(120.dp).height(CouiPickerDefaults.height), value = value.coerceIn(range),
            onValueChange = onValueChange, valueRange = range, label = "选择${label(value)}", formatter = label,
            dark = MaterialTheme.colorScheme.background.luminance() < .5f,
            enabled = enabled, wrap = wrapAround, verticalFading = true)
        }
    } else top.yukonga.miuix.kmp.basic.NumberPicker(value = value, onValueChange = onValueChange,
        modifier = modifier, range = range, enabled = enabled, wrapAround = wrapAround,
        visibleItemCount = visibleItemCount, label = label, colors = colors, textStyle = textStyle, itemHeight = itemHeight)
}
