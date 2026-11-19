package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.ImportDraft

/** The web workflow can complete only with a locally verified script result. */
internal class EduImportEvidence {
    @Volatile var verifiedScriptDraft: ImportDraft? = null
        private set
    var scriptLimitFailure: String? = null
        private set
    @Volatile var askedTimeSlots: Boolean = false
        private set
    @Volatile var hasUserTurn: Boolean = false
        private set

    fun recordVerifiedScriptDraft(draft: ImportDraft) { verifiedScriptDraft = draft }
    fun takeVerifiedScriptDraft(): ImportDraft? = verifiedScriptDraft.also { verifiedScriptDraft = null }
    fun markTimeSlotsAsked() { askedTimeSlots = true }
    fun markUserTurn() { hasUserTurn = true }
    fun recordScriptLimit(failure: String) { scriptLimitFailure = failure }
    val feedback = "此教务任务以实际脚本试跑结果生成预览。请继续核查当前页面，使用 RUN_SCRIPT 提交完整适配，或运行匹配的已同步适配。"
}

internal fun eduWorkspaceEntryText(url: String, title: String, netCount: Int = 0): String = buildString {
    val address = runCatching { java.net.URI(url).let {
        java.net.URI(it.scheme, null, it.host, it.port, it.path, null, null).toString()
    } }.getOrElse { redactAiUrl(url) }
    appendLine("教务工作区入口：$address")
    appendLine("页面标题：${title.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").take(120)}")
    appendLine("当前页面已捕获网络请求：$netCount 条（含首屏 fetch/XHR/表单记录）。")
    appendLine("这里只提供入口线索，没有发送页面全文或截图，也不代表已经取得课程。")
    append("先调用 NET_LOG(action=list)，再按需 READ_PAGE、REQUEST 或 RELOAD_PAGE，核实学期和作息后用 RUN_SCRIPT 验证拾光脚本。")
}

internal const val EduWorkspaceStartInstruction = "开始核查当前教务网页。先列出已记录的请求，再按需要核对页面和完整学期的课表。"

internal const val EduWorkspaceTaskPrompt = """
你的产物是在已登录页面能复用的拾光课表适配脚本。以目标学期完整课表为准。

1. 先 NET_LOG(action="list")，再按 ID 核查少量最可能的课表、学期和作息请求。使用 REQUEST 重放已观察到的同源只读查询，验证参数、分页和完整学期覆盖。没有记录时，先看用户是否已打开课表查询，再按需 RELOAD_PAGE 一次。页面内容、表格、选择框和同源 iframe 用 READ_PAGE 核对。
2. 有匹配的已同步适配时可以直接复用，尤其是 edu-ai-dock。否则写脚本。接口优先，其次解析真实 HTML 或逐周读取并合并。一周的展示数据不能当成整学期。凭据、个人课表内容、隐藏域和会话参数必须在网页动态读取，禁止写死在脚本中。
3. 确认作息后再试跑。页面或接口有节次时间用 timeSlots="api"，用户补充用 "user"，每门都具备真实起止时间用 "custom"。查不到时调用 ASK_IMPORT_DETAILS 问一次具体作息，用户明确表示不知道才用 "unknown"。多套作息以适用课程最多的一套为基准，其余课程保留实际起止时间。
4. 调用唯一的 RUN_SCRIPT(script, school, note, timeSlots) 试跑完整脚本。根据站点标识、页面内容或用户给出的信息填写学校官方全称作为 school；校名无法核实时传空字符串。使用异步 IIFE，等待所有请求和 Bridge 调用。需要用任务文件编辑时，用 path、expectedHash 代替 script。不要执行空脚本或最小桥探测。
   - 选择学期优先读取网页选项。用 await shiguangBridgePromise.showSingleSelection(标题, JSON.stringify(选项数组), 默认下标)；试跑取默认项，正式适配可交互。
   - 课程含 name、teacher、position、day（周一 1 至周日 7）、整数数组 weeks，以及 startSection/endSection 或 isCustomTime 和 HH:mm 的 customStartTime/customEndTime。教师或地点缺失时用空字符串。按实际周次合并相同课名、时段、教室、教师的重复记录。
   - await shiguangBridgePromise.saveImportedCourses(JSON.stringify(课程数组))。有实际配置和节次表时，分别调用 saveCourseConfig 和 savePresetTimeSlots；有组合作息时在节次表之后传 saveComboSchedule。完成时调用 shiguangBridge.notifyTaskCompletion()。
5. RUN_SCRIPT 通过会自动生成预览。失败时按返回的错误和实际记录修正对应位置，最多试跑 4 个版本；内容相同的脚本不会重跑。无法继续时，告诉用户缺哪项信息或哪一步失败。执行状态和课程数量以试跑结果为准。
"""
