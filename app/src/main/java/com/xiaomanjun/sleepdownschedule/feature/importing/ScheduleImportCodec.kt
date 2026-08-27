package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Serializable
data class ScheduleImportPayload(
    val schemaVersion: Int,
    val scheduleConfig: ScheduleConfigPayload,
    val courses: List<ScheduleImportCourse>
)

@Serializable
data class ScheduleConfigPayload(val totalWeeks: Int, val periods: List<PeriodPayload>)

@Serializable
data class PeriodPayload(val index: Int, val startTime: String, val endTime: String)

@Serializable
data class ScheduleImportCourse(
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val weekParity: WeekParityPayload = WeekParityPayload.ALL,
    val note: String? = null,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val customColorArgb: Long? = null
)

@Serializable
enum class WeekParityPayload {
    @SerialName("ALL") ALL,
    @SerialName("ODD") ODD,
    @SerialName("EVEN") EVEN
}

object SchedulePromptBuilder {
    fun build(): String = """
请把我的课程表整理成严格 JSON，不要输出解释文字。
JSON 协议如下：
{
  "schemaVersion": 1,
  "scheduleConfig": {
    "totalWeeks": 20,
    "periods": [
      {"index": 1, "startTime": "08:00", "endTime": "08:45"},
      {"index": 2, "startTime": "08:55", "endTime": "09:40"}
    ]
  },
  "courses": [
    {
      "name": "高等数学",
      "teacher": "张老师",
      "location": "A101",
      "weekday": 1,
      "periods": [1, 2],
      "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],
      "weekParity": "ALL",
      "note": ""
    }
  ]
}
要求：weekday 使用 1-7 表示周一到周日；periods 必须引用 scheduleConfig.periods 里的 index；weeks 必须在 1 到 totalWeeks 内；weekParity 只能是 ALL、ODD、EVEN；时间必须是 HH:mm。只返回一个完整 JSON 对象。JSON 可以正常换行和缩进，但不要在字符串值中为了排版擅自插入换行，不要使用 Markdown 代码块。
""".trimIndent()

    fun buildTokenPrompt(): String = """
请读取我发给你的课表 PDF，并只输出 SleepDown 课程表口令，不要解释、不要 Markdown。
口令格式：
SDCT1
T=总周数
P=节次,开始时间-结束时间;节次,开始时间-结束时间
C=课程名|教师|地点|星期|节次|周次|单双周|备注

规则：
1. 星期用 1-7 表示周一到周日。
2. 节次用逗号或范围，例如 1,2 或 1-2。
3. 周次用逗号或范围，例如 1-16 或 1-8,10,12-16。
4. 单双周用 A/O/E，分别表示全部/单周/双周。
5. 没有教师、地点或备注时写 -。
6. 时间必须是 HH:mm。
7. 必须严格换行：SDCT1 单独一行，T= 单独一行，全部节次写在同一条 P= 行；每一门课程各占一条独立的 C= 行。
8. 不要把一条 P= 或 C= 记录折成多行，不要添加项目符号、序号、空行、Markdown 代码围栏或解释文字。
9. 课程名称、教师、地点和备注中不要使用竖线“|”；如原文包含竖线，请改为空格。每个 C= 行必须恰好包含 8 个由“|”分隔的字段。
示例：
SDCT1
T=20
P=1,08:00-08:45;2,08:55-09:40;3,10:00-10:45
C=高等数学|张老师|A101|1|1-2|1-16|A|-
C=大学英语|-|B203|3|3|2-18|O|-
""".trimIndent()
}

object ScheduleImportParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun cleanMarkdown(input: String): String {
        val trimmed = input.trim()
        val fence = Char(96).toString().repeat(3)
        val firstFence = trimmed.indexOf(fence)
        if (firstFence >= 0) {
            val afterFence = trimmed.indexOf('\n', firstFence).let { if (it < 0) firstFence + fence.length else it + 1 }
            val lastFence = trimmed.indexOf(fence, afterFence)
            if (lastFence >= 0) return trimmed.substring(afterFence, lastFence).trim()
            return trimmed.removePrefix(fence).removePrefix("json").trim()
        }
        if (containsSleepDownToken(trimmed)) return trimmed
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim()
        }
        return trimmed
    }

    fun parse(input: String, baseConfig: ScheduleConfigEntity): Result<ImportDraft> = runCatching {
        val cleaned = cleanMarkdown(input)
        val payload = if (containsSleepDownToken(cleaned)) {
            parseSleepDownToken(cleaned)
        } else {
            decodeSchedulePayloadWithFallback(cleaned)
        }
        if (containsSleepDownToken(cleaned)) {
            validatePayload(payload, baseConfig)
        } else {
            runCatching { validatePayload(payload, baseConfig) }.getOrElse { firstError ->
                val normalizedPayload = decodeNormalizedSchedulePayload(cleaned, firstError)
                runCatching { validatePayload(normalizedPayload, baseConfig) }.getOrElse { secondError ->
                    throw IllegalArgumentException(
                        "本地校验失败：${firstError.message.orEmpty()}；容错清洗后仍失败：${secondError.message.orEmpty()}",
                        secondError
                    )
                }
            }
        }
    }

    fun parseTimeForUi(value: String): LocalTime = LocalTime.parse(value, timeFormatter)

    private fun decodeSchedulePayloadWithFallback(cleaned: String): ScheduleImportPayload {
        return runCatching {
            json.decodeFromString<ScheduleImportPayload>(cleaned)
        }.getOrElse { firstError ->
            runCatching { decodeNormalizedSchedulePayload(cleaned, firstError) }.getOrElse { secondError ->
                throw IllegalArgumentException(
                    "JSON 结构无法解析：${firstError.message.orEmpty()}；容错清洗后仍失败：${secondError.message.orEmpty()}",
                    secondError
                )
            }
        }
    }

    private fun decodeNormalizedSchedulePayload(cleaned: String, cause: Throwable): ScheduleImportPayload {
        val normalizedText = runCatching {
            normalizeAiScheduleJson(Json.parseToJsonElement(cleaned)).toString()
        }.getOrElse {
            throw cause
        }
        return json.decodeFromString(normalizedText)
    }

    private fun normalizeAiScheduleJson(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val courses = element["courses"]?.jsonArray
        return JsonObject(element.mapValues { (key, value) ->
            if (key == "courses" && courses != null) {
                JsonArray(courses.map { normalizeAiCourseJson(it) })
            } else {
                value
            }
        })
    }

    private fun normalizeAiCourseJson(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val normalized = element.mapValues { (key, value) ->
            when (key) {
                "weekday" -> JsonPrimitive(parseFlexibleWeekday(value) ?: value.jsonPrimitiveOrNull()?.intOrNull ?: 7)
                "periods", "weeks" -> flexibleIntArray(value)
                "weekParity" -> JsonPrimitive(normalizeWeekParity(value))
                else -> value
            }
        }.toMutableMap()
        val periods = normalized["periods"] as? JsonArray
        val weeks = normalized["weeks"] as? JsonArray
        val weekday = normalized["weekday"]?.jsonPrimitiveOrNull()?.intOrNull
        val needsPlaceholder = weekday !in 1..7 || periods == null || periods.isEmpty() || weeks == null || weeks.isEmpty()
        if (needsPlaceholder) {
            normalized["weekday"] = JsonPrimitive((weekday ?: 7).coerceIn(1, 7))
            if (periods == null || periods.isEmpty()) normalized["periods"] = JsonArray(listOf(JsonPrimitive(1)))
            if (weeks == null || weeks.isEmpty()) normalized["weeks"] = JsonArray(listOf(JsonPrimitive(1)))
            val note = normalized["note"]?.jsonPrimitiveOrNull()?.contentOrNull.orEmpty().trim()
            normalized["note"] = JsonPrimitive(
                listOf(note, "AI 占位课程：原始数据没有明确上课星期/节次/周次，请手动修改。")
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("；")
            )
        }
        return JsonObject(normalized)
    }

    private fun JsonElement.jsonPrimitiveOrNull() = this as? JsonPrimitive

    private fun flexibleIntArray(value: JsonElement): JsonArray {
        val values = when (value) {
            is JsonArray -> value.flatMap { item ->
                when (item) {
                    is JsonPrimitive -> parseFlexibleNumbers(item.contentOrNull.orEmpty())
                    else -> emptyList()
                }
            }
            is JsonPrimitive -> parseFlexibleNumbers(value.contentOrNull.orEmpty())
            else -> emptyList()
        }
        return buildJsonArray { values.distinct().sorted().forEach { add(JsonPrimitive(it)) } }
    }

    private fun parseFlexibleNumbers(raw: String): List<Int> {
        val text = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace(';', ',')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace("周", "")
            .trim()
        if (text.isBlank()) return emptyList()
        return text.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { part ->
                val range = part.split('-', limit = 2).map { it.trim() }
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start != null && end != null) {
                        if (start <= end) (start..end).toList() else (end..start).toList()
                    } else {
                        emptyList()
                    }
                } else {
                    listOfNotNull(part.toIntOrNull())
                }
            }
    }

    private fun parseFlexibleWeekday(value: JsonElement): Int? {
        val primitive = value.jsonPrimitiveOrNull() ?: return null
        primitive.intOrNull?.let { return it }
        return when (primitive.contentOrNull.orEmpty().trim()) {
            "1", "一", "周一", "星期一", "礼拜一", "Monday", "MONDAY" -> 1
            "2", "二", "周二", "星期二", "礼拜二", "Tuesday", "TUESDAY" -> 2
            "3", "三", "周三", "星期三", "礼拜三", "Wednesday", "WEDNESDAY" -> 3
            "4", "四", "周四", "星期四", "礼拜四", "Thursday", "THURSDAY" -> 4
            "5", "五", "周五", "星期五", "礼拜五", "Friday", "FRIDAY" -> 5
            "6", "六", "周六", "星期六", "礼拜六", "Saturday", "SATURDAY" -> 6
            "7", "日", "天", "周日", "周天", "星期日", "星期天", "礼拜日", "礼拜天", "Sunday", "SUNDAY" -> 7
            else -> null
        }
    }

    private fun normalizeWeekParity(value: JsonElement): String {
        val raw = value.jsonPrimitiveOrNull()?.contentOrNull.orEmpty().trim().uppercase()
        return when (raw) {
            "ODD", "O", "SINGLE", "单", "单周", "奇", "奇周" -> "ODD"
            "EVEN", "E", "DOUBLE", "双", "双周", "偶", "偶周" -> "EVEN"
            else -> "ALL"
        }
    }

    private fun validatePayload(payload: ScheduleImportPayload, baseConfig: ScheduleConfigEntity): ImportDraft {
        require(payload.schemaVersion == 1) { "schemaVersion 目前只支持 1" }
        require(payload.scheduleConfig.totalWeeks in 1..60) { "totalWeeks 必须在 1 到 60 之间" }
        require(payload.scheduleConfig.periods.isNotEmpty()) { "periods 不能为空" }
        val uniquePeriodPayloads = payload.scheduleConfig.periods.distinctBy { it.index }.sortedBy { it.index }
        val indexes = uniquePeriodPayloads.map { it.index }
        require(indexes.all { it > 0 }) { "节次 index 必须大于 0" }
        val periods = uniquePeriodPayloads.map {
            val start = parseTime(it.startTime, "第 ${it.index} 节 startTime")
            val end = parseTime(it.endTime, "第 ${it.index} 节 endTime")
            require(start < end) { "第 ${it.index} 节结束时间必须晚于开始时间" }
            PeriodEntity(it.index, it.startTime, it.endTime)
        }
        val validPeriodIndexes = indexes.toSet()
        val courses = payload.courses.mapIndexed { position, course ->
            val row = "第 ${position + 1} 门课程"
            require(course.name.isNotBlank()) { "$row name 不能为空" }
            require(course.weekday in 1..7) { "$row weekday 必须在 1 到 7 之间" }
            require(course.periods.isNotEmpty()) { "$row periods 不能为空" }
            require(course.periods.all { it in validPeriodIndexes }) { "$row 引用了不存在的节次" }
            require(course.weeks.isNotEmpty()) { "$row weeks 不能为空" }
            require(course.weeks.all { it in 1..payload.scheduleConfig.totalWeeks }) { "$row weeks 超出 totalWeeks" }
            val customStartText = course.customStartTime?.trim()?.ifBlank { null }
            val customEndText = course.customEndTime?.trim()?.ifBlank { null }
            require((customStartText == null) == (customEndText == null)) {
                "$row 自定义开始和结束时间必须同时提供"
            }
            if (customStartText != null && customEndText != null) {
                val customStart = parseTime(customStartText, "$row customStartTime")
                val customEnd = parseTime(customEndText, "$row customEndTime")
                require(customStart < customEnd) { "$row 自定义结束时间必须晚于开始时间" }
            }
            require(course.customColorArgb == null || course.customColorArgb in 0L..0xFFFFFFFFL) {
                "$row customColorArgb 必须是有效 ARGB 值"
            }
            val normalizedWeeks = normalizeImportedWeekSelection(
                weeks = course.weeks,
                parity = when (course.weekParity) {
                    WeekParityPayload.ALL -> WeekParity.ALL
                    WeekParityPayload.ODD -> WeekParity.ODD
                    WeekParityPayload.EVEN -> WeekParity.EVEN
                }
            )
            CourseEntity(
                name = course.name.trim(),
                teacher = course.teacher?.trim()?.ifBlank { null },
                location = course.location?.trim()?.ifBlank { null },
                weekday = course.weekday,
                periods = course.periods.distinct().sorted(),
                weeks = normalizedWeeks.weeks,
                weekParity = normalizedWeeks.parity,
                note = course.note?.trim()?.ifBlank { null },
                customStartTime = customStartText,
                customEndTime = customEndText,
                customColorArgb = course.customColorArgb
            )
        }
        return ImportDraft(
            config = baseConfig.copy(
                totalWeeks = payload.scheduleConfig.totalWeeks,
                currentWeek = effectiveCurrentWeek(baseConfig).coerceIn(1, payload.scheduleConfig.totalWeeks)
            ),
            periods = periods,
            courses = courses
        )
    }

    private fun parseTime(value: String, label: String): LocalTime = try {
        LocalTime.parse(value, timeFormatter)
    } catch (error: Exception) {
        throw IllegalArgumentException("$label 必须是 HH:mm")
    }

    private fun containsSleepDownToken(input: String): Boolean {
        return input.lineSequence().any { it.trim().contains("SDCT1") }
    }

    private fun parseSleepDownToken(input: String): ScheduleImportPayload {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val startIndex = lines.indexOfFirst { it.contains("SDCT1") }
        require(startIndex >= 0) { "未找到 SleepDown 课程表口令 SDCT1" }
        var totalWeeks = 20
        val periods = mutableListOf<PeriodPayload>()
        val courses = mutableListOf<ScheduleImportCourse>()
        lines.drop(startIndex + 1).forEach { line ->
            when {
                line.startsWith("T=") -> totalWeeks = line.removePrefix("T=").trim().toInt()
                line.startsWith("P=") -> {
                    line.removePrefix("P=").split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { item ->
                        val parts = item.split(',', limit = 2)
                        require(parts.size == 2) { "节次口令格式应为 节次,开始-结束" }
                        val times = parts[1].split('-', limit = 2)
                        require(times.size == 2) { "节次时间格式应为 HH:mm-HH:mm" }
                        periods += PeriodPayload(parts[0].trim().toInt(), times[0].trim(), times[1].trim())
                    }
                }
                line.startsWith("C=") -> {
                    val fields = line.removePrefix("C=").split('|')
                    require(fields.size >= 7) { "课程口令至少需要 7 个字段" }
                    courses += ScheduleImportCourse(
                        name = fields[0].trim(),
                        teacher = tokenText(fields.getOrNull(1)),
                        location = tokenText(fields.getOrNull(2)),
                        weekday = fields[3].trim().toInt(),
                        periods = parseNumberRanges(fields[4]),
                        weeks = parseNumberRanges(fields[5]),
                        weekParity = when (fields[6].trim().uppercase()) {
                            "O", "ODD" -> WeekParityPayload.ODD
                            "E", "EVEN" -> WeekParityPayload.EVEN
                            else -> WeekParityPayload.ALL
                        },
                        note = tokenText(fields.getOrNull(7)),
                        customStartTime = tokenText(fields.getOrNull(8)),
                        customEndTime = tokenText(fields.getOrNull(9)),
                        customColorArgb = tokenText(fields.getOrNull(10))?.let { encoded ->
                            encoded.toLongOrNull()
                                ?: throw IllegalArgumentException("课程颜色必须是十进制 ARGB")
                        }
                    )
                }
            }
        }
        require(periods.isNotEmpty()) { "口令缺少 P= 节次定义" }
        require(courses.isNotEmpty()) { "口令缺少 C= 课程定义" }
        return ScheduleImportPayload(1, ScheduleConfigPayload(totalWeeks, periods), courses)
    }

    private fun tokenText(value: String?): String? {
        val text = value?.trim().orEmpty()
        return text.takeUnless { it.isBlank() || it == "-" }
    }

    private fun parseNumberRanges(value: String): List<Int> {
        return value.replace('，', ',')
            .split(',')
            .flatMap { part ->
                val token = part.trim()
                if (token.isBlank()) return@flatMap emptyList()
                if (token.contains('-')) {
                    val bounds = token.split('-', limit = 2).map { it.trim().toInt() }
                    val start = minOf(bounds[0], bounds[1])
                    val end = maxOf(bounds[0], bounds[1])
                    (start..end).toList()
                } else {
                    listOf(token.toInt())
                }
            }
            .distinct()
            .sorted()
    }
}

