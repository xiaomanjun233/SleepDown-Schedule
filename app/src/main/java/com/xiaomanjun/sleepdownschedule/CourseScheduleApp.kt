package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.*

import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestoreService
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentRepository
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceRepository
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator

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
    private val globalSettingsSaveSignal = Channel<Unit>(Channel.CONFLATED)
    private val globalSettingsSaveLock = Any()
    private var pendingGeneralSettings: ScheduleConfigEntity? = null
    private var pendingNotificationSettings: ScheduleConfigEntity? = null
    private var pendingHomeChromeBlurScale: Float? = null

    override fun onCreate() {
        super.onCreate()
        AppIconManager.applyStoredMode(this)
        SleepDownRemoteConfig.initialize(this, applicationScope)
        ActivityTransitionCoordinator.install(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                setTaskExcludedFromRecents(false)
            }

            override fun onStop(owner: LifecycleOwner) {
                // Some ColorOS/Oplus launchers remove the visible task when a launcher alias is
                // changed during an Activity return/theme handoff. Appearance changes record the
                // desired alias immediately, then publish it only after the process is backgrounded.
                AppIconManager.applyStoredMode(this@CourseScheduleApp)
                if (hideFromRecentsEnabled) setTaskExcludedFromRecents(true)
            }
        })
        applicationScope.launch(Dispatchers.IO) {
            cleanupPersistedAppData()
        }
        applicationScope.launch(Dispatchers.IO) {
            for (ignored in globalSettingsSaveSignal) {
                while (true) {
                    val next = synchronized(globalSettingsSaveLock) {
                        val batch = Triple(
                            pendingGeneralSettings,
                            pendingNotificationSettings,
                            pendingHomeChromeBlurScale
                        )
                        pendingGeneralSettings = null
                        pendingNotificationSettings = null
                        pendingHomeChromeBlurScale = null
                        batch
                    }
                    if (next.first == null && next.second == null && next.third == null) break
                    runCatching {
                        repository.saveGlobalSettingsPatches(
                            generalSettings = next.first,
                            notificationSettings = next.second,
                            homeChromeBlurScale = next.third
                        )
                        val snapshot = repository.activeSnapshot()
                        NotificationScheduler.refreshToday(
                            this@CourseScheduleApp,
                            snapshot.courses,
                            snapshot.config,
                            snapshot.periods
                        )
                        TodayCoursesWidgetProvider.refreshAll(this@CourseScheduleApp)
                    }.onFailure { error ->
                        Log.w("CourseScheduleApp", "Global settings save failed", error)
                    }
                }
            }
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
        runCatching {
            BackupRestoreService(this, database).resumePending()
        }.onSuccess { results ->
            results.flatMap { it.warnings }.forEach { warning ->
                Log.w("CourseScheduleApp", "Backup restore resume: $warning")
            }
        }.onFailure { error ->
            Log.w("CourseScheduleApp", "Backup restore resume failed", error)
        }
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

    /**
     * Persists the newest non-structural settings snapshot in process scope. This survives a
     * predictive-back Activity teardown and coalesces rapid slider/toggle changes without letting
     * an older database write overtake a newer one.
     */
    internal fun enqueueGeneralSettingsSave(config: ScheduleConfigEntity) {
        synchronized(globalSettingsSaveLock) {
            pendingGeneralSettings = config
        }
        globalSettingsSaveSignal.trySend(Unit)
    }

    internal fun enqueueNotificationSettingsSave(config: ScheduleConfigEntity) {
        synchronized(globalSettingsSaveLock) {
            pendingNotificationSettings = config
        }
        globalSettingsSaveSignal.trySend(Unit)
    }

    internal fun enqueueHomeChromeBlurScaleSave(value: Float) {
        synchronized(globalSettingsSaveLock) {
            pendingHomeChromeBlurScale = normalizedHomeChromeBlurScale(value)
        }
        globalSettingsSaveSignal.trySend(Unit)
    }

    private companion object {
        const val TRANSIENT_CACHE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
