package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle

@Composable
fun TopBackButton(backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (LocalHomeMaterialControls.current.IconButton(
            iconRes = R.drawable.ic_arrow_back, contentDescription = "返回",
            dark = appUsesDarkTheme(config), interactive = true, selected = false,
            modifier = modifier.size(42.dp), onClick = onClick, toolbarNavigation = true
        )) return
    TopGlassIconButton(
        backdrop = backdrop,
        config = config,
        iconRes = R.drawable.ic_arrow_back,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier,
        buttonHeight = 42.dp
    )
}

@Composable
fun TopGlassIconButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColorOverride: ComposeColor? = null,
    buttonHeight: Dp = 42.dp
) {
    val lightGlass = glassUsesLightStyle(config)
    if (LocalHomeMaterialControls.current.IconButton(iconRes, contentDescription, dark = !lightGlass,
            interactive = true, selected = false, modifier = modifier.size(buttonHeight), onClick = onClick)) return
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = buttonHeight,
            surfaceColor = surfaceColorOverride ?: if (lightGlass) {
                ComposeColor.White.copy(alpha = 0.26f)
            } else {
                ComposeColor(0xFF121212).copy(alpha = 0.28f)
            },
            contentPadding = PaddingValues(0.dp),
            blurRadius = 3.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false,
            shadowEnabled = lightGlass,
            shadowStyle = LightTopBarButtonShadow
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(homeActionIconPainter(iconRes), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier,
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(homeActionIconPainter(iconRes), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
            }
        }
    }
}
