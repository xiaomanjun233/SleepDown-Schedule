package com.xiaomanjun.sleepdownschedule

import android.app.Activity
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private const val CourseManagementCourseIdExtra = "course_management_course_id"
private const val CourseManagementInitialStateTokenExtra = "course_management_initial_state_token"
private val CourseManagementCardShape = RoundedCornerShape(20.dp)

/**
 * An Activity-local handoff for the already loaded schedule state.
 *
 * A fresh [ScheduleViewModel] deliberately starts with [AppState]'s safe empty value before the
 * Room flow emits. That is fine for an ordinary cold launch, but it made an anchored Activity
 * transition precompose the empty page for two frames and then visibly replace it with the real
 * course page. The bitmap snapshots only own the moving/background layers; this handoff keeps the
 * live destination content stable from its very first composition. Room remains the source of
 * truth and replaces the handoff as soon as its loaded state arrives.
 */
private object CourseManagementStateHandoffStore {
    private val nextToken = AtomicLong(1L)
    private val states = ConcurrentHashMap<Long, AppState>()

    fun put(state: AppState): Long {
        val token = nextToken.getAndIncrement()
        states[token] = state
        if (states.size > 6) {
            states.keys.sorted().dropLast(6).forEach(states::remove)
        }
        return token
    }

    fun get(token: Long?): AppState? = token?.let(states::get)

    fun remove(token: Long?) {
        token?.let(states::remove)
    }
}

internal fun Intent.putCourseManagementInitialState(state: AppState): Intent = apply {
    putExtra(CourseManagementInitialStateTokenExtra, CourseManagementStateHandoffStore.put(state))
}

private fun Intent.courseManagementInitialStateTokenOrNull(): Long? =
    getLongExtra(CourseManagementInitialStateTokenExtra, 0L).takeIf { it > 0L }

private fun stableCourseManagementState(live: AppState, initial: AppState?): AppState =
    if (!live.loaded && initial?.loaded == true) initial else live

class CourseManagementActivity : ComponentActivity() {
    private var morphSnapshotToken: Long? = null
    private var stateHandoffToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val sourceBounds = intent.anchoredSourceBoundsOrNull()
        val collapseBounds = intent.anchoredCollapseBoundsOrNull()
        morphSnapshotToken = intent.anchoredMorphSnapshotTokenOrNull()
        stateHandoffToken = intent.courseManagementInitialStateTokenOrNull()
        val transitionSnapshots = AnchoredMorphSnapshotStore.get(morphSnapshotToken)
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
                            AnchoredDetailActivityMorph(
                                 sourceBounds = sourceBounds,
                                 collapseBounds = collapseBounds,
                                 sourceCornerRadius = 30.dp,
                                 collapseCornerRadius = 21.dp,
                                 backgroundSnapshot = transitionSnapshots?.background,
                                 sourceSnapshot = transitionSnapshots?.source,
                                 collapseSnapshot = transitionSnapshots?.collapse,
                                 // Course management is the same second-level destination as
                                 // Edu import. Reuse the activity counterpart of the Home menu
                                 // destination trajectory so the two entry/exit paths stay exact.
                                 motionStyle = AnchoredDetailMotionStyle.HomeMenuDestination,
                                onFinished = { finish() },
                                sourceContent = {
                                    CourseManagementSourceMenuFallback(config = state.config)
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
                                        CourseManagementScreen(
                                            state = state,
                                            backdrop = backdrop,
                                            onBack = requestClose,
                                            captureTransitionFrame = {
                                                pageSnapshotRequested.set(true)
                                                pageSnapshotRequestVersion += 1
                                                withFrameNanos { }
                                                runCatching {
                                                    pageSnapshotLayer.toImageBitmap().asAndroidBitmap()
                                                }.getOrNull()
                                            },
                                            transitionRootPosition = pageRootPosition,
                                            onOpenCourse = { courseId, detailSourceBounds, snapshots ->
                                                val detailIntent = Intent(
                                                    this@CourseManagementActivity,
                                                    CourseManagementDetailActivity::class.java
                                                )
                                                    .putExtra(CourseManagementCourseIdExtra, courseId)
                                                    .putCourseManagementInitialState(state)
                                                    .putAnchoredSourceBounds(detailSourceBounds)
                                                if (snapshots != null) {
                                                    detailIntent.putAnchoredMorphSnapshots(snapshots)
                                                }
                                                startActivityWithAnchoredMorph(detailIntent)
                                            }
                                        )
                                    }
                                }
                            }
                            Box(Modifier.fillMaxSize().zIndex(1_000f)) {
                                top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (isFinishing) {
            AnchoredMorphSnapshotStore.remove(morphSnapshotToken)
            CourseManagementStateHandoffStore.remove(stateHandoffToken)
        }
        super.onDestroy()
    }
}

