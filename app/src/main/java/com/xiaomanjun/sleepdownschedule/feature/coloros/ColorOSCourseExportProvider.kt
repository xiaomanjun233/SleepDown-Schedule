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
        if (path !in supportedPaths) return null
        val appContext = context?.applicationContext ?: return oneRow(-1, "{}")
        if (callingPackage == ColorOSCourseContract.PROXY_PACKAGE) {
            ColorOSCourseBridge.recordProxyQuery(
                appContext,
                uri.encodedPath.orEmpty(),
                uri.getQueryParameter(PROXY_CALLER_PARAMETER)
            )
        }
        return when (path) {
            "has_init" -> oneRow(databaseJson("{\"has_init\":false}") {
                val loaded = app().repository.activeSnapshot().loaded
                "{\"has_init\":$loaded}"
            })
            "show_table_id" -> oneRow(databaseJson("{\"table_id\":1}") {
                "{\"table_id\":${app().repository.activeSnapshot().config.id}}"
            })
            "table_list" -> oneRow(databaseJson("[]") {
                buildJsonArray {
                    app().repository.snapshot().schedules.forEach { schedule ->
                        add(buildJsonObject {
                            put("id", schedule.id)
                            put("tableName", schedule.name)
                        })
                    }
                }.toString()
            })
            "course_list" -> oneRow(courseJson(uri, tomorrow = false))
            "next_course_list" -> oneRow(courseJson(uri, tomorrow = true))
            else -> null
        }
    }

    private fun courseJson(uri: Uri, tomorrow: Boolean): Pair<Int, String> = databaseJson("[]") {
        val zoneId = ZoneId.systemDefault()
        val date = requestedDate(uri, zoneId).let { if (tomorrow) it.plusDays(1) else it }
        val result = ColorOSCourseMapper.export(date, app().repository.activeSnapshot(), zoneId)
        context?.let { ColorOSCourseBridge.recordExport(it, result.exportedCount) }
        result.json
    }

    private fun requestedDate(uri: Uri, zoneId: ZoneId): LocalDate {
        val suffix = uri.pathSegments.drop(1).lastOrNull() ?: return LocalDate.now(zoneId)
        if (suffix.any { !it.isDigit() }) return LocalDate.now(zoneId)
        return runCatching {
            when (suffix.length) {
                8 -> LocalDate.parse(suffix, DateTimeFormatter.BASIC_ISO_DATE)
                10 -> Instant.ofEpochSecond(suffix.toLong()).atZone(zoneId).toLocalDate()
                13 -> Instant.ofEpochMilli(suffix.toLong()).atZone(zoneId).toLocalDate()
                else -> LocalDate.now(zoneId)
            }
        }.getOrDefault(LocalDate.now(zoneId))
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
        val supportedPaths = setOf(
            "has_init",
            "show_table_id",
            "table_list",
            "course_list",
            "next_course_list"
        )
        const val PROXY_CALLER_PARAMETER = "sleepdown_proxy_caller"
    }
}
