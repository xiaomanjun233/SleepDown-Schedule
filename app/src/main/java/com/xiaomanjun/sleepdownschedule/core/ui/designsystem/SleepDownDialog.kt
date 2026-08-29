package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule

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
    val enabled: Boolean = true,
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
    followGlassContrast: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val windowSize = with(density) {
        LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) }
    }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeHeight = (
        windowSize.height -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            32.dp
        ).coerceAtLeast(280.dp)
    val dialogWidth = (windowSize.width * 0.92f).coerceAtMost(SleepDownDesignTokens.Dialog.MaxWidth)
    val dialogMaxHeight = (safeHeight * 0.82f).coerceAtMost(SleepDownDesignTokens.Dialog.MaxHeight)
    val shape = RoundedCornerShape(SleepDownDesignTokens.Dialog.ContainerCorner)
    // Most full dialogs own an opaque-enough material layer and therefore follow the app theme.
    // Home destinations can opt into the sampled glass domain when the wallpaper remains the
    // visible material behind the whole form.
    val dark = if (followGlassContrast) {
        !glassUsesLightStyle(config)
    } else {
        appUsesDarkTheme(config)
    }
    val textColor = if (followGlassContrast) {
        sleepDownGlassForegroundColor(config)
    } else if (dark) {
        Color.White
    } else {
        Color(0xFF111111)
    }
    val surfaceColor = if (dark) {
        Color(0xFF121212).copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.18f)
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
                    .background(Color.Black.copy(alpha = if (dark) 0.20f else 0.035f)),
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
                .background(if (dark) Color(0xFF1C1C1E) else Color.White),
            content = panelContent
        )
    }
}

