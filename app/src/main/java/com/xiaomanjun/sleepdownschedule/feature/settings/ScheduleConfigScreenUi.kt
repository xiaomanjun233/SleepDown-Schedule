package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.coursesOutsideShortenedTerm
import com.xiaomanjun.sleepdownschedule.feature.course.editor.compactWeekSelectionLabel
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorScopeDescription
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasNetPeriodTopologyChange
import com.xiaomanjun.sleepdownschedule.domain.schedule.previewPeriodCourseMapping
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodCourseMappingApproval
import com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.applySavedPeriodScheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import com.xiaomanjun.sleepdownschedule.data.repository.loadPeriodSchemeLibrary
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra

/** Selecting or reloading a public record projects its saved bells without regenerating rules. */
internal fun storedPeriodSchemePeriods(scheduleId: Int, draft: PeriodSchemeDraft): List<PeriodEntity> =
    draft.times.sortedBy { it.periodIndex }.map {
        PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId)
    }

/** A reference change and unrelated table settings are not edits to the selected public record. */
internal fun hasPendingPeriodSchemeEdits(config: ScheduleConfigEntity, draft: SchedulePeriodSchemesDraft): Boolean {
    val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: return true
    val original = draft.originalSchemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: return true
    val projected = schemeConfig(config, original.scheme)
    return active != original ||
        config.morningPeriodCount != projected.morningPeriodCount ||
        config.noonPeriodCount != projected.noonPeriodCount ||
        config.afternoonPeriodCount != projected.afternoonPeriodCount ||
        config.eveningPeriodCount != projected.eveningPeriodCount ||
        config.classDurationMinutes != projected.classDurationMinutes ||
        config.breakDurationMinutes != projected.breakDurationMinutes ||
        hasNetPeriodTopologyChange(original.times.size, draft.topologyOperations)
}


