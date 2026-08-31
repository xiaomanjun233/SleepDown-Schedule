package com.xiaomanjun.sleepdownschedule.feature.course.management

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.domain.course.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CourseManagementCardShape = RoundedCornerShape(20.dp)

@Composable
internal fun CourseManagementColorProvider(
    state: AppState,
    content: @Composable () -> Unit
) {
    // Course management is hosted in its own Activity, so it does not inherit the Home
    // CompositionLocal that normally carries the adaptive glass foreground.  Supplying the
    // same config-derived fallback here keeps root-hosted Miuix pickers (whose PopupHost is a
    // separate composition) on the same light/dark side as the page instead of the default
    // luminance=1 state, which renders all picker text black on a dark glass surface.
    val adaptiveGlass = rememberFallbackAdaptiveGlassState(state.config)
    val wallpaperImages by rememberHomeWallpaperImages(state.config)
    val colorSignature = remember(state.config.id, state.courses) {
        state.courses.map(::courseCardColorKey).distinct().sorted()
    }
    val coursePalette = remember(
        state.config.courseCardColorMode,
        state.config.cardColorArgb,
        state.config.courseCardPalette,
        wallpaperImages.representativeColors
    ) {
        resolvedCourseCardPalette(state.config, wallpaperImages.representativeColors)
    }
    val assignments = remember(
        state.config.id,
        state.config.courseCardColorMode,
        state.config.cardColorArgb,
        state.config.courseCardPalette,
        colorSignature,
        coursePalette
    ) {
        buildCourseCardColorAssignments(
            state.courses,
            coursePalette,
            tonalFamily = state.config.courseCardColorMode == CourseCardColorMode.GRADIENT
        )
    }
    CompositionLocalProvider(
        LocalAdaptiveGlass provides adaptiveGlass,
        LocalCourseCardPalette provides coursePalette,
        LocalCourseCardColorAssignments provides assignments,
        content = content
    )
}

internal data class ManagedCourseGroup(
    val key: String,
    val courses: List<CourseEntity>
) {
    val representative: CourseEntity get() = courses.minBy(CourseEntity::id)
}

internal fun buildManagedCourseGroups(courses: List<CourseEntity>): List<ManagedCourseGroup> = courses
    .groupBy(::courseCardColorKey)
    .map { (key, entries) ->
        ManagedCourseGroup(
            key = key,
            courses = entries.sortedWith(
                compareBy<CourseEntity> { it.weekday }
                    .thenBy { it.customTimeRangeOrNull()?.first }
                    .thenBy { it.periods.minOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.id }
            )
        )
    }
    .sortedWith(
        compareBy<ManagedCourseGroup> { it.representative.customColorArgb ?: Long.MAX_VALUE }
            .thenBy { it.key }
    )

internal fun managedCourseGroupForCourseId(
    courses: List<CourseEntity>,
    courseId: Long
): ManagedCourseGroup? {
    val selected = courses.firstOrNull { it.id == courseId } ?: return null
    val key = courseCardColorKey(selected)
    return buildManagedCourseGroups(courses).firstOrNull { it.key == key }
}

