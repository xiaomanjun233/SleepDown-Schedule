package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class WebThemeColorExtractorTest {
    private val extractor = WebThemeColorExtractor()

    @Test fun solidPagesKeepTheirColorIncludingWhiteAndBlack() {
        for (color in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF2699CE.toInt())) {
            assertEquals(color, extractor.extract(IntArray(64 * 24) { color }))
        }
    }

    @Test fun aSingleWhiteRowDoesNotOverrideTheColoredRowsBelowIt() {
        val blue = 0xFF2060C0.toInt()
        val pixels = IntArray(64 * 24) { if (it < 64) 0xFFFFFFFF.toInt() else blue }
        val result = requireNotNull(extractor.extract(pixels))
        assertTrue(channel(result, 16) < 50)
        assertTrue(channel(result, 8) in 96..105)
        assertTrue(channel(result, 0) in 192..196)
    }

    @Test fun largeRegionsFuseInLinearLightWithoutPickingAnArbitraryWinner() {
        val pixels = IntArray(64 * 24) { if (it < 64 * 12) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        assertEquals(0xFFBC00BC.toInt(), extractor.extract(pixels))
        assertEquals(extractor.extract(pixels), extractor.extract(pixels.reversedArray()))
    }

    @Test fun sparseBlackTextHasLessInfluenceThanItsAreaAverage() {
        val result = requireNotNull(extractor.extract(IntArray(1000) {
            if (it < 100) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }))
        assertTrue(channel(result, 16) > 245)
        assertEquals(channel(result, 16), channel(result, 8))
        assertEquals(channel(result, 8), channel(result, 0))
    }

    @Test fun transparentFramesKeepThePreviousColorAtTheCaller() {
        assertNull(extractor.extract(intArrayOf()))
        assertNull(extractor.extract(IntArray(100) { 0x00FF0000 }))
        assertEquals(0xFF2060C0.toInt(), extractor.extract(intArrayOf(0xFF2060C0.toInt(), 0x00FFFFFF)))
    }

    @Test fun translucentPixelsHaveProportionallyLessInfluence() {
        val result = requireNotNull(extractor.extract(intArrayOf(0xFFFF0000.toInt(), 0x200000FF)))
        assertTrue(channel(result, 16) > 240)
        assertTrue(channel(result, 0) < 80)
    }

    @Test fun reusingTheAccumulatorDoesNotCarryColorsBetweenPages() {
        extractor.extract(IntArray(1000) { 0xFFFF0000.toInt() })
        assertEquals(0xFF0000FF.toInt(), extractor.extract(intArrayOf(0xFF0000FF.toInt())))
        assertNull(extractor.extract(intArrayOf(0)))
    }

    private fun channel(color: Int, shift: Int) = (color ushr shift) and 255
}
