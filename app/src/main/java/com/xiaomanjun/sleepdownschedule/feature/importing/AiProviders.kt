package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URL


internal object AiEndpointDiagnostics {
    @Volatile
    var forcedEndpointStyle: AiEndpointStyle? = null
}

internal fun AiProviderConfig.normalizedForRequest(): AiProviderConfig {
    val useResponses = (AiEndpointDiagnostics.forcedEndpointStyle ?: endpointStyle) ==
        AiEndpointStyle.RESPONSES && supportsResponses
    val configuredPath = if (useResponses) responsesPath else chatCompletionsPath
    val normalizedBaseUrl = if (configuredPath.isBlank()) {
        // An empty path means baseUrl is already the complete endpoint supplied by the backend.
        baseUrl.trim().trimEnd('/')
    } else {
        normalizeAiBaseUrlForProvider(providerId, baseUrl)
    }
    val isMimo = usesMimoProtocol()
    val outputMode = if (providerId == AiProviderPresets.deepSeek.id || isMimo) {
        StructuredOutputMode.PROMPT_ONLY
    } else {
        structuredOutputMode
    }
    return copy(
        baseUrl = normalizedBaseUrl,
        model = if (isMimo && providerId != AiProviderPresets.dailyFree.id && model.equals("mimo-v2.5-omni", ignoreCase = true)) {
            "mimo-v2.5"
        } else {
            model
        },
        endpointStyle = if (useResponses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = outputMode,
        supportsPdfDirect = useResponses && supportsPdfDirect,
        // Compatible chat endpoints cannot consume a raw PDF, but the import
        // pipeline still uses this capability flag to render the PDF into page
        // images before creating the multi-modal request.
        supportsFileUpload = supportsFileUpload,
        authType = when {
            providerId == AiProviderPresets.dailyFree.id -> managedAiAuthType(normalizedBaseUrl)
            isMimo -> AiAuthType.CustomHeader
            else -> authType
        }
    )
}

internal fun isOfficialOpenAIBaseUrl(value: String): Boolean {
    val url = value.trim().trimEnd('/')
    return url.equals("https://api.openai.com/v1", ignoreCase = true)
}


internal interface AiScheduleImportProvider {
    fun parseSchedule(
        config: AiProviderConfig,
        input: AiScheduleInput,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult
}

internal fun JsonObjectBuilder.putChatSamplingAndReasoning(config: AiProviderConfig) {
    if (config.providerId != AiProviderPresets.deepSeek.id) {
        put("temperature", JsonPrimitive(0.1))
        return
    }
    val thinkingEnabled = config.reasoningEffort != AiReasoningEffort.NONE
    put("thinking", buildJsonObject {
        put("type", JsonPrimitive(if (thinkingEnabled) "enabled" else "disabled"))
    })
    if (thinkingEnabled) {
        val effort = when (config.reasoningEffort) {
            AiReasoningEffort.XHIGH, AiReasoningEffort.MAX -> "max"
            else -> "high"
        }
        // DeepSeek Chat Completions defines reasoning_effort as a top-level field.
        put("reasoning_effort", JsonPrimitive(effort))
    }
}

internal class OpenAiCompatibleChatProvider : AiScheduleImportProvider {
    override fun parseSchedule(
        config: AiProviderConfig,
        input: AiScheduleInput,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult {
        val userContent: JsonElement = when (input) {
            is AiScheduleInput.ExtractedText -> JsonPrimitive(
                aiSchedulePrompt() + "\n\n课表原文（${input.sourceName}）：\n" + input.text
            )
            is AiScheduleInput.ImageBase64 -> buildJsonArray {
                addTextPart(aiSchedulePrompt() + "\n\n请识别图片课表并输出 JSON。")
                addImagePart("data:${input.mimeType};base64,${input.base64}")
            }
            is AiScheduleInput.Images -> buildJsonArray {
                addTextPart(aiSchedulePrompt() + "\n\n下面图片来自同一份课表文件，按页码或视口位置有序发送。相邻图片可能保留少量重叠区域用于校对，请不要重复生成课程。请综合所有图片还原完整课程信息。")
                input.images.forEach { image -> addImagePart(image.dataUrl) }
            }
            is AiScheduleInput.CapturedPage -> buildJsonArray {
                addTextPart(
                    aiSchedulePrompt() +
                        "\n\n这是从教务 WebView 分层抓取的页面内容。DOM 文本可能包含导航、版权、重复表格或缺失字段；截图为当前页面可见渲染结果。" +
                        "\n页面来源仅用于识别上下文；仅使用本页明确给出的周次和时间，不根据学校名称或记忆推算作息。" +
                        "\n截图来自同一张课表页面，按滚动位置以原始视口比例分段发送；相邻图片可能有少量重叠，只用于校对上下文，不要把同一课程重复输出。请尽力读取并还原完整课程信息。" +
                        "\n请先分析截图/表格结构：识别星期列、节次行、时间轴、课程块跨度、周次标注、地点和教师，再生成 JSON。请优先以截图中的真实课表为准，DOM 文本作为辅助。若课程待定或未安排，先询问用户如何处理，不要编造占位节次或周次。" +
                        "\n\n来源：${input.sourceName}" +
                        "\n诊断：\n${input.warnings.joinToString("\n").ifBlank { "无" }}" +
                        "\n\n页面文本：\n${input.text}"
                )
                input.images.forEach { image -> addImagePart(image.dataUrl) }
            }
            is AiScheduleInput.RawFile -> error("当前兼容接口不支持直接上传原始文件，请改用文本、图片或 OpenAI 原生文件模式。")
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            val messages = buildJsonArray {
                scheduleParserSystemMessage()
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", userContent)
                })
            }
            put("messages", messages)
            val interactive = networkContext.inputType != "REPAIR"
            put("tools", JsonArray(buildList {
                add(scheduleImportChatTool())
                if (interactive) add(askImportDetailsTool(responses = false))
            }))
            scheduleToolChoice(config, ScheduleImportToolName, allowProgressUpdates = interactive)?.let { put("tool_choice", it) }
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val result = requestScheduleImport(config, body, networkContext)
        return if (result.finishReason == "length") {
            continueTruncatedScheduleJson(
                config,
                body["messages"] ?: JsonArray(emptyList()),
                result,
                networkContext
            )
        } else {
            result
        }
    }

    fun reviseSchedule(
        config: AiProviderConfig,
        request: String,
        history: AiEduImportProgress,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult {
        val initialMessages = buildJsonArray {
            scheduleParserSystemMessage()
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(request))
            })
        }
        val firstBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", initialMessages)
            put("tools", JsonArray(listOf(readOriginalSourceChatTool(), schedulePatchChatTool())))
            if (config.providerId != AiProviderPresets.deepSeek.id || config.reasoningEffort == AiReasoningEffort.NONE) {
                put("tool_choice", JsonPrimitive("auto"))
            }
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val firstResponse = postJson(
            config.resolveRequestEndpoint(),
            config.apiKey,
            firstBody.toString(),
            config.authType,
            config.providerId,
            networkContext
        )
        parseSchedulePatchToolResult(firstResponse)?.let { return it }
        val root = Json.parseToJsonElement(firstResponse).jsonObject
        val assistantMessage = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?: throw AiServiceResponseException("模型未返回可识别的工具调用", firstResponse)
        val readCall = assistantMessage["tool_calls"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull {
                it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull == ReadOriginalSourceToolName
            }
            ?: throw AiServiceResponseException("模型未提交课表，也未请求读取原始材料", firstResponse)
        val callId = readCall["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceContent = revisionSourceChatContent(history)
        val secondMessages = buildJsonArray {
            initialMessages.forEach(::add)
            add(assistantMessage)
            add(buildJsonObject {
                put("role", JsonPrimitive("tool"))
                put("tool_call_id", JsonPrimitive(callId))
                put("content", JsonPrimitive("原始导入材料已加载，请结合随后提供的内容复核。"))
            })
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", sourceContent)
            })
        }
        val secondBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", secondMessages)
            put("tools", JsonArray(listOf(schedulePatchChatTool())))
            scheduleToolChoice(config, SchedulePatchToolName)?.let { put("tool_choice", it) }
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val secondResponse = postJson(
            config.resolveRequestEndpoint(),
            config.apiKey,
            secondBody.toString(),
            config.authType,
            config.providerId,
            networkContext
        )
        return parseSchedulePatchToolResult(secondResponse)
            ?: throw AiServiceResponseException("模型读取原始材料后未提交课表", secondResponse)
    }

    private fun revisionSourceChatContent(history: AiEduImportProgress): JsonElement =
        if (history.screenshotPreviews.isEmpty()) {
            JsonPrimitive(buildAiOriginalSourceContext(history))
        } else {
            buildJsonArray {
                addTextPart(buildAiOriginalSourceContext(history))
                history.screenshotPreviews.forEach { addImagePart(it.dataUrl) }
            }
        }

    private fun JsonArrayBuilder.addTextPart(text: String) {
        add(buildJsonObject {
            put("type", JsonPrimitive("text"))
            put("text", JsonPrimitive(text))
        })
    }

    private fun JsonArrayBuilder.addImagePart(dataUrl: String) {
        add(buildJsonObject {
            put("type", JsonPrimitive("image_url"))
            put("image_url", buildJsonObject { put("url", JsonPrimitive(dataUrl)) })
        })
    }

    private fun JsonArrayBuilder.scheduleParserSystemMessage() {
        add(buildJsonObject {
            put("role", JsonPrimitive("system"))
            put("content", JsonPrimitive("你是 SleepDown Schedule 的课表解析器。最终课表必须通过指定工具提交完整 JSON；允许先按要求输出独立的简短进度摘要，不能混入课程数据。若输出被截断，后续请求只续写剩余 JSON。"))
        })
    }

    private fun JsonObjectBuilder.putChatOutputBudget(config: AiProviderConfig) {
        if (config.providerId == AiProviderPresets.deepSeek.id) {
            put("max_tokens", JsonPrimitive(393216))
        } else if (config.usesMimoProtocol()) {
            put("max_completion_tokens", JsonPrimitive(131072))
        } else {
            put("max_tokens", JsonPrimitive(32768))
        }
    }

    private fun continueTruncatedScheduleJson(
        config: AiProviderConfig,
        originalMessages: JsonElement,
        firstResult: AiProviderTextResult,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult {
        return continueTruncatedImportText(firstResult) { combinedContent ->
            networkContext.interaction?.checkActive()
            val messages = buildJsonArray {
                originalMessages.jsonArray.forEach { add(it) }
                add(buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonPrimitive(combinedContent))
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put(
                        "content",
                        JsonPrimitive(
                            "上一条 JSON 因输出长度限制被截断。请从截断位置继续输出剩余 JSON，直到 JSON 完整闭合。" +
                                "不要重复已经输出的内容，不要输出解释、Markdown、代码块或思考过程。续写必须能直接拼接到上一条末尾。"
                        )
                    )
                })
            }
            val body = buildJsonObject {
                put("model", JsonPrimitive(config.model))
                put("messages", messages)
                putChatSamplingAndReasoning(config)
                putChatOutputBudget(config)
            }
            val response = postJson(
                config.resolveRequestEndpoint(),
                config.apiKey,
                body.toString(),
                config.authType,
                config.providerId,
                networkContext
            )
            networkContext.interaction?.checkActive()
            runCatching {
                parseChatCompletionTextResult(response)
            }.getOrElse {
                if (it is AiServiceResponseException) throw it
                throw AiServiceResponseException("AI 续写响应结构无法解析：${it.message.orEmpty()}", response, it)
            }
        }
    }
}

