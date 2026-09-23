package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


fun aiSchedulePrompt(): String = """
识别输入中的真实课表，并调用 IMPORT_SCHEDULE 工具提交结果。
以可见的表头、星期、节次、课程块和周次为准；不要重复长图接缝处的课程。
不确定的信息保留在 note 中，不要猜测。每门课的 periods 和 weeks 均不能为空。
如果原始课表给出了课程自己的真实起止时间，填写 customStartTime 和 customEndTime（HH:mm）。同节次在不同教学区域可能有不同时间和课间；按各自课程原文填写整体起止时间，不得按统一课间推算。节次作息只是默认值，不能覆盖课程真实时间。逐节铃声只能由真实教务适配器提供，不要自行生成。
changeSummary 必须用简洁中文说明本次识别或修改了哪些课程字段，不能只写“已完成”。
""".trimIndent()

internal const val ScheduleImportToolName = "IMPORT_SCHEDULE"
internal const val SchedulePatchToolName = "PATCH_SCHEDULE"
internal const val ReadOriginalSourceToolName = "READ_ORIGINAL_IMPORT_SOURCE"

private fun scheduleJsonSchemaFormat(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("json_schema"))
    put("name", JsonPrimitive("sleepdown_schedule_import"))
    put("strict", JsonPrimitive(true))
    put("schema", scheduleJsonSchemaBody())
}

private fun scheduleJsonSchema(): JsonObject = buildJsonObject {
    put("name", JsonPrimitive("sleepdown_schedule_import"))
    put("strict", JsonPrimitive(true))
    put("schema", scheduleJsonSchemaBody())
}

private fun scheduleJsonSchemaBody(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf(JsonPrimitive("schemaVersion"), JsonPrimitive("scheduleConfig"), JsonPrimitive("courses"), JsonPrimitive("changeSummary"))))
    put("properties", buildJsonObject {
        put("schemaVersion", buildJsonObject {
            put("type", JsonPrimitive("integer"))
            put("const", JsonPrimitive(1))
        })
        put("scheduleConfig", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("required", JsonArray(listOf(JsonPrimitive("totalWeeks"), JsonPrimitive("periods"))))
            put("properties", buildJsonObject {
                put("totalWeeks", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("minimum", JsonPrimitive(1))
                    put("maximum", JsonPrimitive(60))
                })
                put("periods", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("additionalProperties", JsonPrimitive(false))
                        put("required", JsonArray(listOf(JsonPrimitive("index"), JsonPrimitive("startTime"), JsonPrimitive("endTime"))))
                        put("properties", buildJsonObject {
                            put("index", buildJsonObject { put("type", JsonPrimitive("integer")) })
                            put("startTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("endTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                        })
                    })
                })
            })
        })
        put("courses", buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "required",
                    JsonArray(
                        listOf(
                            "name",
                            "teacher",
                            "location",
                            "weekday",
                            "periods",
                            "weeks",
                            "weekParity",
                            "note",
                            "customStartTime",
                            "customEndTime"
                        ).map(::JsonPrimitive)
                    )
                )
                put("properties", buildJsonObject {
                    put("name", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("teacher", nullableStringSchema())
                    put("location", nullableStringSchema())
                    put("weekday", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("minimum", JsonPrimitive(1))
                        put("maximum", JsonPrimitive(7))
                    })
                    put("periods", integerArraySchema())
                    put("weeks", integerArraySchema())
                    put("weekParity", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", JsonArray(listOf("ALL", "ODD", "EVEN").map(::JsonPrimitive)))
                    })
                    put("note", nullableStringSchema())
                    put("customStartTime", nullableStringSchema())
                    put("customEndTime", nullableStringSchema())
                })
            })
        })
        put("changeSummary", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("本轮实际变更摘要。列出课程名称及新增、删除或改动的字段；禁止只写已完成。"))
        })
    })
}

private fun nullableStringSchema(): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
}

private fun integerArraySchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("array"))
    put("items", buildJsonObject { put("type", JsonPrimitive("integer")) })
}

internal fun scheduleImportChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(ScheduleImportToolName))
        put("description", JsonPrimitive("提交识别完成并可由 SleepDown 本地校验的课程表"))
        put("strict", JsonPrimitive(true))
        put("parameters", scheduleJsonSchemaBody())
    })
}

internal fun readOriginalSourceChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(ReadOriginalSourceToolName))
        put("description", JsonPrimitive("仅在需要复核、重新识别或核对原网页/附件时读取原始导入材料；普通字段修改不要调用"))
        put("strict", JsonPrimitive(true))
        put("parameters", emptyObjectSchema())
    })
}

internal fun scheduleImportResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(ScheduleImportToolName))
    put("description", JsonPrimitive("提交识别完成并可由 SleepDown 本地校验的课程表"))
    put("strict", JsonPrimitive(true))
    put("parameters", scheduleJsonSchemaBody())
}

