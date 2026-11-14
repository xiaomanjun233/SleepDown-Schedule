package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberPageMotionBlur
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeTextContrastFrozen
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassCoordinatesFrozen
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

private const val SwitchGroupCount = HomeMotionSpec.FollowGroups

internal enum class HomeSwitchClip { None, Page, TopBar }

/**
 * One frame clock evaluates the page and six delayed springs. Content can overshoot after
 * the page has arrived; cancellation preserves each track's position and velocity.
 */
@Stable
internal class HomeSwitchMotion(
    initialSecondary: Boolean,
    private val target: State<Boolean>,
    val landscape: Boolean = false,
) {
    private val page = mutableFloatStateOf(if (initialSecondary) 1f else 0f)
    private val tracks = List(SwitchGroupCount) { mutableFloatStateOf(page.floatValue) }
    private val velocities = FloatArray(SwitchGroupCount)
    private var settledSecondary by mutableStateOf(initialSecondary)
    private var running by mutableStateOf(false)
    private var frame by mutableIntStateOf(0)
    val progress: State<Float> = page
    val moving: Boolean get() = running || settledSecondary != target.value
    val sampleKey: Int get() = frame
    val pageSampleKey: Any by derivedStateOf { Triple(page.floatValue, cornerFraction, moving) }
    val cornerFraction: Float by derivedStateOf {
        fun distance(value: Float) = min(abs(value), abs(1f - value))
        val envelope = maxOf(distance(page.floatValue), tracks.maxOf { distance(it.floatValue) })
        HomeMotionSpec.backdropProgress((envelope / 0.06f).coerceIn(0f, 1f))
    }

    fun retains(secondary: Boolean): Boolean = moving || settledSecondary == secondary
    fun groupProgress(group: Int): Float = if (landscape) page.floatValue
        else tracks[group.coerceIn(0, SwitchGroupCount - 1)].floatValue

    suspend fun settleAt(secondary: Boolean) {
        val end = if (secondary) 1f else 0f
        page.floatValue = end
        tracks.forEach { it.floatValue = end }
        velocities.fill(0f)
        frame++
        settledSecondary = secondary
        running = false
    }

    suspend fun animateTo(secondary: Boolean) {
        val end = if (secondary) 1f else 0f
        if (page.floatValue == end && tracks.all { it.floatValue == end } &&
            velocities.all { it == 0f }) {
            settleAt(secondary)
            return
        }
        val interrupted = running
        val context = currentCoroutineContext()
        if ((context[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) {
            settleAt(secondary)
            return
        }
        val pageAnimation = TargetBasedAnimation(HomeMotionSpec.page, Float.VectorConverter,
            page.floatValue, end)
        val followers = tracks.mapIndexed { index, track ->
            TargetBasedAnimation(HomeMotionSpec.follower, Float.VectorConverter,
                track.floatValue, end, velocities[index])
        }
        running = true
        var playTime = 0L
        var previousTime = withFrameNanos { it }
        while (true) {
            val durationScale = context[MotionDurationScale]?.scaleFactor ?: 1f
            if (durationScale <= 0f) break
            var complete = pageAnimation.isFinishedFromNanos(playTime)
            page.floatValue = pageAnimation.getValueFromNanos(playTime)
            followers.forEachIndexed { group, animation ->
                val delay = if (interrupted || landscape) 0L
                    else group * HomeMotionSpec.FollowDelayMillis * 1_000_000L
                val time = (playTime - delay).coerceAtLeast(0L)
                val done = landscape || (playTime >= delay && animation.isFinishedFromNanos(time))
                tracks[group].floatValue = if (landscape) page.floatValue else animation.getValueFromNanos(time)
                velocities[group] = if (done) 0f else animation.getVelocityVectorFromNanos(time).value
                complete = complete && done
            }
            frame++
            if (complete) break
            val now = withFrameNanos { it }
            playTime += ((now - previousTime) / durationScale).toLong()
            previousTime = now
        }
        // Cancellation never reaches this handoff: both panes remain mounted for reversal.
        settleAt(secondary)
    }
}

@Composable
internal fun rememberHomeSwitchMotion(
    secondary: Boolean,
    label: String,
    animate: Boolean = true,
): HomeSwitchMotion {
    val target = rememberUpdatedState(secondary)
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val motion = remember(label, landscape) { HomeSwitchMotion(secondary, target, landscape) }
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
    val motionBlur = rememberPageMotionBlur()
    return graphicsLayer {
        if (motion.landscape) {
            translationX = 0f
            val visibility = (if (secondary) motion.progress.value else 1f - motion.progress.value).coerceIn(0f, 1f)
            alpha = visibility
            scaleX = 0.96f + 0.04f * visibility
            scaleY = scaleX
            renderEffect = if (motion.moving) motionBlur((1f - visibility) * 10f) else null
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
    if (pages.isEmpty() || pages.all { it.motion.landscape }) return this
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
    // The local layer applies only the spatial offset derived from the shared page clock.
    return tracked.graphicsLayer {
        translationX = pages.sumOf { page ->
            // A settled page has no follower transform.
            if (!page.motion.moving || page.motion.landscape) 0.0
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
        val movingSample = remember(motion) { Any() }
        val frozen = remember(motion) {
            { !currentVisible.value || currentParentFrozen.value() || motion.landscape && motion.moving }
        }
        val sampleKey = remember(motion) {
            { if (!currentVisible.value) hiddenKey else if (motion.landscape && motion.moving) movingSample else currentParentKey.value() }
        }
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
    val hd = LocalHomeAdaptiveMetrics.current?.isLargeScreen == true
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
    val movingSample = remember(pager, page) { Any() }
    val frozen = remember(hd, pager) {
        { !currentVisible.value || currentParentFrozen.value() || hd && pager.isScrollInProgress }
    }
    val key = remember(pager, hiddenKey, hd) {
        derivedStateOf {
            if (!currentVisible.value) hiddenKey else if (hd && pager.isScrollInProgress) movingSample
            else Pair(currentParentKey.value(), pager.currentPage + pager.currentPageOffsetFraction)
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
