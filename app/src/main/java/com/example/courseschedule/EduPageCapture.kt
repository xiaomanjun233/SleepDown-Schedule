package com.example.courseschedule

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.util.Base64
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

enum class EduPageCaptureMode {
    TEXT_ONLY,
    TEXT_PLUS_SCREENSHOT,
    SCREENSHOT_ONLY
}

data class EduPageCaptureResult(
    val text: String,
    val screenshots: List<RenderedPageImage>,
    val warnings: List<String>,
    val mode: EduPageCaptureMode
) {
    val diagnosticsText: String = buildString {
        appendLine("抓取模式：$mode")
        appendLine("DOM 文本：${text.length} 字符")
        appendLine("截图数量：${screenshots.size}")
        if (warnings.isNotEmpty()) {
            appendLine("诊断：")
            warnings.forEach { appendLine("- $it") }
        }
    }.trim()
}

private val EduPageDeepExtractScript = """
(function () {
  var seen = [];
  function pushUnique(list, value) {
    value = (value || "").replace(/\s+/g, " ").trim();
    if (!value || value.length < 2) return;
    var key = value.slice(0, 500);
    if (seen.indexOf(key) >= 0) return;
    seen.push(key);
    list.push(value);
  }
  function textOf(node) {
    if (!node) return "";
    return (node.innerText || node.textContent || "").replace(/\s+/g, " ").trim();
  }
  function tableText(table, index) {
    var rows = Array.prototype.slice.call(table.querySelectorAll("tr")).slice(0, 180);
    var body = rows.map(function (row) {
      return Array.prototype.slice.call(row.querySelectorAll("th,td"))
        .map(textOf)
        .filter(Boolean)
        .join(" | ");
    }).filter(Boolean).join("\n");
    return body ? ("Table " + (index + 1) + "\n" + body) : "";
  }
  function collectShadowText(root, depth) {
    if (!root || depth > 3) return "";
    var parts = [];
    try {
      Array.prototype.slice.call(root.querySelectorAll("*")).slice(0, 500).forEach(function (node) {
        if (node.shadowRoot) {
          pushUnique(parts, textOf(node.shadowRoot));
          var nested = collectShadowText(node.shadowRoot, depth + 1);
          if (nested) pushUnique(parts, nested);
        }
      });
    } catch (e) {
      pushUnique(parts, "Shadow DOM read failed: " + (e && e.message ? e.message : e));
    }
    return parts.join("\n");
  }
  var tables = Array.prototype.slice.call(document.querySelectorAll("table"))
    .slice(0, 64)
    .map(tableText)
    .filter(Boolean)
    .join("\n\n");
  var containerSelectors = [
    "[class*='kb']", "[id*='kb']", "[class*='course']", "[id*='course']",
    "[class*='schedule']", "[id*='schedule']", "[class*='timetable']", "[id*='timetable']",
    "[class*='lesson']", "[id*='lesson']", "[class*='calendar']", "[id*='calendar']",
    ".el-table", ".ant-table", ".layui-table", ".ivu-table", "[role='grid']"
  ];
  var containers = [];
  containerSelectors.forEach(function (selector) {
    try {
      Array.prototype.slice.call(document.querySelectorAll(selector)).slice(0, 24).forEach(function (node) {
        pushUnique(containers, selector + "\n" + textOf(node).slice(0, 10000));
      });
    } catch (e) {}
  });
  var formState = [];
  Array.prototype.slice.call(document.querySelectorAll("select,input,textarea,button,[role='button']")).slice(0, 160).forEach(function (node, index) {
    var label = node.getAttribute("aria-label") || node.getAttribute("placeholder") || node.name || node.id || node.className || node.tagName;
    var value = "";
    if (node.tagName === "SELECT") {
      value = Array.prototype.slice.call(node.selectedOptions || []).map(function (option) { return option.text || option.value || ""; }).join(",");
    } else {
      value = node.value || textOf(node);
    }
    pushUnique(formState, (index + 1) + ". " + label + " = " + value);
  });
  var frameWarnings = [];
  var iframeText = [];
  Array.prototype.slice.call(document.querySelectorAll("iframe,frame")).slice(0, 12).forEach(function (frame, index) {
    try {
      var doc = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
      if (doc && doc.body) {
        pushUnique(iframeText, "Frame " + (index + 1) + "\n" + textOf(doc.body).slice(0, 14000));
      } else {
        frameWarnings.push("Frame " + (index + 1) + " empty or inaccessible");
      }
    } catch (e) {
      frameWarnings.push("Frame " + (index + 1) + " inaccessible: " + (e && e.message ? e.message : e));
    }
  });
  var bodyText = textOf(document.body).slice(0, 80000);
  var visualCount =
    document.querySelectorAll("canvas").length +
    document.querySelectorAll("svg").length +
    document.querySelectorAll("img").length;
  return JSON.stringify({
    title: document.title || "",
    url: location.href || "",
    scrollY: window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0,
    visualCount: visualCount,
    tables: tables,
    containers: containers.join("\n\n"),
    formState: formState.join("\n"),
    iframeText: iframeText.join("\n\n"),
    shadowText: collectShadowText(document, 0),
    text: bodyText,
    warnings: frameWarnings
  });
})()
""".trimIndent()

