package com.xiaomanjun.sleepdownschedule

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsetsController
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.app.entrypoints.VersionWelcomeActivity
import com.xiaomanjun.sleepdownschedule.feature.onboarding.VersionWelcomeEntry
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.PlatformSharedViewKitApi
import com.xiaomanjun.sleepdownschedule.transition.TransitionFeatureGate
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayloadStore
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionState
import com.xiaomanjun.sleepdownschedule.transition.transitionSessionIdOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val OpenScheduleWeekExtra = "com.xiaomanjun.sleepdownschedule.extra.OPEN_SCHEDULE_WEEK"
class MainActivity : ComponentActivity() {
    private val pendingExternalIcsUri = MutableStateFlow<Uri?>(null)
    private val pendingExternalIcsUriFlow = pendingExternalIcsUri.asStateFlow()
    private val pendingScheduleWeek = MutableStateFlow<Int?>(null)
    private val startupContentReady = AtomicBoolean(false)
    private var startupPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var inlineWelcomeVisible by mutableStateOf(false)
    private var prepareInlineHome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val welcomeSession = TransitionPayloadStore.session(intent.transitionSessionIdOrNull())
            ?.takeIf { it.routeId == TransitionRouteId.WelcomeToHome }
        if (welcomeSession != null) ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        val restoreInlineWelcome = savedInstanceState?.getBoolean(InlineWelcomeVisibleState) == true
        if (welcomeSession == null && (restoreInlineWelcome || VersionWelcomeEntry.shouldShow(this, intent))) {
            VersionWelcomeEntry.consumeReplay(intent)
            if (!restoreInlineWelcome && useNativeWelcome()) {
                showVersionWelcome()
                return
            }
            inlineWelcomeVisible = true
        }
        acceptEntryIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, Build.VERSION.SDK_INT < 35)
        if (welcomeSession != null) ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        val contentRoot = findViewById<View>(android.R.id.content)
        val systemBars = WindowCompat.getInsetsController(window, contentRoot)
        startupPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (inlineWelcomeVisible || startupContentReady.get()) {
                startupPreDrawListener?.let { listener ->
                    if (contentRoot.viewTreeObserver.isAlive) {
                        contentRoot.viewTreeObserver.removeOnPreDrawListener(listener)
                    }
                }
                startupPreDrawListener = null
                true
            } else {
                false
            }
        }.also(contentRoot.viewTreeObserver::addOnPreDrawListener)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val config by viewModel.themeConfig.collectAsStateWithLifecycle()
            val externalIcsUri by pendingExternalIcsUriFlow.collectAsStateWithLifecycle()
            val requestedScheduleWeek by pendingScheduleWeek.collectAsStateWithLifecycle()
            var homeReady by remember { mutableStateOf(false) }
            var lightWelcomeNavigation by remember { mutableStateOf(false) }
            val welcomeState = welcomeSession?.state?.collectAsStateWithLifecycle()?.value
            val nativeWelcomeOpening = welcomeState != null && !welcomeState.terminal && welcomeState != TransitionSessionState.Open
            val welcomeOpening = nativeWelcomeOpening || inlineWelcomeVisible
            val darkTheme = appUsesDarkTheme(config)
            CourseScheduleTheme(config = config) {
                val timetable: @Composable () -> Unit = {
                    val homeSemantics = if (inlineWelcomeVisible) Modifier.clearAndSetSemantics {} else Modifier
                    Box(Modifier.fillMaxSize().then(homeSemantics)) {
                        if (!inlineWelcomeVisible || prepareInlineHome) CourseScheduleAppUi(
                            viewModel = viewModel,
                            externalIcsUri = externalIcsUri,
                            onExternalIcsConsumed = { pendingExternalIcsUri.compareAndSet(it, null) },
                            requestedScheduleWeekId = requestedScheduleWeek,
                            onRequestedScheduleWeekShown = { id ->
                                if (pendingScheduleWeek.compareAndSet(id, null) && intent.getIntExtra(OpenScheduleWeekExtra, 0) == id) {
                                    intent.removeExtra(OpenScheduleWeekExtra)
                                }
                            },
                            onStartupContentReady = {
                                homeReady = true
                                if (startupContentReady.compareAndSet(false, true)) contentRoot.postInvalidateOnAnimation()
                            },
                            versionWelcomeVisible = welcomeOpening,
                        )
                    }
                }
                if (welcomeSession != null) CrossActivityTransitionHost(
                    activity = this@MainActivity,
                    sourceContent = {},
                    openingReady = homeReady,
                    handleSystemBack = false,
                ) { timetable() } else timetable()
                BackHandler(enabled = nativeWelcomeOpening) { }
                if (inlineWelcomeVisible) InlineVersionWelcome(
                    activity = this@MainActivity,
                    homeReady = homeReady,
                    onPrepareHome = { prepareInlineHome = true },
                    onLightBackground = { lightWelcomeNavigation = it },
                    onFinished = {
                        VersionWelcomeEntry.markSeen(this@MainActivity)
                        inlineWelcomeVisible = false
                    },
                )
            }
            if (inlineWelcomeVisible && Build.VERSION.SDK_INT >= 35) {
                DisposableEffect(systemBars) {
                    systemBars.hide(WindowInsetsCompat.Type.statusBars())
                    onDispose { systemBars.show(WindowInsetsCompat.Type.statusBars()) }
                }
            }
            LaunchedEffect(welcomeOpening, darkTheme, inlineWelcomeVisible, lightWelcomeNavigation) {
                systemBars.isAppearanceLightNavigationBars = if (inlineWelcomeVisible) lightWelcomeNavigation else !darkTheme
                if (inlineWelcomeVisible && Build.VERSION.SDK_INT < 35) {
                    systemBars.isAppearanceLightStatusBars = lightWelcomeNavigation
                }
                if (welcomeSession != null && !welcomeOpening) {
                    if (Build.VERSION.SDK_INT >= 30) setTranslucent(false)
                    window.applyAppThemeSurface(darkTheme)
                    VersionWelcomeEntry.markSeen(this@MainActivity)
                }
            }
        }
    }

    private fun useNativeWelcome(): Boolean =
        !isInMultiWindowMode && !isInPictureInPictureMode &&
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            TransitionFeatureGate.allowsSharedViewKit(TransitionRouteCatalog.get(TransitionRouteId.WelcomeToHome)) &&
            runCatching { PlatformSharedViewKitApi.supportedProtocol() > 0 }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun showVersionWelcome() {
        startActivity(Intent(this, VersionWelcomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ))
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        val contentRoot = findViewById<View>(android.R.id.content)
        startupPreDrawListener?.let { listener ->
            if (contentRoot?.viewTreeObserver?.isAlive == true) {
                contentRoot.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        startupPreDrawListener = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (VersionWelcomeEntry.shouldShow(this, intent)) {
            VersionWelcomeEntry.consumeReplay(intent)
            if (useNativeWelcome()) showVersionWelcome() else inlineWelcomeVisible = true
        } else acceptEntryIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(InlineWelcomeVisibleState, inlineWelcomeVisible)
    }

    private fun acceptEntryIntent(intent: Intent?) {
        val scheduleId = intent?.getIntExtra(OpenScheduleWeekExtra, 0) ?: 0
        if (scheduleId > 0) pendingScheduleWeek.value = scheduleId
        acceptExternalIcsIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun acceptExternalIcsIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        } ?: return
        pendingExternalIcsUri.value = uri
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (hideFromRecentsEnabled) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.appTasks.forEach { it.setExcludeFromRecents(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hideFromRecentsEnabled) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.appTasks.forEach { it.setExcludeFromRecents(false) }
        }
    }

    private companion object {
        const val InlineWelcomeVisibleState = "inline_welcome_visible"
    }
}

