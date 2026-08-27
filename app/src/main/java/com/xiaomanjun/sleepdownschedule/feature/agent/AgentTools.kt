package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

/**
 * Provider-independent read-tool protocol.
 *
 * Providers with native function calling can map their calls to these objects later. Text-only
 * providers receive the same deterministic results as compact system messages, so the rest of the
 * Agent never depends on one vendor's wire format.
 */
enum class AgentToolName {
    GET_CURRENT_OVERVIEW,
    SEARCH_COURSES,
    GET_WEEK_SCHEDULE,
    GET_SEMESTER_SCHEDULE,
    GET_PERIODS,
    GET_SETTINGS,
    UPDATE_MEMORY
}

data class AgentToolCall(
    val id: String,
    val name: AgentToolName,
    val arguments: Map<String, String> = emptyMap()
)

data class AgentToolResult(
    val callId: String,
    val name: AgentToolName,
    val success: Boolean,
    val content: String
)

@Serializable
data class AgentRunStatus(
    val icon: AgentRunStatusIcon,
    val text: String,
    val detail: String? = null
)

@Serializable
enum class AgentRunStatusIcon {
    OVERVIEW,
    SEARCH,
    SCHEDULE,
    PERIOD,
    SETTINGS,
    THINKING
}

internal fun AgentToolName.runStatus(): AgentRunStatus = when (this) {
    AgentToolName.GET_CURRENT_OVERVIEW ->
        AgentRunStatus(AgentRunStatusIcon.OVERVIEW, "读取当前日程")
    AgentToolName.SEARCH_COURSES ->
        AgentRunStatus(AgentRunStatusIcon.SEARCH, "查找课程")
    AgentToolName.GET_WEEK_SCHEDULE ->
        AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "读取本周课表")
    AgentToolName.GET_SEMESTER_SCHEDULE ->
        AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "读取学期课表")
    AgentToolName.GET_PERIODS ->
        AgentRunStatus(AgentRunStatusIcon.PERIOD, "读取节次时间")
    AgentToolName.GET_SETTINGS ->
        AgentRunStatus(AgentRunStatusIcon.SETTINGS, "读取应用设置")
    AgentToolName.UPDATE_MEMORY ->
        AgentRunStatus(AgentRunStatusIcon.SETTINGS, "更新助手记忆")
}

/** Immutable snapshot reads only need to be exposed once per user turn. */
internal val AgentToolName.isOneShotPerTurn: Boolean
    get() = this != AgentToolName.SEARCH_COURSES

internal fun AgentToolCall.cacheKey(): String = buildString {
    append(name.name)
    arguments.toSortedMap().forEach { (key, value) ->
        append('\u0000').append(key).append('=').append(value.trim())
    }
}

/**
 * OpenAI-compatible function declarations. The model, not Kotlin keyword rules, chooses which
 * tools to call. Local code only validates the selected name/arguments and executes it against the
 * active schedule-scoped fact snapshot.
 */
internal fun agentToolDefinitions(
    includeMiMoWebSearch: Boolean = false,
    forceMiMoWebSearch: Boolean = false,
    includeMemoryTool: Boolean = false,
    strictFunctions: Boolean = false,
    excludedTools: Set<AgentToolName> = emptySet()
): JsonArray = buildJsonArray {
    if (AgentToolName.GET_CURRENT_OVERVIEW !in excludedTools) add(agentToolDefinition(
        AgentToolName.GET_CURRENT_OVERVIEW,
        "当前日期、时间、学期状态、有效教学周、今天/明天课程摘要和天气。回答当前状态时读取。",
        strict = strictFunctions
    ))
    if (AgentToolName.SEARCH_COURSES !in excludedTools) add(agentToolDefinition(
        AgentToolName.SEARCH_COURSES,
        "按课程名、教师或地点查找当前课表记录；修改明确对象前读取，可用不同关键词重复检索。",
        queryRequired = true,
        strict = strictFunctions
    ))
    if (AgentToolName.GET_WEEK_SCHEDULE !in excludedTools) add(agentToolDefinition(
        AgentToolName.GET_WEEK_SCHEDULE,
        "当前教学周完整日程，用于空档、冲突和本周安排。",
        strict = strictFunctions
    ))
    if (AgentToolName.GET_SEMESTER_SCHEDULE !in excludedTools) add(agentToolDefinition(
        AgentToolName.GET_SEMESTER_SCHEDULE,
        "当前课表的全部课程记录，用于课程总览、批量或跨周修改。",
        strict = strictFunctions
    ))
    if (AgentToolName.GET_PERIODS !in excludedTools) add(agentToolDefinition(
        AgentToolName.GET_PERIODS,
        "当前节次拓扑、准确时间和全部作息方案；调整节次或时间前读取。",
        strict = strictFunctions
    ))
    if (AgentToolName.GET_SETTINGS !in excludedTools) add(agentToolDefinition(
        AgentToolName.GET_SETTINGS,
        "可访问设置的键、类型、范围和当前值；回答或修改设置前读取。",
        strict = strictFunctions
    ))
    if (includeMemoryTool && AgentToolName.UPDATE_MEMORY !in excludedTools) {
        add(agentMemoryToolDefinition(strictFunctions))
    }
    /*
     * Only fact acquisition is exposed as a model tool. Write plans deliberately remain the
     * generic <agent_actions> JSON protocol in the final answer: turning every write primitive
     * into a function tool makes the schema look like a capability allow-list and causes models
     * to refuse perfectly representable composite tasks.
     */
    if (includeMiMoWebSearch) {
        /*
         * This is MiMo's server-side tool, not a locally executed function. With force_search
         * disabled and tool_choice=auto in the request, MiMo decides whether fresh public web
         * information is needed and returns the summarized answer itself.
         */
        add(buildJsonObject {
            put("type", "web_search")
            put("max_keyword", 3)
            put("force_search", forceMiMoWebSearch)
            put("limit", 1)
        })
    }
}

