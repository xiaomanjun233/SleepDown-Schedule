package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.detailPageSlide
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.landscapePageTransform

internal fun AnimatedContentTransitionScope<TabletSettingsNavigationState>.settingsDetailTransition(): ContentTransform {
    if (initialState.rootPage != targetState.rootPage) return landscapePageTransform()
    return detailPageSlide(returning = initialState.detailPages.size > targetState.detailPages.size)
}
