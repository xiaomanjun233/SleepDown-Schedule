package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import kotlinx.serialization.json.*

/** Only closed objects become provisional cards. This is never an import/save parser. */
internal fun completeStreamingCourses(output: String): List<CourseEntity> {
    val match = Regex("\"courses\"\\s*:\\s*\\[").find(output) ?: return emptyList()
    var quoted = false
    var escaped = false
    var depth = 0
    var start = -1
    val result = mutableListOf<CourseEntity>()
    for (index in match.range.last + 1 until output.length) {
        val char = output[index]
        if (quoted) {
            if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            continue
        }
        if (char == '"') { quoted = true; continue }
        if (char == ']' && depth == 0) break
        if (char == '{') { if (depth == 0) start = index; depth++ }
        if (char == '}' && depth > 0) {
            depth--
            if (depth == 0 && start >= 0) {
                runCatching {
                    val course = Json.parseToJsonElement(output.substring(start, index + 1)).jsonObject
                    fun integers(key: String) = course[key]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
                    val name = course["name"]?.jsonPrimitive?.content.orEmpty()
                    val weekday = course["weekday"]?.jsonPrimitive?.int ?: 0
                    val periods = integers("periods")
                    val weeks = integers("weeks")
                    require(name.isNotBlank() && weekday in 1..7 && periods.isNotEmpty() && weeks.isNotEmpty())
                    CourseEntity(name = name, teacher = course["teacher"]?.jsonPrimitive?.contentOrNull,
                        location = course["location"]?.jsonPrimitive?.contentOrNull, weekday = weekday,
                        periods = periods, weeks = weeks,
                        weekParity = course["weekParity"]?.jsonPrimitive?.contentOrNull?.let {
                            com.xiaomanjun.sleepdownschedule.model.WeekParity.valueOf(it)
                        } ?: com.xiaomanjun.sleepdownschedule.model.WeekParity.ALL,
                        note = course["note"]?.jsonPrimitive?.contentOrNull,
                        customStartTime = course["customStartTime"]?.jsonPrimitive?.contentOrNull,
                        customEndTime = course["customEndTime"]?.jsonPrimitive?.contentOrNull)
                }.getOrNull()?.let(result::add)
            }
        }
    }
    return result
}
