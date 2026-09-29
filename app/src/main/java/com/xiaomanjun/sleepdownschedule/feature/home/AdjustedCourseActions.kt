package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.glassForegroundColor
import java.time.LocalDate
import kotlin.math.roundToInt

/** Resolve the displayed occurrence in the app host, then use the ordinary course editor morph. */
internal val LocalAdjustedCourseEditor = compositionLocalOf<((Long, LocalDate, Rect?) -> Unit)?> { null }

internal fun Density.courseAdjustmentBadgeHeight() = maxOf(16.dp, 11.sp.toDp() + 2.dp)

private fun courseBadgeCornerOutset(height: Dp, corner: Dp): Dp =
    (height / 2f - corner * 0.29289322f).coerceAtMost(2.dp)

internal fun courseBadgeContentInset(height: Dp, corner: Dp): Dp =
    height - courseBadgeCornerOutset(height, corner) + 1.dp

/** Anchor the capsule's end cap at the lower rounded corner; long labels grow inward. */
internal fun Modifier.courseBadgeCornerAnchor(corner: Dp): Modifier = layout { measurable, constraints ->
    val badge = measurable.measure(constraints)
    // Follow the diagonal corner point, limiting overflow to the existing grid gap.
    val outset = courseBadgeCornerOutset(badge.height.toDp(), corner).toPx().roundToInt()
    layout(badge.width, badge.height) { badge.placeRelative(outset, outset) }
}

@Composable
internal fun CourseAdjustmentBadge(label: String, backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier) {
    GlassSurface(
        backdrop = backdrop, config = config, modifier = modifier,
        shape = Capsule(),
        // The card's pager layer already moves this badge. Avoid replaying its glass in
        // another placement layer when the pager reuses the containing card.
        placementLayer = false,
        baseSurfaceColorOverride = if (label == "停") MutedCourseLightColor else Color(0xFFFFB928),
        tokens = GlassTokens.pill(0.65f).copy(blur = 6.dp, surfaceAlpha = 0.30f,
            lensHeight = 4.dp, lensAmount = 4.dp, shadowAlpha = 0f)
    ) {
        Box(Modifier.sizeIn(minWidth = 16.dp, minHeight = 16.dp).padding(horizontal = 2.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold,
                color = glassForegroundColor(config))
        }
    }
}
