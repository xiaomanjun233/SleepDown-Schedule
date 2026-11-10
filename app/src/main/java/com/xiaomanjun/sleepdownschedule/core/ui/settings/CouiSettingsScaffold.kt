package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.topappbar.*
import com.itos.coui.search.SearchBar
import com.itos.coui.search.CouiSearchBarDefaults
import com.itos.coui.search.CouiSearchBarState
import com.itos.coui.search.rememberCouiSearchBarState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidSearchStyle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.rememberCouiTextStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogRenderInRootScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.avoidFloatingChrome
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val LocalCouiSettingsToolbar = staticCompositionLocalOf { false }
internal val LocalCouiSettingsContentPadding = staticCompositionLocalOf<PaddingValues?> { null }

internal data class CouiSettingsSearch(
    val query: String,
    val expanded: Boolean,
    val onQueryChange: (String) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
)

@Composable
internal fun CouiSettingsRootScaffold(
    title: String,
    config: ScheduleConfigEntity,
    search: CouiSettingsSearch,
    content: @Composable (PaddingValues, Backdrop, LazyListState, Boolean, suspend () -> Unit) -> Unit,
) {
    val list = rememberLazyListState()
    val header = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior {
        list.canScrollForward || list.canScrollBackward
    }
    val density = LocalDensity.current
    val searchState = rememberCouiSearchBarState(search.expanded)
    val searchClosing = !search.expanded && searchState.active
    BackHandler(enabled = search.expanded) { search.onExpandedChange(false) }
    LaunchedEffect(searchClosing) {
        if (searchClosing) list.stopScroll(MutatePriority.PreventUserInput)
    }
    CouiSettingsPageScaffold(title = title, config = config, largeHeader = header, rootSearch = search,
        rootSearchState = searchState,
        modifier = Modifier.nestedScroll(header.nestedScrollConnection)) { padding, backdrop ->
        val compensated = remember(header, padding, density) { header.state.contentPadding(padding, density) }
        val revealDistance = with(density) { CouiCollapsingAppBarDefaults.titleHeight.roundToPx() }
        content(compensated, backdrop, list, !searchClosing) {
            if (search.expanded) {
                if (list.canScrollBackward) list.animateScrollToItem(0)
            } else if (!searchState.active) {
                returnSettingsListToTop(list, header.state, revealDistance)
            }
        }
    }
}

private suspend fun returnSettingsListToTop(
    list: LazyListState,
    header: CouiCollapsingAppBarState,
    revealDistance: Int,
) = coroutineScope {
    if (!list.canScrollBackward) {
        header.expand()
        return@coroutineScope
    }
    val startFraction = header.collapsedFraction
    var startOffset = 0
    val reveal = launch(start = CoroutineStart.UNDISPATCHED) {
        // Once the first group is visible, derive the header reveal from the list's actual offset.
        snapshotFlow {
            list.firstVisibleItemScrollOffset.takeIf { list.firstVisibleItemIndex == 0 }
        }.collect { offset ->
            if (offset != null) {
                if (startOffset == 0) startOffset = offset.coerceIn(1, revealDistance.coerceAtLeast(1))
                header.snapScrollCollapseFraction(startFraction * (offset.toFloat() / startOffset).coerceAtMost(1f))
            }
        }
    }
    try {
        list.animateScrollToItem(0)
        header.snapScrollCollapseFraction(0f)
    } finally {
        reveal.cancel()
    }
}

@Composable
internal fun CouiSettingsTabletDetailPaneScaffold(
    title: String, config: ScheduleConfigEntity, modifier: Modifier = Modifier,
    topBarVisible: Boolean = true, showBackButton: Boolean = false,
    onBack: () -> Unit = {}, topBarActions: @Composable (Backdrop?) -> Unit = {},
    content: @Composable (Backdrop?) -> Unit,
) {
    CouiSettingsPageScaffold(title, config, modifier, showBack = showBackButton, onBack = onBack,
        topBarVisible = topBarVisible, avoidHomeTabBar = true, topBarActions = topBarActions) { _, source ->
        content(source.takeIf { Build.VERSION.SDK_INT >= 31 })
    }
}

