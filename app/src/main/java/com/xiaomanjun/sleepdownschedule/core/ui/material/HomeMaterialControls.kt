package com.xiaomanjun.sleepdownschedule.core.ui.material

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction

internal data class DockImportSource(
    val boundsInRoot: Rect,
    val fallbackLayer: GraphicsLayer,
    val view: View? = null,
    val ownsView: Boolean = false,
    val release: () -> Unit = {}
)

/** A false result leaves layout and input with the calling Compose control. */
@Stable
internal interface HomeMaterialControls {
    @Composable
    fun Dock(tabs: List<Pair<Int, String>>, selectedIndex: Int, dark: Boolean,
        modifier: Modifier, onSelect: (Int) -> Unit): Boolean = false

    @Composable
    fun IconButton(iconRes: Int, contentDescription: String, dark: Boolean, interactive: Boolean,
        selected: Boolean, modifier: Modifier, onClick: () -> Unit,
        toolbarNavigation: Boolean = false, enabled: Boolean = true): Boolean = false

    @Composable
    fun ParallelDock(selectedIndex: Int, dark: Boolean, buttonHidden: Boolean, modifier: Modifier,
        onSelect: (Int) -> Unit, onOpenMenu: (DockImportSource) -> Unit): Boolean = false

    @Composable
    fun FloatingButtonVisual(dark: Boolean, modifier: Modifier): Boolean = false

    @Composable
    fun SearchField(value: String, onValueChange: (String) -> Unit, dark: Boolean,
        bottomOffset: Dp, entranceProgress: Float, largeScreen: Boolean, modifier: Modifier,
        backdrop: com.kyant.backdrop.Backdrop? = null): Boolean = false

    @Composable
    fun NameDialog(title: String, initialName: String, dark: Boolean, maxLength: Int?,
        onDismiss: () -> Unit, onConfirm: (String) -> Unit,
        onDraftChange: (String) -> Unit = {}): Boolean = false

    @Composable
    fun ShortcutMenu(session: Any, bounds: Rect, dark: Boolean, actions: List<AddMenuAction>,
        closing: Boolean, onDismiss: () -> Unit, bindDismiss: (((() -> Unit) -> Unit)?) -> Unit,
        anchorFraction: Float = 0f): Boolean = false

    @Composable
    fun OverflowMenuButton(dark: Boolean, interactive: Boolean, actions: List<AddMenuAction>,
        modifier: Modifier, content: @Composable (onClick: () -> Unit) -> Unit): Boolean = false
}

internal val LocalHomeMaterialControls = staticCompositionLocalOf<HomeMaterialControls> {
    object : HomeMaterialControls {}
}
