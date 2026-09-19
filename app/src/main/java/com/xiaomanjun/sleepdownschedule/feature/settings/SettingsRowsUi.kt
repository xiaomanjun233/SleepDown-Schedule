package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.domain.schedule.constrainPeriodTimeSelection
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconMode
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconStyle
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import com.kyant.shapes.Capsule
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt


@Composable
fun SettingsAppIconStyleRow(
    selected: AppIconStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (AppIconStyle) -> Unit
) {
    val options = AppIconStyle.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = "图标风格",
        backdrop = backdrop,
        config = config,
        summary = "选择看板娘或简约图标风格",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsAppIconModeRow(
    selected: AppIconMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (AppIconMode) -> Unit
) {
    val options = AppIconMode.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = "应用图标",
        backdrop = backdrop,
        config = config,
        summary = "选择浅色、深色或跟随应用深色模式",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsDockAlignmentRow(
    selected: DockAlignment,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DockAlignment) -> Unit
) {
    val options = DockAlignment.entries
    val labels = mapOf(
        DockAlignment.LEFT to "左侧",
        DockAlignment.CENTER to "居中",
        DockAlignment.RIGHT to "右侧"
    )
    SleepDownLiquidDropdownPreference(
        items = options.map { labels.getValue(it) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "Dock 栏位置",
        backdrop = backdrop,
        config = config,
        summary = "调整首页底部切换栏对齐方式",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsHomeStartModeRow(
    selected: HomeStartMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (HomeStartMode) -> Unit
) {
    val options = listOf(HomeStartMode.DAY, HomeStartMode.WEEK)
    val normalizedSelected = if (selected == HomeStartMode.TWO_DAY) HomeStartMode.DAY else selected
    SleepDownLiquidDropdownPreference(
        items = listOf("日视图", "周视图"),
        selectedIndex = options.indexOf(normalizedSelected).coerceAtLeast(0),
        title = "默认首页视图",
        backdrop = backdrop,
        config = config,
        summary = "选择每次打开应用时进入日视图或周视图",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 240.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun LiquidOptionTabs(
    selectedIndex: Int,
    labels: List<String>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    width: Dp,
    highContrast: Boolean = false,
    followAppTheme: Boolean = false,
    onSelected: (Int) -> Unit
) {
    if (backdrop != null) {
        val tabContentColor = if (followAppTheme) {
            appPanelForegroundColor(config)
        } else {
            glassForegroundColor(config)
        }
        CompositionLocalProvider(LocalContentColor provides tabContentColor) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex.coerceIn(labels.indices) },
                onTabSelected = { index -> onSelected(index.coerceIn(labels.indices)) },
                backdrop = backdrop,
                tabsCount = labels.size,
                modifier = Modifier.width(width),
                containerHeight = 42.dp,
                indicatorHeight = 34.dp,
                horizontalPadding = 4.dp,
                blurRadius = if (highContrast) 7.dp else 4.dp,
                containerAlpha = if (highContrast) 0.62f else 0.4f,
                lensHeight = 24.dp,
                lensAmount = 24.dp,
                indicatorWidthOverflow = 0.dp,
                indicatorHeightOverflow = 0.dp,
                pressedContentScale = 1.04f,
                chromaticAberrationEnabled = !highContrast,
                isLightThemeOverride = if (followAppTheme) {
                    !appUsesDarkTheme(config)
                } else {
                    glassUsesLightStyle(config)
                },
                useOfficialGlassParameters = true
            ) {
                labels.forEachIndexed { index, label ->
                    LiquidBottomTab(onClick = { onSelected(index) }) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                SettingsFallbackChip(label, selectedIndex == index) { onSelected(index) }
            }
        }
    }
}

@Composable
fun SettingsDefaultWallpaperRow(
    selected: DefaultWallpaperStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DefaultWallpaperStyle) -> Unit
) {
    val options = DefaultWallpaperStyle.entries
    SleepDownLiquidDropdownPreference(
        items = options.map { style ->
            if (style == DefaultWallpaperStyle.KANBAN) "看板娘" else "无"
        },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "默认壁纸",
        backdrop = backdrop,
        config = config,
        summary = "未设置自定义壁纸时使用",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 240.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
private fun SettingsFallbackChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
fun SettingsGroup(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    surfaceColorOverride: ComposeColor? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val miuixLayout = LocalGlassMiuixEnabled.current
    val shape = RoundedRectangle(if (miuixLayout) 24.dp else 30.dp)
    val darkTheme = appUsesDarkTheme(config)
    val contentColor = if (darkTheme) ComposeColor.White else ComposeColor(0xFF111111)
    if (miuixLayout) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .then(surfaceModifier)
                    .clip(shape)
                    .background(
                        surfaceColorOverride
                            ?: if (darkTheme) ComposeColor(0xFF1C1C1E) else ComposeColor(0xFFF7F7F7)
                    ),
                content = content
            )
        }
        return
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            surfaceColor = if (darkTheme) ComposeColor(0xFF1C1C1E).copy(alpha = 0.78f) else ComposeColor.White.copy(alpha = 0.94f)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(Modifier.padding(vertical = if (miuixLayout) 0.dp else 4.dp), content = content)
            }
        }
    } else {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .clip(shape)
                    .background(if (darkTheme) ComposeColor(0xFF1C1C1E) else ComposeColor.White)
                    .padding(vertical = if (miuixLayout) 0.dp else 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val neutralSelection = MaterialTheme.colorScheme.onSurface
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            summary = subtitle,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    neutralSelection.copy(alpha = if (selected) 0.10f else 0f)
                ),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
            .height(70.dp)
            .background(
                neutralSelection.copy(
                    alpha = when {
                        pressed -> 0.12f
                        selected -> 0.10f
                        else -> 0f
                    }
                )
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, backdrop: Backdrop?, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    if (LocalGlassMiuixEnabled.current) {
        GlassMiuixInteractivePreference(
            title = title,
            summary = subtitle.takeIf { it.isNotBlank() },
            controlWidth = 64.dp,
            controlHeight = 28.dp,
            enabled = enabled
        ) {
            if (enabled) {
                LiquidControlToggle(checked, onCheckedChange, backdrop)
            } else {
                LiquidControlToggle(checked, {}, backdrop)
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle.isBlank()) 56.dp else 76.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (enabled) {
            LiquidControlToggle(checked, onCheckedChange, backdrop)
        } else {
            LiquidControlToggle(checked, {}, backdrop)
        }
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
    onClick: () -> Unit
) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            summary = subtitle,
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
        )
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
fun SettingsDivider() {
    if (LocalGlassMiuixEnabled.current) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    )
}

@Composable
fun SettingsTextFieldRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    placeholder: String = "",
    moveCursorToEndOnFocus: Boolean = false
) {
    val context = LocalContext.current
    val isTimePicker = keyboardType == KeyboardType.Text && value.matches(Regex("\\d{1,2}:\\d{2}"))
    val isDatePicker = keyboardType == KeyboardType.Text && (
            value.matches(Regex("\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}")) ||
                    title.contains("日期")
            )
    if (isTimePicker || isDatePicker) {
        SettingsPickerValueRow(
            title = title,
            value = value,
            enabled = enabled,
            onClick = {
                if (isTimePicker) {
                    showNativeTimePicker(context, value, onValueChange)
                } else {
                    showNativeDatePicker(context, value, onValueChange)
                }
            }
        )
        return
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                SettingsInlineTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    keyboardType = keyboardType,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    ),
                    placeholder = placeholder,
                    moveCursorToEndOnFocus = moveCursorToEndOnFocus
                )
            }
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .offset(y = 1.dp)
        )
        SettingsInlineTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            keyboardType = keyboardType,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            ),
            placeholder = placeholder,
            moveCursorToEndOnFocus = moveCursorToEndOnFocus
        )
    }
}

