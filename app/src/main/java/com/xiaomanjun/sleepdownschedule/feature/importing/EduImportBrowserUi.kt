package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.provider.Settings
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@Composable
fun EduImportActivityScreen(
    state: AppState,
    adapter: EduAdapter,
    backdrop: Backdrop?,
    webContentBackdrop: LayerBackdrop,
    useDetailTopPadding: Boolean = true,
    onParsed: (ImportDraft) -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var bridgeInteraction by remember { mutableStateOf<EduBridgeInteractionRequest?>(null) }
    var currentUrl by remember(adapter) {
        mutableStateOf(
            if (adapter.requiresManualEduUrl()) "" else adapter.importUrl.ifBlank { "about:blank" }
        )
    }
    val bridge = remember(adapter) {
        ShiguangBridgeHost(
            context = context,
            onDraft = onParsed,
            onMessage = { message = it },
            onInteractionRequest = { bridgeInteraction = it }
        )
    }
    EduBridgeInteractionDialog(
        request = bridgeInteraction,
        bridge = bridge,
        state = state,
        backdrop = backdrop,
        onFinished = { bridgeInteraction = null }
    )
    EduImportBrowserScreen(
        state = state,
        adapter = adapter,
        webContentBackdrop = webContentBackdrop,
        message = message,
        webView = webView,
        onWebView = { webView = it },
        currentUrl = currentUrl,
        onUrlChange = { currentUrl = it },
        bridge = bridge,
        useDetailTopPadding = useDetailTopPadding,
        onMessage = { message = it }
    )
}

private val AiEduPageExtractScript = """
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
  function tableText(table, index) {
    var rows = Array.prototype.slice.call(table.querySelectorAll("tr")).slice(0, 160);
    var body = rows.map(function (row) {
      return Array.prototype.slice.call(row.querySelectorAll("th,td"))
        .map(textOf)
        .filter(Boolean)
        .join(" | ");
    }).filter(Boolean).join("\n");
    return body ? ("表格 " + (index + 1) + "\n" + body) : "";
  }
  var tables = Array.prototype.slice.call(document.querySelectorAll("table"))
    .slice(0, 48)
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
      Array.prototype.slice.call(document.querySelectorAll(selector)).slice(0, 20).forEach(function (node) {
        pushUnique(containers, selector + "\n" + textOf(node).slice(0, 8000));
      });
    } catch (e) {}
  });
  var formState = [];
  Array.prototype.slice.call(document.querySelectorAll("select,input,textarea,button,[role='button']")).slice(0, 120).forEach(function (node, index) {
    var label = node.getAttribute("aria-label") || node.getAttribute("placeholder") || node.name || node.id || node.className || node.tagName;
    var value = "";
    if (node.tagName === "SELECT") {
      value = Array.prototype.slice.call(node.selectedOptions || []).map(function (option) { return option.text || option.value || ""; }).join(",");
    } else {
      value = node.value || textOf(node);
    }
    pushUnique(formState, (index + 1) + ". " + label + " = " + value);
  });
  var iframeText = [];
  Array.prototype.slice.call(document.querySelectorAll("iframe,frame")).slice(0, 12).forEach(function (frame, index) {
    try {
      var doc = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
      if (doc && doc.body) {
        pushUnique(iframeText, "Frame " + (index + 1) + "\n" + textOf(doc.body).slice(0, 12000));
      } else {
        pushUnique(iframeText, "Frame " + (index + 1) + ": empty or inaccessible");
      }
    } catch (e) {
      pushUnique(iframeText, "Frame " + (index + 1) + ": inaccessible (" + (e && e.message ? e.message : e) + ")");
    }
  });
  var shadowText = collectShadowText(document, 0);
  var bodyText = textOf(document.body).slice(0, 70000);
  return JSON.stringify({
    title: document.title || "",
    url: location.href || "",
    tables: tables,
    containers: containers.join("\n\n"),
    formState: formState.join("\n"),
    iframeText: iframeText.join("\n\n"),
    shadowText: shadowText,
    text: bodyText
  });
})()
""".trimIndent()

private fun decodeAiEduPageSnapshot(encoded: String?): String {
    val decoded = JSONArray("[$encoded]").getString(0)
    val snapshot = JSONObject(decoded)
    val title = snapshot.optString("title")
    val url = snapshot.optString("url")
    val tables = snapshot.optString("tables")
    val containers = snapshot.optString("containers")
    val formState = snapshot.optString("formState")
    val iframeText = snapshot.optString("iframeText")
    val shadowText = snapshot.optString("shadowText")
    val text = snapshot.optString("text")
    return buildString {
        if (title.isNotBlank()) appendLine("页面标题：$title")
        if (url.isNotBlank()) appendLine("页面地址：$url")
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
        if (text.isNotBlank()) {
            appendLine("页面正文：")
            appendLine(text)
        }
    }.trim()
}

private data class EduPageCaptureIssue(
    val step: String,
    val message: String
)

private fun inspectEduPageCapture(pageText: String): EduPageCaptureIssue? {
    val compact = pageText.replace(Regex("\\s+"), "")
    if (compact.length < 80) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：页面文本过少",
            message = "抓不到课表页：当前页面可提取文本太少。请先在内置页面登录并进入具体课表查询结果页。"
        )
    }

    val lower = pageText.lowercase()
    val loginSignals = listOf(
        "登录", "登陆", "密码", "验证码", "统一身份认证", "账号", "学号",
        "login", "password", "captcha", "cas"
    ).count { lower.contains(it.lowercase()) }
    val scheduleSignals = listOf(
        "课表", "课程", "节次", "星期", "周一", "周二", "周三", "周四", "周五", "周六", "周日",
        "教师", "教室", "上课", "校区", "学年", "学期", "教学班", "周数", "周次"
    ).count { pageText.contains(it) }
    val timeSignals = Regex("""\b\d{1,2}:\d{2}\b""").findAll(pageText).take(3).count()

    if (loginSignals >= 2 && scheduleSignals == 0) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：仍在登录或认证页",
            message = "抓不到课表页：当前页面像登录/认证页。请先完成登录，再进入课表页面后重试。"
        )
    }

    if (scheduleSignals < 2 && timeSignals < 2) {
        return EduPageCaptureIssue(
            step = "抓不到课表页：当前页不像课表",
            message = "抓不到课表页：当前页面没有明显课程、节次或时间表信息。这个学校可能需要先点进具体课表查询结果页，或当前拾光适配器抓不到课表页。"
        )
    }

    return null
}

