package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.webkit.WebView
import androidx.annotation.MainThread
import com.xiaomanjun.sleepdownschedule.core.ai.AgentTaskFiles
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaGate
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaKind
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.lang.ref.WeakReference
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume

/** The browser grants one document, not a general browser or access to persisted cookies. */
internal class AiEduWorkspace @MainThread constructor(
    context: Context, webView: WebView, private val bridge: ShiguangBridgeHost,
    private val config: ScheduleConfigEntity, private val periods: List<PeriodEntity>,
    private val entryUrl: String? = null,
    private val initialHasUserTurn: Boolean = false,
    private val initialAskedTimeSlots: Boolean = false,
    private val documentVersion: () -> Int = { 0 },
    private val networkLog: EduNetLogStore = EduNetLogStore(),
    private val stillCurrent: () -> Boolean
) {
    private val app = context.applicationContext
    private val target = WeakReference(webView)
    private val url = webView.url.orEmpty()
    private val browserSession = AiEduBrowserSessions.prepare(webView)
    private val origin = requireNotNull(eduOrigin(url))
    private var acceptedDocumentVersion = documentVersion()
    @Volatile private var ownerTaskId: String? = null
    @Volatile private var scriptRuns: Int = 0
    private val triedSources = HashSet<String>()
    @Volatile private var managedFreeQuotaEnabled: Boolean = false
    @Volatile private var managedFreeQuotaActionId: String? = null
    @Volatile private var scriptQuotaConsumed: Boolean = false
    val hasConsumedQuota: Boolean get() = scriptQuotaConsumed
    val taskId: String? get() = ownerTaskId
    fun bindTask(id: String) { ownerTaskId = id; EduVerifiedScripts.bindTask(id); AiEduBrowserSessions.bind(id, browserSession) }
    fun bindQuotaConsumer(isManagedFree: Boolean, actionId: String?) {
        managedFreeQuotaEnabled = isManagedFree
        managedFreeQuotaActionId = actionId?.takeIf { it.isNotBlank() }
    }
    private suspend fun consumeScriptQuotaIfNeeded() {
        if (!managedFreeQuotaEnabled || scriptQuotaConsumed) return
        val actionId = managedFreeQuotaActionId ?: ownerTaskId ?: UUID.randomUUID().toString()
        FreeAiQuotaGate.consumeIfManagedFree(
            context = app,
            isManagedFree = true,
            kind = FreeAiQuotaKind.IMPORT,
            actionId = actionId
        )
        scriptQuotaConsumed = true
    }
    fun taskFileChanged(path: String, hash: String, expectedTaskId: String?) {
        if (expectedTaskId != null && ownerTaskId == expectedTaskId) EduVerifiedScripts.fileChanged(expectedTaskId, path, hash)
    }
    private fun catalog() = ShiguangWarehouse.loadVisibleAdapters(app).filterNot { it.isAiEduImportTool() }
    private fun adapter(key: String) = catalog().firstOrNull { it.toIntentKey() == key }
        ?: error("适配目录已更新，请重新列出本机适配")
    private suspend fun page(): WebView = withContext(Dispatchers.Main.immediate) {
        requireNotNull(target.get()).also {
            check(browserSession?.available != false) { "教务页面已关闭，请回到网页重新开始" }
            require(stillCurrent() && documentVersion() == acceptedDocumentVersion && it.url == url) {
                "教务页面已变化，请回到网页重新开始"
            }
            check(ownerTaskId != null && AiEduImportProgressSession.progress.value?.taskId == ownerTaskId) { "此教务工具所属任务已结束，请重新开始" }
        }
    }
    fun execute(operation: String, args: JsonObject, files: AgentTaskFiles, checkActive: () -> Unit,
                evidence: EduImportEvidence): AiMaterialReply = runBlocking {
        if (initialHasUserTurn) evidence.markUserTurn()
        if (initialAskedTimeSlots) evidence.markTimeSlotsAsked()
        fun str(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun intArg(key: String, default: Int) = args[key]?.jsonPrimitive?.intOrNull
            ?: str(key).toIntOrNull() ?: default
        val executionTaskId = checkNotNull(ownerTaskId) { "教务任务尚未绑定" }
        browserSession?.activity?.value = when (operation) {
            "NET_LOG" -> "查看页面网络请求"
            "RELOAD_PAGE" -> "刷新并捕获首屏接口"
            "READ_PAGE" -> "读取页面与接口线索"
            "REQUEST" -> "查询教务接口"
            "VIEW_PAGE" -> "查看当前网页视口"
            "SCROLL" -> "滚动网页"
            "PROBE_SCRIPT" -> "运行网页探测脚本"
            "RUN_SCRIPT" -> "运行适配脚本"
            "RUN_ADAPTER" -> "运行本机适配"
            "LIST_ADAPTERS", "READ_ADAPTER" -> "检查本机适配"
            else -> "检查导入结果"
        }
        try {
        val browser = page()
        when (operation) {
            "NET_LOG" -> {
                val action = str("action").ifBlank { "list" }.lowercase()
                val replyText = when (action) {
                    "list" -> listNetLog()
                    "read" -> readNetLog(str("id"), intArg("maxChars", 12_000).coerceIn(1_000, 32_000))
                    "clear" -> {
                        networkLog.clear()
                        "已清空页面网络记录。"
                    }
                    else -> "action 只能是 list / read / clear。"
                }
                AiMaterialReply(replyText)
            }
            "RELOAD_PAGE" -> {
                val waitSeconds = intArg("waitSeconds", 25).coerceIn(10, 60)
                AiMaterialReply(reloadAndWait(browser, waitSeconds * 1_000L, checkActive))
            }
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
                AiMaterialReply("当前网页的单个视口，区域 [${region.left},${region.top},${region.right},${region.bottom}]；未自动滚动或生成长图。需要其他位置时自主 SCROLL 后再次查看。", image)
            }
            "PROBE_SCRIPT" -> {
                val source = files.read(str("path"))
                require(source.hash == str("expectedHash")) { "脚本已变化，请重新读取后执行" }
                val result = probe(browser, source.text, checkActive)
                AiMaterialReply(redactHiddenFields(result.toString()))
            }
            "READ_PAGE" -> {
                val maxChars = intArg("maxChars", 6_000).coerceIn(1_000, 12_000)
                val structured = evaluateObject(browser, readPageSummaryScript(maxChars))
                AiMaterialReply(redactHiddenFields(structured.toString()))
            }
            "LIST_ADAPTERS" -> AiMaterialReply(buildJsonArray { catalog().forEach { adapter -> add(buildJsonObject {
                put("adapterKey", adapter.toIntentKey()); put("name", adapter.displayName)
                put("url", redactAiUrl(adapter.importUrl)); put("local", adapter.isSelfUseAdapter())
                put("source", adapter.source.name)
            }) } }.toString())
            "READ_ADAPTER" -> AiMaterialReply(ShiguangWarehouse.resolveScript(app, adapter(str("adapterKey"))).take(120_000))
            "RUN_ADAPTER" -> {
                val selected = adapter(str("adapterKey"))
                require(selected.isGeneralEduTool() || eduOrigin(selected.importUrl) == origin) { "此适配属于其他教务地址，不能在当前页面执行" }
                val draft = runScript(browser, ShiguangWarehouse.resolveScript(app, selected), checkActive)
                evidence.recordVerifiedScriptDraft(draft)
                AiMaterialReply(draftToPayload(draft).toString())
            }
            "REQUEST" -> AiMaterialReply(redactHiddenFields(request(browser, str("path").ifBlank { str("url") },
                str("method").ifBlank { "GET" }, str("body"), checkActive)))
            "RUN_SCRIPT" -> {
                val directScript = stripMarkdownFences(str("script"))
                val note = str("note").trim()
                val timeSlots = str("timeSlots").trim().lowercase()
                if (timeSlots !in setOf("api", "user", "custom", "unknown") ||
                    (timeSlots == "unknown" && (!evidence.askedTimeSlots || !evidence.hasUserTurn))) {
                    return@runBlocking AiMaterialReply(
                        "TIME_SLOTS_REQUIRED：请先确认作息。timeSlots 为 api/user/custom/unknown；" +
                            "用户明确回答不知道后才使用 unknown。"
                    )
                }
                if (scriptRuns >= MaxScriptRuns) {
                    return@runBlocking AiMaterialReply(
                        "脚本已尝试运行 $scriptRuns 次达到本轮上限，请总结目前试过的接口、错误原因与下一步建议回复用户。"
                    )
                }
                val (scriptText, scriptPath, scriptHash) = if (directScript.isNotBlank()) {
                    require(str("path").isBlank()) { "请从 script 和 path 中选择一种脚本来源" }
                    Triple(directScript, "", sha256Hex(directScript))
                } else {
                    val fileSnap = files.read(str("path"))
                    require(fileSnap.hash == str("expectedHash")) { "脚本已变化，请重新读取后执行" }
                    Triple(fileSnap.text, fileSnap.path, fileSnap.hash)
                }
                require(scriptText.contains("shiguangBridge") && scriptText.contains("notifyTaskCompletion")) {
                    "需要提交完整的拾光适配脚本，并在提交课程后调用 shiguangBridge.notifyTaskCompletion()"
                }
                check(triedSources.add("$acceptedDocumentVersion:${sha256Hex(scriptText)}")) {
                    "SCRIPT_ALREADY_TRIED：本轮已试跑这份脚本，请按上一条错误修改后再调用 RUN_SCRIPT"
                }
                scriptRuns += 1
                val outcome = runCatching {
                    runScript(browser, scriptText, checkActive)
                }
                val draft = outcome.getOrElse { err ->
                    if (err is CancellationException) throw err
                    val detail = err.message.orEmpty().ifBlank { "脚本执行失败" }
                    if (scriptRuns == MaxScriptRuns) evidence.recordScriptLimit(detail)
                    AiEduScriptStore.append(
                        app,
                        AiEduScriptRecord.create(
                            pageUrl = eduScriptUploadPageUrl(entryUrl ?: url),
                            note = note,
                            script = scriptText,
                            ok = false,
                            courseCount = 0,
                            error = detail
                        )
                    )
                    throw err
                }
                checkActive()
                AiEduScriptStore.append(
                    app,
                    AiEduScriptRecord.create(
                        pageUrl = eduScriptUploadPageUrl(entryUrl ?: url),
                        note = note,
                        script = scriptText,
                        ok = true,
                        courseCount = draft.courses.size,
                        error = null
                    )
                )
                consumeScriptQuotaIfNeeded()
                check(ownerTaskId == executionTaskId && AiEduImportProgressSession.withCurrentTask(executionTaskId) {
                    EduVerifiedScripts.record(
                        taskId = executionTaskId,
                        name = str("school"),
                        loginUrl = entryUrl ?: url,
                        path = scriptPath,
                        hash = scriptHash,
                        source = scriptText,
                        note = note,
                        courseCount = draft.courses.size,
                        draft = draft
                    )
                }) { "任务已切换，未保留旧任务脚本" }
                evidence.recordVerifiedScriptDraft(draft)
                AiMaterialReply(
                    "标准拾光脚本已验证 ${draft.courses.size} 门课程，正在生成预览。"
                )
            }
            "OFFER_SAVE" -> {
                val verified = EduVerifiedScripts.current.value
                val pathArg = str("path")
                val hashArg = str("expectedHash")
                if (pathArg.isNotBlank()) {
                    val script = files.read(pathArg)
                    require(ownerTaskId == executionTaskId && script.hash == hashArg && verified?.hash == script.hash && verified.proposal.taskId == executionTaskId) {
                        "只能建议保存本轮实际执行成功且未再修改的脚本"
                    }
                } else {
                    require(ownerTaskId == executionTaskId && verified != null && verified.proposal.taskId == executionTaskId) {
                        "只能建议保存本轮实际执行成功的脚本"
                    }
                }
                val proposal = checkNotNull(verified).proposal
                val name = cleanAiEduSchoolName(str("name")).ifBlank { proposal.name }
                EduAdapterSaveProposals.offer(proposal.copy(name = name))
                AiMaterialReply("已展示保存并上传确认，等待用户操作。")
            }
            else -> error("未知教务工作区操作")
        }
        } finally { browserSession?.activity?.value = "等待操作" }
    }

    private fun listNetLog(): String {
        val all = networkLog.snapshot(origin).filter { it.kind != "document" }
        if (all.isEmpty()) {
            return "还没有记录到任何页面请求。可以让用户手动点一次「课表查询 / 我的课表」，或调用 RELOAD_PAGE 刷新一次后再看。"
        }
        val recent = all.takeLast(40)
        return buildString {
            append("共 ").append(all.size).append(" 条请求，显示最近 ").append(recent.size).append(" 条。")
            append("用 NET_LOG action=read + id 读取单条响应。\n")
            recent.forEach { entry ->
                append('#').append(entry.id).append(' ')
                append(entry.method).append(' ').append(entry.url.take(240))
                append(" → ").append(if (entry.status > 0) entry.status else "未完成")
                if (entry.contentType.isNotBlank()) append(' ').append(entry.contentType.take(60))
                if (entry.size > 0) append(' ').append(entry.size).append('字')
                if (entry.json) append(" json")
                if (entry.keys.isNotEmpty()) append(" keys=").append(entry.keys.joinToString(","))
                if (entry.reqBody.isNotBlank()) append(" req=").append(entry.reqBody.replace('\n', ' ').take(80))
                if (entry.error.isNotBlank()) append(" error=").append(entry.error.take(80))
                append('\n')
            }
        }
    }

    private fun readNetLog(id: String, maxChars: Int): String {
        val clean = id.trim().removePrefix("#")
        val entry = networkLog.snapshot(origin).lastOrNull { it.id == clean }
            ?: return "没有编号为 #$clean 的记录。先调 NET_LOG action=list 看现有请求。"
        return buildString {
            append("id: ").append(entry.id).append('\n')
            append("kind: ").append(entry.kind).append('\n')
            append("method: ").append(entry.method).append('\n')
            append("url: ").append(entry.url).append('\n')
            append("status: ").append(entry.status).append('\n')
            append("contentType: ").append(entry.contentType).append('\n')
            append("size: ").append(entry.size).append('\n')
            if (entry.keys.isNotEmpty()) append("keys: ").append(entry.keys.joinToString(",")).append('\n')
            if (entry.error.isNotBlank()) append("error: ").append(entry.error).append('\n')
            if (entry.reqBody.isNotBlank()) {
                append("--- 请求体 ---\n")
                append(redactHiddenFields(entry.reqBody).take(maxChars)).append('\n')
            }
            if (entry.respBody.isNotBlank()) {
                append("--- 响应 ---\n")
                append(redactHiddenFields(entry.respBody).take(maxChars))
            } else {
                append("--- 响应 ---\n（没有可读正文：可能是二进制、跨域 opaque 响应或请求未完成）")
            }
        }
    }

    private suspend fun reloadAndWait(browser: WebView, waitMs: Long, checkActive: () -> Unit): String {
        check(!eduWebIsTransientAuthUrl(url)) { "当前是认证页面，请先完成登录并进入课表。" }
        val before = networkLog.latestTopDocumentTs(origin)
        val startedAt = System.currentTimeMillis()
        withContext(Dispatchers.Main.immediate) {
            page()
            browser.reload()
        }
        val deadline = startedAt + waitMs
        var sawDocument = false
        var lastCount = networkLog.snapshot(origin).size
        var quietSince = startedAt
        while (System.currentTimeMillis() < deadline) {
            checkActive()
            delay(300)
            val reloaded = withContext(Dispatchers.Main.immediate) {
                check(stillCurrent() && browser.url == url) { "刷新后页面已跳转，请回到教务课表重新分析" }
                browser.progress == 100 && documentVersion() != acceptedDocumentVersion
            }
            if (!sawDocument && (networkLog.latestTopDocumentTs(origin) > before || reloaded)) {
                sawDocument = true
                quietSince = System.currentTimeMillis()
            }
            if (sawDocument) {
                val count = networkLog.snapshot(origin).size
                if (count != lastCount) {
                    lastCount = count
                    quietSince = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - quietSince >= 1_200L) {
                    break
                }
            }
        }
        val newCount = networkLog.snapshot(origin).count { it.ts >= startedAt && it.kind != "document" }
        return if (!sawDocument) {
            "刷新后 ${waitMs / 1000} 秒内没有检测到新文档：页面可能跳到了登录页，或这个站点不允许直接 reload。可以让用户手动点一次「课表查询」，再调 NET_LOG list。"
        } else {
            withContext(Dispatchers.Main.immediate) {
                check(stillCurrent() && browser.url == url) { "刷新后页面已跳转，请回到教务课表重新分析" }
                acceptedDocumentVersion = documentVersion()
            }
            "页面已刷新，本次新捕获 $newCount 条请求。用 NET_LOG action=list 查看，再挑候选读响应。"
        }
    }

    private fun readPageSummaryScript(maxChars: Int): String = """
        (() => {
          const clip = (s, n) => {
            const t = String(s == null ? '' : s).replace(/\s+/g, ' ').trim();
            return t.length > n ? t.slice(0, n) + '…' : t;
          };
          const redact = (u) => {
            try {
              const x = new URL(u, location.href);
              for (const k of Array.from(x.searchParams.keys()))
                if (/token|ticket|password|secret|auth|cookie|session|sign/i.test(k)) x.searchParams.set(k, '***');
              return x.pathname + (x.search || '');
            } catch (e) { return String(u || '').slice(0, 200); }
          };
          const same = (u) => { try { return new URL(u, location.href).origin === location.origin; } catch (e) { return false; } };
          const forms = Array.from(document.forms).slice(0, 8).map((f) => ({
            action: redact(f.action || ''),
            method: String(f.method || 'get').toUpperCase(),
            fields: Array.from(f.elements || []).slice(0, 14).map((e) => e.name || e.id).filter(Boolean)
          }));
          const links = Array.from(document.querySelectorAll('a[href]')).slice(0, 70)
            .map((a) => ({ text: clip(a.textContent, 24), href: redact(a.href) }))
            .filter((x) => x.text && x.href);
          const res = [];
          try { performance.getEntriesByType('resource').forEach((r) => { if (same(r.name)) res.push(redact(r.name)); }); } catch (e) {}
          const tables = Array.from(document.querySelectorAll('table')).slice(0, 8).map((t) => ({
            rows: t.rows ? t.rows.length : 0,
            cols: (t.rows && t.rows[0]) ? t.rows[0].cells.length : 0,
            head: (t.rows && t.rows[0]) ? Array.from(t.rows[0].cells).slice(0, 10).map((c) => clip(c.innerText, 12)) : []
          }));
          const frames = Array.from(document.querySelectorAll('iframe,frame')).slice(0, 8).map((f) => {
            let text = '';
            try { text = clip(f.contentDocument?.body?.innerText, Math.floor($maxChars / 8)); } catch (_) {}
            return { src: redact(f.src || ''), text };
          });
          const selects = Array.from(document.querySelectorAll('select')).slice(0, 12).map((element) => ({
            id: element.id || element.name,
            selected: element.selectedIndex,
            options: Array.from(element.options).slice(0, 16).map((option) => clip(option.label, 40))
          }));
          return JSON.stringify({
            path: location.pathname,
            title: clip(document.title, 80),
            textPreview: clip(document.body ? document.body.innerText : '', $maxChars),
            forms: forms,
            links: links,
            resources: Array.from(new Set(res)).slice(-80),
            tables: tables,
            frames: frames,
            selects: selects
          });
        })()
    """.trimIndent()

    private fun redactHiddenFields(text: String): String = HiddenFieldRegex.replace(text) { m ->
        val size = m.groupValues[3].length
        "${m.groupValues[1]}${m.groupValues[2]}[已省略 $size 字，脚本里用 document.querySelector 读取]"
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline < 0) return trimmed
        val body = trimmed.substring(firstNewline + 1)
        return body.removeSuffix("```").trim()
    }

    private fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

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

    private suspend fun runScript(
        browser: WebView,
        source: String,
        checkActive: () -> Unit
    ): com.xiaomanjun.sleepdownschedule.ImportDraft {
        require(source.toByteArray().size in 1..AiEduScriptUploader.MaxScriptBytes) { "脚本为空或超过上传大小上限" }
        check(!bridge.running.value) { "已有教务脚本正在执行，请等待其结束" }
        val key = "_sleepdownEduTrial_${UUID.randomUUID().toString().replace("-", "")}"
        val access = "window[${JsonPrimitive(key)}]"
        try {
            val started = evaluate(browser, eduScriptTrialSource(key, source))
            if (started != "\"started\"") throw EduScriptTrialFailure("SCRIPT_START：网页没有启动脚本")
            val result = withTimeoutOrNull(90_000) execution@ {
                while (true) {
                    checkActive()
                    val snapshot = evaluateObject(browser, "$access?.snapshot() || JSON.stringify({done:true,code:'DOCUMENT_CHANGED',error:'教务文档已变化'})")
                    if (snapshot["done"]?.jsonPrimitive?.booleanOrNull == true) return@execution snapshot
                    delay(200)
                }
                @Suppress("UNREACHABLE_CODE") error("不可达")
            } ?: throw EduScriptTrialFailure("SCRIPT_TIMEOUT：90 秒内未完成，请检查请求及异步回调是否结束")
            return result.toEduScriptDraft(config, periods, ::redactHiddenFields)
        } finally {
            withContext(NonCancellable) {
                runCatching { evaluate(browser, "(() => { $access?.dispose(); delete $access; })()") }
            }
        }
    }

    private suspend fun evaluate(browser: WebView, source: String): String = withTimeoutOrNull(5_000) {
        withContext(Dispatchers.Main.immediate) {
            page()
            val result = suspendCancellableCoroutine<String> { continuation ->
                browser.evaluateJavascript(source) {
                    if (continuation.isActive) continuation.resume(it ?: "null")
                }
            }
            page()
            result
        }
    } ?: error("网页脚本响应超过5秒，请重试或返回网页检查")

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
            window.__sleepdownNetSkip=true;var reqPromise;
            try{reqPromise=fetch(${JsonPrimitive(address)},Object.assign($options,{signal:controller.signal}))}finally{window.__sleepdownNetSkip=false}
            reqPromise.then(async r=>{
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

    private companion object {
        const val MaxScriptRuns = 4
        val HiddenFieldRegex = Regex(
            """(__VIEWSTATEGENERATOR|__VIEWSTATE|__EVENTVALIDATION|__RequestVerificationToken)""" +
                """(?![A-Za-z0-9])([\s\S]{0,120}?)([A-Za-z0-9+/%_\-][A-Za-z0-9+/=%_\-]{299,})"""
        )
    }
}
