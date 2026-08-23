package com.xiaomanjun.sleepdownschedule

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
        assertFalse(TransitionFeaturePolicy.allowsOplus(nativeRoute, null))
        assertFalse(
            TransitionFeaturePolicy.allowsOplus(
                nativeRoute,
                RemoteTransitionConfig(oplusViewSeamlessEnabled = false)
            )
        )
    }

    @Test
    fun globalAndExactRouteSwitchesAreBothRequired() {
        assertFalse(
            TransitionFeaturePolicy.allowsOplus(
                nativeRoute,
                RemoteTransitionConfig(oplusViewSeamlessEnabled = true)
            )
        )
        assertTrue(
            TransitionFeaturePolicy.allowsOplus(
                nativeRoute,
                RemoteTransitionConfig(
                    oplusViewSeamlessEnabled = true,
                    oplusRouteAllowlist = setOf(nativeRoute.id.wireName)
                )
            )
        )
    }

    @Test
    fun aLegacyOnlyRouteCannotBeEnabledByRemoteConfiguration() {
        assertFalse(
            TransitionFeaturePolicy.allowsOplus(
                legacyOnlyRoute,
                RemoteTransitionConfig(
                    oplusViewSeamlessEnabled = true,
                    oplusRouteAllowlist = setOf(legacyOnlyRoute.id.wireName)
                )
            )
        )
    }
}
