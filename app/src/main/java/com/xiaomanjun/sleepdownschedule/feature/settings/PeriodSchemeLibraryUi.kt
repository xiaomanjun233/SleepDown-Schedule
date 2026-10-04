package com.xiaomanjun.sleepdownschedule.feature.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.app.ui.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.data.repository.loadPeriodSchemeLibrary
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

internal data class PeriodSchemeManagementRequest(
    val id: String,
    val libraryId: String,
    val session: PeriodTimelineSession,
    val source: Rect,
    val creating: Boolean = false,
    val original: SavedPeriodScheme? = null
)

/** Shared timetables are edited here; each schedule chooses one in its own settings. */
@Composable
fun PeriodSchemeManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    onOpenScheduleSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val scope = rememberCoroutineScope()
    val visualState = state.copy(config = settingsVisualConfig(state.config))
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val topPadding = detailContentTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var library by remember { mutableStateOf(emptyList<SavedPeriodScheme>()) }
    var currentDraft by remember(state.config.id) { mutableStateOf<SchedulePeriodSchemesDraft?>(null) }
    var currentSnapshot by remember(state.config.id) { mutableStateOf<SavedPeriodScheme?>(null) }
    var loaded by remember(state.config.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<PeriodSchemeManagementRequest?>(null) }
    var deleting by remember { mutableStateOf<SavedPeriodScheme?>(null) }
    var sourceDetails by remember { mutableStateOf<SavedPeriodScheme?>(null) }
    var addBounds by remember { mutableStateOf(Rect.Zero) }

    fun isCurrent(saved: SavedPeriodScheme): Boolean = currentSnapshot?.id == saved.id

    suspend fun reloadLibrary() {
        currentDraft = repository.loadPeriodSchemes(state.config.id)
        library = repository.loadPeriodSchemeLibrary(context, state.allConfigs + state.config,
            state.schedules.associate { it.id to it.name })
        val active = currentDraft!!.schemes.first { it.scheme.id == currentDraft!!.activeSchemeId }
        currentSnapshot = library.first { it.id == active.scheme.publicId }
        error = null
    }

    LaunchedEffect(state.config, state.periods, retry) {
        try {
            reloadLibrary()
            loaded = true
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = failure.message ?: "作息读取失败"
        }
    }
    SideEffect { onExitInterceptionChange(editing != null || busy) }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0 && editing == null && !busy) onExitCommitFinished(true)
    }

    fun duplicate(saved: SavedPeriodScheme) {
        if (!loaded || busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val copy = repository.duplicatePublicPeriodScheme(saved.id)
                reloadLibrary()
                Toast.makeText(context, "已复制为${copy.name}", Toast.LENGTH_SHORT).show()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "作息复制失败"
            }
            finally { busy = false }
        }
    }

    val fallback = remember(state.config.id) {
        val scheme = PeriodSchemeEntity(id = -1, scheduleId = state.config.id, name = "默认作息", isActive = true,
            classDurationMinutes = state.config.classDurationMinutes, breakDurationMinutes = state.config.breakDurationMinutes)
        SchedulePeriodSchemesDraft(listOf(PeriodSchemeDraft(scheme,
            state.periods.map { PeriodSchemeTimeEntity(scheme.id, it.periodIndex, it.startTime, it.endTime) })), scheme.id)
    }
    val session = editing?.session ?: PeriodTimelineSession(state.config, currentDraft ?: fallback)
    PeriodSchemeEditor(
        state = visualState, backdrop = backdrop, config = session.config, draft = session.draft,
        onDraftChange = {}, onCountsChange = { _, _, _, _ -> }, topPadding = topPadding, leadingContent = {},
        managementRequest = editing, exitCommitRequest = exitCommitRequest,
        onEditorFinished = { editing = null },
        onSaveSession = { edited ->
            val request = checkNotNull(editing) { "编辑会话已结束" }
            val saved = withContext(Dispatchers.IO) {
                repository.savePublicPeriodScheme(request.original, edited, request.libraryId)
            }
            reloadLibrary()
            NotificationScheduler.requestReschedule(context)
            val message = if (request.creating) "已新建${saved.name}" else "作息已保存，引用它的课表已同步更新"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
        managementContent = {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = navigationBottom + 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        MiuixText("所有作息均为公共方案；编辑会同步到引用它的全部课表。请在课表设置中选择当前作息。", style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (!loaded) item { MiuixText("正在读取作息…", modifier = Modifier.padding(vertical = 20.dp),
                        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    error?.let { message -> item {
                        MiuixText(message, style = MiuixTheme.textStyles.body2, color = MaterialTheme.colorScheme.error)
                        if (!loaded) SettingsActionButton("重新读取", backdrop, onClick = { retry++ })
                    } }
                    items(library, key = { it.id }) { saved ->
                        PeriodSchemeCard(saved, isCurrent(saved), loaded && !busy, backdrop, visualState.config,
                            disambiguate = library.count { it.name == saved.name } > 1,
                            onEdit = { source -> editing = PeriodSchemeManagementRequest(UUID.randomUUID().toString(), saved.id,
                                savedPeriodSchemeSession(saved, state.config), source, original = saved) },
                            onDuplicate = { duplicate(saved) }, onDelete = { deleting = saved },
                            onShowSources = { sourceDetails = saved })
                    }
                    if (loaded && library.isEmpty()) item {
                        MiuixText("还没有作息，点击右下角加号新建。", modifier = Modifier.padding(vertical = 24.dp),
                            style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                val canCreate = loaded && !busy
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = "新建作息",
                    onClick = {
                        if (canCreate) {
                            currentSnapshot?.let { seed ->
                                editing = PeriodSchemeManagementRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                    savedPeriodSchemeSession(seed, state.config), addBounds, creating = true)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 32.dp, bottom = navigationBottom + 32.dp)
                        .minimumInteractiveComponentSize().alpha(if (canCreate) 1f else 0.38f)
                        .semantics { if (!canCreate) disabled() }
                        .onGloballyPositioned { addBounds = it.boundsInRoot() },
                    role = DialogButtonRole.Confirm,
                    iconRes = R.drawable.ic_add_course,
                    roundIcon = true,
                    shadowEnabled = false
                )
            }
        }
    )

    deleting?.let { saved ->
        LiquidAlertDialog("删除作息", "删除“${saved.name}”？正在被课表引用的作息不能删除，请先在相关课表中选择其他作息。",
            listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { deleting = null }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repository.deletePublicPeriodScheme(saved.id)
                            reloadLibrary()
                            deleting = null
                        } catch (failure: Exception) {
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            deleting = null
                            error = failure.message ?: "删除失败"
                        }
                        finally { busy = false }
                    }
                })), popupBackdrop, visualState.config, { if (!busy) deleting = null })
    }
    sourceDetails?.let { saved ->
        val description = buildList {
            saved.usages?.let { usages ->
                val names = usages.map { usage ->
                    state.schedules.firstOrNull { it.id == usage.config.id }?.name ?: "课表 ${usage.config.id}"
                }
                add(if (names.isEmpty()) "暂无课表引用" else "引用课表：${names.joinToString("、")}")
            }
            if (saved.createdInLibrary) add("在作息管理中手动新建")
            saved.sources.forEach { add("来自“${it.scheduleName}”：${it.schemeName}") }
            if (!saved.createdInLibrary && saved.sources.isEmpty()) add("未记录创建来源")
            if (saved.alternateNames.isNotEmpty()) add("合并前名称：${saved.alternateNames.joinToString("、")}")
            add("方案编号：${saved.id}")
        }.joinToString("\n")
        LiquidAlertDialog("作息来源与引用", description,
            listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary, onClick = { sourceDetails = null })),
            popupBackdrop, visualState.config, { sourceDetails = null })
    }
}

