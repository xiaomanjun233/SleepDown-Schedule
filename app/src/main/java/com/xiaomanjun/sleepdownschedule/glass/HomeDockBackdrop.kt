package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop

/**
 * COUI Dock samples the visible body, including the wallpaper tone and scrolling content.
 * The fixed top chrome has its own sampling path. Including it here makes each dock patch
 * traverse that chrome's copy of the body as well.
 *
 * The body producer and dock stay separate inside the full home transition recording.
 */
@Composable
internal fun rememberDockUnderlay(): CouiBackdrop =
    // The dock isolates its own patches. Page transitions and the frozen Home host already
    // own their separate layers, so this sampling source can replay the same draw commands.
    rememberCouiBackdrop(diagnosticName = "home-dock-underlay", isolateContent = false)

internal fun Modifier.dockUnderlaySource(backdrop: CouiBackdrop): Modifier =
    couiBackdrop(backdrop)
