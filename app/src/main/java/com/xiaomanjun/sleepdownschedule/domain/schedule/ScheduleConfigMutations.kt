package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*

/**
 * Reapplies only the values edited by the General settings page onto the latest database row.
 * The page saves eagerly, so an older database emission must not roll a newer local tap back,
 * while notification values changed elsewhere must not be overwritten by the General page.
 */
internal fun ScheduleConfigEntity.withGeneralSettingsFrom(draft: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        followSystemDarkMode = draft.followSystemDarkMode,
        darkMode = draft.darkMode,
        dockAlignment = draft.dockAlignment,
        defaultWallpaperStyle = draft.defaultWallpaperStyle,
        defaultHomeMode = draft.defaultHomeMode,
        hideFromRecents = draft.hideFromRecents,
        autoCheckUpdates = draft.autoCheckUpdates
    )
}

/** Applies only the value owned by the Liquid Glass detail page. */
internal fun ScheduleConfigEntity.withHomeChromeBlurScale(value: Float): ScheduleConfigEntity {
    return copy(homeChromeBlurScale = normalizedHomeChromeBlurScale(value))
}

internal fun ScheduleConfigEntity.withNotificationSettingsFrom(draft: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        notificationLeadMinutes = draft.notificationLeadMinutes,
        notificationsEnabled = draft.notificationsEnabled,
        notificationMode = draft.notificationMode,
        liveUpdateChipTextMode = draft.liveUpdateChipTextMode,
        liveUpdateActionsEnabled = draft.liveUpdateActionsEnabled
    )
}

/**
 * Applies only fields that the caller actually changed. Settings are saved asynchronously, so
 * replacing the complete row with an older UI snapshot could otherwise roll back unrelated edits.
 * The receiver is always the latest database row and keeps its schedule id.
 */
