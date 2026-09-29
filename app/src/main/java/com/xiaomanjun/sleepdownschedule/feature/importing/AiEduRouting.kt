package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.*

/** Only reviewed, shipped generic adapters can be selected by a model. */
internal enum class AiEduTool(val folder: String, val adapterId: String, val label: String) {
    ZHENGFANG("zhengfang_jiaowu", "zhengfang_01", "正方"),
    QINGGUO("qingguo_jiaowu", "qingguo_01", "青果"),
    URP("urp_jiaowu", "urp_01", "URP"),
    CHAOXING("chaoxing_jiaowu", "chaoxing", "超星");

    fun findAdapter(adapters: List<EduAdapter>): EduAdapter? = adapters.firstOrNull {
        it.isGeneralEduTool() && it.school.folder == folder && it.adapterId == adapterId
    }
}

internal data class AiEduCandidate(
    val tool: AiEduTool,
    val framePath: List<Int>,
    val documentKey: String,
    val strong: Boolean
)

internal data class AiEduFingerprint(val candidates: List<AiEduCandidate>) {
    val localMatch: AiEduCandidate? get() = candidates.singleOrNull()?.takeIf { it.strong }

    // Page URLs, script source, form values and document keys stay on the device.
    fun modelInput(): String = buildJsonObject {
        put("candidates", buildJsonArray {
            candidates.forEachIndexed { index, candidate ->
                add(buildJsonObject {
                    put("candidate", index)
                    put("system", candidate.tool.label)
                    put("adapter", candidate.tool.adapterId)
                    put("frameDepth", candidate.framePath.size)
                    put("parserStructurePresent", true)
                    put("vendorEvidencePresent", candidate.strong)
                })
            }
        })
    }.toString()
}

internal fun parseAiEduFingerprint(encoded: String): AiEduFingerprint {
    val decoded = Json.parseToJsonElement(encoded).jsonPrimitive.content
    require(decoded.length <= 16_384) { "教务页面特征过大" }
    val root = Json.parseToJsonElement(decoded).jsonObject
    val candidates = root.getValue("candidates").jsonArray
    require(candidates.size <= 32)
    return AiEduFingerprint(candidates.map { value ->
        val item = value.jsonObject
        val path = item.getValue("framePath").jsonArray.map { it.jsonPrimitive.int }
        require(path.size <= 3 && path.all { it in 0..11 })
        val documentKey = item.getValue("documentKey").jsonPrimitive.content
        require(documentKey.matches(Regex("[a-zA-Z0-9.-]{1,80}")))
        AiEduCandidate(
            tool = AiEduTool.valueOf(item.getValue("tool").jsonPrimitive.content),
            framePath = path,
            documentKey = documentKey,
            strong = item.getValue("strong").jsonPrimitive.boolean
        )
    }.distinct())
}

/** Malformed, low-confidence and unknown selections all recommend the text preview. */
internal fun parseAiEduRoutingDecision(output: String, fingerprint: AiEduFingerprint): AiEduCandidate? =
    runCatching {
        require(output.length <= 4096)
        val text = output.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.keys == setOf("action", "candidate", "confidence"))
        if (root.getValue("action").jsonPrimitive.content != "adapter") return@runCatching null
        val confidence = root.getValue("confidence").jsonPrimitive.double
        require(confidence.isFinite() && confidence in 0.90..1.0)
        fingerprint.candidates.getOrNull(root.getValue("candidate").jsonPrimitive.int)
    }.getOrNull()

internal val AiEduRoutingPrompt = """
    你是教务导入路由选择器，只输出一个 JSON 对象，不解析课程，不生成脚本、网址或工具参数。
    输入由本机检查器生成。候选均已具备对应官方通用工具所需的页面结构；vendorEvidencePresent
    表示还有对应厂商的 JS/资源路径证据。多个候选或只有通用表格结构时不要猜测。
    只有能够明确选定时返回 {"action":"adapter","candidate":候选整数索引,"confidence":0.9到1}；
    否则返回 {"action":"text","candidate":null,"confidence":0}，建议用户核对课表文本。
""".trimIndent()

/** Read structural flags only. No storage, cookie, global-value dump or network requests. */
internal val AiEduFingerprintScript = """
(function () {
  var candidates = [], visited = 0;
  function visit(win, path) {
    if (++visited > 12 || path.length > 3) return;
    try {
      var doc = win.document;
      if (!win.__sleepdownEduDocumentKey) {
        win.__sleepdownEduDocumentKey = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
      }
      var key = win.__sleepdownEduDocumentKey;
      var password = Array.from(doc.querySelectorAll('input[type="password"]')).some(function (el) {
        return el.getClientRects().length > 0;
      });
      // Inspect known resource/route markers locally; never return the paths themselves.
      var resources = [win.location.pathname].concat(Array.from(doc.scripts).slice(0, 80).map(function (s) {
        try { return new URL(s.src, win.location.href).pathname; } catch (e) { return ''; }
      })).join(' ').toLowerCase();
      function add(tool, ready, strong) {
        if (!password && ready && candidates.length < 32) candidates.push({
          tool: tool, framePath: path, documentKey: key, strong: !!strong
        });
      }
      var view = doc.querySelector('#shcPDF');
      var table = view && doc.querySelector(view.getAttribute('data-type') === 'list' ? '#kblist_table' : '#kbgrid_table_0');
      add('ZHENGFANG', !!(view && table && win.jQuery), /jwglxt|xsxxxggl|kbcx|zfsoft/.test(resources));
      var qingguoTable = Array.from(doc.querySelectorAll('table')).slice(0, 60).some(function (t) {
        var text = (t.innerText || '').slice(0, 60000);
        return text.indexOf('星期一') >= 0 && /[0-9,-]+\[[0-9]+-[0-9]+\]/.test(text);
      });
      add('QINGGUO', qingguoTable, /qzsoft|qingguo|jsxsd|jwweb/.test(resources) && !/jsxsd/.test(resources));
      add('URP', !!doc.querySelector('td[id*="_"] .class_div p') && !!doc.querySelector('th[id^="0_"]'),
        /\/student\/course|\/student\/wsxk|urp/.test(resources));
      add('CHAOXING', !!doc.querySelector('#xhid') && !!doc.querySelector('#xqdm') && !!doc.querySelector('#xnxq1'),
        /\/admin\/xsd\/|chaoxing/.test(resources));
      for (var i = 0; i < Math.min(win.frames.length, 12); i++) visit(win.frames[i], path.concat(i));
    } catch (e) { /* Cross-origin frames require the existing text/visual fallback. */ }
  }
  visit(window, []);
  return JSON.stringify({ candidates: candidates });
})()
""".trimIndent()
