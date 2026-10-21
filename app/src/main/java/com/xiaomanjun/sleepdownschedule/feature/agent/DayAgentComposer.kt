package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.material.controlAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.text.AutoFitSingleLineText
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme

@Composable
internal fun AgentOperationLiquidButton(
    text: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    destructive: Boolean,
    applied: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = when {
        applied -> Color.White.copy(alpha = 0.55f)
        else -> Color.White
    }
    val bgColor = when {
        destructive -> Color(0xFFFF453A)
        else -> controlAccentColor(Color(0xFF0A84FF))
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (!applied && enabled) onClick() },
            enabled = !applied && enabled,
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            surfaceColor = if (applied || !enabled) bgColor.copy(alpha = 0.35f) else bgColor.copy(alpha = 0.88f),
            tint = if (applied || !enabled) Color.Unspecified else bgColor,
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 6.dp,
            lensHeight = 14.dp,
            lensAmount = 20.dp
        ) {
            Text(
                text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val background = if (applied || !enabled) bgColor.copy(alpha = 0.30f) else bgColor
        Box(
            modifier = modifier
                .height(42.dp)
                .clip(Capsule())
                .background(background.copy(alpha = if (applied) 0.46f else 0.94f))
                .clickable(enabled = !applied && enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AgentSimplePressSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape,
    tokens: GlassTokens,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    GlassSurface(
        nativeMaterial = true,
        backdrop = backdrop,
        config = config,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        ),
        shape = shape,
        tokens = tokens
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val nativeMaterial = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive.current
            if (!nativeMaterial) Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.055f), shape)
            )
            content()
            if (pressed && !nativeMaterial) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.14f), shape)
                )
            }
        }
    }
}

/** Compact, icon-only window controls with a full touch target and spoken labels. */
@Composable
internal fun AgentWindowIconButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    collapse: Boolean = false,
    onClick: () -> Unit
) {
    val label = if (collapse) "退出全屏" else "进入全屏"
    val glyph: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (collapse) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)
            )
        }
    }
    Box(
        Modifier.size(48.dp).semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
    val buttonSize = SleepDownDesignTokens.SecondaryPage.BackButtonSize
    val buttonModifier = Modifier.size(buttonSize)
        GlassSurface(
            nativeMaterial = true,
            nativeDark = true,
            backdrop = backdrop,
            config = config,
            modifier = buttonModifier.clip(CircleShape),
            shape = Capsule(),
            tokens = GlassTokens.dialog().copy(blur = 6.dp, lensHeight = 12.dp, lensAmount = 18.dp,
                chromaticAberration = false, surfaceAlpha = 0.10f, shadowAlpha = 0f),
            baseSurfaceColorOverride = Color.White
        ) { glyph() }
    }
}

@Composable
internal fun AgentComposerTextField(
    inputState: androidx.compose.runtime.MutableState<String>,
    focusRequester: FocusRequester,
    foreground: Color,
    onBoundsChanged: (Rect) -> Unit,
    onSend: () -> Unit,
    multiline: Boolean = false,
    onHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val input = inputState.value
    var editableValue by remember {
        mutableStateOf(TextFieldValue(input, selection = TextRange(input.length)))
    }
    LaunchedEffect(input) {
        if (editableValue.text != input) {
            editableValue = TextFieldValue(input, selection = TextRange(input.length))
        }
    }
    BasicTextField(
        value = editableValue,
        onValueChange = { next ->
            editableValue = next
            if (next.text != inputState.value) inputState.value = next.text
        },
        modifier = modifier
            .then(if (multiline) Modifier.wrapContentHeight(unbounded = true) else Modifier)
            .heightIn(max = if (multiline) 104.dp else 56.dp)
            .onSizeChanged { onHeightChanged(it.height) }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    editableValue = editableValue.copy(
                        selection = TextRange(editableValue.text.length)
                    )
                }
            }
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = foreground),
        cursorBrush = SolidColor(Color(0xFF168CFF)),
        singleLine = !multiline,
        maxLines = if (multiline) 4 else 1,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { inner ->
            if (editableValue.text.isBlank()) {
                AutoFitSingleLineText(
                    text = "问问今天的安排…",
                    color = foreground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            inner()
        }
    )
}

