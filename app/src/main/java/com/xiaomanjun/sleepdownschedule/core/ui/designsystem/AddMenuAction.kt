package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class AddMenuAction(
    val iconRes: Int? = null,
    val label: String,
    val imageVector: ImageVector? = null,
    val iconTint: Color? = null,
    val textTint: Color? = null,
    val onClick: () -> Unit
)
