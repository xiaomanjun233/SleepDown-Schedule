package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Two scalable vector previews; the entire option is one accessible radio target. */
@Composable
internal fun SettingsThemeModeOptions(darkMode: Boolean, onSelected: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(false, true).forEach { dark ->
            val selected = darkMode == dark
            Column(
                modifier = Modifier.weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(dark) })
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePhonePreview(dark = dark)
                Text(
                    text = if (dark) "暗色模式" else "亮色模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

@Composable
private fun ThemePhonePreview(dark: Boolean) {
    val measurer = rememberTextMeasurer()
    val clock = remember(measurer) {
        measurer.measure("10:30", style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
    }
    val wallpaper = remember(dark) {
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF338CD1), Color(0xFF147B9C), Color(0xFF29B5BB))
            else listOf(Color(0xFF88C5F3), Color(0xFF87CDE1), Color(0xFF86D4D5))
        )
    }
    Canvas(
        Modifier.width(88.dp).aspectRatio(0.49f)
            .clearAndSetSemantics { }
    ) {
        val w = size.width
        val h = size.height
        val inset = 2.dp.toPx()
        val corner = CornerRadius(w * 0.17f)
        drawRoundRect(wallpaper, topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2), cornerRadius = corner)
        drawRoundRect(Color(0xFF626A70), topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = corner, style = Stroke(2.dp.toPx()))
        val clockScale = w * 0.56f / clock.size.width.coerceAtLeast(1)
        withTransform({
            translate(w / 2f, h * 0.18f)
            scale(clockScale, clockScale, pivot = Offset.Zero)
        }) {
            drawText(clock, topLeft = Offset(-clock.size.width / 2f, 0f))
        }
        val cardColor = if (dark) Color(0xFF07547C) else Color(0xFFBBF0FC)
        val ink = if (dark) Color(0xFF91CEDF) else Color.White
        for (row in 0..1) {
            val y = h * (0.38f + row * 0.13f)
            drawRoundRect(cardColor, Offset(w * 0.1f, y), Size(w * 0.8f, h * 0.1f), CornerRadius(w * 0.055f))
            if (row == 0) {
                drawRoundRect(ink, Offset(w * 0.15f, y + h * 0.025f), Size(w * 0.1f, h * 0.05f), CornerRadius(w * 0.018f))
                drawRoundRect(ink.copy(alpha = 0.8f), Offset(w * 0.29f, y + h * 0.031f), Size(w * 0.24f, h * 0.009f), CornerRadius(w * 0.008f))
                drawRoundRect(ink.copy(alpha = 0.6f), Offset(w * 0.29f, y + h * 0.055f), Size(w * 0.51f, h * 0.009f), CornerRadius(w * 0.008f))
            }
        }
        drawRoundRect(ink.copy(alpha = 0.6f), Offset(w * 0.34f, h * 0.964f), Size(w * 0.32f, h * 0.006f), CornerRadius(w * 0.01f))
    }
}
