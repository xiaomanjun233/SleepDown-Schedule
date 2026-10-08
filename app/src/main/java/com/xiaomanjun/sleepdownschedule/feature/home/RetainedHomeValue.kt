package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State

private val HomeNotFrozen = object : State<Boolean> { override val value = false }
private val HomeVisible = object : State<Boolean> { override val value = true }
internal val LocalHomeBackgroundFrozen = compositionLocalOf<State<Boolean>> { HomeNotFrozen }

/** Pause CPU wallpaper/foreground decisions during page motion without freezing live glass. */
internal val LocalHomeTextContrastFrozen = compositionLocalOf<State<Boolean>> { HomeNotFrozen }

/** False for retained, fully hidden pages; their active-only callbacks must stay dormant. */
internal val LocalHomePaneVisible = compositionLocalOf<State<Boolean>> { HomeVisible }

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