internal class OpenAiResponsesProvider : AiScheduleImportProvider {
    override fun parseSchedule(
        config: AiProviderConfig,
        input: AiScheduleInput,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult {
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive(aiSchedulePrompt()))
            })
            when (input) {
                is AiScheduleInput.RawFile -> {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_file"))
                        put("filename", JsonPrimitive(input.fileName))
                        put("file_data", JsonPrimitive("data:${input.mimeType};base64," + Base64.encodeToString(input.bytes, Base64.NO_WRAP)))
                    })
                }
                is AiScheduleInput.ExtractedText -> add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(input.text))
                })
                is AiScheduleInput.Images -> input.images.forEach { image ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(image.dataUrl))
                    })
                }
                is AiScheduleInput.CapturedPage -> {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_text"))
                        put(
                            "text",
                            JsonPrimitive(
                                "这是从教务 WebView 分层抓取的页面内容。请优先以截图中的真实课表为准，DOM 文本作为辅助。\n" +
                                    "页面来源仅用于识别上下文；仅使用本页明确给出的周次和时间，不根据学校名称或记忆推算作息。\n" +
                                    "请先分析截图/表格结构：识别星期列、节次行、时间轴、课程块跨度、周次标注、地点和教师，再生成 JSON。\n" +
                                    "截图来自同一张课表页面，按滚动位置以原始视口比例分段发送；相邻图片可能有少量重叠，只用于校对上下文，不要重复生成课程。\n" +
                                    "来源：${input.sourceName}\n" +
                                    "诊断：\n${input.warnings.joinToString("\n").ifBlank { "无" }}\n\n" +
                                    "页面文本：\n${input.text}"
                            )
                        )
                    })
                    input.images.forEach { image ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("input_image"))
                            put("image_url", JsonPrimitive(image.dataUrl))
                        })
                    }
                }
                is AiScheduleInput.ImageBase64 -> add(buildJsonObject {
                    put("type", JsonPrimitive("input_image"))
                    put("image_url", JsonPrimitive("data:${input.mimeType};base64,${input.base64}"))
                })
            }
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            // Course files can contain names, locations and school identifiers. Keep the
            // request stateless so an official OpenAI Responses call is not retained remotely
            // by default.
            put("store", JsonPrimitive(false))
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", content)
                })
            })
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            val interactive = networkContext.inputType != "REPAIR"
            put("tools", JsonArray(buildList {
                add(scheduleImportResponsesTool())
                if (interactive) add(askImportDetailsTool(responses = true))
            }))
            scheduleToolChoice(config, ScheduleImportToolName, allowProgressUpdates = interactive)?.let { put("tool_choice", it) }
        }
        return requestScheduleImport(config, body, networkContext)
    }

    fun reviseSchedule(
        config: AiProviderConfig,
        request: String,
        history: AiEduImportProgress,
        networkContext: AiImportNetworkContext
    ): AiProviderTextResult {
        val initialInput = buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(request))
                })
            })
        }
        val firstBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("store", JsonPrimitive(false))
            put("input", JsonArray(listOf(initialInput)))
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            put("tools", JsonArray(listOf(readOriginalSourceResponsesTool(), schedulePatchResponsesTool())))
            put("tool_choice", JsonPrimitive("auto"))
        }
        val firstResponse = postJson(
            config.resolveRequestEndpoint(),
            config.apiKey,
            firstBody.toString(),
            config.authType,
            config.providerId,
            networkContext
        )
        parseSchedulePatchToolResult(firstResponse)?.let { return it }
        val root = Json.parseToJsonElement(firstResponse).jsonObject
        val outputItems = root["output"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        val readCall = outputItems.firstOrNull {
            it["type"]?.jsonPrimitive?.contentOrNull == "function_call" &&
                it["name"]?.jsonPrimitive?.contentOrNull == ReadOriginalSourceToolName
        } ?: throw AiServiceResponseException("模型未提交课表，也未请求读取原始材料", firstResponse)
        val callId = readCall["call_id"]?.jsonPrimitive?.contentOrNull
            ?: readCall["id"]?.jsonPrimitive?.contentOrNull
            ?: throw AiServiceResponseException("读取原始材料的工具调用缺少 call_id", firstResponse)
        val sourceInput = buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(buildAiOriginalSourceContext(history)))
                })
                history.screenshotPreviews.forEach { image ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(image.dataUrl))
                    })
                }
            })
        }
        val secondInput = buildJsonArray {
            add(initialInput)
            outputItems.forEach(::add)
            add(buildJsonObject {
                put("type", JsonPrimitive("function_call_output"))
                put("call_id", JsonPrimitive(callId))
                put("output", JsonPrimitive("原始导入材料已加载，请结合随后提供的内容复核。"))
            })
            add(sourceInput)
        }
        val secondBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("store", JsonPrimitive(false))
            put("input", secondInput)
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            put("tools", JsonArray(listOf(schedulePatchResponsesTool())))
            scheduleToolChoice(config, SchedulePatchToolName)?.let { put("tool_choice", it) }
        }
        val secondResponse = postJson(
            config.resolveRequestEndpoint(),
            config.apiKey,
            secondBody.toString(),
            config.authType,
            config.providerId,
            networkContext
        )
        return parseSchedulePatchToolResult(secondResponse)
            ?: throw AiServiceResponseException("模型读取原始材料后未提交课表", secondResponse)
    }
}

