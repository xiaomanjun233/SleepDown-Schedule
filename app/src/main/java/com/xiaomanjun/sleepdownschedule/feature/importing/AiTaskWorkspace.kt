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

/** Task files are private scratch space; the optional browser capability stays with its owner. */
internal class AiTaskWorkspace(context: Context, private val edu: AiEduWorkspace? = null,
                               private val allowPageImages: Boolean = false,
                               private val checkActive: () -> Unit = {}) : Closeable {
    private val app = context.applicationContext
    private val files = AgentTaskFiles(File(app.cacheDir, "ai-tasks/${UUID.randomUUID()}"))
    private val eduTaskId = edu?.taskId
    val eduEvidence = edu?.let { EduImportEvidence() }
    val names get() = setOf(TaskWorkspaceTool) + if (edu != null) setOf(EduWorkspaceTool) else emptySet()
    fun definitions(responses: Boolean) = names.map { taskWorkspaceDefinition(it, responses, allowPageImages) }

    fun execute(name: String, args: JsonObject): AiMaterialReply {
        checkActive()
        val operation = args.getValue("operation").jsonPrimitive.content
        fun str(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun snapshot(value: AgentTaskFiles.Snapshot) = AiMaterialReply(buildJsonObject {
            put("path", value.path); put("sha256", value.hash); put("text", value.text)
        }.toString())
        if (name == EduWorkspaceTool) {
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

internal fun taskWorkspaceDefinition(name: String, responses: Boolean, allowPageImages: Boolean = false): JsonObject {
    val edu = name == EduWorkspaceTool
    val operations = if (edu) listOf("READ_PAGE", "REQUEST", "PROBE_SCRIPT", "SCROLL", "LIST_ADAPTERS", "READ_ADAPTER", "RUN_ADAPTER", "RUN_SCRIPT", "PAGE_FALLBACK", "OFFER_SAVE") + (if (allowPageImages) listOf("VIEW_PAGE") else emptyList())
        else listOf("LIST", "READ", "WRITE", "EDIT", "RUN")
    val fields = if (edu) listOf("adapterKey", "url", "method", "body", "path", "expectedHash", "name", "reason", "direction", "selector")
        else listOf("path", "text", "expectedHash", "oldText", "newText", "inputJson")
    val function = buildJsonObject {
        put("name", name)
        put("strict", false)
        put("description", if (edu) "当前登录教务的工作区。READ_PAGE读取DOM、表单与实际资源线索，不自动生成长图。REQUEST也可读取已加载的同源JS源码。PROBE_SCRIPT运行TASK_WORKSPACE文件中的探测函数体（path与最新expectedHash），可await fetch和return局部JSON，console日志一并返回；仅支持同源GET/POST，20秒、结果120000字符、日志20条。只读DOM/课程查询，不输出登录凭据或写教务数据；最终导入仍使用标准RUN_SCRIPT。SCROLL通过direction=up/down/left/right/top/bottom滚动，selector可选CSS选择器，默认文档。支持视觉时VIEW_PAGE只查看当前单个视口，可传0–1000的left/top/right/bottom裁切细节；需要更多位置时自主滚动后查看，不能把当前视口当作全学期。先READ_PAGE/读取真实适配核实接口；REQUEST仅查询已确认的课程接口，禁止猜接口或登录、退课、选课、缴费、修改教务数据。LIST_ADAPTERS/READ_ADAPTER/RUN_ADAPTER复用本机拾光适配。RUN_SCRIPT执行任务文件中的标准拾光异步IIFE脚本，支持当前页面DOM/fetch。用window.shiguangBridgePromise异步调用：saveCourseConfig(JSON字符串{semesterStartDate,semesterTotalWeeks,defaultClassDuration,defaultBreakDuration,firstDayOfWeek})；savePresetTimeSlots(JSON字符串[{number,startTime,endTime}])；saveImportedCourses(JSON字符串[{name,teacher,position,day,startSection,endSection,weeks,isCustomTime,customStartTime,customEndTime,credit}])；teacher/position缺失写空字符串，day=1..7，weeks为实际正周次；saveComboSchedule须先成功保存基础作息。showAlert(title,content,confirmText)、showPrompt(title,tip,defaultText)、showSingleSelection(title,JSON选项数组,defaultIndex)均可等待用户回答。所有保存成功后才调用同步window.shiguangBridge.notifyTaskCompletion()；失败/取消不可通知成功。只生成待确认草稿，成功后结合页面复核。不要硬编码学生标识、登录凭据、学期日期或作息，复用脚本保留来源/MIT署名。OFFER_SAVE仅建议用户保存成功验证且未再修改的同一哈希脚本为自用适配，不能自行落盘。缺失真实作息必须询问，不能编造。"
            else "任务私有文件工作区，路径相对本次目录。WRITE创建时expectedHash为空，覆盖/EDIT/RUN需最近READ返回的sha256；EDIT的oldText需唯一精确匹配。RUN是隔离同步JS main(input)纯数据转换，无DOM/网络/手机文件权限；教务适配脚本使用EDU_WORKSPACE.RUN_SCRIPT及标准拾光Bridge，不用main格式。不能读取用户数据库或凭据。")
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
