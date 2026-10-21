package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.util.AtomicFile
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
internal data class PendingScheduleRefresh(
    val id: String = UUID.randomUUID().toString(),
    val scheduleId: Int,
    val schoolId: String,
    val adapterId: String,
    val schoolName: String,
    val before: List<RefreshCourse>,
    val beforeOriginalTimes: Map<Long, String?>,
    val after: List<RefreshCourse>,
    val beforeTerm: RefreshTerm,
    val afterTerm: RefreshTerm,
    val timingKey: String
) {
    val differences get(): List<RefreshDifference> {
        // Legacy rows may not yet carry a clock snapshot. Compare their actual current clock,
        // while retaining the untouched stored row for both the guard and "keep original".
        val raw = before.associateBy { it.id }
        return refreshDifferences(before.map { it.copy(originalTimes = beforeOriginalTimes[it.id] ?: it.originalTimes) }, after)
            .map { it.copy(before = it.before?.let { row -> raw.getValue(row.id) }) }
    }
    val termChanged get() = beforeTerm != afterTerm
    val count get() = differences.size + if (termChanged) 1 else 0
    val remoteKey get() = refreshDigest(listOf(scheduleId, schoolId, adapterId,
        after.map { it.contentKey().toString() }.sorted(), afterTerm).toString())

    fun rebase(state: AppState) = copy(id = UUID.randomUUID().toString(),
        before = state.courses.map(RefreshCourse::from), beforeTerm = RefreshTerm.from(state.config),
        beforeOriginalTimes = comparisonClocks(state),
        timingKey = refreshTimingKey(state.config, state.periods))

    companion object {
        fun create(profile: AutoRefreshScheduleProfile, state: AppState,
            courses: List<CourseEntity>, config: ScheduleConfigEntity) = PendingScheduleRefresh(
            scheduleId = profile.scheduleId, schoolId = profile.schoolId, adapterId = profile.adapterId,
            schoolName = profile.schoolName, before = state.courses.map(RefreshCourse::from),
            beforeOriginalTimes = comparisonClocks(state),
            after = courses.map(RefreshCourse::from), beforeTerm = RefreshTerm.from(state.config),
            afterTerm = RefreshTerm.from(config), timingKey = refreshTimingKey(state.config, state.periods))

        private fun comparisonClocks(state: AppState) = state.courses.associate { course ->
            course.id to captureOriginalPeriodTimes(course, state.periods).originalPeriodTimes
        }
    }
}

/** App-private, excluded from backup; pending choices survive process death without changing Room. */
internal object AutoRefreshReviewStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow<PendingScheduleRefresh?>(null)
    private var initialized = false
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "pending-schedule-refresh.json"))

    @Synchronized fun observe(context: Context): StateFlow<PendingScheduleRefresh?> {
        if (!initialized) {
            val saved = file(context)
            // A malformed pending file must never trigger a database replacement.
            state.value = if (saved.baseFile.exists()) runCatching {
                json.decodeFromString<PendingScheduleRefresh>(saved.readFully().toString(Charsets.UTF_8))
            }.getOrNull() else null
            initialized = true
        }
        return state
    }
    fun load(context: Context) = observe(context).value

    @Synchronized fun save(context: Context, pending: PendingScheduleRefresh) {
        val target = file(context)
        val stream = target.startWrite()
        try {
            stream.write(json.encodeToString(pending).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) { target.failWrite(stream); throw error }
        initialized = true
        state.value = pending
    }

    @Synchronized fun clear(context: Context) {
        file(context).delete()
        initialized = true
        state.value = null
    }

    fun wasReviewed(context: Context, pending: PendingScheduleRefresh): Boolean =
        context.getSharedPreferences("schedule_refresh_review", Context.MODE_PRIVATE)
            .getString("last_remote", null) == pending.remoteKey

    fun acknowledge(context: Context, pending: PendingScheduleRefresh) {
        check(context.getSharedPreferences("schedule_refresh_review", Context.MODE_PRIVATE).edit()
            .putString("last_remote", pending.remoteKey).commit()) { "保存核对结果失败，请重试" }
        clear(context)
    }
}