private fun aiEduRequestPreview(settings: AiImportSettings, pageTextLength: Int): String {
    val useResponses = AiProviderPresets.shouldUseResponses(settings.profile)
    val path = (if (useResponses) settings.profile.responsesPath else settings.profile.chatCompletionsPath).trim('/')
    val baseUrl = if (path.isEmpty()) {
        settings.profile.baseUrl.trim().trimEnd('/')
    } else {
        normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl).trimEnd('/')
    }
    val endpoint = if (path.isEmpty()) baseUrl.trimEnd('/') else baseUrl.trimEnd('/') + "/" + path
    val outputMode = if (settings.profile.id == AiProviderPresets.deepSeek.id) {
        StructuredOutputMode.PROMPT_ONLY
    } else {
        settings.profile.structuredOutputMode
    }
    return buildString {
        appendLine("服务商：${settings.profile.displayName}")
        appendLine("接口：$endpoint")
        appendLine("模型：${settings.profile.defaultModel}")
        appendLine("请求协议：${if (useResponses) "Responses" else "Chat Completions"}")
        if (useResponses) appendLine("思考强度：${settings.profile.reasoningEffort.label}")
        appendLine("结构化输出：${outputMode.name}")
        if (settings.profile.id == AiProviderPresets.deepSeek.id && !useResponses) {
            appendLine("DeepSeek thinking：enabled / high（保留推理能力，正文与思考分开展示）")
            appendLine("DeepSeek max_tokens：393216；MiMo max_completion_tokens：131072（避免思考过程或长 JSON 耗尽输出额度）")
        }
        appendLine("输入文本：$pageTextLength 字符")
        appendLine("提示词：已附加完整 SleepDown JSON 解析协议与字段示例")
        append("密钥：已从本机安全存储读取，未显示")
    }
}

private fun eduImportIslandStatus(rawStatus: String?): String? {
    val status = rawStatus?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lowercase = status.lowercase(Locale.ROOT)
    val isTechnicalFailure = listOf(
        "失败", "错误", "异常", "无效", "无法", "不能", "未找到", "抓不到",
        "不支持", "超时", "error", "exception", "invalid", "failed", "failure",
        "json", "schema", "bridge", "javascript", "network", "socket", "connection",
        "unexpected", "expected", "token", "null"
    ).any { signal -> lowercase.contains(signal) }
    return when {
        isTechnicalFailure -> "导入遇到问题"
        status.contains("取消") -> "已取消导入"
        status.contains("成功") || status.contains("完成") -> "导入处理完成"
        status.contains("确认") || status.contains("预览") -> "等待确认预览"
        status.contains("截取") || status.contains("截图") -> "正在截取页面"
        status.contains("识屏") -> "正在处理识屏"
        status.contains("解析") || status.contains("整理") -> "正在解析课程"
        status.contains("加载") -> "正在加载页面"
        status.contains("登录") || status.contains("认证") -> "等待完成登录"
        status.contains("保存") -> "正在保存课程"
        status.contains("读取") || status.contains("获取") || status.contains("抓取") ->
            "正在读取课程"
        else -> "正在处理导入"
    }
}