private val AgentRunTraceJson = Json { ignoreUnknownKeys = true }
private val AgentRunTraceMarker = Regex(
    "<agent_run_trace>([\\s\\S]*?)</agent_run_trace>",
    RegexOption.IGNORE_CASE
)

internal data class AgentStoredMessage(
    val content: String,
    val statuses: List<AgentRunStatus>
)

/**
 * Execution summaries are persisted with the visible answer so a completed turn remains
 * inspectable after the in-memory runner is released. They are application metadata, not model
 * reasoning, and are removed again before conversation history is sent to a provider.
 */
internal fun agentMessageWithRunTrace(
    content: String,
    statuses: List<AgentRunStatus>
): String {
    val usefulStatuses = statuses.fold(mutableListOf<AgentRunStatus>()) { result, status ->
        if (result.lastOrNull() != status) result += status
        result
    }
    if (usefulStatuses.isEmpty()) return content
    return "<agent_run_trace>${AgentRunTraceJson.encodeToString(usefulStatuses)}</agent_run_trace>\n$content"
}

internal fun parseAgentStoredMessage(content: String): AgentStoredMessage {
    val marker = AgentRunTraceMarker.find(content)
    val statuses = marker?.groupValues?.getOrNull(1)?.let { json ->
        runCatching { AgentRunTraceJson.decodeFromString<List<AgentRunStatus>>(json) }
            .getOrDefault(emptyList())
    }.orEmpty()
    return AgentStoredMessage(
        content = content.replace(AgentRunTraceMarker, "").trimStart(),
        statuses = statuses
    )
}

/**
 * Responses API function tools use a flattened declaration while Chat Completions nests the
 * same fields under `function`. Keep one schema source and adapt only the wire shape.
 */
internal fun agentResponsesToolDefinitions(
    includeMemoryTool: Boolean = false,
    excludedTools: Set<AgentToolName> = emptySet()
): JsonArray = buildJsonArray {
    agentToolDefinitions(
        includeMemoryTool = includeMemoryTool,
        strictFunctions = true,
        excludedTools = excludedTools
    ).forEach { declaration ->
        val function = declaration.jsonObject["function"]?.jsonObject ?: return@forEach
        add(buildJsonObject {
            put("type", "function")
            function["name"]?.let { put("name", it) }
            function["description"]?.let { put("description", it) }
            function["parameters"]?.let { put("parameters", it) }
            function["strict"]?.let { put("strict", it) }
        })
    }
}

private fun agentMemoryToolDefinition(strict: Boolean) = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", AgentToolName.UPDATE_MEMORY.name)
        put(
            "description",
            "完整替换用户已授权保存的简短长期记忆。仅保存跨天仍有价值的稳定偏好或背景；" +
                "不得保存临时任务、当天安排、聊天复述或敏感凭据。没有值得更新的内容时不要调用。"
        )
        put("parameters", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("memory", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "完整的新记忆文本，不是增量。用简短条目表达；明确忘记全部内容时传空字符串。"
                    )
                    put("maxLength", 1200)
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive("memory")) })
            put("additionalProperties", false)
        })
        if (strict) put("strict", true)
    })
}

