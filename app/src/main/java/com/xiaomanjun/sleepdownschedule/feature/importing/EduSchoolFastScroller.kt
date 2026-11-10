package com.xiaomanjun.sleepdownschedule.feature.importing

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.shape.CouiCapsuleShape
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val FastScrollEasing = CubicBezierEasing(.33f, 0f, .67f, 1f)
private val FastScrollTargetSize = 48.dp

@Composable
internal fun EduSchoolFastScroller(
    listState: LazyListState,
    sectionPositions: Map<String, Int>,
    dark: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (sectionPositions.size < 2) return
    val sections = remember(sectionPositions) { sectionPositions.entries.toList() }
    val scrollable by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount >= 16 && (listState.canScrollBackward || listState.canScrollForward)
        }
    }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var letter by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val dragging by remember { derivedStateOf { dragProgress != null } }
    LaunchedEffect(scrollable, listState.isScrollInProgress, dragging) {
        if (scrollable && (listState.isScrollInProgress || dragging)) {
            visible = true
        } else {
            delay(2_000)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible && scrollable) 1f else 0f,
        tween(160, easing = FastScrollEasing), label = "edu-fast-scroll-visibility")
    val press by animateFloatAsState(if (dragging) 1f else 0f,
        tween(200, easing = FastScrollEasing), label = "edu-fast-scroll-press")
    if (!scrollable || (!visible && alpha == 0f)) return

    val listProgress by remember(listState) {
        derivedStateOf {
            val indicator = listState.scrollIndicatorState
            when {
                !listState.canScrollBackward -> 0f
                !listState.canScrollForward -> 1f
                indicator == null -> 0f
                else -> (indicator.scrollOffset.toFloat() /
                    (indicator.contentSize - indicator.viewportSize).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    val latestProgress = rememberUpdatedState(listProgress)
    val surface = if (dark) Color(0xFF333333) else Color.White
    val ink = if (dark) Color.White else Color.Black
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // App management drags the thumb continuously and jumps the list at section boundaries.
    fun sectionTarget(fraction: Float): Pair<Int, String> {
        val lastItem = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        val position = (fraction * lastItem).toInt()
        val section = sections.lastOrNull { it.value <= position } ?: sections.first()
        val target = when (fraction) {
            0f -> 0
            1f -> lastItem
            else -> section.value.coerceAtMost(lastItem)
        }
        return target to section.key
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(top = topPadding, bottom = bottomPadding)) {
        if (maxHeight < 150.dp) return@BoxWithConstraints
        val travel = with(density) { (maxHeight - FastScrollTargetSize).toPx() }
        fun thumbOffset() = IntOffset(0, ((dragProgress ?: listProgress) * travel).roundToInt())

        if (dragging || press > 0f) {
            Box(Modifier.align(Alignment.TopEnd).offset { thumbOffset() }.padding(end = 53.dp)
                .size(FastScrollTargetSize).graphicsLayer { this.alpha = press * alpha }
                .shadow(8.dp, CouiCapsuleShape).background(surface, CouiCapsuleShape),
                contentAlignment = Alignment.Center) {
                Text(letter, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset { thumbOffset() }.size(FastScrollTargetSize)
                .semantics {
                    contentDescription = "按字母快速定位"
                    progressBarRangeInfo = ProgressBarRangeInfo(dragProgress ?: listProgress, 0f..1f)
                    setProgress { value ->
                        val (target, _) = sectionTarget(value.coerceIn(0f, 1f))
                        listState.requestScrollToItem(target)
                        true
                    }
                }
                .pointerInput(listState, sectionPositions, travel, haptic) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var progress = latestProgress.value
                        var previousTarget = -1
                        var lastTick = 0L
                        dragProgress = progress
                        letter = sections.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key
                            ?: sections.first().key
                        try {
                            verticalDrag(down.id) { change ->
                                progress = (progress + change.positionChange().y / travel).coerceIn(0f, 1f)
                                dragProgress = progress
                                val (target, nextLetter) = sectionTarget(progress)
                                if (target != previousTarget) {
                                    previousTarget = target
                                    listState.requestScrollToItem(target)
                                }
                                if (nextLetter != letter) {
                                    letter = nextLetter
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastTick >= 100L) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastTick = now
                                    }
                                }
                                change.consume()
                            }
                        } finally {
                            dragProgress = null
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(26.dp, 38.dp).graphicsLayer {
                scaleX = 1f + press * .26f
                scaleY = scaleX
                this.alpha = alpha
            }.shadow(7.dp, CouiCapsuleShape).background(surface, CouiCapsuleShape),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(6.dp, 12.dp).drawWithCache {
                    val arrows = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height / 3f)
                        lineTo(0f, size.height / 3f)
                        close()
                        moveTo(size.width / 2f, size.height)
                        lineTo(size.width, size.height * 2f / 3f)
                        lineTo(0f, size.height * 2f / 3f)
                        close()
                    }
                    onDrawBehind { drawPath(arrows, ink) }
                })
            }
        }
    }
}
