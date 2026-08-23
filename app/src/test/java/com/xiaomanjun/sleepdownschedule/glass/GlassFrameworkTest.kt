package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.LiquidCourseCardBlurMax
import com.xiaomanjun.sleepdownschedule.homeMenuDestinationTrajectoryGeometry
import com.xiaomanjun.sleepdownschedule.homePersonalizationTrajectoryGeometry
import com.xiaomanjun.sleepdownschedule.homeThreeDotMenuTrajectoryGeometry
import com.xiaomanjun.sleepdownschedule.legacyCourseEditorMorphSpec
import com.xiaomanjun.sleepdownschedule.legacyHomeMenuDestinationMorphSpec
import com.xiaomanjun.sleepdownschedule.legacyPersonalizationMorphSpec
import com.xiaomanjun.sleepdownschedule.legacyThreeDotMenuMorphSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class GlassFrameworkTest {
    @Test
    fun legacyMaterialFactoriesKeepEveryExistingToken() {
        val pill = GlassMaterialSpec.pill()
        assertEquals(GlassMaterialRole.Pill, pill.role)
        assertEquals(2.5.dp, pill.blur)
        assertEquals(12.dp, pill.lensHeight)
        assertEquals(24.dp, pill.lensAmount)
        assertEquals(0.18f, pill.surfaceAlpha)
        assertEquals(0.32f, pill.borderAlpha)
        assertEquals(0.055f, pill.highlightAlpha)
        assertEquals(0.14f, pill.shadowAlpha)
        assertEquals(0.09f, pill.innerShadowAlpha)

        val dialog = GlassMaterialSpec.dialog(reduceTransparency = true)
        assertEquals(GlassMaterialRole.Dialog, dialog.role)
        assertEquals(0.dp, dialog.blur)
        assertEquals(0.dp, dialog.lensHeight)
        assertEquals(0.dp, dialog.lensAmount)
        assertEquals(0.92f, dialog.surfaceAlpha)
        assertEquals(0.16f, dialog.borderAlpha)

        val card = GlassMaterialSpec.courseCard(LiquidCourseCardBlurMax + 20f)
        assertEquals(GlassMaterialRole.CourseCard, card.role)
        assertEquals(LiquidCourseCardBlurMax.dp, card.blur)
        assertEquals(10.dp, card.lensHeight)
        assertEquals(20.dp, card.lensAmount)
        assertEquals(0.52f, card.surfaceAlpha)
        assertFalse(card.chromaticAberration)
        assertTrue(card.depthEffect)
        assertTrue(card.useVibrancy)
        assertFalse(GlassMaterialSpec.lens().depthEffect)
        assertFalse(GlassMaterialSpec.popup(14.dp).depthEffect)
        assertFalse(GlassEffectFrame(blur = null).depthEffect)
    }

    @Test
    fun rootDomainsRemainIndependentAndOnlyChromeCombinesThem() {
        val nodes = listOf(
            GlassTopologyNode("background", GlassBackdropDomain.Background, GlassTopologyNodeRole.Producer),
            GlassTopologyNode("content", GlassBackdropDomain.Content, GlassTopologyNodeRole.Producer),
            GlassTopologyNode("chrome", GlassBackdropDomain.ChromeCombined, GlassTopologyNodeRole.Consumer),
            GlassTopologyNode("picker", GlassBackdropDomain.PickerScene, GlassTopologyNodeRole.Producer)
        )
        val violations = GlassSceneTopology.validate(
            nodes = nodes,
            links = listOf(
                GlassSamplingLink("background", "chrome"),
                GlassSamplingLink("content", "chrome")
            )
        )
        assertEquals(emptyList<GlassTopologyViolation>(), violations)
        assertEquals(
            setOf(GlassBackdropDomain.Background, GlassBackdropDomain.Content),
            GlassBackdropDomain.ChromeCombined.sourceDomains
        )
        assertEquals(setOf(GlassBackdropDomain.PickerScene), GlassBackdropDomain.PickerScene.sourceDomains)
    }

    @Test
    fun topologyRejectsSelfSamplingAndCycles() {
        val self = GlassSceneTopology.validate(
            nodes = listOf(
                GlassTopologyNode(
                    id = "glass",
                    domain = GlassBackdropDomain.Content,
                    role = GlassTopologyNodeRole.ProducerAndConsumer
                )
            ),
            links = listOf(GlassSamplingLink("glass", "glass"))
        )
        assertTrue(self.any { it.kind == GlassTopologyViolationKind.SelfSampling })

        val cycle = GlassSceneTopology.validate(
            nodes = listOf(
                GlassTopologyNode("first", GlassBackdropDomain.Content, GlassTopologyNodeRole.ProducerAndConsumer),
                GlassTopologyNode("second", GlassBackdropDomain.Content, GlassTopologyNodeRole.ProducerAndConsumer)
            ),
            links = listOf(
                GlassSamplingLink("first", "second"),
                GlassSamplingLink("second", "first")
            )
        )
        assertTrue(cycle.any { it.kind == GlassTopologyViolationKind.SamplingCycle })
    }

    @Test
    fun diagnosticSceneFailsFastForInvalidTopologyButReleaseSceneDoesNot() {
        val nodes = listOf(
            GlassTopologyNode(
                id = "loop",
                domain = GlassBackdropDomain.Content,
                role = GlassTopologyNodeRole.ProducerAndConsumer
            )
        )
        val links = listOf(GlassSamplingLink("loop", "loop"))
        val diagnostic = GlassSceneState("debug", true, GlassBackendPolicy.ReferenceOnly)
        assertTrue(runCatching { diagnostic.requireValidTopology(nodes, links) }.isFailure)

        val release = GlassSceneState("release", false, GlassBackendPolicy.ReferenceOnly)
        assertTrue(runCatching { release.requireValidTopology(nodes, links) }.isSuccess)
    }

    @Test
    fun diagnosticsTrackProviderEffectsPixelsAndLayerSizeChanges() {
        val state = GlassSceneState(
            sceneId = "test",
            diagnosticsEnabled = true,
            backendPolicy = GlassBackendPolicy.ReferenceOnly
        )
        val descriptor = GlassSurfaceDescriptor(
            id = "card",
            domain = GlassBackdropDomain.Content,
            materialRole = GlassMaterialRole.CourseCard
        )

        state.recordProvider(GlassBackdropDomain.Content, "content-provider")
        state.recordEffectChainEvaluation(descriptor)
        state.recordEffectChainRebuild(descriptor)
        state.recordConsumerDraw(descriptor, IntSize(100, 50))
        state.recordConsumerDraw(descriptor, IntSize(100, 50))
        state.recordConsumerDraw(descriptor, IntSize(120, 50))
        state.recordPrewarmHit(GlassBackdropDomain.Content)

        val snapshot = state.snapshot()
        val content = snapshot.domains.getValue(GlassBackdropDomain.Content)
        assertEquals(1L, content.providerRecordCount)
        assertEquals(1, content.distinctProviderCount)
        assertEquals(3L, content.consumerDrawCount)
        assertEquals(1, content.distinctConsumerCount)
        assertEquals(16_000L, content.offscreenPixelArea)
        assertEquals(6_000L, content.maxConsumerPixelArea)
        assertEquals(1L, content.effectChainEvaluationCount)
        assertEquals(1L, content.effectChainRebuildCount)
        assertEquals(1L, content.graphicsLayerSizeChangeCount)
        assertEquals(1L, content.prewarmHitCount)
        assertEquals(1, snapshot.distinctProviderCount)
        assertEquals(1, snapshot.distinctConsumerCount)

        state.snapshotAndResetDiagnostics()
        assertEquals(0L, state.snapshot().consumerDrawCount)
        state.recordConsumerDraw(descriptor, IntSize(140, 50))
        assertEquals(1L, state.snapshot().graphicsLayerSizeChangeCount)
    }

    @Test
    fun stablePhasesExposeUnreleasedTemporaryResources() {
        val state = GlassSceneState(
            sceneId = "lifecycle",
            diagnosticsEnabled = true,
            backendPolicy = GlassBackendPolicy.ReferenceOnly
        )
        state.acquireTemporaryResource("morph-offscreen")
        assertTrue(state.synchronizePhase(GlassRenderPhase.Moving))
        assertTrue(state.synchronizePhase(GlassRenderPhase.Live))
        assertEquals(1, state.snapshot().stableResourceLeakCount)

        state.releaseTemporaryResource("morph-offscreen")
        assertEquals(0, state.snapshot().stableResourceLeakCount)
        assertTrue(state.synchronizePhase(GlassRenderPhase.Moving))
        assertTrue(state.synchronizePhase(GlassRenderPhase.Closing))
        assertTrue(state.synchronizePhase(GlassRenderPhase.Released))
        assertFalse(state.renderPhase.keepsTemporaryLayers)
    }

    @Test
    fun experimentalBackendsStayReferenceOnlyWithoutAnAllowlist() {
        val descriptor = GlassSurfaceDescriptor(
            id = "week-cards",
            domain = GlassBackdropDomain.Content,
            materialRole = GlassMaterialRole.CourseCard,
            requestedRenderer = GlassRendererKind.GroupedExperimental,
            sceneKey = "week-cards"
        )
        val state = GlassSceneState(
            sceneId = "home",
            diagnosticsEnabled = true,
            backendPolicy = GlassBackendPolicy.ReferenceOnly
        )
        assertEquals(GlassRendererKind.KyantReference, state.rendererFor(descriptor))
        assertFalse(state.backendPolicy.usesNewMotion("three-dot-menu"))
    }

    @Test
    fun transitionEnvelopeKeepsOneTargetSizeWhileGeometryMovesInside() {
        val start = GlassTransitionGeometry(Rect(10f, 20f, 40f, 60f), cornerRadiusPx = 15f)
        val end = GlassTransitionGeometry(Rect(100f, 80f, 180f, 200f), cornerRadiusPx = 30f)
        val envelope = GlassTransitionEnvelope.covering(
            geometries = listOf(start, end),
            effectPaddingPx = 8f
        )
        val state = GlassTransitionLayerState(envelope, start)

        assertEquals(Rect(2f, 12f, 188f, 208f), envelope.boundsInRoot)
        assertEquals(186, state.stableTargetWidthPx)
        assertEquals(196, state.stableTargetHeightPx)
        assertEquals(Rect(8f, 8f, 38f, 48f), state.localRect)
        assertEquals(envelope.insetShapeFor(start), envelope.insetShapeFor(start))
        assertFalse(envelope.insetShapeFor(start) == envelope.insetShapeFor(end))

        state.updateGeometry(end)
        assertEquals(186, state.stableTargetWidthPx)
        assertEquals(196, state.stableTargetHeightPx)
        assertEquals(Rect(98f, 68f, 178f, 188f), state.localRect)
    }

    @Test
    fun phase2LargeSurfacePolicyIsExplicitlyRouteAllowlisted() {
        val state = GlassSceneState(
            sceneId = "home",
            diagnosticsEnabled = true,
            backendPolicy = GlassBackendPolicy.Phase2LargeSurfaceExperiment
        )
        fun descriptor(sceneKey: String) = GlassSurfaceDescriptor(
            id = sceneKey,
            domain = GlassBackdropDomain.ChromeCombined,
            materialRole = GlassMaterialRole.MorphShell,
            requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
            sceneKey = sceneKey
        )

        GlassSceneKeys.Phase2LargeSurfaceEnvelopeRoutes.forEach { sceneKey ->
            assertEquals(
                GlassRendererKind.StableEnvelopeExperimental,
                state.rendererFor(descriptor(sceneKey))
            )
        }
        assertEquals(
            GlassRendererKind.KyantReference,
            state.rendererFor(descriptor("home-personalization"))
        )
        assertEquals(
            GlassRendererKind.GroupedExperimental,
            state.rendererFor(
                GlassSurfaceDescriptor(
                    id = GlassSceneKeys.WeekCourseCards,
                    domain = GlassBackdropDomain.Content,
                    materialRole = GlassMaterialRole.CourseCard,
                    requestedRenderer = GlassRendererKind.GroupedExperimental,
                    sceneKey = GlassSceneKeys.WeekCourseCards
                )
            )
        )
    }

    @Test
    fun phase3LiquidMotionPolicyIsIndependentFromPhase2RendererPolicy() {
        val phase3Only = GlassBackendPolicy.experiments(
            largeSurfaceEnabled = false,
            liquidMotionEnabled = true
        )
        assertTrue(phase3Only.usesNewMotion(GlassSceneKeys.HomeThreeDotMenuMotion))
        assertFalse(phase3Only.usesNewMotion("home-personalization"))
        assertEquals(
            GlassRendererKind.KyantReference,
            phase3Only.rendererFor(
                GlassSurfaceDescriptor(
                    id = "phase2-disabled",
                    domain = GlassBackdropDomain.ChromeCombined,
                    materialRole = GlassMaterialRole.MorphShell,
                    requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
                    sceneKey = GlassSceneKeys.HomeMenuDestinationAddCourse
                )
            )
        )

        val combined = GlassBackendPolicy.experiments(
            largeSurfaceEnabled = true,
            liquidMotionEnabled = true
        )
        assertTrue(combined.usesNewMotion(GlassSceneKeys.HomeThreeDotMenuMotion))
        assertEquals(
            GlassRendererKind.StableEnvelopeExperimental,
            combined.rendererFor(
                GlassSurfaceDescriptor(
                    id = "phase2-enabled",
                    domain = GlassBackdropDomain.ChromeCombined,
                    materialRole = GlassMaterialRole.MorphShell,
                    requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
                    sceneKey = GlassSceneKeys.HomeMenuDestinationAddCourse
                )
            )
        )
    }

    @Test
    fun sampledEnvelopePreservesLegacyCenteredContentPixelPosition() {
        fun geometry(progress: Float): GlassTransitionGeometry {
            val arc = 120f * progress * (1f - progress)
            val width = 80f + 720f * progress
            val height = 120f + 980f * progress
            val left = 900f - 640f * progress + arc
            val top = 100f + 180f * progress - arc
            return GlassTransitionGeometry(
                Rect(left, top, left + width, top + height),
                cornerRadiusPx = 30f
            )
        }
        val envelope = sampleGlassTransitionEnvelope(
            tracks = listOf(::geometry),
            steps = 64,
            effectPaddingPx = 2f
        )
        for (index in 0..1_024) {
            assertTrue(envelope.contains(geometry(index / 1_024f).pixelAligned()))
        }

        val current = geometry(0.37f).pixelAligned()
        val targetSize = IntSize(800, 1_100)
        val localOffset = stableContentOffsetInEnvelope(envelope, current, targetSize)
        val expectedGlobalX = current.rectInRoot.left.roundToInt() +
            (current.rectInRoot.width.roundToInt() - targetSize.width) / 2
        val expectedGlobalY = current.rectInRoot.top.roundToInt() +
            (current.rectInRoot.height.roundToInt() - targetSize.height) / 2
        assertEquals(
            expectedGlobalX,
            envelope.boundsInRoot.left.roundToInt() + localOffset.x
        )
        assertEquals(
            expectedGlobalY,
            envelope.boundsInRoot.top.roundToInt() + localOffset.y
        )
    }

    @Test
    fun stableEnvelopeAreaGuardRejectsOversizedRenderTargets() {
        val target = GlassTransitionGeometry(
            rectInRoot = Rect(10f, 10f, 110f, 110f),
            cornerRadiusPx = 24f
        )
        val bounded = GlassTransitionEnvelope(Rect(0f, 0f, 120f, 120f))
        val oversized = GlassTransitionEnvelope(Rect(0f, 0f, 300f, 300f))

        assertEquals(1.44f, bounded.areaRatioComparedTo(target), 0.0001f)
        assertTrue(bounded.isAllocationEfficientFor(target, maximumAreaRatio = 1.5f))
        assertFalse(oversized.isAllocationEfficientFor(target, maximumAreaRatio = 1.5f))
    }

    @Test
    fun nonOverlappingCourseCardsCollapseToOneMaterialGroup() {
        val viewport = Rect(0f, 0f, 1_000f, 2_000f)
        val candidates = (0 until 32).map { index ->
            val column = index % 8
            val row = index / 8
            GlassGroupCandidate(
                id = "course-$index",
                domain = GlassBackdropDomain.Content,
                materialKey = "course-default",
                boundsInViewport = Rect(
                    left = column * 110f,
                    top = row * 160f,
                    right = column * 110f + 100f,
                    bottom = row * 160f + 150f
                ),
                cornerRadiusPx = 24f,
                surfaceColor = Color.White
            )
        }

        val groups = GlassGroupPlanner.plan(viewport, candidates)
        assertEquals(1, groups.size)
        assertEquals(32, groups.single().members.size)

        val boundedGroups = GlassGroupPlanner.plan(
            viewport = viewport,
            candidates = candidates,
            maxMembersPerPlan = GlassGroupMaximumMembers
        )
        assertEquals(4, boundedGroups.size)
        assertTrue(boundedGroups.all { it.members.size <= GlassGroupMaximumMembers })

        val tightLayer = boundedGroups[1].toTightLayerPlan()
        assertEquals(IntOffset(0, 160), tightLayer.offsetInViewport)
        assertEquals(IntSize(870, 150), tightLayer.size)
        assertEquals(Rect(0f, 0f, 870f, 150f), tightLayer.localPlan.viewport)
        assertEquals(
            boundedGroups[1].members.map { it.boundsInViewport },
            tightLayer.localPlan.members.map { member ->
                member.boundsInViewport.translate(
                    Offset(
                        tightLayer.offsetInViewport.x.toFloat(),
                        tightLayer.offsetInViewport.y.toFloat()
                    )
                )
            }
        )
    }

    @Test
    fun overlappingOrDifferentMaterialCardsNeverShareAnEffectGroup() {
        val viewport = Rect(0f, 0f, 500f, 500f)
        fun candidate(id: String, material: String, rect: Rect) = GlassGroupCandidate(
            id = id,
            domain = GlassBackdropDomain.Content,
            materialKey = material,
            boundsInViewport = rect,
            cornerRadiusPx = 20f,
            surfaceColor = Color.White
        )
        val groups = GlassGroupPlanner.plan(
            viewport,
            listOf(
                candidate("a", "liquid", Rect(0f, 0f, 100f, 100f)),
                candidate("b", "liquid", Rect(50f, 50f, 150f, 150f)),
                candidate("c", "simple", Rect(200f, 0f, 300f, 100f))
            )
        )

        assertEquals(3, groups.size)
        assertTrue(groups.all { it.members.size == 1 })
    }

    @Test
    fun groupedCourseCardLensIsEligibleWithBoundedMultiShapeSdf() {
        val sceneKey = "week-course-cards"
        val state = GlassSceneState(
            sceneId = "home",
            diagnosticsEnabled = true,
            backendPolicy = GlassBackendPolicy(groupedSceneAllowlist = setOf(sceneKey))
        )
        val plan = GlassGroupPlan(
            domain = GlassBackdropDomain.Content,
            materialKey = "course-default",
            viewport = Rect(0f, 0f, 500f, 500f),
            members = listOf(
                GlassGroupCandidate(
                    id = "course",
                    domain = GlassBackdropDomain.Content,
                    materialKey = "course-default",
                    boundsInViewport = Rect(0f, 0f, 100f, 100f),
                    cornerRadiusPx = 20f,
                    surfaceColor = Color.White
                )
            )
        )

        assertEquals(
            GlassGroupRenderEligibility.Eligible,
            state.glassGroupEligibility(
                sceneKey = sceneKey,
                plan = plan,
                effectFrame = GlassEffectFrame(
                    blur = 3.dp,
                    lensHeight = 10.dp,
                    lensAmount = 20.dp
                )
            )
        )
        assertEquals(
            GlassGroupRenderEligibility.Eligible,
            state.glassGroupEligibility(
                sceneKey = sceneKey,
                plan = plan,
                effectFrame = GlassEffectFrame(blur = 3.dp)
            )
        )
    }

    @Test
    fun morphControllerReleasesEveryGenerationExactlyOnce() {
        val controller = LiquidMorphController()
        var cleanupCount = 0
        val token = controller.prepare("three-dot-menu")
        assertTrue(controller.registerCleanup(token, "offscreen") { cleanupCount += 1 })
        assertFalse(controller.registerCleanup(token, "offscreen") { cleanupCount += 100 })
        assertTrue(controller.startOpening(token))
        assertTrue(controller.finishOpening(token))
        assertTrue(controller.startClosing(token))
        assertTrue(controller.finishClosing(token))
        assertFalse(controller.finishClosing(token))
        assertFalse(controller.cancel(token))
        assertEquals(1, cleanupCount)
        assertEquals(LiquidMorphPhase.Released, controller.state.phase)
        assertTrue(controller.reset())
        assertEquals(LiquidMorphPhase.Idle, controller.state.phase)
    }

    @Test
    fun morphControllerHandlesImmediateBackRotationAndStaleCallbacks() {
        val controller = LiquidMorphController()
        var cleanupCount = 0
        val first = controller.prepare("course-editor")
        controller.registerCleanup(first, "clip") { cleanupCount += 1 }
        assertTrue(controller.startClosing(first))
        assertTrue(controller.finishClosing(first))

        val second = controller.prepare("course-editor")
        controller.registerCleanup(second, "clip") { cleanupCount += 1 }
        val replacement = controller.replaceForConfiguration(second)
        assertEquals(2, cleanupCount)
        assertFalse(controller.startOpening(first))
        assertFalse(controller.startOpening(second))
        assertTrue(replacement != null && controller.startOpening(replacement))
    }

    @Test
    fun morphControllerReleasesRemainingResourcesWhenOneCleanupFails() {
        val controller = LiquidMorphController()
        var released = 0
        val token = controller.prepare("course-editor")
        assertTrue(controller.registerCleanup(token, "broken") { error("cleanup failure") })
        assertTrue(controller.registerCleanup(token, "offscreen") { released += 1 })

        assertTrue(controller.cancel(token))
        assertEquals(1, released)
        assertEquals(LiquidMorphPhase.Released, controller.state.phase)
    }

    @Test
    fun morphControllerReleasesMovementLayersAtOpenAndAgainAfterClose() {
        val controller = LiquidMorphController()
        var movementReleases = 0
        val token = controller.prepare("three-dot-menu")
        assertTrue(
            controller.registerCleanup(
                token = token,
                resourceKey = "opening-offscreen",
                lifetime = LiquidMorphResourceLifetime.Movement
            ) { movementReleases += 1 }
        )
        assertTrue(controller.startOpening(token))
        assertTrue(controller.finishOpening(token))
        assertEquals(1, movementReleases)

        assertTrue(controller.startClosing(token))
        assertTrue(
            controller.registerCleanup(
                token = token,
                resourceKey = "closing-offscreen",
                lifetime = LiquidMorphResourceLifetime.Movement
            ) { movementReleases += 1 }
        )
        assertTrue(controller.finishClosing(token))
        assertEquals(2, movementReleases)
    }

    @Test
    fun legacyHomeSpecsAreGeometryIdenticalAtEveryHandoffWindow() {
        val source = Rect(840f, 80f, 900f, 140f)
        val target = Rect(620f, 150f, 900f, 650f)
        val collapse = Rect(850f, 82f, 898f, 130f)
        val progressPoints = listOf(0f, 0.02f, 0.08f, 0.13f, 0.28f, 0.40f, 0.82f, 0.999f, 1f)
        val threeDotSpec = legacyThreeDotMenuMorphSpec(
            source = source,
            target = target,
            sourceCornerRadiusPx = 21f,
            targetCornerRadiusPx = 30f,
            sourcePressedScale = 1.05f,
            openingPinchDiameterPx = 18f,
            openingMinimumDropPx = 36f,
            openingMaximumDropPx = 72f,
            openingMaximumArcPx = 90f,
            verticalReboundAmplitudePx = 12f,
            closingSinkOffsetPx = 6f,
            closingControlDropPx = 14f
        )
        val personalizationSpec = legacyPersonalizationMorphSpec(
            source = source,
            target = target,
            pinchDiameterPx = 18f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 90f,
            targetCornerRadiusPx = 28f,
            sourcePressedScale = 1.05f,
            verticalReboundAmplitudePx = 12f,
            closingSinkOffsetPx = 6f,
            closingControlDropPx = 14f
        )
        val destinationSpec = legacyHomeMenuDestinationMorphSpec(
            sourceBoundsInRoot = target,
            collapseBoundsInRoot = collapse,
            target = Rect(0f, 0f, 1_000f, 800f),
            menuCornerRadiusPx = 30f,
            buttonCornerRadiusPx = 21f,
            pinchDiameterPx = 18f,
            minimumDropPx = 12f,
            maximumDropPx = 90f,
            maximumArcPx = 106f,
            targetCornerRadiusPx = 0f
        )

        listOf(false, true).forEach { closing ->
            progressPoints.forEach { progress ->
                assertEquals(
                    homeThreeDotMenuTrajectoryGeometry(
                        source = source,
                        target = target,
                        rawProgress = progress,
                        closing = closing,
                        sourceCornerRadiusPx = 21f,
                        targetCornerRadiusPx = 30f,
                        sourcePressedScale = 1.05f,
                        openingPinchDiameterPx = 18f,
                        openingMinimumDropPx = 36f,
                        openingMaximumDropPx = 72f,
                        openingMaximumArcPx = 90f,
                        verticalReboundAmplitudePx = 12f,
                        closingSinkOffsetPx = 6f,
                        closingControlDropPx = 14f
                    ),
                    threeDotSpec.homeGeometry(source, target, progress, closing)
                )
                assertEquals(
                    homePersonalizationTrajectoryGeometry(
                        source = source,
                        target = target,
                        rawProgress = progress,
                        pinchDiameterPx = 18f,
                        minimumDropPx = 36f,
                        maximumDropPx = 72f,
                        maximumArcPx = 90f,
                        targetCornerRadiusPx = 28f,
                        sourcePressedScale = 1.05f,
                        closing = closing,
                        verticalReboundAmplitudePx = 12f,
                        closingSinkOffsetPx = 6f,
                        closingControlDropPx = 14f
                    ),
                    personalizationSpec.homeGeometry(source, target, progress, closing)
                )
                val fullTarget = Rect(0f, 0f, 1_000f, 800f)
                assertEquals(
                    homeMenuDestinationTrajectoryGeometry(
                        sourceBoundsInRoot = target,
                        collapseBoundsInRoot = collapse,
                        target = fullTarget,
                        rawProgress = progress,
                        closing = closing,
                        menuCornerRadiusPx = 30f,
                        buttonCornerRadiusPx = 21f,
                        pinchDiameterPx = 18f,
                        minimumDropPx = 12f,
                        maximumDropPx = 90f,
                        maximumArcPx = 106f,
                        targetCornerRadiusPx = 0f
                    ),
                    destinationSpec.homeGeometry(target, fullTarget, progress, closing)
                )
            }
        }
    }

    @Test
    fun independentSpringMotionSeparatesTrajectoryAndShapeKinematics() {
        val motion = IndependentSpringMotionSpec(
            trajectoryClock = SpringProgressClock(
                stiffness = 180f,
                dampingRatio = 0.72f,
                durationSeconds = 0.44f
            ),
            shapeClock = SpringProgressClock(
                stiffness = 260f,
                dampingRatio = 0.58f,
                durationSeconds = 0.44f
            )
        )
        val sample = motion.sample(
            LiquidMorphInput(
                source = Rect.Zero,
                target = Rect(0f, 0f, 100f, 100f),
                rawProgress = 0.4f,
                direction = LiquidMorphDirection.Opening
            )
        )

        assertFalse(sample.trajectory.progress == sample.shape.progress)
        assertFalse(sample.trajectory.velocity == sample.shape.velocity)
        assertFalse(sample.trajectory.acceleration == sample.shape.acceleration)
    }

    @Test
    fun kinematicDeformationIsMotionOnlyAndLeavesEndpointsUndeformed() {
        val motion = IndependentSpringMotionSpec(
            trajectoryClock = SpringProgressClock(180f, 0.72f, 0.44f),
            shapeClock = SpringProgressClock(260f, 0.58f, 0.44f)
        )
        val deformation = KinematicLiquidDeformationSpec()
        fun frame(progress: Float): LiquidDeformationFrame {
            val input = LiquidMorphInput(
                source = Rect(0f, 0f, 40f, 40f),
                target = Rect(200f, 120f, 500f, 620f),
                rawProgress = progress,
                direction = LiquidMorphDirection.Opening
            )
            return deformation.sample(input, motion.sample(input))
        }

        assertEquals(LiquidDeformationFrame.None, frame(0f))
        assertEquals(LiquidDeformationFrame.None, frame(1f))
        val moving = frame(0.4f)
        assertTrue(moving.tangentStretch > 0f)
        assertTrue(moving.crossAxisSqueeze > 0f)
        assertTrue(moving.tailLag > 0f || moving.rebound > 0f)
    }

    @Test
    fun issue70OutlineSpringLeavesLegacyEndpointsAndContentCoordinatesUntouched() {
        val source = Rect(80f, 120f, 122f, 162f)
        val target = Rect(430f, 280f, 624f, 597f)
        val spec = issue70InspiredLiquidOutlineMotionSpec(durationSeconds = 0.44f)

        fun deformation(progress: Float, direction: LiquidMorphDirection) = spec.sample(
            LiquidMorphInput(
                source = source,
                target = target,
                rawProgress = progress,
                direction = direction
            )
        )

        assertEquals(LiquidDeformationFrame.None, deformation(0f, LiquidMorphDirection.Opening))
        assertEquals(LiquidDeformationFrame.None, deformation(1f, LiquidMorphDirection.Opening))
        assertEquals(LiquidDeformationFrame.None, deformation(1f, LiquidMorphDirection.Closing))
        assertEquals(LiquidDeformationFrame.None, deformation(0f, LiquidMorphDirection.Closing))
        assertTrue(deformation(0.42f, LiquidMorphDirection.Opening).tangentStretch > 0f)
        val routeTangentFrame = spec.sample(
            LiquidMorphInput(
                source = source,
                target = target,
                rawProgress = 0.42f,
                direction = LiquidMorphDirection.Opening,
                trajectoryTangentAngleRadians = 0.25f
            )
        )
        assertEquals(0.25f, routeTangentFrame.tangentAngleRadians, 0.0001f)

        val bounds = Rect(28f, 28f, 222f, 345f)
        val contentPoint = Offset(91f, 173f)
        assertEquals(
            contentPoint,
            liquidMotionTransformPoint(
                point = contentPoint,
                bounds = bounds,
                deformation = LiquidDeformationFrame.None
            )
        )
        // The outline moves independently, while the accepted content point remains available to
        // the legacy target-sized layout channel above.
        assertFalse(
            contentPoint == liquidMotionTransformPoint(
                point = contentPoint,
                bounds = bounds,
                deformation = deformation(0.42f, LiquidMorphDirection.Opening)
            )
        )
    }

    @Test
    fun courseEditorLegacySpecKeepsGeometryContentAndBlurSnapshots() {
        val source = Rect(80f, 160f, 320f, 420f)
        val target = Rect(40f, 90f, 960f, 1_500f)
        val spec = legacyCourseEditorMorphSpec(
            source = source,
            target = target,
            sourceCornerRadiusPx = 24f,
            targetCornerRadiusPx = 32f,
            maximumArcPx = 180f,
            sourceBlurMaxPx = 6f,
            destinationBlurMaxPx = 5f,
            hasSourceTransform = true
        )

        data class Snapshot(
            val closing: Boolean,
            val progress: Float,
            val rect: Rect,
            val corner: Float,
            val trajectory: Float,
            val shape: Float,
            val sourceAlpha: Float,
            val destinationAlpha: Float,
            val sourceBlur: Float,
            val destinationBlur: Float,
            val interactive: Boolean = false
        )
        val snapshots = listOf(
            Snapshot(false, 0f, Rect(80f, 160f, 320f, 420f), 24f, 0f, 0f, 1f, 0f, 0f, 5f),
            Snapshot(false, 0.1f, Rect(200.3f, 415.9f, 440.3f, 675.9f), 24f, 0.40106973f, 0f, 0.16804391f, 0.8319561f, 6f, 5f),
            Snapshot(false, 0.4f, Rect(80.3f, 179.9f, 854.7f, 1343.7f), 31.614937f, 0.89170784f, 0.7859048f, 0f, 1f, 6f, 5f),
            Snapshot(false, 0.78f, Rect(42.8f, 96.6f, 952.2f, 1488.7f), 32f, 0.99164605f, 0.9844623f, 0f, 1f, 6f, 0.098852515f),
            Snapshot(false, 1f, target, 32f, 1f, 1f, 0f, 1f, 6f, 0f, interactive = true),
            Snapshot(true, 0f, Rect(80f, 160f, 320f, 420f), 24f, 0f, 0f, 1f, 0f, 0f, 5f),
            Snapshot(true, 0.1f, Rect(80.6f, 162.8f, 322.5f, 424.9f), 24f, 0.00529176f, 0f, 1f, 0f, 0.03397435f, 5f),
            Snapshot(true, 0.4f, Rect(108.5f, 229.8f, 357.8f, 505.5f), 24f, 0.110464156f, 0.013654172f, 0.49502707f, 0.50497293f, 5.8923564f, 5f),
            Snapshot(true, 0.78f, Rect(193.6f, 397.6f, 623.1f, 978.1f), 25.506887f, 0.69451034f, 0.27870768f, 0f, 1f, 6f, 5f),
            Snapshot(true, 1f, target, 32f, 1f, 1f, 0f, 1f, 6f, 5f)
        )

        snapshots.forEach { expected ->
            val frame = spec.frame(
                LiquidMorphInput(
                    source = source,
                    target = target,
                    rawProgress = expected.progress,
                    direction = if (expected.closing) {
                        LiquidMorphDirection.Closing
                    } else {
                        LiquidMorphDirection.Opening
                    },
                    backdropScale = 1.08f,
                    backdropBlurPx = 12f,
                    useCachedBackdrop = true
                )
            )
            val label = "closing=${expected.closing}, progress=${expected.progress}"
            assertEquals("$label left", expected.rect.left, frame.rect.left, 0.15f)
            assertEquals("$label top", expected.rect.top, frame.rect.top, 0.15f)
            assertEquals("$label right", expected.rect.right, frame.rect.right, 0.15f)
            assertEquals("$label bottom", expected.rect.bottom, frame.rect.bottom, 0.15f)
            assertEquals("$label corner", expected.corner, frame.cornerRadiusPx, 0.0001f)
            assertEquals("$label trajectory", expected.trajectory, frame.trajectoryProgress, 0.0001f)
            assertEquals("$label shape", expected.shape, frame.shapeProgress, 0.0001f)
            assertEquals("$label source alpha", expected.sourceAlpha, frame.content.sourceAlpha, 0.0001f)
            assertEquals("$label destination alpha", expected.destinationAlpha, frame.content.destinationContentAlpha, 0.0001f)
            assertEquals("$label source blur", expected.sourceBlur, frame.content.sourceBlurPx, 0.0001f)
            assertEquals("$label destination blur", expected.destinationBlur, frame.content.destinationBlurPx, 0.0001f)
            assertEquals("$label interaction", expected.interactive, frame.content.destinationInteractive)
            assertEquals(1.08f, frame.backdropDepth.scale)
            assertEquals(12f, frame.backdropDepth.blurPx)
            assertTrue(frame.backdropDepth.useCachedScene)
        }
    }
}
