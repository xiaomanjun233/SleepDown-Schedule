package com.xiaomanjun.sleepdownschedule

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

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
    var cardFontScale by mutableStateOf<Float?>(null)
        private set

    fun update(key: String, candidate: ScheduleConfigEntity) {
        when (key) {
            PersonalizeWallpaperBlurSlider -> wallpaperBlur = candidate.wallpaperBlur
            PersonalizeWallpaperBrightnessSlider -> wallpaperBrightness = candidate.wallpaperBrightness
            PersonalizeCardAlphaSlider -> cardAlpha = candidate.cardAlpha
            PersonalizeCardBlurSlider -> cardBlur = candidate.courseCardBlur
            PersonalizeCardFontSlider -> cardFontScale = candidate.courseCardFontScale
        }
    }

    fun clear(key: String) {
        when (key) {
            PersonalizeWallpaperBlurSlider -> wallpaperBlur = null
            PersonalizeWallpaperBrightnessSlider -> wallpaperBrightness = null
            PersonalizeCardAlphaSlider -> cardAlpha = null
            PersonalizeCardBlurSlider -> cardBlur = null
            PersonalizeCardFontSlider -> cardFontScale = null
        }
    }

    fun clearAll() {
        wallpaperBlur = null
        wallpaperBrightness = null
        cardAlpha = null
        cardBlur = null
        cardFontScale = null
    }
}

internal val LocalPersonalizationPreview =
    staticCompositionLocalOf<PersonalizationPreviewState?> { null }
