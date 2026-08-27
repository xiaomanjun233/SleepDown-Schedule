package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Stable SleepDown UI values shared by glass dialogs and quick sheets.
 *
 * Tokens intentionally mirror the already accepted production geometry. Moving a value here must
 * not be used as an excuse to retune it; visual changes require their own reviewed batch.
 */
object SleepDownDesignTokens {
    /**
     * Shared page chrome and content rhythm for secondary screens.
     *
     * These values describe the common family resemblance; individual pages may choose a
     * different content profile when their information density or interaction model requires it.
     */
    object SecondaryPage {
        val CompactTopBarHeight = 58.dp
        val TopOverlayExtra = 74.dp
        val ContentTopGap = 44.dp
        val HorizontalPadding = 16.dp
        val SettingsSectionSpacing = 14.dp
        val ContentSectionSpacing = 12.dp
        val BackButtonSize = 42.dp
        val CenterTitleSideClearance = 64.dp
    }

    /**
     * Shared geometry and material limits for every modified Miuix centered dialog.
     *
     * Confirmation alerts and compact pickers intentionally share this outer silhouette while
     * retaining their own content density. The actual outline is supplied by Kyant Shapes' G2
     * continuous-curvature rounded rectangle rather than Compose's circular corner shape.
     */
    object CenteredDialog {
        // Dialog bodies retain SleepDown v2's content-driven measurement. The supplied reference
        // is used only for the denser 48dp action controls and the current material treatment.
        val Width = 286.dp
        val ContentPadding = 14.dp
        // Alerts keep the shared v2 shell geometry but sit deliberately narrower than the compact
        // pickers that share the same silhouette.
        val AlertWidth = 260.dp
        // Alert actions sit as close to the bottom edge as they do to the side edges (14dp).
        val AlertVerticalPadding = 14.dp
        val AlertTopInset = 32.dp
        val AlertTextHorizontalInset = 8.dp
        // 14dp shell inset + the 24dp radius of a 48dp Capsule. Keeping their curve centres on
        // the same axis avoids the outer G2 shell looking flatter than the action row below it.
        val Corner = 39.dp
        // Restore the SleepDown v2 shell rather than the later Apple-style dense material.
        // The old shell used a 12dp blur; 16dp is the requested slightly softer variant.
        val MaxBlur = 16.dp
        val ActionHeight = 50.dp
        val ActionSpacing = 10.dp
        val MessageActionSpacing = 28.dp
        // Short-copy alerts may tighten this gap without changing their action geometry or the
        // action-to-edge spacing.
        val AlertSingleLineActionSpacing = 18.dp
        // Single-row-action alerts are tightened only through their copy band. Their action row
        // keeps the accepted height, spacing and edge inset.
        val CompactAlertTopInset = 28.dp
        val CompactTitleContentSpacing = 11.dp
        val CompactMessageActionSpacing = 27.dp
        val CompactSingleLineActionSpacing = 16.dp
        // A title plus a two-line message keeps its measured shell and action row unchanged; only
        // the copy is optically lifted so the extra line does not make the upper half look heavy.
        val ThreeLineAlertTextLift = 3.dp
        val LensHeight = 4.dp
        val LensAmount = 8.dp
        val TitleContentSpacing = 13.dp
        // A slightly wider single-pass blur is still inexpensive, while remaining strong enough
        // to diffuse the large Miuix title glyphs together with the rest of the page underlay.
        val BackgroundBlur = 10.dp
        const val DarkSurfaceAlpha = 0.74f
    }

    object Dialog {
        val ContainerCorner = 32.dp
        val AlertCorner = CenteredDialog.Corner
        val HeaderHeight = 70.dp
        val HorizontalPadding = 16.dp
        val ContentSpacing = 10.dp
        val AlertContentHorizontalPadding = CenteredDialog.ContentPadding
        val AlertContentVerticalPadding = CenteredDialog.ContentPadding
        val ActionSpacing = 8.dp
        val ActionHeight = 50.dp
        val MaxWidth = 600.dp
        val MaxHeight = 600.dp
    }

    object Field {
        val SingleLineCorner = 50.dp
        val MultiLineCorner = 24.dp
        val HorizontalPadding = 16.dp
        val SingleLineVerticalPadding = 12.dp
        val MultiLineVerticalPadding = 14.dp
    }

    object QuickSheet {
        val BottomCorner = 28.dp
        val CenterCorner = CenteredDialog.Corner
        val InnerCorner = 24.dp
        val HorizontalMargin = 18.dp
        val VerticalMargin = 18.dp
        // Pickers are working surfaces rather than reading-first alerts. Their title therefore
        // starts closer to the shell and hands off to the controls with a compact, even rhythm.
        val PickerVerticalInset = 20.dp
        val PickerContentSpacing = 16.dp
        val PickerContentPadding = 2.dp
    }
}

@Composable
fun sleepDownGlassForegroundColor(config: ScheduleConfigEntity): Color =
    if (glassUsesLightStyle(config)) Color.Black else Color.White

@Composable
fun sleepDownPanelForegroundColor(config: ScheduleConfigEntity): Color =
    if (appUsesDarkTheme(config)) Color.White else Color(0xFF111111)
