package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.switchCourseCardGlassMode

internal const val PersonalizeWallpaperBlurSlider = "wallpaper-blur"
internal const val PersonalizeWallpaperBrightnessSlider = "wallpaper-brightness"
internal const val PersonalizeWallpaperContentChange = "wallpaper-content"
internal const val PersonalizeWeekHeightSlider = "week-height"
internal const val PersonalizeWeekLocationChange = "week-location"
internal const val PersonalizeWeekTeacherChange = "week-teacher"
internal const val PersonalizeWeekLayoutChange = "week-layout"
internal const val PersonalizeWeekCornerSlider = "week-corner"
internal const val PersonalizeCardColorChange = "card-color"
internal const val PersonalizeCardAlphaSlider = "card-alpha"
internal const val PersonalizeCardBlurSlider = "card-blur"
internal const val PersonalizeCardRefractionSlider = "card-refraction"
internal const val PersonalizeNativeCardTintSlider = "native-card-tint"
internal const val PersonalizeNativeCardBlurSlider = "native-card-blur"
internal const val PersonalizeCardFontSlider = "card-font"
internal const val PersonalizeCardGlassChange = "card-glass"
internal const val PersonalizeCardOutlineLightChange = "card-outline-light"
internal const val PersonalizeCardGaussianBlurChange = "card-gaussian-blur"
internal const val PersonalizeCardColoredTextChange = "card-colored-text"

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
