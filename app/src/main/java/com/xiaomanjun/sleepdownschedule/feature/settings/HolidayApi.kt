package com.xiaomanjun.sleepdownschedule.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment

/** [wage] is the overtime multiplier: 3 marks a legal holiday, 2 a plain day off, 1 a workday. */
data class HolidayProposal(val date: LocalDate, val name: String, val isRest: Boolean, val wage: Int = 2)

/** One statutory holiday period: its days off plus the make-up workdays that replace lost lessons. */
data class HolidayPlan(val name: String, val restDates: List<LocalDate>, val makeups: List<HolidayMakeup>)

/** [suggestedSource] is the teaching date this make-up day most likely replaces; schools may differ. */
data class HolidayMakeup(val date: LocalDate, val suggestedSource: LocalDate?)

/** A school's saved source dates take precedence over the service's suggested pairing. */
internal fun HolidayPlan.isAlreadyAdded(entries: List<ScheduleAdjustment>): Boolean {
    if (restDates.isEmpty() && makeups.isEmpty()) return false
    val saved = entries.associateBy { it.date }
    return restDates.all { date -> saved[date.toString()]?.let { it.sourceDate == null } == true } &&
        makeups.all { saved[it.date.toString()]?.sourceDate != null }
}

/**
 * Groups rest days into contiguous holiday periods and pairs every make-up workday with the workday it
 * replaces. A period only has to make up the workdays it takes beyond the legal holidays it contains
 * (the 3-wage days the service reports), so the make-up days around a short festival like a 中秋 next
 * to 国庆 belong to the long one. Within a period the make-up days take the last workdays lost, in
 * chronological order. Only a suggestion.
 */
fun planHolidays(proposals: List<HolidayProposal>): List<HolidayPlan> {
    val sorted = proposals.sortedBy { it.date }
    val runs = buildList<MutableList<HolidayProposal>> {
        sorted.filter { it.isRest }.forEach { rest ->
            val current = lastOrNull()
            if (current != null && ChronoUnit.DAYS.between(current.last().date, rest.date) == 1L) current += rest
            else add(mutableListOf(rest))
        }
    }
    val unassigned = sorted.filterNot { it.isRest }.toMutableList()
    val owned = runs.map { run ->
        val borrowed = run.count { it.date.dayOfWeek.value <= 5 }
        val statutory = run.count { it.wage >= 3 }.takeIf { it > 0 && borrowed > 0 } ?: minOf(1, borrowed)
        val picked = unassigned.sortedBy { distanceToRun(run, it.date) }
            .take((borrowed - statutory).coerceAtLeast(0))
        unassigned.removeAll(picked)
        picked.sortedBy { it.date }
    }.toMutableList()
    unassigned.forEach { leftover ->
        val index = runs.indices.minByOrNull { distanceToRun(runs[it], leftover.date) } ?: return@forEach
        owned[index] = (owned[index] + leftover).sortedBy { it.date }
    }
    return runs.mapIndexed { index, run ->
        val borrowed = run.map { it.date }.filter { it.dayOfWeek.value <= 5 }
        val own = owned[index]
        val targets = borrowed.takeLast(own.size)
        HolidayPlan(
            name = run.first().name,
            restDates = run.map { it.date },
            makeups = own.mapIndexed { position, makeup ->
                HolidayMakeup(makeup.date, targets.getOrNull(position))
            }
        )
    }
}

private fun distanceToRun(run: List<HolidayProposal>, date: LocalDate): Long = when {
    date < run.first().date -> ChronoUnit.DAYS.between(date, run.first().date)
    date > run.last().date -> ChronoUnit.DAYS.between(run.last().date, date)
    else -> 0L
}

/** Public, keyless API. Fetching only creates a preview; it never writes schedule data. */
object HolidayApi {
    const val Documentation = "https://timor.tech/api/holiday"

    suspend fun fetch(year: Int): List<HolidayProposal> = withContext(Dispatchers.IO) {
        require(year in 2000..2100) { "年份必须在 2000 到 2100 之间" }
        val connection = URL("https://timor.tech/api/holiday/year/$year/").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SleepDown-Schedule")
            require(connection.responseCode == 200) { "节假日服务暂时不可用，请稍后重试" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512 * 1024) { "节假日数据过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            parse(bytes.toString(Charsets.UTF_8), year)
        } finally { connection.disconnect() }
    }

    fun parse(body: String, year: Int): List<HolidayProposal> {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["code"]?.jsonPrimitive?.intOrNull == 0) { "节假日服务返回异常" }
        val holidays = root["holiday"]?.jsonObject ?: error("节假日数据缺失")
        val result = holidays.map { (key, element) ->
            val entry = element.jsonObject
            val date = LocalDate.parse(entry["date"]?.jsonPrimitive?.contentOrNull ?: "$year-$key")
            require(date.year == year) { "节假日年份不匹配" }
            HolidayProposal(date, entry["name"]?.jsonPrimitive?.contentOrNull?.take(80) ?: "调休",
                entry["holiday"]?.jsonPrimitive?.booleanOrNull ?: error("节假日类型缺失"),
                entry["wage"]?.jsonPrimitive?.intOrNull ?: 2)
        }.sortedBy { it.date }
        require(result.isNotEmpty()) { "$year 年节假日尚未公布" }
        require(result.map { it.date }.distinct().size == result.size) { "节假日日期重复" }
        return result
    }
}
