package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import android.annotation.SuppressLint
import android.webkit.WebView

internal const val EDU_BRIDGE_TEST_PAGE_URL =
    "file:///android_asset/shiguang_warehouse-main/resources/GLOBAL_TOOLS/test_page.html"

internal val EDU_BRIDGE_PROMISE_BOOTSTRAP = """
    (function () {
        var nativeBridge = window.AndroidBridge;
        var requestBridge = window.SleepDownBridgeRequests;
        if (!nativeBridge || !requestBridge) {
            throw new Error("SleepDown import bridge is unavailable");
        }
        var pending = Object.create(null);
        var nextRequestId = 0;
        function nullableString(value) {
            return value == null ? null : String(value);
        }
        function enqueue(start) {
            return new Promise(function (resolve, reject) {
                var requestId = String(Date.now()) + "-" + String(++nextRequestId);
                pending[requestId] = { resolve: resolve, reject: reject };
                try {
                    start(requestId);
                } catch (error) {
                    delete pending[requestId];
                    reject(error);
                }
            });
        }
        window.__sleepDownBridgeResolve = function (requestId, value) {
            var entry = pending[String(requestId)];
            if (!entry) return false;
            delete pending[String(requestId)];
            entry.resolve(value);
            return true;
        };
        window.__sleepDownBridgeReject = function (requestId, message) {
            var entry = pending[String(requestId)];
            if (!entry) return false;
            delete pending[String(requestId)];
            entry.reject(new Error(message || "Import interaction cancelled"));
            return true;
        };
        window.AndroidBridgePromise = {
            saveCourseConfig: function (json) {
                return Promise.resolve(nativeBridge.saveCourseConfig(json));
            },
            saveImportedCourses: function (json) {
                return Promise.resolve(nativeBridge.saveImportedCourses(json));
            },
            savePresetTimeSlots: function (json) {
                return Promise.resolve(nativeBridge.savePresetTimeSlots(json));
            },
            showAlert: function (title, message, confirmText) {
                return enqueue(function (requestId) {
                    requestBridge.requestAlert(
                        requestId,
                        nullableString(title),
                        nullableString(message),
                        nullableString(confirmText)
                    );
                });
            },
            showPrompt: function (title, message, defaultValue, validator) {
                return enqueue(function (requestId) {
                    requestBridge.requestPrompt(
                        requestId,
                        nullableString(title),
                        nullableString(message),
                        nullableString(defaultValue),
                        nullableString(validator)
                    );
                });
            },
            showSingleSelection: function (title, optionsJson, defaultIndex) {
                return enqueue(function (requestId) {
                    requestBridge.requestSingleSelection(
                        requestId,
                        nullableString(title),
                        nullableString(optionsJson),
                        Number.isInteger(defaultIndex) ? defaultIndex : -1
                    );
                });
            }
        };
        window.shiguangBridge = nativeBridge;
        window.shiguangBridgePromise = window.AndroidBridgePromise;
        return true;
    })();
""".trimIndent()

internal fun normalizeEduUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isBlank() ||
            trimmed.equals("http://", ignoreCase = true) ||
            trimmed.equals("https://", ignoreCase = true) -> ""
        trimmed.equals(EDU_BRIDGE_TEST_PAGE_URL, ignoreCase = true) -> EDU_BRIDGE_TEST_PAGE_URL
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        ExplicitUriScheme.containsMatchIn(trimmed) -> ""
        else -> "https://$trimmed"
    }
}

private val ExplicitUriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureEduImportSecurity(adapter: EduAdapter) {
    settings.allowContentAccess = false
    settings.allowFileAccess = adapter.isEduTestTool()
}

@SuppressLint("JavascriptInterface")
internal fun WebView.attachEduImportBridge(bridge: EduImportBridge) {
    detachEduImportBridge()
    addJavascriptInterface(bridge, "AndroidBridge")
    addJavascriptInterface(bridge, "SleepDownBridgeRequests")
}

internal fun WebView.detachEduImportBridge() {
    removeJavascriptInterface("AndroidBridgePromise")
    removeJavascriptInterface("AndroidBridge")
    removeJavascriptInterface("SleepDownBridgeRequests")
}
