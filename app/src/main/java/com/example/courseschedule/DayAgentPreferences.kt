package com.example.courseschedule

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Process-independent Agent preferences.
 *
 * The preference file and every key intentionally remain unchanged so existing installations keep
 * their consent, options, memory, and applied-action idempotency records after this refactor.
 */
object DayAgentPreferences {
    private const val Prefs = "day_agent_preferences"
    private const val MemoryMaxLength = 1200
    private const val MemoryTurnsBeforeUpdate = 3
    private val mutableChanges = MutableStateFlow(0L)
    val changes: Flow<Long> = mutableChanges

    fun hasDecision(context: Context): Boolean = prefs(context).getBoolean("has_decision", false)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun isDailyAiEnabled(context: Context): Boolean = prefs(context).getBoolean("daily_ai_enabled", true)
    fun isWeatherEnabled(context: Context): Boolean = prefs(context).getBoolean("weather_enabled", true)
    fun isMemoryEnabled(context: Context): Boolean = prefs(context).getBoolean("memory_enabled", false)
    fun memory(context: Context): String = prefs(context).getString("memory", null).orEmpty()

    fun noteConversationTurn(context: Context, date: LocalDate) {
        val today = date.toString()
        val storage = prefs(context)
        val previousDay = storage.getString("memory_turn_day", null)
        val nextCount = if (previousDay == today) {
            storage.getInt("memory_turn_count", 0) + 1
        } else {
            1
        }
        storage.edit {
                putString("memory_turn_day", today)
                .putInt("memory_turn_count", nextCount)
            }
    }

    fun shouldOfferMemoryUpdate(context: Context, date: LocalDate): Boolean {
        if (!isMemoryEnabled(context)) return false
        val storage = prefs(context)
        val today = date.toString()
        return storage.getString("memory_turn_day", null) == today &&
            storage.getInt("memory_turn_count", 0) >= MemoryTurnsBeforeUpdate &&
            storage.getString("memory_last_agent_update_day", null) != today
    }

    fun setEnabled(context: Context, enabled: Boolean, markDecided: Boolean = true) {
        prefs(context).edit {
                putBoolean("enabled", enabled)
                .putBoolean("has_decision", markDecided)
            }
        mutableChanges.value += 1
    }

    fun saveOptions(context: Context, dailyAiEnabled: Boolean, weatherEnabled: Boolean) {
        prefs(context).edit {
                putBoolean("daily_ai_enabled", dailyAiEnabled)
                .putBoolean("weather_enabled", weatherEnabled)
            }
        mutableChanges.value += 1
    }

    fun setMemoryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
                putBoolean("memory_enabled", enabled)
            }
        mutableChanges.value += 1
    }

    fun saveMemory(context: Context, memory: String) {
        saveMemoryInternal(context, memory)
    }

    fun saveMemoryFromAgent(context: Context, memory: String, date: LocalDate) {
        saveMemoryInternal(context, memory)
        prefs(context).edit {
                putString("memory_last_agent_update_day", date.toString())
            }
    }

    private fun saveMemoryInternal(context: Context, memory: String) {
        val normalized = memory
            .replace("\r\n", "\n")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MemoryMaxLength)
        prefs(context).edit {
                putString("memory", normalized)
            }
        mutableChanges.value += 1
    }

    fun clearMemory(context: Context) {
        prefs(context).edit {remove("memory")}
        mutableChanges.value += 1
    }

    fun getAppliedActions(context: Context, scheduleId: Int): Set<String> {
        return prefs(context).getStringSet("applied_actions_$scheduleId", emptySet()) ?: emptySet()
    }

    fun markActionApplied(context: Context, scheduleId: Int, actionKey: String) {
        val existing = getAppliedActions(context, scheduleId).toMutableSet()
        if (existing.add(actionKey)) {
            prefs(context).edit {
                    putStringSet("applied_actions_$scheduleId", existing)
                }
        }
    }

    fun unmarkActionApplied(context: Context, scheduleId: Int, actionKey: String) {
        val existing = getAppliedActions(context, scheduleId).toMutableSet()
        if (existing.remove(actionKey)) {
            prefs(context).edit {
                    putStringSet("applied_actions_$scheduleId", existing)
                }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
}
