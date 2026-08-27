package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

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
        SleepDownSecondaryPageStyle.Content -> SleepDownDesignTokens.SecondaryPage.HorizontalPadding
        SleepDownSecondaryPageStyle.Immersive -> Dp.Hairline
    }
    val resolvedSectionSpacing = sectionSpacing ?: when (style) {
        SleepDownSecondaryPageStyle.Settings ->
            SleepDownDesignTokens.SecondaryPage.SettingsSectionSpacing
        SleepDownSecondaryPageStyle.Content ->
            SleepDownDesignTokens.SecondaryPage.ContentSectionSpacing
        SleepDownSecondaryPageStyle.Immersive -> Dp.Hairline
    }
    LazyColumn(
        modifier = modifier,
        state = state,
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
