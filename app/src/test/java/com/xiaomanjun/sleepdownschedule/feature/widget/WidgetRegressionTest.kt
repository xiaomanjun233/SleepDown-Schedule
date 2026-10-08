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

    @Test fun legacyBoundsKeepPortraitAndLandscapePairsInsteadOfInventingAShortPortraitWidget() {
        val options = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 560)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
        }
        assertEquals(listOf(WidgetRenderSize(320, 180), WidgetRenderSize(560, 110)),
            legacyWidgetRenderSizes(options, WidgetAppearanceVariant.COURSES_LARGE))
        options.remove(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        options.remove(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        assertEquals(listOf(WidgetRenderSize(320, 110)),
            legacyWidgetRenderSizes(options, WidgetAppearanceVariant.COURSES_LARGE))
    }

    @Test @Config(sdk = [30]) fun legacyHostReceivesBothOrientationLayouts() {
        fun remote(title: String) = android.widget.RemoteViews(context.packageName,
            com.xiaomanjun.sleepdownschedule.R.layout.widget_today_courses_miuix).apply {
            setTextViewText(com.xiaomanjun.sleepdownschedule.R.id.widget_title, title)
        }
        val views = widgetResponsiveViews(listOf(WidgetRenderSize(320, 180) to remote("portrait"),
            WidgetRenderSize(560, 110) to remote("landscape")))
        for ((orientation, label) in listOf(Configuration.ORIENTATION_PORTRAIT to "portrait",
            Configuration.ORIENTATION_LANDSCAPE to "landscape")) {
            val local = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                this.orientation = orientation
            })
            val root = views.apply(local, FrameLayout(local))
            assertEquals(label, root.findViewById<android.widget.TextView>(
                com.xiaomanjun.sleepdownschedule.R.id.widget_title).text.toString())
        }
    }

    @Test fun fourByTwoShowsThirdAndFourthCoursesAcrossDensitiesWithoutOversizedHeader() {
        val target = java.time.LocalDateTime.now().let {
            if (it.hour >= 22) it.toLocalDate().plusDays(1) else it.toLocalDate()
        }
        val config = defaultConfig().copy(termStartDate = target.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString(),
            autoCurrentWeek = true)
        for (dpi in listOf(160, 240, 420)) for (fontScale in listOf(1f, 1.5f)) {
            val local = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                densityDpi = dpi; this.fontScale = fontScale
            })
            for (size in listOf(WidgetRenderSize(280, 160), WidgetRenderSize(320, 160), WidgetRenderSize(336, 168))) {
                for (count in listOf(2, 3, 4)) {
                    val state = AppState(config = config, courses = (1..count).map { index ->
                        CourseEntity(index.toLong(), "测试课程 $index", null, "教学楼 512", target.dayOfWeek.value,
                            listOf(index), listOf(1), WeekParity.ALL, null,
                            customStartTime = "23:00", customEndTime = "23:59")
                    })
                    val remote = MiuixTodayWidgetRenderer.buildViews(local, state, TodayWidgetVariant.LARGE,
                        WidgetAppearanceEntity.defaults(WidgetAppearanceVariant.COURSES_LARGE), size)
                    val view = remote.apply(local, FrameLayout(local))
                    val density = local.resources.displayMetrics.density
                    val width = (size.widthDp * density).toInt()
                    val height = (size.heightDp * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    val header = view.findViewById<android.widget.TextView>(com.xiaomanjun.sleepdownschedule.R.id.widget_title)
                    assertTrue("Header must fit its row: $dpi/$fontScale/$size", header.layout.height <= header.height)
                    assertTrue(header.textSize <= 15 * local.resources.displayMetrics.scaledDensity + 1)
                    if (count > 2) {
                        val cell = view.findViewById<View>(if (count == 3) com.xiaomanjun.sleepdownschedule.R.id.widget_grid_cell_3
                            else com.xiaomanjun.sleepdownschedule.R.id.widget_grid_cell_4)
                        val bounds = android.graphics.Rect()
                        assertTrue("Course $count should be visible: $dpi/$fontScale/$size", cell.getGlobalVisibleRect(bounds))
                        assertTrue("The second row must fit (within pixel rounding): $dpi/$fontScale/$size",
                            cell.height - bounds.height() <= 1)
                    }
                }
            }
        }
    }

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