private fun eduPageScrollCaptureScript(fraction: Float): String = """
(function () {
  var fraction = ${fraction.coerceIn(0f, 1f)};
  function textOf(node) {
    if (!node) return "";
    return (node.innerText || node.textContent || "").replace(/\s+/g, " ").trim();
  }
  function visibleArea(el) {
    try {
      var rect = el.getBoundingClientRect();
      var width = Math.max(0, Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0));
      var height = Math.max(0, Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0));
      return width * height;
    } catch (e) {
      return 0;
    }
  }
  function score(el) {
    if (!el) return -1;
    var maxScroll = Math.max(0, (el.scrollHeight || 0) - (el.clientHeight || window.innerHeight || 1));
    if (maxScroll < 40) return -1;
    var text = textOf(el).slice(0, 5000);
    var signals = ["课表", "课程", "节次", "星期", "周一", "周二", "周三", "教师", "教室", "周次", "时间"];
    var signalScore = signals.reduce(function (sum, key) { return sum + (text.indexOf(key) >= 0 ? 1 : 0); }, 0);
    var area = visibleArea(el);
    var tagBoost = /table|grid|kb|course|schedule|timetable|lesson|calendar/i.test((el.className || "") + " " + (el.id || "")) ? 4 : 0;
    return maxScroll / 120 + signalScore * 10 + tagBoost + area / 40000;
  }
  function scheduleScore(el) {
    if (!el) return -1;
    var text = textOf(el).slice(0, 8000);
    var signals = ["课表", "课程", "节次", "星期", "周一", "周二", "周三", "周四", "周五", "教师", "教室", "周次", "时间"];
    var signalScore = signals.reduce(function (sum, key) { return sum + (text.indexOf(key) >= 0 ? 1 : 0); }, 0);
    var tagBoost = /table|grid|kb|course|schedule|timetable|lesson|calendar|el-table|ant-table|layui-table/i.test((el.className || "") + " " + (el.id || "") + " " + el.tagName) ? 8 : 0;
    return signalScore * 12 + tagBoost + visibleArea(el) / 50000;
  }
  function scrollParentOf(el) {
    var current = el;
    while (current && current !== document.body && current !== document.documentElement) {
      var maxScroll = Math.max(0, (current.scrollHeight || 0) - (current.clientHeight || 1));
      if (maxScroll >= 40) return current;
      current = current.parentElement;
    }
    return document.scrollingElement || document.documentElement || document.body;
  }
  var candidates = [document.scrollingElement, document.documentElement, document.body];
  try {
    candidates = candidates.concat(Array.prototype.slice.call(document.querySelectorAll("*")).slice(0, 1400));
  } catch (e) {}
  var bestSignal = null;
  var bestSignalScore = -1;
  candidates.forEach(function (el) {
    var current = scheduleScore(el);
    if (current > bestSignalScore) {
      bestSignal = el;
      bestSignalScore = current;
    }
  });
  var best = null;
  var bestScore = -1;
  candidates.forEach(function (el) {
    var current = score(el);
    if (current > bestScore) {
      best = el;
      bestScore = current;
    }
  });
  if (bestSignal && bestSignalScore >= 8) {
    best = scrollParentOf(bestSignal);
    try { bestSignal.scrollIntoView({ block: "center", inline: "nearest" }); } catch (e) {}
  }
  if (!best || bestScore < 0) {
    var doc = document.scrollingElement || document.documentElement || document.body;
    var docMax = Math.max(0, (doc.scrollHeight || 0) - window.innerHeight);
    window.scrollTo(0, Math.round(docMax * fraction));
    return JSON.stringify({ target: "document", maxScroll: docMax, fraction: fraction });
  }
  var maxScroll = Math.max(0, (best.scrollHeight || 0) - (best.clientHeight || window.innerHeight || 1));
  var nextTop = Math.round(maxScroll * fraction);
  if (best === document.scrollingElement || best === document.documentElement || best === document.body) {
    window.scrollTo(0, nextTop);
  } else {
    best.scrollTop = nextTop;
    try { best.dispatchEvent(new Event("scroll", { bubbles: true })); } catch (e) {}
  }
  return JSON.stringify({
    target: best.tagName + "#" + (best.id || "") + "." + (best.className || ""),
    maxScroll: maxScroll,
    scrollTop: best.scrollTop || window.scrollY || 0,
    fraction: fraction
  });
})()
""".trimIndent()

