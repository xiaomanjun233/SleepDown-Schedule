package com.xiaomanjun.sleepdownschedule.model

/** Explicit course imports start with the standard card appearance; schedule data stays intact. */
internal fun ScheduleConfigEntity.withImportedCourseCardDefaults(): ScheduleConfigEntity {
    val defaults = defaultConfig(id)
    return copy(
        cardColorArgb = defaults.cardColorArgb,
        cardAlpha = 0.5f,
        courseCardBlur = courseCardBlurMaximum(glassEnabled = true) * 0.35f,
        courseCardGlassEnabled = defaults.courseCardGlassEnabled,
        courseCardOutlineLightEnabled = true,
        courseCardRefractionStrength = defaults.courseCardRefractionStrength,
        courseCardGaussianBlurEnabled = defaults.courseCardGaussianBlurEnabled,
        courseCardColoredTextEnabled = true,
        courseCardFontScale = defaults.courseCardFontScale,
        courseCardColorMode = CourseCardColorMode.COLORFUL,
        courseCardPalette = defaults.courseCardPalette,
        alternateCardColorArgb = defaults.alternateCardColorArgb,
        alternateCardAlpha = 0.5f,
        alternateCourseCardBlur = courseCardBlurMaximum(glassEnabled = false) * 0.35f,
        alternateCourseCardFontScale = defaults.alternateCourseCardFontScale,
        alternateCourseCardColorMode = CourseCardColorMode.COLORFUL,
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
