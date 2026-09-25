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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.glassForegroundColor
import java.time.LocalDate

/** Resolve the displayed occurrence in the app host, then use the ordinary course editor morph. */
internal val LocalAdjustedCourseEditor = compositionLocalOf<((Long, LocalDate, Rect?) -> Unit)?> { null }

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
        Box(Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp).padding(3.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold,
                color = glassForegroundColor(config))
        }
    }
}
