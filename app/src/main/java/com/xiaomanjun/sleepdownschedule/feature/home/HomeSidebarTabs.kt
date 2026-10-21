package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSelectedAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeFollowLightRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSegmentRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeFollowLightRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSegmentRequest
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar

/** Compact HD navigation shares the sidebar's destinations and keeps its 48 dp frame. */
@Composable
internal fun HomeSidebarTabBar(
    tabs: List<Pair<HomeSidebarDestination, String>>,
    selected: HomeSidebarDestination,
    enabled: Boolean,
    moving: Boolean,
    lightGlass: Boolean,
    ink: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    modifier: Modifier,
    onNavigate: (HomeSidebarDestination) -> Unit,
    onExpand: () -> Unit
) {
    val segmentRenderer = LocalNativeSegmentRenderer.current
    val tabContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            val nativeTabs = segmentRenderer?.invoke(
                NativeSegmentRequest(
                    labels = tabs.map { it.second },
                    selected = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                    onSelected = { onNavigate(tabs[it].first) },
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    dark = !lightGlass,
                    height = 40.dp,
                    enabled = enabled,
                    containerSurfaceEnabled = false,
                    commitOnRelease = true
                )
            ) == true
            if (!nativeTabs) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    LiquidBottomTabs(
                        selectedTabIndex = { tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0) },
                        onTabSelected = { index -> onNavigate(tabs[index].first) },
                        backdrop = backdrop, tabsCount = tabs.size,
                        modifier = Modifier.fillMaxWidth(),
                        containerHeight = 48.dp, indicatorHeight = 40.dp,
                        blurRadius = homeChromeBlur(1.3.dp, config),
                        containerAlpha = homeChromeGlassSurfaceAlpha(lightGlass),
                        lensHeight = 10.dp, lensAmount = 40.dp,
                        indicatorWidthOverflow = 8.dp, indicatorHeightOverflow = 4.dp,
                        indicatorLensHeight = 12.dp, indicatorLensAmount = 17.dp,
                        officialHighlightAlpha = 0.07f, officialShadowAlpha = 0.05f,
                        officialInnerShadowAlpha = 0.08f, chromaticAberrationEnabled = true,
                        isLightThemeOverride = lightGlass,
                        lightContainerColor = HomeLightGlassSurfaceColor,
                        lightAccentColor = HomeLightGlassSelectedAccentColor,
                        useOfficialGlassParameters = true,
                        containerSurfaceEnabled = !moving && !LocalNativeMaterialSurfaceActive.current,
                        leadingWidth = 52.dp,
                        leadingContent = {
                            // Reserve space only. The toggle owns a later, independent input layer.
                            Spacer(Modifier.width(52.dp).fillMaxHeight())
                        }
                    ) {
                        tabs.forEach { (destination, label) ->
                            LiquidBottomTab(onClick = { onNavigate(destination) }) {
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
            // A 36 dp target expanded into the first tab's hit area. Use a full 48 dp target
            // above the indicator so its pressed overflow cannot steal the toggle's DOWN.
            HomeSidebarIconButton(
                description = "展开侧栏", dark = !lightGlass, enabled = enabled,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    .width(48.dp).zIndex(1f),
                height = 48.dp, onClick = onExpand
            ) {
                Icon(MiuixIcons.Sidebar, null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
    // One full capsule owns the light and touch transform, including the leading button.
    // The inset native rail retains its selection springs without drawing a second shell.
    val nativeSurface = if (segmentRenderer != null) LocalNativeMaterialSurfaceRenderer.current?.invoke(
        NativeMaterialSurfaceRequest(
            modifier = modifier.height(48.dp), shape = RoundedRectangle(24.dp),
            dark = !lightGlass, role = NativeMaterialSurfaceRole.HomeNavigation,
            renderSurface = !moving, elasticTouchFeedback = true
        ), tabContent
    ) == true else false
    if (!nativeSurface) Box(modifier.height(48.dp)) { tabContent() }
}

@Composable
internal fun HomeSidebarIconButton(
    description: String,
    dark: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    height: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val described = modifier.semantics { contentDescription = description }
    val sharedSurface = LocalNativeMaterialSurfaceActive.current
    val nativeLight = if (sharedSurface) null else LocalNativeFollowLightRenderer.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var center by remember { mutableStateOf(Offset.Zero) }
    val radius = with(LocalDensity.current) { height.toPx() * 0.75f }
    Box(
        described.height(height).clip(RoundedRectangle(12.dp))
            .onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) }
            .clickable(interactionSource = interaction,
                indication = if (nativeLight == null && !sharedSurface) LocalIndication.current else null,
                enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        nativeLight?.invoke(NativeFollowLightRequest(
            modifier = Modifier.matchParentSize(), dark = dark, position = center,
            pressed = enabled && pressed, radiusPx = radius
        ))
        content()
    }
}
