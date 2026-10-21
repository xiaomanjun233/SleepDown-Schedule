package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphController
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphControllerBridge
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase

internal enum class HomeAnchoredOverlayKind {
    Add,
    Personalize
}

internal enum class HomeAnchoredOverlayPhase {
    Idle,
    Preparing,
    Opening,
    Open,
    Closing,
    Disposing
}

internal val HomeAnchoredOverlayPhase.isMovingTransition: Boolean
    get() = this == HomeAnchoredOverlayPhase.Preparing ||
        this == HomeAnchoredOverlayPhase.Opening ||
        this == HomeAnchoredOverlayPhase.Closing ||
        this == HomeAnchoredOverlayPhase.Disposing

internal data class HomeAnchoredOverlayRequest(
    val kind: HomeAnchoredOverlayKind,
    val sourceBoundsInRoot: Rect,
    val sourcePressedScale: Float = 1f,
    val fromDock: Boolean = false
)

@Stable
internal class HomeAnchoredMorphState {
    val progress = Animatable(0f)
    val backgroundZoom = Animatable(1f)
    var renderedKind by mutableStateOf<HomeAnchoredOverlayKind?>(null)
        internal set
    private val controllerBridge = LiquidMorphControllerBridge {
        when (renderedKind) {
            HomeAnchoredOverlayKind.Add -> "home-three-dot-menu"
            HomeAnchoredOverlayKind.Personalize -> "home-personalization"
            null -> "home-anchored"
        }
    }
    val liquidMorphController: LiquidMorphController get() = controllerBridge.controller
    private var currentPhase by mutableStateOf(HomeAnchoredOverlayPhase.Idle)
    var phase: HomeAnchoredOverlayPhase
        get() = currentPhase
        internal set(value) {
            if (currentPhase == value) return
            currentPhase = value
            controllerBridge.synchronize(
                when (value) {
                    HomeAnchoredOverlayPhase.Idle -> LiquidMorphPhase.Idle
                    HomeAnchoredOverlayPhase.Preparing -> LiquidMorphPhase.Preparing
                    HomeAnchoredOverlayPhase.Opening -> LiquidMorphPhase.Opening
                    HomeAnchoredOverlayPhase.Open -> LiquidMorphPhase.Open
                    HomeAnchoredOverlayPhase.Closing -> LiquidMorphPhase.Closing
                    HomeAnchoredOverlayPhase.Disposing -> LiquidMorphPhase.Released
                }
            )
        }
}

@Composable
internal fun rememberHomeAnchoredMorphState(): HomeAnchoredMorphState =
    remember { HomeAnchoredMorphState() }
