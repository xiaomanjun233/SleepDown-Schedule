package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.topappbar.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopBackButton
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogRenderInRootScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop

internal val LocalCouiSettingsListState = staticCompositionLocalOf<LazyListState?> { null }
internal val LocalCouiSettingsToolbar = staticCompositionLocalOf { false }

@Composable
internal fun CouiSettingsRootScaffold(title: String, config: ScheduleConfigEntity,
    content: @Composable (PaddingValues, Backdrop) -> Unit) {
    val list = rememberLazyListState()
    val header = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior {
        list.canScrollForward || list.canScrollBackward
    }
    val density = LocalDensity.current
    CouiSettingsPageScaffold(title = title, config = config, largeHeader = header,
        modifier = Modifier.nestedScroll(header.nestedScrollConnection)) { padding, backdrop ->
        val compensated = remember(header, padding, density) { header.state.contentPadding(padding, density) }
        CompositionLocalProvider(LocalCouiSettingsListState provides list) { content(compensated, backdrop) }
    }
}

@Composable
internal fun CouiSettingsPageScaffold(
    title: String,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    largeHeader: CouiTopAppBarScrollBehavior? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    topBarVisible: Boolean = true,
    showTopGradientBlur: Boolean = true,
    isolateContentFromBackdrop: Boolean = false,
    preserveStatusBarSpace: Boolean = false,
    topBarBackdropOverride: Backdrop? = null,
    topBarActions: @Composable (Backdrop?) -> Unit = {},
    content: @Composable (PaddingValues, Backdrop) -> Unit,
) {
    val pageConfig = settingsVisualConfig(config)
    val source = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "coui-settings-content")
    val backgroundSource = rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "coui-settings-background")
    val scene = rememberCenteredDialogSceneBackdrop("coui-settings-dialog-scene")
    val editor = remember { mutableFloatStateOf(0f) }
    val inheritedOverlay = LocalDetailActivityFloatingOverlayHost.current
    val overlays = remember(inheritedOverlay) { inheritedOverlay ?: DetailActivityFloatingOverlayHost() }
    GlassMiuixSettingsTheme(pageConfig) {
        val colors = CouiTheme.colorScheme
        val sampling = Build.VERSION.SDK_INT >= 31 && !isolateContentFromBackdrop
        val paneBackground = rememberSettingsPaneBackground(colors.background)
        val material = LocalCouiMaterialEnabled.current && showTopGradientBlur && !isolateContentFromBackdrop
        CompositionLocalProvider(
            LocalCenteredDialogSceneBackdrop provides scene,
            LocalCenteredDialogRenderInRootScaffold provides false,
            LocalSettingsPopupBackdrop provides if (sampling) scene else null,
            LocalSettingsEditorProgress provides editor,
            LocalDetailActivityFloatingOverlayHost provides overlays,
            LocalCouiMaterialEnabled provides material,
        ) {
            Box(modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .then(if (sampling) Modifier.glassBackdropProducer(backgroundSource) else Modifier)
                    .then(paneBackground.modifier))
                Scaffold(modifier = Modifier.fillMaxSize()
                    .then(if (sampling) Modifier.centeredDialogSceneProducer(scene) else Modifier),
                    containerColor = colors.background,
                    topBar = {
                        if (topBarVisible) {
                            val toolbar: @Composable () -> Unit = {
                                val chrome = Modifier.layout { measurable, constraints ->
                                    val node = measurable.measure(constraints)
                                    layout(node.width, node.height) {
                                        if (editor.floatValue < 1f) node.placeRelative(0, 0)
                                    }
                                }.graphicsLayer {
                                    alpha = 1f - editor.floatValue
                                    translationY = 28.dp.toPx() * editor.floatValue
                                }
                                CompositionLocalProvider(LocalCouiSettingsToolbar provides true) {
                                    val label: @Composable () -> Unit = {
                                        Text(text = title, kind = if (largeHeader != null) CouiTextKind.HomeTitle else CouiTextKind.Toolbar,
                                            textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val navigation: @Composable () -> Unit = {
                                        if (showBack) {
                                            if (Build.VERSION.SDK_INT >= 31) TopBackButton(source, pageConfig, onBack)
                                            else CouiSettingsToolbarButton(CouiIcons.Back, "返回", null, onBack)
                                        }
                                    }
                                    val insets = if (preserveStatusBarSpace) WindowInsets.statusBarsIgnoringVisibility
                                        else CouiTopAppBarDefaults.windowInsets
                                    if (largeHeader != null) LargeTopAppBar(modifier = chrome,
                                        title = label, navigationIcon = navigation,
                                        actions = { topBarActions(topBarBackdropOverride ?: source) },
                                        scrollBehavior = largeHeader, windowInsets = insets)
                                    else TopAppBar(modifier = chrome, title = label,
                                        navigationIcon = navigation,
                                        actions = { topBarActions(topBarBackdropOverride ?: source) }, windowInsets = insets)
                                }
                            }
                            if (LocalCouiLiquidMaterial.current && sampling && showTopGradientBlur)
                                SettingsGradientTopBar(pageConfig, source, true, content = toolbar)
                            else toolbar()
                        }
                    }) { padding ->
                    CompositionLocalProvider(LocalGlassSettingsContentTopPadding provides padding.calculateTopPadding()) {
                        Box(Modifier.fillMaxSize()
                            .then(if (sampling) Modifier.glassBackdropProducer(source) else Modifier)
                            .then(paneBackground.modifier)) { content(padding, backgroundSource) }
                    }
                }
                if (inheritedOverlay == null) overlays.content?.invoke()
            }
        }
    }
}

/** 与 COUI 的 48dp 命中区/40dp 表面相同；旧系统只绘制普通表面。 */
@Composable
internal fun CouiSettingsToolbarButton(icon: ImageVector, description: String, backdrop: Backdrop?,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= 34 && LocalCouiMaterialEnabled.current) {
        TopAppBarButton(modifier = modifier, onClick = onClick) {
            Icon(icon, description, Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
        }
        return
    }
    val dark = CouiTheme.isDark
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (Build.VERSION.SDK_INT >= 31 && LocalCouiLiquidMaterial.current && backdrop != null) {
            LiquidButton(onClick = onClick, backdrop = backdrop, modifier = Modifier.size(40.dp), height = 40.dp,
                contentPadding = PaddingValues(0.dp)) {
                Icon(icon, description, Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
            }
        } else Box(Modifier.size(40.dp).clip(CouiTopAppBarButtonDefaults.circleShape)
            .background(CouiTheme.colorScheme.container)
            .couiButtonFeedback(dark, interaction, true)
            .clickable(interaction, null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
        }
    }
}
