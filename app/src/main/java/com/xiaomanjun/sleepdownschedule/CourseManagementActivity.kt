package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.domain.course.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.course.management.*

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme


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
