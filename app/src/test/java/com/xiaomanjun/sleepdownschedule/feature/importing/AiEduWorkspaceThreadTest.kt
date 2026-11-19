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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
        val hints = JSONObject(reply.text)
        assertEquals("/api/semester", hints.getJSONArray("resources").getString(0))
        assertEquals(1, browser.scripts.size)
    }

    @Test fun workerRequestsUseMainForJavascript() = withWorkspace { workspace, browser, files ->
        val evidence = EduImportEvidence()
        val reply = onWorker { workspace.execute("REQUEST", buildJsonObject { put("url", "/api/semester") }, files, {}, evidence) }
        assertTrue(reply.text.contains("fixture-semester"))
        assertTrue(browser.scripts.any { it.contains("fetch(") })
        assertTrue(browser.scripts.any { it.contains("delete window[") })
    }

    @Test fun sameOriginNavigationCannotReplaceTheDocumentGrantedAtEntry() = withWorkspace { workspace, browser, files ->
        browser.currentUrl = "http://127.0.0.1:18765/other"
        val evidence = EduImportEvidence()
        val failure = assertThrows(IllegalArgumentException::class.java) {
            onWorker { workspace.execute("READ_PAGE", buildJsonObject {}, files, {}, evidence) }
        }
        assertTrue(failure.message.orEmpty().contains("页面已变化"))
        assertTrue(browser.scripts.isEmpty())
    }

    @Test fun probeUsesTheLatestTaskFileOnTheSameBorrowedBrowserAndCleansUp() = withWorkspace { workspace, browser, files ->
        val file = files.write("probe.js", "return {courses:2};", "")
        val evidence = EduImportEvidence()
        val args = buildJsonObject { put("path", file.path); put("expectedHash", file.hash) }
        val result = onWorker { workspace.execute("PROBE_SCRIPT", args, files, {}, evidence) }
        assertTrue(result.text.contains("probe-result"))
        assertTrue(browser.scripts.any { it.contains("async function(window, fetch, console)") && it.contains(file.text) })
        assertTrue(browser.scripts.any { it.contains("delete window[") && it.contains("__sleepdown_probe_") })
        files.edit(file.path, "courses:2", "courses:3", file.hash)
        val before = browser.scripts.size
        assertThrows(IllegalArgumentException::class.java) { onWorker { workspace.execute("PROBE_SCRIPT", args, files, {}, evidence) } }
        assertEquals(before, browser.scripts.size)
    }

    @Test fun scrollUsesAnExplicitContainer() = withWorkspace { workspace, browser, files ->
        val evidence = EduImportEvidence()
        val result = onWorker { workspace.execute("SCROLL", buildJsonObject { put("direction", "down"); put("selector", "#courses") }, files, {}, evidence) }
        assertEquals(80, JSONObject(result.text).getInt("y"))
        assertTrue(browser.scripts.single().contains("#courses"))
    }

    @Test fun pageImageToolIsAdvertisedOnlyToVisualModelsInBothProtocols() {
        for (responses in listOf(true, false)) for (vision in listOf(true, false)) {
            val raw = taskWorkspaceDefinition(EduWorkspaceTool, responses, vision)
            val function = if (responses) raw else raw.getValue("function").jsonObject
            val operations = function.getValue("parameters").jsonObject.getValue("properties").jsonObject
                .getValue("operation").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(vision, "VIEW_PAGE" in operations)
            assertTrue("PROBE_SCRIPT" in operations && "SCROLL" in operations)
            assertFalse("RUN_SCRIPT" in operations)
        }
    }

    @Test fun verifiedScriptCanBeSavedAfterScratchCleanupButAnEditRequiresANewRun() = withWorkspace { workspace, _, _ ->
        val app = RuntimeEnvironment.getApplication()
        val script = """(async()=>{await window.shiguangBridgePromise.saveImportedCourses('[{"name":"验证课程","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]');window.shiguangBridge.notifyTaskCompletion()})()"""
        AiTaskWorkspace(app, workspace).use { task ->
            val written = task.execute(TaskWorkspaceTool, buildJsonObject { put("operation", "WRITE"); put("path", "adapter.js"); put("text", script); put("expectedHash", "") })
            val hash = JSONObject(written.text).getString("sha256")
            fun run(hash: String) = onWorker { task.execute("RUN_SCRIPT", buildJsonObject {
                put("path", "adapter.js"); put("expectedHash", hash); put("timeSlots", "api")
            }) }
            run(hash)
            assertEquals(script, EduVerifiedScripts.current.value?.proposal?.source)
            val edited = task.execute(TaskWorkspaceTool, buildJsonObject { put("operation", "EDIT"); put("path", "./adapter.js"); put("oldText", "验证课程"); put("newText", "修订课程"); put("expectedHash", hash) })
            assertNull(EduVerifiedScripts.current.value)
            run(JSONObject(edited.text).getString("sha256"))
            assertTrue(EduVerifiedScripts.current.value!!.proposal.source.contains("修订课程"))
        }
        assertTrue(EduVerifiedScripts.current.value!!.proposal.source.contains("修订课程"))
    }

    @Test fun validatedRunScriptFinishesWithoutAnotherModelTurn() = withWorkspace { workspace, _, _ ->
        val app = RuntimeEnvironment.getApplication()
        val config = AiProviderConfig("fixture", "fixture", "placeholder", "https://fixture.invalid/v1", "fixture",
            AiEndpointStyle.CHAT_COMPLETIONS, StructuredOutputMode.PROMPT_ONLY, false, false, false,
            false, AiInputMode.AUTO, AiReasoningEffort.NONE, contextWindowTokens = 32_768)
        val body = buildJsonObject { put("messages", JsonArray(emptyList())) }
        var turns = 0
        val result = onWorker {
            AiMaterialWorkspace.fromPages("教务", "课表入口", emptyList()).use { materials ->
                materials.taskWorkspace = AiTaskWorkspace(app, workspace)
                requestScheduleImport(config, body, AiImportNetworkContext("EDU_WORKSPACE", materials = materials)) { _, _ ->
                    assertEquals(1, ++turns)
                    val args = buildJsonObject {
                        put("script", "window.shiguangBridge.notifyTaskCompletion()")
                        put("school", "测试大学")
                        put("timeSlots", "api")
                    }
                    """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","tool_calls":[{"id":"run-1","type":"function","function":{"name":"RUN_SCRIPT","arguments":${JsonPrimitive(args.toString())}}}]}}]}"""
                }
            }
        }
        val verified = checkNotNull(result.verifiedEduDraft)
        val preview = AiImportCheckpoint.create("thread-fixture", verified, "预览")
            .restore(defaultConfig()).getOrThrow()
        assertEquals("验证课程", verified.courses.single().name)
        assertTrue(checkNotNull(EduVerifiedScripts.current.value).matchesDraft(preview))
        AiEduImportProgressSession.requestFinalImport(preview, createNewSchedule = true, liveTaskId = "thread-fixture")
        val request = checkNotNull(AiEduImportProgressSession.finalImportRequest.value)
        assertTrue(AiEduImportProgressSession.claimFinalImportRequest(request))
        assertFalse(AiEduImportProgressSession.claimFinalImportRequest(request))
        val script = checkNotNull(request.verifiedScript)
        assertEquals("测试大学", script.schoolNameForAutomaticSave())
        assertTrue(isSleepDownAiEduAdapterId(script.proposal.adapterId))
        assertFalse(script.submission.state.value.saving)
    }

    private fun withWorkspace(test: (AiEduWorkspace, MainThreadWebView, AgentTaskFiles) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val browser = MainThreadWebView(app)
        val home = EduBrowserWebViewHost(app, browser)
        val bridge = ShiguangBridgeHost({}, {}, {})
        try {
            val workspace = AiEduWorkspace(app, browser, bridge, defaultConfig(), defaultPeriods()) { true }
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
        private var scriptCourse = "验证课程"
        override fun getUrl(): String {
            check(Looper.myLooper() == Looper.getMainLooper()) { "WebView.getUrl called off main thread" }
            return currentUrl
        }
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            check(Looper.myLooper() == Looper.getMainLooper()) { "WebView.evaluateJavascript called off main thread" }
            scripts += script
            val result = when {
                script.contains("const AsyncFunction =") -> {
                    scriptCourse = if (script.contains("修订课程")) "修订课程" else "验证课程"
                    "\"started\""
                }
                script.contains("_sleepdownEduTrial_") && script.contains("?.snapshot()") ->
                    JSONObject.quote(JSONObject().put("done", true).put("courses",
                        """[{"name":"$scriptCourse","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""").toString())
                script.contains("performance.getEntriesByType") ->
                    JSONObject.quote("""{"path":"/","textPreview":"虚构课程页面","resources":["/api/semester"],"forms":[]}""")
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
