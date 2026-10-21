package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls

internal object ColorOsHomeMaterialControls : HomeMaterialControls {
    @Composable
    override fun Dock(tabs: List<Pair<Int, String>>, selectedIndex: Int, dark: Boolean,
        modifier: Modifier, onSelect: (Int) -> Unit) = ColorOsHomeDock(tabs, selectedIndex, dark, modifier, onSelect)

    @Composable
    override fun IconButton(iconRes: Int, contentDescription: String, dark: Boolean, interactive: Boolean,
        selected: Boolean, modifier: Modifier, onClick: () -> Unit, toolbarNavigation: Boolean, enabled: Boolean) =
        ColorOsHomeIconButton(iconRes, contentDescription, dark, interactive, selected, modifier, onClick, toolbarNavigation, enabled)

    @Composable
    override fun ParallelDock(selectedIndex: Int, dark: Boolean, buttonHidden: Boolean, modifier: Modifier,
        onSelect: (Int) -> Unit, onOpenMenu: (Rect, GraphicsLayer) -> Unit) =
        ColorOsParallelHomeDock(selectedIndex, dark, buttonHidden, modifier, onSelect, onOpenMenu)

    @Composable
    override fun FloatingButtonVisual(dark: Boolean, modifier: Modifier) = ColorOsHomeFloatingButtonVisual(dark, modifier)

    @Composable
    override fun ordinaryDockWidth() = colorOsOrdinaryDockWidth()

    @Composable
    override fun SearchField(value: String, onValueChange: (String) -> Unit, dark: Boolean,
        bottomOffset: Dp, entranceProgress: Float, largeScreen: Boolean, modifier: Modifier) =
        ColorOsSchoolSearchField(value, onValueChange, dark, bottomOffset, entranceProgress, largeScreen, modifier)

    @Composable
    override fun NameDialog(title: String, initialName: String, dark: Boolean, maxLength: Int?,
        onDismiss: () -> Unit, onConfirm: (String) -> Unit) = ColorOsNameDialog(title, initialName, dark, maxLength, onDismiss, onConfirm)

    @Composable
    override fun ShortcutMenu(session: Any, bounds: Rect, dark: Boolean, actions: List<AddMenuAction>,
        closing: Boolean, onDismiss: () -> Unit, bindDismiss: (((() -> Unit) -> Unit)?) -> Unit) =
        ColorOsCourseShortcutMenu(session, bounds, dark, actions, closing, onDismiss, bindDismiss)
}
