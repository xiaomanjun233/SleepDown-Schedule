package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator

/** Edits the calling detailed-settings draft; only its owner commits the configuration. */
class ScheduleAdjustmentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        val scheduleId = intent.getIntExtra(ScheduleIdExtra, -1)
        if (scheduleId <= 0) { finish(); return }
        val initial = decodeScheduleAdjustments(intent.getStringExtra(ArrangementsExtra).orEmpty())
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.allSchedulesState.collectAsStateWithLifecycle()
            val storedConfig = state.allConfigs.firstOrNull { it.id == scheduleId }
            CourseScheduleTheme(config = state.config) {
                if (storedConfig == null || !state.loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val draftState = remember(state, storedConfig) {
                        state.copy(
                            config = storedConfig.copy(
                                totalWeeks = intent.getIntExtra(TotalWeeksExtra, storedConfig.totalWeeks),
                                currentWeek = intent.getIntExtra(CurrentWeekExtra, storedConfig.currentWeek),
                                autoCurrentWeek = intent.getBooleanExtra(AutoWeekExtra, storedConfig.autoCurrentWeek),
                                termStartDate = intent.getStringExtra(TermStartExtra)?.ifBlank { null },
                                scheduleAdjustmentsJson = encodeScheduleAdjustments(initial)
                            ),
                            courses = state.allCourses.filter { it.scheduleId == scheduleId },
                            periods = state.allPeriods.filter { it.scheduleId == scheduleId }
                        )
                    }
                    ScheduleAdjustmentsScreen(draftState, initial,
                        onDismiss = { finish() },
                        onConfirm = { arrangements ->
                            setResult(Activity.RESULT_OK, Intent()
                                .putExtra(ScheduleIdExtra, scheduleId)
                                .putExtra(OriginalArrangementsExtra, encodeScheduleAdjustments(initial))
                                .putExtra(ArrangementsExtra, encodeScheduleAdjustments(arrangements)))
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        internal const val ArrangementsExtra = "schedule_adjustments_draft"
        internal const val ScheduleIdExtra = "schedule_id"
        internal const val OriginalArrangementsExtra = "original_schedule_adjustments"
        private const val TotalWeeksExtra = "total_weeks"
        private const val CurrentWeekExtra = "current_week"
        private const val AutoWeekExtra = "auto_current_week"
        private const val TermStartExtra = "term_start_date"

        internal fun intent(context: Context, config: ScheduleConfigEntity, draft: String): Intent =
            Intent(context, ScheduleAdjustmentsActivity::class.java)
                .putExtra(ScheduleIdExtra, config.id)
                .putExtra(TotalWeeksExtra, config.totalWeeks)
                .putExtra(CurrentWeekExtra, config.currentWeek)
                .putExtra(AutoWeekExtra, config.autoCurrentWeek)
                .putExtra(TermStartExtra, config.termStartDate.orEmpty())
                .putExtra(ArrangementsExtra, draft)
    }
}
