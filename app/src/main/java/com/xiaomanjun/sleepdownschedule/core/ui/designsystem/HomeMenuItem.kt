package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIcon
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle

@Composable
fun AddMenuLiquidItem(
    config: ScheduleConfigEntity,
    action: AddMenuAction,
    itemHeight: Dp,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    compactCapsule: Boolean = false,
    foreground: ComposeColor = glassForegroundColor(config)
) {
    val baseText = foreground
    val selectedColor = ComposeColor(0xFF8E8E93).copy(
        alpha = if (glassUsesLightStyle(config)) 0.20f else 0.28f
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight - 2.dp)
                .clip(if (compactCapsule) Capsule() else RoundedRectangle(HomeAddMenuSelectionCornerDp.dp))
                .background(if (highlighted) selectedColor else ComposeColor.Transparent)
                .padding(horizontal = if (compactCapsule) 14.dp else 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactCapsule) 12.dp else 10.dp)
            ) {
                Icon(
                    (action.imageVector ?: homeActionIcon(action.iconRes))?.let { rememberVectorPainter(it) }
                        ?: painterResource(requireNotNull(action.iconRes)),
                    contentDescription = null,
                    modifier = Modifier.size(if (compactCapsule) 20.dp else 21.dp),
                    tint = action.iconTint ?: baseText
                )
                Text(
                    action.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = action.textTint ?: baseText,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
