package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.itos.coui.effect.CouiSpotlightTouch
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeFollowLightRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativePagerIndicatorRequest
import kotlin.math.roundToInt

@Composable
internal fun CouiFollowLight(request: NativeFollowLightRequest): Boolean {
    val latest = rememberUpdatedState(request)
    val (input, light) = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.OpaqueMedium2,
        dark = request.dark,
        intensity = 1.2f,
        startRadius = request.radiusPx * 0.2f,
        endRadius = request.radiusPx,
        externalTouch = { latest.value.let { CouiSpotlightTouch(it.position, it.pressed) } },
    )
    Spacer(request.modifier.graphicsLayer { clip = true }.then(input).drawBehind { light?.invoke(this) })
    return true
}

/** Launcher-size geometry and scrub state stay in the existing host. Kit draws the backdrop. */
@RequiresApi(34)
@Composable
internal fun CouiPagerIndicator(request: NativePagerIndicatorRequest): Boolean {
    val backdrop = request.backdrop ?: return false
    return CouiMaterialSurface(NativeMaterialSurfaceRequest(
        modifier = request.modifier,
        shape = Capsule(),
        dark = true,
        role = NativeMaterialSurfaceRole.PageIndicator,
        backdrop = backdrop,
        followTouch = request.enabled,
        elasticTouchFeedback = request.enabled,
    )) {
        val context = LocalContext.current
        val direction = LocalLayoutDirection.current
        val latest = rememberUpdatedState(request)
        val host = remember(context) {
            LauncherPagerIndicatorHost(context).also { indicator ->
                indicator.pageFeedback = { indicator.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                indicator.onSelect = { if (latest.value.enabled) latest.value.onSelect(it) }
                indicator.onScrub = { position, active, cancelled ->
                    val value = latest.value
                    if (value.enabled || !active) {
                        val scrub = value.onScrub
                        if (scrub != null) scrub(position, active, cancelled)
                        else if (!active && !cancelled) value.onSelect(position.roundToInt().coerceIn(0, value.pageCount - 1))
                    }
                }
            }
        }
        AndroidView(factory = { host }, update = {
            it.layoutDirection = if (direction == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            it.setInputEnabled(request.enabled)
        })
        LaunchedEffect(host) {
            snapshotFlow { latest.value.let { it.pageCount to it.position() } }
                .collect { (count, position) -> host.setPages(count, position) }
        }
        DisposableEffect(host) { onDispose { host.release() } }
    }
}
