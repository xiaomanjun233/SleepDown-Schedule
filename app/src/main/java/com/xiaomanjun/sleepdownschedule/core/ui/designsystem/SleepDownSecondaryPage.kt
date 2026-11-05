package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberOverscrollEffect
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsEnabled
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsContentPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsListLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Content rhythms used below the existing SleepDown secondary-page chrome.
 *
 * This intentionally does not own navigation, Morph, Backdrop providers or persistence. A page
 * selects the closest rhythm and can override spacing when its content genuinely needs it.
 */
enum class SleepDownSecondaryPageStyle {
    /** Settings and form pages built from labelled groups. */
    Settings,

    /** Reading, history and task pages with a slightly tighter narrative rhythm. */
    Content,

    /** Edge-to-edge task surfaces that own all internal placement themselves. */
    Immersive
}

@Composable
fun SleepDownSecondaryPageList(
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    style: SleepDownSecondaryPageStyle = SleepDownSecondaryPageStyle.Settings,
    horizontalPadding: Dp? = null,
    sectionSpacing: Dp? = null,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val resolvedHorizontalPadding = horizontalPadding ?: when (style) {
        SleepDownSecondaryPageStyle.Settings,
        SleepDownSecondaryPageStyle.Content -> if (LocalCouiSettingsEnabled.current)
            CouiPageDefaults.horizontalMargin else SleepDownDesignTokens.SecondaryPage.HorizontalPadding
        SleepDownSecondaryPageStyle.Immersive -> Dp.Hairline
    }
    val resolvedSectionSpacing = sectionSpacing ?: when (style) {
        SleepDownSecondaryPageStyle.Settings -> if (LocalCouiSettingsEnabled.current)
            0.dp else SleepDownDesignTokens.SecondaryPage.SettingsSectionSpacing
        SleepDownSecondaryPageStyle.Content ->
            SleepDownDesignTokens.SecondaryPage.ContentSectionSpacing
        SleepDownSecondaryPageStyle.Immersive -> Dp.Hairline
    }
    CompositionLocalProvider(LocalCouiSettingsListLayout provides
        (LocalCouiSettingsEnabled.current && style == SleepDownSecondaryPageStyle.Settings && sectionSpacing == null)) {
    LazyColumn(
        modifier = modifier.then(LocalCouiSettingsContentPadding.current?.let {
            Modifier.consumeWindowInsets(it)
        } ?: Modifier),
        state = state,
        overscrollEffect = if (LocalCouiSettingsEnabled.current) rememberCouiOverscrollEffect()
            else rememberOverscrollEffect(),
        contentPadding = PaddingValues(
            start = resolvedHorizontalPadding,
            end = resolvedHorizontalPadding,
            top = contentTopPadding,
            bottom = contentBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(resolvedSectionSpacing),
        content = content
    )
    }
}
