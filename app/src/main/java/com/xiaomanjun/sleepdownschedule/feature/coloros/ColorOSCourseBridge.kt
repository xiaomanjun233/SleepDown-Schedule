package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.room.InvalidationTracker
import com.xiaomanjun.sleepdownschedule.AppDatabase
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
    private const val SECOND_REFRESH_DELAY_MS = 1_000L

    private val installed = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var applicationContext: Context? = null
    private var pendingReason = "schedule_changed"
    private val secondRefresh = Runnable { dispatchRefresh("${pendingReason}_retry") }
    private val firstRefresh = Runnable {
        dispatchRefresh(pendingReason)
        handler.postDelayed(secondRefresh, SECOND_REFRESH_DELAY_MS)
    }

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

    private fun scheduleRefresh(reason: String, delayMillis: Long = REFRESH_DEBOUNCE_MS) {
        pendingReason = reason
        handler.removeCallbacks(firstRefresh)
        handler.removeCallbacks(secondRefresh)
        handler.postDelayed(firstRefresh, delayMillis)
    }

    private fun dispatchRefresh(reason: String) {
        val context = applicationContext ?: return
        runCatching {
            val resolver = context.contentResolver
            val proxyHandledRefresh = runCatching {
                resolver.call(
                    ColorOSCourseContract.refreshUri,
                    ColorOSCourseContract.PROXY_REFRESH_METHOD,
                    reason,
                    null
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
            Log.w(TAG, "Failed to notify ColorOS course refresh", error)
        }
    }
}
