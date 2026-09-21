package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.TodayCoursesWidgetProvider
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal data class AutoRefreshOutcome(
    val success: Boolean,
    val message: String,
    val profile: AutoRefreshScheduleProfile? = null
)

internal object AutoRefreshScheduleCoordinator {
    private val refreshMutex = Mutex()

    suspend fun loginAndRefresh(
        context: Context,
        adapter: EduAdapter,
        username: String,
        password: String,
        scheduleId: Int,
        initialCookies: List<AutoRefreshCookie> = emptyList()
    ): AutoRefreshOutcome = refreshMutex.withLock {
        val provisional = AutoRefreshScheduleProfile(
            schoolId = adapter.school.id,
            schoolName = adapter.school.name,
            adapterId = adapter.adapterId,
            adapterName = adapter.adapterName,
            username = username,
            password = password,
            scheduleId = scheduleId,
            cookies = initialCookies,
            lastResult = "正在验证教务登录"
        )
        execute(context, adapter, provisional, persistNewProfile = true)
    }

    suspend fun refreshSaved(context: Context): AutoRefreshOutcome = refreshMutex.withLock {
        val profile = AutoRefreshScheduleStore.load(context)
            ?: return@withLock AutoRefreshOutcome(false, "请先登录教务系统")
        val adapters = ShiguangApiAdapterCatalog.loadSupported(context)
        val adapter = ShiguangApiAdapterCatalog.find(adapters, profile.schoolId, profile.adapterId)
            ?: return@withLock recordFailure(context, profile, "该适配器已不再满足纯接口刷新条件")
        execute(context, adapter, profile, persistNewProfile = false)
    }

    private suspend fun execute(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        persistNewProfile: Boolean
    ): AutoRefreshOutcome {
        val app = context.applicationContext as CourseScheduleApp
        return runCatching {
            val targetState = app.repository.scheduleSnapshot(profile.scheduleId)
            val fetched = AutoRefreshShiguangRunner.fetch(app, adapter, profile, targetState)
            app.repository.importDraftForSchedule(profile.scheduleId, fetched.draft)
            val now = System.currentTimeMillis()
            val updated = profile.copy(
                cookies = mergeCookies(profile.cookies, fetched.cookies),
                lastRefreshAt = now,
                lastResult = "刷新成功，共 ${fetched.draft.courses.size} 门课程"
            )
            if (persistNewProfile) {
                AutoRefreshScheduleStore.save(app, updated)
            } else {
                AutoRefreshScheduleStore.update(app) { current ->
                    if (current.schoolId == profile.schoolId &&
                        current.adapterId == profile.adapterId &&
                        current.username == profile.username
                    ) {
                        updated.copy(
                            automatic = current.automatic,
                            frequencyMinutes = current.frequencyMinutes,
                            avatarPath = current.avatarPath
                        )
                    } else current
                }
            }
            val active = app.repository.activeSnapshot()
            NotificationScheduler.refreshToday(app, active.courses, active.config, active.periods)
            TodayCoursesWidgetProvider.refreshAll(app)
            AutoRefreshOutcome(true, updated.lastResult, AutoRefreshScheduleStore.load(app) ?: updated)
        }.getOrElse { error ->
            recordFailure(
                context,
                profile,
                error.message?.takeIf(String::isNotBlank)
                    ?: "刷新失败，请检查网络、账号或教务系统状态"
            )
        }
    }

    private fun mergeCookies(
        existing: List<AutoRefreshCookie>,
        refreshed: List<AutoRefreshCookie>
    ): List<AutoRefreshCookie> = (existing + refreshed)
        .associateBy(AutoRefreshCookie::url)
        .values
        .toList()

    private fun recordFailure(
        context: Context,
        profile: AutoRefreshScheduleProfile,
        message: String
    ): AutoRefreshOutcome {
        val friendly = when {
            message.contains("timed out", ignoreCase = true) ->
                "刷新超时；教务系统可能需要校园网、代理或额外验证"
            else -> message
        }
        if (AutoRefreshScheduleStore.load(context) != null) {
            AutoRefreshScheduleStore.update(context) { current ->
                if (current.schoolId == profile.schoolId && current.adapterId == profile.adapterId) {
                    current.copy(lastResult = friendly)
                } else current
            }
        }
        return AutoRefreshOutcome(false, friendly, AutoRefreshScheduleStore.load(context))
    }
}

class AutoRefreshScheduleWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val profile = AutoRefreshScheduleStore.load(applicationContext)
        if (profile?.automatic != true) return Result.success()
        AutoRefreshScheduleCoordinator.refreshSaved(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WorkName = "schedule_api_auto_refresh"
        internal val FrequencyMinutes = listOf(15L, 30L, 60L, 180L, 360L, 720L)

        internal fun updateSchedule(context: Context, profile: AutoRefreshScheduleProfile?) {
            enqueue(context, profile, ExistingPeriodicWorkPolicy.UPDATE)
        }

        internal fun ensureSchedule(context: Context, profile: AutoRefreshScheduleProfile?) {
            enqueue(context, profile, ExistingPeriodicWorkPolicy.KEEP)
        }

        private fun enqueue(
            context: Context,
            profile: AutoRefreshScheduleProfile?,
            policy: ExistingPeriodicWorkPolicy
        ) {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (profile?.automatic != true) {
                manager.cancelUniqueWork(WorkName)
                return
            }
            val minutes = profile.frequencyMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<AutoRefreshScheduleWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                WorkName,
                policy,
                request
            )
        }
    }
}