@Composable
fun CenterLiquidDialog(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    size: LiquidDialogSize = LiquidDialogSize.Standard,
    followGlassContrast: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidDialogSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        size = size,
        followGlassContrast = followGlassContrast
    ) {
        Column(
            modifier = if (size == LiquidDialogSize.Standard) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ContentSpacing),
            content = content
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
    buttonLightStyleOverride: Boolean? = null,
    onConfirm: (() -> Unit)? = null
) {
    val inheritedContentColor = LocalContentColor.current
    val titleColor = if (inheritedContentColor == Color.Unspecified) {
        sleepDownPanelForegroundColor(config)
    } else {
        inheritedContentColor
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SleepDownDesignTokens.Dialog.HeaderHeight)
            .padding(horizontal = SleepDownDesignTokens.Dialog.HorizontalPadding)
    ) {
        DialogLiquidButton(
            backdrop,
            "取消",
            onDismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 4.dp),
            role = DialogButtonRole.Cancel,
            blurRadius = buttonBlurRadius,
            lightStyleOverride = buttonLightStyleOverride
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
            color = titleColor,
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
                blurRadius = buttonBlurRadius,
                lightStyleOverride = buttonLightStyleOverride
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
            .padding(horizontal = SleepDownDesignTokens.Dialog.HorizontalPadding),
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
            .padding(
                horizontal = SleepDownDesignTokens.Dialog.HorizontalPadding,
                vertical = SleepDownDesignTokens.Field.SingleLineVerticalPadding
            ),
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
    messageContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Alerts own their material tint, so their foreground must follow that stable surface rather
    // than the wallpaper behind the dim layer.
    val foreground = sleepDownPanelForegroundColor(config)
    // One- and two-action alerts share the accepted single-row action layout. Tighten that whole
    // family through the copy band only; action geometry and bottom/side insets stay unchanged.
    var messageLineCount by remember(message) { mutableStateOf(0) }
    val messageSingleLine = messageLineCount == 1
    val compact = actions.size <= 2
    val copyLift = if (messageLineCount == 2) {
        -SleepDownDesignTokens.CenteredDialog.ThreeLineAlertTextLift
    } else {
        0.dp
    }
    Column(
        modifier = modifier.padding(
            top = (if (compact) {
                SleepDownDesignTokens.CenteredDialog.CompactAlertTopInset
            } else {
                SleepDownDesignTokens.CenteredDialog.AlertTopInset
            }) -
                SleepDownDesignTokens.CenteredDialog.AlertVerticalPadding
        )
    ) {
        Text(
            text = title,
            modifier = Modifier
                .offset(y = copyLift)
                .padding(
                    horizontal = SleepDownDesignTokens.CenteredDialog.AlertTextHorizontalInset
                ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
        Spacer(
            Modifier.height(
                if (compact) {
                    SleepDownDesignTokens.CenteredDialog.CompactTitleContentSpacing
                } else {
                    SleepDownDesignTokens.CenteredDialog.TitleContentSpacing
                }
            )
        )
        if (messageContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SleepDownDesignTokens.CenteredDialog.AlertTextHorizontalInset
                    )
                    .heightIn(max = 240.dp)
            ) {
                messageContent()
            }
        } else {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = copyLift)
                    .padding(
                        horizontal = SleepDownDesignTokens.CenteredDialog.AlertTextHorizontalInset
                    )
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                color = foreground.copy(alpha = 0.68f),
                onTextLayout = { result -> messageLineCount = result.lineCount }
            )
        }
        Spacer(
            Modifier.height(
                if (messageSingleLine) {
                    if (compact) {
                        SleepDownDesignTokens.CenteredDialog.CompactSingleLineActionSpacing
                    } else {
                        SleepDownDesignTokens.CenteredDialog.AlertSingleLineActionSpacing
                    }
                } else {
                    if (compact) {
                        SleepDownDesignTokens.CenteredDialog.CompactMessageActionSpacing
                    } else {
                        SleepDownDesignTokens.CenteredDialog.MessageActionSpacing
                    }
                }
            )
        )
        LiquidAlertActions(
            actions = actions,
            backdrop = backdrop,
            config = config
        )
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
            horizontalArrangement = Arrangement.spacedBy(
                SleepDownDesignTokens.CenteredDialog.ActionSpacing
            )
        ) {
            actions.forEach { action ->
                LiquidAlertActionButton(action, backdrop, config, Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                SleepDownDesignTokens.CenteredDialog.ActionSpacing
            )
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
    onDismissRequest: () -> Unit,
    messageContent: (@Composable () -> Unit)? = null
) {
    val completeUnderlayBackdrop = LocalCenteredDialogSceneBackdrop.current ?: backdrop
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

    val visuals = rememberCenteredDialogVisuals(
        backdrop = completeUnderlayBackdrop,
        config = config,
        blurRadius = SleepDownDesignTokens.CenteredDialog.MaxBlur,
        maxWidth = SleepDownDesignTokens.CenteredDialog.AlertWidth
    )
    val renderInRootScaffold = LocalCenteredDialogRenderInRootScaffold.current

    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = visible,
        title = null,
        enableWindowDim = false,
        backgroundColor = Color.Transparent,
        forceCenter = true,
        surfaceModifier = visuals.surfaceModifier,
        backgroundModifier = visuals.backgroundModifier,
        animationProgressState = visuals.animationProgress,
        enablePredictiveBackAnimation = false,
        excludeFromBackdropCapture = true,
        renderInRootScaffold = renderInRootScaffold,
        outsideMargin = DpSize(
            SleepDownDesignTokens.QuickSheet.HorizontalMargin,
            SleepDownDesignTokens.QuickSheet.VerticalMargin
        ),
        insideMargin = DpSize(
            SleepDownDesignTokens.CenteredDialog.ContentPadding,
            SleepDownDesignTokens.CenteredDialog.AlertVerticalPadding
        ),
        onDismissRequest = { closeThen(onDismissRequest) },
        onDismissFinished = {
            val action = completion
            completion = null
            action?.invoke()
        }
    ) {
        LiquidAlertContent(
            title = title,
            message = message,
            actions = animatedActions,
            backdrop = completeUnderlayBackdrop,
            config = config,
            messageContent = messageContent
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
    val fallbackInteractionSource = remember { MutableInteractionSource() }
    val fallbackPressed by fallbackInteractionSource.collectIsPressedAsState()
    val baseContentColor = when (action.style) {
        LiquidAlertActionStyle.Primary -> Color.White
        LiquidAlertActionStyle.Secondary -> sleepDownPanelForegroundColor(config)
        LiquidAlertActionStyle.Destructive -> Color(0xFFFF453A)
    }
    val contentColor = baseContentColor.copy(alpha = if (action.enabled) 1f else 0.38f)
    val dark = appUsesDarkTheme(config)
    val baseSurfaceColor = when (action.style) {
        LiquidAlertActionStyle.Primary -> if (dark) Color(0xFF099AFF) else {
            Color(0xFF0A84FF).copy(alpha = 0.90f)
        }
        LiquidAlertActionStyle.Secondary -> if (dark) {
            Color(0xFF363639)
        } else {
            Color(0xFFD6D9DF).copy(alpha = 0.80f)
        }
        // Destructive actions share the same neutral glass as secondary actions. Their red label
        // is the only destructive accent, matching the reference alert behavior.
        LiquidAlertActionStyle.Destructive -> if (dark) {
            Color(0xFF363639)
        } else {
            Color(0xFFD6D9DF).copy(alpha = 0.80f)
        }
    }
    val surfaceColor = if (action.enabled) {
        baseSurfaceColor
    } else {
        baseSurfaceColor.copy(alpha = (baseSurfaceColor.alpha * 0.45f).coerceAtLeast(0.12f))
    }
    val shape = Capsule()
    if (backdrop != null) {
        LiquidButton(
            onClick = if (action.enabled) action.onClick else ({ }),
            backdrop = backdrop,
            modifier = modifier,
            isInteractive = false,
            staticPressDimAlpha = 0.12f,
            height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
            shape = shape,
            surfaceColor = surfaceColor,
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 12.dp,
            lensHeight = 4.dp,
            lensAmount = 6.dp,
            chromaticAberration = false,
            shadowEnabled = false,
            highlightEnabled = false
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
                .height(SleepDownDesignTokens.CenteredDialog.ActionHeight)
                .clip(shape)
                .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.16f)))
                .drawWithContent {
                    drawContent()
                    if (fallbackPressed) drawRect(Color.Black.copy(alpha = 0.12f))
                }
                .clickable(
                    interactionSource = fallbackInteractionSource,
                    indication = null,
                    enabled = action.enabled,
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

enum class DialogButtonRole { Neutral, Confirm, Cancel }

@Composable
fun DialogLiquidButton(
    backdrop: Backdrop?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: DialogButtonRole = DialogButtonRole.Neutral,
    iconRes: Int? = null,
    blurRadius: Dp = 3.dp,
    destructiveFilled: Boolean = false,
    monochromeNeutral: Boolean = false,
    lightStyleOverride: Boolean? = null,
    highContrast: Boolean = false,
    roundIcon: Boolean = false
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useMonochromeNeutral = role == DialogButtonRole.Neutral && monochromeNeutral
    val controlDark = lightStyleOverride?.not() ?: darkTheme
    val useRoundIcon = roundIcon && role != DialogButtonRole.Neutral
    val resolvedIconRes = iconRes ?: when {
        useRoundIcon && role == DialogButtonRole.Cancel -> R.drawable.ic_close_light
        useRoundIcon && role == DialogButtonRole.Confirm -> R.drawable.ic_check
        else -> null
    }
    val textColor = if (highContrast) Color.White else when (role) {
        DialogButtonRole.Confirm -> Color.White
        DialogButtonRole.Cancel -> Color.White
        DialogButtonRole.Neutral -> if (useMonochromeNeutral) {
            if (controlDark) Color.White else Color.Black
        } else MaterialTheme.colorScheme.primary
    }
    val surfaceColor = if (highContrast) {
        Color.Black.copy(alpha = if (controlDark) 0.62f else 0.52f)
    } else when (role) {
        DialogButtonRole.Confirm -> Color(0xFF0A84FF).copy(alpha = 0.82f)
        DialogButtonRole.Cancel -> if (destructiveFilled) {
            Color(0xFFFF453A).copy(alpha = 0.78f)
        } else {
            Color.Black.copy(alpha = if (controlDark) 0.42f else 0.30f)
        }
        DialogButtonRole.Neutral -> if (useMonochromeNeutral) {
            (if (controlDark) Color.Black else Color.White)
                .copy(alpha = if (controlDark) 0.46f else 0.62f)
        } else Color.Transparent
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = if (useRoundIcon) modifier.size(42.dp) else modifier,
            height = if (useRoundIcon) 42.dp else 40.dp,
            surfaceColor = surfaceColor,
            contentPadding = if (useRoundIcon) PaddingValues(0.dp) else PaddingValues(horizontal = 18.dp),
            blurRadius = blurRadius,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            chromaticAberration = false
        ) {
            resolvedIconRes?.let {
                Icon(painterResource(it), contentDescription = label, modifier = Modifier.size(20.dp), tint = textColor)
            }
            if (!useRoundIcon) {
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    } else {
        Row(
            modifier = (if (useRoundIcon) modifier.size(42.dp) else modifier.height(40.dp))
                .clip(RoundedCornerShape(50))
                .background(
                    surfaceColor.copy(
                        alpha = surfaceColor.alpha.coerceAtLeast(
                            if (role == DialogButtonRole.Neutral) 0f else 0.16f
                        )
                    )
                )
                .clickable(onClick = onClick)
                .then(if (useRoundIcon) Modifier else Modifier.padding(horizontal = 18.dp)),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            resolvedIconRes?.let {
                Icon(painterResource(it), contentDescription = label, modifier = Modifier.size(20.dp), tint = textColor)
            }
            if (!useRoundIcon) {
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun DialogCapsuleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    cornerRadius: Dp? = null,
    fieldTextColor: Color? = null,
    fieldLightStyleOverride: Boolean? = null
) {
    val dark = fieldLightStyleOverride?.not() ?: appUsesDarkTheme(config)
    val fieldBase = if (dark) Color(0xFF2C2C2E) else Color.White
    val background = fieldBase.copy(alpha = if (dark) 0.54f else 0.70f)
    val textColor = fieldTextColor ?: LocalContentColor.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (minLines == 1) ImeAction.Done else ImeAction.Default
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        cursorBrush = SolidColor(textColor),
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    cornerRadius ?: if (minLines == 1) {
                        SleepDownDesignTokens.Field.SingleLineCorner
                    } else {
                        SleepDownDesignTokens.Field.MultiLineCorner
                    }
                )
            )
            .background(background)
            .padding(
                horizontal = SleepDownDesignTokens.Field.HorizontalPadding,
                vertical = if (minLines == 1) {
                    SleepDownDesignTokens.Field.SingleLineVerticalPadding
                } else {
                    SleepDownDesignTokens.Field.MultiLineVerticalPadding
                }
            ),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        color = textColor.copy(alpha = 0.52f),
                        maxLines = if (minLines == 1) 1 else 2,
                        overflow = TextOverflow.Clip
                    )
                }
                innerTextField()
            }
        }
    )
}
