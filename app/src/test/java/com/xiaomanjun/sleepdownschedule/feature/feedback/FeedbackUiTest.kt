package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-320dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchesRoadmapAndReturnsToMineAfterSubmitting() {
        val service = InMemoryFeedback()
        val config = defaultConfig()
        compose.setContent {
            MaterialTheme {
                DetailActivityScaffold("问题反馈", config, onBack = {}) { backdrop ->
                    FeedbackScreen(config, backdrop, service)
                }
            }
        }

        waitForText("日历显示")
        compose.onNodeWithText("修复中").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索反馈").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("导入&课表")
        waitForText("未找到相关反馈")
        assertEquals("导入&课表", service.lastQuery)

        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("新增反馈").performClick()
        compose.onNodeWithText("新增反馈").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("新增反馈").assertDoesNotExist()
        compose.onNodeWithContentDescription("新增反馈").performClick()
        compose.onNodeWithText("功能建议").performClick()
        compose.onNodeWithContentDescription("反馈标题").performTextInput("我的建议")
        compose.onNodeWithContentDescription("反馈描述").performTextInput("希望在课程卡显示教室")
        compose.onNodeWithText("提交").performClick()
        waitForText("已收到的建议")
        compose.onNodeWithText("新增反馈").assertDoesNotExist()
        assertEquals("feature", service.submission?.kind)
        assertEquals("希望在课程卡显示教室", service.submission?.description)
    }

    @Test fun sendsSearchAndSubmitsWithTheSamePrivateToken() = runBlocking {
        MockWebServer().use { server ->
            val item = """{"id":"entry","kind":"feature","title":"建议","description":"希望在课程卡显示教室","status":"received","createdAt":1,"updatedAt":1}"""
            server.enqueue(MockResponse().setBody("""{"items":[],"total":0}"""))
            server.enqueue(MockResponse().setResponseCode(201).setBody(item))
            server.enqueue(MockResponse().setBody("""{"items":[$item],"total":1}"""))
            server.start()
            val client = FeedbackClient(RuntimeEnvironment.getApplication(), server.url("/").toString())
            assertEquals(0, client.list(own = false, page = 1, query = "导入&课表").total)
            assertEquals("entry", client.submit("entry", FeedbackKind.Feature, "建议", "希望在课程卡显示教室").id)
            assertEquals(1, client.list(own = true, page = 0, query = "").total)
            val roadmap = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("/api/v1/feedback/roadmap", roadmap?.requestUrl?.encodedPath)
            assertEquals("导入&课表", roadmap?.requestUrl?.queryParameter("q"))
            assertEquals("20", roadmap?.requestUrl?.queryParameter("offset"))
            val submission = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("POST", submission?.method)
            val body = submission?.body?.readUtf8().orEmpty()
            assertTrue(body.contains("\"kind\":\"feature\""))
            assertTrue(body.contains("希望在课程卡显示教室"))
            val token = submission?.getHeader("X-SleepDown-Feedback-Token")
            assertNotNull(token)
            val mine = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("/api/v1/feedback/mine", mine?.requestUrl?.encodedPath)
            assertEquals(token, mine?.getHeader("X-SleepDown-Feedback-Token"))
        }
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil("visible text $text", 10_000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
    }

    private class InMemoryFeedback : FeedbackService {
        private val roadmap = FeedbackItem("roadmap", "bug", "日历显示", "日期显示反馈", "in_progress",
            createdAt = 1, updatedAt = 1)
        var lastQuery = ""
        var submission: FeedbackItem? = null

        override suspend fun list(own: Boolean, page: Int, query: String): FeedbackPage {
            lastQuery = query
            val items = if (own) listOfNotNull(submission?.copy(title = "已收到的建议")) else listOf(roadmap)
            val matching = items.filter { it.title.contains(query) }
            return FeedbackPage(matching, matching.size)
        }

        override suspend fun submit(id: String, kind: FeedbackKind, title: String, description: String): FeedbackItem =
            FeedbackItem(id, kind.wire, title, description, "received", createdAt = 2, updatedAt = 2)
                .also { submission = it }
    }
}