@Composable
private fun EduImportGuideMorphOverlay(
    adapter: EduAdapter,
    backdrop: Backdrop,
    visible: Boolean,
    expanded: Boolean,
    statusText: String?,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
) {
    val isLargeScreen = rememberHomeAdaptiveMetrics().isLargeScreen
    val density = LocalDensity.current
    val view = LocalView.current
    val window = (view.context as? ComponentActivity)?.window
    val originalCutoutMode = remember(window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
        } else {
            null
        }
    }
    val morphProgress = remember(adapter) { Animatable(0f) }
    val openMorphEasing = remember { CubicBezierEasing(0.20f, 0.48f, 0.18f, 1f) }
    val closeMorphEasing = remember { CubicBezierEasing(0.32f, 0f, 0.22f, 1f) }
    val reboundMorphEasing = remember { CubicBezierEasing(0.30f, 0f, 0.24f, 1f) }
    val settleMorphEasing = remember { CubicBezierEasing(0.34f, 0f, 0.30f, 1f) }
    var handleDragY by remember(adapter) { mutableFloatStateOf(0f) }
    val visibleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) 300 else 200,
            easing = if (visible) openMorphEasing else closeMorphEasing
        ),
        label = "edu-import-guide-visible"
    )
    LaunchedEffect(window, view, visible) {
        val targetWindow = window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(targetWindow, view)
        if (visible) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = targetWindow.attributes
                attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                targetWindow.attributes = attributes
            }
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                val attributes = targetWindow.attributes
                attributes.layoutInDisplayCutoutMode = originalCutoutMode
                targetWindow.attributes = attributes
            }
        }
    }
    DisposableEffect(window, view, originalCutoutMode) {
        onDispose {
            val targetWindow = window ?: return@onDispose
            WindowCompat.getInsetsController(targetWindow, view)
                .show(WindowInsetsCompat.Type.statusBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                val attributes = targetWindow.attributes
                attributes.layoutInDisplayCutoutMode = originalCutoutMode
                targetWindow.attributes = attributes
            }
        }
    }
    LaunchedEffect(visible, expanded) {
        val target = if (visible && expanded) 1f else 0f
        val start = morphProgress.value
        val travel = abs(target - start)
        if (travel <= 0.0005f) {
            morphProgress.snapTo(target)
            return@LaunchedEffect
        }
        val opening = target > start
        val fullDurationMillis = if (opening) 620 else 560
        val duration = (fullDurationMillis * travel.coerceIn(0.38f, 1f)).roundToInt()
        val reboundAt = (duration * 0.74f).roundToInt()
        val settleAt = (duration * 0.90f).roundToInt()
        morphProgress.animateTo(
            targetValue = target,
            animationSpec = keyframes {
                durationMillis = duration
                start at 0 using if (opening) openMorphEasing else closeMorphEasing
                (if (opening) 1.016f else -0.014f) at reboundAt using reboundMorphEasing
                (if (opening) 0.997f else 0.003f) at settleAt using settleMorphEasing
            }
        )
    }
    val activeStatus = statusText?.trim()?.takeIf { it.isNotEmpty() }
    val collapsedStatus = remember(activeStatus) { activeStatus?.take(7) ?: "导入中" }
    val collapsedStatusStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold
    )
    val textMeasurer = rememberTextMeasurer()
    val collapsedStatusWidth = with(density) {
        textMeasurer.measure(
            text = collapsedStatus,
            style = collapsedStatusStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    LaunchedEffect(visible, expanded, activeStatus) {
        if (visible && expanded) {
            delay(5_000)
            onCollapse()
        }
    }
    val geometryProgress = morphProgress.value.coerceIn(-0.02f, 1.02f)
    val synchronizedProgress = geometryProgress.coerceIn(0f, 1f)
    val foreground = ComposeColor.White
    val contentMotionBlurPx =
        detailMotionBlurRadiusDp(synchronizedProgress) * 0.75f * density.density

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
            .zIndex(1000f)
    ) {
        val rootInsets = ViewCompat.getRootWindowInsets(view)
        val statusBarHeightPx = rootInsets
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?.takeIf { it > 0 }
            ?: WindowInsets.statusBars.getTop(density)
        val statusBarHeight = with(density) { statusBarHeightPx.toDp() }
        val topCutoutRects = rootInsets?.displayCutout?.boundingRects.orEmpty()
            .filter { it.top <= statusBarHeightPx }
        val cutoutBounds = if (topCutoutRects.isEmpty()) {
            null
        } else {
            android.graphics.Rect(
                topCutoutRects.minOf { it.left },
                topCutoutRects.minOf { it.top },
                topCutoutRects.maxOf { it.right },
                topCutoutRects.maxOf { it.bottom }
            )
        }
        val cutoutWidth = with(density) { (cutoutBounds?.width() ?: 0).toDp() }
        val cutoutHeight = with(density) { (cutoutBounds?.height() ?: 0).toDp() }
        val cameraContentGap = maxOf(cutoutWidth + 18.dp, 42.dp)
        val collapsedSideWidth = maxOf(34.dp, collapsedStatusWidth + 13.dp)
        val targetCollapsedWidth = maxOf(
            150.dp,
            cameraContentGap + collapsedSideWidth * 2f
        )
            .coerceAtMost((maxWidth - 12.dp).coerceAtLeast(0.dp))
        val collapsedWidth by animateDpAsState(
            targetValue = targetCollapsedWidth,
            animationSpec = tween(durationMillis = 240, easing = openMorphEasing),
            label = "edu-import-guide-collapsed-width"
        )
        val collapsedHeight = maxOf(32.dp, cutoutHeight + 2.dp).coerceAtMost(38.dp)
        val cameraCenterX = cutoutBounds?.let { bounds ->
            with(density) { bounds.centerX().toDp() }
        } ?: maxWidth / 2f
        val cameraCenterY = cutoutBounds?.let { bounds ->
            with(density) { bounds.centerY().toDp() }
        } ?: statusBarHeight / 2f
        val collapsedLeft = (cameraCenterX - collapsedWidth / 2f).coerceIn(
            6.dp,
            (maxWidth - collapsedWidth - 6.dp).coerceAtLeast(6.dp)
        )
        val collapsedTop = (cameraCenterY - collapsedHeight / 2f).coerceAtLeast(3.dp)
        val expandedSafeInset = 8.dp
        val guideHeaderHeight = maxOf(statusBarHeight, 38.dp)
        val guideContentTopPadding = (guideHeaderHeight - 6.dp).coerceAtLeast(32.dp)
        val expandedWidth = if (isLargeScreen) {
            (maxWidth * 0.5f).coerceAtLeast(collapsedWidth)
        } else {
            (maxWidth - expandedSafeInset * 2f).coerceAtLeast(collapsedWidth)
        }
        val expandedHeight = (guideHeaderHeight + 126.dp)
            .coerceAtMost((maxHeight - expandedSafeInset * 2f).coerceAtLeast(collapsedHeight))
        val cardWidth = collapsedWidth + (expandedWidth - collapsedWidth) * geometryProgress
        val cardHeight = collapsedHeight + (expandedHeight - collapsedHeight) * geometryProgress
        val expandedLeft = (maxWidth - expandedWidth) / 2f
        val cardLeft = collapsedLeft + (expandedLeft - collapsedLeft) * geometryProgress
        val cardTop = collapsedTop + (expandedSafeInset - collapsedTop) * geometryProgress

        fun roundedCornerRadius(position: Int): Dp? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return view.rootWindowInsets
                ?.getRoundedCorner(position)
                ?.radius
                ?.takeIf { it > 0 }
                ?.let { with(density) { it.toDp() } }
        }

        val fallbackBodyCorner = 30.dp
        val expandedBodyCorner = listOfNotNull(
            roundedCornerRadius(android.view.RoundedCorner.POSITION_TOP_LEFT),
            roundedCornerRadius(android.view.RoundedCorner.POSITION_TOP_RIGHT),
            roundedCornerRadius(android.view.RoundedCorner.POSITION_BOTTOM_LEFT),
            roundedCornerRadius(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
        ).maxOrNull() ?: fallbackBodyCorner
        val collapsedCorner = collapsedHeight / 2f
        val cardCorner = collapsedCorner +
            (expandedBodyCorner - collapsedCorner) * synchronizedProgress
        val cardShape = RoundedRectangle(cardCorner)
        val expandedContentAlpha = synchronizedProgress
        val collapsedContentAlpha = 1f - synchronizedProgress
        val handleCollapseThreshold = with(density) { 18.dp.toPx() }

        LiquidButton(
            onClick = { if (!expanded) onExpand() },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = cardLeft, y = cardTop)
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    alpha = visibleAlpha
                    clip = false
                },
            isInteractive = visible,
            clickTargetEnabled = visible,
            height = cardHeight,
            contentPadding = PaddingValues(0.dp),
            blurRadius = 10.dp,
            lensHeight = 24.dp + 6.dp * synchronizedProgress,
            lensAmount = 42.dp + 8.dp * synchronizedProgress,
            chromaticAberration = true,
            surfaceColor = ComposeColor.Black.copy(alpha = 0.68f - 0.42f * synchronizedProgress),
            shadowEnabled = false,
            highlightEnabled = true,
            shape = cardShape,
            clipToBounds = false,
            pressExpansion = 3.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.34f + 0.66f * synchronizedProgress }
                        .background(
                            Brush.verticalGradient(
                                0f to ComposeColor.Black.copy(alpha = 0.98f),
                                0.28f to ComposeColor.Black.copy(alpha = 0.84f),
                                0.48f to ComposeColor.Black.copy(alpha = 0.54f),
                                0.72f to ComposeColor.Black.copy(alpha = 0.14f),
                                1f to ComposeColor.Black.copy(alpha = 0.03f)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - synchronizedProgress }
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ComposeColor.White.copy(alpha = 0.10f),
                                    ComposeColor.Black.copy(alpha = 0.88f),
                                    ComposeColor.Black.copy(alpha = 0.88f),
                                    ComposeColor.White.copy(alpha = 0.10f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp)
                        .graphicsLayer {
                            alpha = collapsedContentAlpha
                            scaleX = 1f - 0.04f * synchronizedProgress
                            scaleY = 1f - 0.04f * synchronizedProgress
                            compositingStrategy = if (contentMotionBlurPx > 0.01f) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                            renderEffect = platformMotionBlurRenderEffect(contentMotionBlurPx)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_school_import),
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = collapsedStatus,
                        color = foreground,
                        style = collapsedStatusStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = expandedContentAlpha
                            translationY = (1f - expandedContentAlpha) * 6.dp.toPx()
                            scaleX = 0.98f + 0.02f * expandedContentAlpha
                            scaleY = 0.98f + 0.02f * expandedContentAlpha
                            compositingStrategy = if (contentMotionBlurPx > 0.01f) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                            renderEffect = platformMotionBlurRenderEffect(contentMotionBlurPx)
                        }
                        .padding(
                            start = 18.dp,
                            top = guideContentTopPadding,
                            end = 18.dp,
                            bottom = 6.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedRectangle(12.dp))
                                .background(foreground.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_school_import),
                                contentDescription = null,
                                tint = foreground,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = adapter.school.name,
                                color = foreground,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "适配作者 · ${adapter.maintainer.ifBlank { "拾光社区" }}",
                                color = foreground.copy(alpha = 0.66f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (adapter.description.isNotBlank()) {
                        Text(
                            text = adapter.description,
                            modifier = Modifier.fillMaxWidth(),
                            color = foreground.copy(alpha = 0.56f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(foreground.copy(alpha = 0.14f))
                    )
                    Text(
                        text = activeStatus
                            ?: "登录后进入课程表页面，点击底部导入并核对预览。",
                        color = foreground.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .pointerInput(expanded, onCollapse, handleCollapseThreshold) {
                                detectDragGestures(
                                    onDragStart = { handleDragY = 0f },
                                    onDragCancel = { handleDragY = 0f },
                                    onDragEnd = {
                                        if (handleDragY <= -handleCollapseThreshold) onCollapse()
                                        handleDragY = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    handleDragY += dragAmount.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = handleDragY.coerceIn(-10f, 8f) * 0.25f
                                }
                                .width(38.dp)
                                .height(4.dp)
                                .clip(Capsule())
                                .background(foreground.copy(alpha = 0.34f))
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EduImportBrowserScreen(
    state: AppState,
    adapter: EduAdapter,
    webContentBackdrop: LayerBackdrop,
    message: String?,
    webView: WebView?,
    onWebView: (WebView?) -> Unit,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    bridge: ShiguangBridgeHost,
    useDetailTopPadding: Boolean = true,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buttonBackdrop = webContentBackdrop
    val backgroundPermissionGate = rememberAiImportBackgroundPermissionGate()
    var addressText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var aiParsing by remember { mutableStateOf(false) }
    var aiProgress by remember { mutableStateOf<AiEduImportProgress?>(null) }
    var isScreenCapturing by remember { mutableStateOf(false) }
    var screenCaptureStatus by remember { mutableStateOf<String?>(null) }
    var popupWebView by remember(adapter) { mutableStateOf<WebView?>(null) }
    val webCompatDelegates = remember(adapter) { mutableMapOf<WebView, WebCompatDelegate>() }
    var webViewGeneration by remember(adapter) { mutableIntStateOf(0) }
    var rendererRestoreUrl by remember(adapter) { mutableStateOf<String?>(null) }
    var webTopEdgeColor by remember(adapter) { mutableStateOf<ComposeColor?>(null) }
    val webTopThemeColor = animateColorAsState(
        targetValue = webTopEdgeColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(160),
        label = "web-header-theme"
    )
    var importGuideVisible by remember(adapter) { mutableStateOf(false) }
    var importGuideExpanded by remember(adapter) { mutableStateOf(false) }
    var webGestureActive by remember(adapter) { mutableStateOf(false) }
    var loginHistory by remember(adapter) { mutableStateOf(EduLoginHistoryStore.load(context)) }
    val unifiedDockHistoryEnabled = adapter.isGeneralEduTool() || adapter.isAiEduImportTool()
    var dockHistoryExpanded by remember(adapter) { mutableStateOf(unifiedDockHistoryEnabled) }
    val dockHistoryEntries = remember(loginHistory, adapter) {
        if (unifiedDockHistoryEnabled) {
            loginHistory.take(3)
        } else {
            emptyList()
        }
    }
    val visibleWebView = rememberUpdatedState(popupWebView ?: webView)
    val sampleHeightPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val topEdgeSampler = remember(adapter, sampleHeightPx) {
        WebTopEdgeSampler(sampleHeightPx, { visibleWebView.value }) {
            webTopEdgeColor = ComposeColor(it)
        }
    }
    DisposableEffect(topEdgeSampler) {
        onDispose { topEdgeSampler.dispose() }
    }
    val currentTopEdgeSampler = rememberUpdatedState(topEdgeSampler)
    val requestInterceptor = remember(adapter) { ShiguangWebRequestInterceptor() }
    val taskProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    LaunchedEffect(taskProgress?.taskId, taskProgress?.finished) {
        val completed = taskProgress?.takeIf {
            it.taskId.isNotBlank() && it.routeLabel == "AI教务导入" && it.finished
        } ?: return@LaunchedEffect
        aiProgress = completed
        aiParsing = false
        onMessage(completed.error ?: completed.liveSummary.ifBlank { completed.steps.lastOrNull().orEmpty() })
    }
    val topPadding = if (useDetailTopPadding) detailContentTopPadding() else 0.dp
    val normalizedUrl = remember(addressText) {
        normalizeEduUrl(addressText)
    }

    fun scheduleWebTopEdgeSample() {
        currentTopEdgeSampler.value.schedule()
    }

    fun loadAddress() {
        if (normalizedUrl.isBlank()) {
            onMessage("请输入教务系统网址")
            return
        }
        onUrlChange(normalizedUrl)
        (popupWebView ?: webView)?.loadUrl(normalizedUrl)
    }

    fun appendAiStep(step: String) {
        val next = (aiProgress ?: AiEduImportProgress()).let {
            it.copy(steps = it.steps + step)
        }
        aiProgress = next
        AiEduImportProgressSession.update(next)
    }

    fun setAiProgress(progress: AiEduImportProgress?) {
        aiProgress = progress
        AiEduImportProgressSession.update(progress)
    }

    fun runAiEduImport(forceFallback: Boolean = false) {
        val target = popupWebView ?: webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        if (aiParsing) return
        val routeLabel = "AI教务导入"
        fun cancelAiImport(message: String = "已取消 AI 教务导入") {
            AiEduImportProgressSession.clearActions()
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + "用户取消",
                awaitingConfirmation = false,
                finished = true
            ))
            onMessage(message)
            aiParsing = false
        }

        fun sendCaptureToAi(capture: EduPageCaptureResult, settings: AiImportSettings, includePageText: Boolean = true) {
            val pageUrl = target.url?.takeIf { it.isNotBlank() } ?: currentUrl
            val aiPageText = buildString {
                appendLine("当前教务页面地址：${pageUrl.ifBlank { "未知" }}")
                appendLine("请根据该网址识别学校或教务系统来源，并在模型具备相关能力时参考该学校公开的作息/排课时间。")
                appendLine()
                if (includePageText) {
                    append(capture.text)
                } else {
                    appendLine("用户选择只发送截图，DOM 文本未发送；请以截图中的课表结构、表头、时间轴和课程块为准。")
                }
            }
            AiEduImportProgressSession.clearActions()
            setAiProgress(aiProgress?.copy(
                awaitingConfirmation = false,
                requestSent = false,
                confirmActionLabel = "",
                secondaryConfirmActionLabel = "",
                screenModeActionLabel = "",
                steps = aiProgress?.steps.orEmpty() + (if (includePageText) {
                    "用户确认发送给 AI"
                } else {
                    "用户确认只发送截图给 AI"
                })
            ))
            val initial = checkNotNull(aiProgress).copy(
                requestPreview = aiEduRequestPreview(settings, aiPageText.length)
            )
            onMessage("AI 正在解析当前教务页面...")
            AiImportTaskManager.startCapturedPageImport(
                context = context,
                text = aiPageText,
                screenshots = capture.screenshots,
                sourceName = routeLabel,
                warnings = capture.warnings,
                settings = settings,
                scheduleConfig = state.config,
                initialProgress = initial
            )
        }

        fun prepareCapturePreview(capture: EduPageCaptureResult, settings: AiImportSettings, screenMode: Boolean) {
            val supportsVision = AiProviderPresets.supportsImageInput(settings.profile)
            val pageIssue = inspectEduPageCapture(capture.text)
            val isLoginPage = pageIssue?.step?.contains("登录") == true
            val pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(60_000)
            if (isLoginPage) {
                AiEduImportProgressSession.clearActions()
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + pageIssue.step,
                    pageText = pageText,
                    error = pageIssue.message,
                    finished = true
                ))
                onMessage(pageIssue.message)
                aiParsing = false
                return
            }
            if (capture.screenshots.isNotEmpty() && !supportsVision) {
                AiEduImportProgressSession.clearActions()
                val message = "已生成截图兜底，但当前模型不支持视觉输入。请换视觉模型，或手动进入可复制文本课表页。"
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + "当前模型不支持识屏",
                    pageText = pageText,
                    error = message,
                    finished = true
                ))
                onMessage(message)
                aiParsing = false
                return
            }
            val warningStep = when {
                pageIssue != null && supportsVision -> "页面文本不够像课表，请确认是否改用识屏模式"
                pageIssue != null -> pageIssue.step
                capture.screenshots.isNotEmpty() -> "已准备页面文本和识屏截图，等待确认发送"
                else -> "已抓取页面文本，等待确认发送"
            }
            val confirmLabel = when {
                capture.screenshots.isNotEmpty() -> "发送截图+文本"
                else -> "确认发送文本"
            }
            val secondaryConfirmLabel = if (capture.screenshots.isNotEmpty()) "只发送截图" else ""
            val screenLabel = if (!screenMode && supportsVision) "进入高清识屏" else ""
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + warningStep,
                pageText = pageText,
                hasReadablePageText = capture.text.isNotBlank(),
                screenshotPreviews = capture.screenshots.take(6),
                userPrompt = "帮我按规则导入当前页面的课表",
                attachmentTitle = when {
                    capture.screenshots.isNotEmpty() && capture.text.isNotBlank() -> "课表页面内容"
                    capture.screenshots.isNotEmpty() -> "课表页面截图"
                    else -> "课表页面文字"
                },
                requestSent = false,
                requestPreview = aiEduRequestPreview(settings, capture.text.length),
                awaitingConfirmation = true,
                confirmActionLabel = confirmLabel,
                secondaryConfirmActionLabel = secondaryConfirmLabel,
                screenModeActionLabel = screenLabel,
                cancelActionLabel = "返回重抓",
                finished = false,
                error = null
            ))
            AiEduImportProgressSession.setActions(
                onConfirm = { sendCaptureToAi(capture, settings, includePageText = true) },
                onSecondaryConfirm = if (secondaryConfirmLabel.isNotBlank()) {
                    { sendCaptureToAi(capture, settings, includePageText = false) }
                } else null,
                onScreenMode = if (screenLabel.isNotBlank()) {
                    {
                        setAiProgress(aiProgress?.copy(
                            steps = aiProgress?.steps.orEmpty() + "用户选择识屏模式",
                            awaitingConfirmation = false,
                            secondaryConfirmActionLabel = "",
                            screenModeActionLabel = ""
                        ))
                        scope.launch {
                            delay(420)
                            isScreenCapturing = true
                            screenCaptureStatus = "正在识屏截取，请保持页面不动..."
                            withFrameNanos { }
                            withFrameNanos { }
                            delay(120)
                            val screenCapture = runCatching {
                                try {
                                    captureEduPage(
                                        webView = target,
                                        maxScreenshots = 6,
                                        forceScreenshots = true,
                                        onScreenshotProgress = { index, total ->
                                            screenCaptureStatus = "正在截取第 $index/$total 段"
                                        }
                                    )
                                } finally {
                                    isScreenCapturing = false
                                    screenCaptureStatus = null
                                }
                            }.getOrElse {
                                context.openRegisteredActivity(
                                    TransitionRouteId.ImportToAiProgress,
                                    Intent(context, AiEduImportProgressActivity::class.java)
                                )
                                AiEduImportProgressSession.clearActions()
                                setAiProgress(aiProgress?.copy(
                                    steps = aiProgress?.steps.orEmpty() + "识屏截图失败",
                                    error = it.message ?: "识屏截图失败",
                                    finished = true
                                ))
                                onMessage(it.message ?: "识屏截图失败")
                                aiParsing = false
                                return@launch
                            }
                            context.openRegisteredActivity(
                                TransitionRouteId.ImportToAiProgress,
                                Intent(context, AiEduImportProgressActivity::class.java)
                            )
                            prepareCapturePreview(screenCapture, settings, screenMode = true)
                        }
                    }
                } else null,
                onCancel = { cancelAiImport() }
            )
            onMessage(
                if (capture.screenshots.isNotEmpty()) "已进入识屏预览，确认后才会发送给 AI。"
                else "已抓取页面文本，请确认后再发送给 AI。"
            )
        }

        AiEduImportProgressSession.clearActions()
        setAiProgress(AiEduImportProgress(steps = listOf("准备读取当前页面")))
        context.openRegisteredActivity(
            TransitionRouteId.ImportToAiProgress,
            Intent(context, AiEduImportProgressActivity::class.java)
        )
        setAiProgress(aiProgress?.copy(routeLabel = routeLabel))
        onMessage("正在分层抓取当前页面...")
        aiParsing = true
        scope.launch {
            val capture = runCatching { captureEduPage(target, allowScreenshotFallback = false) }.getOrElse {
                setAiProgress(aiProgress?.copy(
                    steps = aiProgress?.steps.orEmpty() + "页面抓取失败",
                    error = it.message ?: "页面抓取失败",
                    finished = true
                ))
                onMessage(it.message ?: "页面抓取失败")
                aiParsing = false
                return@launch
            }
            val captureStep = when (capture.mode) {
                EduPageCaptureMode.TEXT_ONLY -> "已完成 DOM 深度抓取（${capture.text.length} 字符）"
                EduPageCaptureMode.TEXT_PLUS_SCREENSHOT -> "已完成 DOM 抓取并生成截图兜底（${capture.screenshots.size} 张）"
                EduPageCaptureMode.SCREENSHOT_ONLY -> "DOM 文本不足，已生成截图兜底（${capture.screenshots.size} 张）"
            }
            setAiProgress(aiProgress?.copy(
                steps = aiProgress?.steps.orEmpty() + captureStep,
                pageText = (capture.diagnosticsText + "\n\n" + capture.text).take(60_000)
            ))
            if (capture.warnings.isNotEmpty()) {
                appendAiStep("页面诊断：${capture.warnings.joinToString("；").take(120)}")
            }
            val settings = AiImportSettingsStore.load(context)
            appendAiStep(
                if (capture.screenshots.isEmpty()) "已准备页面文本预览，等待用户确认"
                else "已生成页面截图兜底，等待用户确认"
            )
            prepareCapturePreview(capture, settings, screenMode = capture.screenshots.isNotEmpty())
        }
    }

    fun runOriginalImportScript() {
        val target = popupWebView ?: webView
        if (target == null) {
            onMessage("网页还没有加载完成")
            return
        }
        target.evaluateJavascript(AiEduPageExtractScript) { encoded ->
            runCatching { inspectEduPageCapture(decodeAiEduPageSnapshot(encoded)) }
                .getOrNull()
                ?.let { onMessage("${it.message}；仍将尝试执行原有拾光导入脚本。") }
        }
        scope.launch {
            runCatching { ShiguangWarehouse.resolveScript(context, adapter) }
                .onSuccess { script ->
                    bridge.bindWebView(target)
                    bridge.beginTask(state.config, state.periods)
                    target.injectShiguangRuntime(desktopMode)
                    onMessage("正在执行拾光官方适配器")
                    target.evaluateJavascript(script, null)
                }
                .onFailure { onMessage("拾光仓库脚本加载失败：${it.message ?: "找不到该学校的导入脚本"}") }
        }
    }

    fun updateNavigationState(target: WebView?) {
        canGoBack = target?.canGoBack() == true
        canGoForward = target?.canGoForward() == true
    }

    fun releaseWebCompat(target: WebView, rendererGone: Boolean = false) {
        webCompatDelegates.remove(target)?.dispose(rendererGone)
    }

    fun closePopupWebView() {
        popupWebView?.let { popup ->
            releaseWebCompat(popup)
            popup.uninstallShiguangRuntime()
            (popup.parent as? ViewGroup)?.removeView(popup)
            popup.releaseSleepDownWebView(clearResourceCache = false)
        }
        popupWebView = null
        webView?.let { primary ->
            bridge.bindWebView(primary)
            addressText = primary.url.orEmpty().ifBlank { currentUrl }
            onUrlChange(addressText)
            updateNavigationState(primary)
            scheduleWebTopEdgeSample()
        }
    }

    fun handleExternalNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme == "http" || scheme == "https") return false
        val intent = try {
            if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, uri)
        } catch (_: java.net.URISyntaxException) {
            onMessage("无法识别此页面请求的外部链接")
            return true
        }
        val safeIntent = intent.apply {
            component = null
            selector = null
        }
        if (safeIntent.resolveActivity(context.packageManager) == null) {
            onMessage("未找到可处理 ${scheme.ifBlank { "此" }} 链接的应用")
            return true
        }
        context.startActivity(safeIntent)
        return true
    }

    fun configureEduWebView(target: WebView, isPopup: Boolean) {
        target.apply webView@ {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // Keep Nexio's normal window renderer. The Compose sampling boundary below isolates
            // Chromium from repeated backdrop replay without forcing a second WebView layer.
            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            setOnTouchListener { _, event ->
                webGestureActive = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                    else -> webGestureActive
                }
                false
            }
            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                scheduleWebTopEdgeSample()
                if (webGestureActive && kotlin.math.abs(scrollY - oldScrollY) > 1) {
                    importGuideExpanded = false
                    dockHistoryExpanded = false
                }
            }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    scheduleWebTopEdgeSample()
                }
            }
            settings.databaseEnabled = true
            configureEduImportSecurity()
            enableSystemCredentialAutofill()
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(!isPopup)
            // Match Shiguang: legacy HTTPS teaching pages may load their JS loader over HTTP.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            val compatDelegate = WebCompatDelegate(this, desktopMode)
            webCompatDelegates[this] = compatDelegate
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
            }
            installShiguangRuntime(bridge)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    return handleExternalNavigation(request.url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    view?.injectShiguangRuntime(desktopMode)
                    val visiblePage = if (isPopup) popupWebView === view else popupWebView == null
                    if (visiblePage) updateNavigationState(view)
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    compatDelegate.onPageFinished()
                    view?.injectShiguangRuntime(desktopMode)
                    scheduleWebTopEdgeSample()
                    val visiblePage = if (isPopup) popupWebView === view else popupWebView == null
                    if (visiblePage) updateNavigationState(view)
                    val pageUri = runCatching { Uri.parse(url) }.getOrNull()
                    if (
                        visiblePage &&
                        !importGuideVisible &&
                        pageUri?.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                        !pageUri?.host.isNullOrBlank()
                    ) {
                        importGuideVisible = true
                        importGuideExpanded = true
                    }
                    if (visiblePage && !url.isNullOrBlank()) {
                        addressText = url
                        onUrlChange(url)
                        EduLoginHistoryStore.remember(
                            context,
                            adapter,
                            url,
                            CookieManager.getInstance().getCookie(url)
                        )
                        loginHistory = EduLoginHistoryStore.load(context)
                        CookieManager.getInstance().flush()
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    scheduleWebTopEdgeSample()
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return request?.let { requestInterceptor.intercept(it, desktopMode) }
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onMessage("页面加载失败：${error?.description ?: "网络连接异常"}")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (
                        request?.isForMainFrame == false &&
                        request.url.path?.endsWith(".js", ignoreCase = true) == true
                    ) {
                        Log.w(
                            "EduWebView",
                            "JS resource HTTP ${errorResponse?.statusCode ?: "unknown"}: ${request.url}"
                        )
                    }
                    if (request?.isForMainFrame == true) {
                        onMessage("页面返回 HTTP ${errorResponse?.statusCode ?: "错误"}")
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    onMessage("网站证书校验失败，已停止加载")
                    super.onReceivedSslError(view, handler, error)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val restoreUrl = view?.url?.takeIf { it.isNotBlank() }
                        ?: addressText.takeIf { it.isNotBlank() }
                        ?: currentUrl
                    view?.let { releaseWebCompat(it, rendererGone = true) }
                    view?.uninstallShiguangRuntime()
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                    popupWebView?.takeIf { it !== view }?.let { popup ->
                        releaseWebCompat(popup)
                        popup.uninstallShiguangRuntime()
                        (popup.parent as? ViewGroup)?.removeView(popup)
                        popup.destroy()
                    }
                    popupWebView = null
                    if (isPopup) {
                        webView?.let { primary ->
                            releaseWebCompat(primary)
                            primary.uninstallShiguangRuntime()
                            (primary.parent as? ViewGroup)?.removeView(primary)
                            primary.destroy()
                        }
                    }
                    onWebView(null)
                    bridge.bindWebView(null)
                    rendererRestoreUrl = restoreUrl
                    webViewGeneration += 1
                    onMessage(
                        if (detail?.didCrash() == true) "网页渲染器异常，已恢复当前页面"
                        else "网页渲染器已被系统回收，正在恢复"
                    )
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let { message ->
                        Log.d(
                            "EduWebView",
                            "console=${message.message()} sourceId=${message.sourceId()} lineNumber=${message.lineNumber()}"
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    if (isPopup) return false
                    val message = resultMsg ?: return false
                    val transport = message.obj as? WebView.WebViewTransport ?: return false
                    closePopupWebView()
                    val child = WebView(context)
                    configureEduWebView(child, isPopup = true)
                    popupWebView = child
                    transport.webView = child
                    message.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    if (window === popupWebView) closePopupWebView()
                }
            }
            enableSleepDownDownloads()
        }
    }

    fun createEduWebView(context: Context): WebView {
        return WebView(context).apply {
            configureEduWebView(this, isPopup = false)
            onWebView(this)
            bridge.bindWebView(this)
            updateNavigationState(this)
            val initialUrl = rendererRestoreUrl ?: normalizedUrl
            if (initialUrl.isNotBlank()) webCompatDelegates.getValue(this).loadInitialUrl(initialUrl)
        }
    }

    BackHandler(enabled = popupWebView != null) {
        closePopupWebView()
    }
    DisposableEffect(Unit) {
        onDispose {
            webCompatDelegates.values.forEach { it.dispose() }
            webCompatDelegates.clear()
            popupWebView?.let { popup ->
                popup.uninstallShiguangRuntime()
                (popup.parent as? ViewGroup)?.removeView(popup)
                popup.releaseSleepDownWebView(clearResourceCache = false)
            }
            popupWebView = null
        }
    }

    val guideStatusText = eduImportIslandStatus(screenCaptureStatus ?: message)
    LaunchedEffect(importGuideVisible, guideStatusText) {
        if (importGuideVisible && !guideStatusText.isNullOrBlank()) {
            importGuideExpanded = true
        }
    }
    val floatingOverlayHost = LocalDetailActivityFloatingOverlayHost.current
    val currentGuideVisible = rememberUpdatedState(importGuideVisible)
    val currentGuideExpanded = rememberUpdatedState(importGuideExpanded)
    val currentGuideStatus = rememberUpdatedState(guideStatusText)
    val currentGuideExpandAction = rememberUpdatedState<() -> Unit>({ importGuideExpanded = true })
    val currentGuideCollapseAction = rememberUpdatedState<() -> Unit>({ importGuideExpanded = false })
    val floatingImportGuide: (@Composable () -> Unit)? = if (floatingOverlayHost != null) {
        remember(floatingOverlayHost, adapter, buttonBackdrop) {
            @Composable {
                EduImportGuideMorphOverlay(
                    adapter = adapter,
                    backdrop = buttonBackdrop,
                    visible = currentGuideVisible.value,
                    expanded = currentGuideExpanded.value,
                    statusText = currentGuideStatus.value,
                    onExpand = currentGuideExpandAction.value,
                    onCollapse = currentGuideCollapseAction.value
                )
            }
        }
    } else {
        null
    }
    DisposableEffect(floatingOverlayHost, floatingImportGuide) {
        val host = floatingOverlayHost
        if (host != null && floatingImportGuide != null) {
            host.content = floatingImportGuide
        }
        onDispose {
            if (host != null && host.content === floatingImportGuide) {
                host.content = null
            }
        }
    }
    val importGuideMountedAtRoot = floatingOverlayHost != null &&
        floatingOverlayHost.content === floatingImportGuide

    // Keep WebView at its natural position. The multi-row theme fills the toolbar and feathers
    // into the page, so its color never ends at a hard horizontal boundary. The existing separate
    // toolbar blur consumer samples this producer; no extra WebView capture or blur pass is added.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropProducer(webContentBackdrop)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(importGuideExpanded) {
                    if (importGuideExpanded) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            // Observe without consuming: the same gesture still reaches WebView.
                            importGuideExpanded = false
                        }
                    }
                }
        ) {
            Box(
                Modifier.fillMaxWidth().height(topPadding).drawBehind {
                    drawRect(webTopThemeColor.value)
                }
            )
            key(webViewGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
                    factory = { createEduWebView(it) },
                    // Navigation remains event-driven; observed page URLs never feed back into
                    // loadUrl from recomposition.
                    update = {},
                    onRelease = { released ->
                        releaseWebCompat(released)
                        if (released === webView) {
                            onWebView(null)
                            bridge.bindWebView(null)
                        }
                        released.uninstallShiguangRuntime()
                        released.releaseSleepDownWebView()
                    }
                )
            }
            popupWebView?.let { popup ->
                key(popup) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            },
                        factory = { popup },
                        update = {},
                        onRelease = { released ->
                            releaseWebCompat(released)
                            if (released === popupWebView) {
                                popupWebView = null
                                released.uninstallShiguangRuntime()
                                released.releaseSleepDownWebView(clearResourceCache = false)
                            }
                        }
                    )
                }
            }
            if (topPadding > 0.dp) {
                Box(
                    Modifier.fillMaxWidth().padding(top = topPadding).height(28.dp).drawBehind {
                        val theme = webTopThemeColor.value
                        drawRect(
                            Brush.verticalGradient(
                                0f to theme,
                                0.2f to theme.copy(alpha = theme.alpha * 0.90f),
                                0.5f to theme.copy(alpha = theme.alpha * 0.50f),
                                0.8f to theme.copy(alpha = theme.alpha * 0.10f),
                                1f to theme.copy(alpha = 0f)
                            )
                        )
                    }
                )
            }
        }

        if (!isScreenCapturing) {
            EduBrowserDock(
                config = state.config,
                backdrop = buttonBackdrop,
                address = addressText,
                onAddressChange = { addressText = it },
                onGo = { loadAddress() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = {
                    val target = popupWebView ?: webView
                    target?.goBack()
                    updateNavigationState(target)
                },
                onForward = {
                    val target = popupWebView ?: webView
                    target?.goForward()
                    updateNavigationState(target)
                },
                originalImportAvailable = !adapter.isAiEduImportTool(),
                aiImportRunning = aiParsing,
                onOriginalImport = { runOriginalImportScript() },
                onAiImport = {
                    backgroundPermissionGate.continueWithPermissionTip {
                        runAiEduImport(forceFallback = !adapter.isAiEduImportTool())
                    }
                },
                desktopMode = desktopMode,
                onRefresh = {
                    (popupWebView ?: webView)?.reload()
                    onMessage("已刷新页面")
                },
                onToggleDesktopMode = {
                    desktopMode = !desktopMode
                    webCompatDelegates.values.toList().forEach { it.setDesktopMode(desktopMode) }
                },
                historyEntries = dockHistoryEntries,
                historyExpanded = dockHistoryExpanded,
                onHistoryExpandedChange = { dockHistoryExpanded = it },
                onHistorySelected = { entry ->
                    EduLoginHistoryStore.restoreCookies(entry)
                    addressText = entry.url
                    onUrlChange(entry.url)
                    dockHistoryExpanded = false
                    (popupWebView ?: webView)?.loadUrl(entry.url)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 18.dp)
            )
        }
        if (!importGuideMountedAtRoot) {
            EduImportGuideMorphOverlay(
                adapter = adapter,
                backdrop = buttonBackdrop,
                visible = importGuideVisible,
                expanded = importGuideExpanded,
                statusText = guideStatusText,
                onExpand = { importGuideExpanded = true },
                onCollapse = { importGuideExpanded = false }
            )
        }
    }
    AiImportBackgroundPermissionTip(
        state = backgroundPermissionGate,
        backdrop = buttonBackdrop,
        config = state.config
    )
}

