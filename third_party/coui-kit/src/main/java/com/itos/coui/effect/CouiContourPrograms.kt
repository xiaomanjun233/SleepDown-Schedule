package com.itos.coui.effect

import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size

/** A scene owns its shader instances; equal-sized surfaces reuse the same optical program. */
@RequiresApi(33)
class CouiContourPrograms(private val style: CouiContourStyle) {
    private data class Key(val size: Size, val density: Float, val radius: Float, val weight: Float)
    private val programs = linkedMapOf<Key, CouiContour>()

    fun forGeometry(size: Size, density: Float, radius: Float, weight: Float): CouiContour =
        programs.getOrPut(Key(size, density, radius, weight)) {
            if (programs.size == 64) programs.remove(programs.keys.first())
            CouiContour(style = style)
        }
}
