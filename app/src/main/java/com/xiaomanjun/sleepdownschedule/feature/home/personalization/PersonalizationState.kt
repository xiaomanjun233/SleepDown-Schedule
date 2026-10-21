package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWallpaperBlurSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWallpaperBrightnessSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWallpaperContentChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWeekHeightSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWeekLocationChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWeekTeacherChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWeekLayoutChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWeekCornerSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardColorChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardAlphaSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardBlurSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardRefractionSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardFontSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardGlassChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardOutlineLightChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardGaussianBlurChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardColoredTextChange
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.switchCourseCardGlassMode
internal fun resolveActivePersonalizationSlider(
    currentKey: String?,
    eventKey: String,
    active: Boolean
): String? = when {
    active -> eventKey
    currentKey == eventKey -> null
    else -> currentKey
}

internal fun mergePersonalizationCandidate(
    current: ScheduleConfigEntity,
    candidate: ScheduleConfigEntity,
    changeKey: String
): ScheduleConfigEntity = when (changeKey) {
    PersonalizeWallpaperContentChange -> current.copy(
        wallpaperUri = candidate.wallpaperUri,
        wallpaperPortraitCenterX = candidate.wallpaperPortraitCenterX,
        wallpaperPortraitCenterY = candidate.wallpaperPortraitCenterY,
        wallpaperPortraitScale = candidate.wallpaperPortraitScale,
        wallpaperLandscapeCenterX = candidate.wallpaperLandscapeCenterX,
        wallpaperLandscapeCenterY = candidate.wallpaperLandscapeCenterY,
        wallpaperLandscapeScale = candidate.wallpaperLandscapeScale,
        wallpaperSourceWidth = candidate.wallpaperSourceWidth,
        wallpaperSourceHeight = candidate.wallpaperSourceHeight,
        homeTextLight = candidate.homeTextLight
    )
    PersonalizeWallpaperBlurSlider -> current.copy(wallpaperBlur = candidate.wallpaperBlur)
    PersonalizeWallpaperBrightnessSlider -> current.copy(
        wallpaperBrightness = candidate.wallpaperBrightness
    )
    PersonalizeWeekHeightSlider -> current.copy(
        weekCardHeightDp = null,
        weekCardHeightScale = candidate.weekCardHeightScale
    )
    PersonalizeWeekCornerSlider -> current.copy(
        weekCardCornerProgress = candidate.weekCardCornerProgress
    )
    PersonalizeWeekLocationChange -> current.copy(weekCardShowLocation = candidate.weekCardShowLocation)
    PersonalizeWeekTeacherChange -> current.copy(weekCardShowTeacher = candidate.weekCardShowTeacher)
    PersonalizeWeekLayoutChange -> current.copy(
        weekCardContentLayout = candidate.weekCardContentLayout, weekCardTextAlignment = candidate.weekCardTextAlignment)
    PersonalizeCardColorChange -> current.copy(
        cardColorArgb = candidate.cardColorArgb,
        courseCardColorMode = candidate.courseCardColorMode,
        courseCardPalette = if (candidate.courseCardColorMode == CourseCardColorMode.COLORFUL) {
            candidate.courseCardPalette
        } else {
            current.courseCardPalette
        }
    )
    PersonalizeCardAlphaSlider -> current.copy(cardAlpha = candidate.cardAlpha)
    PersonalizeCardBlurSlider -> current.copy(courseCardBlur = candidate.courseCardBlur)
    PersonalizeCardRefractionSlider -> current.copy(
        courseCardRefractionStrength = candidate.courseCardRefractionStrength
    )
    PersonalizeCardFontSlider -> current.copy(courseCardFontScale = candidate.courseCardFontScale)
    PersonalizeCardGlassChange -> current.switchCourseCardGlassMode(
        candidate.courseCardGlassEnabled
    )
    PersonalizeCardOutlineLightChange -> current.copy(
        courseCardOutlineLightEnabled = candidate.courseCardOutlineLightEnabled
    )
    PersonalizeCardGaussianBlurChange -> current.copy(
        courseCardGaussianBlurEnabled = candidate.courseCardGaussianBlurEnabled
    )
    PersonalizeCardColoredTextChange -> current.copy(
        courseCardColoredTextEnabled = candidate.courseCardColoredTextEnabled
    )
    else -> current
}
