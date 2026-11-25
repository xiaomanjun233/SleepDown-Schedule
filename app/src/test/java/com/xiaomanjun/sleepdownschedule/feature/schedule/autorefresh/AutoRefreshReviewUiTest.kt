package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real review host, synthetic session/draft only; confirmation persistence is covered by Room tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoRefreshReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val config = defaultConfig()
    private var accepted: Pair<Set<Int>, Boolean>? = null
    private lateinit var pending: PendingScheduleRefresh

    @Before fun prepare() {
        val profile = AutoRefreshScheduleProfile("fixture", "测试学校", "fixture", "教务", 1)
        // Supply an in-memory profile, avoiding keystore, actual accounts and persisted credentials.
        (AutoRefreshScheduleStore.observe(compose.activity) as MutableStateFlow<AutoRefreshScheduleProfile?>).value = profile
        val course = CourseEntity(id = 1, name = "数学", teacher = null, location = "101", weekday = 1,
            periods = listOf(1, 2, 4), weeks = listOf(1), weekParity = WeekParity.ALL, note = null,
            customPeriodTimes = "1,08:00-08:45;2,08:55-09:40;4,10:45-11:30")
        val removed = course.copy(id = 2, name = "物理", location = "303", weekday = 3,
            periods = listOf(1, 2), customPeriodTimes = "2,08:55-09:40")
        pending = PendingScheduleRefresh.create(profile, AppState(config = config, courses = listOf(course, removed)),
            listOf(course.copy(id = 0, location = "202"),
                removed.copy(id = 0, name = "英语", weekday = 2, location = "404", customPeriodTimes = null)),
            config.copy(totalWeeks = config.totalWeeks + 1))
        AutoRefreshReviewStore.save(compose.activity, pending)
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    Scaffold(Modifier.fillMaxSize(), popupHost = {
                        MiuixPopupUtils.MiuixPopupHost()
                        AutoRefreshReviewHost(config, listOf(ScheduleProfileEntity(1, "测试课表")), confirmReview = { _, id, selected, term ->
                            assertEquals(pending.id, id)
                            accepted = selected to term
                            AutoRefreshReviewStore.clear(compose.activity)
                            "已保存"
                        })
                    }) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
    }

    @After fun clean() {
        AutoRefreshReviewStore.clear(compose.activity)
        (AutoRefreshScheduleStore.observe(compose.activity) as MutableStateFlow<AutoRefreshScheduleProfile?>).value = null
    }

    @Test fun aPendingRefreshAutomaticallyShowsAndDefaultsToKeepingEverything() {
        compose.onNodeWithText("核对课表更新").assertIsDisplayed()
        compose.onNodeWithText("${pending.count} 项变更 · 已选 0 项").assertIsDisplayed()
        compose.onNodeWithText("确认").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Int>() to false, accepted)
    }

    @Test fun confirmationKeepsOnlyTheSelectedChange() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("学期信息"))
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .assertIsOn()
        compose.onNodeWithText("确认").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Int>() to true, accepted)
    }

    @Test fun floatingActionSelectsAndClearsCoursesAndTermTogether() {
        compose.onNodeWithText("全选").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("全选").assertIsDisplayed().performClick()
        compose.onNodeWithText("${pending.count} 项变更 · 已选 ${pending.count} 项").assertIsDisplayed()
        compose.onNodeWithText("全不选").assertIsDisplayed().performClick()
        compose.onNodeWithText("${pending.count} 项变更 · 已选 0 项").assertIsDisplayed()
        compose.onNodeWithText("全不选").assertDoesNotExist()
        compose.onNodeWithText("全选").assertDoesNotExist()
        compose.onNodeWithText("确认").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Int>() to false, accepted)
    }

    @Test fun cancellationKeepsThePendingReviewWithoutSaving() {
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("核对课表更新").assertDoesNotExist()
        assertNull(accepted)
        assertEquals(pending.id, AutoRefreshReviewStore.load(compose.activity)?.id)
        assertEquals(pending.id, AutoRefreshReviewStore.deferredReviewId.value)
    }

    @Test fun sheetScrollsToTheOriginalAndUpdatedCourseValues() {
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("101"))
        val original = compose.onNodeWithText("101").assertIsDisplayed().getUnclippedBoundsInRoot()
        val changed = compose.onNodeWithText("202").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(original.bottom < changed.top)
        val originalFields = hasAnyAncestor(hasContentDescription("原有信息") and hasAnyDescendant(hasText("101")))
        val titleBounds = compose.onNodeWithText("数学", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val firstFieldBounds = compose.onNode(hasText("周一") and originalFields)
            .getUnclippedBoundsInRoot()
        assertTrue(firstFieldBounds.top - titleBounds.bottom <= 5.dp)
        compose.onNodeWithText("周一 · 第1–2、4节").assertDoesNotExist()
        val clockLabel = "第1–2节 08:00–09:40 · 第4节 10:45–11:30"
        compose.onAllNodesWithText(clockLabel).assertCountEquals(2)
        val clock = compose.onNode(hasText(clockLabel) and originalFields)
        val clockBounds = clock.getUnclippedBoundsInRoot()
        assertEquals(
            (original.top.value + original.bottom.value) / 2f,
            (clockBounds.top.value + clockBounds.bottom.value) / 2f,
            1f,
        )
        val arrangementIcon = compose.onNode(hasContentDescription("安排") and originalFields).getUnclippedBoundsInRoot()
        val timeIcon = compose.onNode(hasContentDescription("时间") and originalFields).getUnclippedBoundsInRoot()
        val weeksIcon = compose.onNode(hasContentDescription("周次") and originalFields).getUnclippedBoundsInRoot()
        val locationIcon = compose.onNode(hasContentDescription("地点") and originalFields).getUnclippedBoundsInRoot()
        assertEquals(arrangementIcon.left, timeIcon.left)
        assertEquals(weeksIcon.left, locationIcon.left)
        assertEquals(firstFieldBounds.left, clockBounds.left)
        assertTrue(timeIcon.right < clockBounds.left)
        assertTrue(clockBounds.left < locationIcon.left)
        assertTrue(locationIcon.right < original.left)
        compose.onNodeWithText("确认").assertIsDisplayed()
    }

    @Test fun customTimeChangeIsShownEvenWithAnUnchangedBellSnapshot() {
        val before = pending.before.first().copy(
            periods = listOf(1), periodTimes = null, originalTimes = "1,08:00-08:45",
            start = "09:00", end = "09:45",
        )
        compose.runOnIdle {
            AutoRefreshReviewStore.save(compose.activity, pending.copy(
                before = listOf(before),
                after = listOf(before.copy(id = 0, start = "09:15", end = "10:00")),
                beforeTerm = pending.afterTerm,
            ))
        }

        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("09:00–09:45"))
        compose.onNodeWithText("09:00–09:45").assertExists()
        list.performScrollToNode(hasText("09:15–10:00"))
        compose.onNodeWithText("09:15–10:00").assertExists()
        compose.onNodeWithText("第1节 08:00–08:45").assertDoesNotExist()
    }

    @Test fun removalDisplaysTheOriginalCourseAndOnlyMarksItsRemoval() {
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("303"))
        compose.onNodeWithText("303").assertIsDisplayed()
        compose.onNodeWithText("第1节 · 第2节 08:55–09:40").assertExists()
        compose.onNodeWithText("移除").assertIsDisplayed()
        compose.onNodeWithText("移除这条课程安排").assertDoesNotExist()
    }
}