@Composable
fun ScheduleConfigScreen(
    state: AppState,
    backdrop: Backdrop?,
    section: SettingsSection,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    onOpenPeriodSchemes: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val saveScope = rememberCoroutineScope()
    val visualState = state.copy(config = settingsVisualConfig(state.config))
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var totalWeeks by remember { mutableStateOf(state.config.totalWeeks.toString()) }
    var currentWeek by remember { mutableStateOf(state.config.currentWeek.toString()) }
    var leadMinutes by remember { mutableStateOf(state.config.notificationLeadMinutes.toString()) }
    var notificationsEnabled by remember { mutableStateOf(state.config.notificationsEnabled) }
    var notificationMode by remember { mutableStateOf(state.config.notificationMode) }
    var liveUpdateChipTextMode by remember { mutableStateOf(state.config.liveUpdateChipTextMode) }
    var liveUpdateActionsEnabled by remember { mutableStateOf(state.config.liveUpdateActionsEnabled) }
    var autoCurrentWeek by remember { mutableStateOf(state.config.autoCurrentWeek) }
    var hideEmptyWeekends by remember { mutableStateOf(state.config.hideEmptyWeekends) }
    var scheduleAdjustmentsJson by remember { mutableStateOf(state.config.scheduleAdjustmentsJson) }
    var termStartDate by remember { mutableStateOf(state.config.termStartDate.orEmpty()) }
    var classDurationMinutes by remember { mutableStateOf(state.config.classDurationMinutes.toString()) }
    var breakDurationMinutes by remember { mutableStateOf(state.config.breakDurationMinutes.toString()) }
    var morningPeriodCount by remember { mutableIntStateOf(state.config.morningPeriodCount) }
    var noonPeriodCount by remember { mutableIntStateOf(state.config.noonPeriodCount) }
    var afternoonPeriodCount by remember { mutableIntStateOf(state.config.afternoonPeriodCount) }
    var eveningPeriodCount by remember { mutableIntStateOf(state.config.eveningPeriodCount) }
    var periods by remember { mutableStateOf(state.periods) }
    var alignmentMode by remember { mutableStateOf(state.config.periodAlignmentMode) }
    var pendingSchemeSwitch by remember(state.config.id) { mutableStateOf<SavedPeriodScheme?>(null) }
    var schemeDraft by remember(state.config.id) { mutableStateOf<SchedulePeriodSchemesDraft?>(null) }
    var periodSchemeLibrary by remember(state.config.id) { mutableStateOf(emptyList<SavedPeriodScheme>()) }
    var lastSavedSchemeDraft by remember(state.config.id) { mutableStateOf<SchedulePeriodSchemesDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showExitSaveConfirm by remember { mutableStateOf(false) }
    var showCourseRemapConfirm by remember { mutableStateOf(false) }
    var courseRemapDescription by remember { mutableStateOf("") }
    var pendingCourseMappingApproval by remember { mutableStateOf<PeriodCourseMappingApproval?>(null) }
    var pendingRemapSaveCompletion by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var pendingRemapTermShorteningConfirmed by remember { mutableStateOf(false) }
    var showTermShorteningConfirm by remember { mutableStateOf(false) }
    var pendingTermSaveCompletion by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var pendingTermRemapConfirmed by remember { mutableStateOf(false) }
    var lastSavedConfig by remember { mutableStateOf(state.config) }
    var lastSavedPeriods by remember { mutableStateOf(state.periods) }
    var currentDraftScheduleId by remember { mutableIntStateOf(state.config.id) }
    var draftReady by remember(state.config.id, section) {
        mutableStateOf(section != SettingsSection.Schedule)
    }
    val latestState by rememberUpdatedState(state)
    var schemeReloadRequest by remember(state.config.id) { mutableIntStateOf(0) }
    val periodManagementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        schemeReloadRequest++
    }

    fun resetConfigDraftFromState(source: AppState = state) {
        currentDraftScheduleId = source.config.id
        totalWeeks = source.config.totalWeeks.toString()
        currentWeek = source.config.currentWeek.toString()
        leadMinutes = source.config.notificationLeadMinutes.toString()
        notificationsEnabled = source.config.notificationsEnabled
        notificationMode = source.config.notificationMode
        liveUpdateChipTextMode = source.config.liveUpdateChipTextMode
        liveUpdateActionsEnabled = source.config.liveUpdateActionsEnabled
        autoCurrentWeek = source.config.autoCurrentWeek
        hideEmptyWeekends = source.config.hideEmptyWeekends
        scheduleAdjustmentsJson = source.config.scheduleAdjustmentsJson
        termStartDate = source.config.termStartDate.orEmpty()
        classDurationMinutes = source.config.classDurationMinutes.toString()
        breakDurationMinutes = source.config.breakDurationMinutes.toString()
        morningPeriodCount = source.config.morningPeriodCount
        noonPeriodCount = source.config.noonPeriodCount
        afternoonPeriodCount = source.config.afternoonPeriodCount
        eveningPeriodCount = source.config.eveningPeriodCount
        periods = source.periods
        alignmentMode = source.config.periodAlignmentMode
        pendingSchemeSwitch = null
        error = null
        lastSavedConfig = source.config
        lastSavedPeriods = source.periods
    }

    fun computeDirty(): Boolean {
        return when (section) {
            SettingsSection.Schedule -> draftReady && (
                totalWeeks != lastSavedConfig.totalWeeks.toString() ||
                    alignmentMode != lastSavedConfig.periodAlignmentMode ||
                    currentWeek != lastSavedConfig.currentWeek.toString() ||
                    autoCurrentWeek != lastSavedConfig.autoCurrentWeek ||
                    hideEmptyWeekends != lastSavedConfig.hideEmptyWeekends ||
                    scheduleAdjustmentsJson != lastSavedConfig.scheduleAdjustmentsJson ||
                    termStartDate != lastSavedConfig.termStartDate.orEmpty() ||
                    classDurationMinutes != lastSavedConfig.classDurationMinutes.toString() ||
                    breakDurationMinutes != lastSavedConfig.breakDurationMinutes.toString() ||
                    morningPeriodCount != lastSavedConfig.morningPeriodCount ||
                    noonPeriodCount != lastSavedConfig.noonPeriodCount ||
                    afternoonPeriodCount != lastSavedConfig.afternoonPeriodCount ||
                    eveningPeriodCount != lastSavedConfig.eveningPeriodCount ||
                    schemeDraft?.copy(topologyOperations = emptyList()) != lastSavedSchemeDraft?.copy(topologyOperations = emptyList()) ||
                    (schemeDraft?.let { hasNetPeriodTopologyChange(lastSavedPeriods.size, it.topologyOperations) } == true) ||
                    periods != lastSavedPeriods
                )
            SettingsSection.Notifications ->
                leadMinutes != lastSavedConfig.notificationLeadMinutes.toString() ||
                    notificationsEnabled != lastSavedConfig.notificationsEnabled ||
                    notificationMode != lastSavedConfig.notificationMode ||
                    liveUpdateChipTextMode != lastSavedConfig.liveUpdateChipTextMode ||
                    liveUpdateActionsEnabled != lastSavedConfig.liveUpdateActionsEnabled
        }
    }

    LaunchedEffect(state.config.id, state.config, state.periods) {
        if (state.config.id != currentDraftScheduleId || !computeDirty()) {
            resetConfigDraftFromState()
        }
    }
    LaunchedEffect(state.config.id, section, schemeReloadRequest) {
        if (section != SettingsSection.Schedule) {
            schemeDraft = null
            lastSavedSchemeDraft = null
            draftReady = true
            return@LaunchedEffect
        }
        draftReady = false
        if (schemeReloadRequest > 0) {
            schemeDraft = null
            lastSavedSchemeDraft = null
        }
        runCatching {
            val loaded = repository.loadPeriodSchemes(state.config.id)
            val library = repository.loadPeriodSchemeLibrary(context, state.allConfigs + state.config,
                state.schedules.associate { it.id to it.name })
            loaded to library
        }.onSuccess { (loaded, library) ->
                periodSchemeLibrary = library
                if (schemeReloadRequest > 0) resetConfigDraftFromState(latestState)
                val active = loaded.schemes.firstOrNull { scheme ->
                    scheme.scheme.id == loaded.activeSchemeId
                }
                val loadedActivePeriods = active?.let { storedPeriodSchemePeriods(state.config.id, it) }
                    ?: state.periods
                schemeDraft = loaded
                lastSavedSchemeDraft = loaded
                periods = loadedActivePeriods
                lastSavedPeriods = loadedActivePeriods
                lastSavedConfig = latestState.config
                draftReady = true
            }
            .onFailure {
                error = it.message ?: "作息方案加载失败"
                draftReady = true
            }
    }
    val detectedWeek = remember(autoCurrentWeek, termStartDate, totalWeeks, currentWeek) {
        val total = totalWeeks.toIntOrNull() ?: state.config.totalWeeks
        val manual = currentWeek.toIntOrNull() ?: state.config.currentWeek
        effectiveCurrentWeek(state.config.copy(totalWeeks = total.coerceAtLeast(1), currentWeek = manual.coerceAtLeast(1), termStartDate = termStartDate.ifBlank { null }, autoCurrentWeek = true))
    }
    val detectedWeekDescription = remember(autoCurrentWeek, termStartDate, totalWeeks, currentWeek, detectedWeek) {
        if (!autoCurrentWeek) {
            "学期状态：手动设置 · 第 ${currentWeek.toIntOrNull() ?: state.config.currentWeek} 周"
        } else {
            val total = totalWeeks.toIntOrNull() ?: state.config.totalWeeks
            val manual = currentWeek.toIntOrNull() ?: state.config.currentWeek
            val draftConfig = state.config.copy(
                totalWeeks = total.coerceAtLeast(1),
                currentWeek = manual.coerceAtLeast(1),
                termStartDate = termStartDate.ifBlank { null },
                autoCurrentWeek = true
            )
            "学期状态：${scheduleTermStatusDescription(draftConfig, LocalDate.now())}"
        }
    }
    val displayedCurrentWeek = if (autoCurrentWeek) detectedWeek.toString() else currentWeek
    val dirty = computeDirty()

    LaunchedEffect(section, dirty, saving) {
        onExitInterceptionChange(shouldInterceptSettingsBack(section, dirty, saving))
    }
    DisposableEffect(onExitInterceptionChange) {
        onDispose { onExitInterceptionChange(false) }
    }

    fun saveConfigDraft(
        onFinished: ((Boolean) -> Unit)? = null,
        remapConfirmed: Boolean = false,
        termShorteningConfirmed: Boolean = false
    ) {
        if (saving) {
            if (onFinished != null) {
                saveScope.launch {
                    snapshotFlow { saving }.first { !it }
                    if (computeDirty()) saveConfigDraft(onFinished, remapConfirmed, termShorteningConfirmed)
                    else onFinished(true)
                }
            }
            return
        }
        val total = totalWeeks.toIntOrNull()
        val current = currentWeek.toIntOrNull()
        val lead = leadMinutes.toIntOrNull()
        val classDuration = classDurationMinutes.toIntOrNull()
        val breakDuration = breakDurationMinutes.toIntOrNull()
        try {
            require(total != null && total in 1..60) { "总周数必须在 1 到 60 之间" }
            require(current != null && current in 1..total) { "当前周必须在 1 到总周数之间" }
            require(lead != null && lead in 0..180) { "提醒分钟必须在 0 到 180 之间" }
            require(classDuration != null && classDuration in 1..300) { "单节课分钟数必须在 1 到 300 之间" }
            require(breakDuration != null && breakDuration in 0..300) { "课间分钟数必须在 0 到 300 之间" }
            if (autoCurrentWeek) {
                require(termStartDate.isNotBlank()) { "开启自动计算当前周时必须填写学期开始日期" }
                val date = parseScheduleDate(termStartDate)
                require(date != null) { "学期开始日期必须是 yyyy-MM-dd 格式" }
            } else if (termStartDate.isNotBlank()) {
                require(parseScheduleDate(termStartDate) != null) { "学期开始日期必须是 yyyy-MM-dd 格式" }
            }
            val nextPeriods = periods
                .filter { it.periodIndex > 0 }
                .distinctBy { it.periodIndex }
                .sortedBy { it.periodIndex }
            require(nextPeriods.isNotEmpty()) { "至少需要保留 1 个节次" }
            nextPeriods.forEach {
                val start = ScheduleImportParser.parseTimeForUi(it.startTime)
                val end = ScheduleImportParser.parseTimeForUi(it.endTime)
                require(start < end) { "第" + it.periodIndex + "节结束时间必须晚于开始时间" }
            }
            validateResolvedPeriodTimes(
                nextPeriods.map { PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime) }
            )?.let { throw IllegalArgumentException(it) }
            if (section == SettingsSection.Schedule && !termShorteningConfirmed &&
                coursesOutsideShortenedTerm(state.courses, state.config.id, lastSavedConfig.totalWeeks, total).isNotEmpty()
            ) {
                pendingTermSaveCompletion = onFinished
                pendingTermRemapConfirmed = remapConfirmed
                showTermShorteningConfirm = true
                return
            }
            // Keep the stored manual week as a fallback. The visible automatic week
            // is derived from the date at render time and must not turn an upcoming
            // term into a persisted "week 1" merely because settings were saved.
            val storedCurrentWeek = current
            error = null
            val nextConfig = state.config.copy(
                totalWeeks = total,
                currentWeek = storedCurrentWeek,
                notificationLeadMinutes = lead,
                termStartDate = termStartDate.ifBlank { null },
                autoCurrentWeek = autoCurrentWeek,
                hideEmptyWeekends = hideEmptyWeekends,
                scheduleAdjustmentsJson = scheduleAdjustmentsJson,
                notificationsEnabled = notificationsEnabled,
                notificationMode = notificationMode,
                liveUpdateChipTextMode = liveUpdateChipTextMode,
                liveUpdateActionsEnabled = liveUpdateActionsEnabled,
                classDurationMinutes = classDuration,
                breakDurationMinutes = breakDuration
                ,morningPeriodCount = morningPeriodCount
                ,noonPeriodCount = noonPeriodCount
                ,afternoonPeriodCount = afternoonPeriodCount
                ,eveningPeriodCount = eveningPeriodCount
                ,periodAlignmentMode = alignmentMode
            )
            currentWeek = storedCurrentWeek.toString()
            periods = nextPeriods
            if (section == SettingsSection.Notifications) {
                // Non-structural settings use the process-scoped, conflated writer supplied by
                // the ViewModel. Returning from this Activity must never be part of the save path.
                onSave(nextConfig, nextPeriods)
                lastSavedConfig = nextConfig
                lastSavedPeriods = nextPeriods
                onFinished?.invoke(true)
                return
            }
            val currentSchemes = schemeDraft
            if (currentSchemes != null) {
                val active = currentSchemes.schemes.firstOrNull { it.scheme.id == currentSchemes.activeSchemeId }
                    ?: currentSchemes.schemes.first()
                val previousActive = currentSchemes.originalSchemes
                    .firstOrNull { it.scheme.id == active.scheme.id }
                    ?: lastSavedSchemeDraft?.schemes?.firstOrNull { it.scheme.id == active.scheme.id }
                val previousActiveConfig = previousActive?.let { schemeConfig(lastSavedConfig, it.scheme) }
                    ?: lastSavedConfig
                val activePeriods = resolveSchemeTimesForSave(
                    config = nextConfig,
                    draft = active,
                    storedConfig = previousActiveConfig,
                    storedDraft = previousActive
                ).map {
                    PeriodEntity(it.periodIndex, it.startTime, it.endTime, nextConfig.id)
                }
                currentSchemes.schemes.forEach { item ->
                    val previous = currentSchemes.originalSchemes.firstOrNull { it.scheme.id == item.scheme.id }
                        ?: lastSavedSchemeDraft?.schemes?.firstOrNull { it.scheme.id == item.scheme.id }
                    validateResolvedPeriodTimes(
                        resolveSchemeTimesForSave(nextConfig, item,
                            previous?.let { schemeConfig(lastSavedConfig, it.scheme) } ?: lastSavedConfig, previous)
                    )?.let {
                        throw IllegalArgumentException("${item.scheme.name}：$it")
                    }
                }
                val mappingApproval = PeriodCourseMappingApproval(
                    state.courses.map { it.originalArrangement() }, lastSavedPeriods,
                    activePeriods.map { PeriodSchemeTimeEntity(active.scheme.id, it.periodIndex, it.startTime, it.endTime) },
                    currentSchemes.topologyOperations
                )
                val switchingScheme = lastSavedSchemeDraft?.activeSchemeId != currentSchemes.activeSchemeId
                // Choosing another public scheme changes the reference only. Its different bells
                // must not be interpreted as edits to the previous scheme's lesson identities.
                val mapping = if (switchingScheme) null else previewPeriodCourseMapping(
                    mappingApproval.courses, mappingApproval.originalPeriods,
                    mappingApproval.targetTimes, mappingApproval.operations)
                if (mapping != null && mapping.changedCount > 0 &&
                    (!remapConfirmed || pendingCourseMappingApproval != mappingApproval)) {
                    courseRemapDescription = "${mapping.changedCount} 门课程的节次将按原上课时间调整：\n" +
                        state.courses.indices.filter { state.courses[it].periods != mapping.courses[it].periods }
                            .joinToString("\n") { index ->
                                val before = state.courses[index]
                                "${before.name}：${before.periods.joinToString(",")} → ${mapping.courses[index].periods.joinToString(",")}"
                            }
                    pendingRemapSaveCompletion = onFinished
                    pendingRemapTermShorteningConfirmed = termShorteningConfirmed
                    pendingCourseMappingApproval = mappingApproval
                    showCourseRemapConfirm = true
                    return
                }
                saving = true
                saveScope.launch {
                    runCatching {
                        repository.saveScheduleDetail(nextConfig, currentSchemes,
                            expectedCourses = mappingApproval.courses, expectedPeriods = mappingApproval.originalPeriods)
                        repository.loadPeriodSchemes(nextConfig.id) to repository.loadPeriodSchemeLibrary(
                            context, state.allConfigs + nextConfig, state.schedules.associate { it.id to it.name })
                    }.onSuccess { (savedDraft, library) ->
                        val savedActive = savedDraft.schemes.first { it.scheme.id == savedDraft.activeSchemeId }
                        val savedConfig = schemeConfig(nextConfig, savedActive.scheme)
                        val savedPeriods = storedPeriodSchemePeriods(savedConfig.id, savedActive)
                        periods = savedPeriods
                        onSave(savedConfig, savedPeriods)
                        lastSavedConfig = savedConfig
                        lastSavedPeriods = savedPeriods
                        lastSavedSchemeDraft = savedDraft
                        schemeDraft = savedDraft
                        periodSchemeLibrary = library
                        onFinished?.invoke(true)
                    }.onFailure {
                        error = it.message ?: "设置保存失败"
                        onFinished?.invoke(false)
                    }
                    saving = false
                }
            } else {
                saving = true
                saveScope.launch {
                    runCatching {
                        // Persist locally before notifying the Activity. The callback may
                        // finish the detail page and cancel its ViewModel scope immediately.
                        repository.saveConfigForSchedule(nextConfig.id, nextConfig, nextPeriods)
                    }.onSuccess {
                        onSave(nextConfig, nextPeriods)
                        lastSavedConfig = nextConfig
                        lastSavedPeriods = nextPeriods
                        onFinished?.invoke(true)
                    }.onFailure {
                        error = it.message ?: "设置保存失败"
                        onFinished?.invoke(false)
                    }
                    saving = false
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: "设置保存失败"
            onFinished?.invoke(false)
        }
    }

    fun openPeriodSchemeManagement() {
        if (!draftReady || saving) return
        fun navigate() {
            if (onOpenPeriodSchemes != null) {
                onOpenPeriodSchemes()
            } else {
                // Suspend the parent draft until the child returns and its saved times are reloaded.
                draftReady = false
                periodManagementLauncher.launch(
                    Intent(context, SettingsDetailActivity::class.java)
                        .putExtra("settings_page", SettingsPage.PeriodSchemes.name)
                        .putExtra(ScheduleCustomizeIdExtra, state.config.id)
                )
            }
        }
        if (computeDirty()) {
            error = "请先保存或放弃当前课表设置修改，再打开作息管理"
        } else {
            navigate()
        }
    }

    fun selectPeriodScheme(saved: SavedPeriodScheme) {
        val draft = schemeDraft ?: return
        if (!draftReady || saving) return
        val pendingConfig = state.config.copy(
            morningPeriodCount = morningPeriodCount, noonPeriodCount = noonPeriodCount,
            afternoonPeriodCount = afternoonPeriodCount, eveningPeriodCount = eveningPeriodCount,
            classDurationMinutes = classDurationMinutes.toIntOrNull() ?: state.config.classDurationMinutes,
            breakDurationMinutes = breakDurationMinutes.toIntOrNull() ?: state.config.breakDurationMinutes)
        if (hasPendingPeriodSchemeEdits(pendingConfig, draft)) {
            error = "请先保存或放弃当前作息修改，再切换"
            return
        }
        pendingSchemeSwitch = saved
    }

    fun applyPeriodSchemeSelection(selected: SavedPeriodScheme, mode: PeriodAlignmentMode) {
            val currentDraft = schemeDraft ?: return
            try {
                val applied = applySavedPeriodScheme(selected, state.config.copy(
                    morningPeriodCount = morningPeriodCount, noonPeriodCount = noonPeriodCount,
                    afternoonPeriodCount = afternoonPeriodCount, eveningPeriodCount = eveningPeriodCount), currentDraft)
                morningPeriodCount = applied.config.morningPeriodCount
                noonPeriodCount = applied.config.noonPeriodCount
                afternoonPeriodCount = applied.config.afternoonPeriodCount
                eveningPeriodCount = applied.config.eveningPeriodCount
                classDurationMinutes = selected.classDurationMinutes.toString()
                breakDurationMinutes = selected.breakDurationMinutes.toString()
                alignmentMode = mode
                schemeDraft = applied.draft
                val active = applied.draft.schemes.first { it.scheme.id == applied.draft.activeSchemeId }
                periods = storedPeriodSchemePeriods(state.config.id, active)
                error = null
            } catch (invalid: IllegalArgumentException) {
                error = invalid.message ?: "作息切换失败"
            }
    }

    pendingSchemeSwitch?.let { selected ->
        val target = checkNotNull(selected.storedDraft) { "公共作息记录缺失" }
        PeriodSchemeSwitchDialog(selected.name, state.courses, lastSavedConfig, lastSavedPeriods,
            schemeConfig(state.config, target.scheme), storedPeriodSchemePeriods(state.config.id, target),
            popupBackdrop, visualState.config, onDismiss = { pendingSchemeSwitch = null }, onConfirm = { mode ->
                pendingSchemeSwitch = null
                applyPeriodSchemeSelection(selected, mode)
            })
    }

    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest <= 0) return@LaunchedEffect
        when (section) {
            SettingsSection.Schedule -> {
                when {
                    saving -> Unit
                    computeDirty() -> showExitSaveConfirm = true
                    else -> onExitCommitFinished(true)
                }
            }
            SettingsSection.Notifications -> {
                // Controls enqueue their write independently of navigation; this only makes the
                // toolbar back button deterministic if it is tapped in the same frame.
                if (computeDirty() || saving) saveConfigDraft(onExitCommitFinished)
                else onExitCommitFinished(true)
            }
        }
    }

    LaunchedEffect(
        section,
        leadMinutes,
        notificationsEnabled,
        notificationMode,
        liveUpdateChipTextMode,
        liveUpdateActionsEnabled
    ) {
        if (section == SettingsSection.Notifications && computeDirty()) {
            saveConfigDraft()
        }
    }

    ScheduleSettingsContent(
        section = section,
        state = visualState,
        backdrop = backdrop,
        totalWeeks = totalWeeks,
        onTotalWeeksChange = { totalWeeks = it },
        currentWeek = displayedCurrentWeek,
        onCurrentWeekChange = { currentWeek = it },
        leadMinutes = leadMinutes,
        onLeadMinutesChange = { leadMinutes = it },
        notificationsEnabled = notificationsEnabled,
        onNotificationsEnabledChange = { notificationsEnabled = it },
        notificationMode = notificationMode,
        onNotificationModeChange = { notificationMode = it },
        liveUpdateChipTextMode = liveUpdateChipTextMode,
        onLiveUpdateChipTextModeChange = { liveUpdateChipTextMode = it },
        liveUpdateActionsEnabled = liveUpdateActionsEnabled,
        onLiveUpdateActionsEnabledChange = { liveUpdateActionsEnabled = it },
        autoCurrentWeek = autoCurrentWeek,
        onAutoCurrentWeekChange = { autoCurrentWeek = it },
        hideEmptyWeekends = hideEmptyWeekends,
        onHideEmptyWeekendsChange = { hideEmptyWeekends = it },
        termStartDate = termStartDate,
        onTermStartDateChange = { termStartDate = it },
        classDurationMinutes = classDurationMinutes,
        onClassDurationMinutesChange = { classDurationMinutes = it },
        breakDurationMinutes = breakDurationMinutes,
        onBreakDurationMinutesChange = { breakDurationMinutes = it },
        morningPeriodCount = morningPeriodCount,
        noonPeriodCount = noonPeriodCount,
        afternoonPeriodCount = afternoonPeriodCount,
        eveningPeriodCount = eveningPeriodCount,
        onPeriodCountsChange = { morning, noon, afternoon, evening ->
            morningPeriodCount = morning
            noonPeriodCount = noon
            afternoonPeriodCount = afternoon
            eveningPeriodCount = evening
        },
        schemeDraft = schemeDraft,
        onSchemeDraftChange = { updated ->
            schemeDraft = updated
            val active = updated.schemes.firstOrNull { it.scheme.id == updated.activeSchemeId }
            if (active != null) {
                val draftConfig = state.config.copy(
                    morningPeriodCount = morningPeriodCount,
                    noonPeriodCount = noonPeriodCount,
                    afternoonPeriodCount = afternoonPeriodCount,
                    eveningPeriodCount = eveningPeriodCount
                )
                val previous = updated.originalSchemes.firstOrNull { it.scheme.id == active.scheme.id }
                    ?: lastSavedSchemeDraft?.schemes?.firstOrNull { it.scheme.id == active.scheme.id }
                periods = resolveSchemeTimesForSave(draftConfig, active,
                    previous?.let { schemeConfig(lastSavedConfig, it.scheme) } ?: lastSavedConfig, previous).map {
                    PeriodEntity(it.periodIndex, it.startTime, it.endTime, state.config.id)
                }
            }
        },
        onAutoMatchPeriodEnds = {
            val classDuration = classDurationMinutes.toIntOrNull() ?: state.config.classDurationMinutes
            val breakDuration = breakDurationMinutes.toIntOrNull() ?: state.config.breakDurationMinutes
            periods = autoMatchPeriodTimes(periods, classDuration, breakDuration)
        },
        periods = periods,
        onPeriodsChange = { periods = it },
        detectedWeek = detectedWeek,
        detectedWeekDescription = detectedWeekDescription,
        error = error,
        onPreviewLiveUpdate = onPreviewLiveUpdate,
        scheduleAdjustmentsJson = scheduleAdjustmentsJson,
        onScheduleAdjustmentsChange = {
            scheduleAdjustmentsJson = it
            lastSavedConfig = lastSavedConfig.copy(scheduleAdjustmentsJson = it)
        },
        onOpenPeriodSchemes = ::openPeriodSchemeManagement,
        periodSchemeLibrary = periodSchemeLibrary,
        onSelectPeriodScheme = ::selectPeriodScheme
    )

    if (showExitSaveConfirm) {
        LiquidAlertDialog(
            title = "保存课表设置？",
            message = "保存课表、作息与调休安排后退出详细设置。作息修改仅影响当前课表，保存时会创建公共副本。",
            actions = listOf(
                LiquidAlertAction("保存并退出", LiquidAlertActionStyle.Primary) {
                    showExitSaveConfirm = false
                    saveConfigDraft(onExitCommitFinished)
                },
                LiquidAlertAction("不保存", LiquidAlertActionStyle.Destructive) {
                    showExitSaveConfirm = false
                    onExitCommitFinished(true)
                },
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) {
                    showExitSaveConfirm = false
                    onExitCommitFinished(false)
                }
            ),
            backdrop = popupBackdrop,
            config = visualState.config,
            onDismissRequest = {
                showExitSaveConfirm = false
                onExitCommitFinished(false)
            }
        )
    }

    if (showCourseRemapConfirm) {
        LiquidAlertDialog(
            title = "重映射课程节次？",
            message = "$courseRemapDescription\n课程名称、星期和周次不变。占用已删除节次、对应关系不明确或产生新冲突时会阻止保存。",
            actions = listOf(
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) {
                    showCourseRemapConfirm = false
                    pendingRemapSaveCompletion = null
                },
                LiquidAlertAction("确认并保存", LiquidAlertActionStyle.Primary) {
                    val completion = pendingRemapSaveCompletion
                    pendingRemapSaveCompletion = null
                    showCourseRemapConfirm = false
                    saveConfigDraft(completion, remapConfirmed = true,
                        termShorteningConfirmed = pendingRemapTermShorteningConfirmed)
                }
            ),
            backdrop = popupBackdrop,
            config = visualState.config,
            onDismissRequest = {
                showCourseRemapConfirm = false
                pendingRemapSaveCompletion = null
            },
            messageContent = {
                Text("$courseRemapDescription\n课程名称、星期和周次不变。占用已删除节次、对应关系不明确或产生新冲突时会阻止保存。",
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        )
    }
    if (showTermShorteningConfirm) {
        val nextTotal = totalWeeks.toIntOrNull() ?: lastSavedConfig.totalWeeks
        val affected = coursesOutsideShortenedTerm(
            state.courses, state.config.id, lastSavedConfig.totalWeeks, nextTotal
        )
        fun cancelTermShortening() {
            showTermShorteningConfirm = false
            val completion = pendingTermSaveCompletion
            pendingTermSaveCompletion = null
            completion?.invoke(false)
        }
        LiquidAlertDialog(
            title = "缩短学期到 $nextTotal 周？",
            message = "以下 ${affected.size} 条课程安排包含超出新学期的周次，缩短后不会在课表中显示。原排课数据会保留，延长学期后可重新显示。\n\n" +
                affected.joinToString("\n\n") { course ->
                    val hidden = course.copy(weeks = course.weeks.filter { it > nextTotal })
                    "${course.name} · ${compactWeekSelectionLabel(hidden.weeks)}\n${courseEditorScopeDescription(listOf(hidden), state.config)}"
                },
            actions = listOf(
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Secondary) { cancelTermShortening() },
                LiquidAlertAction("保留课程并缩短", LiquidAlertActionStyle.Primary) {
                    val completion = pendingTermSaveCompletion
                    pendingTermSaveCompletion = null
                    showTermShorteningConfirm = false
                    saveConfigDraft(completion, pendingTermRemapConfirmed, termShorteningConfirmed = true)
                }
            ),
            backdrop = popupBackdrop,
            config = visualState.config,
            onDismissRequest = { cancelTermShortening() }
        )
    }
}

