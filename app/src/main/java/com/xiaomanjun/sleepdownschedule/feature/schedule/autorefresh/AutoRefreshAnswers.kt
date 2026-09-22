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

    fun resolve(answers: Map<String, String>, request: EduBridgeInteractionRequest): String? = when (request) {
        is EduBridgeInteractionRequest.Alert -> "true"
        is EduBridgeInteractionRequest.Prompt -> answers[key(request)]
        is EduBridgeInteractionRequest.SingleSelection -> answers[key(request)]?.let { selected ->
            request.options.indexOf(selected).takeIf { it >= 0 }?.toString()
        }
    }
}
