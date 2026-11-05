package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.FrameLayout
import com.xiaomanjun.sleepdownschedule.core.ai.AgentTaskFiles
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AiEduWorkspaceThreadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun workerReadsDomAndInterfaceHintsWithoutAccessingWebViewOffMain() = withWorkspace { workspace, browser, files ->
        val evidence = EduImportEvidence()
        val reply = onWorker { workspace.execute("READ_PAGE", buildJsonObject {}, files, {}, evidence) }
        assertTrue(reply.text.contains("虚构课程页面"))
        val hints = JSONObject(reply.text.substringAfter("[当前页面已加载资源与接口线索]\n"))
        assertEquals("/api/semester", hints.getJSONArray("resources").getString(0))
        assertEquals(2, browser.scripts.size)
        assertFalse(evidence.readyToSubmit)
        evidence.choosePageFallback("已核实当前页面没有可用的结构化课程接口")
        assertTrue(evidence.readyToSubmit)
    }

    @Test fun workerRequestsUseMainForJavascriptAndKeepInterfaceEvidence() = withWorkspace { workspace, browser, files ->
        val evidence = EduImportEvidence()
        val reply = onWorker { workspace.execute("REQUEST", buildJsonObject { put("url", "/api/semester") }, files, {}, evidence) }
        assertTrue(reply.text.contains("fixture-semester"))
        assertTrue(browser.scripts.any { it.contains("fetch(") })
        assertTrue(browser.scripts.any { it.contains("delete window[") })
        evidence.recordPageRead()
        assertTrue(evidence.readyToSubmit)
    }

    @Test fun sameOriginNavigationCannotReplaceTheDocumentGrantedAtEntry() = withWorkspace { workspace, browser, files ->
        browser.currentUrl = "http://127.0.0.1:18765/other"
        val evidence = EduImportEvidence()
        val failure = assertThrows(IllegalArgumentException::class.java) {
            onWorker { workspace.execute("READ_PAGE", buildJsonObject {}, files, {}, evidence) }
        }
        assertTrue(failure.message.orEmpty().contains("页面已变化"))
        assertTrue(browser.scripts.isEmpty())
        assertFalse(evidence.readyToSubmit)
    }

    @Test fun probeUsesTheLatestTaskFileOnTheSameBorrowedBrowserAndCleansUp() = withWorkspace { workspace, browser, files ->
        val file = files.write("probe.js", "return {courses:2};", "")
        val evidence = EduImportEvidence()
        val args = buildJsonObject { put("path", file.path); put("expectedHash", file.hash) }
        val result = onWorker { workspace.execute("PROBE_SCRIPT", args, files, {}, evidence) }
        assertTrue(result.text.contains("probe-result"))
        assertTrue(browser.scripts.any { it.contains("async function(window, fetch, console)") && it.contains(file.text) })
        assertTrue(browser.scripts.any { it.contains("delete window[") && it.contains("__sleepdown_probe_") })
        evidence.recordPageRead()
        assertTrue(evidence.readyToSubmit)
        files.edit(file.path, "courses:2", "courses:3", file.hash)
        val before = browser.scripts.size
        assertThrows(IllegalArgumentException::class.java) { onWorker { workspace.execute("PROBE_SCRIPT", args, files, {}, evidence) } }
        assertEquals(before, browser.scripts.size)
    }

    @Test fun scrollUsesAnExplicitContainerAndDoesNotFabricateInvestigationEvidence() = withWorkspace { workspace, browser, files ->
        val evidence = EduImportEvidence()
        val result = onWorker { workspace.execute("SCROLL", buildJsonObject { put("direction", "down"); put("selector", "#courses") }, files, {}, evidence) }
        assertEquals(80, JSONObject(result.text).getInt("y"))
        assertTrue(browser.scripts.single().contains("#courses"))
        assertFalse(evidence.readyToSubmit)
    }

    @Test fun pageImageToolIsAdvertisedOnlyToVisualModelsInBothProtocols() {
        for (responses in listOf(true, false)) for (vision in listOf(true, false)) {
            val raw = taskWorkspaceDefinition(EduWorkspaceTool, responses, vision)
            val function = if (responses) raw else raw.getValue("function").jsonObject
            val operations = function.getValue("parameters").jsonObject.getValue("properties").jsonObject
                .getValue("operation").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(vision, "VIEW_PAGE" in operations)
            assertTrue("PROBE_SCRIPT" in operations && "SCROLL" in operations)
        }
    }

    @Test fun nativeSuccessfulScriptCanBeSavedAfterScratchCleanupButAnEditRequiresANewRun() = withWorkspace { workspace, _, _ ->
        val app = RuntimeEnvironment.getApplication()
        val script = """(async()=>{await window.shiguangBridgePromise.saveImportedCourses('[{"name":"验证课程","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]');window.shiguangBridge.notifyTaskCompletion()})()"""
        AiTaskWorkspace(app, workspace).use { task ->
            val written = task.execute(TaskWorkspaceTool, buildJsonObject { put("operation", "WRITE"); put("path", "adapter.js"); put("text", script); put("expectedHash", "") })
            val hash = JSONObject(written.text).getString("sha256")
            fun run(hash: String) = onWorker { task.execute(EduWorkspaceTool, buildJsonObject { put("operation", "RUN_SCRIPT"); put("path", "adapter.js"); put("expectedHash", hash) }) }
            run(hash)
            assertEquals(script, EduVerifiedScripts.current.value?.proposal?.source)
            val edited = task.execute(TaskWorkspaceTool, buildJsonObject { put("operation", "EDIT"); put("path", "./adapter.js"); put("oldText", "验证课程"); put("newText", "修订课程"); put("expectedHash", hash) })
            assertNull(EduVerifiedScripts.current.value)
            run(JSONObject(edited.text).getString("sha256"))
            assertTrue(EduVerifiedScripts.current.value!!.proposal.source.contains("修订课程"))
        }
        assertTrue(EduVerifiedScripts.current.value!!.proposal.source.contains("修订课程"))
    }

    private fun withWorkspace(test: (AiEduWorkspace, MainThreadWebView, AgentTaskFiles) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val browser = MainThreadWebView(app)
        val home = EduBrowserWebViewHost(app, browser)
        val bridge = ShiguangBridgeHost({}, {}, {})
        browser.onStandardScript = { source ->
            val token = Regex("var token = \\\"([^\\\"]+)\\\"").find(source)!!.groupValues[1]
            val name = if (source.contains("修订课程")) "修订课程" else "验证课程"
            bridge.onMessageReceived(JSONObject().put("action", "saveImportedCourses").put("executionToken", token)
                .put("callbackId", "save-courses").put("payload", JSONObject().put("coursesJsonString",
                    """[{"name":"$name","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""").toString()).toString())
            bridge.onMessageReceived(JSONObject().put("action", "notifyTaskCompletion").put("executionToken", token).toString())
        }
        try {
            val workspace = AiEduWorkspace(app, browser, bridge, defaultConfig(), defaultPeriods(), false) { true }
            workspace.bindTask("thread-fixture")
            val floatingHost = FrameLayout(app)
            assertTrue(checkNotNull(AiEduBrowserSessions.current.value).session.attach(floatingHost))
            assertSame(floatingHost, browser.parent)
            AiEduImportProgressSession.beginTask(AiEduImportProgress(taskId = "thread-fixture"))
            AgentTaskFiles(temporary.newFolder("task")).use { test(workspace, browser, it) }
        } finally {
            AiEduImportProgressSession.update(null)
            EduVerifiedScripts.clear("thread-fixture")
            AiEduBrowserSessions.release(browser)
            home.removeView(browser)
            bridge.close()
            browser.destroy()
        }
    }

    private fun <T> onWorker(action: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<T> { action() }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!future.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(1)
            }
            return try { future.get(1, TimeUnit.SECONDS) }
            catch (failure: ExecutionException) { throw checkNotNull(failure.cause) }
        } finally {
            future.cancel(true)
            executor.shutdownNow()
        }
    }

    private class MainThreadWebView(app: Application) : WebView(app) {
        var currentUrl = "http://127.0.0.1:18765/"
        val scripts = mutableListOf<String>()
        var onStandardScript: ((String) -> Unit)? = null
        override fun getUrl(): String {
            check(Looper.myLooper() == Looper.getMainLooper()) { "WebView.getUrl called off main thread" }
            return currentUrl
        }
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            check(Looper.myLooper() == Looper.getMainLooper()) { "WebView.evaluateJavascript called off main thread" }
            scripts += script
            val result = when {
                script.contains("var actualWindow = window;") -> { onStandardScript?.invoke(script); "\"started\"" }
                script.contains("performance.getEntriesByType") -> JSONObject.quote("""{"path":"/","resources":["/api/semester"],"forms":[]}""")
                script.contains("node.scrollTo(") -> JSONObject.quote("""{"x":0,"y":80,"height":100}""")
                script.startsWith("JSON.stringify(window[") && script.contains("__sleepdown_probe_") -> JSONObject.quote("""{"result":"probe-result","requests":1,"logs":[]}""")
                script.contains("fetch(") -> "true"
                script.startsWith("JSON.stringify(window[") -> JSONObject.quote("""{"status":200,"text":"fixture-semester"}""")
                script.contains("delete window[") -> "null"
                else -> JSONObject.quote(JSONObject().put("title", "测试教务").put("url", currentUrl).put("text", "虚构课程页面").toString())
            }
            resultCallback?.onReceiveValue(result)
        }
    }
}
