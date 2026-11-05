package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope

internal enum class NativeMaterialSurfaceRole { Content, Dialog, Form, HomeDock, HomeNavigation }

data class NativeSurfaceTouch(val position: androidx.compose.ui.geometry.Offset, val pressed: Boolean)

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
    val followLightIntensityScale: Float = 1f,
    /** A lifted surface receives the gesture from its stable owner, outside its hit path. */
    val externalTouch: (() -> NativeSurfaceTouch)? = null,
    val sampleBrightness: Float = 1f
)

/** Stroke-only overlay used when the liquid card should match the native card outline. */
internal data class NativeStrokeOverlayRequest(
    val modifier: Modifier,
    val shape: Shape,
    val dark: Boolean
)

internal val LocalNativeMaterialSurfaceRenderer =
    compositionLocalOf<(@Composable (NativeMaterialSurfaceRequest, @Composable () -> Unit) -> Boolean)?> { null }

/** Available independently of native glass so liquid mode can reuse the native outer stroke. */
internal val LocalNativeStrokeOverlayRenderer =
    compositionLocalOf<(@Composable (NativeStrokeOverlayRequest) -> Boolean)?> { null }

/** Content can omit legacy press paint only when its surface actually uses native material. */
internal val LocalNativeMaterialSurfaceActive = compositionLocalOf { false }
internal val LocalNativeMaterialForeground = compositionLocalOf { androidx.compose.ui.graphics.Color.Unspecified }
