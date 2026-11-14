package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.referentialEqualityPolicy
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.home.WeekCourseAdditionCorner
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseCreationGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal data class WeekCourseAddTarget(
    val scheduleId: Int,
    val week: Int,
    val weekday: Int,
    val period: Int,
    val bounds: Rect
)

@Stable
internal class WeekCourseAddition {
    var target by mutableStateOf<WeekCourseAddTarget?>(null, referentialEqualityPolicy())
        private set
    var preview by mutableStateOf<WeekCourseAddTarget?>(null, referentialEqualityPolicy())
        private set
    var editorOwnsPreview by mutableStateOf(false)
        private set

    fun select(value: WeekCourseAddTarget?) {
        if (value == null) clear() else {
            preview = value
            target = value
        }
    }

    fun clear() {
        target = null
        if (editorOwnsPreview) preview = null
        editorOwnsPreview = false
    }

    fun dismiss(value: WeekCourseAddTarget) {
        if (target === value) clear()
    }

    fun finishDismissal(value: WeekCourseAddTarget) {
        if (target == null && preview === value) preview = null
    }

    fun handOffToEditor(value: WeekCourseAddTarget) {
        if (target === value) editorOwnsPreview = true
    }

    fun returnFromEditor() { editorOwnsPreview = false }
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
    val target = selection.preview ?: return
    val editorOwnsPreview = selection.editorOwnsPreview
    val visible = selection.target === target && !editorOwnsPreview
    val enabled = interactive && visible
    BackHandler(enabled = enabled) { selection.dismiss(target) }
    LaunchedEffect(selection, target, enabled) {
        if (!enabled) return@LaunchedEffect
        delay(3_000)
        selection.dismiss(target)
    }
    val density = LocalDensity.current
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    val progress = remember(target) { Animatable(0f) }
    LaunchedEffect(progress, visible, editorOwnsPreview) {
        when {
            editorOwnsPreview -> progress.snapTo(1f)
            visible -> progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 560f))
            else -> {
                progress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                selection.finishDismissal(target)
            }
        }
    }
    if (editorOwnsPreview) return
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
                        val scale = 0.80f + 0.20f * progress.value
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .then(if (enabled) Modifier.clickable(role = Role.Button) { onConfirm(target) }
                        else Modifier.clearAndSetSemantics {})
            ) {
                Icon(
                    painterResource(R.drawable.ic_add_course), contentDescription = description,
                    tint = glassForegroundColor(config), modifier = Modifier.align(Alignment.Center).size(23.dp)
                )
            }
        }
    }
}
