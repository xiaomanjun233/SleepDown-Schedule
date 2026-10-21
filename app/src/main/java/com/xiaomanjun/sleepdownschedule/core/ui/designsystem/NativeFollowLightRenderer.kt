package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/** Passive visual feedback for a gesture owned elsewhere (for example a lifted course card). */
internal data class NativeFollowLightRequest(
    val modifier: Modifier, val dark: Boolean, val position: Offset,
    val pressed: Boolean, val radiusPx: Float
)

internal val LocalNativeFollowLightRenderer =
    compositionLocalOf<(@Composable (NativeFollowLightRequest) -> Boolean)?> { null }