/**
 * MiMo's web_search extension is intentionally sent only to the documented official endpoint and
 * supported model names. Custom OpenAI-compatible services frequently reject unknown tool types.
 */
internal fun supportsMiMoOfficialWebSearch(
    providerId: String,
    baseUrl: String,
    model: String
): Boolean {
    @Suppress("UNUSED_VARIABLE")
    val configuredProviderId = providerId
    val host = runCatching { URI(baseUrl.trim()).host.orEmpty().lowercase() }.getOrDefault("")
    // Xiaomi currently documents the server-side web_search plugin only on the pay-as-you-go
    // Chat Completions endpoint. Token Plan is deliberately not treated as equivalent here.
    if (host != "api.xiaomimimo.com") return false
    return model.trim().lowercase() in setOf("mimo-v2.5-pro", "mimo-v2.5")
}

private fun agentToolDefinition(
    name: AgentToolName,
    description: String,
    queryRequired: Boolean = false,
    strict: Boolean = false
) = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", name.name)
        put("description", description)
        put("parameters", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                if (queryRequired) {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "课程名、教师、地点或用户描述中的检索词")
                    })
                }
            })
            put("required", buildJsonArray {
                if (queryRequired) add(JsonPrimitive("query"))
            })
            put("additionalProperties", false)
        })
        if (strict) put("strict", true)
    })
}

internal fun executeAgentReadTools(
    calls: List<AgentToolCall>,
    facts: DayAgentFacts
): List<AgentToolResult> {
    /*
     * Every tool must read the same schedule-scoped fact snapshot. Upstream UI transitions may
     * briefly retain slots from the previous pager, so enforce the boundary here rather than
     * trusting any caller. This also makes tool results mutually consistent by construction.
     */
    val scopedSemesterCourses = (
        facts.semesterCourses.asSequence() +
            facts.week.asSequence().map { it.course } +
            facts.today.asSequence().map { it.course } +
            facts.tomorrow.asSequence().map { it.course }
        )
        .filter { it.scheduleId == facts.scheduleId }
        .distinctBy { it.id }
        .toList()
    val courseIds = scopedSemesterCourses.mapTo(hashSetOf()) { it.id }
    val scopedFacts = facts.copy(
        today = facts.today.filter {
            it.course.scheduleId == facts.scheduleId && it.course.id in courseIds
        },
        tomorrow = facts.tomorrow.filter {
            it.course.scheduleId == facts.scheduleId && it.course.id in courseIds
        },
        week = facts.week.filter {
            it.course.scheduleId == facts.scheduleId && it.course.id in courseIds
        },
        periodDefinitions = facts.periodDefinitions.filter { it.scheduleId == facts.scheduleId },
        semesterCourses = scopedSemesterCourses
    )
    return calls.map { call ->
        AgentToolResult(
            callId = call.id,
            name = call.name,
            success = true,
            content = "读取成功；当前课表ID=${scopedFacts.scheduleId}；事实版本=${scopedFacts.sourceHash}\n" +
                when (call.name) {
                    AgentToolName.GET_CURRENT_OVERVIEW -> agentOverviewResult(scopedFacts)
                    AgentToolName.SEARCH_COURSES ->
                        agentCourseSearchResult(call.arguments["query"].orEmpty(), scopedFacts)
                    AgentToolName.GET_WEEK_SCHEDULE -> agentWeekResult(scopedFacts)
                    AgentToolName.GET_SEMESTER_SCHEDULE -> agentSemesterResult(scopedFacts)
                    AgentToolName.GET_PERIODS -> agentPeriodResult(scopedFacts)
                    AgentToolName.GET_SETTINGS ->
                        AgentSettingRegistry.promptCatalog(
                            periods = scopedFacts.periodDefinitions,
                            currentValues = scopedFacts.settingSnapshot
                        )
                    AgentToolName.UPDATE_MEMORY ->
                        "记忆更新只能由助手会话层处理"
                }
        )
    }
}

private val CompleteAgentToolResult = Regex(
    "<\\s*tool_(?:result|call)\\b[^>]*>[\\s\\S]*?<\\s*/\\s*tool_(?:result|call)\\s*>",
    RegexOption.IGNORE_CASE
)

private val IncompleteAgentToolResult = Regex(
    "<\\s*tool_(?:result|call)\\b[^>]*(?:>|$)[\\s\\S]*$",
    RegexOption.IGNORE_CASE
)

