package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls

/** An absent capability leaves the existing Compose control in charge of rendering and input. */
@Immutable
internal data class ControlMaterialRenderers(
    val home: HomeMaterialControls = object : HomeMaterialControls {},
    val button: (@Composable (ButtonMaterialRequest, androidx.compose.foundation.interaction.MutableInteractionSource) -> androidx.compose.ui.Modifier?)? = null,
    val followLight: (@Composable (NativeFollowLightRequest) -> Boolean)? = null,
    val courseCard: (@Composable (NativeCourseCardRequest, @Composable () -> Unit) -> Boolean)? = null,
    val courseBackdropFilter: ((Boolean, Float) -> androidx.compose.ui.graphics.RenderEffect)? = null,
    val surface: NativeMaterialSurfaceRenderer? = null,
    val strokeOverlay: (@Composable (NativeStrokeOverlayRequest) -> Boolean)? = null,
    val cardLight: (@Composable (NativeCardLightRequest) -> Boolean)? = null,
    val pageIndicator: (@Composable (NativePagerIndicatorRequest) -> Boolean)? = null,
    val slider: (@Composable (NativeSliderRequest) -> Boolean)? = null,
    val switch: (@Composable (NativeSwitchRequest) -> Boolean)? = null,
    val menuRow: (@Composable (NativeMenuRowRequest) -> Boolean)? = null,
    val menuList: (@Composable (NativeMenuListRequest) -> Boolean)? = null,
    val menuSurface: (@Composable (NativeMenuSurfaceRequest, @Composable () -> Unit) -> Boolean)? = null
) {
    companion object {
        val LiquidGlass = ControlMaterialRenderers()
    }
}

/** Resolve the active material capabilities, regardless of which settings entry selected them. */
@Composable
internal fun usesNativeMaterialMotion(): Boolean =
    LocalNativeMaterialSurfaceRenderer.current != null || LocalNativeCourseCardMode.current?.enabled == true

@Composable
internal fun ProvideControlMaterials(
    renderers: ControlMaterialRenderers,
    courseCardMode: NativeCourseCardMode? = null,
    progressiveBlur: NativeProgressiveBlur? = null,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHomeMaterialControls provides renderers.home,
        LocalButtonMaterial provides renderers.button,
        LocalNativeFollowLightRenderer provides renderers.followLight,
        LocalNativeCourseCardMode provides courseCardMode,
        LocalNativeCourseCardRenderer provides renderers.courseCard,
        LocalCourseBackdropFilter provides renderers.courseBackdropFilter,
        LocalNativeMaterialSurfaceRenderer provides renderers.surface,
        LocalNativeStrokeOverlayRenderer provides renderers.strokeOverlay,
        LocalNativeCardLightRenderer provides renderers.cardLight,
        LocalNativePagerIndicatorRenderer provides renderers.pageIndicator,
        LocalNativeProgressiveBlur provides progressiveBlur,
        LocalNativeSliderRenderer provides renderers.slider,
        LocalNativeSwitchRenderer provides renderers.switch,
        LocalNativeMenuRowRenderer provides renderers.menuRow,
        LocalNativeMenuListRenderer provides renderers.menuList,
        LocalNativeMenuSurfaceRenderer provides renderers.menuSurface,
        content = content
    )
}