internal fun ScheduleConfigEntity.withChangesFrom(
    original: ScheduleConfigEntity,
    updated: ScheduleConfigEntity
): ScheduleConfigEntity {
    fun <T> changed(oldValue: T, newValue: T, currentValue: T): T =
        if (oldValue != newValue) newValue else currentValue

    return copy(
        totalWeeks = changed(original.totalWeeks, updated.totalWeeks, totalWeeks),
        currentWeek = changed(original.currentWeek, updated.currentWeek, currentWeek),
        notificationLeadMinutes = changed(
            original.notificationLeadMinutes,
            updated.notificationLeadMinutes,
            notificationLeadMinutes
        ),
        termStartDate = changed(original.termStartDate, updated.termStartDate, termStartDate),
        autoCurrentWeek = changed(original.autoCurrentWeek, updated.autoCurrentWeek, autoCurrentWeek),
        termState = changed(original.termState, updated.termState, termState),
        notificationsEnabled = changed(
            original.notificationsEnabled,
            updated.notificationsEnabled,
            notificationsEnabled
        ),
        notificationMode = changed(original.notificationMode, updated.notificationMode, notificationMode),
        wallpaperUri = changed(original.wallpaperUri, updated.wallpaperUri, wallpaperUri),
        wallpaperBlur = changed(original.wallpaperBlur, updated.wallpaperBlur, wallpaperBlur),
        wallpaperBrightness = changed(
            original.wallpaperBrightness,
            updated.wallpaperBrightness,
            wallpaperBrightness
        ),
        wallpaperPortraitCenterX = changed(
            original.wallpaperPortraitCenterX,
            updated.wallpaperPortraitCenterX,
            wallpaperPortraitCenterX
        ),
        wallpaperPortraitCenterY = changed(
            original.wallpaperPortraitCenterY,
            updated.wallpaperPortraitCenterY,
            wallpaperPortraitCenterY
        ),
        wallpaperPortraitScale = changed(
            original.wallpaperPortraitScale,
            updated.wallpaperPortraitScale,
            wallpaperPortraitScale
        ),
        wallpaperLandscapeCenterX = changed(
            original.wallpaperLandscapeCenterX,
            updated.wallpaperLandscapeCenterX,
            wallpaperLandscapeCenterX
        ),
        wallpaperLandscapeCenterY = changed(
            original.wallpaperLandscapeCenterY,
            updated.wallpaperLandscapeCenterY,
            wallpaperLandscapeCenterY
        ),
        wallpaperLandscapeScale = changed(
            original.wallpaperLandscapeScale,
            updated.wallpaperLandscapeScale,
            wallpaperLandscapeScale
        ),
        wallpaperSourceWidth = changed(
            original.wallpaperSourceWidth,
            updated.wallpaperSourceWidth,
            wallpaperSourceWidth
        ),
        wallpaperSourceHeight = changed(
            original.wallpaperSourceHeight,
            updated.wallpaperSourceHeight,
            wallpaperSourceHeight
        ),
        cardColorArgb = changed(original.cardColorArgb, updated.cardColorArgb, cardColorArgb),
        cardAlpha = changed(original.cardAlpha, updated.cardAlpha, cardAlpha),
        courseCardBlur = changed(original.courseCardBlur, updated.courseCardBlur, courseCardBlur),
        courseCardGlassEnabled = changed(
            original.courseCardGlassEnabled,
            updated.courseCardGlassEnabled,
            courseCardGlassEnabled
        ),
        courseCardOutlineLightEnabled = changed(
            original.courseCardOutlineLightEnabled,
            updated.courseCardOutlineLightEnabled,
            courseCardOutlineLightEnabled
        ),
        courseCardRefractionStrength = changed(
            original.courseCardRefractionStrength,
            updated.courseCardRefractionStrength,
            courseCardRefractionStrength
        ),
        courseCardGaussianBlurEnabled = changed(
            original.courseCardGaussianBlurEnabled,
            updated.courseCardGaussianBlurEnabled,
            courseCardGaussianBlurEnabled
        ),
        courseCardFontScale = changed(
            original.courseCardFontScale,
            updated.courseCardFontScale,
            courseCardFontScale
        ),
        courseCardColorMode = changed(
            original.courseCardColorMode,
            updated.courseCardColorMode,
            courseCardColorMode
        ),
        courseCardPalette = changed(
            original.courseCardPalette,
            updated.courseCardPalette,
            courseCardPalette
        ),
        alternateCardColorArgb = changed(
            original.alternateCardColorArgb,
            updated.alternateCardColorArgb,
            alternateCardColorArgb
        ),
        alternateCardAlpha = changed(
            original.alternateCardAlpha,
            updated.alternateCardAlpha,
            alternateCardAlpha
        ),
        alternateCourseCardBlur = changed(
            original.alternateCourseCardBlur,
            updated.alternateCourseCardBlur,
            alternateCourseCardBlur
        ),
        alternateCourseCardFontScale = changed(
            original.alternateCourseCardFontScale,
            updated.alternateCourseCardFontScale,
            alternateCourseCardFontScale
        ),
        alternateCourseCardColorMode = changed(
            original.alternateCourseCardColorMode,
            updated.alternateCourseCardColorMode,
            alternateCourseCardColorMode
        ),
        alternateCourseCardPalette = changed(
            original.alternateCourseCardPalette,
            updated.alternateCourseCardPalette,
            alternateCourseCardPalette
        ),
        weekCardHeightDp = changed(original.weekCardHeightDp, updated.weekCardHeightDp, weekCardHeightDp),
        weekCardHeightScale = changed(
            original.weekCardHeightScale,
            updated.weekCardHeightScale,
            weekCardHeightScale
        ),
        weekCardCornerProgress = changed(
            original.weekCardCornerProgress,
            updated.weekCardCornerProgress,
            weekCardCornerProgress
        ),
        homeTextLight = changed(original.homeTextLight, updated.homeTextLight, homeTextLight),
        homeChromeBlurScale = changed(
            original.homeChromeBlurScale,
            updated.homeChromeBlurScale,
            homeChromeBlurScale
        ),
        homeChromeSamplingScale = changed(
            original.homeChromeSamplingScale,
            updated.homeChromeSamplingScale,
            homeChromeSamplingScale
        ),
        followSystemDarkMode = changed(
            original.followSystemDarkMode,
            updated.followSystemDarkMode,
            followSystemDarkMode
        ),
        darkMode = changed(original.darkMode, updated.darkMode, darkMode),
        defaultWallpaperStyle = changed(
            original.defaultWallpaperStyle,
            updated.defaultWallpaperStyle,
            defaultWallpaperStyle
        ),
        hideEmptyWeekends = changed(
            original.hideEmptyWeekends,
            updated.hideEmptyWeekends,
            hideEmptyWeekends
        ),
        dockAlignment = changed(original.dockAlignment, updated.dockAlignment, dockAlignment),
        defaultHomeMode = changed(original.defaultHomeMode, updated.defaultHomeMode, defaultHomeMode),
        liveUpdateActionsEnabled = changed(
            original.liveUpdateActionsEnabled,
            updated.liveUpdateActionsEnabled,
            liveUpdateActionsEnabled
        ),
        liveUpdateChipTextMode = changed(
            original.liveUpdateChipTextMode,
            updated.liveUpdateChipTextMode,
            liveUpdateChipTextMode
        ),
        classDurationMinutes = changed(
            original.classDurationMinutes,
            updated.classDurationMinutes,
            classDurationMinutes
        ),
        breakDurationMinutes = changed(
            original.breakDurationMinutes,
            updated.breakDurationMinutes,
            breakDurationMinutes
        ),
        morningPeriodCount = changed(
            original.morningPeriodCount,
            updated.morningPeriodCount,
            morningPeriodCount
        ),
        noonPeriodCount = changed(original.noonPeriodCount, updated.noonPeriodCount, noonPeriodCount),
        afternoonPeriodCount = changed(
            original.afternoonPeriodCount,
            updated.afternoonPeriodCount,
            afternoonPeriodCount
        ),
        eveningPeriodCount = changed(
            original.eveningPeriodCount,
            updated.eveningPeriodCount,
            eveningPeriodCount
        ),
        hideFromRecents = changed(original.hideFromRecents, updated.hideFromRecents, hideFromRecents),
        autoCheckUpdates = changed(original.autoCheckUpdates, updated.autoCheckUpdates, autoCheckUpdates)
    )
}

