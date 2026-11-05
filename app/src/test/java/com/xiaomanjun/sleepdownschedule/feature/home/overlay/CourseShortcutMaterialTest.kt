package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialLevel
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseBlock
import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val LocalShortcutFixtureOwner = staticCompositionLocalOf { "source" }

/** Exercise the real source/overlay handoff; only the unavailable vendor renderer is replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CourseShortcutMaterialTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortcutStopsHiddenSourceMaterialAndRestoresItWithoutRemounting() {
        AppMaterialPreferences.setLevel(RuntimeEnvironment.getApplication(), AppMaterialLevel.QUALITY)
        val course = CourseEntity(1, "测试课程", null, null, 1, listOf(1), listOf(1), WeekParity.ALL, null)
        val config = defaultConfig()
        val visibility = mutableMapOf<String, Boolean>()
        val mounts = mutableMapOf<String, Int>()
        lateinit var shortcuts: CourseShortcutController
        val mode = NativeCourseCardMode(mutableStateOf(true), {}, mutableFloatStateOf(0.5f),
            mutableFloatStateOf(1f), 24f, { _, _ -> }, { _, _ -> }, {})
        val controls = object : HomeMaterialControls {
            @Composable override fun ShortcutMenu(session: Any, bounds: Rect, dark: Boolean,
                actions: List<AddMenuAction>, closing: Boolean, onDismiss: () -> Unit,
                bindDismiss: (((() -> Unit) -> Unit)?) -> Unit) = true
        }
        val renderer: @Composable (NativeCourseCardRequest, @Composable () -> Unit) -> Boolean = { request, content ->
            val owner = LocalShortcutFixtureOwner.current
            SideEffect { visibility[owner] = request.materialVisible }
            DisposableEffect(owner) {
                mounts[owner] = (mounts[owner] ?: 0) + 1
                onDispose { visibility.remove(owner) }
            }
            Box(request.modifier) { content() }
            true
        }
        compose.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val controller = remember(scope) { CourseShortcutController(scope) }
                SideEffect { shortcuts = controller }
                CompositionLocalProvider(LocalCourseShortcuts provides controller,
                    LocalNativeCourseCardMode provides mode, LocalNativeCourseCardRenderer provides renderer,
                    LocalHomeMaterialControls provides controls) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.offset(40.dp, 160.dp).size(90.dp, 120.dp)) {
                            WeekCourseBlock(course, listOf(PeriodEntity(1, "08:00", "08:45")),
                                height = 120.dp, cardColor = Color.Blue, backdrop = null,
                                config = config, shortcutPivotX = 0f, gridColumnWidth = 90.dp,
                                onCourseClick = { _, _ -> })
                        }
                        CompositionLocalProvider(LocalShortcutFixtureOwner provides "lifted") {
                            CourseShortcutOverlay(controller, config, null, null,
                                onEdit = {}, onCopy = { _, _ -> }, onRemove = { _, _ -> })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(mapOf("source" to true), visibility) }
        repeat(2) {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNotNull(shortcuts.request)
                assertEquals(false, visibility["source"])
                assertEquals(true, visibility["lifted"])
                assertEquals(1, mounts["source"])
                shortcuts.close()
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull(shortcuts.request)
                assertEquals(mapOf("source" to true), visibility)
                assertEquals(1, mounts["source"])
            }
        }
    }
}
