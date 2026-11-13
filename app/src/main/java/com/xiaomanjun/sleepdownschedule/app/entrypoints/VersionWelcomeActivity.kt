package com.xiaomanjun.sleepdownschedule.app.entrypoints

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.itos.coui.button.CouiButtonDefaults
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.feature.onboarding.VersionWelcomeEntry
import com.xiaomanjun.sleepdownschedule.feature.onboarding.VersionWelcomeScreen
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayloadStore
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSession
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionState
import com.xiaomanjun.sleepdownschedule.transition.liveTransitionBoundsInActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VersionWelcomeActivity : ComponentActivity() {
    private var opening by mutableStateOf(false)
    private var sessionId: TransitionSessionId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.hide(WindowInsetsCompat.Type.statusBars())
        bars.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val restoredId = savedInstanceState?.getString(OpeningSessionState)?.let(::TransitionSessionId)
        TransitionPayloadStore.session(restoredId)?.let(::waitForHome)
        setContent {
            VersionWelcomeScreen(
                opening = opening,
                onLightBackground = { bars.isAppearanceLightNavigationBars = it },
                onContinue = ::openHome,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(OpeningSessionState, sessionId?.value)
    }

    private fun openHome(source: View?) {
        if (opening) return
        opening = true
        lifecycleScope.launch {
            val destination = Intent(this@VersionWelcomeActivity, MainActivity::class.java)
            val bounds = source?.liveTransitionBoundsInActivity(this@VersionWelcomeActivity)
            try {
                if (bounds == null) {
                    startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
                    finishWelcome()
                    return@launch
                }
                val anchor = TransitionAnchorFrame(
                    bounds, bounds.height / 2f, bitmap = null,
                    surfaceColor = CouiButtonDefaults.secondaryColors(dark = false).surface.toArgb(),
                )
                val result = ActivityTransitionCoordinator.open(
                    this@VersionWelcomeActivity, TransitionRouteId.WelcomeToHome, destination,
                    TransitionPayload(openingAnchor = anchor, returnAnchorProvider = null, openingSourceView = source),
                    prepareLegacy = { payload ->
                        val frame = captureGlassWindowRegion(window, bounds)
                        val underlay = if (Build.VERSION.SDK_INT < 30) {
                            val decor = window.decorView
                            captureGlassWindowRegion(window, Rect(0f, 0f, decor.width.toFloat(), decor.height.toFloat()))
                        } else null
                        payload.copy(openingAnchor = anchor.copy(bitmap = frame), backgroundBitmap = underlay)
                    },
                )
                if (result is TransitionLaunchResult.Failed) {
                    showLaunchError()
                } else {
                    val session = TransitionPayloadStore.session(result.sessionId)
                    if (session == null) finishWelcome() else waitForHome(session)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                showLaunchError()
            }
        }
    }

    private fun waitForHome(session: TransitionSession) {
        sessionId = session.id
        opening = true
        lifecycleScope.launch {
            val terminal = session.state.first { it.terminal }
            sessionId = null
            if (terminal == TransitionSessionState.Finished) finishWelcome() else opening = false
        }
    }

    private fun showLaunchError() {
        opening = false
        Toast.makeText(this, "无法打开课程表，请重试", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun finishWelcome() {
        VersionWelcomeEntry.markSeen(this)
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        finish()
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val OpeningSessionState = "welcome_opening_session"
    }
}
