package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.TodayCoursesWidgetProvider
import com.xiaomanjun.sleepdownschedule.data.repository.preserveTimingForAutoRefresh
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleRefreshStaleException
import com.xiaomanjun.sleepdownschedule.domain.schedule.mergeReviewedRefresh
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal data class AutoRefreshOutcome(
    val success: Boolean,
    val message: String,
    val profile: AutoRefreshScheduleProfile? = null,
    val retryable: Boolean = false
)

/** Only transient transport failures get bounded retries; expired login needs user action. */
internal fun autoRefreshWorkResult(outcome: AutoRefreshOutcome, attempt: Int): androidx.work.ListenableWorker.Result = when {
    outcome.success -> androidx.work.ListenableWorker.Result.success()
    outcome.retryable && attempt < 2 -> androidx.work.ListenableWorker.Result.retry()
    else -> androidx.work.ListenableWorker.Result.failure()
}

internal object AutoRefreshScheduleCoordinator {
    private val refreshMutex = Mutex()

    suspend fun selectTargetSchedule(context: Context, saved: AutoRefreshScheduleProfile, targetId: Int): String? =
        refreshMutex.withLock {
            val current = requireNotNull(AutoRefreshScheduleStore.load(context)) { "请先连接教务系统" }
            require(current.bindingId == saved.bindingId &&
                current.matches(saved.schoolId, saved.adapterId, saved.scheduleId)) { "教务连接已改变，请重新选择" }
            val app = context.applicationContext as CourseScheduleApp
            require(targetId in app.repository.referencedScheduleIds()) { "该课表已不在列表中，请重新选择" }

            val updated = checkNotNull(AutoRefreshScheduleStore.update(app) { it.withTargetSchedule(targetId) })
            AutoRefreshReviewStore.clear(app)
            runCatching { AutoRefreshScheduleWorker.updateSchedule(app, updated) }
                .exceptionOrNull()?.let { "目标课表已保存，定时任务暂未更新" }
        }

    suspend fun logout(context: Context) {
        // Stop a scheduled lookup before waiting for its browser to close.
        AutoRefreshScheduleWorker.updateSchedule(context, null)
        refreshMutex.withLock {
            val profile = AutoRefreshScheduleStore.load(context)
            try {
                AutoRefreshScheduleStore.clear(context)
            } catch (failure: IllegalStateException) {
                AutoRefreshScheduleWorker.updateSchedule(context, profile)
                throw failure
            }
            AutoRefreshReviewStore.clear(context)
            retireProfile(profile?.browserProfileName)
        }
    }

    /** The foreground browser and this verification run share the same Chromium profile. */
    suspend fun connect(
        context: Context,
        adapter: EduAdapter,
        scheduleId: Int,
        bindingId: String,
        isolatedBrowser: Boolean,
        authenticatedUrl: String,
        desktopMode: Boolean,
        onInteraction: (AutoRefreshLoginInteraction?) -> Unit
    ): AutoRefreshOutcome = refreshMutex.withLock {
        val previous = AutoRefreshScheduleStore.load(context)
        val sameSchedule = previous?.takeIf { it.matches(adapter.school.id, adapter.adapterId, scheduleId) }
        val provisional = AutoRefreshScheduleProfile(
            schoolId = adapter.school.id,
            schoolName = adapter.school.name,
            adapterId = adapter.adapterId,
            adapterName = adapter.adapterName,
            scheduleId = scheduleId,
            bindingId = bindingId,
            isolatedBrowser = isolatedBrowser,
            adapterSource = adapter.source.name,
            authenticatedUrl = authenticatedUrl,
            desktopMode = desktopMode,
            automatic = sameSchedule?.automatic ?: false,
            frequencyMinutes = sameSchedule?.frequencyMinutes ?: AutoRefreshFrequency.DailyMinutes,
            avatarPath = sameSchedule?.avatarPath,
            lastRefreshAt = sameSchedule?.lastRefreshAt ?: 0
        )
        val app = context.applicationContext as CourseScheduleApp
        try {
            require(AutoRefreshLoginRoutes.isSessionPage(authenticatedUrl)) { "请先进入登录后的教务页面" }
            val target = snapshot(app, scheduleId)
            val source = if (isolatedBrowser) ShiguangApiAdapterCatalog.backgroundScriptOrNull(app, adapter) else null
            val connected = if (source != null) {
                val probe = AutoRefreshShiguangRunner.fetch(app, provisional, target, source, onInteraction)
                provisional.copy(connection = RefreshConnection.Ready,
                    scriptFingerprint = ShiguangApiAdapterCatalog.fingerprint(source),
                    interactionAnswers = probe.answers,
                    lastResult = "教务连接已验证，可检查课表变化")
            } else {
                provisional.copy(connection = RefreshConnection.BrowserOnly,
                    lastResult = if (isolatedBrowser) "此适配器需要在教务页面操作，可手动核对课表"
                        else "当前 WebView 无法隔离后台登录会话，可在教务页面手动核对")
            }
            AutoRefreshScheduleStore.save(app, connected)
            AutoRefreshReviewStore.clear(app)
            retireProfile(previous?.browserProfileName?.takeIf { it != connected.browserProfileName })
            val message = runCatching { AutoRefreshScheduleWorker.updateSchedule(app, connected) }
                .fold(
                    onSuccess = { connected.lastResult },
                    onFailure = { "教务连接已保存，定时任务暂未启动" }
                )
            AutoRefreshOutcome(true, message, connected)
        } catch (timeout: TimeoutCancellationException) {
            AutoRefreshOutcome(false, "验证教务连接超时，请检查网络后重试", previous)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AutoRefreshOutcome(false, failure.message ?: "无法验证教务连接", previous)
        }
    }

