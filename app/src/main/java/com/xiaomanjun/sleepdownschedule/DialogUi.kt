package com.xiaomanjun.sleepdownschedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlinx.coroutines.delay

enum class LiquidDialogSize {
    Standard,
    Compact
}

enum class LiquidAlertActionStyle {
    Primary,
    Secondary,
    Destructive
}

data class LiquidAlertAction(
    val label: String,
    val style: LiquidAlertActionStyle,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun LiquidDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    size: LiquidDialogSize = LiquidDialogSize.Standard,
    blurRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val windowSize = currentWindowSizeDp()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeHeight = (
        windowSize.height -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            32.dp
        ).coerceAtLeast(280.dp)
    val dialogWidth = (windowSize.width * 0.92f).coerceAtMost(600.dp)
    val dialogMaxHeight = (safeHeight * 0.82f).coerceAtMost(600.dp)
    val shape = RoundedCornerShape(32.dp)
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
    val surfaceColor = if (lightGlass) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFF121212).copy(alpha = 0.28f)
    }
    val panelModifier = modifier
        .width(dialogWidth)
        .then(
            if (size == LiquidDialogSize.Standard) {
                Modifier.height(dialogMaxHeight)
            } else {
                Modifier.heightIn(max = dialogMaxHeight)
            }
        )

    val panelContent: @Composable BoxScope.() -> Unit = {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Box(
                modifier = (if (size == LiquidDialogSize.Standard) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .clip(shape)
                    .background(Color.Black.copy(alpha = if (lightGlass) 0.035f else 0.20f)),
                content = content
            )
        }
    }

    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = panelModifier,
            shape = shape,
            surfaceColor = surfaceColor,
            blurRadius = blurRadius,
            content = panelContent
        )
    } else {
        Box(
            modifier = panelModifier
                .clip(shape)
                .background(if (appUsesDarkTheme(config)) Color(0xFF1C1C1E) else Color.White),
            content = panelContent
        )
    }
}

@Composable
fun LiquidDialogHeader(
    title: String,
    onDismiss: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    buttonBlurRadius: Dp = 3.dp,
    onConfirm: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 16.dp)
    ) {
        DialogLiquidButton(
            backdrop,
            "取消",
            onDismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 4.dp),
            role = DialogButtonRole.Cancel,
            blurRadius = buttonBlurRadius
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 4.dp)
                .padding(horizontal = 58.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = glassForegroundColor(config),
            maxLines = 1
        )
        if (onConfirm != null) {
            DialogLiquidButton(
                backdrop,
                "完成",
                onConfirm,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = 4.dp),
                role = DialogButtonRole.Confirm,
                blurRadius = buttonBlurRadius
            )
        }
    }
}

@Composable
fun ColumnScope.LiquidDialogBody(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        content = content
    )
}

@Composable
fun LiquidDialogFooter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun LiquidAlertContent(
    title: String,
    message: String,
    actions: List<LiquidAlertAction>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    val foreground = appPanelForegroundColor(config)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            color = foreground.copy(alpha = 0.68f)
        )
        LiquidAlertActions(actions, backdrop, config)
    }
}

@Composable
fun LiquidAlertActions(
    actions: List<LiquidAlertAction>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    require(actions.size in 1..3) { "LiquidAlertActions supports one to three actions" }
    if (actions.size <= 2) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            actions.forEach { action ->
                LiquidAlertActionButton(action, backdrop, config, Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            actions.forEach { action ->
                LiquidAlertActionButton(action, backdrop, config, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun LiquidAlertDialog(
    title: String,
    message: String,
    actions: List<LiquidAlertAction>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var completion by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dark = appUsesDarkTheme(config)

    fun closeThen(action: () -> Unit) {
        if (!visible) return
        completion = action
        visible = false
    }

    val animatedActions = actions.map { action ->
        action.copy(onClick = {
            if (action.dismissOnClick) closeThen(action.onClick) else action.onClick()
        })
    }

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = visible,
        title = null,
        enableWindowDim = true,
        backgroundColor = Color.Transparent,
        forceCenter = true,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = backdrop,
            config = config,
            blurRadius = 28.dp,
            centered = true
        )
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (dark) Color.Black.copy(alpha = 0.38f)
                else Color.White.copy(alpha = 0.42f)
            ),
        outsideMargin = DpSize(18.dp, 18.dp),
        insideMargin = DpSize(20.dp, 18.dp),
        onDismissRequest = { closeThen(onDismissRequest) },
        onDismissFinished = {
            val action = completion
            completion = null
            action?.invoke()
        }
    ) {
        LiquidAlertContent(title, message, animatedActions, backdrop, config)
    }
}

/** Same-window alert used when the glass must sample a Compose layer below it. */
@Composable
fun LiquidAlertOverlay(
    title: String,
    message: String,
    actions: List<LiquidAlertAction>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = appUsesDarkTheme(config)
    var visible by remember { mutableStateOf(false) }
    var completion by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scrimProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "liquidAlertOverlayScrim"
    )

    fun closeThen(action: () -> Unit) {
        if (!visible) return
        completion = action
        visible = false
    }

    val animatedActions = actions.map { action ->
        action.copy(onClick = {
            if (action.dismissOnClick) closeThen(action.onClick) else action.onClick()
        })
    }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(visible) {
        if (!visible && completion != null) {
            delay(240)
            val action = completion
            completion = null
            action?.invoke()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1_000f)
            .background(
                Color.Black.copy(
                    alpha = (if (dark) 0.48f else 0.30f) * scrimProgress
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { closeThen(onDismissRequest) }
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(tween(280), initialScale = 0.90f),
            exit = fadeOut(tween(190)) + scaleOut(tween(230), targetScale = 0.92f)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        Modifier.quickSheetBackdropModifier(
                            backdrop = backdrop,
                            config = config,
                            blurRadius = 28.dp,
                            centered = true
                        )
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (dark) Color.Black.copy(alpha = 0.38f)
                        else Color.White.copy(alpha = 0.42f)
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                LiquidAlertContent(title, message, animatedActions, backdrop, config)
            }
        }
    }
}

@Composable
private fun LiquidAlertActionButton(
    action: LiquidAlertAction,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    val contentColor = when (action.style) {
        LiquidAlertActionStyle.Primary -> Color.White
        LiquidAlertActionStyle.Secondary -> appPanelForegroundColor(config)
        LiquidAlertActionStyle.Destructive -> Color(0xFFFF453A)
    }
    val dark = appUsesDarkTheme(config)
    val surfaceColor = when (action.style) {
        LiquidAlertActionStyle.Primary -> Color(0xFF0A84FF).copy(alpha = 0.90f)
        LiquidAlertActionStyle.Secondary -> if (dark) {
            Color(0xFF252A33).copy(alpha = 0.88f)
        } else {
            Color(0xFFF0F3F8).copy(alpha = 0.86f)
        }
        LiquidAlertActionStyle.Destructive -> if (dark) {
            Color(0xFF4A2024).copy(alpha = 0.82f)
        } else {
            Color(0xFFFFE7E5).copy(alpha = 0.88f)
        }
    }
    val shape = RoundedCornerShape(50)
    if (backdrop != null) {
        LiquidButton(
            onClick = action.onClick,
            backdrop = backdrop,
            modifier = modifier,
            height = 50.dp,
            surfaceColor = surfaceColor,
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 18.dp,
            lensHeight = 7.dp,
            lensAmount = 10.dp,
            chromaticAberration = false
        ) {
            Text(
                text = action.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    } else {
        Box(
            modifier = modifier
                .height(50.dp)
                .clip(shape)
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.16f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = action.onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = action.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
