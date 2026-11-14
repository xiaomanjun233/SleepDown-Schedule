package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.app.Application
import android.os.Looper
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ShiguangBridgeLifecycleTest {
    @Test fun oldScriptWireTokenCannotStageCoursesOrFinishANewerCapture() {
        var delivered: com.xiaomanjun.sleepdownschedule.model.ImportDraft? = null
        val host = ShiguangBridgeHost({}, { fail("Stale task leaked a message") }, {})
        host.beginTask(defaultConfig(), defaultPeriods(), capture = { delivered = it.getOrThrow() }, executionToken = "new")
        fun tagged(action: String, token: String, name: String = "") = JSONObject(message(action,
            if (action == "saveImportedCourses") JSONObject().put("coursesJsonString",
                """[{"name":"$name","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""").toString() else null))
            .put("executionToken", token).toString()
        host.onMessageReceived(tagged("saveImportedCourses", "old", "过期课程"))
        host.onMessageReceived(tagged("notifyTaskCompletion", "old"))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(delivered)
        assertTrue(host.running.value)
        host.onMessageReceived(tagged("saveImportedCourses", "new", "当前课程"))
        host.onMessageReceived(tagged("notifyTaskCompletion", "new"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("当前课程", delivered!!.courses.single().name)
        host.close()
    }
    @Test fun agentCaptureReturnsValidatedDraftWithoutTriggeringNormalImportAndRoutesQuestions() {
        var normalDrafts = 0
        var result: Result<com.xiaomanjun.sleepdownschedule.model.ImportDraft>? = null
        val questions = mutableListOf<com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest>()
        val host = ShiguangBridgeHost({ normalDrafts++ }, {}, { fail("Question escaped AI host") })
        host.beginTask(defaultConfig(), defaultPeriods(), capture = { result = it }, captureInteraction = questions::add)
        host.onMessageReceived(JSONObject().put("action", "showAlert").put("callbackId", "question")
            .put("payload", JSONObject().put("titleText", "核对").put("contentText", "请选择学期").toString()).toString())
        host.onMessageReceived(message("saveImportedCourses", JSONObject().put("coursesJsonString",
            """[{"name":"课程","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""").toString()))
        host.onMessageReceived(message("notifyTaskCompletion"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, questions.size)
        assertEquals("课程", result!!.getOrThrow().courses.single().name)
        assertEquals(0, normalDrafts)
        assertFalse(host.running.value)
        host.close()
    }

    @Test fun cancellingAnOldCaptureCannotCancelANewerTask() {
        var stopped = false
        val callback: (Result<com.xiaomanjun.sleepdownschedule.model.ImportDraft>) -> Unit = { stopped = it.isFailure }
        val host = ShiguangBridgeHost({}, {}, {})
        host.beginTask(defaultConfig(), defaultPeriods(), capture = callback)
        host.cancelCapture(callback)
        assertTrue(stopped)
        host.beginTask(defaultConfig(), defaultPeriods())
        host.cancelCapture(callback)
        assertTrue(host.running.value)
        host.close()
    }
    private fun message(action: String, payload: String? = null) = JSONObject().apply {
        put("action", action)
        if (payload != null) put("payload", payload)
        if (action.startsWith("save")) put("callbackId", "test-call")
    }.toString()

    private fun toast(text: String) = message("showToast", JSONObject().put("message", text).toString())

    @Test fun closeDropsQueuedAndFutureCallbacksAndIsIdempotent() {
        val messages = mutableListOf<String>()
        val host = ShiguangBridgeHost({}, messages::add, {})
        host.onMessageReceived(toast("old"))
        host.close()
        host.close()
        host.onMessageReceived(toast("late"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(messages.isEmpty())
        assertFalse(host.running.value)
        assertFalse(host.evaluateJavascript("void 0"))
        assertThrows(IllegalStateException::class.java) { host.beginTask(defaultConfig(), defaultPeriods()) }
    }

    @Test fun cancellationDoesNotLetAnOldCompletionReachTheNextTask() {
        var drafts = 0
        val host = ShiguangBridgeHost({ drafts++ }, {}, {})
        host.beginTask(defaultConfig(), defaultPeriods())
        val courses = """[{"name":"课程","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]"""
        host.onMessageReceived(message("saveImportedCourses", JSONObject().put("coursesJsonString", courses).toString()))
        host.onMessageReceived(message("notifyTaskCompletion"))
        host.finishExecution()
        host.beginTask(defaultConfig(2), defaultPeriods(2))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, drafts)
        assertTrue(host.running.value)
        host.close()
    }

    @Test fun queuedExecutionFinishCannotStopANewerTask() {
        val host = ShiguangBridgeHost({}, {}, {})
        host.beginTask(defaultConfig(), defaultPeriods())
        host.onMessageReceived(message("notifyExecutionFinished"))
        host.beginTask(defaultConfig(2), defaultPeriods(2))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.running.value)
        host.close()
    }

    @Test fun validCompletionStillDeliversOneDraftAndEndsTheTask() {
        var names = emptyList<String>()
        val messages = mutableListOf<String>()
        val host = ShiguangBridgeHost({ names = it.courses.map { course -> course.name } }, messages::add, {})
        host.beginTask(defaultConfig(), defaultPeriods())
        val courses = """[{"name":"课程","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}]"""
        host.onMessageReceived(message("saveImportedCourses", JSONObject().put("coursesJsonString", courses).toString()))
        host.onMessageReceived(message("notifyTaskCompletion"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("课程"), names)
        assertTrue(messages.isEmpty())
        assertFalse(host.running.value)
        host.close()
    }

    @Test fun detachedCancellationEndsBusyStateButOptionalWarningsDoNot() {
        var drafts = 0
        val host = ShiguangBridgeHost({ drafts++ }, {}, {})
        host.beginTask(defaultConfig(), defaultPeriods())
        host.onMessageReceived(toast("作息接口读取失败，仍可继续核对"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.running.value)
        host.onMessageReceived(toast("用户取消了导入。"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.running.value)
        assertEquals(0, drafts)
        host.close()
    }
}
