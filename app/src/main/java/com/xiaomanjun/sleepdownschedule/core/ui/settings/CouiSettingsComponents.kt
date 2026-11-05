package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent as LegacyBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors

internal val LocalCouiSettingsEnabled = staticCompositionLocalOf { false }
internal val LocalCouiLiquidMaterial = staticCompositionLocalOf { false }

@Composable
internal fun CouiSettingsTheme(config: ScheduleConfigEntity, content: @Composable () -> Unit) {
    val enabled = BuildConfig.SLEEPDOWN_COUI_SETTINGS
    val dark = appUsesDarkTheme(config)
    val liquid = !LocalVisualEffects.current.usesNativeControls
    val colors = (if (dark) CouiPresets.Dark else CouiPresets.Light)
        .copy(accent = MaterialTheme.colorScheme.primary)
    CouiTheme(darkTheme = dark, colorScheme = colors) {
        CompositionLocalProvider(
            LocalCouiSettingsEnabled provides enabled,
            LocalCouiLiquidMaterial provides liquid,
            LocalCouiMaterialEnabled provides (enabled && !liquid && Build.VERSION.SDK_INT >= 31),
            content = content
        )
    }
}

/** 业务继续持有状态；此入口保留 Miuix 回滚路径，只切换设置行的布局。 */
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
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
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
        onClick, holdDownState, enabled, iconSize) {
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
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    role: Role? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalCouiSettingsEnabled.current) {
        LegacyBasicComponent(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin, onClick = onClick,
            holdDownState = holdDownState, enabled = enabled, role = role, content = content)
        return
    }
    Column(modifier) {
        Item(icon = startAction, iconSize = iconSize, onClick = onClick, enabled = enabled,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            trailing = { if (endActions != null) Row(content = endActions) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
        bottomAction?.invoke()
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
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
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
            Spacer(Modifier.width(8.dp))
            Icon(CouiIcons.Next, null, Modifier.size(20.dp), tint = CouiTheme.colorScheme.onSurfaceVariant)
        }, bottomAction = bottomAction, onClick = onClick, enabled = enabled, iconSize = iconSize)
}
