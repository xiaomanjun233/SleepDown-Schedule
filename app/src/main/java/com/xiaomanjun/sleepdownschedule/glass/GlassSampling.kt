package com.xiaomanjun.sleepdownschedule.glass


internal fun GlassEffectFrame.decorationOnly(): GlassEffectFrame = copy(
    blur = null,
    lensHeight = null,
    lensAmount = null,
    useVibrancy = false
)

/** Decoration and placement changes must not invalidate the observed blur/refraction inputs. */
internal fun GlassEffectFrame.materialEffectsOnly(): GlassEffectFrame = copy(
    highlight = null,
    shadowAlpha = null,
    innerShadow = null,
    layerScale = null
)
