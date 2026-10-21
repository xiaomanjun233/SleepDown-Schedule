package com.xiaomanjun.sleepdownschedule.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.CourseManagementActivity
import com.xiaomanjun.sleepdownschedule.DefaultWallpaperStyle
import com.xiaomanjun.sleepdownschedule.EduImportActivity
import com.xiaomanjun.sleepdownschedule.EduSchoolSelectActivity
import com.xiaomanjun.sleepdownschedule.HomeStartMode
import com.xiaomanjun.sleepdownschedule.ImportDraftSource
import com.xiaomanjun.sleepdownschedule.QuickSheetSettingsDetailActivity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.SettingsDetailActivity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.performance.ContentEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.GlassPerformanceDiagnostics
import com.xiaomanjun.sleepdownschedule.core.performance.LocalGlassQuality
import com.xiaomanjun.sleepdownschedule.core.performance.LocalStartupPhase
import com.xiaomanjun.sleepdownschedule.core.performance.PerformanceAnimationState
import com.xiaomanjun.sleepdownschedule.core.performance.RefreshCadenceDiagnostics
import com.xiaomanjun.sleepdownschedule.core.performance.StartupJankStats
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.performance.TopBarEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.animatedGlassQuality
import com.xiaomanjun.sleepdownschedule.core.performance.homeWallpaperRenderKey
import com.xiaomanjun.sleepdownschedule.core.performance.rememberHomeWallpaperImages
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeIconButtonVisual
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalEditingCourseId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalSharedTransitionScope
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CenterLiquidDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidDialogSize
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalToolbarBackdropBlurred
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.MaterialFormDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.isLandscapeMenuWindow
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.popEnterTransition
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.popExitTransition
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.setStatusBarDarkIcons
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeButtonAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenu
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.DetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentActionHandler
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlan
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlanExecutionResult
import com.xiaomanjun.sleepdownschedule.domain.course.conflictWeeksForAddedCourses
import com.xiaomanjun.sleepdownschedule.domain.course.conflictWeeksForEditedCourseGroup
import com.xiaomanjun.sleepdownschedule.domain.course.conflictWeeksForSingleWeekEdit
import com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.formatScheduleDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.previewCourseEdits
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentBackgroundZoomRestScale
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentCountdownCinematicState
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentPreferences
import com.xiaomanjun.sleepdownschedule.feature.agent.HomeAssistantHost
import com.xiaomanjun.sleepdownschedule.feature.agent.HomeAssistantStage
import com.xiaomanjun.sleepdownschedule.feature.agent.HomeAssistantState
import com.xiaomanjun.sleepdownschedule.feature.agent.HomeCountdownCinematicOverlay
import com.xiaomanjun.sleepdownschedule.feature.agent.LocalDayAgentCountdownCinematic
import com.xiaomanjun.sleepdownschedule.feature.agent.LocalHomeAssistant
import com.xiaomanjun.sleepdownschedule.feature.agent.TodayAgentHost
import com.xiaomanjun.sleepdownschedule.feature.agent.assistantPullGesture
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave
import com.xiaomanjun.sleepdownschedule.feature.agent.rememberDayAgentBackgroundMotionState
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorCloseDurationMillis
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorContainerOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorFlightRegistry
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorOverlayPhase
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorOverlayRequest
import com.xiaomanjun.sleepdownschedule.feature.course.editor.LocalCourseEditorFlightRegistry
import com.xiaomanjun.sleepdownschedule.feature.course.editor.NormalizedCourseEditorScreen
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorApplyAllScope
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorHasOccurrence
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorWeekLandingBounds
import com.xiaomanjun.sleepdownschedule.feature.course.editor.rememberCourseEditorMotionState
import com.xiaomanjun.sleepdownschedule.feature.course.management.LandscapeCourseManagementPane
import com.xiaomanjun.sleepdownschedule.feature.course.management.putCourseManagementInitialState
import com.xiaomanjun.sleepdownschedule.feature.home.DockBackdropContinuityPatch
import com.xiaomanjun.sleepdownschedule.feature.home.HomeBackgroundBlurLayer
import com.xiaomanjun.sleepdownschedule.feature.home.HomeBackgroundZoomLayer
import com.xiaomanjun.sleepdownschedule.feature.home.HomeDialog
import com.xiaomanjun.sleepdownschedule.feature.home.HomeDockChrome
import com.xiaomanjun.sleepdownschedule.feature.home.HomeImportButtonReturnVisual
import com.xiaomanjun.sleepdownschedule.feature.home.HomeJumpWeekDialog
import com.xiaomanjun.sleepdownschedule.feature.home.HomeNavigationPreferences
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSidebar
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSidebarDestination
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSwitchClip
import com.xiaomanjun.sleepdownschedule.feature.home.HomeSwitchPane
import com.xiaomanjun.sleepdownschedule.feature.home.HomeTopGradientBlur
import com.xiaomanjun.sleepdownschedule.feature.home.LandscapePageTransition
import com.xiaomanjun.sleepdownschedule.feature.home.LocalAdjustedCourseEditor
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePagerStartOverflow
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.NativeHomeDockChrome
import com.xiaomanjun.sleepdownschedule.feature.home.PersonalizationPreviewState
import com.xiaomanjun.sleepdownschedule.feature.home.RetainedHomeValue
import com.xiaomanjun.sleepdownschedule.feature.home.ScheduleManagerEntryPill
import com.xiaomanjun.sleepdownschedule.feature.home.WallpaperEditorOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.adaptiveWeekRowHeightDp
import com.xiaomanjun.sleepdownschedule.feature.home.boundlessHomeTopGradientHeight
import com.xiaomanjun.sleepdownschedule.feature.home.day.ApplyCourseDeleteDialog
import com.xiaomanjun.sleepdownschedule.feature.home.day.ApplyCourseEditDialog
import com.xiaomanjun.sleepdownschedule.feature.home.day.CourseConflictRetentionDialog
import com.xiaomanjun.sleepdownschedule.feature.home.day.DayViewPreferences
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeBackdropFallback
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadabilityContext
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeScreen
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeWallpaper
import com.xiaomanjun.sleepdownschedule.feature.home.day.LocalHomeReadability
import com.xiaomanjun.sleepdownschedule.feature.home.day.WallpaperGlassSamplingToneOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.day.WallpaperToneOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.day.courseWeeksChanged
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeStatusBarWallpaperLuminance
import com.xiaomanjun.sleepdownschedule.feature.home.day.luminance
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.day.sampleVisibleWallpaperColors
import com.xiaomanjun.sleepdownschedule.feature.home.day.visibleWeekdaysForBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.homeOverlayDepthProgress
import com.xiaomanjun.sleepdownschedule.feature.home.homeSidebarContentInset
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchLayer
import com.xiaomanjun.sleepdownschedule.feature.home.minimumWeekCardHeightScale
import com.xiaomanjun.sleepdownschedule.feature.home.normalizedWeekCardHeightScale
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseCopyController
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseCopyOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseRemovalDurationMillis
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseRemovalMotion
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseShortcutController
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseShortcutOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAddMenuTargetCornerDp
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAnchoredMorphOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAnchoredOverlayKind
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAnchoredOverlayPhase
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAnchoredOverlayRequest
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeMenuDestinationCloseDurationMillis
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeMenuDestinationKind
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeMenuDestinationOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeMenuDestinationRequest
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseCopy
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseRemoval
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseShortcuts
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.homeAddMenuTargetRect
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.rememberHomeAnchoredMorphState
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.rememberHomeMenuDestinationMotionState
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeCardAlphaSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeCardBlurSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeCardFontSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeCardRefractionSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizePanel
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeWallpaperBlurSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeWallpaperBrightnessSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.PersonalizeWeekHeightSlider
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.mergePersonalizationCandidate
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.resolveActivePersonalizationSlider
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeSidebarState
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeSwitchMotion
import com.xiaomanjun.sleepdownschedule.feature.home.shouldReuseHomeSurface
import com.xiaomanjun.sleepdownschedule.feature.home.shouldUseFrozenHomeMorphBlur
import com.xiaomanjun.sleepdownschedule.feature.home.shouldUseStagedHomeOverlayBlur
import com.xiaomanjun.sleepdownschedule.feature.home.stagedHomeOverlayBlurProgress
import com.xiaomanjun.sleepdownschedule.feature.home.week.BoundlessWeekGridEndPadding
import com.xiaomanjun.sleepdownschedule.feature.home.week.BoundlessWeekHeaderRowHeight
import com.xiaomanjun.sleepdownschedule.feature.home.week.BoundlessWeekRowHeaderWidth
import com.xiaomanjun.sleepdownschedule.feature.home.week.BoundlessWeekdayHeaderRow
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekViewPreferences
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekViewStyle
import com.xiaomanjun.sleepdownschedule.feature.home.withPersonalizationConfig
import com.xiaomanjun.sleepdownschedule.feature.home.withSidebarInsets
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryBackgroundCapture
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettingsStore
import com.xiaomanjun.sleepdownschedule.feature.importing.ConfirmScheduleScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.ImportPreviewPage
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchoolPickerScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.IcsScheduleCodec
import com.xiaomanjun.sleepdownschedule.feature.importing.NormalizedAiManualImportScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.PendingImportSetupStore
import com.xiaomanjun.sleepdownschedule.feature.importing.buildSleepDownScheduleToken
import com.xiaomanjun.sleepdownschedule.feature.importing.loadAiImportFile
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.feature.schedule.ScheduleSnapshotStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.shareScheduleIcs
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.shareScheduleToken
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.CustomizeUiState
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleDraft
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleSettingsSheets
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.SchedulePickerOverlay
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.ScheduleShareType
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.forSchedule
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.rememberSchedulePickerState
import com.xiaomanjun.sleepdownschedule.feature.settings.HolidayReminder
import com.xiaomanjun.sleepdownschedule.feature.settings.ScheduleAdjustmentsActivity
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsUpdateDialog
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsUpdateDialogHost
import com.xiaomanjun.sleepdownschedule.feature.settings.UpcomingHoliday
import com.xiaomanjun.sleepdownschedule.feature.settings.readableUpdateMessage
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeUpdateCheckResult
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassBackendPolicy
import com.xiaomanjun.sleepdownschedule.glass.GlassRenderPhase
import com.xiaomanjun.sleepdownschedule.glass.GlassSamplingLink
import com.xiaomanjun.sleepdownschedule.glass.GlassTopologyNode
import com.xiaomanjun.sleepdownschedule.glass.GlassTopologyNodeRole
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSceneState
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSceneState
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalCourseCardColorAssignments
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.buildCourseCardColorAssignments
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardColorKey
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardGlassEffectFrame
import com.xiaomanjun.sleepdownschedule.glass.ui.rememberFallbackAdaptiveGlassState
import com.xiaomanjun.sleepdownschedule.glass.ui.rememberScreenScaledBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.resolvedCourseCardPalette
import com.xiaomanjun.sleepdownschedule.isAfterScheduleTerm
import com.xiaomanjun.sleepdownschedule.isBeforeScheduleTerm
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType
import com.xiaomanjun.sleepdownschedule.model.toHomeMode
import com.xiaomanjun.sleepdownschedule.parseScheduleDate
import com.xiaomanjun.sleepdownschedule.periodCount
import com.xiaomanjun.sleepdownschedule.resolveScheduleCurrentWeek
import com.xiaomanjun.sleepdownschedule.scheduleDayNavigationRange
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.legacy.DetailMorphRequest
import com.xiaomanjun.sleepdownschedule.transition.legacy.DetailMorphState
import com.xiaomanjun.sleepdownschedule.transition.legacy.DetailScheduleMorphOverlay
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/** Follow-through state for the three-dot button after a morph surface hands off on close. */
private data class SourceButtonFollowThrough(
    val startCenter: Offset,
    val startScale: Float
)