@Composable
private fun SettingsInlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    textStyle: TextStyle,
    placeholder: String,
    moveCursorToEndOnFocus: Boolean
) {
    val fieldModifier = Modifier.width(170.dp)
    val decoration: @Composable ((@Composable () -> Unit) -> Unit) = { innerTextField ->
        Box(contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.End
                )
            }
            innerTextField()
        }
    }
    if (!moveCursorToEndOnFocus) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = textStyle,
            modifier = fieldModifier,
            decorationBox = decoration
        )
        return
    }

    var editableValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (editableValue.text != value) {
            editableValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            editableValue = editableValue.copy(
                selection = TextRange(editableValue.text.length)
            )
        }
    }
    BasicTextField(
        value = editableValue,
        onValueChange = { next ->
            editableValue = next
            if (next.text != value) onValueChange(next.text)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = textStyle,
        modifier = fieldModifier.onFocusChanged { focused = it.isFocused },
        decorationBox = decoration
    )
}

@Composable
internal fun SettingsDayViewModeRow(
    selected: DayViewMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DayViewMode) -> Unit
) {
    val options = DayViewMode.entries
    SleepDownLiquidDropdownPreference(
        items = listOf("默认模式", "两日模式"),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "日视图模式",
        backdrop = backdrop,
        config = config,
        summary = "两日模式会在原日视图下方继续显示第二天课程",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 220.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
internal fun SettingsWeekViewStyleRow(
    selected: WeekViewStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (WeekViewStyle) -> Unit
) {
    val options = WeekViewStyle.entries
    SleepDownLiquidDropdownPreference(
        items = listOf("普通模式", "无界模式"),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "周视图模式",
        backdrop = backdrop,
        config = config,
        summary = "无界模式会隐藏原来的表头、周切换按钮，页面更沉浸",
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 220.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsValueRow(title: String, value: String) {
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
    enabled: Boolean = true
) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixArrowPreference(
            title = title,
            endActions = {
                Text(
                    value.ifBlank { "未设置" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
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
        Text(value.ifBlank { "未设置" }, modifier = Modifier.offset(y = 1.dp).widthIn(min = 88.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
    }
}

fun showNativeDatePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val date = parseScheduleDate(currentValue) ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onPicked(formatScheduleDate(LocalDate.of(year, month + 1, day)))
        },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

fun showNativeTimePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val time = runCatching { ScheduleImportParser.parseTimeForUi(currentValue) }.getOrNull() ?: java.time.LocalTime.of(8, 0)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onPicked("%02d:%02d".format(hour, minute))
        },
        time.hour,
        time.minute,
        true
    ).show()
}

@Composable
fun SettingsDatePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val initialDate = remember(value, showPicker) { parseScheduleDate(value) ?: LocalDate.now() }
    var pickerYear by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.year) }
    var pickerMonth by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.monthValue) }
    var pickerDay by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.dayOfMonth) }
    SettingsPickerValueRow(
        title = title,
        value = value,
        enabled = enabled,
        onClick = { showPicker = true }
    )
    SleepDownPickerDialog(
        show = showPicker,
        title = "选择日期",
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        SettingsDatePickerContent(
            year = pickerYear, month = pickerMonth, day = pickerDay,
            onYearChange = { pickerYear = it }, onMonthChange = { pickerMonth = it },
            onDayChange = { pickerDay = it }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)) {
            QuickSheetLiquidAction(
                "取消", true, popupBackdrop, config,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) { showPicker = false }
            QuickSheetLiquidAction(
                "确定", true, popupBackdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                val maxDay = java.time.YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
                onValueChange(formatScheduleDate(LocalDate.of(pickerYear, pickerMonth, pickerDay.coerceAtMost(maxDay))))
                showPicker = false
            }
        }
    }
}

