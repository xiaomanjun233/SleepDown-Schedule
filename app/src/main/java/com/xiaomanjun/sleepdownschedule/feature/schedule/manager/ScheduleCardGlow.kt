package com.xiaomanjun.sleepdownschedule.feature.schedule.manager

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCardLightRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCardLightRequest

/** The active material provider owns capability gating and the installed native implementation. */
@Composable
internal fun rememberScheduleManagerGlowEnabled(): Boolean =
    LocalNativeCardLightRenderer.current != null

@Composable
internal fun ScheduleCardGlow(
    bitmap: Bitmap?,
    cardBounds: () -> Rect,
    strength: () -> Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 34.dp,
    receiverBounds: (() -> Rect)? = null,
    revealProgress: () -> Float = strength
) {
    LocalNativeCardLightRenderer.current?.invoke(
        NativeCardLightRequest(bitmap, cardBounds, strength, modifier, cornerRadius, receiverBounds, revealProgress)
    )
}
