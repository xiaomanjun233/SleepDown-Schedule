package com.example.courseschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel

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
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            32.dp
        ).coerceAtLeast(280.dp)
    val dialogWidth = (configuration.screenWidthDp.dp * 0.92f).coerceAtMost(600.dp)
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
                    .background(Color.Black.copy(alpha = if (lightGlass) 0.12f else 0.20f)),
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
    onConfirm: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 14.dp)
    ) {
        DialogLiquidButton(
            backdrop,
            "取消",
            onDismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 4.dp),
            role = DialogButtonRole.Cancel
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
                "保存",
                onConfirm,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = 4.dp),
                role = DialogButtonRole.Confirm
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
fun LiquidAlertSurface(
    title: String,
    message: String,
    actions: List<LiquidAlertAction>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    require(actions.size in 1..3) { "LiquidAlertSurface supports one to three actions" }
    LiquidDialogSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        size = LiquidDialogSize.Compact,
        blurRadius = 28.dp
    ) {
        LiquidAlertContent(
            title = title,
            message = message,
            actions = actions,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = glassForegroundColor(config)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            color = glassForegroundColor(config).copy(alpha = 0.68f)
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
        surfaceModifier = quickSheetBackdropModifier(
            backdrop = backdrop,
            config = config,
            blurRadius = 28.dp,
            centered = true
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
    LiquidAlertDialog(
        title = title,
        message = message,
        actions = actions,
        backdrop = backdrop,
        config = config,
        onDismissRequest = onDismissRequest
    )
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
        LiquidAlertActionStyle.Secondary -> glassForegroundColor(config)
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