/**
 * Shared year/month/day wheels. Kept separate from the row so a centered dialog can host the same
 * control as one of its pages instead of stacking a second dialog on top of itself.
 */
@Composable
internal fun SettingsDatePickerContent(
    year: Int,
    month: Int,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) {
        if (day > maxDay) onDayChange(maxDay)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        // NumberPicker defaults to MIUIX title1. Three equal columns make a four digit year
        // ellipsize on narrow dialogs or when display/font scaling is raised. Keep the picker
        // readable without changing the dialog width: reserve more width for the year and cap
        // only this dense numeric control's effective size at the extreme DPI combinations.
        val compactPicker = maxWidth < 300.dp || fontScale > 1.12f
        val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
            fontSize = when {
                maxWidth < 270.dp || fontScale > 1.32f -> 21.sp
                compactPicker -> 24.sp
                else -> 28.sp
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compactPicker) 4.dp else 8.dp)
        ) {
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = year,
                onValueChange = onYearChange,
                range = 2000..2100,
                visibleItemCount = 3,
                label = { "${it}年" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(if (compactPicker) 1.65f else 1.5f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = month,
                onValueChange = onMonthChange,
                range = 1..12,
                visibleItemCount = 3,
                label = { "${it}月" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = day.coerceAtMost(maxDay),
                onValueChange = onDayChange,
                range = 1..maxDay,
                visibleItemCount = 3,
                label = { "${it}日" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SettingsTimePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true,
    minimumMinute: Int = 0,
    maximumMinute: Int = LastMinuteOfDay
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val initialTime = remember(value, showPicker) {
        runCatching { ScheduleImportParser.parseTimeForUi(value) }.getOrNull()
            ?: java.time.LocalTime.of(8, 0)
    }
    var pickerHour by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.hour) }
    var pickerMinute by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.minute) }
    val safeMinimum = minimumMinute.coerceIn(0, LastMinuteOfDay)
    val safeMaximum = maximumMinute.coerceIn(safeMinimum, LastMinuteOfDay)
    val selectedMinute = (pickerHour * 60 + pickerMinute).coerceIn(safeMinimum, safeMaximum)
    val selectedHour = selectedMinute / 60
    val allowedMinuteRange = minuteRangeForHour(selectedHour, safeMinimum, safeMaximum)
    SettingsPickerValueRow(title = title, value = value, enabled = enabled, onClick = { showPicker = true })
    SleepDownPickerDialog(
        show = showPicker,
        title = "选择时间",
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
            SettingsTimePickerContent(selectedMinute, safeMinimum..safeMaximum) {
                pickerHour = it / 60
                pickerMinute = it % 60
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction(
                    "取消", true, popupBackdrop, config,
                    modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) { showPicker = false }
                QuickSheetLiquidAction(
                    "确定", true, popupBackdrop, config, primary = true,
                    modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) {
                    onValueChange("%02d:%02d".format(selectedMinute / 60, selectedMinute % 60))
                    showPicker = false
                }
            }
    }
}

