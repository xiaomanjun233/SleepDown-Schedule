package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
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

    @Test fun searchesWithinTheSelectedFeedbackCategory() {
        val service = InMemoryFeedback()
        val config = defaultConfig()
        compose.setContent {
            MaterialTheme {
                FeedbackScreen(config, service)
            }
        }

        waitForText("日历显示")
        compose.onNodeWithText("修复中").assertIsDisplayed()
        compose.onNodeWithText("功能建议").performClick()
        waitForText("此分类暂无反馈")
        assertEquals(FeedbackKind.Feature, service.lastKind)
        compose.onNodeWithContentDescription("搜索反馈").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("导入&课表")
        waitForText("未找到相关反馈")
        assertEquals("导入&课表", service.lastQuery)
        assertEquals(FeedbackKind.Feature, service.lastKind)

        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("新增反馈").assertIsDisplayed()
    }

    @Test fun togglesWhichTabsAreVisibleAndFallsBackWhenTheActiveTabIsHidden() {
        val service = InMemoryFeedback()
        compose.setContent { MaterialTheme { FeedbackScreen(defaultConfig(), service) } }
        waitForText("日历显示")
        compose.onNodeWithText("功能建议").performClick()
        waitForText("此分类暂无反馈")
        compose.onNodeWithContentDescription("选择展示的分类").performClick()
        val feature = compose.onNode(hasText("功能建议") and isToggleable())
        feature.assertIsOn().performClick()
        feature.assertIsOff()
        waitForText("日历显示")
        assertEquals(null, service.lastKind)
        compose.onNode(hasText("功能建议") and !isToggleable()).assertDoesNotExist()
        feature.performClick()
        compose.onNode(hasText("功能建议") and !isToggleable()).assertExists()
    }

    @Test fun editorSubmitsAnOptionalContactWithTheFeedback() {
        val service = InMemoryFeedback()
        var submitted = false
        compose.setContent {
            MaterialTheme { FeedbackEditor(defaultConfig(), service) { submitted = it } }
        }
        compose.onNodeWithText("新增反馈").assertIsDisplayed()
        compose.onNodeWithText("功能建议").performClick()
        compose.onNodeWithContentDescription("反馈标题").performTextInput("我的建议")
        compose.onNodeWithContentDescription("反馈描述").performTextInput("希望在课程卡显示教室")
        compose.onNodeWithContentDescription("联系方式").performTextInput("contact@example.invalid")
        compose.onNodeWithText("提交").performClick()
        compose.waitUntil { submitted }
        assertEquals("feature", service.submission?.kind)
        assertEquals("希望在课程卡显示教室", service.submission?.description)
        assertEquals("contact@example.invalid", service.contact)
    }

    @Test fun progressAreaOpensPublicDetailsWithoutDeleteActions() {
        compose.setContent { MaterialTheme { FeedbackScreen(defaultConfig(), InMemoryFeedback()) } }
        waitForText("日历显示")
        compose.onNode(hasText("日历显示") and hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .performTouchInput { click(Offset(center.x / 2, bottom - 18.dp.toPx())) }

        waitForText("日期显示反馈")
        compose.onNodeWithText("日期显示反馈").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除反馈").assertDoesNotExist()

        compose.onNodeWithContentDescription("返回").performClick()
        waitForText("日历显示")
        compose.onNodeWithText("日期显示反馈").assertDoesNotExist()
        compose.onNodeWithText("日历显示").assertIsDisplayed()
    }

    @Test fun ownFeedbackCanBeDeletedByLongPressOrFromItsDetails() {
        val service = InMemoryFeedback()
        service.submission = FeedbackItem("owned", "feature", "我的建议", "原始正文", "received",
            contact = "contact@example.invalid", note = "开发者已收到", createdAt = 2, updatedAt = 2)
        compose.setContent { MaterialTheme { FeedbackScreen(defaultConfig(), service) } }
        waitForText("日历显示")
        compose.onNodeWithText("我的反馈").performScrollTo().performClick()
        waitForText("已收到的建议")
        val row = compose.onNode(hasText("已收到的建议") and hasClickAction())
        row.performTouchInput { longClick(Offset(center.x / 2, bottom - 18.dp.toPx())) }
        compose.onNodeWithText("删除这条反馈？").assertIsDisplayed()
        compose.onNode(hasText("取消") and isEnabled()).performClick()

        row.performClick()
        compose.onNodeWithText("原始正文").assertIsDisplayed()
        compose.onNodeWithText("开发者已收到").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除反馈").performClick()
        compose.onNodeWithText("删除").performClick()
        waitForText("此分类暂无反馈")
        assertEquals("owned", service.deletedId)
        compose.onNodeWithText("已收到的建议").assertDoesNotExist()
    }

    @Test fun usesTheSameFeedbackTokenForSearchSubmitHistoryAndDeletion() = runBlocking {
        MockWebServer().use { server ->
            val item = """{"id":"entry","kind":"feature","title":"建议","description":"希望在课程卡显示教室","status":"received","createdAt":1,"updatedAt":1}"""
            server.enqueue(MockResponse().setBody("""{"items":[],"total":0}"""))
            server.enqueue(MockResponse().setResponseCode(201).setBody(item))
            server.enqueue(MockResponse().setBody("""{"items":[$item],"total":1}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val client = FeedbackClient(RuntimeEnvironment.getApplication(), server.url("/").toString())
            assertEquals(0, client.list(own = false, page = 1, query = "导入&课表",
                kind = FeedbackKind.Feature, status = FeedbackStatus.InProgress).total)
            assertEquals("entry", client.submit("entry", FeedbackKind.Feature, "建议", "希望在课程卡显示教室", "").id)
            assertEquals(1, client.list(own = true, page = 0, query = "").total)
            client.delete("entry")
            val roadmap = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("/api/v1/feedback/roadmap", roadmap?.requestUrl?.encodedPath)
            assertEquals("导入&课表", roadmap?.requestUrl?.queryParameter("q"))
            assertEquals("20", roadmap?.requestUrl?.queryParameter("offset"))
            assertEquals("feature", roadmap?.requestUrl?.queryParameter("kind"))
            assertEquals("in_progress", roadmap?.requestUrl?.queryParameter("status"))
            val submission = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("POST", submission?.method)
            val body = submission?.body?.readUtf8().orEmpty()
            assertTrue(body.contains("\"kind\":\"feature\""))
            assertTrue(body.contains("希望在课程卡显示教室"))
            assertTrue(!body.contains("\"contact\""))
            val token = submission?.getHeader("X-SleepDown-Feedback-Token")
            assertNotNull(token)
            assertEquals(token, roadmap?.getHeader("X-SleepDown-Feedback-Token"))
            val mine = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("/api/v1/feedback/mine", mine?.requestUrl?.encodedPath)
            assertEquals(token, mine?.getHeader("X-SleepDown-Feedback-Token"))
            val removal = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("DELETE", removal?.method)
            assertEquals("/api/v1/feedback/mine/entry", removal?.requestUrl?.encodedPath)
            assertEquals(token, removal?.getHeader("X-SleepDown-Feedback-Token"))
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
        var lastKind: FeedbackKind? = null
        var submission: FeedbackItem? = null
        var contact = ""
        var deletedId: String? = null

        override suspend fun list(
            own: Boolean, page: Int, query: String, kind: FeedbackKind?, status: FeedbackStatus?,
        ): FeedbackPage {
            lastQuery = query
            lastKind = kind
            val items = if (own) listOfNotNull(submission?.copy(title = "已收到的建议")) else listOf(roadmap)
            val matching = items.filter { it.title.contains(query) &&
                (kind == null || it.kind == kind.wire) && (status == null || it.status == status.wire) }
            return FeedbackPage(matching, matching.size)
        }

        override suspend fun submit(
            id: String, kind: FeedbackKind, title: String, description: String, contact: String,
        ): FeedbackItem {
            this.contact = contact
            return FeedbackItem(id, kind.wire, title, description, "received", createdAt = 2, updatedAt = 2)
                .also { submission = it }
        }

        override suspend fun delete(id: String) {
            check(submission?.id == id)
            deletedId = id
            submission = null
        }
    }
}
