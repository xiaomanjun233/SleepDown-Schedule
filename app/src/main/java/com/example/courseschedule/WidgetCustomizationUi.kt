package com.example.courseschedule

import android.appwidget.AppWidgetManager
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

private data class WidgetEditRequest(
    val appearance: WidgetAppearanceEntity,
    val sourceUri: Uri?,
    val sourceBounds: Rect
)

@Composable
fun WidgetCustomizationScreen(
    state: AppState,
    backdrop: Backdrop?,
    onEditorVisibilityChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as CourseScheduleApp
    val repository = app.widgetAppearanceRepository
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val latestEditorVisibilityChange = rememberUpdatedState(onEditorVisibilityChange)
    var appearances by remember { mutableStateOf<List<WidgetAppearanceEntity>>(emptyList()) }
    var selectedType by remember { mutableStateOf(WidgetAppearanceVariant.COURSES_LARGE) }
    var editRequest by remember { mutableStateOf<WidgetEditRequest?>(null) }
    var editorSourceHandedOff by remember { mutableStateOf(false) }
    var currentPreviewBounds by remember { mutableStateOf(Rect.Zero) }
    val topPadding = detailContentTopPadding()

    fun installedIds(type: WidgetAppearanceVariant): IntArray {
        val provider = when (type) {
            WidgetAppearanceVariant.COURSES_LARGE -> TodayCoursesWidgetProvider::class.java
            WidgetAppearanceVariant.COURSES_SQUARE -> TodayCoursesSquareWidgetProvider::class.java
            WidgetAppearanceVariant.TODAY_ASSISTANT -> TodayAssistantWidgetProvider::class.java
        }
        return manager.getAppWidgetIds(ComponentName(context, provider))
    }
    val pagerState = rememberPagerState { WidgetAppearanceVariant.entries.size }

    suspend fun reload() {
        appearances = repository.all()
    }

    fun refresh(type: WidgetAppearanceVariant) {
        val ids = installedIds(type)
        when (type) {
            WidgetAppearanceVariant.COURSES_LARGE ->
                MiuixTodayWidgetRenderer.refresh(context, manager, ids, TodayWidgetVariant.LARGE)
            WidgetAppearanceVariant.COURSES_SQUARE ->
                MiuixTodayWidgetRenderer.refresh(context, manager, ids, TodayWidgetVariant.SQUARE)
            WidgetAppearanceVariant.TODAY_ASSISTANT ->
                TodayAssistantWidgetRenderer.refresh(context, manager, ids)
        }
    }

    fun save(next: WidgetAppearanceEntity) {
        appearances = appearances.filterNot {
            it.variant == next.variant && it.appWidgetId == next.appWidgetId
        } + next
        scope.launch {
            repository.save(next)
            reload()
            refresh(next.type)
        }
    }

    fun preview(next: WidgetAppearanceEntity) {
        appearances = appearances.filterNot {
            it.variant == next.variant && it.appWidgetId == next.appWidgetId
        } + next
    }

    fun latestAppearance(type: WidgetAppearanceVariant, appWidgetId: Int): WidgetAppearanceEntity =
        appearances.firstOrNull {
            it.variant == type.key && it.appWidgetId == appWidgetId
        } ?: WidgetAppearanceEntity.defaults(type, appWidgetId)

    LaunchedEffect(Unit) {
        WidgetAppearanceVariant.entries.forEach { repository.reconcile(it, installedIds(it)) }
        reload()
    }
    LaunchedEffect(editRequest != null) {
        latestEditorVisibilityChange.value(editRequest != null)
    }
    DisposableEffect(Unit) {
        onDispose { latestEditorVisibilityChange.value(false) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest {
            selectedType = WidgetAppearanceVariant.entries[it]
        }
    }

    val currentId = WidgetDefaultAppearanceId
    val current = appearances.firstOrNull {
        it.variant == selectedType.key && it.appWidgetId == currentId
    } ?: WidgetAppearanceEntity.defaults(selectedType, currentId)
    val darkPage = appUsesDarkTheme(state.config)
    val hasWallpaper = current.wallpaperUri != null

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = DockScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = maxWidth * 0.84f
                        HorizontalPager(
                            state = pagerState,
                            pageSize = PageSize.Fixed(cardWidth),
                            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                            pageSpacing = 4.dp,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxWidth().height(208.dp)
                        ) { page ->
                            val type = WidgetAppearanceVariant.entries[page]
                            val appearance = appearances.firstOrNull {
                                it.variant == type.key && it.appWidgetId == WidgetDefaultAppearanceId
                            } ?: WidgetAppearanceEntity.defaults(type)
                            val relative = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                            val offset = abs(relative).coerceAtMost(1f)
                            val scale = 1f - offset * 0.07f
                            Column(
                                Modifier
                                    .width(cardWidth)
                                    .height(208.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 1f - offset * 0.08f
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (!editorSourceHandedOff || page != pagerState.currentPage) {
                                    WidgetRemoteViewsPreview(
                                        type,
                                        appearance,
                                        state,
                                        onBoundsChanged = {
                                            if (page == pagerState.currentPage) currentPreviewBounds = it
                                        }
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(
                                                if (type == WidgetAppearanceVariant.COURSES_SQUARE) 0.50f else 1f
                                            )
                                            .aspectRatio(type.canonicalAspect)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(type.displayName, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    ProjectPagerIndicator(
                        pagerState = pagerState,
                        pageCount = WidgetAppearanceVariant.entries.size
                    )
                    LiquidMenuButton(
                        backdrop = backdrop,
                        label = "自定义背景",
                        onClick = {
                            editorSourceHandedOff = false
                            editRequest = WidgetEditRequest(
                                current,
                                current.wallpaperUri?.let(Uri::parse),
                                currentPreviewBounds
                            )
                        },
                        modifier = Modifier.width(132.dp),
                        textColorOverride = if (darkPage) Color.White else Color.Black,
                        surfaceColorOverride = if (darkPage) {
                            Color(0xFF4A4A4F).copy(alpha = 0.72f)
                        } else {
                            Color.White.copy(alpha = 0.74f)
                        }
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                GlassPreferenceSection("当前组件") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "使用自定义背景",
                        subtitle = if (current.wallpaperUri == null) "先选择一张图片" else "关闭不会删除图片和取景",
                        checked = current.enabled,
                        backdrop = backdrop,
                        enabled = current.wallpaperUri != null,
                        onCheckedChange = { save(current.copy(enabled = it)) }
                    )
                    SettingsDivider()
                    WidgetSliderRow(
                        title = "壁纸模糊度",
                        valueText = "${(current.blurDp * 10f).toInt()}%",
                        value = current.blurDp / 10f,
                        range = 0f..1f,
                        backdrop = backdrop,
                        enabled = hasWallpaper,
                        onLive = { value ->
                            preview(latestAppearance(selectedType, currentId).copy(blurDp = value * 10f))
                        },
                        onCommit = {
                            save(latestAppearance(selectedType, currentId).copy(blurDp = it * 10f))
                        }
                    )
                    SettingsDivider()
                    WidgetSliderRow(
                        title = "壁纸亮度",
                        valueText = "${(current.brightness * 100).toInt()}%",
                        value = current.brightness,
                        range = 0.35f..1f,
                        backdrop = backdrop,
                        enabled = hasWallpaper,
                        onLive = { value ->
                            preview(latestAppearance(selectedType, currentId).copy(brightness = value))
                        },
                        onCommit = {
                            save(latestAppearance(selectedType, currentId).copy(brightness = it))
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "恢复默认",
                        subtitle = "清除当前组件类型的背景",
                        buttonText = "恢复",
                        iconRes = R.drawable.ic_download,
                        backdrop = backdrop,
                        onClick = {
                            scope.launch {
                                repository.reset(selectedType, currentId)
                                reload()
                                refresh(selectedType)
                            }
                        }
                    )
                }
                }
                }
            }
        }

        editRequest?.let { request ->
            WidgetWallpaperEditor(
                request = request,
                state = state,
                config = state.config,
                backdrop = backdrop,
                onSourceHandoff = { editorSourceHandedOff = true },
                onSourceRestore = { editorSourceHandedOff = false },
                onCancel = {
                    editRequest = null
                },
                onSave = { draft, pickedUri, cleared ->
                    scope.launch {
                        if (cleared) {
                            repository.save(
                                draft.copy(
                                    enabled = false,
                                    wallpaperUri = null,
                                    sourceWidth = null,
                                    sourceHeight = null,
                                    centerX = 0.5f,
                                    centerY = 0.5f,
                                    scale = 1f
                                )
                            )
                            reload()
                            refresh(draft.type)
                            editorSourceHandedOff = false
                            editRequest = null
                            return@launch
                        }
                        val savedUri = withContext(Dispatchers.IO) {
                            if (pickedUri != null && pickedUri.toString() != request.appearance.wallpaperUri) {
                                repository.persistSelectedImage(pickedUri)
                            } else pickedUri
                        }
                        val source = savedUri ?: return@launch
                        repository.save(draft.copy(wallpaperUri = source.toString(), enabled = true))
                        reload()
                        refresh(draft.type)
                        editorSourceHandedOff = false
                        editRequest = null
                    }
                }
            )
        }
    }
}

@Composable
private fun WidgetSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    enabled: Boolean = true,
    onLive: (Float) -> Unit,
    onCommit: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.36f }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, color = MaterialTheme.colorScheme.primary)
        }
        if (backdrop != null) {
            Box(Modifier.fillMaxWidth()) {
                LiquidSlider(
                    value = { value },
                    onPreviewValueChange = onLive,
                    onPreviewModeChange = {},
                    onCommit = onCommit,
                    valueRange = range,
                    visibilityThreshold = 0.005f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!enabled) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .zIndex(2f)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }
            }
        } else {
            LiquidControlSlider(
                value = value,
                onValueChange = onCommit,
                onLiveValueChange = onLive,
                valueRange = range,
                backdrop = null,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WidgetRemoteViewsPreview(
    type: WidgetAppearanceVariant,
    appearance: WidgetAppearanceEntity,
    state: AppState,
    fillFraction: Float = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 0.50f else 1f,
    transparentBackground: Boolean = false,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit = {},
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val renderSize = remember(type) {
        if (type == WidgetAppearanceVariant.COURSES_SQUARE) WidgetRenderSize(168, 168)
        else WidgetRenderSize(336, 168)
    }
    var remoteViews by remember { mutableStateOf<RemoteViews?>(null) }
    var previewBackground by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(type, appearance, state, transparentBackground) {
        val rendered = withContext(Dispatchers.IO) {
            val zone = ZoneId.of("Asia/Shanghai")
            val today = LocalDate.now(zone)
            val now = LocalTime.now(zone)
            val targetDate = if (now >= LocalTime.of(22, 0)) today.plusDays(1) else today
            val courseLimit = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 2 else 4
            val courseCount = if (type == WidgetAppearanceVariant.TODAY_ASSISTANT) {
                0
            } else {
                MiuixTodayWidgetRenderer.coursesForDate(state, targetDate)
                    .count {
                        targetDate != today ||
                            courseEndTime(it, state.periods)?.isAfter(now) != false
                    }
                    .coerceAtMost(courseLimit)
            }
            val custom = WidgetBackgroundRenderer.render(
                context = context.applicationContext,
                appearance = appearance,
                size = renderSize,
                courseCount = courseCount,
                darkMode = MiuixTodayWidgetRenderer.usesDarkTheme(
                    context.applicationContext,
                    state.config
                )
            )
            val views = when (type) {
                WidgetAppearanceVariant.COURSES_LARGE ->
                    MiuixTodayWidgetRenderer.buildViews(
                        context.applicationContext,
                        state,
                        TodayWidgetVariant.LARGE,
                        appearance,
                        renderSize
                    )
                WidgetAppearanceVariant.COURSES_SQUARE ->
                    MiuixTodayWidgetRenderer.buildViews(
                        context.applicationContext,
                        state,
                        TodayWidgetVariant.SQUARE,
                        appearance,
                        renderSize
                    )
                WidgetAppearanceVariant.TODAY_ASSISTANT -> {
                    val weather = if (DayAgentPreferences.isWeatherEnabled(context)) {
                        DayAgentWeatherRepository(context.applicationContext).getWeather()
                    } else null
                    TodayAssistantWidgetRenderer.buildViews(
                        context.applicationContext,
                        state,
                        weather,
                        appearance,
                        renderSize
                    )
                }
            }
            if (transparentBackground || custom != null) {
                val root = if (type == WidgetAppearanceVariant.TODAY_ASSISTANT) {
                    R.id.widget_agent_root
                } else {
                    R.id.widget_root
                }
                val background = if (type == WidgetAppearanceVariant.TODAY_ASSISTANT) {
                    R.id.widget_agent_background_image
                } else {
                    R.id.widget_background_image
                }
                views.setInt(root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                views.setViewVisibility(background, android.view.View.GONE)
            }
            views to custom?.bitmap
        }
        remoteViews = rendered.first
        previewBackground = if (transparentBackground) null else rendered.second
        onReady()
    }
    val density = LocalDensity.current
    val logicalCornerRadius = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 16.dp else 18.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(fillFraction)
            .aspectRatio(type.canonicalAspect)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .clip(RoundedCornerShape(logicalCornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        previewBackground?.let { background ->
            Image(
                bitmap = background.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        val scale = with(density) {
            (maxWidth.toPx() / renderSize.widthDp.dp.toPx()).coerceAtLeast(0.1f)
        }
        AndroidView(
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(android.graphics.Color.TRANSPARENT)
                        cornerRadius =
                            logicalCornerRadius.value * viewContext.resources.displayMetrics.density
                    }
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                    clipChildren = true
                }
            },
            update = { host ->
                val remote = remoteViews ?: return@AndroidView
                if (host.tag !== remote) {
                    runCatching {
                        val view = remote.apply(host.context, host)
                        host.removeAllViews()
                        host.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        host.tag = remote
                    }
                }
            },
            modifier = Modifier
                .requiredSize(renderSize.widthDp.dp, renderSize.heightDp.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

@Composable
private fun WidgetWallpaperEditor(
    request: WidgetEditRequest,
    state: AppState,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onSourceHandoff: () -> Unit,
    onSourceRestore: () -> Unit,
    onCancel: () -> Unit,
    onSave: (WidgetAppearanceEntity, Uri?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember(request) { mutableStateOf(request.sourceUri) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var sourceSize by remember(uri) { mutableStateOf<WallpaperSourceSize?>(null) }
    var cleared by remember(request) { mutableStateOf(request.sourceUri == null) }
    var crop by remember(request) {
        mutableStateOf(WallpaperCropState(request.appearance.centerX, request.appearance.centerY, request.appearance.scale))
    }
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var sourceReplicaReady by remember { mutableStateOf(false) }
    var returnToEditedAppearance by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selected ->
        if (selected != null) {
            uri = selected
            cleared = false
            crop = WallpaperCropState()
        }
    }
    val morphEasing = remember { androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.72f, 0.18f, 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(460, easing = morphEasing)) }
    LaunchedEffect(sourceReplicaReady) {
        if (sourceReplicaReady) {
            androidx.compose.runtime.withFrameNanos { }
            onSourceHandoff()
        }
    }
    fun closeThen(commitEditedAppearance: Boolean = false, action: () -> Unit) {
        if (closing) return
        closing = true
        returnToEditedAppearance = commitEditedAppearance
        scope.launch {
            var sourceRestored = false
            progress.animateTo(0f, tween(400, easing = morphEasing)) {
                // Keep the real preview unmounted until the replica has nearly
                // returned home. This prevents it from appearing underneath the
                // shrinking editor too early while still leaving a few frames for
                // RemoteViews to settle before the overlay is removed.
                if (!sourceRestored && value <= 0.16f) {
                    sourceRestored = true
                    onSourceRestore()
                }
            }
            if (!sourceRestored) onSourceRestore()
            androidx.compose.runtime.withFrameNanos { }
            action()
        }
    }
    LaunchedEffect(uri) {
        val source = uri
        bitmap = if (source == null) null else withContext(Dispatchers.IO) {
            loadSampledBitmap(context.applicationContext, source, 1800)
        }
        sourceSize = if (source == null) null else withContext(Dispatchers.IO) {
            readWallpaperSourceSize(context.applicationContext, source)
        }
    }
    androidx.activity.compose.BackHandler(enabled = !closing) { closeThen(action = onCancel) }
    val p = progress.value.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(300f)
            .background(Color.Black.copy(alpha = 0.92f * p))
    ) {
        val density = LocalDensity.current
        val ratio = request.appearance.type.canonicalAspect
        val availableHeight = (maxHeight - 240.dp).coerceAtLeast(260.dp)
        val desiredWidth = minOf(
            if (request.appearance.type == WidgetAppearanceVariant.COURSES_SQUARE) maxWidth * 0.68f else maxWidth * 0.88f,
            availableHeight * ratio
        ).coerceAtLeast(210.dp)
        val desiredHeight = desiredWidth / ratio
        val validSourceBounds = request.sourceBounds.width > 8f && request.sourceBounds.height > 8f
        val startLeft = if (validSourceBounds) with(density) { request.sourceBounds.left.toDp() } else (maxWidth - desiredWidth) / 2
        val startTop = if (validSourceBounds) with(density) { request.sourceBounds.top.toDp() } else (maxHeight - desiredHeight) / 2
        val startWidth = if (validSourceBounds) with(density) { request.sourceBounds.width.toDp() } else desiredWidth
        val startHeight = if (validSourceBounds) with(density) { request.sourceBounds.height.toDp() } else desiredHeight
        val targetLeft = (maxWidth - desiredWidth) / 2
        val targetTop = (maxHeight - desiredHeight) / 2
        val cardLeft = startLeft + (targetLeft - startLeft) * p
        val cardTop = startTop + (targetTop - startTop) * p
        val cardWidth = startWidth + (desiredWidth - startWidth) * p
        val cardHeight = startHeight + (desiredHeight - startHeight) * p
        val sourceCorner = if (request.appearance.type == WidgetAppearanceVariant.COURSES_SQUARE) 16.dp else 18.dp
        val cardCorner = sourceCorner + (24.dp - sourceCorner) * p
        val editorAppearance = request.appearance.copy(
            enabled = uri != null,
            wallpaperUri = uri?.toString(),
            centerX = crop.centerX,
            centerY = crop.centerY,
            scale = crop.scale,
            sourceWidth = sourceSize?.width ?: bitmap?.width,
            sourceHeight = sourceSize?.height ?: bitmap?.height
        )
        Box(
            modifier = Modifier
                .offset(cardLeft, cardTop)
                .size(cardWidth, cardHeight)
                .graphicsLayer {
                    shadowElevation = 30.dp.toPx() * p
                    shape = RoundedCornerShape(cardCorner)
                    clip = true
                }
                .background(Color(0xFF1C1C1E).copy(alpha = p)),
            contentAlignment = Alignment.Center
        ) {
            val source = bitmap
            val editorAlpha = if (source != null) {
                ((p - 0.16f) / 0.36f).coerceIn(0f, 1f)
            } else {
                ((p - 0.34f) / 0.30f).coerceIn(0f, 1f)
            }
            WidgetRemoteViewsPreview(
                type = request.appearance.type,
                appearance = if (returnToEditedAppearance) editorAppearance else request.appearance,
                state = state,
                fillFraction = 1f,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - editorAlpha
                },
                onReady = { sourceReplicaReady = true }
            )
            if (source != null) {
                WallpaperGestureCanvas(
                    bitmap = source,
                    cropState = crop,
                    enabled = false,
                    onCropChange = { crop = it },
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = editorAlpha }
                )
                WidgetRemoteViewsPreview(
                    type = request.appearance.type,
                    // Keep the background transparent, but render the foreground with
                    // the same sampled colors that the actual widget will use.
                    appearance = editorAppearance,
                    state = state,
                    fillFraction = 1f,
                    transparentBackground = true,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = 0.72f * editorAlpha
                    }
                )
                WallpaperGestureCanvas(
                    bitmap = source,
                    cropState = crop,
                    enabled = p > 0.97f && !closing,
                    onCropChange = { crop = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.001f * editorAlpha }
                )
            } else {
                WidgetRemoteViewsPreview(
                    type = request.appearance.type,
                    appearance = request.appearance.copy(enabled = false, wallpaperUri = null),
                    state = state,
                    fillFraction = 1f,
                    transparentBackground = true,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = 0.72f * editorAlpha
                    }
                )
                Text(
                    "请选择一张图片",
                    color = Color.White.copy(alpha = 0.66f * editorAlpha)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .height(92.dp)
                .graphicsLayer { alpha = ((p - 0.5f) / 0.34f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            WallpaperHeaderButton("取消", backdrop, Color(0xFF4A4A4F).copy(alpha = 0.78f), !closing) {
                closeThen(action = onCancel)
            }
            Spacer(Modifier.weight(1f))
            Text("调整组件背景", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            WallpaperHeaderButton("完成", backdrop, Color(0xFF0A84FF).copy(alpha = 0.84f), !closing) {
                val source = uri
                closeThen(commitEditedAppearance = true) {
                    onSave(
                        request.appearance.copy(
                            centerX = crop.centerX,
                            centerY = crop.centerY,
                            scale = crop.scale,
                            sourceWidth = sourceSize?.width ?: bitmap?.width,
                            sourceHeight = sourceSize?.height ?: bitmap?.height
                        ),
                        source,
                        cleared
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .graphicsLayer { alpha = ((p - 0.64f) / 0.28f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                WidgetWallpaperRoundAction("选择图片", R.drawable.ic_image_attachment, false, backdrop) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                WidgetWallpaperRoundAction("清除壁纸", R.drawable.ic_trash, true, backdrop) {
                    uri = null
                    bitmap = null
                    sourceSize = null
                    crop = WallpaperCropState()
                    cleared = true
                }
            }
            Text("双指缩放 · 拖动调整", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WidgetWallpaperRoundAction(
    label: String,
    iconRes: Int,
    destructive: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit
) {
    val surface = if (destructive) Color(0xFFFF453A).copy(alpha = 0.82f) else Color(0xFF0A84FF).copy(alpha = 0.86f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (backdrop != null) {
            LiquidButton(
                onClick = onClick,
                backdrop = backdrop,
                modifier = Modifier.size(56.dp),
                height = 56.dp,
                contentPadding = PaddingValues(0.dp),
                surfaceColor = surface,
                blurRadius = 10.dp,
                lensHeight = 32.dp,
                lensAmount = 46.dp
            ) {
                Icon(painterResource(iconRes), contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        } else {
            Surface(
                onClick = onClick,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(50),
                color = surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(iconRes), contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelMedium)
    }
}
