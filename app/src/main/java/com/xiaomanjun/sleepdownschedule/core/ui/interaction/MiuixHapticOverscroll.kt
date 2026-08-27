package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import top.yukonga.miuix.kmp.utils.MiuixOverscrollEffect
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
private class HapticMiuixOverscrollFactory(
    private val onBoundaryReached: () -> Unit
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        HapticMiuixOverscrollEffect(onBoundaryReached)

    override fun equals(other: Any?): Boolean =
        other is HapticMiuixOverscrollFactory && other.onBoundaryReached === onBoundaryReached

    override fun hashCode(): Int = System.identityHashCode(onBoundaryReached)
}

@OptIn(ExperimentalFoundationApi::class)
private class HapticMiuixOverscrollEffect(
    private val onBoundaryReached: () -> Unit
) : OverscrollEffect {
    private val delegate = MiuixOverscrollEffect()
    private var latchedDirection = 0

    override val node: DelegatableNode
        get() = delegate.node

    override val isInProgress: Boolean
        get() = delegate.isInProgress

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        var boundaryDirection = 0
        val consumed = delegate.applyToScroll(delta, source) { available ->
            val contentConsumed = performScroll(available)
            boundaryDirection = directionOf(available.y - contentConsumed.y, 1f)
            if (boundaryDirection == 0 && abs(contentConsumed.y) > 1f) {
                latchedDirection = 0
            }
            contentConsumed
        }
        if (source == NestedScrollSource.UserInput) {
            notifyBoundary(boundaryDirection)
        }
        return consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        var boundaryDirection = 0
        try {
            delegate.applyToFling(velocity) { available ->
                val contentConsumed = performFling(available)
                boundaryDirection = directionOf(available.y - contentConsumed.y, 24f)
                contentConsumed
            }
            notifyBoundary(boundaryDirection)
        } finally {
            latchedDirection = 0
        }
    }

    private fun notifyBoundary(direction: Int) {
        if (direction != 0 && direction != latchedDirection) {
            latchedDirection = direction
            onBoundaryReached()
        }
    }

    private fun directionOf(value: Float, threshold: Float): Int = when {
        value > threshold -> 1
        value < -threshold -> -1
        else -> 0
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberHapticMiuixOverscrollFactory(): OverscrollFactory {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        HapticMiuixOverscrollFactory {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