class CourseManagementDetailActivity : ComponentActivity() {
    private var morphSnapshotToken: Long? = null
    private var stateHandoffToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val courseId = intent.getLongExtra(CourseManagementCourseIdExtra, Long.MIN_VALUE)
        val sourceBounds = intent.anchoredSourceBoundsOrNull()
        morphSnapshotToken = intent.anchoredMorphSnapshotTokenOrNull()
        stateHandoffToken = intent.courseManagementInitialStateTokenOrNull()
        val transitionSnapshots = AnchoredMorphSnapshotStore.get(morphSnapshotToken)
        val initialState = CourseManagementStateHandoffStore.get(stateHandoffToken)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val liveState by viewModel.state.collectAsStateWithLifecycle()
            val state = stableCourseManagementState(liveState, initialState)
            val group = remember(state.courses, courseId) {
                managedCourseGroupForCourseId(state.courses, courseId)
            }
            CourseScheduleTheme(config = state.config) {
                CourseManagementColorProvider(state) {
                    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
                        Box(Modifier.fillMaxSize()) {
                            AnchoredDetailActivityMorph(
                                sourceBounds = sourceBounds,
                                sourceCornerRadius = 20.dp,
                                backgroundSnapshot = transitionSnapshots?.background,
                                sourceSnapshot = transitionSnapshots?.source,
                                // Independently copy the Home course-card parabola without
                                // coupling later Home editor tuning to this Activity transition.
                                motionStyle = AnchoredDetailMotionStyle.CourseManagementDetail,
                                onFinished = { finish() },
                                sourceContent = {
                                    group?.let {
                                        ManagedCourseListCardContent(
                                            group = it,
                                            config = state.config,
                                            periods = state.periods,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            ) { requestClose ->
                                if (group == null) {
                                    DetailActivityScaffold(
                                        title = "课程详情",
                                        config = state.config,
                                        onBack = requestClose
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                if (state.loaded) {
                                                    "课程记录已变更，请返回课程管理重新选择"
                                                } else {
                                                    "正在载入课程…"
                                                },
                                                color = appPanelForegroundColor(state.config).copy(alpha = 0.62f)
                                            )
                                        }
                                    }
                                } else {
                                    CourseManagementDetailPage(
                                        group = group,
                                        state = state,
                                        onBack = requestClose,
                                        onSave = { replacements ->
                                            if (replacements.isEmpty()) {
                                                viewModel.deleteCoursesAndThen(group.courses, requestClose)
                                            } else {
                                                viewModel.replaceCourseGroupAndThen(
                                                    group.courses,
                                                    replacements,
                                                    requestClose
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            Box(Modifier.fillMaxSize().zIndex(1_000f)) {
                                top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            AnchoredMorphSnapshotStore.remove(morphSnapshotToken)
            CourseManagementStateHandoffStore.remove(stateHandoffToken)
        }
        super.onDestroy()
    }
}

@Composable
private fun CourseManagementColorProvider(
    state: AppState,
    content: @Composable () -> Unit
) {
    // Course management is hosted in its own Activity, so it does not inherit the Home
    // CompositionLocal that normally carries the adaptive glass foreground.  Supplying the
    // same config-derived fallback here keeps root-hosted Miuix pickers (whose PopupHost is a
    // separate composition) on the same light/dark side as the page instead of the default
    // luminance=1 state, which renders all picker text black on a dark glass surface.
    val adaptiveGlass = rememberFallbackAdaptiveGlassState(state.config)
    val wallpaperImages by rememberHomeWallpaperImages(state.config)
    val colorSignature = remember(state.config.id, state.courses) {
        state.courses.map(::courseCardColorKey).distinct().sorted()
    }
    val assignments = remember(
        state.config.id,
        colorSignature,
        wallpaperImages.representativeColors
    ) {
        buildCourseCardColorAssignments(state.courses, wallpaperImages.representativeColors)
    }
    CompositionLocalProvider(
        LocalAdaptiveGlass provides adaptiveGlass,
        LocalCourseCardPalette provides wallpaperImages.representativeColors,
        LocalCourseCardColorAssignments provides assignments,
        content = content
    )
}

internal data class ManagedCourseGroup(
    val key: String,
    val courses: List<CourseEntity>
) {
    val representative: CourseEntity get() = courses.minBy(CourseEntity::id)
}

internal fun buildManagedCourseGroups(courses: List<CourseEntity>): List<ManagedCourseGroup> = courses
    .groupBy(::courseCardColorKey)
    .map { (key, entries) ->
        ManagedCourseGroup(
            key = key,
            courses = entries.sortedWith(
                compareBy<CourseEntity> { it.weekday }
                    .thenBy { it.customTimeRangeOrNull()?.first }
                    .thenBy { it.periods.minOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.id }
            )
        )
    }
    .sortedWith(
        compareBy<ManagedCourseGroup> { it.representative.customColorArgb ?: Long.MAX_VALUE }
            .thenBy { it.key }
    )

internal fun managedCourseGroupForCourseId(
    courses: List<CourseEntity>,
    courseId: Long
): ManagedCourseGroup? {
    val selected = courses.firstOrNull { it.id == courseId } ?: return null
    val key = courseCardColorKey(selected)
    return buildManagedCourseGroups(courses).firstOrNull { it.key == key }
}

@Composable
internal fun CourseManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    onBack: () -> Unit,
    captureTransitionFrame: suspend () -> Bitmap? = { null },
    transitionRootPosition: Offset = Offset.Zero,
    onOpenCourse: (Long, Rect, AnchoredMorphSnapshots?) -> Unit
) {
    val groups = remember(state.courses) { buildManagedCourseGroups(state.courses) }
    val foreground = appPanelForegroundColor(state.config)
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hiddenGroupKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hiddenGroupKey = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)
    Box(
        Modifier.fillMaxSize()
    ) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前课表还没有课程", color = foreground.copy(alpha = 0.62f))
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = detailContentTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                staggeredItems(groups, key = ManagedCourseGroup::key) { group ->
                    ManagedCourseListCard(
                        group = group,
                        config = state.config,
                        periods = state.periods,
                        sourceHidden = hiddenGroupKey == group.key,
                        onClick = { sourceBounds ->
                            if (hiddenGroupKey != null) return@ManagedCourseListCard
                            scope.launch {
                                val fullFrame = captureTransitionFrame()
                                val sourceSnapshot = fullFrame?.cropToCourseManagementBounds(
                                    sourceBounds,
                                    transitionRootPosition
                                )
                                val windowOverlay = (context as? Activity)?.window?.decorView?.overlay
                                val placeholder = sourceSnapshot?.let { bitmap ->
                                    BitmapDrawable(context.resources, bitmap).apply {
                                        bounds = android.graphics.Rect(
                                            sourceBounds.left.roundToInt(),
                                            sourceBounds.top.roundToInt(),
                                            sourceBounds.right.roundToInt(),
                                            sourceBounds.bottom.roundToInt()
                                        )
                                    }
                                }
                                placeholder?.let { windowOverlay?.add(it) }
                                try {
                                    hiddenGroupKey = group.key
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    val cleanBackground = captureTransitionFrame()
                                    onOpenCourse(
                                        group.representative.id,
                                        sourceBounds,
                                        cleanBackground?.let {
                                            AnchoredMorphSnapshots(
                                                background = it,
                                                source = sourceSnapshot
                                            )
                                        }
                                    )
                                    delay(HomeMenuDestinationLegacyMotion.OpenDurationMillis.toLong())
                                } finally {
                                    placeholder?.let { windowOverlay?.remove(it) }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun ManagedCourseListCard(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    sourceHidden: Boolean,
    onClick: (Rect) -> Unit
) {
    var sourceBounds by remember(group.key) { mutableStateOf<Rect?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
            .graphicsLayer { alpha = if (sourceHidden) 0f else 1f }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                sourceBounds?.let(onClick)
            }
    ) {
        ManagedCourseListCardContent(group, config, periods, Modifier.fillMaxWidth())
    }
}

private fun Bitmap.cropToCourseManagementBounds(bounds: Rect, rootPosition: Offset): Bitmap? =
    runCatching {
        val left = (bounds.left - rootPosition.x).roundToInt().coerceIn(0, width - 1)
        val top = (bounds.top - rootPosition.y).roundToInt().coerceIn(0, height - 1)
        val cropWidth = bounds.width.roundToInt().coerceIn(1, width - left)
        val cropHeight = bounds.height.roundToInt().coerceIn(1, height - top)
        Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    }.getOrNull()

@Composable
private fun ManagedCourseListCardContent(
    group: ManagedCourseGroup,
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    modifier: Modifier
) {
    val representative = group.representative
    val cardColor = courseCardBaseColor(config, representative)
    val textColor = appPanelForegroundColor(config)
    val summaryColor = textColor.copy(alpha = 0.72f)
    val showCourseColor = config.cardColorArgb == MulticolorCourseCardArgb
    val courseInfoLines = buildList {
        representative.teacher?.takeIf { it.isNotBlank() }?.let { add("教师：$it") }
        representative.location?.takeIf { it.isNotBlank() }?.let { add("地点：$it") }
    }
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                CourseManagementCardShape
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (showCourseColor) cardColor else Color.Transparent)
            )
            Text(
                representative.name,
                color = textColor,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Bold,
                softWrap = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = textColor.copy(alpha = 0.70f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
        if (courseInfoLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                courseInfoLines.forEach { info ->
                    Text(
                        text = info,
                        color = summaryColor,
                        style = MiuixTheme.textStyles.body2,
                        lineHeight = 20.sp,
                        softWrap = true
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            group.courses.forEach { arrangement ->
                Text(
                    text = "周${weekdayLabel(arrangement.weekday)} ${courseTimeLabel(arrangement, periods)}",
                    color = summaryColor,
                    style = MiuixTheme.textStyles.body2,
                    lineHeight = 20.sp,
                    softWrap = true
                )
            }
        }
    }
}

@Composable
private fun CourseManagementSourceMenuFallback(config: ScheduleConfigEntity) {
    val foreground = appPanelForegroundColor(config)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), CourseManagementCardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("日视图 / 周视图", "添加单节课", "课程管理", "手动导入课表", "教务系统导入", "课表设置")
            .forEachIndexed { index, label ->
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == 2) foreground.copy(alpha = 0.34f)
                                else Color.Transparent
                            )
                    )
                    Text(
                        label,
                        color = foreground.copy(alpha = if (index == 2) 1f else 0.74f),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
    }
}

private data class ManagedArrangementDraft(
    val localKey: Long,
    val course: CourseEntity
)

private data class PendingArrangementDelete(
    val localKey: Long,
    val arrangementNumber: Int,
    val confirm: () -> Unit,
    val cancel: () -> Unit
)

@Composable
private fun CourseManagementDetailPage(
    group: ManagedCourseGroup,
    state: AppState,
    onBack: () -> Unit,
    onSave: (List<CourseEntity>) -> Unit
) {
    var name by remember(group.key) { mutableStateOf(group.representative.name) }
    var selectedColor by remember(group.key) { mutableStateOf(group.representative.customColorArgb) }
    var nextLocalKey by remember(group.key) { mutableLongStateOf(-1L) }
    var arrangements by remember(group.key) {
        mutableStateOf(group.courses.map { ManagedArrangementDraft(it.id, it) })
    }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    var showSaveChangesDialog by remember(group.key) { mutableStateOf(false) }
    var validationMessage by remember(group.key) { mutableStateOf<String?>(null) }
    var pendingDelete by remember(group.key) { mutableStateOf<PendingArrangementDelete?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colorModeEnabled = state.config.cardColorArgb == MulticolorCourseCardArgb

    fun replacementsForSave(): List<CourseEntity> {
        val normalizedName = name.trim()
        return arrangements.map { draft ->
            draft.course.copy(
                name = normalizedName,
                customColorArgb = if (colorModeEnabled) selectedColor else draft.course.customColorArgb
            )
        }
    }

    val replacements = replacementsForSave()
    val hasChanges = name != group.representative.name || replacements != group.courses

    fun updateArrangement(localKey: Long, transform: (CourseEntity) -> CourseEntity) {
        arrangements = arrangements.map { draft ->
            if (draft.localKey == localKey) draft.copy(course = transform(draft.course)) else draft
        }
    }

    fun saveAndReturn() {
        val trimmedName = name.trim()
        if (arrangements.isNotEmpty() && trimmedName.isBlank()) {
            validationMessage = "课程名称不能为空"
            return
        }
        onSave(replacements)
    }

    fun requestBack() {
        if (hasChanges) {
            showSaveChangesDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasChanges) { requestBack() }

    DetailActivityScaffold(
        title = name.ifBlank { "课程详情" },
        config = state.config,
        onBack = ::requestBack,
        compactTopBar = true,
        centerCompactTitle = true,
        compactTitleMatchesSettings = true,
        topBarActions = { topBackdrop ->
            DialogLiquidButton(
                backdrop = topBackdrop,
                label = "添加安排",
                onClick = {
                    val template = arrangements.lastOrNull()?.course ?: group.representative
                    val firstPeriod = state.periods.firstOrNull()?.periodIndex ?: 1
                    val added = template.copy(
                        id = 0,
                        name = name.ifBlank { template.name },
                        weekday = 1,
                        periods = listOf(firstPeriod),
                        weeks = (1..state.config.totalWeeks.coerceAtLeast(1)).toList(),
                        weekParity = WeekParity.ALL,
                        customStartTime = null,
                        customEndTime = null,
                        customColorArgb = selectedColor,
                        scheduleId = state.config.id
                    )
                    arrangements = arrangements + ManagedArrangementDraft(nextLocalKey--, added)
                    scope.launch { listState.animateScrollToItem(arrangements.lastIndex + 1) }
                },
                role = DialogButtonRole.Confirm,
                iconRes = R.drawable.ic_add_course,
                roundIcon = true,
                blurRadius = 10.dp
            )
        }
    ) { cardBackdrop ->
        // DetailActivityScaffold records its scrolling content into
        // LocalSettingsPopupBackdrop. A card inside that recorder must never sample that same
        // producer: doing so creates a RenderNode cycle and crashes RenderThread with an
        // unbounded prepareTreeImpl recursion. This is the same one-way arrangement used by the
        // AI import history detail page: in-page controls sample the scaffold's plain background
        // producer, while only the root-hosted picker samples the completed content producer.
        val pickerBackdrop = LocalSettingsPopupBackdrop.current ?: cardBackdrop
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                // The centered compact title is wider/taller than the history-page title. Give
                // the first card a real clear band below it instead of letting the top-gradient
                // overlay visually crop the color heading.
                top = detailContentTopPadding() + 24.dp,
                end = 16.dp,
                bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "course-identity") {
                CourseIdentityCard(
                    name = name,
                    onNameChange = { name = it },
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it },
                    config = state.config
                )
            }
            itemsIndexed(arrangements, key = { _, item -> item.localKey }) { index, draft ->
                SwipeDeleteArrangementCard(
                    key = draft.localKey,
                    onDelete = { arrangements = arrangements.filterNot { it.localKey == draft.localKey } },
                    onDeleteRequest = { confirm, cancel ->
                        pendingDelete = PendingArrangementDelete(
                            localKey = draft.localKey,
                            arrangementNumber = index + 1,
                            confirm = confirm,
                            cancel = cancel
                        )
                    }
                ) {
                    CourseArrangementEditorCard(
                        localKey = draft.localKey,
                        index = index,
                        course = draft.course,
                        periods = state.periods,
                        totalWeeks = state.config.totalWeeks.coerceAtLeast(1),
                        config = state.config,
                        backdrop = cardBackdrop,
                        onCourseChange = { changed -> updateArrangement(draft.localKey) { changed } },
                        onOpenPicker = { pickerRequest = it }
                    )
                }
            }
            if (arrangements.isEmpty()) {
                item(key = "empty-arrangements") {
                    CourseManagementSettingsSection(title = "上课安排") {
                        Text(
                            "已移除全部安排，返回时可确认删除这门课程。",
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        pickerRequest?.let { request ->
            CourseEditorPickerOverlay(
                request = request,
                backdrop = pickerBackdrop,
                config = state.config,
                renderInRootScaffold = true,
                onDismiss = { pickerRequest = null }
            )
        }
        if (showSaveChangesDialog) {
            val canSave = arrangements.isEmpty() || name.trim().isNotBlank()
            LiquidAlertDialog(
                title = "保存修改？",
                message = validationMessage
                    ?: "你修改了课程安排。保存完成后才会退出课程详情。",
                actions = listOf(
                    LiquidAlertAction(
                        label = "继续编辑",
                        style = LiquidAlertActionStyle.Secondary,
                        onClick = {
                            validationMessage = null
                            showSaveChangesDialog = false
                        }
                    ),
                    LiquidAlertAction(
                        label = "不保存",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = onBack
                    ),
                    LiquidAlertAction(
                        label = "保存并退出",
                        style = LiquidAlertActionStyle.Primary,
                        dismissOnClick = canSave,
                        onClick = ::saveAndReturn
                    )
                ),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = {
                    validationMessage = null
                    showSaveChangesDialog = false
                }
            )
        }
        pendingDelete?.let { pending ->
            LiquidAlertDialog(
                title = "删除上课安排？",
                message = "确定删除第 ${pending.arrangementNumber} 个上课安排吗？",
                actions = listOf(
                    LiquidAlertAction(
                        label = "取消",
                        style = LiquidAlertActionStyle.Secondary,
                        onClick = {
                            pending.cancel()
                            pendingDelete = null
                        }
                    ),
                    LiquidAlertAction(
                        label = "删除",
                        style = LiquidAlertActionStyle.Destructive,
                        onClick = {
                            pendingDelete = null
                            pending.confirm()
                        }
                    )
                ),
                backdrop = pickerBackdrop,
                config = state.config,
                onDismissRequest = {
                    pending.cancel()
                    pendingDelete = null
                }
            )
        }
    }
}

@Composable
private fun CourseManagementSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CourseManagementCardShape)
            .padding(vertical = 6.dp)
    ) {
        Text(
            title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun CourseIdentityCard(
    name: String,
    onNameChange: (String) -> Unit,
    selectedColor: Long?,
    onColorSelected: (Long?) -> Unit,
    config: ScheduleConfigEntity
) {
    CourseManagementSettingsSection(title = "课程信息") {
        SettingsTextFieldRow(
            title = "课程名称",
            value = name,
            onValueChange = onNameChange,
            placeholder = "输入课程名称"
        )
        if (config.cardColorArgb == MulticolorCourseCardArgb) {
            MiuixBasicComponent(
                title = "课程颜色",
                summary = "应用到全部安排",
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            )
            val palette = LocalCourseCardPalette.current.ifEmpty { DefaultCourseCardPalette }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (selectedColor == null) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onColorSelected(null) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = "已选择自动课程颜色",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(11.dp)
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_course_color_auto),
                        contentDescription = "自动课程颜色",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                palette.take(6).forEach { argb ->
                    val selected = selectedColor == argb
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                scaleX = if (selected) 1.12f else 1f
                                scaleY = if (selected) 1.12f else 1f
                            }
                            .clip(RoundedCornerShape(50))
                            .background(Color(argb.toInt()))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        if (Color(argb.toInt()).luminance() > 0.55f) Color.Black else Color.White,
                                        RoundedCornerShape(50)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColorSelected(argb) }
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "已选择课程颜色",
                                tint = if (Color(argb.toInt()).luminance() > 0.55f) Color.Black else Color.White,
                                modifier = Modifier.align(Alignment.Center).size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseArrangementEditorCard(
    localKey: Long,
    index: Int,
    course: CourseEntity,
    periods: List<PeriodEntity>,
    totalWeeks: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onCourseChange: (CourseEntity) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit
) {
    CourseManagementSettingsSection(title = "上课安排 ${index + 1}") {
        CourseManagementArrowRow(
            title = "上课星期",
            value = "周${weekdayLabel(course.weekday)}",
            onClick = {
                onOpenPicker(
                    CourseEditorPickerRequest.Wheel(
                        title = "选择上课星期",
                        labels = (1..7).map { "周${weekdayLabel(it)}" },
                        startIndex = (course.weekday - 1).coerceIn(0, 6),
                        onConfirm = { selected, _ ->
                            onCourseChange(course.copy(weekday = selected + 1))
                        }
                    )
                )
            }
        )
        CourseManagementArrowRow(
            title = "上课节次",
            value = if (course.hasCustomTime()) courseTimeLabel(course, periods)
            else "第${course.periods.minOrNull() ?: 1}-${course.periods.maxOrNull() ?: 1}节",
            onClick = {
                val values = periods.map(PeriodEntity::periodIndex).distinct().sorted().ifEmpty { listOf(1) }
                val startIndex = values.indexOf(course.periods.minOrNull()).coerceAtLeast(0)
                val endIndex = values.indexOf(course.periods.maxOrNull()).coerceAtLeast(startIndex)
                onOpenPicker(
                    CourseEditorPickerRequest.Period(
                        title = "选择节次",
                        labels = values.map { "第${it}节" },
                        startIndex = startIndex,
                        endIndex = endIndex,
                        endIndexUpperBound = values.lastIndex,
                        customStartTime = course.customStartTime,
                        customEndTime = course.customEndTime,
                        regularStartTime = periods.firstOrNull { it.periodIndex == values[startIndex] }?.startTime,
                        regularEndTime = periods.firstOrNull { it.periodIndex == values[endIndex] }?.endTime,
                        onConfirmPeriods = { start, end ->
                            onCourseChange(
                                course.copy(
                                    periods = values.subList(start, end + 1),
                                    customStartTime = null,
                                    customEndTime = null
                                )
                            )
                        },
                        onConfirmCustomTime = { startText, endText ->
                            val start = LocalTime.parse(startText)
                            val end = LocalTime.parse(endText)
                            val anchors = courseAnchorPeriodsForTimeRange(start, end, periods).ifEmpty { course.periods }
                            onCourseChange(
                                course.copy(
                                    periods = anchors,
                                    customStartTime = startText,
                                    customEndTime = endText
                                )
                            )
                        }
                    )
                )
            }
        )
        SettingsTextFieldRow(
            title = "地点",
            value = course.location.orEmpty(),
            onValueChange = { onCourseChange(course.copy(location = it.ifBlank { null })) },
            placeholder = "未填写"
        )
        SettingsTextFieldRow(
            title = "教师",
            value = course.teacher.orEmpty(),
            onValueChange = { onCourseChange(course.copy(teacher = it.ifBlank { null })) },
            placeholder = "未填写"
        )
        CourseManagementWeekFold(
            localKey = localKey,
            course = course,
            totalWeeks = totalWeeks,
            onCourseChange = onCourseChange
        )
        SettingsTextFieldRow(
            title = "备注",
            value = course.note.orEmpty(),
            onValueChange = { onCourseChange(course.copy(note = it.ifBlank { null })) },
            placeholder = "未填写"
        )
    }
}

@Composable
private fun CourseManagementArrowRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    MiuixBasicComponent(
        title = title,
        summary = value,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        endActions = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }
            )
        },
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseManagementWeekFold(
    localKey: Long,
    course: CourseEntity,
    totalWeeks: Int,
    onCourseChange: (CourseEntity) -> Unit
) {
    var expanded by remember(localKey, totalWeeks) { mutableStateOf(false) }
    val expansionEasing = remember { CubicBezierEasing(0.20f, 0f, 0f, 1f) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 180f,
        animationSpec = tween(280, easing = expansionEasing),
        label = "course-management-week-arrow-$localKey"
    )
    val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
    val selectedWeeks = course.weeks.toSet()
    val selectedMode = inferCourseWeekSelectionMode(selectedWeeks)
    // Miuix's BasicComponent only measures its preference row. The expanding body must be a
    // sibling in this Column, otherwise the grid looks present but is clipped and cannot receive
    // touch events on certain devices.
    Column(Modifier.fillMaxWidth()) {
        MiuixBasicComponent(
            title = "上课周次",
            summary = compactWeekSelectionLabel(course.weeks),
            modifier = Modifier.fillMaxWidth(),
             insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "收起周次" else "展开周次",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation }
                )
            },
            onClick = { expanded = !expanded }
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(320, easing = expansionEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(190, delayMillis = 30)),
            exit = shrinkVertically(
                animationSpec = tween(240, easing = expansionEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(150))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CourseWeekSelectionMode.EVERY to "全部",
                        CourseWeekSelectionMode.ODD to "单周",
                        CourseWeekSelectionMode.EVEN to "双周"
                    ).forEach { (mode, label) ->
                        val selected = selectedMode == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val weeks = weeksForCourseWeekSelectionMode(
                                        mode = mode,
                                        currentWeeks = selectedWeeks,
                                        totalWeeks = safeTotalWeeks
                                    )
                                    onCourseChange(
                                        course.copy(
                                            weeks = weeks.sorted(),
                                            weekParity = mode.toWeekParity()
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 6
                ) {
                    (1..safeTotalWeeks).forEach { week ->
                        val selected = week in selectedWeeks
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 38.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (selected && selectedWeeks.size == 1) return@clickable
                                    val next = if (selected) selectedWeeks - week else selectedWeeks + week
                                    val sorted = next.sorted()
                                    onCourseChange(
                                        course.copy(
                                            weeks = sorted,
                                            weekParity = inferCourseWeekSelectionMode(sorted).toWeekParity()
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                week.toString(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeDeleteArrangementCard(
    key: Long,
    onDeleteRequest: (confirm: () -> Unit, cancel: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(key) { Animatable(0f) }
    val removal = remember(key) { Animatable(0f) }
    var revealThresholdCrossed by remember(key) { mutableStateOf(false) }
    var deleteThresholdCrossed by remember(key) { mutableStateOf(false) }
    var deleteRequested by remember(key) { mutableStateOf(false) }
    var deletionStarted by remember(key) { mutableStateOf(false) }
    var widthPx by remember(key) { mutableFloatStateOf(1f) }
    var measuredHeightPx by remember(key) { mutableFloatStateOf(0f) }
    val actionWidthPx = with(density) { 48.dp.toPx() }
    val actionEndPaddingPx = with(density) { 12.dp.toPx() }
    val actionGapPx = with(density) { 22.dp.toPx() }
    val revealPx = actionWidthPx + actionEndPaddingPx + actionGapPx
    val deleteTriggerPx = max(
        revealPx + with(density) { 156.dp.toPx() },
        widthPx * 0.78f
    ).coerceAtMost(widthPx * 0.88f)
    val maximumDragPx = (widthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(revealPx)
    val settleSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 420f)

    fun confirmDelete() {
        if (deletionStarted) return
        deletionStarted = true
        scope.launch {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.coroutineScope {
                launch { offset.animateTo(-widthPx * 1.08f, tween(300, easing = DetailExitEasing)) }
                launch { removal.animateTo(1f, tween(360, easing = DetailExitEasing)) }
            }
            onDelete()
        }
    }

    fun cancelDelete() {
        if (deletionStarted) return
        deleteRequested = false
        revealThresholdCrossed = false
        deleteThresholdCrossed = false
        scope.launch { offset.animateTo(0f, settleSpring) }
    }

    fun requestDelete(performHaptic: Boolean = true) {
        if (deletionStarted || deleteRequested) return
        deleteRequested = true
        if (performHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            if (abs(offset.value) < deleteTriggerPx) {
                offset.animateTo(-deleteTriggerPx, tween(180, easing = DetailExitEasing))
            }
            onDeleteRequest(::confirmDelete, ::cancelDelete)
        }
    }

    val collapseProgress = ((removal.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
    val dragDistance = (-offset.value).coerceAtLeast(0f)
    val removalStretch = (removal.value / 0.42f).coerceIn(0f, 1f)
    val revealProgress = (dragDistance / revealPx).coerceIn(0f, 1f)
    val measuredHeight = with(density) { measuredHeightPx.toDp() }
    val collapsedHeight = with(density) {
        (measuredHeightPx * (1f - collapseProgress)).coerceAtLeast(1f).toDp()
    }
    val outerHeightModifier = if (measuredHeightPx > 0f && removal.value > 0.001f) {
        Modifier.height(collapsedHeight)
    } else {
        Modifier
    }

    Box(
        Modifier.fillMaxWidth()
            .then(outerHeightModifier)
            .onSizeChanged { size ->
                widthPx = size.width.toFloat().coerceAtLeast(1f)
                if (removal.value <= 0.01f && size.height > 1) {
                    measuredHeightPx = max(measuredHeightPx, size.height.toFloat())
                }
            }
    ) {
        val deleteModifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp)
            .size(48.dp)
            .graphicsLayer {
                val vanish = ((removal.value - 0.66f) / 0.30f).coerceIn(0f, 1f)
                alpha = max(revealProgress, removalStretch) * (1f - vanish)
                val revealScale = 0.64f + 0.36f * max(revealProgress, removalStretch)
                scaleX = revealScale * (1f - 0.24f * vanish)
                scaleY = revealScale * (1f - 0.24f * vanish)
                transformOrigin = TransformOrigin.Center
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = platformBlurRenderEffect(10.dp.toPx() * vanish)
            }
        Box(
            deleteModifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFF3B30))
                .clickable(onClick = ::requestDelete),
            contentAlignment = Alignment.Center
        ) {
            val iconVanish = ((removal.value - 0.72f) / 0.24f).coerceIn(0f, 1f)
            Image(
                painter = painterResource(R.drawable.ic_delete_history),
                contentDescription = "删除安排",
                modifier = Modifier.size(20.dp).graphicsLayer {
                    alpha = 1f - iconVanish
                    scaleX = 1f - 0.22f * iconVanish
                    scaleY = 1f - 0.22f * iconVanish
                }
            )
        }
    val foregroundHeightModifier = if (measuredHeightPx > 0f && removal.value > 0.001f) {
        Modifier.height(measuredHeight)
    } else {
        Modifier
    }
        Box(
            Modifier.fillMaxWidth()
                .then(foregroundHeightModifier)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(key, widthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = drag@{ change, amount ->
                            change.consume()
                            if (deletionStarted || deleteRequested) return@drag
                            val next = (offset.value + amount).coerceIn(
                                -maximumDragPx,
                                with(density) { 8.dp.toPx() }
                            )
                            val revealCrossed = abs(next) >= revealPx * 0.48f
                            if (revealCrossed != revealThresholdCrossed) {
                                revealThresholdCrossed = revealCrossed
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { offset.snapTo(next) }
                            val deleteCrossed = abs(next) >= deleteTriggerPx
                            if (deleteCrossed && !deleteThresholdCrossed) {
                                deleteThresholdCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                requestDelete(performHaptic = false)
                            }
                        },
                        onDragEnd = dragEnd@{
                            if (deletionStarted || deleteRequested) return@dragEnd
                            scope.launch {
                                val target = if (abs(offset.value) >= revealPx * 0.48f) -revealPx else 0f
                                revealThresholdCrossed = target < 0f
                                deleteThresholdCrossed = false
                                offset.animateTo(target, settleSpring)
                            }
                        },
                        onDragCancel = dragCancel@{
                            if (deletionStarted) return@dragCancel
                            revealThresholdCrossed = false
                            deleteThresholdCrossed = false
                            scope.launch { offset.animateTo(0f, settleSpring) }
                        }
                    )
                },
            content = content
        )
    }
}