private val EduPageScrollTargetMetricsScript = """
(function () {
  function textOf(node) {
    if (!node) return "";
    return (node.innerText || node.textContent || "").replace(/\s+/g, " ").trim();
  }
  function visibleArea(el) {
    try {
      var rect = el.getBoundingClientRect();
      var width = Math.max(0, Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0));
      var height = Math.max(0, Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0));
      return width * height;
    } catch (e) {
      return 0;
    }
  }
  function scheduleScore(el) {
    if (!el) return -1;
    var text = textOf(el).slice(0, 8000);
    var signals = ["课表", "课程", "节次", "星期", "周一", "周二", "周三", "周四", "周五", "教师", "教室", "周次", "时间"];
    var signalScore = signals.reduce(function (sum, key) { return sum + (text.indexOf(key) >= 0 ? 1 : 0); }, 0);
    var tagBoost = /table|grid|kb|course|schedule|timetable|lesson|calendar|el-table|ant-table|layui-table/i.test((el.className || "") + " " + (el.id || "") + " " + el.tagName) ? 8 : 0;
    return signalScore * 12 + tagBoost + visibleArea(el) / 50000;
  }
  function scrollParentOf(el) {
    var current = el;
    while (current && current !== document.body && current !== document.documentElement) {
      var maxScroll = Math.max(0, (current.scrollHeight || 0) - (current.clientHeight || 1));
      if (maxScroll >= 40) return current;
      current = current.parentElement;
    }
    return document.scrollingElement || document.documentElement || document.body;
  }
  var candidates = [document.scrollingElement, document.documentElement, document.body];
  try {
    candidates = candidates.concat(Array.prototype.slice.call(document.querySelectorAll("*")).slice(0, 1400));
  } catch (e) {}
  var bestSignal = null;
  var bestSignalScore = -1;
  candidates.forEach(function (el) {
    var current = scheduleScore(el);
    if (current > bestSignalScore) {
      bestSignal = el;
      bestSignalScore = current;
    }
  });
  var target = bestSignal && bestSignalScore >= 8 ? scrollParentOf(bestSignal) : (document.scrollingElement || document.documentElement || document.body);
  var clientHeight = target === document.scrollingElement || target === document.documentElement || target === document.body ? window.innerHeight : (target.clientHeight || window.innerHeight);
  var maxScroll = Math.max(0, (target.scrollHeight || 0) - clientHeight);
  return JSON.stringify({
    target: target.tagName + "#" + (target.id || "") + "." + (target.className || ""),
    clientHeight: clientHeight,
    maxScroll: maxScroll
  });
})()
""".trimIndent()

