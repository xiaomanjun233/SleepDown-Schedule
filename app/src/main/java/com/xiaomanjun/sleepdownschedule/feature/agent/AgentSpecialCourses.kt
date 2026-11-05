package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeTimeSlotCourses
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val specialJson = Json { encodeDefaults = true }
private val specialFields = setOf("id", "name", "startTime", "endTime", "afterPeriod", "weekdays", "periodIndices",
    "customPeriodTimes", "displayMode", "location", "note", "startWeek", "endWeek")

internal fun normalizeSpecialCoursePatch(raw: String): String? = runCatching {
    require(raw.length <= 40_000)
    val patch = specialJson.parseToJsonElement(raw).jsonObject
    require(patch.isNotEmpty() && patch.keys.all { it in setOf("upsert", "deleteIds") })
    val updates = patch["upsert"]?.jsonArray.orEmpty()
    val deletes = patch["deleteIds"]?.jsonArray.orEmpty()
    require(updates.size <= 12 && deletes.size <= 12 && updates.size + deletes.size > 0)
    val ids = updates.map { value ->
        val fields = value.jsonObject
        require(fields.keys.all { it in specialFields })
        fields.getValue("id").jsonPrimitive.content.also { require(it.isNotBlank() && it.length <= 100) }
    }
    val removed = deletes.map { it.jsonPrimitive.content }
    require(ids.distinct().size == ids.size && removed.distinct().size == removed.size && ids.none { it in removed })
    patch.toString()
}.getOrNull()

/** Merge only explicitly named fields, preserving other courses and all untouched editor fields. */
internal fun applySpecialCoursePatch(current: String, raw: String): String? = runCatching {
    val patch = specialJson.parseToJsonElement(requireNotNull(normalizeSpecialCoursePatch(raw))).jsonObject
    val values = decodeTimeSlotCourses(current).associateByTo(linkedMapOf()) { it.id }
    patch["deleteIds"]?.jsonArray.orEmpty().forEach { values.remove(it.jsonPrimitive.content) }
    patch["upsert"]?.jsonArray.orEmpty().forEach { element ->
        val update = element.jsonObject
        val id = update.getValue("id").jsonPrimitive.content
        val old = values[id]?.let { specialJson.encodeToJsonElement(it).jsonObject }.orEmpty()
        val merged = JsonObject(old + update)
        values[id] = specialJson.decodeFromJsonElement<TimeSlotCourse>(merged)
    }
    encodeTimeSlotCourses(values.values.toList())
}.getOrNull()

internal fun specialCourseSummary(raw: String): String = runCatching {
    decodeTimeSlotCourses(raw).joinToString("\n") { slot ->
        "${slot.name} · ${slot.startTime}–${slot.endTime} · 星期${slot.weekdays.joinToString("、")} · " +
            (if (slot.afterPeriod == 0) "第一节前" else "第${slot.afterPeriod}节后") +
            " · 第${slot.startWeek}–${slot.endWeek?.toString() ?: "学期末"}周" +
            slot.location.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
    }.ifBlank { "无特殊课程" }
}.getOrDefault("特殊课程数据无法读取，请先在设置中检查")
