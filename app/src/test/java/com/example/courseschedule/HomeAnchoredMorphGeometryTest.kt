package com.example.courseschedule

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnchoredMorphGeometryTest {
    private val source = Rect(880f, 48f, 922f, 90f)
    private val target = Rect(606f, 104f, 808f, 272f)

    @Test
    fun openingStartsAtSourceButton() {
        val geometry = geometry(progress = 0f)

        assertRectEquals(source, geometry.rect)
        assertEquals(1f, geometry.sourceAlpha, Tolerance)
        assertEquals(0f, geometry.contentAlpha, Tolerance)
    }

    @Test
    fun openingPinchesToTenPixelDropletAfterFalling() {
        val geometry = geometry(progress = HomeAnchoredMorphPinchFraction)

        assertEquals(10f, geometry.rect.width, Tolerance)
        assertEquals(10f, geometry.rect.height, Tolerance)
        assertEquals(source.center.x, geometry.rect.center.x, Tolerance)
        assertTrue(geometry.rect.center.y > source.center.y)
    }

    @Test
    fun openingFinishesAtTargetPanel() {
        assertRectEquals(target, geometry(progress = 1f).rect)
    }

    @Test
    fun closingReturnsToSameAnchor() {
        assertRectEquals(target, geometry(progress = 1f, closing = true).rect)
        assertRectEquals(source, geometry(progress = 0f, closing = true).rect)
    }

    @Test
    fun addTargetUsesSpecifiedSizeAndClampsToRoot() {
        val root = IntSize(1080, 1920)
        val result = homeAddMenuTargetRect(
            source = Rect(1010f, 80f, 1052f, 122f),
            rootSize = root,
            density = 2f,
            actionCount = 3
        )

        assertEquals(404f, result.width, Tolerance)
        assertEquals(336f, result.height, Tolerance)
        assertInBounds(result, root, margin = 24f)
    }

    @Test
    fun personalizeTargetAndOversizedTargetsStayInBounds() {
        val root = IntSize(1080, 2400)
        val personalize = homePersonalizeTargetRect(root, density = 3f)
        val clamped = clampHomeMorphTarget(
            target = Rect(-100f, -200f, 1400f, 2800f),
            rootSize = root,
            marginPx = 36f
        )

        assertEquals(934.8f, personalize.width, Tolerance)
        assertInBounds(personalize, root, margin = 36f)
        assertEquals(1008f, clamped.width, Tolerance)
        assertEquals(2328f, clamped.height, Tolerance)
        assertInBounds(clamped, root, margin = 36f)
    }

    private fun geometry(
        progress: Float,
        closing: Boolean = false
    ): HomeAnchoredMorphGeometry = homeAnchoredMorphGeometry(
        source = source,
        target = target,
        rawProgress = progress,
        closing = closing,
        pinchDiameterPx = 10f,
        minimumDropPx = 36f,
        maximumDropPx = 72f,
        maximumArcPx = 48f,
        targetCornerRadiusPx = 26f
    )

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, Tolerance)
        assertEquals(expected.top, actual.top, Tolerance)
        assertEquals(expected.right, actual.right, Tolerance)
        assertEquals(expected.bottom, actual.bottom, Tolerance)
    }

    private fun assertInBounds(rect: Rect, root: IntSize, margin: Float) {
        assertTrue(rect.left >= margin - Tolerance)
        assertTrue(rect.top >= margin - Tolerance)
        assertTrue(rect.right <= root.width - margin + Tolerance)
        assertTrue(rect.bottom <= root.height - margin + Tolerance)
    }

    private companion object {
        const val Tolerance = 0.01f
    }
}
