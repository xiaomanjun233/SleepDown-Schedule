package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import com.itos.coui.layout.Item
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent as LegacyBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

internal val LocalCouiSettingsEnabled = staticCompositionLocalOf { false }
internal val LocalCouiLiquidMaterial = staticCompositionLocalOf { false }
internal val LocalCouiSettingsListLayout = staticCompositionLocalOf { false }
internal val LocalCouiSettingsGroupHasHeader = staticCompositionLocalOf { false }

/** E 盘 Demo 的 Settings 配色；通用组件的 container 并不是设置卡片底色。 */
internal fun settingsCouiColorScheme(dark: Boolean, accent: Color, onAccent: Color) =
    (if (dark) CouiPresets.Dark else CouiPresets.Light).copy(
        container = if (dark) Color.White.copy(alpha = 26f / 255f) else Color.White,
        accent = accent, onAccent = onAccent, accentOnSurface = accent,
        checkboxAccent = accent, menuCheckboxAccent = accent)

@Composable
internal fun settingsPreferencePadding(compact: Boolean = false): PaddingValues = when {
    compact -> PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    LocalCouiSettingsEnabled.current -> PaddingValues(horizontal = CouiPageDefaults.rowPadding,
        vertical = CouiPageDefaults.contentPadding)
    else -> PaddingValues(horizontal = 14.dp, vertical = 12.dp)
}

@Composable
internal fun CouiSettingsTheme(config: ScheduleConfigEntity, content: @Composable () -> Unit) {
    val enabled = true
    val dark = appUsesDarkTheme(config)
    val liquid = !LocalVisualEffects.current.usesNativeControls
    val accent = MaterialTheme.colorScheme.primary
    val colors = settingsCouiColorScheme(dark, accent, MaterialTheme.colorScheme.onPrimary)
    val inverse = if (dark) CouiPresets.Light else CouiPresets.Dark
    // 与 DemoTheme 的 native 分支一致：混用的 M3 内容槽也消费同一组颜色角色。
    val hostColors = if (enabled) MaterialTheme.colorScheme.copy(
        primary = colors.accent, onPrimary = colors.onAccent,
        primaryContainer = colors.container, onPrimaryContainer = colors.onSurface,
        inversePrimary = inverse.accent,
        secondary = colors.onSurfaceVariant, onSecondary = colors.surface,
        secondaryContainer = colors.container, onSecondaryContainer = colors.onSurface,
        tertiary = colors.accentOnSurface, onTertiary = colors.onAccent,
        tertiaryContainer = colors.surface, onTertiaryContainer = colors.onSurface,
        background = colors.background, onBackground = colors.onSurface,
        surface = colors.surface, onSurface = colors.onSurface,
        surfaceVariant = colors.container, onSurfaceVariant = colors.onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = inverse.surface, inverseOnSurface = inverse.onSurface,
        outline = colors.onSurfaceVariant, outlineVariant = colors.outlineVariant,
        surfaceBright = colors.container, surfaceDim = colors.background,
        surfaceContainer = colors.background, surfaceContainerLow = colors.surface,
        surfaceContainerLowest = colors.background, surfaceContainerHigh = colors.container,
        surfaceContainerHighest = colors.container,
    ) else MaterialTheme.colorScheme
    val hostTypography = if (enabled) MaterialTheme.typography.copy(
        titleLarge = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.SectionTitle),
        titleMedium = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.PreferenceTitle),
        titleSmall = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.GroupTitle),
        bodyLarge = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.Body),
        bodyMedium = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.Body),
        bodySmall = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.PreferenceSummary),
        labelLarge = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.PreferenceValue),
        labelMedium = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.Description),
        labelSmall = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.Description),
    ) else MaterialTheme.typography
    MaterialTheme(colorScheme = hostColors, typography = hostTypography) {
    CouiTheme(darkTheme = dark, colorScheme = colors) {
        CompositionLocalProvider(
            LocalCouiSettingsEnabled provides enabled,
            LocalCouiLiquidMaterial provides liquid,
            LocalCouiMaterialEnabled provides (enabled && !liquid && Build.VERSION.SDK_INT >= 31),
            content = content
        )
    }
    }
}

/** 业务只持有状态；设置正文使用 COUI Item 测量及交互。 */
@Composable
internal fun SettingsBasicComponent(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: BasicComponentColors? = null,
    summary: String? = null,
    summaryColor: BasicComponentColors? = null,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = settingsPreferencePadding(),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = CouiPageDefaults.iconSize,
) {
    if (!LocalCouiSettingsEnabled.current) {
        LegacyBasicComponent(modifier = modifier, title = title, summary = summary,
            startAction = startAction, endActions = endActions, bottomAction = bottomAction,
            insideMargin = insideMargin, onClick = onClick, holdDownState = holdDownState,
            enabled = enabled,
            titleColor = titleColor ?: top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(),
            summaryColor = summaryColor ?: top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor())
        return
    }
    SettingsBasicComponent(modifier, startAction, endActions, bottomAction, insideMargin,
        onClick, holdDownState, enabled, iconSize = iconSize) {
        if (title != null) Text(text = title, kind = CouiTextKind.PreferenceTitle,
            color = titleColor?.let { if (enabled) it.color else it.disabledColor }
                ?: CouiTheme.colorScheme.onSurface.copy(alpha = if (enabled) .9f else .3f))
        if (!summary.isNullOrEmpty()) Text(text = summary, kind = CouiTextKind.PreferenceSummary,
            color = summaryColor?.let { if (enabled) it.color else it.disabledColor }
                ?: CouiTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsBasicComponent(
    modifier: Modifier = Modifier,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = settingsPreferencePadding(),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    role: Role? = null,
    iconSize: Dp = CouiPageDefaults.iconSize,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalCouiSettingsEnabled.current) {
        LegacyBasicComponent(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin, onClick = onClick,
            holdDownState = holdDownState, enabled = enabled, role = role, content = content)
        return
    }
    val row: @Composable (Modifier) -> Unit = { rowModifier ->
        Item(modifier = rowModifier, icon = startAction, iconSize = iconSize, contentPadding = insideMargin, onClick = onClick, enabled = enabled,
            trailing = { endActions?.let { Row(content = it) } }) {
            Column(verticalArrangement = Arrangement.spacedBy(CouiPageDefaults.summarySpacing), content = content)
        }
    }
    if (bottomAction == null) row(modifier)
    else Column(modifier) {
        row(Modifier)
        Box(Modifier.padding(insideMargin)) { bottomAction() }
    }
}

@Composable
internal fun SettingsArrowPreference(
    modifier: Modifier = Modifier,
    title: String = "",
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = settingsPreferencePadding(),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = CouiPageDefaults.iconSize,
) {
    if (!LocalCouiSettingsEnabled.current) {
        top.yukonga.miuix.kmp.preference.ArrowPreference(modifier = modifier, title = title,
            summary = summary, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin, onClick = onClick,
            holdDownState = holdDownState, enabled = enabled)
        return
    }
    SettingsBasicComponent(modifier, title, summary = summary, startAction = startAction,
        endActions = {
            endActions()
            SettingsForwardIndicator(Modifier.padding(start = 4.dp))
        }, bottomAction = bottomAction, insideMargin = insideMargin, onClick = onClick,
        holdDownState = holdDownState, enabled = enabled, iconSize = iconSize)
}
