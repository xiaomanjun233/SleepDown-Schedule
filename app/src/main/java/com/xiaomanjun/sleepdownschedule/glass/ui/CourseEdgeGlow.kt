package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices

/** One cached gradient mesh, clipped by the real card outline at the call site. */
internal fun courseEdgeGlowMesh(bounds: Rect, spread: Float, color: Color, strength: Float): Vertices? {
    if (bounds.width <= 0f || bounds.height <= 0f || spread <= 0f || strength <= 0f) return null
    val topFeather = minOf(spread, bounds.width / 2f, bounds.height / 2f)
    val sideFeather = topFeather * 0.35f
    val xs = ((0..16).map { sideFeather * it / 16f } +
        (0..16).map { bounds.width - sideFeather * it / 16f }).distinct().sorted()
    val ys = ((0..16).map { topFeather * it / 16f } +
        (0..16).map { bounds.height * it / 16f }).distinct().sorted()
    fun falloff(distance: Float, feather: Float): Float {
        val t = (distance / feather).coerceIn(0f, 1f)
        return (1f - t) * (1f - t) * (1f + 2f * t)
    }
    val positions = ArrayList<Offset>(xs.size * ys.size)
    val colors = ArrayList<Color>(xs.size * ys.size)
    for (y in ys) {
        val t = ((y / bounds.height - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val verticalFade = (1f - t) * (1f - t) * (1f + 2f * t)
        for (x in xs) {
            val edge = 1f - (1f - falloff(x, sideFeather)) *
                (1f - falloff(bounds.width - x, sideFeather)) * (1f - falloff(y, topFeather))
            positions += bounds.topLeft + Offset(x, y)
            colors += color.copy(alpha = (0.28f * strength * edge * verticalFade).coerceIn(0f, 1f))
        }
    }
    val indices = ArrayList<Int>()
    for (row in 0 until ys.lastIndex) for (column in 0 until xs.lastIndex) {
        val a = row * xs.size + column
        val b = a + 1
        val c = a + xs.size
        val d = c + 1
        // Leave the transparent center out of the draw entirely.
        if (colors[a].alpha == 0f && colors[b].alpha == 0f && colors[c].alpha == 0f && colors[d].alpha == 0f) continue
        indices.addAll(listOf(a, b, c, b, d, c))
    }
    return Vertices(VertexMode.Triangles, positions, positions, colors, indices)
}
