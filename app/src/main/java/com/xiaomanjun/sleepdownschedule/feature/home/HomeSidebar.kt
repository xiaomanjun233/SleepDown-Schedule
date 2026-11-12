package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.pageVerticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.RoundedRectangularShape
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeTodayIcon
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIcon
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuRowRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuItem
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuRowRequest
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Weeks

internal enum class HomeSidebarDestination { Day, Week, Settings, Courses, Schedules, EduImport }

private val HomeSidebarPanelCorner = 32.dp
private val HomeSidebarPanelShape = RoundedRectangle(HomeSidebarPanelCorner)
private val HomeSidebarTabs = listOf(
    HomeSidebarDestination.Day to "今日",
    HomeSidebarDestination.Week to "课程表",
    HomeSidebarDestination.Settings to "设置"
)

private class HomeSidebarMorphShape(
    private val state: HomeSidebarState,
    override val style: RoundedCornerStyle = HomeSidebarPanelShape.style,
) : RoundedRectangularShape {
    private fun outlineShape() = RoundedRectangle(
        lerp(HomeSidebarTabCorner.value, HomeSidebarPanelCorner.value, state.shellExpansion()).dp,
        style,
    )

    override fun corners(size: Size, layoutDirection: LayoutDirection, density: Density) =
        outlineShape().corners(size, layoutDirection, density)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        outlineShape().createOutline(size, layoutDirection, density)

    override fun copy(style: RoundedCornerStyle): RoundedRectangularShape = HomeSidebarMorphShape(state, style)
}

