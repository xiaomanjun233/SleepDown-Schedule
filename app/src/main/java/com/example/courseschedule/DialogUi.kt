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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val onClick: () -> Unit
)

@Composable
fun LiquidDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    size: LiquidDialogSize = LiquidDialogSize.Standard,
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
            modifier = Modifier.align(Alignment.CenterStart),
            role = DialogButtonRole.Cancel
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
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
                modifier = Modifier.align(Alignment.CenterEnd),
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
        size = LiquidDialogSize.Compact
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        LiquidAlertSurface(
            title = title,
            message = message,
            actions = actions,
            backdrop = backdrop,
            config = config
        )
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
        LiquidAlertActionStyle.Secondary -> glassForegroundColor(config)
        LiquidAlertActionStyle.Destructive -> Color(0xFFFF453A)
    }
    val surfaceColor = when (action.style) {
        LiquidAlertActionStyle.Primary -> Color(0xFF0A84FF).copy(alpha = 0.84f)
        LiquidAlertActionStyle.Secondary -> Color.White.copy(alpha = 0.10f)
        LiquidAlertActionStyle.Destructive -> Color(0xFFFF453A).copy(alpha = 0.16f)
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
            blurRadius = 3.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
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