@Composable
internal fun AgentInputLiquidCapsule(
    backdrop: Backdrop?,
    foreground: Color,
    config: ScheduleConfigEntity,
    modifier: Modifier,
    expanded: Boolean,
    shape: Shape,
    tokens: GlassTokens,
    baseSurfaceColorOverride: Color,
    interactionEnabledAt: (size: Size, offset: Offset) -> Boolean,
    surfaceVisibility: () -> Float = { 1f },
    sharedInteractiveHighlight: InteractiveHighlight? = null,
    heightOverride: Dp? = null,
    content: @Composable () -> Unit
) {
    val dockHeight = heightOverride ?: if (expanded) 94.dp else 56.dp
    val nativeSurface = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer.current
    if (nativeSurface?.invoke(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
        modifier = modifier.height(dockHeight), shape = shape, dark = foreground.luminance() > 0.5f,
        surfaceAlpha = surfaceVisibility, elasticTouchFeedback = true
    )) { Box(Modifier.fillMaxSize()) { content() } } == true) return

    val interactionScope = rememberCoroutineScope()
    val currentInteractionEnabledAt = rememberUpdatedState(interactionEnabledAt)
    val interactiveHighlight = remember(interactionScope, sharedInteractiveHighlight) {
        sharedInteractiveHighlight ?: InteractiveHighlight(
            animationScope = interactionScope,
            radius = { size -> size.minDimension * 1.5f },
            acceptsGesture = { size, offset -> currentInteractionEnabledAt.value(size, offset) }
        )
    }
    val pressExpansion = 1.5.dp
    val surfaceBase = if (com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance)
        com.xiaomanjun.sleepdownschedule.glass.flatControlColor(foreground.luminance() > 0.5f)
        else baseSurfaceColorOverride

    Box(
        modifier = modifier
            .height(dockHeight)
            .graphicsLayer { clip = false }
    ) {
        if (backdrop != null) {
            // The glass consumer stays a stable background sibling so the editable caret can
            // blink without invalidating or rebuilding this backdrop layer.
            LiquidButton(
                onClick = {},
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = surfaceVisibility() },
                height = dockHeight,
                contentPadding = PaddingValues(0.dp),
                blurRadius = 16.dp,
                lensHeight = 18.dp,
                lensAmount = 28.dp,
                chromaticAberration = false,
                surfaceColor = surfaceBase.copy(alpha = tokens.surfaceAlpha),
                flatOpacity = 0.90f,
                shadowEnabled = false,
                highlightEnabled = true,
                isInteractive = true,
                highlightRadiusMultiplier = 1.5f,
                shape = shape,
                clipToBounds = false,
                clickTargetEnabled = false,
                pressExpansion = pressExpansion,
                sharedInteractiveHighlight = interactiveHighlight,
                interactionEnabledAt = interactionEnabledAt
            ) {}
        } else {
            GlassSurface(
                backdrop = null,
                config = config,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = surfaceVisibility() },
                shape = shape,
                tokens = tokens,
                baseSurfaceColorOverride = surfaceBase
            ) {}
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidButtonVisualTransform(
                    interactiveHighlight = interactiveHighlight,
                    pressExpansion = pressExpansion
                )
                .then(interactiveHighlight.gestureModifier)
        ) {
            content()
        }
    }
}


@Composable
internal fun AgentCapsuleSendButton(
    sending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .semantics { contentDescription = if (sending) "停止回复" else "发送" }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
            contentDescription = null,
            tint = if (enabled) Color(0xFFF0F0F0) else Color(0xFF777777),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun AgentSendLiquidButton(
    backdrop: Backdrop?,
    sending: Boolean,
    onClick: () -> Unit
) {
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.size(40.dp),
            height = 40.dp,
            contentPadding = PaddingValues(0.dp),
            blurRadius = 5.dp,
            lensHeight = 14.dp,
            lensAmount = 20.dp,
            chromaticAberration = false,
            tint = controlAccentColor(Color(0xFF0A84FF)),
            surfaceColor = controlAccentColor(Color(0xFF0A84FF)).copy(alpha = 0.82f),
            shadowEnabled = true
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(controlAccentColor(Color(0xFF0A84FF)).copy(alpha = 0.90f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