/** A compact, provider-neutral edit protocol for existing imported schedules. */
private fun schedulePatchSchemaBody(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf(JsonPrimitive("changeSummary"), JsonPrimitive("operations"))))
    put("properties", buildJsonObject {
        put("changeSummary", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("逐项说明实际更改的课程和字段，禁止只写已完成。"))
        })
        put("operations", buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("maxItems", JsonPrimitive(16))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("additionalProperties", JsonPrimitive(false))
                put("required", JsonArray(listOf(
                    JsonPrimitive("type"), JsonPrimitive("index"), JsonPrimitive("course"),
                    JsonPrimitive("periods"), JsonPrimitive("totalWeeks")
                )))
                put("properties", buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", JsonArray(listOf("replace_course", "add_course", "remove_course", "replace_periods", "set_total_weeks").map(::JsonPrimitive)))
                    })
                    put("index", buildJsonObject { put("type", JsonPrimitive("integer")); put("minimum", JsonPrimitive(0)) })
                    put("course", nullableRevisionCourseSchema())
                    put("periods", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object")); put("additionalProperties", JsonPrimitive(false))
                            put("required", JsonArray(listOf(JsonPrimitive("index"), JsonPrimitive("startTime"), JsonPrimitive("endTime"))))
                            put("properties", buildJsonObject {
                                put("index", buildJsonObject { put("type", JsonPrimitive("integer")) })
                                put("startTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                                put("endTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                            })
                        })
                    })
                    put("totalWeeks", buildJsonObject { put("type", JsonPrimitive("integer")); put("minimum", JsonPrimitive(0)); put("maximum", JsonPrimitive(60)) })
                })
            })
        })
    })
}

private fun nullableRevisionCourseSchema(): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("object"), JsonPrimitive("null"))))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf("name", "teacher", "location", "weekday", "periods", "weeks", "weekParity", "note", "customStartTime", "customEndTime").map(::JsonPrimitive)))
    put("properties", scheduleJsonSchemaBody().jsonObject["properties"]!!.jsonObject["courses"]!!.jsonObject["items"]!!.jsonObject["properties"]!!)
}

internal fun schedulePatchChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(SchedulePatchToolName))
        put("description", JsonPrimitive("对已有课表应用局部操作；只提交需要改变的课程或配置，不要回传整份课表。"))
        put("strict", JsonPrimitive(true))
        put("parameters", schedulePatchSchemaBody())
    })
}

internal fun schedulePatchResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(SchedulePatchToolName))
    put("description", JsonPrimitive("对已有课表应用局部操作；只提交需要改变的课程或配置，不要回传整份课表。"))
    put("strict", JsonPrimitive(true))
    put("parameters", schedulePatchSchemaBody())
}

internal fun readOriginalSourceResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(ReadOriginalSourceToolName))
    put("description", JsonPrimitive("仅在需要复核、重新识别或核对原网页/附件时读取原始导入材料；普通字段修改不要调用"))
    put("strict", JsonPrimitive(true))
    put("parameters", emptyObjectSchema())
}

private fun emptyObjectSchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("properties", buildJsonObject {})
    put("required", JsonArray(emptyList()))
}

internal fun parseScheduleToolResult(response: String): AiProviderTextResult? {
    val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
    val chatCall = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject
        ?.optionalArray("tool_calls")?.firstOrNull()?.jsonObject
        ?.get("function")?.jsonObject
    val responseCall = root.optionalArray("output")
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
    val call = chatCall ?: responseCall ?: return null
    if (call["name"]?.jsonPrimitive?.contentOrNull != ScheduleImportToolName) return null
    val arguments = call["arguments"]?.let { value ->
        if (value is JsonPrimitive) value.contentOrNull else value.toString()
    }?.trim().orEmpty()
    if (arguments.isBlank()) return null
    val modelReasoning = runCatching {
        if (root.containsKey("choices")) {
            parseChatCompletionTextResult(response, requireContent = false).reasoning
        } else {
            collectResponsesReasoning(root).joinToString("\n\n")
        }
    }.getOrDefault("")
    val changeSummary = runCatching {
        Json.parseToJsonElement(arguments).jsonObject["changeSummary"]
            ?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")
    val reasoning = listOf(changeSummary, modelReasoning)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    return AiProviderTextResult(arguments, reasoning, "tool_call")
}

internal fun parseSchedulePatchToolResult(response: String): AiProviderTextResult? {
    val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
    val chatCall = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.optionalArray("tool_calls")?.firstOrNull()?.jsonObject
        ?.get("function")?.jsonObject
    val responseCall = root.optionalArray("output").mapNotNull { it as? JsonObject }
        ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
    val call = chatCall ?: responseCall ?: return null
    if (call["name"]?.jsonPrimitive?.contentOrNull != SchedulePatchToolName) return null
    val arguments = call["arguments"]?.let { value ->
        if (value is JsonPrimitive) value.contentOrNull else value.toString()
    }?.trim().orEmpty()
    if (arguments.isBlank()) return null
    val changeSummary = runCatching {
        Json.parseToJsonElement(arguments).jsonObject["changeSummary"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")
    require(changeSummary.isNotBlank()) { "模型没有提供本轮修改摘要" }
    return AiProviderTextResult(arguments, changeSummary, "tool_call")
}

