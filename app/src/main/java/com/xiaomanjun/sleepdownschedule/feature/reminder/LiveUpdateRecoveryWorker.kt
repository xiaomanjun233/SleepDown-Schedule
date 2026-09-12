package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import java.util.concurrent.TimeUnit

/** Low-frequency repair of lost alarm registrations; never drives minute refreshes. */
class LiveUpdateRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CourseScheduleApp
        val snapshot = app.repository.activeSnapshot()
        NotificationScheduler.refreshToday(
            app, snapshot.courses, snapshot.config, snapshot.periods, forceReschedule = true
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "live_update_alarm_recovery"

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
