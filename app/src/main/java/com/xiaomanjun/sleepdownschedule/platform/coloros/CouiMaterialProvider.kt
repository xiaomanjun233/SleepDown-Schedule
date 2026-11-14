package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.GlowCourseShortcutMenu
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.GlowOverflowMenuButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ControlMaterialRenderers
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalGlowMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ProvideControlMaterials
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls

@RequiresApi(34)
private object CouiHomeControls : HomeMaterialControls {
    @Composable
    override fun IconButton(iconRes: Int, contentDescription: String, dark: Boolean,
        interactive: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit,
        toolbarNavigation: Boolean, enabled: Boolean): Boolean {
        if (!LocalCouiHomeTopBarActive.current) return false
        CouiHomeIconButton(iconRes, contentDescription, dark, interactive, selected, modifier, onClick, enabled)
        return true
    }

    @Composable
    override fun ShortcutMenu(session: Any, bounds: Rect, dark: Boolean, actions: List<AddMenuAction>,
        closing: Boolean, onDismiss: () -> Unit, bindDismiss: (((() -> Unit) -> Unit)?) -> Unit,
        anchorFraction: Float): Boolean {
        GlowCourseShortcutMenu(session, bounds, dark, actions, closing, onDismiss, bindDismiss, anchorFraction)
        return true
    }

    @Composable
    override fun OverflowMenuButton(dark: Boolean, interactive: Boolean, actions: List<AddMenuAction>,
        modifier: Modifier, content: @Composable (onClick: () -> Unit) -> Unit): Boolean {
        GlowOverflowMenuButton(dark, interactive, actions, modifier, content = content)
        return true
    }

    @Composable
    override fun SearchField(value: String, onValueChange: (String) -> Unit, dark: Boolean,
        bottomOffset: Dp, entranceProgress: Float, largeScreen: Boolean, modifier: Modifier, backdrop: Backdrop?): Boolean {
        if (backdrop == null) return false
        CouiSchoolSearchField(value, onValueChange, dark, bottomOffset, entranceProgress, largeScreen, modifier, backdrop)
        return true
    }
}

@RequiresApi(34)
private fun couiRenderers() = ControlMaterialRenderers(
    home = CouiHomeControls,
    button = { request, interaction -> rememberGlowButtonMaterial(request, interaction) },
    followLight = { CouiFollowLight(it) },
    courseCard = { request, content -> CouiCourseCard(request, content) },
    courseBackdropFilter = ::couiCourseBackdropEffect,
    surface = { request, content -> CouiMaterialSurface(request, content = content) },
    cardLight = { CouiCardProjection(it) },
    pageIndicator = { CouiPagerIndicator(it) },
    slider = { CouiValueSlider(it) },
    switch = { CouiValueSwitch(it) },
    menuSurface = { request, content -> CouiHomeMenuSurface(request, content) },
)

@Composable
internal fun ProvideCouiMaterials(
    couiControlsEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val kit = if (Build.VERSION.SDK_INT >= 34) remember { couiRenderers() } else null
    val superPerformance = AppMaterialPreferences.isSuperPerformance
    val renderers = if (couiControlsEnabled && kit != null) {
        if (superPerformance) {
            kit.copy(surface = null, menuSurface = null, cardLight = null, followLight = null)
        } else kit
    } else ControlMaterialRenderers.LiquidGlass
    val courseMode = if (couiControlsEnabled && kit != null) {
        rememberCourseCardMaterialPreferences(CouiCourseBlurRadiusPx)
    } else null
    val glowSurface = kit?.surface?.takeUnless { superPerformance }
    CompositionLocalProvider(LocalGlowMaterialSurfaceRenderer provides glowSurface) {
        ProvideControlMaterials(renderers, courseCardMode = courseMode, content = content)
    }
}
