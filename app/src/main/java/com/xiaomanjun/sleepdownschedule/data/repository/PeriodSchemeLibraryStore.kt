package com.xiaomanjun.sleepdownschedule.data.repository

import android.content.Context
import com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Independent of schedule IDs; deleting or importing a schedule cannot remove the library. */
object PeriodSchemeLibraryStore {
    private const val PreferencesName = "period_scheme_library"
    private const val SchemesKey = "schemes"
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(context: Context): List<SavedPeriodScheme> {
        val text = preferences(context).getString(SchemesKey, null) ?: return emptyList()
        return json.decodeFromString<List<SavedPeriodScheme>>(text).also(::validate)
    }

    @Synchronized
    fun save(context: Context, scheme: SavedPeriodScheme) {
        write(context, load(context).filterNot { it.id == scheme.id } + scheme)
    }

    @Synchronized
    fun delete(context: Context, id: String) = write(context, load(context).filterNot { it.id == id })

    @Synchronized
    fun restore(context: Context, schemes: List<SavedPeriodScheme>) {
        // A library is global user content: a restore merges it instead of discarding local entries.
        val incomingIds = schemes.mapTo(HashSet()) { it.id }
        write(context, load(context).filterNot { it.id in incomingIds } + schemes)
    }

    fun validate(schemes: List<SavedPeriodScheme>) {
        require(schemes.map { it.id }.distinct().size == schemes.size) { "作息库编号重复" }
        schemes.forEach { it.validate() }
    }

    private fun write(context: Context, schemes: List<SavedPeriodScheme>) {
        validate(schemes)
        check(preferences(context).edit().putString(SchemesKey, json.encodeToString(schemes)).commit()) {
            "作息库保存失败，请重试"
        }
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
