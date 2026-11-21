package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class ChangePreviewTone {
    Move, Remove, Rename, Add, Edit
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChangePreviewCard(
    title: String,
    foreground: Color,
    badges: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ChangePreviewSurface(foreground) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            badges()
        }
        Text(title, color = foreground, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun ChangePreviewSurface(
    foreground: Color,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    spacing: Dp = 8.dp,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(foreground.copy(alpha = 0.045f), RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
internal fun ChangePreviewBadge(
    label: String,
    tone: ChangePreviewTone,
    foreground: Color,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val dark = foreground.luminance() > 0.5f
    val color = when (tone) {
        ChangePreviewTone.Move -> if (dark) Color(0xFF79B9FF) else Color(0xFF165FC0)
        ChangePreviewTone.Remove -> if (dark) Color(0xFFFF8A83) else Color(0xFFBB2525)
        ChangePreviewTone.Rename -> if (dark) Color(0xFFFFD36B) else Color(0xFF856100)
        ChangePreviewTone.Add -> if (dark) Color(0xFF80DDA7) else Color(0xFF16713C)
        ChangePreviewTone.Edit -> if (dark) Color(0xFFC0ADFF) else Color(0xFF6846B8)
    }
    Text(label, Modifier.background(color.copy(alpha = 0.12f), shape)
        .padding(horizontal = 7.dp, vertical = 3.dp), color = color,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
internal fun ChangePreviewField(label: String, before: String?, after: String, foreground: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = foreground.copy(alpha = 0.62f), style = MaterialTheme.typography.labelMedium)
        before?.let { Text("原来：$it", color = foreground.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium) }
        Text(if (before == null) after else "改为：$after", color = foreground, style = MaterialTheme.typography.bodyMedium)
    }
}
