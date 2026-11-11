package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.*
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupBackdropCapture

internal val LocalCenteredDialogSceneBackdrop = compositionLocalOf<Backdrop?> { null }
internal val LocalCenteredDialogRenderInRootScaffold = compositionLocalOf { true }

/** Complete root-scene producer; the popup host is a sibling and therefore cannot self-sample. */
@Composable
internal fun rememberCenteredDialogSceneBackdrop(providerId: String): LayerBackdrop =
    rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.DialogBridge,
        providerId = providerId
    ) { drawContent() }

@Composable
internal fun Modifier.centeredDialogSceneProducer(backdrop: LayerBackdrop): Modifier = then(
    if (MiuixPopupBackdropCapture.backdropCaptureNeeded) {
        Modifier.glassBackdropProducer(backdrop)
    } else {
        Modifier
    }
)

/**
 * Switches alternate dialog content inside the existing window. Native controls resize without
 * a fading offscreen layer; the Miuix fallback retains its content crossfade.
 */
@Composable
internal fun <T> CenteredDialogContentTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "CenteredDialogContentTransition",
    smoothResize: Boolean = false,
    content: @Composable (T) -> Unit
) {
    val nativeWindow = LocalControlDialogSurface.current
    if (nativeWindow) {
        // One live picker only: AnimatedContent keeps both AndroidView trees mounted while
        // size changes, letting outgoing controls draw/measure over the newly selected mode.
        // A wrap-content native window measures this panel again after every size change.
        // Animate only when the caller requests it, otherwise resize once with the content.
        Box(modifier.then(if (smoothResize) Modifier.animateContentSize(
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) else Modifier)) {
            content(targetState)
        }
        return
    }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(durationMillis = 160, delayMillis = 24)) togetherWith
                fadeOut(tween(durationMillis = 100)) using if (smoothResize) {
                    SizeTransform(clip = true) { _, _ -> tween(260, easing = FastOutSlowInEasing) }
                } else SizeTransform(clip = false)
        },
        label = label,
        content = { state -> content(state) }
    )
}

/**
 * Canonical shell for compact selector/editor dialogs.
 *
 * The content remains a free [ColumnScope] so a date picker, multi-column period editor or text
 * editor can keep its own density without cloning the window, material and spacing contract.
 */
@Composable
fun SleepDownPickerDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    config: ScheduleConfigEntity,
    onDismissFinished: (() -> Unit)? = null,
    contentSpacing: Dp = SleepDownDesignTokens.QuickSheet.PickerContentSpacing,
    titleAction: (@Composable () -> Unit)? = null,
    contentTransitionKey: Any? = null,
    contentForState: (@Composable ColumnScope.(Any?) -> Unit)? = null,
    scrollableContent: Boolean = false,
    smoothContentResize: Boolean = false,
    bottomActions: (@Composable () -> Unit)? = null,
    actions: List<LiquidAlertAction>? = null,
    adjustForIme: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        val animatePadding = smoothContentResize && contentTransitionKey != null
        Column(
            modifier = if (animatePadding) Modifier else Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            if (contentTransitionKey == null) {
                content()
            } else {
                CenteredDialogContentTransition(
                    targetState = contentTransitionKey,
                    modifier = if (animatePadding) Modifier.fillMaxWidth() else Modifier,
                    smoothResize = smoothContentResize,
                    label = "picker-dialog-content"
                ) { displayedState ->
                    Column(
                        modifier = if (animatePadding) Modifier.padding(contentPadding) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        if (contentForState != null) contentForState(displayedState) else content()
                    }
                }
            }
        }
    }
    ControlPickerDialog(PickerDialogRequest(
        show, title, appUsesDarkTheme(config), onDismissRequest, onDismissFinished,
        titleAction, actions, bottomActions, scrollableContent, adjustForIme, body, message))

}