@Composable
internal fun CourseManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    onBack: () -> Unit,
    transitionActivity: ComponentActivity? = null,
    captureTransitionFrame: suspend () -> Bitmap? = { null },
    transitionRootPosition: Offset = Offset.Zero,
    onOpenCourse: suspend (
        Long,
        Rect,
        AnchoredMorphSnapshots?,
        () -> Unit,
        () -> Unit
    ) -> Boolean
) {
    val groups = remember(state.courses) { buildManagedCourseGroups(state.courses) }
    val foreground = appPanelForegroundColor(state.config)
    val scope = rememberCoroutineScope()
    val activity = transitionActivity
    var hiddenGroupKey by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)
    Box(
        Modifier.fillMaxSize()
    ) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前课表还没有课程", color = foreground.copy(alpha = 0.62f))
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = detailContentTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                staggeredItems(groups, key = ManagedCourseGroup::key) { group ->
                    ManagedCourseListCard(
                        group = group,
                        config = state.config,
                        periods = state.periods,
                        sourceHidden = hiddenGroupKey == group.key,
                        onClick = { sourceBounds ->
                            if (hiddenGroupKey != null) return@ManagedCourseListCard
                            scope.launch {
                                val fullFrame = captureTransitionFrame()
                                val sourceSnapshot = fullFrame?.cropToCourseManagementBounds(
                                    sourceBounds,
                                    transitionRootPosition
                                )
                                val sourceActivity = activity ?: return@launch
                                val sourceOverlay = sourceActivity.window.decorView.overlay
                                if (sourceSnapshot == null) return@launch
                                val openingSourcePlaceholder = BitmapDrawable(
                                    sourceActivity.resources,
                                    sourceSnapshot
                                ).apply {
                                    setBounds(
                                        sourceBounds.left.roundToInt(),
                                        sourceBounds.top.roundToInt(),
                                        sourceBounds.right.roundToInt(),
                                        sourceBounds.bottom.roundToInt()
                                    )
                                }
                                val sourceHandoffReleased = AtomicBoolean(false)
                                val releaseOpeningSource = {
                                    if (sourceHandoffReleased.compareAndSet(false, true)) {
                                        sourceOverlay.remove(openingSourcePlaceholder)
                                    }
                                }
                                val sourceHandoffAttached = runCatching {
                                    sourceOverlay.add(openingSourcePlaceholder)
                                    true
                                }.getOrDefault(false)
                                if (!sourceHandoffAttached) return@launch
                                hiddenGroupKey = group.key
                                withFrameNanos { }
                                withFrameNanos { }
                                val cleanBackground = captureTransitionFrame()
                                if (cleanBackground == null) {
                                    releaseOpeningSource()
                                    hiddenGroupKey = null
                                    return@launch
                                }
                                val launched = onOpenCourse(
                                    group.representative.id,
                                    sourceBounds,
                                    AnchoredMorphSnapshots(
                                        background = cleanBackground,
                                        source = sourceSnapshot
                                    ),
                                    releaseOpeningSource,
                                    { hiddenGroupKey = null }
                                )
                                if (!launched) {
                                    releaseOpeningSource()
                                    hiddenGroupKey = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun ManagedCourseListCard(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    sourceHidden: Boolean,
    onClick: (Rect) -> Unit
) {
    var sourceBounds by remember(group.key) { mutableStateOf<Rect?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Use the full grid cell bounds as the Oplus source rect: the cell includes the
                // card plus its grid padding/gap, so the placeholder covers the whole spot and no
                // underlying card leaks out during the motion (accepting a rectangular morph).
                sourceBounds?.let(onClick)
            }
    ) {
        ManagedCourseListCardContent(
            group = group,
            config = config,
            periods = periods,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (sourceHidden) 0f else 1f }
        )
    }
}

/** Opaque host selected only after ColorOS accepts the Home → course-management session. */
private fun Bitmap.cropToCourseManagementBounds(bounds: Rect, rootPosition: Offset): Bitmap? =
    runCatching {
        val left = (bounds.left - rootPosition.x).roundToInt().coerceIn(0, width - 1)
        val top = (bounds.top - rootPosition.y).roundToInt().coerceIn(0, height - 1)
        val cropWidth = bounds.width.roundToInt().coerceIn(1, width - left)
        val cropHeight = bounds.height.roundToInt().coerceIn(1, height - top)
        Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    }.getOrNull()

@Composable
internal fun ManagedCourseListCardContent(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    modifier: Modifier
) {
    val representative = group.representative
    val cardColor = courseCardBaseColor(config, representative)
    val textColor = appPanelForegroundColor(config)
    val summaryColor = textColor.copy(alpha = 0.72f)
    // The identity rail is useful in every palette mode: solid deliberately repeats one color,
    // gradient shows the tonal assignment, and colorful shows the multi-seed assignment.
    val showCourseColor = true
    val courseInfoLines = buildList {
        representative.teacher?.takeIf { it.isNotBlank() }?.let { add("教师：$it") }
        representative.location?.takeIf { it.isNotBlank() }?.let { add("地点：$it") }
    }
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                CourseManagementCardShape
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (showCourseColor) cardColor else Color.Transparent)
            )
            Text(
                representative.name,
                color = textColor,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Bold,
                softWrap = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = textColor.copy(alpha = 0.70f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
        if (courseInfoLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                courseInfoLines.forEach { info ->
                    Text(
                        text = info,
                        color = summaryColor,
                        style = MiuixTheme.textStyles.body2,
                        lineHeight = 20.sp,
                        softWrap = true
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            group.courses.forEach { arrangement ->
                Text(
                    text = "周${weekdayLabel(arrangement.weekday)} ${courseTimeLabel(arrangement, periods)}",
                    color = summaryColor,
                    style = MiuixTheme.textStyles.body2,
                    lineHeight = 20.sp,
                    softWrap = true
                )
            }
        }
    }
}

@Composable
internal fun HomeMenuActivitySourceFallback(
    config: ScheduleConfigEntity,
    highlightedRowIndex: Int
) {
    val foreground = appPanelForegroundColor(config)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), CourseManagementCardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("日视图 / 周视图", "添加单节课", "课程管理", "手动导入课表", "教务系统导入", "课表设置")
            .forEachIndexed { index, label ->
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == highlightedRowIndex) foreground.copy(alpha = 0.34f)
                                else Color.Transparent
                            )
                    )
                    Text(
                        label,
                        color = foreground.copy(
                            alpha = if (index == highlightedRowIndex) 1f else 0.74f
                        ),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
    }
}

private data class ManagedArrangementDraft(
    val localKey: Long,
    val course: CourseEntity
)

private data class PendingArrangementDelete(
    val localKey: Long,
    val arrangementNumber: Int,
    val confirm: () -> Unit,
    val cancel: () -> Unit
)

private data class PendingCourseManagementConflictSave(
    val replacements: List<CourseEntity>,
    val conflictWeeks: List<Int>
)

@Composable
internal fun CourseManagementDetailPage(
    group: ManagedCourseGroup,
    state: AppState,
    onBack: () -> Unit,
    onSave: (List<CourseEntity>) -> Unit
) {
    var name by remember(group.key) { mutableStateOf(group.representative.name) }
    var selectedColor by remember(group.key) { mutableStateOf(group.representative.customColorArgb) }
    var nextLocalKey by remember(group.key) { mutableLongStateOf(-1L) }
    var arrangements by remember(group.key) {
        mutableStateOf(group.courses.map { ManagedArrangementDraft(it.id, it) })
    }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var showSaveChangesDialog by remember(group.key) { mutableStateOf(false) }
    var validationMessage by remember(group.key) { mutableStateOf<String?>(null) }
    var pendingDelete by remember(group.key) { mutableStateOf<PendingArrangementDelete?>(null) }
    var pendingConflictSave by remember(group.key) {
        mutableStateOf<PendingCourseManagementConflictSave?>(null)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colorModeEnabled = courseCardAllowsCustomOverrides(state.config)

    fun replacementsForSave(): List<CourseEntity> {
        val normalizedName = name.trim()
        return arrangements.map { draft ->
            draft.course.copy(
                name = normalizedName,
                customColorArgb = if (colorModeEnabled) selectedColor else draft.course.customColorArgb
            )
        }
    }

    val replacements = replacementsForSave()
    val hasChanges = name != group.representative.name || replacements != group.courses

    fun updateArrangement(localKey: Long, transform: (CourseEntity) -> CourseEntity) {
        arrangements = arrangements.map { draft ->
            if (draft.localKey == localKey) draft.copy(course = transform(draft.course)) else draft
        }
    }

    fun saveAndReturn() {
        val trimmedName = name.trim()
        if (arrangements.isNotEmpty() && trimmedName.isBlank()) {
            validationMessage = "课程名称不能为空"
            return
        }
        val replacementsToSave = replacementsForSave()
        if (replacementsToSave.isEmpty()) {
            onSave(replacementsToSave)
            return
        }
        val conflictWeeks = conflictWeeksForEditedCourseGroup(
            originals = group.courses,
            edited = replacementsToSave,
            courses = state.courses,
            periodDefinitions = state.periods
        )
        if (conflictWeeks.isEmpty()) {
            onSave(replacementsToSave)
        } else {
            showSaveChangesDialog = false
            pendingConflictSave = PendingCourseManagementConflictSave(
                replacements = replacementsToSave,
                conflictWeeks = conflictWeeks
            )
        }
    }

    fun requestBack() {
        if (hasChanges) {
            showSaveChangesDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasChanges) { requestBack() }

    DetailActivityScaffold(
        title = name.ifBlank { "课程详情" },
        config = state.config,
        onBack = ::requestBack,
        compactTopBar = true,
        centerCompactTitle = true,
        compactTitleMatchesSettings = true,
        topBarActions = { topBackdrop ->
            DialogLiquidButton(
                backdrop = topBackdrop,
                label = "添加安排",
                onClick = {
                    val template = arrangements.lastOrNull()?.course ?: group.representative
                    val firstPeriod = state.periods.firstOrNull()?.periodIndex ?: 1
                    val added = template.copy(
                        id = 0,
                        name = name.ifBlank { template.name },
                        weekday = 1,
                        periods = listOf(firstPeriod),
                        weeks = (1..state.config.totalWeeks.coerceAtLeast(1)).toList(),
                        weekParity = WeekParity.ALL,
                        customStartTime = null,
                        customEndTime = null,
                        customColorArgb = selectedColor,
                        scheduleId = state.config.id
                    )
                    arrangements = arrangements + ManagedArrangementDraft(nextLocalKey--, added)
                    scope.launch { listState.animateScrollToItem(arrangements.lastIndex + 1) }
                },
                role = DialogButtonRole.Confirm,
                iconRes = R.drawable.ic_add_course,
                roundIcon = true,
                blurRadius = 10.dp
            )
        }
    ) { cardBackdrop ->
        // DetailActivityScaffold records its scrolling content into
        // LocalSettingsPopupBackdrop. A card inside that recorder must never sample that same
        // producer: doing so creates a RenderNode cycle and crashes RenderThread with an
        // unbounded prepareTreeImpl recursion. This is the same one-way arrangement used by the
        // AI import history detail page: in-page controls sample the scaffold's plain background
        // producer, while only the root-hosted picker samples the completed content producer.
        val pickerBackdrop = LocalSettingsPopupBackdrop.current ?: cardBackdrop
        // Centered dialogs and their action capsules resolve LocalCenteredDialogSceneBackdrop.
        // The Miuix scaffold hosts them as a sibling of the complete page producer, so every layer
        // can be sampled without allowing the dialog subtree to feed itself back into RenderThread.
        LazyColumn(
            state = listState,
            // The explicit modifier is the only Miuix path that still responds when a single
            // arrangement fits entirely in the viewport; disable the theme factory to avoid a
            // second spring once longer content becomes normally scrollable.
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = 16.dp,
                // The centered compact title is wider/taller than the history-page title. Give
                // the first card a real clear band below it instead of letting the top-gradient
                // overlay visually crop the color heading.
                top = detailContentTopPadding() + 24.dp,
                end = 16.dp,
                bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "course-identity") {
                CourseIdentityCard(
                    name = name,
                    onNameChange = { name = it },
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it },
                    onOpenColorPicker = { colorPickerVisible = true },
                    backdrop = cardBackdrop,
                    config = state.config
                )
            }
            itemsIndexed(arrangements, key = { _, item -> item.localKey }) { index, draft ->
                SwipeDeleteArrangementCard(
                    key = draft.localKey,
                    onDelete = { arrangements = arrangements.filterNot { it.localKey == draft.localKey } },
                    onDeleteRequest = { confirm, cancel ->
                        pendingDelete = PendingArrangementDelete(
                            localKey = draft.localKey,
                            arrangementNumber = index + 1,
                            confirm = confirm,
                            cancel = cancel
                        )
                    }
                ) {
                    CourseArrangementEditorCard(
                        localKey = draft.localKey,
                        index = index,
                        course = draft.course,
                        periods = state.periods,
                        totalWeeks = state.config.totalWeeks.coerceAtLeast(1),
                        config = state.config,
                        backdrop = cardBackdrop,
                        onCourseChange = { changed -> updateArrangement(draft.localKey) { changed } },
                        onOpenPicker = {
                            pickerRequest = it
                            pickerVisible = true
                        }
                    )
                }
            }
            if (arrangements.isEmpty()) {
                item(key = "empty-arrangements") {
                    CourseManagementSettingsSection(title = "上课安排") {
                        Text(
                            "已移除全部安排，返回时可确认删除这门课程。",
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            if (arrangements.size <= 1) {
                item(key = "elastic-scroll-tail") {
                    // Preserve a small physical scroll range on short detail pages. Combined with
                    // Miuix overscroll this keeps the page responsive to a swipe even when there
                    // is only one arrangement, without changing any visible business content.
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
        pickerRequest?.let { request ->
            CourseEditorPickerOverlay(
                show = pickerVisible,
                request = request,
                backdrop = pickerBackdrop,
                config = state.config,
                renderInRootScaffold = true,
                onDismiss = { pickerVisible = false },
                onDismissFinished = {
                    if (!pickerVisible) pickerRequest = null
                }
            )
        }
        CourseColorPicker(
            show = colorPickerVisible,
            selectedColorArgb = selectedColor,
            automaticColorArgb = courseCardBaseColor(
                state.config,
                group.representative.copy(customColorArgb = null)
            ).toArgb().toLong() and 0xFFFFFFFFL,
            backdrop = pickerBackdrop,
            config = state.config,
            renderInRootScaffold = true,
            onDismissRequest = { colorPickerVisible = false },
            onDismissFinished = {},
            onColorSelected = { selectedColor = it }
        )
        if (showSaveChangesDialog) {
            val canSave = arrangements.isEmpty() || name.trim().isNotBlank()
            LiquidAlertDialog(
                title = "保存修改？",
                message = validationMessage
                    ?: "你修改了课程安排。保存完成后才会退出课程详情。",
                actions = listOf(
                    LiquidAlertAction(
                        label = "继续编辑",
                        style = LiquidAlertActionStyle.Secondary,
                        onClick = {
                            validationMessage = null
                            showSaveChangesDialog = false
                        }
                    ),
                    LiquidAlertAction(
                        label = "不保存",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = onBack
                    ),
                    LiquidAlertAction(
                        label = "保存并退出",
                        style = LiquidAlertActionStyle.Primary,
                        dismissOnClick = canSave,
                        onClick = ::saveAndReturn
                    )
                ),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = {
                    validationMessage = null
                    showSaveChangesDialog = false
                }
            )
        }
        pendingDelete?.let { pending ->
            LiquidAlertDialog(
                title = "删除上课安排？",
                message = "确定删除第 ${pending.arrangementNumber} 个上课安排吗？",
                actions = listOf(
                    LiquidAlertAction(
                        label = "取消",
                        style = LiquidAlertActionStyle.Secondary,
                        onClick = {
                            pending.cancel()
                            pendingDelete = null
                        }
                    ),
                    LiquidAlertAction(
                        label = "删除",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = {
                            pendingDelete = null
                            pending.confirm()
                        }
                    )
                ),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = {
                    pending.cancel()
                    pendingDelete = null
                }
            )
        }
        pendingConflictSave?.let { pending ->
            CourseConflictRetentionDialog(
                course = pending.replacements.firstOrNull() ?: group.representative,
                conflictWeeks = pending.conflictWeeks,
                backdrop = pickerBackdrop,
                config = state.config,
                onKeepTemporarily = {
                    pendingConflictSave = null
                    onSave(pending.replacements)
                },
                onReturn = { pendingConflictSave = null },
                retentionMessage = buildString {
                    append("“${pending.replacements.firstOrNull()?.name ?: group.representative.name}”在")
                    append(pending.conflictWeeks.take(4).joinToString("、") { "第${it}周" })
                    if (pending.conflictWeeks.size > 4) append("等${pending.conflictWeeks.size}周")
                    append("与其他课程重合。可以暂时保留这些安排，之后仍可在课表中继续调整。")
                }
            )
        }
    }
}

@Composable
private fun CourseManagementSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CourseManagementCardShape)
            .padding(vertical = 6.dp)
    ) {
        Text(
            title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun CourseIdentityCard(
    name: String,
    onNameChange: (String) -> Unit,
    selectedColor: Long?,
    onColorSelected: (Long?) -> Unit,
    onOpenColorPicker: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    CourseManagementSettingsSection(title = "课程信息") {
        SettingsTextFieldRow(
            title = "课程名称",
            value = name,
            onValueChange = onNameChange,
            placeholder = "输入课程名称"
        )
        if (courseCardAllowsCustomOverrides(config)) {
            MiuixBasicComponent(
                title = "课程颜色",
                summary = "应用到全部安排",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            )
            val palette = LocalCourseCardPalette.current.ifEmpty { DefaultCourseCardPalette }
            val visiblePalette = palette.take(4)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePalette.forEach { argb ->
                    val selected = selectedColor == argb
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                scaleX = if (selected) 1.12f else 1f
                                scaleY = if (selected) 1.12f else 1f
                            }
                            .clip(RoundedCornerShape(50))
                            .background(Color(argb.toInt()))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        if (Color(argb.toInt()).luminance() > 0.55f) Color.Black else Color.White,
                                        RoundedCornerShape(50)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColorSelected(argb) }
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "已选择课程颜色",
                                tint = if (Color(argb.toInt()).luminance() > 0.55f) Color.Black else Color.White,
                                modifier = Modifier.align(Alignment.Center).size(17.dp)
                            )
                        }
                    }
                }
                CourseColorPaletteButton(
                    backdrop = backdrop,
                    selected = selectedColor != null && selectedColor !in visiblePalette,
                    onClick = onOpenColorPicker,
                    size = 34.dp
                )
            }
        }
    }
}

@Composable
private fun CourseArrangementEditorCard(
    localKey: Long,
    index: Int,
    course: CourseEntity,
    periods: List<PeriodEntity>,
    totalWeeks: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onCourseChange: (CourseEntity) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit
) {
    CourseManagementSettingsSection(title = "上课安排 ${index + 1}") {
        CourseManagementArrowRow(
            title = "上课星期",
            value = "周${weekdayLabel(course.weekday)}",
            onClick = {
                onOpenPicker(
                    CourseEditorPickerRequest.Wheel(
                        title = "选择上课星期",
                        labels = (1..7).map { "周${weekdayLabel(it)}" },
                        startIndex = (course.weekday - 1).coerceIn(0, 6),
                        onConfirm = { selected, _ ->
                            onCourseChange(course.copy(weekday = selected + 1))
                        }
                    )
                )
            }
        )
        CourseManagementArrowRow(
            title = "上课节次",
            value = if (course.hasCustomTime()) courseTimeLabel(course, periods)
            else "第${course.periods.minOrNull() ?: 1}-${course.periods.maxOrNull() ?: 1}节",
            onClick = {
                val values = periods.map(PeriodEntity::periodIndex).distinct().sorted().ifEmpty { listOf(1) }
                val startIndex = values.indexOf(course.periods.minOrNull()).coerceAtLeast(0)
                val endIndex = values.indexOf(course.periods.maxOrNull()).coerceAtLeast(startIndex)
                onOpenPicker(
                    CourseEditorPickerRequest.Period(
                        title = "选择节次",
                        labels = values.map { "第${it}节" },
                        startIndex = startIndex,
                        endIndex = endIndex,
                        endIndexUpperBound = values.lastIndex,
                        customStartTime = course.customStartTime,
                        customEndTime = course.customEndTime,
                        regularStartTime = periods.firstOrNull { it.periodIndex == values[startIndex] }?.startTime,
                        regularEndTime = periods.firstOrNull { it.periodIndex == values[endIndex] }?.endTime,
                        onConfirmPeriods = { start, end ->
                            onCourseChange(
                                course.copy(
                                    periods = values.subList(start, end + 1),
                                    customStartTime = null,
                                    customEndTime = null
                                )
                            )
                        },
                        onConfirmCustomTime = { startText, endText ->
                            val start = LocalTime.parse(startText)
                            val end = LocalTime.parse(endText)
                            val anchors = courseAnchorPeriodsForTimeRange(start, end, periods).ifEmpty { course.periods }
                            onCourseChange(
                                course.copy(
                                    periods = anchors,
                                    customStartTime = startText,
                                    customEndTime = endText
                                )
                            )
                        }
                    )
                )
            }
        )
        SettingsTextFieldRow(
            title = "地点",
            value = course.location.orEmpty(),
            onValueChange = { onCourseChange(course.copy(location = it.ifBlank { null })) },
            placeholder = "未填写"
        )
        SettingsTextFieldRow(
            title = "教师",
            value = course.teacher.orEmpty(),
            onValueChange = { onCourseChange(course.copy(teacher = it.ifBlank { null })) },
            placeholder = "未填写"
        )
        CourseManagementWeekFold(
            localKey = localKey,
            course = course,
            totalWeeks = totalWeeks,
            onCourseChange = onCourseChange
        )
        SettingsTextFieldRow(
            title = "备注",
            value = course.note.orEmpty(),
            onValueChange = { onCourseChange(course.copy(note = it.ifBlank { null })) },
            placeholder = "未填写"
        )
    }
}

@Composable
private fun CourseManagementArrowRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    MiuixBasicComponent(
        title = title,
        summary = value,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        endActions = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }
            )
        },
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseManagementWeekFold(
    localKey: Long,
    course: CourseEntity,
    totalWeeks: Int,
    onCourseChange: (CourseEntity) -> Unit
) {
    var expanded by remember(localKey, totalWeeks) { mutableStateOf(false) }
    val expansionEasing = remember { CubicBezierEasing(0.20f, 0f, 0f, 1f) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 180f,
        animationSpec = tween(280, easing = expansionEasing),
        label = "course-management-week-arrow-$localKey"
    )
    val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
    val selectedWeeks = course.weeks.toSet()
    val selectedMode = inferCourseWeekSelectionMode(selectedWeeks)
    val haptic = LocalHapticFeedback.current
    fun updateWeeks(nextWeeks: Set<Int>) {
        if (nextWeeks.isEmpty() || nextWeeks == selectedWeeks) return
        val sorted = nextWeeks.sorted()
        onCourseChange(
            course.copy(
                weeks = sorted,
                weekParity = inferCourseWeekSelectionMode(sorted).toWeekParity()
            )
        )
    }
    // Miuix's BasicComponent only measures its preference row. The expanding body must be a
    // sibling in this Column, otherwise the grid looks present but is clipped and cannot receive
    // touch events on certain devices.
    Column(Modifier.fillMaxWidth()) {
        MiuixBasicComponent(
            title = "上课周次",
            summary = compactWeekSelectionLabel(course.weeks),
            modifier = Modifier.fillMaxWidth(),
             insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "收起周次" else "展开周次",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation }
                )
            },
            onClick = { expanded = !expanded }
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(320, easing = expansionEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(190, delayMillis = 30)),
            exit = shrinkVertically(
                animationSpec = tween(240, easing = expansionEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(150))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CourseWeekSelectionMode.EVERY to "全部",
                        CourseWeekSelectionMode.ODD to "单周",
                        CourseWeekSelectionMode.EVEN to "双周"
                    ).forEach { (mode, label) ->
                        val selected = selectedMode == mode
                        CourseManagementWeekChoice(
                            label = label,
                            selected = selected,
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            val weeks = weeksForCourseWeekSelectionMode(
                                mode = mode,
                                currentWeeks = selectedWeeks,
                                totalWeeks = safeTotalWeeks
                            ).toSet()
                            if (weeks != selectedWeeks) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                updateWeeks(weeks)
                            }
                        }
                    }
                }
                CourseManagementWeekSelectionGrid(
                    totalWeeks = safeTotalWeeks,
                    selectedWeeks = selectedWeeks,
                    onSelectionChange = { nextWeeks ->
                        updateWeeks(nextWeeks)
                    }
                )
            }
        }
    }
}

