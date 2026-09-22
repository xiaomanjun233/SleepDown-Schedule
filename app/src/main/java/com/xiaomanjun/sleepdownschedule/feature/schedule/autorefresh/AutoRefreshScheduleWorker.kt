package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.webkit.WebView
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
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
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

    suspend fun logout(context: Context) = refreshMutex.withLock {
        AutoRefreshScheduleWorker.updateSchedule(context, null)
        AutoRefreshScheduleStore.clear(context)
    }

    suspend fun loginAndRefresh(
        context: Context,
        adapter: EduAdapter,
        username: String,
        password: String,
        scheduleId: Int,
        initialCookies: List<AutoRefreshCookie> = emptyList(),
        authenticatedUrl: String? = null,
        webStorage: AutoRefreshWebStorage? = null,
        authenticationWebView: WebView? = null,
        authenticationBridge: ShiguangBridgeHost? = null,
        desktopMode: Boolean = false,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)? = null
    ): AutoRefreshOutcome = refreshMutex.withLock {
        val existing = AutoRefreshScheduleStore.load(context)?.takeIf {
            it.schoolId == adapter.school.id && it.adapterId == adapter.adapterId &&
                it.scheduleId == scheduleId
        }
        val provisional = AutoRefreshScheduleProfile(
            schoolId = adapter.school.id,
            schoolName = adapter.school.name,
            adapterId = adapter.adapterId,
            adapterName = adapter.adapterName,
            username = username,
            password = password,
            scheduleId = scheduleId,
            cookies = initialCookies,
            authenticatedUrl = authenticatedUrl,
            desktopMode = desktopMode,
            webStorage = webStorage,
            automatic = existing?.automatic ?: false,
            frequencyMinutes = existing?.frequencyMinutes ?: AutoRefreshFrequency.DailyMinutes,
            avatarPath = existing?.avatarPath,
            lastResult = "正在验证教务登录"
        )
        execute(context, adapter, provisional, persistNewProfile = true, authenticationWebView, authenticationBridge, onInteraction)
    }

    suspend fun refreshSaved(context: Context): AutoRefreshOutcome = refreshMutex.withLock {
        val profile = AutoRefreshScheduleStore.load(context)
            ?: return@withLock AutoRefreshOutcome(false, "请先登录教务系统")
        val adapters = ShiguangApiAdapterCatalog.loadSupported(context)
        val adapter = ShiguangApiAdapterCatalog.find(adapters, profile.schoolId, profile.adapterId)
            ?: return@withLock recordFailure(context, profile, "该教务入口已更新，请重新选择学校并登录")
        execute(context, adapter, profile, persistNewProfile = false)
    }

    private suspend fun execute(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        persistNewProfile: Boolean,
        authenticationWebView: WebView? = null,
        authenticationBridge: ShiguangBridgeHost? = null,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)? = null
    ): AutoRefreshOutcome {
        val app = context.applicationContext as CourseScheduleApp
        return runCatching {
            val targetState = app.repository.scheduleSnapshot(profile.scheduleId)
            val fetched = AutoRefreshShiguangRunner.fetch(app, adapter, profile, targetState, authenticationWebView, authenticationBridge, onInteraction)
            app.repository.importDraftForSchedule(profile.scheduleId, fetched.draft)
            val now = System.currentTimeMillis()
            val updated = profile.copy(
                cookies = mergeCookies(profile.cookies, fetched.cookies),
                webStorage = fetched.storage ?: profile.webStorage,
                interactionAnswers = fetched.interactionAnswers,
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
            if (error is kotlinx.coroutines.CancellationException) throw error
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

        internal fun updateSchedule(context: Context, profile: AutoRefreshScheduleProfile?) {
            enqueue(context, profile, ExistingPeriodicWorkPolicy.UPDATE)
        }

        internal fun ensureSchedule(context: Context, profile: AutoRefreshScheduleProfile?) {
            enqueue(context, profile, ExistingPeriodicWorkPolicy.UPDATE)
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
            val minutes = AutoRefreshFrequency.normalize(profile.frequencyMinutes)
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
