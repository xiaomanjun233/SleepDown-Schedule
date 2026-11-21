package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.feature.schedule.ScheduleScreenViewModel
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionDialog
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBrowserActionFailure
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBrowserPrimaryAction
import com.xiaomanjun.sleepdownschedule.feature.importing.EduImportActivityScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.commitSystemCredentialAutofill
import com.xiaomanjun.sleepdownschedule.feature.importing.eduAdapterFromIntentKey
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalLegacyProgressiveBlur

/** Retains the existing Activity identity; all auto-refresh schools now use the education browser. */
class SwuUnifiedAuthActivity : ComponentActivity() {
    private lateinit var bindingId: String
    private var profileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { AutoRefreshWebSession.pruneRetiredProfiles(AutoRefreshScheduleStore.load(this)?.browserProfileName) }
        bindingId = savedInstanceState?.getString(BindingState) ?: AutoRefreshWebSession.newBindingId()
        profileName = bindingId.takeIf { AutoRefreshWebSession.isSupported() }?.let(AutoRefreshWebSession::profileName)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleScreenViewModel = viewModel(factory = ScheduleScreenViewModel.Factory(app, app.repository, app.applicationScope))
            val state by viewModel.state.collectAsStateWithLifecycle()
            val requested = remember { eduAdapterFromIntentKey(intent.getStringExtra(AdapterExtra)) }
            var adapter by remember { mutableStateOf<EduAdapter?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            var loginPageReady by remember { mutableStateOf(false) }
            var interaction by remember { mutableStateOf<AutoRefreshLoginInteraction?>(null) }
            val previous = remember { AutoRefreshScheduleStore.load(app) }
            LaunchedEffect(requested) {
                try {
                    val choice = requested
                    adapter = choice?.let {
                        ShiguangApiAdapterCatalog.find(ShiguangApiAdapterCatalog.loadLoginAdapters(app),
                            it.school.id, it.adapterId, it.source.name)
                    }
                    if (adapter == null) error = "该教务入口已更新，请返回重新选择"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "学校列表读取失败，请返回重试"
                }
            }
            val webBackdrop = rememberGlassLayerBackdrop(
                domain = GlassBackdropDomain.Content,
                providerId = "auto-refresh-login-web"
            )
            val webToolbarColor = remember(adapter) {
                androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.Color?>(null)
            }
            CourseScheduleTheme(config = state.config) {
                CompositionLocalProvider(LocalLegacyProgressiveBlur provides true) {
                    DetailActivityScaffold(
                        title = adapter?.school?.name ?: "教务登录",
                        config = state.config,
                        onBack = ::finish,
                        isolateContentFromBackdrop = true,
                        compactTopBar = true,
                        centerCompactTitle = true,
                        compactTitleMatchesSettings = true,
                        preserveStatusBarSpace = true,
                        topBarBackdropOverride = webBackdrop,
                        topBarBackgroundColor = webToolbarColor
                    ) { backdrop ->
                        interaction?.let { pending ->
                            EduBridgeInteractionDialog(
                                request = pending.request,
                                bridge = pending.bridge,
                                state = state,
                                backdrop = backdrop,
                                onFinished = { if (interaction === pending) interaction = null },
                                resolveInteraction = { _, value -> pending.resolve(value) }
                            )
                        }
                        val selected = adapter
                        if (selected == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (error == null) CircularProgressIndicator() else Text(error!!)
                            }
                        } else {
                            EduImportActivityScreen(
                                state = state,
                                adapter = selected,
                                browserProfileName = profileName,
                                initialDesktopMode = previous?.takeIf { it.matches(selected.school.id, selected.adapterId,
                                    intent.getIntExtra(ScheduleExtra, state.config.id)) }?.desktopMode ?: false,
                                backdrop = backdrop,
                                webContentBackdrop = webBackdrop,
                                webToolbarColor = webToolbarColor,
                                primaryAction = EduBrowserPrimaryAction(
                                    label = "验证并连接",
                                    progressMessage = "正在验证教务连接…",
                                    guide = "请登录学校教务并打开课表页面，再验证连接。验证时可选择学期。此操作只建立连接。",
                                    enabled = loginPageReady,
                                    onPageStarted = { _, _ -> loginPageReady = false },
                                    onPageFinished = { _, url -> loginPageReady = AutoRefreshLoginRoutes.isSessionPage(url) },
                                    onInvoke = { webView, _, desktopMode ->
                                        val currentUrl = webView.url.orEmpty()
                                        if (!AutoRefreshLoginRoutes.isSessionPage(currentUrl)) {
                                            throw EduBrowserActionFailure("请先登录并进入学校的课表页面")
                                        }
                                        AutoRefreshWebSession.flush(webView)
                                        val result = AutoRefreshScheduleCoordinator.connect(
                                            context = app,
                                            adapter = selected,
                                            scheduleId = intent.getIntExtra(ScheduleExtra, state.config.id),
                                            bindingId = bindingId,
                                            isolatedBrowser = profileName != null,
                                            authenticatedUrl = currentUrl,
                                            desktopMode = desktopMode,
                                            onInteraction = { interaction = it }
                                        )
                                        if (result.success) {
                                            webView.commitSystemCredentialAutofill()
                                            setResult(Activity.RESULT_OK)
                                            finish()
                                        }
                                        result.message
                                    }
                                ),
                                onParsed = {}
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BindingState, bindingId)
    }

    override fun onDestroy() {
        if (isFinishing && AutoRefreshScheduleStore.load(this)?.bindingId != bindingId) {
            runCatching { AutoRefreshWebSession.retire(profileName) }
        }
        super.onDestroy()
    }

    companion object {
        private const val BindingState = "auto_refresh_binding_attempt"
        private const val AdapterExtra = "auto_refresh_auth_adapter"
        private const val ScheduleExtra = "auto_refresh_auth_schedule"

        internal fun intent(context: Context, adapter: EduAdapter, scheduleId: Int): Intent =
            Intent(context, SwuUnifiedAuthActivity::class.java)
                .putExtra(AdapterExtra, adapter.toIntentKey())
                .putExtra(ScheduleExtra, scheduleId)
    }
}
