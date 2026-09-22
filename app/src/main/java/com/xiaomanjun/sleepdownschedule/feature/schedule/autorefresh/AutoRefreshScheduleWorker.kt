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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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

    /** Validate the captured session in the background, then save credentials without importing. */
    suspend fun connect(
        context: Context,
        adapter: EduAdapter,
        scheduleId: Int,
        initialCookies: List<AutoRefreshCookie> = emptyList(),
        authenticatedUrl: String? = null,
        webStorage: AutoRefreshWebStorage? = null,
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
            username = "",
            password = "",
            scheduleId = scheduleId,
            cookies = initialCookies,
            authenticatedUrl = authenticatedUrl,
            desktopMode = desktopMode,
            webStorage = webStorage,
            interactionAnswers = existing?.interactionAnswers.orEmpty(),
            automatic = existing?.automatic ?: false,
            frequencyMinutes = existing?.frequencyMinutes ?: AutoRefreshFrequency.DailyMinutes,
            avatarPath = existing?.avatarPath,
            lastRefreshAt = existing?.lastRefreshAt ?: 0,
            lastResult = "正在验证教务登录"
        )
        val app = context.applicationContext as CourseScheduleApp
        runCatching {
            require(initialCookies.isNotEmpty() || webStorage != null) {
                "未读取到登录凭证，请先完成学校登录"
            }
            val targetState = app.repository.scheduleSnapshot(scheduleId)
            // Validate through the same restored-page path used by automatic refresh.
            // The adapter only stages a draft in its private bridge; this path never writes it.
            val verified = AutoRefreshShiguangRunner.fetch(app, adapter, provisional, targetState, onInteraction)
            val connected = provisional.copy(
                cookies = mergeCookies(provisional.cookies, verified.cookies),
                webStorage = verified.storage ?: provisional.webStorage,
                interactionAnswers = verified.interactionAnswers,
                lastResult = "登录凭证已保存，可手动或自动刷新课表"
            )
            AutoRefreshScheduleStore.save(app, connected)
            AutoRefreshOutcome(true, connected.lastResult, connected)
        }.getOrElse { error ->
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            recordFailure(context, provisional, error.message ?: "登录态校验失败，请确认已进入教务系统")
        }
    }

    suspend fun refreshSaved(context: Context): AutoRefreshOutcome = refreshMutex.withLock {
        val profile = AutoRefreshScheduleStore.load(context)
            ?: return@withLock AutoRefreshOutcome(false, "请先登录教务系统")
        if (profile.sessionOnly) return@withLock AutoRefreshOutcome(false, "请打开教务页面手动刷新课表", profile)
        val adapters = ShiguangApiAdapterCatalog.loadSupported(context)
        val adapter = ShiguangApiAdapterCatalog.find(adapters, profile.schoolId, profile.adapterId)
            ?: return@withLock recordFailure(context, profile, "该教务入口已更新，请重新选择学校并登录")
        execute(context, adapter, profile)
    }

    /** Retain browser state only; HTML adapters still require their normal preview and confirmation. */
    suspend fun retainSession(
        context: Context,
        adapter: EduAdapter,
        scheduleId: Int,
        cookies: List<AutoRefreshCookie>,
        authenticatedUrl: String,
        webStorage: AutoRefreshWebStorage?,
        desktopMode: Boolean
    ): AutoRefreshOutcome = refreshMutex.withLock {
        val retained = retainedEduSessionProfile(
            adapter, scheduleId, cookies, authenticatedUrl, webStorage, desktopMode,
            AutoRefreshScheduleStore.load(context)
        )
        AutoRefreshScheduleStore.save(context, retained)
        AutoRefreshOutcome(true, retained.lastResult, retained)
    }

    private suspend fun execute(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile
    ): AutoRefreshOutcome {
        val app = context.applicationContext as CourseScheduleApp
        return runCatching {
            val targetState = app.repository.scheduleSnapshot(profile.scheduleId)
            val fetched = AutoRefreshShiguangRunner.fetch(app, adapter, profile, targetState)
            app.repository.importDraftForSchedule(profile.scheduleId, fetched.draft)
            val now = System.currentTimeMillis()
            val updated = profile.copy(
                cookies = mergeCookies(profile.cookies, fetched.cookies),
                webStorage = fetched.storage ?: profile.webStorage,
                interactionAnswers = fetched.interactionAnswers,
                lastRefreshAt = now,
                lastResult = "刷新成功，共 ${fetched.draft.courses.size} 门课程"
            )
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
            val active = app.repository.activeSnapshot()
            NotificationScheduler.refreshToday(app, active.courses, active.config, active.periods)
            TodayCoursesWidgetProvider.refreshAll(app)
            AutoRefreshOutcome(true, updated.lastResult, AutoRefreshScheduleStore.load(app) ?: updated)
        }.getOrElse { error ->
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
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
                "教务请求超时，请检查校园网或 VPN 后重试"
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
        if (profile?.automaticRefreshEnabled != true) return Result.success()
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
            if (profile?.automaticRefreshEnabled != true) {
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
