package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared day-view divider; callers retain the foreground appropriate to their surface. */
@Composable
internal fun SleepDownTimeSectionDivider(
    textColor: Color,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    time: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        label()
        Box(Modifier.weight(1f).height(1.dp).background(textColor.copy(alpha = 0.18f)))
        time()
    }
}
