package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import java.util.concurrent.TimeUnit

/** Low-frequency repair of lost alarm registrations; never drives minute refreshes. */
class LiveUpdateRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CourseScheduleApp
        val snapshot = app.repository.activeSnapshot()
        NotificationScheduler.refreshToday(
            app, snapshot.courses, snapshot.config, snapshot.periods,
            forceReschedule = !inputData.getBoolean(ISLAND_CHECKPOINT, false)
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "live_update_alarm_recovery"
        private const val ISLAND_CHECKPOINT = "island_checkpoint"

        fun scheduleIslandCheckpoint(context: Context, checkpointAtMillis: Long?) {
            val checkpoint = checkpointAtMillis ?: return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "live_update_island_checkpoint_$checkpoint",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LiveUpdateRecoveryWorker>()
                    .setInitialDelay((checkpoint - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ISLAND_CHECKPOINT to true))
                    .build()
            )
        }

        fun updateSchedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LiveUpdateRecoveryWorker>(6, TimeUnit.HOURS)
                    .setInitialDelay(6, TimeUnit.HOURS)
                    .build()
            )
        }
    }
}
