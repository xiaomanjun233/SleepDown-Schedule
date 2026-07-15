package com.example.courseschedule

import android.content.Context
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object IcsScheduleCodec {
    private val compactDate = DateTimeFormatter.BASIC_ISO_DATE
    private val compactDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val compactDateTimeMinutes = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    private val displayTime = DateTimeFormatter.ofPattern("HH:mm")
    private val shanghaiZone = ZoneId.of("Asia/Shanghai")

    fun parse(bytes: ByteArray, baseConfig: ScheduleConfigEntity): Result<ImportDraft> = runCatching {
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require(text.contains("BEGIN:VCALENDAR", ignoreCase = true)) { "所选文件不是有效的 ICS 日历" }
        val events = parseEvents(text)
        require(events.isNotEmpty()) { "ICS 中没有找到日历事件" }

        val occurrences = events.flatMap(::expandEvent).distinctBy {
            listOf(it.name, it.location, it.description, it.start, it.end)
        }
        require(occurrences.isNotEmpty()) { "ICS 中没有可导入的定时课程事件" }

        val firstMonday = occurrences.minOf { it.start.toLocalDate() }
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rawLastWeek = occurrences.maxOf { occurrence ->
            ChronoUnit.WEEKS.between(firstMonday, occurrence.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt() + 1
        }
        val totalWeeks = rawLastWeek.coerceIn(1, 60)
        val usable = occurrences.filter { occurrence ->
            val week = ChronoUnit.WEEKS.between(
                firstMonday,
                occurrence.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ).toInt() + 1
            week in 1..totalWeeks
        }

        val timeRanges = usable
            .map { it.start.toLocalTime() to it.end.toLocalTime() }
            .distinct()
            .sortedWith(compareBy<Pair<LocalTime, LocalTime>> { it.first }.thenBy { it.second })
        require(timeRanges.isNotEmpty()) { "ICS 中没有可识别的课程时间" }
        val periodIndexByRange = timeRanges.withIndex().associate { it.value to it.index + 1 }
        val periods = timeRanges.mapIndexed { index, range ->
            PeriodEntity(
                periodIndex = index + 1,
                startTime = range.first.format(displayTime),
                endTime = range.second.format(displayTime),
                scheduleId = baseConfig.id
            )
        }

        data class CourseKey(
            val name: String,
            val teacher: String?,
            val location: String?,
            val weekday: Int,
            val periodIndex: Int,
            val note: String?
        )

        val grouped = usable.groupBy { occurrence ->
            val teacher = extractTeacher(occurrence.description)
            CourseKey(
                name = occurrence.name.ifBlank { "未命名课程" },
                teacher = teacher,
                location = occurrence.location.takeIf { it.isNotBlank() },
                weekday = occurrence.start.dayOfWeek.value,
                periodIndex = periodIndexByRange.getValue(occurrence.start.toLocalTime() to occurrence.end.toLocalTime()),
                note = occurrence.description
                    .takeIf { it.isNotBlank() }
                    ?.takeUnless { description -> teacher != null && description.trim() == "教师：$teacher" }
            )
        }
        val courses = grouped.map { (key, values) ->
            val weeks = values.map { occurrence ->
                ChronoUnit.WEEKS.between(
                    firstMonday,
                    occurrence.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ).toInt() + 1
            }.filter { it in 1..totalWeeks }.distinct().sorted()
            CourseEntity(
                name = key.name,
                teacher = key.teacher,
                location = key.location,
                weekday = key.weekday,
                periods = listOf(key.periodIndex),
                weeks = weeks,
                weekParity = WeekParity.ALL,
                note = key.note,
                scheduleId = baseConfig.id
            )
        }.filter { it.weeks.isNotEmpty() }
            .sortedWith(compareBy<CourseEntity> { it.weekday }.thenBy { it.periods.firstOrNull() ?: 0 }.thenBy { it.name })
        require(courses.isNotEmpty()) { "ICS 中没有可导入的课程" }

        val today = LocalDate.now(shanghaiZone)
        val detectedWeek = (ChronoUnit.DAYS.between(firstMonday, today).toInt() / 7 + 1).coerceIn(1, totalWeeks)
        ImportDraft(
            config = baseConfig.copy(
                totalWeeks = totalWeeks,
                currentWeek = detectedWeek,
                termStartDate = firstMonday.toString(),
                autoCurrentWeek = true
            ),
            periods = periods,
            courses = courses
        )
    }

    fun export(
        calendarName: String,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        courses: List<CourseEntity>,
        today: LocalDate = LocalDate.now(shanghaiZone)
    ): String {
        val periodsByIndex = periods.associateBy { it.periodIndex }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//SleepDown Schedule//Course Schedule//CN")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("X-WR-CALNAME:${escapeText(calendarName)}")
            courses.sortedWith(compareBy<CourseEntity> { it.weekday }.thenBy { it.name }).forEach { course ->
                val ranges = contiguousPeriodRanges(course.periods, periodsByIndex)
                course.weeks.distinct().sorted()
                    .filter { it in 1..config.totalWeeks && parityMatches(course.weekParity, it) }
                    .forEach { week ->
                        val date = scheduleWeekStartDate(config, week, today).plusDays((course.weekday - 1).toLong())
                        ranges.forEachIndexed { rangeIndex, range ->
                            val start = date.atTime(range.first)
                            val end = date.atTime(range.second)
                            appendLine("BEGIN:VEVENT")
                            appendLine("UID:sleepdown-${config.id}-${course.id}-$week-$rangeIndex@sleepdown.local")
                            appendLine("DTSTAMP:$stamp")
                            appendLine("DTSTART;TZID=Asia/Shanghai:${start.format(compactDateTime)}")
                            appendLine("DTEND;TZID=Asia/Shanghai:${end.format(compactDateTime)}")
                            appendLine("SUMMARY:${escapeText(course.name)}")
                            course.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escapeText(it)}") }
                            val description = listOfNotNull(
                                course.teacher?.takeIf { it.isNotBlank() }?.let { "教师：$it" },
                                course.note?.takeIf { it.isNotBlank() }
                            ).joinToString("\\n")
                            if (description.isNotBlank()) appendLine("DESCRIPTION:${escapeText(description)}")
                            appendLine("X-SLEEPDOWN-WEEK:$week")
                            appendLine("END:VEVENT")
                        }
                    }
            }
            appendLine("END:VCALENDAR")
        }.replace("\r\n", "\n").replace("\n", "\r\n")
    }

    fun writeShareFile(
        context: Context,
        calendarName: String,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        courses: List<CourseEntity>
    ): File {
        val directory = File(context.cacheDir, "shared_schedules").apply { mkdirs() }
        val safeName = calendarName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "SleepDown课表" }
        return File(directory, "$safeName.ics").apply {
            writeText(export(calendarName, config, periods, courses), Charsets.UTF_8)
        }
    }

    private data class IcsProperty(val name: String, val parameters: Map<String, String>, val value: String)
    private data class IcsEvent(val properties: Map<String, List<IcsProperty>>)
    private data class Occurrence(
        val name: String,
        val location: String,
        val description: String,
        val start: LocalDateTime,
        val end: LocalDateTime
    )

    private fun parseEvents(text: String): List<IcsEvent> {
        val unfolded = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] += line.drop(1)
            } else {
                unfolded += line
            }
        }
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, MutableList<IcsProperty>>? = null
        unfolded.forEach { line ->
            when (line.trim().uppercase()) {
                "BEGIN:VEVENT" -> current = linkedMapOf()
                "END:VEVENT" -> current?.let { values ->
                    events += IcsEvent(values.mapValues { it.value.toList() })
                    current = null
                }
                else -> current?.let { values ->
                    parseProperty(line)?.let { property ->
                        values.getOrPut(property.name) { mutableListOf() } += property
                    }
                }
            }
        }
        return events
    }

    private fun parseProperty(line: String): IcsProperty? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val header = line.substring(0, separator).split(';')
        val name = header.first().uppercase()
        val parameters = header.drop(1).mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) null else part.substring(0, equals).uppercase() to part.substring(equals + 1).trim('"')
        }.toMap()
        return IcsProperty(name, parameters, line.substring(separator + 1))
    }

    private fun expandEvent(event: IcsEvent): List<Occurrence> {
        val startProperty = event.properties["DTSTART"]?.firstOrNull() ?: return emptyList()
        val start = parseDateTime(startProperty) ?: return emptyList()
        val end = event.properties["DTEND"]?.firstOrNull()?.let(::parseDateTime)
            ?: event.properties["DURATION"]?.firstOrNull()?.value?.let { value ->
                runCatching { start.plus(Duration.parse(value)) }.getOrNull()
            }
            ?: start.plusHours(1)
        if (!end.isAfter(start)) return emptyList()
        val duration = Duration.between(start, end)
        val exclusions = event.properties["EXDATE"].orEmpty().flatMap { property ->
            property.value.split(',').mapNotNull { value -> parseDateTime(property.copy(value = value)) }
        }.toSet()
        val additions = event.properties["RDATE"].orEmpty().flatMap { property ->
            property.value.split(',').mapNotNull { value -> parseDateTime(property.copy(value = value)) }
        }
        val recurrence = event.properties["RRULE"]?.firstOrNull()?.value?.split(';')
            ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }
            ?.toMap()
        val starts = when (recurrence?.get("FREQ")?.uppercase()) {
            "WEEKLY" -> expandWeekly(start, recurrence)
            "DAILY" -> expandDaily(start, recurrence)
            else -> listOf(start)
        }.plus(additions).distinct().filterNot { it in exclusions }.sorted()
        val name = unescapeText(event.properties["SUMMARY"]?.firstOrNull()?.value.orEmpty()).ifBlank { "未命名课程" }
        val location = unescapeText(event.properties["LOCATION"]?.firstOrNull()?.value.orEmpty())
        val description = unescapeText(event.properties["DESCRIPTION"]?.firstOrNull()?.value.orEmpty())
        return starts.take(1000).map { occurrenceStart ->
            Occurrence(name, location, description, occurrenceStart, occurrenceStart.plus(duration))
        }
    }

    private fun expandWeekly(start: LocalDateTime, rule: Map<String, String>): List<LocalDateTime> {
        val interval = rule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = rule["COUNT"]?.toIntOrNull()?.coerceIn(1, 1000)
        val until = rule["UNTIL"]?.let { parseRuleUntil(it, start) }
        val weekdays = rule["BYDAY"]?.split(',')?.mapNotNull(::parseWeekday)?.distinct()?.sortedBy { it.value }
            .orEmpty().ifEmpty { listOf(start.dayOfWeek) }
        val firstWeek = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val output = mutableListOf<LocalDateTime>()
        var weekOffset = 0L
        while (output.size < (count ?: 1000) && weekOffset < 60L * interval) {
            val week = firstWeek.plusWeeks(weekOffset)
            weekdays.forEach { weekday ->
                val candidate = week.plusDays((weekday.value - 1).toLong()).atTime(start.toLocalTime())
                if (!candidate.isBefore(start) && (until == null || !candidate.isAfter(until)) && output.size < (count ?: 1000)) {
                    output += candidate
                }
            }
            if (until != null && week.atStartOfDay().isAfter(until)) break
            weekOffset += interval
        }
        return output
    }

    private fun expandDaily(start: LocalDateTime, rule: Map<String, String>): List<LocalDateTime> {
        val interval = rule["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
        val count = rule["COUNT"]?.toIntOrNull()?.coerceIn(1, 1000)
        val until = rule["UNTIL"]?.let { parseRuleUntil(it, start) }
        val weekdays = rule["BYDAY"]?.split(',')?.mapNotNull(::parseWeekday)?.toSet().orEmpty()
        val output = mutableListOf<LocalDateTime>()
        var candidate = start
        var checked = 0
        while (output.size < (count ?: 1000) && checked < 420) {
            if ((weekdays.isEmpty() || candidate.dayOfWeek in weekdays) && (until == null || !candidate.isAfter(until))) output += candidate
            if (until != null && candidate.isAfter(until)) break
            candidate = candidate.plusDays(interval)
            checked++
        }
        return output
    }

    private fun parseDateTime(property: IcsProperty): LocalDateTime? {
        val value = property.value.trim()
        if (property.parameters["VALUE"].equals("DATE", ignoreCase = true) || (!value.contains('T') && value.length == 8)) return null
        val zone = property.parameters["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: shanghaiZone
        val utc = value.endsWith('Z', ignoreCase = true)
        val raw = if (utc) value.dropLast(1) else value
        val local = runCatching { LocalDateTime.parse(raw, compactDateTime) }
            .recoverCatching { LocalDateTime.parse(raw, compactDateTimeMinutes) }
            .getOrNull() ?: return null
        return if (utc) local.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime() else local
    }

    private fun parseRuleUntil(value: String, reference: LocalDateTime): LocalDateTime? {
        if (!value.contains('T') && value.length == 8) {
            return runCatching { LocalDate.parse(value, compactDate).atTime(LocalTime.MAX) }.getOrNull()
        }
        return parseDateTime(IcsProperty("UNTIL", emptyMap(), value)) ?: reference
    }

    private fun parseWeekday(value: String): DayOfWeek? = when (value.trim().takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun contiguousPeriodRanges(
        indexes: List<Int>,
        periodsByIndex: Map<Int, PeriodEntity>
    ): List<Pair<LocalTime, LocalTime>> {
        val sorted = indexes.distinct().sorted().mapNotNull { index -> periodsByIndex[index]?.let { index to it } }
        if (sorted.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Pair<Int, PeriodEntity>>>()
        sorted.forEach { value ->
            val current = groups.lastOrNull()
            if (current == null || value.first != current.last().first + 1) groups += mutableListOf(value) else current += value
        }
        return groups.mapNotNull { group ->
            val start = runCatching { LocalTime.parse(group.first().second.startTime) }.getOrNull()
            val end = runCatching { LocalTime.parse(group.last().second.endTime) }.getOrNull()
            if (start != null && end != null && end.isAfter(start)) start to end else null
        }
    }

    private fun extractTeacher(description: String): String? {
        return Regex("(?:教师|老师|授课教师)\\s*[:：]\\s*([^\\n;,，；]+)")
            .find(description)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun unescapeText(value: String): String = value
        .replace("\\n", "\n", ignoreCase = true)
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
        .trim()

    private fun escapeText(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
