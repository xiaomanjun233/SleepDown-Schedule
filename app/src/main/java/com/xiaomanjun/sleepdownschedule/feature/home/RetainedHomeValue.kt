package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.compositionLocalOf

internal val LocalHomeBackgroundFrozen = compositionLocalOf { false }

/** Retains input references, not a second composition or a copy of the course data. */
internal class RetainedHomeValue<T>(initial: T) {
    private var retained = initial
    private var wasFrozen = false

    fun update(value: T, frozen: Boolean): T {
        if (!frozen || !wasFrozen) retained = value
        wasFrozen = frozen
        return retained
    }
}
