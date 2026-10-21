package com.xiaomanjun.sleepdownschedule.feature.schedule.picker

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates

/** Resolve at capture time: a COUI window can move without a Compose layout callback. */
internal class QuickSheetDetailAnchor(
    private val coordinates: LayoutCoordinates,
    private val owner: View,
    private val activityOwner: View
) {
    fun resolve(): Rect? {
        if (!coordinates.isAttached || !owner.isAttachedToWindow) return null
        val bounds = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        val mapped = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val matrix = Matrix()
        owner.transformMatrixToGlobal(matrix)
        matrix.mapRect(mapped)
        val screen = IntArray(2).also(activityOwner::getLocationOnScreen)
        val window = IntArray(2).also(activityOwner::getLocationInWindow)
        mapped.offset((window[0] - screen[0]).toFloat(), (window[1] - screen[1]).toFloat())
        if (!mapped.left.isFinite() || !mapped.top.isFinite() || mapped.width() <= 1f || mapped.height() <= 1f) return null
        return Rect(mapped.left, mapped.top, mapped.right, mapped.bottom)
    }
}
