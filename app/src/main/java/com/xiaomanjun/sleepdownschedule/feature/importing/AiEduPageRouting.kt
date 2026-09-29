package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.webkit.WebView
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.injectShiguangRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

internal suspend fun captureAiEduFingerprint(webView: WebView): AiEduFingerprint =
    withContext(Dispatchers.Main.immediate) {
        parseAiEduFingerprint(evaluateAiEduScript(webView, AiEduFingerprintScript))
    }

private suspend fun evaluateAiEduScript(webView: WebView, script: String): String = withTimeout(5_000) {
    suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result ?: "null")
        }
    }
}

internal suspend fun executeAiEduAdapter(
    context: Context,
    webView: WebView,
    bridge: ShiguangBridgeHost,
    candidate: AiEduCandidate,
    adapter: EduAdapter,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    desktopMode: Boolean
): Boolean {
    require(candidate.tool.findAdapter(listOf(adapter)) != null)
    val source = ShiguangWarehouse.resolveScript(context, adapter)
    return withContext(Dispatchers.Main.immediate) {
        // A recommendation must never execute on a different document or frame after navigation.
        val fresh = captureAiEduFingerprint(webView)
        if (fresh.candidates.none { it.tool == candidate.tool && it.framePath == candidate.framePath &&
                it.documentKey == candidate.documentKey }) return@withContext false
        bridge.bindWebView(webView)
        bridge.beginTask(config, periods, allowImportedBellTimes = true)
        webView.injectShiguangRuntime(desktopMode)
        val wrapped = "(async function() {\n$source\n})().catch(function() { window.shiguangBridge.showToast('通用教务导入失败，可再次点击 AI 导入改用页面文本。'); });"
        val invocation = if (candidate.framePath.isEmpty()) wrapped else
            "target.eval(${JSONObject.quote(wrapped)});"
        val script = """
            (function() {
              try {
                var target = window;
                var path = ${candidate.framePath};
                for (var i = 0; i < path.length; i++) target = target.frames[path[i]];
                if (target.__sleepdownEduDocumentKey !== ${JSONObject.quote(candidate.documentKey)}) return false;
                // All promises and native callbacks remain in the already-installed top bridge.
                target.shiguangBridge = window.shiguangBridge;
                target.shiguangBridgePromise = window.shiguangBridgePromise;
                $invocation
                return true;
              } catch (e) { return false; }
            })()
        """.trimIndent()
        evaluateAiEduScript(webView, script) == "true"
    }
}