@Stable
internal class HomeSidebarState(
    private val expandedState: MutableState<Boolean>,
    private val transition: Transition<Boolean>,
    private val horizontal: State<Float>,
    private val vertical: State<Float>,
    private val tabEntries: List<State<Float>>,
    val enabled: Boolean,
    val overlaysContent: Boolean,
    val startEdge: Dp,
    val endInset: Dp,
    val collapsedInset: Dp,
    val expandedInset: Dp
) {
    var expanded by expandedState

    val moving: Boolean by derivedStateOf {
        enabled && (transition.currentState != expanded || transition.isRunning)
    }

    val targetContentInset: Dp
        get() = when {
            !enabled -> 0.dp
            overlaysContent || !expanded -> collapsedInset
            else -> expandedInset
        }

    fun horizontalExpansion(): Float = horizontal.value.coerceIn(0f, 1f)

    fun verticalExpansion(): Float = vertical.value.coerceIn(0f, 1f)

    // The capsule settles into its destination size before the sideways travel stops.
    fun shellExpansion(): Float = ((horizontalExpansion() - 0.2f) / 0.8f).coerceIn(0f, 1f)

    fun sidebarAlpha(): Float {
        val arrived = minOf(shellExpansion(), tabEntryExpansion(0), tabEntryExpansion(HomeSidebarTabs.lastIndex))
        return ((arrived - 0.68f) / 0.32f).coerceIn(0f, 1f)
    }

    fun tabBarAlpha(): Float {
        val remaining = maxOf(horizontalExpansion(), tabEntryExpansion(0), tabEntryExpansion(HomeSidebarTabs.lastIndex))
        return (1f - remaining / 0.25f).coerceIn(0f, 1f)
    }

    fun tabEntryExpansion(index: Int): Float = tabEntries[index].value

    fun animatedContentInset(): Dp = when {
        !enabled -> 0.dp
        overlaysContent -> collapsedInset
        else -> lerp(collapsedInset.value, expandedInset.value, horizontalExpansion()).dp
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
    val motion = updateTransition(metrics.isLargeScreen && expanded.value, label = "HomeNavigationMorph")
    val horizontal = motion.animateFloat(transitionSpec = {
        if (targetState) homeNavSpring(0.20f, 0.97f) else homeNavSpring(0.40f, 0.82f)
    }, label = "HomeNavigationHorizontal") { if (it) 1f else 0f }
    val vertical = motion.animateFloat(transitionSpec = {
        if (targetState) homeNavSpring(0.40f, 0.72f) else homeNavSpring(0.20f, 0.97f)
    }, label = "HomeNavigationVertical") { if (it) 1f else 0f }
    val tabEntries = HomeSidebarTabs.mapIndexed { index, (destination, _) ->
        motion.animateFloat(transitionSpec = {
            val order = if (targetState) index else HomeSidebarTabs.lastIndex - index
            tween(if (targetState) 300 else 320, delayMillis = order * 20, easing = FastOutSlowInEasing)
        }, label = "HomeNavigation${destination.name}") { if (it) 1f else 0f }
    }
    val safeLeft = with(density) { WindowInsets.safeDrawing.getLeft(this, layoutDirection).toDp() }
    val safeRight = with(density) { WindowInsets.safeDrawing.getRight(this, layoutDirection).toDp() }
    val safeStart = if (layoutDirection == LayoutDirection.Ltr) safeLeft else safeRight
    val safeEnd = if (layoutDirection == LayoutDirection.Ltr) safeRight else safeLeft
    val startEdge = safeStart + 12.dp
    val collapsedInset = safeStart
    val expandedInset = safeStart + (metrics.screenWidth * 0.22f).coerceIn(200.dp, 320.dp)
    return remember(expanded, motion, horizontal, vertical, tabEntries, metrics.isLargeScreen, portrait,
        startEdge, safeEnd, expandedInset) {
        HomeSidebarState(
            expanded, motion, horizontal, vertical, tabEntries, metrics.isLargeScreen, portrait, startEdge,
            if (metrics.isLargeScreen) safeEnd else 0.dp, collapsedInset, expandedInset
        )
    }
}

private fun homeNavSpring(responseSeconds: Float, damping: Float) = spring<Float>(
    dampingRatio = damping,
    stiffness = (2f * PI.toFloat() / responseSeconds).let { it * it },
    visibilityThreshold = 0.002f,
)

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
    tabSelection: HomeSidebarDestination = selected,
    onTabBarBoundsChanged: (Rect?) -> Unit = {},
    onJumpWeek: () -> Unit,
    onNavigate: (HomeSidebarDestination) -> Unit
) {
    val currentBoundsCallback by rememberUpdatedState(onTabBarBoundsChanged)
    DisposableEffect(Unit) { onDispose { currentBoundsCallback(null) } }
    SideEffect { if (!state.enabled || !visible || state.expanded || state.moving) currentBoundsCallback(null) }
    if (!state.enabled || !visible) return
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = if (followHomeGlass) adaptiveGlass.lightGlass else !appUsesDarkTheme(config)
    val ink by animateColorAsState(
        targetValue = if (followHomeGlass) adaptiveGlass.contentColor else if (lightGlass) Color.Black else Color.White,
        animationSpec = tween(220), label = "HomeSidebarContentTheme"
    )
    val moving = state.moving
    val shape = remember(state) { HomeSidebarMorphShape(state) }
    val tabWidth = minOf(300.dp, metrics.screenWidth - 24.dp)
    val sidebarScroll = rememberScrollState()
    val enabled = navigationEnabled && !moving
    val elasticTabBar = enabled && !state.expanded
    val touch = rememberCouiTouchMotion(elasticTabBar)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnJumpWeek by rememberUpdatedState(onJumpWeek)
    val scrimInteraction = remember { MutableInteractionSource() }
    BackHandler(enabled = state.overlaysContent && state.expanded && navigationEnabled) {
        state.expanded = false
    }
    fun navigate(destination: HomeSidebarDestination) {
        if (!currentEnabled) return
        state.collapseOverlay()
        currentOnNavigate(destination)
    }
    fun jumpWeek() {
        if (!currentEnabled) return
        state.collapseOverlay()
        currentOnJumpWeek()
    }
    Box(Modifier.fillMaxSize()) {
        if (state.overlaysContent && (state.expanded || moving)) {
            Box(
                Modifier.fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.12f * state.verticalExpansion())) }
                    .then(if (state.expanded) Modifier.semantics { contentDescription = "收起侧栏" }
                        .clickable(
                            enabled = navigationEnabled, interactionSource = scrimInteraction,
                            indication = null, onClick = { state.expanded = false }
                        ) else Modifier)
            )
        }
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val progress = state.shellExpansion()
                    val sidebarWidth = (state.expandedInset - state.startEdge).roundToPx()
                    val tabWidthPx = minOf(tabWidth.roundToPx(), constraints.maxWidth - 24.dp.roundToPx())
                        .coerceAtLeast(1)
                    val sidebarHeight = (constraints.maxHeight -
                        (metrics.safeTop + metrics.safeBottom + 14.dp).roundToPx()).coerceAtLeast(1)
                    val width = lerp(tabWidthPx.toFloat(), sidebarWidth.toFloat(), progress).roundToInt()
                        .coerceIn(1, constraints.maxWidth)
                    val height = lerp(HomeSidebarTabHeight.toPx(), sidebarHeight.toFloat(), progress).roundToInt()
                        .coerceIn(1, constraints.maxHeight)
                    val start = lerp((constraints.maxWidth - tabWidthPx) / 2f, state.startEdge.toPx(),
                        state.horizontalExpansion())
                    val top = metrics.safeTop.toPx() + 2.dp.toPx() * state.verticalExpansion()
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
                .then(if (!state.expanded && !moving) Modifier.onGloballyPositioned {
                    currentBoundsCallback(it.boundsInRoot())
                } else Modifier)
                .couiTouchMotion(touch, expanded = elasticTabBar)
                .graphicsLayer {
                    scaleX = touch.scaleX.value
                    scaleY = touch.scaleY.value
                    translationX = touch.translationX.value
                    translationY = touch.translationY.value
                }
                .pointerInput(enabled) {
                    if (!enabled) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { testTag = if (state.expanded) "home_sidebar" else "home_tab_bar" }
        ) {
            HomeNavigationSurface(
                config = config, backdrop = backdrop, lightGlass = lightGlass, shape = shape,
                domain = if (selected == HomeSidebarDestination.Schedules) GlassBackdropDomain.PickerScene
                    else GlassBackdropDomain.ChromeCombined,
                modifier = Modifier.matchParentSize(),
                followTouch = elasticTabBar,
            ) {
                if (!state.expanded || moving) HomeSidebarTabBar(
                    tabs = HomeSidebarTabs,
                    selected = tabSelection,
                    enabled = enabled,
                    lightGlass = lightGlass,
                    ink = ink,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(HomeSidebarTabHeight)
                        .fixedFloatingChromeContent(tabWidth, HomeSidebarTabHeight)
                        .graphicsLayer {
                            alpha = state.tabBarAlpha()
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .then(if (moving) Modifier.clearAndSetSemantics { } else Modifier),
                    onNavigate = ::navigate,
                    onExpand = { state.expanded = true }
                )
            }
            if (state.expanded || moving) {
                Column(
                    Modifier.fillMaxSize().clipToBounds()
                        .fixedFloatingChromeContent(state.expandedInset - state.startEdge,
                            metrics.screenHeight - metrics.safeTop - metrics.safeBottom - 14.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .graphicsLayer { alpha = state.sidebarAlpha() }
                        .then(if (moving) Modifier.clearAndSetSemantics { } else Modifier)
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
                    Column(Modifier.weight(1f).pageVerticalScroll(sidebarScroll),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        HomeSidebarTabs.forEach { (destination, label) ->
                            val icon = when (destination) {
                                HomeSidebarDestination.Day -> HomeTodayIcon
                                HomeSidebarDestination.Week -> MiuixIcons.Weeks
                                else -> requireNotNull(homeActionIcon(R.drawable.ic_settings))
                            }
                            SidebarDestinationRow(destination, label, icon, selected, ink, enabled, !lightGlass) {
                                navigate(destination)
                            }
                        }
                        SidebarSectionHeading("课表操作", ink)
                        SidebarDestinationRow(HomeSidebarDestination.Courses, "课程管理", MiuixIcons.Notes,
                            selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Courses) }
                        SidebarRow("跳转周数", ink, false, enabled, !lightGlass, ::jumpWeek, Role.Button) {
                            Icon(requireNotNull(homeActionIcon(R.drawable.ic_material_event)), null,
                                tint = ink, modifier = Modifier.size(22.dp))
                        }
                        SidebarDestinationRow(HomeSidebarDestination.Schedules, "多课表管理", MiuixIcons.Layers,
                            selected, ink, enabled, !lightGlass) { navigate(HomeSidebarDestination.Schedules) }
                        SidebarSectionHeading("导入", ink)
                        SidebarDestinationRow(HomeSidebarDestination.EduImport, "导入课表",
                            requireNotNull(homeActionIcon(R.drawable.ic_ai_import)), selected, ink, enabled, !lightGlass) {
                            navigate(HomeSidebarDestination.EduImport)
                        }
                    }
                }
            }
            if (moving) HomeSidebarMorphLabels(state, tabWidth, sidebarScroll, ink, Modifier.matchParentSize())
        }
        }
    }
}

