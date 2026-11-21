package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.os.Build
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.icons.CouiIcons
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.sheet.ModalBottomSheet
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.ScheduleProfileEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiLiquidMaterial
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsEditorSheetToolbar
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.RefreshCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.RefreshTerm
import com.xiaomanjun.sleepdownschedule.domain.schedule.compactPeriodsLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.compactWeeksLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekdayLabel
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ReviewPresentation(val review: PendingScheduleRefresh, val scheduleName: String)

@Composable
internal fun AutoRefreshReviewHost(
    config: ScheduleConfigEntity,
    schedules: List<ScheduleProfileEntity>,
    enabled: Boolean = true,
    openRequest: Int = 0,
    confirmReview: suspend (Context, String, Set<Int>, Boolean) -> String = AutoRefreshScheduleCoordinator::confirmReview,
) {
    val context = LocalContext.current
    val pending by AutoRefreshReviewStore.observe(context).collectAsState()
    val deferredId by AutoRefreshReviewStore.deferredReviewId.collectAsState()
    val profile by AutoRefreshScheduleStore.observe(context).collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var seenRequest by remember { mutableIntStateOf(openRequest) }
    var retained by remember { mutableStateOf<ReviewPresentation?>(null) }
    LaunchedEffect(openRequest) {
        if (openRequest != seenRequest) {
            AutoRefreshReviewStore.reopenReview()
            seenRequest = openRequest
        }
    }
    val current = pending?.takeIf { it.belongsTo(profile) }?.let { review ->
        schedules.firstOrNull { it.id == review.scheduleId }?.let { ReviewPresentation(review, it.name) }
    }
    val presentation = current ?: retained ?: return
    val review = presentation.review
    val visible = current != null && enabled && lifecycle == Lifecycle.State.RESUMED && deferredId != review.id
    if (visible) SideEffect { retained = current }
    key(review.id) {
        val differences = remember(review) { review.differences }
        var selected by rememberSaveable(stateSaver = listSaver(
            save = { it.toList() }, restore = { it.toSet() },
        )) { mutableStateOf(emptySet<Int>()) }
        var useTerm by rememberSaveable { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val interactive = visible && !saving
        val dark = appUsesDarkTheme(config)
        val foreground = sleepDownPanelForegroundColor(config)
        val pageColor = settingsPageBackground(config)
        val windowBackdrop = rememberCouiWindowBackdrop()
        val material = if (Build.VERSION.SDK_INT >= 33 && windowBackdrop != null &&
            LocalCouiMaterialEnabled.current && !LocalCouiLiquidMaterial.current
        ) CouiSheetDefaults.material(dark) else null
        val totalCount = differences.size + if (review.termChanged) 1 else 0
        val selectedCount = selected.size + if (useTerm) 1 else 0
        val hasSelection = selectedCount > 0
        val allSelected = hasSelection && selectedCount == totalCount
        var lastWasAllSelected by remember { mutableStateOf(false) }
        if (hasSelection) SideEffect { lastWasAllSelected = allSelected }
        val showClearAction = if (hasSelection) allSelected else lastWasAllSelected
        val dismiss = {
            if (interactive) {
                selected = emptySet()
                useTerm = false
                error = null
                AutoRefreshReviewStore.deferReview(review.id)
            }
        }
        ModalBottomSheet(
            visible = visible,
            onDismissRequest = dismiss,
            onDismissFinished = { if (!visible) retained = null },
            title = "核对课表更新",
            dismissDescription = "取消核对课表更新",
            dark = dark,
            draggable = !saving,
            dismissOnClickOutside = !saving,
            showDragHandle = false,
            contentDrawsBehindNavigationBar = true,
            colors = CouiSheetDefaults.colors(dark).copy(surface = pageColor),
            material = material,
            backdrop = windowBackdrop,
        ) {
            val listBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "auto-refresh-review-list") {
                drawRect(pageColor)
                drawContent()
            }
            val density = LocalDensity.current
            val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            var toolbarHeightPx by remember { mutableIntStateOf(0) }
            val toolbarHeight = if (toolbarHeightPx > 0) with(density) { toolbarHeightPx.toDp() }
                else CouiSheetDefaults.toolbarTopSpacing + CouiSheetDefaults.toolbarHeight
            Box(Modifier.fillMaxSize()) {
                SleepDownSecondaryPageList(
                    contentTopPadding = toolbarHeight + 4.dp,
                    contentBottomPadding = 16.dp + navigationBarPadding +
                        (if (hasSelection) CardDeleteBottomSpace else 0.dp),
                    modifier = Modifier.fillMaxSize().glassBackdropProducer(listBackdrop),
                    style = SleepDownSecondaryPageStyle.Content,
                    sectionSpacing = 10.dp,
                ) {
                    item("summary") {
                        SettingsGroup(null, config) {
                            SettingsInfoRow("${presentation.scheduleName} · ${review.schoolName}",
                                "$totalCount 项变更 · 已选 $selectedCount 项")
                        }
                    }
                    error?.let { message -> item("error") {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } }
                    if (review.termChanged) {
                        item("term") {
                            ReviewCard("学期信息", ReviewChange.Updated, foreground,
                                termFields(review.beforeTerm, review.afterTerm), useTerm, interactive) { useTerm = it }
                        }
                    }
                    itemsIndexed(differences, key = { index, _ -> index }) { index, change ->
                        val before = change.before
                        val after = change.after
                        val kind = when {
                            before == null -> ReviewChange.Added
                            after == null -> ReviewChange.Removed
                            else -> ReviewChange.Updated
                        }
                        val fields = remember(change) { courseFields(before, after) }
                        ReviewCard(checkNotNull(before ?: after).name, kind, foreground,
                            fields, index in selected, interactive) {
                            selected = if (it) selected + index else selected - index
                        }
                    }
                }
                ProgressiveBackdropBlur(
                    backdrop = listBackdrop,
                    modifier = Modifier.align(Alignment.TopCenter),
                    tintColor = pageColor,
                    height = toolbarHeight + 11.dp,
                    fallbackTintStops = listOf(
                        0f to pageColor.copy(alpha = 0.42f),
                        0.68f to pageColor.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    ),
                )
                Box(Modifier.fillMaxWidth().onSizeChanged { toolbarHeightPx = it.height }) {
                    SettingsEditorSheetToolbar(
                        title = "核对课表更新",
                        dark = dark,
                        backdrop = listBackdrop,
                        enabled = interactive,
                        onCancel = dismiss,
                        actionLabel = if (saving) "保存中…" else "确认",
                        onAction = {
                            if (!saving) {
                                saving = true
                                error = null
                                scope.launch {
                                    try {
                                        confirmReview(context, review.id, selected, useTerm)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = failure.message ?: "保存失败，请重试"
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        },
                    )
                }
                CardSelectionFloatingAction(
                    visible = hasSelection,
                    label = if (showClearAction) "全不选" else "全选",
                    icon = if (showClearAction) CouiIcons.Close else CouiIcons.Check,
                    ink = foreground,
                    enabled = interactive && hasSelection,
                    backdrop = listBackdrop,
                ) {
                    selected = if (allSelected) emptySet() else differences.indices.toSet()
                    useTerm = !allSelected && review.termChanged
                }
            }
        }
    }
}

private enum class ReviewField(val label: String) {
    Name("名称"), Arrangement("安排"), Weeks("周次"), Time("时间"),
    Location("地点"), Teacher("教师"), Credit("学分"), Note("备注"),
    TermStart("开学日期"), TermWeeks("教学周数"), Record("教务记录");

    val icon: ImageVector
        @Composable get() = when (this) {
            Arrangement -> Icons.Outlined.Schedule
            Time -> CouiIcons.Time
            Weeks, TermStart, TermWeeks -> CouiIcons.Calendar
            Location -> CouiIcons.Filled.Location
            Teacher -> CouiIcons.Filled.UserCircle
            Credit -> CouiIcons.Filled.Certificate
            Name, Note, Record -> CouiIcons.Info
        }
}

private data class RefreshField(val kind: ReviewField, val before: String?, val after: String?)

private enum class ReviewChange(val label: String, val tone: ChangePreviewTone) {
    Added("新增", ChangePreviewTone.Add),
    Removed("移除", ChangePreviewTone.Remove),
    Updated("变更", ChangePreviewTone.Edit),
}

@Composable
private fun ReviewCard(
    title: String,
    change: ReviewChange,
    foreground: Color,
    fields: List<RefreshField>,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    ChangePreviewSurface(
        foreground,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 3.dp, bottom = 12.dp),
        spacing = 3.dp,
        cornerRadius = 20.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = selected,
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelect,
            ).padding(top = 7.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = foreground, modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChangePreviewBadge(change.label, change.tone, foreground, shape = RoundedCornerShape(50))
            }
            Checkbox(checked = selected, enabled = enabled)
        }
        val hasBefore = fields.any { it.before != null }
        val hasAfter = fields.any { it.after != null }
        if (hasBefore) ReviewSnapshot(fields, before = true, foreground = foreground)
        if (hasBefore && hasAfter) HorizontalDivider(color = foreground.copy(alpha = 0.12f))
        if (hasAfter) ReviewSnapshot(fields, before = false, foreground = foreground)
    }
}