@Composable
fun CourseScheduleTheme(
    config: ScheduleConfigEntity = defaultConfig(),
    content: @Composable () -> Unit
) {
    com.xiaomanjun.sleepdownschedule.platform.coloros.ColorOsStatusBarScrollToTop()
    com.xiaomanjun.sleepdownschedule.app.ui.ProvideVisualEffects {
        CourseScheduleThemeContent(config, content)
    }
}

@Composable
private fun CourseScheduleThemeContent(
    config: ScheduleConfigEntity,
    content: @Composable () -> Unit
) {
    val darkTheme = appUsesDarkTheme(config)
    val view = LocalView.current
    LaunchedEffect(config.followSystemDarkMode, darkTheme, view.context) {
        // Changing launcher aliases during the settings-to-home handoff can make ColorOS/Oplus
        // remove the visible task. Record the desired appearance here; CourseScheduleApp applies
        // the alias only after the whole process enters the background.
        if (view.context is MainActivity) {
            AppIconManager.syncAppearance(
                context = view.context.applicationContext,
                followsSystemDarkMode = config.followSystemDarkMode,
                darkTheme = darkTheme
            )
        }
    }
    LaunchedEffect(darkTheme, view) {
        val activity = view.context as? ComponentActivity ?: return@LaunchedEffect
        val window = activity.window
        if (!com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator.usesLegacyMorphWindow(activity)) {
            window.applyAppThemeSurface(darkTheme)
        }
        window.makeSystemBarsTransparent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (darkTheme) 0 else {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                },
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (darkTheme) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
    val glow = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects.current.usesCouiControls
    val blue = if (glow) Color(com.xiaomanjun.sleepdownschedule.core.ui.material.rememberWallpaperThemeAccent(config, darkTheme))
        else Color(0xFF007AFF)
    val blueContainer = if (glow) androidx.compose.ui.graphics.lerp(blue, Color.White, 0.84f) else Color(0xFFD6E9FF)
    val darkBlueContainer = if (glow) androidx.compose.ui.graphics.lerp(blue, Color.Black, 0.55f) else Color(0xFF003A66)
    MaterialTheme(
        shapes = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownContinuousShapes,
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = blue,
                onPrimary = Color.White,
                primaryContainer = darkBlueContainer,
                onPrimaryContainer = blueContainer,
                secondary = blue,
                secondaryContainer = darkBlueContainer,
                tertiary = blue,
                tertiaryContainer = darkBlueContainer,
                background = Color.Black,
                surface = Color(0xFF111111),
                surfaceVariant = Color(0xFF1C1C1E),
                surfaceContainerHigh = Color(0xFF1C1C1E)
            )
        } else {
            lightColorScheme(
                primary = blue,
                onPrimary = Color.White,
                primaryContainer = blueContainer,
                onPrimaryContainer = darkBlueContainer,
                secondary = blue,
                secondaryContainer = blueContainer,
                tertiary = blue,
                tertiaryContainer = blueContainer,
                background = SleepDownDesignTokens.LightPageBackground,
                surface = Color.White,
                surfaceVariant = Color(0xFFF2F2F7),
                surfaceContainerHigh = Color.White
            )
        }
    ) {
        val morphWindow = (view.context as? ComponentActivity)?.let(ActivityTransitionCoordinator::hasAnchoredMorphSession) == true
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (morphWindow) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

@Suppress("DEPRECATION")
private fun android.view.Window.makeSystemBarsTransparent() {
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
}

internal fun android.view.Window.applyAppThemeSurface(darkTheme: Boolean) {
    val backgroundColor = if (darkTheme) {
        android.graphics.Color.BLACK
    } else {
        SleepDownDesignTokens.LightPageBackground.toArgb()
    }
    setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))
    decorView.setBackgroundColor(backgroundColor)
}
