package com.xiaomanjun.sleepdownschedule.feature.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.SettingsDetailActivity
import com.xiaomanjun.sleepdownschedule.app.ui.SettingsPage
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.app.ui.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.data.repository.PeriodSchemeLibraryStore
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class PeriodSchemeManagementRequest(
    val id: String,
    val libraryId: String,
    val session: PeriodTimelineSession,
    val source: Rect,
    val creating: Boolean = false,
    val original: SavedPeriodScheme? = null
)

private fun openPeriodSettings(context: Context, page: SettingsPage) {
    context.startActivity(Intent(context, SettingsDetailActivity::class.java).putExtra("settings_page", page.name))
}

/** Independent list of reusable timetables. Editing reuses the existing timeline scene. */
@Composable
fun PeriodSchemeManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {}
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
    var incompatible by remember { mutableStateOf<SavedPeriodScheme?>(null) }
    var addBounds by remember { mutableStateOf(Rect.Zero) }

    val scheduleName = state.schedules.firstOrNull { it.id == state.config.id }?.name ?: "当前课表"
    fun isCurrent(saved: SavedPeriodScheme): Boolean = currentSnapshot?.copy(id = saved.id) == saved

    LaunchedEffect(state.config, state.periods, retry) {
        try {
            currentDraft = repository.loadPeriodSchemes(state.config.id)
            val active = currentDraft!!.schemes.first { it.scheme.id == currentDraft!!.activeSchemeId }
            currentSnapshot = savePeriodSchemeSnapshot("current", active.scheme.name, state.config, active)
            // Bring existing schemes into the independent list once; preserve their original copies.
            for (config in state.allConfigs.ifEmpty { listOf(state.config) }) {
                if (PeriodSchemeLibraryStore.hasImportedSchedule(context, config.id)) continue
                val draft = if (config.id == state.config.id) currentDraft!! else repository.loadPeriodSchemes(config.id)
                val snapshots = draft.schemes.map { item ->
                    val id = UUID.nameUUIDFromBytes("sleepdown-period-${config.id}-${item.scheme.id}".toByteArray()).toString()
                    savePeriodSchemeSnapshot(id, item.scheme.name, config, item)
                }
                withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.importScheduleOnce(context, config.id, snapshots) }
            }
            library = withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.load(context) }
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

    suspend fun applyToCurrent(saved: SavedPeriodScheme) {
        val draft = repository.loadPeriodSchemes(state.config.id)
        val applied = applySavedPeriodScheme(saved, state.config, draft)
        repository.saveScheduleDetail(applied.config, applied.draft,
            expectedCourses = state.courses, expectedPeriods = state.periods)
        currentDraft = repository.loadPeriodSchemes(state.config.id)
        currentSnapshot = saved.copy(id = "current")
        NotificationScheduler.requestReschedule(context)
    }
    fun select(saved: SavedPeriodScheme) {
        if (!loaded || busy || isCurrent(saved)) return
        if (saved.times.size != state.config.totalPeriodCount()) { incompatible = saved; return }
        busy = true
        error = null
        scope.launch {
            try {
                applyToCurrent(saved)
                Toast.makeText(context, "已使用${saved.name}", Toast.LENGTH_SHORT).show()
            } catch (failure: Exception) { error = failure.message ?: "作息切换失败" }
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
            val wasCurrent = request.original?.let(::isCurrent) == true
            val saved = savePeriodSchemeSnapshot(request.libraryId, edited.active.scheme.name, edited.config, edited.active)
            withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.save(context, saved) }
            if (wasCurrent && saved.times.size == state.config.totalPeriodCount()) applyToCurrent(saved)
            library = withContext(Dispatchers.IO) { PeriodSchemeLibraryStore.load(context) }
            Toast.makeText(context, if (request.creating) "已新建${saved.name}" else "作息已保存", Toast.LENGTH_SHORT).show()
        },
        managementContent = {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = navigationBottom + 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前课表：$scheduleName", style = MaterialTheme.typography.titleSmall)
                            Text("点选作息即可使用，也可在其他课表选择同一套作息。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!loaded) item { Text("正在读取作息…", modifier = Modifier.padding(vertical = 20.dp)) }
                    error?.let { message -> item {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        if (!loaded) SettingsActionButton("重新读取", backdrop, onClick = { retry++ })
                    } }
                    items(library, key = { it.id }) { saved ->
                        PeriodSchemeCard(saved, isCurrent(saved), loaded && !busy, backdrop, visualState.config,
                            onSelect = { select(saved) },
                            onEdit = { source -> editing = PeriodSchemeManagementRequest(UUID.randomUUID().toString(), saved.id,
                                savedPeriodSchemeSession(saved, state.config), source, original = saved) },
                            onDelete = { deleting = saved })
                    }
                    if (loaded && library.isEmpty()) item {
                        Text("还没有作息，点击下方“新建作息”开始设置。", modifier = Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                QuickSheetLiquidAction("新建作息", loaded && !busy, backdrop, visualState.config,
                    Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = navigationBottom + 24.dp).widthIn(min = 148.dp, max = 220.dp)
                        .onGloballyPositioned { addBounds = it.boundsInRoot() }, primary = true, height = 48.dp) {
                    val seed = currentSnapshot ?: return@QuickSheetLiquidAction
                    editing = PeriodSchemeManagementRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                        savedPeriodSchemeSession(seed, state.config), addBounds, creating = true)
                }
            }
        }
    )

    deleting?.let { saved ->
        LiquidAlertDialog("删除作息", "删除“${saved.name}”？已使用这套作息的课表会保留原来的时间。",
            listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { deleting = null }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            library = withContext(Dispatchers.IO) {
                                PeriodSchemeLibraryStore.delete(context, saved.id)
                                PeriodSchemeLibraryStore.load(context)
                            }
                            deleting = null
                        } catch (failure: Exception) { error = failure.message ?: "删除失败" }
                        finally { busy = false }
                    }
                })), popupBackdrop, visualState.config, { if (!busy) deleting = null })
    }
    incompatible?.let { saved ->
        LiquidAlertDialog("节数不同", "“${saved.name}”有 ${saved.times.size} 节，“$scheduleName”有 ${state.config.totalPeriodCount()} 节。先调整课表的节数，再选择这套作息。",
            listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { incompatible = null }),
                LiquidAlertAction("调整课表节数", LiquidAlertActionStyle.Primary, onClick = {
                    incompatible = null
                    openPeriodSettings(context, SettingsPage.Schedule)
                })), popupBackdrop, visualState.config, { incompatible = null })
    }
}

