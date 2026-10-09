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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Commits only adjustments; the calling detailed-settings page retains its other drafts. */
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
                    val originalStored = remember(scheduleId) { storedConfig.scheduleAdjustmentsJson }
                    var saving by remember { mutableStateOf(false) }
                    var saveError by remember { mutableStateOf<String?>(null) }
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
                    ScheduleAdjustmentsScreen(draftState, initial, saving = saving, saveError = saveError,
                        onDismiss = { if (!saving) finish() },
                        onConfirm = { arrangements ->
                            if (!saving) {
                                saving = true
                                saveError = null
                                val value = encodeScheduleAdjustments(arrangements)
                                viewModel.saveScheduleAdjustments(scheduleId, originalStored, value,
                                    onSuccess = {
                                        viewModel.refreshNotificationsAfterSave()
                                        setResult(Activity.RESULT_OK, Intent()
                                            .putExtra(ScheduleIdExtra, scheduleId)
                                            .putExtra(OriginalArrangementsExtra, encodeScheduleAdjustments(initial))
                                            .putExtra(SavedExtra, true)
                                            .putExtra(ArrangementsExtra, value))
                                        finish()
                                    }, onFailure = {
                                        saving = false
                                        saveError = "保存失败，草稿已保留。请确认安排没有被其他入口修改后重试。"
                                    })
                            }
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
        internal const val SavedExtra = "schedule_adjustments_saved"
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
