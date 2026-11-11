// SleepDown extension to Kyant Backdrop, Apache-2.0.
package com.kyant.backdrop.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Shape

/** Exact host outline for clipping, explicit corner geometry for the rounded refraction SDF. */
class RefractionShape(val outline: Shape, val geometry: CornerBasedShape) : Shape by outline
