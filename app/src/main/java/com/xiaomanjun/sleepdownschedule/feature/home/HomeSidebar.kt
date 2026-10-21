package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeTodayIcon
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIcon
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuRowRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuItem
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuRowRequest
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Weeks

internal enum class HomeSidebarDestination { Day, Week, Settings, Courses, Schedules, EduImport }

@Stable
internal class HomeSidebarState(
    private val expandedState: MutableState<Boolean>,
    private val progress: State<Float>,
    val enabled: Boolean,
    val overlaysContent: Boolean,
    val startEdge: Dp,
    val endInset: Dp,
    val collapsedInset: Dp,
    val expandedInset: Dp
) {
    var expanded by expandedState

    val moving: Boolean by derivedStateOf {
        enabled && abs(progress.value - if (expanded) 1f else 0f) > 0.001f
    }

    val targetContentInset: Dp
        get() = when {
            !enabled -> 0.dp
            overlaysContent || !expanded -> collapsedInset
            else -> expandedInset
        }

    fun expansionProgress(): Float = progress.value

    fun animatedContentInset(): Dp = when {
        !enabled -> 0.dp
        overlaysContent -> collapsedInset
        else -> lerp(collapsedInset.value, expandedInset.value, progress.value).dp
    }

    val targetContentTopInset: Dp
        get() = if (enabled && !expanded) 56.dp else 0.dp

    fun collapseOverlay() {
        if (overlaysContent) expanded = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberHomeSidebarState(metrics: HomeAdaptiveMetrics): HomeSidebarState {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val portrait = metrics.screenHeight >= metrics.screenWidth
    val compactHeight = metrics.screenHeight < 480.dp
    val expanded = rememberSaveable(portrait, compactHeight) {
        mutableStateOf(!portrait && !compactHeight)
    }
    val progress = animateFloatAsState(
        targetValue = if (metrics.isLargeScreen && expanded.value) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "HomeSidebarExpansion"
    )
    val safeLeft = with(density) { WindowInsets.safeDrawing.getLeft(this, layoutDirection).toDp() }
    val safeRight = with(density) { WindowInsets.safeDrawing.getRight(this, layoutDirection).toDp() }
    val safeStart = if (layoutDirection == LayoutDirection.Ltr) safeLeft else safeRight
    val safeEnd = if (layoutDirection == LayoutDirection.Ltr) safeRight else safeLeft
    val startEdge = safeStart + 12.dp
    val collapsedInset = safeStart
    val expandedInset = safeStart + (metrics.screenWidth * 0.22f).coerceIn(200.dp, 320.dp)
    return remember(expanded, progress, metrics.isLargeScreen, portrait, startEdge, safeEnd, expandedInset) {
        HomeSidebarState(
            expanded, progress, metrics.isLargeScreen, portrait, startEdge,
            if (metrics.isLargeScreen) safeEnd else 0.dp, collapsedInset, expandedInset
        )
    }
}

/** Measure the page once per target; only placement observes the spring on each frame. */
internal fun Modifier.homeSidebarContentInset(state: HomeSidebarState): Modifier {
    val targetInset = state.targetContentInset
    if (!state.enabled) return this
    return layout { measurable, constraints ->
        val targetPx = targetInset.roundToPx()
        val endPx = state.endInset.roundToPx()
        val width = (constraints.maxWidth - targetPx - endPx).coerceAtLeast(1)
        val placeable = measurable.measure(constraints.copy(
            minWidth = width, maxWidth = width
        ))
        layout(constraints.maxWidth, placeable.height) {
            // Placement keeps glass sampling and real popup anchors in window coordinates.
            placeable.placeRelative(state.animatedContentInset().roundToPx(), 0)
        }
    }
}

/** One glass surface travels between the sidebar and the compact top Tab Bar. */
@Composable
internal fun HomeSidebar(
    state: HomeSidebarState,
    metrics: HomeAdaptiveMetrics,
    selected: HomeSidebarDestination,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    navigationEnabled: Boolean,
    visible: Boolean,
    followHomeGlass: Boolean,
    hasGradientBackground: Boolean = false,
    tabSelection: HomeSidebarDestination = selected,
    actions: List<AddMenuAction> = emptyList(),
    onAction: (AddMenuAction) -> Unit = { it.onClick() },
    onNavigate: (HomeSidebarDestination) -> Unit
) {
    if (!state.enabled || !visible) return
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = if (followHomeGlass) adaptiveGlass.lightGlass else !appUsesDarkTheme(config)
    val ink by animateColorAsState(
        targetValue = if (followHomeGlass) adaptiveGlass.contentColor else if (lightGlass) Color.Black else Color.White,
        animationSpec = tween(220), label = "HomeSidebarContentTheme"
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (!lightGlass && !config.hasAnyWallpaper() && !hasGradientBackground) {
            Color(0xFF2C2C2E).copy(alpha = 0.88f)
        } else {
            (if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF121212))
                .copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass) * 0.86f)
        },
        animationSpec = tween(220), label = "HomeSidebarSurfaceTheme"
    )
    val expansion = state.expansionProgress().coerceIn(0f, 1f)
    val tokens = GlassTokens.pill().copy(
        blur = homeChromeBlur(lerp(1.3f, 16f, expansion).dp, config),
        lensHeight = lerp(10f, 6f, expansion).dp,
        lensAmount = lerp(40f, 12f, expansion).dp,
        surfaceAlpha = homeChromeGlassSurfaceAlpha(lightGlass) * 0.86f,
        highlightAlpha = 0.035f
    )
    val shape = RoundedRectangle(24.dp)
    val enabled = navigationEnabled && !state.moving
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnAction by rememberUpdatedState(onAction)
    val scrimInteraction = remember { MutableInteractionSource() }
    BackHandler(enabled = state.overlaysContent && state.expanded && navigationEnabled) {
        state.expanded = false
    }
    fun navigate(destination: HomeSidebarDestination) {
        if (!currentEnabled) return
        state.collapseOverlay()
        currentOnNavigate(destination)
    }
    fun action(action: AddMenuAction) {
        if (!currentEnabled) return
        state.collapseOverlay()
        currentOnAction(action)
    }
    Box(Modifier.fillMaxSize()) {
        if (state.overlaysContent && state.expanded) {
            Box(
                Modifier.fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.12f * state.expansionProgress())) }
                    .semantics { contentDescription = "收起侧栏" }
                    .clickable(
                        enabled = navigationEnabled, interactionSource = scrimInteraction,
                        indication = null, onClick = { state.expanded = false }
                    )
            )
        }
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val progress = state.expansionProgress().coerceIn(0f, 1f)
                    val sidebarWidth = (state.expandedInset - state.startEdge).roundToPx()
                    val tabWidth = minOf(300.dp.roundToPx(), constraints.maxWidth - 24.dp.roundToPx())
                        .coerceAtLeast(1)
                    val sidebarHeight = (constraints.maxHeight -
                        (metrics.safeTop + metrics.safeBottom + 14.dp).roundToPx()).coerceAtLeast(1)
                    val width = lerp(tabWidth.toFloat(), sidebarWidth.toFloat(), progress).roundToInt()
                        .coerceIn(1, constraints.maxWidth)
                    val height = lerp(48.dp.toPx(), sidebarHeight.toFloat(), progress).roundToInt()
                        .coerceIn(1, constraints.maxHeight)
                    val start = lerp((constraints.maxWidth - tabWidth) / 2f, state.startEdge.toPx(), progress)
                    val top = (metrics.safeTop + 2.dp).toPx() + sin(progress * PI).toFloat() * 12.dp.toPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(start.roundToInt(), top.roundToInt())
                    }
                }
        ) {
        // The outer node occupies the window only to place the floating bar. Give input its
        // own measured child, otherwise a disabled bar can shield the dialog beside it.
        Box(
            Modifier.fillMaxSize().excludeParentPullGesture()
                .pointerInput(enabled) {
                    if (!enabled) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { testTag = if (state.expanded) "home_sidebar" else "home_tab_bar" }
        ) {
            // Glass clips its own drawContent to the material outline. Keep the slider a sibling
            // so its pressed lens can grow beyond the shell instead of being cut at both ends.
            if (state.expanded || state.moving) GlassSurface(
                backdrop = backdrop, config = config, tokens = tokens,
                surfaceColorOverride = surfaceColor, shape = shape,
                domain = if (selected == HomeSidebarDestination.Schedules) GlassBackdropDomain.PickerScene
                    else GlassBackdropDomain.ChromeCombined,
                debugLabel = "HomeSidebar",
                nativeMaterial = true,
                nativeDark = !lightGlass,
                restShadowAlpha = if (lightGlass && !config.hasAnyWallpaper()) 0.14f else 0f,
                modifier = Modifier.matchParentSize().then(if (LocalNativeMaterialSurfaceRenderer.current != null) Modifier
                    else Modifier.border(0.75.dp,
                    Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = if (lightGlass) 0.38f else 0.30f),
                        ink.copy(alpha = if (lightGlass) 0.06f else 0.12f),
                        Color.White.copy(alpha = if (lightGlass) 0.22f else 0.22f)
                    )), shape))
            ) {}
            if (state.expanded || state.moving) {
                Column(
                    Modifier.fillMaxSize().clipToBounds().padding(horizontal = 8.dp, vertical = 6.dp)
                        .graphicsLayer { alpha = ((state.expansionProgress() - 0.35f) / 0.65f).coerceIn(0f, 1f) }
                        .then(if (state.moving) Modifier.clearAndSetSemantics { } else Modifier)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(
                                painterResource(currentIconResId(LocalContext.current, !lightGlass)), null,
                                Modifier.size(28.dp).clip(RoundedRectangle(7.dp))
                            )
                            Text(stringResource(R.string.app_name), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HomeSidebarIconButton(
                            description = "切换为标签栏", dark = !lightGlass, enabled = enabled,
                            modifier = Modifier.size(40.dp), height = 40.dp,
                            onClick = { state.expanded = false }
                        ) {
                            Icon(painterResource(R.drawable.ic_tab_bar), null, tint = ink, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SidebarDestinationRow(HomeSidebarDestination.Day, "今日", HomeTodayIcon,
                            selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Day) }
                        SidebarDestinationRow(HomeSidebarDestination.Week, "课程表", MiuixIcons.Weeks,
                            selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Week) }
                        SidebarDestinationRow(HomeSidebarDestination.Settings, "设置", requireNotNull(homeActionIcon(R.drawable.ic_settings)),
                            selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Settings) }
                        if (actions.isNotEmpty()) {
                            SidebarSectionHeading("课表操作", ink)
                            SidebarDestinationRow(HomeSidebarDestination.Courses, "课程管理", MiuixIcons.Notes,
                                selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Courses) }
                            actions.firstOrNull { it.iconRes == R.drawable.ic_material_event }?.let { item ->
                                SidebarActionRow(item, ink, enabled, !lightGlass) { action(item) }
                            }
                            SidebarDestinationRow(HomeSidebarDestination.Schedules, "多课表管理", MiuixIcons.Layers,
                                selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Schedules) }
                            val imports = actions.filter {
                                it.iconRes != R.drawable.ic_material_event &&
                                    it.iconRes != R.drawable.ic_settings && it.iconRes != R.drawable.ic_courses
                            }
                            if (imports.isNotEmpty()) {
                                SidebarSectionHeading("导入", ink)
                                imports.forEach { item -> SidebarActionRow(item, ink, enabled, !lightGlass) { action(item) } }
                            }
                        }
                    }
                }
            }
            if (!state.expanded || state.moving) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .graphicsLayer {
                            alpha = (1f - state.expansionProgress() * 2.5f).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .then(if (state.moving) Modifier.clearAndSetSemantics { } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        HomeSidebarDestination.Day to "今日",
                        HomeSidebarDestination.Week to "课程表",
                        HomeSidebarDestination.Settings to "设置"
                    )
                    HomeSidebarTabBar(
                        tabs = tabs,
                        selected = tabSelection,
                        enabled = enabled,
                        moving = state.moving,
                        lightGlass = lightGlass,
                        ink = ink,
                        config = config,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        onNavigate = ::navigate,
                        onExpand = { state.expanded = true }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SidebarSectionHeading(label: String, ink: Color) {
    Spacer(Modifier.height(4.dp))
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp)
        .background(ink.copy(alpha = 0.14f)))
    Text(label, color = ink.copy(alpha = 0.54f), fontSize = 12.sp,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp))
}

