package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.webkit.WebView
import androidx.annotation.MainThread
import com.xiaomanjun.sleepdownschedule.core.ai.AgentTaskFiles
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.injectShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.isolatedShiguangScript
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.lang.ref.WeakReference
import java.net.URI
import java.util.UUID
import kotlin.coroutines.resume

internal data class EduAiInteraction(val taskId: String, val bridge: ShiguangBridgeHost, val request: EduBridgeInteractionRequest)
internal object EduAiInteractions {
    val current = MutableStateFlow<EduAiInteraction?>(null)
    fun clear(bridge: ShiguangBridgeHost) { if (current.value?.bridge === bridge) current.value = null }
}

/** The browser grants one document, not a general browser or access to persisted cookies. */
internal class AiEduWorkspace @MainThread constructor(
    context: Context, webView: WebView, private val bridge: ShiguangBridgeHost,
    private val config: ScheduleConfigEntity, private val periods: List<PeriodEntity>,
    private val desktopMode: Boolean, private val entryUrl: String? = null, private val stillCurrent: () -> Boolean
) {
    private val app = context.applicationContext
    private val target = WeakReference(webView)
    private val url = webView.url.orEmpty()
    private val pageName = webView.title.orEmpty().ifBlank { URI(url).host.orEmpty() }
    // Bind the document on Main; invoking the reader later belongs to the tool worker.
    private val readPage = eduPageMaterialReader(webView, stillCurrent)
    private val browserSession = AiEduBrowserSessions.prepare(webView)
    private val origin = requireNotNull(eduOrigin(url))
    @Volatile private var ownerTaskId: String? = null
    val taskId: String? get() = ownerTaskId
    fun bindTask(id: String) { ownerTaskId = id; EduVerifiedScripts.bindTask(id); AiEduBrowserSessions.bind(id, browserSession) }
    fun taskFileChanged(path: String, hash: String, expectedTaskId: String?) {
        if (expectedTaskId != null && ownerTaskId == expectedTaskId) EduVerifiedScripts.fileChanged(expectedTaskId, path, hash)
    }
    private fun catalog() = ShiguangWarehouse.loadVisibleAdapters(app).filterNot { it.isAiEduImportTool() }
    private fun adapter(key: String) = catalog().firstOrNull { it.toIntentKey() == key }
        ?: error("适配目录已更新，请重新列出本机适配")
    private suspend fun page(): WebView = withContext(Dispatchers.Main.immediate) {
        requireNotNull(target.get()).also {
            check(browserSession?.available != false) { "教务页面已关闭，请回到网页重新开始" }
            check(stillCurrent() && eduOrigin(it.url.orEmpty()) == origin) { "原教务页面已关闭或跳转，请回到网页重新开始" }
            check(ownerTaskId != null && AiEduImportProgressSession.progress.value?.taskId == ownerTaskId) { "此教务工具所属任务已结束，请重新开始" }
        }
    }
    fun execute(operation: String, args: JsonObject, files: AgentTaskFiles, checkActive: () -> Unit,
                evidence: EduImportEvidence): AiMaterialReply = runBlocking {
        fun str(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val executionTaskId = checkNotNull(ownerTaskId) { "教务任务尚未绑定" }
        browserSession?.activity?.value = when (operation) {
            "READ_PAGE" -> "读取页面与接口线索"
            "REQUEST" -> "查询教务接口"
            "VIEW_PAGE" -> "查看当前网页视口"
            "SCROLL" -> "滚动网页"
            "PROBE_SCRIPT" -> "运行网页探测脚本"
            "RUN_SCRIPT" -> "运行临时脚本"
            "RUN_ADAPTER" -> "运行本机适配"
            "LIST_ADAPTERS", "READ_ADAPTER" -> "检查本机适配"
            else -> "检查导入结果"
        }
        try {
        val browser = page()
        when (operation) {
            "SCROLL" -> AiMaterialReply(evaluateObject(browser, eduViewportScrollScript(str("direction"), str("selector"))).toString())
            "VIEW_PAGE" -> {
                val captured = withContext(Dispatchers.Main.immediate) {
                    page()
                    requireNotNull(captureEduViewport(browser)) { "当前网页视口无法读取，请先用 DOM 或接口核对，或请用户返回网页" }
                }
                page()
                val region = MaterialRegion(str("left").toIntOrNull() ?: 0, str("top").toIntOrNull() ?: 0,
                    str("right").toIntOrNull() ?: 1000, str("bottom").toIntOrNull() ?: 1000)
                val image = if (region == MaterialRegion()) captured else withContext(Dispatchers.Default) {
                    renderImageDetail(android.util.Base64.decode(captured.base64, android.util.Base64.DEFAULT), 0, region)
                }
                evidence.recordPageRead()
                AiMaterialReply("当前网页的单个视口，区域 [${region.left},${region.top},${region.right},${region.bottom}]；未自动滚动或生成长图。需要其他位置时自主 SCROLL 后再次查看。", image)
            }
            "PROBE_SCRIPT" -> {
                val source = files.read(str("path"))
                require(source.hash == str("expectedHash")) { "脚本已变化，请重新读取后执行" }
                val result = probe(browser, source.text, checkActive)
                if (result["requests"]?.jsonPrimitive?.intOrNull?.let { it > 0 } == true) evidence.recordInterfaceAttempt()
                AiMaterialReply(result.toString())
            }
            "READ_PAGE" -> AiMaterialReply(readPage() + "\n[当前页面已加载资源与接口线索]\n" +
                evaluateObject(browser, """
                    JSON.stringify({path:location.pathname,resources:performance.getEntriesByType('resource').map(r=>r.name)
                      .filter(u=>{try{return new URL(u).origin===location.origin}catch(e){return false}}).slice(-80)
                      .map(u=>{const v=new URL(u);for(const k of Array.from(v.searchParams.keys()))if(/token|ticket|password|secret|authorization|cookie/i.test(k))v.searchParams.delete(k);return v.pathname+v.search}),
                      forms:Array.from(document.forms).slice(0,10).map(f=>({action:new URL(f.action).pathname,method:f.method}))})
                """.trimIndent())).also { evidence.recordPageRead() }
            "LIST_ADAPTERS" -> AiMaterialReply(buildJsonArray { catalog().forEach { adapter -> add(buildJsonObject {
                put("adapterKey", adapter.toIntentKey()); put("name", adapter.displayName)
                put("url", redactAiUrl(adapter.importUrl)); put("local", adapter.isSelfUseAdapter())
            }) } }.toString())
            "READ_ADAPTER" -> AiMaterialReply(ShiguangWarehouse.resolveScript(app, adapter(str("adapterKey"))).take(120_000))
            "RUN_ADAPTER" -> {
                val selected = adapter(str("adapterKey"))
                require(selected.isGeneralEduTool() || eduOrigin(selected.importUrl) == origin) { "此适配属于其他教务地址，不能在当前页面执行" }
                AiMaterialReply(draftToPayload(runScript(browser, ShiguangWarehouse.resolveScript(app, selected), checkActive)).toString())
                    .also { evidence.recordInterfaceAttempt() }
            }
            "REQUEST" -> AiMaterialReply(request(browser, str("url"), str("method").ifBlank { "GET" }, str("body"), checkActive))
                .also { evidence.recordInterfaceAttempt() }
            "PAGE_FALLBACK" -> {
                evidence.choosePageFallback(str("reason"))
                AiMaterialReply("已记录页面解析依据：${str("reason")}。可使用已读取的实际页面内容核对课表；若只是当前周视图，仍需取得全学期数据或询问用户。")
            }
            "RUN_SCRIPT" -> {
                val script = files.read(str("path"))
                require(script.hash == str("expectedHash")) { "脚本已变化，请重新读取后执行" }
                EduVerifiedScripts.clear(executionTaskId)
                val draft = runScript(browser, script.text, checkActive)
                checkActive()
                check(ownerTaskId == executionTaskId && AiEduImportProgressSession.withCurrentTask(executionTaskId) {
                    EduVerifiedScripts.record(executionTaskId, pageName, entryUrl ?: url, script.path, script.hash, script.text)
                }) { "任务已切换，未保留旧任务脚本" }
                evidence.recordInterfaceAttempt()
                AiMaterialReply("标准拾光脚本已成功生成并通过本地校验，尚未保存课程。请结合页面/接口结果复核后提交 IMPORT_SCHEDULE。\n" + draftToPayload(draft))
            }
            "OFFER_SAVE" -> {
                val script = files.read(str("path"))
                val verified = EduVerifiedScripts.current.value
                require(ownerTaskId == executionTaskId && script.hash == str("expectedHash") && verified?.hash == script.hash && verified.proposal.taskId == executionTaskId) { "只能建议保存本轮实际执行成功且未再修改的脚本" }
                val name = str("name").trim()
                require(name.isNotBlank() && name.length <= 80)
                EduAdapterSaveProposals.offer(checkNotNull(verified).proposal.copy(name = name))
                AiMaterialReply("已向用户展示保存自用适配的建议，等待用户确认；未写入本机适配目录，也未发布到仓库。")
            }
            else -> error("未知教务工作区操作")
        }
        } finally { browserSession?.activity?.value = "等待操作" }
    }

    private suspend fun probe(browser: WebView, source: String, checkActive: () -> Unit): JsonObject = withTimeoutOrNull(25_000) operation@{
        val key = "__sleepdown_probe_${UUID.randomUUID().toString().replace("-", "")}"
        require(evaluate(browser, "$EduProbeRunnerScript(${JsonPrimitive(key)}, async function(window, fetch, console) {\n$source\n})") == "true") {
            "探测脚本未启动，请检查 JavaScript 语法"
        }
        try {
            while (true) {
                checkActive()
                delay(100)
                val result = evaluateObject(browser, "JSON.stringify(window[${JsonPrimitive(key)}] || {error:'页面已变化'})")
                if (result["pending"]?.jsonPrimitive?.booleanOrNull != true) return@operation result
            }
            @Suppress("UNREACHABLE_CODE") error("不可达")
        } finally {
            withContext(NonCancellable) { runCatching { evaluate(browser,
                "(()=>{const r=window[${JsonPrimitive(key)}];if(r&&r.cancel)r.cancel();delete window[${JsonPrimitive(key)}]})()") } }
        }
    } ?: error("网页探测超过25秒，请缩小范围或检查当前页面")

    private suspend fun runScript(browser: WebView, source: String, checkActive: () -> Unit): com.xiaomanjun.sleepdownschedule.ImportDraft =
        withContext(Dispatchers.Main.immediate) {
            require(source.toByteArray().size in 1..262_144 && source.contains("shiguangBridge")) { "需要标准拾光 Bridge 脚本，最大 256 KiB" }
            check(!bridge.running.value) { "已有教务脚本正在执行，请等待其结束" }
            page()
            val completion = CompletableDeferred<Result<com.xiaomanjun.sleepdownschedule.ImportDraft>>()
            val executionToken = UUID.randomUUID().toString()
            val capture: (Result<com.xiaomanjun.sleepdownschedule.ImportDraft>) -> Unit = { outcome ->
                EduAiInteractions.clear(bridge)
                completion.complete(outcome)
            }
            try { withTimeoutOrNull(90_000) execution@{
                    bridge.bindWebView(browser)
                    bridge.beginTask(config, periods, allowImportedBellTimes = true, capture = capture,
                        captureInteraction = { EduAiInteractions.current.value = EduAiInteraction(checkNotNull(ownerTaskId), bridge, it) },
                        executionToken = executionToken)
                    browser.injectShiguangRuntime(desktopMode)
                    browser.evaluateJavascript(isolatedShiguangScript(source, executionToken)) { result ->
                        if (result != "\"started\"") bridge.cancelCapture(capture)
                    }
                while (true) {
                    checkActive()
                    withTimeoutOrNull(150) { completion.await() }?.let { return@execution it.getOrThrow() }
                }
                @Suppress("UNREACHABLE_CODE") error("不可达")
            } ?: error("拾光脚本超过90秒，请检查接口或补充实际错误后重试") } finally { bridge.cancelCapture(capture); EduAiInteractions.clear(bridge) }
        }

    private suspend fun evaluate(browser: WebView, source: String): String = withTimeoutOrNull(5_000) { withContext(Dispatchers.Main.immediate) {
        page()
        suspendCancellableCoroutine { continuation -> browser.evaluateJavascript(source) {
            if (continuation.isActive) continuation.resume(it ?: "null")
        } }
    } } ?: error("网页脚本响应超过5秒，请重试或返回网页检查")

    private suspend fun evaluateObject(browser: WebView, source: String): JsonObject =
        Json.parseToJsonElement(Json.parseToJsonElement(evaluate(browser, source)).jsonPrimitive.content).jsonObject

    private suspend fun request(browser: WebView, path: String, method: String, body: String, checkActive: () -> Unit): String = withTimeoutOrNull(25_000) operation@{
        val address = URI(url).resolve(path).toString()
        require(eduOrigin(address) == origin) { "仅能查询当前登录教务的同源接口" }
        require(method.uppercase() in setOf("GET", "POST") && body.length <= 16_000)
        require(method.uppercase() != "GET" || body.isBlank())
        val key = "__sleepdown_ai_${UUID.randomUUID().toString().replace("-", "")}"
        val options = buildJsonObject { put("method", method.uppercase()); put("credentials", "same-origin")
            if (body.isNotBlank()) { put("body", body); put("headers", buildJsonObject {
                put("Content-Type", if (body.trimStart().startsWith('{')) "application/json" else "application/x-www-form-urlencoded")
            }) }
        }
        evaluate(browser, """
            (()=>{const key=${JsonPrimitive(key)}, controller=new AbortController();
            window[key]={pending:true,cancel:()=>controller.abort()};const timer=setTimeout(()=>controller.abort(),20000);
            fetch(${JsonPrimitive(address)},Object.assign($options,{signal:controller.signal})).then(async r=>{
              if(new URL(r.url).origin!==location.origin)throw Error('接口跳转到了其他登录地址，请重新登录');
              const text=await r.text();if(text.length>2000000)throw Error('接口数据超过2MB，请缩小查询范围');
              let clean=text;try{clean=JSON.stringify(JSON.parse(text),(k,v)=>/password|token|ticket|cookie|secret|authorization/i.test(k)?'[登录凭据已隐藏]':v)}catch(e){}
              window[key]={status:r.status,type:r.headers.get('content-type'),text:clean.slice(0,120000),truncated:clean.length>120000};
            }).catch(()=>window[key]={error:'接口查询失败，请检查登录态、实际接口和校园网络'}).finally(()=>clearTimeout(timer));return true})()
        """.trimIndent())
        try {
            while (true) {
                checkActive()
                delay(100)
                val data = evaluateObject(browser, "JSON.stringify(window[${JsonPrimitive(key)}] || {error:'页面已变化'})")
                if (data["pending"]?.jsonPrimitive?.booleanOrNull != true) return@operation data.toString()
            }
            @Suppress("UNREACHABLE_CODE") ""
        } finally { withContext(NonCancellable) { runCatching { evaluate(browser, "(()=>{const r=window[${JsonPrimitive(key)}];if(r&&r.cancel)r.cancel();delete window[${JsonPrimitive(key)}]})()") } } }
    } ?: error("接口查询超过25秒，请核对页面、登录态与查询范围")
}
