package com.xiaomanjun.sleepdownschedule

import android.content.Context
import androidx.core.content.edit

/** Bridges an import completed in a secondary Activity back to the main picker flow. */
object PendingImportSetupStore {
    private const val PreferencesName = "pending_import_setup"
    private const val ScheduleIdKey = "schedule_id"

    fun put(context: Context, scheduleId: Int) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit {
                putInt(ScheduleIdKey, scheduleId)
            }
    }

    fun consume(context: Context): Int? {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val id = preferences.getInt(ScheduleIdKey, -1).takeIf { it > 0 }
        if (id != null) preferences.edit {remove(ScheduleIdKey)}
        return id
    }
}