/** Floating forms use an actual COUI window, so their background samples the complete page. */
@Composable
internal fun SleepDownFloatingSheet(
    show: Boolean,
    title: String,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    allowDismiss: Boolean = true,
    contentModifier: Modifier = Modifier,
    actions: List<LiquidAlertAction>? = null,
    startAction: @Composable () -> Unit,
    endAction: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (actions != null) {
        ControlPickerDialog(PickerDialogRequest(
            show, title, appUsesDarkTheme(config), { if (allowDismiss) onDismissRequest() }, onDismissFinished,
            null, actions, null, false, false, { Column(Modifier.fillMaxWidth()) { content() } }))
        return
    }
    val body: @Composable () -> Unit = {
        val foreground = sleepDownPanelForegroundColor(config)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides foreground
        ) {
            Column(contentModifier.fillMaxWidth().padding(
                vertical = com.itos.coui.dialog.CouiDialogDefaults.buttonBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(
                    horizontal = com.itos.coui.dialog.CouiDialogDefaults.buttonEdgePadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    startAction()
                    BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        val density = LocalDensity.current
                        val measurer = rememberTextMeasurer()
                        val titleSize = 18.sp
                        val titleStyle = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize,
                            lineHeight = 22.sp)
                        val naturalWidth = remember(title, titleStyle, measurer) {
                            measurer.measure(title, style = titleStyle, softWrap = false, maxLines = 1).size.width
                        }
                        val widthPx = with(density) { maxWidth.toPx() }
                        val fit = if (naturalWidth > 0) ((widthPx - 1f) / naturalWidth).coerceIn(0.01f, 1f) else 1f
                        Text(title, Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = titleStyle, fontSize = titleSize * fit,
                            maxLines = 1, softWrap = false, color = foreground)
                    }
                    endAction()
                }
                Box(Modifier.weight(1f, fill = false)) { content() }
            }
        }
    }
    ControlPickerDialog(PickerDialogRequest(
        show, "", appUsesDarkTheme(config), { if (allowDismiss) onDismissRequest() },
        onDismissFinished, null, null, null, false, true, body))

}

@Composable
internal fun QuickSheetLiquidAction(
    label: String,
    enabled: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    height: Dp = 38.dp,
    onClick: () -> Unit
) {
    val centeredAction = height == SleepDownDesignTokens.CenteredDialog.ActionHeight
    // Header actions keep the compact capsule dimensions; only full-size confirmation actions
    // inherit the system dialog button size, which otherwise crowds the sheet title.
    val nativeStyle = LocalControlPickerStyle.current.takeIf { LocalControlDialogSurface.current && centeredAction }
    val actionModifier = if (modifier != Modifier) modifier else nativeStyle?.let {
        Modifier.widthIn(min = it.buttonMinWidth)
    } ?: Modifier.width(84.dp)
    val dark = appUsesDarkTheme(config)
    val neutralSurface = if (centeredAction && dark) {
        Color(0xFF363639)
    } else if (centeredAction) {
        Color(0xFFD6D9DF).copy(alpha = 0.80f)
    } else if (dark) {
        Color(0xFF272C36).copy(alpha = 0.92f)
    } else {
        Color(0xFFF3F6FB).copy(alpha = 0.90f)
    }
    val actionSurfaceColor = when {
        primary && dark -> Color(0xFF099AFF)
        primary -> Color(0xFF0A84FF).copy(alpha = 0.88f)
        destructive && !centeredAction -> Color(0xFFFF453A).copy(alpha = 0.88f)
        else -> neutralSurface
    }
    val actionTint = when {
        primary && dark -> Color(0xFF099AFF)
        primary -> Color(0xFF0A84FF)
        destructive && !centeredAction -> Color(0xFFFF453A)
        else -> Color.Unspecified
    }
    val actionTextColor = when {
        primary -> Color.White
        destructive -> Color(0xFFFF453A)
        else -> MaterialTheme.colorScheme.onSurface
    }
    LiquidButton(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        backdrop = backdrop,
        modifier = actionModifier,
        height = nativeStyle?.buttonHeight ?: height,
        nativePrimaryColor = if (primary) MaterialTheme.colorScheme.primary else Color.Unspecified,
        nativeDarkOverride = dark,
        blurRadius = 12.dp,
        lensHeight = 4.dp,
        lensAmount = 6.dp,
        tint = actionTint,
        surfaceColor = actionSurfaceColor,
        contentPadding = PaddingValues(horizontal = 14.dp),
        isInteractive = !centeredAction,
        staticPressDimAlpha = if (centeredAction) 0.12f else 0f,
        shape = Capsule(),
        shadowEnabled = !centeredAction,
        highlightEnabled = !centeredAction
    ) {
        Text(label, color = actionTextColor, fontWeight = nativeStyle?.buttonWeight ?: FontWeight.SemiBold, fontSize = nativeStyle?.buttonTextSize ?: 13.sp)
    }
}
