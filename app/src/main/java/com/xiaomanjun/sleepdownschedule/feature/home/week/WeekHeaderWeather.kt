package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentPreferences
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentWeatherRepository
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentWeatherStore
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun WeekHeaderWeather(color: Color) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val frozen = LocalHomeBackgroundFrozen.current
    val repository = remember(context) { DayAgentWeatherRepository(context) }
    var weather by remember(context) {
        mutableStateOf(
            DayAgentWeatherStore.load(context).takeIf {
                DayAgentPreferences.isWeatherEnabled(context) && repository.hasLocationPermission()
            }
        )
    }
    LaunchedEffect(lifecycle, frozen) {
        if (frozen) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                weather = if (DayAgentPreferences.isWeatherEnabled(context) && repository.hasLocationPermission()) {
                    repository.getWeather()
                } else null
                delay(30 * 60 * 1_000L)
            }
        }
    }
    val current = weather ?: return
    val condition = remember(current.summary) {
        // The rest of the summary always mentions precipitation probability, even on sunny days.
        val sky = current.summary.substringBefore('，').substringBefore(',').uppercase()
        when {
            "雷" in sky || "THUNDER" in sky || "STORM" in sky -> "thunder"
            "雪" in sky || "SNOW" in sky -> "snow"
            "雨" in sky || "RAIN" in sky || "SLEET" in sky -> "rain"
            "雾" in sky || "霾" in sky || "FOG" in sky || "HAZE" in sky -> "fog"
            "云" in sky || "阴" in sky || "CLOUD" in sky || "OVERCAST" in sky -> "cloud"
            sky == "CLEAR_NIGHT" -> "moon"
            "晴" in sky || "CLEAR" in sky || "SUN" in sky -> "sun"
            else -> "cloud"
        }
    }
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "当前天气，${current.summary}，${current.temperature}摄氏度"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(Modifier.size(16.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                if (condition == "moon") {
                    drawPath(Path().apply {
                        moveTo(16f, 3f)
                        cubicTo(2f, 0f, 0f, 20f, 13f, 21f)
                        cubicTo(18f, 21f, 22f, 17f, 22f, 13f)
                        cubicTo(13f, 18f, 9f, 9f, 16f, 3f)
                        close()
                    }, color, style = Stroke(1.7f))
                } else if (condition == "sun") {
                    drawCircle(color, 4f, Offset(12f, 12f), style = Stroke(1.7f))
                    repeat(8) { index ->
                        val angle = index * Math.PI / 4
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(color, Offset(12f, 12f) + direction * 7f,
                            Offset(12f, 12f) + direction * 9f, 1.7f, StrokeCap.Round)
                    }
                } else {
                    val cloud = Path().apply {
                        moveTo(6f, 15f)
                        cubicTo(0f, 15f, 1f, 7f, 7f, 8f)
                        cubicTo(8f, 1f, 18f, 2f, 19f, 9f)
                        cubicTo(25f, 10f, 23f, 15f, 19f, 15f)
                        close()
                    }
                    drawPath(cloud, color, style = Stroke(1.7f))
                    when (condition) {
                        "rain" -> repeat(3) { i ->
                            drawLine(color, Offset(7f + 5f * i, 18f), Offset(6f + 5f * i, 21f), 1.6f, StrokeCap.Round)
                        }
                        "snow" -> repeat(3) { i -> drawCircle(color, 1.1f, Offset(7f + 5f * i, 20f)) }
                        "fog" -> repeat(2) { i -> drawLine(color, Offset(5f, 18f + 3f * i), Offset(20f, 18f + 3f * i), 1.6f, StrokeCap.Round) }
                        "thunder" -> drawPath(Path().apply {
                            moveTo(13f, 16f); lineTo(10f, 20f); lineTo(14f, 20f); lineTo(11f, 24f)
                        }, color, style = Stroke(1.7f))
                    }
                }
            }
        }
        Text("${current.temperature}°", color = color, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), maxLines = 1)
    }
}
