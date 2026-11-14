package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.xiaomanjun.sleepdownschedule.feature.importing.history.AiImportHistorySwipeRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiImportHistorySwipeRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun crossingTheDeleteThresholdOnlyCommitsOnRelease() {
        var deleted = 0
        val entry = AiImportHistoryEntry("test", 0, "临时导入记录", "", "", "")
        compose.setContent {
            MaterialTheme {
                var revealed by remember { mutableStateOf(false) }
                AiImportHistorySwipeRow(entry, Color.Black,
                    selection = remember { com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeletionSelection() },
                    onStartSelection = {}, revealed = revealed, onRevealChange = { revealed = it },
                    onDelete = { deleted++ }, onDeleteFailed = { error(it) }, onOpen = {}, onBoundsChanged = {})
            }
        }
        val row = compose.onNodeWithText(entry.title).assertIsDisplayed()
        row.performTouchInput {
            down(Offset(width * .9f, centerY))
            moveTo(Offset(width * .15f, centerY))
            cancel()
        }
        compose.runOnIdle { assertEquals(0, deleted) }
        row.performTouchInput { swipeLeft(startX = width * .9f, endX = width * .68f, durationMillis = 350) }
        compose.onNodeWithContentDescription("删除").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deleted) }
        row.performTouchInput { swipeLeft(startX = width * .8f, endX = width * .1f, durationMillis = 350) }
        compose.runOnIdle { assertEquals(1, deleted) }
    }
}
