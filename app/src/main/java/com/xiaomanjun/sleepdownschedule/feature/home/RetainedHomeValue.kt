package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.compositionLocalOf

internal val LocalHomeBackgroundFrozen = compositionLocalOf { false }

/** Pause CPU wallpaper/foreground decisions during page motion without freezing live glass. */
internal val LocalHomeTextContrastFrozen = compositionLocalOf { false }

/** False for retained, fully hidden pages; their active-only callbacks must stay dormant. */
internal val LocalHomePaneVisible = compositionLocalOf { true }

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
