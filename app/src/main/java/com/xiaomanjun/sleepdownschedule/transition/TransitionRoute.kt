package com.xiaomanjun.sleepdownschedule.transition

import com.xiaomanjun.sleepdownschedule.R

/** Stable wire identifiers used in Intents and structured transition diagnostics. */
enum class TransitionRouteId(val wireName: String) {
    HomeToCourseManagement("home_to_course_management"),
    TabletHomeToCourseManagement("tablet_home_to_course_management"),
    TabletHomeToEduImport("tablet_home_to_edu_import"),
    CourseManagementToDetail("course_management_to_detail"),
    ManualImportToHistory("manual_import_to_history"),
    AiProgressToHistory("ai_progress_to_history"),
    AiHistoryToDetail("ai_history_to_detail"),
    HomeToSettingsDetail("home_to_settings_detail"),
    QuickSheetToSettingsDetail("quick_sheet_to_settings_detail"),
    SettingsToSettingsDetail("settings_to_settings_detail"),
    ScheduleManagerToSettingsDetail("schedule_manager_to_settings_detail"),
    HomeToEduImport("home_to_edu_import"),
    SchoolSelectToEduImport("school_select_to_edu_import"),
    ImportToAiProgress("import_to_ai_progress"),
    ReturnToHome("return_to_home");

    companion object {
        fun fromWireName(value: String?): TransitionRouteId? =
            entries.firstOrNull { it.wireName == value }
    }
}

enum class AnchoredLegacyProfileId {
    HomeMenuDestination,
    CourseManagementDetail,
    DetailSettings,
    Liquid,
    Parabolic
}

sealed interface LegacyTransitionProfile {
    data class Anchored(
        val profileId: AnchoredLegacyProfileId,
        val sourceCornerRadiusDp: Float,
        val returnCornerRadiusDp: Float = sourceCornerRadiusDp,
        val destinationFirstOpening: Boolean = false
    ) : LegacyTransitionProfile

    data class Depth(
        val openEnterAnimation: Int,
        val openExitAnimation: Int,
        val closeEnterAnimation: Int,
        val closeExitAnimation: Int
    ) : LegacyTransitionProfile

    data object PlatformDefault : LegacyTransitionProfile
    data object TaskReturn : LegacyTransitionProfile
}

enum class TransitionNativePolicy { Never, OplusAllowlisted }

enum class TransitionNativeClosePolicy { MatchOpen, LegacyOnly }

enum class TransitionDestinationWindowPolicy { Existing, OpaqueNativeCandidate }

data class TransitionRouteSpec(
    val id: TransitionRouteId,
    val destinationClassName: String,
    val legacyProfile: LegacyTransitionProfile,
    val nativePolicy: TransitionNativePolicy = TransitionNativePolicy.Never,
    val nativeClosePolicy: TransitionNativeClosePolicy =
        TransitionNativeClosePolicy.MatchOpen,
    val destinationWindowPolicy: TransitionDestinationWindowPolicy =
        TransitionDestinationWindowPolicy.Existing,
    /**
     * A real opaque Activity component used only after ViewSeamless accepts this route. Activity
     * aliases cannot change WindowManager's translucency classification for their target and must
     * never be used here.
     */
    val nativeDestinationClassName: String? = null,
    val requiresOpeningAnchor: Boolean = false,
    val requiresReturnAnchor: Boolean = false
)

/**
 * Single source of truth for every in-app Activity route. External Intents, widgets and deep links
 * intentionally stay outside this catalog because their task-stack semantics belong to Android.
 */
object TransitionRouteCatalog {
    private const val PackageName = "com.xiaomanjun.sleepdownschedule"

