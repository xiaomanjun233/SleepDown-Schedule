package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassCoordinatesFrozen
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
import kotlin.math.abs
import kotlin.math.min

private const val SwitchGroupCount = 6
private const val SwitchGroupDelayMillis = 18
private val SwitchContentSpring = spring<Float>(dampingRatio = 0.74f, stiffness = 260f, visibilityThreshold = 0.0015f)
// Only the content tracks overshoot. The wallpaper/page settles monotonically underneath them.
private val SwitchPageMotion = tween<Float>(360, easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f))
// Nexio's main-tab motion: one non-bouncy page translation, without per-card tracks.
private val SwitchPlainSlide = spring<Float>(dampingRatio = 1f, stiffness = 320f, visibilityThreshold = 0.0015f)

internal enum class HomeSwitchClip { None, Page, TopBar }

/** Sparse pages stagger content; pages with more than ten cards move as a single surface. */
@Stable
internal class HomeSwitchMotion(
    initialSecondary: Boolean,
    private val target: State<Boolean>,
    val landscape: Boolean = false,
    private val renderedCardCount: () -> Int = { 0 }
) {
    private val page = Animatable(if (initialSecondary) 1f else 0f)
    private val tracks = List(SwitchGroupCount) { Animatable(if (initialSecondary) 1f else 0f) }
    // Animatable resets velocity on cancellation. Keep the last frame for a continuous reversal.
    private var pageVelocity = 0f
    private val velocities = FloatArray(SwitchGroupCount)
    private var settledSecondary by mutableStateOf(initialSecondary)
    private var running by mutableStateOf(false)
    // Latch once per switch (including reversals), so a count update cannot jump live cards.
    private var latchedPlainSlide by mutableStateOf(false)
    // Decide while idle as data loads, before the first moving frame mounts card layers.
    val plainSlide: Boolean get() = if (running) latchedPlainSlide else renderedCardCount() > 10
    val progress: State<Float> = page.asState()
    val moving: Boolean get() = running || settledSecondary != target.value

    // Keep sampling throughout the stagger and the spring's overshoot/settling frames.
    // All glass consumers share one immutable key per frame instead of rebuilding the same
    // lists for every card's sample pass. State reads remain in drawing, outside composition.
    val sampleKey: List<Float> by derivedStateOf {
        if (plainSlide) listOf(page.value) else listOf(page.value) + tracks.map { it.value }
    }
    val pageSampleKey: Any by derivedStateOf { Triple(page.value, cornerFraction, moving) }

    val cornerFraction: Float by derivedStateOf {
            // Follow the whole motion envelope. Clamping progress to 0..1 erased the corners
            // at the first endpoint crossing, exactly when the spring started rebounding.
            fun edgeDistance(value: Float) = min(abs(value), abs(1f - value))
            val distance = if (plainSlide) edgeDistance(page.value)
                else maxOf(edgeDistance(page.value), tracks.maxOf { edgeDistance(it.value) })
            val fraction = (distance / 0.06f).coerceIn(0f, 1f)
            fraction * fraction * (3f - 2f * fraction)
        }

    fun retains(secondary: Boolean): Boolean = moving || settledSecondary == secondary
    fun groupProgress(group: Int): Float = if (plainSlide) page.value
        else tracks[group.coerceIn(0, SwitchGroupCount - 1)].value

    suspend fun settleAt(secondary: Boolean) {
        latchedPlainSlide = renderedCardCount() > 10
        val destination = if (secondary) 1f else 0f
        page.snapTo(destination)
        tracks.forEach { it.snapTo(destination) }
        pageVelocity = 0f
        velocities.fill(0f)
        settledSecondary = secondary
        running = false
    }

    suspend fun animateTo(secondary: Boolean) {
        if (!running) latchedPlainSlide = renderedCardCount() > 10
        val destination = if (secondary) 1f else 0f
        if (page.value == destination && pageVelocity == 0f &&
            tracks.all { it.value == destination } && velocities.all { it == 0f }) {
            settledSecondary = secondary
            running = false
            return
        }
        // On reversal keep every group's current position, without another initial pause.
        val settledPosition = if (settledSecondary) 1f else 0f
        val interrupted = page.value != settledPosition || pageVelocity != 0f ||
            tracks.any { it.value != settledPosition } || velocities.any { it != 0f }
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        running = true
        var completed = false
        try {
            if (plainSlide || landscape) {
                page.animateTo(destination,
                    animationSpec = if (plainSlide) SwitchPlainSlide else SwitchPageMotion,
                    initialVelocity = pageVelocity) { pageVelocity = velocity }
                pageVelocity = 0f
                tracks.forEach { it.snapTo(destination) }
                velocities.fill(0f)
                settledSecondary = secondary
                completed = true
                return
            }
            coroutineScope {
                launch {
                    page.animateTo(destination, animationSpec = SwitchPageMotion) {
                        pageVelocity = velocity
                    }
                    pageVelocity = 0f
                }
                tracks.forEachIndexed { group, track ->
                    launch {
                        if (!interrupted) delay((group * SwitchGroupDelayMillis * durationScale).toLong())
                        track.animateTo(destination, animationSpec = SwitchContentSpring,
                            initialVelocity = velocities[group]) {
                            velocities[group] = velocity
                        }
                        velocities[group] = 0f
                    }
                }
            }
            settledSecondary = secondary
            completed = true
        } finally {
            // An interrupted LaunchedEffect is replaced with a new target. Clearing this flag
            // between cancellation and restart would unmount the outgoing pane and its groups
            // while their Animatables still hold an in-flight position.
            if (completed) running = false
        }
    }
}

