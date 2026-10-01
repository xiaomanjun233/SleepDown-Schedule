package com.xiaomanjun.sleepdownschedule.feature.course.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.feature.home.day.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.LandscapePageTransition

/** Selection keeps the original group snapshot until its draft has been saved or discarded. */
@Composable
internal fun LandscapeCourseManagementPane(
    state: AppState,
    onSave: (List<CourseEntity>, List<CourseEntity>, (Boolean) -> Unit) -> Unit,
    onExitHandlerChange: (((() -> Unit) -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(state.courses) { buildManagedCourseGroups(state.courses) }
    var selected by remember(state.config.id) { mutableStateOf(groups.firstOrNull()) }
    var exitHandler by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    var selectionMoving by remember { mutableStateOf(false) }
    val latestExitHandler by rememberUpdatedState(exitHandler)
    val latestSelectionMoving by rememberUpdatedState(selectionMoving)
    val latestOnExitHandlerChange by rememberUpdatedState(onExitHandlerChange)
    val requestExit = remember {
        { action: () -> Unit ->
            if (!latestSelectionMoving) latestExitHandler?.invoke(action) ?: action()
        }
    }
    DisposableEffect(Unit) {
        latestOnExitHandlerChange(requestExit)
        onDispose { latestOnExitHandlerChange(null) }
    }
    fun selectGroup(group: ManagedCourseGroup?) {
        exitHandler = null
        selected = group
    }
    val ink = appPanelForegroundColor(state.config)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.36f).coerceIn(152.dp, 320.dp).coerceAtMost(maxWidth * 0.46f)
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(listWidth).fillMaxHeight(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 12.dp,
                    bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("课程管理", color = ink, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp)) }
                items(groups, key = { it.key }) { group ->
                    val active = selected?.courses?.any { course -> group.courses.any { it.id == course.id } } == true
                    ManagedCourseListCardContent(group, state.config, state.periods,
                        Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                            .clickable(enabled = !selectionMoving) {
                                if (!active) {
                                    val select = { selectGroup(group) }
                                    exitHandler?.invoke(select) ?: select()
                                }
                            }.padding(12.dp))
                }
                if (groups.isEmpty()) item { Text("还没有课程，可从侧栏添加或导入。", color = ink.copy(alpha = 0.64f)) }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(ink.copy(alpha = 0.10f)))
            LandscapePageTransition(selected, Modifier.weight(1f).fillMaxHeight(), onMovingChange = { selectionMoving = it }) { group ->
                if (group == null) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("选择左侧课程查看与编辑安排", color = ink.copy(alpha = 0.60f))
                    }
                } else key(state.config.id, group.key) {
                    CourseManagementDetailPage(group = group, state = state,
                        onBack = { selectGroup(null) }, onSave = {},
                        onSaveAndThen = { edited, done -> onSave(group.courses, edited, done) },
                        onExitHandlerChange = { handler ->
                            if (selected?.key == group.key) exitHandler = handler
                        })
                }
            }
        }
    }
}
