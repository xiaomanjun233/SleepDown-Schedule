package com.xiaomanjun.sleepdownschedule.data.repository

import android.content.Context
import com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.serialization.json.Json

/** Read-only compatibility bridge. Live writes and the one-time migration journal belong to Room. */
object PeriodSchemeLibraryStore {
    fun load(context: Context): List<SavedPeriodScheme> {
        val text = context.applicationContext.getSharedPreferences("period_scheme_library", Context.MODE_PRIVATE)
            .getString("schemes", null) ?: return emptyList()
        return Json { ignoreUnknownKeys = true }.decodeFromString<List<SavedPeriodScheme>>(text).also(::validate)
    }

    fun validate(schemes: List<SavedPeriodScheme>) {
        require(schemes.map { it.id }.distinct().size == schemes.size) { "作息库编号重复" }
        schemes.forEach { it.validate() }
    }
}

/** Both views read the same global records, including inactive and newly imported schemes. */
@Suppress("UNUSED_PARAMETER")
suspend fun ScheduleRepository.loadPeriodSchemeLibrary(context: Context, configs: List<ScheduleConfigEntity>,
    scheduleNames: Map<Int, String>): List<SavedPeriodScheme> {
    migrateLegacyPeriodSchemeLibrary(context)
    return publicPeriodSchemes()
}
