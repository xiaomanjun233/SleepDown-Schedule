package com.xiaomanjun.sleepdownschedule.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Owns the observer, while navigation/import policy stays at the application assembly point. */
@Composable
internal fun HomeResumeEffect(onStart: () -> Unit, onReturn: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentStart by rememberUpdatedState(onStart)
    val currentReturn by rememberUpdatedState(onReturn)
    var started by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                currentStart()
                if (started) currentReturn() else started = true
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