@Composable
private fun ReviewSnapshot(fields: List<RefreshField>, before: Boolean, foreground: Color) {
    val ink = if (before) foreground.copy(alpha = 0.72f) else foreground
    val description = if (before) "原有信息" else "更新后的信息"
    val values = fields.mapNotNull { field ->
        val value = if (before) field.before else field.after
        value?.let { field.kind to it.lineSequence().joinToString(" ") }
    }
    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { (field, value) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(field.icon, contentDescription = field.label,
                            tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
                        Text(
                            text = value,
                            modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                            color = ink,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (before) FontWeight.Normal else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun termFields(before: RefreshTerm, after: RefreshTerm) = listOfNotNull(
    changedField(ReviewField.TermStart, before.startDate, after.startDate),
    changedField(ReviewField.TermWeeks, "${before.totalWeeks} 周", "${after.totalWeeks} 周"),
)

private fun courseFields(before: RefreshCourse?, after: RefreshCourse?): List<RefreshField> {
    val original = before?.details().orEmpty()
    val updated = after?.details().orEmpty()
    if (before == null) return updated.mapNotNull { (kind, value) ->
        value?.let { RefreshField(kind, null, it) }
    }
    if (after == null) return original.mapNotNull { (kind, value) ->
        value?.let { RefreshField(kind, it, null) }
    }
    return buildList {
        changedField(ReviewField.Name, before.name, after.name)?.let(::add)
        updated.forEach { (kind, value) ->
            val previous = original[kind]
            if (previous != value) {
                add(RefreshField(kind, previous ?: "未设置", value ?: "未设置"))
            } else if (value != null && kind != ReviewField.Credit && kind != ReviewField.Note) {
                add(RefreshField(kind, value, value))
            }
        }
        if (none { it.before != it.after }) add(RefreshField(ReviewField.Record, null, "教务记录已更新"))
    }
}

private fun changedField(kind: ReviewField, before: String?, after: String?): RefreshField? =
    if (before == after) null else RefreshField(kind, before ?: "未设置", after ?: "未设置")

private fun RefreshCourse.details(): Map<ReviewField, String?> {
    val clock = runCatching { parseCoursePeriodTimes(periodTimes ?: originalTimes) }.getOrDefault(emptyList())
    val day = "周${if (weekday in 1..7) scheduleWeekdayLabel(weekday) else weekday}"
    val arrangement = if (clock.isEmpty()) "$day · ${compactPeriodsLabel(periods)}" else day
    val time = when {
        clock.isNotEmpty() -> reviewTimeLabel(periods, clock)
        !start.isNullOrBlank() && !end.isNullOrBlank() -> "$start–$end"
        else -> null
    }
    val parityLabel = when (parity) {
        "ODD" -> "（单周）"
        "EVEN" -> "（双周）"
        else -> ""
    }
    return linkedMapOf(
        ReviewField.Arrangement to arrangement,
        ReviewField.Weeks to (compactWeeksLabel(weeks) + parityLabel),
        ReviewField.Time to time,
        ReviewField.Location to location?.trim()?.takeIf(String::isNotEmpty),
        ReviewField.Teacher to teacher?.trim()?.takeIf(String::isNotEmpty),
        ReviewField.Credit to credit?.toString(),
        ReviewField.Note to note?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun reviewTimeLabel(periods: List<Int>, clock: List<CoursePeriodTime>): String {
    val times = clock.associateBy(CoursePeriodTime::index)
    val indices = (periods + times.keys).distinct().sorted()
    return buildList {
        var first = indices.first()
        indices.forEachIndexed { index, last ->
            val next = indices.getOrNull(index + 1)
            val end = times[last]?.end
            if (next != null && next == last + 1 && (next in times) == (end != null)) return@forEachIndexed
            val label = if (first == last) "第${first}节" else "第$first–${last}节"
            add(if (end == null) label else "$label ${times.getValue(first).start}–$end")
            if (next != null) first = next
        }
    }.joinToString(" · ")
}
