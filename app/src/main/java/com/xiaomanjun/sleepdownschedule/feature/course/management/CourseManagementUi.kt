package com.xiaomanjun.sleepdownschedule.feature.course.management

import com.xiaomanjun.sleepdownschedule.feature.course.editor.manualCourseClockError
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseClockOrigin

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme


import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeCoursePeriodTimes

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.animateScrollToTop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.domain.course.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.isHiddenByPeriodAlignment
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.previewCourseEdits
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onBack: () -> Unit,
    onDelete: suspend (List<CourseEntity>) -> Unit,
    transitionActivity: ComponentActivity? = null,
    captureTransitionFrame: suspend () -> Bitmap? = { null },
    transitionRootPosition: Offset = Offset.Zero,
    onOpenCourse: suspend (
        Long,
        Rect,
        () -> Rect?,
        AnchoredMorphSnapshots?,
        () -> Unit,
        () -> Unit
    ) -> Boolean
) {
    val groups = remember(state.courses) { buildManagedCourseGroups(state.courses) }
    val currentGroups by rememberUpdatedState(groups)
    val foreground = appPanelForegroundColor(state.config)
    val scope = rememberCoroutineScope()
    val activity = transitionActivity
    val selection = remember(state.config.id) { CardDeletionSelection() }
    val pageConfig = settingsVisualConfig(state.config)
    val pageColor = settingsPageBackground(pageConfig)
    val listBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "course-management-list") {
        drawRect(pageColor)
        drawContent()
    }
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
            val listState = rememberLazyStaggeredGridState()
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = listState,
                modifier = Modifier.fillMaxSize().glassBackdropProducer(listBackdrop).animateScrollToTop {
                    if (listState.canScrollBackward) listState.animateScrollToItem(0)
                },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = detailContentTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = if (selection.active) detailContentBottomPadding() + CardDeleteBottomSpace else 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                staggeredItems(groups, key = ManagedCourseGroup::key) { group ->
                    ManagedCourseListCard(
                        group = group,
                        config = state.config,
                        periods = state.periods,
                        selection = selection,
                        enabled = hiddenGroupKey == null,
                        sourceHidden = hiddenGroupKey == group.key,
                        onClick = { sourceBounds, currentBounds ->
                            if (hiddenGroupKey != null || selection.active) return@ManagedCourseListCard
                            scope.launch {
                                val fullFrame = captureTransitionFrame()
                                if (selection.active || hiddenGroupKey != null) return@launch
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
                                    {
                                        currentBounds().takeIf {
                                            currentGroups.any { current -> current.key == group.key &&
                                                current.courses.any { it.id == group.representative.id } }
                                        }
                                    },
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
        CardSelectionChrome(
            selection = selection,
            cards = remember(groups) { groups.associateBy(ManagedCourseGroup::key) },
            config = pageConfig,
            backdrop = listBackdrop,
            confirmationTitle = "删除课程？",
            confirmationMessage = { count -> "将删除 ${count} 门课程及其全部安排。" },
            onDelete = { selected -> onDelete(selected.flatMap(ManagedCourseGroup::courses)) }
        )
    }

}

@Composable
private fun ManagedCourseListCard(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    selection: CardDeletionSelection,
    enabled: Boolean,
    sourceHidden: Boolean,
    onClick: (Rect, () -> Rect?) -> Unit
) {
    var sourceBounds by remember(group.key) { mutableStateOf<Rect?>(null) }
    var coordinates by remember(group.key) { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(group.key) { onDispose { coordinates = null } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates = it; sourceBounds = it.boundsInWindow() }
            .clip(CourseManagementCardShape)
            .cardSelectionInteraction(selection, group.key, enabled, appUsesDarkTheme(config)) {
                sourceBounds?.let { bounds ->
                    onClick(bounds) {
                        coordinates?.takeIf { it.isAttached }?.let { current ->
                            val visible = current.boundsInWindow()
                            // A clipped/recycled lazy item is not a reliable system return target.
                            visible.takeIf { it.width >= current.size.width - 1f &&
                                it.height >= current.size.height - 1f }
                        }
                    }
                }
            }
    ) {
        ManagedCourseListCardContent(
            group = group,
            config = config,
            periods = periods,
            checkedForDeletion = if (selection.active) group.key in selection.selectedKeys else null,
            selectionEnabled = enabled && !selection.deleting,
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

internal data class ManagedCourseDaySummary(val weekday: Int, val times: List<String>)

/** Shared by the painted card and its native transition; course chips have a separate tint. */
internal fun managedCourseCardSurfaceColor(surface: Color, selected: Boolean = false): Color {
    val fill = surface.copy(alpha = 0.86f)
    return if (selected) Color.Black.copy(alpha = 0.08f).compositeOver(fill) else fill
}

internal fun buildManagedCourseDaySummaries(
    courses: List<CourseEntity>, periods: List<PeriodEntity>, weekStartsOnSunday: Boolean
): List<ManagedCourseDaySummary> = courses.groupBy(CourseEntity::weekday).entries
    .sortedBy { (day, _) -> if (weekStartsOnSunday) day % 7 else day }
    .map { (day, entries) ->
        ManagedCourseDaySummary(day, entries.map { arrangement ->
            if (arrangement.isHiddenByPeriodAlignment()) {
                "暂不显示 · 原始第${arrangement.originalArrangement().periods.joinToString("、")}节"
            } else courseTimeLabel(arrangement, periods).replace(" - ", "–")
        }.distinct())
    }

@Composable
internal fun ManagedCourseListCardContent(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    modifier: Modifier,
    selected: Boolean = false,
    checkedForDeletion: Boolean? = null,
    selectionEnabled: Boolean = true,
) {
    val representative = group.representative
    val cardColor = courseCardBaseColor(config, representative)
    val textColor = appPanelForegroundColor(config)
    val summaryColor = textColor.copy(alpha = 0.78f)
    val locations = remember(group.courses) {
        group.courses.mapNotNull { it.location?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    }
    val daySummaries = remember(group.courses, periods, config.weekStartsOnSunday) {
        buildManagedCourseDaySummaries(group.courses, periods, config.weekStartsOnSunday)
    }
    Column(
        modifier = modifier
            .background(
                managedCourseCardSurfaceColor(MaterialTheme.colorScheme.surfaceContainerHigh, selected),
                CourseManagementCardShape
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(Capsule())
                    .background(cardColor)
            )
            Text(
                representative.name,
                color = textColor,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            if (checkedForDeletion != null) {
                CardSelectionCheck(checkedForDeletion, selectionEnabled, appUsesDarkTheme(config))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.70f),
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f }
                )
            }
        }
        if (locations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = "地点",
                    tint = summaryColor.copy(alpha = 0.62f), modifier = Modifier.size(15.dp))
                Text(locations.joinToString(" · "), color = summaryColor,
                    style = MiuixTheme.textStyles.body2.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    lineHeight = 20.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            daySummaries.take(4).forEach { day ->
                ManagedCourseArrangementChip("周${weekdayLabel(day.weekday)}", day.times, cardColor, summaryColor)
            }
            if (daySummaries.size > 4) {
                ManagedCourseArrangementChip(null, listOf("+${daySummaries.size - 4} 天安排"), cardColor, summaryColor)
            }
        }
    }
}

@Composable
private fun ManagedCourseArrangementChip(day: String?, times: List<String>, courseColor: Color, textColor: Color) {
    val style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, lineHeight = 17.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(courseColor.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = "安排", tint = textColor.copy(alpha = 0.68f),
            modifier = Modifier.size(13.dp))
        if (day != null) Text(day, color = textColor, style = style, maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            times.forEach { time ->
                Text(time, color = textColor, style = style, maxLines = 1, softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
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
        listOf("日视图 / 周视图", "添加单节课", "课程管理", "口令/文件导入", "教务系统导入", "课表设置", "多课表管理")
            .forEachIndexed { index, label ->
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(Capsule())
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

internal fun newManagedCourseArrangement(
    template: CourseEntity,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>
): CourseEntity = template.copy(
    id = 0,
    weekday = 1,
    periods = listOf(periods.firstOrNull()?.periodIndex ?: 1),
    weeks = (1..config.totalWeeks.coerceAtLeast(1)).toList(),
    weekParity = WeekParity.ALL,
    customStartTime = null,
    customEndTime = null,
    customPeriodTimes = null,
    // The new arrangement owns a new clock; the template's snapshot/projection describes its old periods.
    originalPeriodTimes = null,
    arrangementProjection = null,
    scheduleId = config.id
)

private data class ManagedArrangementDraft(
    val localKey: Long,
    val course: CourseEntity
)

private data class PendingArrangementDelete(
    val localKey: Long,
    val arrangementNumber: Int,
    val answer: CompletableDeferred<Boolean>,
)

private data class DepartingArrangement(val index: Int, val draft: ManagedArrangementDraft)

private data class PendingCourseManagementConflictSave(
    val replacements: List<CourseEntity>,
    val conflictWeeks: List<Int>
)

@Composable
internal fun CourseManagementDetailPage(
    group: ManagedCourseGroup,
    state: AppState,
    onBack: () -> Unit,
    onSave: (List<CourseEntity>, List<CourseEntity>, (Boolean) -> Unit) -> Unit,
    onExitHandlerChange: (((() -> Unit) -> Unit)?) -> Unit = {}
) {
    val originalGroup = remember(group.key) { group }
    val originalCourses = remember(originalGroup) { originalGroup.courses.map { it.originalArrangement() } }
    var pendingExitAction by remember(group.key) { mutableStateOf<(() -> Unit)?>(null) }
    var saving by remember(group.key) { mutableStateOf(false) }
    var name by remember(group.key) { mutableStateOf(originalGroup.representative.name) }
    var selectedColor by remember(group.key) { mutableStateOf(originalGroup.representative.customColorArgb) }
    var nextLocalKey by remember(group.key) { mutableLongStateOf(-1L) }
    var arrangements by remember(group.key) {
        mutableStateOf(originalCourses.map { ManagedArrangementDraft(it.id, it) })
    }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var showSaveChangesDialog by remember(group.key) { mutableStateOf(false) }
    var validationMessage by remember(group.key) { mutableStateOf<String?>(null) }
    var pendingDelete by remember(group.key) { mutableStateOf<PendingArrangementDelete?>(null) }
    var revealedArrangement by remember(group.key) { mutableStateOf<Long?>(null) }
    var departing by remember(group.key) { mutableStateOf<DepartingArrangement?>(null) }
    val displayedArrangements = remember(arrangements, departing) {
        val outgoing = departing
        if (outgoing == null) arrangements else arrangements.toMutableList().apply {
            add(outgoing.index.coerceAtMost(size), outgoing.draft)
        }
    }
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

    val remainingOriginals = remember(arrangements, originalCourses) {
        val retainedKeys = arrangements.mapTo(hashSetOf()) { it.localKey }
        originalCourses.filter { it.id in retainedKeys }
    }
    val hasConfirmedDeletion = remainingOriginals.size < originalCourses.size
    val hasOtherChanges = arrangements.isNotEmpty() && (
        name != originalGroup.representative.name ||
            (colorModeEnabled && selectedColor != originalGroup.representative.customColorArgb) ||
            arrangements.map { it.course } != remainingOriginals
        )
    val hasChanges = hasConfirmedDeletion || hasOtherChanges

    fun finishBack() {
        saving = false
        showSaveChangesDialog = false
        val action = pendingExitAction
        pendingExitAction = null
        if (action != null) action() else onBack()
    }
    fun commit(values: List<CourseEntity>) {
        if (saving) return
        val clockError = values.firstNotNullOfOrNull { edited -> manualCourseClockError(edited,
            originalCourses.firstOrNull { it.id == edited.id }, state.periods) }
        if (clockError != null) { validationMessage = clockError; return }
        validationMessage = null
        saving = true
        onSave(originalGroup.courses, values) { success ->
            saving = false
            if (success) finishBack() else {
                pendingExitAction = null
                validationMessage = "保存失败，请重试"
            }
        }
    }

    fun updateArrangement(localKey: Long, transform: (CourseEntity) -> CourseEntity) {
        arrangements = arrangements.map { draft ->
            if (draft.localKey == localKey) draft.copy(course = captureOriginalPeriodTimes(
                transform(draft.course), state.periods, draft.course)) else draft
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
            commit(replacementsToSave)
            return
        }
        val conflictWeeks = conflictWeeksForEditedCourseGroup(
            originals = state.previewCourseEdits(originalGroup.courses),
            edited = state.previewCourseEdits(replacementsToSave),
            courses = state.courses,
            periodDefinitions = state.periods
        )
        if (conflictWeeks.isEmpty()) {
            commit(replacementsToSave)
        } else {
            showSaveChangesDialog = false
            pendingConflictSave = PendingCourseManagementConflictSave(
                replacements = replacementsToSave,
                conflictWeeks = conflictWeeks
            )
        }
    }

    fun requestBack() {
        if (saving) return
        when {
            hasOtherChanges -> showSaveChangesDialog = true
            hasConfirmedDeletion -> commit(remainingOriginals)
            else -> finishBack()
        }
    }

    fun discardOtherChanges() {
        if (hasConfirmedDeletion) {
            name = originalGroup.representative.name
            selectedColor = originalGroup.representative.customColorArgb
            arrangements = remainingOriginals.map { ManagedArrangementDraft(it.id, it) }
            showSaveChangesDialog = false
            commit(remainingOriginals)
        } else {
            finishBack()
        }
    }

    suspend fun requestArrangementDeletion(localKey: Long): Boolean {
        if (saving || pendingDelete != null || departing != null) return false
        val index = arrangements.indexOfFirst { it.localKey == localKey }
        if (index == -1) return false
        val pending = PendingArrangementDelete(localKey, index + 1, CompletableDeferred())
        pendingDelete = pending
        return try {
            pending.answer.await()
        } finally {
            if (pendingDelete === pending) pendingDelete = null
        }
    }

    fun confirmArrangementDeletion(pending: PendingArrangementDelete) {
        if (pendingDelete !== pending || pending.answer.isCompleted) return
        val index = arrangements.indexOfFirst { it.localKey == pending.localKey }
        if (index == -1) {
            pending.answer.complete(false)
            return
        }
        departing = DepartingArrangement(index, arrangements[index])
        arrangements = arrangements.filterNot { it.localKey == pending.localKey }
        pending.answer.complete(true)
    }

    fun finishArrangementRemoval(localKey: Long) {
        if (departing?.draft?.localKey == localKey) departing = null
    }

    val latestExitHandler = rememberUpdatedState<(()->Unit)->Unit> { action ->
        if (!saving) {
            pendingExitAction = action
            requestBack()
        }
    }
    DisposableEffect(group.key) {
        onExitHandlerChange { action -> latestExitHandler.value(action) }
        onDispose { onExitHandlerChange(null) }
    }

    BackHandler(enabled = hasChanges || saving) { requestBack() }

    Box(Modifier.fillMaxSize().then(if (saving) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        } else Modifier)) {
    DetailActivityScaffold(
        title = name.ifBlank { "课程详情" },
        config = state.config,
        onBack = ::requestBack,
        topBarActions = { topBackdrop ->
            CouiSettingsToolbarButton(
                backdrop = topBackdrop,
                onClick = {
                    revealedArrangement = null
                    val template = arrangements.lastOrNull()?.course ?: group.representative
                    val added = newManagedCourseArrangement(template, state.config, state.periods).copy(
                        name = name.ifBlank { template.name },
                        customColorArgb = selectedColor
                    )
                    arrangements = arrangements + ManagedArrangementDraft(nextLocalKey--, added)
                    scope.launch { listState.animateScrollToItem(arrangements.lastIndex + 1) }
                },
            ) {
                Icon(CouiIcons.Add, "添加安排", Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
            }
        }
    ) { cardBackdrop ->
        // Only the root-hosted pickers sample the completed page.
        val pickerBackdrop = LocalSettingsPopupBackdrop.current ?: cardBackdrop
        SleepDownSecondaryPageList(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentTopPadding = detailContentTopPadding(),
            contentBottomPadding = detailContentBottomPadding(),
        ) {
            item(key = "course-identity") {
                CourseIdentityCard(
                    name = name,
                    onNameChange = { name = it },
                    selectedColor = selectedColor,
                    onOpenColorPicker = {
                        revealedArrangement = null
                        colorPickerVisible = true
                    },
                    backdrop = cardBackdrop,
                    config = state.config
                )
            }
            itemsIndexed(displayedArrangements, key = { _, item -> item.localKey }) { index, draft ->
                val revealed = revealedArrangement == draft.localKey
                DisposableEffect(draft.localKey) {
                    onDispose {
                        if (revealedArrangement == draft.localKey) revealedArrangement = null
                        finishArrangementRemoval(draft.localKey)
                    }
                }
                GlassPreferenceSection(
                    "上课安排 ${index + 1}",
                    Modifier.animateItem(fadeInSpec = null, placementSpec = null),
                ) {
                    SwipeDeleteRow(
                        rowKey = draft.localKey,
                        surfaceColor = CouiTheme.colorScheme.container,
                        revealed = revealed,
                        onRevealChange = { open ->
                            if (open) revealedArrangement = draft.localKey
                            else if (revealedArrangement == draft.localKey) revealedArrangement = null
                        },
                        onDelete = { finishArrangementRemoval(draft.localKey) },
                        onDeleteFailed = {
                            departing?.takeIf { it.draft.localKey == draft.localKey }?.let { outgoing ->
                                arrangements = arrangements.toMutableList().apply {
                                    add(outgoing.index.coerceAtMost(size), outgoing.draft)
                                }
                                finishArrangementRemoval(draft.localKey)
                            }
                            validationMessage = "无法删除安排，请重试"
                        },
                        swipeEnabled = !saving || departing?.draft?.localKey == draft.localKey,
                        slideContent = false,
                        actionHeight = 48.dp,
                        expandActionOnDrag = false,
                        rowCorners = { SwipeDeleteCorners(24.dp) },
                        confirmDelete = { requestArrangementDeletion(draft.localKey) },
                    ) { slide, blocked, openOrClose ->
                        Box(slide) {
                            CourseArrangementEditorCard(
                                course = draft.course,
                                periods = state.periods,
                                totalWeeks = state.config.totalWeeks.coerceAtLeast(1),
                                config = state.config,
                                backdrop = cardBackdrop,
                                enabled = !blocked && !revealed,
                                onCourseChange = { changed -> updateArrangement(draft.localKey) { changed } },
                                onOpenPicker = {
                                    revealedArrangement = null
                                    pickerRequest = it
                                    pickerVisible = true
                                }
                            )
                            if (revealed && !blocked) {
                                Box(Modifier.matchParentSize().clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClickLabel = "收起删除操作",
                                    onClick = { openOrClose {} },
                                ))
                            }
                        }
                    }
                }
            }
            if (displayedArrangements.isEmpty()) {
                item(key = "empty-arrangements") {
                    CourseManagementSettingsSection("上课安排", state.config, cardBackdrop) {
                        SettingsInfoRow("已移除全部安排", "返回时会删除这门课程。")
                    }
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
                    ?: if (hasConfirmedDeletion) "已确认的删除会提交。其余课程修改要一起保存吗？"
                    else "你修改了课程安排。保存完成后才会退出课程详情。",
                actions = listOf(
                    LiquidAlertAction(
                        label = "继续编辑",
                        style = LiquidAlertActionStyle.Cancel,
                        onClick = {
                            validationMessage = null
                            pendingExitAction = null
                            showSaveChangesDialog = false
                        }
                    ),
                    LiquidAlertAction(
                        label = if (hasConfirmedDeletion) "放弃其他修改" else "不保存",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = ::discardOtherChanges
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
                    pendingExitAction = null
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
                        style = LiquidAlertActionStyle.Cancel,
                        onClick = { pending.answer.complete(false) }
                    ),
                    LiquidAlertAction(
                        label = "删除",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = { confirmArrangementDeletion(pending) }
                    )
                ),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = { pending.answer.complete(false) }
            )
        }
        if (!showSaveChangesDialog && !saving) validationMessage?.let { message ->
            LiquidAlertDialog(
                title = "课程操作未完成",
                message = message,
                actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                    validationMessage = null
                }),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = { validationMessage = null },
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
                    commit(pending.replacements)
                },
                onReturn = { pendingConflictSave = null; pendingExitAction = null },
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
}

@Composable
private fun CourseManagementSettingsSection(
    title: String,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPreferenceSection(title) {
        SettingsGroup(backdrop, config, content = content)
    }
}

@Composable
private fun CourseIdentityCard(
    name: String,
    onNameChange: (String) -> Unit,
    selectedColor: Long?,
    onOpenColorPicker: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    CourseManagementSettingsSection("课程信息", config, backdrop) {
        SettingsTextFieldRow(
            title = "课程名称",
            value = name,
            onValueChange = onNameChange,
            placeholder = "输入课程名称"
        )
        if (courseCardAllowsCustomOverrides(config)) {
            SettingsDivider()
            SettingsArrowPreference(
                modifier = Modifier.fillMaxWidth(),
                title = "课程颜色",
                endActions = {
                    if (selectedColor == null) {
                        Text("自动", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Box(Modifier.size(18.dp).background(Color(selectedColor.toInt()), Capsule())
                            .semantics { contentDescription = "自定义颜色" })
                    }
                },
                onClick = onOpenColorPicker,
            )
        }
    }
}

@Composable
private fun CourseArrangementEditorCard(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    totalWeeks: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    enabled: Boolean,
    onCourseChange: (CourseEntity) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit
) {
    val effective = projectCourseArrangement(captureOriginalPeriodTimes(course, periods), config, periods)
    val matchingCustomClock = course.hasCustomTime() && course.customPeriodTimes.isNullOrBlank() &&
        !effective.hasCustomTime() && !effective.isHiddenByPeriodAlignment()
    val displayedPeriodIndices = effective.periods.ifEmpty { course.periods }
    SettingsGroup(backdrop, config, surfaceColorOverride = Color.Transparent) {
        SettingsPickerValueRow(
            title = "上课星期",
            value = "周${weekdayLabel(course.weekday)}",
            enabled = enabled,
            onClick = {
                onOpenPicker(
                    CourseEditorPickerRequest.Grid(
                        title = "选择上课星期",
                        labels = (1..7).map { "周${weekdayLabel(it)}" },
                        selectedIndices = buildSet { add((course.weekday - 1).coerceIn(0, 6)) },
                        preferredColumns = 7,
                        onConfirm = { set ->
                            set.minOrNull()?.let { selected ->
                                onCourseChange(course.copy(weekday = selected + 1))
                            }
                        }
                    )
                )
            }
        )
        SettingsDivider()
        SettingsPickerValueRow(
            title = "上课节次",
            value = if (effective.hasCustomTime()) courseTimeLabel(effective, periods)
            else "第${displayedPeriodIndices.minOrNull() ?: 1}-${displayedPeriodIndices.maxOrNull() ?: 1}节",
            enabled = enabled,
            valueBelowTitle = effective.hasCustomTime(),
            onClick = {
                val values = periods.map(PeriodEntity::periodIndex).distinct().sorted().ifEmpty { listOf(1) }
                val displayedPeriods = if (matchingCustomClock) effective.periods else course.periods
                val startIndex = values.indexOf(displayedPeriods.minOrNull()).coerceAtLeast(0)
                val endIndex = values.indexOf(displayedPeriods.maxOrNull()).coerceAtLeast(startIndex)
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
                        periodIndices = course.periods.distinct().sorted(),
                        availablePeriodIndices = values,
                        customPeriodTimes = course.customPeriodTimes,
                        customTimeMatchesPeriods = matchingCustomClock,
                        minimumStartTime = courseClockOrigin(periods),
                        onConfirmPeriods = { start, end ->
                            onCourseChange(
                                course.copy(
                                    periods = values.subList(start, end + 1),
                                    originalPeriodTimes = null,
                                    customStartTime = null,
                                    customEndTime = null,
                                    customPeriodTimes = null
                                )
                            )
                        },
                        onConfirmLessonTimes = { times ->
                            onCourseChange(course.copy(
                                periods = times.map { it.index },
                                originalPeriodTimes = null,
                                customStartTime = times.first().start.toString(),
                                customEndTime = times.last().end.toString(),
                                customPeriodTimes = encodeCoursePeriodTimes(times)
                            ))
                        },
                        onConfirmCustomTime = { startText, endText ->
                            val start = LocalTime.parse(startText)
                            val end = LocalTime.parse(endText)
                            val anchors = courseAnchorPeriodsForTimeRange(start, end, periods).ifEmpty { course.periods }
                            onCourseChange(
                                course.copy(
                                    periods = anchors,
                                    originalPeriodTimes = null,
                                    customStartTime = startText,
                                    customEndTime = endText,
                                    customPeriodTimes = null
                                )
                            )
                        }
                    )
                )
            }
        )
        SettingsDivider()
        SettingsPickerValueRow(
            title = "上课周次",
            value = compactWeekSelectionLabel(course.weeks),
            enabled = enabled,
            onClick = { onOpenPicker(arrangementWeeksPicker(course, totalWeeks, onCourseChange)) },
        )
        SettingsDivider()
        SettingsTextFieldRow(
            title = "地点",
            value = course.location.orEmpty(),
            onValueChange = { onCourseChange(course.copy(location = it.ifBlank { null })) },
            enabled = enabled,
            placeholder = "未填写"
        )
        SettingsDivider()
        SettingsTextFieldRow(
            title = "教师",
            value = course.teacher.orEmpty(),
            onValueChange = { onCourseChange(course.copy(teacher = it.ifBlank { null })) },
            enabled = enabled,
            placeholder = "未填写"
        )
        SettingsDivider()
        SettingsTextFieldRow(
            title = "备注",
            value = course.note.orEmpty(),
            onValueChange = { onCourseChange(course.copy(note = it.ifBlank { null })) },
            enabled = enabled,
            placeholder = "未填写"
        )
    }
}

private fun arrangementWeeksPicker(
    course: CourseEntity,
    totalWeeks: Int,
    onCourseChange: (CourseEntity) -> Unit,
): CourseEditorPickerRequest.Grid {
    val values = ((1..totalWeeks).toList() + course.weeks).distinct().sorted()
    val current = course.weeks.toSet()
    val presets = listOf(
        "全部" to CourseWeekSelectionMode.EVERY,
        "单周" to CourseWeekSelectionMode.ODD,
        "双周" to CourseWeekSelectionMode.EVEN,
    ).mapNotNull { (label, mode) ->
        val weeks = weeksForCourseWeekSelectionMode(mode, current, totalWeeks)
        val indices = values.indices.filterTo(linkedSetOf()) { values[it] in weeks }
        indices.takeIf { it.isNotEmpty() }?.let { CourseEditorPickerRequest.GridPreset(label, it) }
    }
    return CourseEditorPickerRequest.Grid(
        title = "选择上课周次",
        labels = values.map(Int::toString),
        selectedIndices = values.indices.filterTo(linkedSetOf()) { values[it] in current },
        preferredColumns = 6,
        presets = presets,
        requireSelection = true,
        onConfirm = { indices ->
            val weeks = indices.sorted().map { values[it] }
            if (weeks.toSet() != current) {
                onCourseChange(course.copy(
                    weeks = weeks,
                    weekParity = inferCourseWeekSelectionMode(weeks).toWeekParity(),
                ))
            }
        },
    )
}
