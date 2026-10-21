package com.xiaomanjun.sleepdownschedule.core.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule

@Composable
fun LiquidMenuButton(
    backdrop: Backdrop?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    textColorOverride: ComposeColor? = null,
    surfaceColorOverride: ComposeColor? = null
) {
    val textColor = textColorOverride ?: if (destructive) ComposeColor(0xFFFF453A) else MaterialTheme.colorScheme.primary
    val surfaceColor = surfaceColorOverride ?: if (destructive) ComposeColor(0xFFFF453A).copy(alpha = 0.16f) else ComposeColor.White.copy(alpha = 0.10f)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 40.dp,
            surfaceColor = surfaceColor,
            contentPadding = PaddingValues(horizontal = 14.dp),
            blurRadius = 8.dp,
            lensHeight = 24.dp,
            lensAmount = 28.dp,
            chromaticAberration = false
        ) {
            Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    } else {
        Text(
            label,
            modifier = modifier
                .clip(Capsule())
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.14f)))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false
        )
    }
}
