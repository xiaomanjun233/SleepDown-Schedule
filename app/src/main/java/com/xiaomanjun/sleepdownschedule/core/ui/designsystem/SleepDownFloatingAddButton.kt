package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.shapes.Capsule

@Composable
internal fun SleepDownFloatingAddButton(
    backdrop: Backdrop?,
    contentDescription: String,
    onClick: () -> Unit,
    surfaceColor: Color,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sharedInteractiveHighlight: InteractiveHighlight? = null
) {
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription, Modifier.size(24.dp), tint = Color.White)
        }
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.size(54.dp),
            isInteractive = enabled,
            clickTargetEnabled = enabled,
            height = 54.dp,
            contentPadding = PaddingValues(0.dp),
            surfaceColor = surfaceColor,
            sharedInteractiveHighlight = sharedInteractiveHighlight,
            blurRadius = blurRadius,
            lensHeight = 10.dp,
            lensAmount = 40.dp,
            chromaticAberration = true,
            pressExpansion = 4.dp
        ) { content() }
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(54.dp),
            shape = Capsule(),
            color = surfaceColor
        ) { content() }
    }
}
