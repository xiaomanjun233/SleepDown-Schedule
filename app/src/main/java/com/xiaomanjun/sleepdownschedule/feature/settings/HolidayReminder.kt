package com.xiaomanjun.sleepdownschedule.feature.settings

import android.content.Context
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekForDateOrNull
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A statutory holiday that is close enough to matter and has not been set up yet. */
data class UpcomingHoliday(val name: String, val start: LocalDate, val end: LocalDate)

/**
 * Answers "which holiday is coming up" for the daily home prompt. The public calendar is cached per
 * year so the prompt keeps working offline after one successful fetch, and the last prompt date is
 * remembered so the dialog appears at most once a day.
 */
object HolidayReminder {
    private const val PREFS = "holiday_reminder"
    private const val KEY_CALENDAR = "calendar_"
    private const val KEY_FETCHED_AT = "fetched_at_"
    private const val KEY_LAST_PROMPT = "last_prompt_date"
    private const val LEAD_DAYS = 14L
    private const val REFRESH_DAYS = 30L

    suspend fun upcomingHoliday(
        context: Context,
        config: ScheduleConfigEntity,
        today: LocalDate = LocalDate.now()
    ): UpcomingHoliday? {
        val configured = decodeScheduleAdjustments(config.scheduleAdjustmentsJson).map { it.date }.toSet()
        val proposals = load(context, today)
        if (proposals.isEmpty()) return null
        return planHolidays(proposals)
            .filter { it.restDates.isNotEmpty() }
            .mapNotNull { plan ->
                val start = plan.restDates.first()
                if (ChronoUnit.DAYS.between(today, start) !in 0..LEAD_DAYS) return@mapNotNull null
                if (config.autoCurrentWeek && scheduleWeekForDateOrNull(config, start) == null) return@mapNotNull null
                // A holiday the user already planned stays silent, however many days remain.
                if (plan.restDates.any { it.toString() in configured }) return@mapNotNull null
                UpcomingHoliday(plan.name, start, plan.restDates.last())
            }
            .minByOrNull { it.start }
    }

    /** Claims today's single prompt. Returns false when the user has already been asked today. */
    fun claimDailyPrompt(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = today.toString()
        if (prefs.getString(KEY_LAST_PROMPT, null) == key) return false
        prefs.edit { putString(KEY_LAST_PROMPT, key) }
        return true
    }

    private suspend fun load(context: Context, today: LocalDate): List<HolidayProposal> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return listOf(today.year, today.year + 1).flatMap { year ->
            val cached = prefs.getString(KEY_CALENDAR + year, null)?.let(::decode)
            val fetchedAt = prefs.getLong(KEY_FETCHED_AT + year, 0L)
            val fresh = fetchedAt > 0L && ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                today
            ) < REFRESH_DAYS
            if (cached != null && fresh) return@flatMap cached
            // Next year is usually not published yet; a failed refresh keeps whatever is cached.
            val fetched = runCatching { HolidayApi.fetch(year) }.getOrNull()
            if (fetched == null) cached.orEmpty() else {
                prefs.edit {
                    putString(KEY_CALENDAR + year, encode(fetched))
                    putLong(KEY_FETCHED_AT + year, System.currentTimeMillis())
                }
                fetched
            }
        }
    }

    private fun encode(values: List<HolidayProposal>): String =
        values.joinToString(";") { "${it.date}|${it.name}|${if (it.isRest) 1 else 0}|${it.wage}" }

    private fun decode(value: String): List<HolidayProposal> = value.split(';').mapNotNull { row ->
        val parts = row.split('|')
        if (parts.size < 4) return@mapNotNull null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
        HolidayProposal(date, parts[1], parts[2] == "1", parts[3].toIntOrNull() ?: 2)
    }
}
