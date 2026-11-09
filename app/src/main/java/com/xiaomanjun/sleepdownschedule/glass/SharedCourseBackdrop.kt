package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.BackdropViewport
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop

// The course-blur slider replaces this sampler during preview. Only actual consumers must
// recompose; a static local invalidates the whole home provider, including the native Dock.
internal val LocalSharedCourseBackdrop = compositionLocalOf<SharedBlurBackdrop?> { null }
internal val LocalSharedCouiCourseBackdrop = compositionLocalOf<SharedBlurBackdrop?> { null }
internal val LocalCourseBackdropViewport = compositionLocalOf<BackdropViewport?> { null }

private val PreparedCourseRenderOptions = BackdropRenderOptions(
    placementLayer = false,
    clipGenericOutlineInDraw = false,
)

/** Draw the already filtered source directly inside the caller's rounded clip. */
internal fun Modifier.drawPreparedCourseBackdrop(backdrop: Backdrop): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    effects = {},
    highlight = null,
    shadow = null,
    renderOptions = PreparedCourseRenderOptions,
)