internal fun JsonObjectBuilder.putResponsesReasoning(config: AiProviderConfig) {
    put("reasoning", buildJsonObject {
        val effort = if (config.providerId == AiProviderPresets.deepSeek.id) {
            when (config.reasoningEffort) {
                AiReasoningEffort.NONE -> "none"
                AiReasoningEffort.MINIMAL, AiReasoningEffort.LOW -> "low"
                AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH -> "high"
                AiReasoningEffort.XHIGH, AiReasoningEffort.MAX -> "max"
            }
        } else if (config.usesMimoProtocol()) {
            mimoResponsesEffort(config.reasoningEffort)
        } else {
            config.reasoningEffort.apiValue
        }
        put("effort", JsonPrimitive(effort))
        if (config.requestsResponsesReasoningSummary()) {
            put("summary", JsonPrimitive("auto"))
        }
    })
}

internal fun AiProviderConfig.requestsResponsesReasoningSummary(): Boolean {
    if (reasoningEffort == AiReasoningEffort.NONE || usesMimoProtocol() ||
        providerId == AiProviderPresets.deepSeek.id) return false
    // Model families keep their summary protocol when served by a managed/custom gateway.
    val name = model.lowercase()
    return (providerId == AiProviderPresets.openAI.id && isOfficialOpenAIBaseUrl(baseUrl)) ||
        Regex("^(gpt-[56](?:[.-]|$)|o[134](?:-|$))").containsMatchIn(name)
}

