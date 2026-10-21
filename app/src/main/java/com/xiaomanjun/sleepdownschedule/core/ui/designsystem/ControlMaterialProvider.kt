package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls

/** An absent capability leaves the existing Compose control in charge of rendering and input. */
@Immutable
internal data class ControlMaterialRenderers(
    val home: HomeMaterialControls = object : HomeMaterialControls {},
    val alert: (@Composable (NativeAlertRequest) -> Boolean)? = null,
    val button: (@Composable (NativeCapsuleButtonRequest, @Composable RowScope.() -> Unit) -> Boolean)? = null,
    val followLight: (@Composable (NativeFollowLightRequest) -> Boolean)? = null,
    val courseCard: (@Composable (NativeCourseCardRequest, @Composable () -> Unit) -> Boolean)? = null,
    val surface: (@Composable (NativeMaterialSurfaceRequest, @Composable () -> Unit) -> Boolean)? = null,
    val slider: (@Composable (NativeSliderRequest) -> Boolean)? = null,
    val switch: (@Composable (NativeSwitchRequest) -> Boolean)? = null,
    val segments: (@Composable (NativeSegmentRequest) -> Boolean)? = null,
    val menuRow: (@Composable (NativeMenuRowRequest) -> Boolean)? = null,
    val menuList: (@Composable (NativeMenuListRequest) -> Boolean)? = null,
    val menuSurface: (@Composable (NativeMenuSurfaceRequest, @Composable () -> Unit) -> Boolean)? = null
) {
    companion object {
        val LiquidGlass = ControlMaterialRenderers()
    }
}

@Composable
internal fun ProvideControlMaterials(
    renderers: ControlMaterialRenderers,
    courseCardMode: NativeCourseCardMode? = null,
    progressiveBlur: NativeProgressiveBlur? = null,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHomeMaterialControls provides renderers.home,
        LocalNativeAlertRenderer provides renderers.alert,
        LocalNativeCapsuleButtonRenderer provides renderers.button,
        LocalNativeFollowLightRenderer provides renderers.followLight,
        LocalNativeCourseCardMode provides courseCardMode,
        LocalNativeCourseCardRenderer provides renderers.courseCard,
        LocalNativeMaterialSurfaceRenderer provides renderers.surface,
        LocalNativeProgressiveBlur provides progressiveBlur,
        LocalNativeSliderRenderer provides renderers.slider,
        LocalNativeSwitchRenderer provides renderers.switch,
        LocalNativeSegmentRenderer provides renderers.segments,
        LocalNativeMenuRowRenderer provides renderers.menuRow,
        LocalNativeMenuListRenderer provides renderers.menuList,
        LocalNativeMenuSurfaceRenderer provides renderers.menuSurface,
        content = content
    )
}
