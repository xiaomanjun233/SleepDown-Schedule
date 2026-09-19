package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.ui.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.openRegisteredActivity
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

private val WeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun weekdayText(date: LocalDate) = WeekdayLabels[date.dayOfWeek.value - 1]

private fun shortDate(date: LocalDate) = "${date.monthValue}/${date.dayOfMonth}"

@Composable
fun ScheduleAdjustmentsSettings(state: AppState, backdrop: Backdrop?, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(ScheduleAdjustmentsActivity.ArrangementsExtra)?.let(currentOnChange)
        }
    }
    val entries = remember(value) { decodeScheduleAdjustments(value) }
    GlassPreferenceSection("调休课表") {
        SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
            SettingsNavigationRow(
                title = "调休课表",
                subtitle = if (entries.isEmpty()) "自定义停课与补课，或获取节假日后确认" else
                    "${entries.count { it.sourceDate == null }} 天停课 · ${entries.count { it.sourceDate != null }} 天补课",
                onClick = {
                    context.openRegisteredActivity(TransitionRouteId.SettingsToScheduleAdjustments,
                        ScheduleAdjustmentsActivity.intent(context, state.config, value),
                        launchActivity = { launcher.launch(it) })
                }
            )
        }
    }
}

/** A fetched holiday period plus the review state; nothing is written before 采用所选日期. */
private data class HolidayReview(
    val plan: HolidayPlan,
    val restSelected: Boolean,
    val makeups: List<MakeupReview>
)

private data class MakeupReview(val date: LocalDate, val source: String, val selected: Boolean)

/** The single adjustment being edited in the centered picker; nothing reaches the list before 保存. */
private data class AdjustmentDraft(
    val date: String,
    val rest: Boolean,
    val source: String,
    val isNew: Boolean
)

private enum class AdjustmentPickerPage { DETAILS, TARGET_DATE, SOURCE_DATE }

