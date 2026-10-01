package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
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
import com.xiaomanjun.sleepdownschedule.app.ui.AddMenuAction
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import kotlin.math.abs

internal enum class HomeSidebarDestination { Day, Week, Settings, Courses, Schedules }

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

    fun panelWidth(): Dp =
        (lerp(collapsedInset.value, expandedInset.value, progress.value).dp - startEdge)

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
    val collapsedInset = safeStart + 96.dp
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
        val placeable = measurable.measure(
            constraints.copy(minWidth = width, maxWidth = width)
        )
        layout(constraints.maxWidth, placeable.height) {
            // Placement keeps glass sampling and real popup anchors in window coordinates.
            placeable.placeRelative(state.animatedContentInset().roundToPx(), 0)
        }
    }
}

/** A sibling consumer of the complete home scene; never recorded into contentBackdrop. */
@Composable
internal fun HomeSidebar(
    state: HomeSidebarState,
    metrics: HomeAdaptiveMetrics,
    selected: HomeSidebarDestination,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    navigationEnabled: Boolean,
    visible: Boolean,
    actions: List<AddMenuAction> = emptyList(),
    onAction: (AddMenuAction) -> Unit = { it.onClick() },
    onNavigate: (HomeSidebarDestination) -> Unit
) {
    if (!state.enabled || !visible) return
    val lightGlass = !appUsesDarkTheme(config)
    val ink = if (lightGlass) Color(0xFF202126) else Color(0xFFF3F4F8)
    val tokens = GlassTokens.pill().copy(
        blur = 16.dp,
        lensHeight = 4.dp,
        lensAmount = 8.dp,
        surfaceAlpha = if (lightGlass) 0.68f else 0.66f,
        highlightAlpha = 0.035f
    )
    val shape = RoundedRectangle(28.dp)
    val scrimInteraction = remember { MutableInteractionSource() }
    BackHandler(enabled = state.overlaysContent && state.expanded && navigationEnabled) {
        state.expanded = false
    }
    Box(Modifier.fillMaxSize()) {
        if (state.overlaysContent && state.expanded) {
            Box(
                Modifier.fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.12f * state.expansionProgress())) }
                    .semantics { contentDescription = "收起侧栏" }
                    .clickable(
                        enabled = navigationEnabled,
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = { state.expanded = false }
                    )
            )
        }
        GlassSurface(
            backdrop = backdrop,
            config = config,
            tokens = tokens,
            baseSurfaceColorOverride = if (lightGlass) Color.White else Color(0xFF1F1F1F),
            shape = shape,
            debugLabel = "HomeSidebar",
            modifier = Modifier
                .padding(start = state.startEdge, top = metrics.safeTop + 2.dp, bottom = metrics.safeBottom + 12.dp)
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val width = state.panelWidth().roundToPx().coerceIn(1, constraints.maxWidth)
                    val placeable = measurable.measure(
                        Constraints.fixed(width, constraints.maxHeight)
                    )
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { testTag = "home_sidebar" }
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
                SidebarRow(
                    state = state,
                    label = if (state.expanded) "收起侧栏" else "展开侧栏",
                    ink = ink,
                    selected = false,
                    enabled = navigationEnabled,
                    onClick = { state.expanded = !state.expanded },
                    role = Role.Button,
                    icon = {
                        Icon(
                            if (state.expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                            contentDescription = null,
                            tint = ink,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    fun navigate(destination: HomeSidebarDestination) {
                        state.collapseOverlay()
                        onNavigate(destination)
                    }
                    SidebarDestinationRow(state, HomeSidebarDestination.Day, "今日", R.drawable.ic_day_view,
                        selected, ink, navigationEnabled && !state.moving) { navigate(HomeSidebarDestination.Day) }
                    SidebarDestinationRow(state, HomeSidebarDestination.Week, "课程表", R.drawable.ic_week_view,
                        selected, ink, navigationEnabled && !state.moving) { navigate(HomeSidebarDestination.Week) }
                    SidebarDestinationRow(state, HomeSidebarDestination.Settings, "设置", R.drawable.ic_settings,
                        selected, ink, navigationEnabled && !state.moving) { navigate(HomeSidebarDestination.Settings) }
                    // Removing folded actions also removes their hit targets and accessibility nodes.
                    if (state.expanded) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.10f)))
                        Text("课表管理", color = ink.copy(alpha = 0.54f), fontSize = 12.sp,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                                .graphicsLayer { alpha = state.expansionProgress() })
                        SidebarDestinationRow(state, HomeSidebarDestination.Courses, "课程管理", R.drawable.ic_courses,
                            selected, ink, navigationEnabled && !state.moving) { navigate(HomeSidebarDestination.Courses) }
                        SidebarDestinationRow(state, HomeSidebarDestination.Schedules, "切换课表", R.drawable.ic_share_schedule,
                            selected, ink, navigationEnabled && !state.moving) { navigate(HomeSidebarDestination.Schedules) }
                        if (actions.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.10f)))
                            Text("课表操作", color = ink.copy(alpha = 0.54f), fontSize = 12.sp,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                            actions.forEach { action ->
                                SidebarRow(state, action.label, ink, false, navigationEnabled && !state.moving,
                                    onClick = { state.collapseOverlay(); onAction(action) }, role = Role.Button) {
                                    action.iconRes?.let { Icon(painterResource(it), null, Modifier.size(26.dp), tint = ink) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarDestinationRow(
    state: HomeSidebarState,
    destination: HomeSidebarDestination,
    label: String,
    icon: Int,
    selected: HomeSidebarDestination,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SidebarRow(state, label, ink, destination == selected, enabled, onClick) {
        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SidebarRow(
    state: HomeSidebarState,
    label: String,
    ink: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Tab,
    icon: @Composable () -> Unit
) {
    val shape = RoundedRectangle(24.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (selected) ink.copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(68.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { icon() }
        if (state.expanded) {
            Text(label, color = ink, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
                    .clearAndSetSemantics { }
                    .graphicsLayer { alpha = state.expansionProgress() })
        }
    }
}
