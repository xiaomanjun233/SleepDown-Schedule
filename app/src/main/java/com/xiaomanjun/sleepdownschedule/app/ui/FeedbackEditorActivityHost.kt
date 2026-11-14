package com.xiaomanjun.sleepdownschedule.app.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.feature.feedback.FeedbackEditor
import com.xiaomanjun.sleepdownschedule.feature.feedback.FeedbackEditorAccentExtra
import com.xiaomanjun.sleepdownschedule.feature.feedback.FeedbackEditorDarkExtra
import com.xiaomanjun.sleepdownschedule.feature.feedback.FeedbackClient
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost

open class FeedbackEditorActivityHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        val dark = intent.getBooleanExtra(FeedbackEditorDarkExtra, false)
        val accent = Color(intent.getIntExtra(FeedbackEditorAccentExtra, 0xFF007AFF.toInt()))
        val config = defaultConfig().copy(followSystemDarkMode = false, darkMode = dark)
        val client = FeedbackClient(applicationContext)
        setContent {
            CourseScheduleTheme(config) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = accent)) {
                    CrossActivityTransitionHost(
                        activity = this,
                        sourceContent = {},
                        handleSystemBack = false,
                    ) { requestClose ->
                        FeedbackEditor(config, client) { submitted ->
                            if (submitted) setResult(Activity.RESULT_OK)
                            requestClose()
                        }
                    }
                }
            }
        }
    }
}