@Composable
private fun SidebarDestinationRow(
    destination: HomeSidebarDestination,
    label: String,
    icon: ImageVector,
    selected: HomeSidebarDestination,
    ink: Color,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit
) {
    SidebarRow(label, ink, destination == selected, enabled, dark, onClick) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SidebarActionRow(action: AddMenuAction, ink: Color, enabled: Boolean, dark: Boolean, onClick: () -> Unit) {
    SidebarRow(action.label, ink, false, enabled, dark, onClick, Role.Button) {
        Icon(action.imageVector ?: homeActionIcon(action.iconRes) ?: MiuixIcons.Notes,
            null, Modifier.size(22.dp), tint = ink)
    }
}

@Composable
private fun SidebarRow(
    label: String,
    ink: Color,
    selected: Boolean,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Tab,
    icon: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedRectangle(16.dp))
            .background(if (selected) ink.copy(alpha = 0.09f) else Color.Transparent)
    ) {
        val nativeFeedback = LocalNativeMenuRowRenderer.current?.invoke(
            NativeMenuRowRequest(
                item = NativeMenuItem(title = label, enabled = enabled),
                dark = dark,
                modifier = Modifier.matchParentSize().clearAndSetSemantics { },
                pressed = pressed,
                contentVisible = false
            )
        ) == true
        Row(
            modifier = Modifier.fillMaxSize()
                .selectable(
                    selected = selected, enabled = enabled, role = role,
                    interactionSource = interaction,
                    indication = if (nativeFeedback) null else LocalIndication.current,
                    onClick = onClick
                )
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { icon() }
            Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp).clearAndSetSemantics { })
        }
    }
}
