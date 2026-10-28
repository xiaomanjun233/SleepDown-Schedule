package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State

private val HomeVisible = object : State<Boolean> { override val value = true }

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
