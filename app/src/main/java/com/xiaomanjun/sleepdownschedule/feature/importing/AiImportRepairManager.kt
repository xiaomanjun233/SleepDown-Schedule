package com.xiaomanjun.sleepdownschedule.feature.importing

import android.util.Log
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal enum class AiImportParseErrorType {
    JSON_PARSE_ERROR,
    SCHEMA_VALIDATION_ERROR
}

internal data class AiImportParseFailure(
    val errorType: AiImportParseErrorType,
    val field: String?,
    val debugMessage: String
)

internal data class AiImportRepairSuccess(
    val draft: ImportDraft,
    val aiResult: AiScheduleImportResult,
    val repairAttempts: Int
)

internal class AiImportRepairException(
    val failure: AiImportParseFailure,
    cause: Throwable? = null
) : IllegalStateException(AiImportRepairManager.UserFacingFormatError, cause)

/** Parses AI schedule output and performs bounded, provider-neutral JSON repair rounds. */
internal object AiImportRepairManager {
    const val MaxRepairAttempts = 3
    const val UserFacingFormatError = "AI生成的数据格式异常，请重试"
    private const val LogTag = "AiImportParser"
    private const val MaxRepairOutputCharacters = 48_000
    private val diagnosticJson = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun parseWithRepair(
        initialResult: AiScheduleImportResult,
        scheduleConfig: ScheduleConfigEntity,
        onRepairAttempt: (attempt: Int, failure: AiImportParseFailure) -> Unit = { _, _ -> },
        requestRepair: suspend (
            output: String,
            failure: AiImportParseFailure,
            attempt: Int
        ) -> Result<AiScheduleImportResult>
    ): Result<AiImportRepairSuccess> {
        var currentResult = initialResult
        var currentOutput = currentResult.output.ifBlank { currentResult.rawOutput }
        var parseResult = ScheduleImportParser.parse(currentOutput, scheduleConfig)
        parseResult.getOrNull()?.let { draft ->
            logSuccess(repairAttempt = 0)
            return Result.success(AiImportRepairSuccess(draft, currentResult, repairAttempts = 0))
        }
        var failure = classifyFailure(currentOutput, checkNotNull(parseResult.exceptionOrNull()))

        for (attempt in 1..MaxRepairAttempts) {
            logFailure(failure, repairAttempt = attempt, maxAttemptsReached = false)
            onRepairAttempt(attempt, failure)
            currentResult = requestRepair(currentOutput, failure, attempt).getOrElse { requestError ->
                runCatching {
                    Log.w(
                        LogTag,
                        "repair request failed: errorType=${failure.errorType} field=${failure.field ?: "unknown"} repairAttempt=$attempt",
                        requestError
                    )
                }
                return Result.failure(AiImportRepairException(failure, requestError))
            }
            currentOutput = currentResult.output.ifBlank { currentResult.rawOutput }
            parseResult = ScheduleImportParser.parse(currentOutput, scheduleConfig)
            parseResult.getOrNull()?.let { draft ->
                logSuccess(repairAttempt = attempt)
                return Result.success(AiImportRepairSuccess(draft, currentResult, repairAttempts = attempt))
            }
            failure = classifyFailure(currentOutput, checkNotNull(parseResult.exceptionOrNull()))
        }

        logFailure(failure, repairAttempt = MaxRepairAttempts, maxAttemptsReached = true)
        return Result.failure(AiImportRepairException(failure, parseResult.exceptionOrNull()))
    }

    fun buildRepairPrompt(output: String, failure: AiImportParseFailure): String {
        val cleaned = ScheduleImportParser.cleanMarkdown(output).trim()
        val boundedOutput = cleaned.take(MaxRepairOutputCharacters)
        return buildString {
            appendLine("你刚才生成的课程表数据无法通过 SleepDown 本地校验。")
            appendLine("错误类型：${failure.errorType}")
            appendLine("错误字段：${failure.field ?: "unknown"}")
            appendLine("错误摘要：${failure.debugMessage.take(300)}")
            appendLine("原始 AI 输出摘要：${outputSummary(cleaned)}")
            appendLine()
            appendLine("请仅修正数据格式，不改变课程名称、教师、地点、星期、节次、周次或时间信息。")
            appendLine("当前 schema：根对象需要 schemaVersion=1、scheduleConfig、courses、changeSummary；scheduleConfig 需要 totalWeeks(1..60) 与 periods(index,startTime,endTime)；courses 每项需要 name、teacher、location、weekday、periods、weeks、weekParity、note；periods/weeks 不得为空，weekday=1..7，weekParity=ALL|ODD|EVEN，时间=HH:mm。")
            appendLine("只调用 IMPORT_SCHEDULE 返回符合 schema 的 JSON，不要解释。")
            appendLine()
            appendLine("待修复的上轮 AI 输出：")
            append(boundedOutput)
        }
    }

