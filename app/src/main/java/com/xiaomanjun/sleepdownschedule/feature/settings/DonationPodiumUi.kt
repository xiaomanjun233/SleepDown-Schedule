package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.RemoteDonationEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.sin

/** Published order is authoritative; amounts in different currencies are never compared. */
@Composable
internal fun DonationPodium(entries: List<RemoteDonationEntry>) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    val gold = Color(0xFFD89924)
    val silver = Color(0xFF7795BB)
    val bronze = Color(0xFFC77F59)
    val colors = remember { listOf(gold, silver, bronze) }
    val order = if (entries.size >= 2) listOf(1, 0, 2) else listOf(0)
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            order.filter { it < entries.size }.forEach { rank ->
                val supporter = entries[rank]
                val color = colors[rank]
                Column(
                    Modifier.weight(1f).graphicsLayer {
                        val progress = (reveal.value * 2f - rank * 0.09f).coerceIn(0f, 1f)
                        alpha = progress
                        translationY = (1f - progress) * 18.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(if (rank == 0) 58.dp else 48.dp)
                            .background(color.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(if (rank == 0) 34.dp else 28.dp)
                        )
                    }
                    Text(
                        supporter.supporterId,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        supporter.formattedAmount(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(when (rank) { 0 -> 92.dp; 1 -> 70.dp; else -> 54.dp })
                            .background(
                                Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.05f))),
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${rank + 1}",
                            style = MiuixTheme.textStyles.title1,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // A single brief burst. Draw-time reads avoid recomposing the podium or its backdrop.
        Canvas(Modifier.matchParentSize()) {
            val progress = reveal.value
            if (progress <= 0f || progress >= 1f) return@Canvas
            val alpha = (sin(progress * Math.PI).toFloat() * 0.65f).coerceIn(0f, 1f)
            repeat(18) { index ->
                val lane = ((index * 37) % 101) / 100f
                val x = size.width * lane
                val y = size.height * (progress * 0.48f - (index % 4) * 0.05f)
                val center = Offset(x, y)
                rotate(index * 29f + progress * 120f, center) {
                    drawRoundRect(
                        color = colors[index % colors.size].copy(alpha = alpha),
                        topLeft = center,
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                    )
                }
            }
        }
    }
}
