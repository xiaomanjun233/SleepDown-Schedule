package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.allViews
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveDockImportSourceViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingUsesTheDisplayedFabAndItsSoftwareFrame() {
        lateinit var activity: Activity
        var openingView: View? = null
        compose.setContent {
            val host = checkNotNull(LocalActivity.current)
            SideEffect { activity = host }
            CouiHomeDock(
                selectedIndex = 1, dark = false, dockDark = false, dockForeground = null,
                dockSampleBrightness = 1f, buttonHidden = false, config = defaultConfig(),
                backdrop = rememberCouiBackdrop(diagnosticName = "original-fab-test"),
                onSelect = {}, onOpenMenu = { openingView = it.view },
            )
        }
        val view = compose.runOnIdle {
            activity.window.decorView.allViews.filterIsInstance<LiveDockImportSourceView>().single()
        }
        compose.onNodeWithContentDescription("导入课表").performClick()
        compose.runOnIdle {
            assertSame(view, openingView)
            assertSame(activity, view.context)
            assertTrue(view.isAttachedToWindow)
            val frame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            val sample = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(view.prepareSoftwareFrame(frame))
                view.draw(Canvas(sample))
                assertEquals(Color.BLUE, sample.getPixel(view.width / 2, view.height / 2))
                assertEquals(Color.TRANSPARENT, sample.getPixel(0, 0))
            } finally {
                view.clearSoftwareFrame()
                frame.recycle()
                sample.recycle()
            }
        }
    }
}