    internal fun classifyFailure(output: String, error: Throwable): AiImportParseFailure {
        val cleaned = ScheduleImportParser.cleanMarkdown(output)
        val parsedElement = runCatching { Json.parseToJsonElement(cleaned) }.getOrNull()
        val type = if (parsedElement == null) {
            AiImportParseErrorType.JSON_PARSE_ERROR
        } else {
            AiImportParseErrorType.SCHEMA_VALIDATION_ERROR
        }
        val messages = generateSequence(error) { it.cause }
            .mapNotNull(Throwable::message)
            .toList()
        return AiImportParseFailure(
            errorType = type,
            field = parsedElement?.let(::findMissingRequiredField)
                ?: extractFieldFromMessages(messages),
            debugMessage = messages.firstOrNull().orEmpty().ifBlank { type.name }
        )
    }

    private fun findMissingRequiredField(element: JsonElement): String? {
        val root = element as? JsonObject ?: return "\$"
        listOf("schemaVersion", "scheduleConfig", "courses").firstOrNull { it !in root }
            ?.let { return it }
        val config = root["scheduleConfig"] as? JsonObject ?: return "scheduleConfig"
        listOf("totalWeeks", "periods").firstOrNull { it !in config }
            ?.let { return "scheduleConfig.$it" }
        val courses = root["courses"] as? JsonArray ?: return "courses"
        courses.forEachIndexed { index, elementCourse ->
            val course = elementCourse as? JsonObject ?: return "courses[$index]"
            listOf("name", "weekday", "periods", "weeks").firstOrNull { it !in course }
                ?.let { return "courses[$index].$it" }
        }
        return null
    }

    private fun extractFieldFromMessages(messages: List<String>): String? {
        val message = messages.joinToString("；")
        val serializationPath = Regex("(?:at|path[:=]?)\\s+(\\$[^\\s,;]+)", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimEnd('.', ':')
            ?.removePrefix("\$.")
        val missingField = Regex("Field ['\"]([^'\"]+)['\"] is required", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
        if (serializationPath != null && missingField != null && !serializationPath.endsWith(missingField)) {
            return "$serializationPath.$missingField"
        }
        if (serializationPath != null) return serializationPath
        if (missingField != null) return missingField

        val courseMatch = Regex("第\\s*(\\d+)\\s*门课程.*?(name|weekday|periods|weeks|customStartTime|customEndTime|customColorArgb)")
            .find(message)
        if (courseMatch != null) {
            val index = courseMatch.groupValues[1].toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
            return "courses[$index].${courseMatch.groupValues[2]}"
        }
        return listOf(
            "schemaVersion",
            "scheduleConfig.totalWeeks",
            "scheduleConfig.periods",
            "courses"
        ).firstOrNull { field -> message.contains(field.substringAfterLast('.'), ignoreCase = true) }
    }

    private fun outputSummary(output: String): String {
        val element = runCatching { diagnosticJson.parseToJsonElement(output) }.getOrNull()
        val root = element as? JsonObject
        val courseCount = (root?.get("courses") as? JsonArray)?.size
        return buildString {
            append("length=${output.length}")
            append(", jsonObject=${root != null}")
            if (courseCount != null) append(", courses=$courseCount")
            if (output.length > MaxRepairOutputCharacters) append(", repairInputTruncated=true")
        }
    }

    private fun logSuccess(repairAttempt: Int) {
        runCatching { Log.i(LogTag, "parse success repairAttempt=$repairAttempt") }
    }

    private fun logFailure(
        failure: AiImportParseFailure,
        repairAttempt: Int,
        maxAttemptsReached: Boolean
    ) {
        runCatching {
            Log.w(
                LogTag,
                "parse failed: errorType=${failure.errorType} field=${failure.field ?: "unknown"}" +
                    " repairAttempt=$repairAttempt maxAttemptsReached=$maxAttemptsReached"
            )
        }
    }
}