@Composable
internal fun SettingsMinutePickerRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true,
    range: IntRange = 0..180
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val safeValue = value.coerceIn(range)
    var pickerValue by remember(safeValue, showPicker) { mutableIntStateOf(safeValue) }
    SettingsPickerValueRow(
        title = title,
        value = "$safeValue 分钟",
        enabled = enabled,
        onClick = { showPicker = true }
    )
    SleepDownPickerDialog(
        show = showPicker,
        title = "选择提前时间",
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        SettingsMinutePickerContent(pickerValue, { pickerValue = it }, range)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
        ) {
            QuickSheetLiquidAction(
                "取消", true, popupBackdrop, config,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) { showPicker = false }
            QuickSheetLiquidAction(
                "确定", true, popupBackdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                onValueChange(pickerValue)
                showPicker = false
            }
        }
    }
}

/** Shared by settings rows and the period editor; typography and wheel geometry stay identical. */
@Composable
internal fun SettingsTimePickerContent(
    value: Int, range: IntRange, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit
) {
    val safeMinimum = range.first.coerceIn(0, LastMinuteOfDay)
    val safeMaximum = range.last.coerceIn(safeMinimum, LastMinuteOfDay)
    val selectedMinute = value.coerceIn(safeMinimum, safeMaximum)
    val selectedHour = selectedMinute / 60
    val allowedMinuteRange = minuteRangeForHour(selectedHour, safeMinimum, safeMaximum)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val pickerContentColor = LocalContentColor.current
                val compact = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
                val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    color = pickerContentColor,
                    fontSize = if (compact) 23.sp else 28.sp
                )
                val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                    selectedTextColor = pickerContentColor,
                    unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                    disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                    disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = selectedHour,
                        onValueChange = { hour ->
                            val minute = (selectedMinute % 60).coerceIn(minuteRangeForHour(hour, safeMinimum, safeMaximum))
                            onValueChange(hour * 60 + minute)
                        },
                        range = (safeMinimum / 60)..(safeMaximum / 60),
                        visibleItemCount = 3,
                        label = { "%02d时".format(it) },
                        colors = pickerColors,
                        textStyle = pickerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = selectedMinute % 60,
                        onValueChange = { onValueChange(selectedHour * 60 + it) },
                        range = allowedMinuteRange,
                        wrapAround = allowedMinuteRange == 0..59,
                        visibleItemCount = 3,
                        label = { "%02d分".format(it) },
                        colors = pickerColors,
                        textStyle = pickerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
}

