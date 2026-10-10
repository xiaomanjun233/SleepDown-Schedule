package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityMatches
import com.xiaomanjun.sleepdownschedule.feature.importing.ScheduleImportParser
import com.xiaomanjun.sleepdownschedule.feature.importing.draftToPayload
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import kotlinx.serialization.json.*
import java.time.LocalDate

/** A detached, revisioned JSON document. No application, calendar or database handles live here. */
class AgentImportWorkspace internal constructor(
    draft: ImportDraft,
    private val onlyCurrentWeek: Boolean = false,
    private val onCheckpoint: (ImportDraft, String) -> Unit = { _, _ -> }
) {
    internal var draft: ImportDraft = AgentImportRuntime.workspace(draft)
        private set
    internal var revision: Int = 0
        private set
    private var lastAssignedCourseId = this.draft.courses.maxOfOrNull { it.id } ?: 0L
    private val initial = this.draft
    private val restrictedWeek = buildDayAgentFacts(initial.courses, initial.periods, initial.config, LocalDate.now(), null).currentWeek

    internal fun execute(call: AgentToolCall): AgentToolResult {
        call.validationError?.let { return AgentToolResult(call.id, call.name, false, it) }
        return try {
            when (call.name) {
                AgentToolName.READ_IMPORT_JSON -> AgentToolResult(call.id, call.name, true, readJson())
                AgentToolName.PATCH_IMPORT_JSON -> {
                    require(call.arguments["revision"]?.toIntOrNull() == revision) {
                        "草稿版本已变化，当前 revision=$revision；请重新读取 JSON 后按新索引修改。"
                    }
                    val candidate = patchedDraft(call.arguments["operationsJson"].orEmpty())
                    publish(candidate, call.arguments["summary"].orEmpty().ifBlank { "已校验 JSON 编辑" })
                    AgentToolResult(call.id, call.name, true,
                        "完整草稿已通过本地校验；revision=$revision，课程 ${draft.courses.size} 条。" +
                            "该阶段可供用户预览选择，尚未保存到课表。后续修改请使用新 revision；可继续读取 JSON。")
                }
                else -> AgentToolResult(call.id, call.name, false,
                    "导入工作区只允许 READ_IMPORT_JSON 和 PATCH_IMPORT_JSON；不能操作应用设置、日历、数据库或助手记忆。")
            }
        } catch (error: IllegalArgumentException) {
            AgentToolResult(call.id, call.name, false,
                "JSON 草稿校验失败，未应用本次补丁，revision=$revision：" +
                    error.message.orEmpty().substringBefore("JSON input:").take(800) +
                    "。请修正完整补丁后重试，未涉及的数据必须保留。")
        }
    }

    internal fun validateAnswer(answer: String): String? = runCatching {
        checked(AgentImportRuntime.applyPlan(draft, answer, onlyCurrentWeek))
    }.exceptionOrNull()?.message

    internal fun applyAnswer(answer: String, summary: String) {
        publish(AgentImportRuntime.applyPlan(draft, answer, onlyCurrentWeek), summary)
    }

    private fun readJson(): String = buildJsonObject {
        put("revision", revision)
        put("draft", document())
        put("note", "数组索引从 0 开始；_courseId、customPeriodTimes、originalPeriodTimes 为只读来源信息。" +
            "补丁路径相对于 draft。先读后改；每个成功补丁都是完整且通过校验的独立阶段。")
    }.toString()

    private fun document(): JsonObject {
        val payload = Json.parseToJsonElement(draftToPayload(draft).toString()).jsonObject
        return JsonObject(payload + ("courses" to JsonArray(payload.getValue("courses").jsonArray.mapIndexed { index, course ->
            JsonObject(course.jsonObject + ("_courseId" to JsonPrimitive(draft.courses[index].id)))
        })))
    }

    private fun patchedDraft(text: String): ImportDraft {
        require(text.length <= 120_000) { "补丁超过 120000 字符，请分成独立完整阶段" }
        val operations = Json.parseToJsonElement(text) as? JsonArray
            ?: throw IllegalArgumentException("operationsJson 必须是完整 JSON 数组")
        require(operations.size in 1..300) { "每次需要 1 至 300 个补丁操作" }
        var document: JsonElement = document()
        val originalRows = document.jsonObject.getValue("courses").jsonArray.associate {
            it.jsonObject.getValue("_courseId").jsonPrimitive.long to it.jsonObject
        }
        operations.forEach { element ->
            val operation = element as? JsonObject ?: throw IllegalArgumentException("补丁操作必须是对象")
            require(operation.keys.all { it in setOf("op", "path", "value", "from") }) { "补丁仅支持 op、path、value、from" }
            val op = operation["op"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val path = operation["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            require(op in setOf("add", "replace", "remove", "copy")) { "仅支持 add、replace、remove、copy" }
            require(op in setOf("remove", "copy") || operation.containsKey("value")) { "add / replace 缺少 value" }
            val parts = path.removePrefix("/").split('/')
            require(path.startsWith('/') && parts.none { it.isBlank() || '~' in it }) { "需要明确的 JSON 路径" }
            require(editablePath(parts, if (op == "copy") "add" else op)) { "此路径不可编辑：$path；只可编辑导入字段，来源铃声与 ID 为只读" }
            if (op == "copy") {
                require(parts.first() == "courses" && parts.size == 2) { "copy 仅用于复制一条已有课程以拆分周次" }
                val from = operation["from"]?.jsonPrimitive?.contentOrNull.orEmpty()
                require(Regex("/courses/[0-9]+").matches(from)) { "copy 的 from 必须是已有课程路径" }
                val source = document.jsonObject.getValue("courses").jsonArray.getOrNull(from.substringAfterLast('/').toInt())
                    ?: throw IllegalArgumentException("copy 引用了不存在的课程")
                document = patchElement(document, parts, "add", source)
                return@forEach
            }
            if (parts.first() == "courses" && parts.size == 2 && op == "add") {
                val course = operation["value"] as? JsonObject ?: throw IllegalArgumentException("新增课程必须是 JSON 对象")
                require(course.keys.all { it in editableCourseFields }) { "新增课程包含未知字段或只读来源信息" }
            }
            document = patchElement(document, parts, op, operation["value"] ?: JsonNull)
        }
        val root = document.jsonObject
        val previousById = draft.courses.associateBy { it.id }
        val rawCourses = root.getValue("courses").jsonArray.map { it.jsonObject }
        val sourceCourses = rawCourses.map { raw -> raw["_courseId"]?.jsonPrimitive?.longOrNull?.let(previousById::get) }
        val timingChanged = rawCourses.zip(sourceCourses).map { (raw, previous) ->
            previous != null && (raw["periods"] != JsonArray(previous.periods.map(::JsonPrimitive)) ||
                raw["customStartTime"] != (previous.customStartTime?.let(::JsonPrimitive) ?: JsonNull) ||
                raw["customEndTime"] != (previous.customEndTime?.let(::JsonPrimitive) ?: JsonNull))
        }
        val normalizedDocument = JsonObject(root + ("courses" to JsonArray(rawCourses.mapIndexed { index, raw ->
            JsonObject(raw.filterKeys { it != "_courseId" &&
                (!timingChanged[index] || it !in setOf("customPeriodTimes", "originalPeriodTimes")) })
        })))
        val parsed = ScheduleImportParser.parseStoredDraft(normalizedDocument.toString(), draft.config).getOrThrow()
        var nextId = lastAssignedCourseId
        return parsed.copy(source = draft.source,
            config = draft.config.copy(totalWeeks = parsed.config.totalWeeks,
                currentWeek = draft.config.currentWeek.coerceIn(1, parsed.config.totalWeeks),
                periodAlignmentMode = parsed.config.periodAlignmentMode),
            periods = parsed.periods.map { it.copy(scheduleId = draft.config.id) },
            courses = parsed.courses.mapIndexed { index, course ->
                val previous = sourceCourses[index]
                val identified = course.copy(id = previous?.id ?: ++nextId, scheduleId = draft.config.id).let { parsedCourse ->
                    if (previous == null) parsedCourse else {
                        fun unchanged(field: String) = rawCourses[index][field] == originalRows[previous.id]?.get(field)
                        parsedCourse.copy(
                            name = if (unchanged("name")) previous.name else parsedCourse.name,
                            teacher = if (unchanged("teacher")) previous.teacher else parsedCourse.teacher,
                            location = if (unchanged("location")) previous.location else parsedCourse.location,
                            note = if (unchanged("note")) previous.note else parsedCourse.note,
                            periods = if (unchanged("periods")) previous.periods else parsedCourse.periods,
                            weeks = if (unchanged("weeks") && unchanged("weekParity")) previous.weeks else parsedCourse.weeks,
                            weekParity = if (unchanged("weeks") && unchanged("weekParity")) previous.weekParity else parsedCourse.weekParity,
                            customPeriodTimes = if (timingChanged[index]) parsedCourse.customPeriodTimes else previous.customPeriodTimes,
                            originalPeriodTimes = if (timingChanged[index]) parsedCourse.originalPeriodTimes else previous.originalPeriodTimes
                        )
                    }
                }
                when {
                    previous != null && rawCourses[index] == originalRows[previous.id] -> previous
                    timingChanged[index] -> captureOriginalPeriodTimes(identified, parsed.periods, previous)
                    else -> identified
                }
            })
    }

    private fun checked(candidate: ImportDraft): ImportDraft {
        ScheduleImportParser.validateEditedDraft(candidate)
        val result = candidate.copy(
            periods = candidate.periods.map { it.copy(scheduleId = candidate.config.id) },
            courses = candidate.courses.map { it.copy(scheduleId = candidate.config.id) })
        if (onlyCurrentWeek) {
            require(result.config == initial.config && result.periods == initial.periods) {
                "本次只允许修改第 $restrictedWeek 周，不能改变全局周数或作息"
            }
            val otherWeeks = (1..initial.config.totalWeeks).filter { it != restrictedWeek }
            fun cells(courses: List<CourseEntity>) = courses.flatMap { course ->
                otherWeeks.filter { it in course.weeks && parityMatches(course.weekParity, it) }.map { week ->
                    course.copy(id = 0, weeks = listOf(week), weekParity = WeekParity.ALL, arrangementProjection = null)
                }
            }.groupingBy { it }.eachCount()
            require(cells(result.courses) == cells(initial.courses)) { "仅本周修改必须完整保留其他周的所有课程和字段" }
        }
        return result
    }

    private fun publish(candidate: ImportDraft, summary: String) {
        val checked = checked(candidate)
        if (checked == draft) return
        // Assign identities to newly split course records without reusing deleted IDs.
        var nextId = maxOf(lastAssignedCourseId, checked.courses.maxOfOrNull { it.id } ?: 0L)
        val seen = hashSetOf<Long>()
        val snapshot = checked.copy(courses = checked.courses.map { course ->
            val id = course.id.takeIf { it > 0 && seen.add(it) } ?: (++nextId).also(seen::add)
            course.copy(id = id, periods = course.periods.toList(), weeks = course.weeks.toList())
        }, periods = checked.periods.toList())
        onCheckpoint(snapshot, summary.take(200))
        draft = snapshot
        revision += 1
        lastAssignedCourseId = nextId
    }

    internal companion object {
        const val TaskStage = """[导入工作区任务]
只使用本轮提供的 READ_IMPORT_JSON 和 PATCH_IMPORT_JSON。先读取完整 JSON 及 revision，再根据用户目标分阶段编辑；每次补丁必须形成完整可用课表。成功后可以继续下一阶段，不能把成功补丁当作任务结束。
operationsJson 是 JSON 数组字符串，每项为 {"op":"replace|add|remove","path":"/courses/0/location","value":"新地点"}。路径相对于 draft；数组索引从 0 开始。revision 必须等于读取或上次成功补丁返回的版本。
可改 scheduleConfig 的 totalWeeks、periodAlignmentMode、periods 及每节 index/startTime/endTime；可改课程 name、teacher、location、weekday、periods、weeks、weekParity、note、customStartTime、customEndTime、customColorArgb。新增课程用 add /courses/-，删除用 remove /courses/索引；只改需要的字段，不整体覆盖课程列表。课程真实起止时间必须同时提供或同时置 null。
拆分周次可用 {"op":"copy","from":"/courses/0","path":"/courses/-"} 复制已有课程，再在同一批补丁里调整原行和新行的 weeks、目标字段；本地会保留真实来源铃声并分配新 ID。
_courseId、customPeriodTimes、originalPeriodTimes 是只读来源数据，不能生成或伪造逐节铃声。未涉及的课程、周次和字段原样保留。仅本周修改需拆分保留其他周；全局作息变化不能冒充仅本周。
本地报错表示整批未应用；依据错误修正，必要时重新读取新 JSON，不要求用户修 JSON。所有阶段只是待确认草稿，不能宣称已保存、写入数据库或修改应用设置。最终用简短正文说明实际完成的阶段和仍缺少的信息，不输出内部协议。"""
        private val editableCourseFields = setOf("name", "teacher", "location", "weekday", "periods", "weeks",
            "weekParity", "note", "customStartTime", "customEndTime", "customColorArgb")

        private fun editablePath(parts: List<String>, op: String): Boolean = when (parts.firstOrNull()) {
            "scheduleConfig" -> parts.size >= 2 && when (parts[1]) {
                "totalWeeks", "periodAlignmentMode" -> parts.size == 2 && op == "replace"
                "periods" -> parts.size == 2 && op == "replace" ||
                    parts.size == 3 && (parts[2].toIntOrNull() != null || parts[2] == "-") ||
                    parts.size == 4 && parts[2].toIntOrNull() != null && parts[3] in setOf("index", "startTime", "endTime") && op == "replace"
                else -> false
            }
            "courses" -> parts.size >= 2 && (parts[1].toIntOrNull() != null || parts[1] == "-") &&
                (parts.size == 2 && op in setOf("add", "remove") ||
                    parts.size == 3 && parts[2] in editableCourseFields ||
                    parts.size == 4 && parts[2] in setOf("periods", "weeks") &&
                        (parts[3].toIntOrNull() != null || parts[3] == "-"))
            else -> false
        }

        private fun patchElement(element: JsonElement, path: List<String>, op: String, value: JsonElement): JsonElement {
            val key = path.first()
            val leaf = path.size == 1
            return when (element) {
                is JsonObject -> {
                    require(op == "add" && leaf || element.containsKey(key)) { "路径不存在：$key" }
                    val result = element.toMutableMap()
                    if (leaf && op == "remove") result.remove(key)
                    else result[key] = if (leaf) value else patchElement(element.getValue(key), path.drop(1), op, value)
                    JsonObject(result)
                }
                is JsonArray -> {
                    val index = if (key == "-" && leaf && op == "add") element.size else
                        key.toIntOrNull() ?: throw IllegalArgumentException("无效数组索引：$key")
                    val result = element.toMutableList()
                    require(index in 0..if (leaf && op == "add") result.size else result.lastIndex) { "数组索引不存在：$key" }
                    when {
                        !leaf -> result[index] = patchElement(result[index], path.drop(1), op, value)
                        op == "add" -> result.add(index, value)
                        op == "remove" -> result.removeAt(index)
                        else -> result[index] = value
                    }
                    JsonArray(result)
                }
                else -> throw IllegalArgumentException("路径经过非容器字段：$key")
            }
        }
    }
}

internal fun agentImportToolDefinitions(strict: Boolean): JsonArray = buildJsonArray {
    listOf(AgentToolName.READ_IMPORT_JSON, AgentToolName.PATCH_IMPORT_JSON).forEach { name ->
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", name.name)
                put("description", if (name == AgentToolName.READ_IMPORT_JSON) "读取当前完整导入 JSON 和版本号，供精确编辑。"
                    else "原子应用 JSON 补丁并本地校验整个草稿。成功后发布可选的完整阶段，继续后续编辑；不会保存到数据库。")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        if (name == AgentToolName.PATCH_IMPORT_JSON) {
                            put("revision", buildJsonObject { put("type", "integer"); put("description", "读取或上次成功编辑返回的 revision") })
                            put("operationsJson", buildJsonObject { put("type", "string"); put("description", "完整 JSON Patch 数组字符串，支持 add/replace/remove，拆分课程可 copy 已有行") })
                            put("summary", buildJsonObject { put("type", "string"); put("description", "该阶段完成的具体改动，简短说明") })
                        }
                    })
                    put("required", buildJsonArray {
                        if (name == AgentToolName.PATCH_IMPORT_JSON) listOf("revision", "operationsJson", "summary").forEach { add(JsonPrimitive(it)) }
                    })
                    put("additionalProperties", false)
                })
                if (strict) put("strict", true)
            })
        })
    }
}
