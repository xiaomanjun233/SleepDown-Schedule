package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** Both panes and their glass producers use the same gradient in root coordinates. */
@Stable
internal class SettingsSharedGradient(val colors: List<Color>) {
    var boundsInRoot by mutableStateOf(Rect.Zero)
}

internal val LocalSettingsSharedGradient = compositionLocalOf<SettingsSharedGradient?> { null }

@Stable
internal class SettingsPaneBackground(
    private val fallback: Color,
    private val shared: SettingsSharedGradient?
) {
    private var origin by mutableStateOf(Offset.Zero)
    private val brush by derivedStateOf {
        shared?.takeIf { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }?.let {
            Brush.linearGradient(
                colors = it.colors,
                start = it.boundsInRoot.topLeft - origin,
                end = it.boundsInRoot.bottomRight - origin
            )
        }
    }

    val trackingModifier: Modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() }
    val modifier: Modifier = trackingModifier.drawBehind { draw(this) }

    fun draw(scope: DrawScope) {
        val gradient = brush
        if (gradient != null) scope.drawRect(gradient) else scope.drawRect(fallback)
    }
}

@Composable
internal fun rememberSettingsPaneBackground(fallback: Color): SettingsPaneBackground {
    val shared = LocalSettingsSharedGradient.current
    return remember(fallback, shared) { SettingsPaneBackground(fallback, shared) }
}
