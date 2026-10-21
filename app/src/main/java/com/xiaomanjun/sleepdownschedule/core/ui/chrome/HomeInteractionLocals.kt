package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val LocalEditingCourseId = compositionLocalOf<Long?> { null }
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