private val LeakedAgentFunctionProtocol = Regex(
    "(?:<\\s*[|｜]\\s*DSML\\s*[|｜]\\s*(?:tool_calls?|invoke|parameter)\\b|" +
        "<\\s*(?:tool_calls|function_call|invoke|parameter)\\b|" +
        "[\"'](?:tool_calls|function_call)[\"']\\s*:)",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

internal fun containsLeakedAgentFunctionProtocol(content: String): Boolean =
    LeakedAgentFunctionProtocol.containsMatchIn(content)

internal class AgentProtocolViolationException :
    IllegalStateException("模型输出了无效的内部工具协议")

/**
 * Streams normal answer text with a short look-behind window while keeping provider-internal
 * function syntax out of the UI. A protocol failure can therefore be retried without briefly
 * flashing DSML or persisting it as an assistant message.
 */
internal class AgentFinalOutputGate(
    private val onDelta: (String) -> Unit,
    private val holdBackCharacters: Int = 64
) {
    private val content = StringBuilder()
    private var forwardedCharacters = 0

    fun accept(delta: String) {
        if (delta.isEmpty()) return
        val scanStart = (content.length - holdBackCharacters).coerceAtLeast(0)
        content.append(delta)
        if (containsLeakedAgentFunctionProtocol(content.substring(scanStart))) {
            throw AgentProtocolViolationException()
        }
        forwardUntil((content.length - holdBackCharacters).coerceAtLeast(0))
    }

    fun finish(answer: String): String {
        if (answer.isBlank()) throw MissingAgentBodyException()
        if (containsLeakedAgentFunctionProtocol(answer)) throw AgentProtocolViolationException()
        if (content.toString() != answer) {
            throw IllegalStateException("AI 流式响应内容不完整，请重试")
        }
        forwardUntil(content.length)
        return answer
    }

    private fun forwardUntil(endExclusive: Int) {
        if (endExclusive <= forwardedCharacters) return
        onDelta(content.substring(forwardedCharacters, endExclusive))
        forwardedCharacters = endExclusive
    }
}

/**
 * Tool payloads are an internal transport detail. Never persist or render them as assistant text.
 *
 * The incomplete form matters for streaming: from the first opening tag until its closing tag
 * arrives, the partial payload stays invisible. Once the close tag arrives, any natural-language
 * answer after it becomes visible immediately.
 */
internal fun sanitizeAgentToolOutput(content: String): String {
    if (containsLeakedAgentFunctionProtocol(content)) return ""
    val completeMatches = CompleteAgentToolResult.findAll(content).toList()
    if (completeMatches.isNotEmpty()) {
        return content
            .substring(completeMatches.last().range.last + 1)
            .replace(IncompleteAgentToolResult, "")
            .trim()
    }
    if (IncompleteAgentToolResult.containsMatchIn(content)) return ""
    return content.trim()
}

/**
 * Text emitted before a tool payload is orchestration narration ("让我查一下……"), not the
 * assistant's final answer. It may be shown as a subdued transient reasoning/status line while
 * streaming, but it must never be merged into the final body.
 */
internal fun extractAgentToolPrelude(content: String): String {
    val completeMatches = CompleteAgentToolResult.findAll(content).toList()
    if (completeMatches.isNotEmpty()) {
        return content
            .substring(0, completeMatches.last().range.last + 1)
            .replace(CompleteAgentToolResult, "")
            .trim()
    }
    val incomplete = IncompleteAgentToolResult.find(content) ?: return ""
    return content.substring(0, incomplete.range.first).trim()
}

private fun agentOverviewResult(facts: DayAgentFacts): String = buildString {
    val weekday = weekdayLabel(facts.date.dayOfWeek.toChineseWeekday())
    appendLine("日期=${facts.date} 星期$weekday；当前时间=${facts.now.toLocalTime()}")
    val teachingWeek = if (facts.termState in setOf(ScheduleTermState.MANUAL, ScheduleTermState.ACTIVE)) {
        facts.currentWeek.toString()
    } else {
        "无"
    }
    appendLine("课表ID=${facts.scheduleId}；学期状态=${facts.termState.name}（${facts.termStatus}）；" +
        "当前有效教学周=$teachingWeek；总周数=${facts.totalWeeks}")
    appendLine("天气=${facts.weather?.summary ?: "不可用"}")
    appendLine(
        "今日=" + facts.today.joinToString("；") {
            "${it.start}-${it.end} ${it.course.name} @${it.course.location ?: "待确认"}"
        }.ifBlank { "无课" }
    )
    appendLine(
        "明日=" + facts.tomorrow.joinToString("；") {
            "${it.start}-${it.end} ${it.course.name} @${it.course.location ?: "待确认"}"
        }.ifBlank { "无课" }
    )
}

private fun agentCourseSearchResult(query: String, facts: DayAgentFacts): String {
    val unique = facts.semesterCourses.distinctBy { it.id }
    val needle = query.trim()
    /*
     * Match in both directions: the model may pass either a fragment of the course name
     * ("数学" → "高等数学") or a whole user sentence that embeds the full name. One-directional
     * query.contains(name) silently failed the first, far more common case.
     */
    fun fieldMatches(field: String?): Boolean {
        val value = field?.takeIf(String::isNotBlank) ?: return false
        return value.contains(needle, ignoreCase = true) ||
            needle.contains(value, ignoreCase = true)
    }
    val matched = unique.filter { course ->
        needle.isNotBlank() &&
            (fieldMatches(course.name) || fieldMatches(course.teacher) || fieldMatches(course.location))
    }.take(24)
    return matched.joinToString("\n", transform = ::agentCourseLine)
        .ifBlank { "没有匹配课程" }
}

private fun agentWeekResult(facts: DayAgentFacts): String = buildString {
    appendLine("学期状态=${facts.termState.name}（${facts.termStatus}）")
    if (facts.termState !in setOf(ScheduleTermState.MANUAL, ScheduleTermState.ACTIVE)) {
        append("当前没有有效教学周")
    } else {
        append(
            facts.week.joinToString("\n") { item ->
                "${item.date} ${item.start}-${item.end} ${agentCourseLine(item.course)}"
            }.ifBlank { "本周无课" }
        )
    }
}

private fun agentSemesterResult(facts: DayAgentFacts): String = buildString {
    appendLine("学期状态=${facts.termState.name}（${facts.termStatus}）")
    append(
        facts.semesterCourses.distinctBy { it.id }
            .joinToString("\n", transform = ::agentCourseLine)
            .ifBlank { "本学期无课程" }
    )
}

private fun agentPeriodResult(facts: DayAgentFacts): String =
    buildString {
        appendLine(
            "节次拓扑：上午=${facts.settingSnapshot["MORNING_PERIOD_COUNT"] ?: "UNKNOWN"}；" +
                "中午=${facts.settingSnapshot["NOON_PERIOD_COUNT"] ?: "UNKNOWN"}；" +
                "下午=${facts.settingSnapshot["AFTERNOON_PERIOD_COUNT"] ?: "UNKNOWN"}；" +
                "晚上=${facts.settingSnapshot["EVENING_PERIOD_COUNT"] ?: "UNKNOWN"}"
        )
        appendLine("当前生效的物化节次：")
        appendLine(
            facts.periodDefinitions.sortedBy { it.periodIndex }
                .joinToString("；") { "第${it.periodIndex}节 ${it.startTime}-${it.endTime}" }
                .ifBlank { "没有节次定义" }
        )
        if (facts.periodSchemes.isEmpty()) {
            appendLine("作息方案：数据库暂未返回方案")
        } else {
            appendLine("全部作息方案：")
            facts.periodSchemes.forEach { scheme ->
                appendLine(
                    "- ID=${scheme.id}；名称=${scheme.name}；当前=${scheme.isActive}；" +
                        "模式=${scheme.mode}；单节=${scheme.classDurationMinutes}分钟；" +
                        "普通课间=${scheme.breakDurationMinutes}分钟；" +
                        "时段起点=上午${scheme.morningStartTime}/中午${scheme.noonStartTime}/" +
                        "下午${scheme.afternoonStartTime}/晚上${scheme.eveningStartTime}；" +
                        "特殊课间=${scheme.specialBreaks.ifEmpty { mapOf<Int, Int>() }}；" +
                        "手动覆盖=${scheme.overriddenPeriods.sorted()}"
                )
                appendLine(
                    "  时间线=" + scheme.times.joinToString("；") {
                        "第${it.periodIndex}节 ${it.startTime}-${it.endTime}"
                    }
                )
            }
        }
    }.trim()

private fun agentCourseLine(course: CourseEntity): String =
    "ID=${course.id} ${course.name}；星期=${course.weekday}；节次=${course.periods.joinToString(",")}" +
        "；周次=${course.weeks.joinToString(",")}；单双周=${course.weekParity}" +
        "；地点=${course.location ?: "待确认"}；教师=${course.teacher ?: "待确认"}" +
        course.customTimeRangeOrNull()?.let { (start, end) ->
            "；自定义时间=${start}-${end}（优先于节次默认时间）"
        }.orEmpty()
