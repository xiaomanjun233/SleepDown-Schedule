package com.xiaomanjun.sleepdownschedule.data.repository

import android.content.Context
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Independent of schedule IDs; deleting or importing a schedule cannot remove the library. */
object PeriodSchemeLibraryStore {
    private const val PreferencesName = "period_scheme_library"
    private const val SchemesKey = "schemes"
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(context: Context): List<SavedPeriodScheme> {
        val text = preferences(context).getString(SchemesKey, null) ?: return emptyList()
        return json.decodeFromString<List<SavedPeriodScheme>>(text).also(::validate)
    }

    @Synchronized
    fun load(context: Context): List<SavedPeriodScheme> {
        val current = read(context)
        val normalized = normalizePeriodSchemeLibrary(current)
        if (normalized != current) write(context, normalized)
        return normalized
    }

    @Synchronized
    fun save(context: Context, scheme: SavedPeriodScheme): SavedPeriodScheme {
        val current = load(context)
        val original = current.firstOrNull { it.id == scheme.id }
        val updated = scheme.copy(sources = original?.sources.orEmpty() + scheme.sources,
            alternateNames = (original?.alternateNames.orEmpty() + scheme.alternateNames).distinct(),
            createdInLibrary = original?.createdInLibrary == true || scheme.createdInLibrary)
        val next = if (original != null) current.map { if (it.id == scheme.id) updated else it }
            else current + updated
        val normalized = normalizePeriodSchemeLibrary(next, preferredId = original?.id)
        write(context, normalized)
        return normalized.first { it.hasSameContent(scheme) }
    }

    @Synchronized
    fun delete(context: Context, id: String) = write(context, load(context).filterNot { it.id == id })

    @Synchronized
    fun restore(context: Context, schemes: List<SavedPeriodScheme>) {
        // A library is global user content: a restore merges it instead of discarding local entries.
        val merged = mergeRestoredPeriodSchemes(read(context), schemes)
        validate(merged)
        val preferences = preferences(context)
        val editor = preferences.edit().putString(SchemesKey, json.encodeToString(merged))
        // Restoring Room can allocate new schedule IDs. Source discovery must run again.
        preferences.all.keys.filter { it.startsWith("imported_schedule_") }.forEach { editor.remove(it) }
        check(editor.commit()) { "作息库保存失败，请重试" }
    }

    @Synchronized
    fun resetScheduleImports(context: Context) {
        val preferences = preferences(context)
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("imported_schedule_") }.forEach { editor.remove(it) }
        check(editor.commit()) { "作息来源更新失败，请重试" }
    }

    @Synchronized
    fun importScheduleOnce(context: Context, scheduleId: Int, scheduleName: String,
        schemes: List<SavedPeriodScheme>): List<SavedPeriodScheme> {
        val preferences = preferences(context)
        val marker = "imported_schedule_version_$scheduleId"
        val current = read(context)
        if (preferences.getInt(marker, 0) >= 2) return load(context)
        val merged = migrateSchedulePeriodSchemes(current, schemes, scheduleName,
            previouslyImported = preferences.getBoolean("imported_schedule_$scheduleId", false))
        validate(merged)
        check(preferences.edit().putString(SchemesKey, json.encodeToString(merged)).putInt(marker, 2).commit()) {
            "作息保存失败，请重试"
        }
        return merged
    }

    fun validate(schemes: List<SavedPeriodScheme>) {
        require(schemes.map { it.id }.distinct().size == schemes.size) { "作息库编号重复" }
        schemes.forEach { it.validate() }
    }

    fun hasImportedSchedule(context: Context, scheduleId: Int): Boolean =
        preferences(context).getInt("imported_schedule_version_$scheduleId", 0) >= 2

    private fun write(context: Context, schemes: List<SavedPeriodScheme>) {
        validate(schemes)
        check(preferences(context).edit().putString(SchemesKey, json.encodeToString(schemes)).commit()) {
            "作息库保存失败，请重试"
        }
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}

/** Shared by the switch menu and manager so both see the same upgraded global library. */
suspend fun ScheduleRepository.loadPeriodSchemeLibrary(context: Context, configs: List<ScheduleConfigEntity>,
    scheduleNames: Map<Int, String>): List<SavedPeriodScheme> {
    for (config in configs.distinctBy { it.id }) {
        if (PeriodSchemeLibraryStore.hasImportedSchedule(context, config.id)) continue
        val draft = loadPeriodSchemes(config.id)
        val snapshots = draft.schemes.map { item ->
            val id = UUID.nameUUIDFromBytes("sleepdown-period-${config.id}-${item.scheme.id}".toByteArray(Charsets.UTF_8)).toString()
            savePeriodSchemeSnapshot(id, item.scheme.name, config, item)
        }
        withContext(Dispatchers.IO) {
            PeriodSchemeLibraryStore.importScheduleOnce(context, config.id,
                scheduleNames[config.id]?.takeIf { it.isNotBlank() } ?: "课表 ${config.id}", snapshots)
        }
    }
    return withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.load(context) }
}
