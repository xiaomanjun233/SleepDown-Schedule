package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest

/** Remember selections by label, not position: upstream option ordering can change. */
internal object AutoRefreshAnswers {
    private fun key(request: EduBridgeInteractionRequest): String = when (request) {
        is EduBridgeInteractionRequest.Prompt -> "prompt|${request.title}|${request.message}"
        is EduBridgeInteractionRequest.SingleSelection -> "select|${request.title}"
        is EduBridgeInteractionRequest.Alert -> "alert|${request.title}"
    }

    fun record(answers: MutableMap<String, String>, request: EduBridgeInteractionRequest, json: String) {
        when (request) {
            is EduBridgeInteractionRequest.Prompt -> answers[key(request)] = json
            is EduBridgeInteractionRequest.SingleSelection ->
                json.toIntOrNull()?.let { request.options.getOrNull(it) }?.let { answers[key(request)] = it }
            is EduBridgeInteractionRequest.Alert -> Unit
        }
    }

    fun resolve(
        answers: Map<String, String>,
        request: EduBridgeInteractionRequest,
        schoolId: String? = null
    ): String? = when (request) {
        is EduBridgeInteractionRequest.Alert -> {
            // Instructions and previews may mention errors or contain course names. Only
            // an error heading makes this an adapter failure (e.g. UJS's timetable notice).
            val failed = listOf(
                "失败", "错误", "无效", "无法", "未登录", "请登录", "登录失效", "超时",
                "未获取到", "未找到", "导入已取消", "error", "exception", "invalid", "unauthorized", "forbidden"
            ).any { request.title.contains(it, ignoreCase = true) }
            if (failed) null else "true"
        }
        is EduBridgeInteractionRequest.Prompt -> answers[key(request)]
        is EduBridgeInteractionRequest.SingleSelection -> {
            val saved = answers[key(request)]
            if (saved != null) {
                // A missing saved option must never silently switch the connected semester.
                request.options.indexOf(saved).takeIf { it >= 0 }?.toString()
            } else if (schoolId in setOf("SWU", "UJS") &&
                request.title in setOf("选择学年", "选择学期")
            ) {
                // These reviewed Zhengfang adapters read the selected term from the school page.
                request.defaultIndex.takeIf { it in request.options.indices }?.toString()
            } else null
        }
    }
}