@Composable
internal fun ScheduleAdjustmentsScreen(
    state: AppState, initial: List<ScheduleAdjustment>,
    onDismiss: () -> Unit, onConfirm: (List<ScheduleAdjustment>) -> Unit
) {
    var entries by rememberSaveable(stateSaver = Saver<List<ScheduleAdjustment>, String>(
        save = { encodeScheduleAdjustments(it) }, restore = { decodeScheduleAdjustments(it) }
    )) { mutableStateOf(initial) }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var loading by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<HolidayReview>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showExitConfirm by remember { mutableStateOf(false) }
    // The centered picker edits one adjustment at a time; nothing leaves this screen before 保存.
    var draft by remember { mutableStateOf<AdjustmentDraft?>(null) }
    var draftError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ScheduleAdjustment?>(null) }
    // The source always means the ORIGINAL teaching date, even if that day itself is on holiday.
    val originalState = remember(state) { state.copy(config = state.config.copy(scheduleAdjustmentsJson = "")) }
    fun inTerm(candidate: LocalDate): Boolean =
        !state.config.autoCurrentWeek || scheduleWeekForDateOrNull(state.config, candidate) != null
    fun preview(sourceDate: String): String {
        val parsed = parseScheduleDate(sourceDate) ?: return "请选择原课程日期"
        val week = adjustedTeachingWeekForDate(state.config, parsed) ?: return "原课程日期不在学期内"
        val courses = coursesForDate(originalState.copy(config = originalState.config.copy(currentWeek = week)), parsed)
        return "第 $week 周 · ${courses.size} 门课" +
            courses.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\n", limit = 4) { it.name }.orEmpty()
    }
    fun validEntry(target: String, origin: String?, label: String = ""): ScheduleAdjustment {
        val targetDate = parseScheduleDate(target) ?: error("请选择调休日期")
        if (state.config.autoCurrentWeek) require(scheduleWeekForDateOrNull(state.config, targetDate) != null) { "调休日期不在学期内" }
        val sourceDate = origin?.let { parseScheduleDate(it) ?: error("请选择原课程日期") }
        if (sourceDate != null) require(adjustedTeachingWeekForDate(state.config, sourceDate) != null) { "原课程日期不在学期内" }
        return ScheduleAdjustment(targetDate.toString(), sourceDate?.toString(), label).also {
            validateScheduleAdjustments(listOf(it))
        }
    }
    val changed = remember(entries, initial) {
        encodeScheduleAdjustments(entries) != encodeScheduleAdjustments(initial)
    }
    fun beginEdit(entry: ScheduleAdjustment) {
        draft = AdjustmentDraft(entry.date, entry.sourceDate == null, entry.sourceDate.orEmpty(), isNew = false)
        draftError = null
    }
    fun beginNewAdjustment() {
        draft = AdjustmentDraft(LocalDate.now().toString(), rest = true, source = "", isNew = true)
        draftError = null
    }
    fun commitDraft() {
        val current = draft ?: return
        val label = entries.firstOrNull { it.date == current.date }?.label.orEmpty()
        runCatching { validEntry(current.date, if (current.rest) null else current.source, label) }
            .onSuccess { next ->
                entries = (entries.filter { it.date != next.date } + next).sortedBy { it.date }
                draft = null
                draftError = null
            }
            .onFailure { draftError = it.message }
    }
    fun dropDraft() {
        draft = null
        draftError = null
    }
    fun requestBack() {
        if (reviews != null) { reviews = null; error = null }
        else if (changed) showExitConfirm = true
        else onDismiss()
    }
    // Read the latest review list at call time: a picker or toggle lambda may outlive the
    // composition that created it, and must never write back a stale snapshot.
    fun updateReview(planIndex: Int, transform: (HolidayReview) -> HolidayReview) {
        val current = reviews ?: return
        val plan = current.getOrNull(planIndex) ?: return
        reviews = current.toMutableList().also { it[planIndex] = transform(plan) }
    }
    fun updateMakeup(planIndex: Int, makeupIndex: Int, transform: (MakeupReview) -> MakeupReview) =
        updateReview(planIndex) { plan ->
            plan.copy(makeups = plan.makeups.toMutableList().also { it[makeupIndex] = transform(it[makeupIndex]) })
        }
    fun applySelected() {
        val pending = reviews ?: return
        runCatching {
            val imported = buildList {
                pending.forEach { review ->
                    if (review.restSelected) review.plan.restDates.forEach {
                        add(validEntry(it.toString(), null, review.plan.name))
                    }
                    review.makeups.filter { it.selected }.forEach {
                        add(validEntry(it.date.toString(), it.source, review.plan.name))
                    }
                }
            }
            require(imported.isNotEmpty()) { "请至少选择一个日期" }
            entries = (entries.filter { old -> imported.none { it.date == old.date } } + imported).sortedBy { it.date }
            reviews = null
            error = null
        }.onFailure { error = it.message ?: "调休安排无效" }
    }
    // Only intercept back when there is something to save or a preview to drop, so an untouched
    // page keeps the platform predictive-back animation.
    BackHandler(enabled = reviews != null || changed, onBack = ::requestBack)
    DetailActivityScaffold(
        title = "调休课表",
        config = state.config,
        onBack = ::requestBack
    ) { backdrop ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = detailContentTopPadding() + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            GlassPreferenceSection("自动获取") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsTextFieldRow("年份", year, { year = it.filter(Char::isDigit).take(4) }, KeyboardType.Number)
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = if (loading) "正在获取…" else "获取节假日与调休",
                        subtitle = "自动匹配补课日期，仅生成预览",
                        onClick = {
                            if (!loading) scope.launch {
                                loading = true; error = null
                                runCatching { HolidayApi.fetch(year.toIntOrNull() ?: error("请输入年份")) }
                                    .onSuccess { fetched ->
                                        val plans = planHolidays(fetched).mapNotNull { plan ->
                                            val rests = plan.restDates.filter(::inTerm)
                                            val makeups = plan.makeups.filter { inTerm(it.date) }
                                            if (rests.isEmpty() && makeups.isEmpty()) null
                                            else plan.copy(restDates = rests, makeups = makeups)
                                        }
                                        if (plans.isEmpty()) error = "该年份没有学期内的节假日"
                                        else reviews = plans.map { plan ->
                                            HolidayReview(plan, restSelected = true, makeups = plan.makeups.map { makeup ->
                                                val suggested = makeup.suggestedSource
                                                val matched = suggested != null &&
                                                    adjustedTeachingWeekForDate(state.config, suggested) != null
                                                MakeupReview(makeup.date, if (matched) suggested.toString() else "", matched)
                                            })
                                        }
                                    }.onFailure {
                                        if (it is CancellationException) throw it
                                        error = when (it) {
                                            is IOException -> "连接节假日服务失败，请检查网络后重试"
                                            is SerializationException -> "节假日数据格式异常，请稍后重试"
                                            else -> it.message ?: "获取失败，请稍后重试"
                                        }
                                    }
                                loading = false
                            }
                        })
                }
            }
            val pending = reviews
            if (pending != null) {
                val picked = pending.sumOf { review ->
                    (if (review.restSelected) review.plan.restDates.size else 0) + review.makeups.count { it.selected }
                }
                GlassPreferenceSection("待确认 · $picked 天") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        pending.forEachIndexed { planIndex, review ->
                            SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                                val rests = review.plan.restDates
                                if (rests.isNotEmpty()) {
                                    SettingsToggleRow(
                                        title = review.plan.name,
                                        subtitle = "${shortDate(rests.first())} – ${shortDate(rests.last())} · 停课 ${rests.size} 天",
                                        checked = review.restSelected, backdrop = backdrop,
                                        onCheckedChange = { checked -> updateReview(planIndex) { it.copy(restSelected = checked) } }
                                    )
                                }
                                review.makeups.forEachIndexed { makeupIndex, makeup ->
                                    if (rests.isNotEmpty() || makeupIndex > 0) SettingsDivider()
                                    SettingsToggleRow(
                                        title = "补课 · ${shortDate(makeup.date)} ${weekdayText(makeup.date)}",
                                        subtitle = if (makeup.source.isBlank()) "请选择原课程日期"
                                            else "补 ${makeup.source} ${weekdayText(LocalDate.parse(makeup.source))} 的课",
                                        checked = makeup.selected, backdrop = backdrop,
                                        onCheckedChange = { checked -> updateMakeup(planIndex, makeupIndex) { it.copy(selected = checked) } }
                                    )
                                    if (makeup.selected) {
                                        SettingsDivider()
                                        SettingsDatePickerRow("原课程日期", makeup.source,
                                            { next -> updateMakeup(planIndex, makeupIndex) { it.copy(source = next) } },
                                            backdrop, state.config)
                                        Text(preview(makeup.source), Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    GlassPreferenceCategory("补课日期按调休规则自动匹配原课程日期，请核对后再采用。",
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    DialogLiquidButton(backdrop, "采用所选日期", ::applySelected,
                        role = DialogButtonRole.Confirm, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
            GlassPreferenceSection(
                if (entries.isEmpty()) "调休安排" else "调休安排 · ${entries.size} 天"
            ) {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        key(entry.date) {
                            SettingsSwipeDeleteRow(rowKey = entry.date, onRequestDelete = { deleting = entry }) {
                                AdjustmentRow(entry, preview = ::preview, onClick = { beginEdit(entry) })
                            }
                        }
                    }
                    if (entries.isNotEmpty()) SettingsDivider()
                    SettingsNavigationRow(
                        title = "新增调休日",
                        subtitle = "选择停课或补课的日期与原课程日期",
                        onClick = ::beginNewAdjustment
                    )
                }
                GlassPreferenceCategory("点按安排可在居中面板中修改，左滑删除。", modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
        }
        if (showExitConfirm) LiquidAlertDialog(
            title = "保存调休安排？", message = "完成后返回课表详细设置，与其他修改一起保存。",
            actions = listOf(
                LiquidAlertAction("完成", LiquidAlertActionStyle.Primary) { onConfirm(entries) },
                LiquidAlertAction("放弃修改", LiquidAlertActionStyle.Destructive) { onDismiss() },
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) { showExitConfirm = false }
            ),
            backdrop = backdrop, config = state.config, onDismissRequest = { showExitConfirm = false }
        )
        deleting?.let { entry ->
            val date = LocalDate.parse(entry.date)
            LiquidAlertDialog(
                title = "删除这条调休安排？",
                message = "${shortDate(date)} ${weekdayText(date)} 的安排会被移除，保存详细设置后生效。",
                actions = listOf(
                    LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                        entries = entries.filterNot { it.date == entry.date }
                        if (draft?.date == entry.date) dropDraft()
                        deleting = null
                    },
                    LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { deleting = null }
                ),
                backdrop = backdrop, config = state.config, onDismissRequest = { deleting = null }
            )
        }
        draft?.let { current ->
            AdjustmentEditorDialog(
                draft = current,
                error = draftError,
                onDraftChange = { draft = it; draftError = null },
                preview = ::preview,
                onSave = ::commitDraft,
                onCancel = ::dropDraft,
                backdrop = backdrop,
                config = state.config
            )
        }
    }
}

@Composable
private fun AdjustmentRow(
    entry: ScheduleAdjustment,
    preview: (String) -> String, onClick: () -> Unit
) {
    val date = LocalDate.parse(entry.date)
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text("${shortDate(date)} ${weekdayText(date)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (entry.sourceDate == null) "停课${entry.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
                else "补 ${entry.sourceDate}\n${preview(entry.sourceDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsForwardIndicator()
    }
}

/**
 * Centered editor for one adjustment. The date wheels live on their own page of the same dialog
 * rather than in a second stacked dialog, which keeps one backdrop and input host throughout.
 */
@Composable
private fun AdjustmentEditorDialog(
    draft: AdjustmentDraft,
    error: String?,
    onDraftChange: (AdjustmentDraft) -> Unit,
    preview: (String) -> String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop?, config: ScheduleConfigEntity
) {
    val targetDate = parseScheduleDate(draft.date) ?: LocalDate.now()
    var page by remember(draft.isNew) {
        mutableStateOf(if (draft.isNew) AdjustmentPickerPage.TARGET_DATE else AdjustmentPickerPage.DETAILS)
    }
    fun leaveCurrentPage() {
        page = AdjustmentPickerPage.DETAILS
    }
    val title = when (page) {
        AdjustmentPickerPage.DETAILS -> if (draft.isNew) "新增调休日" else "编辑调休安排"
        AdjustmentPickerPage.TARGET_DATE -> "调休日期"
        AdjustmentPickerPage.SOURCE_DATE -> "原课程日期"
    }
    SleepDownPickerDialog(
        show = true,
        title = title,
        onDismissRequest = {
            if (page == AdjustmentPickerPage.DETAILS) onCancel() else leaveCurrentPage()
        },
        backdrop = backdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        contentTransitionKey = page,
        contentForState = { displayed ->
            when (displayed as AdjustmentPickerPage) {
                AdjustmentPickerPage.DETAILS -> Column(
                    verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)
                ) {
                    Text(
                        if (draft.isNew) "新安排" else "${shortDate(targetDate)} ${weekdayText(targetDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
                    ) {
                        QuickSheetLiquidAction(
                            label = "停课", enabled = true, backdrop = backdrop, config = config,
                            primary = draft.rest, modifier = Modifier.weight(1f),
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                            onClick = { onDraftChange(draft.copy(rest = true)) }
                        )
                        QuickSheetLiquidAction(
                            label = "补课", enabled = true, backdrop = backdrop, config = config,
                            primary = !draft.rest, modifier = Modifier.weight(1f),
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                            onClick = { onDraftChange(draft.copy(rest = false)) }
                        )
                    }
                    Text(
                        if (draft.rest) "当天课程暂停：课表仍保留课程卡片并置灰，不可点开编辑。"
                        else "当天按指定的原课程日期上课，卡片正常显示但不可点开编辑。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    if (draft.isNew) {
                        SettingsPickerValueRow(
                            title = "调休日期",
                            value = formatScheduleDate(targetDate),
                            onClick = { page = AdjustmentPickerPage.TARGET_DATE }
                        )
                    }
                    if (!draft.rest) {
                        SettingsPickerValueRow(
                            title = "原课程日期",
                            value = draft.source.ifBlank { "未设置" },
                            onClick = { page = AdjustmentPickerPage.SOURCE_DATE }
                        )
                        Text(
                            preview(draft.source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp))
                    }
                    PeriodPickerActions(
                        backdrop, config,
                        onCancel = onCancel, onConfirm = onSave, confirmText = "保存"
                    )
                }
                AdjustmentPickerPage.TARGET_DATE -> AdjustmentDatePickerPage(
                    initial = targetDate,
                    backdrop = backdrop, config = config,
                    onCancel = { if (draft.isNew) onCancel() else leaveCurrentPage() },
                    onConfirm = { picked ->
                        onDraftChange(draft.copy(date = picked.toString()))
                        leaveCurrentPage()
                    }
                )
                AdjustmentPickerPage.SOURCE_DATE -> AdjustmentDatePickerPage(
                    initial = parseScheduleDate(draft.source) ?: LocalDate.now(),
                    backdrop = backdrop, config = config,
                    onCancel = ::leaveCurrentPage,
                    onConfirm = { picked ->
                        onDraftChange(draft.copy(source = picked.toString()))
                        leaveCurrentPage()
                    }
                )
            }
        },
        content = {}
    )
}

@Composable
private fun AdjustmentDatePickerPage(
    initial: LocalDate,
    backdrop: Backdrop?, config: ScheduleConfigEntity,
    onCancel: () -> Unit, onConfirm: (LocalDate) -> Unit
) {
    var year by remember(initial) { mutableIntStateOf(initial.year) }
    var month by remember(initial) { mutableIntStateOf(initial.monthValue) }
    var day by remember(initial) { mutableIntStateOf(initial.dayOfMonth) }
    Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
        SettingsDatePickerContent(
            year = year, month = month, day = day,
            onYearChange = { year = it }, onMonthChange = { month = it }, onDayChange = { day = it }
        )
        PeriodPickerActions(backdrop, config, onCancel = onCancel, onConfirm = {
            val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
            onConfirm(LocalDate.of(year, month, day.coerceAtMost(maxDay)))
        })
    }
}
