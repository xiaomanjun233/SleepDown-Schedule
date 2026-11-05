package com.xiaomanjun.sleepdownschedule.core.ai

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptSandbox
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

/** Pure transformations run out of process. There is no DOM, network, filesystem or Java bridge. */
internal object AgentJavaScript {
    fun available(): Boolean = runCatching { JavaScriptSandbox.isSupported() }.getOrDefault(false)

    @Synchronized fun execute(context: Context, source: String, inputJson: String): String {
        require(source.toByteArray().size <= 256 * 1024 && inputJson.toByteArray().size <= 120 * 1024) { "脚本或输入超过本地执行上限" }
        require(available()) { "当前系统 WebView 不支持隔离 JS 引擎，可继续使用材料读取与课表工具" }
        JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).get(8, TimeUnit.SECONDS).use { sandbox ->
            require(sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION) &&
                sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) { "当前引擎缺少脚本资源限制能力" }
            val parameters = IsolateStartupParameters().apply { setMaxHeapSizeBytes(32L * 1024 * 1024) }
            sandbox.createIsolate(parameters).use { isolate ->
                val code = """
                    (function() {
                      "use strict";
                      const input = JSON.parse(${JsonPrimitive(inputJson)});
                      $source
                      if (typeof main !== "function") throw new Error("请定义 main(input) 函数");
                      const output = main(input);
                      if (output && typeof output.then === "function") throw new Error("请使用同步 main；网络与文件通过 App 工具读取");
                      const result = JSON.stringify(output);
                      if (typeof result !== "string" || result.length > 60000) throw new Error("返回值需要可序列化且不超过 60000 字符");
                      return result;
                    })()
                """.trimIndent()
                return isolate.evaluateJavaScriptAsync(code).get(5, TimeUnit.SECONDS)
            }
        }
    }
}
