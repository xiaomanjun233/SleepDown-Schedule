package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction

/** Weather uses COUI's neutral/transparent button material, independent of its sky colors. */
@Immutable
internal data class HomeMaterialStyle(
    val dark: Boolean,
    val weatherMaterial: Boolean,
    val foreground: Int?
)

internal val LocalHomeMaterialStyle = compositionLocalOf<HomeMaterialStyle?> { null }

/** The morph target and expanded menu share one neutral tint. The FAB keeps its accent. */
internal fun homeMaterialNeutralTint(dark: Boolean) =
    androidx.compose.ui.graphics.Color(if (dark) 0xFF050505 else 0xFFFFFFFF)
        .copy(alpha = if (dark) 0.40f else 0.28f)

internal val LocalHomeDockAccent = compositionLocalOf<Int?> { null }
internal val LocalHomeButtonAccent = compositionLocalOf<Int?> { null }
internal val LocalHomeOverflowMenu = compositionLocalOf<State<List<AddMenuAction>>?> { null }