    suspend fun refreshSaved(context: Context, manual: Boolean = false, scheduledBinding: String? = null): AutoRefreshOutcome =
        refreshMutex.withLock {
            val profile = AutoRefreshScheduleStore.load(context)
            if (!manual && (scheduledBinding == null || profile?.bindingId != scheduledBinding ||
                    profile?.automaticRefreshEnabled != true)) return@withLock AutoRefreshOutcome(true, "")
            if (profile == null) return@withLock AutoRefreshOutcome(false, "请先连接教务系统")
            if (!profile.canCheckInBackground) {
                return@withLock AutoRefreshOutcome(false, profile.connection.label, profile)
            }
            val app = context.applicationContext as CourseScheduleApp
            try {
                val target = snapshot(app, profile.scheduleId)
                try {
                    ShiguangWarehouseUpdater.refreshIfStale(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An unavailable repository does not prevent a previously cached adapter from running.
                }
                val adapters = ShiguangApiAdapterCatalog.loadLoginAdapters(context)
                val adapter = ShiguangApiAdapterCatalog.find(adapters, profile.schoolId, profile.adapterId, profile.adapterSource)
                val source = adapter?.let { ShiguangApiAdapterCatalog.backgroundScriptOrNull(app, it) }
                if (source == null || ShiguangApiAdapterCatalog.fingerprint(source) != profile.scriptFingerprint) {
                    throw RefreshFailure("教务适配已更新，请重新验证连接", RefreshConnection.AdapterChanged)
                }
                val fetched = AutoRefreshShiguangRunner.fetch(app, profile, target, source)
                val message = stageReview(app, profile, target, fetched.draft, manual)
                val updated = AutoRefreshScheduleStore.update(app) { current ->
                    current.copy(interactionAnswers = fetched.answers, lastRefreshAt = System.currentTimeMillis(), lastResult = message)
                }
                AutoRefreshOutcome(true, message, updated)
            } catch (_: TimeoutCancellationException) {
                recordFailure(app, "教务取课超时，请检查校园网络", retryable = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RefreshFailure) {
                recordFailure(app, failure.message.orEmpty(), failure.connection, failure.retryable)
            } catch (_: java.io.IOException) {
                recordFailure(app, "教务网络请求失败，请稍后重试", retryable = true)
            } catch (_: Exception) {
                recordFailure(app, "无法检查课表变化，可打开教务页面核对")
            }
        }

    suspend fun confirmReview(context: Context, id: String, useIncoming: Set<Int>, useIncomingTerm: Boolean): String =
        refreshMutex.withLock {
            val pending = requireNotNull(AutoRefreshReviewStore.load(context)) { "待核对内容已更新，请重新打开" }
            require(pending.id == id) { "待核对内容已更新，请重新选择" }
            val profile = AutoRefreshScheduleStore.load(context)
            require(pending.belongsTo(profile)) { "教务绑定已改变，请重新检查" }
            val app = context.applicationContext as CourseScheduleApp
            try {
                if (useIncoming.isNotEmpty() || (useIncomingTerm && pending.termChanged)) {
                    app.repository.applyReviewedRefresh(pending.scheduleId, pending.before, pending.timingKey,
                        mergeReviewedRefresh(pending.before, pending.differences, useIncoming),
                        pending.afterTerm.takeIf { useIncomingTerm && pending.termChanged })
                }
            } catch (stale: ScheduleRefreshStaleException) {
                AutoRefreshReviewStore.save(context, pending.rebase(snapshot(app, pending.scheduleId)))
                throw stale
            }
            AutoRefreshReviewStore.acknowledge(context, pending)
            val result = "已核对课表：采用 ${useIncoming.size + if (useIncomingTerm && pending.termChanged) 1 else 0} 处更新，其余保留原内容"
            AutoRefreshScheduleStore.update(context) { it.copy(lastResult = result) }
            val active = app.repository.activeSnapshot()
            NotificationScheduler.refreshToday(app, active)
            TodayCoursesWidgetProvider.refreshAll(app)
            result
        }

    /** Visible and scheduled imports both stage a review for the binding's target schedule. */
    suspend fun reviewBrowserRefresh(context: Context, saved: AutoRefreshScheduleProfile,
        draft: com.xiaomanjun.sleepdownschedule.ImportDraft) = refreshMutex.withLock {
        val current = requireNotNull(AutoRefreshScheduleStore.load(context)) { "教务登录已退出，请重新打开" }
        require(current.bindingId != null && current.bindingId == saved.bindingId) { "教务绑定已改变，请重新打开" }
        require(current.scheduleId == saved.scheduleId) { "目标课表已切换，请重新打开教务页面" }
        val app = context.applicationContext as CourseScheduleApp
        val message = stageReview(app, current, snapshot(app, current.scheduleId), draft, manual = true)
        AutoRefreshScheduleStore.update(app) { it.copy(lastRefreshAt = System.currentTimeMillis(), lastResult = message) }
        message
    }

    private fun stageReview(
        context: Context, profile: AutoRefreshScheduleProfile, target: com.xiaomanjun.sleepdownschedule.AppState,
        draft: com.xiaomanjun.sleepdownschedule.ImportDraft, manual: Boolean
    ): String {
        require(draft.courses.isNotEmpty()) { "教务未返回有效课程" }
        val prepared = preserveTimingForAutoRefresh(target.config, target.periods, draft)
        val pending = PendingScheduleRefresh.create(profile, target, prepared.courses, prepared.config)
        val old = AutoRefreshReviewStore.load(context)?.takeIf { it.belongsTo(profile) }
        return when (decideRefreshReview(old, pending, manual, AutoRefreshReviewStore.wasReviewed(context, pending))) {
            RefreshReviewDecision.Keep -> "有 ${checkNotNull(old).count} 处变化等待核对"
            RefreshReviewDecision.Replace -> {
                AutoRefreshReviewStore.save(context, pending)
                "有 ${pending.count} 处变化等待核对"
            }
            RefreshReviewDecision.Clear -> {
                if (AutoRefreshReviewStore.load(context) != null) AutoRefreshReviewStore.clear(context)
                "检查完成，教务没有需要核对的新变化"
            }
        }
    }

    private suspend fun snapshot(app: CourseScheduleApp, scheduleId: Int) =
        try {
            app.repository.scheduleSnapshot(scheduleId)
        } catch (_: IllegalArgumentException) {
            throw RefreshFailure("原目标课表已不在列表中，请重新选择", RefreshConnection.ScheduleMissing)
        }

    private fun recordFailure(context: Context, message: String,
        connection: RefreshConnection? = null, retryable: Boolean = false): AutoRefreshOutcome {
        val updated = AutoRefreshScheduleStore.update(context) { current ->
            current.copy(connection = connection ?: current.connection, lastResult = message)
        }
        return AutoRefreshOutcome(false, message, updated, retryable)
    }

    private suspend fun retireProfile(name: String?) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
        runCatching { AutoRefreshWebSession.retire(name) }
    }
}

internal fun describeScheduleRefresh(before: List<CourseEntity>, after: List<CourseEntity>): String {
    fun key(course: CourseEntity): String = listOf(
        course.name.trim(), course.weekday.toString(), course.periods.sorted().joinToString(","),
        course.teacher.orEmpty().trim(), course.location.orEmpty().trim()
    ).joinToString("|")
    fun value(course: CourseEntity): String = listOf(
        course.weeks.sorted().joinToString(","), course.weekParity.name,
        course.customStartTime.orEmpty(), course.customEndTime.orEmpty(), course.customPeriodTimes.orEmpty(),
        course.note.orEmpty()
    ).joinToString("|")
    val old = before.groupBy(::key)
    val fresh = after.groupBy(::key)
    val added = fresh.filterKeys { it !in old }.values.flatten()
    val changed = fresh.filter { (courseKey, courses) ->
        old[courseKey]?.map(::value)?.sorted() != null &&
            old[courseKey]?.map(::value)?.sorted() != courses.map(::value).sorted()
    }.values.flatten()
    val removed = old.filterKeys { it !in fresh }.values.flatten()
    fun names(courses: List<CourseEntity>): String = courses.map(CourseEntity::name).distinct()
        .take(4).joinToString("、") + if (courses.map(CourseEntity::name).distinct().size > 4) "等" else ""
    return buildString {
        append("刷新成功，共 ${after.size} 门课程；")
        append(if (added.isEmpty()) "无新增课程" else "新增 ${added.size} 门：${names(added)}")
        if (changed.isNotEmpty()) append("；更新 ${changed.size} 门：${names(changed)}")
        if (removed.isNotEmpty()) append("；移除 ${removed.size} 门：${names(removed)}")
    }
}

class AutoRefreshScheduleWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return autoRefreshWorkResult(AutoRefreshScheduleCoordinator.refreshSaved(applicationContext,
            scheduledBinding = inputData.getString(BindingKey)), runAttemptCount)
    }

    companion object {
        private const val WorkName = "schedule_api_auto_refresh"
        private const val BindingKey = "binding"

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
            if (profile?.automaticRefreshEnabled != true) {
                manager.cancelUniqueWork(WorkName)
                return
            }
            val minutes = AutoRefreshFrequency.normalize(profile.frequencyMinutes)
            val request = PeriodicWorkRequestBuilder<AutoRefreshScheduleWorker>(minutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(BindingKey to profile.bindingId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
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
