package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val ShiguangBridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal val ShiguangPayloadJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

@Serializable
internal data class ShiguangBridgeMessage(
    @SerialName("action") val action: String,
    @SerialName("callbackId") val callbackId: String? = null,
    @SerialName("payload") val payload: String? = null
)

@Serializable
internal data class ShiguangShowToastPayload(val message: String)

@Serializable
internal data class ShiguangShowAlertPayload(
    val titleText: String,
    val contentText: String,
    val confirmText: String? = null
)

@Serializable
internal data class ShiguangShowPromptPayload(
    val titleText: String,
    val tipText: String,
    val defaultText: String = "",
    val validatorJsFunction: String? = null
)

@Serializable
internal data class ShiguangShowSingleSelectionPayload(
    val titleText: String,
    val itemsJsonString: String,
    val defaultSelectedIndex: Int = -1
)

@Serializable
internal data class ShiguangSaveCoursesPayload(val coursesJsonString: String)

@Serializable
internal data class ShiguangSaveConfigPayload(val configJsonString: String)

@Serializable
internal data class ShiguangSaveTimeSlotsPayload(val timeSlotsJsonString: String)

internal fun buildShiguangJsCallbackScript(
    callbackId: String,
    success: Boolean,
    resultRawJs: String
): String = "window._shiguangNativeCallback('$callbackId', $success, $resultRawJs);"

internal val ShiguangBridgeInitScript = """
(function() {
    if (window._shiguangBridgeInjected) return;
    window._shiguangBridgeInjected = true;

    var callbacks = {};
    var callbackCounter = 0;

    function postRawMessage(msg) {
        if (window._shiguangNativeBridge && typeof window._shiguangNativeBridge.postMessage === 'function') {
            window._shiguangNativeBridge.postMessage(msg);
            return;
        }
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.shiguangBridge) {
            window.webkit.messageHandlers.shiguangBridge.postMessage(msg);
            return;
        }
        if (typeof window.cefQuery === 'function') {
            window.cefQuery({ request: msg });
            return;
        }
        throw new Error('网页与 App 的连接不可用，请刷新页面后重试');
    }

    function postMessageToNative(action, payload, callbackId) {
        var msg = JSON.stringify({
            action: action,
            callbackId: callbackId || null,
            payload: payload ? JSON.stringify(payload) : null
        });
        var cb = callbackId && callbacks[callbackId];
        if (cb) {
            cb.timer = setTimeout(function() {
                delete callbacks[callbackId];
                cb.reject(new Error('等待 App 响应超时，请重新导入'));
            }, action.indexOf('show') === 0 ? 300000 : 30000);
        }
        try { postRawMessage(msg); }
        catch (error) {
            if (!cb) throw error;
            clearTimeout(cb.timer);
            delete callbacks[callbackId];
            cb.reject(error);
        }
    }

    window._shiguangNativeCallback = function(callbackId, isSuccess, result) {
        var cb = callbacks[callbackId];
        if (cb) {
            clearTimeout(cb.timer);
            if (isSuccess) cb.resolve(result);
            else cb.reject(new Error(result && result.message ? result.message : String(result)));
            delete callbacks[callbackId];
        }
    };

    var shiguangBridgePromise = {
        showAlert: function(titleText, contentText, confirmText) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showAlert', {
                    titleText: titleText || '',
                    contentText: contentText || '',
                    confirmText: confirmText || null
                }, id);
            });
        },
        showPrompt: function(titleText, tipText, defaultText, validatorJsFunction) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showPrompt', {
                    titleText: titleText || '',
                    tipText: tipText || '',
                    defaultText: defaultText || '',
                    validatorJsFunction: validatorJsFunction || ''
                }, id);
            });
        },
        showSingleSelection: function(titleText, items, defaultSelectedIndex) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                var itemsJson = (typeof items === 'string') ? items : JSON.stringify(items || []);
                postMessageToNative('showSingleSelection', {
                    titleText: titleText || '',
                    itemsJsonString: itemsJson,
                    defaultSelectedIndex: defaultSelectedIndex !== undefined ? defaultSelectedIndex : -1
                }, id);
            });
        },
        saveImportedCourses: function(coursesJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveImportedCourses', { coursesJsonString: coursesJsonString }, id);
            });
        },
        saveCourseConfig: function(configJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveCourseConfig', { configJsonString: configJsonString }, id);
            });
        },
        savePresetTimeSlots: function(timeSlotsJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('savePresetTimeSlots', { timeSlotsJsonString: timeSlotsJsonString }, id);
            });
        }
    };

    var shiguangBridge = {
        isAvailable: function() {
            return !!(window._shiguangNativeBridge && typeof window._shiguangNativeBridge.postMessage === 'function');
        },
        showToast: function(message) {
            postMessageToNative('showToast', { message: message });
        },
        notifyTaskCompletion: function() {
            postMessageToNative('notifyTaskCompletion');
        },
        notifyExecutionFinished: function() {
            postMessageToNative('notifyExecutionFinished');
        }
    };

    window.shiguangBridgePromise = shiguangBridgePromise;
    window.shiguangBridge = shiguangBridge;
    window.AndroidBridgePromise = shiguangBridgePromise;
    window.AndroidBridge = shiguangBridge;
})();
""".trimIndent()
