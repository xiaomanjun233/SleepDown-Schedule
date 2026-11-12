package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseCreationGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal val WeekCourseAdditionCorner = 13.dp

internal data class WeekCourseAddTarget(
    val scheduleId: Int,
    val week: Int,
    val weekday: Int,
    val period: Int,
    val bounds: Rect
)

@Stable
internal class WeekCourseAddition {
    var target by mutableStateOf<WeekCourseAddTarget?>(null)
        private set

    fun select(value: WeekCourseAddTarget?) { target = value }

    fun clear() { target = null }

    fun clearIfCurrent(value: WeekCourseAddTarget) {
        if (target === value) clear()
    }
}

internal val LocalWeekCourseAddition = compositionLocalOf<WeekCourseAddition?> { null }

@Composable
internal fun WeekCourseAddPreview(
    selection: WeekCourseAddition,
    interactive: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onConfirm: (WeekCourseAddTarget) -> Unit
) {
    val target = selection.target ?: return
    BackHandler(enabled = interactive) { selection.clearIfCurrent(target) }
    LaunchedEffect(selection, target, interactive) {
        if (interactive) {
            delay(3_000)
            selection.clearIfCurrent(target)
        }
    }
    val density = LocalDensity.current
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    val entrance = remember(target) { Animatable(0f) }
    LaunchedEffect(entrance) {
        entrance.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 560f))
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInRoot() }) {
        if (!hostBounds.isEmpty) {
            val description = "添加周${weekdayLabel(target.weekday)}第${target.period}节课程"
            CourseCreationGlass(
                backdrop = backdrop,
                config = config,
                shape = RoundedRectangle(WeekCourseAdditionCorner),
                modifier = Modifier.absoluteOffset {
                    IntOffset(
                        (target.bounds.left - hostBounds.left).roundToInt(),
                        (target.bounds.top - hostBounds.top).roundToInt()
                    )
                }
                    .size(with(density) { target.bounds.width.toDp() }, with(density) { target.bounds.height.toDp() })
                    .graphicsLayer {
                        val scale = 0.88f + 0.12f * entrance.value
                        scaleX = scale
                        scaleY = scale
                        alpha = entrance.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .clickable(enabled = interactive, role = Role.Button) { onConfirm(target) }
            ) {
                Icon(
                    painterResource(R.drawable.ic_add_course), contentDescription = description,
                    tint = glassForegroundColor(config), modifier = Modifier.align(Alignment.Center).size(23.dp)
                )
            }
        }
    }
}
