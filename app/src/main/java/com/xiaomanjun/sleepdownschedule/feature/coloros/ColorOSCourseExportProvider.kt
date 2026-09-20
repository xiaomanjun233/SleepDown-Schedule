package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.domain.schedule.ColorOSCourseMapper
import com.xiaomanjun.sleepdownschedule.model.ScheduleProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ColorOSCourseExportProvider : ContentProvider() {
    override fun onCreate(): Boolean = BuildConfig.SLEEPDOWN_EXP_BUILD

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val path = uri.pathSegments.firstOrNull() ?: return null
        if (path !in ColorOSCourseProviderContract.supportedPaths) return null
        val appContext = context?.applicationContext ?: return oneRow(-1, "{}")
        if (callingPackage == ColorOSCourseContract.PROXY_PACKAGE) {
            ColorOSCourseBridge.recordProxyQuery(
                appContext,
                uri.encodedPath.orEmpty(),
                uri.getQueryParameter(PROXY_CALLER_PARAMETER)
            )
        }
        return when (path) {
            "has_init" -> oneRow(databaseJson(ColorOSCourseProviderContract.hasInitJson(false)) {
                val loaded = app().repository.activeSnapshot().loaded
                ColorOSCourseProviderContract.hasInitJson(loaded)
            })
            "show_table_id" -> oneRow(databaseJson(ColorOSCourseProviderContract.showTableIdJson(1)) {
                ColorOSCourseProviderContract.showTableIdJson(app().repository.activeSnapshot().config.id)
            })
            "table_list" -> oneRow(databaseJson("[]") {
                ColorOSCourseProviderContract.tableListJson(app().repository.snapshot().schedules)
            })
            "course_list" -> oneRow(courseJson(uri, tomorrow = false))
            "next_course_list" -> oneRow(courseJson(uri, tomorrow = true))
            else -> null
        }
    }

    private fun courseJson(uri: Uri, tomorrow: Boolean): Pair<Int, String> = databaseJson("[]") {
        val zoneId = ZoneId.systemDefault()
        val date = ColorOSCourseProviderContract.requestedDate(uri.pathSegments, zoneId)
            .let { if (tomorrow) it.plusDays(1) else it }
        val result = ColorOSCourseMapper.export(date, app().repository.activeSnapshot(), zoneId)
        val exportedJson = if (tomorrow) {
            result.json
        } else {
            requireNotNull(context).let {
                ColorOSCourseExperiment.appendTestPreview(it, result.json, date, zoneId)
            }
        }
        context?.let { ColorOSCourseBridge.recordExport(it, result.exportedCount) }
        exportedJson
    }

    private fun databaseJson(fallback: String, block: suspend () -> String): Pair<Int, String> = try {
        0 to runBlocking(Dispatchers.IO) { block() }
    } catch (error: Exception) {
        Log.e(TAG, "Failed to export ColorOS course data", error)
        context?.let { ColorOSCourseBridge.recordExport(it, 0, error.message ?: error.javaClass.simpleName) }
        -1 to fallback
    }

    private fun oneRow(result: Pair<Int, String>): Cursor = oneRow(result.first, result.second)

    private fun oneRow(code: Int, data: String): Cursor = MatrixCursor(columns).apply {
        addRow(arrayOf<Any>(code, data))
    }

    private fun app(): CourseScheduleApp =
        requireNotNull(context?.applicationContext as? CourseScheduleApp) { "SleepDown application is unavailable" }

    override fun getType(uri: Uri): String = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("read-only provider")

    private companion object {
        const val TAG = "ColorOSCourseExport"
        val columns = arrayOf("code", "data")
        const val PROXY_CALLER_PARAMETER = "sleepdown_proxy_caller"
    }
}

internal object ColorOSCourseProviderContract {
    val supportedPaths = setOf(
        "has_init",
        "show_table_id",
        "table_list",
        "course_list",
        "next_course_list"
    )

    fun hasInitJson(loaded: Boolean): String = "{\"has_init\":$loaded}"

    fun showTableIdJson(tableId: Int): String = "{\"table_id\":$tableId}"

    fun tableListJson(schedules: List<ScheduleProfileEntity>): String = buildJsonArray {
        schedules.forEach { schedule ->
            add(buildJsonObject {
                put("id", schedule.id)
                put("tableName", schedule.name)
            })
        }
    }.toString()

    fun requestedDate(
        pathSegments: List<String>,
        zoneId: ZoneId,
        today: LocalDate = LocalDate.now(zoneId)
    ): LocalDate {
        val suffix = pathSegments.drop(1).lastOrNull() ?: return today
        if (suffix.any { !it.isDigit() }) return today
        return runCatching {
            when (suffix.length) {
                8 -> LocalDate.parse(suffix, DateTimeFormatter.BASIC_ISO_DATE)
                10 -> Instant.ofEpochSecond(suffix.toLong()).atZone(zoneId).toLocalDate()
                13 -> Instant.ofEpochMilli(suffix.toLong()).atZone(zoneId).toLocalDate()
                else -> today
            }
        }.getOrDefault(today)
    }
}