internal data class NormalizedImportedWeekSelection(
    val weeks: List<Int>,
    val parity: WeekParity
)

internal fun normalizeImportedWeekSelection(
    weeks: List<Int>,
    parity: WeekParity
): NormalizedImportedWeekSelection {
    val sorted = weeks.filter { it > 0 }.distinct().sorted()
    if (sorted.size < 2) return NormalizedImportedWeekSelection(sorted, parity)
    val inferredParity = when {
        parity != WeekParity.ALL -> parity
        sorted.all { it % 2 == 0 } -> WeekParity.EVEN
        sorted.all { it % 2 == 1 } -> WeekParity.ODD
        else -> WeekParity.ALL
    }
    if (inferredParity == WeekParity.ALL) {
        return NormalizedImportedWeekSelection(sorted, WeekParity.ALL)
    }
    val expected = (sorted.first()..sorted.last()).filter { week ->
        when (inferredParity) {
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
            WeekParity.ALL -> true
        }
    }
    return if (sorted == expected) {
        NormalizedImportedWeekSelection(
            weeks = (sorted.first()..sorted.last()).toList(),
            parity = inferredParity
        )
    } else {
        // Irregular selections such as 2, 6, 8 must stay explicit; inferring a parity range here
        // would silently add lessons the user never imported.
        NormalizedImportedWeekSelection(sorted, parity)
    }
}

