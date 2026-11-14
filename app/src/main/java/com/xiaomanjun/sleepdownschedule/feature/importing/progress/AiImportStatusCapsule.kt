package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ai.DefaultAgentContextTokens
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.softBlurEnter
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import com.xiaomanjun.sleepdownschedule.feature.importing.aiImportElapsedLabel
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** One stable capsule: icons and live values stay visible together, without rotating pages. */
@Composable
internal fun AiImportStatusCapsule(taskId: String, config: ScheduleConfigEntity, backdrop: Backdrop?, modifier: Modifier = Modifier) {
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    if (live.taskId != taskId) return
    val now by produceState(System.nanoTime(), taskId) {
        while (true) { delay(1_000); value = System.nanoTime() }
    }
    val usage = live.usage
    val seconds = ((now - live.startedAtNanos).coerceAtLeast(0) / 1_000_000_000L)
    val elapsed = if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
    val speed = usage.averageOutputPerSecond?.let { String.format(Locale.ROOT, "%.0f tok/s", it) } ?: "—"
    val cache = if (usage.cached != null && usage.input != null && usage.input > 0)
        "${(usage.cached.toDouble() * 100 / usage.input).roundToInt()}%" else "—"
    val limit = usage.contextLimit?.takeIf { it > 0 } ?: DefaultAgentContextTokens.toLong()
    val contextFraction = usage.contextUsed?.let { it.toDouble() / limit }
    val context = contextFraction?.let { "${(it * 100).roundToInt()}%" } ?: "—"
    val color = LocalAiImportTextColors.current.secondary
    GlassSurface(backdrop, config, modifier = modifier.widthIn(max = 360.dp).softBlurEnter(taskId)
        .semantics {
            stateDescription = "输入 ${usage.input?.toString() ?: "未知"}，输出 ${usage.output?.toString() ?: "未知"} tokens；上下文预算 $limit tokens"
        }, nativeMaterial = true, nativeDark = appUsesDarkTheme(config), nativeElasticTouchFeedback = true,
        debugLabel = "AiImportStatusCapsule") {
        Row(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 420f))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            @Composable fun metric(icon: Int, value: String, description: String) {
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(16.dp))
                    MetricValue(value)
                }
            }
            metric(R.drawable.ic_ai_timer, elapsed, aiImportElapsedLabel(now, live.startedAtNanos))
            metric(R.drawable.ic_ai_speed, speed, "平均输出速度 $speed")
            metric(R.drawable.ic_ai_cache, cache, "缓存命中 $cache")
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "上下文占用 $context" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(16.dp).padding(1.dp)) {
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(color.copy(alpha = 0.18f), -90f, 360f, false, style = stroke)
                    contextFraction?.let { drawArc(color, -90f, (it.coerceIn(0.0, 1.0) * 360).toFloat(), false, style = stroke) }
                }
                MetricValue(context)
            }
        }
    }
}

@Composable
private fun MetricValue(value: String) {
    val transition = updateTransition(value, label = "import-metric-value")
    val density = LocalDensity.current.density
    transition.AnimatedContent(transitionSpec = {
        (fadeIn(tween(180, 40)) togetherWith fadeOut(tween(140)))
            .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 420f) })
    }) { label ->
        val blur by transition.animateFloat({ tween(180) }, label = "metric-text-blur") { if (it == label) 0f else 3f }
        Text(label, color = LocalAiImportTextColors.current.secondary, style = AiImportTextStyles.Caption,
            maxLines = 1, modifier = Modifier.graphicsLayer {
                renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > 0.1f) BlurEffect(blur * density, blur * density) else null
            })
    }
}
