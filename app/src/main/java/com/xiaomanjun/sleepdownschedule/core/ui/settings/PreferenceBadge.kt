package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

@Composable
internal fun PreferenceBadge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier.background(color.copy(alpha = 0.14f), Capsule())
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
internal fun PreferenceLabel(
    text: String,
    badge: String?,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    centered: Boolean = false,
    fontWeight: FontWeight = FontWeight.Medium
) {
    if (badge == null) {
        Text(text, style = style, color = color, fontWeight = fontWeight)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp,
            if (centered) androidx.compose.ui.Alignment.CenterHorizontally else androidx.compose.ui.Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(text, style = style, color = color, fontWeight = fontWeight)
        PreferenceBadge(badge, badgeColor)
    }
}
