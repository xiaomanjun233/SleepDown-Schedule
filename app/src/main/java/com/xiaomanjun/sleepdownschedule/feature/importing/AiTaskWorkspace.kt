package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import com.xiaomanjun.sleepdownschedule.core.ai.AgentJavaScript
import com.xiaomanjun.sleepdownschedule.core.ai.AgentTaskFiles
import kotlinx.serialization.json.*
import java.io.Closeable
import java.io.File
import java.util.UUID

internal const val TaskWorkspaceTool = "TASK_WORKSPACE"
internal const val EduWorkspaceTool = "EDU_WORKSPACE"
internal val EduPrimaryTools = setOf("NET_LOG", "READ_PAGE", "REQUEST", "RELOAD_PAGE", "RUN_SCRIPT")

internal fun eduToolOperation(name: String, args: JsonObject): String =
    if (name in EduPrimaryTools) name else args["operation"]?.jsonPrimitive?.contentOrNull.orEmpty()

/** Task files are private scratch space; the optional browser capability stays with its owner. */
internal class AiTaskWorkspace(context: Context, private val edu: AiEduWorkspace? = null,
                               private val allowPageImages: Boolean = false,
                               private val checkActive: () -> Unit = {}) : Closeable {
    private val app = context.applicationContext
    private val files = AgentTaskFiles(File(app.cacheDir, "ai-tasks/${UUID.randomUUID()}"))
    private val eduTaskId = edu?.taskId
    val eduEvidence = edu?.let { EduImportEvidence() }
    val names get() = setOf(TaskWorkspaceTool) + if (edu != null) EduPrimaryTools + EduWorkspaceTool else emptySet()
    fun definitions(responses: Boolean) = names.map { name ->
        if (name in EduPrimaryTools) eduPrimaryToolDefinition(name, responses)
        else taskWorkspaceDefinition(name, responses, allowPageImages)
    }

    fun execute(name: String, args: JsonObject): AiMaterialReply {
        checkActive()
        val operation = eduToolOperation(name, args)
        fun str(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun snapshot(value: AgentTaskFiles.Snapshot) = AiMaterialReply(buildJsonObject {
            put("path", value.path); put("sha256", value.hash); put("text", value.text)
        }.toString())
        if (name == EduWorkspaceTool || name in EduPrimaryTools) {
            require(operation != "VIEW_PAGE" || allowPageImages) { "当前模型不支持图片输入，请使用 DOM、接口或探测脚本" }
            return checkNotNull(edu) { "本轮没有教务网页权限" }.execute(operation, args, files, checkActive, checkNotNull(eduEvidence))
        }
        require(name == TaskWorkspaceTool)
        return when (operation) {
            "LIST" -> AiMaterialReply(files.list().joinToString("\n").ifBlank { "任务目录为空" })
            "READ" -> snapshot(files.read(str("path")))
            "WRITE" -> snapshot(files.write(str("path"), str("text"), str("expectedHash")).also { edu?.taskFileChanged(it.path, it.hash, eduTaskId) })
            "EDIT" -> snapshot(files.edit(str("path"), str("oldText"), str("newText"), str("expectedHash")).also { edu?.taskFileChanged(it.path, it.hash, eduTaskId) })
            "RUN" -> {
                val source = files.read(str("path"))
                require(source.hash == str("expectedHash")) { "脚本已变化，请重新读取后执行" }
                AiMaterialReply(AgentJavaScript.execute(app, source.text, str("inputJson").ifBlank { "{}" }))
            }
            else -> error("未知任务文件操作")
        }
    }
    override fun close() { files.close() }
}

private fun eduPrimaryToolDefinition(name: String, responses: Boolean): JsonObject {
    val (description, fields, required) = when (name) {
        "NET_LOG" -> Triple("列出课表网页已捕获的真实请求，或按 ID 读取脱敏请求和响应。",
            listOf("action", "id", "maxChars"), listOf("action"))
        "READ_PAGE" -> Triple("读取当前页面的正文、课表、学期选项和接口线索。", listOf("maxChars"), emptyList())
        "REQUEST" -> Triple("沿用当前登录态，通过同源相对路径只读查询教务接口。",
            listOf("path", "method", "body"), listOf("path"))
        "RELOAD_PAGE" -> Triple("在记录缺失时刷新当前文档并等待其请求结束。", listOf("waitSeconds"), emptyList())
        else -> Triple("在当前文档试跑完整拾光脚本。传 script，或传任务文件的 path 和 expectedHash。school 填已核实的学校全称。timeSlots 为 api、user、custom 或 unknown。校验通过后直接进入预览。",
            listOf("script", "path", "expectedHash", "school", "note", "timeSlots"), listOf("timeSlots", "school"))
    }
    val function = buildJsonObject {
        put("name", name)
        put("description", description)
        put("strict", false)
        put("parameters", buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", JsonArray(required.map(::JsonPrimitive)))
            put("properties", buildJsonObject {
                fields.forEach { field ->
                    put(field, buildJsonObject {
                        put("type", if (field == "maxChars" || field == "waitSeconds") "integer" else "string")
                    })
                }
            })
        })
    }
    return if (responses) JsonObject(function + ("type" to JsonPrimitive("function")))
        else buildJsonObject { put("type", "function"); put("function", function) }
}

internal fun taskWorkspaceDefinition(name: String, responses: Boolean, allowPageImages: Boolean = false): JsonObject {
    val edu = name == EduWorkspaceTool
    val operations = if (edu) listOf("PROBE_SCRIPT", "SCROLL", "LIST_ADAPTERS", "READ_ADAPTER", "RUN_ADAPTER", "OFFER_SAVE") + (if (allowPageImages) listOf("VIEW_PAGE") else emptyList())
        else listOf("LIST", "READ", "WRITE", "EDIT", "RUN")
    val fields = if (edu) listOf("adapterKey", "path", "expectedHash", "name", "direction", "selector")
        else listOf("path", "text", "expectedHash", "oldText", "newText", "inputJson")
    val function = buildJsonObject {
        put("name", name)
        put("strict", false)
        put("description", if (edu) "辅助教务工具：PROBE_SCRIPT 执行任务文件中的只读同源探测；SCROLL 和 VIEW_PAGE 查看页面；LIST_ADAPTERS/READ_ADAPTER/RUN_ADAPTER 查看或复用已同步适配；用户要求手动保存时可用 OFFER_SAVE 打开已验证脚本的保存并上传弹窗。新脚本在独立 RUN_SCRIPT 工具试跑。"
            else "任务私有文件工作区，路径相对本次目录。WRITE创建时expectedHash为空，覆盖/EDIT/RUN需最近READ返回的sha256；EDIT的oldText需唯一精确匹配。RUN是隔离同步JS main(input)纯数据转换，无DOM/网络/手机文件权限。教务脚本用标准拾光 Bridge，在 RUN_SCRIPT 工具试跑。")
        put("parameters", buildJsonObject {
            put("type", "object"); put("additionalProperties", false)
            put("required", buildJsonArray { add("operation") })
            put("properties", buildJsonObject {
                put("operation", buildJsonObject { put("type", "string"); put("enum", JsonArray(operations.map(::JsonPrimitive))) })
                fields.forEach { put(it, buildJsonObject { put("type", "string") }) }
                if (edu && allowPageImages) listOf("left", "top", "right", "bottom").forEach { key ->
                    put(key, buildJsonObject { put("type", "integer"); put("minimum", 0); put("maximum", 1000) })
                }
            })
        })
    }
    return if (responses) JsonObject(function + ("type" to JsonPrimitive("function")))
        else buildJsonObject { put("type", "function"); put("function", function) }
}
