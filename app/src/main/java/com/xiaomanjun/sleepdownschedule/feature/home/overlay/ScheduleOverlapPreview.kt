package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.LocalGlassQuality
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensAmount
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensHeight
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialForeground
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeLabel
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorFormData
import com.xiaomanjun.sleepdownschedule.feature.course.editor.NormalizedCourseEditorScreen
import com.xiaomanjun.sleepdownschedule.feature.home.CourseAdjustmentBadge
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.contentRectPx
import com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeCornerAnchor
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeCourseTextColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.MorphDialogSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.MutedCourseLightColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardBaseColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseGlassTintAlpha
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
data class ScheduleOverlapEntry(
    val key: String,
    val course: CourseEntity,
    val weekday: Int,
    val week: Int,
    val startSlot: Double,
    val endSlot: Double,
    val editingAllowed: Boolean = true,
    val muted: Boolean = false,
    val adjustmentLabel: String? = null,
    val adjustedOccurrenceDate: LocalDate? = null,
    val isTopInGrid: Boolean = false
)

enum class ScheduleOverlapPhase { Idle, Opening, Open, Closing }

@Stable
data class ScheduleOverlapSession(
    val entries: List<ScheduleOverlapEntry>,
    val anchorKey: String,
    val anchorSourceRect: Rect,
    val sourceCornerPx: Float,
    val weekday: Int,
    val week: Int
)

@Stable
data class OverlapEditorTarget(
    val course: CourseEntity,
    val targetWeek: Int?,
    val contextMessage: String?
)

