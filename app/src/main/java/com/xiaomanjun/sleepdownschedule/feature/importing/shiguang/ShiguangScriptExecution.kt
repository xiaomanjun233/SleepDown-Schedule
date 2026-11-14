package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import org.json.JSONObject

/** Direct eval has a fresh function scope and preserves the adapter's final async expression. */
internal fun isolatedShiguangScript(source: String, executionToken: String? = null): String = """
(function(window) {
    var actualWindow = window;
    var token = ${JSONObject.quote(executionToken)};
    if (token) {
        if (typeof actualWindow._shiguangWithExecutionToken !== 'function') return 'bridge_unavailable';
        var scopedBridge = function(target) {
            return new Proxy(target, { get: function(object, key) {
                var value = object[key];
                if (typeof value !== 'function' || String(key).charAt(0) === '_') return value;
                return function() {
                    var args = arguments;
                    return actualWindow._shiguangWithExecutionToken(token, function() { return value.apply(object, args); });
                };
            } });
        };
        var promiseBridge = scopedBridge(actualWindow.shiguangBridgePromise);
        var syncBridge = scopedBridge(actualWindow.shiguangBridge);
        window = new Proxy(actualWindow, { get: function(object, key) {
            if (key === 'shiguangBridgePromise' || key === 'AndroidBridgePromise') return promiseBridge;
            if (key === 'shiguangBridge' || key === 'AndroidBridge') return syncBridge;
            if (key === 'window' || key === 'self' || key === 'globalThis') return window;
            var value = Reflect.get(object, key, object);
            return typeof value === 'function' ? value.bind(object) : value;
        } });
    }
    var shiguangBridgePromise = window.shiguangBridgePromise;
    var shiguangBridge = window.shiguangBridge;
    var AndroidBridgePromise = shiguangBridgePromise;
    var AndroidBridge = shiguangBridge;
    var globalThis = window;
    var self = window;
    if (!window.shiguangBridge || !window.shiguangBridge.isAvailable()) return 'bridge_unavailable';
    var bridge = window.shiguangBridge;
    var nativeFetch = window.fetch.bind(window);
    var fetch = function(input, options) {
        var controller = new AbortController();
        var originalSignal = options && options.signal;
        var abort = function() { controller.abort(); };
        if (originalSignal) {
            if (originalSignal.aborted) abort();
            else originalSignal.addEventListener('abort', abort, { once: true });
        }
        var timer = setTimeout(abort, 30000);
        return nativeFetch(input, Object.assign({}, options || {}, { signal: controller.signal }))
            .then(function(response) {
                // Include body transfer in the deadline without changing Response metadata or HTTP semantics.
                return response.clone().arrayBuffer().then(function() { return response; });
            }).catch(function(error) {
                if (controller.signal.aborted) throw new Error('教务请求已取消或超过 30 秒，请检查登录状态和校园网络');
                throw error;
            }).finally(function() {
                clearTimeout(timer);
                if (originalSignal) originalSignal.removeEventListener('abort', abort);
            });
    };
    var report = function(error) {
        var detail = error && error.message ? error.message : String(error || '未知错误');
        bridge.showToast('教务导入失败：' + detail);
    };
    var validateInput;
    var cleanup = function() {
        if (bridge._evaluateAdapterInput === validateInput) delete bridge._evaluateAdapterInput;
        if (bridge._cleanupAdapterExecution === cleanup) delete bridge._cleanupAdapterExecution;
    };
    Promise.resolve().then(function() {
        // Keep named callbacks in the adapter scope, including adapters run inside an iframe.
        validateInput = function(name, input) { return eval(name + '(input)'); };
        bridge._evaluateAdapterInput = validateInput;
        bridge._cleanupAdapterExecution = cleanup;
        var execution = eval(${JSONObject.quote(source)});
        // Many official adapters start an async flow inside a synchronous IIFE and return
        // undefined. Only an actual returned Promise can tell us when execution is finished;
        // detached flows end through the bridge's explicit completion notification.
        if (execution && typeof execution.then === 'function') return Promise.resolve(execution)
            .then(function() {
                if (bridge._cleanupAdapterExecution === cleanup) bridge.notifyExecutionFinished();
                else cleanup();
            });
    }).catch(function(error) {
        report(error);
        if (bridge._cleanupAdapterExecution === cleanup) bridge.notifyExecutionFinished();
        else cleanup();
    });
    return 'started';
})(window);
""".trimIndent()
