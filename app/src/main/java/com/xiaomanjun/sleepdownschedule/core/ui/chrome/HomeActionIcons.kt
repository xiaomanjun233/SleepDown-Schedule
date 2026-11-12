package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import com.itos.coui.icons.CouiIcons
import com.xiaomanjun.sleepdownschedule.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Weeks

/** A null selection uses the standalone action glyph. */
@Composable
internal fun homeActionIcon(iconRes: Int?, selected: Boolean? = null): ImageVector? = when (iconRes) {
    R.drawable.ic_day_view -> CouiIcons.Filled.Certificate
    R.drawable.ic_week_view -> ImageVector.vectorResource(
        if (selected == true) R.drawable.ic_week_view_selected else R.drawable.ic_week_view
    )
    R.drawable.ic_material_event -> MiuixIcons.Weeks
    R.drawable.ic_courses -> CouiIcons.Filled.BookOpen
    R.drawable.ic_schedule_layers -> CouiIcons.Filled.MultiPhone
    R.drawable.ic_settings -> if (selected == false) CouiIcons.Outlined.Setting else CouiIcons.Filled.Setting
    R.drawable.ic_add_course -> Icons.Rounded.Add
    R.drawable.ic_ai_import -> MiuixIcons.Import
    R.drawable.ic_school_import -> MiuixIcons.Community
    R.drawable.ic_edit -> MiuixIcons.Edit
    R.drawable.ic_more_horizontal -> MiuixIcons.More
    R.drawable.ic_share_schedule -> MiuixIcons.Share
    else -> null
}

@Composable
internal fun homeActionIconPainter(iconRes: Int, selected: Boolean? = null): Painter =
    homeActionIcon(iconRes, selected)?.let { rememberVectorPainter(it) } ?: painterResource(iconRes)
