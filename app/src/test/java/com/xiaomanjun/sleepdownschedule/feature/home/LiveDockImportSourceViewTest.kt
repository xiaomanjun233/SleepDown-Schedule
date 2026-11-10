package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
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

    @Test fun attachedFabCanBeSampledByTheKitsSoftwareCanvas() {
        lateinit var attach: () -> LiveDockImportSourceView
        compose.setContent {
            val activity = checkNotNull(LocalActivity.current)
            val root = LocalView.current
            val layer = rememberGraphicsLayer()
            val density = LocalDensity.current
            val direction = LocalLayoutDirection.current
            Box(Modifier.fillMaxSize())
            SideEffect {
                attach = {
                    val bounds = Rect(0f, 0f, 54f, 54f)
                    layer.record(density, direction, IntSize(54, 54)) { }
                    checkNotNull(LiveDockImportSourceView.attach(activity, root, bounds, layer, bounds, density, direction))
                }
            }
        }
        compose.runOnIdle {
            val view = attach()
            val frame = Bitmap.createBitmap(54, 54, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val sample = Bitmap.createBitmap(54, 54, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(view.isAttachedToWindow)
                assertTrue(view.prepareSoftwareFrame(frame))
                view.draw(Canvas(sample))
                assertEquals(Color.BLUE, sample.getPixel(27, 27))
                assertEquals(Color.TRANSPARENT, sample.getPixel(0, 0))
            } finally {
                view.release()
                frame.recycle()
                sample.recycle()
            }
        }
    }
}