@Composable
internal fun CouiSettingsPageScaffold(
    title: String,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    largeHeader: CouiTopAppBarScrollBehavior? = null,
    rootSearch: CouiSettingsSearch? = null,
    rootSearchState: CouiSearchBarState? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    topBarVisible: Boolean = true,
    showTopGradientBlur: Boolean = true,
    isolateContentFromBackdrop: Boolean = false,
    preserveStatusBarSpace: Boolean = false,
    avoidHomeTabBar: Boolean = false,
    avoidIme: Boolean = true,
    topBarBackdropOverride: Backdrop? = null,
    topBarBackgroundColor: androidx.compose.runtime.State<Color?>? = null,
    topBarActions: @Composable (Backdrop?) -> Unit = {},
    topBarNavigation: (@Composable () -> Unit)? = null,
    centerTopBarTitle: Boolean = false,
    content: @Composable (PaddingValues, Backdrop) -> Unit,
) {
    val pageConfig = settingsVisualConfig(config)
    val source = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "coui-settings-content")
    val backgroundSource = rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "coui-settings-background")
    val scene = rememberCenteredDialogSceneBackdrop("coui-settings-dialog-scene")
    val editor = remember { mutableFloatStateOf(0f) }
    val inheritedOverlay = LocalDetailActivityFloatingOverlayHost.current
    val overlays = remember(inheritedOverlay) { inheritedOverlay ?: DetailActivityFloatingOverlayHost() }
    val searchState = rootSearchState ?: rememberCouiSearchBarState(rootSearch?.expanded == true)
    CouiSettingsTheme(pageConfig) {
        val colors = CouiTheme.colorScheme
        val sampling = Build.VERSION.SDK_INT >= 31 && !isolateContentFromBackdrop
        val paneBackground = rememberSettingsPaneBackground(colors.background)
        val material = LocalCouiMaterialEnabled.current && showTopGradientBlur && !isolateContentFromBackdrop
        val webToolbarMaterial = LocalCouiMaterialEnabled.current && topBarBackdropOverride != null
        CompositionLocalProvider(
            LocalCenteredDialogSceneBackdrop provides scene,
            LocalCenteredDialogRenderInRootScaffold provides false,
            LocalSettingsPopupBackdrop provides if (sampling) scene else null,
            LocalSettingsEditorProgress provides editor,
            LocalDetailActivityFloatingOverlayHost provides overlays,
            LocalCouiMaterialEnabled provides material,
        ) {
            SettingsMiuixPopupHost {
            ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { windowSource ->
            Box(modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .then(if (sampling) Modifier.glassBackdropProducer(backgroundSource) else Modifier)
                    .then(paneBackground.modifier))
                // 与 DemoApp 一致：弹窗持有输入时，下层页面不跟随键盘重新布局。
                val imeAvoidance = if (avoidIme && LocalWindowInfo.current.isWindowFocused) Modifier.imePadding() else Modifier
                // Keep the searching toolbar's viewport and backdrop source at their window size.
                // Only the results viewport needs to follow the IME while the capsules separate.
                Scaffold(modifier = Modifier.fillMaxSize()
                    .then(if (rootSearch == null) imeAvoidance else Modifier)
                    .then(windowSource)
                    .then(if (sampling) Modifier.centeredDialogSceneProducer(scene) else Modifier),
                    containerColor = colors.background,
                    // The opaque settings list already owns component-local blend modes.
                    // During IME resize, keep its display list without a second viewport texture.
                    isolateBodyBackdrop = rootSearch == null || colors.background.alpha < 1f,
                    topBar = {
                        if (topBarVisible) {
                            val toolbar: @Composable () -> Unit = {
                                val toolbarContent = if (avoidHomeTabBar) Modifier.avoidFloatingChrome() else Modifier
                                val chrome = Modifier.layout { measurable, constraints ->
                                    val node = measurable.measure(constraints)
                                    layout(node.width, node.height) {
                                        if (editor.floatValue < 1f) node.placeRelative(0, 0)
                                    }
                                }.graphicsLayer {
                                    alpha = 1f - editor.floatValue
                                    translationY = 28.dp.toPx() * editor.floatValue
                                }
                                val sampledDark = topBarBackgroundColor?.value?.let { it.luminance() < 0.179f }
                                val toolbarForeground = sampledDark?.let { if (it) Color.White else Color.Black }
                                    ?: colors.onSurface
                                CompositionLocalProvider(LocalCouiSettingsToolbar provides true,
                                    LocalCouiMaterialEnabled provides (material || webToolbarMaterial)) {
                                    val label: @Composable () -> Unit = {
                                        Text(text = title, kind = if (largeHeader != null) CouiTextKind.HomeTitle else CouiTextKind.Toolbar,
                                            color = toolbarForeground, textAlign = TextAlign.Start, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                    val navigation: @Composable () -> Unit = {
                                        if (showBack) {
                                            if (topBarNavigation != null) topBarNavigation()
                                            else {
                                                CouiSettingsToolbarButton(topBarBackdropOverride ?: source, onBack,
                                                    darkOverride = sampledDark, sampleExplicitBackdrop = webToolbarMaterial) {
                                                    Icon(CouiIcons.Back, "返回", Modifier.size(24.dp), tint = toolbarForeground)
                                                }
                                            }
                                        }
                                    }
                                    val insets = if (preserveStatusBarSpace) WindowInsets.statusBarsIgnoringVisibility
                                        else CouiTopAppBarDefaults.windowInsets
                                    val barColors = CouiTopAppBarDefaults.colors(backgroundColor = Color.Transparent)
                                    val searchBar: @Composable () -> Unit = {
                                        if (rootSearch != null && Build.VERSION.SDK_INT >= 31 && !LocalCouiLiquidMaterial.current) SearchBar(
                                            state = searchState, query = rootSearch.query,
                                            onQueryChange = rootSearch.onQueryChange,
                                            onExpandedChange = rootSearch.onExpandedChange,
                                            placeholder = "搜索设置项", cancelText = "取消", clearDescription = "清空搜索",
                                            shadowProgress = largeHeader?.state?.collapsedFraction ?: 0f,
                                        ) else if (rootSearch != null) CouiCompatibleSettingsSearch(rootSearch,
                                            backdrop = source.takeIf { sampling && LocalCouiLiquidMaterial.current })
                                    }
                                    if (largeHeader != null && rootSearch != null && Build.VERSION.SDK_INT >= 28) {
                                        CollapsingTopAppBar(modifier = chrome, state = largeHeader.state,
                                            searchState = searchState, title = title, color = colors.onSurface,
                                            backgroundColor = if (LocalCouiLiquidMaterial.current) Color.Transparent else colors.background,
                                            searchBar = searchBar)
                                    } else if (largeHeader != null && rootSearch != null) {
                                        Column(chrome) {
                                            TopAppBar(title = label, colors = barColors, windowInsets = insets)
                                            Box(Modifier.padding(vertical = 8.dp)) {
                                                searchBar()
                                            }
                                        }
                                    } else if (largeHeader != null) LargeTopAppBar(modifier = chrome,
                                        title = label, navigationIcon = navigation,
                                        actions = { topBarActions(topBarBackdropOverride ?: source) },
                                        scrollBehavior = largeHeader, windowInsets = insets, colors = barColors)
                                    else TopAppBar(modifier = chrome, title = label,
                                        navigationIcon = navigation,
                                        actions = { topBarActions(topBarBackdropOverride ?: source) }, windowInsets = insets,
                                        colors = barColors, contentModifier = toolbarContent, centerTitle = centerTopBarTitle)
                                }
                            }
                            if (LocalCouiLiquidMaterial.current && sampling && showTopGradientBlur)
                                SettingsGradientTopBar(pageConfig, source, true, content = toolbar)
                            else toolbar()
                        }
                    }) { padding ->
                    CompositionLocalProvider(
                        LocalGlassSettingsContentTopPadding provides padding.calculateTopPadding(),
                        LocalCouiSettingsContentPadding provides padding,
                    ) {
                        Box(Modifier.fillMaxSize()
                            // COUI 顶栏已从 Scaffold 的正文源采样；液态才需要第二份 Kyant 正文源。
                            .then(if (sampling && LocalCouiLiquidMaterial.current) Modifier.glassBackdropProducer(source) else Modifier)
                            .then(paneBackground.modifier)
                            .then(if (rootSearch != null) imeAvoidance else Modifier)) { content(padding, backgroundSource) }
                    }
                }
                if (inheritedOverlay == null) overlays.content?.invoke()
            }
            }
            }
        }
    }
}

