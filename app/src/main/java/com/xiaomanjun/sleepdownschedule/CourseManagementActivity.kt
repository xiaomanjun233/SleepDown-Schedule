package com.xiaomanjun.sleepdownschedule

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.domain.course.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.course.management.*
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import java.util.concurrent.atomic.AtomicBoolean

open class CourseManagementActivity : ComponentActivity() {
    private var stateHandoffToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        stateHandoffToken = intent.courseManagementInitialStateTokenOrNull()
        val initialState = CourseManagementStateHandoffStore.get(stateHandoffToken)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val liveState by viewModel.state.collectAsStateWithLifecycle()
            val state = stableCourseManagementState(liveState, initialState)
            CourseScheduleTheme(config = state.config) {
                CourseManagementColorProvider(state) {
                    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
                        Box(Modifier.fillMaxSize()) {
                            CrossActivityTransitionHost(
                                activity = this@CourseManagementActivity,
                                sourceContent = {
                                    HomeMenuActivitySourceFallback(
                                        config = state.config,
                                        highlightedRowIndex = 2
                                    )
                                }
                            ) { requestClose ->
                                val pageSnapshotLayer = rememberGraphicsLayer()
                                val pageSnapshotRequested = remember { AtomicBoolean(false) }
                                var pageSnapshotRequestVersion by remember { mutableStateOf(0) }
                                var pageRootPosition by remember { mutableStateOf(Offset.Zero) }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .onGloballyPositioned {
                                            pageRootPosition = it.boundsInWindow().topLeft
                                        }
                                        .drawWithContent {
                                            if (pageSnapshotRequestVersion >= 0 && pageSnapshotRequested.compareAndSet(true, false)) {
                                                pageSnapshotLayer.record {
                                                    this@drawWithContent.drawContent()
                                                }
                                            }
                                            drawContent()
                                        }
                                ) {
                                    DetailActivityScaffold(
                                        title = "课程管理",
                                        config = state.config,
                                        onBack = requestClose
                                    ) { backdrop ->
                                        val transitionDensity = LocalDensity.current
                                        CourseManagementScreen(
                                             state = state,
                                             backdrop = backdrop,
                                             onBack = requestClose,
                                            transitionActivity = this@CourseManagementActivity,
                                            captureTransitionFrame = {
                                                pageSnapshotRequested.set(true)
                                                pageSnapshotRequestVersion += 1
                                                withFrameNanos { }
                                                runCatching {
                                                    pageSnapshotLayer.toImageBitmap().asAndroidBitmap()
                                                }.getOrNull()
                                            },
                                            transitionRootPosition = pageRootPosition,
                                             onOpenCourse = {
                                                 courseId,
                                                 detailSourceBounds,
                                                snapshots,
                                                 onOpeningSourceHandoff,
                                                onSourceReleased ->
                                                val detailIntent = Intent(
                                                    this@CourseManagementActivity,
                                                    CourseManagementDetailActivity::class.java
                                                )
                                                    .putExtra(CourseManagementCourseIdExtra, courseId)
                                                    .putCourseManagementInitialState(state)
                                                val anchor = TransitionAnchorFrame(
                                                    boundsInWindow = detailSourceBounds,
                                                    bitmap = snapshots?.source,
                                                    cornerRadiusPx = with(transitionDensity) {
                                                        20.dp.toPx()
                                                    }
                                                )
                                                val launchResult = ActivityTransitionCoordinator.open(
                                                    activity = this@CourseManagementActivity,
                                                    routeId = TransitionRouteId.CourseManagementToDetail,
                                                    intent = detailIntent,
                                                    payload = TransitionPayload(
                                                        openingAnchor = anchor,
                                                        // Course detail closes to the same captured
                                                        // card frame. The Oplus backend owns a plain
                                                        // decor snapshot View for the whole session;
                                                        // business UI no longer exposes a Compose
                                                        // AndroidView as a vendor source.
                                                        returnAnchorProvider =
                                                            TransitionAnchorProvider { anchor },
                                                        backgroundBitmap = snapshots?.background,
                                                        onOpeningSourceHandoff = onOpeningSourceHandoff,
                                                        nativeSourceLeashAlphaOutOnOpen = true,
                                                        onSourceReleased = onSourceReleased
                                                    )
                                                )
                                                launchResult !is TransitionLaunchResult.Failed
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
    }

    override fun onDestroy() {
        if (isFinishing) {
            CourseManagementStateHandoffStore.remove(stateHandoffToken)
        }
        super.onDestroy()
    }
}
