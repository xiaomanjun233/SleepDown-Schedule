package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
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
// The page/background leads every content group, brakes early and has only a small rebound.
private val SwitchPageSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 700f, visibilityThreshold = 0.0015f)

internal enum class HomeSwitchClip { None, Page, TopBar }

/** The page arrives first; the accepted content springs follow in six staggered groups. */
@Stable
internal class HomeSwitchMotion(initialSecondary: Boolean, private val target: State<Boolean>) {
    private val page = Animatable(if (initialSecondary) 1f else 0f)
    private val tracks = List(SwitchGroupCount) { Animatable(if (initialSecondary) 1f else 0f) }
    // Animatable resets velocity on cancellation. Keep the last frame for a continuous reversal.
    private var pageVelocity = 0f
    private val velocities = FloatArray(SwitchGroupCount)
    private var settledSecondary by mutableStateOf(initialSecondary)
    private var running by mutableStateOf(false)
    val progress: State<Float> = page.asState()
    val moving: Boolean get() = running || settledSecondary != target.value

    // Keep sampling throughout the stagger and the spring's overshoot/settling frames.
    // All glass consumers share one immutable key per frame instead of rebuilding the same
    // lists for every card's sample pass. State reads remain in drawing, outside composition.
    val sampleKey: List<Float> by derivedStateOf { listOf(page.value) + tracks.map { it.value } }
    val pageSampleKey: Any by derivedStateOf { Triple(page.value, cornerFraction, moving) }

    val cornerFraction: Float by derivedStateOf {
            // Follow the whole motion envelope. Clamping progress to 0..1 erased the corners
            // at the first endpoint crossing, exactly when the spring started rebounding.
            fun edgeDistance(value: Float) = min(abs(value), abs(1f - value))
            val distance = maxOf(edgeDistance(page.value), tracks.maxOf { edgeDistance(it.value) })
            val fraction = (distance / 0.06f).coerceIn(0f, 1f)
            fraction * fraction * (3f - 2f * fraction)
        }

    fun retains(secondary: Boolean): Boolean = moving || settledSecondary == secondary
    fun groupProgress(group: Int): Float = tracks[group.coerceIn(0, SwitchGroupCount - 1)].value

    suspend fun animateTo(secondary: Boolean) {
        val destination = if (secondary) 1f else 0f
        if (page.value == destination && pageVelocity == 0f &&
            tracks.all { it.value == destination } && velocities.all { it == 0f }) {
            settledSecondary = secondary
            return
        }
        // On reversal keep every group's current position, without another initial pause.
        val settledPosition = if (settledSecondary) 1f else 0f
        val interrupted = page.value != settledPosition || pageVelocity != 0f ||
            tracks.any { it.value != settledPosition } || velocities.any { it != 0f }
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        running = true
        try {
            coroutineScope {
                launch {
                    page.animateTo(destination, animationSpec = SwitchPageSpring,
                        initialVelocity = pageVelocity) {
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
        } finally {
            running = false
        }
    }
}

@Composable
internal fun rememberHomeSwitchMotion(secondary: Boolean, label: String): HomeSwitchMotion {
    val target = rememberUpdatedState(secondary)
    val motion = remember(label) { HomeSwitchMotion(secondary, target) }
    LaunchedEffect(motion, secondary) { motion.animateTo(secondary) }
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
        translationX = direction * size.width * ((if (secondary) 1f else 0f) - motion.progress.value)
        // Keep rounding through the trailing content's rebound, then release the clip at rest.
        // Do not clamp the translation: the spring must be allowed to overshoot and return.
        clip = pageClip != HomeSwitchClip.None && motion.moving
        shape = if (clip) {
            val radius = 32.dp * motion.cornerFraction
            val bottom = if (pageClip == HomeSwitchClip.TopBar) 0.dp else radius
            RoundedCornerShape(topStart = radius, topEnd = radius, bottomEnd = bottom, bottomStart = bottom)
        } else RectangleShape
    }
}

private class SwitchPageScope(val motion: HomeSwitchMotion, val width: State<Int>, val direction: Float)
private val LocalSwitchPages = staticCompositionLocalOf<List<SwitchPageScope>> { emptyList() }

/** Apply once per real content group, so text, cards and their decorations move together. */
@Composable
internal fun Modifier.homeSwitchGroup(cardOrderFraction: Float? = null): Modifier {
    val pages = LocalSwitchPages.current
    if (pages.isEmpty()) return this
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
    // At rest the translation is zero. Releasing this layer keeps card glass in the same
    // clipping stack as its wallpaper sampler; the six staggered tracks are kept for motion.
    return if (pages.any { it.motion.moving }) tracked.graphicsLayer {
        translationX = pages.sumOf { page ->
            (page.direction * page.width.value *
                (page.motion.progress.value - page.motion.groupProgress(group.intValue))).toDouble()
        }.toFloat()
        clip = false
    } else tracked
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
    val inputModifier = if (motion.moving || !visible) {
        Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else Modifier
    Box(
        modifier.drawWithContent { if (visible) drawContent() }
            .onSizeChanged { width.intValue = it.width }
            .homeSwitchLayer(motion, secondary, pageClip).then(inputModifier),
        contentAlignment = contentAlignment
    ) {
        val parentFrozen = LocalGlassCoordinatesFrozen.current
        val parentKey = LocalGlassSampleRecordKey.current
        val hiddenKey = remember { Any() }
        val frozen = remember(visible, parentFrozen) { { !visible || parentFrozen() } }
        val sampleKey = remember(visible, parentKey) { { if (visible) parentKey() else hiddenKey } }
        CompositionLocalProvider(
            LocalSwitchPages provides pages,
            LocalHomePaneVisible provides (LocalHomePaneVisible.current && visible),
            LocalGlassCoordinatesFrozen provides frozen,
            LocalGlassSampleRecordKey provides sampleKey,
            LocalHomeBackgroundFrozen provides (LocalHomeBackgroundFrozen.current || !visible),
            LocalHomeTextContrastFrozen provides (LocalHomeTextContrastFrozen.current || !visible)
        ) { content() }
    }
}

/** A retained day page must not leak neighbouring cards through the outer page's rebound. */
@Composable
internal fun HomeDayPageDrawingScope(pager: PagerState, page: Int, content: @Composable () -> Unit) {
    val homeSwitching = LocalHomeTextContrastFrozen.current
    val visible by remember(pager, page, homeSwitching) {
        derivedStateOf {
            if (homeSwitching) page == pager.settledPage else {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                page > position - 1f && page < position + 1f
            }
        }
    }
    val parentFrozen = LocalGlassCoordinatesFrozen.current
    val parentKey = LocalGlassSampleRecordKey.current
    val hiddenKey = remember(page) { Any() }
    val frozen = remember(visible, parentFrozen) { { !visible || parentFrozen() } }
    val key = remember(visible, parentKey, pager) {
        derivedStateOf {
            if (visible) Pair(parentKey(), pager.currentPage + pager.currentPageOffsetFraction) else hiddenKey
        }
    }
    val sampleKey = remember(key) { { key.value } }
    CompositionLocalProvider(
        LocalGlassCoordinatesFrozen provides frozen,
        LocalGlassSampleRecordKey provides sampleKey,
        LocalHomePaneVisible provides (LocalHomePaneVisible.current && visible),
        LocalHomeBackgroundFrozen provides (LocalHomeBackgroundFrozen.current || !visible),
        LocalHomeTextContrastFrozen provides (homeSwitching || !visible || pager.isScrollInProgress)
    ) {
        Box(Modifier.drawWithContent { if (visible) drawContent() }) { content() }
    }
}