private fun JsonObjectBuilder.putResponsesOutputBudget(config: AiProviderConfig) {
    when (config.providerId) {
        AiProviderPresets.deepSeek.id -> put("max_output_tokens", JsonPrimitive(393216))
        AiProviderPresets.mimo.id,
        AiProviderPresets.mimoTokenPlan.id,
        AiProviderPresets.dailyFree.id -> put("max_output_tokens", JsonPrimitive(131072))
    }
}

suspend fun loadAiImportFile(context: Context, uri: Uri): Result<AiImportFile> =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val reportedMime = resolver.getType(uri)
            val metadata = runCatching {
                resolver.query(
                    uri,
                    arrayOf(
                        android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            cursor.getString(nameIndex)
                        } else {
                            null
                        }
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            cursor.getLong(sizeIndex)
                        } else {
                            null
                        }
                        displayName to size
                    }
                }
            }.getOrNull()
            val name = metadata?.first
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "schedule-file"
            val mime = normalizeAiImportMimeType(name, reportedMime)
            metadata?.second
                ?.takeIf { it >= 0 }
                ?.let { size ->
                    require(size <= MaxAiImportFileBytes) { "文件不能超过 20MB" }
                }
            val rawBytes = resolver.openInputStream(uri)
                ?.use { it.readBytesWithLimit(MaxAiImportFileBytes) }
                ?: error("无法读取文件")
            val bytes = if (classifyAiImportDocument(name, mime) == AiImportDocumentKind.IMAGE) {
                compressAiImportImage(rawBytes)
            } else {
                rawBytes
            }
            AiImportFile(uri, name, mime, bytes)
        }
    }


private fun AiImportFile.toDataUrl(): String {
    return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

