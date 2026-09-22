package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.app.ui.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBrowserPrimaryAction
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionDialog
import com.xiaomanjun.sleepdownschedule.feature.importing.EduImportActivityScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.eduAdapterFromIntentKey
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.feature.importing.commitSystemCredentialAutofill
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalLegacyProgressiveBlur

/** Retains the existing Activity identity; all auto-refresh schools now use the education browser. */
class SwuUnifiedAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModelFactory(app, app.repository))
            val state by viewModel.state.collectAsStateWithLifecycle()
            val requested = remember { eduAdapterFromIntentKey(intent.getStringExtra(AdapterExtra)) }
            var adapter by remember { mutableStateOf<EduAdapter?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            val visitedUrls = remember { linkedSetOf<String>() }
            var teachingRedirects by remember { mutableIntStateOf(0) }
            var loginPageReady by remember { mutableStateOf(false) }
            var interaction by remember { mutableStateOf<AutoRefreshLoginInteraction?>(null) }
            LaunchedEffect(Unit) {
                runCatching {
                    val supported = ShiguangApiAdapterCatalog.loadSupported(app)
                    if (requested == null) supported.firstOrNull(ShiguangApiAdapterCatalog::isSwuAdapter)
                    else ShiguangApiAdapterCatalog.find(supported, requested.school.id, requested.adapterId)
                }.onSuccess {
                    adapter = it
                    if (it == null) error = "该学校暂不支持自动刷新"
                }.onFailure { error = "学校列表读取失败，请返回重试" }
            }
            val webBackdrop = rememberGlassLayerBackdrop(
                domain = GlassBackdropDomain.Content,
                providerId = "auto-refresh-login-web"
            )
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
                        topBarBackdropOverride = webBackdrop
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
                                adapter = selected.copy(importUrl = AutoRefreshLoginRoutes.entryUrl(selected)),
                                backdrop = backdrop,
                                webContentBackdrop = webBackdrop,
                                primaryAction = EduBrowserPrimaryAction(
                                    label = "读取登录态",
                                    guide = if (loginPageReady) "页面已就绪，点击“读取登录态”校验并保存凭证。课表可在连接后刷新。"
                                        else "请完成学校登录并进入教务系统，再点击“读取登录态”保存凭证。",
                                    enabled = loginPageReady,
                                    onPageStarted = { _, _ -> loginPageReady = false },
                                    onPageFinished = { webView, url ->
                                        url?.takeIf { AutoRefreshWebSession.origin(it) != null }?.let(visitedUrls::add)
                                        loginPageReady = AutoRefreshLoginRoutes.isSessionPage(selected.school.id, url)
                                        if (ShiguangApiAdapterCatalog.isSwuAdapter(selected) &&
                                            SwuAuthRoutes.isAuthenticatedTeachingPage(url) &&
                                            !SwuAuthRoutes.isCoursePage(url) && teachingRedirects < 3
                                        ) {
                                            teachingRedirects++
                                            webView.loadUrl(SwuAuthRoutes.CoursePageUrl)
                                        }
                                    },
                                    onInvoke = { webView, _, desktopMode ->
                                        val currentUrl = webView.url.orEmpty()
                                        require(AutoRefreshLoginRoutes.isSessionPage(selected.school.id, currentUrl)) {
                                            "请先完成学校登录并进入教务系统"
                                        }
                                        val storage = AutoRefreshWebSession.captureStorage(webView, selected.school.id)
                                        val cookieUrls = visitedUrls + selected.importUrl + currentUrl +
                                            if (ShiguangApiAdapterCatalog.isSwuAdapter(selected)) SwuAuthRoutes.sessionCookieUrls else emptyList()
                                        val result = AutoRefreshScheduleCoordinator.connect(
                                            context = app,
                                            adapter = selected,
                                            scheduleId = intent.getIntExtra(ScheduleExtra, state.config.id),
                                            initialCookies = AutoRefreshWebSession.captureCookies(cookieUrls),
                                            authenticatedUrl = currentUrl,
                                            webStorage = storage,
                                            desktopMode = desktopMode,
                                            onInteraction = { interaction = it }
                                        )
                                        if (result.success) {
                                            webView.commitSystemCredentialAutofill()
                                            AutoRefreshScheduleWorker.updateSchedule(app, result.profile)
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

    companion object {
        private const val AdapterExtra = "auto_refresh_auth_adapter"
        private const val ScheduleExtra = "auto_refresh_auth_schedule"

        internal fun intent(context: Context, adapter: EduAdapter, scheduleId: Int): Intent =
            Intent(context, SwuUnifiedAuthActivity::class.java)
                .putExtra(AdapterExtra, adapter.toIntentKey())
                .putExtra(ScheduleExtra, scheduleId)
    }
}
