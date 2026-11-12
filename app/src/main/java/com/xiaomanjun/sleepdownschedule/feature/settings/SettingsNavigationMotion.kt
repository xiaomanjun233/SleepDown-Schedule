package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.landscapePageTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Matches the navigation motion used by the bundled Miuix system-style navigator. */
private class MiuixSettingsNavigationEasing(
    response: Float = 0.8f,
    damping: Float = 0.95f
) : Easing {
    private val decay: Float
    private val frequency: Float
    private val phase: Float

    init {
        val omega = 2.0 * PI / response
        val stiffness = omega * omega
        val friction = damping * 4.0 * PI / response
        frequency = (sqrt(4.0 * stiffness - friction * friction) / 2.0).toFloat()
        decay = (-friction / 2.0).toFloat()
        phase = decay / frequency
    }

    override fun transform(fraction: Float): Float {
        val time = fraction.toDouble()
        val attenuation = exp(decay * time)
        return (attenuation * (-cos(frequency * time) + phase * sin(frequency * time)) + 1.0).toFloat()
    }
}

private val SettingsDetailSlide = tween<IntOffset>(500, easing = MiuixSettingsNavigationEasing())

internal fun AnimatedContentTransitionScope<TabletSettingsNavigationState>.settingsDetailTransition(): ContentTransform {
    if (initialState.rootPage != targetState.rootPage) return landscapePageTransform()
    val returning = initialState.detailPages.size > targetState.detailPages.size
    return if (returning) {
        (slideInHorizontally(SettingsDetailSlide) { width -> -width / 4 } togetherWith
            slideOutHorizontally(SettingsDetailSlide) { width -> width }).using(null).apply {
            targetContentZIndex = -1f
        }
    } else {
        (slideInHorizontally(SettingsDetailSlide) { width -> width } togetherWith
            slideOutHorizontally(SettingsDetailSlide) { width -> -width / 4 }).using(null)
    }
}
