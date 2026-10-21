package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent

@Composable
fun SettingsTextFieldRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    placeholder: String = "",
    moveCursorToEndOnFocus: Boolean = false,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val isTimePicker = keyboardType == KeyboardType.Text && value.matches(Regex("\\d{1,2}:\\d{2}"))
    val isDatePicker = keyboardType == KeyboardType.Text && (
            value.matches(Regex("\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}")) ||
                    title.contains("日期")
            )
    val fieldWidth = if (compact && keyboardType == KeyboardType.Number) 64.dp else 170.dp
    if (isTimePicker || isDatePicker) {
        SettingsPickerValueRow(
            title = title,
            value = value,
            enabled = enabled,
            compact = compact,
            onClick = {
                if (isTimePicker) {
                    showNativeTimePicker(context, value, onValueChange)
                } else {
                    showNativeDatePicker(context, value, onValueChange)
                }
            }
        )
        return
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = if (compact) 4.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
            endActions = {
                SettingsInlineTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    keyboardType = keyboardType,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    ),
                    placeholder = placeholder,
                    moveCursorToEndOnFocus = moveCursorToEndOnFocus,
                    fieldWidth = fieldWidth
                )
            }
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 64.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = if (compact) 4.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        SettingsInlineTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            keyboardType = keyboardType,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            ),
            placeholder = placeholder,
            moveCursorToEndOnFocus = moveCursorToEndOnFocus,
            fieldWidth = fieldWidth
        )
    }
}

@Composable
private fun SettingsInlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    textStyle: TextStyle,
    placeholder: String,
    moveCursorToEndOnFocus: Boolean,
    fieldWidth: androidx.compose.ui.unit.Dp
) {
    val fieldModifier = Modifier.width(fieldWidth)
    val decoration: @Composable ((@Composable () -> Unit) -> Unit) = { innerTextField ->
        Box(contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.End
                )
            }
            innerTextField()
        }
    }
    if (!moveCursorToEndOnFocus) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (keyboardType == KeyboardType.Password) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = textStyle,
            modifier = fieldModifier,
            decorationBox = decoration
        )
        return
    }

    var editableValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (editableValue.text != value) {
            editableValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            editableValue = editableValue.copy(
                selection = TextRange(editableValue.text.length)
            )
        }
    }
    BasicTextField(
        value = editableValue,
        onValueChange = { next ->
            editableValue = next
            if (next.text != value) onValueChange(next.text)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (keyboardType == KeyboardType.Password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = textStyle,
        modifier = fieldModifier.onFocusChanged { focused = it.isFocused },
        decorationBox = decoration
    )
}