@Composable
private fun PeriodSchemeCard(
    saved: SavedPeriodScheme, selected: Boolean, enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity,
    onSelect: () -> Unit, onEdit: (Rect) -> Unit, onDelete: () -> Unit
) {
    var editBounds by remember { mutableStateOf(Rect.Zero) }
    val parts = listOf("上午" to saved.morningPeriodCount, "中午" to saved.noonPeriodCount,
        "下午" to saved.afternoonPeriodCount, "晚上" to saved.eveningPeriodCount).filter { it.second > 0 }
    val starts = listOf("上午" to saved.morningStartTime, "中午" to saved.noonStartTime,
        "下午" to saved.afternoonStartTime, "晚上" to saved.eveningStartTime).filter { start -> parts.any { it.first == start.first } }
    SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClickLabel = "使用${saved.name}", onClick = onSelect)
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(saved.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selected) Text("当前使用", Modifier.clip(Capsule())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(parts.joinToString(" · ") { "${it.first} ${it.second} 节" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(starts.joinToString(" · ") { "${it.first} ${it.second}" }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuickSheetLiquidAction("编辑", enabled, backdrop, config,
                    Modifier.weight(1f).onGloballyPositioned { editBounds = it.boundsInRoot() }, primary = true, height = 48.dp) {
                    onEdit(editBounds)
                }
                if (!selected) {
                    Spacer(Modifier.width(8.dp))
                    QuickSheetLiquidAction("删除", enabled, backdrop, config, Modifier.weight(1f), destructive = true, height = 48.dp) { onDelete() }
                }
            }
        }
    }
}
