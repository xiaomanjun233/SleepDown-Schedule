package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/** A programmatic scroll target, separate from the immediate accessibility index action. */
internal val AnimateScrollToTopAction = SemanticsPropertyKey<suspend () -> Unit>("AnimateScrollToTop")

internal fun Modifier.animateScrollToTop(action: suspend () -> Unit): Modifier = semantics {
    this[AnimateScrollToTopAction] = action
}