suspend fun captureEduPage(
    webView: WebView,
    maxScreenshots: Int = 6,
    allowScreenshotFallback: Boolean = true,
    forceScreenshots: Boolean = false,
    onScreenshotProgress: ((index: Int, total: Int) -> Unit)? = null
): EduPageCaptureResult {
    return withContext(Dispatchers.Main.immediate) {
        waitForWebViewStable(webView)
        val initialY = webView.scrollY
        val viewportHeight = webView.height.coerceAtLeast(1)
        val contentHeight = (webView.contentHeight * webView.scale).toInt().coerceAtLeast(viewportHeight)
        val positions = scrollSamplePositions(initialY, viewportHeight, contentHeight, maxScreenshots)
        val snapshots = mutableListOf<EduPageSnapshot>()

        for (position in positions) {
            webView.scrollTo(webView.scrollX, position)
            delay(160)
            evaluateWebViewJson(webView, EduPageDeepExtractScript)
                ?.let { decodeEduPageSnapshot(it) }
                ?.let { snapshots += it }
        }
        webView.scrollTo(webView.scrollX, initialY)
        delay(80)

        val text = snapshots.joinToString("\n\n") { it.toTextBlock() }
            .replace(Regex("\n{3,}"), "\n\n")
            .take(80_000)
        val warnings = snapshots.flatMap { it.warnings }.distinct().toMutableList()
        val needsScreenshot = forceScreenshots || (allowScreenshotFallback && shouldUseScreenshotFallback(text, snapshots))
        if (forceScreenshots) {
            warnings += "用户选择识屏模式，已强制生成当前 WebView 页面截图。"
        }
        val screenshots = if (needsScreenshot) {
            val captured = mutableListOf<RenderedPageImage>()
            val metrics = if (forceScreenshots) {
                evaluateWebViewJson(webView, EduPageScrollTargetMetricsScript)
                    ?.let { decodeEduPageScrollMetrics(it) }
            } else {
                null
            }
            val screenshotCount = if (forceScreenshots) {
                0
            } else {
                positions.size.coerceIn(1, maxScreenshots.coerceIn(1, 6))
            }
            val fractions = if (forceScreenshots) {
                val rollingFractions = rollingScreenshotFractions(metrics, viewportHeight)
                warnings += "识屏将按小步重叠滚动截取 ${rollingFractions.size} 张：${metrics?.summary().orEmpty()}"
                rollingFractions
            } else if (screenshotCount <= 1) {
                listOf(0f)
            } else {
                (0 until screenshotCount).map { index -> index.toFloat() / (screenshotCount - 1).toFloat() }
            }
            var zoomOutCount = 0
            if (forceScreenshots) {
                repeat(10) {
                    val changed = runCatching { webView.zoomOut() }.getOrDefault(false)
                    if (changed) {
                        zoomOutCount += 1
                    }
                }
            }
            if (zoomOutCount > 0) {
                warnings += "识屏前已临时缩小网页到最小比例（zoomOut $zoomOutCount 次）"
                delay(520)
            }
            try {
                fractions.forEachIndexed { index, fraction ->
                    onScreenshotProgress?.invoke(index + 1, fractions.size)
                    evaluateWebViewJson(webView, eduPageScrollCaptureScript(fraction))?.let { info ->
                        if (index == 0 || index == fractions.lastIndex) {
                            warnings += "识屏滚动段 ${index + 1}/${fractions.size}：$info"
                        }
                    }
                    waitForWebViewRender(webView)
                    captureVisibleWebViewBitmap(webView, index)?.let { captured += it }
                    delay(140)
                }
            } finally {
                if (zoomOutCount > 0) {
                    repeat(zoomOutCount) {
                        runCatching { webView.zoomIn() }
                    }
                    delay(260)
                }
            }
            webView.scrollTo(webView.scrollX, initialY)
            stitchRenderedImages(captured)?.let { listOf(it) } ?: captured
        } else {
            emptyList()
        }
        if (needsScreenshot && screenshots.isEmpty()) {
            warnings += "页面文本不足或疑似图片课表，但截图生成失败。"
        }
        val mode = when {
            screenshots.isEmpty() -> EduPageCaptureMode.TEXT_ONLY
            text.count { !it.isWhitespace() } < 80 -> EduPageCaptureMode.SCREENSHOT_ONLY
            else -> EduPageCaptureMode.TEXT_PLUS_SCREENSHOT
        }
        EduPageCaptureResult(text = text, screenshots = screenshots, warnings = warnings.distinct(), mode = mode)
    }
}

fun shouldUseScreenshotFallback(text: String, snapshots: List<EduPageSnapshot>): Boolean {
    val compactLength = text.count { !it.isWhitespace() }
    val scheduleSignals = listOf("课表", "课程", "节次", "星期", "周一", "周二", "教师", "教室", "上课", "周次")
        .count { text.contains(it) }
    val hasFrameWarning = snapshots.any { snapshot -> snapshot.warnings.any { it.contains("inaccessible", ignoreCase = true) } }
    val hasVisualHeavyPage = snapshots.maxOfOrNull { it.visualCount }?.let { it >= 6 } == true
    return compactLength < 220 || scheduleSignals < 2 || hasFrameWarning || hasVisualHeavyPage
}

