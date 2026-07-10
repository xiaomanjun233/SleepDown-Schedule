package com.example.courseschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class ScheduleManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installScheduleDepthTransitions()
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.allSchedulesState.collectAsStateWithLifecycle()
            CourseScheduleTheme(config = state.config) {
                ScheduleManagerScreen(
                    state = state,
                    onSelect = { id ->
                        Log.d("ScheduleManager", "select schedule id=$id")
                        viewModel.activateSchedule(id) { finish() }
                    },
                    onCustomize = { id ->
                        Log.d("ScheduleManager", "customize schedule id=$id")
                        val intent = Intent(this, SettingsDetailActivity::class.java)
                            .putExtra("settings_page", SettingsPage.Schedule.name)
                            .putScheduleCustomizeId(id)
                        startActivity(intent)
                    },
                    onCreate = {
                        Log.d("ScheduleManager", "create schedule")
                        viewModel.createSchedule()
                    },
                    onRename = { id, name ->
                        Log.d("ScheduleManager", "rename schedule id=$id name=$name")
                        viewModel.renameSchedule(id, name)
                    },
                    onDelete = { id ->
                        Log.d("ScheduleManager", "delete schedule id=$id")
                        viewModel.deleteSchedule(id)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        applyLegacyScheduleDepthCloseTransition()
    }
}

@Composable
fun ScheduleManagerScreen(
    state: AppState,
    activeSnapshot: Bitmap? = null,
    onSelect: (Int) -> Unit,
    onCustomize: (Int) -> Unit,
    onCreate: () -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit
) {
    val profiles = state.schedules.ifEmpty {
        listOf(ScheduleProfileEntity(id = 1, name = "默认课表", isActive = true))
    }
    val activeIndex = profiles.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val backgroundBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = backgroundBackdrop
    var deleteCandidate by remember { mutableStateOf<ScheduleProfileEntity?>(null) }
    var renameCandidate by remember { mutableStateOf<ScheduleProfileEntity?>(null) }
    var deleteReveal by remember { mutableFloatStateOf(0f) }
    var selectedProfileId by remember { mutableIntStateOf(profiles[activeIndex].id) }
    var pendingActivationId by remember { mutableStateOf<Int?>(null) }
    var initialActiveCentered by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activeProfileId = profiles.getOrNull(activeIndex)?.id ?: profiles.first().id
    var lastCenteredActiveProfileId by remember { mutableIntStateOf(activeProfileId) }
    val configsBySchedule = remember(state.allConfigs) {
        state.allConfigs.associateBy { it.id }
    }
    val coursesBySchedule = remember(state.allCourses) {
        state.allCourses.groupBy { it.scheduleId }
    }
    val periodsBySchedule = remember(state.allPeriods) {
        state.allPeriods.groupBy { it.scheduleId }
    }

    val currentPageIndex by remember(profiles.size, activeIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2f - center)
            }?.index?.coerceIn(0, profiles.lastIndex)
                ?: activeIndex.coerceIn(0, profiles.lastIndex)
        }
    }
    val selectedIndex = profiles.indexOfFirst { it.id == selectedProfileId }
        .takeIf { it >= 0 }
        ?: activeIndex
    val centeredProfile = profiles.getOrNull(selectedIndex) ?: profiles.first()
    val centeredConfig = configsBySchedule[centeredProfile.id]
        ?: if (centeredProfile.isActive) state.config else defaultConfig(centeredProfile.id)
    val centeredCourses = coursesBySchedule[centeredProfile.id].orEmpty()
    val centeredPeriods = periodsBySchedule[centeredProfile.id].orEmpty().ifEmpty {
        if (centeredProfile.isActive) state.periods else defaultPeriods(centeredProfile.id)
    }

    LaunchedEffect(profiles.size, activeProfileId) {
        val pendingId = pendingActivationId
        if (pendingId != null && pendingId != activeProfileId) {
            return@LaunchedEffect
        }
        if (pendingId == activeProfileId) {
            pendingActivationId = null
        }
        val shouldCenterActive = !initialActiveCentered ||
            pendingId == activeProfileId ||
            (selectedProfileId == lastCenteredActiveProfileId && activeProfileId != lastCenteredActiveProfileId)
        if (shouldCenterActive || profiles.none { it.id == selectedProfileId }) {
            selectedProfileId = activeProfileId
        }
        if (shouldCenterActive || (!listState.isScrollInProgress && selectedProfileId == activeProfileId)) {
            listState.animateScrollToItem(activeIndex)
        }
        if (shouldCenterActive) {
            lastCenteredActiveProfileId = activeProfileId
        }
        initialActiveCentered = true
    }
    LaunchedEffect(listState.isScrollInProgress, currentPageIndex, profiles.size) {
        if (!listState.isScrollInProgress) {
            profiles.getOrNull(currentPageIndex)?.let { profile ->
                if (profile.id != selectedProfileId) {
                    selectedProfileId = profile.id
                }
            }
        }
    }
    LaunchedEffect(centeredProfile.id) {
        deleteReveal = 0f
    }
    BackHandler(enabled = deleteReveal > 0f) {
        deleteReveal = 0f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .layerBackdrop(backgroundBackdrop)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(92.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center
        ) {
            LiquidButton(
                onClick = onBack,
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
                    .width(86.dp),
                height = 40.dp,
                blurRadius = 10.dp,
                lensHeight = 26.dp,
                lensAmount = 34.dp,
                surfaceColor = Color.Black.copy(alpha = 0.30f),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("取消", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                centeredProfile.name,
                modifier = Modifier
                    .padding(horizontal = 112.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { renameCandidate = centeredProfile },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LiquidButton(
                onClick = {
                    pendingActivationId = centeredProfile.id
                    onSelect(centeredProfile.id)
                },
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .width(86.dp),
                height = 40.dp,
                blurRadius = 10.dp,
                lensHeight = 26.dp,
                lensAmount = 34.dp,
                surfaceColor = Color(0xFF007AFF).copy(alpha = 0.24f),
                tint = Color(0xFF007AFF),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("设为", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 118.dp, bottom = 138.dp)
        ) {
            val availableHeight = maxHeight.coerceAtLeast(320.dp)
            val targetWidth = maxWidth * 0.72f
            val ratioLimitedWidth = availableHeight * (9f / 19.6f)
            val cardWidth = minOf(targetWidth, ratioLimitedWidth).coerceAtLeast(220.dp)
            val cardHeight = cardWidth * (19.6f / 9f)
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(24.dp)
            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    val isCentered = profile.id == selectedProfileId
                    val config = configsBySchedule[profile.id]
                        ?: if (profile.isActive) state.config else defaultConfig(profile.id)
                    val courses = coursesBySchedule[profile.id].orEmpty()
                    val periods = periodsBySchedule[profile.id].orEmpty().ifEmpty {
                        if (profile.isActive) state.periods else defaultPeriods(profile.id)
                    }
                    ScheduleCarouselCard(
                        profile = profile,
                        config = config,
                        courses = courses,
                        periods = periods,
                        backdrop = backgroundBackdrop,
                        snapshot = if (profile.isActive) activeSnapshot else null,
                        selected = profile.isActive,
                        isCentered = isCentered,
                        deleteReveal = if (isCentered) deleteReveal else 0f,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .zIndex(if (isCentered) 1f else 0f)
                            .graphicsLayer {
                                scaleX = 1f
                                scaleY = 1f
                                this.alpha = 1f
                            },
                        onTap = {
                            if (isCentered) {
                                pendingActivationId = profile.id
                                onSelect(profile.id)
                            } else {
                                selectedProfileId = profile.id
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        onLongPress = {
                            if (isCentered) deleteReveal = 1f
                        },
                        onDeleteReveal = { reveal ->
                            if (isCentered) deleteReveal = reveal
                        },
                        onDeleteClick = { deleteCandidate = centeredProfile }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 108.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEachIndexed { index, profile ->
                val active = profile.id == selectedProfileId
                val dotSize by animateDpAsState(
                    targetValue = if (active) 9.dp else 7.dp,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                    label = "schedule-dot-size-${profile.id}"
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (active) 0.92f else 0.36f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    label = "schedule-dot-alpha-${profile.id}"
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = dotAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                selectedProfileId = profile.id
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        )
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 26.dp)
        ) {
            val compact = maxWidth < 360.dp
            val customizeWidth = if (compact) 160.dp else 176.dp
            val buttonSize = if (compact) 48.dp else 52.dp
            val plusOffset = customizeWidth / 2 + if (compact) 52.dp else 58.dp
            LiquidButton(
                onClick = {
                    val token = buildSleepDownScheduleToken(centeredConfig, centeredPeriods, centeredCourses)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SleepDown 课程表口令", token))
                    Toast.makeText(context, "课表口令已复制", Toast.LENGTH_SHORT).show()
                },
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -plusOffset)
                    .size(buttonSize),
                height = buttonSize,
                tint = Color(0xFF34C759),
                surfaceColor = Color(0xFF34C759).copy(alpha = 0.22f),
                blurRadius = 10.dp,
                lensHeight = 30.dp,
                lensAmount = 44.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share_schedule),
                    contentDescription = "分享课表",
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }
            LiquidButton(
                onClick = { onCustomize(centeredProfile.id) },
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(customizeWidth)
                    .height(if (compact) 50.dp else 54.dp),
                height = if (compact) 50.dp else 54.dp,
                blurRadius = 10.dp,
                lensHeight = 28.dp,
                lensAmount = 40.dp,
                surfaceColor = Color.Black.copy(alpha = 0.30f),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Text("自定义", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            LiquidButton(
                onClick = onCreate,
                backdrop = chromeBackdrop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = plusOffset)
                    .size(buttonSize),
                height = buttonSize,
                tint = Color(0xFF007AFF),
                surfaceColor = Color(0xFF007AFF).copy(alpha = 0.22f),
                blurRadius = 10.dp,
                lensHeight = 30.dp,
                lensAmount = 44.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_course),
                    contentDescription = "新建课表",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }

    deleteCandidate?.let { profile ->
        Dialog(onDismissRequest = { deleteCandidate = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            KyantLiquidDialog(backdrop = chromeBackdrop, config = settingsVisualConfig(state.config)) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("删除课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("确定要删除「${profile.name}」吗？该课表内的课程会一起删除。", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DialogLiquidButton(chromeBackdrop, "取消", { deleteCandidate = null }, modifier = Modifier.weight(1f), role = DialogButtonRole.Neutral)
                        DialogLiquidButton(chromeBackdrop, "删除", {
                            onDelete(profile.id)
                            deleteCandidate = null
                            deleteReveal = 0f
                        }, modifier = Modifier.weight(1f), role = DialogButtonRole.Cancel)
                    }
                }
            }
        }
    }

    renameCandidate?.let { profile ->
        ScheduleRenameDialog(
            profile = profile,
            backdrop = chromeBackdrop,
            config = settingsVisualConfig(state.config),
            onCancel = { renameCandidate = null },
            onSave = { name ->
                onRename(profile.id, name)
                renameCandidate = null
            }
        )
    }
}

@Composable
fun ScheduleCarouselCard(
    profile: ScheduleProfileEntity,
    config: ScheduleConfigEntity,
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    backdrop: Backdrop?,
    snapshot: Bitmap?,
    selected: Boolean,
    isCentered: Boolean,
    deleteReveal: Float,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteReveal: (Float) -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dark = appUsesDarkTheme(config)
    var bitmap by remember(config.wallpaperUri, config.defaultWallpaperStyle, dark) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(config.wallpaperUri, config.defaultWallpaperStyle, dark) {
        bitmap = withContext(Dispatchers.IO) { loadWallpaperBitmap(context, config, dark) }
    }
    val deleteVisible = deleteReveal > 0.05f
    val animatedPressOffset by animateDpAsState(
        targetValue = if (deleteVisible) 14.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "schedule-delete-press"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (deleteVisible) 0.46f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "schedule-delete-overlay"
    )
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier = modifier
            .offset { IntOffset(0, animatedPressOffset.roundToPx()) }
            .clip(shape)
            .background(Color(0xFF111111))
            .then(
                if (deleteVisible) {
                    Modifier
                } else {
                    Modifier.pointerInput(isCentered) {
                        detectTapGestures(
                            onLongPress = {
                                if (isCentered) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress()
                                }
                            },
                            onTap = { onTap() }
                        )
                    }
                }
            )
    ) {
        if (snapshot != null) {
            Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            ScheduleHomeSnapshotPreview(
                config = config,
                courses = courses,
                periods = periods,
                wallpaperBitmap = bitmap,
                selected = selected,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (overlayAlpha > 0.01f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = overlayAlpha)))
        }
        AnimatedVisibility(
            visible = deleteVisible,
            enter = fadeIn(animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f)) +
                    scaleIn(initialScale = 0.64f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 560f)),
            exit = fadeOut(animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f)) +
                    scaleOut(targetScale = 0.72f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (backdrop != null) {
                LiquidButton(
                    onClick = onDeleteClick,
                    backdrop = backdrop,
                    modifier = Modifier.size(66.dp),
                    height = 66.dp,
                    tint = Color(0xFFFF453A),
                    surfaceColor = Color(0xFFFF453A).copy(alpha = 0.38f),
                    blurRadius = 12.dp,
                    lensHeight = 34.dp,
                    lensAmount = 46.dp,
                    chromaticAberration = false,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        contentDescription = "删除课表",
                        modifier = Modifier.size(26.dp),
                        tint = Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF453A).copy(alpha = 0.88f))
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        contentDescription = "删除课表",
                        modifier = Modifier.size(26.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleHomeSnapshotPreview(
    config: ScheduleConfigEntity,
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    wallpaperBitmap: Bitmap?,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val week = effectiveCurrentWeek(config)
    val previewState = remember(config, courses, periods) {
        AppState(
            courses = courses,
            allCourses = courses,
            config = config,
            periods = periods.ifEmpty { defaultPeriods(config.id) },
            loaded = true
        )
    }
    val cardColor = Color(config.cardColorArgb.toInt()).copy(alpha = if (config.courseCardGlassEnabled) 0.66f else config.cardAlpha)
    val textColor = homeForegroundColor(config)
    Box(modifier = modifier.background(if (appUsesDarkTheme(config)) Color(0xFF050505) else Color.White)) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (appUsesDarkTheme(config)) 0.20f else 0.04f)))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (appUsesDarkTheme(config)) Color(0xFF101014) else Color(0xFFF7FAFF),
                                Color(config.cardColorArgb.toInt()).copy(alpha = if (appUsesDarkTheme(config)) 0.22f else 0.30f),
                                if (appUsesDarkTheme(config)) Color(0xFF050505) else Color.White
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 0.985f
                    scaleY = 0.985f
                }
                .padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            StaticWeekSnapshotGrid(
                state = previewState,
                displayWeek = week,
                cardColor = cardColor,
                textColor = textColor,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("正在使用", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StaticWeekSnapshotGrid(
    state: AppState,
    displayWeek: Int,
    cardColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val rowHeaderWidth = 40.dp
    val today = LocalDate.now()
    val weekStart = today
        .minusDays((today.dayOfWeek.toChineseWeekday() - 1).toLong())
        .plusWeeks((displayWeek - effectiveCurrentWeek(state.config)).toLong())
    val visibleCourses = remember(state.courses, displayWeek) {
        state.courses.filter { course ->
            displayWeek in course.weeks && parityMatches(course.weekParity, displayWeek)
        }
    }
    val weekdays = remember(visibleCourses, state.config.hideEmptyWeekends) {
        val weekendHasCourse = visibleCourses.any { it.weekday == 6 || it.weekday == 7 }
        if (state.config.hideEmptyWeekends && !weekendHasCourse) (1..5).toList() else (1..7).toList()
    }
    val periods = state.periods.take(10).ifEmpty { defaultPeriods(state.config.id).take(10) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            SnapshotRoundButton("<", textColor)
            Text(
                text = "第${displayWeek}周",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = textColor
            )
            SnapshotRoundButton(">", textColor)
        }
        Box(
            modifier = Modifier
                .height(38.dp)
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (appUsesDarkTheme(state.config)) 0.12f else 0.42f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "节次",
                    modifier = Modifier.width(rowHeaderWidth),
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                weekdays.forEach { day ->
                    val isToday = day == today.dayOfWeek.toChineseWeekday()
                    val date = weekStart.plusDays((day - 1).toLong())
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "周${scheduleWeekdayLabel(day)}",
                            fontSize = 7.sp,
                            lineHeight = 8.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                            color = textColor,
                            maxLines = 1
                        )
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 6.sp,
                            lineHeight = 7.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.width(rowHeaderWidth)) {
                periods.forEach { period ->
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(period.periodIndex.toString(), fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(period.startTime, fontSize = 5.5.sp, lineHeight = 6.sp, color = textColor.copy(alpha = 0.78f), maxLines = 1)
                            Text(period.endTime, fontSize = 5.5.sp, lineHeight = 6.sp, color = textColor.copy(alpha = 0.78f), maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxHeight()) {
                weekdays.forEach { day ->
                    Column(modifier = Modifier.weight(1f)) {
                        SnapshotWeekDayColumn(
                            courses = visibleCourses.filter { it.weekday == day },
                            periods = periods,
                            cardColor = cardColor,
                            emptyBackground = Color.Transparent,
                            config = state.config
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotRoundButton(label: String, textColor: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ColumnScope.SnapshotWeekDayColumn(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    cardColor: Color,
    emptyBackground: Color,
    config: ScheduleConfigEntity
) {
    val periodIndexes = periods.map { it.periodIndex }
    var periodCursor = 0
    while (periodCursor < periods.size) {
        val period = periods[periodCursor]
        val startingCourses = courses
            .filter { snapshotCourseStartsAt(it, period.periodIndex) }
            .sortedBy { it.name }
        if (startingCourses.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(1.dp)
                    .background(emptyBackground)
            )
            periodCursor += 1
        } else {
            val span = startingCourses.maxOf { snapshotContinuousSpanFrom(it, period.periodIndex, periodIndexes) }.coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .weight(span.toFloat())
                    .fillMaxWidth()
                    .padding(1.dp)
                    .background(emptyBackground)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    startingCourses.take(2).forEach { course ->
                        SnapshotCourseBlock(
                            course = course,
                            cardColor = cardColor,
                            config = config,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
            }
            periodCursor += span
        }
    }
}

@Composable
fun SnapshotCourseBlock(
    course: CourseEntity,
    cardColor: Color,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    CourseGlassCard(
        backdrop = null,
        config = config,
        modifier = modifier,
        shape = RoundedCornerShape(7.dp)
    ) {
        val textColor = readableOn(cardColor)
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)) {
            val tiny = maxHeight < 28.dp
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    course.name,
                    fontSize = if (tiny) 5.7.sp else 6.4.sp,
                    lineHeight = if (tiny) 6.1.sp else 6.8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = if (tiny) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                course.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 5.4.sp,
                        lineHeight = 5.9.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleRenameDialog(
    profile: ScheduleProfileEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CenterLiquidDialog(backdrop = backdrop, config = config) {
            Text("重命名课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DialogLiquidButton(backdrop, "取消", onCancel, modifier = Modifier.weight(1f), role = DialogButtonRole.Neutral)
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = "保存",
                    onClick = { onSave(name.trim().ifBlank { "未命名课表" }) },
                    modifier = Modifier.weight(1f),
                    role = DialogButtonRole.Confirm
                )
            }
        }
    }
}

private fun snapshotCourseStartsAt(course: CourseEntity, periodIndex: Int): Boolean =
    periodIndex in course.periods && course.periods.minOrNull() == periodIndex

private fun snapshotContinuousSpanFrom(course: CourseEntity, start: Int, periodIndexes: List<Int>): Int {
    var span = 0
    var current = start
    while (current in course.periods && current in periodIndexes) {
        span += 1
        current += 1
    }
    return span.coerceAtLeast(1)
}

private fun scheduleWeekdayLabel(weekday: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[(weekday - 1).coerceIn(0, 6)]
