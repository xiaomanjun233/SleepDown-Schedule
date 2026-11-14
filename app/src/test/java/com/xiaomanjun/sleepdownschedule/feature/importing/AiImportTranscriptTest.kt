package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.rememberAiImportFollowingLatest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiImportTranscriptTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun keyboardPaddingAndWindowResizeKeepTheLatestMessageAboveTheComposer() {
        val bottom = mutableStateOf(16.dp)
        val height = mutableStateOf(800.dp)
        lateinit var viewport: LazyListState
        compose.setContent {
            MaterialTheme {
                val list = rememberLazyListState()
                rememberAiImportFollowingLatest(list, "keyboard-fixture")
                SideEffect { viewport = list }
                LazyColumn(Modifier.fillMaxWidth().height(height.value).testTag("keyboard-conversation"), state = list,
                    contentPadding = PaddingValues(bottom = bottom.value)) {
                    items((0 until 25).toList(), key = { it }) { index ->
                        Text("消息 $index", Modifier.fillMaxWidth().height(90.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(viewport.canScrollForward) }
        for (inset in listOf(96.dp, 180.dp, 280.dp)) {
            compose.runOnIdle { bottom.value = inset }
            compose.waitForIdle()
            compose.runOnIdle { assertFalse("Latest message must clear the animated composer inset", viewport.canScrollForward) }
        }
        compose.runOnIdle { height.value = 500.dp; bottom.value = 80.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("Resized windows must stay at the end", viewport.canScrollForward) }
        compose.onNodeWithText("消息 24").assertIsDisplayed()
        compose.onNodeWithTag("keyboard-conversation").performTouchInput { swipeDown() }
        compose.waitForIdle()
        var before = 0
        compose.runOnIdle { before = viewport.firstVisibleItemIndex }
        compose.runOnIdle { height.value = 450.dp; bottom.value = 180.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Reading earlier messages must keep its anchor", before, viewport.firstVisibleItemIndex) }
    }

    @Test fun newMessagesFollowTheEndButDoNotPullTheUserAwayFromEarlierDialogue() {
        val count = mutableIntStateOf(25)
        lateinit var viewport: LazyListState
        lateinit var following: State<Boolean>
        compose.setContent {
            MaterialTheme {
                val list = rememberLazyListState()
                val follow = rememberAiImportFollowingLatest(list, "fixture")
                SideEffect { viewport = list; following = follow }
                LazyColumn(Modifier.fillMaxSize().testTag("conversation"), state = list) {
                    items((0 until count.intValue).toList(), key = { it }) { index ->
                        Text("消息 $index", Modifier.fillMaxWidth().height(90.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("消息 24").assertIsDisplayed()
        compose.runOnIdle { count.intValue = 27 }
        compose.waitForIdle()
        compose.onNodeWithText("消息 26").assertIsDisplayed()
        compose.onNodeWithTag("conversation").performTouchInput { swipeDown() }
        compose.waitForIdle()
        var before = 0
        compose.runOnIdle { before = viewport.firstVisibleItemIndex; assertFalse(following.value) }
        compose.runOnIdle { count.intValue = 30 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(before, viewport.firstVisibleItemIndex) }
        compose.onNodeWithText("消息 29").assertDoesNotExist()
    }
}
