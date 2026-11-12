package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaomanjun.sleepdownschedule.glass.sleepDownGlassSurface
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTabsDarkSurface
import com.kyant.backdrop.catalog.components.liquidBottomTabsContainerMaterial
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeDockGlassBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeDockGlassLensAmount
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeDockGlassLensHeight
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeFollowLightRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ControlSegments
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeFollowLightRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SegmentControlRequest
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassEffectFrame
import com.xiaomanjun.sleepdownschedule.glass.flatControlColor
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar

internal val HomeSidebarTabHeight = 52.dp
internal val HomeSidebarTabCorner = 26.dp

/** Compact HD navigation shares the sidebar's destinations. */
@Composable
internal fun HomeSidebarTabBar(
    tabs: List<Pair<HomeSidebarDestination, String>>,
    selected: HomeSidebarDestination,
    enabled: Boolean,
    lightGlass: Boolean,
    ink: Color,
    backdrop: Backdrop,
    modifier: Modifier,
    onNavigate: (HomeSidebarDestination) -> Unit,
    onExpand: () -> Unit
) {
    Box(modifier.fillMaxWidth().height(HomeSidebarTabHeight)) {
        ControlSegments(
            SegmentControlRequest(
                labels = tabs.map { it.second },
                selected = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                onSelected = { onNavigate(tabs[it].first) },
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                dark = !lightGlass,
                height = 48.dp,
                enabled = enabled,
                containerSurfaceEnabled = false,
                commitOnRelease = true,
                backdrop = backdrop
            )
        )
        // Keep the full toggle hit area above the indicator's pressed overflow.
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

@Composable
internal fun HomeNavigationSurface(
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    lightGlass: Boolean,
    shape: Shape,
    modifier: Modifier,
    domain: GlassBackdropDomain = GlassBackdropDomain.ChromeCombined,
    followTouch: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val rendered = LocalNativeMaterialSurfaceRenderer.current?.invoke(
        NativeMaterialSurfaceRequest(
            modifier = modifier.then(if (lightGlass) Modifier.shadow(9.dp, shape, clip = false)
                else Modifier),
            shape = shape, dark = !lightGlass,
            role = NativeMaterialSurfaceRole.HomeNavigation, backdrop = backdrop,
            followTouch = followTouch,
        ), content
    ) == true
    if (rendered) return
    val blur = homeChromeBlur(HomeDockGlassBlur, config)
    val material = remember(blur) {
        liquidBottomTabsContainerMaterial(
            blur, HomeDockGlassLensHeight, HomeDockGlassLensAmount,
            chromaticAberration = true, shadowEnabled = true
        )
    }
    val tint by animateColorAsState(
        targetValue = (if (lightGlass) HomeLightGlassSurfaceColor else LiquidBottomTabsDarkSurface)
            .copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass)),
        animationSpec = tween(220), label = "HomeNavigationTint"
    )
    val descriptor = rememberGlassSurfaceDescriptor("HomeNavigation", domain, material.role)
    Box(modifier.sleepDownGlassSurface(
        backdrop = backdrop, descriptor = descriptor, material = material, shape = { shape },
        fallbackColor = flatControlColor(!lightGlass),
        effectFrame = GlassEffectFrame(blur, material.lensHeight, material.lensAmount,
            useVibrancy = true, chromaticAberration = true),
        highlightOverride = { Highlight.Default },
        shadowOverride = { Shadow.Default },
        onDrawSurface = { drawRect(tint) }
    )) { content() }
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
