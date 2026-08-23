package com.xiaomanjun.sleepdownschedule

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionNativeSourceViewProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayloadStore
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.transitionSessionIdOrNull
import kotlinx.coroutines.launch

private const val ProbeStageExtra = "oplus_transition_probe_stage"

private enum class ProbeStage {
    Empty,
    Compose,
    GraphicsLayer,
    RenderEffect,
    Morph
}

/**
 * Debug-only, adb-launchable source for layer-by-layer ColorOS bisection. It never participates in
 * the release manifest or production route selection.
 */
class OplusTransitionDebugSourceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (48 * density).toInt(), (24 * density).toInt(), 0)
            background = ColorDrawable(Color.rgb(237, 238, 243))
        }
        val source = TextView(this).apply {
            text = "ViewSeamless source card"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = ColorDrawable(Color.rgb(64, 96, 180))
        }
        root.addView(
            source,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (96 * density).toInt()
            )
        )
        ProbeStage.entries.forEach { stage ->
            root.addView(Button(this).apply {
                text = stage.name
                setOnClickListener { openProbe(source, stage, density) }
            })
        }
        setContentView(root)
    }

    private fun openProbe(source: View, stage: ProbeStage, density: Float) {
        if (source.width <= 0 || source.height <= 0) return
        val location = IntArray(2)
        source.getLocationInWindow(location)
        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        source.draw(Canvas(bitmap))
        val anchor = TransitionAnchorFrame(
            boundsInWindow = androidx.compose.ui.geometry.Rect(
                location[0].toFloat(),
                location[1].toFloat(),
                location[0] + source.width.toFloat(),
                location[1] + source.height.toFloat()
            ),
            cornerRadiusPx = 20f * density,
            bitmap = bitmap
        )
        lifecycleScope.launch {
            ActivityTransitionCoordinator.openDebugNativeCandidate(
                activity = this@OplusTransitionDebugSourceActivity,
                routeId = TransitionRouteId.CourseManagementToDetail,
                intent = Intent(
                    this@OplusTransitionDebugSourceActivity,
                    OplusTransitionDebugDestinationActivity::class.java
                ).putExtra(ProbeStageExtra, stage.name),
                payload = TransitionPayload(
                    openingAnchor = anchor,
                    returnAnchorProvider = StaticTransitionAnchorProvider(anchor),
                    nativeSourceViewProvider = TransitionNativeSourceViewProvider { source }
                )
            )
        }
    }
}

class OplusTransitionDebugDestinationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        val stage = runCatching {
            ProbeStage.valueOf(intent.getStringExtra(ProbeStageExtra).orEmpty())
        }.getOrDefault(ProbeStage.Empty)
        setContent {
            CourseScheduleTheme(config = defaultConfig()) {
                if (stage == ProbeStage.Morph) {
                    CrossActivityTransitionHost(
                        activity = this@OplusTransitionDebugDestinationActivity,
                        sourceContent = { ProbeCard("source") }
                    ) {
                        ProbeContent(stage)
                    }
                } else {
                    DebugProbeSessionHost(this@OplusTransitionDebugDestinationActivity) {
                        ProbeContent(stage)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugProbeSessionHost(
    activity: OplusTransitionDebugDestinationActivity,
    content: @Composable () -> Unit
) {
    val sessionId = activity.intent.transitionSessionIdOrNull()
    BackHandler {
        if (ActivityTransitionCoordinator.requestNativeClose(activity, sessionId)) {
            activity.finish()
        } else {
            TransitionPayloadStore.remove(sessionId)
            activity.finish()
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
    content()
}

@Composable
private fun ProbeContent(stage: ProbeStage) {
    when (stage) {
        ProbeStage.Empty -> Box(Modifier.fillMaxSize())
        ProbeStage.Compose -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Compose destination")
            Card { Text("Card", Modifier.padding(24.dp)) }
        }
        ProbeStage.GraphicsLayer -> Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            contentAlignment = Alignment.Center
        ) { ProbeCard("graphicsLayer") }
        ProbeStage.RenderEffect -> Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    renderEffect = BlurEffect(1f, 1f, TileMode.Clamp)
                },
            contentAlignment = Alignment.Center
        ) { ProbeCard("RenderEffect") }
        ProbeStage.Morph -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProbeCard("Anchored Morph")
        }
    }
}

@Composable
private fun ProbeCard(label: String) {
    Box(
        Modifier
            .size(240.dp, 96.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF4060B4)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White)
    }
}
