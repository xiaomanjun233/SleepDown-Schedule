package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.effects.RefractionShape

/** Keep the host's exact outline while supplying its explicit radii to liquid refraction. */
@Composable
internal fun rememberRoundedMaterialShape(outline: Shape, radius: Dp): Shape =
    remember(outline, radius) { RefractionShape(outline, RoundedCornerShape(radius)) }
