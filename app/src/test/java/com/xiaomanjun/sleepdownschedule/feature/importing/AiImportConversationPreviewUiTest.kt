package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.AiEduImportProgressPage
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowWebView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port", shadows = [LayoutShadowWebView::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiImportConversationPreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val session = AiEduImportProgressSession
    private val config = defaultConfig()
    private val original = ImportDraft(config, defaultPeriods(), listOf(
        CourseEntity(1, "原始课程", null, null, 1, listOf(1), (1..8).toList() + (10..12).toList(), WeekParity.ALL, "课表备注\n".repeat(12))))

    @After fun cleanup() { session.update(null) }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-land")
    fun tabletHistoryCanReturnToTheRunningTaskAndStatsRemainAboveTheInput() {
        val context = compose.activity
        AiImportHistoryStore.clear(context)
        val historyDraft = original.copy(courses = listOf(original.courses.single().copy(name = "历史课程", note = null)))
        AiImportHistoryStore.record(context, historyDraft, AiEduImportProgress(taskId = "old", userPrompt = "旧会话问题", finished = true))
        session.beginTask(AiEduImportProgress(taskId = "live", userPrompt = "当前进行中的导入", requestSent = true))
        session.beginReasoning("live")
        try {
            compose.setContent {
                MaterialTheme { GlassMiuixSettingsTheme(config) { AiEduImportProgressPage(config, onClose = {}) } }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("历史课程 等 1 门课").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("导入历史").assertExists()
            val input = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            val timer = compose.onNodeWithContentDescription("正在导入中", substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue("统计应位于输入框上方", timer.bottom <= input.top)
            compose.onNodeWithText("历史课程 等 1 门课").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("正在导入中", substring = true).fetchSemanticsNodes().isEmpty() }
            compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(0)
            compose.onNodeWithText("旧会话问题").assertExists()
            compose.runOnIdle { assertEquals("live", session.progress.value?.taskId); assertFalse(session.progress.value!!.finished) }
            compose.onNodeWithText("当前导入").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("当前进行中的导入").assertExists()
            compose.onNodeWithContentDescription("正在导入中", substring = true).assertExists()
        } finally { AiImportHistoryStore.clear(context) }
    }

    @Test fun newDialogueFollowsThePreviousPreviewAndEachArtifactKeepsItsOwnImportSnapshot() {
        session.beginTask(AiEduImportProgress(taskId = "first", userPrompt = "导入课表", finished = true,
            activityReports = listOf(AiImportReport("**已识别课程**，正在核对周次", AiImportReportKind.MODEL),
                AiImportReport("本地 JSON 校验完成", AiImportReportKind.EXECUTION))), baseDraft = original)
        val first = checkNotNull(session.progress.value)
        val firstDraft = first.checkpoints.single().restore(config).getOrThrow()
        val imported = mutableListOf<Pair<ImportDraft, Boolean>>()
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    AiEduImportProgressPage(config, onClose = {}, onImportSubmitted = {}, showHistoryAction = false,
                        onImportRequested = { draft, create -> imported += draft to create })
                }
            }
        }
        compose.waitForIdle()
        stopFollowing()
        positionOf(hasText("已识别课程，正在核对周次"))
        val formatted = compose.onNodeWithText("已识别课程，正在核对周次").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(formatted.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        compose.onNodeWithText("**已识别课程**，正在核对周次").assertDoesNotExist()
        compose.onNodeWithText("本地 JSON 校验完成").assertDoesNotExist()
        compose.onNodeWithText("课表工作区").assertDoesNotExist()
        compose.onNodeWithText("阶段 1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("课表选项").assertDoesNotExist()
        compose.onNodeWithText("查看 / 编辑 JSON").assertDoesNotExist()
        positionOf(hasText("周次：第1–8、10–12周 · 每周"))
        positionOf(hasText("执行步骤 · 1"))
        compose.onNodeWithText("执行步骤 · 1").performClick()
        positionOf(hasText("本地 JSON 校验完成"))
        compose.onNodeWithText("执行步骤 · 1").performClick()
        compose.onNodeWithText("本地 JSON 校验完成").assertDoesNotExist()
        compose.runOnIdle {
            session.beginTask(first.copy(taskId = "second", userPrompt = "下一句话", finished = false,
                activityReports = emptyList(),
                conversationTurns = archiveImportTurn(first, firstDraft)), baseDraft = firstDraft,
                preserveCheckpointsFromTaskId = first.taskId)
        }
        compose.waitForIdle()
        stopFollowing()
        positionOf(previewTitle(1))
        positionOf(hasText("下一句话"))
        assertItemBefore("conversation-turn-0-preview", "current-user-message")
        compose.onAllNodesWithContentDescription("第2轮导入预览").assertCountEquals(0)

        compose.runOnIdle {
            session.updateActivity("second", AiImportActivity("**正在抓取", AiImportActivitySource.PROVIDER_SUMMARY))
        }
        positionOf(hasText("正在抓取"))
        compose.onNodeWithText("**正在抓取").assertDoesNotExist()
        compose.runOnIdle {
            session.updateActivity("second", AiImportActivity("**已抓取页面**，正在核对", AiImportActivitySource.PROVIDER_SUMMARY))
        }
        positionOf(hasText("已抓取页面，正在核对"))
        val status = compose.onNodeWithText("已抓取页面，正在核对").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(status.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        compose.onNodeWithContentDescription("正在导入中", substring = true).assertExists()
        compose.onNodeWithText("等待 AI 服务响应", substring = true).assertDoesNotExist()

        val revised = firstDraft.copy(courses = listOf(firstDraft.courses.single().copy(name = "修订课程")))
        compose.runOnIdle {
            session.publishCheckpoint("second", revised).getOrThrow()
            session.update(checkNotNull(session.progress.value).copy(finished = true, assistantMessage = "已更新课程"))
            // Persisted/restored dialogue must keep the same artifact order.
            session.update(progressFromJson(progressToJson(checkNotNull(session.progress.value))))
        }
        compose.waitForIdle()
        stopFollowing()
        compose.onNodeWithContentDescription("回到最新").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("回到最新").assertDoesNotExist()
        stopFollowing()
        positionOf(previewTitle(2))
        positionOf(hasText("下一句话"))
        assertItemBefore("current-user-message", "current-preview")
        positionOf(hasText("已更新课程"))
        compose.onNodeWithText("已更新课程").assertExists()

        val createOriginal = hasText("创建新课表") and hasAnyAncestor(hasContentDescription("第1轮导入预览"))
        positionOf(createOriginal)
        compose.onNode(createOriginal).performClick()
        compose.runOnIdle {
            assertEquals(1, imported.size)
            assertTrue(imported.single().second)
            assertEquals("原始课程", imported.single().first.courses.single().name)
        }
    }

    private fun stopFollowing() {
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitForIdle()
    }

    @Test fun floatingBrowserStaysMountedWhenCollapsedAndReturnsToItsSourceOnExit() {
        val visible = mutableStateOf(true)
        lateinit var browser: WebView
        lateinit var home: EduBrowserWebViewHost
        compose.runOnUiThread {
            // Robolectric's WebViewProvider is a deep proxy with no Chromium measure delegate.
            browser = object : WebView(compose.activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    setMeasuredDimension(android.view.View.MeasureSpec.getSize(widthMeasureSpec), android.view.View.MeasureSpec.getSize(heightMeasureSpec))
                }
            }.apply { loadUrl("https://fixture.invalid/portal") }
            home = EduBrowserWebViewHost(compose.activity, browser)
            AiEduBrowserSessions.bind("browser-task", checkNotNull(AiEduBrowserSessions.prepare(browser)))
            session.beginTask(AiEduImportProgress(taskId = "browser-task", requestSent = true))
        }
        try {
            compose.setContent { MaterialTheme { GlassMiuixSettingsTheme(config) { if (visible.value) AiEduImportProgressPage(config, onClose = {}) } } }
            compose.onNodeWithContentDescription("教务调试窗口").assertExists()
            val expanded = compose.onNodeWithContentDescription("教务调试窗口").fetchSemanticsNode().boundsInRoot
            val floating = browser.parent
            assertNotSame(home, floating)
            compose.runOnIdle {
                val host = browser.parent as android.view.View
                assertTrue("expanded web=${browser.width}x${browser.height}, measured=${browser.measuredWidth}x${browser.measuredHeight}, host=${host.width}x${host.height}, measuredHost=${host.measuredWidth}x${host.measuredHeight}", browser.height > 0)
            }
            compose.onNodeWithContentDescription("收起教务网页").performClick()
            compose.waitForIdle()
            val collapsed = compose.onNodeWithContentDescription("教务调试窗口").fetchSemanticsNode().boundsInRoot
            assertTrue(collapsed.height < expanded.height)
            compose.runOnIdle {
                assertSame(floating, browser.parent)
                assertTrue("收起不能把实际网页视口缩到零", browser.height > 0)
            }
            compose.onNodeWithContentDescription("拖动教务窗口").performTouchInput {
                swipe(center, center + Offset(-60f, 80f), durationMillis = 300)
            }
            val moved = compose.onNodeWithContentDescription("教务调试窗口").fetchSemanticsNode().boundsInRoot
            assertTrue(moved.left < collapsed.left)
            assertTrue(moved.top > collapsed.top)
            compose.onNodeWithContentDescription("展开教务网页").performClick()
            compose.waitForIdle()
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { assertSame(home, browser.parent) }
            compose.runOnIdle { visible.value = true }
            compose.waitForIdle()
            compose.runOnIdle { AiEduBrowserSessions.release(browser) }
            compose.onNodeWithContentDescription("教务调试窗口").assertDoesNotExist()
            assertNull(browser.parent)
        } finally {
            compose.runOnUiThread { AiEduBrowserSessions.release(browser); browser.destroy() }
        }
    }

    @Test fun adaptedSchoolImportButtonRunsTheAdapterWithoutOpeningAModeMenu() {
        var imports = 0
        var aiImports = 0
        val running = mutableStateOf(false)
        val adapted = mutableStateOf(true)
        compose.setContent { MaterialTheme { GlassMiuixSettingsTheme(config) {
            val backdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "dock-test") { drawContent() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().glassBackdropProducer(backdrop))
                EduBrowserDock(config = config, backdrop = backdrop,
                    address = "https://fixture.invalid/portal", onAddressChange = {}, onGo = {},
                    canGoBack = false, canGoForward = false, onBack = {}, onForward = {},
                    originalImportAvailable = adapted.value, originalImportRunning = running.value, aiImportRunning = false,
                    onOriginalImport = { imports++ }, onAiImport = { aiImports++ }, desktopMode = false,
                    onRefresh = {}, onToggleDesktopMode = {}, modifier = Modifier.fillMaxSize())
            }
        } } }
        compose.onNodeWithContentDescription("导入", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, imports); assertEquals(0, aiImports) }
        compose.onNodeWithText("AI 导入").assertDoesNotExist()
        compose.runOnIdle { running.value = true }
        compose.onNodeWithContentDescription("导入").assertIsNotEnabled()
        compose.runOnIdle { running.value = false; adapted.value = false }
        compose.onNodeWithContentDescription("导入", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, imports); assertEquals(1, aiImports) }
        compose.onNodeWithText("AI 导入").assertDoesNotExist()
    }

    @Test fun successfulScriptHasAnExplicitSaveActionAndSavingDoesNotImportCourses() {
        val context = compose.activity
        val taskId = "save-script-task"
        val name = "保存入口验证学校"
        val script = "// original source\n(async()=>{window.shiguangBridge.notifyTaskCompletion()})()"
        var courseImports = 0
        session.beginTask(AiEduImportProgress(taskId = taskId, finished = true), baseDraft = original)
        EduVerifiedScripts.record(taskId, name, "https://edu.example.edu/login", "adapter.js", "verified-hash", script)
        try {
            compose.setContent { MaterialTheme { GlassMiuixSettingsTheme(config) {
                AiEduImportProgressPage(config, onClose = {}, onImportRequested = { _, _ -> courseImports++ })
            } } }
            positionOf(hasText("保存当前脚本"))
            compose.onNodeWithText("保存当前脚本").performClick()
            compose.onNodeWithText("保存为我的适配？").assertExists()
            compose.onNodeWithText("查看脚本").performClick()
            compose.onNodeWithText(script).assertExists()
            compose.onNodeWithText("保存", substring = false).performClick()
            compose.waitUntil(10_000) { LocalEduAdapterStore.load(context).any { it.school.name == name } }
            compose.waitForIdle()
            val saved = LocalEduAdapterStore.load(context).single { it.school.name == name }
            assertEquals(script, LocalEduAdapterStore.source(context, saved))
            assertEquals(0, courseImports)
            positionOf(hasText("已保存到我的适配"))
            compose.onNodeWithText("已保存到我的适配").assertIsNotEnabled()
        } finally {
            LocalEduAdapterStore.load(context).filter { it.school.name == name }.forEach { LocalEduAdapterStore.delete(context, it) }
            EduVerifiedScripts.clear(taskId)
        }
    }

    private fun previewTitle(round: Int) = hasText("导入预览") and
        hasAnyAncestor(hasContentDescription("第${round}轮导入预览"))

    private fun positionOf(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        compose.waitForIdle()
        compose.onNode(matcher).assertExists()
    }

    private fun assertItemBefore(first: String, next: String) {
        // LazyList's accessibility scroll estimate is not a pixel coordinate. Use its actual
        // item order so collapsing status groups cannot turn this into a layout-size assertion.
        val indexForKey = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        val firstIndex = indexForKey(first)
        assertTrue("Missing list item $first", firstIndex >= 0)
        assertTrue("$next must follow $first", indexForKey(next) > firstIndex)
    }
}

/** Chromium's provider normally forwards this hidden method to View; Robolectric's proxy does not. */
@Implements(WebView::class)
class LayoutShadowWebView : ShadowWebView() {
    @RealObject private lateinit var target: WebView
    @Implementation
    protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        org.robolectric.shadow.api.Shadow.directlyOn(target, android.view.View::class.java, "setFrame",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, left),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, top),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, right),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, bottom))
}
