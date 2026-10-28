package com.xiaomanjun.sleepdownschedule.app.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeResumeEffectTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun firstStartOnlyConsumesImportAndLaterStartsUseCurrentCallbacks() {
        val owner = Owner()
        val label = mutableStateOf("old")
        val mounted = mutableStateOf(true)
        var starts = 0
        val returns = mutableListOf<String>()
        compose.setContent {
            val currentLabel = label.value
            if (mounted.value) CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                HomeResumeEffect(onStart = { starts++ }, onReturn = { returns += currentLabel })
            }
        }
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            assertEquals(1, starts)
            assertEquals(emptyList<String>(), returns)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            label.value = "new"
        }
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            assertEquals(listOf("new"), returns)
            mounted.value = false
        }
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            assertEquals(2, starts)
            assertEquals(listOf("new"), returns)
        }
    }

    @Test fun attachingToStartedOwnerRetainsExistingReturnSemantics() {
        val owner = Owner()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        var returns = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                HomeResumeEffect(onStart = {}, onReturn = { returns++ })
            }
        }
        compose.runOnIdle { assertEquals(1, returns) }
    }
}
