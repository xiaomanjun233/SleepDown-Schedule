package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.app.startup.*
import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val pendingExternalIcsUri = MutableStateFlow<Uri?>(null)
    private val pendingExternalIcsUriFlow = pendingExternalIcsUri.asStateFlow()
    private val startupContentReady = AtomicBoolean(false)
    private var startupPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptExternalIcsIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val contentRoot = findViewById<View>(android.R.id.content)
        startupPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (startupContentReady.get()) {
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
            CourseScheduleTheme(config = config) {
                CourseScheduleAppUi(
                    viewModel = viewModel,
                    externalIcsUri = externalIcsUri,
                    onExternalIcsConsumed = { consumed ->
                        pendingExternalIcsUri.compareAndSet(consumed, null)
                    },
                    onStartupContentReady = {
                        if (startupContentReady.compareAndSet(false, true)) {
                            contentRoot.postInvalidateOnAnimation()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        val contentRoot = findViewById<View>(android.R.id.content)
        startupPreDrawListener?.let { listener ->
            if (contentRoot.viewTreeObserver.isAlive) {
                contentRoot.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        startupPreDrawListener = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
}

@Composable
fun CourseScheduleTheme(
    config: ScheduleConfigEntity = defaultConfig(),
    content: @Composable () -> Unit
) {
    val darkTheme = appUsesDarkTheme(config)
    val view = LocalView.current
    LaunchedEffect(config.followSystemDarkMode, darkTheme, view.context) {
        // Changing launcher aliases from a secondary settings Activity can make
        // ColorOS/Oplus remove the visible task. Apply icon changes only from the
        // main Activity; settings still update their theme immediately and the
        // launcher alias catches up when the user returns home.
        if (view.context is MainActivity) {
            AppIconManager.syncAppearance(
                context = view.context,
                followsSystemDarkMode = config.followSystemDarkMode,
                darkTheme = darkTheme
            )
        }
    }
    LaunchedEffect(darkTheme, view) {
        val window = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
        window.applyAppThemeSurface(darkTheme)
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
    val blue = Color(0xFF007AFF)
    val blueContainer = Color(0xFFD6E9FF)
    val darkBlueContainer = Color(0xFF003A66)
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = blue,
                onPrimary = Color.White,
                primaryContainer = darkBlueContainer,
                onPrimaryContainer = Color(0xFFD6E9FF),
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
                onPrimaryContainer = Color(0xFF003A66),
                secondary = blue,
                secondaryContainer = blueContainer,
                tertiary = blue,
                tertiaryContainer = blueContainer,
                background = Color.White,
                surface = Color.White,
                surfaceVariant = Color(0xFFF2F2F7),
                surfaceContainerHigh = Color.White
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
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
        android.graphics.Color.rgb(0xED, 0xEE, 0xF3)
    }
    setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))
    decorView.setBackgroundColor(backgroundColor)
}