@Composable
internal fun SettingsMinutePickerContent(
    value: Int, onValueChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier
) {
        val pickerContentColor = LocalContentColor.current
        top.yukonga.miuix.kmp.basic.NumberPicker(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            range = range,
            visibleItemCount = 3,
            label = { "${it}分钟" },
            colors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                selectedTextColor = pickerContentColor,
                unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
            ),
            textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                color = pickerContentColor,
                fontSize = 28.sp
            ),
            modifier = modifier.fillMaxWidth()
        )
}

private fun minuteRangeForHour(hour: Int, minimumMinute: Int, maximumMinute: Int): IntRange {
    val lower = if (hour == minimumMinute / 60) minimumMinute % 60 else 0
    val upper = if (hour == maximumMinute / 60) maximumMinute % 60 else 59
    return lower.coerceAtMost(upper)..upper
}

@Composable
internal fun ConstrainedPeriodTimePickers(
    startMinute: Int,
    endMinute: Int,
    bounds: PeriodTimePickerBounds,
    onSelectionChange: (PeriodTimeSelection) -> Unit,
    textStyle: TextStyle,
    showSectionLabels: Boolean,
    modifier: Modifier = Modifier,
    minimumDurationMinutes: Int = 1
) {
    val foreground = LocalContentColor.current
    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
        selectedTextColor = foreground,
        unselectedTextColor = foreground.copy(alpha = 0.34f),
        disabledSelectedTextColor = foreground.copy(alpha = 0.55f),
        disabledUnselectedTextColor = foreground.copy(alpha = 0.22f)
    )
    val duration = minimumDurationMinutes.coerceIn(1, (bounds.maximumEndMinute - bounds.minimumStartMinute).coerceAtLeast(1))
    val selection = constrainPeriodTimeSelection(startMinute, endMinute, bounds, minimumDurationMinutes = duration)
    LaunchedEffect(selection, startMinute, endMinute) {
        if (selection.startMinute != startMinute || selection.endMinute != endMinute) {
            onSelectionChange(selection)
        }
    }
    val latestStart = selection.endMinute - duration
    val earliestEnd = selection.startMinute + duration
    val startHour = selection.startMinute / 60
    val endHour = selection.endMinute / 60
    val startMinuteRange = minuteRangeForHour(startHour, bounds.minimumStartMinute, latestStart)
    val endMinuteRange = minuteRangeForHour(endHour, earliestEnd, bounds.maximumEndMinute)

    fun updateStart(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                candidate,
                selection.endMinute,
                bounds,
                PeriodTimeSelectionAnchor.START,
                minimumDurationMinutes = duration
            )
        )
    }

    fun updateEnd(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                selection.startMinute,
                candidate,
                bounds,
                PeriodTimeSelectionAnchor.END,
                minimumDurationMinutes = duration
            )
        )
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (showSectionLabels) 14.dp else 4.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("开始时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = startHour,
                    onValueChange = { hour ->
                        val minute = (selection.startMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, bounds.minimumStartMinute, latestStart))
                        updateStart(hour * 60 + minute)
                    },
                    range = (bounds.minimumStartMinute / 60)..(latestStart / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.startMinute % 60,
                    onValueChange = { updateStart(startHour * 60 + it) },
                    range = startMinuteRange,
                    wrapAround = startMinuteRange == 0..59,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("结束时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = endHour,
                    onValueChange = { hour ->
                        val minute = (selection.endMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, earliestEnd, bounds.maximumEndMinute))
                        updateEnd(hour * 60 + minute)
                    },
                    range = (earliestEnd / 60)..(bounds.maximumEndMinute / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.endMinute % 60,
                    onValueChange = { updateEnd(endHour * 60 + it) },
                    range = endMinuteRange,
                    wrapAround = endMinuteRange == 0..59,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    if (LocalCollapsibleSettingsInfoRows.current) {
        CollapsibleChangelogRow(title, body)
        return
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = title,
            summary = body,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

internal val LocalCollapsibleSettingsInfoRows = compositionLocalOf { false }

private val changelogReleaseDates = mapOf(
    "1.2.6_beta5" to "2026-09-16",
    "1.2.6_beta4" to "2026-09-15",
    "1.2.6_beta3" to "2026-09-15",
    "1.2.6_beta2" to "2026-09-14",
    "1.2.6_beta1" to "2026-09-14",
    "1.2.5" to "2026-09-14",
    "1.2.3" to "2026-09-01",
    "1.2.2" to "2026-08-29",
    "1.2.1" to "2026-08-28",
    "1.2.0" to "2026-08-19",
    "1.1.5" to "2026-08-11",
    "1.1.4" to "2026-08-08",
    "1.1.3" to "2026-08-08",
    "1.1.2" to "2026-08-05",
    "1.1.1" to "2026-08-02",
    "1.1.0" to "2026-07-30",
    "1.0.9" to "2026-07-26",
    "1.0.8" to "2026-07-22",
    "1.0.7" to "2026-07-22",
    "1.0.6" to "2026-07-20",
    "1.0.5" to "2026-07-19",
    "1.0.4" to "2026-07-18",
    "1.0.3" to "2026-07-18",
    "1.0.2" to "2026-07-16",
    "1.0.1" to "2026-07-15",
    "1.09 beta" to "2026-07-01",
    "1.04 beta" to "2026-05-27",
    "1.03 beta" to "2026-05-27"
)

@Composable
private fun CollapsibleChangelogRow(version: String, body: String) {
    val isCurrentVersion = version == BuildConfig.VERSION_NAME || version == "下一版本（开发中）"
    val releaseDate = changelogReleaseDates[version]
    val entries = remember(body) {
        val rawEntries = if ('\n' in body) {
            body.lineSequence()
        } else {
            body.splitToSequence('；')
        }
        rawEntries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { entry ->
                if (entry.lastOrNull() in setOf('。', '！', '？', '!', '?', '…')) {
                    entry
                } else {
                    "$entry。"
                }
            }
    }
    var expanded by remember(version) { mutableStateOf(isCurrentVersion) }
    val gentleExpansionEasing = remember {
        CubicBezierEasing(0.20f, 0f, 0f, 1f)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(280, easing = gentleExpansionEasing),
        label = "changelog-arrow-$version"
    )
    val details: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(420, easing = gentleExpansionEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(280, delayMillis = 60)),
            exit = shrinkVertically(
                animationSpec = tween(320, easing = gentleExpansionEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(220))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = version,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                releaseDate?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "折叠 $version" else "展开 $version",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
            },
            bottomAction = details,
            onClick = { expanded = !expanded }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    version,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                releaseDate?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "折叠 $version" else "展开 $version",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
            }
            details()
        }
    }
}

