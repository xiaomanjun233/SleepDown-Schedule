package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.ScheduleProfileEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.RefreshCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.RefreshTerm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AutoRefreshReviewHost(config: ScheduleConfigEntity, backdrop: Backdrop?, schedules: List<ScheduleProfileEntity>,
    enabled: Boolean = true, openRequest: Int = 0) {
    val context = LocalContext.current
    val pending by AutoRefreshReviewStore.observe(context).collectAsState()
    val profile by AutoRefreshScheduleStore.observe(context).collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var deferredId by rememberSaveable { mutableStateOf<String?>(null) }
    var seenRequest by remember { mutableIntStateOf(openRequest) }
    LaunchedEffect(openRequest) {
        if (openRequest != seenRequest) { deferredId = null; seenRequest = openRequest }
    }
    val review = pending ?: return
    if (review.scheduleId != profile?.scheduleId || review.schoolId != profile?.schoolId ||
        review.adapterId != profile?.adapterId) return
    key(review.id) {
        val differences = remember(review) { review.differences }
        var selected by remember { mutableStateOf(emptySet<Int>()) }
        var useTerm by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val foreground = sleepDownPanelForegroundColor(config)
        SleepDownPickerDialog(
            show = enabled && lifecycle == Lifecycle.State.RESUMED && deferredId != review.id,
            title = "课表有 ${review.count} 处不同",
            onDismissRequest = { if (!saving) deferredId = review.id },
            backdrop = backdrop, config = config,
            bottomActions = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickSheetLiquidAction("稍后", !saving, backdrop, config, Modifier.weight(1f),
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight) { deferredId = review.id }
                        QuickSheetLiquidAction(if (saving) "保存中…" else "确认选择", !saving, backdrop, config,
                            Modifier.weight(1f), primary = true,
                            height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                            saving = true
                            error = null
                            scope.launch {
                                try {
                                    AutoRefreshScheduleCoordinator.confirmReview(context, review.id, selected, useTerm)
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                                finally { saving = false }
                            }
                        }
                    }
                }
            }
        ) {
            val scheduleName = schedules.firstOrNull { it.id == review.scheduleId }?.name ?: "已绑定课表"
            Text("$scheduleName · ${review.schoolName}\n更新尚未保存。稍后可在自动刷新设置中继续核对。",
                style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickSheetLiquidAction("全部保留", !saving, backdrop, config, Modifier.weight(1f)) {
                    selected = emptySet(); useTerm = false
                }
                QuickSheetLiquidAction("全部采用", !saving, backdrop, config, Modifier.weight(1f)) {
                    selected = differences.indices.toSet(); useTerm = review.termChanged
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                .heightIn(max = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.termChanged) {
                    ReviewItem("学期信息", review.beforeTerm.description(), review.afterTerm.description(),
                        useTerm, !saving, backdrop) { useTerm = it }
                }
                differences.forEachIndexed { index, change ->
                    val kind = when { change.before == null -> "新增"; change.after == null -> "移除"; else -> "变更" }
                    ReviewItem("$kind · ${(change.after ?: change.before)!!.name}",
                        change.before?.description() ?: "没有这门课",
                        change.after?.description() ?: "不再包含这门课",
                        index in selected, !saving, backdrop) {
                        selected = if (it) selected + index else selected - index
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(title: String, before: String, after: String, useIncoming: Boolean,
    enabled: Boolean, backdrop: Backdrop?, onSelect: (Boolean) -> Unit) {
    HorizontalDivider(color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.14f))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text("原内容：$before", style = MaterialTheme.typography.bodySmall)
    Text("教务更新：$after", style = MaterialTheme.typography.bodySmall)
    SettingsToggleRow("采用更新", if (useIncoming) "确认后采用教务更新" else "保留原内容",
        useIncoming, backdrop, enabled = enabled, compact = true, onCheckedChange = onSelect)
}

private fun RefreshTerm.description() = "${startDate ?: "未设开学日期"} · 共 $totalWeeks 周"
private fun RefreshCourse.description() = buildList {
    add("周${listOf("一", "二", "三", "四", "五", "六", "日").getOrNull(weekday - 1) ?: weekday}")
    add("第 ${periods.joinToString(",")} 节")
    add("第 ${weeks.joinToString(",")} 周" + when (parity) { "ODD" -> "（单周）"; "EVEN" -> "（双周）"; else -> "" })
    if (!start.isNullOrBlank() && !end.isNullOrBlank()) add("$start–$end")
    (periodTimes ?: originalTimes)?.takeIf { it.isNotBlank() }?.let { add("时间：$it") }
    location?.takeIf { it.isNotBlank() }?.let { add("地点：$it") }
    teacher?.takeIf { it.isNotBlank() }?.let { add("教师：$it") }
    note?.takeIf { it.isNotBlank() }?.let { add("备注：$it") }
}.joinToString(" · ")
