package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.R

/** Android drawable equivalents of the project's HomeActionIcons; IDs still identify actions. */
internal fun colorOsHomeIconResource(iconRes: Int): Int = when (iconRes) {
    R.drawable.ic_edit -> R.drawable.ic_coloros_home_edit
    R.drawable.ic_more_horizontal -> R.drawable.ic_coloros_home_more
    R.drawable.ic_courses -> R.drawable.ic_coloros_menu_courses
    R.drawable.ic_week_view, R.drawable.ic_material_event -> R.drawable.ic_coloros_dock_week
    // The project deliberately retains the original Compose Settings glyph.
    else -> iconRes
}
