package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.LiveUpdateChipTextMode
import com.xiaomanjun.sleepdownschedule.NotificationMode
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidControlToggle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogButtonRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference

@Composable
internal fun SettingsFallbackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = Capsule()
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    selected: Boolean = false,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leadingIconTint: ComposeColor = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val neutralSelection = MaterialTheme.colorScheme.onSurface
    val iconTint = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        androidx.compose.ui.graphics.lerp(leadingIconTint, ComposeColor.White, 0.18f) else leadingIconTint
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = {
                leadingIcon?.let {
                    Icon(it, contentDescription = null, tint = iconTint,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp).size(23.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    neutralSelection.copy(alpha = if (selected) 0.10f else 0f)
                ),
            insideMargin = PaddingValues(start = 14.dp, end = if (leadingIcon != null) 22.dp else 14.dp,
                top = 12.dp, bottom = 12.dp),
            endActions = {
                if (badgeText != null) {
                    Text(
                        badgeText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(Capsule())
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            },
            onClick = onClick
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 58.dp else 70.dp)
            .background(
                neutralSelection.copy(
                    alpha = when {
                        pressed -> 0.12f
                        selected -> 0.10f
                        else -> 0f
                    }
                )
            )
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = if (leadingIcon != null) 28.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = iconTint,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp).size(23.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badgeText != null) {
            Text(
                badgeText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(Capsule())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(">", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The unified forward indicator every row uses when it opens a secondary surface. Rows that cannot
 * be a plain [SettingsNavigationRow] still reuse this exact Miuix arrow so the affordance matches.
 */
@Composable
internal fun SettingsForwardIndicator(modifier: Modifier = Modifier) {
    Image(
        modifier = modifier.size(width = 10.dp, height = 16.dp),
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    )
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, backdrop: Backdrop?, enabled: Boolean = true, badgeText: String? = null, compact: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = title,
            summary = subtitle.takeIf { it.isNotBlank() },
            badgeText = badgeText,
            compact = compact,
            controlWidth = 52.dp,
            controlHeight = 24.dp,
            enabled = enabled
        ) {
            LiquidControlToggle(checked, onCheckedChange, backdrop, enabled = enabled, compact = true)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 52.dp else if (subtitle.isBlank()) 56.dp else 76.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = if (compact) 4.dp else 20.dp, vertical = if (compact) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            PreferenceLabel(title, badgeText)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        LiquidControlToggle(checked, onCheckedChange, backdrop, enabled = enabled, compact = true)
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    iconRes: Int,
    backdrop: Backdrop?,
    destructive: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                DialogLiquidButton(
                    backdrop = backdrop,
                    label = buttonText,
                    onClick = onClick,
                    role = if (destructive) DialogButtonRole.Cancel else DialogButtonRole.Confirm,
                    iconRes = iconRes,
                    destructiveFilled = destructive
                )
            }
        ) { SettingsPreferenceText(title, subtitle, badgeText) }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            PreferenceLabel(title, badgeText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        DialogLiquidButton(
            backdrop = backdrop,
            label = buttonText,
            onClick = onClick,
            role = if (destructive) DialogButtonRole.Cancel else DialogButtonRole.Confirm,
            iconRes = iconRes,
            destructiveFilled = destructive
        )
    }
}

@Composable
fun SettingsValueRow(title: String, value: String, compact: Boolean = false) {
    if (compact && LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(title = title, summary = value, modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        return
    }
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        Text(value, modifier = Modifier.offset(y = 1.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsPickerValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    valueBelowTitle: Boolean = false
) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            summary = value.ifBlank { "未设置" }.takeIf { valueBelowTitle },
            endActions = {
                if (!valueBelowTitle) {
                    Text(
                        value.ifBlank { "未设置" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = if (compact) 4.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp)
        )
        return
    }
    if (valueBelowTitle) {
        Column(modifier.fillMaxWidth().heightIn(min = 52.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(value.ifBlank { "未设置" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 58.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 4.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        Text(value.ifBlank { "未设置" }, modifier = Modifier.offset(y = 1.dp).widthIn(min = 88.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String, badgeText: String? = null) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) { SettingsPreferenceText(title, body, badgeText) }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PreferenceLabel(title, badgeText)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

@Composable
private fun GlassMiuixInteractivePreference(
    title: String,
    summary: String? = null,
    controlWidth: Dp,
    controlHeight: Dp = 42.dp,
    enabled: Boolean = true,
    badgeText: String? = null,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    MiuixBasicComponent(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f),
        insideMargin = PaddingValues(horizontal = if (compact) 4.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
        endActions = {
            // Let Miuix measure the multiline label and its control in the same row. A separate
            // overlay could retain the first-pass center while the summary wrapped on entry.
            Box(Modifier.size(controlWidth, controlHeight),
                contentAlignment = Alignment.Center) {
                content()
            }
        }
    ) { SettingsPreferenceText(title, summary, badgeText, enabled) }
}

@Composable
fun SettingsChoiceRow(title: String, selected: NotificationMode, backdrop: Backdrop?, config: ScheduleConfigEntity, onSelected: (NotificationMode) -> Unit) {
    val modes = NotificationMode.entries
    SleepDownLiquidDropdownPreference(
        items = modes.map {
            when (it) {
                NotificationMode.STANDARD -> "普通通知"
                NotificationMode.LIVE_UPDATE -> "实时活动"
            }
        },
        selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
        title = title,
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 240.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> onSelected(modes[index.coerceIn(modes.indices)]) }
    )
}

@Composable
fun SettingsLiveUpdateChipTextRow(
    selected: LiveUpdateChipTextMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (LiveUpdateChipTextMode) -> Unit
) {
    val options = listOf(
        LiveUpdateChipTextMode.LOCATION,
        LiveUpdateChipTextMode.COUNTDOWN,
        LiveUpdateChipTextMode.NORMAL
    )
    val labels = listOf("地点", "倒计时", "课程名称")
    // SHORT was removed from the settings surface. Treat old saved rows as the new course-name
    // mode so opening this page never appears to select an invisible option.
    val visibleSelected = if (selected == LiveUpdateChipTextMode.SHORT) {
        LiveUpdateChipTextMode.NORMAL
    } else {
        selected
    }
    SleepDownLiquidDropdownPreference(
        items = labels,
        selectedIndex = options.indexOf(visibleSelected).coerceAtLeast(0),
        title = "岛上缩略态",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> onSelected(options[index.coerceIn(options.indices)]) }
    )
}

@Composable
fun SettingsActionButton(
    label: String,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    monochrome: Boolean = false,
    glowing: Boolean = false,
    badgeText: String? = null
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val monochromeSurface = if (darkTheme) ComposeColor.Black else ComposeColor.White
    val tint = when {
        destructive -> ComposeColor(0xFFFF453A)
        monochrome -> monochromeSurface
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = if (monochrome) {
        if (darkTheme) ComposeColor.White else ComposeColor.Black
    } else {
        ComposeColor.White
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = if (badgeText == null) modifier else modifier.heightIn(min = 42.dp),
            height = if (badgeText == null) 42.dp else Dp.Unspecified,
            tint = tint,
            surfaceColor = tint.copy(
                alpha = when {
                    destructive -> 0.86f
                    monochrome && darkTheme -> 0.58f
                    monochrome -> 0.74f
                    glowing -> 0.64f
                    else -> 0.84f
                }
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (badgeText == null) 0.dp else 6.dp),
            shadowStyle = if (glowing) Shadow(radius = 14.dp, color = tint.copy(alpha = 0.26f)) else Shadow.Default,
            blurRadius = if (glowing) 8.dp else 4.dp,
            lensHeight = 14.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            PreferenceLabel(label, badgeText, style = MaterialTheme.typography.labelLarge,
                color = textColor, badgeColor = textColor, centered = true, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Box(
            modifier = modifier
                .clip(Capsule())
                .background(
                    tint.copy(
                        alpha = when {
                            destructive -> 0.90f
                            monochrome && darkTheme -> 0.72f
                            monochrome -> 0.88f
                            else -> 0.88f
                        }
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            PreferenceLabel(label, badgeText, style = MaterialTheme.typography.labelLarge,
                color = textColor, badgeColor = textColor, centered = true, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsPreferenceText(title: String, summary: String?, badge: String?, enabled: Boolean = true) {
    val theme = top.yukonga.miuix.kmp.theme.MiuixTheme
    val titleColor = if (enabled) theme.colorScheme.onBackground else theme.colorScheme.disabledOnSecondaryVariant
    if (badge == null) {
        top.yukonga.miuix.kmp.basic.Text(title, fontSize = theme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium, color = titleColor)
    } else {
        PreferenceLabel(title, badge, style = theme.textStyles.headline1, color = titleColor)
    }
    if (summary != null) {
        top.yukonga.miuix.kmp.basic.Text(summary, fontSize = theme.textStyles.body2.fontSize,
            color = if (enabled) theme.colorScheme.onSurfaceVariantSummary else theme.colorScheme.disabledOnSecondaryVariant)
    }
}

/**
 * Swipe a settings row left to reveal a delete action. The gesture, threshold haptics, elastic
 * action growth and spring settle follow the AI import history row; deleting is only requested so
 * the caller can confirm it first, and the row springs back while the dialog is up.
 */
@Composable
internal fun SettingsSwipeDeleteRow(
    rowKey: Any?,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    softAppearance: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(rowKey) { Animatable(0f) }
    var revealCrossed by remember(rowKey) { mutableStateOf(false) }
    var deleteCrossed by remember(rowKey) { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(1f) }
    val actionWidthPx = with(density) { 60.dp.toPx() }
    val actionGapPx = with(density) { 12.dp.toPx() }
    val revealPx = actionWidthPx + actionGapPx
    val deleteTriggerPx = maxOf(revealPx + with(density) { 132.dp.toPx() }, widthPx * 0.72f)
        .coerceAtMost(widthPx * 0.86f)
    val maximumDragPx = (widthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(revealPx)
    val settleSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 420f)
    fun requestDelete() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        revealCrossed = false
        deleteCrossed = false
        scope.launch { offset.animateTo(0f, settleSpring) }
        onRequestDelete()
    }
    val dragDistance = (-offset.value).coerceAtLeast(0f)
    val revealProgress = (dragDistance / revealPx).coerceIn(0f, 1f)
    val stretch = ((dragDistance - revealPx) / (deleteTriggerPx - revealPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val actionWidth = actionWidthPx +
        (widthPx - with(density) { 32.dp.toPx() } - actionWidthPx).coerceAtLeast(0f) * stretch
    val visibleActionWidth = minOf(actionWidth, (dragDistance - actionGapPx).coerceAtLeast(actionWidthPx))
    Box(modifier.fillMaxWidth().onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { visibleActionWidth.toDp() })
                    .padding(end = if (softAppearance) 12.dp else 16.dp, top = if (softAppearance) 8.dp else 0.dp,
                        bottom = if (softAppearance) 8.dp else 0.dp)
                    .graphicsLayer {
                        alpha = revealProgress
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .clip(RoundedRectangle(15.dp))
                    .then(if (softAppearance) Modifier
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                            ComposeColor(0xFFFF6B61).copy(alpha = 0.22f), ComposeColor(0xFFFF453A).copy(alpha = 0.10f))))
                        .border(0.7.dp, ComposeColor(0xFFFF665B).copy(alpha = 0.22f), RoundedRectangle(15.dp))
                        else Modifier.background(ComposeColor(0xFFFF3B30)))
                    .clickable(onClick = ::requestDelete),
                contentAlignment = Alignment.Center
            ) {
                if (softAppearance) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(painter = painterResource(R.drawable.ic_delete_history), contentDescription = null,
                        tint = ComposeColor(0xFFFF453A), modifier = Modifier.size(18.dp))
                    Text("删除", color = ComposeColor(0xFFFF453A), fontSize = 10.sp, lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium)
                } else Image(
                    painter = painterResource(R.drawable.ic_delete_history),
                    contentDescription = "删除",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(rowKey, widthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = drag@{ change, dragAmount ->
                            change.consume()
                            val next = (offset.value + dragAmount).coerceIn(-maximumDragPx, 0f)
                            val revealNow = abs(next) >= revealPx * 0.48f
                            if (revealNow != revealCrossed) {
                                revealCrossed = revealNow
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { offset.snapTo(next) }
                            if (abs(next) >= deleteTriggerPx && !deleteCrossed) {
                                deleteCrossed = true
                                requestDelete()
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val target = if (abs(offset.value) >= revealPx * 0.48f) -revealPx else 0f
                                revealCrossed = target < 0f
                                deleteCrossed = false
                                offset.animateTo(target, settleSpring)
                            }
                        },
                        onDragCancel = {
                            revealCrossed = false
                            deleteCrossed = false
                            scope.launch { offset.animateTo(0f, settleSpring) }
                        }
                    )
                }
        ) { content() }
    }
}
