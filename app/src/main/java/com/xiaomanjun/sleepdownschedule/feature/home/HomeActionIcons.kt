package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Weeks

/** Keep action resource IDs as callback identities while sharing glyphs across window sizes. */
internal fun homeActionIcon(iconRes: Int?): ImageVector? = when (iconRes) {
    R.drawable.ic_day_view -> HomeTodayIcon
    R.drawable.ic_week_view, R.drawable.ic_material_event -> MiuixIcons.Weeks
    R.drawable.ic_courses -> MiuixIcons.Notes
    R.drawable.ic_settings -> Icons.Default.Settings
    R.drawable.ic_add_course -> Icons.Rounded.Add
    R.drawable.ic_ai_import -> MiuixIcons.Import
    R.drawable.ic_school_import -> MiuixIcons.Community
    R.drawable.ic_edit -> MiuixIcons.Edit
    R.drawable.ic_more_horizontal -> MiuixIcons.More
    R.drawable.ic_share_schedule -> MiuixIcons.Share
    else -> null
}

/** Same rounded calendar silhouette as Weeks; the hands distinguish today's timeline. */
internal val HomeTodayIcon: ImageVector by lazy {
    ImageVector.Builder("Today", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 3.5f)
            lineTo(18f, 3.5f)
            curveTo(20f, 3.5f, 21f, 4.5f, 21f, 6.5f)
            lineTo(21f, 17.5f)
            curveTo(21f, 19.5f, 20f, 20.5f, 18f, 20.5f)
            lineTo(6f, 20.5f)
            curveTo(4f, 20.5f, 3f, 19.5f, 3f, 17.5f)
            lineTo(3f, 6.5f)
            curveTo(3f, 4.5f, 4f, 3.5f, 6f, 3.5f)
            close()
            moveTo(3f, 7.5f); lineTo(21f, 7.5f)
            moveTo(12f, 10.5f); lineTo(12f, 14.5f); lineTo(15f, 14.5f)
        }
    }.build()
}

@Composable
internal fun homeActionIconPainter(iconRes: Int): Painter =
    homeActionIcon(iconRes)?.let { rememberVectorPainter(it) } ?: painterResource(iconRes)
