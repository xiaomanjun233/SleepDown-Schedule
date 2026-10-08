package com.xiaomanjun.sleepdownschedule.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.xiaomanjun.sleepdownschedule.R
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun staticWidgetPreview(context: Context, type: WidgetAppearanceVariant) = RemoteViews(context.packageName, when (type) {
    WidgetAppearanceVariant.COURSES_LARGE -> R.layout.widget_preview_today_courses
    WidgetAppearanceVariant.COURSES_SQUARE -> R.layout.widget_preview_today_courses_square
    WidgetAppearanceVariant.TODAY_TOMORROW -> R.layout.widget_preview_today_tomorrow
    WidgetAppearanceVariant.TODAY_ASSISTANT -> R.layout.widget_preview_today_assistant
    WidgetAppearanceVariant.WEEK_SCHEDULE -> R.layout.widget_preview_week_schedule
})

/** Pin dialogs have no installed widget size; give the host one bounded, intrinsically sized image. */
internal fun widgetPinPreview(context: Context, type: WidgetAppearanceVariant, content: RemoteViews?): RemoteViews {
    val logical = canonicalWidgetPreviewSize(type)
    val density = context.resources.displayMetrics.density
    val width = (logical.widthDp * density).roundToInt().coerceAtLeast(1)
    val height = (logical.heightDp * density).roundToInt().coerceAtLeast(1)
    val scale = minOf(1f, sqrt(240_000f / (width.toFloat() * height)))
    val bitmap = Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    // Keep drawable intrinsic dimensions in canonical dp after downsampling.
    bitmap.density = (context.resources.displayMetrics.densityDpi * scale).roundToInt().coerceAtLeast(1)
    val host = FrameLayout(context)
    val view = (content ?: staticWidgetPreview(context, type)).apply(context, host)
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, width, height)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    view.draw(canvas)
    return RemoteViews(context.packageName, R.layout.widget_pin_preview).apply {
        setImageViewBitmap(R.id.widget_pin_preview_image, bitmap)
    }
}