@Composable
internal fun rememberHomeSwitchMotion(
    secondary: Boolean,
    label: String,
    animate: Boolean = true,
    renderedCardCount: () -> Int = { 0 }
): HomeSwitchMotion {
    val target = rememberUpdatedState(secondary)
    val latestCardCount = rememberUpdatedState(renderedCardCount)
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val motion = remember(label, landscape) {
        HomeSwitchMotion(secondary, target, landscape) { latestCardCount.value() }
    }
    LaunchedEffect(motion, secondary, animate) {
        if (animate) motion.animateTo(secondary) else motion.settleAt(secondary)
    }
    return motion
}

@Composable
internal fun Modifier.homeSwitchLayer(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    pageClip: HomeSwitchClip = HomeSwitchClip.None
): Modifier {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return graphicsLayer {
        if (motion.landscape && !motion.plainSlide) {
            translationX = 0f
            val visibility = (if (secondary) motion.progress.value else 1f - motion.progress.value).coerceIn(0f, 1f)
            alpha = visibility
            scaleX = 0.96f + 0.04f * visibility
            scaleY = scaleX
            val blur = (1f - visibility) * 10.dp.toPx()
            renderEffect = if (motion.moving && blur > 0.5f) BlurEffect(blur, blur, TileMode.Clamp) else null
            clip = motion.moving
            shape = if (clip) RoundedCornerShape(24.dp) else RectangleShape
        } else {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            renderEffect = null
            translationX = direction * size.width * ((if (secondary) 1f else 0f) - motion.progress.value)
            // Both motion styles keep the original rounded page clip while moving.
            clip = pageClip != HomeSwitchClip.None && motion.moving
            shape = if (clip) {
                val radius = 32.dp * motion.cornerFraction
                val bottom = if (pageClip == HomeSwitchClip.TopBar) 0.dp else radius
                RoundedCornerShape(topStart = radius, topEnd = radius, bottomEnd = bottom, bottomStart = bottom)
            } else RectangleShape
        }
    }
}

private class SwitchPageScope(val motion: HomeSwitchMotion, val width: State<Int>, val direction: Float)
private val LocalSwitchPages = staticCompositionLocalOf<List<SwitchPageScope>> { emptyList() }

/** Apply once per real content group, so text, cards and their decorations move together. */
@Composable
internal fun Modifier.homeSwitchGroup(cardOrderFraction: Float? = null): Modifier {
    val pages = LocalSwitchPages.current
    if (pages.isEmpty() || pages.all { it.motion.landscape || it.motion.plainSlide }) return this
    val group = remember { mutableIntStateOf(-1) }
    val tracked = onGloballyPositioned { coordinates ->
        if (group.intValue < 0 || pages.none { it.motion.moving }) {
            val height = coordinates.findRootCoordinates().size.height.coerceAtLeast(1)
            val top = coordinates.localToRoot(Offset.Zero).y
            // A timetable card's place among real cards matters even when scrolling puts several
            // cards in the same screen band. Blend that order with its current visible position.
            val screenFraction = (top / height).coerceIn(0f, 1f)
            val fraction = cardOrderFraction?.let { order ->
                (order.coerceIn(0f, 1f) * 0.55f + screenFraction * 0.45f)
            } ?: screenFraction
            group.intValue = (fraction * SwitchGroupCount).toInt().coerceIn(0, SwitchGroupCount - 1)
        }
    }
    // Sparse pages retain their stagger layer. Dense pages returned above: one page transform
    // moves all cards, without retaining a redundant transform RenderNode on every card.
    return tracked.graphicsLayer {
        translationX = pages.sumOf { page ->
            // A plain slide never observes per-frame page progress in each card's layer.
            if (!page.motion.moving || page.motion.plainSlide || page.motion.landscape) 0.0
            else (page.direction * page.width.value *
                (page.motion.progress.value - page.motion.groupProgress(group.intValue))).toDouble()
        }.toFloat()
        clip = false
    }
}

