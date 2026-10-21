package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun popEnterTransition(): EnterTransition =
    fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)) +
        scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f))

internal fun popExitTransition(): ExitTransition =
    fadeOut(animationSpec = spring(dampingRatio = 0.92f, stiffness = 560f)) +
        scaleOut(targetScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 560f))
