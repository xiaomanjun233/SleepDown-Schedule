package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.RenderEffect

/** The existing glass producer owns sampling; the vendor supplies only the blur operator. */
internal fun interface NativeProgressiveBlur {
    fun create(startRadius: Float, endRadius: Float, vertical: Boolean): RenderEffect?
}

internal val LocalNativeProgressiveBlur = compositionLocalOf<NativeProgressiveBlur?> { null }

/** A toolbar's backdrop already contains its page-level progressive blur. */
internal val LocalToolbarBackdropBlurred = compositionLocalOf { false }