internal fun shouldInterceptSettingsBack(
    section: SettingsSection,
    hasUnsavedChanges: Boolean,
    saveInProgress: Boolean
): Boolean = section == SettingsSection.Schedule && (hasUnsavedChanges || saveInProgress)

internal fun autoMatchPeriodTimes(
    periods: List<PeriodEntity>,
    classDurationMinutes: Int,
    breakDurationMinutes: Int,
    longBreaks: List<Pair<Int, Int>> = emptyList()
): List<PeriodEntity> {
    val duration = classDurationMinutes.coerceIn(1, 300).toLong()
    val defaultBreak = breakDurationMinutes.coerceIn(0, 300).toLong()
    val lbMap = longBreaks.toMap()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    var cursor = periods.firstOrNull()?.startTime?.let {
        runCatching { ScheduleImportParser.parseTimeForUi(it) }.getOrNull()
    }
    return periods.map { period ->
        val start = cursor ?: runCatching { ScheduleImportParser.parseTimeForUi(period.startTime) }.getOrNull()
        if (start == null) return@map period
        val end = start.plusMinutes(duration)
        val gap = (lbMap[period.periodIndex] ?: defaultBreak.toInt()).toLong()
        cursor = end.plusMinutes(gap)
        period.copy(
            startTime = start.format(formatter),
            endTime = end.format(formatter)
        )
    }
}

fun openKeepAliveSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    )
    intents.firstOrNull { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

@SuppressLint("BatteryLife")
private fun openBatteryOptimizationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
