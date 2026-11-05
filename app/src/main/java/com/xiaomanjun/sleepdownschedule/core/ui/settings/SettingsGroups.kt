package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle

@Composable
fun SettingsGroup(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    surfaceColorOverride: ComposeColor? = null,
    topSpacing: androidx.compose.ui.unit.Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalCouiSettingsEnabled.current) {
        val colors = com.itos.coui.theme.CouiTheme.colorScheme
        val resolvedTopSpacing = topSpacing ?: if (LocalCouiSettingsListLayout.current && !LocalCouiSettingsGroupHasHeader.current)
            com.itos.coui.layout.CouiPageDefaults.groupSpacing else 0.dp
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            // Settings 17 的普通卡片消费者使用 24dp / 2.5；Demo 目录的权重 3 不套到表单。
            com.itos.coui.layout.Group(modifier = modifier.then(surfaceModifier),
                colors = com.itos.coui.layout.CouiPageDefaults.themeColors().copy(card = surfaceColorOverride ?: colors.container),
                shape = com.itos.coui.shape.CouiShape(24.dp, 2.5f),
                horizontalMargin = 0.dp, topSpacing = resolvedTopSpacing, content = content)
        }
        return
    }
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
fun SettingsDivider(startIndent: androidx.compose.ui.unit.Dp = 16.dp) {
    if (LocalCouiSettingsEnabled.current) {
        com.itos.coui.layout.Divider(startIndent = startIndent,
            color = com.itos.coui.theme.CouiTheme.colorScheme.outlineVariant)
        return
    }
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
fun GlassPreferenceCategory(text: String, modifier: Modifier = Modifier) {
    if (LocalCouiSettingsEnabled.current) {
        // 列表承担外侧 16dp，标题与上游 Group 的 32dp 起始位置对齐。
        val topSpacing = if (LocalCouiSettingsListLayout.current)
            com.itos.coui.layout.CouiPageDefaults.categoryTopSpacing else 0.dp
        Box(modifier.padding(top = topSpacing).padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 16.dp)) {
            com.itos.coui.text.Text(text = text, kind = com.itos.coui.text.CouiTextKind.GroupTitle,
                color = com.itos.coui.theme.CouiTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
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
fun GlassPreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (badgeText == null) GlassPreferenceCategory(title) else {
            val coui = LocalCouiSettingsEnabled.current
            Row(if (coui) Modifier.padding(top = if (LocalCouiSettingsListLayout.current)
                    com.itos.coui.layout.CouiPageDefaults.categoryTopSpacing else 0.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 16.dp)
                else Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp)) {
                PreferenceLabel(title, badgeText, style = if (coui)
                    com.itos.coui.text.rememberCouiTextStyle(com.itos.coui.text.CouiTextKind.GroupTitle)
                    else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CompositionLocalProvider(LocalCouiSettingsGroupHasHeader provides LocalCouiSettingsEnabled.current) {
            content()
        }
    }
}