/** 液态材质及旧系统沿用 COUI 搜索几何，分别由 glass 和普通填色提供表面。 */
@Composable
private fun CouiCompatibleSettingsSearch(search: CouiSettingsSearch, backdrop: Backdrop?) {
    val colors = CouiTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dark = CouiTheme.isDark
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    LaunchedEffect(search.expanded) {
        if (!search.expanded) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    Row(Modifier.fillMaxWidth().height(CouiSearchBarDefaults.height)
        .padding(horizontal = CouiPageDefaults.horizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CouiSearchBarDefaults.cancelGap)) {
        val field: @Composable () -> Unit = {
        BasicTextField(search.query, search.onQueryChange,
            Modifier.fillMaxSize()
                .onFocusChanged { if (it.isFocused) search.onExpandedChange(true) },
            textStyle = rememberCouiTextStyle(CouiTextKind.Search).copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface), singleLine = true,
            decorationBox = { field ->
                Row(Modifier.fillMaxSize().padding(horizontal = CouiSearchBarDefaults.iconStart),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(CouiSearchBarDefaults.innerIconSlot), contentAlignment = Alignment.Center) {
                        Icon(CouiIcons.SearchField, null, Modifier.size(CouiSearchBarDefaults.iconSize),
                            tint = colors.onSurfaceVariant)
                    }
                    Box(Modifier.weight(1f)) {
                        if (search.query.isEmpty()) Text(text = "搜索设置项", kind = CouiTextKind.Search,
                            color = colors.onSurfaceVariant)
                        field()
                    }
                    if (search.query.isNotEmpty()) Box(Modifier.size(CouiSearchBarDefaults.innerIconSlot)
                        .clickable { search.onQueryChange("") }, contentAlignment = Alignment.Center) {
                        Icon(CouiIcons.Close, "清空搜索", Modifier.size(CouiSearchBarDefaults.iconSize),
                            tint = colors.onSurfaceVariant)
                    }
                }
            })
        }
        val surfaceHeight = if (backdrop != null) LiquidSearchStyle.height else CouiSearchBarDefaults.surfaceHeight
        val fieldModifier = Modifier.weight(1f).height(surfaceHeight)
        if (backdrop != null) Box(fieldModifier) {
            LiquidButton(onClick = {}, backdrop = backdrop, modifier = Modifier.fillMaxSize(), height = surfaceHeight,
                contentPadding = PaddingValues(0.dp), blurRadius = LiquidSearchStyle.blur,
                lensHeight = LiquidSearchStyle.lensHeight, lensAmount = LiquidSearchStyle.lensAmount,
                surfaceColor = LiquidSearchStyle.surface(dark), chromaticAberration = false,
                shadowEnabled = !dark, shadowStyle = LiquidSearchStyle.shadow, clipToBounds = false,
                clickTargetEnabled = false, pressExpansion = LiquidSearchStyle.pressExpansion,
                sharedInteractiveHighlight = highlight) {}
            Box(Modifier.fillMaxSize().liquidButtonVisualTransform(highlight, LiquidSearchStyle.pressExpansion)
                .then(highlight.gestureModifier)) { field() }
        } else Box(fieldModifier.clip(CouiSearchBarDefaults.shape).background(colors.container)) { field() }
        val cancel = {
            keyboard?.hide()
            focus.clearFocus()
            search.onExpandedChange(false)
        }
        if (search.expanded) {
            if (backdrop != null) LiquidButton(onClick = cancel, backdrop = backdrop,
                height = surfaceHeight, blurRadius = LiquidSearchStyle.blur,
                lensHeight = LiquidSearchStyle.lensHeight, lensAmount = LiquidSearchStyle.lensAmount,
                surfaceColor = LiquidSearchStyle.surface(dark), chromaticAberration = false,
                shadowEnabled = !dark, shadowStyle = LiquidSearchStyle.shadow,
                contentPadding = PaddingValues(horizontal = CouiSearchBarDefaults.cancelPadding)) {
                Text(text = "取消", kind = CouiTextKind.SearchCancel, color = colors.onSurface)
            } else Box(Modifier.height(CouiSearchBarDefaults.surfaceHeight).clip(CouiSearchBarDefaults.shape)
                .background(colors.container).clickable(onClick = cancel)
                .padding(horizontal = CouiSearchBarDefaults.cancelPadding), contentAlignment = Alignment.Center) {
                Text(text = "取消", kind = CouiTextKind.SearchCancel)
            }
        }
    }
}

