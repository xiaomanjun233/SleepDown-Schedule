package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaomanjun.sleepdownschedule.glass.sleepDownGlassSurface
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar

private val HomeSidebarTabShape = RoundedRectangle(24.dp)
private val HomeSidebarTabShadow = Shadow(radius = 9.dp, color = Color(0x33000000), offset = DpOffset(0.dp, 3.dp))

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
    val tabContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            ControlSegments(
                SegmentControlRequest(
                    labels = tabs.map { it.second },
                    selected = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                    onSelected = { onNavigate(tabs[it].first) },
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    dark = !lightGlass,
                    height = 40.dp,
                    enabled = enabled,
                    containerSurfaceEnabled = false,
                    commitOnRelease = true,
                    backdrop = backdrop
                )
            )
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
    Box(modifier.height(48.dp)) {
        if (lightGlass && !moving) {
            Spacer(
                Modifier.matchParentSize()
                    .drawWithCache {
                        val outline = HomeSidebarTabShape.createOutline(size, layoutDirection, this)
                        val capsule = Path().apply { addOutline(outline) }
                        // A translucent capsule should keep its original fill inside the outline.
                        onDrawWithContent {
                            clipPath(capsule, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                        }
                    }
                    .dropShadow(HomeSidebarTabShape, HomeSidebarTabShadow)
            )
        }
        val nativeSurface = LocalNativeMaterialSurfaceRenderer.current?.invoke(
            NativeMaterialSurfaceRequest(
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = HomeSidebarTabShape,
                dark = !lightGlass, role = NativeMaterialSurfaceRole.HomeNavigation,
                renderSurface = !moving, elasticTouchFeedback = true, backdrop = backdrop
            ), tabContent
        ) == true
        if (!nativeSurface) {
            val material = com.xiaomanjun.sleepdownschedule.glass.GlassMaterialSpec(
                role = com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole.Control,
                blur = homeChromeBlur(1.3.dp, config), lensHeight = 10.dp, lensAmount = 40.dp,
                surfaceAlpha = homeChromeGlassSurfaceAlpha(lightGlass), borderAlpha = 0f,
                highlightAlpha = .07f, shadowAlpha = 0f, innerShadowAlpha = 0f,
                chromaticAberration = true, depthEffect = false)
            val descriptor = com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor(
                "HomeNavigation", com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain.ChromeCombined,
                material.role)
            val tint = if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF26272D)
            Box(Modifier.fillMaxWidth().height(48.dp).then(if (moving) Modifier else
                Modifier.sleepDownGlassSurface(backdrop, descriptor, material, { HomeSidebarTabShape },
                    com.xiaomanjun.sleepdownschedule.glass.GlassEffectFrame(material.blur,
                        material.lensHeight, material.lensAmount, useVibrancy = true, chromaticAberration = true),
                    onDrawSurface = { drawRect(tint.copy(alpha = material.surfaceAlpha)) }))) { tabContent() }
        }
    }
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