@Composable
fun GlassPreferenceCategory(text: String, modifier: Modifier = Modifier) {
    if (LocalGlassMiuixEnabled.current) {
        MiuixSmallTitle(
            text = text,
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(start = 6.dp, top = 8.dp, bottom = 8.dp)
        )
    } else {
        Text(
            text = text,
            modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GlassMiuixInteractivePreference(
    title: String,
    summary: String? = null,
    controlWidth: Dp,
    controlHeight: Dp = 42.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (enabled) 1f else 0.48f)
    ) {
        MiuixBasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = controlWidth + 12.dp),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(controlWidth, controlHeight),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun GlassPreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        GlassPreferenceCategory(title)
        content()
    }
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
        summary = "可显示上课地点、剩余时间或课程名称。",
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
    monochrome: Boolean = false
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
            modifier = modifier,
            height = 42.dp,
            tint = tint,
            surfaceColor = tint.copy(
                alpha = when {
                    destructive -> 0.86f
                    monochrome && darkTheme -> 0.58f
                    monochrome -> 0.74f
                    else -> 0.84f
                }
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            blurRadius = 4.dp,
            lensHeight = 14.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            Text(
                label,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Text(
            label,
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
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
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
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        alpha = revealProgress
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .clip(RoundedRectangle(15.dp))
                    .background(ComposeColor(0xFFFF3B30))
                    .clickable(onClick = ::requestDelete),
                contentAlignment = Alignment.Center
            ) {
                Image(
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

