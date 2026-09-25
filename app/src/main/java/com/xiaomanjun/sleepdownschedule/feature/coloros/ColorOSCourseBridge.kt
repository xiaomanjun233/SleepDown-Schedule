package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.Log
import androidx.room.InvalidationTracker
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.domain.schedule.ColorOSCourseMapper
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCourseColors
import com.xiaomanjun.sleepdownschedule.feature.widget.providers.MiuixTodayWidgetRenderer
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

object ColorOSCourseBridge {
    private const val TAG = "ColorOSCourseBridge"
    private const val PREFERENCES = "coloros_course_bridge"
    internal const val KEY_LAST_REFRESH_AT = "last_refresh_at"
    internal const val KEY_LAST_REFRESH_REASON = "last_refresh_reason"
    internal const val KEY_LAST_PROXY_QUERY_AT = "last_proxy_query_at"
    internal const val KEY_LAST_PROXY_QUERY_PATH = "last_proxy_query_path"
    internal const val KEY_LAST_PROXY_QUERY_CALLER = "last_proxy_query_caller"
    internal const val KEY_LAST_EXPORT_AT = "last_export_at"
    internal const val KEY_LAST_EXPORT_COUNT = "last_export_count"
    internal const val KEY_LAST_EXPORT_ERROR = "last_export_error"
    private const val REFRESH_DEBOUNCE_MS = 250L

    private val installed = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var applicationContext: Context? = null
    private var pendingReason = "schedule_changed"
    private val refreshRequests = Channel<String>(Channel.CONFLATED)
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        for (reason in refreshRequests) dispatchRefresh(reason)
    }
    private val firstRefresh = Runnable { refreshRequests.trySend(pendingReason) }

    private val databaseObserver = object : InvalidationTracker.Observer(
        "courses",
        "schedule_profiles",
        "schedule_config",
        "periods",
        "period_schemes",
        "period_scheme_times"
    ) {
        override fun onInvalidated(tables: Set<String>) {
            scheduleRefresh("database:${tables.sorted().joinToString(",")}")
        }
    }

    fun install(context: Context, database: AppDatabase) {
        applicationContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        database.invalidationTracker.addObserver(databaseObserver)
        scheduleRefresh("process_started", delayMillis = 0L)
    }

    fun notifyScheduleChanged(context: Context, reason: String = "manual") {
        applicationContext = context.applicationContext
        scheduleRefresh(reason, delayMillis = 0L)
    }

    internal fun recordProxyQuery(context: Context, path: String, externalCaller: String?) {
        if (externalCaller.isNullOrBlank() || externalCaller == context.packageName ||
            externalCaller == ColorOSCourseContract.PROXY_PACKAGE ||
            externalCaller == "com.android.shell"
        ) return
        preferences(context).edit()
            .putLong(KEY_LAST_PROXY_QUERY_AT, System.currentTimeMillis())
            .putString(KEY_LAST_PROXY_QUERY_PATH, path)
            .putString(KEY_LAST_PROXY_QUERY_CALLER, externalCaller)
            .apply()
    }

    internal fun recordExport(context: Context, exportedCount: Int, error: String? = null) {
        preferences(context).edit()
            .putLong(KEY_LAST_EXPORT_AT, System.currentTimeMillis())
            .putInt(KEY_LAST_EXPORT_COUNT, exportedCount)
            .putString(KEY_LAST_EXPORT_ERROR, error.orEmpty())
            .apply()
    }

    internal fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    internal fun courseColorResolver(context: Context, state: AppState): (CourseEntity) -> Int {
        val dark = MiuixTodayWidgetRenderer.usesDarkTheme(context, state.config)
        val assignments = WidgetCourseColors.assignments(context, state, dark)
        return { course -> WidgetCourseColors.color(state.config, course, assignments) }
    }

    private fun scheduleRefresh(reason: String, delayMillis: Long = REFRESH_DEBOUNCE_MS) {
        pendingReason = reason
        handler.removeCallbacks(firstRefresh)
        handler.postDelayed(firstRefresh, delayMillis)
    }

    private suspend fun dispatchRefresh(reason: String) {
        val context = applicationContext ?: return
        runCatching {
            val resolver = context.contentResolver
            val snapshot = (context as CourseScheduleApp).repository.snapshot()
            check(snapshot.loaded) { "Course snapshot is not ready" }
            val courseColor = courseColorResolver(context, snapshot)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val rows = Bundle().apply {
                putString("has_init", ColorOSCourseProviderContract.hasInitJson(true))
                putString("show_table_id", ColorOSCourseProviderContract.showTableIdJson(snapshot.config.id))
                putString("table_list", ColorOSCourseProviderContract.tableListJson(snapshot.schedules))
            }
            val baseRows = Bundle()
            repeat(8) { offset ->
                val date = today.plusDays(offset.toLong())
                val base = ColorOSCourseMapper.export(date, snapshot, zone, courseColor).json
                val key = "course|$date"
                baseRows.putString(key, base)
                rows.putString(key, ColorOSCourseExperiment.appendTestPreview(context, base, date, zone))
            }
            val export = Bundle().apply {
                putInt("snapshot_version", 1)
                putString("zone", zone.id)
                putLong("valid_until", today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli())
                putLong("preview_until", ColorOSCourseExperiment.activeTestPreviewExpiresAt(context))
                putBundle("rows", rows)
                putBundle("base_rows", baseRows)
            }
            val proxyHandledRefresh = runCatching {
                resolver.call(
                    ColorOSCourseContract.refreshUri,
                    ColorOSCourseContract.PROXY_REFRESH_METHOD,
                    reason,
                    export
                )?.getBoolean(ColorOSCourseContract.PROXY_REFRESH_ACCEPTED) == true
            }.getOrDefault(false)
            if (!proxyHandledRefresh) {
                // Older or missing components do not implement the refresh call. Keep the
                // notification fallback so diagnostics and the upgrade prompt remain usable.
                resolver.notifyChange(ColorOSCourseContract.refreshUri, null)
            }
            preferences(context).edit()
                .putLong(KEY_LAST_REFRESH_AT, System.currentTimeMillis())
                .putString(KEY_LAST_REFRESH_REASON, reason)
                .apply()
            Log.d(TAG, "Refresh notified reason=$reason proxyHandled=$proxyHandledRefresh")
        }.onFailure { error ->
            Log.w(TAG, "Failed to synchronize ColorOS courses: ${error.javaClass.simpleName}")
        }
    }
}