/** 与上游 Demo 相同的 48dp 命中区、40dp 表面和系统材质；液态模式保留原材质。 */
@Composable
internal fun CouiSettingsToolbarButton(backdrop: Backdrop?, onClick: () -> Unit,
    modifier: Modifier = Modifier, darkOverride: Boolean? = null, sampleExplicitBackdrop: Boolean = false,
    content: @Composable () -> Unit) {
    val dark = darkOverride ?: CouiTheme.isDark
    if (Build.VERSION.SDK_INT >= 34 && LocalCouiMaterialEnabled.current && LocalCouiSettingsToolbar.current) {
        val density = LocalDensity.current.density
        val contour = LocalCouiContourGlowEnabled.current
        val scheme = CouiTheme.colorSchemeFor(dark)
        val style = CouiTopAppBarButtonDefaults.style(dark).copy(
                contentColor = scheme.onSurface,
                textStyle = rememberCouiTextStyle(CouiTextKind.Toolbar),
                cursorColor = scheme.accent,
                material = { CouiTopAppBarButtonDefaults.systemMaterial(dark, it) },
                shadow = { CouiTopAppBarButtonDefaults.shadow(dark, it, density, contour, alphaScale = 1f) },
            )
        if (sampleExplicitBackdrop && backdrop != null) {
            TopAppBarButton(modifier = modifier, onClick = onClick, style = style,
                backdrop = com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop(backdrop)) { content() }
        } else TopAppBarButton(modifier = modifier, onClick = onClick, style = style) { content() }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    if (Build.VERSION.SDK_INT >= 31 && LocalCouiLiquidMaterial.current && backdrop != null) {
        Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
            LiquidButton(onClick = onClick, backdrop = backdrop, modifier = Modifier.size(40.dp), height = 40.dp,
                contentPadding = PaddingValues(0.dp), content = { content() })
        }
    } else Box(modifier.size(48.dp).clip(CouiShape(24.dp, 2f))
        .couiButtonFeedback(dark, interaction, true)
        .clickable(interaction, null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center, content = { content() })
}

/** ColorOS action-menu text uses a 60dp minimum width and a 40dp capsule. */
@Composable
internal fun CouiSettingsToolbarTextButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val dark = CouiTheme.isDark
    val textStyle = rememberCouiTextStyle(CouiTextKind.Button)
    if (Build.VERSION.SDK_INT >= 34 && LocalCouiMaterialEnabled.current) {
        val style = CouiTopAppBarButtonDefaults.style(dark).copy(
            textStyle = textStyle,
            material = { CouiTopAppBarButtonDefaults.systemMaterial(dark, it) }
        )
        TopAppBarButton(onClick = onClick, enabled = enabled, text = true, style = style) {
            Text(text = label, kind = CouiTextKind.Button, color = LocalCouiContentColor.current, maxLines = 1)
        }
    } else {
        val colors = CouiButtonDefaults.secondaryColors(dark)
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            CouiCapsuleButton(
                modifier = Modifier.widthIn(min = 60.dp), onClick = onClick, dark = dark, enabled = enabled,
                colors = colors, height = 40.dp, contentPadding = PaddingValues(horizontal = 12.dp),
                preserveMaterialWhenDisabled = true
            ) {
                Text(text = label, kind = CouiTextKind.Button,
                    color = if (enabled) colors.content else colors.disabledContent, maxLines = 1)
            }
        }
    }
}