@Stable
class ScheduleOverlapController(
    private val scope: CoroutineScope
) {
    var session by mutableStateOf<ScheduleOverlapSession?>(null)
        private set

    var phase by mutableStateOf(ScheduleOverlapPhase.Idle)
        private set

    val progress = Animatable(0f)

    var interruptedFromRects by mutableStateOf<Map<String, Rect>?>(null)
        private set

    var interruptedStartRaw by mutableStateOf<Float?>(null)
        private set

    val liveCardRects = mutableStateMapOf<String, Rect>()

    var activeEditorEntry by mutableStateOf<ScheduleOverlapEntry?>(null)
        private set

    private var activeJob: Job? = null

    val isEditorHosted: Boolean
        get() = activeEditorEntry != null

    val visible: Boolean
        get() = session != null

    val backgroundActive: Boolean
        get() = session != null && (isEditorHosted || phase != ScheduleOverlapPhase.Idle || progress.value > 0.001f)

    val backgroundZoomProgress: Float
        get() = if (session != null) progress.value.coerceIn(0f, 1f) else 0f

    val backgroundBlurProgress: Float
        get() = if (session != null) progress.value.coerceIn(0f, 1f) else 0f

    fun hides(course: CourseEntity, weekday: Int, week: Int): Boolean {
        val current = session ?: return false
        if (current.week != week || current.weekday != weekday) return false
        val courseKey = course.occurrenceOverrideKey()
        return current.entries.any {
            it.course.id == course.id && it.course.occurrenceOverrideKey() == courseKey
        }
    }

    fun open(
        entries: List<ScheduleOverlapEntry>,
        anchorKey: String,
        sourceRect: Rect,
        sourceCornerPx: Float
    ) {
        if (entries.size <= 1) return
        val current = session
        val currentRaw = progress.value.coerceIn(0f, 1f)
        val reOpeningSameGroup =
            activeEditorEntry == null &&
                current != null &&
                phase == ScheduleOverlapPhase.Closing &&
                current.weekday == entries.first().weekday &&
                current.week == entries.first().week &&
                current.entries.map { it.key } == entries.map { it.key }

        activeEditorEntry = null
        activeJob?.cancel()
        if (reOpeningSameGroup && currentRaw > 0.02f && currentRaw < 0.98f && liveCardRects.isNotEmpty()) {
            interruptedFromRects = liveCardRects.toMap()
            interruptedStartRaw = currentRaw
        } else {
            interruptedFromRects = null
            interruptedStartRaw = null
            liveCardRects.clear()
        }

        val first = entries.first()
        session = ScheduleOverlapSession(
            entries = entries,
            anchorKey = anchorKey,
            anchorSourceRect = sourceRect,
            sourceCornerPx = sourceCornerPx,
            weekday = first.weekday,
            week = first.week
        )
        phase = ScheduleOverlapPhase.Opening
        activeJob = scope.launch {
            try {
                if (!reOpeningSameGroup) {
                    progress.snapTo(0f)
                }
                val startVal = progress.value.coerceIn(0f, 1f)
                val duration = ((1f - startVal) * ScheduleOverlapMotion.OpenDurationMs)
                    .roundToInt()
                    .coerceAtLeast(1)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
                interruptedFromRects = null
                interruptedStartRaw = null
                phase = ScheduleOverlapPhase.Open
            } catch (_: CancellationException) {
                // Superseded by dismiss() or reset().
            }
        }
    }

    fun openEditor(entry: ScheduleOverlapEntry) {
        if (session == null || phase != ScheduleOverlapPhase.Open || activeEditorEntry != null) return
        activeEditorEntry = entry
    }

    fun closeEditor() {
        activeEditorEntry = null
    }

    /**
     * Keeps an open/hosted overlap deck synchronized with live timetable edits and exits cleanly
     * back to the grid if overlapping entries drop to one or fewer.
     */
    fun syncWithLiveEntries(updatedEntries: List<ScheduleOverlapEntry>): ScheduleOverlapEntry? {
        val current = session ?: return null
        if (updatedEntries.size <= 1) {
            activeEditorEntry = null
            dismiss()
            return null
        }
        val oldActiveEntry = activeEditorEntry
        val nextAnchorKey = updatedEntries.firstOrNull { it.key == current.anchorKey }?.key
            ?: updatedEntries.last().key
        session = current.copy(
            entries = updatedEntries,
            anchorKey = nextAnchorKey
        )
        if (oldActiveEntry == null) return null
        val matched = updatedEntries.firstOrNull { it.key == oldActiveEntry.key }
            ?: updatedEntries.firstOrNull { it.course.id == oldActiveEntry.course.id }
            ?: updatedEntries.firstOrNull { it.course.name == oldActiveEntry.course.name }
        if (matched != null && matched.key != oldActiveEntry.key) {
            liveCardRects[oldActiveEntry.key]?.let { rect ->
                liveCardRects[matched.key] = rect
            }
        }
        activeEditorEntry = matched
        return matched
    }

    fun dismiss(immediate: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (session == null || activeEditorEntry != null) {
            onFinished?.invoke()
            return
        }
        if (!immediate && phase == ScheduleOverlapPhase.Closing) {
            return
        }
        activeJob?.cancel()
        if (immediate) {
            reset()
            onFinished?.invoke()
            return
        }
        val currentRaw = progress.value.coerceIn(0f, 1f)
        if (phase != ScheduleOverlapPhase.Open && currentRaw > 0.02f && currentRaw < 0.98f && liveCardRects.isNotEmpty()) {
            interruptedFromRects = liveCardRects.toMap()
            interruptedStartRaw = currentRaw
        } else {
            interruptedFromRects = null
            interruptedStartRaw = null
        }
        phase = ScheduleOverlapPhase.Closing
        activeJob = scope.launch {
            try {
                val duration = (currentRaw * ScheduleOverlapMotion.CloseDurationMs)
                    .roundToInt()
                    .coerceAtLeast(1)
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
                interruptedFromRects = null
                interruptedStartRaw = null
                liveCardRects.clear()
                phase = ScheduleOverlapPhase.Idle
                session = null
                onFinished?.invoke()
            } catch (_: CancellationException) {
                // Superseded by re-open().
            }
        }
    }

    fun reset() {
        activeEditorEntry = null
        activeJob?.cancel()
        interruptedFromRects = null
        interruptedStartRaw = null
        liveCardRects.clear()
        phase = ScheduleOverlapPhase.Idle
        session = null
        scope.launch {
            progress.snapTo(0f)
        }
    }
}

val LocalCourseOverlapPreview = compositionLocalOf<ScheduleOverlapController?> { null }

internal fun overlapEntryKey(course: CourseEntity, weekday: Int, startSlot: Double, endSlot: Double): String =
    "${course.id}_${course.occurrenceOverrideKey()}_${weekday}_${(startSlot * 100).roundToInt()}_${(endSlot * 100).roundToInt()}"

@Composable
internal fun CourseOverlapBadge(
    count: Int,
    cardCorner: Dp,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    val label = "${count}门"
    val outerCorner = cardCorner.coerceAtLeast(0.dp)
    val innerCorner = if (cardCorner <= 4.dp) cardCorner else 5.dp
    val shape = remember(outerCorner, innerCorner) {
        RoundedCornerShape(
            topStart = innerCorner,
            topEnd = outerCorner,
            bottomEnd = innerCorner,
            bottomStart = innerCorner
        )
    }
    val native = LocalNativeMaterialSurfaceRenderer.current
    val dark = !glassUsesLightStyle(config)
    if (native?.invoke(
            NativeMaterialSurfaceRequest(
                modifier = modifier,
                shape = RoundedRectangle(innerCorner),
                dark = dark,
                followTouch = false
            )
        ) {
            Box(
                Modifier
                    .sizeIn(minWidth = 22.dp, minHeight = 16.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } == true
    ) {
        return
    }
    val pillBase = if (dark) Color(0xFF1A1D24).copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.28f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(lerp(pillBase, Color.White, 0.14f), pillBase)),
                shape
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.34f), shape)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal fun overlapExpandedCardCornerRadius(
    cardWidth: Dp,
    cardHeight: Dp,
    cornerProgress: Float
): Dp {
    val shortEdge = min(cardWidth.value, cardHeight.value).coerceAtLeast(1f)
    val p = cornerProgress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
    val minRadius = 4f
    val baseRadius = 14f
    val maxRadius = 18f
    val raw = if (p <= 0.5f) {
        minRadius + (baseRadius - minRadius) * (p * 2f)
    } else {
        baseRadius + (maxRadius - baseRadius) * ((p - 0.5f) * 2f)
    }
    return raw.coerceAtMost(shortEdge * 0.18f).dp
}

