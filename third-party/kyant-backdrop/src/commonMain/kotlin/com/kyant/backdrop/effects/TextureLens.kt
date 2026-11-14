// SleepDown texture consumer, Apache-2.0. Uses the unchanged upstream optical programs.
package com.kyant.backdrop.effects

import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString

/** The same lens formula can consume a prepared texture without a local RenderEffect layer. */
fun textureLensShaderSource(chromaticAberration: Boolean): String =
    if (chromaticAberration) RoundedRectRefractionWithDispersionShaderString
    else RoundedRectRefractionShaderString
