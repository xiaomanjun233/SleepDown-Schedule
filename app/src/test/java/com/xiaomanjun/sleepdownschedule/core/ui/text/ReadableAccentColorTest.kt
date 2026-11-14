package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ReadableAccentColorTest {
    @Test fun paleCourseColorsRemainReadableOnLightAndDarkPreviewSurfaces() {
        for (background in listOf(Color.White, Color(0xFFF8F8FA), Color(0xFF1C1C1E))) {
            for (accent in listOf(Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF5AC8FA),
                    Color(0xFFFF9F0A), Color(0xFFAF52DE), Color.White, Color.Black)) {
                val ink = readableAccentColor(accent, background)
                val ratio = (maxOf(ink.luminance(), background.luminance()) + 0.05f) /
                    (minOf(ink.luminance(), background.luminance()) + 0.05f)
                assertTrue("$accent on $background has contrast $ratio", ratio >= 4.5f)
                assertEquals(1f, ink.alpha, 0f)
            }
        }
    }

    @Test fun anAlreadyReadableAccentKeepsItsOriginalColor() {
        val blue = Color(0xFF174EA6)
        assertEquals(blue, readableAccentColor(blue, Color.White))
    }
}
