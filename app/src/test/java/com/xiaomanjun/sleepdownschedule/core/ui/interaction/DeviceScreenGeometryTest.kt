package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.app.Activity
import android.app.Application
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceScreenGeometryTest {
    @Test fun mapsNestedViewsWithScrollAndScalingOnAndroidNine() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val parent = FrameLayout(activity).apply {
            pivotX = 0f
            pivotY = 0f
            scaleX = 1.5f
            scaleY = 0.5f
        }
        val child = View(activity).apply {
            pivotX = 0f
            scaleX = 2f
        }
        parent.addView(child)
        root.addView(parent)
        activity.setContentView(root)
        root.layout(0, 0, 300, 300)
        parent.layout(30, 40, 230, 240)
        child.layout(8, 12, 28, 22)
        val beforeScroll = RectF(0f, 0f, 20f, 10f)
        child.localToScreenMatrix().mapRect(beforeScroll)

        parent.scrollTo(8, 6)
        val afterScroll = RectF(0f, 0f, 20f, 10f)
        child.localToScreenMatrix().mapRect(afterScroll)
        val location = IntArray(2).also(child::getLocationOnScreen)

        assertEquals(location[0].toFloat(), afterScroll.left, 0.01f)
        assertEquals(location[1].toFloat(), afterScroll.top, 0.01f)
        assertEquals(60f, afterScroll.width(), 0.01f)
        assertEquals(5f, afterScroll.height(), 0.01f)
        assertEquals(12f, beforeScroll.left - afterScroll.left, 1f)
        assertEquals(3f, beforeScroll.top - afterScroll.top, 1f)
    }
}
