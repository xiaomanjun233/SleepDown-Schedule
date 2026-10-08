package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import org.json.JSONObject

/** Direct eval has a fresh function scope and preserves the adapter's final async expression. */
internal fun isolatedShiguangScript(source: String): String = """
(function() {
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
    Promise.resolve().then(function() {
        return eval(${JSONObject.quote(source)});
    }).catch(report).finally(function() {
        bridge.notifyExecutionFinished();
    });
    return 'started';
})();
""".trimIndent()
