package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

private val WeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

internal fun adjustmentDateLabel(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日 ${WeekdayLabels[date.dayOfWeek.value - 1]}"

@Composable
internal fun ScheduleAdjustmentsSettingsRow(config: ScheduleConfigEntity, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(ScheduleAdjustmentsActivity.ArrangementsExtra)?.let(currentOnChange)
        }
    }
    val entries = remember(value) { decodeScheduleAdjustments(value) }
    SettingsNavigationRow(
        title = "调休课表",
        subtitle = if (entries.isEmpty()) "自定义停课与补课，或获取节假日后确认" else
            "${entries.count { it.sourceDate == null }} 天停课 · ${entries.count { it.sourceDate != null }} 天补课",
        onClick = {
            context.openRegisteredActivity(TransitionRouteId.SettingsToScheduleAdjustments,
                ScheduleAdjustmentsActivity.intent(context, config, value),
                launchActivity = { launcher.launch(it) })
        },
    )
}

private data class AdjustmentDeleteConfirmation(
    val entry: ScheduleAdjustment,
    val answer: CompletableDeferred<Boolean>,
)

@Composable
internal fun ScheduleAdjustmentsScreen(
    state: AppState,
    entries: List<ScheduleAdjustment>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (previous: List<ScheduleAdjustment>, updated: List<ScheduleAdjustment>) -> Unit,
) {
    val latestEntries by rememberUpdatedState(entries)
    val config = state.config
    var year by rememberSaveable {
        mutableIntStateOf((parseScheduleDate(config.termStartDate)?.year ?: LocalDate.now().year).coerceIn(2000, 2100))
    }
    var yearDialogMounted by remember { mutableStateOf(false) }
    var yearDialogVisible by remember { mutableStateOf(false) }
    var queuedFetchYear by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var duplicateNotice by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<HolidayAdjustmentPreview?>(null) }
    var previewVisible by remember { mutableStateOf(false) }
    var duplicateAfterPreview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<AdjustmentDraft?>(null) }
    var draftVisible by remember { mutableStateOf(false) }
    var draftError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by remember { mutableStateOf<AdjustmentDeleteConfirmation?>(null) }
    var departing by remember { mutableStateOf<ScheduleAdjustment?>(null) }
    val displayedEntries = remember(entries, departing) {
        val outgoing = departing
        if (outgoing != null && entries.none { it.date == outgoing.date })
            (entries + outgoing).sortedBy(ScheduleAdjustment::date)
        else entries
    }
    val scope = rememberCoroutineScope()
    val swipes = remember { AdjustmentListSwipeState() }
    val listState = rememberLazyListState()
    // Make-up days refer to the original teaching date, including days that are now days off.
    val teachingState = remember(state) { state.copy(config = state.config.copy(scheduleAdjustmentsJson = "")) }

    fun inTerm(date: LocalDate): Boolean = !config.autoCurrentWeek || scheduleWeekForDateOrNull(config, date) != null

    fun coursePreview(source: String): String {
        val date = parseScheduleDate(source) ?: return "请选择原课程日期"
        val week = adjustedTeachingWeekForDate(config, date) ?: return "原课程日期不在学期内"
        val courses = coursesForDate(teachingState.copy(config = teachingState.config.copy(currentWeek = week)), date)
        return "第 $week 周 · ${courses.size} 门课" +
            courses.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\n", limit = 4) { it.name }.orEmpty()
    }

    fun validEntry(target: String, origin: String?, label: String = ""): ScheduleAdjustment {
        val date = requireNotNull(parseScheduleDate(target)) { "请选择调休日期" }
        require(inTerm(date)) { "调休日期不在学期内" }
        if (origin != null) {
            val source = requireNotNull(parseScheduleDate(origin)) { "请选择原课程日期" }
            require(adjustedTeachingWeekForDate(config, source) != null) { "原课程日期不在学期内" }
        }
        return scheduleAdjustmentFromInput(target, origin, label)
    }

    fun beginEdit(entry: ScheduleAdjustment?) {
        if (saving) return
        draft = if (entry == null) AdjustmentDraft(LocalDate.now().toString(), rest = true, source = "")
        else AdjustmentDraft(entry.date, entry.sourceDate == null, entry.sourceDate.orEmpty(),
            originalDate = entry.date, label = entry.label, allDayPlaceholder = entry.allDayPlaceholder)
        draftError = null
        draftVisible = true
    }

    fun closeEditor() {
        draftVisible = false
    }

    fun commitDraft() {
        if (saving || !draftVisible) return
        val current = draft ?: return
        scope.launch {
            try {
                val previous = latestEntries
                val entry = validEntry(current.date, current.source.takeUnless { current.rest }, current.label)
                    .copy(allDayPlaceholder = current.rest && current.allDayPlaceholder)
                val updated = replaceScheduleAdjustment(previous, current.originalDate, entry)
                if (updated != previous) onSave(previous, updated)
                error = null
                closeEditor()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                draftError = adjustmentFailureMessage(failure)
            }
        }
    }

    suspend fun confirmRemoval(entry: ScheduleAdjustment): Boolean {
        if (saving || deleteConfirmation != null || departing != null) return false
        val answer = CompletableDeferred<Boolean>()
        deleteConfirmation = AdjustmentDeleteConfirmation(entry, answer)
        try {
            if (!answer.await()) return false
            val previous = latestEntries
            val updated = previous.filterNot { it.date == entry.date }
            if (updated == previous) return false
            departing = entry
            try {
                onSave(previous, updated)
                error = null
            } catch (failure: Exception) {
                departing = null
                throw failure
            }
            return true
        } finally {
            if (deleteConfirmation?.answer === answer) deleteConfirmation = null
        }
    }

    fun finishRemoval(date: String) {
        if (departing?.date == date) departing = null
    }

    fun requestBack() {
        if (saving) return
        if (preview != null) previewVisible = false else onDismiss()
    }

    fun fetchHoliday(year: Int) {
        loading = true
        error = null
        scope.launch {
            try {
                val plans = planHolidays(HolidayApi.fetch(year)).mapNotNull { plan ->
                    val rests = plan.restDates.filter(::inTerm)
                    val makeups = plan.makeups.filter { inTerm(it.date) }
                    if (rests.isEmpty() && makeups.isEmpty()) null
                    else plan.copy(restDates = rests, makeups = makeups)
                }
                if (plans.isEmpty()) {
                    error = "$year 年没有学期内的节假日"
                    return@launch
                }
                val current = latestEntries
                val (added, pending) = plans.partition { it.isAlreadyAdded(current) }
                if (pending.isEmpty()) {
                    duplicateNotice = true
                    return@launch
                }
                val saved = current.associateBy { it.date }
                preview = HolidayAdjustmentPreview(year, pending.map { plan ->
                    HolidayReview(plan, restSelected = true, makeups = plan.makeups.map { makeup ->
                        val existing = saved[makeup.date.toString()]?.sourceDate?.let(LocalDate::parse)
                        val source = (existing ?: makeup.suggestedSource)?.takeIf {
                            adjustedTeachingWeekForDate(config, it) != null
                        }
                        MakeupReview(makeup.date, source?.toString().orEmpty(), source != null)
                    })
                }, added, existingDates = saved.keys)
                previewVisible = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = when (failure) {
                    is IOException -> "连接节假日服务失败，请检查网络后重试"
                    is SerializationException -> "节假日数据格式异常，请稍后重试"
                    else -> failure.message ?: "获取失败，请稍后重试"
                }
            } finally {
                loading = false
            }
        }
    }

    fun updateReview(index: Int, transform: (HolidayReview) -> HolidayReview) {
        val current = preview ?: return
        val plan = current.reviews.getOrNull(index) ?: return
        preview = current.copy(reviews = current.reviews.toMutableList().also { it[index] = transform(plan) })
        error = null
    }

    fun updateMakeup(plan: Int, index: Int, transform: (MakeupReview) -> MakeupReview) {
        updateReview(plan) { review ->
            review.copy(makeups = review.makeups.toMutableList().also { it[index] = transform(it[index]) })
        }
    }

    fun applySelected() {
        if (saving || !previewVisible) return
        val pending = preview ?: return
        scope.launch {
            try {
                val imported = buildList {
                    pending.reviews.forEach { review ->
                        if (review.restSelected) review.plan.restDates.forEach {
                            add(validEntry(it.toString(), null, review.plan.name))
                        }
                        review.makeups.filter { it.selected }.forEach {
                            add(validEntry(it.date.toString(), it.source, review.plan.name))
                        }
                    }
                }
                require(imported.isNotEmpty()) { "请至少选择一个日期" }
                val previous = latestEntries
                val dates = imported.mapTo(hashSetOf()) { it.date }
                val updated = (previous.filterNot { it.date in dates } + imported).sortedBy { it.date }
                validateScheduleAdjustments(updated)
                duplicateAfterPreview = updated == previous
                if (!duplicateAfterPreview) onSave(previous, updated)
                previewVisible = false
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = adjustmentFailureMessage(failure)
            }
        }
    }

    BackHandler(enabled = saving || preview != null, onBack = ::requestBack)
    DetailActivityScaffold(
        title = "调休课表",
        config = config,
        onBack = ::requestBack,
    ) { backdrop ->
        SleepDownSecondaryPageList(
            modifier = Modifier.fillMaxSize(),
            contentTopPadding = detailContentTopPadding() + 8.dp,
            contentBottomPadding = detailContentBottomPadding(),
            state = listState,
        ) {
            item("adjustment-actions") {
                SettingsGroup(backdrop, config) {
                    SettingsNavigationRow("新增调休日", enabled = !saving) { beginEdit(null) }
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = if (loading) "正在获取…" else "获取节假日与调休",
                        subtitle = "选择年份，预览停课与补课安排",
                        enabled = !loading && !saving,
                        onClick = {
                            queuedFetchYear = null
                            yearDialogMounted = true
                            yearDialogVisible = true
                        },
                    )
                    error.takeIf { preview == null }?.let {
                        SettingsDivider()
                        SettingsInfoRow(it, "")
                    }
                }
            }
            scheduleAdjustmentItems(
                entries = displayedEntries,
                config = config,
                swipes = swipes,
                enabled = !saving,
                departingDate = departing?.date,
                onEdit = ::beginEdit,
                confirmDelete = ::confirmRemoval,
                onDelete = { finishRemoval(it.date) },
                onRowDisposed = ::finishRemoval,
                onDeleteFailed = {
                    departing = null
                    error = adjustmentFailureMessage(it)
                },
            )
        }

        preview?.let { pending ->
            val selectedDates = remember(pending) { pending.selectedDates() }
            val replacements = remember(pending) { selectedDates.count { it in pending.existingDates } }
            HolidayAdjustmentPreviewSheet(
                visible = previewVisible,
                saving = saving,
                preview = pending,
                config = config,
                selectedCount = selectedDates.size,
                replacementCount = replacements,
                error = error,
                coursePreview = ::coursePreview,
                onRestSelection = { plan, selected -> updateReview(plan) { it.copy(restSelected = selected) } },
                onMakeupSelection = { plan, index, selected -> updateMakeup(plan, index) { it.copy(selected = selected) } },
                onMakeupSource = { plan, index, value -> updateMakeup(plan, index) {
                    it.copy(source = parseScheduleDate(value)?.toString().orEmpty())
                } },
                onApply = ::applySelected,
                onDismiss = { if (!saving) previewVisible = false },
                onDismissFinished = {
                    preview = null
                    error = null
                    if (duplicateAfterPreview) duplicateNotice = true
                    duplicateAfterPreview = false
                },
            )
        }

        if (yearDialogMounted) HolidayYearDialog(
            show = yearDialogVisible, initialYear = year, backdrop = backdrop, config = config,
            onDismiss = { yearDialogVisible = false },
            onDismissFinished = {
                yearDialogMounted = false
                queuedFetchYear?.let(::fetchHoliday)
                queuedFetchYear = null
            },
            onFetch = {
                year = it
                queuedFetchYear = it
                yearDialogVisible = false
            },
        )
        if (duplicateNotice) LiquidAlertDialog(
            title = "调休安排已是最新",
            message = "已有安排与本次获取的安排一致。",
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) { duplicateNotice = false }),
            backdrop = backdrop, config = config, onDismissRequest = { duplicateNotice = false },
        )
        deleteConfirmation?.let { confirmation ->
            LiquidAlertDialog(
                title = "删除这条调休安排？",
                message = "${adjustmentDateLabel(LocalDate.parse(confirmation.entry.date))}的调休将从当前课表移除。",
                actions = listOf(
                    LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { confirmation.answer.complete(false) },
                    LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) { confirmation.answer.complete(true) },
                ),
                backdrop = backdrop, config = config, onDismissRequest = { confirmation.answer.complete(false) },
            )
        }
        draft?.let { current ->
            AdjustmentEditorDialog(
                show = draftVisible,
                saving = saving,
                draft = current,
                error = draftError,
                backdrop = backdrop,
                config = config,
                onDraftChange = { if (!saving) { draft = it; draftError = null } },
                coursePreview = ::coursePreview,
                onSave = ::commitDraft,
                onCancel = ::closeEditor,
                onDismissFinished = { draft = null; draftError = null },
            )
        }
    }
}

private fun adjustmentFailureMessage(failure: Exception): String =
    if (failure is IllegalArgumentException || failure is IllegalStateException)
        failure.message ?: "无法保存调休安排，请重试"
    else "无法保存调休安排，请重试"