fun buildSleepDownScheduleToken(
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    courses: List<CourseEntity>
): String {
    val periodLine = periods
        .filter { it.periodIndex > 0 }
        .distinctBy { it.periodIndex }
        .sortedBy { it.periodIndex }
        .joinToString(";") { "${it.periodIndex},${it.startTime}-${it.endTime}" }
    val courseLines = courses
        .sortedWith(compareBy<CourseEntity> { it.weekday }.thenBy { it.periods.minOrNull() ?: 0 }.thenBy { it.name })
        .map { course ->
            listOf(
                tokenField(course.name),
                tokenField(course.teacher),
                tokenField(course.location),
                course.weekday.toString(),
                formatNumberRanges(course.periods),
                formatNumberRanges(course.weeks),
                when (course.weekParity) {
                    WeekParity.ALL -> "A"
                    WeekParity.ODD -> "O"
                    WeekParity.EVEN -> "E"
                },
                tokenField(course.note),
                tokenField(course.customStartTime),
                tokenField(course.customEndTime),
                course.customColorArgb?.toString() ?: "-"
            ).joinToString("|")
        }
    return buildString {
        appendLine("SDCT1")
        appendLine("T=${config.totalWeeks.coerceIn(1, 60)}")
        appendLine("P=$periodLine")
        courseLines.forEach { appendLine("C=$it") }
    }.trimEnd()
}

private fun tokenField(value: String?): String {
    val text = value
        ?.replace('|', ' ')
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        .orEmpty()
    return text.ifBlank { "-" }
}

private fun formatNumberRanges(values: List<Int>): String {
    val sorted = values.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return "-"
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    sorted.drop(1).forEach { value ->
        if (value == previous + 1) {
            previous = value
        } else {
            ranges += if (start == previous) start.toString() else "$start-$previous"
            start = value
            previous = value
        }
    }
    ranges += if (start == previous) start.toString() else "$start-$previous"
    return ranges.joinToString(",")
}
