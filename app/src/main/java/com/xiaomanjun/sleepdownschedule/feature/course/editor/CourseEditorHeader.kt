package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidDialogHeader
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.drawContinuousRoundRect
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBlurDirection
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme

@Composable
fun DialogHeader(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig()
) {
    LiquidDialogHeader(
        title,
        onCancel,
        backdrop,
        config,
        modifier = modifier,
        buttonBlurRadius = 8.dp,
        onConfirm = onSave
    )
}

@Composable
fun NormalizedDialogHeader(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    LiquidDialogHeader(title, onCancel, backdrop, config, onConfirm = onSave)
}

/** Fixed glass header for add/edit forms; only the fields beneath it are scrollable. */
@Composable
internal fun CourseEditorFixedHeader(
    title: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier
) {
    val dark = appUsesDarkTheme(config)
    val tint = if (dark) {
        ComposeColor(0xFF111318).copy(alpha = 0.48f)
    } else {
        ComposeColor.White.copy(alpha = 0.42f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        // Fade the header's backdrop into the form rather than drawing a hard horizontal strip.
        ProgressiveBackdropBlur(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            tintColor = tint,
            height = 70.dp,
            blurRadius = 12.dp,
            tintIntensity = 0.16f,
            direction = ProgressiveBlurDirection.TopToBottom,
            topMaskFadeStart = 0.56f,
            topMaskFadeEnd = 1f,
            topTintFadeStart = 0.50f,
            topTintFadeEnd = 1f,
            fallbackTintStops = listOf(
                0f to tint,
                0.60f to tint.copy(alpha = tint.alpha * 0.78f),
                1f to ComposeColor.Transparent
            )
        )
        LiquidDialogHeader(
            title = title,
            onDismiss = onCancel,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.fillMaxSize().then(contentModifier),
            buttonBlurRadius = 4.dp,
            onConfirm = onSave
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dotSpacingPx = with(density) { 15.dp.toPx() }
    val horizontalPaddingPx = with(density) { 12.dp.toPx() }
    val indicatorWidth = with(density) { (24 + (pageCount - 1) * 15).dp }
    Canvas(modifier = modifier.width(indicatorWidth).height(24.dp)) {
        drawContinuousRoundRect(
            color = ComposeColor.Black.copy(alpha = 0.28f),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        )
        repeat(pageCount) { page ->
            drawCircle(
                color = ComposeColor.White.copy(alpha = 0.30f),
                radius = 4.dp.toPx(),
                center = Offset(horizontalPaddingPx + page * dotSpacingPx, size.height / 2f)
            )
        }
        val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (pageCount - 1).toFloat())
        drawCircle(
            color = ComposeColor.White,
            radius = 5.dp.toPx(),
            center = Offset(horizontalPaddingPx + position * dotSpacingPx, size.height / 2f)
        )
    }
}
