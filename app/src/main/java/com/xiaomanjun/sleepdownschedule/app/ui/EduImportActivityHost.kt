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
            val retainedSession = remember(adapter) {
                if (adapter != null && intent.getBooleanExtra(AutoRefreshWebSession.RestoreSessionExtra, false)) {
                    AutoRefreshScheduleStore.load(app)?.takeIf {
                        it.schoolId == adapter.school.id && it.adapterId == adapter.adapterId
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
            val sessionRestores = remember { mutableListOf<AutoCloseable>() }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { sessionRestores.forEach { it.close() }; sessionRestores.clear() }
            }
            val eduWebContentBackdrop = rememberGlassLayerBackdrop(
                domain = GlassBackdropDomain.Content,
                providerId = "edu-import-web-content"
            )
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
                        topBarBackdropOverride = eduWebContentBackdrop
                    ) { backdrop ->
                        if (adapter == null) {
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
                                useDetailTopPadding = true,
                                prepareWebView = { webView ->
                                    retainedSession?.let {
                                        AutoRefreshWebSession.restore(webView, it)?.let(sessionRestores::add)
                                    }
                                },
                                initialDesktopMode = retainedSession?.desktopMode ?: false,
                                onParsed = { draft -> saveError = null; pendingDraft = draft }
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
                                    if (retainedSession != null && !createNewSchedule) {
                                        scope.launch {
                                            try {
                                                com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleCoordinator
                                                    .reviewBrowserRefresh(app, retainedSession, previewDraft)
                                                returnToScheduleHome()
                                            } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (error: Exception) { saveError = error.message ?: "课表比较失败，请重试" }
                                            finally { submitting = false }
                                        }
                                        return@ConfirmScheduleScreen
                                    }
                                    viewModel.importDraft(previewDraft, createNewSchedule, onFailure = {
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
