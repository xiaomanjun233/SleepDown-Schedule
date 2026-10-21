package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.CourseEntity
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal val LocalCourseRemoval = staticCompositionLocalOf<CourseRemovalMotion?> { null }
internal const val CourseRemovalDurationMillis = 1100

/** Retain only the deleted rows until the erosion and its last particles have finished. */
@Stable
internal class CourseRemovalMotion(courses: List<CourseEntity>, val week: Int?) {
    private val originals = courses.associateBy(CourseEntity::id)
    val progress = Animatable(0f)

    fun matches(course: CourseEntity, displayedWeek: Int): Boolean =
        originals[course.id]?.scheduleId == course.scheduleId && (week == null || week == displayedWeek)

    fun appliedTo(courses: List<CourseEntity>): Boolean = courses.none {
        it.id in originals && (week == null || week in it.weeks)
    }

    fun retainIn(courses: List<CourseEntity>): List<CourseEntity> = buildList {
        courses.forEach { add(originals[it.id] ?: it) }
        val currentIds = courses.mapTo(HashSet(), CourseEntity::id)
        originals.values.filterNot { it.id in currentIds }.forEach(::add)
    }
}

private data class CourseLightParticle(
    val origin: Offset,
    val birth: Float,
    val life: Float,
    val radius: Float,
    val wind: Float,
    val flutter: Float,
    val colorIndex: Int
)

@Composable
internal fun Modifier.courseRemovalMotion(course: CourseEntity, week: Int, color: Color): Modifier {
    val motion = LocalCourseRemoval.current?.takeIf { it.matches(course, week) } ?: return this
    return drawWithCache {
        val random = Random(course.id)
        val spacing = 9.dp.toPx()
        val count = (size.width * size.height / (spacing * spacing)).toInt().coerceIn(36, 180)
        val palette = listOf(lerp(color.copy(alpha = 1f), Color.White, 0.45f),
            lerp(color.copy(alpha = 1f), Color.White, 0.78f), Color.White)
        val glowRadius = 7.dp.toPx()
        val glows = palette.map {
            Brush.radialGradient(listOf(it.copy(alpha = 0.45f), it.copy(alpha = 0f)),
                center = Offset.Zero, radius = glowRadius)
        }
        val particles = List(count) { index ->
            val y = (index + random.nextFloat()) / count * size.height
            CourseLightParticle(
                origin = Offset(random.nextFloat() * size.width, y),
                birth = y / size.height * 0.68f,
                life = 0.26f + random.nextFloat() * 0.06f,
                radius = (0.75f + random.nextFloat() * 1.2f).dp.toPx(),
                wind = 0.65f + random.nextFloat() * 0.7f,
                flutter = random.nextFloat() * 2f * PI.toFloat(),
                colorIndex = index % palette.size
            )
        }
        val windDistance = size.width * 0.75f + 32.dp.toPx()
        val lift = 48.dp.toPx()
        val flutterHeight = 9.dp.toPx()
        val roughness = 3.dp.toPx()
        val edgeNoise = FloatArray(25) { random.nextFloat() * 2f - 1f }
        val remaining = Path()
        val edge = Path()
        val softEdge = Stroke(width = 5.dp.toPx())
        val hotEdge = Stroke(width = 1.1.dp.toPx())
        // Four taps over an 18 ms shutter follow the actual curved path. Their weights sum to
        // one, keeping the light soft without raising brightness or blurring the whole scene.
        val shutterWeights = floatArrayOf(0.52f, 0.25f, 0.15f, 0.08f)
        val shutterProgress = 18f / CourseRemovalDurationMillis
        onDrawWithContent {
            val p = motion.progress.value.coerceIn(0f, 1f)
            if (p <= 0f) {
                drawContent()
                return@onDrawWithContent
            }
            val erosion = (p / 0.68f).coerceIn(0f, 1f)
            if (erosion < 1f) {
                remaining.reset()
                edge.reset()
                val amplitude = sin(erosion * PI.toFloat()) * roughness
                for (i in edgeNoise.indices) {
                    val x = size.width * i / edgeNoise.lastIndex
                    val y = (size.height * erosion + amplitude *
                        (edgeNoise[i] + sin(i * 1.7f + p * 18f) * 0.4f)).coerceIn(0f, size.height)
                    if (i == 0) { remaining.moveTo(x, y); edge.moveTo(x, y) }
                    else { remaining.lineTo(x, y); edge.lineTo(x, y) }
                }
                remaining.lineTo(size.width, size.height)
                remaining.lineTo(0f, size.height)
                remaining.close()
                clipPath(remaining) { this@onDrawWithContent.drawContent() }
                val heat = sin(erosion * PI.toFloat()).coerceAtLeast(0f)
                drawPath(edge, palette[0], alpha = heat * 0.2f, style = softEdge)
                drawPath(edge, palette[1], alpha = heat * 0.9f, style = hotEdge)
            }
            particles.forEach { particle ->
                val age = (p - particle.birth) / particle.life
                if (age > 0f && age < 1f) {
                    val alpha = sin(age * PI.toFloat()).coerceAtLeast(0f).pow(0.65f)
                    for (sample in shutterWeights.indices.reversed()) {
                        val sampledAge = (age - shutterProgress / particle.life * sample / shutterWeights.lastIndex)
                            .coerceAtLeast(0f)
                        val x = particle.origin.x + windDistance * particle.wind * sampledAge.pow(1.25f)
                        val y = particle.origin.y - lift * sampledAge + flutterHeight *
                            (sin(particle.flutter + sampledAge * 5f) - sin(particle.flutter))
                        val sampledAlpha = alpha * shutterWeights[sample]
                        translate(x, y) {
                            drawCircle(glows[particle.colorIndex], radius = glowRadius,
                                center = Offset.Zero, alpha = sampledAlpha * 0.65f)
                            drawCircle(palette[particle.colorIndex], radius = particle.radius * (1f - age * 0.65f),
                                center = Offset.Zero, alpha = sampledAlpha)
                        }
                    }
                }
            }
        }
    }.pointerInput(motion) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }.clearAndSetSemantics { }
}