    private val routes = listOf(
        TransitionRouteSpec(
            id = TransitionRouteId.HomeToCourseManagement,
            destinationClassName = "$PackageName.CourseManagementActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.HomeMenuDestination,
                sourceCornerRadiusDp = 30f,
                returnCornerRadiusDp = 21f,
                destinationFirstOpening = true
            ),
            nativePolicy = TransitionNativePolicy.OplusAllowlisted,
            nativeClosePolicy = TransitionNativeClosePolicy.LegacyOnly,
            destinationWindowPolicy = TransitionDestinationWindowPolicy.OpaqueNativeCandidate,
            nativeDestinationClassName = "$PackageName.OplusCourseManagementActivity",
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            id = TransitionRouteId.CourseManagementToDetail,
            destinationClassName = "$PackageName.CourseManagementDetailActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.CourseManagementDetail,
                sourceCornerRadiusDp = 20f
            ),
            nativePolicy = TransitionNativePolicy.OplusAllowlisted,
            destinationWindowPolicy = TransitionDestinationWindowPolicy.OpaqueNativeCandidate,
            nativeDestinationClassName =
                "$PackageName.OplusCourseManagementDetailActivity",
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            id = TransitionRouteId.ManualImportToHistory,
            destinationClassName = "$PackageName.AiImportHistoryActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.Parabolic,
                sourceCornerRadiusDp = 21f
            ),
            nativePolicy = TransitionNativePolicy.OplusAllowlisted,
            destinationWindowPolicy = TransitionDestinationWindowPolicy.OpaqueNativeCandidate,
            nativeDestinationClassName = "$PackageName.OplusAiImportHistoryActivity",
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            id = TransitionRouteId.AiProgressToHistory,
            destinationClassName = "$PackageName.AiImportHistoryActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.Liquid,
                sourceCornerRadiusDp = 21f
            ),
            nativePolicy = TransitionNativePolicy.OplusAllowlisted,
            destinationWindowPolicy = TransitionDestinationWindowPolicy.OpaqueNativeCandidate,
            nativeDestinationClassName = "$PackageName.OplusAiImportHistoryActivity",
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            id = TransitionRouteId.AiHistoryToDetail,
            destinationClassName = "$PackageName.AiImportHistoryDetailActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.DetailSettings,
                sourceCornerRadiusDp = 18f
            ),
            nativePolicy = TransitionNativePolicy.Never,
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            TransitionRouteId.HomeToSettingsDetail,
            "$PackageName.SettingsDetailActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        // 多课表快速设置的"详细设置"按钮：走非 Oplus 锚定 Morph 链路。
        // 使用 Manifest 原生透明的真实 Activity 保留首页 Surface；页面仍复用同一个
        // SettingsDetailActivityHost，不能用运行时 setTheme 或 Activity alias 代替。
        TransitionRouteSpec(
            id = TransitionRouteId.QuickSheetToSettingsDetail,
            destinationClassName = "$PackageName.QuickSheetSettingsDetailActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.DetailSettings,
                sourceCornerRadiusDp = 18f
            ),
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            TransitionRouteId.SettingsToSettingsDetail,
            "$PackageName.SettingsDetailActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        TransitionRouteSpec(
            TransitionRouteId.ScheduleManagerToSettingsDetail,
            "$PackageName.SettingsDetailActivity",
            LegacyTransitionProfile.Depth(
                R.anim.schedule_depth_enter,
                R.anim.schedule_depth_exit,
                R.anim.schedule_depth_pop_enter,
                R.anim.schedule_depth_pop_exit
            )
        ),
        TransitionRouteSpec(
            id = TransitionRouteId.HomeToEduImport,
            destinationClassName = "$PackageName.EduSchoolSelectActivity",
            legacyProfile = LegacyTransitionProfile.Anchored(
                profileId = AnchoredLegacyProfileId.HomeMenuDestination,
                sourceCornerRadiusDp = 30f,
                returnCornerRadiusDp = 21f,
                destinationFirstOpening = true
            ),
            nativePolicy = TransitionNativePolicy.OplusAllowlisted,
            nativeClosePolicy = TransitionNativeClosePolicy.LegacyOnly,
            destinationWindowPolicy = TransitionDestinationWindowPolicy.OpaqueNativeCandidate,
            nativeDestinationClassName = "$PackageName.OplusEduSchoolSelectActivity",
            requiresOpeningAnchor = true,
            requiresReturnAnchor = true
        ),
        TransitionRouteSpec(
            TransitionRouteId.SchoolSelectToEduImport,
            "$PackageName.EduImportActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        TransitionRouteSpec(
            TransitionRouteId.TabletHomeToCourseManagement,
            "$PackageName.CourseManagementActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        TransitionRouteSpec(
            TransitionRouteId.TabletHomeToEduImport,
            "$PackageName.EduSchoolSelectActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        TransitionRouteSpec(
            TransitionRouteId.ImportToAiProgress,
            "$PackageName.AiEduImportProgressActivity",
            LegacyTransitionProfile.PlatformDefault
        ),
        TransitionRouteSpec(
            TransitionRouteId.ReturnToHome,
            "$PackageName.MainActivity",
            LegacyTransitionProfile.TaskReturn
        )
    ).associateBy(TransitionRouteSpec::id)

    fun get(id: TransitionRouteId): TransitionRouteSpec =
        checkNotNull(routes[id]) { "Unregistered transition route: $id" }

    fun all(): Collection<TransitionRouteSpec> = routes.values
}
