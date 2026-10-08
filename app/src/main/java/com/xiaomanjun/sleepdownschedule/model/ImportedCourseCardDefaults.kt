package com.xiaomanjun.sleepdownschedule.model

/** Explicit course imports start with the standard card appearance; schedule data stays intact. */
internal fun ScheduleConfigEntity.withImportedCourseCardDefaults(): ScheduleConfigEntity {
    val defaults = defaultConfig(id)
    return copy(
        cardColorArgb = defaults.cardColorArgb,
        cardAlpha = defaults.cardAlpha,
        courseCardBlur = defaults.courseCardBlur,
        courseCardGlassEnabled = defaults.courseCardGlassEnabled,
        courseCardOutlineLightEnabled = true,
        courseCardRefractionStrength = defaults.courseCardRefractionStrength,
        courseCardGaussianBlurEnabled = defaults.courseCardGaussianBlurEnabled,
        courseCardColoredTextEnabled = true,
        courseCardFontScale = defaults.courseCardFontScale,
        courseCardColorMode = defaults.courseCardColorMode,
        courseCardPalette = defaults.courseCardPalette,
        alternateCardColorArgb = defaults.alternateCardColorArgb,
        alternateCardAlpha = defaults.alternateCardAlpha,
        alternateCourseCardBlur = defaults.alternateCourseCardBlur,
        alternateCourseCardFontScale = defaults.alternateCourseCardFontScale,
        alternateCourseCardColorMode = defaults.alternateCourseCardColorMode,
        alternateCourseCardPalette = defaults.alternateCourseCardPalette,
        weekCardHeightDp = defaults.weekCardHeightDp,
        weekCardHeightScale = defaults.weekCardHeightScale,
        weekCardCornerProgress = defaults.weekCardCornerProgress,
        weekCardShowLocation = defaults.weekCardShowLocation,
        weekCardShowTeacher = defaults.weekCardShowTeacher,
        weekCardTextAlignment = defaults.weekCardTextAlignment,
        weekCardContentLayout = defaults.weekCardContentLayout
    )
}