@Composable
internal fun HomeSwitchPane(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    modifier: Modifier = Modifier,
    pageClip: HomeSwitchClip = HomeSwitchClip.None,
    retainContent: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val visible = motion.retains(secondary)
    // Keep expensive timetable composition ready after the first idle interval. Hidden pages
    // do not draw, sample glass, tick their minute clocks or publish accessibility content.
    var warmed by remember { mutableStateOf(false) }
    LaunchedEffect(retainContent, motion.moving) {
        if (retainContent && !motion.moving && !warmed) {
            delay(200)
            warmed = true
        }
    }
    if (!visible && !(retainContent && warmed)) return
    val width = remember { mutableIntStateOf(0) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val parents = LocalSwitchPages.current
    val pages = remember(parents, motion, direction) { parents + SwitchPageScope(motion, width, direction) }
    val inputModifier = if (motion.moving) {
        Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else Modifier
    Box(
        modifier
            // Alpha/draw suppression does not remove a retained pane from hit testing. In
            // landscape the hidden Week pane shares Day's origin and otherwise eats its taps.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (visible) placeable.place(0, 0)
                }
            }
            .drawWithContent { if (visible) drawContent() }
            .onSizeChanged { width.intValue = it.width }
            .homeSwitchLayer(motion, secondary, pageClip).then(inputModifier),
        contentAlignment = contentAlignment
    ) {
        val parentFrozen = LocalGlassCoordinatesFrozen.current
        val parentKey = LocalGlassSampleRecordKey.current
        val hiddenKey = remember { Any() }
        val currentVisible = rememberUpdatedState(visible)
        val currentParentFrozen = rememberUpdatedState(parentFrozen)
        val currentParentKey = rememberUpdatedState(parentKey)
        // The callbacks read changing visibility in drawing. Replacing the callbacks would
        // instead invalidate every retained glass consumer when this pane appears/disappears.
        val frozen = remember { { !currentVisible.value || currentParentFrozen.value() } }
        val sampleKey = remember { { if (currentVisible.value) currentParentKey.value() else hiddenKey } }
        val parentVisible = LocalHomePaneVisible.current
        val parentBackgroundFrozen = LocalHomeBackgroundFrozen.current
        val parentTextFrozen = LocalHomeTextContrastFrozen.current
        val paneVisible = remember(parentVisible) {
            derivedStateOf { parentVisible.value && currentVisible.value }
        }
        val backgroundFrozen = remember(parentBackgroundFrozen) {
            derivedStateOf { parentBackgroundFrozen.value || !currentVisible.value }
        }
        val textFrozen = remember(parentTextFrozen) {
            derivedStateOf { parentTextFrozen.value || !currentVisible.value }
        }
        CompositionLocalProvider(
            LocalSwitchPages provides pages,
            LocalHomePaneVisible provides paneVisible,
            LocalGlassCoordinatesFrozen provides frozen,
            LocalGlassSampleRecordKey provides sampleKey,
            LocalHomeBackgroundFrozen provides backgroundFrozen,
            LocalHomeTextContrastFrozen provides textFrozen
        ) { content() }
    }
}

/** A retained day page must not leak neighbouring cards through the outer page's rebound. */
@Composable
internal fun HomeDayPageDrawingScope(pager: PagerState, page: Int, content: @Composable () -> Unit) {
    val homeSwitching = LocalHomeTextContrastFrozen.current
    val visible by remember(pager, page, homeSwitching) {
        derivedStateOf {
            if (homeSwitching.value) page == pager.settledPage else {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                page > position - 1f && page < position + 1f
            }
        }
    }
    val parentFrozen = LocalGlassCoordinatesFrozen.current
    val parentKey = LocalGlassSampleRecordKey.current
    val hiddenKey = remember(page) { Any() }
    val currentVisible = rememberUpdatedState(visible)
    val currentParentFrozen = rememberUpdatedState(parentFrozen)
    val currentParentKey = rememberUpdatedState(parentKey)
    val frozen = remember { { !currentVisible.value || currentParentFrozen.value() } }
    val key = remember(pager, hiddenKey) {
        derivedStateOf {
            if (currentVisible.value) Pair(currentParentKey.value(), pager.currentPage + pager.currentPageOffsetFraction) else hiddenKey
        }
    }
    val sampleKey = remember(key) { { key.value } }
    val parentVisible = LocalHomePaneVisible.current
    val parentBackgroundFrozen = LocalHomeBackgroundFrozen.current
    val paneVisible = remember(parentVisible) {
        derivedStateOf { parentVisible.value && currentVisible.value }
    }
    val backgroundFrozen = remember(parentBackgroundFrozen) {
        derivedStateOf { parentBackgroundFrozen.value || !currentVisible.value }
    }
    val textFrozen = remember(homeSwitching, pager) {
        derivedStateOf { homeSwitching.value || !currentVisible.value || pager.isScrollInProgress }
    }
    CompositionLocalProvider(
        LocalGlassCoordinatesFrozen provides frozen,
        LocalGlassSampleRecordKey provides sampleKey,
        LocalHomePaneVisible provides paneVisible,
        LocalHomeBackgroundFrozen provides backgroundFrozen,
        LocalHomeTextContrastFrozen provides textFrozen
    ) {
        Box(Modifier.drawWithContent { if (visible) drawContent() }) { content() }
    }
}
