package com.xiaomanjun.sleepdownschedule

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

private const val BenchmarkWallpaperAction =
    "com.xiaomanjun.sleepdownschedule.benchmark.CONFIGURE_WALLPAPER"

/** Configures the isolated benchmark target; it does not bypass or alter any measured animation. */
class BenchmarkWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BenchmarkWallpaperAction) return
        val repository = (context.applicationContext as CourseScheduleApp).repository
        runBlocking {
            repository.ensureDefaults()
            val current = repository.activeSnapshot().config
            repository.savePersonalizationSnapshot(
                current.copy(
                    wallpaperUri = null,
                    defaultWallpaperStyle = DefaultWallpaperStyle.KANBAN,
                    wallpaperBlur = 0f,
                    wallpaperBrightness = 1f
                )
            )
        }
        clearHomeWallpaperCaches()
        resultCode = Activity.RESULT_OK
    }
}