private suspend fun waitForWebViewStable(webView: WebView) {
    repeat(8) {
        val ready = evaluateWebViewJson(webView, "document.readyState")?.trim('"') == "complete"
        if (ready) {
            delay(260)
            return
        }
        delay(180)
    }
}

private fun scrollSamplePositions(initialY: Int, viewportHeight: Int, contentHeight: Int, maxCount: Int): List<Int> {
    if (contentHeight <= viewportHeight * 3 / 2) return listOf(initialY.coerceAtLeast(0))
    val maxY = (contentHeight - viewportHeight).coerceAtLeast(0)
    val count = maxCount.coerceIn(1, 6)
    val step = (maxY / (count - 1).coerceAtLeast(1)).coerceAtLeast(1)
    return (0 until count).map { (it * step).coerceIn(0, maxY) }.distinct()
}

private suspend fun evaluateWebViewJson(webView: WebView, script: String): String? {
    return suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { value ->
            if (continuation.isActive) continuation.resume(value)
        }
    }
}

private fun decodeEduPageSnapshot(encoded: String?): EduPageSnapshot? {
    return runCatching {
        val decoded = JSONArray("[$encoded]").getString(0)
        val snapshot = JSONObject(decoded)
        EduPageSnapshot(
            title = snapshot.optString("title"),
            url = snapshot.optString("url"),
            scrollY = snapshot.optInt("scrollY"),
            visualCount = snapshot.optInt("visualCount"),
            tables = snapshot.optString("tables"),
            containers = snapshot.optString("containers"),
            formState = snapshot.optString("formState"),
            iframeText = snapshot.optString("iframeText"),
            shadowText = snapshot.optString("shadowText"),
            text = snapshot.optString("text"),
            warnings = snapshot.optJSONArray("warnings")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
            }.orEmpty()
        )
    }.getOrNull()
}

private fun decodeEduPageScrollMetrics(encoded: String?): EduPageScrollMetrics? {
    return runCatching {
        val decoded = JSONArray("[$encoded]").getString(0)
        val metrics = JSONObject(decoded)
        EduPageScrollMetrics(
            target = metrics.optString("target"),
            clientHeight = metrics.optInt("clientHeight"),
            maxScroll = metrics.optInt("maxScroll")
        )
    }.getOrNull()
}

private fun rollingScreenshotFractions(metrics: EduPageScrollMetrics?, fallbackViewportHeight: Int): List<Float> {
    val viewport = (metrics?.clientHeight ?: fallbackViewportHeight).coerceAtLeast(1)
    val maxScroll = (metrics?.maxScroll ?: 0).coerceAtLeast(0)
    if (maxScroll <= viewport / 3) return listOf(0f)
    val stepPx = (viewport * 0.52f).toInt().coerceAtLeast(1)
    val positions = buildList {
        var current = 0
        while (current < maxScroll) {
            add(current)
            current += stepPx
        }
        add(maxScroll)
    }.distinct()
    return positions
        .take(10)
        .let { limited ->
            if (limited.lastOrNull() == maxScroll) limited else limited.dropLast(1) + maxScroll
        }
        .map { it.toFloat() / maxScroll.toFloat() }
}

private data class EduPageScrollMetrics(
    val target: String,
    val clientHeight: Int,
    val maxScroll: Int
) {
    fun summary(): String = "滚动容器=${target.take(80)}，可滚动高度=$maxScroll，视口高度=$clientHeight"
}

