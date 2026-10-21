package com.xiaomanjun.sleepdownschedule

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.performance.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.domain.course.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.course.management.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost

open class CourseManagementDetailActivity : ComponentActivity() {
    private var stateHandoffToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val courseId = intent.getLongExtra(CourseManagementCourseIdExtra, Long.MIN_VALUE)
        stateHandoffToken = intent.courseManagementInitialStateTokenOrNull()
        val initialState = CourseManagementStateHandoffStore.get(stateHandoffToken)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val liveState by viewModel.state.collectAsStateWithLifecycle()
            val state = stableCourseManagementState(liveState, initialState)
            val group = remember(state.courses, courseId) {
                managedCourseGroupForCourseId(state.courses, courseId)
            }
            CourseScheduleTheme(config = state.config) {
                CourseManagementColorProvider(state) {
                    GlassMiuixSettingsTheme(settingsVisualConfig(state.config)) {
                        Box(Modifier.fillMaxSize()) {
                            CrossActivityTransitionHost(
                                activity = this@CourseManagementDetailActivity,
                                sourceContent = {
                                    group?.let {
                                        ManagedCourseListCardContent(
                                            group = it,
                                            config = state.config,
                                            periods = state.periods,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            ) { requestClose ->
                                if (group == null) {
                                    DetailActivityScaffold(
                                        title = "课程详情",
                                        config = state.config,
                                        onBack = requestClose
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                if (state.loaded) {
                                                    "课程记录已变更，请返回课程管理重新选择"
                                                } else {
                                                    "正在载入课程…"
                                                },
                                                color = appPanelForegroundColor(state.config).copy(alpha = 0.62f)
                                            )
                                        }
                                    }
                                } else {
                                    CourseManagementDetailPage(
                                        group = group,
                                        state = state,
                                        onBack = requestClose,
                                        onSave = { replacements ->
                                            if (replacements.isEmpty()) {
                                                viewModel.deleteCoursesAndThen(group.courses, requestClose)
                                            } else {
                                                viewModel.replaceCourseGroupAndThen(
                                                    group.courses,
                                                    replacements,
                                                    requestClose
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CourseManagementStateHandoffStore.remove(stateHandoffToken)
        }
        super.onDestroy()
    }
}

/**
 * Opaque manifest host for ColorOS ViewSeamless. It inherits the exact production detail page;
 * the legacy route continues to target [CourseManagementDetailActivity]'s translucent window.
 */
