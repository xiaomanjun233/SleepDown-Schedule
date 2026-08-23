package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.Backdrop

@Immutable
data class GlassGroupCandidate(
    val id: String,
    val domain: GlassBackdropDomain,
    val materialKey: String,
    val boundsInViewport: Rect,
    val cornerRadiusPx: Float,
    val surfaceColor: Color
)

@Immutable
data class GlassGroupPlan(
    val domain: GlassBackdropDomain,
    val materialKey: String,
    val viewport: Rect,
    val members: List<GlassGroupCandidate>
)

/** Greedy interval coloring: overlapping cards never share one effect layer. */
object GlassGroupPlanner {
    fun plan(
        viewport: Rect,
        candidates: List<GlassGroupCandidate>
    ): List<GlassGroupPlan> = candidates
        .asSequence()
        .filter { it.boundsInViewport.overlaps(viewport) }
        .groupBy { it.domain to it.materialKey }
        .flatMap { (key, sameMaterial) ->
            val groups = mutableListOf<MutableList<GlassGroupCandidate>>()
            sameMaterial.sortedWith(
                compareBy<GlassGroupCandidate> { it.boundsInViewport.top }
                    .thenBy { it.boundsInViewport.left }
            ).forEach { candidate ->
                val target = groups.firstOrNull { group ->
                    group.none { existing -> existing.boundsInViewport.overlaps(candidate.boundsInViewport) }
                } ?: mutableListOf<GlassGroupCandidate>().also(groups::add)
                target += candidate
            }
            groups.map { members ->
                GlassGroupPlan(
                    domain = key.first,
                    materialKey = key.second,
                    viewport = viewport,
                    members = members
                )
            }
        }
}

private class GlassGroupShape(
    private val members: () -> List<GlassGroupCandidate>
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        members().forEach { member ->
            val rect = member.boundsInViewport
            val radius = member.cornerRadiusPx.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
            path.addRoundRect(
                RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
        return Outline.Generic(path)
    }
}

fun GlassSceneState.isGlassGroupEnabled(sceneKey: String): Boolean =
    backendPolicy.rendererFor(sceneKey, GlassRendererKind.GroupedExperimental) ==
        GlassRendererKind.GroupedExperimental

enum class GlassGroupRenderEligibility {
    Eligible,
    DisabledByPolicy,
    NoVisibleMembers,
    LensRequiresPerShapeSdf
}

/**
 * Backdrop 2.0's lens shader accepts one rounded rectangle and derives its SDF from the complete
 * modifier bounds. A disjoint [Outline.Generic] group therefore cannot preserve the per-card lens
 * today. Keep those scenes on the reference backend until upstream exposes a multi-shape SDF/API.
 */
fun GlassSceneState.glassGroupEligibility(
    sceneKey: String,
    plan: GlassGroupPlan,
    effectFrame: GlassEffectFrame
): GlassGroupRenderEligibility {
    if (!isGlassGroupEnabled(sceneKey)) return GlassGroupRenderEligibility.DisabledByPolicy
    if (plan.members.isEmpty()) return GlassGroupRenderEligibility.NoVisibleMembers
    val hasLens = (effectFrame.lensHeight?.value ?: 0f) > 0f &&
        (effectFrame.lensAmount?.value ?: 0f) > 0f
    return if (hasLens) {
        GlassGroupRenderEligibility.LensRequiresPerShapeSdf
    } else {
        GlassGroupRenderEligibility.Eligible
    }
}

/**
 * Experimental single-effect-chain renderer for effect frames that do not use the rounded-rect
 * lens. Card content, input and semantics remain separate siblings; this modifier draws only the
 * shared material beneath them. Liquid course cards are deliberately ineligible until a true
 * per-shape lens/SDF implementation is available.
 */
@Composable
fun Modifier.sleepDownGlassGroupSurface(
    backdrop: Backdrop,
    plan: GlassGroupPlan,
    material: GlassMaterialSpec,
    effectFrame: GlassEffectFrame,
    sceneState: GlassSceneState,
    sceneKey: String
): Modifier {
    val eligibility = sceneState.glassGroupEligibility(sceneKey, plan, effectFrame)
    check(eligibility == GlassGroupRenderEligibility.Eligible) {
        "GlassGroup '$sceneKey' is not render-equivalent ($eligibility); " +
            "keep the per-card KyantReference backend."
    }
    val currentMembers = rememberUpdatedState(plan.members)
    val shape = remember { GlassGroupShape { currentMembers.value } }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "GlassGroup:$sceneKey",
        domain = plan.domain,
        materialRole = material.role,
        requestedRenderer = GlassRendererKind.GroupedExperimental,
        sceneKey = sceneKey
    )
    return sleepDownGlassSurface(
        backdrop = backdrop,
        descriptor = descriptor,
        material = material,
        shape = { shape },
        effectFrame = effectFrame,
        sceneState = sceneState,
        onDrawSurface = {
            currentMembers.value.forEach { member ->
                val rect = member.boundsInViewport
                val radius = member.cornerRadiusPx.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
                drawRoundRect(
                    color = member.surfaceColor,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }
    )
}
