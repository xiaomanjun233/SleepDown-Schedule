package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.ui.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.app.ui.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.app.ui.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
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
    val isNew: Boolean,
    val originalDate: String? = null,
    val label: String = ""
)

private enum class AdjustmentPickerPage { DETAILS, TARGET_DATE, SOURCE_DATE }

private data class AdjustmentPickerContent(val page: AdjustmentPickerPage, val rest: Boolean, val error: String?)

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
    var alreadyAddedNotice by remember { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<HolidayReview>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showExitConfirm by remember { mutableStateOf(false) }
    // The centered picker edits one adjustment at a time; nothing leaves this screen before 保存.
    var draft by remember { mutableStateOf<AdjustmentDraft?>(null) }
    var draftVisible by remember { mutableStateOf(false) }
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
        return scheduleAdjustmentFromInput(target, origin, label)
    }
    val changed = remember(entries, initial) {
        encodeScheduleAdjustments(entries) != encodeScheduleAdjustments(initial)
    }
    fun beginEdit(entry: ScheduleAdjustment) {
        draftVisible = true
        draft = AdjustmentDraft(entry.date, entry.sourceDate == null, entry.sourceDate.orEmpty(),
            isNew = false, originalDate = entry.date, label = entry.label)
        draftError = null
    }
    fun beginNewAdjustment() {
        draftVisible = true
        draft = AdjustmentDraft(LocalDate.now().toString(), rest = true, source = "", isNew = true)
        draftError = null
    }
    fun commitDraft() {
        val current = draft ?: return
        runCatching {
            val next = validEntry(current.date, if (current.rest) null else current.source, current.label)
            replaceScheduleAdjustment(entries, current.originalDate, next)
        }
            .onSuccess { next ->
                entries = next
                draftVisible = false
                draftError = null
            }
            .onFailure { draftError = it.message }
    }
    fun dropDraft() {
        draftVisible = false
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
            val merged = (entries.filter { old -> imported.none { it.date == old.date } } + imported).sortedBy { it.date }
            validateScheduleAdjustments(merged)
            entries = merged
            reviews = null
            error = null
        }.onFailure { error = it.message ?: "调休安排无效" }
    }
    // Only intercept back when there is something to save or a preview to drop, so an untouched
    // page keeps the platform predictive-back animation.
    BackHandler(enabled = reviews != null || changed, onBack = ::requestBack)
    val pageColor = settingsPageBackground(state.config)
    val pageBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "schedule-adjustments-body") {
        drawRect(pageColor)
        drawContent()
    }
    DetailActivityScaffold(
        title = "调休课表",
        config = state.config,
        onBack = ::requestBack
    ) { backdrop ->
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().glassBackdropProducer(pageBackdrop).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = detailContentTopPadding() + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + DockScrollPadding),
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
                                loading = true; error = null; alreadyAddedNotice = null; reviews = null
                                runCatching { HolidayApi.fetch(year.toIntOrNull() ?: error("请输入年份")) }
                                    .onSuccess { fetched ->
                                        val plans = planHolidays(fetched).mapNotNull { plan ->
                                            val rests = plan.restDates.filter(::inTerm)
                                            val makeups = plan.makeups.filter { inTerm(it.date) }
                                            if (rests.isEmpty() && makeups.isEmpty()) null
                                            else plan.copy(restDates = rests, makeups = makeups)
                                        }
                                        val alreadyAdded = plans.filter { it.isAlreadyAdded(entries) }
                                        alreadyAddedNotice = alreadyAdded.takeIf { it.isNotEmpty() }
                                            ?.joinToString(separator = "、", postfix = "：已添加过") { it.name }
                                        if (plans.isEmpty()) error = "该年份没有学期内的节假日"
                                        else reviews = plans.filterNot { it in alreadyAdded }.map { plan ->
                                            HolidayReview(plan, restSelected = true, makeups = plan.makeups.map { makeup ->
                                                val savedSource = entries.firstOrNull { it.date == makeup.date.toString() }
                                                    ?.sourceDate?.let(LocalDate::parse)
                                                val suggested = savedSource ?: makeup.suggestedSource
                                                val matched = suggested != null &&
                                                    adjustedTeachingWeekForDate(state.config, suggested) != null
                                                MakeupReview(makeup.date, if (matched) suggested.toString() else "", matched)
                                            })
                                        }.takeIf { it.isNotEmpty() }
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
                alreadyAddedNotice?.let {
                    GlassPreferenceCategory(it, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
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
                                            else "补 ${makeup.source} ${parseScheduleDate(makeup.source)?.let(::weekdayText).orEmpty()} 的课",
                                        checked = makeup.selected, backdrop = backdrop,
                                        onCheckedChange = { checked -> updateMakeup(planIndex, makeupIndex) { it.copy(selected = checked) } }
                                    )
                                    if (makeup.selected) {
                                        SettingsDivider()
                                        SettingsDatePickerRow("原课程日期", makeup.source,
                                            { next -> updateMakeup(planIndex, makeupIndex) {
                                                it.copy(source = parseScheduleDate(next)?.toString().orEmpty())
                                            } },
                                            backdrop, state.config)
                                        Text(preview(makeup.source), Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    GlassPreferenceCategory("自动匹配仅供参考，请按学校安排核对。采用后会替换所选日期的已有安排，其他日期保留。",
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    DialogLiquidButton(backdrop, "采用所选日期", ::applySelected,
                        role = DialogButtonRole.Confirm, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
            GlassPreferenceSection(
                if (entries.isEmpty()) "调休安排" else "调休安排 · ${entries.size} 天"
            ) {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        title = "新增调休日",
                        subtitle = "选择停课或补课的日期与原课程日期",
                        onClick = ::beginNewAdjustment
                    )
                    entries.forEach { entry ->
                        SettingsDivider()
                        key(entry.date) {
                            SettingsSwipeDeleteRow(rowKey = entry.date, onRequestDelete = { deleting = entry }, softAppearance = true) {
                                AdjustmentRow(entry, preview = ::preview, onClick = { beginEdit(entry) })
                            }
                        }
                    }
                }
                GlassPreferenceCategory("点按修改，左滑删除。修改原课程，也会同步更新对应的补课。", modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
        }
        val density = LocalDensity.current
        val imeLift = with(density) {
            (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density)).coerceAtLeast(0).toDp()
        }
        val darkPage = appUsesDarkTheme(state.config)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(168.dp + imeLift)
            .background(Brush.verticalGradient(listOf(
                pageColor.copy(alpha = 0f),
                pageColor.copy(alpha = if (darkPage) 0.42f else 0.36f),
                pageColor.copy(alpha = if (darkPage) 0.76f else 0.70f),
                pageColor.copy(alpha = if (darkPage) 0.94f else 0.92f)
            )))
        )
        SettingsActionButton("保存调休安排", pageBackdrop, onClick = { onConfirm(entries) }, glowing = true,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp).fillMaxWidth())
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
                show = draftVisible,
                onDismissFinished = { draft = null; draftError = null },
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
                else "补 ${shortDate(LocalDate.parse(entry.sourceDate))} ${weekdayText(LocalDate.parse(entry.sourceDate))}的课 · ${preview(entry.sourceDate).substringBefore('\n')}",
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
    show: Boolean,
    onDismissFinished: () -> Unit,
    draft: AdjustmentDraft,
    error: String?,
    onDraftChange: (AdjustmentDraft) -> Unit,
    preview: (String) -> String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop?, config: ScheduleConfigEntity
) {
    val targetDate = parseScheduleDate(draft.date) ?: LocalDate.now()
    var page by remember { mutableStateOf(AdjustmentPickerPage.DETAILS) }
    var selectedDate by remember { mutableStateOf(targetDate) }
    fun openDatePage(destination: AdjustmentPickerPage) {
        selectedDate = if (destination == AdjustmentPickerPage.SOURCE_DATE)
            parseScheduleDate(draft.source) ?: targetDate else targetDate
        page = destination
    }
    fun backToDetails() { page = AdjustmentPickerPage.DETAILS }
    val title = when (page) {
        AdjustmentPickerPage.DETAILS -> if (draft.isNew) "新增调休日" else "编辑调休安排"
        AdjustmentPickerPage.TARGET_DATE -> "选择调休日期"
        AdjustmentPickerPage.SOURCE_DATE -> "选择原课程日期"
    }
    SleepDownPickerDialog(
        show = show,
        onDismissFinished = onDismissFinished,
        title = title,
        onDismissRequest = { if (page == AdjustmentPickerPage.DETAILS) onCancel() else backToDetails() },
        backdrop = backdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        contentTransitionKey = AdjustmentPickerContent(page, draft.rest, error),
        scrollableContent = true,
        smoothContentResize = true,
        bottomActions = {
            if (page == AdjustmentPickerPage.DETAILS) {
                PeriodPickerActions(backdrop, config, onCancel = onCancel, onConfirm = onSave,
                    confirmText = "保存")
            } else {
                PeriodPickerActions(backdrop, config, onCancel = ::backToDetails, cancelText = "返回",
                    confirmText = "选用日期", onConfirm = {
                        onDraftChange(if (page == AdjustmentPickerPage.TARGET_DATE)
                            draft.copy(date = selectedDate.toString()) else draft.copy(source = selectedDate.toString()))
                        backToDetails()
                    })
            }
        },
        contentForState = { displayed ->
            val view = displayed as AdjustmentPickerContent
            when (view.page) {
                AdjustmentPickerPage.DETAILS -> Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)) {
                        QuickSheetLiquidAction("停课", true, backdrop, config,
                            primary = view.rest, modifier = Modifier.weight(1f),
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                            onClick = { onDraftChange(draft.copy(rest = true)) })
                        QuickSheetLiquidAction("补课", true, backdrop, config,
                            primary = !view.rest, modifier = Modifier.weight(1f),
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                            onClick = { onDraftChange(draft.copy(rest = false)) })
                    }
                    Text(if (view.rest) "当天暂停上课，保留原课程且不发送提醒。"
                        else "选择要补哪一天的课，时间和课程内容随原课程同步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp))
                    Column {
                        SettingsNavigationRow(
                            title = "调休日期",
                            subtitle = "${formatScheduleDate(targetDate)}  ${weekdayText(targetDate)}",
                            onClick = { openDatePage(AdjustmentPickerPage.TARGET_DATE) })
                        if (!view.rest) {
                            SettingsDivider()
                            val sourceDate = parseScheduleDate(draft.source)
                            SettingsNavigationRow(
                                title = "原课程日期",
                                subtitle = sourceDate?.let { "${formatScheduleDate(it)}  ${weekdayText(it)}" } ?: "请选择要补哪一天的课",
                                onClick = { openDatePage(AdjustmentPickerPage.SOURCE_DATE) })
                        }
                    }
                    if (!view.rest && draft.source.isNotBlank()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("补课预览", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(preview(draft.source), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    view.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                AdjustmentPickerPage.TARGET_DATE, AdjustmentPickerPage.SOURCE_DATE ->
                    AdjustmentDatePickerPage(selectedDate, onChange = { selectedDate = it })
            }
        },
        content = {}
    )
}

@Composable
private fun AdjustmentDatePickerPage(selected: LocalDate, onChange: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${shortDate(selected)}  ${weekdayText(selected)}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SettingsDatePickerContent(
            year = selected.year, month = selected.monthValue, day = selected.dayOfMonth,
            onYearChange = { onChange(selected.withYear(it)) },
            onMonthChange = { onChange(selected.withMonth(it)) },
            onDayChange = { onChange(selected.withDayOfMonth(it.coerceIn(1, selected.lengthOfMonth()))) }
        )
    }
}
