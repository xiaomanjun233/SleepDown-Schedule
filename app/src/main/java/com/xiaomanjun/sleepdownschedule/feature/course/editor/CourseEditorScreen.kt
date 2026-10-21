package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalTimeSegments
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardAllowsCustomOverrides
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardBaseColor
import kotlinx.coroutines.flow.map

@Composable
fun NormalizedCourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onSaveCourses: ((List<CourseEntity>) -> Unit)? = null,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?,
    pickerRenderInRootScaffold: Boolean = true,
    copyDraft: CourseEntity? = null,
    contextMessage: String? = null,
    onDismissHandlerChange: (((() -> Unit)?) -> Unit)? = null
) {
    val formData = remember(state.config, state.periods, state.courses) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods,
            courses = state.courses
        )
    }
    NormalizedCourseEditorScreen(
        formData = formData,
        initialCourse = initialCourse,
        onCancel = onCancel,
        onSave = onSave,
        onSaveCourses = onSaveCourses,
        onDelete = onDelete,
        backdrop = backdrop,
        pickerRenderInRootScaffold = pickerRenderInRootScaffold,
        copyDraft = copyDraft,
        contextMessage = contextMessage,
        onDismissHandlerChange = onDismissHandlerChange
    )
}

@OptIn(ExperimentalFoundationApi::class)
data class CourseEditorPagerPresentation(
    val pagerState: PagerState,
    val pageCount: Int,
    val visible: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalizedCourseEditorScreen(
    formData: CourseEditorFormData,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onSaveCourses: ((List<CourseEntity>) -> Unit)? = null,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?,
    onSaveWithOriginal: ((CourseEntity, CourseEntity) -> Unit)? = null,
    onSaveGroup: ((List<CourseEntity>, List<CourseEntity>) -> Unit)? = null,
    onDeleteGroup: ((List<CourseEntity>) -> Unit)? = null,
    pickerRenderInRootScaffold: Boolean = true,
    renderPagerIndicator: Boolean = true,
    onPagerPresentationChange: ((CourseEditorPagerPresentation) -> Unit)? = null,
    rowEntrance: (Int) -> Float = { _ -> 1f },
    copyDraft: CourseEntity? = null,
    contextMessage: String? = null,
    onDismissHandlerChange: (((() -> Unit)?) -> Unit)? = null
) {
    val config = formData.config
    val editorGroups = remember(initialCourse, formData.courses) {
        buildCourseEditorGroups(initialCourse?.originalArrangement(), formData.courses.map { it.originalArrangement() })
    }
    val periodValues = remember(formData.periods, editorGroups) {
        (formData.periods.map { it.periodIndex } + editorGroups.flatMap { group -> group.courses.flatMap(CourseEntity::periods) })
            .distinct()
            .sorted()
    }
    val initialPage = remember(initialCourse, editorGroups) {
        editorGroups.indexOfFirst { group -> group.courses.any { it.id == initialCourse?.id } }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { editorGroups.size }
    val initialDrafts = remember(editorGroups, periodValues, config.totalWeeks, copyDraft) {
        editorGroups.mapIndexed { index, group ->
            index to if (copyDraft != null && initialCourse == null) {
                // A copy is a new draft, never a member of the source course's edit group.
                // Keep its content and selected weeks, but require a fresh time selection.
                courseEditorCopyDraft(copyDraft, periodValues, config.totalWeeks.coerceAtLeast(1))
            } else courseEditorDraft(group.courses, periodValues, config.totalWeeks.coerceAtLeast(1))
        }.toMap()
    }
    var drafts by remember(initialDrafts) { mutableStateOf(initialDrafts) }
    var showExitConfirm by remember(initialDrafts) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var colorPickerPage by remember { mutableStateOf<Int?>(null) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    val currentPage = pagerState.currentPage.coerceIn(editorGroups.indices)
    val pagerIndicatorVisible = editorGroups.size > 1 && pickerRequest == null && colorPickerPage == null

    fun savePage(page: Int) {
        val group = editorGroups[page]
        val course = group.representative
        val currentDraft = drafts.getValue(page)
        val edited = currentDraft.toCourses(
            originals = group.courses,
            periodValues = periodValues,
            allowCustomColorOverride = courseCardAllowsCustomOverrides(config)
        )
        when {
            currentDraft.name.isBlank() -> error = "课程名称不能为空"
            currentDraft.weekdays.isEmpty() -> error = "请选择星期"
            currentDraft.weeks.isEmpty() -> error = "请选择周次"
            edited.firstOrNull()?.periods.isNullOrEmpty() -> error = "请选择节次"
            group.courses.isNotEmpty() && onSaveGroup != null -> onSaveGroup(group.courses, edited)
            course != null && edited.size == 1 && onSaveWithOriginal != null -> onSaveWithOriginal(course, edited.single())
            onSaveCourses != null -> onSaveCourses(edited)
            edited.size == 1 -> onSave(edited.single())
            else -> edited.forEach(onSave)
        }
    }

    fun requestCancel() {
        when {
            pickerRequest != null -> pickerVisible = false
            colorPickerPage != null -> colorPickerVisible = false
            drafts != initialDrafts -> showExitConfirm = true
            else -> onCancel()
        }
    }
    val latestRequestCancel by rememberUpdatedState<() -> Unit>({ requestCancel() })
    DisposableEffect(onDismissHandlerChange) {
        onDismissHandlerChange?.invoke { latestRequestCancel() }
        onDispose { onDismissHandlerChange?.invoke(null) }
    }
    BackHandler(enabled = pickerRequest == null && colorPickerPage == null) { requestCancel() }

    SideEffect {
        onPagerPresentationChange?.invoke(
            CourseEditorPagerPresentation(
                pagerState = pagerState,
                pageCount = editorGroups.size,
                visible = pagerIndicatorVisible
            )
        )
    }

    LaunchedEffect(currentPage) { error = null }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            pageSpacing = 10.dp,
            userScrollEnabled = pickerRequest == null && colorPickerPage == null,
            key = { page -> editorGroups[page].representative?.id ?: Long.MIN_VALUE }
        ) { page ->
            val group = editorGroups[page]
            val course = group.representative
            val draft = drafts.getValue(page)
            val visibleCourse = formData.courses.firstOrNull { it.id == course?.id }
                ?: initialCourse?.takeIf { it.id == course?.id }
            val hidden = visibleCourse?.arrangementProjection?.hiddenPeriods.orEmpty()
            val originalTimes = course?.originalTimeSegments().orEmpty().joinToString("；") { "${it.start}–${it.end}" }
            val pageContext = listOfNotNull(contextMessage?.takeIf { group.courses.any { it.id == initialCourse?.id } },
                if (hidden.isNotEmpty()) buildString {
                    append("原始安排：${course?.periods?.joinToString("、")} 节")
                    if (originalTimes.isNotBlank()) append(" · $originalTimes")
                    append("\n${hidden.joinToString("、")} 节当前未显示。")
                } else null)
                .takeIf { it.isNotEmpty() }?.joinToString("\n")
            CourseEditorFormPage(
                course = course,
                title = when {
                    copyDraft != null -> "复制课程"
                    course == null -> "添加单节课"
                    pageContext != null -> "编辑原课程"
                    else -> "编辑单节课"
                },
                groupedCourses = group.courses,
                contextMessage = pageContext,
                draft = draft,
                onDraftChange = { drafts = drafts + (page to it); error = null },
                periodValues = periodValues,
                periods = formData.periods,
                totalWeeks = config.totalWeeks.coerceAtLeast(1),
                config = config,
                backdrop = backdrop,
                error = error.takeIf { page == currentPage },
                onCancel = { requestCancel() },
                onSave = { savePage(page) },
                onDelete = course?.let {
                    {
                        if (onDeleteGroup != null) onDeleteGroup(group.courses) else onDelete(it)
                    }
                },
                onOpenPicker = {
                    pickerRequest = it
                    pickerVisible = true
                },
                onOpenColorPicker = {
                    colorPickerPage = page
                    colorPickerVisible = true
                },
                pageCount = editorGroups.size,
                rowEntrance = rowEntrance
            )
        }
        if (renderPagerIndicator && pagerIndicatorVisible) {
            ProjectPagerIndicator(
                pagerState = pagerState,
                pageCount = editorGroups.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f)
                    .padding(bottom = 10.dp)
            )
        }
        pickerRequest?.let { request ->
            CourseEditorPickerOverlay(
                show = pickerVisible,
                request = request,
                backdrop = backdrop,
                config = config,
                renderInRootScaffold = pickerRenderInRootScaffold,
                onDismiss = { pickerVisible = false },
                onDismissFinished = {
                    if (!pickerVisible) pickerRequest = null
                }
            )
        }
        colorPickerPage?.let { page ->
            CourseColorPicker(
                show = colorPickerVisible,
                selectedColorArgb = drafts.getValue(page).customColorArgb,
                automaticColorArgb = courseCardBaseColor(
                    config,
                    editorGroups[page].representative?.copy(customColorArgb = null)
                ).toArgb().toLong() and 0xFFFFFFFFL,
                backdrop = backdrop,
                config = config,
                renderInRootScaffold = pickerRenderInRootScaffold,
                onDismissRequest = { colorPickerVisible = false },
                onDismissFinished = {
                    if (!colorPickerVisible) colorPickerPage = null
                },
                onColorSelected = { color ->
                    drafts = drafts + (page to drafts.getValue(page).copy(customColorArgb = color))
                    error = null
                }
            )
        }
    }
    if (showExitConfirm) {
        val otherPageChanged = drafts.any { (page, draft) -> page != currentPage && draft != initialDrafts[page] }
        LiquidAlertDialog(
            title = "保存课程修改？",
            message = if (otherPageChanged) "其他编辑页也有草稿。保存只应用当前页，其他页的草稿会放弃；可以继续编辑后分别保存。"
                else "课程有未保存的修改，请选择保存、放弃或继续编辑。",
            actions = listOf(
                LiquidAlertAction("保存当前页", LiquidAlertActionStyle.Primary) {
                    showExitConfirm = false
                    savePage(currentPage)
                },
                LiquidAlertAction("放弃修改", LiquidAlertActionStyle.Destructive) {
                    showExitConfirm = false
                    onCancel()
                },
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Cancel) { showExitConfirm = false }
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = { showExitConfirm = false }
        )
    }
}

@Composable
fun MissingCourseScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("请先配置课程表。")
        Button(onClick = onBack) { Text("返回") }
    }
}
