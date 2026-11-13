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
                TransitionRouteId.CourseManagementToDetail,
                TransitionRouteId.AiHistoryToDetail,
                TransitionRouteId.ManualImportToHistory,
                TransitionRouteId.AiProgressToHistory,
                TransitionRouteId.HomeToEduImport,
                TransitionRouteId.HomeDockToImport,
                TransitionRouteId.HomeToCourseManagement,
                TransitionRouteId.QuickSheetToSettingsDetail,
                TransitionRouteId.SpecialCourseToEditor,
                TransitionRouteId.FeedbackToEditor,
                TransitionRouteId.WelcomeToHome,
            ),
            nativeCandidates.map { it.id }.toSet()
        )
        assertTrue(nativeCandidates.all { it.legacyProfile is LegacyTransitionProfile.Anchored })
        assertTrue(nativeCandidates.all { it.requiresOpeningAnchor && (it.requiresReturnAnchor || it.oneWay) })
        assertTrue(
            TransitionRouteCatalog.all()
                .filter { it.nativePolicy == TransitionNativePolicy.Never }
                .all { it.nativeDestinationClassName == null }
        )
    }

    @Test
    fun adaptedRoutesUseConcreteOpaqueHostsAndOthersStayLegacy() {
        val adapted = setOf(
            TransitionRouteId.CourseManagementToDetail,
            TransitionRouteId.AiHistoryToDetail,
            TransitionRouteId.ManualImportToHistory,
            TransitionRouteId.AiProgressToHistory,
            TransitionRouteId.HomeToEduImport,
            TransitionRouteId.HomeDockToImport,
            TransitionRouteId.HomeToCourseManagement,
            TransitionRouteId.QuickSheetToSettingsDetail,
            TransitionRouteId.SpecialCourseToEditor,
            TransitionRouteId.FeedbackToEditor,
            TransitionRouteId.WelcomeToHome,
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeCourseManagementDetailActivity",
            TransitionRouteCatalog.get(TransitionRouteId.CourseManagementToDetail)
                .nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeAiImportHistoryDetailActivity",
            TransitionRouteCatalog.get(TransitionRouteId.AiHistoryToDetail)
                .nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeAiImportHistoryActivity",
            TransitionRouteCatalog.get(TransitionRouteId.AiProgressToHistory)
                .nativeDestinationClassName
        )
        assertEquals(
            TransitionRouteCatalog.get(TransitionRouteId.AiProgressToHistory).nativeDestinationClassName,
            TransitionRouteCatalog.get(TransitionRouteId.ManualImportToHistory).nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeEduSchoolSelectActivity",
            TransitionRouteCatalog.get(TransitionRouteId.HomeToEduImport)
                .nativeDestinationClassName
        )
        assertEquals(
            TransitionRouteCatalog.get(TransitionRouteId.HomeToEduImport).nativeDestinationClassName,
            TransitionRouteCatalog.get(TransitionRouteId.HomeDockToImport).nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeCourseManagementActivity",
            TransitionRouteCatalog.get(TransitionRouteId.HomeToCourseManagement)
                .nativeDestinationClassName
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.NativeQuickSheetSettingsDetailActivity",
            TransitionRouteCatalog.get(TransitionRouteId.QuickSheetToSettingsDetail).nativeDestinationClassName
        )
        val special = TransitionRouteCatalog.get(TransitionRouteId.SpecialCourseToEditor)
        assertEquals("com.xiaomanjun.sleepdownschedule.feature.settings.NativeSpecialCourseEditorActivity",
            special.nativeDestinationClassName)
        val feedback = TransitionRouteCatalog.get(TransitionRouteId.FeedbackToEditor)
        assertEquals("com.xiaomanjun.sleepdownschedule.feature.feedback.NativeFeedbackEditorActivity",
            feedback.nativeDestinationClassName)
        assertEquals(special.legacyProfile, feedback.legacyProfile)
        assertTrue(feedback.requiresLiveSourceView)
        val welcome = TransitionRouteCatalog.get(TransitionRouteId.WelcomeToHome)
        assertTrue(welcome.oneWay && welcome.requiresLiveSourceView && !welcome.requiresReturnAnchor)
        assertEquals("com.xiaomanjun.sleepdownschedule.MainActivity", welcome.nativeDestinationClassName)
        assertTrue(TransitionRouteCatalog.all().filter { it.id !in adapted }
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
