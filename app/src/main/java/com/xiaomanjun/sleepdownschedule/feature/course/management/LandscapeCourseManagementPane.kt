package com.xiaomanjun.sleepdownschedule.feature.course.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeleteBottomSpace
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeletionSelection
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardSelectionChrome
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CourseManagementCardShape
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.cardSelectionInteraction
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.LandscapePageTransition
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.animateScrollToTop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsTheme
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor

/** Selection keeps the original group snapshot until its draft has been saved or discarded. */
@Composable
internal fun LandscapeCourseManagementPane(
    state: AppState,
    onSave: (List<CourseEntity>, List<CourseEntity>, (Boolean) -> Unit) -> Unit,
    onDelete: suspend (List<CourseEntity>) -> Unit,
    onExitHandlerChange: (((() -> Unit) -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(state.courses) { buildManagedCourseGroups(state.courses) }
    val selection = remember(state.config.id) { CardDeletionSelection() }
    var selected by remember(state.config.id) { mutableStateOf(groups.firstOrNull()) }
    var exitHandler by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    var selectionMoving by remember { mutableStateOf(false) }
    val latestExitHandler by rememberUpdatedState(exitHandler)
    val latestSelectionMoving by rememberUpdatedState(selectionMoving)
    val latestOnExitHandlerChange by rememberUpdatedState(onExitHandlerChange)
    val requestExit = remember(selection) {
        { action: () -> Unit ->
            if (!latestSelectionMoving && !selection.deleting) latestExitHandler?.invoke(action) ?: action()
        }
    }
    DisposableEffect(requestExit) {
        latestOnExitHandlerChange(requestExit)
        onDispose { latestOnExitHandlerChange(null) }
    }
    fun selectGroup(group: ManagedCourseGroup?) {
        exitHandler = null
        selected = group
    }
    val ink = appPanelForegroundColor(state.config)
    val pageConfig = settingsVisualConfig(state.config)
    val pageColor = settingsPageBackground(pageConfig)
    val dark = appUsesDarkTheme(pageConfig)
    val listState = rememberLazyStaggeredGridState()
    val listBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "landscape-course-management-list") {
        drawRect(pageColor)
        drawContent()
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.48f).coerceIn(280.dp, 520.dp).coerceAtMost(maxWidth * 0.52f)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                val bottomSpace = if (selection.active) CardDeleteBottomSpace else 12.dp
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2), state = listState,
                    modifier = Modifier.fillMaxSize().glassBackdropProducer(listBackdrop).animateScrollToTop {
                        if (listState.canScrollBackward) listState.animateScrollToItem(0)
                    },
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                        top = insets.calculateTopPadding() + 12.dp,
                        bottom = insets.calculateBottomPadding() + bottomSpace),
                    verticalItemSpacing = 10.dp, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        val title = if (selection.active) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { }
                            else Modifier
                        Text("课程管理", color = ink, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, modifier = title.padding(vertical = 12.dp))
                    }
                    items(groups, key = { it.key }) { group ->
                        val active = selected?.courses?.any { course -> group.courses.any { it.id == course.id } } == true
                        val gesture = Modifier.fillMaxWidth().clip(CourseManagementCardShape)
                            .cardSelectionInteraction(selection, group.key, !selectionMoving, dark, onLongClick = {
                                val startSelection = {
                                    selectGroup(null)
                                    selection.start(group.key)
                                }
                                exitHandler?.invoke(startSelection) ?: startSelection()
                            }) {
                                if (!active) {
                                    val open = { selectGroup(group) }
                                    exitHandler?.invoke(open) ?: open()
                                }
                            }
                        ManagedCourseListCardContent(group, state.config, state.periods, gesture,
                            selected = active,
                            checkedForDeletion = if (selection.active) group.key in selection.selectedKeys else null,
                            selectionEnabled = !selection.deleting)
                    }
                    if (groups.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                        Text("还没有课程，可从侧栏添加或导入。", color = ink.copy(alpha = 0.64f))
                    }
                }
                CouiSettingsTheme(pageConfig) {
                    CardSelectionChrome(
                        selection = selection,
                        cards = remember(groups) { groups.associateBy(ManagedCourseGroup::key) },
                        config = pageConfig,
                        backdrop = listBackdrop,
                        confirmationTitle = "删除课程？",
                        confirmationMessage = { count -> "将删除 ${count} 门课程及其全部安排。" },
                        onDelete = { checked -> onDelete(checked.flatMap(ManagedCourseGroup::courses)) }
                    )
                }
            }
            Box(Modifier.padding(vertical = 28.dp).width(0.5.dp).fillMaxHeight().background(ink.copy(alpha = 0.10f)))
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