@Composable
private fun PeriodSchemeCard(
    saved: SavedPeriodScheme, selected: Boolean, enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity,
    disambiguate: Boolean, onEdit: (Rect) -> Unit, onDuplicate: () -> Unit,
    onDelete: () -> Unit, onShowSources: () -> Unit
) {
    var editBounds by remember { mutableStateOf(Rect.Zero) }
    val parts = listOf("上午" to saved.morningPeriodCount, "中午" to saved.noonPeriodCount,
        "下午" to saved.afternoonPeriodCount, "晚上" to saved.eveningPeriodCount).filter { it.second > 0 }
    val starts = listOf("上午" to saved.morningStartTime, "中午" to saved.noonStartTime,
        "下午" to saved.afternoonStartTime, "晚上" to saved.eveningStartTime).filter { start -> parts.any { it.first == start.first } }
    SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiuixText(saved.name, Modifier.weight(1f, fill = false), style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selected) MiuixText("当前", Modifier.clip(RoundedRectangle(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary)
                }
                MiuixText("${saved.times.size}节 · ${starts.joinToString(" / ") { it.second }}", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sources = buildList {
                    if (saved.createdInLibrary) add("手动新建")
                    addAll(saved.sources.map { it.scheduleName }.distinct())
                }
                val sourceLabel = if (sources.isEmpty()) "公共作息"
                    else "来源：" + sources.joinToString("、")
                val referenceLabel = saved.usages?.let { "${it.size} 个课表引用 · " }.orEmpty()
                MiuixText(referenceLabel + sourceLabel + if (disambiguate) " · #${saved.roomId}" else "",
                    Modifier.clickable(onClickLabel = "查看引用课表、作息来源和原名称", onClick = onShowSources),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogLiquidButton(
                    backdrop = backdrop, label = "复制",
                    onClick = { if (enabled) onDuplicate() },
                    modifier = Modifier.minimumInteractiveComponentSize().alpha(if (enabled) 1f else 0.38f)
                        .semantics { if (!enabled) disabled() },
                    role = DialogButtonRole.Cancel, shadowEnabled = false
                )
                Spacer(Modifier.width(8.dp))
                DialogLiquidButton(
                    backdrop = backdrop, label = "编辑${saved.name}",
                    onClick = { if (enabled) onEdit(editBounds) },
                    modifier = Modifier.minimumInteractiveComponentSize().alpha(if (enabled) 1f else 0.38f)
                        .semantics { if (!enabled) disabled() }
                        .onGloballyPositioned { editBounds = it.boundsInRoot() },
                    role = DialogButtonRole.Confirm, iconRes = R.drawable.ic_edit,
                    roundIcon = true, shadowEnabled = false
                )
                AnimatedVisibility(!selected,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        val canDelete = enabled && !selected
                        DialogLiquidButton(
                            backdrop = backdrop, label = "删除${saved.name}",
                            onClick = { if (canDelete) onDelete() },
                            modifier = Modifier.minimumInteractiveComponentSize().alpha(if (canDelete) 1f else 0.38f)
                                .semantics { if (!canDelete) disabled() },
                            role = DialogButtonRole.Cancel, destructiveFilled = true,
                            iconRes = R.drawable.ic_delete_history, roundIcon = true, shadowEnabled = false
                        )
                    }
                }
            }
        }
    }
}
