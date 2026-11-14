package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.text.readableAccentColor
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentRunTraceAppearance

/** Mobile dialogue hierarchy inspired by Codex, using the user's system typeface and font scale. */
internal object AiImportTextStyles {
    val Body = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp)
    val Input = Body.copy(lineHeight = 24.sp)
    val Progress = Body.copy(fontSize = 13.sp, lineHeight = 20.sp)
    val PreviewMetadata = Body.copy(fontSize = 14.sp, lineHeight = 22.sp)
    val Secondary = Body.copy(fontSize = 13.sp, lineHeight = 20.sp)
    val Operation = Secondary.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    val Caption = Body.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val Label = Caption.copy(fontWeight = FontWeight.Medium)
    val Action = Secondary.copy(fontWeight = FontWeight.Medium)
    val SmallTitle = Progress.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val SectionTitle = Body.copy(fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val Title = SectionTitle.copy(fontSize = 18.sp, lineHeight = 26.sp)
}

@Immutable
internal data class AiImportTextColors(val primary: Color, val secondary: Color, val muted: Color)

internal val LocalAiImportTextColors = staticCompositionLocalOf {
    AiImportTextColors(Color(0xFF1F1F1F), Color(0xFF5F5F5F), Color(0xFF666666))
}

@Composable
internal fun AiImportConversationTheme(config: ScheduleConfigEntity, content: @Composable () -> Unit) {
    val background = settingsPageBackground(settingsVisualConfig(config))
    val colors = remember(background) {
        val seeds = if (background.luminance() < 0.5f)
            listOf(Color(0xFFECECEC), Color(0xFFB5B5B5), Color(0xFF9A9A9A))
        else listOf(Color(0xFF1F1F1F), Color(0xFF5F5F5F), Color(0xFF666666))
        val ink = seeds.map { readableAccentColor(it, background) }
        AiImportTextColors(ink[0], ink[1], ink[2])
    }
    val base = MaterialTheme.typography
    val typography = remember(base) { base.copy(
        titleLarge = AiImportTextStyles.Title, titleMedium = AiImportTextStyles.SectionTitle,
        titleSmall = AiImportTextStyles.SmallTitle, bodyLarge = AiImportTextStyles.Body,
        bodyMedium = AiImportTextStyles.PreviewMetadata, bodySmall = AiImportTextStyles.Secondary,
        labelLarge = AiImportTextStyles.Action, labelMedium = AiImportTextStyles.Label,
        labelSmall = AiImportTextStyles.Caption) }
    CompositionLocalProvider(LocalAiImportTextColors provides colors) {
        MaterialTheme(typography = typography, colorScheme = MaterialTheme.colorScheme.copy(
            onBackground = colors.primary, onSurface = colors.primary, onSurfaceVariant = colors.secondary), content = content)
    }
}

@Composable
internal fun aiImportTraceAppearance(): AgentRunTraceAppearance {
    val colors = LocalAiImportTextColors.current
    return remember(colors) { AgentRunTraceAppearance(colors.secondary, colors.secondary, colors.muted,
        colors.primary, AiImportTextStyles.Operation, AiImportTextStyles.Secondary) }
}
