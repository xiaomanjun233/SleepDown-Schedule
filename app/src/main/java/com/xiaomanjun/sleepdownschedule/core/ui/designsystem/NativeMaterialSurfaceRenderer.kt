package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope

internal enum class NativeMaterialSurfaceRole { Content, Dialog, HomeDock, HomeNavigation }

/** A passive material background. Geometry stays with its owner; content stays in Compose. */
internal data class NativeMaterialSurfaceRequest(
    val modifier: Modifier, val shape: Shape, val dark: Boolean,
    val renderSurface: Boolean = true, val materialVisible: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val onDrawTint: (DrawScope.() -> Unit)? = null,
    val shapeProvider: (() -> Shape)? = null,
    val surfaceAlpha: () -> Float = { 1f },
    val edgeEffects: Boolean = true,
    val blurRadiusScale: Float = 1f,
    val tintAlphaScale: Float = 1f,
    val role: NativeMaterialSurfaceRole = NativeMaterialSurfaceRole.Content,
    val followTouch: Boolean = true,
    val followLightRadius: androidx.compose.ui.unit.Dp? = null,
    val elasticTouchFeedback: Boolean = false,
    val followLightIntensityScale: Float = 1f
)

internal val LocalNativeMaterialSurfaceRenderer =
    compositionLocalOf<(@Composable (NativeMaterialSurfaceRequest, @Composable () -> Unit) -> Boolean)?> { null }

/** Content can omit legacy press paint only when its surface actually uses native material. */
internal val LocalNativeMaterialSurfaceActive = compositionLocalOf { false }
internal val LocalNativeMaterialForeground = compositionLocalOf { androidx.compose.ui.graphics.Color.Unspecified }
