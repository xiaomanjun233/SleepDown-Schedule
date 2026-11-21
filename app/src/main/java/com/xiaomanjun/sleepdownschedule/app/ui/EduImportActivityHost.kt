package com.xiaomanjun.sleepdownschedule.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.ImportDraftSource
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.feature.course.editor.MissingCourseScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.ConfirmScheduleScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.EduImportActivityScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.PendingImportSetupStore
import com.xiaomanjun.sleepdownschedule.feature.importing.eduAdapterFromIntentKey
import com.xiaomanjun.sleepdownschedule.feature.importing.isGeneralEduTool
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleCoordinator
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshWebSession
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalLegacyProgressiveBlur
import com.xiaomanjun.sleepdownschedule.transition.legacy.returnToScheduleHome

@OptIn(ExperimentalMaterial3Api::class)
open class EduImportActivityHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val adapter = remember { eduAdapterFromIntentKey(intent.getStringExtra(EduAdapterExtra)) }
            val refreshRequest = remember {
                intent.getBooleanExtra(AutoRefreshWebSession.RestoreSessionExtra, false)
            }
            val retainedSession = remember(adapter) {
                if (adapter != null && refreshRequest) {
                    AutoRefreshScheduleStore.load(app)?.takeIf {
                        val binding = intent.getStringExtra(AutoRefreshWebSession.BindingExtra)
                        it.bindingId != null && it.adapterId == adapter.adapterId &&
                            (if (binding != null) it.bindingId == binding else it.schoolId == adapter.school.id)
                    }
                } else null
            }
            val browserAdapter = remember(adapter, retainedSession) {
                adapter?.copy(importUrl = retainedSession?.authenticatedUrl ?: adapter.importUrl)
            }
            var pendingDraft by remember { mutableStateOf<ImportDraft?>(null) }
            var submitting by remember { mutableStateOf(false) }
            var saveError by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            val eduWebContentBackdrop = rememberGlassLayerBackdrop(
                domain = GlassBackdropDomain.Content,
                providerId = "edu-import-web-content"
            )
            val webToolbarColor = remember(browserAdapter) { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
            CourseScheduleTheme(config = state.config) {
                if (pendingDraft == null) {
                    CompositionLocalProvider(LocalLegacyProgressiveBlur provides true) {
                    DetailActivityScaffold(
                        title = adapter?.school?.name ?: "教务导入",
                        config = state.config,
                        onBack = { finish() },
                        isolateContentFromBackdrop = true,
                        compactTopBar = true,
                        centerCompactTitle = true,
                        compactTitleMatchesSettings = true,
                        preserveStatusBarSpace = true,
                        topBarBackdropOverride = eduWebContentBackdrop,
                        topBarBackgroundColor = webToolbarColor
                    ) { backdrop ->
                        if (adapter == null || (refreshRequest && retainedSession == null)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = detailContentTopPadding())
                            ) {
                                MissingCourseScreen(onBack = { finish() })
                            }
                        } else {
                            EduImportActivityScreen(
                                state = state,
                                adapter = checkNotNull(browserAdapter),
                                backdrop = backdrop,
                                webContentBackdrop = eduWebContentBackdrop,
                                webToolbarColor = webToolbarColor,
                                useDetailTopPadding = true,
                                standaloneImport = !refreshRequest,
                                browserProfileName = retainedSession?.browserProfileName,
                                initialUrl = retainedSession?.authenticatedUrl,
                                initialDesktopMode = retainedSession?.desktopMode ?: false,
                                onParsed = { draft ->
                                    if (!refreshRequest) {
                                        saveError = null
                                        pendingDraft = draft
                                    } else if (!submitting && retainedSession != null) {
                                        submitting = true
                                        scope.launch {
                                            try {
                                                val result = AutoRefreshScheduleCoordinator.reviewBrowserRefresh(app, retainedSession, draft)
                                                android.widget.Toast.makeText(app, result, android.widget.Toast.LENGTH_SHORT).show()
                                                returnToScheduleHome()
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                android.widget.Toast.makeText(app, error.message ?: "课表比较失败，请重试",
                                                    android.widget.Toast.LENGTH_LONG).show()
                                            } finally {
                                                submitting = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    }
                } else {
                    val previewDraft = checkNotNull(pendingDraft)
                    androidx.activity.compose.BackHandler {
                        if (!submitting) pendingDraft = null
                    }
                    DetailActivityScaffold(
                        title = "导入预览",
                        config = state.config,
                        onBack = { if (!submitting) pendingDraft = null },
                        isolateContentFromBackdrop = true,
                        compactTopBar = true,
                        centerCompactTitle = true,
                        compactTitleMatchesSettings = true,
                        preserveStatusBarSpace = true
                    ) { backdrop ->
                    if (adapter == null) {
                        MissingCourseScreen(onBack = { finish() })
                    } else {
                        Box(modifier = Modifier.padding(top = detailContentTopPadding())) {
                            ConfirmScheduleScreen(
                                draft = previewDraft,
                                submitting = submitting,
                                errorMessage = saveError,
                                warning = if (adapter.isGeneralEduTool()) "可能部分节次信息会有误，请自行检查修改。" else null,
                                backdrop = backdrop,
                                onCancel = { if (!submitting) pendingDraft = null },
                                onConfirm = { createNewSchedule ->
                                    if (submitting) return@ConfirmScheduleScreen
                                    submitting = true
                                    saveError = null
                                    viewModel.importDraft(previewDraft, createNewSchedule,
                                        historySource = adapter.school.name, onFailure = {
                                        submitting = false
                                        saveError = it.message ?: "保存失败，请重试"
                                    }) { scheduleId ->
                                        PendingImportSetupStore.put(this@EduImportActivityHost, scheduleId)
                                        returnToScheduleHome()
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