/** The week fold uses the editor's drag-select language, while keeping the detail-page layout. */
@Composable
private fun CourseManagementWeekSelectionGrid(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestSelectedWeeks by rememberUpdatedState(selectedWeeks)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val density = LocalDensity.current
    val columns = 6
    val gap = 8.dp
    val cellHeight = 38.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rowCount = (totalWeeks + columns - 1) / columns
        val gridHeight = cellHeight * rowCount + gap * (rowCount - 1).coerceAtLeast(0)
        val gapPx = with(density) { gap.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .pointerInput(totalWeeks, columns, gapPx, cellHeightPx) {
                    var gestureSelection = mutableSetOf<Int>()
                    var selectMode = true
                    var lastWeek = -1

                    fun weekAt(position: Offset): Int? {
                        if (position.x < 0f || position.y < 0f) return null
                        val cellWidth = (size.width - gapPx * (columns - 1)) / columns
                        val columnStride = cellWidth + gapPx
                        val rowStride = cellHeightPx + gapPx
                        val column = (position.x / columnStride).toInt()
                        val row = (position.y / rowStride).toInt()
                        if (column !in 0 until columns || row !in 0 until rowCount) return null
                        if (position.x - column * columnStride > cellWidth) return null
                        if (position.y - row * rowStride > cellHeightPx) return null
                        return (row * columns + column + 1).takeIf { it in 1..totalWeeks }
                    }

                    fun applyWeek(week: Int) {
                        if (week == lastWeek) return
                        lastWeek = week
                        val next = gestureSelection.toMutableSet()
                        if (selectMode) next.add(week) else next.remove(week)
                        if (next.isEmpty() || next == gestureSelection) return
                        gestureSelection = next
                        latestOnSelectionChange(next)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            gestureSelection = latestSelectedWeeks.toMutableSet()
                            lastWeek = -1
                            weekAt(position)?.let { week ->
                                selectMode = week !in gestureSelection
                                applyWeek(week)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            weekAt(change.position)?.let(::applyWeek)
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                (1..totalWeeks).toList().chunked(columns).forEach { rowWeeks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowWeeks.forEach { week ->
                            val selected = week in selectedWeeks
                            CourseManagementWeekChoice(
                                label = week.toString(),
                                selected = selected,
                                modifier = Modifier.weight(1f).height(cellHeight)
                            ) {
                                val next = if (selected) selectedWeeks - week else selectedWeeks + week
                                if (next.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectionChange(next)
                                }
                            }
                        }
                        repeat(columns - rowWeeks.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseManagementWeekChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val baseColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val pressedColor = baseColor.copy(
        red = baseColor.red * 0.82f,
        green = baseColor.green * 0.82f,
        blue = baseColor.blue * 0.82f
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (pressed) pressedColor else baseColor,
        animationSpec = tween(durationMillis = if (pressed) 70 else 150),
        label = "course-management-week-choice-press"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SwipeDeleteArrangementCard(
    key: Long,
    onDeleteRequest: (confirm: () -> Unit, cancel: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(key) { Animatable(0f) }
    val removal = remember(key) { Animatable(0f) }
    var revealThresholdCrossed by remember(key) { mutableStateOf(false) }
    var deleteThresholdCrossed by remember(key) { mutableStateOf(false) }
    var deleteRequested by remember(key) { mutableStateOf(false) }
    var deletionStarted by remember(key) { mutableStateOf(false) }
    var widthPx by remember(key) { mutableFloatStateOf(1f) }
    var measuredHeightPx by remember(key) { mutableFloatStateOf(0f) }
    val actionWidthPx = with(density) { 48.dp.toPx() }
    val actionEndPaddingPx = with(density) { 12.dp.toPx() }
    val actionGapPx = with(density) { 22.dp.toPx() }
    val revealPx = actionWidthPx + actionEndPaddingPx + actionGapPx
    val deleteTriggerPx = max(
        revealPx + with(density) { 156.dp.toPx() },
        widthPx * 0.78f
    ).coerceAtMost(widthPx * 0.88f)
    val maximumDragPx = (widthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(revealPx)
    val settleSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 420f)

    fun confirmDelete() {
        if (deletionStarted) return
        deletionStarted = true
        scope.launch {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.coroutineScope {
                launch { offset.animateTo(-widthPx * 1.08f, tween(300, easing = DetailExitEasing)) }
                launch { removal.animateTo(1f, tween(360, easing = DetailExitEasing)) }
            }
            onDelete()
        }
    }

    fun cancelDelete() {
        if (deletionStarted) return
        deleteRequested = false
        revealThresholdCrossed = false
        deleteThresholdCrossed = false
        scope.launch { offset.animateTo(0f, settleSpring) }
    }

    fun requestDelete(performHaptic: Boolean = true) {
        if (deletionStarted || deleteRequested) return
        deleteRequested = true
        if (performHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            if (abs(offset.value) < deleteTriggerPx) {
                offset.animateTo(-deleteTriggerPx, tween(180, easing = DetailExitEasing))
            }
            onDeleteRequest(::confirmDelete, ::cancelDelete)
        }
    }

    val collapseProgress = ((removal.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
    val dragDistance = (-offset.value).coerceAtLeast(0f)
    val removalStretch = (removal.value / 0.42f).coerceIn(0f, 1f)
    val revealProgress = (dragDistance / revealPx).coerceIn(0f, 1f)
    val measuredHeight = with(density) { measuredHeightPx.toDp() }
    val collapsedHeight = with(density) {
        (measuredHeightPx * (1f - collapseProgress)).coerceAtLeast(1f).toDp()
    }
    val outerHeightModifier = if (measuredHeightPx > 0f && removal.value > 0.001f) {
        Modifier.height(collapsedHeight)
    } else {
        Modifier
    }

    Box(
        Modifier.fillMaxWidth()
            .then(outerHeightModifier)
            .onSizeChanged { size ->
                widthPx = size.width.toFloat().coerceAtLeast(1f)
                if (removal.value <= 0.01f && size.height > 1) {
                    measuredHeightPx = max(measuredHeightPx, size.height.toFloat())
                }
            }
    ) {
        val deleteModifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp)
            .size(48.dp)
            .graphicsLayer {
                val vanish = ((removal.value - 0.66f) / 0.30f).coerceIn(0f, 1f)
                alpha = max(revealProgress, removalStretch) * (1f - vanish)
                val revealScale = 0.64f + 0.36f * max(revealProgress, removalStretch)
                scaleX = revealScale * (1f - 0.24f * vanish)
                scaleY = revealScale * (1f - 0.24f * vanish)
                transformOrigin = TransformOrigin.Center
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = platformBlurRenderEffect(10.dp.toPx() * vanish)
            }
        Box(
            deleteModifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFF3B30))
                .clickable(onClick = ::requestDelete),
            contentAlignment = Alignment.Center
        ) {
            val iconVanish = ((removal.value - 0.72f) / 0.24f).coerceIn(0f, 1f)
            Image(
                painter = painterResource(R.drawable.ic_delete_history),
                contentDescription = "删除安排",
                modifier = Modifier.size(20.dp).graphicsLayer {
                    alpha = 1f - iconVanish
                    scaleX = 1f - 0.22f * iconVanish
                    scaleY = 1f - 0.22f * iconVanish
                }
            )
        }
    val foregroundHeightModifier = if (measuredHeightPx > 0f && removal.value > 0.001f) {
        Modifier.height(measuredHeight)
    } else {
        Modifier
    }
        Box(
            Modifier.fillMaxWidth()
                .then(foregroundHeightModifier)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(key, widthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = drag@{ change, amount ->
                            change.consume()
                            if (deletionStarted || deleteRequested) return@drag
                            val next = (offset.value + amount).coerceIn(
                                -maximumDragPx,
                                with(density) { 8.dp.toPx() }
                            )
                            val revealCrossed = abs(next) >= revealPx * 0.48f
                            if (revealCrossed != revealThresholdCrossed) {
                                revealThresholdCrossed = revealCrossed
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { offset.snapTo(next) }
                            val deleteCrossed = abs(next) >= deleteTriggerPx
                            if (deleteCrossed && !deleteThresholdCrossed) {
                                deleteThresholdCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                requestDelete(performHaptic = false)
                            }
                        },
                        onDragEnd = dragEnd@{
                            if (deletionStarted || deleteRequested) return@dragEnd
                            scope.launch {
                                val target = if (abs(offset.value) >= revealPx * 0.48f) -revealPx else 0f
                                revealThresholdCrossed = target < 0f
                                deleteThresholdCrossed = false
                                offset.animateTo(target, settleSpring)
                            }
                        },
                        onDragCancel = dragCancel@{
                            if (deletionStarted) return@dragCancel
                            revealThresholdCrossed = false
                            deleteThresholdCrossed = false
                            scope.launch { offset.animateTo(0f, settleSpring) }
                        }
                    )
                },
            content = content
        )
    }
}