/**
 * Replaces the complete set of fields owned by the personalization UI. Each queued save carries
 * a self-contained snapshot, so conflating rapid edits cannot lose a toggle or mistake a value
 * returning to its original state for "unchanged". Non-personalization settings stay database-led.
 */
internal fun ScheduleConfigEntity.withPersonalizationFrom(
    updated: ScheduleConfigEntity
): ScheduleConfigEntity = copy(
    wallpaperUri = updated.wallpaperUri,
    wallpaperBlur = updated.wallpaperBlur,
    wallpaperBrightness = updated.wallpaperBrightness,
    wallpaperPortraitCenterX = updated.wallpaperPortraitCenterX,
    wallpaperPortraitCenterY = updated.wallpaperPortraitCenterY,
    wallpaperPortraitScale = updated.wallpaperPortraitScale,
    wallpaperLandscapeCenterX = updated.wallpaperLandscapeCenterX,
    wallpaperLandscapeCenterY = updated.wallpaperLandscapeCenterY,
    wallpaperLandscapeScale = updated.wallpaperLandscapeScale,
    wallpaperSourceWidth = updated.wallpaperSourceWidth,
    wallpaperSourceHeight = updated.wallpaperSourceHeight,
    cardColorArgb = updated.cardColorArgb,
    cardAlpha = updated.cardAlpha,
    courseCardBlur = updated.courseCardBlur,
    courseCardGlassEnabled = updated.courseCardGlassEnabled,
    courseCardOutlineLightEnabled = updated.courseCardOutlineLightEnabled,
    courseCardRefractionStrength = updated.courseCardRefractionStrength,
    courseCardGaussianBlurEnabled = updated.courseCardGaussianBlurEnabled,
    courseCardFontScale = updated.courseCardFontScale,
    courseCardColorMode = updated.courseCardColorMode,
    courseCardPalette = updated.courseCardPalette,
    alternateCardColorArgb = updated.alternateCardColorArgb,
    alternateCardAlpha = updated.alternateCardAlpha,
    alternateCourseCardBlur = updated.alternateCourseCardBlur,
    alternateCourseCardFontScale = updated.alternateCourseCardFontScale,
    alternateCourseCardColorMode = updated.alternateCourseCardColorMode,
    alternateCourseCardPalette = updated.alternateCourseCardPalette,
    weekCardHeightDp = updated.weekCardHeightDp,
    weekCardHeightScale = updated.weekCardHeightScale,
    weekCardCornerProgress = updated.weekCardCornerProgress,
    homeTextLight = updated.homeTextLight
)

internal fun ScheduleConfigEntity.switchCourseCardGlassMode(enabled: Boolean): ScheduleConfigEntity {
    if (enabled == courseCardGlassEnabled) return this
    return copy(
        cardColorArgb = alternateCardColorArgb,
        cardAlpha = alternateCardAlpha,
        courseCardBlur = alternateCourseCardBlur.coerceIn(0f, courseCardBlurMaximum(enabled)),
        courseCardGlassEnabled = enabled,
        courseCardFontScale = alternateCourseCardFontScale,
        courseCardColorMode = alternateCourseCardColorMode,
        courseCardPalette = alternateCourseCardPalette,
        alternateCardColorArgb = cardColorArgb,
        alternateCardAlpha = cardAlpha,
        alternateCourseCardBlur = courseCardBlur,
        alternateCourseCardFontScale = courseCardFontScale,
        alternateCourseCardColorMode = courseCardColorMode,
        alternateCourseCardPalette = courseCardPalette
    )
}
