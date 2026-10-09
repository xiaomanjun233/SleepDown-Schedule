package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.xiaomanjun.sleepdownschedule.app.ui.HomeMode

/** Retained pages may not scroll again when revealed; replay their own last overlap on selection. */
@Composable
internal fun rememberHomeTopBarScrollReporter(
    mode: HomeMode,
    scheduleId: Int,
    onChange: (Boolean) -> Unit
): (HomeMode, Boolean) -> Unit {
    val overlap = remember(scheduleId) { HomeMode.entries.associateWith { mutableStateOf(false) } }
    val currentMode = rememberUpdatedState(mode)
    val currentCallback = rememberUpdatedState(onChange)
    LaunchedEffect(overlap) {
        snapshotFlow { overlap.getValue(currentMode.value).value }.collect { under ->
            currentCallback.value(under)
        }
    }
    return remember(overlap) { { source, under -> overlap.getValue(source).value = under } }
}
