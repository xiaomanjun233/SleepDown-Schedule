package com.xiaomanjun.sleepdownschedule.feature.widget

import android.app.Application
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.feature.widget.providers.*
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun backgroundSwitchUpdatesPinnedInstancesWithoutDiscardingTheirImagesOrOtherTypes() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = WidgetAppearanceRepository(context, database)
            val type = WidgetAppearanceVariant.COURSES_LARGE
            val first = WidgetAppearanceEntity.defaults(type).copy(enabled = true, wallpaperUri = "file:///default.jpg")
            val pinned = first.copy(appWidgetId = 42, wallpaperUri = "file:///custom.jpg", centerX = 0.3f)
            val other = first.copy(variant = WidgetAppearanceVariant.COURSES_SQUARE.key, appWidgetId = 43)
            database.widgetAppearanceDao().upsert(first)
            database.widgetAppearanceDao().upsert(pinned)
            database.widgetAppearanceDao().upsert(other)
            repo.setBackgroundEnabled(type, false)
            assertFalse(repo.get(type, 0).enabled)
            assertFalse(repo.get(type, 42).enabled)
            assertEquals("file:///custom.jpg", repo.get(type, 42).wallpaperUri)
            assertEquals(0.3f, repo.get(type, 42).centerX)
            assertTrue(repo.get(WidgetAppearanceVariant.COURSES_SQUARE, 43).enabled)
            repo.setBackgroundEnabled(type, true)
            assertTrue(repo.get(type, 42).enabled)
        } finally { database.close() }
    }

    @Test fun widgetThemeAlwaysUsesSystemRegardlessOfAppPreference() {
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (night) "night" else "notnight")
            for (appDark in listOf(false, true)) for (follow in listOf(false, true)) {
                assertEquals(night, MiuixTodayWidgetRenderer.usesDarkTheme(context,
                    defaultConfig().copy(darkMode = appDark, followSystemDarkMode = follow)))
            }
        }
    }

    @Test fun everyPinPreviewHasIntrinsicSizeWithoutLauncherBounds() {
        val state = AppState()
        for (type in ActiveWidgetAppearanceVariants) {
            val appearance = WidgetAppearanceEntity.defaults(type)
            val size = canonicalWidgetPreviewSize(type)
            val dynamic = when (type) {
                WidgetAppearanceVariant.COURSES_LARGE -> MiuixTodayWidgetRenderer.buildViews(context, state, TodayWidgetVariant.LARGE, appearance, size)
                WidgetAppearanceVariant.COURSES_SQUARE -> MiuixTodayWidgetRenderer.buildViews(context, state, TodayWidgetVariant.SQUARE, appearance, size)
                WidgetAppearanceVariant.TODAY_TOMORROW -> TodayTomorrowWidgetRenderer.buildViews(context, state, appearance, size)
                WidgetAppearanceVariant.TODAY_ASSISTANT -> TodayAssistantWidgetRenderer.buildViews(context, state, null, appearance, size)
                else -> error("Not exposed")
            }
            for (source in listOf(dynamic, null)) {
                val preview = widgetPinPreview(context, type, source).apply(context, FrameLayout(context))
                preview.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                assertTrue("$type width", preview.measuredWidth > 0)
                assertTrue("$type height", preview.measuredHeight > 0)
                assertEquals(type.canonicalAspect, preview.measuredWidth.toFloat() / preview.measuredHeight, 0.02f)
                val image = (preview as android.widget.ImageView).drawable as android.graphics.drawable.BitmapDrawable
                val bitmap = image.bitmap
                assertTrue("$type preview must contain visible pixels", android.graphics.Color.alpha(
                    bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
            }
        }
    }
}