/** The three shared destinations travel with the morph; their interactive rows stay measured. */
@Composable
private fun HomeSidebarMorphLabels(
    state: HomeSidebarState, tabWidth: Dp, sidebarScroll: ScrollState, ink: Color, modifier: Modifier,
) {
    Layout(
        content = {
            HomeSidebarTabs.forEach { (_, label) -> Text(label, color = ink, fontSize = 15.sp, maxLines = 1) }
        },
        modifier = modifier.clipToBounds().graphicsLayer {
            alpha = 1f - maxOf(state.sidebarAlpha(), state.tabBarAlpha())
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }.clearAndSetSemantics { },
    ) { measurables, constraints ->
        val labels = measurables.map { it.measure(Constraints(maxWidth = (tabWidth / 3f).roundToPx())) }
        val segmentWidth = (tabWidth - 60.dp).toPx() / labels.size
        layout(constraints.maxWidth, constraints.maxHeight) {
            labels.forEachIndexed { index, label ->
                val progress = state.tabEntryExpansion(index)
                val fromX = 56.dp.toPx() + (index + 0.5f) * segmentWidth - label.width / 2f
                val fromY = (HomeSidebarTabHeight.toPx() - label.height) / 2f
                val toY = (77 + 44 * index).dp.toPx() - label.height / 2f - sidebarScroll.value
                label.placeRelative(
                    lerp(fromX, 52.dp.toPx(), progress).roundToInt(),
                    lerp(fromY, toY, progress).roundToInt(),
                )
            }
        }
    }
}

/** Only the shell is remeasured during morph; native rows/text retain stable constraints. */
internal fun Modifier.fixedFloatingChromeContent(width: Dp, height: Dp): Modifier = layout { measurable, constraints ->
    val child = measurable.measure(Constraints.fixed(width.roundToPx().coerceAtLeast(1), height.roundToPx().coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) { child.placeRelative(0, 0) }
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
        Modifier.fillMaxWidth().height(42.dp).clip(HomeSidebarPanelShape)
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
