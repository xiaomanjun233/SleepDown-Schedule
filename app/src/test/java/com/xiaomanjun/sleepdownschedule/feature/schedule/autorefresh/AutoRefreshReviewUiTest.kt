package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        val profile = AutoRefreshScheduleProfile("fixture", "测试学校", "fixture", "教务", "", "", 1)
        // Supply an in-memory profile, avoiding keystore, actual accounts and persisted credentials.
        (AutoRefreshScheduleStore.observe(compose.activity) as MutableStateFlow<AutoRefreshScheduleProfile?>).value = profile
        val course = CourseEntity(id = 1, name = "数学", teacher = null, location = "101", weekday = 1,
            periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)
        pending = PendingScheduleRefresh.create(profile, AppState(config = config, courses = listOf(course)),
            listOf(course.copy(id = 0, location = "202"), course.copy(id = 0, name = "英语", weekday = 2)),
            config.copy(totalWeeks = config.totalWeeks + 1))
        AutoRefreshReviewStore.save(compose.activity, pending)
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    Scaffold(Modifier.fillMaxSize(), popupHost = {
                        MiuixPopupUtils.MiuixPopupHost()
                        AutoRefreshReviewHost(config, null, emptyList(), confirmReview = { _, id, selected, term ->
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
        compose.onNodeWithText("课表有 ${pending.count} 处不同").assertIsDisplayed()
        compose.onNodeWithText("确认选择").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Int>() to false, accepted)
    }

    @Test fun adoptAllThenChooseOneCourseSeparately() {
        compose.onNodeWithText("全部采用").performClick()
        // The first toggle is the term, followed by individual course differences.
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("确认选择").performClick()
        compose.waitForIdle()
        assertEquals(setOf(1) to true, accepted)
    }
}
