package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.transition.AnchoredLegacyProfileId
import com.xiaomanjun.sleepdownschedule.transition.LegacyTransitionProfile
import com.xiaomanjun.sleepdownschedule.transition.TransitionNativePolicy
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionRouteCatalogTest {
    @Test
    fun colorOsCourseManagementUsesTheSamePageTransitionAsSettingsDetails() {
        val course = TransitionRouteCatalog.get(TransitionRouteId.HomeMenuToCourseManagement)
        val settings = TransitionRouteCatalog.get(TransitionRouteId.SettingsToSettingsDetail)
        assertEquals(settings.legacyProfile, course.legacyProfile)
        assertEquals(settings.nativePolicy, course.nativePolicy)
        assertTrue(!course.requiresOpeningAnchor && !course.requiresReturnAnchor)
    }

    @Test
    fun everyRouteIdHasExactlyOneCatalogEntryAndWireName() {
        val routes = TransitionRouteCatalog.all()
        assertEquals(TransitionRouteId.entries.size, routes.size)
        assertEquals(routes.size, routes.map { it.id }.distinct().size)
        assertEquals(routes.size, routes.map { it.id.wireName }.distinct().size)
        assertTrue(routes.all { it.destinationClassName.startsWith("com.xiaomanjun.sleepdownschedule.") })
    }

    @Test
    fun onlyThePilotCanEnterTheSharedViewKitAllowlist() {
        val nativeCandidates = TransitionRouteCatalog.all().filter {
            it.nativePolicy == TransitionNativePolicy.SharedViewKitAllowlisted
        }
        assertEquals(
            setOf(
                TransitionRouteId.CourseManagementToDetail
            ),
            nativeCandidates.map { it.id }.toSet()
        )
        assertTrue(nativeCandidates.all { it.legacyProfile is LegacyTransitionProfile.Anchored })
        assertTrue(nativeCandidates.all { it.requiresOpeningAnchor && it.requiresReturnAnchor })
        assertTrue(
            TransitionRouteCatalog.all()
                .filter { it.nativePolicy == TransitionNativePolicy.Never }
                .all { it.nativeDestinationClassName == null }
        )
    }

    @Test
    fun retiredVendorRoutesKeepLegacyAndThePilotUsesAConcreteOpaqueHost() {
        val detail = TransitionRouteCatalog.get(TransitionRouteId.CourseManagementToDetail)
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeCourseManagementDetailActivity",
            detail.nativeDestinationClassName
        )
        assertTrue(TransitionRouteCatalog.all().filter { it.id != detail.id }
            .all { it.nativePolicy == TransitionNativePolicy.Never && it.nativeDestinationClassName == null })
    }

    @Test
    fun validatedLegacyProfilesStayBoundToTheirOriginalRoutes() {
        fun profile(route: TransitionRouteId) =
            (TransitionRouteCatalog.get(route).legacyProfile as LegacyTransitionProfile.Anchored)
                .profileId

        assertEquals(
            AnchoredLegacyProfileId.HomeMenuDestination,
            profile(TransitionRouteId.HomeToCourseManagement)
        )
        assertEquals(
            AnchoredLegacyProfileId.CourseManagementDetail,
            profile(TransitionRouteId.CourseManagementToDetail)
        )
        assertEquals(
            AnchoredLegacyProfileId.HomeMenuDestination,
            profile(TransitionRouteId.HomeToEduImport)
        )
        assertEquals(
            AnchoredLegacyProfileId.Parabolic,
            profile(TransitionRouteId.ManualImportToHistory)
        )
        assertEquals(
            AnchoredLegacyProfileId.Liquid,
            profile(TransitionRouteId.AiProgressToHistory)
        )
        assertEquals(
            AnchoredLegacyProfileId.DetailSettings,
            profile(TransitionRouteId.AiHistoryToDetail)
        )
    }
}
