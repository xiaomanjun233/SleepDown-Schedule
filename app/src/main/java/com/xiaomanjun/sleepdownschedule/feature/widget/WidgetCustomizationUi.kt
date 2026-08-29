package com.xiaomanjun.sleepdownschedule.feature.widget

import com.xiaomanjun.sleepdownschedule.feature.widget.providers.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import android.view.ViewGroup
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import top.yukonga.miuix.kmp.squircle.squircleClip

private data class WidgetEditRequest(
    val appearance: WidgetAppearanceEntity,
    val sourceUri: Uri?,
    val sourceBounds: Rect
)

internal fun widgetAppearanceForType(
    appearances: List<WidgetAppearanceEntity>,
    type: WidgetAppearanceVariant,
    appWidgetId: Int = WidgetDefaultAppearanceId
): WidgetAppearanceEntity = appearances.firstOrNull {
    it.variant == type.key && it.appWidgetId == appWidgetId
} ?: WidgetAppearanceEntity.defaults(type, appWidgetId)

internal fun updateWidgetDefaultAppearance(
    current: WidgetAppearanceEntity,
    type: WidgetAppearanceVariant,
    transform: (WidgetAppearanceEntity) -> WidgetAppearanceEntity
): WidgetAppearanceEntity = transform(current).copy(
    variant = type.key,
    appWidgetId = WidgetDefaultAppearanceId
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
    var editRequest by remember { mutableStateOf<WidgetEditRequest?>(null) }
    var editorSourceHandedOff by remember { mutableStateOf(false) }
    var currentPreviewBounds by remember { mutableStateOf(Rect.Zero) }
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    val topPadding = detailContentTopPadding() +
        if (adaptiveMetrics.isLargeScreen) 18.dp else 0.dp

    fun installedIds(type: WidgetAppearanceVariant): IntArray {
        val provider = when (type) {
            WidgetAppearanceVariant.COURSES_LARGE -> TodayCoursesWidgetProvider::class.java
            WidgetAppearanceVariant.COURSES_SQUARE -> TodayCoursesSquareWidgetProvider::class.java
            WidgetAppearanceVariant.TODAY_TOMORROW -> TodayTomorrowWidgetProvider::class.java
            WidgetAppearanceVariant.WEEK_SCHEDULE -> WeekScheduleWidgetProvider::class.java
            WidgetAppearanceVariant.TODAY_ASSISTANT -> TodayAssistantWidgetProvider::class.java
        }
        return manager.getAppWidgetIds(ComponentName(context, provider))
    }
    val widgetTypes = ActiveWidgetAppearanceVariants
    val pagerState = rememberPagerState { widgetTypes.size }
    val selectedPage by remember(pagerState) {
        derivedStateOf { pagerState.settledPage.coerceIn(widgetTypes.indices) }
    }
    val selectedType = widgetTypes[selectedPage]

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
            WidgetAppearanceVariant.TODAY_TOMORROW ->
                TodayTomorrowWidgetRenderer.refresh(context, manager, ids)
            WidgetAppearanceVariant.WEEK_SCHEDULE ->
                WeekScheduleWidgetRenderer.refresh(context, manager, ids)
            WidgetAppearanceVariant.TODAY_ASSISTANT ->
                TodayAssistantWidgetRenderer.refresh(context, manager, ids)
        }
    }

    fun latestAppearance(type: WidgetAppearanceVariant, appWidgetId: Int): WidgetAppearanceEntity =
        appearances.firstOrNull {
            it.variant == type.key && it.appWidgetId == appWidgetId
        } ?: WidgetAppearanceEntity.defaults(type, appWidgetId)

    fun saveDefault(
        type: WidgetAppearanceVariant,
        transform: (WidgetAppearanceEntity) -> WidgetAppearanceEntity
    ) {
        val next = updateWidgetDefaultAppearance(
            current = latestAppearance(type, WidgetDefaultAppearanceId),
            type = type,
            transform = transform
        )
        appearances = appearances.filterNot {
            it.variant == next.variant && it.appWidgetId == next.appWidgetId
        } + next
        scope.launch {
            repository.save(next)
            reload()
            refresh(next.type)
        }
    }

    fun previewDefault(
        type: WidgetAppearanceVariant,
        transform: (WidgetAppearanceEntity) -> WidgetAppearanceEntity
    ) {
        val next = updateWidgetDefaultAppearance(
            current = latestAppearance(type, WidgetDefaultAppearanceId),
            type = type,
            transform = transform
        )
        appearances = appearances.filterNot {
            it.variant == next.variant && it.appWidgetId == next.appWidgetId
        } + next
    }

    LaunchedEffect(Unit) {
        widgetTypes.forEach { repository.reconcile(it, installedIds(it)) }
        reload()
    }
    LaunchedEffect(editRequest != null) {
        latestEditorVisibilityChange.value(editRequest != null)
    }
    DisposableEffect(Unit) {
        onDispose { latestEditorVisibilityChange.value(false) }
    }
    val currentId = WidgetDefaultAppearanceId
    val current = widgetAppearanceForType(appearances, selectedType, currentId)
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
                        val cardWidth = if (adaptiveMetrics.isLargeScreen) {
                            minOf(maxWidth, 392.dp)
                        } else {
                            maxWidth * 0.84f
                        }
                        HorizontalPager(
                            state = pagerState,
                            pageSize = PageSize.Fixed(cardWidth),
                            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                            pageSpacing = 4.dp,
                            beyondViewportPageCount = (widgetTypes.size - 1).coerceAtLeast(0),
                            key = { page -> widgetTypes[page].key },
                            modifier = Modifier.fillMaxWidth().height(252.dp)
                        ) { page ->
                            val type = widgetTypes[page]
                            val appearance = widgetAppearanceForType(appearances, type)
                            val relative = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                            val offset = abs(relative).coerceAtMost(1f)
                            val scale = 1f - offset * 0.07f
                            Column(
                                Modifier
                                    .width(cardWidth)
                                    .height(252.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 1f - offset * 0.08f
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val placeholderSize = canonicalWidgetPreviewSize(type)
                                val previewSlot = if (adaptiveMetrics.isLargeScreen) {
                                    if (type == WidgetAppearanceVariant.WEEK_SCHEDULE) {
                                        Modifier.requiredSize(280.dp, 210.dp)
                                    } else {
                                        Modifier.requiredSize(
                                            placeholderSize.widthDp.dp,
                                            placeholderSize.heightDp.dp
                                        )
                                    }
                                } else {
                                    Modifier
                                        .fillMaxWidth(
                                            when (type) {
                                                WidgetAppearanceVariant.COURSES_SQUARE -> 0.50f
                                                WidgetAppearanceVariant.WEEK_SCHEDULE -> 0.83f
                                                else -> 1f
                                            }
                                        )
                                        .aspectRatio(type.canonicalAspect)
                                }
                                Box(previewSlot) {
                                    WidgetRemoteViewsPreview(
                                        type,
                                        appearance,
                                        state,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .drawWithContent {
                                                if (!editorSourceHandedOff || page != pagerState.currentPage) {
                                                    drawContent()
                                                }
                                            },
                                        useParentSize = true,
                                        onBoundsChanged = {
                                            if (page == pagerState.currentPage) currentPreviewBounds = it
                                        }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(type.displayName, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    ProjectPagerIndicator(
                        pagerState = pagerState,
                        pageCount = widgetTypes.size
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
                key(selectedType.key) {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "使用自定义背景",
                        subtitle = if (current.wallpaperUri == null) "先选择一张图片" else "关闭不会删除图片和取景",
                        checked = current.enabled,
                        backdrop = backdrop,
                        enabled = current.wallpaperUri != null,
                        onCheckedChange = { enabled ->
                            saveDefault(selectedType) { it.copy(enabled = enabled) }
                        }
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
                            previewDefault(selectedType) { it.copy(blurDp = value * 10f) }
                        },
                        onCommit = {
                            val value = it
                            saveDefault(selectedType) { appearance ->
                                appearance.copy(blurDp = value * 10f)
                            }
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
                            previewDefault(selectedType) { it.copy(brightness = value) }
                        },
                        onCommit = {
                            val value = it
                            saveDefault(selectedType) { appearance ->
                                appearance.copy(brightness = value)
                            }
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
                            val targetType = selectedType
                            scope.launch {
                                repository.reset(targetType, currentId)
                                reload()
                                refresh(targetType)
                            }
                        }
                    )
                }
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
                onCancel = {
                    editorSourceHandedOff = false
                    editRequest = null
                },
                onSave = { draft, pickedUri, pickedBitmap, cleared ->
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
                                pickedBitmap?.let { repository.persistSelectedBitmap(it) }
                                    ?: repository.persistSelectedImage(pickedUri)
                            } else pickedUri
                        }
                        val source = savedUri ?: run {
                            Toast.makeText(context, "图片处理失败，请重新选择", Toast.LENGTH_SHORT).show()
                            editorSourceHandedOff = false
                            editRequest = null
                            return@launch
                        }
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
    modifier: Modifier = Modifier,
    fillFraction: Float = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 0.50f else 1f,
    preserveCanonicalSize: Boolean = false,
    useParentSize: Boolean = false,
    transparentBackground: Boolean = false,
    onBoundsChanged: (Rect) -> Unit = {},
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val renderSize = remember(type) { canonicalWidgetPreviewSize(type) }
    var remoteViews by remember(type) { mutableStateOf<RemoteViews?>(null) }
    val latestOnReady = rememberUpdatedState(onReady)
    LaunchedEffect(type, appearance, state, transparentBackground) {
        // Slider/crop gestures can emit dozens of appearance snapshots per second. Keep the
        // last valid preview on screen and collapse that burst into one expensive bitmap pass.
        if (remoteViews != null) delay(90)
        val rendered = runCatching {
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                if (type == WidgetAppearanceVariant.WEEK_SCHEDULE) {
                    return@withContext WeekScheduleWidgetRenderer.buildViews(
                        context = appContext,
                        state = state,
                        appearance = appearance,
                        size = renderSize,
                        transparentBackground = transparentBackground
                    ) to null
                }
                val views = when (type) {
                    WidgetAppearanceVariant.COURSES_LARGE ->
                        MiuixTodayWidgetRenderer.buildViews(
                            appContext,
                            state,
                            TodayWidgetVariant.LARGE,
                            appearance,
                            renderSize
                        )
                    WidgetAppearanceVariant.COURSES_SQUARE ->
                        MiuixTodayWidgetRenderer.buildViews(
                            appContext,
                            state,
                            TodayWidgetVariant.SQUARE,
                            appearance,
                            renderSize
                        )
                    WidgetAppearanceVariant.TODAY_TOMORROW ->
                        TodayTomorrowWidgetRenderer.buildViews(
                            appContext,
                            state,
                            appearance,
                            renderSize
                        )
                    WidgetAppearanceVariant.WEEK_SCHEDULE -> error("周视图预览已提前处理")
                    WidgetAppearanceVariant.TODAY_ASSISTANT -> {
                        val weather = if (DayAgentPreferences.isWeatherEnabled(context)) {
                            DayAgentWeatherRepository(context.applicationContext).getWeather()
                        } else null
                        TodayAssistantWidgetRenderer.buildViews(
                            appContext,
                            state,
                            weather,
                            appearance,
                            renderSize
                        )
                    }
                }
                if (transparentBackground) {
                    val root = when (type) {
                        WidgetAppearanceVariant.TODAY_ASSISTANT -> R.id.widget_agent_root
                        WidgetAppearanceVariant.TODAY_TOMORROW -> R.id.widget_tt_root
                        else -> R.id.widget_root
                    }
                    val background = when (type) {
                        WidgetAppearanceVariant.TODAY_ASSISTANT -> R.id.widget_agent_background_image
                        WidgetAppearanceVariant.TODAY_TOMORROW -> R.id.widget_tt_background_image
                        else -> R.id.widget_background_image
                    }
                    views.setInt(root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                    views.setViewVisibility(background, android.view.View.GONE)
                }
                // Keep the wallpaper and foreground in the same RemoteViews hierarchy. Course
                // surfaces belong to the real rows/cells, while the bitmap owns only the wallpaper.
                views to null
            }
        }
        rendered.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        rendered.onSuccess { (views, _) ->
            remoteViews = views
            latestOnReady.value()
        }.onFailure {
            android.util.Log.e("WidgetPreview", "Failed to render ${type.key} preview", it)
        }
    }
    val density = LocalDensity.current
    val logicalCornerRadius = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 16.dp else 18.dp
    val previewFrame = if (useParentSize) {
        Modifier.fillMaxSize()
    } else if (preserveCanonicalSize) {
        Modifier.requiredSize(renderSize.widthDp.dp, renderSize.heightDp.dp)
    } else {
        Modifier
            .fillMaxWidth(fillFraction)
            .aspectRatio(type.canonicalAspect)
    }
    BoxWithConstraints(
        modifier = modifier
            .then(previewFrame)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .squircleClip(logicalCornerRadius),
        contentAlignment = Alignment.Center
    ) {
        val scale = with(density) {
            (maxWidth.toPx() / renderSize.widthDp.dp.toPx()).coerceAtLeast(0.1f)
        }
        AndroidView(
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    clipToOutline = false
                    clipChildren = true
                }
            },
            update = { host ->
                val remote = remoteViews ?: return@AndroidView
                if (host.tag !== remote) {
                    runCatching {
                        if (host.childCount == 1 && host.tag != null) {
                            remote.reapply(host.context, host.getChildAt(0))
                        } else {
                            val view = remote.apply(host.context, host)
                            host.removeAllViews()
                            host.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                        host.tag = remote
                    }.onFailure {
                        android.util.Log.e("WidgetPreview", "Failed to apply ${type.key} preview", it)
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

internal fun canonicalWidgetPreviewSize(type: WidgetAppearanceVariant): WidgetRenderSize = when (type) {
    WidgetAppearanceVariant.COURSES_SQUARE -> WidgetRenderSize(168, 168)
    WidgetAppearanceVariant.WEEK_SCHEDULE -> WidgetRenderSize(336, 252)
    else -> WidgetRenderSize(336, 168)
}

@Composable
private fun WidgetWallpaperEditor(
    request: WidgetEditRequest,
    state: AppState,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onSourceHandoff: () -> Unit,
    onCancel: () -> Unit,
    onSave: (WidgetAppearanceEntity, Uri?, Bitmap?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember(request) { mutableStateOf(request.sourceUri) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var sourceSize by remember(uri) { mutableStateOf<WallpaperSourceSize?>(null) }
    var sourceLoading by remember(uri) { mutableStateOf(uri != null) }
    var cleared by remember(request) { mutableStateOf(request.sourceUri == null) }
    var crop by remember(request) {
        mutableStateOf(WallpaperCropState(request.appearance.centerX, request.appearance.centerY, request.appearance.scale))
    }
    val progress = remember { Animatable(0f) }
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    var editorHostOrigin by remember { mutableStateOf(Offset.Zero) }
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
    LaunchedEffect(sourceReplicaReady) {
        if (sourceReplicaReady) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            onSourceHandoff()
            androidx.compose.runtime.withFrameNanos { }
            progress.animateTo(1f, tween(460, easing = morphEasing))
        }
    }
    fun closeThen(commitEditedAppearance: Boolean = false, action: () -> Unit) {
        if (closing) return
        closing = true
        returnToEditedAppearance = commitEditedAppearance
        scope.launch {
            progress.animateTo(0f, tween(400, easing = morphEasing))
            action()
        }
    }
    LaunchedEffect(uri) {
        val source = uri
        sourceLoading = source != null
        val loaded = if (source == null) null else withContext(Dispatchers.IO) {
            loadWallpaperSource(context.applicationContext, source, 1800)
        }
        bitmap = loaded?.bitmap
        sourceSize = loaded?.sourceSize
        sourceLoading = false
    }
    val loadedBitmap = bitmap
    DisposableEffect(loadedBitmap) {
        onDispose { loadedBitmap?.recycle() }
    }
    androidx.activity.compose.BackHandler(enabled = !closing) { closeThen(action = onCancel) }
    val p = progress.value.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(300f)
            .onGloballyPositioned { editorHostOrigin = it.positionInWindow() }
            .background(Color.Black.copy(alpha = 0.92f * p))
    ) {
        val density = LocalDensity.current
        val ratio = request.appearance.type.canonicalAspect
        val availableHeight = (maxHeight - if (adaptiveMetrics.isLargeScreen) 284.dp else 240.dp)
            .coerceAtLeast(260.dp)
        val widthFraction = when {
            !adaptiveMetrics.isLargeScreen ->
                if (request.appearance.type == WidgetAppearanceVariant.COURSES_SQUARE) 0.68f else 0.88f
            request.appearance.type == WidgetAppearanceVariant.COURSES_SQUARE -> 0.56f
            else -> 0.78f
        }
        val largeScreenWidthCap = when (request.appearance.type) {
            // Preview at the exact RemoteViews design size on large screens. It may scale down
            // when space is tight, but must never be enlarged to fill a tablet pane.
            WidgetAppearanceVariant.COURSES_SQUARE -> 168.dp
            WidgetAppearanceVariant.COURSES_LARGE,
            WidgetAppearanceVariant.TODAY_TOMORROW,
            WidgetAppearanceVariant.WEEK_SCHEDULE,
            WidgetAppearanceVariant.TODAY_ASSISTANT -> 336.dp
        }
        val desiredWidth = minOf(
            maxWidth * widthFraction,
            availableHeight * ratio,
            if (adaptiveMetrics.isLargeScreen) largeScreenWidthCap else maxWidth
        ).coerceAtLeast(
            if (request.appearance.type == WidgetAppearanceVariant.COURSES_SQUARE) 140.dp else 210.dp
        )
        val desiredHeight = desiredWidth / ratio
        val validSourceBounds = request.sourceBounds.width > 8f && request.sourceBounds.height > 8f
        val startLeft = if (validSourceBounds) {
            with(density) { (request.sourceBounds.left - editorHostOrigin.x).toDp() }
        } else {
            (maxWidth - desiredWidth) / 2
        }
        val startTop = if (validSourceBounds) {
            with(density) { (request.sourceBounds.top - editorHostOrigin.y).toDp() }
        } else {
            (maxHeight - desiredHeight) / 2
        }
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
            WallpaperHeaderButton(
                "完成",
                backdrop,
                Color(0xFF0A84FF).copy(alpha = 0.84f),
                !closing && !sourceLoading && (uri == null || bitmap != null)
            ) {
                val source = uri
                val sourceBitmap = bitmap
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
                        sourceBitmap,
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
