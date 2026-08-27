package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.transition.AnchoredLegacyProfileId
import com.xiaomanjun.sleepdownschedule.transition.LegacyTransitionProfile
import com.xiaomanjun.sleepdownschedule.transition.TransitionNativeClosePolicy
import com.xiaomanjun.sleepdownschedule.transition.TransitionNativePolicy
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionRouteCatalogTest {
    @Test
    fun everyRouteIdHasExactlyOneCatalogEntryAndWireName() {
        val routes = TransitionRouteCatalog.all()
        assertEquals(TransitionRouteId.entries.size, routes.size)
        assertEquals(routes.size, routes.map { it.id }.distinct().size)
        assertEquals(routes.size, routes.map { it.id.wireName }.distinct().size)
        assertTrue(routes.all { it.destinationClassName.startsWith("com.xiaomanjun.sleepdownschedule.") })
    }

    @Test
    fun onlyAnchoredRoutesCanEnterTheOplusAllowlist() {
        val nativeCandidates = TransitionRouteCatalog.all().filter {
            it.nativePolicy == TransitionNativePolicy.OplusAllowlisted
        }
        assertEquals(
            setOf(
                TransitionRouteId.HomeToCourseManagement,
                TransitionRouteId.CourseManagementToDetail,
                TransitionRouteId.ManualImportToHistory,
                TransitionRouteId.AiProgressToHistory,
                TransitionRouteId.HomeToEduImport
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
    fun menuOriginRoutesUseNativeOpenButLegacyClose() {
        assertEquals(
            TransitionNativeClosePolicy.LegacyOnly,
            TransitionRouteCatalog.get(TransitionRouteId.HomeToCourseManagement).nativeClosePolicy
        )
        assertEquals(
            TransitionNativeClosePolicy.LegacyOnly,
            TransitionRouteCatalog.get(TransitionRouteId.HomeToEduImport).nativeClosePolicy
        )
        assertEquals(
            TransitionNativeClosePolicy.MatchOpen,
            TransitionRouteCatalog.get(TransitionRouteId.CourseManagementToDetail).nativeClosePolicy
        )
    }

    @Test
    fun validatedNativeRoutesUseConcreteOpaqueActivities() {
        val detail = TransitionRouteCatalog.get(TransitionRouteId.CourseManagementToDetail)
        val manualHistory = TransitionRouteCatalog.get(TransitionRouteId.ManualImportToHistory)
        val aiHistory = TransitionRouteCatalog.get(TransitionRouteId.AiProgressToHistory)
        val courseManagement = TransitionRouteCatalog.get(TransitionRouteId.HomeToCourseManagement)
        val eduSchoolSelect = TransitionRouteCatalog.get(TransitionRouteId.HomeToEduImport)
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.OplusCourseManagementActivity",
            courseManagement.nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.OplusCourseManagementDetailActivity",
            detail.nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.OplusAiImportHistoryActivity",
            manualHistory.nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.OplusAiImportHistoryActivity",
            aiHistory.nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.OplusEduSchoolSelectActivity",
            eduSchoolSelect.nativeDestinationClassName
        )
        assertTrue(
            TransitionRouteCatalog.all()
                .filter {
                    it.id !in setOf(
                        TransitionRouteId.CourseManagementToDetail,
                        TransitionRouteId.ManualImportToHistory,
                        TransitionRouteId.AiProgressToHistory,
                        TransitionRouteId.HomeToCourseManagement,
                        TransitionRouteId.HomeToEduImport
                    )
                }
                .all { it.nativeDestinationClassName == null }
        )
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