data class EduPageSnapshot(
    val title: String,
    val url: String,
    val scrollY: Int,
    val visualCount: Int,
    val tables: String,
    val containers: String,
    val formState: String,
    val iframeText: String,
    val shadowText: String,
    val text: String,
    val warnings: List<String>
) {
    fun toTextBlock(): String = buildString {
        appendLine("ScrollY: $scrollY")
        if (title.isNotBlank()) appendLine("页面标题：$title")
        if (url.isNotBlank()) appendLine("页面地址：$url")
        if (visualCount > 0) appendLine("Visual elements: $visualCount")
        if (tables.isNotBlank()) {
            appendLine("页面表格：")
            appendLine(tables)
        }
        if (containers.isNotBlank()) {
            appendLine("Page schedule-like containers:")
            appendLine(containers)
        }
        if (formState.isNotBlank()) {
            appendLine("Page form state:")
            appendLine(formState)
        }
        if (iframeText.isNotBlank()) {
            appendLine("Page frames:")
            appendLine(iframeText)
        }
        if (shadowText.isNotBlank()) {
            appendLine("Page shadow DOM:")
            appendLine(shadowText)
        }
        if (warnings.isNotEmpty()) {
            appendLine("Page warnings:")
            warnings.forEach { appendLine("- $it") }
        }
        if (text.isNotBlank()) {
            appendLine("页面正文：")
            appendLine(text)
        }
    }.trim()
}

private suspend fun waitForWebViewRender(webView: WebView) {
    webView.invalidate()
    awaitNextWebViewFrame(webView)
    delay(760)
    awaitNextWebViewFrame(webView)
}

private suspend fun awaitNextWebViewFrame(webView: WebView) {
    suspendCancellableCoroutine<Unit> { continuation ->
        webView.postOnAnimation {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

private suspend fun captureVisibleWebViewBitmap(webView: WebView, pageIndex: Int): RenderedPageImage? {
    val width = webView.width
    val height = webView.height
    if (width <= 0 || height <= 0) return null
    val maxSide = 1600f
    val scale = minOf(1f, maxSide / maxOf(width, height).toFloat())
    val bitmap = captureWebViewPixels(webView)
        ?: captureWebViewByDraw(webView)
        ?: return null
    return withContext(Dispatchers.Default) {
        val outputBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        ByteArrayOutputStream().use { output ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            if (outputBitmap !== bitmap) outputBitmap.recycle()
            bitmap.recycle()
            RenderedPageImage(
                pageIndex = pageIndex,
                mimeType = "image/jpeg",
                base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            )
        }
    }
}

private suspend fun captureWebViewPixels(webView: WebView): Bitmap? {
    val activity = webView.context.findActivity() ?: return null
    val window = activity.window ?: return null
    val location = IntArray(2)
    webView.getLocationInWindow(location)
    val sourceRect = Rect(
        location[0],
        location[1],
        location[0] + webView.width,
        location[1] + webView.height
    )
    if (sourceRect.width() <= 0 || sourceRect.height() <= 0) return null
    val bitmap = Bitmap.createBitmap(sourceRect.width(), sourceRect.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(
                window,
                sourceRect,
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        bitmap.recycle()
                    } else if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }.onFailure {
            bitmap.recycle()
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

private fun captureWebViewByDraw(webView: WebView): Bitmap? {
    val width = webView.width
    val height = webView.height
    if (width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.draw(canvas)
    return bitmap
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun stitchRenderedImages(images: List<RenderedPageImage>): RenderedPageImage? {
    if (images.size <= 1) return null
    return withContext(Dispatchers.Default) {
        val bitmaps = images.mapNotNull { image ->
            runCatching {
                val bytes = Base64.decode(image.base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        if (bitmaps.isEmpty()) return@withContext null
        try {
            val sourceWidth = bitmaps.maxOf { it.width }.coerceAtMost(1200)
            val rawHeights = bitmaps.map { bitmap ->
                (bitmap.height * (sourceWidth / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
            }
            val totalHeight = rawHeights.sum().coerceAtLeast(1)
            val longScale = minOf(1f, 7200f / totalHeight.toFloat())
            val targetWidth = (sourceWidth * longScale).toInt().coerceAtLeast(1)
            val targetHeights = rawHeights.map { (it * longScale).toInt().coerceAtLeast(1) }
            val stitched = Bitmap.createBitmap(targetWidth, targetHeights.sum(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            var top = 0
            bitmaps.forEachIndexed { index, bitmap ->
                val height = targetHeights[index]
                canvas.drawBitmap(bitmap, null, Rect(0, top, targetWidth, top + height), null)
                top += height
            }
            ByteArrayOutputStream().use { output ->
                stitched.compress(Bitmap.CompressFormat.JPEG, 82, output)
                stitched.recycle()
                RenderedPageImage(
                    pageIndex = 0,
                    mimeType = "image/jpeg",
                    base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                )
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }
}
