package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*

import com.xiaomanjun.sleepdownschedule.transition.TransitionFeaturePolicy
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionFeaturePolicyTest {
    private val nativeRoute = TransitionRouteCatalog.get(TransitionRouteId.CourseManagementToDetail)
    private val legacyOnlyRoute = TransitionRouteCatalog.get(TransitionRouteId.HomeToSettingsDetail)

    @Test
    fun missingOrDisabledConfigurationAlwaysUsesLegacy() {
        assertFalse(TransitionFeaturePolicy.allowsSharedViewKit(nativeRoute, null))
        assertFalse(
            TransitionFeaturePolicy.allowsSharedViewKit(
                nativeRoute,
                RemoteTransitionConfig(sharedViewKitEnabled = false)
            )
        )
    }

    @Test
    fun globalAndExactRouteSwitchesAreBothRequired() {
        assertFalse(
            TransitionFeaturePolicy.allowsSharedViewKit(
                nativeRoute,
                RemoteTransitionConfig(sharedViewKitEnabled = true)
            )
        )
        assertTrue(
            TransitionFeaturePolicy.allowsSharedViewKit(
                nativeRoute,
                RemoteTransitionConfig(
                    sharedViewKitEnabled = true,
                    sharedViewKitRouteAllowlist = setOf(nativeRoute.id.wireName)
                )
            )
        )
    }

    @Test
    fun aLegacyOnlyRouteCannotBeEnabledByRemoteConfiguration() {
        assertFalse(
            TransitionFeaturePolicy.allowsSharedViewKit(
                legacyOnlyRoute,
                RemoteTransitionConfig(
                    sharedViewKitEnabled = true,
                    sharedViewKitRouteAllowlist = setOf(legacyOnlyRoute.id.wireName)
                )
            )
        )
    }
}
