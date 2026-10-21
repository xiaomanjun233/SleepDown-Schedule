package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

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

/**
 * Lightweight, transient values used while a personalization slider is being dragged.
 *
 * These values intentionally do not form a ScheduleConfigEntity. Consumers subscribe only to
 * the property they draw, so a pointer sample cannot invalidate the complete HomeScreen tree.
 */
@Stable
internal class PersonalizationPreviewState {
    var wallpaperBlur by mutableStateOf<Float?>(null)
        private set
    var wallpaperBrightness by mutableStateOf<Float?>(null)
        private set
    var cardAlpha by mutableStateOf<Float?>(null)
        private set
    var cardBlur by mutableStateOf<Float?>(null)
        private set
    var cardRefractionStrength by mutableStateOf<Float?>(null)
        private set
    var cardFontScale by mutableStateOf<Float?>(null)
        private set
    var weekCardCornerProgress by mutableStateOf<Float?>(null)
        private set

    fun update(key: String, candidate: ScheduleConfigEntity) {
        when (key) {
            PersonalizeWallpaperBlurSlider -> wallpaperBlur = candidate.wallpaperBlur
            PersonalizeWallpaperBrightnessSlider -> wallpaperBrightness = candidate.wallpaperBrightness
            PersonalizeCardAlphaSlider -> cardAlpha = candidate.cardAlpha
            PersonalizeCardBlurSlider -> cardBlur = candidate.courseCardBlur
            PersonalizeCardRefractionSlider -> cardRefractionStrength = candidate.courseCardRefractionStrength
            PersonalizeCardFontSlider -> cardFontScale = candidate.courseCardFontScale
            PersonalizeWeekCornerSlider -> weekCardCornerProgress = candidate.weekCardCornerProgress
        }
    }

    fun clear(key: String) {
        when (key) {
            PersonalizeWallpaperBlurSlider -> wallpaperBlur = null
            PersonalizeWallpaperBrightnessSlider -> wallpaperBrightness = null
            PersonalizeCardAlphaSlider -> cardAlpha = null
            PersonalizeCardBlurSlider -> cardBlur = null
            PersonalizeCardRefractionSlider -> cardRefractionStrength = null
            PersonalizeCardFontSlider -> cardFontScale = null
            PersonalizeWeekCornerSlider -> weekCardCornerProgress = null
        }
    }

    fun clearAll() {
        wallpaperBlur = null
        wallpaperBrightness = null
        cardAlpha = null
        cardBlur = null
        cardRefractionStrength = null
        cardFontScale = null
        weekCardCornerProgress = null
    }
}

internal val LocalPersonalizationPreview =
    staticCompositionLocalOf<PersonalizationPreviewState?> { null }