private data class PendingCourseGroupEdit(
    val originals: List<CourseEntity>,
    val edited: List<CourseEntity>
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CourseScheduleAppUi(
    viewModel: ScheduleViewModel,
    externalIcsUri: Uri? = null,
    onExternalIcsConsumed: (Uri) -> Unit = {},
    onStartupContentReady: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allSchedulesState by viewModel.allSchedulesState.collectAsStateWithLifecycle()
    val message by viewModel.snackbar.collectAsStateWithLifecycle()
    val pickerState = rememberSchedulePickerState()
    var previewScheduleId by remember { mutableStateOf<Int?>(null) }
    var pendingPickerEditorScheduleId by remember { mutableStateOf<Int?>(null) }
    var quickScheduleDraft by remember { mutableStateOf<QuickScheduleDraft?>(null) }
    var quickScheduleReturnToManager by remember { mutableStateOf(false) }
    var importSubmitting by remember { mutableStateOf(false) }
    var importSourceState by remember { mutableStateOf<AppState?>(null) }
    val importHomeReveal = remember { Animatable(1f) }
    var importPreviewMounted by remember { mutableStateOf(false) }
    var importPreviousCourseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var importExpectedCourseCount by remember { mutableStateOf<Int?>(null) }
    val dayAgentBackgroundMotionState = rememberDayAgentBackgroundMotionState()
    val dayAgentCountdownCinematic = remember { DayAgentCountdownCinematicState() }
    var dayAgentPagerSettled by remember { mutableStateOf(false) }
    var detailMorphState by remember { mutableStateOf<DetailMorphState>(DetailMorphState.Idle) }
    var detailMorphRequest by remember { mutableStateOf<DetailMorphRequest?>(null) }
    var detailCaptureCoverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailCaptureRecordCleanFrame by remember { mutableStateOf(false) }
    val detailCaptureMaskActive = remember { AtomicBoolean(false) }
    // Saving may activate a new schedule before the import window has finished retracting.
    val baseVisualState = importSourceState ?: (previewScheduleId?.let(allSchedulesState::forSchedule) ?: state)
    var personalizationDraftConfig by remember { mutableStateOf<ScheduleConfigEntity?>(null) }
    var personalizationPendingCommitConfig by remember { mutableStateOf<ScheduleConfigEntity?>(null) }
    var personalizationSliderPreviewKey by remember { mutableStateOf<String?>(null) }
    val personalizationPreviewState = remember { PersonalizationPreviewState() }
    val personalizationPreviewProgress by animateFloatAsState(
        targetValue = if (personalizationSliderPreviewKey != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.22f, 0f, 0.2f, 1f)),
        label = "personalization-preview-transition"
    )
    LaunchedEffect(baseVisualState.config, personalizationPendingCommitConfig) {
        val pending = personalizationPendingCommitConfig
        if (pending != null && baseVisualState.config == pending) {
            personalizationPendingCommitConfig = null
        }
    }
    LaunchedEffect(baseVisualState.config.id) {
        if (personalizationDraftConfig?.id != baseVisualState.config.id) {
            personalizationDraftConfig = null
        }
        if (personalizationPendingCommitConfig?.id != baseVisualState.config.id) {
            personalizationPendingCommitConfig = null
        }
    }
    val personalizationConfig = personalizationDraftConfig ?: personalizationPendingCommitConfig
    val pendingVisualState = baseVisualState.withPersonalizationConfig(personalizationConfig)
    val glassBackendPolicy = remember { GlassBackendPolicy.LargeGlass }
    val glassSceneState = rememberGlassSceneState(
        sceneId = "home",
        backendPolicy = glassBackendPolicy
    )
    val screenGraphicsLayer = rememberGraphicsLayer()
    // The week grid is already recorded into this layer for Morph motion. Reuse that GPU layer
    // as the personalization backdrop producer so the glass does not traverse every live
    // week-card RenderNode again on each animation frame.
    val cachedWeekHomeBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "home-cached-week-scene",
        sceneState = glassSceneState,
        graphicsLayer = screenGraphicsLayer
    )
    val detailScreenGraphicsLayer = rememberGraphicsLayer()
    val detailPopupGraphicsLayer = rememberGraphicsLayer()
    var detailPopupCaptureActive by remember { mutableStateOf(false) }
    var detailPopupCaptureToken by remember { mutableIntStateOf(0) }
    val detailPopupCapturedToken = remember { AtomicInteger(-1) }
    val recordedScheduleId = remember { AtomicInteger(-1) }
    val recordedHomeGeneration = remember { AtomicLong(0L) }
    val lastRecordedHomeFrameKey = remember { AtomicReference<String?>(null) }
    val weekHomeSurfaceUsesOffscreenCache = remember { AtomicBoolean(false) }
    var captureRenderToken by remember { mutableIntStateOf(0) }
    var snapshotGeneration by remember { mutableIntStateOf(0) }
    var snapshotJob by remember { mutableStateOf<Job?>(null) }
    var cacheHydrationJob by remember { mutableStateOf<Job?>(null) }
    var entryPrewarmJob by remember { mutableStateOf<Job?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sidebarPage by rememberSaveable { mutableStateOf(HomeSidebarDestination.Settings) }
    var sidebarPageMoving by remember { mutableStateOf(false) }
    var coursePaneExitHandler by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    var settingsExitInterceptionRequired by remember { mutableStateOf(false) }
    var settingsExitRequest by remember { mutableIntStateOf(0) }
    var pendingSettingsExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var homeMode by remember(state.loaded, state.config.defaultHomeMode) {
        mutableStateOf(state.config.defaultHomeMode.toHomeMode())
    }
    var dayRenderedCardCount by remember(state.config.id) { mutableIntStateOf(0) }
    var weekRenderedCardCount by remember(state.config.id) { mutableIntStateOf(0) }
    val rootPageMotion = rememberHomeSwitchMotion(screen is Screen.Config, "home-settings",
        renderedCardCount = { if (homeMode == HomeMode.Week) weekRenderedCardCount else dayRenderedCardCount })
    val homeModeMotion = key(state.loaded) {
        rememberHomeSwitchMotion(homeMode == HomeMode.Week, "day-week",
            animate = !rootPageMotion.landscape || !rootPageMotion.moving,
            renderedCardCount = { maxOf(dayRenderedCardCount, weekRenderedCardCount) })
    }
    val rootPageStateHolder = rememberSaveableStateHolder()
    var homeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    val aiHistorySelection by AiEduImportProgressSession.historySelection.collectAsStateWithLifecycle()
    val aiFinalImportRequest by AiEduImportProgressSession.finalImportRequest.collectAsStateWithLifecycle()
    LaunchedEffect(aiHistorySelection) {
        aiHistorySelection?.let { restored ->
            homeDialog = HomeDialog.ConfirmImport(restored, returnDialog = HomeDialog.ImportSchedule)
            AiEduImportProgressSession.consumeHistoryDraft()
        }
    }
    var renderedHomeDialog by remember { mutableStateOf<HomeDialog?>(null) }
    var homeDialogVisible by remember { mutableStateOf(false) }
    val appScope = rememberCoroutineScope()
    val homeAssistant = remember(appScope) { HomeAssistantState(appScope) }
    var assistantImportUri by remember { mutableStateOf<Uri?>(null) }
    var assistantImportText by remember { mutableStateOf<String?>(null) }
    val assistantHaptic = LocalHapticFeedback.current
    var assistantHapticSent by remember { mutableStateOf(false) }

    var courseEditorRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var pendingCourseGroupEdit by remember { mutableStateOf<PendingCourseGroupEdit?>(null) }
    var pendingCourseGroupDelete by remember { mutableStateOf<List<CourseEntity>>(emptyList()) }
    var homeCourseDismissHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val onHomeCourseDismissHandlerChange = remember {
        { handler: (() -> Unit)? -> homeCourseDismissHandler = handler }
    }
    var courseEditorRenderedCourseId by remember { mutableStateOf<Long?>(null) }
    val courseEditorMotionState = rememberCourseEditorMotionState()
    val courseEditorFlightRegistry = remember { CourseEditorFlightRegistry() }
    val courseEditorOverlayPhase = courseEditorMotionState.phase
    fun openCourseEditor(course: CourseEntity, targetWeek: Int?, sourceBounds: Rect?, copyDraft: CourseEntity? = null, contextMessage: String? = null, sourceAdjustmentLabel: String? = null) {
        if (courseEditorRequest != null) return
        val clickedSource = courseEditorFlightRegistry.consumeSource(sourceBounds)
        val sourceIsDayCard = clickedSource != null || (homeMode != HomeMode.Week && sourceBounds != null)
        val sourceGrid = if (sourceIsDayCard) null else targetWeek?.let(courseEditorFlightRegistry::grid)
        courseEditorFlightRegistry.frozen = true
        courseEditorRequest = CourseEditorOverlayRequest(
            course = course,
            targetWeek = targetWeek,
            sourceBoundsInRoot = sourceBounds,
            sourceClipBoundsInRoot = clickedSource?.visibleBounds,
            sourceDayAppearance = clickedSource?.dayAppearance,
            sourceIsDayCard = sourceIsDayCard,
            copyDraft = copyDraft,
            sourceGrid = sourceGrid,
            contextMessage = contextMessage,
            sourceAdjustmentLabel = sourceAdjustmentLabel
        )
    }
    fun openAdjustedCourseEditor(courseId: Long, date: LocalDate, sourceBounds: Rect?) {
        val original = state.courses.firstOrNull { it.id == courseId } ?: return
        val adjustment = scheduleAdjustmentForDate(state.config, date)
        val originalDate = adjustment?.sourceDate?.let(LocalDate::parse) ?: date
        val originalWeek = adjustedTeachingWeekForDate(state.config, originalDate) ?: return
        val message = if (adjustment?.sourceDate != null) {
            "$date 补 $originalDate（第 $originalWeek 周）的课。这里编辑原课程，修改也会同步到对应补课安排。"
        } else if (adjustment != null) {
            "$date 已停课。这里编辑保留的原课程；当天是否上课由调休安排决定。"
        } else null
        openCourseEditor(original, originalWeek, sourceBounds, contextMessage = message,
            sourceAdjustmentLabel = adjustment?.let { if (it.sourceDate == null) "停" else "补" })
    }
    fun closeCourseEditor() {
        courseEditorRequest = null
    }
    fun dismissHomeDialog() {
        homeDialogVisible = false
        homeDialog = null
        pendingCourseGroupEdit = null
        pendingCourseGroupDelete = emptyList()
    }
    var courseRemoval by remember { mutableStateOf<CourseRemovalMotion?>(null) }
    fun deleteHomeCourses(courses: List<CourseEntity>, week: Int?) {
        val removal = CourseRemovalMotion(courses, week)
        courseRemoval = removal
        val onFailure = { if (courseRemoval === removal) courseRemoval = null }
        if (week == null) viewModel.deleteCourses(courses, onFailure)
        else viewModel.deleteCoursesSingleWeek(courses, week, onFailure)
        dismissHomeDialog()
        closeCourseEditor()
    }
    LaunchedEffect(screen, homeMode, state.config.id) { courseRemoval = null }
    LaunchedEffect(homeDialog) {
        if (homeDialog != null) {
            renderedHomeDialog = homeDialog
            homeDialogVisible = true
        } else if (renderedHomeDialog != null) {
            homeDialogVisible = false
            delay(320)
            renderedHomeDialog = null
        }
    }
    val homeAnchoredMorphState = rememberHomeAnchoredMorphState()
    val courseShortcuts = remember(appScope) { CourseShortcutController(appScope) }
    val latestCopyState = rememberUpdatedState(state)
    val courseCopy = remember(appScope, viewModel) {
        CourseCopyController(
            scope = appScope,
            validate = { candidate ->
                val latest = latestCopyState.value
                when {
                    latest.config.id != candidate.scheduleId -> "课表已切换，请重新选择"
                    else -> conflictWeeksForAddedCourses(latest.previewCourseEdits(listOf(candidate)), latest.courses, latest.periods)
                        .takeIf { it.isNotEmpty() }
                        ?.let { "课程冲突，请换个位置" }
                }
            },
            save = { candidate, result -> viewModel.copyCourses(listOf(candidate), result); Unit }
        )
    }
    val homeMenuDestinationMotionState = rememberHomeMenuDestinationMotionState()
    var homeMenuDestinationRequest by remember { mutableStateOf<HomeMenuDestinationRequest?>(null) }
    var homeMenuSourceHidden by remember { mutableStateOf(false) }
    var homeAddMenuBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var homeAnchoredOverlayRequest by remember { mutableStateOf<HomeAnchoredOverlayRequest?>(null) }
    var homeMenuFromDock by remember { mutableStateOf(false) }
    var dockImportButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var dockImportReturnLayer by remember { mutableStateOf<androidx.compose.ui.graphics.layer.GraphicsLayer?>(null) }
    var jumpWeekDialogMounted by remember { mutableStateOf(false) }
    var jumpWeekDialogVisible by remember { mutableStateOf(false) }
    var pendingJumpWeekDialog by remember { mutableStateOf(false) }
    var pendingOpenScheduleSettings by remember { mutableStateOf(false) }
    var pendingQuickScheduleSettings by remember { mutableStateOf(false) }
    var homeMenuActivityLaunched by remember { mutableStateOf(false) }
    var destinationOwnsButtonReturn by remember { mutableStateOf(false) }
    var destinationCollapseHandedOff by remember { mutableStateOf(false) }
    var addButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var personalizeButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var sourceButtonFollowThrough by remember { mutableStateOf<SourceButtonFollowThrough?>(null) }
    val sourceButtonFollowProgress = remember { Animatable(0f) }
    val latestSourceFollowThrough = rememberUpdatedState<(Rect) -> Unit> { rect ->
        if (sourceButtonFollowThrough == null) {
            val rest = addButtonBounds ?: return@rememberUpdatedState
            sourceButtonFollowThrough = SourceButtonFollowThrough(
                startCenter = rect.center,
                startScale = (rect.width / rest.width.coerceAtLeast(1f)).coerceIn(0.30f, 1.05f)
            )
        }
    }
    var pendingHomeAnchoredOverlay by remember { mutableStateOf<HomeAnchoredOverlayKind?>(null) }
    var pendingHomeAnchoredSourceScale by remember { mutableFloatStateOf(1f) }
    var showScheduleEntryPill by remember { mutableStateOf(false) }
    val landingCourse = courseEditorMotionState.closingCourseOverride
    val editingCourseId: Long? = if (isLandscapeMenuWindow()) null else if (landingCourse != null) {
        state.courses.firstOrNull {
            it.copy(id = 0, weeks = landingCourse.weeks, weekParity = landingCourse.weekParity) == landingCourse.copy(id = 0)
        }?.id
    } else courseEditorRequest?.course?.id ?: courseEditorRenderedCourseId
    val activeHomeAnchoredOverlay =
        homeAnchoredOverlayRequest?.kind ?: homeAnchoredMorphState.renderedKind
    val destinationTransitionActive =
        homeMenuDestinationRequest != null ||
            homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle
    val addButtonHidden = activeHomeAnchoredOverlay == HomeAnchoredOverlayKind.Add ||
        sourceButtonFollowThrough != null ||
        homeMenuActivityLaunched ||
        (destinationTransitionActive && !destinationCollapseHandedOff)

    val personalizationPreviewActive by remember {
        derivedStateOf {
            personalizationSliderPreviewKey != null || personalizationPreviewProgress > 0.001f
        }
    }
    val homeBackgroundOverlayActive =
        homeAnchoredOverlayRequest != null || homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle ||
            destinationTransitionActive || courseEditorRequest != null ||
            courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle || courseShortcuts.request != null ||
            homeAssistant.stage == HomeAssistantStage.Conversation
    val preserveHomeOverlayMotion = !isLandscapeMenuWindow() ||
        activeHomeAnchoredOverlay == HomeAnchoredOverlayKind.Personalize ||
        courseShortcuts.request != null || homeAssistant.visible
    val homeBackgroundFreezeActive = preserveHomeOverlayMotion && !rootPageMotion.moving && !homeModeMotion.moving && shouldUseFrozenHomeMorphBlur(
        screenIsHome = screen is Screen.Home,
        previewActive = personalizationPreviewActive,
        overlayActive = homeBackgroundOverlayActive
    )
    // Retain data inputs as well as the drawn scene. Foreground editing still uses the live
    // ViewModel state. A copied course must enter layout before its existing landing animation.
    val retainedHomeState = remember(baseVisualState.config.id) { RetainedHomeValue(baseVisualState) }
    val retainedVisualState = retainedHomeState.update(
        baseVisualState,
        frozen = homeBackgroundFreezeActive && landingCourse == null
    ).withPersonalizationConfig(personalizationConfig)
    val removal = courseRemoval
    val visualState = remember(retainedVisualState, removal) {
        if (removal == null) retainedVisualState
        else retainedVisualState.copy(courses = removal.retainIn(retainedVisualState.courses))
    }
    val removalReady = removal != null && removal.appliedTo(state.courses) &&
        !homeBackgroundFreezeActive && renderedHomeDialog == null && editingCourseId == null
    LaunchedEffect(removal, removalReady) {
        if (removal != null && removalReady) {
            removal.progress.animateTo(1f, tween(CourseRemovalDurationMillis, easing = LinearEasing))
            if (courseRemoval === removal) courseRemoval = null
        }
    }
    val retainedAgentState = remember(state.config.id) { RetainedHomeValue(state) }
    val agentVisualState = retainedAgentState.update(state, frozen = homeBackgroundFreezeActive)
    val homeBackgroundSession = remember(homeBackgroundFreezeActive) { Any() }

    fun openHomeAnchoredOverlay(kind: HomeAnchoredOverlayKind, sourcePressedScale: Float = 1f) {
        if (homeAnchoredOverlayRequest != null ||
            homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle
        ) return
        val bounds = when (kind) {
            HomeAnchoredOverlayKind.Add -> addButtonBounds
            HomeAnchoredOverlayKind.Personalize -> personalizeButtonBounds
        }
        if (bounds == null || bounds.width <= 2f || bounds.height <= 2f) {
            pendingHomeAnchoredOverlay = kind
            pendingHomeAnchoredSourceScale = sourcePressedScale
            return
        }
        pendingHomeAnchoredOverlay = null
        if (kind == HomeAnchoredOverlayKind.Add) {
            homeMenuFromDock = false
            homeAddMenuBoundsInRoot = null
            dockImportReturnLayer = null
        }
        homeAnchoredOverlayRequest = HomeAnchoredOverlayRequest(kind, bounds, sourcePressedScale)
    }

    fun toggleHomeAnchoredOverlay(kind: HomeAnchoredOverlayKind, sourcePressedScale: Float = 1f) {
        if (homeAnchoredOverlayRequest?.kind == kind) {
            homeAnchoredOverlayRequest = null
        } else {
            openHomeAnchoredOverlay(kind, sourcePressedScale)
        }
    }

    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val homeAdaptiveMetrics = rememberHomeAdaptiveMetrics()
    val homeSidebarState = rememberHomeSidebarState(homeAdaptiveMetrics)
    val eduFloatingOverlayHost = remember(homeSidebarState) {
        DetailActivityFloatingOverlayHost { Modifier.homeSidebarContentInset(homeSidebarState) }
    }
    val navigationRevision by HomeNavigationPreferences.changes.collectAsStateWithLifecycle()
    val navigationContext = LocalContext.current.applicationContext
    val parallelNavigation = remember(navigationContext, navigationRevision) { HomeNavigationPreferences.isParallel(navigationContext) }
    val parallelPhoneNavigation = parallelNavigation && !homeAdaptiveMetrics.isLargeScreen &&
        homeAdaptiveMetrics.screenHeight >= homeAdaptiveMetrics.screenWidth
    var tabletAboutGradientColors by remember { mutableStateOf<List<ComposeColor>?>(null) }
    val tabletAboutGradient = remember(tabletAboutGradientColors, windowContainerSize) {
        tabletAboutGradientColors?.let { colors ->
            SettingsSharedGradient(colors).apply {
                boundsInRoot = Rect(0f, 0f, windowContainerSize.width.toFloat(), windowContainerSize.height.toFloat())
            }
        }
    }
    val sidebarContentMetrics = remember(
        homeAdaptiveMetrics, homeSidebarState.targetContentInset, homeSidebarState.endInset,
        homeSidebarState.expanded
    ) {
        homeAdaptiveMetrics.withSidebarInsets(
            homeSidebarState.targetContentInset, homeSidebarState.endInset,
            expanded = homeSidebarState.expanded && !homeSidebarState.overlaysContent
        )
    }
    val homeDeviceCornerPx = com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx()

    LaunchedEffect(
        pendingHomeAnchoredOverlay,
        homeAnchoredMorphState.phase,
        screen
    ) {
        val pending = pendingHomeAnchoredOverlay ?: return@LaunchedEffect
        if (screen !is Screen.Home || homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle) {
            return@LaunchedEffect
        }
        // Top-bar anchors move on every Settings/Home frame. Observe them only for a pending
        // open; reading them as effect keys recomposed this entire root during the page slide.
        snapshotFlow {
            if (pending == HomeAnchoredOverlayKind.Add) addButtonBounds else personalizeButtonBounds
        }.first { it != null && it.width > 2f && it.height > 2f }
        openHomeAnchoredOverlay(pending, pendingHomeAnchoredSourceScale)
    }
    LaunchedEffect(screen) {
        if (screen !is Screen.Home) {
            pendingHomeAnchoredOverlay = null
            homeAnchoredOverlayRequest = null
            personalizationSliderPreviewKey = null
            personalizationDraftConfig = null
            personalizationPreviewState.clearAll()
        }
        if (screen !is Screen.Config) {
            settingsExitInterceptionRequired = false
            pendingSettingsExitAction = null
        }
    }
    LaunchedEffect(homeAnchoredOverlayRequest) {
        if (homeAnchoredOverlayRequest?.kind != HomeAnchoredOverlayKind.Personalize) {
            personalizationSliderPreviewKey = null
            personalizationDraftConfig = null
            personalizationPreviewState.clearAll()
        }
    }
    var homeContentUnderTopBar by remember { mutableStateOf(false) }
    val adaptiveWeekCardHeight = remember(
        windowContainerSize.height,
        density.density,
        density.fontScale,
        homeAdaptiveMetrics.weekTopSpacerHeight,
        homeAdaptiveMetrics.safeBottom,
        visualState.periods.size
    ) {
        adaptiveWeekRowHeightDp(
            viewportHeightDp = windowContainerSize.height / density.density.coerceAtLeast(0.001f),
            topSpacerDp = homeAdaptiveMetrics.weekTopSpacerHeight.value,
            periodCount = visualState.periods.size,
            bottomInsetDp = homeAdaptiveMetrics.safeBottom.value + 8f,
            fontScale = density.fontScale
        )
    }
    val weekCardHeightScaleFloor = remember(adaptiveWeekCardHeight, density.fontScale) {
        minimumWeekCardHeightScale(
            adaptiveRowHeightDp = adaptiveWeekCardHeight,
            fontScale = density.fontScale
        )
    }
    var weekCardHeightScale by remember(
        visualState.config.id,
        visualState.config.weekCardHeightScale,
        weekCardHeightScaleFloor
    ) {
        mutableFloatStateOf(
            normalizedWeekCardHeightScale(
                visualState.config.weekCardHeightScale,
                weekCardHeightScaleFloor
            )
        )
    }
    val weekCardHeight = adaptiveWeekCardHeight * weekCardHeightScale
    val context = LocalContext.current
    val dayViewMode = remember(screen, state.config.defaultHomeMode, context) {
        DayViewPreferences.mode(
            context = context,
            legacyTwoDay = state.config.defaultHomeMode == HomeStartMode.TWO_DAY
        )
    }
    var weekViewStyle by remember(screen, context) {
        mutableStateOf(WeekViewPreferences.style(context))
    }
    val remoteExperience by SleepDownRemoteConfig.experience.collectAsStateWithLifecycle()
    val remoteConfigState by SleepDownRemoteConfig.state.collectAsStateWithLifecycle()
    fun requestSettingsExit(action: () -> Unit) {
        if (screen is Screen.Config && sidebarPage == HomeSidebarDestination.Courses && coursePaneExitHandler != null) {
            coursePaneExitHandler?.invoke(action)
        } else if (screen is Screen.Config && (!homeAdaptiveMetrics.isTabletLandscape || sidebarPage == HomeSidebarDestination.Settings) && settingsExitInterceptionRequired) {
            pendingSettingsExitAction = action
            settingsExitRequest++
        } else {
            action()
        }
    }
    BackHandler(enabled = screen is Screen.Config && settingsExitInterceptionRequired) {
        requestSettingsExit { context.findActivity()?.finish() }
    }
    var showManagedFreeAiOffer by remember(context) { mutableStateOf(false) }
    LaunchedEffect(remoteExperience) {
        if (remoteExperience.agreement != null || remoteExperience.notice != null) {
            showManagedFreeAiOffer = false
        }
    }
    var pendingImportedSetupId by remember(context) {
        mutableStateOf(PendingImportSetupStore.consume(context))
    }
    // Daily "a holiday is coming" prompt. It asks at most once a day, stays quiet for holidays the
    // user already planned, and never competes with the agreement/notice surfaces.
    var upcomingHoliday by remember { mutableStateOf<UpcomingHoliday?>(null) }
    val latestHomeConfig by rememberUpdatedState(state.config)
    val latestHomePeriods by rememberUpdatedState(state.periods)
    val holidayAdjustmentsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(ScheduleAdjustmentsActivity.ArrangementsExtra)?.let { value ->
                val scheduleId = result.data?.getIntExtra(ScheduleAdjustmentsActivity.ScheduleIdExtra, -1) ?: -1
                val original = result.data?.getStringExtra(ScheduleAdjustmentsActivity.OriginalArrangementsExtra)
                if (scheduleId > 0 && original != null &&
                    result.data?.getBooleanExtra(ScheduleAdjustmentsActivity.SavedExtra, false) != true
                ) viewModel.saveScheduleAdjustments(scheduleId, original, value)
            }
        }
    }
    LaunchedEffect(remoteExperience, state.loaded, state.config.id, state.config.scheduleAdjustmentsJson) {
        if (!state.loaded) return@LaunchedEffect
        if (remoteExperience.agreement != null || remoteExperience.notice != null) return@LaunchedEffect
        val holiday = HolidayReminder.upcomingHoliday(context, state.config) ?: return@LaunchedEffect
        if (HolidayReminder.claimDailyPrompt(context)) upcomingHoliday = holiday
    }
    LaunchedEffect(aiFinalImportRequest) {
        aiFinalImportRequest?.let { request ->
            importSourceState = visualState
            importPreviousCourseIds = state.courses.map { it.id }.toSet()
            importExpectedCourseCount = request.draft.courses.size
            viewModel.importDraft(request.draft, request.createNewSchedule, onFailure = {
                importSourceState = null
                importExpectedCourseCount = null
                importPreviousCourseIds = emptySet()
                homeDialog = HomeDialog.ConfirmImport(request.draft, returnDialog = null,
                    errorMessage = it.message ?: "课表保存失败，请重试")
            }) { scheduleId ->
                pendingImportedSetupId = scheduleId
            }
            // Claim this one-shot request now so Activity recreation cannot import it twice.
            AiEduImportProgressSession.consumeFinalImportRequest()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(externalIcsUri, state.loaded) {
        val uri = externalIcsUri ?: return@LaunchedEffect
        if (!state.loaded) return@LaunchedEffect
        onExternalIcsConsumed(uri)
        screen = Screen.Home
        pendingHomeAnchoredOverlay = null
        homeAnchoredOverlayRequest = null
        loadAiImportFile(context, uri)
            .onSuccess { file ->
                if (!file.isIcs) {
                    Toast.makeText(context, "分享的文件不是有效的 ICS 日历", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                IcsScheduleCodec.parse(file.bytes, state.config)
                    .onSuccess { draft ->
                        homeDialog = HomeDialog.ConfirmImport(draft, returnDialog = null)
                    }
                    .onFailure { error ->
                        if (IcsScheduleCodec.isSleepDown(file.bytes)) {
                            Toast.makeText(context, error.message ?: "ICS 解析失败", Toast.LENGTH_SHORT).show()
                        } else {
                            assistantImportUri = uri
                            homeDialog = HomeDialog.ImportSchedule
                        }
                    }
            }
            .onFailure { error ->
                Toast.makeText(context, error.message ?: "ICS 文件读取失败", Toast.LENGTH_SHORT).show()
            }
    }
    val backgroundBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Background,
        providerId = "home-background",
        sceneState = glassSceneState
    )
    // Record before page translation/rounding. Course cards compare their relative position
    // with this moving origin; the wallpaper pixels and shared blur stay unchanged on a tab switch.
    val courseWallpaperBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Background,
        providerId = "home-course-wallpaper",
        sceneState = glassSceneState
    )
    val contentBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "home-content",
        sceneState = glassSceneState
    )
    val pickerSceneBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.PickerScene,
        providerId = "home-picker-scene",
        sceneState = glassSceneState
    )
    val centeredDialogSceneBackdrop = rememberCenteredDialogSceneBackdrop(
        providerId = "home-centered-dialog-scene"
    )
    val chromeBackdrop = rememberGlassCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    remember(glassSceneState) {
        glassSceneState.requireValidTopology(
            nodes = listOf(
                GlassTopologyNode("home-background", GlassBackdropDomain.Background, GlassTopologyNodeRole.Producer),
                GlassTopologyNode("home-content", GlassBackdropDomain.Content, GlassTopologyNodeRole.Producer),
                GlassTopologyNode("home-chrome", GlassBackdropDomain.ChromeCombined, GlassTopologyNodeRole.Consumer),
                GlassTopologyNode("home-picker", GlassBackdropDomain.PickerScene, GlassTopologyNodeRole.Producer),
                GlassTopologyNode("home-dialog", GlassBackdropDomain.DialogBridge, GlassTopologyNodeRole.Consumer),
                GlassTopologyNode("home-cached-week", GlassBackdropDomain.Content, GlassTopologyNodeRole.Producer),
                GlassTopologyNode(
                    "home-personalization",
                    GlassBackdropDomain.ChromeCombined,
                    GlassTopologyNodeRole.Consumer
                )
            ),
            links = listOf(
                GlassSamplingLink("home-background", "home-chrome"),
                GlassSamplingLink("home-content", "home-chrome"),
                GlassSamplingLink("home-background", "home-dialog"),
                GlassSamplingLink("home-content", "home-dialog"),
                GlassSamplingLink("home-cached-week", "home-personalization")
            )
        )
    }
    var homeReadabilityRootSize by remember { mutableStateOf(IntSize.Zero) }
    var homeRootPositionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var homeRootPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    fun currentHomeMenuDestinationRequest(
        kind: HomeMenuDestinationKind
    ): HomeMenuDestinationRequest? {
        if (homeAdaptiveMetrics.isTabletLandscape) {
            // Landscape destinations have no source button; their host uses the centered flight.
            return HomeMenuDestinationRequest(kind, Rect.Zero, Rect.Zero)
        }
        val sourceButton = (if (homeMenuFromDock) dockImportButtonBounds else addButtonBounds) ?: return null
        if (homeReadabilityRootSize.width <= 0 || homeReadabilityRootSize.height <= 0) return null
        val menuBounds = homeAddMenuBoundsInRoot ?: homeAddMenuTargetRect(
            source = sourceButton,
            rootSize = homeReadabilityRootSize,
            density = density.density,
            actionCount = if (parallelPhoneNavigation) 3 else 6,
            adaptiveMetrics = homeAdaptiveMetrics,
            showModeSwitch = !parallelPhoneNavigation,
            aboveSource = homeMenuFromDock,
            actionItemHeightDp = if (homeMenuFromDock) 40f * density.fontScale.coerceAtLeast(1f) else 40f
        )
        return HomeMenuDestinationRequest(
            kind = kind,
            sourceBoundsInRoot = menuBounds,
            collapseBoundsInRoot = sourceButton,
            fromDock = homeMenuFromDock,
            showModeSwitch = !parallelPhoneNavigation
        )
    }
    fun openHomeMenuDestination(kind: HomeMenuDestinationKind) {
        val request = currentHomeMenuDestinationRequest(kind) ?: return
        destinationOwnsButtonReturn = false
        destinationCollapseHandedOff = false
        homeMenuDestinationRequest = request
    }
    val currentVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    var automaticUpdateDialog by remember { mutableStateOf<SettingsUpdateDialog?>(null) }
    var automaticDownloadedUpdate by remember { mutableStateOf<java.io.File?>(null) }
    val automaticInstallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = automaticDownloadedUpdate
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                .onFailure { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
        }
    }
    fun downloadAutomaticUpdate(release: GiteeReleaseInfo) {
        if (release.apkUrl == null) {
            automaticUpdateDialog = SettingsUpdateDialog.NoApk(release)
            return
        }
        automaticUpdateDialog = SettingsUpdateDialog.Downloading(release)
        appScope.launch {
            GiteeAppUpdater.downloadApk(context, release).fold(
                onSuccess = { apk ->
                    automaticDownloadedUpdate = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                            .onSuccess { automaticUpdateDialog = null }
                            .onFailure { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    } else {
                        automaticUpdateDialog = SettingsUpdateDialog.InstallPermissionRequired
                    }
                },
                onFailure = { automaticUpdateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }
    LaunchedEffect(state.loaded, state.config.autoCheckUpdates, currentVersionName) {
        if (!state.loaded) return@LaunchedEffect
        if (!AppDistribution.supportsSelfUpdate) return@LaunchedEffect
        GiteeAppUpdater.restoreCachedStatus(context, currentVersionName)
        if (!state.config.autoCheckUpdates || !GiteeAppUpdater.shouldRunDailyCheck(context)) return@LaunchedEffect
        GiteeAppUpdater.markDailyCheckStarted(context)
        GiteeAppUpdater.checkForUpdate(context, currentVersionName).onSuccess { result ->
            GiteeAppUpdater.recordCheckResult(context, result)
            if (result is GiteeUpdateCheckResult.UpdateAvailable) {
                automaticUpdateDialog = SettingsUpdateDialog.Available(result.release)
            }
        }
    }
    val wallpaperImages by rememberHomeWallpaperImages(visualState.config)
    val colorOsOverflowActions = remember { mutableStateOf<List<AddMenuAction>>(emptyList()) }
    val homeWallpaperRecordKey = remember { mutableStateOf<Any?>(null) }
    val updateHomeWallpaperRecordKey: (Any?) -> Unit = remember {
        { homeWallpaperRecordKey.value = it }
    }
    val homeReadabilityContext = remember(
        wallpaperImages.readabilityBitmap,
        visualState.config,
        homeReadabilityRootSize,
        homeRootPositionInWindow
    ) {
        HomeReadabilityContext(
            bitmap = wallpaperImages.readabilityBitmap,
            config = visualState.config,
            rootSize = homeReadabilityRootSize,
            rootOffsetInWindow = homeRootPositionInWindow
        )
    }
    val appDarkTheme = appUsesDarkTheme(visualState.config)
    val nativeVisuals = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects.current.usesNativeControls
    val colorOsAccentBackground = remember(nativeVisuals, homeReadabilityContext, appDarkTheme) {
        if (!nativeVisuals) return@remember 0
        val root = homeReadabilityContext.rootSize
        val colors = sampleVisibleWallpaperColors(homeReadabilityContext,
            Rect(0f, root.height * 0.80f, root.width.toFloat(), root.height.toFloat()), columns = 9, rows = 3)
        if (colors.isNullOrEmpty()) (if (appDarkTheme) ComposeColor.Black else ComposeColor.White).toArgb()
        else ComposeColor(colors.map { it.red }.average().toFloat(), colors.map { it.green }.average().toFloat(),
            colors.map { it.blue }.average().toFloat()).toArgb()
    }
    val colorOsHomeAccents = com.xiaomanjun.sleepdownschedule.platform.coloros
        .rememberColorOsHomeAccents(visualState.config, appDarkTheme, colorOsAccentBackground)
    val colorOsActiveDockAccent = if (screen is Screen.Home) colorOsHomeAccents?.dock
        else colorOsHomeAccents?.button?.let {
            com.xiaomanjun.sleepdownschedule.platform.coloros.colorOsContrastingAccent(
                it, MaterialTheme.colorScheme.background.toArgb())
        }
    val adaptiveGlassState = rememberFallbackAdaptiveGlassState(visualState.config)
    val homeChromeHasWallpaper = visualState.config.hasAnyWallpaper()
    val homeChromeForeground = adaptiveGlassState.contentColor.toArgb().takeIf { homeChromeHasWallpaper }
    val homeChromeDark = if (homeChromeHasWallpaper) !adaptiveGlassState.lightGlass else appDarkTheme
    val homeNeutralChromeStyle = remember(nativeVisuals, homeChromeDark, homeChromeHasWallpaper, homeChromeForeground) {
        if (!nativeVisuals) null else com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialStyle(
            homeChromeDark, homeChromeHasWallpaper && !homeChromeDark, homeChromeForeground)
    }
    val statusBarWallpaperLuminance = remember(homeReadabilityContext) {
        homeStatusBarWallpaperLuminance(homeReadabilityContext)
    }
    var homeStatusBarDarkIcons by remember { mutableStateOf(!appDarkTheme) }
    LaunchedEffect(statusBarWallpaperLuminance, appDarkTheme) {
        homeStatusBarDarkIcons = statusBarWallpaperLuminance?.let { luminance ->
            // A small dead band prevents icon polarity from flickering while brightness is dragged.
            if (homeStatusBarDarkIcons) luminance >= 0.46f else luminance >= 0.56f
        } ?: !appDarkTheme
    }
    LaunchedEffect(screen, homeStatusBarDarkIcons, appDarkTheme, context, homeAssistant.visible) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        window.setStatusBarDarkIcons(
            darkIcons = if (screen is Screen.Home && homeAssistant.visible) false
                else if (screen is Screen.Home) homeStatusBarDarkIcons else !appDarkTheme
        )
    }
    val expectedWallpaperRenderKey = homeWallpaperRenderKey(
        visualState.config,
        appUsesDarkTheme(visualState.config)
    )
    val wallpaperLoadFinished = !visualState.config.hasAnyWallpaper() ||
        wallpaperImages.renderKey == expectedWallpaperRenderKey
    val noWallpaperResolved =
        visualState.config.defaultWallpaperStyle == DefaultWallpaperStyle.NONE &&
            wallpaperImages.renderKey == expectedWallpaperRenderKey &&
            wallpaperImages.source == null
    LaunchedEffect(wallpaperImages.renderKey, expectedWallpaperRenderKey) {
        if (wallpaperImages.renderKey == expectedWallpaperRenderKey) {
            // Keep the cheap GPU blur preview visible until the one final CPU-cached bitmap is
            // ready, then hand over without exposing the previously committed blur for a frame.
            personalizationPreviewState.clear(PersonalizeWallpaperBlurSlider)
        }
    }
    val latestOnStartupContentReady = rememberUpdatedState(onStartupContentReady)
    var startupContentReported by remember { mutableStateOf(false) }
    val latestVisualState = rememberUpdatedState(visualState)
    val latestWallpaperImages = rememberUpdatedState(wallpaperImages)
    val latestAllSchedulesState = rememberUpdatedState(allSchedulesState)
    val startupPhase = StartupPhase.FullQuality
    LaunchedEffect(state.loaded, wallpaperLoadFinished) {
        if (!state.loaded || !wallpaperLoadFinished) return@LaunchedEffect
        // Release the first-draw gate only after the wallpaper and stationary home share a frame.
        withFrameNanos { }
        if (!startupContentReported) {
            startupContentReported = true
            latestOnStartupContentReady.value()
        }
    }
    LaunchedEffect(
        state.loaded,
        startupPhase,
        screen,
        homeDialogVisible,
        automaticUpdateDialog,
        courseEditorRequest,
        remoteConfigState.bootstrap?.ai?.configVersion,
        remoteConfigState.bootstrap?.ai?.enabled,
        remoteConfigState.bootstrap?.aiConfigs
    ) {
        if (
            state.loaded &&
            startupPhase == StartupPhase.FullQuality &&
            screen is Screen.Home &&
            !homeDialogVisible &&
            automaticUpdateDialog == null &&
            courseEditorRequest == null &&
            remoteExperience.agreement == null &&
            remoteExperience.notice == null &&
            AiImportSettingsStore.shouldOfferManagedFreeAi(context)
        ) {
            delay(240)
            showManagedFreeAiOffer = true
        }
    }
    val glassQuality = animatedGlassQuality(startupPhase)
    val homeAnchoredKindLabel = when (homeAnchoredMorphState.renderedKind) {
        HomeAnchoredOverlayKind.Add -> "AddMenu"
        HomeAnchoredOverlayKind.Personalize -> "Personalize"
        null -> "Unknown"
    }
    val homeMenuDestinationKindLabel = when (homeMenuDestinationMotionState.kind) {
        HomeMenuDestinationKind.AddCourse -> "AddCourse"
        HomeMenuDestinationKind.ManualImport -> "ManualImport"
        HomeMenuDestinationKind.EduImport -> "EduImport"
        null -> "Unknown"
    }
    val startupAnimation = when {
        personalizationSliderPreviewKey != null -> "PersonalizeSliderDrag"
        homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle -> when (homeMenuDestinationMotionState.phase) {
            HomeAnchoredOverlayPhase.Preparing -> "HomeMenuDestinationPrepare:$homeMenuDestinationKindLabel"
            HomeAnchoredOverlayPhase.Opening -> "HomeMenuDestinationOpen:$homeMenuDestinationKindLabel"
            HomeAnchoredOverlayPhase.Closing,
            HomeAnchoredOverlayPhase.Disposing -> "HomeMenuDestinationClose:$homeMenuDestinationKindLabel"
            HomeAnchoredOverlayPhase.Open -> "Idle"
            HomeAnchoredOverlayPhase.Idle -> "Idle"
        }
        homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle -> when (homeAnchoredMorphState.phase) {
            HomeAnchoredOverlayPhase.Preparing -> if (homeAnchoredKindLabel == "Personalize") {
                "PersonalizePrepare"
            } else "HomeAnchoredPrepare:$homeAnchoredKindLabel"
            HomeAnchoredOverlayPhase.Opening -> if (homeAnchoredKindLabel == "Personalize") {
                "PersonalizeOpen"
            } else "HomeAnchoredOpen:$homeAnchoredKindLabel"
            HomeAnchoredOverlayPhase.Closing,
            HomeAnchoredOverlayPhase.Disposing -> if (homeAnchoredKindLabel == "Personalize") {
                "PersonalizeClose"
            } else "HomeAnchoredClose:$homeAnchoredKindLabel"
            HomeAnchoredOverlayPhase.Open -> "Idle"
            HomeAnchoredOverlayPhase.Idle -> "Idle"
        }
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Preparing -> "CourseEditorPrepare"
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Opening -> "CourseEditorOpen"
        courseEditorOverlayPhase == CourseEditorOverlayPhase.Closing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Disposing -> "CourseEditorClose"
        homeDialogVisible -> "DialogOpen"
        else -> "Idle"
    }
    val glassRenderPhase = when {
        startupAnimation.contains("Prepare", ignoreCase = true) -> GlassRenderPhase.Preparing
        startupAnimation.contains("Close", ignoreCase = true) -> GlassRenderPhase.Closing
        startupAnimation != "Idle" -> GlassRenderPhase.Moving
        else -> GlassRenderPhase.Live
    }
    LaunchedEffect(glassRenderPhase) {
        glassSceneState.synchronizePhase(glassRenderPhase)
    }
    PerformanceAnimationState(startupAnimation, startupAnimation != "Idle")
    GlassPerformanceDiagnostics(glassSceneState, startupAnimation)
    RefreshCadenceDiagnostics(
        enabled = BuildConfig.SLEEPDOWN_LARGE_GLASS_EXPERIMENT && startupAnimation != "Idle"
    )
    StartupJankStats(
        phase = startupPhase,
        screen = if (screen is Screen.Home) "Home" else if (screen is Screen.Config) "Settings" else "Other",
        animation = startupAnimation,
        personalizeMode = if (activeHomeAnchoredOverlay == HomeAnchoredOverlayKind.Personalize) {
            homeMode.name
        } else {
            "Idle"
        },
        personalizeSlider = when (personalizationSliderPreviewKey) {
            PersonalizeWallpaperBlurSlider -> "WallpaperBlur"
            PersonalizeWallpaperBrightnessSlider -> "WallpaperBrightness"
            PersonalizeWeekHeightSlider -> "WeekHeight"
            PersonalizeCardAlphaSlider -> "CardAlpha"
            PersonalizeCardBlurSlider -> "CardBlur"
            PersonalizeCardRefractionSlider -> "CardRefraction"
            PersonalizeCardFontSlider -> "CardFont"
            else -> "Idle"
        }
    )
    // Sample the window-origin recorded home scene so the morph shell and the wallpaper
    // behind it stay aligned (see homeAnchoredOverlayBackdrop).
    val courseEditorBackdrop = rememberScreenScaledBackdrop(
        backdrop = cachedWeekHomeBackdrop,
        scale = { courseEditorMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val courseEditorSourceBackdrop = rememberScreenScaledBackdrop(
        backdrop = courseWallpaperBackdrop,
        scale = { courseEditorMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    // The week grid is recorded into screenGraphicsLayer at the window origin (the whole
    // readable root including the top bar). Sampling that same recorded scene keeps the
    // source-anchored overlays aligned in both day and week modes; the live chromeBackdrop
    // producers live below the top bar, so drawing them at the root origin shifts the
    // sampled wallpaper upward by the top-bar height.
    val homeAnchoredOverlayBackdrop = rememberScreenScaledBackdrop(
        backdrop = cachedWeekHomeBackdrop,
        scale = {
            val zoom = homeAnchoredMorphState.backgroundZoom.value
            1f + (zoom - 1f) * (1f - personalizationPreviewProgress)
        },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    // Android Dialog content owns a separate Compose root/window. Wrap each recorded layer in
    // screen coordinates before combining them, otherwise LayerBackdrop mixes the dialog and
    // activity window origins and samples with a decor/status-bar offset on ColorOS.
    val homeDialogBackdrop = rememberScreenScaledBackdrop(
        backdrop = cachedWeekHomeBackdrop,
        scale = { 1f },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    ) ?: cachedWeekHomeBackdrop
    val homeMenuDestinationBackdrop = rememberScreenScaledBackdrop(
        backdrop = cachedWeekHomeBackdrop,
        scale = { homeMenuDestinationMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val fullScreenMenuDestinationActive =
        homeMenuDestinationRequest?.kind == HomeMenuDestinationKind.EduImport ||
            (homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle &&
                homeMenuDestinationMotionState.kind == HomeMenuDestinationKind.EduImport)
    val homeOverlayBackgroundZoom: () -> Float = {
        if (personalizationPreviewProgress > 0.001f) {
            val zoom = homeAnchoredMorphState.backgroundZoom.value
            1f + (zoom - 1f) * (1f - personalizationPreviewProgress)
        } else if (fullScreenMenuDestinationActive) {
            homeMenuDestinationMotionState.backgroundZoom.value
        } else {
            maxOf(
                courseEditorMotionState.backgroundZoom.value,
                homeAnchoredMorphState.backgroundZoom.value,
                homeMenuDestinationMotionState.backgroundZoom.value
            )
        }
    }
    val dayAgentBackdrop = rememberScreenScaledBackdrop(
        // TodayAgentHost is inside screenGraphicsLayer: sampling that layer here would
        // make its RenderNode graph recursive. The wallpaper producer excludes the agent.
        backdrop = backgroundBackdrop,
        scale = { dayAgentBackgroundMotionState.backgroundZoom.value },
        rootPositionOnScreen = { homeRootPositionOnScreen },
        rootSize = { homeReadabilityRootSize }
    )
    val systemDark = isSystemInDarkTheme()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            appScope.launch {
                val entrySnapshot = runCatching {
                    captureRenderToken += 1
                    withFrameNanos { }
                    screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                homeDialog = HomeDialog.EditWallpaper(uri, entrySnapshot)
            }
        }
    }
    val todayDate = LocalDate.now()
    val homeCurrentWeek = effectiveCurrentWeek(visualState.config)
    val beforeScheduleTerm = isBeforeScheduleTerm(visualState.config, todayDate)
    val afterScheduleTerm = isAfterScheduleTerm(visualState.config, todayDate)
    val homeWeekHeaderPreview = remember(visualState.config.id) { mutableStateOf<Int?>(null) }
    var homeDisplayWeek by remember(visualState.config.id, visualState.loaded) {
        mutableIntStateOf(if (beforeScheduleTerm) 1 else homeCurrentWeek)
    }
    var pendingConflictCourseId by remember(visualState.config.id) { mutableStateOf<Long?>(null) }
    var pendingConflictCourseKey by remember(visualState.config.id) { mutableStateOf<String?>(null) }
    var pendingConflictWeeks by remember(visualState.config.id) { mutableStateOf<List<Int>>(emptyList()) }
    var homeDisplayDate by remember(visualState.config.id) { mutableStateOf(todayDate) }
    LaunchedEffect(
        state.loaded,
        visualState.config.id,
        pickerState.overlayVisible,
        homeMode,
        homeDisplayDate
    ) {
        // The Agent background is transformed live now, so there is no frozen snapshot to
        // invalidate when leaving the day page or opening the picker.
    }
    LaunchedEffect(visualState.loaded, visualState.config.id, visualState.config.totalWeeks, homeCurrentWeek, visualState.config.autoCurrentWeek, beforeScheduleTerm) {
        if (!visualState.loaded) return@LaunchedEffect
        val currentTargetWeek = if (beforeScheduleTerm) 1 else homeCurrentWeek
        homeDisplayWeek = homeDisplayWeek.coerceIn(1, visualState.config.totalWeeks.coerceAtLeast(1))
        if (visualState.config.autoCurrentWeek) homeDisplayWeek = currentTargetWeek
    }
    val homeTitleWeek = if (homeMode != HomeMode.Week) {
        effectiveCurrentWeek(visualState.config, homeDisplayDate)
    } else {
        homeDisplayWeek
    }
    val homeTitleDate = if (homeMode != HomeMode.Week) {
        homeDisplayDate
    } else {
        todayDate
    }
    val homeReturnTargetWeek = if (beforeScheduleTerm) 1 else homeCurrentWeek
    val homeShowingAnotherWeek = homeMode == HomeMode.Week && homeDisplayWeek != homeReturnTargetWeek
    val homeCourseColorSignature = remember(visualState.config.id, visualState.courses) {
        visualState.courses
            .map(::courseCardColorKey)
            .distinct()
            .sorted()
    }
    val homeCoursePalette = remember(
        visualState.config.courseCardColorMode,
        visualState.config.cardColorArgb,
        visualState.config.courseCardPalette,
        wallpaperImages.representativeColors
    ) {
        resolvedCourseCardPalette(visualState.config, wallpaperImages.representativeColors)
    }
    val homeCourseColorAssignments = remember(
        visualState.config.id,
        visualState.config.courseCardColorMode,
        visualState.config.cardColorArgb,
        visualState.config.courseCardPalette,
        homeCourseColorSignature,
        homeCoursePalette
    ) {
        // Card movement and resizing must not recolor the whole grid.
        // The semantic color-key set changes only when the actual course identities do.
        buildCourseCardColorAssignments(
            visualState.courses,
            homeCoursePalette,
            tonalFamily = visualState.config.courseCardColorMode == CourseCardColorMode.GRADIENT
        )
    }
    val homeCaptureFrameKey = remember(
        homeBackgroundSession,
        homeReadabilityRootSize,
        density.density,
        density.fontScale,
        captureRenderToken,
        visualState.config,
        visualState.courses,
        visualState.periods,
        wallpaperImages.renderKey,
        wallpaperImages.source,
        homeWallpaperRecordKey.value,
        homeCourseColorSignature,
        homeMode,
        homeDisplayWeek,
        homeDisplayDate,
        editingCourseId,
        activeHomeAnchoredOverlay,
        addButtonHidden,
        homeMenuFromDock,
        rootPageMotion.moving,
        homeModeMotion.moving,
        sidebarContentMetrics.contentWidth,
        parallelPhoneNavigation,
        tabletAboutGradientColors
    ) {
        buildString {
            append(System.identityHashCode(homeBackgroundSession)).append('|')
            append(homeReadabilityRootSize).append('|').append(density.density).append('|')
                .append(density.fontScale).append('|')
            append(sidebarContentMetrics.contentWidth).append('|')
            append(parallelPhoneNavigation).append('|')
            append(tabletAboutGradientColors).append('|')
            append(captureRenderToken).append('|')
            append(visualState.config.hashCode()).append('|')
            append(visualState.courses.hashCode()).append('|')
            append(visualState.periods.hashCode()).append('|')
            append(wallpaperImages.renderKey).append('|')
            append(wallpaperImages.source != null).append('|')
            // The drawable becomes recordable after its source bitmap is first presented.
            // Re-capture the home scene then; the bitmap selection may be unchanged.
            append(homeWallpaperRecordKey.value?.hashCode()).append('|')
            append(homeCourseColorSignature.hashCode()).append('|')
            append(homeMode).append('|').append(homeDisplayWeek).append('|').append(homeDisplayDate)
                .append('|').append(editingCourseId)
                .append('|').append(activeHomeAnchoredOverlay)
                .append('|').append(addButtonHidden)
                .append('|').append(homeMenuFromDock)
                .append('|').append(rootPageMotion.moving).append('|').append(homeModeMotion.moving)
            // Menus are sibling consumers outside this recorder. Hiding the source menu during
            // handoff changes no Home pixels and must not recapture the scene during Opening.
        }
    }
    val useFrozenHomeMorphBlur: () -> Boolean = {
        homeBackgroundFreezeActive && !homeSidebarState.moving
    }
    val useCachedHomeSurface: () -> Boolean = {
        !homeSidebarState.moving && shouldReuseHomeSurface(
            screenIsHome = screen is Screen.Home,
            previewActive = personalizationPreviewActive,
            overlayActive = homeBackgroundOverlayActive,
            cachedScheduleId = recordedScheduleId.get(),
            currentScheduleId = visualState.config.id,
            cachedFrameKey = lastRecordedHomeFrameKey.get(),
            currentFrameKey = homeCaptureFrameKey
        )
    }
    val currentHomeCoordinatesFreeze = rememberUpdatedState(homeBackgroundFreezeActive && !homeSidebarState.moving)
    val currentHomeCaptureFrameKey = rememberUpdatedState(homeCaptureFrameKey)
    val homeGlassMotionKey = remember(rootPageMotion, homeModeMotion) {
        derivedStateOf {
            Triple(currentHomeCaptureFrameKey.value, rootPageMotion.sampleKey, homeModeMotion.sampleKey)
        }
    }
    // Observe page movement in glass draw nodes, even when their layout coordinate object is reused.
    val homeGlassSampleRecordKey = remember(homeGlassMotionKey) { { homeGlassMotionKey.value } }
    val freezeHomeGlassCoordinates = remember {
        {
            currentHomeCoordinatesFreeze.value &&
                lastRecordedHomeFrameKey.get() == currentHomeCaptureFrameKey.value
        }
    }
    val substantialHomeAnchoredCoverage =
        activeHomeAnchoredOverlay == HomeAnchoredOverlayKind.Personalize &&
            (
                homeAnchoredOverlayRequest?.kind == HomeAnchoredOverlayKind.Personalize ||
                    homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle
                )
    val substantialMenuDestinationCoverage =
        homeMenuDestinationRequest != null ||
            homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle
    val substantialCourseEditorCoverage =
        courseEditorRequest != null || courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle
    val homeBackgroundBlurClosing =
        homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Closing ||
            homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Closing ||
            courseEditorOverlayPhase == CourseEditorOverlayPhase.Closing
    val legacyHomeOverlayBackgroundBlurProgress: () -> Float = {
        val legacyDepth = homeOverlayDepthProgress(homeOverlayBackgroundZoom())
        val previewActive = personalizationSliderPreviewKey != null ||
            personalizationPreviewProgress > 0.001f
        val substantialOverlayActive = substantialCourseEditorCoverage ||
            substantialMenuDestinationCoverage || substantialHomeAnchoredCoverage
        if (!shouldUseStagedHomeOverlayBlur(previewActive, substantialOverlayActive)) {
            legacyDepth
        } else when {
            substantialCourseEditorCoverage &&
                courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle ->
                stagedHomeOverlayBlurProgress(
                    legacyDepthProgress = legacyDepth,
                    morphProgress = courseEditorMotionState.progress.value,
                    closing = courseEditorOverlayPhase == CourseEditorOverlayPhase.Closing
                )
            substantialMenuDestinationCoverage &&
                homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle ->
                stagedHomeOverlayBlurProgress(
                    legacyDepthProgress = legacyDepth,
                    morphProgress = homeMenuDestinationMotionState.progress.value,
                    closing = homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Closing
                )
            substantialHomeAnchoredCoverage &&
                homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle ->
                stagedHomeOverlayBlurProgress(
                    legacyDepthProgress = legacyDepth,
                    morphProgress = homeAnchoredMorphState.progress.value,
                    closing = homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Closing
                )
            else -> legacyDepth
        }
    }
    val landscapeWindow = isLandscapeMenuWindow()
    val homeOverlayBackgroundBlurProgress: () -> Float = {
        if (landscapeWindow && !preserveHomeOverlayMotion) {
            maxOf(
                if (courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle) courseEditorMotionState.progress.value else 0f,
                if (homeMenuDestinationMotionState.phase != HomeAnchoredOverlayPhase.Idle) homeMenuDestinationMotionState.progress.value else 0f
            )
        } else legacyHomeOverlayBackgroundBlurProgress()
    }
    LaunchedEffect(screen, homeMode, visualState.config.id, homeDisplayWeek,
        homeAdaptiveMetrics.screenWidth, homeAdaptiveMetrics.screenHeight,
        homeAnchoredOverlayRequest, pickerState.overlayVisible) {
        courseShortcuts.reset()
    }
    LaunchedEffect(screen, homeMode, visualState.config.id, homeAnchoredOverlayRequest, pickerState.overlayVisible) {
        courseCopy.reset()
    }
    LaunchedEffect(homeDisplayWeek, homeAdaptiveMetrics.screenWidth, homeAdaptiveMetrics.screenHeight) {
        if (courseCopy.busy) courseCopy.reset() else courseCopy.clearSelection()
    }
    suspend fun awaitHomeBackgroundFrame(routeEligible: Boolean) {
        if (!routeEligible || !homeBackgroundFreezeActive) return
        // The existing preparation phase supplies the clean source-hidden frame. Keep every
        // course node mounted; there is no flat-material capture or post-close rebuild.
        repeat(3) {
            if (useCachedHomeSurface() && weekHomeSurfaceUsesOffscreenCache.get()) return
            withFrameNanos { }
        }
    }

    suspend fun awaitRenderedSchedule(scheduleId: Int): Boolean {
        val generationBeforeSwitch = recordedHomeGeneration.get()
        Log.d("SchedulePicker", "await start requested=$scheduleId recorded=${recordedScheduleId.get()} generation=$generationBeforeSwitch")
        previewScheduleId = scheduleId
        captureRenderToken += 1
        snapshotFlow { latestVisualState.value.config.id }.first { it == scheduleId }
        Log.d("SchedulePicker", "visual state ready requested=$scheduleId token=$captureRenderToken")
        val target = latestAllSchedulesState.value.allConfigs.firstOrNull { it.id == scheduleId }
            ?: defaultConfig(scheduleId)
        if (target.hasAnyWallpaper()) {
            // The wallpaper loader currently exposes no terminal error state. Bound this wait
            // so an unreadable URI degrades to the same loading/fallback layer as the real home.
            withTimeoutOrNull(2_500L) {
                snapshotFlow {
                    latestVisualState.value.config.id to (latestWallpaperImages.value.source != null)
                }.first { (renderedId, wallpaperReady) -> renderedId == scheduleId && wallpaperReady }
            }
        }
        val ready = withTimeoutOrNull(900L) {
            while (
                recordedScheduleId.get() != scheduleId ||
                recordedHomeGeneration.get() == generationBeforeSwitch
            ) {
                withFrameNanos { }
            }
            // The marker is written after graphicsLayer.record completes. Cross one more
            // frame boundary so capture never races the frame that produced the marker.
            withFrameNanos { }
            true
        } ?: false
        Log.d(
            "SchedulePicker",
            "await end requested=$scheduleId ready=$ready recorded=${recordedScheduleId.get()} generation=${recordedHomeGeneration.get()}"
        )
        return ready
    }

    suspend fun captureRenderedSchedule(scheduleId: Int): Bitmap? {
        if (!awaitRenderedSchedule(scheduleId)) return null
        val bitmap = runCatching { screenGraphicsLayer.toImageBitmap().asAndroidBitmap() }
            .onFailure { Log.e("SchedulePicker", "capture failed for schedule=$scheduleId", it) }
            .getOrNull()
        Log.d("SchedulePicker", "capture result requested=$scheduleId bitmap=${bitmap?.width}x${bitmap?.height}")
        if (bitmap != null) {
            appScope.launch { ScheduleSnapshotStore.save(context, scheduleId, bitmap) }
        }
        return bitmap
    }

    fun hydratePersistedSnapshots(generation: Int) {
        cacheHydrationJob?.cancel()
        cacheHydrationJob = appScope.launch {
            val selectedId = pickerState.selectedScheduleId ?: return@launch
            val selectedIndex = pickerState.orderIds.indexOf(selectedId)
            val neighborIds = listOf(selectedIndex - 1, selectedIndex + 1)
                .mapNotNull(pickerState.orderIds::getOrNull)
            val orderedTargets = (neighborIds + pickerState.orderIds)
                .distinct()
                .filter { it != selectedId && pickerState.snapshots[it] == null }
            for (targetId in orderedTargets) {
                if (generation != snapshotGeneration || !pickerState.overlayVisible) break
                ScheduleSnapshotStore.load(context, targetId)?.let { persisted ->
                    pickerState.snapshots[targetId] = persisted
                }
            }
        }
    }

    fun prewarmCurrentScheduleSnapshot() {
        if (homeAdaptiveMetrics.isTabletLandscape) return
        entryPrewarmJob?.cancel()
        val requestedId = state.config.id
        entryPrewarmJob = appScope.launch {
            val snapshot = captureRenderedSchedule(requestedId) ?: return@launch
            if (
                pickerState.phase is CustomizeUiState.ShowingEntryButton &&
                state.config.id == requestedId
            ) {
                pickerState.currentSnapshot = snapshot
                pickerState.currentSnapshotScheduleId = requestedId
                pickerState.snapshots[requestedId] = snapshot
            }
        }
    }

    fun pickerPreviewBitmap(bitmap: Bitmap): Bitmap {
        if (!homeAdaptiveMetrics.isTabletLandscape || bitmap.width != windowContainerSize.width ||
            bitmap.height != windowContainerSize.height) return bitmap
        val left = with(density) { homeSidebarState.targetContentInset.roundToPx() }
            .coerceIn(0, bitmap.width - 1)
        val top = with(density) { homeSidebarState.targetContentTopInset.roundToPx() }
            .coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width - with(density) { homeSidebarState.endInset.roundToPx() })
            .coerceIn(left + 1, bitmap.width)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bitmap.height - top)
    }

    fun enterCustomizePage() {
        if (pickerState.phase !is CustomizeUiState.Home && pickerState.phase !is CustomizeUiState.ShowingEntryButton) return
        snapshotJob?.cancel()
        val generation = ++snapshotGeneration
        appScope.launch {
            showScheduleEntryPill = false
            pickerState.phase = CustomizeUiState.CapturingSnapshots
            val entryExitStartedAt = withFrameNanos { it }
            var frameTime: Long
            do {
                frameTime = withFrameNanos { it }
            } while (frameTime - entryExitStartedAt < 120_000_000L)
            val currentId = state.config.id
            pickerState.selectedScheduleId = currentId
            pickerState.originalScheduleId = currentId
            pickerState.orderIds.clear()
            pickerState.orderIds += currentId
            pickerState.orderIds += allSchedulesState.schedules.map { it.id }.filter { it != currentId }
            val prewarmed = pickerState.currentSnapshot
                ?.takeIf { pickerState.currentSnapshotScheduleId == currentId && !homeAdaptiveMetrics.isTabletLandscape }
            val recordedNow = if (prewarmed == null && recordedScheduleId.get() == currentId) {
                runCatching { screenGraphicsLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
            } else null
            val currentSnapshot = pickerPreviewBitmap(prewarmed
                ?: recordedNow
                ?: ScheduleSnapshotStore.load(context, currentId)
                ?: ScheduleSnapshotStore.createEmptySchedulePlaceholder(
                    context,
                    windowContainerSize.width,
                    windowContainerSize.height,
                    if (state.config.followSystemDarkMode) systemDark else state.config.darkMode
                ))
            pickerState.currentSnapshot = currentSnapshot
            pickerState.snapshots[currentId] = currentSnapshot
            pickerState.currentSnapshotScheduleId = currentId
            if (recordedNow != null) {
                appScope.launch { ScheduleSnapshotStore.save(context, currentId, recordedNow) }
            }
            pickerState.enterProgress.snapTo(0f)
            pickerState.chromeProgress.snapTo(0f)
            pickerState.pageSpacingProgress.snapTo(0f)
            pickerState.cornerProgress.snapTo(0f)
            pickerState.realHomeRevealProgress.snapTo(0f)
            pickerState.phase = CustomizeUiState.EnteringPicker
            withFrameNanos { }
            coroutineScope {
                launch { pickerState.enterProgress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch { pickerState.cornerProgress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch {
                    val titleDelayStart = withFrameNanos { it }
                    var titleFrameTime: Long
                    do {
                        titleFrameTime = withFrameNanos { it }
                    } while (titleFrameTime - titleDelayStart < 100_000_000L)
                    pickerState.chromeProgress.animateTo(1f, tween(350))
                }
                launch { pickerState.pageSpacingProgress.animateTo(1f, tween(500)) }
            }
            if (generation != snapshotGeneration) return@launch
            pickerState.phase = CustomizeUiState.Picker
            hydratePersistedSnapshots(generation)
        }
    }

    fun switchPickerSchedule(scheduleId: Int) {
        if (pickerState.phase !is CustomizeUiState.Picker || scheduleId == pickerState.selectedScheduleId) return
        snapshotJob?.cancel()
        val generation = ++snapshotGeneration
        pickerState.selectedScheduleId = scheduleId
        pickerState.snapshots[scheduleId]?.let {
            pickerState.currentSnapshot = it
            pickerState.currentSnapshotScheduleId = scheduleId
        }
        snapshotJob = appScope.launch {
            val requestedId = scheduleId
            val cached = pickerState.snapshots[requestedId]
                ?: ScheduleSnapshotStore.load(context, requestedId)
            if (cached != null && generation == snapshotGeneration && pickerState.selectedScheduleId == requestedId) {
                pickerState.snapshots[requestedId] = cached
                pickerState.currentSnapshot = cached
                pickerState.currentSnapshotScheduleId = requestedId
            }
            // Pager navigation is visual-only. Do not switch or recapture the hidden real
            // homepage here; doing so rebuilt the entire schedule on every settled swipe and
            // competed with the Pager animation for both main-thread and GPU time.
            if (generation == snapshotGeneration && pickerState.selectedScheduleId == requestedId) {
                hydratePersistedSnapshots(generation)
            }
        }
    }

    fun quickDraftFor(scheduleId: Int): QuickScheduleDraft {
        val config = latestAllSchedulesState.value.allConfigs.firstOrNull { it.id == scheduleId }
            ?: defaultConfig(scheduleId)
        val totalWeeks = config.totalWeeks.coerceIn(1, 60)
        val resolvedWeek = resolveScheduleCurrentWeek(
            config,
            totalWeeks,
            config.currentWeek,
            config.termStartDate,
            config.autoCurrentWeek
        )
        return QuickScheduleDraft(
            scheduleId = scheduleId,
            totalWeeks = totalWeeks,
            currentWeek = resolvedWeek,
            autoCurrentWeek = config.autoCurrentWeek,
            hideEmptyWeekends = config.hideEmptyWeekends,
            termStartDate = config.termStartDate.orEmpty(),
            showNonCurrentWeekCourses = config.showNonCurrentWeekCourses
        )
    }

    fun closeHomeMenuDestination() {
        // The destination owns the return to the real source button.
        destinationOwnsButtonReturn = true
        destinationCollapseHandedOff = false
        homeAnchoredOverlayRequest = null
        homeMenuDestinationRequest = null
    }

    LaunchedEffect(pendingImportedSetupId) {
        val scheduleId = pendingImportedSetupId ?: return@LaunchedEffect
        // All successful imports (new/overwrite/education Activity) leave the old entry first.
        // The assistant can otherwise keep an overwrite's home data frozen indefinitely.
        dismissHomeDialog()
        closeHomeMenuDestination()
        // Keep the source timetable unchanged until both the entry and preview really unmount.
        snapshotFlow {
            !importPreviewMounted && renderedHomeDialog == null &&
                homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Idle
        }.first { it }
        homeAssistant.reset()
        previewScheduleId = null
        if (pickerState.overlayVisible) pickerState.reset()
        screen = Screen.Home
        homeMode = HomeMode.Week
        importHomeReveal.snapTo(0f)
        importSourceState = null
        snapshotFlow {
            val latest = latestAllSchedulesState.value
            val rendered = latestVisualState.value
            !importPreviewMounted && renderedHomeDialog == null && rendered.loaded &&
                latest.schedules.any { it.id == scheduleId } &&
                latest.allConfigs.any { it.id == scheduleId } &&
                rendered.config.id == scheduleId &&
                (importExpectedCourseCount == null || (rendered.courses.size == importExpectedCourseCount &&
                    rendered.courses.none { it.id in importPreviousCourseIds }))
        }.first { it }
        withFrameNanos { }
        importHomeReveal.animateTo(1f, tween(300, easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f)))
        // Open setup only after the root preview has really gone and live data is ready.
        importSubmitting = false
        importExpectedCourseCount = null
        importPreviousCourseIds = emptySet()
        quickScheduleReturnToManager = false
        quickScheduleDraft = quickDraftFor(scheduleId)
        pendingImportedSetupId = null
    }

    fun exitPicker(
        apply: Boolean,
        targetOverride: Int? = null,
        commitTarget: Boolean = apply,
        crossfadeToTarget: Boolean = !apply,
        onFinished: (() -> Unit)? = null
    ) {
        if (pickerState.interactionsLocked) return
        snapshotJob?.cancel()
        ++snapshotGeneration
        val targetId = targetOverride
            ?: if (apply) pickerState.selectedScheduleId ?: state.config.id else state.config.id
        appScope.launch {
            // Always start from exactly what the centered card is currently showing, even when
            // Cancel is returning to a different, previously-applied schedule.
            val centeredId = pickerState.selectedScheduleId
            val cachedCardSnapshot = centeredId?.let { id ->
                pickerState.currentSnapshot
                    ?.takeIf { pickerState.currentSnapshotScheduleId == id }
                    ?: pickerState.snapshots[id]
            }
            pickerState.phase = if (commitTarget) CustomizeUiState.Applying else CustomizeUiState.ExitingPicker
            pickerState.preparingExit = true
            val cancelTargetSnapshot = if (crossfadeToTarget) {
                pickerState.snapshots[targetId] ?: ScheduleSnapshotStore.load(context, targetId)
            } else {
                null
            }
            // Prepare the actual live destination, but do not turn it into the second visual
            // layer. The cached centered card will later fade away to reveal this real home.
            awaitRenderedSchedule(targetId)
            pickerState.currentSnapshot = cachedCardSnapshot ?: pickerState.currentSnapshot
            // Cancel visually dissolves the original/applied schedule's persisted card directly
            // over the currently centered card while it grows. Apply continues to enlarge the
            // selected card and hands it directly to the live destination.
            pickerState.transitionFromSnapshot = cancelTargetSnapshot?.let(::pickerPreviewBitmap)
            pickerState.snapshotCoverBitmap = null
            if (commitTarget) {
                val activationFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
                viewModel.activateSchedule(targetId) { activationFinished.complete(Unit) }
                activationFinished.await()
                withTimeoutOrNull(1_200L) {
                    snapshotFlow { latestVisualState.value.config.id }.first { it == targetId }
                }
            }
            // Force the fully loaded destination through two additional draw/vsync boundaries.
            // The loading indicator remains visible throughout this preparation window.
            captureRenderToken += 1
            withFrameNanos { }
            withFrameNanos { }
            pickerState.preparingExit = false
            coroutineScope {
                launch { pickerState.enterProgress.animateTo(0f, tween(420, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1f))) }
                launch { pickerState.chromeProgress.animateTo(0f, tween(220)) }
                launch { pickerState.pageSpacingProgress.animateTo(0f, tween(320)) }
            }
            // Geometry is now fully screen-sized. Only now flatten the corner radius so no
            // square card edge can be exposed while the card is still floating over the home.
            pickerState.cornerProgress.animateTo(0f, tween(90))
            // The cached card is now fully screen-sized and square. Reveal the already-rendered
            // real home underneath instead of crossfading to another bitmap.
            pickerState.realHomeRevealProgress.animateTo(1f, tween(250))
            val handoffHoldStart = withFrameNanos { it }
            var handoffFrame: Long
            do {
                handoffFrame = withFrameNanos { it }
            } while (handoffFrame - handoffHoldStart < 64_000_000L)
            val temporaryToDelete = pickerState.temporaryIds.filter { !commitTarget || it != targetId }
            previewScheduleId = null
            pickerState.reset()
            temporaryToDelete.forEach(viewModel::deleteSchedule)
            withFrameNanos { }
            onFinished?.invoke()
        }
    }

    val latestPickerBackAction by rememberUpdatedState(newValue = {
        if (pickerState.phase is CustomizeUiState.Picker) {
            if (pickerState.deletingScheduleId != null) {
                pickerState.deletingScheduleId = null
                pickerState.deleteReveal = 0f
            } else {
                // System Back is deliberately identical to the visible Cancel action.
                exitPicker(apply = false)
            }
        }
    })
    BackHandler(enabled = pickerState.overlayVisible) {
        latestPickerBackAction()
    }
    var returnHomeWeekRequest by remember { mutableIntStateOf(0) }
    val returnHomeToCurrentDateAndWeek = {
        returnHomeWeekRequest++
        if (beforeScheduleTerm) {
            homeDisplayDate = parseScheduleDate(visualState.config.termStartDate) ?: LocalDate.now()
            homeDisplayWeek = 1
        } else {
            homeDisplayDate = LocalDate.now()
            homeDisplayWeek = homeCurrentWeek
        }
    }

    LaunchedEffect(state.config.hideFromRecents) {
        hideFromRecentsEnabled = state.config.hideFromRecents
    }

    var initialLifecycleStartSeen by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                PendingImportSetupStore.consume(context)?.let { pendingImportedSetupId = it }
                if (initialLifecycleStartSeen) {
                    viewModel.refreshNotifications()
                    pendingPickerEditorScheduleId?.let { scheduleId ->
                        pendingPickerEditorScheduleId = null
                        val generation = ++snapshotGeneration
                        appScope.launch {
                            pickerState.phase = CustomizeUiState.ExitingEditor
                            val snap = captureRenderedSchedule(scheduleId)
                            if (generation == snapshotGeneration && pickerState.selectedScheduleId == scheduleId) {
                                if (snap != null) {
                                    pickerState.snapshots[scheduleId] = snap
                                    pickerState.currentSnapshot = snap
                                    pickerState.currentSnapshotScheduleId = scheduleId
                                }
                                pickerState.phase = CustomizeUiState.Picker
                                hydratePersistedSnapshots(generation)
                            }
                        }
                    }
                } else {
                    initialLifecycleStartSeen = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(1800)
            viewModel.clearSnackbar()
        }
    }
    // Root wrapper — ensures splash covers all content including Scaffold internals
    @OptIn(ExperimentalSharedTransitionApi::class)
    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize()
    ) {
    val sharedTransitionScope = this
    val activeSharedTransitionScope = if (startupPhase == StartupPhase.FullQuality) sharedTransitionScope else null
    val courseBackdropViewport = remember { com.kyant.backdrop.BackdropViewport() }
    val sharedCourseFrame = courseCardGlassEffectFrame(
        tokens = GlassTokens.courseCard(visualState.config.courseCardBlur),
        liveBlur = personalizationPreviewState.cardBlur ?: visualState.config.courseCardBlur,
        quality = glassQuality,
        hasWallpaper = visualState.config.hasAnyWallpaper()
    )
    val sharedCourseRadiusPx = with(LocalDensity.current) { (sharedCourseFrame.blur ?: 0.dp).toPx() }
    val courseMaterialsEnabled = AppMaterialPreferences.policy.courseSamples(
        visualState.config.courseCardGlassEnabled, visualState.config.courseCardGaussianBlurEnabled,
        visualState.config.hasAnyWallpaper())
    val sharedCourseBackdrop = if (courseMaterialsEnabled && visualState.config.courseCardGlassEnabled)
        remember(courseWallpaperBackdrop) {
        com.kyant.backdrop.backdrops.SharedBlurBackdrop(courseWallpaperBackdrop, sharedCourseRadiusPx, sharedCourseFrame.useVibrancy)
    }.apply {
        radiusPx = sharedCourseRadiusPx
        vibrant = sharedCourseFrame.useVibrancy
    } else null
    // Retained timetable cards must retain their material source as well. Removing this on
    // Settings used to replace every card's sampler and allocate it again on the return frame.
    val sharedCourseBackdropExpected = sharedCourseBackdrop != null && visualState.config.courseCardGlassEnabled && visualState.config.hasAnyWallpaper() &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useSharedCourseBackdrop = sharedCourseBackdropExpected && wallpaperImages.source != null
    lateinit var handleHomeAgentAction: AgentActionHandler
    handleHomeAgentAction = {
        plan: AgentPlan,
        onResult: (AgentPlanExecutionResult) -> Unit ->
        val actions = plan.actions
        val action = actions.singleOrNull()
        val courseActions = actions.filter {
            it.type == AgentValidatedActionType.ADD ||
                it.type == AgentValidatedActionType.UPDATE ||
                it.type == AgentValidatedActionType.REPLACE ||
                it.type == AgentValidatedActionType.DELETE
        }
         val settingActions = actions.filter {
             it.type == AgentValidatedActionType.SET_SETTING ||
                 it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
                 it.type == AgentValidatedActionType.SET_ADJUSTMENTS
        }
        when {
            courseActions.size == actions.size && actions.isNotEmpty() ->
                viewModel.executeAgentPlan(actions, onResult)
            settingActions.isNotEmpty() && settingActions.size + courseActions.size == actions.size ->
                viewModel.executeAgentSettingPlan(actions, onResult)
            actions.isEmpty() -> onResult(AgentPlanExecutionResult(false, null, false, "没有可执行操作"))
            action == null -> {
                // Keep dependent operations ordered. Adjacent course/settings steps share one
                // transaction; navigation/context changes occur only after that batch succeeds.
                val batch = actions.takeWhile { it in courseActions || it in settingActions }
                    .ifEmpty { listOf(actions.first()) }
                handleHomeAgentAction(AgentPlan(batch)) { firstResult ->
                    if (!firstResult.success || !firstResult.verified) onResult(firstResult)
                    else handleHomeAgentAction(AgentPlan(actions.drop(batch.size))) { remaining ->
                        onResult(remaining.copy(verified = firstResult.verified && remaining.verified,
                            message = firstResult.message + "\n" + remaining.message, undo = null))
                    }
                }
            }
            else -> when (action.type) {
            AgentValidatedActionType.ADD,
            AgentValidatedActionType.UPDATE,
            AgentValidatedActionType.REPLACE,
            AgentValidatedActionType.DELETE ->
                viewModel.executeAgentPlan(actions, onResult)
            AgentValidatedActionType.OPEN_SETTINGS -> viewModel.openAgentPage(onResult) { current ->
                if (action.settingsPage == "SCHEDULE_ADJUSTMENTS") {
                    context.openRegisteredActivity(TransitionRouteId.SettingsToScheduleAdjustments,
                        ScheduleAdjustmentsActivity.intent(context, current.config, current.config.scheduleAdjustmentsJson),
                        launchActivity = { holidayAdjustmentsLauncher.launch(it) })
                } else if (action.settingsPage == "PERSONALIZATION") {
                    openHomeAnchoredOverlay(HomeAnchoredOverlayKind.Personalize)
                } else agentSettingsPage(action.settingsPage)?.let { page ->
                    val intent = Intent(context, SettingsDetailActivity::class.java)
                        .putExtra(SettingsDetailPageExtra, page.name)
                    if (page == SettingsPage.Schedule) {
                        intent.putExtra(ScheduleCustomizeIdExtra, current.config.id)
                    }
                    context.openRegisteredActivity(
                        TransitionRouteId.HomeToSettingsDetail,
                        intent
                    )
                }
            }
            AgentValidatedActionType.OPEN_IMPORT -> {
                assistantImportText = action.importText
                assistantImportUri = action.importAttachmentUri?.let(Uri::parse)
                homeDialog = HomeDialog.ImportSchedule
                onResult(
                    AgentPlanExecutionResult(
                        success = true,
                        preview = null,
                        verified = true,
                        message = "已打开 AI 导入"
                    )
                )
            }
            AgentValidatedActionType.SET_SETTING,
            AgentValidatedActionType.SET_PERIOD_SETTINGS,
            AgentValidatedActionType.SET_ADJUSTMENTS ->
                viewModel.executeAgentSettingPlan(actions, onResult)
            AgentValidatedActionType.CREATE_SCHEDULE ->
                viewModel.createScheduleFromAgent(action.scheduleName.orEmpty(), onResult)
            AgentValidatedActionType.ACTIVATE_SCHEDULE ->
                action.scheduleId?.let { viewModel.activateScheduleFromAgent(it, onResult) }
                    ?: onResult(
                        AgentPlanExecutionResult(false, null, false, "目标课表不存在，请重新读取课表列表")
                    )
            AgentValidatedActionType.DELETE_SCHEDULE ->
                action.scheduleId?.let { viewModel.deleteScheduleFromAgent(it, onResult) }
                    ?: onResult(
                        AgentPlanExecutionResult(false, null, false, "目标课表不存在，请重新读取课表列表")
                    )
        }
        }
    }
    // Local function references capture the current host inputs. Keep the provided callback
    // itself stable: every course card consumes it, including ordinary cards, and a tab change
    // must not invalidate that whole subtree just to keep its eventual click up to date.
    val currentAdjustedCourseEditor = rememberUpdatedState<(Long, LocalDate, Rect?) -> Unit>(::openAdjustedCourseEditor)
    val adjustedCourseEditor = remember {
        { courseId: Long, date: LocalDate, bounds: Rect? ->
            currentAdjustedCourseEditor.value(courseId, date, bounds)
        }
    }
    val currentHomeCourseClick = rememberUpdatedState<(CourseEntity, Int, Rect?) -> Unit>({ course, week, bounds ->
        openCourseEditor(course, week, bounds)
    })
    val homeCourseClick = remember {
        { course: CourseEntity, week: Int, bounds: Rect? -> currentHomeCourseClick.value(course, week, bounds) }
    }
    // Keep callback identity independent of overlay state. The latest guards and navigation
    // inputs are read only when the user acts, not while a shortcut animates its background.
    val currentDockSelect = rememberUpdatedState<(Int) -> Unit> { index ->
        if (index == 2) {
            sidebarPage = HomeSidebarDestination.Settings
            screen = Screen.Config
        } else requestSettingsExit {
            homeMode = if (index == 0) HomeMode.Day else HomeMode.Week
            screen = Screen.Home
        }
    }
    val dockSelect = remember { { index: Int -> currentDockSelect.value(index) } }
    val currentOrdinaryDockHome = rememberUpdatedState<() -> Unit> {
        requestSettingsExit { screen = Screen.Home }
    }
    val ordinaryDockHome = remember { { currentOrdinaryDockHome.value() } }
    val currentDockOpenMenu = rememberUpdatedState<(Rect, GraphicsLayer) -> Unit> { bounds, returnButtonLayer ->
        if (!homeBackgroundOverlayActive && !homeDialogVisible) {
            dockImportReturnLayer = returnButtonLayer
            dockImportButtonBounds = bounds
            homeMenuFromDock = true
            homeAddMenuBoundsInRoot = null
            homeAnchoredOverlayRequest = HomeAnchoredOverlayRequest(
                HomeAnchoredOverlayKind.Add, bounds, fromDock = true
            )
        }
    }
    val dockOpenMenu = remember {
        { bounds: Rect, layer: GraphicsLayer -> currentDockOpenMenu.value(bounds, layer) }
    }
    CompositionLocalProvider(
        LocalHomeAssistant provides homeAssistant,
        com.xiaomanjun.sleepdownschedule.core.ui.interaction.LocalPullGestureExclusions provides homeAssistant.pullExclusions,
        LocalDayAgentCountdownCinematic provides dayAgentCountdownCinematic,
        LocalAdjustedCourseEditor provides adjustedCourseEditor,
        LocalCourseShortcuts provides courseShortcuts,
        LocalCourseCopy provides courseCopy,
        LocalCourseRemoval provides courseRemoval,
        com.xiaomanjun.sleepdownschedule.glass.LocalSharedCourseBackdrop provides
            sharedCourseBackdrop.takeIf { sharedCourseBackdropExpected },
        com.xiaomanjun.sleepdownschedule.glass.LocalCourseBackdropViewport provides courseBackdropViewport,
        LocalSharedTransitionScope provides activeSharedTransitionScope,
        LocalEditingCourseId provides editingCourseId,
        LocalCourseEditorFlightRegistry provides courseEditorFlightRegistry,
        LocalStartupPhase provides startupPhase,
        LocalGlassQuality provides glassQuality,
        LocalAdaptiveGlass provides adaptiveGlassState,
        com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeDockAccent provides colorOsActiveDockAccent,
        com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeButtonAccent provides colorOsHomeAccents?.button,
        com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle provides
            homeNeutralChromeStyle.takeIf { screen is Screen.Home },
        com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenu provides
            colorOsOverflowActions.takeIf { parallelPhoneNavigation },
        LocalHomeReadability provides homeReadabilityContext,
        LocalCourseCardPalette provides homeCoursePalette,
        LocalCourseCardColorAssignments provides homeCourseColorAssignments,
        LocalGlassSceneState provides glassSceneState,
        LocalCenteredDialogSceneBackdrop provides centeredDialogSceneBackdrop
    ) {
    // Home now owns an explicit Miuix root scaffold, matching the already-fixed secondary-page
    // topology. Its complete business stack is the underlay; Miuix renders every second-level
    // popup in the scaffold's later sibling host, so a dialog consumer can never be recorded by
    // the LayerBackdrop it samples.
    top.yukonga.miuix.kmp.basic.Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Page gestures must not be ancestors of the Miuix popup host: an Initial-pass
        // pull recognizer there can take a dialog slider's drag before it reaches the slider.
        underlayModifier = Modifier.fillMaxSize().assistantPullGesture(
            enabled = screen is Screen.Home && homeMode == HomeMode.Week && state.loaded && !homeAssistant.visible,
            canStart = { position ->
                position.y > with(density) { homeAdaptiveMetrics.safeTop.toPx() } &&
                    homeAssistant.canPullAt(position) &&
                    !homeContentUnderTopBar && !homeAssistant.editing && !homeBackgroundOverlayActive && !courseCopy.active &&
                    renderedHomeDialog == null && !jumpWeekDialogMounted &&
                    pickerState.phase is CustomizeUiState.Home &&
                    dayAgentBackgroundMotionState.progress.value < 0.001f &&
                    DayAgentPreferences.isWeekAssistantEnabled(context) &&
                    (!DayAgentPreferences.hasDecision(context) || DayAgentPreferences.isEnabled(context))
            },
            onStart = { assistantHapticSent = false; homeAssistant.beginPull() },
            onDistance = {
                if (homeAssistant.pull(it, density.density) && !assistantHapticSent) {
                    assistantHapticSent = true
                    assistantHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onRelease = { homeAssistant.release(it && homeAssistant.armed) }
        ).then(if (courseShortcuts.request != null || courseCopy.active || homeAssistant.visible) {
                Modifier.glassBackdropProducer(centeredDialogSceneBackdrop)
            } else {
                Modifier.centeredDialogSceneProducer(centeredDialogSceneBackdrop)
            }),
        popupHost = {
            Box(Modifier.fillMaxSize()) {
                HomeAssistantHost(
                    controller = homeAssistant,
                    state = state,
                    available = screen is Screen.Home && homeMode == HomeMode.Week && state.loaded,
                    remindersAllowed = homeMode == HomeMode.Week && !homeAssistant.editing &&
                        !homeBackgroundOverlayActive && !courseCopy.active && renderedHomeDialog == null &&
                        !jumpWeekDialogMounted && pickerState.phase is CustomizeUiState.Home &&
                        dayAgentBackgroundMotionState.progress.value < 0.001f,
                    backdrop = centeredDialogSceneBackdrop,
                    backgroundMotion = dayAgentBackgroundMotionState,
                    onAgentAction = handleHomeAgentAction,
                    onImportFile = { uri -> assistantImportUri = uri; homeDialog = HomeDialog.ImportSchedule }
                )
                CourseCopyOverlay(courseCopy, visualState.config, centeredDialogSceneBackdrop, backgroundBackdrop)
                CourseShortcutOverlay(
                    controller = courseShortcuts,
                    config = visualState.config,
                    backdrop = centeredDialogSceneBackdrop,
                    cardBackdrop = backgroundBackdrop,
                    onEdit = { source -> openCourseEditor(source.course, source.week, source.bounds) },
                    onCopy = { source, draft ->
                        courseCopy.begin(source, draft)
                    },
                    onRemove = { course, week ->
                        pendingCourseGroupDelete = emptyList()
                        homeDialog = HomeDialog.ApplyCourseDelete(course, week)
                    }
                )
                // Overlay content is subcomposed by the host, not at its call site. Preserve the
                // same Miuix/Glass CompositionLocals that wrapped the 1.1.5 host; otherwise sheet
                // rows fall back to the taller non-Miuix implementation and their dividers become
                // visible. The host remains a sibling of the backdrop producer, so this does not
                // reintroduce the former producer/consumer recursion.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            if (detailPopupCaptureActive) {
                                val requestedToken = detailPopupCaptureToken
                                if (detailPopupCapturedToken.get() != requestedToken) {
                                    detailPopupGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    detailPopupCapturedToken.set(requestedToken)
                                }
                                drawLayer(detailPopupGraphicsLayer)
                            } else {
                                drawContent()
                            }
                        }
                ) {
                    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
                        MiuixPopupHost()
                        // A fresh, local sheet host is a sibling of every page producer.
                        QuickScheduleSettingsSheets(
                            draft = quickScheduleDraft,
                            config = state.config,
                            // The home/chrome producers are siblings below the sheet, so this remains a real
                            // liquid backdrop without ever recording the dialog that consumes it. In particular,
                            // do not restore the former root-level quickSheetBackdrop: it caused the native
                            // RenderThread recursion when the new-schedule sheet opened after Picker exit.
                            backdrop = if (pickerState.overlayVisible) pickerSceneBackdrop else chromeBackdrop,
                            onDraftChange = { quickScheduleDraft = it },
                            onDismiss = { quickScheduleDraft = null },
                            onDismissFinished = {
                                // Direct customization leaves the manager below the sheet. New-schedule setup
                                // still returns through the existing home-to-picker Morph after its sheet closes.
                                if (quickScheduleReturnToManager && !pickerState.overlayVisible) {
                                    quickScheduleReturnToManager = false
                                    pickerState.phase = CustomizeUiState.Home
                                    enterCustomizePage()
                                }
                            },
                            onSave = { draft, onSaved ->
                                val latest = latestAllSchedulesState.value
                                val baseConfig = latest.allConfigs.firstOrNull { it.id == draft.scheduleId }
                                    ?: return@QuickScheduleSettingsSheets
                                val totalWeeks = draft.totalWeeks.coerceIn(1, 60)
                                val manualWeek = draft.currentWeek.coerceIn(1, totalWeeks)
                                val datedConfig = baseConfig.copy(
                                    totalWeeks = totalWeeks,
                                    currentWeek = manualWeek,
                                    termStartDate = draft.termStartDate.ifBlank { null },
                                    autoCurrentWeek = draft.autoCurrentWeek,
                                    hideEmptyWeekends = draft.hideEmptyWeekends,
                                    showNonCurrentWeekCourses = draft.showNonCurrentWeekCourses
                                )
                                val periods = latest.allPeriods.filter { it.scheduleId == draft.scheduleId }
                                    .ifEmpty { defaultPeriods(draft.scheduleId) }
                                viewModel.saveConfigForSchedule(
                                    draft.scheduleId,
                                    // The automatic display week is derived at runtime. Persist the
                                    // selected fallback instead of writing a clamped pre-term week 1.
                                    datedConfig.copy(currentWeek = manualWeek),
                                    periods,
                                    onSaved
                                )
                            },
                            suppressDetailedButton = detailMorphState !is DetailMorphState.Idle,
                            onDetailedSettings = { scheduleId, resolveSourceBounds, sourceWindow, saveBeforeOpening ->
                                if (homeAdaptiveMetrics.isTabletLandscape) {
                                    saveBeforeOpening {
                                        appScope.launch {
                                            context.openRegisteredActivity(
                                                TransitionRouteId.QuickSheetToSettingsDetail,
                                                Intent(context, QuickSheetSettingsDetailActivity::class.java)
                                                    .putExtra(SettingsDetailPageExtra, SettingsPage.Schedule.name)
                                                    .putExtra(ScheduleCustomizeIdExtra, scheduleId)
                                            )
                                        }
                                    }
                                    return@QuickScheduleSettingsSheets
                                }
                                // The detailed page is the real cross-activity SettingsDetailActivity opened
                                // through the shared transition framework (QuickSheetToSettingsDetail route).
                                if (detailMorphState !is DetailMorphState.Idle) {
                                    return@QuickScheduleSettingsSheets
                                }
                                val activity = context.findActivity() ?: return@QuickScheduleSettingsSheets
                                val detailInputNanos = System.nanoTime()
                                appScope.launch {
                                    suspend fun capturePopupFrame(): Bitmap? {
                                        detailPopupCaptureActive = true
                                        val requestedToken = detailPopupCaptureToken + 1
                                        detailPopupCaptureToken = requestedToken
                                        var waitedFrames = 0
                                        while (
                                            detailPopupCapturedToken.get() != requestedToken &&
                                            waitedFrames < 4
                                        ) {
                                            withFrameNanos { }
                                            waitedFrames += 1
                                        }
                                        if (detailPopupCapturedToken.get() != requestedToken) return null
                                        return runCatching {
                                            detailPopupGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                        }.getOrNull()
                                    }

                                    // Capture after the dialog has finished its IME resize. A layout-time
                                    // rectangle is stale when WindowManager repositions the floating window.
                                    val inputWindow = sourceWindow ?: activity.window
                                    inputWindow.currentFocus?.clearFocus()
                                    androidx.core.view.WindowCompat.getInsetsController(inputWindow, inputWindow.decorView)
                                        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    kotlinx.coroutines.withTimeoutOrNull(750L) {
                                        while (androidx.core.view.ViewCompat.getRootWindowInsets(inputWindow.decorView)
                                                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true) withFrameNanos { }
                                    }
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    val sourceBoundsInWindow = resolveSourceBounds()

                                    // Let the released press state reach the source window before copying its
                                    // final compositor output. This is the authoritative path for the custom
                                    // schedule picker, whose nested RenderNodes cannot be flattened reliably by
                                    // recording only the outer Compose layer.
                                    withFrameNanos { }
                                    val windowSnapshot = captureDetailMorphWindowSnapshot(
                                        activity = activity,
                                        rootPositionInWindow = homeRootPositionInWindow,
                                        rootSize = homeReadabilityRootSize,
                                        overlayWindow = sourceWindow
                                    )
                                    val layeredSnapshot = if (windowSnapshot == null && sourceWindow == null) {
                                        val sourceUnderlaySnapshot = runCatching {
                                            detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                        }.getOrNull()
                                        val sourcePopupSnapshot = try {
                                            capturePopupFrame()
                                        } finally {
                                            detailPopupCaptureActive = false
                                        }
                                        if (sourceUnderlaySnapshot != null && sourcePopupSnapshot != null) {
                                            composeDetailMorphSnapshot(sourceUnderlaySnapshot, sourcePopupSnapshot)
                                        } else {
                                            null
                                        }
                                    } else {
                                        null
                                    }
                                    val fullSnapshot = windowSnapshot?.bitmap ?: layeredSnapshot
                                    // This source is not a normal page: the home underlay and QuickSheet are two
                                    // sibling draw layers in the root Miuix Scaffold. Preserve their already
                                    // composed frame for the destination-side depth/blur renderer; cross-window
                                    // blur cannot reconstruct that Compose backdrop topology on ColorOS.
                                    val snapshotOriginInWindow = windowSnapshot?.originInWindow
                                        ?: homeRootPositionInWindow
                                    val sourceBoundsInSnapshot = sourceBoundsInWindow?.translate(-snapshotOriginInWindow)
                                    val buttonSnapshot = sourceBoundsInSnapshot?.let { bounds ->
                                        windowSnapshot?.dialog?.let { layer ->
                                            layer.foreground.cropToAnchoredBounds(bounds.translate(-layer.windowBounds.topLeft))
                                        } ?: fullSnapshot?.cropToAnchoredBounds(bounds)
                                    }
                                    com.xiaomanjun.sleepdownschedule.glass.GlassBackendTrace.durationSince(
                                        "Detail.InputThroughCapture", detailInputNanos
                                    )
                                    val openingAnchor = if (sourceBoundsInSnapshot == null ||
                                        (sourceWindow != null && fullSnapshot == null)) null else TransitionAnchorFrame(
                                        boundsInWindow = sourceBoundsInSnapshot,
                                        cornerRadiusPx = with(density) { 25.dp.toPx() },
                                        bitmap = buttonSnapshot
                                    )
                                    // The destination must not race the draft write. Keeping the real button in
                                    // place while saving gives the translucent destination an unchanged live
                                    // underlay and ensures the first detailed-settings composition sees the
                                    // committed values.
                                    val detailSaveNanos = System.nanoTime()
                                    saveBeforeOpening {
                                        com.xiaomanjun.sleepdownschedule.glass.GlassBackendTrace.durationSince(
                                            "Detail.Save", detailSaveNanos
                                        )
                                        appScope.launch {
                                            com.xiaomanjun.sleepdownschedule.glass.GlassBackendTrace.durationSince(
                                                "Detail.InputToOpenDispatch", detailInputNanos
                                            )
                                            ActivityTransitionCoordinator.open(
                                                activity = activity,
                                                routeId = TransitionRouteId.QuickSheetToSettingsDetail,
                                                intent = Intent(activity, QuickSheetSettingsDetailActivity::class.java)
                                                    .putExtra(SettingsDetailPageExtra, SettingsPage.Schedule.name)
                                                    .putExtra(ScheduleCustomizeIdExtra, scheduleId),
                                                payload = TransitionPayload(
                                                    openingAnchor = openingAnchor,
                                                    returnAnchorProvider = StaticTransitionAnchorProvider(openingAnchor),
                                                    backgroundBitmap = fullSnapshot,
                                                    backgroundDialog = windowSnapshot?.dialog
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Detailed settings deliberately overlays the still-mounted quick sheet, matching
                // the 1.2.0 handoff. Keeping it after the Miuix host prevents the retained sheet
                // from covering the destination without closing/reopening either surface.
                detailCaptureCoverBitmap?.let { cover ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(259f)
                            .drawWithContent {
                                if (!detailCaptureMaskActive.get()) drawContent()
                            }
                            .pointerInput(cover) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = cover.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                detailMorphRequest?.let { request ->
                    val detailState = allSchedulesState.forSchedule(request.scheduleId)
                    DetailScheduleMorphOverlay(
                        request = request,
                        detailState = detailState,
                        onMorphStateChange = { detailMorphState = it },
                        onSave = { _, _ -> viewModel.refreshNotificationsAfterSave() },
                        onPreviewLiveUpdate = viewModel::previewLiveUpdate,
                        onFinished = {
                            detailMorphRequest = null
                            detailCaptureCoverBitmap = null
                            detailCaptureRecordCleanFrame = false
                            detailPopupCaptureActive = false
                            detailMorphState = DetailMorphState.Idle
                        },
                        modifier = Modifier.zIndex(260f)
                    )
                }
                HomeCountdownCinematicOverlay(
                    effect = dayAgentCountdownCinematic,
                    state = agentVisualState,
                    available = screen is Screen.Home && visualState.loaded &&
                        !homeAssistant.visible && !homeBackgroundOverlayActive &&
                        !courseCopy.active && renderedHomeDialog == null &&
                        !jumpWeekDialogMounted && detailMorphRequest == null &&
                        pickerState.phase is CustomizeUiState.Home,
                    fromDayCard = homeMode == HomeMode.Day &&
                        homeDisplayDate == todayDate && dayAgentPagerSettled,
                    modifier = Modifier.fillMaxSize().zIndex(300f)
                )
            }
        },
        containerColor = ComposeColor.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned {
                homeReadabilityRootSize = it.size
                homeRootPositionOnScreen = it.positionOnScreen()
                homeRootPositionInWindow = it.positionInWindow()
            }
            .drawWithContent {
                val recordCleanFrame =
                    detailMorphState is DetailMorphState.Capturing && detailCaptureRecordCleanFrame
                val courseEditorOwnsFrame =
                    courseEditorRequest != null ||
                        courseEditorOverlayPhase != CourseEditorOverlayPhase.Idle
                // Never record the course editor into the detail-page capture layer. It contains
                // several backdrop consumers and recording that entire overlay on every animation
                // frame causes an explosive offscreen-render workload after the first use.
                // HomeAnchored/HomeMenuDestination own similarly heavy live backdrop consumers;
                // pause this unrelated capture while they move. Their settled Open frame remains
                // available to flows such as manual-import history detail background capture.
                // Keep the detailed-settings source frame live while the quick sheet animates.
                // The detailed-settings handoff needs consecutive fully composed source and
                // clean-background frames, as in the 1.1.5 implementation.
                val shouldRecordStableDetailFrame =
                    detailMorphState is DetailMorphState.Idle &&
                        !rootPageMotion.moving && !homeModeMotion.moving &&
                        !courseEditorOwnsFrame &&
                        (
                            homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Open ||
                                (homeAnchoredOverlayRequest == null &&
                                    homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Idle)
                            ) &&
                        (
                            homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Open ||
                                (homeMenuDestinationRequest == null &&
                                    homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Idle)
                            )
                if (shouldRecordStableDetailFrame || recordCleanFrame) {
                    detailCaptureMaskActive.set(recordCleanFrame)
                    try {
                        detailScreenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                    } finally {
                        detailCaptureMaskActive.set(false)
                    }
                    if (!recordCleanFrame) {
                        // Recording already traversed this same visible frame. Present that
                        // display list instead of drawing every backdrop consumer a second time.
                        // Masked transition captures still need their separate unmasked draw.
                        drawLayer(detailScreenGraphicsLayer)
                        return@drawWithContent
                    }
                }
                drawContent()
            }
    ) {
        val nativeHomeDockAvailable = nativeVisuals
        val dockVisible = !homeAdaptiveMetrics.isLargeScreen && (screen is Screen.Home || screen is Screen.Config)
        val dockSelectedIndex = if (screen is Screen.Config) 2 else if (homeMode == HomeMode.Day) 0 else 1
        val dockConfig = if (screen is Screen.Home) visualState.config else state.config
        val dockButtonHidden = homeMenuFromDock && addButtonHidden
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    translationY = if (homeAssistant.pullingHome) homeAssistant.pullPixels else 0f
                    shape = RoundedRectangle(homeDeviceCornerPx.toDp())
                    clip = translationY != 0f
                    val zoom = dayAgentBackgroundMotionState.backgroundZoom.value
                    val depthProgress =
                        ((1f - zoom) / (1f - DayAgentBackgroundZoomRestScale)).coerceIn(0f, 1f)
                    scaleX = zoom
                    scaleY = zoom
                    val blurPx = if (homeAssistant.visible) 0f else with(density) { 12.dp.toPx() } * depthProgress
                    renderEffect = if (blurPx > 0.001f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
        ) {
        // The live home surface owns shared depth for source-anchored overlays. Consumers render
        // outside this layer and compensate through rememberScreenScaledBackdrop, keeping the
        // liquid sampling coordinates aligned without blurring the foreground panel itself.
        HomeBackgroundZoomLayer(
            zoom = homeOverlayBackgroundZoom,
            dimProgress = { maxOf(homeOverlayBackgroundBlurProgress(), courseShortcuts.progress.value) },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        HomeBackgroundBlurLayer(
            blurProgress = { maxOf(homeOverlayBackgroundBlurProgress(), courseShortcuts.progress.value) },
            useFrozenHomeScene = useFrozenHomeMorphBlur,
            closing = { homeBackgroundBlurClosing || courseShortcuts.closing },
            sceneKey = homeCaptureFrameKey,
            modifier = Modifier.fillMaxSize()
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val freeze = useFrozenHomeMorphBlur()
                    // A moving pair of pages is not a stable overlay source. Recording it on
                    // the switch's first frame adds a full tree traversal to page construction.
                    val needsCapture = !rootPageMotion.moving && !homeModeMotion.moving && !homeSidebarState.moving &&
                        lastRecordedHomeFrameKey.get() != homeCaptureFrameKey
                    screenGraphicsLayer.alpha = 1f
                    if (needsCapture) {
                        screenGraphicsLayer.record { this@drawWithContent.drawContent() }
                        recordedScheduleId.set(visualState.config.id)
                        recordedHomeGeneration.incrementAndGet()
                        lastRecordedHomeFrameKey.set(homeCaptureFrameKey)
                    }
                    if (freeze) {
                        if (weekHomeSurfaceUsesOffscreenCache.compareAndSet(false, true)) {
                            screenGraphicsLayer.compositingStrategy =
                                androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        }
                        // Replay the neutral scene. Zoom and blur belong to the two outer
                        // layers; the retained course tree is neither drawn nor re-recorded.
                        drawLayer(screenGraphicsLayer)
                    } else {
                        if (weekHomeSurfaceUsesOffscreenCache.compareAndSet(true, false)) {
                            screenGraphicsLayer.compositingStrategy =
                                androidx.compose.ui.graphics.layer.CompositingStrategy.Auto
                        }
                        // A changed model/window has already been drawn by record above. Replay
                        // that frame once instead of traversing the home tree twice on resume.
                        if (needsCapture) drawLayer(screenGraphicsLayer) else drawContent()
                    }
                }
        ) {
        CompositionLocalProvider(
            LocalHomeAdaptiveMetrics provides sidebarContentMetrics,
            LocalHomePagerStartOverflow provides homeSidebarState.targetContentInset,
            LocalSettingsSharedGradient provides tabletAboutGradient,
            LocalHomeBackgroundFrozen provides rememberUpdatedState(homeBackgroundFreezeActive),
            LocalHomeTextContrastFrozen provides rememberUpdatedState(rootPageMotion.moving || homeModeMotion.moving || homeSidebarState.moving),
            com.xiaomanjun.sleepdownschedule.glass.LocalGlassCoordinatesFrozen provides
                freezeHomeGlassCoordinates,
            com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey provides homeGlassSampleRecordKey
        ) {
        Scaffold(
            containerColor = ComposeColor.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                HomeSwitchPane(rootPageMotion, secondary = false,
                    modifier = Modifier.fillMaxWidth().homeSidebarContentInset(homeSidebarState),
                    pageClip = HomeSwitchClip.TopBar, retainContent = true) {
                TopBarEntranceContainer(
                    phase = startupPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            rootTopBarLayoutHeight(
                                Screen.Home,
                                homeMode == HomeMode.Week && weekViewStyle == WeekViewStyle.BOUNDLESS
                            )
                        )
                ) {
                    // The pane suppresses hidden drawing and input. Keep the chrome's text
                    // layouts and glass nodes alive, just like the retained course pages.
                    run {
                        AnimatedVisibility(
                            visible = homeContentUnderTopBar,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f),
                            enter = fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)),
                            exit = fadeOut(animationSpec = spring(dampingRatio = 0.95f, stiffness = 560f))
                        ) {
                            HomeTopGradientBlur(
                                config = visualState.config,
                                backdrop = chromeBackdrop,
                                height = if (
                                    homeMode == HomeMode.Week &&
                                    weekViewStyle == WeekViewStyle.BOUNDLESS
                                ) {
                                    boundlessHomeTopGradientHeight(homeAdaptiveMetrics)
                                } else if (homeMode == HomeMode.Week) {
                                    // 普通周视图用独立的顶栏模糊：不包含无界模式表头那段高度
                                    (homeAdaptiveMetrics.topOverlayHeight -
                                        BoundlessWeekHeaderRowHeight).coerceAtLeast(110.dp)
                                } else {
                                    homeAdaptiveMetrics.topOverlayHeight
                                },
                                fullBlurHeight = if (homeMode == HomeMode.Week && weekViewStyle == WeekViewStyle.BOUNDLESS) {
                                    homeAdaptiveMetrics.safeTop + 66.dp + BoundlessWeekHeaderRowHeight + 4.dp
                                } else 0.dp,
                                modifier = Modifier
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(11f)
                            .homeCountdownShockwave(0.75f)
                    ) {
                        CompositionLocalProvider(LocalToolbarBackdropBlurred provides homeContentUnderTopBar) {
                        AppTopBar(
                            screen = Screen.Home,
                            state = visualState,
                            settingsPage = SettingsPage.Root,
                            backdrop = chromeBackdrop,
                            homeMode = homeMode,
                            onHomeModeChange = { homeMode = it },
                            homeDisplayDate = homeDisplayDate,
                            homeDisplayWeek = if (weekViewStyle == WeekViewStyle.BOUNDLESS) {
                                homeWeekHeaderPreview.value ?: homeDisplayWeek
                            } else homeDisplayWeek,
                            beforeScheduleTerm = beforeScheduleTerm,
                            afterScheduleTerm = afterScheduleTerm,
                            homeShowingAnotherWeek = homeShowingAnotherWeek,
                            onReturnHomeToCurrentWeek = returnHomeToCurrentDateAndWeek,
                            activeHomeOverlay = activeHomeAnchoredOverlay,
                            addButtonHidden = !homeMenuFromDock && addButtonHidden,
                            onAddButtonPositioned = { addButtonBounds = it },
                            onPersonalizeButtonPositioned = { personalizeButtonBounds = it },
                            onToggleAddMenu = { sourceScale ->
                                toggleHomeAnchoredOverlay(HomeAnchoredOverlayKind.Add, sourceScale)
                            },
                            onTogglePersonalize = { sourceScale ->
                                toggleHomeAnchoredOverlay(HomeAnchoredOverlayKind.Personalize, sourceScale)
                            },
                            onBackHome = { screen = Screen.Home }
                        )
                        }
                    }
                    if (weekViewStyle == WeekViewStyle.BOUNDLESS) {
                        // Boundless week header lives on the top bar layer (above the gradient
                        // blur) so weekday labels are never covered; geometry mirrors the course
                        // grid (rowHeaderWidth slot + equal columns + weekGridEndPadding).
                        HomeSwitchPane(
                            motion = homeModeMotion,
                            secondary = true,
                            retainContent = true,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(12f)
                                .fillMaxWidth()
                                .padding(top = homeAdaptiveMetrics.safeTop + 66.dp)
                                .padding(
                                    start = if (homeAdaptiveMetrics.isLargeScreen) homeAdaptiveMetrics.tabletContentMargin else 0.dp,
                                    end = if (homeAdaptiveMetrics.isLargeScreen) homeAdaptiveMetrics.tabletContentMargin else 0.dp
                                )
                                .homeCountdownShockwave(0.70f)
                        ) {
                        BoundlessWeekdayHeaderRow(
                            displayWeek = homeWeekHeaderPreview.value ?: homeDisplayWeek,
                            courses = visualState.courses,
                            config = visualState.config,
                            today = todayDate,
                            textColor = homeForegroundColor(visualState.config),
                            backdrop = chromeBackdrop,
                            rowHeaderWidth = BoundlessWeekRowHeaderWidth,
                            weekGridEndPadding = if (homeAdaptiveMetrics.isLargeScreen) {
                                0.dp
                            } else {
                                BoundlessWeekGridEndPadding
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        }
                    }
                }
                }
            }
        ) { padding ->
            val transitionBackground = settingsPageBackground(settingsVisualConfig(state.config))
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .glassBackdropProducer(backgroundBackdrop, recordKey = {
                            if (rootPageMotion.retains(false) && visualState.loaded && wallpaperImages.source != null) {
                                homeWallpaperRecordKey.value?.let { imageKey ->
                                    listOf(imageKey, personalizationPreviewState.wallpaperBrightness
                                        ?: visualState.config.wallpaperBrightness, rootPageMotion.pageSampleKey,
                                        transitionBackground)
                                }
                            } else null
                        })
                        .background(transitionBackground)
                ) {
                    run {
                        Box(Modifier.fillMaxSize()
                            .drawWithContent { if (rootPageMotion.retains(false)) drawContent() }
                            .homeSwitchLayer(rootPageMotion, secondary = false, pageClip = HomeSwitchClip.Page)
                            .glassBackdropProducer(courseWallpaperBackdrop, enabled = courseMaterialsEnabled, recordKey = {
                                homeWallpaperRecordKey.value?.let { imageKey ->
                                    imageKey to (personalizationPreviewState.wallpaperBrightness
                                        ?: visualState.config.wallpaperBrightness)
                                }
                            })) {
                        if (!visualState.loaded) {
                            HomeBackdropFallback(
                                noWallpaper = !visualState.config.hasAnyWallpaper()
                            )
                        } else {
                            if (wallpaperImages.source != null) {
                                HomeWallpaper(
                                    visualState.config,
                                    wallpaperImages,
                                    startupPhase,
                                    previewState = personalizationPreviewState,
                                    onRecordKeyChanged = updateHomeWallpaperRecordKey
                                )
                                WallpaperGlassSamplingToneOverlay(
                                    visualState.config,
                                    personalizationPreviewState
                                )
                            } else {
                                HomeBackdropFallback(noWallpaper = noWallpaperResolved)
                            }
                        }
                        }
                    }
                    if (rootPageMotion.retains(true)) {
                        Box(Modifier.fillMaxSize()
                            .homeSwitchLayer(rootPageMotion, secondary = true, pageClip = HomeSwitchClip.Page)
                            .background(tabletAboutGradientColors?.takeIf {
                                homeAdaptiveMetrics.isLargeScreen && sidebarPage == HomeSidebarDestination.Settings
                            }?.let { Brush.linearGradient(it) }
                                ?: SolidColor(settingsPageBackground(settingsVisualConfig(state.config)))))
                    }
                }
                /*
                 * Wallpaper brightness is a visual treatment for the wallpaper only. Keep it
                 * outside backgroundBackdrop's provider node: liquid course cards should sample
                 * the original wallpaper, otherwise lowering wallpaper brightness also darkens
                 * every glass surface that consumes this backdrop.
                 */
                if (
                    rootPageMotion.retains(false) &&
                    visualState.loaded &&
                    visualState.config.hasAnyWallpaper() &&
                    wallpaperImages.source != null
                ) {
                    Box(Modifier.fillMaxSize().homeSwitchLayer(rootPageMotion, secondary = false,
                        pageClip = HomeSwitchClip.Page)) {
                        WallpaperToneOverlay(visualState.config, personalizationPreviewState)
                    }
                }
                if (useSharedCourseBackdrop) {
                    Box(Modifier.fillMaxSize()
                        .drawWithContent { if (rootPageMotion.retains(false)) drawContent() }
                        .then(requireNotNull(sharedCourseBackdrop).preRenderModifier {
                        // This source is inside the page transform: neither translation nor
                        // page corners change its pixels. Each card still samples its live offset.
                        homeWallpaperRecordKey.value?.let { imageKey ->
                            imageKey to (personalizationPreviewState.wallpaperBrightness
                                ?: visualState.config.wallpaperBrightness)
                        }
                    }))
                }
                Box(Modifier.fillMaxSize()
                    .onGloballyPositioned { courseBackdropViewport.update(it.boundsInWindow()) }
                    .glassBackdropProducer(contentBackdrop)) {
                val contentModifier = Modifier.fillMaxSize().homeSidebarContentInset(homeSidebarState)
                Column(modifier = contentModifier) {
                    if (screen !is Screen.Home) {
                        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    }
                    ContentEntranceContainer(phase = startupPhase, modifier = Modifier.weight(1f)) {
                        HomeSwitchPane(rootPageMotion, secondary = false, modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                alpha = importHomeReveal.value
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                            },
                            pageClip = HomeSwitchClip.Page, retainContent = true) {
                            rootPageStateHolder.SaveableStateProvider("home") {
                                 if (visualState.loaded) HomeScreen(
                                     state = visualState,
                                     agentState = agentVisualState,
                                     personalizationPreviewState = personalizationPreviewState,
                                     mode = homeMode,
                                     modeMotion = homeModeMotion,
                                     onRenderedCardCountChanged = { mode, count ->
                                         if (mode == HomeMode.Week) weekRenderedCardCount = count
                                         else dayRenderedCardCount = count
                                     },
                                     dayViewMode = dayViewMode,
                                     weekViewStyle = weekViewStyle,
                                     adaptiveMetrics = sidebarContentMetrics,
                                    weekCardHeight = weekCardHeight.dp,
                                    displayWeek = homeDisplayWeek,
                                    returnToCurrentWeekRequest = returnHomeWeekRequest,
                                    displayDate = homeDisplayDate,
                                    backdrop = courseWallpaperBackdrop,
                                    dayAgentBackdrop = dayAgentBackdrop,
                                    // Dragged week cards must not sample contentBackdrop/chromeBackdrop:
                                    // they live inside contentBackdrop, so sampling it can recursively include themselves.
                                    floatingCourseBackdrop = backgroundBackdrop,
                                     weekHeaderBackdrop = backgroundBackdrop,
                                    onSwipeWeek = { delta -> homeDisplayWeek = (homeDisplayWeek + delta).coerceIn(1, visualState.config.totalWeeks.coerceAtLeast(1)) },
                                    onWeekHeaderPreview = { homeWeekHeaderPreview.value = it },
                                    onWeekJumpSettled = { settledWeek ->
                                        if (settledWeek == homeDisplayWeek) captureRenderToken++
                                    },
                                    onSwipeDay = { delta ->
                                        val requested = homeDisplayDate.plusDays(delta.toLong())
                                        val range = scheduleDayNavigationRange(visualState.config, todayDate)
                                        homeDisplayDate = if (range == null) requested
                                        else requested.coerceIn(range.start, range.endInclusive)
                                    },
                                    onContentUnderTopBarChange = { if (screen is Screen.Home) homeContentUnderTopBar = it },
                                    dayAgentBackgroundMotionState = dayAgentBackgroundMotionState,
                                    onAgentPagerSettledChange = { settled ->
                                        dayAgentPagerSettled = settled
                                    },
                                    onAgentPrepareOpen = {
                                        // Never open over a partially swiped day page. The background is now
                                        // transformed live (blur + zoom), so no frozen snapshot capture is
                                        // needed — only the pager-settled gate remains.
                                        if (!dayAgentPagerSettled) {
                                            snapshotFlow { dayAgentPagerSettled }.first { it }
                                        }
                                    },
                                    onAgentDismissed = {},
                                    onCourseClick = homeCourseClick,
                                    onAddCourse = viewModel::addCourse,
                                    onAgentAction = handleHomeAgentAction,
                                    onAgentImportFile = { uri ->
                                        assistantImportUri = uri
                                        homeDialog = HomeDialog.ImportSchedule
                                    },
                                    onUpdateCourseSingleWeek = viewModel::updateCourseSingleWeek,
                                    conflictFocusCourseId = pendingConflictCourseId
                                        ?.takeIf { homeDisplayWeek in pendingConflictWeeks },
                                    conflictFocusCourseKey = pendingConflictCourseKey
                                        ?.takeIf { homeDisplayWeek in pendingConflictWeeks },
                                    onResolveCourseConflict = { course, moved, week ->
                                            viewModel.updateCourseSingleWeek(course, moved, week)
                                        appScope.launch {
                                            delay(180)
                                            pendingConflictCourseId = null
                                            pendingConflictCourseKey = null
                                            pendingConflictWeeks = emptyList()
                                        }
                                    },
                                     onDeleteCourseSingleWeek = { course, week ->
                                         pendingCourseGroupDelete = emptyList()
                                         homeDialog = HomeDialog.ApplyCourseDelete(course, week)
                                     },
                                      weekEditInteractionEnabled = pickerState.phase is CustomizeUiState.Home,
                                      onScheduleLongPress = {
                                        if (pickerState.phase is CustomizeUiState.Home) {
                                            pendingHomeAnchoredOverlay = null
                                            homeAnchoredOverlayRequest = null
                                            if (homeAdaptiveMetrics.isLargeScreen) {
                                                homeSidebarState.collapseOverlay()
                                                enterCustomizePage()
                                            } else {
                                                pickerState.phase = CustomizeUiState.ShowingEntryButton
                                                showScheduleEntryPill = true
                                                prewarmCurrentScheduleSnapshot()
                                            }
                                        }
                                    }
                                )
                            }
                            if (!homeAdaptiveMetrics.isLargeScreen) {
                                DockBackdropContinuityPatch(
                                    config = visualState.config,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                        HomeSwitchPane(rootPageMotion, secondary = true,
                            modifier = Modifier.fillMaxSize().padding(top = homeSidebarState.targetContentTopInset),
                            pageClip = HomeSwitchClip.Page) {
                            LandscapePageTransition(
                                sidebarPage,
                                modifier = Modifier.fillMaxSize(), onMovingChange = { sidebarPageMoving = it }
                            ) { destination ->
                            when (destination) {
                                HomeSidebarDestination.Courses -> LandscapeCourseManagementPane(
                                    state = state, onSave = viewModel::saveManagedCourseGroup,
                                    onExitHandlerChange = { coursePaneExitHandler = it }
                                )
                                HomeSidebarDestination.EduImport -> CompositionLocalProvider(
                                    LocalDetailActivityFloatingOverlayHost provides eduFloatingOverlayHost
                                ) { DetailActivityScaffold(
                                    title = "选择学校", config = state.config, onBack = { screen = Screen.Home }
                                ) { schoolBackdrop ->
                                    EduSchoolPickerScreen(state = state, backdrop = schoolBackdrop, onSelect = { adapter ->
                                        context.openRegisteredActivity(
                                            TransitionRouteId.SchoolSelectToEduImport,
                                            Intent(context, EduImportActivity::class.java)
                                                .putExtra(EduAdapterExtra, adapter.toIntentKey())
                                        )
                                    })
                                } }
                                else -> {
                            rootPageStateHolder.SaveableStateProvider("settings") {
                                SettingsScreen(
                                        page = SettingsPage.Root,
                                        state = state,
                                        backdrop = backgroundBackdrop,
                                        onPageChange = {
                                            val intent = Intent(context, SettingsDetailActivity::class.java)
                                                .putExtra(SettingsDetailPageExtra, it.name)
                                            if (it == SettingsPage.Schedule) {
                                                intent.putExtra(ScheduleCustomizeIdExtra, state.config.id)
                                            }
                                            context.openRegisteredActivity(
                                                TransitionRouteId.HomeToSettingsDetail,
                                                intent
                                            )
                                        },
                                        onSave = { _, _ -> viewModel.refreshNotificationsAfterSave() },
                                        onUpdateConfig = viewModel::saveNotificationSettings,
                                        onUpdateGeneralConfig = viewModel::saveGeneralSettings,
                                        onUpdateHomeChromeBlurScale = viewModel::saveHomeChromeBlurScale,
                                        onPreviewLiveUpdate = viewModel::previewLiveUpdate,
                                        exitCommitRequest = settingsExitRequest,
                                        onExitCommitFinished = { completed ->
                                            val action = pendingSettingsExitAction
                                            pendingSettingsExitAction = null
                                            if (completed) action?.invoke()
                                        },
                                        onExitInterceptionChange = {
                                            settingsExitInterceptionRequired = it
                                        },
                                        onTabletAboutBackgroundChanged = { tabletAboutGradientColors = it }
                                    )
                            }
                                }
                            }
                            }
                        }
                    }
                }
                // The full-window decoration is recorded below the global sidebar. Interactive
                // controls retain their right-pane coordinates through the inherited host.
                if (screen is Screen.Config && sidebarPage == HomeSidebarDestination.EduImport) {
                    eduFloatingOverlayHost.content?.invoke()
                }
                }
            if (screen !is Screen.Home) {
                showScheduleEntryPill = false
                if (pickerState.phase is CustomizeUiState.ShowingEntryButton) pickerState.phase = CustomizeUiState.Home
            }
            if (!nativeHomeDockAvailable) HomeDockChrome(
                visible = dockVisible,
                parallel = parallelPhoneNavigation,
                selectedIndex = dockSelectedIndex,
                config = dockConfig,
                backdrop = chromeBackdrop,
                phase = startupPhase,
                buttonHidden = dockButtonHidden,
                onSelect = dockSelect,
                onOrdinaryHome = ordinaryDockHome,
                onOpenMenu = dockOpenMenu,
                modifier = Modifier.align(Alignment.BottomStart).zIndex(100f)
            )
        }
        }

    } // end course/frozen locals

    // Capture the complete page, including its native Dock, at full size before the outer
    // shortcut blur downsamples it. Native material corners must not first render into the
    // reduced blur pass. Keep inputs outside course-only locals to avoid native recomposition.
    // The Compose fallback captures backdrop/config state that the native Dock never uses.
    // Keep that changing closure behind State, so it cannot invalidate the native host.
    val nativeDockFallback = rememberUpdatedState<@Composable () -> Unit> {
        HomeDockChrome(dockVisible, parallelPhoneNavigation, dockSelectedIndex, dockConfig,
            chromeBackdrop, startupPhase, dockButtonHidden, dockSelect, ordinaryDockHome, dockOpenMenu)
    }
    if (nativeHomeDockAvailable) com.xiaomanjun.sleepdownschedule.feature.home.NativeHomeDockChrome(
        visible = dockVisible,
        parallel = parallelPhoneNavigation,
        selectedIndex = dockSelectedIndex,
        dark = if (screen is Screen.Home) appDarkTheme else appUsesDarkTheme(state.config),
        alignment = dockConfig.dockAlignment,
        buttonHidden = dockButtonHidden,
        onSelect = dockSelect,
        onHome = ordinaryDockHome,
        onOpenMenu = dockOpenMenu,
        phase = startupPhase,
        fallback = nativeDockFallback,
        modifier = Modifier.align(Alignment.BottomStart)
    )
    } // end cached page content, including native Dock

        // This control intentionally lives outside the recorded home layer. It can be visible
        // while the real home is pre-captured without becoming part of its own preview bitmap.
        if (screen is Screen.Home && !homeAdaptiveMetrics.isLargeScreen) {
            ScheduleManagerEntryPill(
                visible = showScheduleEntryPill,
                backdrop = chromeBackdrop,
                config = visualState.config,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(89f)
                    .homeCountdownShockwave(0.85f),
                onClick = ::enterCustomizePage,
                onDismiss = {
                    entryPrewarmJob?.cancel()
                    showScheduleEntryPill = false
                    if (pickerState.phase is CustomizeUiState.ShowingEntryButton) {
                        pickerState.phase = CustomizeUiState.Home
                    }
                }
            )
        }

        pickerState.snapshotCoverBitmap?.let { cover ->
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().zIndex(90f)
            )
        }

        SchedulePickerOverlay(
            pickerState = pickerState,
            allState = allSchedulesState,
            backdrop = chromeBackdrop,
            dialogBackdrop = pickerSceneBackdrop,
            contentStartInset = homeSidebarState.targetContentInset,
            contentEndInset = homeSidebarState.endInset,
            contentTopInset = homeSidebarState.targetContentTopInset,
            onPageSelected = ::switchPickerSchedule,
            onApply = { exitPicker(apply = true) },
            onClose = { exitPicker(apply = false) },
            onBack = { centeredId ->
                exitPicker(apply = false)
            },
            onCreate = {
                if (pickerState.phase is CustomizeUiState.Picker) {
                    snapshotJob?.cancel()
                    pickerState.phase = CustomizeUiState.CreatingCombination
                    viewModel.createSchedule(activate = false) { newId ->
                        appScope.launch {
                            snapshotFlow {
                                val latest = latestAllSchedulesState.value
                                latest.schedules.any { it.id == newId } &&
                                    latest.allConfigs.any { it.id == newId } &&
                                    latest.allPeriods.any { it.scheduleId == newId }
                            }.first { it }
                            pickerState.temporaryIds += newId
                            // Keep the currently centered card as the transition source. The new
                            // schedule is rendered underneath and revealed by the same crossfade
                            // hand-off used by Cancel, so no fake card or Pager insertion is needed.
                            pickerState.phase = CustomizeUiState.Picker
                            exitPicker(
                                apply = true,
                                targetOverride = newId,
                                commitTarget = true,
                                crossfadeToTarget = true,
                                onFinished = {
                                    quickScheduleReturnToManager = true
                                    quickScheduleDraft = quickDraftFor(newId)
                                }
                            )
                        }
                    }
                }
            },
            onShare = { scheduleId, shareType ->
                val profile = allSchedulesState.schedules.firstOrNull { it.id == scheduleId }
                val config = allSchedulesState.allConfigs.firstOrNull { it.id == scheduleId } ?: defaultConfig(scheduleId)
                val periods = allSchedulesState.allPeriods.filter { it.scheduleId == scheduleId }.ifEmpty { defaultPeriods(scheduleId) }
                val courses = allSchedulesState.allCourses.filter { it.scheduleId == scheduleId }
                val scheduleName = profile?.name ?: "课表"
                when (shareType) {
                    ScheduleShareType.TOKEN -> shareScheduleToken(
                        context,
                        scheduleName,
                        buildSleepDownScheduleToken(config, periods, courses)
                    )
                    ScheduleShareType.ICS -> appScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                IcsScheduleCodec.writeShareFile(context, scheduleName, config, periods, courses)
                            }
                        }.onSuccess { shareScheduleIcs(context, scheduleName, it) }
                            .onFailure { Toast.makeText(context, it.message ?: "ICS 文件生成失败", Toast.LENGTH_SHORT).show() }
                    }
                }
            },
            onCustomize = { scheduleId ->
                if (pickerState.phase is CustomizeUiState.Picker) {
                    quickScheduleReturnToManager = false
                    quickScheduleDraft = quickDraftFor(scheduleId)
                }
            },
            onRename = viewModel::renameSchedule,
            onDeleteRequest = { scheduleId ->
                if (pickerState.phase is CustomizeUiState.Picker && pickerState.orderIds.size > 1) {
                    snapshotJob?.cancel()
                    pickerState.phase = CustomizeUiState.DeletingCombination
                    appScope.launch {
                        val animation = Animatable(0f)
                        animation.animateTo(1f, tween(280)) {
                            pickerState.deleteScale = 1f - value * 0.4f
                            pickerState.deleteAlpha = 1f - value
                        }
                        val deletedIndex = pickerState.orderIds.indexOf(scheduleId)
                        pickerState.orderIds.remove(scheduleId)
                        pickerState.temporaryIds.remove(scheduleId)
                        pickerState.snapshots.remove(scheduleId)
                        appScope.launch { ScheduleSnapshotStore.delete(context, scheduleId) }
                        viewModel.deleteSchedule(scheduleId)
                        val nextId = pickerState.orderIds.getOrNull(deletedIndex.coerceAtMost(pickerState.orderIds.lastIndex))
                            ?: pickerState.orderIds.firstOrNull()
                        pickerState.deletingScheduleId = null
                        pickerState.deleteReveal = 0f
                        pickerState.deleteScale = 1f
                        pickerState.deleteAlpha = 1f
                        pickerState.phase = CustomizeUiState.Picker
                        nextId?.let(::switchPickerSchedule)
                    }
                }
            },
            // Capture the complete manager (cards, title, indicators and actions) in a producer
            // that does not contain the later quick-settings sheet consumer.
            modifier = Modifier.glassBackdropProducer(pickerSceneBackdrop)
        )
    } // end HomeBackgroundBlurLayer
    } // end HomeBackgroundZoomLayer
    } // end assistant background depth

    val latestOpenHomeMenuDestination = rememberUpdatedState<(HomeMenuDestinationKind) -> Unit> {
        kind -> openHomeMenuDestination(kind)
    }
    fun releaseHomeMenuActivitySource() {
        homeMenuActivityLaunched = false
        destinationOwnsButtonReturn = false
        destinationCollapseHandedOff = false
        homeMenuSourceHidden = false
    }
    val latestOpenHomeActivityDestination =
        rememberUpdatedState<(TransitionRouteId, Intent) -> Unit> { routeId, targetIntent ->
            if (homeMenuActivityLaunched) return@rememberUpdatedState
            val activity = context.findActivity() ?: return@rememberUpdatedState
            if (homeAdaptiveMetrics.isLargeScreen) {
                // Large screens skip the heavy home morph: first retract the three-dot menu,
                // then open with the system default cross-activity transition. Returning lands
                // on the plain home with the menu already closed.
                homeMenuActivityLaunched = true
                homeAnchoredOverlayRequest = null
                appScope.launch {
                    try {
                        snapshotFlow {
                            homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Idle &&
                                homeMenuDestinationMotionState.phase == HomeAnchoredOverlayPhase.Idle
                        }.first { it }
                        if (!activity.isFinishing) {
                            ActivityTransitionCoordinator.openImmediate(
                                activity,
                                if (routeId == TransitionRouteId.HomeToCourseManagement) {
                                    TransitionRouteId.TabletHomeToCourseManagement
                                } else {
                                    TransitionRouteId.TabletHomeToEduImport
                                },
                                targetIntent
                            )
                        }
                    } finally {
                        releaseHomeMenuActivitySource()
                    }
                }
                return@rememberUpdatedState
            }
            val sharedDestinationRequest =
                currentHomeMenuDestinationRequest(HomeMenuDestinationKind.EduImport)
                    ?: return@rememberUpdatedState
            val sourceBoundsInRoot = sharedDestinationRequest.sourceBoundsInRoot
            val collapseBoundsInRoot = sharedDestinationRequest.collapseBoundsInRoot
            if (sourceBoundsInRoot.width <= 2f || sourceBoundsInRoot.height <= 2f) {
                return@rememberUpdatedState
            }
            homeMenuActivityLaunched = true
            appScope.launch {
            var openingSourceHandoffOwnedBySession = false
            var openingSourcePlaceholder: BitmapDrawable? = null
            val openingSourceOverlay = activity.window.decorView.overlay
            try {
                val fullFrame = runCatching {
                    detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                val sourceSnapshot = fullFrame?.cropToAnchoredBounds(sourceBoundsInRoot)
                // Only crossing an Activity boundary needs a bitmap. By now the hidden dock
                // has recorded the normal, non-interactive button during the preparing frame.
                val collapseSnapshot = if (homeMenuFromDock) runCatching {
                    dockImportReturnLayer?.toImageBitmap()?.asAndroidBitmap()
                }.getOrNull() else fullFrame?.cropToAnchoredBounds(collapseBoundsInRoot)
                // Never hide the accepted glass menu unless both the complete opening source and
                // the real top-right return button have been captured successfully.
                if (sourceSnapshot == null || collapseSnapshot == null) {
                    releaseHomeMenuActivitySource()
                    return@launch
                }
                val sourceBoundsInWindow = Rect(
                    left = sourceBoundsInRoot.left + homeRootPositionInWindow.x,
                    top = sourceBoundsInRoot.top + homeRootPositionInWindow.y,
                    right = sourceBoundsInRoot.right + homeRootPositionInWindow.x,
                    bottom = sourceBoundsInRoot.bottom + homeRootPositionInWindow.y
                )
                val collapseBoundsInWindow = Rect(
                    left = collapseBoundsInRoot.left + homeRootPositionInWindow.x,
                    top = collapseBoundsInRoot.top + homeRootPositionInWindow.y,
                    right = collapseBoundsInRoot.right + homeRootPositionInWindow.x,
                    bottom = collapseBoundsInRoot.bottom + homeRootPositionInWindow.y
                )
                openingSourcePlaceholder = BitmapDrawable(context.resources, sourceSnapshot).apply {
                    setBounds(
                        sourceBoundsInWindow.left.roundToInt(),
                        sourceBoundsInWindow.top.roundToInt(),
                        sourceBoundsInWindow.right.roundToInt(),
                        sourceBoundsInWindow.bottom.roundToInt()
                    )
                }
                val sourceHandoffAttached = runCatching {
                    openingSourceOverlay.add(checkNotNull(openingSourcePlaceholder))
                    true
                }.getOrDefault(false)
                if (!sourceHandoffAttached) {
                    releaseHomeMenuActivitySource()
                    return@launch
                }
                // Preserve the exact clicked menu until either ColorOS actually starts its spring
                // or the Legacy backend synchronously installs its own placeholder. The hidden
                // Compose frame below is still what the destination backdrop records.
                homeMenuSourceHidden = true
                withFrameNanos { }
                withFrameNanos { }
                val cleanBackground = runCatching {
                    detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                if (cleanBackground == null) {
                    releaseHomeMenuActivitySource()
                    return@launch
                }
                val openingAnchor = TransitionAnchorFrame(
                    boundsInWindow = sourceBoundsInWindow,
                    cornerRadiusPx = with(density) { HomeAddMenuTargetCornerDp.dp.toPx() },
                    bitmap = sourceSnapshot
                )
                val returnAnchor = TransitionAnchorFrame(
                    boundsInWindow = collapseBoundsInWindow,
                    cornerRadiusPx = minOf(collapseBoundsInRoot.width, collapseBoundsInRoot.height) / 2f,
                    bitmap = collapseSnapshot
                )
                val launchResult = ActivityTransitionCoordinator.open(
                    activity = activity,
                    routeId = routeId,
                    intent = targetIntent,
                    payload = TransitionPayload(
                        openingAnchor = openingAnchor,
                        returnAnchorProvider = StaticTransitionAnchorProvider(returnAnchor),
                        backgroundBitmap = cleanBackground,
                        onOpeningSourceHandoff = {
                            activity.runOnUiThread {
                                openingSourcePlaceholder?.let(openingSourceOverlay::remove)
                                openingSourcePlaceholder = null
                            }
                        },
                        // The source placeholder intentionally remains visible across the async
                        // Activity launch. Ask the public ColorOS API to alpha the source Activity
                        // leash when its native animation starts, preventing a stationary menu
                        // from remaining below the moving system bitmap.
                        nativeSourceLeashAlphaOutOnOpen = true,
                        // Source onResume happens before ColorOS finishes its CLOSE spring. Let
                        // the session-scoped backend release the hidden menu/button only after the
                        // matching native end callback (or Legacy completion/watchdog).
                        onSourceReleased = {
                            activity.runOnUiThread { releaseHomeMenuActivitySource() }
                        }
                    )
                )
                if (launchResult is TransitionLaunchResult.Failed) {
                    releaseHomeMenuActivitySource()
                    return@launch
                }
                openingSourceHandoffOwnedBySession = true
                // The Activity now owns the exact Edu-import return to the real three-dot button.
                // Silently dispose the first-level menu underneath it instead of restoring that
                // menu as a false return anchor.
                destinationOwnsButtonReturn = true
                destinationCollapseHandedOff = false
                homeAnchoredOverlayRequest = null
            } catch (_: Throwable) {
                releaseHomeMenuActivitySource()
            } finally {
                if (!openingSourceHandoffOwnedBySession) {
                    openingSourcePlaceholder?.let(openingSourceOverlay::remove)
                    openingSourcePlaceholder = null
                }
            }
        }
        }
    val latestOpenCourseManagement = rememberUpdatedState<() -> Unit> {
        if (homeAdaptiveMetrics.isTabletLandscape) {
            requestSettingsExit { sidebarPage = HomeSidebarDestination.Courses; screen = Screen.Config }
        } else {
        latestOpenHomeActivityDestination.value(
            TransitionRouteId.HomeToCourseManagement,
            Intent(context, CourseManagementActivity::class.java)
                .putCourseManagementInitialState(state)
        )
        }
    }
    val latestOpenEduSchoolSelect = rememberUpdatedState<() -> Unit> {
        if (homeAdaptiveMetrics.isTabletLandscape) {
            requestSettingsExit { sidebarPage = HomeSidebarDestination.EduImport; screen = Screen.Config }
        }
        else {
        latestOpenHomeActivityDestination.value(
            TransitionRouteId.HomeToEduImport,
            Intent(context, EduSchoolSelectActivity::class.java)
        )
        }
    }
    val latestOpenJumpWeekDialog = rememberUpdatedState<() -> Unit> {
        pendingJumpWeekDialog = true
        homeAnchoredOverlayRequest = null
    }
    val latestOpenScheduleSettings = rememberUpdatedState<() -> Unit> {
        pendingQuickScheduleSettings = true
        homeAnchoredOverlayRequest = null
    }
    val latestOpenScheduleManager = rememberUpdatedState<() -> Unit> {
        pendingOpenScheduleSettings = true
        homeAnchoredOverlayRequest = null
    }
    val homeAddActions = remember {
        listOf(
            AddMenuAction(R.drawable.ic_add_course, "添加单节课") {
                latestOpenHomeMenuDestination.value(HomeMenuDestinationKind.AddCourse)
            },
            AddMenuAction(R.drawable.ic_ai_import, "口令/文件导入") {
                latestOpenHomeMenuDestination.value(HomeMenuDestinationKind.ManualImport)
            },
            AddMenuAction(R.drawable.ic_school_import, "教务系统导入") {
                latestOpenEduSchoolSelect.value()
            },
            AddMenuAction(R.drawable.ic_courses, "课程管理") {
                latestOpenCourseManagement.value()
            },
            AddMenuAction(R.drawable.ic_material_event, "跳转周数") {
                latestOpenJumpWeekDialog.value()
            },
            AddMenuAction(R.drawable.ic_settings, "课表设置") {
                latestOpenScheduleSettings.value()
            },
            AddMenuAction(R.drawable.ic_schedule_layers, "多课表管理") {
                latestOpenScheduleManager.value()
            }
        )
    }

    val latestNativeCourseManagement = rememberUpdatedState<() -> Unit> {
        val activity = context.findActivity()
        if (activity != null && !activity.isFinishing) {
            // NativeOverflowMenu invokes this only after COUI has finished retracting its window.
            ActivityTransitionCoordinator.openImmediate(activity, TransitionRouteId.HomeMenuToCourseManagement,
                Intent(context, com.xiaomanjun.sleepdownschedule.feature.experimental.colorosui.ColorOsCourseManagementActivity::class.java)
                    .putCourseManagementInitialState(state))
        }
    }
    val nativeOverflowActions = remember(homeAddActions) {
        listOf(AddMenuAction(R.drawable.ic_courses, "课程管理") { latestNativeCourseManagement.value() }) +
            homeAddActions.takeLast(3)
    }
    androidx.compose.runtime.SideEffect { colorOsOverflowActions.value = nativeOverflowActions }

    fun runSidebarAction(action: () -> Unit) {
        if (pickerState.phase is CustomizeUiState.Picker) {
            exitPicker(apply = false, onFinished = { requestSettingsExit(action) })
        } else if (!pickerState.overlayVisible) {
            requestSettingsExit(action)
        }
    }

    HomeSidebar(
        state = homeSidebarState, metrics = homeAdaptiveMetrics,
        selected = when {
            pickerState.overlayVisible -> HomeSidebarDestination.Schedules
            screen is Screen.Config -> if (homeAdaptiveMetrics.isTabletLandscape) sidebarPage else HomeSidebarDestination.Settings
            homeMode == HomeMode.Day -> HomeSidebarDestination.Day
            else -> HomeSidebarDestination.Week
        },
        config = if (screen is Screen.Config) settingsVisualConfig(state.config) else visualState.config,
        backdrop = if (pickerState.overlayVisible) pickerSceneBackdrop else chromeBackdrop,
        followHomeGlass = screen is Screen.Home,
        hasGradientBackground = screen is Screen.Config && tabletAboutGradientColors != null,
        tabSelection = if (screen is Screen.Config) HomeSidebarDestination.Settings
            else if (homeMode == HomeMode.Day) HomeSidebarDestination.Day else HomeSidebarDestination.Week,
        navigationEnabled = !rootPageMotion.moving && !homeModeMotion.moving && !sidebarPageMoving &&
            pendingSettingsExitAction == null && !homeBackgroundOverlayActive && !homeAssistant.visible &&
            courseShortcuts.request == null && !courseCopy.active && !homeDialogVisible && quickScheduleDraft == null &&
            courseEditorRequest == null && courseEditorOverlayPhase == CourseEditorOverlayPhase.Idle &&
            (!pickerState.overlayVisible || pickerState.phase is CustomizeUiState.Picker) &&
            dayAgentBackgroundMotionState.progress.value < 0.001f,
        visible = homeAdaptiveMetrics.isTabletLandscape || pickerState.phase is CustomizeUiState.Home ||
            pickerState.phase is CustomizeUiState.ShowingEntryButton,
        actions = if (homeAdaptiveMetrics.isTabletLandscape) homeAddActions.filter { it.iconRes != R.drawable.ic_courses } else emptyList(),
        onAction = { action ->
            runSidebarAction {
                if (action.iconRes == R.drawable.ic_school_import) {
                    action.onClick()
                } else {
                    screen = Screen.Home
                    appScope.launch {
                        withFrameNanos { }
                        snapshotFlow { rootPageMotion.moving }.first { !it }
                        action.onClick()
                    }
                }
            }
        },
        onNavigate = { destination ->
            if (destination != HomeSidebarDestination.Schedules || !pickerState.overlayVisible) runSidebarAction {
                when (destination) {
                    HomeSidebarDestination.Day -> { homeMode = HomeMode.Day; screen = Screen.Home }
                    HomeSidebarDestination.Week -> { homeMode = HomeMode.Week; screen = Screen.Home }
                    HomeSidebarDestination.Settings -> { sidebarPage = destination; screen = Screen.Config }
                    HomeSidebarDestination.Courses -> {
                        if (homeAdaptiveMetrics.isTabletLandscape) { sidebarPage = destination; screen = Screen.Config }
                        else latestOpenCourseManagement.value()
                    }
                    HomeSidebarDestination.Schedules -> {
                        screen = Screen.Home
                        appScope.launch {
                            withFrameNanos { }
                            snapshotFlow { rootPageMotion.moving }.first { !it }
                            withFrameNanos { }
                            if (screen is Screen.Home) enterCustomizePage()
                        }
                    }
                    HomeSidebarDestination.EduImport -> latestOpenEduSchoolSelect.value()
                }
            }
        }
    )

    LaunchedEffect(pendingQuickScheduleSettings, homeAnchoredMorphState.phase, screen) {
        if (pendingQuickScheduleSettings && screen is Screen.Home &&
            homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Idle) {
            pendingQuickScheduleSettings = false
            quickScheduleReturnToManager = false
            quickScheduleDraft = quickDraftFor(state.config.id)
        }
    }
    LaunchedEffect(
        pendingOpenScheduleSettings,
        homeAnchoredMorphState.phase,
        screen,
        state.config.id
    ) {
        if (!pendingOpenScheduleSettings || screen !is Screen.Home) return@LaunchedEffect
        if (homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle) return@LaunchedEffect
        pendingOpenScheduleSettings = false
        if (pickerState.phase is CustomizeUiState.Home) {
            // Equivalent to long-pressing the schedule homepage and tapping the 课表设置
            // entry pill: open the in-app multi-schedule customization page directly.
            pickerState.phase = CustomizeUiState.ShowingEntryButton
            showScheduleEntryPill = true
            prewarmCurrentScheduleSnapshot()
            enterCustomizePage()
        }
    }

    LaunchedEffect(
        pendingJumpWeekDialog,
        homeAnchoredMorphState.phase,
        screen,
        state.config.id
    ) {
        if (!pendingJumpWeekDialog || screen !is Screen.Home) return@LaunchedEffect
        if (homeAnchoredMorphState.phase != HomeAnchoredOverlayPhase.Idle) return@LaunchedEffect
        pendingJumpWeekDialog = false
        // Open the jump-week dialog only after the three-dot menu has fully retracted, so the
        // dialog never overlaps the closing menu.
        jumpWeekDialogMounted = true
        jumpWeekDialogVisible = true
    }

    LaunchedEffect(sourceButtonFollowThrough) {
        val follow = sourceButtonFollowThrough ?: return@LaunchedEffect
        sourceButtonFollowProgress.snapTo(0f)
        sourceButtonFollowProgress.animateTo(
            1f,
            spring(dampingRatio = 0.86f, stiffness = 460f)
        )
        sourceButtonFollowThrough = null
    }

    HomeAnchoredMorphOverlayHost(
        request = if (screen is Screen.Home) homeAnchoredOverlayRequest else null,
        motionState = homeAnchoredMorphState,
        backdrop = homeAnchoredOverlayBackdrop,
        config = state.config,
        addActions = if (homeMenuFromDock) homeAddActions.take(3)
            else if (parallelPhoneNavigation) homeAddActions.drop(3) else homeAddActions,
        showModeSwitch = !parallelPhoneNavigation,
        homeMode = homeMode,
        onHomeModeChange = { homeMode = it },
        adaptiveMetrics = homeAdaptiveMetrics,
        modifier = Modifier
            .zIndex(24f)
            .graphicsLayer { alpha = if (homeMenuSourceHidden) 0f else 1f },
        awaitOpeningGate = {
            awaitHomeBackgroundFrame(routeEligible = true)
        },
        onDismissRequest = {
            homeAnchoredOverlayRequest = null
        },
        onAddMenuBoundsChanged = { homeAddMenuBoundsInRoot = it },
        onSourceFollowThrough = { rect -> if (!homeMenuFromDock) latestSourceFollowThrough.value(rect) },
        suppressClose = destinationOwnsButtonReturn,
        personalizePreviewProgress = personalizationPreviewProgress,
        sourceContent = { kind, sourceModifier ->
            if (kind == HomeAnchoredOverlayKind.Add) {
                // The shell fades out as this complete button fades in. An icon-only clone left
                // the blue button surface missing until the real Dock returned on the last frame.
                if (homeMenuFromDock) {
                    HomeImportButtonReturnVisual(homeAnchoredOverlayBackdrop, state.config, sourceModifier)
                } else {
                    HomeIconButtonVisual(
                        backdrop = homeAnchoredOverlayBackdrop, config = state.config,
                        iconRes = R.drawable.ic_more_horizontal, contentDescription = "添加菜单",
                        modifier = sourceModifier, isInteractive = false
                    )
                }
            } else {
            HomeIconButtonVisual(
                backdrop = homeAnchoredOverlayBackdrop,
                config = state.config,
                iconRes = if (kind == HomeAnchoredOverlayKind.Personalize) {
                    R.drawable.ic_edit
                } else {
                    R.drawable.ic_more_horizontal
                },
                contentDescription = if (kind == HomeAnchoredOverlayKind.Personalize) {
                    "benchmark_personalize_button"
                } else {
                    "添加菜单"
                },
                modifier = sourceModifier,
                isInteractive = false
            )
            }
        },
        personalizeContent = { panelModifier ->
            PersonalizePanel(
                modifier = panelModifier.semantics { testTag = "benchmark_personalize_panel" },
                drawSurface = false,
                rowEntranceActive = homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Opening ||
                    homeAnchoredMorphState.phase == HomeAnchoredOverlayPhase.Open,
                state = pendingVisualState,
                backdrop = homeAnchoredOverlayBackdrop,
                mode = homeMode,
                weekCardHeightScale = weekCardHeightScale,
                weekCardHeightScaleFloor = weekCardHeightScaleFloor,
                onWeekCardHeightScale = {
                    val safeScale = normalizedWeekCardHeightScale(it, weekCardHeightScaleFloor)
                    val committedConfig = mergePersonalizationCandidate(
                        current = personalizationDraftConfig ?: visualState.config,
                        candidate = visualState.config.copy(
                            weekCardHeightDp = null,
                            weekCardHeightScale = safeScale
                        ),
                        changeKey = PersonalizeWeekHeightSlider
                    )
                    weekCardHeightScale = safeScale
                    personalizationDraftConfig = committedConfig
                    personalizationPendingCommitConfig = committedConfig
                    viewModel.savePersonalization(committedConfig)
                },
                onWeekCardHeightScalePreview = {
                    weekCardHeightScale = normalizedWeekCardHeightScale(it, weekCardHeightScaleFloor)
                },
                onPickWallpaper = {
                    wallpaperLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onUpdateConfig = { changeKey, candidate ->
                    val merged = mergePersonalizationCandidate(
                        current = personalizationDraftConfig ?: visualState.config,
                        candidate = candidate,
                        changeKey = changeKey
                    )
                    personalizationDraftConfig = merged
                    personalizationPendingCommitConfig = merged
                    if (changeKey != PersonalizeWallpaperBlurSlider) {
                        personalizationPreviewState.clear(changeKey)
                    }
                    viewModel.savePersonalization(merged)
                },
                onPreviewConfig = { sliderKey, candidate ->
                    personalizationPreviewState.update(sliderKey, candidate)
                },
                previewSliderKey = personalizationSliderPreviewKey,
                previewProgress = personalizationPreviewProgress,
                onSliderPreviewActiveChange = { key, active ->
                    personalizationSliderPreviewKey = resolveActivePersonalizationSlider(
                        currentKey = personalizationSliderPreviewKey,
                        eventKey = key,
                        active = active
                    )
                }
            )
        }
    )

    // The first-level menu alone owns this final button follow-through. Destinations restore the
    // real button directly at their legacy pinch handoff and never enter this spring choreography.
    sourceButtonFollowThrough?.let { follow ->
        val rest = addButtonBounds ?: return@let
        val density = LocalDensity.current
        val buttonSizePx = with(density) { 42.dp.toPx() }
        Box(
            modifier = Modifier
                .offset {
                    val progress = sourceButtonFollowProgress.value
                    val center = Offset(
                        x = follow.startCenter.x + (rest.center.x - follow.startCenter.x) * progress,
                        y = follow.startCenter.y + (rest.center.y - follow.startCenter.y) * progress
                    )
                    IntOffset(
                        (center.x - buttonSizePx / 2f).roundToInt(),
                        (center.y - buttonSizePx / 2f).roundToInt()
                    )
                }
                .requiredSize(42.dp)
                .zIndex(300f)
        ) {
            HomeIconButtonVisual(
                backdrop = homeAnchoredOverlayBackdrop,
                config = state.config,
                iconRes = R.drawable.ic_more_horizontal,
                contentDescription = "添加菜单",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = follow.startScale + (1f - follow.startScale) * sourceButtonFollowProgress.value
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }

    if (jumpWeekDialogMounted) {
        HomeJumpWeekDialog(
            show = jumpWeekDialogVisible,
            initialWeek = homeDisplayWeek,
            totalWeeks = visualState.config.totalWeeks,
            backdrop = homeDialogBackdrop,
            config = visualState.config,
            onDismissRequest = { jumpWeekDialogVisible = false },
            onDismissFinished = { targetWeek ->
                jumpWeekDialogVisible = false
                jumpWeekDialogMounted = false
                targetWeek?.let { requestedWeek ->
                    val boundedWeek = requestedWeek.coerceIn(
                        1,
                        visualState.config.totalWeeks.coerceAtLeast(1)
                    )
                    appScope.launch {
                        if (homeMode != HomeMode.Week) {
                            homeMode = HomeMode.Week
                            // Let the current week page become the settled week surface first;
                            // the selected target then enters through the normal week transition.
                            delay(220)
                        } else {
                            withFrameNanos { }
                        }
                        homeDisplayWeek = boundedWeek
                    }
                }
            }
        )
    }

    HomeMenuDestinationOverlayHost(
        request = homeMenuDestinationRequest,
        motionState = homeMenuDestinationMotionState,
        state = state,
        backdrop = homeMenuDestinationBackdrop,
        adaptiveMetrics = homeAdaptiveMetrics,
        homeMode = homeMode,
        modifier = Modifier.zIndex(90f),
        landscapeContentInsets = PaddingValues(start = homeSidebarState.targetContentInset,
            top = homeSidebarState.targetContentTopInset, end = homeSidebarState.endInset),
        awaitOpeningGate = { awaitHomeBackgroundFrame(routeEligible = true) },
        onDismissRequest = ::closeHomeMenuDestination,
        sourceActions = if (homeMenuFromDock) homeAddActions.take(3)
            else if (parallelPhoneNavigation) homeAddActions.drop(3) else homeAddActions,
        onSourceHandoff = { homeMenuSourceHidden = true },
        collapseContent = { returnModifier ->
            if (homeMenuFromDock) {
                HomeImportButtonReturnVisual(homeMenuDestinationBackdrop, state.config, returnModifier)
            } else {
                HomeIconButtonVisual(
                    backdrop = homeMenuDestinationBackdrop, config = state.config,
                    iconRes = R.drawable.ic_more_horizontal, contentDescription = "添加菜单",
                    modifier = returnModifier, isInteractive = false
                )
            }
        },
        onCollapseHandoff = {
            destinationCollapseHandedOff = true
        },
        onClosed = {
            homeMenuSourceHidden = false
            destinationCollapseHandedOff = false
            destinationOwnsButtonReturn = false
        },
        onAddCourses = { courses ->
            val conflictWeeks = conflictWeeksForAddedCourses(state.previewCourseEdits(courses), state.courses, state.periods)
            if (conflictWeeks.isEmpty()) {
                viewModel.addCourses(courses)
                closeHomeMenuDestination()
            } else {
                pendingCourseGroupEdit = PendingCourseGroupEdit(emptyList(), courses)
                homeDialog = HomeDialog.ConfirmCourseConflicts(
                    original = courses.first(),
                    edited = courses.first(),
                    targetWeek = effectiveCurrentWeek(state.config),
                    conflictWeeks = conflictWeeks
                )
            }
        },
        onManualImportParsed = { draft ->
            closeHomeMenuDestination()
            appScope.launch {
                snapshotFlow { homeMenuDestinationMotionState.phase }.first { it == HomeAnchoredOverlayPhase.Idle }
                homeDialog = HomeDialog.ConfirmImport(draft, returnDialog = null)
            }
        },
        captureHistoryBackground = {
            // History handoff is a rare explicit capture. Advance the frame key so the optimized
            // stable-frame recorder refreshes both the visible source frame and, after the source
            // is hidden, the clean background frame. Without this the callback can return the
            // previous cached menu texture even though Compose has already changed the source.
            captureRenderToken += 1
            withFrameNanos { }
            withFrameNanos { }
            runCatching {
                AiImportHistoryBackgroundCapture(
                    bitmap = detailScreenGraphicsLayer.toImageBitmap().asAndroidBitmap(),
                    rootLeftInWindow = homeRootPositionInWindow.x,
                    rootTopInWindow = homeRootPositionInWindow.y
                )
            }.getOrNull()
        },
        onEduAdapterSelected = { adapter ->
            context.openRegisteredActivity(
                TransitionRouteId.SchoolSelectToEduImport,
                Intent(context, EduImportActivity::class.java)
                    .putExtra(EduAdapterExtra, adapter.toIntentKey())
            )
        }
    )
    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {

        // Keep the course editor inside the same root stack as the stock MIUIX popup
        // host. The host stays in its 1.0.6 position and therefore preserves the manager
        // quick sheet's original backdrop, colors and animation, while its later draw
        // order still places picker dialogs above the editor shell.
        CourseEditorContainerOverlayHost(
            request = courseEditorRequest,
            state = state,
            backdrop = courseEditorBackdrop,
            sourceCardBackdrop = courseEditorSourceBackdrop,
            config = state.config,
            adaptiveMetrics = homeAdaptiveMetrics,
            modifier = Modifier.zIndex(100f),
            landscapeContentInsets = PaddingValues(start = homeSidebarState.targetContentInset,
                top = homeSidebarState.targetContentTopInset, end = homeSidebarState.endInset),
            awaitOpeningGate = { awaitHomeBackgroundFrame(routeEligible = true) },
            onDismissRequest = { closeCourseEditor() },
            onCopy = { courses, onResult ->
                val sourceRequest = courseEditorRequest
                viewModel.copyCourses(courses) { success ->
                    onResult(success)
                    if (success && courseEditorRequest === sourceRequest) {
                        val destination = courses.first()
                        appScope.launch {
                            val visibleWeeks = destination.weeks.filter {
                                weekCourseBuckets(listOf(destination), it).visibleCourses.isNotEmpty()
                            }
                            val destinationWeek = sourceRequest?.targetWeek?.takeIf { it in visibleWeeks }
                                ?: visibleWeeks.firstOrNull() ?: homeDisplayWeek
                            val targetBuckets = weekCourseBuckets(state.courses + courses, destinationWeek)
                            val weekdays = visibleWeekdaysForBuckets(targetBuckets, state.config.hideEmptyWeekends)
                            val destinationBounds = sourceRequest?.sourceGrid?.reveal(destination, weekdays)
                                ?: sourceRequest?.sourceBoundsInRoot?.let { source ->
                                    courseEditorWeekLandingBounds(
                                        source, sourceRequest.course, destination,
                                        state.periods.map { it.periodIndex },
                                        with(density) { weekCardHeight.dp.toPx() },
                                        with(density) { 4.dp.toPx() }
                                    )
                                }
                            if (courseEditorRequest === sourceRequest) {
                                homeDisplayWeek = destinationWeek
                                courseEditorMotionState.retractTo(destinationBounds, destination)
                                closeCourseEditor()
                            }
                        }
                    }
                }
            },
            onSave = { originals, editedCourses, targetWeek ->
                // Keep the form mounted while both single-record and grouped saves choose a scope.
                homeDialog = HomeDialog.ApplyCourseEdit(
                    original = originals.first(),
                    edited = editedCourses.first(),
                    targetWeek = targetWeek ?: effectiveCurrentWeek(state.config),
                    originals = originals,
                    editedCourses = editedCourses
                )
            },
            onDelete = { courses, targetWeek ->
                pendingCourseGroupDelete = courses.takeIf { it.size > 1 }.orEmpty()
                homeDialog = HomeDialog.ApplyCourseDelete(
                    courses.first(),
                    targetWeek ?: effectiveCurrentWeek(state.config)
                )
            },
            motionState = courseEditorMotionState,
            onRenderedCourseIdChange = {
                courseEditorRenderedCourseId = it
                if (it == null) courseEditorFlightRegistry.frozen = false
            },
            onPhaseChange = {}
        )

    }

    com.xiaomanjun.sleepdownschedule.feature.backup.webdav.WebDavRestorePrompt(
        state.config, chromeBackdrop,
        canShow = !showManagedFreeAiOffer && homeDialog == null && courseEditorRequest == null && homeAnchoredOverlayRequest == null
    ) {
        context.startActivity(Intent(context, SettingsDetailActivity::class.java)
            .putExtra(SettingsDetailPageExtra, SettingsPage.WebDav.name))
    }

    if (showManagedFreeAiOffer) {
        LiquidAlertDialog(
            title = "启用每日免费 AI？",
            message = "SleepDown 为尚未配置模型服务的用户提供每日免费 AI 额度，可用于 AI 助理、AI 对话和 AI 教务导入。固定使用 gpt-5.6-luna 与 Responses 接口，可随时在 AI 设置中切换或关闭。",
            actions = listOf(
                LiquidAlertAction("暂不启用", LiquidAlertActionStyle.Cancel) {
                    AiImportSettingsStore.declineManagedFreeAi(context)
                    showManagedFreeAiOffer = false
                },
                LiquidAlertAction("启用", LiquidAlertActionStyle.Primary) {
                    AiImportSettingsStore.enableManagedFreeAi(context)
                    showManagedFreeAiOffer = false
                }
            ),
            backdrop = chromeBackdrop,
            config = state.config,
            onDismissRequest = {
                AiImportSettingsStore.declineManagedFreeAi(context)
                showManagedFreeAiOffer = false
            }
        )
    }

    remoteExperience.agreement?.let { agreement ->
        LiquidAlertDialog(
            title = agreement.title,
            message = if (agreement.forceReaccept) {
                "协议或政策已经更新，请查看新版本并确认同意后继续使用。"
            } else {
                "首次使用前，请查看并同意此协议或政策。"
            },
            actions = listOf(
                LiquidAlertAction("查看正文", LiquidAlertActionStyle.Secondary) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, agreement.url.toUri()))
                    }
                },
                LiquidAlertAction("同意", LiquidAlertActionStyle.Primary) {
                    SleepDownRemoteConfig.acceptAgreement(context, agreement)
                }
            ),
            backdrop = chromeBackdrop,
            config = state.config,
            onDismissRequest = {}
        )
    } ?: remoteExperience.notice?.let { notice ->
        LiquidAlertDialog(
            title = notice.title,
            message = notice.content,
            actions = listOf(
                LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                    SleepDownRemoteConfig.markNoticeShown(context, notice)
                }
            ),
            backdrop = chromeBackdrop,
            config = state.config,
            onDismissRequest = {
                SleepDownRemoteConfig.markNoticeShown(context, notice)
            }
        )
    }

    if (remoteExperience.agreement == null && remoteExperience.notice == null) {
        upcomingHoliday?.let { holiday ->
            LiquidAlertDialog(
                title = "临近${holiday.name}",
                message = buildString {
                    append("${holiday.name}假期 ")
                    append(formatScheduleDate(holiday.start))
                    if (holiday.end != holiday.start) append(" – ${formatScheduleDate(holiday.end)}")
                    append("，是否现在设置调休课表？")
                    append("停课当天的课程会保留为灰色卡片，补课日按原课程日期上课。")
                },
                actions = listOf(
                    LiquidAlertAction("稍后再说", LiquidAlertActionStyle.Cancel) {
                        upcomingHoliday = null
                    },
                    LiquidAlertAction("去设置", LiquidAlertActionStyle.Primary) {
                        upcomingHoliday = null
                        context.openRegisteredActivity(
                            TransitionRouteId.SettingsToScheduleAdjustments,
                            ScheduleAdjustmentsActivity.intent(
                                context,
                                latestHomeConfig,
                                latestHomeConfig.scheduleAdjustmentsJson
                            ),
                            launchActivity = { holidayAdjustmentsLauncher.launch(it) }
                        )
                    }
                ),
                backdrop = chromeBackdrop,
                config = state.config,
                onDismissRequest = { upcomingHoliday = null }
            )
        }
    }

    (renderedHomeDialog as? HomeDialog.EditWallpaper)?.let { dialog ->
        WallpaperEditorOverlay(
            uri = dialog.uri,
            entrySnapshot = dialog.entrySnapshot,
            config = visualState.config,
            backdrop = chromeBackdrop,
            visible = homeDialogVisible,
            adaptiveMetrics = homeAdaptiveMetrics,
            onCancel = { dismissHomeDialog() },
            onApply = { nextConfig ->
                viewModel.savePersonalization(nextConfig)
                dismissHomeDialog()
            },
            modifier = Modifier.zIndex(280f)
        )
    }

    com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshReviewHost(
        config = state.config, backdrop = homeDialogBackdrop,
        schedules = state.schedules,
        enabled = state.loaded && renderedHomeDialog == null
    )

    // Dialog-based dialogs for all other types (including EditCourse without a source card)
    renderedHomeDialog?.let { dialog ->
        if (dialog !is HomeDialog.EditWallpaper && (dialog !is HomeDialog.EditCourse || dialog.course == null)) {
        if (dialog is HomeDialog.ConfirmImport) {
            ImportPreviewPage(draft = dialog.draft, config = state.config, visible = homeDialogVisible,
                submitting = importSubmitting, errorMessage = dialog.errorMessage,
                onContentMountedChanged = { importPreviewMounted = it },
                onBack = {
                    importExpectedCourseCount = null
                    importPreviousCourseIds = emptySet()
                    homeDialog = dialog.returnDialog
                },
                onDraftChanged = { homeDialog = dialog.copy(draft = it, errorMessage = null) },
                onConfirm = { createNew ->
                    if (!importSubmitting) {
                        importSubmitting = true
                        importSourceState = visualState
                        importPreviousCourseIds = state.courses.map { it.id }.toSet()
                        importExpectedCourseCount = dialog.draft.courses.size
                        homeDialog = dialog.copy(errorMessage = null)
                        viewModel.importDraft(dialog.draft, createNew, onFailure = {
                            importSubmitting = false
                            importSourceState = null
                            importExpectedCourseCount = null
                            importPreviousCourseIds = emptySet()
                            homeDialog = dialog.copy(errorMessage = it.message ?: "保存失败，请重试")
                        }) { scheduleId -> pendingImportedSetupId = scheduleId }
                    }
                })
        } else if (dialog is HomeDialog.ApplyCourseEdit) {
            val allScope = courseEditorApplyAllScope(dialog.originals, dialog.editedCourses, state.courses)
            val destinationWeek = com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorSingleDestinationWeek(
                dialog.original, dialog.edited, dialog.targetWeek)
            val singleEdited = dialog.edited.copy(
                id = dialog.original.id,
                weeks = listOf(destinationWeek ?: dialog.targetWeek),
                weekParity = WeekParity.ALL
            )
            ApplyCourseEditDialog(
                original = dialog.original,
                edited = dialog.edited,
                backdrop = homeDialogBackdrop,
                config = state.config,
                targetWeek = dialog.targetWeek,
                originals = allScope.originals,
                editedCourses = allScope.edited,
                singleAllowed = dialog.editedCourses.size == 1 &&
                    destinationWeek != null &&
                    courseEditorHasOccurrence(dialog.original, dialog.targetWeek),
                onSingle = {
                    val conflictWeeks = conflictWeeksForSingleWeekEdit(
                        state.previewCourseEdits(listOf(dialog.original)).single(),
                        state.previewCourseEdits(listOf(singleEdited)).single(),
                        dialog.targetWeek,
                        state.courses,
                        state.periods
                    )
                    if (conflictWeeks.isEmpty()) {
                        viewModel.updateCourseSingleWeek(
                            dialog.original,
                            singleEdited,
                            dialog.targetWeek
                        )
                        dismissHomeDialog()
                        closeCourseEditor()
                        homeDisplayWeek = destinationWeek ?: dialog.targetWeek
                    } else {
                        homeDialog = HomeDialog.ConfirmCourseConflicts(
                            dialog.original,
                            singleEdited,
                            dialog.targetWeek,
                            conflictWeeks,
                            singleWeekOnly = true,
                            returnEdit = dialog
                        )
                    }
                },
                onAll = {
                    val conflictWeeks = conflictWeeksForEditedCourseGroup(
                        state.previewCourseEdits(allScope.originals), state.previewCourseEdits(allScope.edited), state.courses, state.periods
                    )
                    if (conflictWeeks.isEmpty()) {
                        if (allScope.originals.size == 1 && allScope.edited.size == 1) {
                            viewModel.updateCourse(allScope.edited.single())
                        } else viewModel.replaceCourseGroup(allScope.originals, allScope.edited)
                        dismissHomeDialog()
                        closeCourseEditor()
                    } else {
                        pendingCourseGroupEdit = PendingCourseGroupEdit(allScope.originals, allScope.edited)
                        homeDialog = HomeDialog.ConfirmCourseConflicts(
                            dialog.original,
                            allScope.edited.first(),
                            dialog.targetWeek,
                            conflictWeeks,
                            returnEdit = dialog
                        )
                    }
                },
                onCancel = { dismissHomeDialog() }
            )
        } else if (dialog is HomeDialog.ConfirmCourseConflicts) {
            CourseConflictRetentionDialog(
                course = dialog.edited,
                conflictWeeks = dialog.conflictWeeks,
                backdrop = homeDialogBackdrop,
                config = state.config,
                onKeepTemporarily = keepConflict@{
                    pendingCourseGroupEdit?.let { groupEdit ->
                        if (groupEdit.originals.isEmpty()) {
                            viewModel.addCourses(groupEdit.edited)
                            pendingCourseGroupEdit = null
                            pendingConflictCourseId = null
                            pendingConflictCourseKey = groupEdit.edited.firstOrNull()?.occurrenceOverrideKey()
                            pendingConflictWeeks = dialog.conflictWeeks
                            dismissHomeDialog()
                            closeHomeMenuDestination()
                            val conflictWeek = dialog.conflictWeeks.first()
                            appScope.launch {
                                delay(HomeMenuDestinationCloseDurationMillis.toLong() + 32L)
                                homeDisplayWeek = conflictWeek
                                homeMode = HomeMode.Week
                            }
                            return@keepConflict
                        }
                        val editorWasOpen = courseEditorRequest != null
                        viewModel.replaceCourseGroup(groupEdit.originals, groupEdit.edited)
                        pendingCourseGroupEdit = null
                        pendingConflictCourseId = groupEdit.edited.firstOrNull()?.id?.takeIf { it > 0 }
                        pendingConflictCourseKey = groupEdit.edited.firstOrNull()?.occurrenceOverrideKey()
                        pendingConflictWeeks = dialog.conflictWeeks
                        dismissHomeDialog()
                        closeCourseEditor()
                        val conflictWeek = dialog.conflictWeeks.first()
                        if (editorWasOpen) {
                            appScope.launch {
                                delay(CourseEditorCloseDurationMillis.toLong() + 32L)
                                homeDisplayWeek = conflictWeek
                                homeMode = HomeMode.Week
                            }
                        } else {
                            homeDisplayWeek = conflictWeek
                            homeMode = HomeMode.Week
                        }
                        return@keepConflict
                    }
                    val editorWasOpen = courseEditorRequest != null
                    val editedRetractBounds = courseEditorRequest
                        ?.sourceBoundsInRoot
                        ?.takeIf { homeMode == HomeMode.Week }
                        ?.let { source ->
                            val periodIndexes = state.periods.map { it.periodIndex }
                            val originalStart = dialog.original.periods
                                .map(periodIndexes::indexOf)
                                .filter { it >= 0 }
                                .minOrNull()
                            val editedPositions = dialog.edited.periods
                                .map(periodIndexes::indexOf)
                                .filter { it >= 0 }
                                .distinct()
                                .sorted()
                            val editedStart = editedPositions.firstOrNull()
                            if (originalStart == null || editedStart == null) {
                                null
                            } else {
                                val cardHeightPx = with(density) { weekCardHeight.dp.toPx() }
                                val cardGapPx = with(density) { 4.dp.toPx() }
                                val left = source.left +
                                    (dialog.edited.weekday - dialog.original.weekday) *
                                    (source.width + cardGapPx)
                                val top = source.top +
                                    (editedStart - originalStart) * cardHeightPx
                                val targetHeight = (
                                    cardHeightPx * editedPositions.size.coerceAtLeast(1) -
                                        cardGapPx
                                    ).coerceAtLeast(with(density) { 18.dp.toPx() })
                                Rect(left, top, left + source.width, top + targetHeight)
                            }
                        }
                    courseEditorMotionState.retractTo(editedRetractBounds)
                    if (dialog.singleWeekOnly) {
                        viewModel.updateCourseSingleWeek(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek
                        )
                    } else {
                        viewModel.updateCourse(dialog.edited)
                    }
                    pendingConflictCourseId =
                        dialog.edited.id.takeUnless { dialog.singleWeekOnly }
                    pendingConflictCourseKey = dialog.edited.occurrenceOverrideKey()
                    pendingConflictWeeks = dialog.conflictWeeks
                    dismissHomeDialog()
                    closeCourseEditor()
                    val conflictWeek = dialog.conflictWeeks.first()
                    if (editorWasOpen) {
                        appScope.launch {
                            delay(CourseEditorCloseDurationMillis.toLong() + 32L)
                            homeDisplayWeek = conflictWeek
                            homeMode = HomeMode.Week
                        }
                    } else {
                        homeDisplayWeek = conflictWeek
                        homeMode = HomeMode.Week
                    }
                },
                onReturn = {
                    if (pendingCourseGroupEdit != null || courseEditorRequest != null) {
                        pendingCourseGroupEdit = null
                        dismissHomeDialog()
                    } else {
                        homeDialog = dialog.returnEdit ?: HomeDialog.ApplyCourseEdit(
                            dialog.original,
                            dialog.edited,
                            dialog.targetWeek
                        )
                    }
                }
            )
        } else if (dialog is HomeDialog.ApplyCourseDelete) {
            ApplyCourseDeleteDialog(
                course = dialog.course,
                backdrop = homeDialogBackdrop,
                config = state.config,
                targetWeek = dialog.targetWeek,
                courses = pendingCourseGroupDelete.ifEmpty { listOf(dialog.course) },
                onSingle = {
                    deleteHomeCourses(listOf(dialog.course), dialog.targetWeek)
                },
                onAll = {
                    deleteHomeCourses(pendingCourseGroupDelete.ifEmpty { listOf(dialog.course) }, null)
                },
                onCancel = {
                    pendingCourseGroupDelete = emptyList()
                    dismissHomeDialog()
                }
            )
        } else {
        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.MaterialFormDialog(
            config = state.config,
            visible = homeDialogVisible,
            onDismissRequest = {
                if (dialog is HomeDialog.EditCourse) homeCourseDismissHandler?.invoke() ?: dismissHomeDialog()
                else dismissHomeDialog()
            }
        ) {
            if (dialog is HomeDialog.ImportSchedule) {
                val dialogView = LocalView.current
                DisposableEffect(dialogView) {
                    val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
                    val previousSoftInputMode = dialogWindow?.attributes?.softInputMode
                    dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    onDispose {
                        previousSoftInputMode?.let { dialogWindow.setSoftInputMode(it) }
                    }
                }
            }
            val animatedDialogContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = homeDialogVisible || com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface.current,
                    enter = popEnterTransition(),
                    exit = popExitTransition()
                ) {
                val dialogContent: @Composable () -> Unit = {
                    when (dialog) {
                    is HomeDialog.EditCourse -> {
                        val editor: @Composable () -> Unit = {
                            NormalizedCourseEditorScreen(
                                state = state,
                                initialCourse = dialog.course,
                                onCancel = { dismissHomeDialog() },
                                onDismissHandlerChange = onHomeCourseDismissHandlerChange,
                                onSave = {
                                    if (dialog.course == null) {
                                        viewModel.addCourse(it)
                                        dismissHomeDialog()
                                    } else {
                                        homeDialog = HomeDialog.ApplyCourseEdit(dialog.course, it, dialog.targetWeek ?: effectiveCurrentWeek(state.config))
                                    }
                                },
                                onDelete = {
                                    homeDialog = HomeDialog.ApplyCourseDelete(it, dialog.targetWeek ?: effectiveCurrentWeek(state.config))
                                },
                                backdrop = homeDialogBackdrop,
                                pickerRenderInRootScaffold = dialog.course != null
                            )
                        }
                        if (dialog.course == null) {
                            // Keep the Android dialog itself under its original centered constraints.
                            // Only the editor content owns a local overlay host, so wheel pickers can
                            // cover the form without changing the glass shell's size or position.
                            top.yukonga.miuix.kmp.basic.Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = ComposeColor.Transparent,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                editor()
                            }
                        } else {
                            editor()
                        }
                    }
                    HomeDialog.ImportSchedule -> NormalizedAiManualImportScreen(
                        state = state,
                        backdrop = homeDialogBackdrop,
                        initialFileUri = assistantImportUri,
                        onInitialFileConsumed = { assistantImportUri = null },
                        initialText = assistantImportText,
                        onInitialTextConsumed = { assistantImportText = null },
                        onCancel = { dismissHomeDialog() },
                        onParsed = { draft ->
                            val closing = renderedHomeDialog
                            dismissHomeDialog()
                            appScope.launch {
                                snapshotFlow { renderedHomeDialog !== closing }.first { it }
                                if (homeDialog == null) homeDialog = HomeDialog.ConfirmImport(draft, returnDialog = null)
                            }
                        }
                    )
                    HomeDialog.EduImport -> Unit
                    is HomeDialog.EditWallpaper -> Unit
                    is HomeDialog.ConfirmImport -> Unit
                    is HomeDialog.ApplyCourseEdit -> Unit
                    is HomeDialog.ConfirmCourseConflicts -> Unit
                    is HomeDialog.ApplyCourseDelete -> Unit
                    }
                }
                CenterLiquidDialog(
                        backdrop = homeDialogBackdrop,
                        config = state.config,
                        modifier = Modifier,
                        size = if (dialog is HomeDialog.ImportSchedule) {
                            LiquidDialogSize.Compact
                        } else {
                            LiquidDialogSize.Standard
                        },
                        followGlassContrast = dialog is HomeDialog.ImportSchedule
                    ) {
                        dialogContent()
                    }
                }
            }
            animatedDialogContent()
        }
        }
        }
        }

        SettingsUpdateDialogHost(
            dialog = automaticUpdateDialog,
            backdrop = chromeBackdrop,
            config = state.config,
            onDismiss = { automaticUpdateDialog = null },
            onRetry = {
                automaticUpdateDialog = SettingsUpdateDialog.Checking
                appScope.launch {
                    automaticUpdateDialog = GiteeAppUpdater.checkForUpdate(context, currentVersionName).fold(
                        onSuccess = { result ->
                            GiteeAppUpdater.recordCheckResult(context, result)
                            when (result) {
                                is GiteeUpdateCheckResult.UpdateAvailable -> SettingsUpdateDialog.Available(result.release)
                                is GiteeUpdateCheckResult.UpToDate -> SettingsUpdateDialog.UpToDate(result.release.tagName)
                            }
                        },
                        onFailure = { SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    )
                }
            },
            onDownload = ::downloadAutomaticUpdate,
            onOpenRelease = { release ->
                context.startActivity(Intent(Intent.ACTION_VIEW, release.releaseUrl.toUri()))
            },
            onOpenBackup = {
                automaticUpdateDialog = null
                screen = Screen.Config
            },
            onRequestInstallPermission = {
                automaticInstallPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
            }
        )

    }
    }
    }
    }

}
