package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.itos.coui.icons.CouiIcons
import com.xiaomanjun.sleepdownschedule.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Weeks

/** Keep action resource IDs as callback identities while sharing glyphs across window sizes. */
@Composable
internal fun homeActionIcon(iconRes: Int?, filledSetting: Boolean = true): ImageVector? = when (iconRes) {
    R.drawable.ic_day_view -> CouiIcons.Filled.Certificate
    R.drawable.ic_week_view -> CouiIcons.Calendar
    R.drawable.ic_material_event -> MiuixIcons.Weeks
    R.drawable.ic_courses -> CouiIcons.Filled.BookOpen
    R.drawable.ic_schedule_layers -> CouiIcons.Filled.MultiPhone
    R.drawable.ic_settings -> if (filledSetting) CouiIcons.Filled.Setting else CouiIcons.Outlined.Setting
    R.drawable.ic_add_course -> Icons.Rounded.Add
    R.drawable.ic_ai_import -> MiuixIcons.Import
    R.drawable.ic_school_import -> MiuixIcons.Community
    R.drawable.ic_edit -> MiuixIcons.Edit
    R.drawable.ic_more_horizontal -> MiuixIcons.More
    R.drawable.ic_share_schedule -> MiuixIcons.Share
    else -> null
}

@Composable
internal fun homeActionIconPainter(iconRes: Int, filledSetting: Boolean = true): Painter =
    homeActionIcon(iconRes, filledSetting)?.let { rememberVectorPainter(it) } ?: painterResource(iconRes)
