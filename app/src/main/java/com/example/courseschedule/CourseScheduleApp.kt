package com.example.courseschedule

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Process-level dependency owner and lifecycle coordinator.
 *
 * Database schema and repair details stay in the data layer; the Application only
 * controls process-scoped instances and Android lifecycle integration.
 */
class CourseScheduleApp : Application() {
    private val processExceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e("CourseScheduleApp", "Uncaught process background task failure", error)
    }

    internal val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + processExceptionHandler
    )

    override fun onCreate() {
        super.onCreate()
        AppIconManager.applyStoredMode(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                setTaskExcludedFromRecents(false)
            }

            override fun onStop(owner: LifecycleOwner) {
                if (hideFromRecentsEnabled) setTaskExcludedFromRecents(true)
            }
        })
        applicationScope.launch(Dispatchers.IO) {
            cleanupPersistedAppData()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (shouldClearHomeWallpaperCaches(level)) {
            clearHomeWallpaperCaches()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        clearHomeWallpaperCaches()
    }

    private fun setTaskExcludedFromRecents(excluded: Boolean) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.appTasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(excluded) }
        }
    }

    private suspend fun cleanupPersistedAppData() {
        cleanupTransientCacheData()
        runCatching {
            repository.ensureDefaults()
            cleanupUnreferencedScheduleWallpapers(this, repository.referencedWallpaperUris())
            ScheduleSnapshotStore.cleanupUnreferenced(this, repository.referencedScheduleIds())
        }.onFailure { error ->
            Log.w("CourseScheduleApp", "Schedule wallpaper cleanup failed", error)
        }
        runCatching {
            widgetAppearanceRepository.ensureDefaults()
            widgetAppearanceRepository.cleanupUnreferencedFiles()
        }.onFailure { error ->
            Log.w("CourseScheduleApp", "Widget wallpaper cleanup failed", error)
        }
        runCatching {
            DayAgentRepository(this).cleanup(LocalDate.now())
        }.onFailure { error ->
            Log.w("CourseScheduleApp", "Agent history cleanup failed", error)
        }
    }

    private fun cleanupTransientCacheData(now: Long = System.currentTimeMillis()) {
        runCatching {
            File(cacheDir, "updates").listFiles().orEmpty().forEach { it.delete() }
            File(cacheDir, "shared_schedules").listFiles().orEmpty()
                .filter { now - it.lastModified() >= TRANSIENT_CACHE_MAX_AGE_MILLIS }
                .forEach { it.delete() }
            cacheDir.listFiles().orEmpty()
                .filter {
                    it.isFile &&
                        it.name.startsWith("sleepdown_ai_pdf_") &&
                        now - it.lastModified() >= TRANSIENT_CACHE_MAX_AGE_MILLIS
                }
                .forEach { it.delete() }
        }.onFailure { error ->
            Log.w("CourseScheduleApp", "Transient cache cleanup failed", error)
        }
    }

    val database: AppDatabase by lazy { createAppDatabase(this) }
    val repository: ScheduleRepository by lazy { ScheduleRepository(database) }
    val widgetAppearanceRepository: WidgetAppearanceRepository by lazy {
        WidgetAppearanceRepository(this, database)
    }

    private companion object {
        const val TRANSIENT_CACHE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
