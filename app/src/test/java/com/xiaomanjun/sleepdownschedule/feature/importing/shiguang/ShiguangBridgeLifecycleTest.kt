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
}
