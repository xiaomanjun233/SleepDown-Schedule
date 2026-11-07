package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction

/** Home chrome follows the wallpaper contrast while preserving each system material recipe. */
@Immutable
internal data class HomeMaterialStyle(
    val dark: Boolean,
    val weatherMaterial: Boolean,
    val foreground: Int?,
    val sampleBrightness: Float = 1f
)

internal val LocalHomeMaterialStyle = compositionLocalOf<HomeMaterialStyle?> { null }

/** The morph target and expanded menu share one neutral tint. The FAB keeps its accent. */
internal fun homeMaterialNeutralTint(dark: Boolean) =
    androidx.compose.ui.graphics.Color(if (dark) 0xFF050505 else 0xFFFFFFFF)
        .copy(alpha = if (dark) 0.40f else 0.28f)

internal val LocalHomeDockAccent = compositionLocalOf<Int?> { null }
internal val LocalHomeButtonAccent = compositionLocalOf<Int?> { null }
internal val LocalHomeOverflowMenu = compositionLocalOf<State<List<AddMenuAction>>?> { null }

/** 系统菜单报告真实窗口矩形，供既有目标表单/Activity保留返回锚点。 */
internal val LocalHomeOverflowMenuBounds = compositionLocalOf<((androidx.compose.ui.geometry.Rect) -> Unit)?> { null }