@Composable
internal fun CourseOverlapOverlay(
    controller: ScheduleOverlapController,
    state: AppState,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    adaptiveMetrics: HomeAdaptiveMetrics,
    contentInsets: PaddingValues,
    backdrop: Backdrop?,
    cardBackdrop: Backdrop?,
    resolveEditorTarget: (ScheduleOverlapEntry) -> OverlapEditorTarget?,
    onSaveCourse: (originals: List<CourseEntity>, edited: List<CourseEntity>, targetWeek: Int?) -> Unit,
    onDeleteCourse: (courses: List<CourseEntity>, targetWeek: Int?) -> Unit
) {
    val session = controller.session ?: return
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val cornerProgress = LocalPersonalizationPreview.current?.weekCardCornerProgress
        ?: config.weekCardCornerProgress

    val isEditorHosted = controller.isEditorHosted

    val closing = controller.phase == ScheduleOverlapPhase.Closing
    val showSettledDeck = controller.phase == ScheduleOverlapPhase.Open &&
        controller.progress.value >= 0.999f
    val rawProgress = controller.progress.value.coerceIn(0f, 1f)
    val chrome = remember(rawProgress, closing) {
        ScheduleOverlapMotion.computeChromeProgress(rawProgress, closing)
    }

    val entries = session.entries
    val anchorEntry = remember(entries, session.anchorKey) {
        entries.firstOrNull { it.key == session.anchorKey } ?: entries.last()
    }
    val rangeStartIndex = remember(entries, periods.size) {
        val minStart = entries.minOf { it.startSlot }
        floor(minStart).toInt().coerceIn(0, (periods.size - 1).coerceAtLeast(0))
    }
    val rangeEndIndex = remember(entries, periods.size, rangeStartIndex) {
        val maxEnd = entries.maxOf { it.endSlot }
        ceil(maxEnd).toInt().coerceIn(rangeStartIndex + 1, periods.size.coerceAtLeast(rangeStartIndex + 1))
    }
    val rowCount = (rangeEndIndex - rangeStartIndex).coerceAtLeast(1)

    val lightStyle = glassUsesLightStyle(config)
    val baseScrimAlpha = (if (lightStyle) 0.34f else 0.30f) * chrome.scrimProgress

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!showSettledDeck) {
                    Modifier.pointerInput(session) {
                        detectTapGestures {
                            controller.dismiss()
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val safeLeftPx = with(density) { safePadding.calculateLeftPadding(layoutDirection).toPx() }
        val safeRightPx = with(density) { safePadding.calculateRightPadding(layoutDirection).toPx() }
        val safeTopPx = with(density) { safePadding.calculateTopPadding().toPx() }
        val safeBottomPx = with(density) { safePadding.calculateBottomPadding().toPx() }
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val safeWidthPx = (screenWidthPx - safeLeftPx - safeRightPx).coerceAtLeast(1f)
        val safeHeightPx = (screenHeightPx - safeTopPx - safeBottomPx).coerceAtLeast(1f)

        val axisWidthDp = 52.dp
        val axisGapDp = 10.dp
        val sideMarginDp = 16.dp
        val cardSpacingDp = 10.dp

        val axisWidthPx = with(density) { axisWidthDp.toPx() }
        val axisGapPx = with(density) { axisGapDp.toPx() }
        val sideMarginPx = with(density) { sideMarginDp.toPx() }
        val cardSpacingPx = with(density) { cardSpacingDp.toPx() }
        val minSlotHeightPx = with(density) { 12.dp.toPx() }

        val gridHeightPx = min(
            with(density) { 420.dp.toPx() },
            max(with(density) { 180.dp.toPx() }, safeHeightPx * 0.56f)
        )
        val gridUnitPx = gridHeightPx / rowCount.toFloat()

        val maxDeckViewportWidthPx = (safeWidthPx - sideMarginPx * 2f - axisWidthPx - axisGapPx)
            .coerceAtLeast(with(density) { 120.dp.toPx() })
        val rawCardWidthPx = (maxDeckViewportWidthPx - cardSpacingPx * (entries.size - 1).coerceAtLeast(0)) /
            entries.size.coerceAtLeast(1)
        val cardWidthPx = rawCardWidthPx.coerceIn(
            with(density) { 116.dp.toPx() },
            with(density) { 196.dp.toPx() }
        )
        val cardWidthDp = with(density) { cardWidthPx.toDp() }
        val totalDeckWidthPx = entries.size * cardWidthPx + (entries.size - 1).coerceAtLeast(0) * cardSpacingPx
        val deckViewportWidthPx = min(totalDeckWidthPx, maxDeckViewportWidthPx)

        // Center the [TimeAxis + Gap + CardDeck] group horizontally in the safe screen width.
        val combinedGroupWidthPx = axisWidthPx + axisGapPx + deckViewportWidthPx
        val groupLeftPx = safeLeftPx + ((safeWidthPx - combinedGroupWidthPx) * 0.5f).coerceAtLeast(sideMarginPx)
        val deckViewportLeftPx = groupLeftPx + axisWidthPx + axisGapPx

        val gridTopPx = safeTopPx + max(
            with(density) { 104.dp.toPx() },
            (safeHeightPx - gridHeightPx + with(density) { 64.dp.toPx() }) * 0.5f
        )

        val scrollState = rememberScrollState()
        val scrollOffsetPx = scrollState.value.toFloat()

        val anchorSpan = (anchorEntry.endSlot - anchorEntry.startSlot).coerceAtLeast(0.25)
        val anchorUnitPx = (session.anchorSourceRect.height / anchorSpan).toFloat()

        fun sourceRectFor(entry: ScheduleOverlapEntry): Rect {
            val top = session.anchorSourceRect.top +
                ((entry.startSlot - anchorEntry.startSlot) * anchorUnitPx).toFloat()
            val height = max(
                minSlotHeightPx,
                ((entry.endSlot - entry.startSlot) * anchorUnitPx).toFloat()
            )
            return Rect(
                left = session.anchorSourceRect.left,
                top = top,
                right = session.anchorSourceRect.right,
                bottom = top + height
            )
        }

        fun targetRectInScreenFor(index: Int, entry: ScheduleOverlapEntry): Rect {
            val left = deckViewportLeftPx + index * (cardWidthPx + cardSpacingPx) - scrollOffsetPx
            val top = gridTopPx + ((entry.startSlot - rangeStartIndex) * gridUnitPx).toFloat()
            val height = max(
                with(density) { 48.dp.toPx() },
                ((entry.endSlot - entry.startSlot) * gridUnitPx).toFloat()
            )
            return Rect(left, top, left + cardWidthPx, top + height)
        }

        fun targetCornerFor(heightDp: Dp): Dp = overlapExpandedCardCornerRadius(
            cardWidth = cardWidthDp,
            cardHeight = heightDp,
            cornerProgress = cornerProgress
        )

        // Continuous dimming scrim shared across flight, deck, and in-place shared-element editor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = baseScrimAlpha }
                .background(Color.Black)
        )

        val headerSlidePx = with(density) { (14.dp.toPx()) * (1f - chrome.progress) }
        val headerScale = 0.96f + 0.04f * chrome.progress
        val headerTextShadow = remember {
            Shadow(
                color = Color.Black.copy(alpha = 0.28f),
                offset = Offset(0f, 2f),
                blurRadius = 6f
            )
        }
        val weekdayTitle = overlapWeekdayTitle(session.weekday)
        val axisSlidePx = with(density) { -14.dp.toPx() * (1f - chrome.progress) }
        val axisScale = 0.94f + 0.06f * chrome.progress
        val guideColorOuter = Color.White.copy(alpha = 0.22f)
        val guideColorInner = Color.White.copy(alpha = 0.12f)

        @Composable
        fun OverlapChromeContent(
            guideModifier: Modifier = Modifier,
            headerModifier: Modifier = Modifier,
            axisModifier: Modifier = Modifier,
            onCloseClick: () -> Unit
        ) {
            if (chrome.opacity > 0.001f) {
                Canvas(
                    modifier = guideModifier
                        .offset {
                            IntOffset(
                                deckViewportLeftPx.roundToInt(),
                                gridTopPx.roundToInt()
                            )
                        }
                        .width(with(density) { deckViewportWidthPx.toDp() })
                        .height(with(density) { gridHeightPx.toDp() })
                        .graphicsLayer {
                            alpha = chrome.opacity
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            scaleX = chrome.progress.coerceIn(0f, 1f)
                        }
                ) {
                    for (i in 0..rowCount) {
                        val y = i * gridUnitPx
                        drawLine(
                            color = if (i == 0 || i == rowCount) guideColorOuter else guideColorInner,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                }
                Box(
                    modifier = headerModifier
                        .offset {
                            IntOffset(
                                groupLeftPx.roundToInt(),
                                (gridTopPx - 80.dp.toPx()).coerceAtLeast(safeTopPx + 12.dp.toPx()).roundToInt()
                            )
                        }
                        .width(with(density) { combinedGroupWidthPx.toDp() })
                        .graphicsLayer {
                            alpha = chrome.opacity
                            translationY = headerSlidePx
                            scaleX = headerScale
                            scaleY = headerScale
                        }
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = weekdayTitle,
                            color = Color.White,
                            fontSize = 28.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            style = TextStyle(shadow = headerTextShadow)
                        )
                        Text(
                            text = "${entries.size}门重叠课程",
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            style = TextStyle(shadow = headerTextShadow)
                        )
                    }
                    OverlapCloseButton(
                        backdrop = backdrop,
                        config = config,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp),
                        onClick = onCloseClick
                    )
                }
                Box(
                    modifier = axisModifier
                        .offset {
                            IntOffset(
                                groupLeftPx.roundToInt(),
                                gridTopPx.roundToInt()
                            )
                        }
                        .width(axisWidthDp)
                        .height(with(density) { gridHeightPx.toDp() })
                        .graphicsLayer {
                            alpha = chrome.opacity
                            translationX = axisSlidePx
                            scaleX = axisScale
                            scaleY = axisScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                ) {
                    for (rowOffset in 0 until rowCount) {
                        val periodIndex = rangeStartIndex + rowOffset
                        val period = periods.getOrNull(periodIndex)
                        val rowHeightDp = with(density) { gridUnitPx.toDp() }
                        val compactAxis = rowHeightDp < 46.dp
                        Box(
                            modifier = Modifier
                                .offset(y = with(density) { (rowOffset * gridUnitPx).toDp() })
                                .fillMaxWidth()
                                .height(rowHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${period?.periodIndex ?: (periodIndex + 1)}",
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = if (compactAxis) 13.sp else 15.sp,
                                    lineHeight = if (compactAxis) 15.sp else 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(shadow = headerTextShadow)
                                )
                                if (period != null) {
                                    Text(
                                        text = period.startTime,
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontSize = if (compactAxis) 10.sp else 11.5.sp,
                                        lineHeight = if (compactAxis) 11.5.sp else 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(shadow = headerTextShadow)
                                    )
                                    Text(
                                        text = period.endTime,
                                        color = Color.White.copy(alpha = 0.68f),
                                        fontSize = if (compactAxis) 10.sp else 11.5.sp,
                                        lineHeight = if (compactAxis) 11.5.sp else 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(shadow = headerTextShadow)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettledDeck) {
            var retainedEditorEntry by remember(session) {
                mutableStateOf<ScheduleOverlapEntry?>(controller.activeEditorEntry)
            }
            val editorReveal = remember(session) {
                Animatable(if (controller.activeEditorEntry != null) 1f else 0f)
            }
            LaunchedEffect(controller.activeEditorEntry, session) {
                val target = controller.activeEditorEntry
                if (target != null) {
                    retainedEditorEntry = target
                    editorReveal.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f)
                    )
                } else if (retainedEditorEntry != null) {
                    editorReveal.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                    retainedEditorEntry = null
                }
            }
            BackHandler(enabled = controller.activeEditorEntry == null && retainedEditorEntry == null) {
                controller.dismiss()
            }

            val rootIntSize = androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
            val (editorWidthDp, editorHeightDp) = remember(rootIntSize, density, adaptiveMetrics, contentInsets, layoutDirection) {
                with(density) {
                    if (adaptiveMetrics.isLargeScreen) {
                        val window = adaptiveMetrics.contentRectPx(rootIntSize, density.density)
                        val leftEdge = window.left + contentInsets.calculateLeftPadding(layoutDirection).toPx()
                        val rightEdge = (window.right - contentInsets.calculateRightPadding(layoutDirection).toPx())
                            .coerceAtLeast(leftEdge + 1f)
                        val topEdge = maxOf(window.top, (adaptiveMetrics.safeTop + contentInsets.calculateTopPadding()).toPx())
                        val content = Rect(leftEdge, topEdge, rightEdge, window.bottom.coerceAtLeast(topEdge + 1f))
                        val minW = minOf(600.dp.toPx(), content.width)
                        val maxW = minOf(680.dp.toPx(), content.width).coerceAtLeast(minW)
                        val maxH = minOf(content.height * 0.82f, 640.dp.toPx())
                            .coerceAtLeast(minOf(content.height, 420.dp.toPx()))
                        maxW.toDp() to maxH.toDp()
                    } else {
                        val maxW = minOf(constraints.maxWidth * 0.92f, 600.dp.toPx())
                        val maxH = minOf(constraints.maxHeight * 0.82f, 600.dp.toPx())
                        maxW.toDp() to maxH.toDp()
                    }
                }
            }
            val deckInteractive = controller.activeEditorEntry == null && editorReveal.value <= 0.01f

            // Stably mounted overlap deck & chrome (preserves horizontal scroll across editor open/close)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = editorReveal.value.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin.Center
                        val deckScale = 1f - 0.06f * p
                        scaleX = deckScale
                        scaleY = deckScale
                        alpha = 1f - p
                    }
                    .then(
                        if (deckInteractive) {
                            Modifier.pointerInput(session) {
                                detectTapGestures {
                                    controller.dismiss()
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                OverlapChromeContent(
                    onCloseClick = {
                        if (deckInteractive) controller.dismiss()
                    }
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                deckViewportLeftPx.roundToInt(),
                                gridTopPx.roundToInt()
                            )
                        }
                        .width(with(density) { deckViewportWidthPx.toDp() })
                        .height(with(density) { gridHeightPx.toDp() })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState, enabled = deckInteractive),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacingDp)
                    ) {
                        entries.forEach { entry ->
                            key(entry.key) {
                                val topDp = with(density) {
                                    (((entry.startSlot - rangeStartIndex) * gridUnitPx).toFloat()).toDp()
                                }
                                val heightPx = max(
                                    with(density) { 48.dp.toPx() },
                                    ((entry.endSlot - entry.startSlot) * gridUnitPx).toFloat()
                                )
                                val heightDp = with(density) { heightPx.toDp() }
                                val cardCorner = targetCornerFor(heightDp)
                                Box(
                                    modifier = Modifier
                                        .width(cardWidthDp)
                                        .fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = topDp)
                                            .width(cardWidthDp)
                                            .height(heightDp)
                                            .onGloballyPositioned { coords ->
                                                if (coords.isAttached) {
                                                    controller.liveCardRects[entry.key] = coords.boundsInRoot()
                                                }
                                            }
                                    ) {
                                        ExpandedOverlapCard(
                                            entry = entry,
                                            periods = periods,
                                            config = config,
                                            cardBackdrop = cardBackdrop,
                                            cardWidth = cardWidthDp,
                                            cardHeight = heightDp,
                                            cornerRadius = cardCorner,
                                            onClick = if (deckInteractive) {
                                                {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.openEditor(entry)
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (totalDeckWidthPx > deckViewportWidthPx) {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }

            // Standard SleepDown center-dialog scale + fade overlay (no shared-element transition)
            retainedEditorEntry?.let { editorEntry ->
                val editorTarget = remember(editorEntry, state.courses, state.config) {
                    resolveEditorTarget(editorEntry)
                }
                if (editorTarget == null) {
                    LaunchedEffect(editorEntry) { controller.closeEditor() }
                } else {
                    val formData = remember(state.config, state.periods, state.courses, state.timetableCatalog) {
                        CourseEditorFormData(
                            config = state.config,
                            periods = state.periods,
                            courses = state.courses,
                            timetableCatalog = state.timetableCatalog
                        )
                    }
                    val focusManager = LocalFocusManager.current
                    val keyboard = LocalSoftwareKeyboardController.current
                    var contentDismissHandler by remember(editorTarget.course.id, editorTarget.targetWeek) {
                        mutableStateOf<(() -> Unit)?>(null)
                    }
                    val closeEditorSafely: () -> Unit = {
                        if (controller.activeEditorEntry != null) {
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                            contentDismissHandler?.invoke() ?: controller.closeEditor()
                        }
                    }
                    val editorShape = RoundedRectangle(32.dp)
                    val editorFormBackdrop = rememberGlassLayerBackdrop(
                        domain = GlassBackdropDomain.Content,
                        providerId = "overlap-center-editor-shell"
                    )
                    val editorContentBackdrop = cardBackdrop ?: backdrop
                    val editorTextColor = if (editorContentBackdrop != null) {
                        LocalAdaptiveGlass.current.contentColor
                    } else {
                        glassForegroundColor(config)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (controller.activeEditorEntry != null) {
                                    Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { closeEditorSafely() }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = editorWidthDp, height = editorHeightDp)
                                .graphicsLayer {
                                    val p = editorReveal.value.coerceIn(0f, 1f)
                                    transformOrigin = TransformOrigin.Center
                                    val editorScale = 0.90f + 0.10f * p
                                    scaleX = editorScale
                                    scaleY = editorScale
                                    alpha = (p * 1.25f).coerceIn(0f, 1f)
                                    shape = editorShape
                                    clip = true
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {}
                        ) {
                            if (usesNativeMaterialMotion()) {
                                val nativeCardMaterial =
                                    com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardRenderer.current != null &&
                                        com.xiaomanjun.sleepdownschedule.core.performance.effectiveAppMaterialPolicy().denseMaterials
                                CourseGlassCard(
                                    backdrop = editorContentBackdrop,
                                    config = config,
                                    course = editorTarget.course,
                                    muted = editorEntry.muted,
                                    expandedOutlineLight = true,
                                    followTouch = false,
                                    nativeEdgeEffects = true,
                                    nativeCaustic = false,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .glassBackdropProducer(editorFormBackdrop),
                                    shape = editorShape,
                                    blurOverride = if (nativeCardMaterial) null else 22f,
                                    nativeBlurRadius = if (nativeCardMaterial) 22.dp else null
                                ) {}
                            } else {
                                val base = if (editorEntry.muted) MutedCourseLightColor else courseCardBaseColor(config, editorTarget.course)
                                val tintAlpha = courseGlassTintAlpha(config.cardAlpha, LocalGlassQuality.current, config.hasAnyWallpaper())
                                MorphDialogSurface(
                                    surfaceColor = base.copy(alpha = tintAlpha),
                                    shape = editorShape,
                                    modifier = Modifier.fillMaxSize(),
                                    surfaceBackdrop = editorFormBackdrop,
                                    bottomLitTint = true,
                                    backdrop = editorContentBackdrop,
                                    blurRadius = config.courseCardBlur.coerceIn(20f, 30f).dp * LocalGlassQuality.current
                                ) {}
                            }
                            // Keep the scrolling form in a sibling container so its producer/consumer
                            // hierarchy stays identical to CourseEditorContainerOverlayHost.
                            val foregroundColor = if (!usesNativeMaterialMotion()) {
                                glassForegroundColor(config)
                            } else {
                                editorTextColor
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(editorShape)
                            ) {
                                GlassMiuixSettingsTheme(settingsVisualConfig(config)) {
                                    CompositionLocalProvider(
                                        LocalContentColor provides foregroundColor,
                                        LocalNativeMaterialForeground provides foregroundColor
                                    ) {
                                        NormalizedCourseEditorScreen(
                                            formData = formData,
                                            initialCourse = editorTarget.course,
                                            contextMessage = editorTarget.contextMessage,
                                            onCancel = {
                                                focusManager.clearFocus(force = true)
                                                keyboard?.hide()
                                                controller.closeEditor()
                                            },
                                            onDismissHandlerChange = { contentDismissHandler = it },
                                            onSave = {},
                                            onSaveCourses = { onSaveCourse(emptyList(), it, editorTarget.targetWeek) },
                                            onSaveGroup = { originals, edited ->
                                                onSaveCourse(originals, edited, editorTarget.targetWeek)
                                            },
                                            onDelete = {},
                                            onDeleteGroup = { courses ->
                                                onDeleteCourse(courses, editorTarget.targetWeek)
                                            },
                                            backdrop = editorFormBackdrop,
                                            pickerRenderInRootScaffold = true,
                                            renderPagerIndicator = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            BackHandler(enabled = !isEditorHosted) {
                controller.dismiss()
            }
            OverlapChromeContent(onCloseClick = { controller.dismiss() })
            // Animated flight layer: each card flies individually along a 2D parabolic arc with
            // staggered timing and compact->expanded crossfade, drawing bottom-to-top.
            entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    val srcRect = sourceRectFor(entry)
                    val dstRect = targetRectInScreenFor(index, entry)
                    val targetHeightDp = with(density) { dstRect.height.toDp().coerceAtLeast(1.dp) }
                    val targetCornerPx = with(density) { targetCornerFor(targetHeightDp).toPx() }
                    val motion = ScheduleOverlapMotion.computeCardMotion(
                        rawProgress = rawProgress,
                        closing = closing,
                        index = index,
                        totalCards = entries.size,
                        sourceRect = srcRect,
                        targetRect = dstRect,
                        sourceCornerPx = session.sourceCornerPx,
                        targetCornerPx = targetCornerPx,
                        interruptedFromRect = controller.interruptedFromRects?.get(entry.key),
                        interruptedStartRaw = controller.interruptedStartRaw
                    )
                    controller.liveCardRects[entry.key] = motion.rect

                    val cornerDp = with(density) { motion.cornerRadiusPx.toDp().coerceAtLeast(0.dp) }
                    val widthDp = with(density) { motion.rect.width.toDp().coerceAtLeast(1.dp) }
                    val heightDp = with(density) { motion.rect.height.toDp().coerceAtLeast(1.dp) }

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    motion.rect.left.roundToInt(),
                                    motion.rect.top.roundToInt()
                                )
                            }
                            .size(width = widthDp, height = heightDp)
                            .zIndex(index.toFloat())
                    ) {
                        CourseGlassCard(
                            backdrop = cardBackdrop,
                            config = config,
                            course = entry.course,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedRectangle(cornerDp),
                            expandedOutlineLight = true,
                            nativeEdgeEffects = true,
                            nativeCaustic = false,
                            followTouch = false,
                            muted = entry.muted,
                            onClick = null
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (motion.compactOpacity > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = motion.compactOpacity
                                                scaleX = motion.compactScale
                                                scaleY = motion.compactScale
                                            }
                                    ) {
                                        WeekCourseOverlayCardContent(
                                            course = entry.course,
                                            config = config,
                                            muted = entry.muted
                                        )
                                    }
                                }
                                if (motion.expandedOpacity > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = motion.expandedOpacity
                                                scaleX = motion.expandedScale
                                                scaleY = motion.expandedScale
                                            }
                                    ) {
                                        ExpandedOverlapCardBody(
                                            entry = entry,
                                            periods = periods,
                                            config = config,
                                            backdrop = cardBackdrop,
                                            cardWidth = cardWidthDp,
                                            cardHeight = heightDp,
                                            cardCorner = cornerDp
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
}

@Composable
private fun OverlapCloseButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lightGlass = LocalAdaptiveGlass.current.lightGlass
    val native = LocalNativeMaterialSurfaceRenderer.current
    val contentTint = if (lightGlass) Color.Black.copy(alpha = 0.85f) else Color.White
    if (native?.invoke(
            NativeMaterialSurfaceRequest(
                modifier = modifier,
                shape = Capsule(),
                dark = !lightGlass,
                followTouch = true,
                onClick = onClick
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭重叠课程预览",
                    tint = contentTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        } == true
    ) {
        return
    }
    val baseSurfaceColor = if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF121212)
    val buttonSurfaceColor = baseSurfaceColor.copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass))
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 40.dp,
            surfaceColor = buttonSurfaceColor,
            contentPadding = PaddingValues(0.dp),
            blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false,
            shadowEnabled = lightGlass,
            shadowStyle = com.xiaomanjun.sleepdownschedule.core.ui.chrome.LightTopBarButtonShadow,
            pressExpansion = 3.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭重叠课程预览",
                    tint = LocalAdaptiveGlass.current.contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier,
            onClick = onClick
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭重叠课程预览",
                    tint = contentTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedOverlapCard(
    entry: ScheduleOverlapEntry,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    cardBackdrop: Backdrop?,
    cardWidth: Dp,
    cardHeight: Dp,
    cornerRadius: Dp,
    onClick: (() -> Unit)?
) {
    CourseGlassCard(
        backdrop = cardBackdrop,
        config = config,
        course = entry.course,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedRectangle(cornerRadius),
        expandedOutlineLight = true,
        nativeEdgeEffects = true,
        nativeCaustic = false,
        followTouch = onClick != null,
        muted = entry.muted,
        onClick = onClick
    ) {
        ExpandedOverlapCardBody(
            entry = entry,
            periods = periods,
            config = config,
            backdrop = cardBackdrop,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            cardCorner = cornerRadius
        )
    }
}

@Composable
internal fun ExpandedOverlapCardBody(
    entry: ScheduleOverlapEntry,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    cardWidth: Dp,
    cardHeight: Dp,
    cardCorner: Dp,
    courseOverride: CourseEntity? = null
) {
    val course = courseOverride ?: entry.course
    val pageForeground = homeForegroundColor(config)
    val textColor = homeCourseTextColor(config, course, pageForeground, entry.muted)
    val secondaryColor = textColor.copy(alpha = 0.80f)
    val compactVertical = cardHeight < 108.dp
    val compactHorizontal = cardWidth < 136.dp
    val timeText = remember(course, periods) {
        courseTimeLabel(course, periods)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactHorizontal) 10.dp else 12.dp,
                    vertical = if (compactVertical) 8.dp else 12.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = course.name,
                color = textColor,
                fontSize = if (compactVertical || compactHorizontal) 14.sp else 16.sp,
                lineHeight = if (compactVertical || compactHorizontal) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (compactVertical) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!course.location.isNullOrBlank()) {
                Spacer(Modifier.height(if (compactVertical) 2.dp else 4.dp))
                Text(
                    text = course.location.orEmpty(),
                    color = secondaryColor,
                    fontSize = if (compactHorizontal) 11.5.sp else 12.5.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (compactVertical) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!compactVertical && !course.teacher.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = course.teacher.orEmpty(),
                    color = secondaryColor,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (timeText.isNotBlank()) {
                Spacer(Modifier.height(if (compactVertical) 3.dp else 6.dp))
                Text(
                    text = timeText,
                    color = textColor.copy(alpha = 0.74f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        entry.adjustmentLabel?.let { label ->
            CourseAdjustmentBadge(
                label = label,
                backdrop = backdrop,
                config = config,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .courseBadgeCornerAnchor(cardCorner)
            )
        }
    }
}

private fun overlapWeekdayTitle(weekday: Int): String = when (weekday) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    7 -> "星期日"
    else -> "周$weekday"
}
