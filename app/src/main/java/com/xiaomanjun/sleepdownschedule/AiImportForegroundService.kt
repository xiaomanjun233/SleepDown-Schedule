package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportTaskManager
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class AiImportForegroundService : Service() {
    private var activeTaskId: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        Log.d(TAG, "SERVICE_CREATE pid=${Process.myPid()} elapsed=${SystemClock.elapsedRealtime()}")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        Log.d(
            TAG,
            "SERVICE_START action=${intent?.action} task=$taskId pid=${Process.myPid()}" +
                " elapsed=${SystemClock.elapsedRealtime()}"
        )
        when (intent?.action) {
            ACTION_START -> {
                activeTaskId = taskId
                ServiceCompat.startForeground(
                    this,
                    RUNNING_NOTIFICATION_ID,
                    runningNotification(
                        taskId,
                        intent.getStringExtra(EXTRA_STATUS).orEmpty()
                    ),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                // The workflow itself runs on the process application scope (same as the Day
                // Agent), so an OEM stopping this service cannot cancel the in-flight request.
                val job = AiImportTaskManager.launchPending(this, taskId)
                if (job == null) {
                    Log.e(TAG, "No pending AI import workflow for task=$taskId")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    Log.d(TAG, "AI import workflow owned by foreground service task=$taskId")
                }
            }
            ACTION_COMPLETE, ACTION_FAILED -> {
                // The result notification has already been posted directly by
                // AiImportTaskManager; this service only clears its foreground state.
                activeTaskId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(
            TAG,
            "SERVICE_TASK_REMOVED task=$activeTaskId pid=${Process.myPid()}" +
                " elapsed=${SystemClock.elapsedRealtime()}"
        )
    }

    override fun onDestroy() {
        Log.d(
            TAG,
            "SERVICE_DESTROY task=$activeTaskId pid=${Process.myPid()}" +
                " elapsed=${SystemClock.elapsedRealtime()}"
        )
        // Deliberately no cancellation here: the workflow intentionally outlives this service.
        super.onDestroy()
    }

    private fun runningNotification(taskId: String, status: String): Notification =
        buildRunningNotification(this, taskId, status)

    companion object {
        private val ACTION_START = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_START"
        private val ACTION_COMPLETE = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_COMPLETE"
        private val ACTION_FAILED = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_FAILED"
        private const val EXTRA_TASK_ID = "ai_import_task_id"
        private const val EXTRA_STATUS = "ai_import_status"
        private const val EXTRA_MESSAGE = "ai_import_message"
        // The old channel was created at LOW importance and Android does not allow apps to raise
        // an existing channel. A new ID lets AI import use the same promotable importance as the
        // mature course live-update path.
        private const val RUNNING_CHANNEL_ID = "ai_import_live_update"
        private const val RESULT_CHANNEL_ID = "ai_import_result"
        private const val RUNNING_NOTIFICATION_ID = 20260830
        private const val RESULT_NOTIFICATION_ID = 20260831
        private const val TAG = "SleepDownAiImport"

        fun start(context: Context, taskId: String, status: String) {
            ContextCompat.startForegroundService(
                context,
                serviceIntent(context, ACTION_START, taskId).putExtra(EXTRA_STATUS, status)
            )
        }

        /**
         * Updates the existing foreground notification directly, without dispatching another
         * service command. The run phase must not re-enter the service (an OEM background
         * service gate would otherwise be able to interleave with every HTTP phase).
         */
        fun update(context: Context, taskId: String, status: String) {
            ensureChannels(context)
            context.getSystemService(NotificationManager::class.java)
                .notify(RUNNING_NOTIFICATION_ID, buildRunningNotification(context, taskId, status))
        }

        fun complete(context: Context, taskId: String, courseCount: Int) {
            // Post the result directly from the task side: the service may have been stopped
            // by an OEM battery guard while the app was backgrounded, and the completion must
            // still be shown.
            postResultNotification(context) {
                completedNotification(context, taskId, courseCount)
            }
            dispatchToRunningService(
                context,
                serviceIntent(context, ACTION_COMPLETE, taskId)
            )
        }

        fun fail(context: Context, taskId: String, message: String) {
            postResultNotification(context) {
                failedNotification(context, taskId, message)
            }
            dispatchToRunningService(
                context,
                serviceIntent(context, ACTION_FAILED, taskId).putExtra(EXTRA_MESSAGE, message)
            )
        }

        fun clearCompletion(context: Context, taskId: String?) {
            if (taskId.isNullOrBlank()) return
            context.getSystemService(NotificationManager::class.java)
                .cancel(RESULT_NOTIFICATION_ID)
        }

        private fun postResultNotification(context: Context, build: () -> Notification) {
            ensureChannels(context)
            if (NotificationScheduler.canPostNotifications(context)) {
                context.getSystemService(NotificationManager::class.java)
                    .notify(RESULT_NOTIFICATION_ID, build())
            }
        }

        private fun ensureChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannels(
                    listOf(
                        NotificationChannel(
                            RUNNING_CHANNEL_ID,
                            "AI 导入运行状态",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { setShowBadge(false) },
                        NotificationChannel(
                            RESULT_CHANNEL_ID,
                            "AI 导入结果",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { setShowBadge(false) }
                    )
                )
        }

        private fun buildRunningNotification(
            context: Context,
            taskId: String,
            status: String
        ): Notification {
            val builder = Notification.Builder(context, RUNNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_agent_thinking)
                .setContentTitle("SleepDown · AI 导入")
                .setContentText(status.ifBlank { "正在整理输入" })
                .setContentIntent(progressPendingIntent(context, taskId, 8401))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setColor(0xFF0A84FF.toInt())
                .requestPromotedOngoing("AI导入中")
            return builder.build()
                .also { notification ->
                    val promotable = runCatching {
                        notification.javaClass
                            .getMethod("hasPromotableCharacteristics")
                            .invoke(notification) as? Boolean
                    }.getOrNull()
                    val requested = notification.extras
                        .getBoolean("android.requestPromotedOngoing", false)
                    Log.d(TAG, "AI import live update built: promotable=$promotable, requested=$requested")
                }
        }

        private fun completedNotification(
            context: Context,
            taskId: String,
            courseCount: Int
        ): Notification {
            return Notification.Builder(context, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_agent_thinking)
                .setContentTitle("课表解析完成 · 发现 ${courseCount} 门课程")
                .setContentText("点击查看导入预览")
                .setContentIntent(progressPendingIntent(context, taskId, 8402))
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setColor(0xFF0A84FF.toInt())
                .requestPromotedOngoing("待查看")
                .build()
        }

        private fun failedNotification(
            context: Context,
            taskId: String,
            message: String
        ): Notification =
            Notification.Builder(context, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_agent_thinking)
                .setContentTitle("AI 导入未完成")
                .setContentText(message.ifBlank { "点击查看任务详情" })
                .setContentIntent(progressPendingIntent(context, taskId, 8403))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .build()

        private fun progressPendingIntent(
            context: Context,
            taskId: String,
            requestCode: Int
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, AiEduImportProgressActivity::class.java)
                    .putExtra(AiImportTaskManager.EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun Notification.Builder.requestPromotedOngoing(shortText: String): Notification.Builder = apply {
            runCatching {
                javaClass.getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE).invoke(this, true)
            }
            extras.putBoolean("android.requestPromotedOngoing", true)
            runCatching {
                javaClass.getMethod("setShortCriticalText", CharSequence::class.java).invoke(this, shortText)
            }.recoverCatching {
                javaClass.getMethod("setShortCriticalText", String::class.java).invoke(this, shortText)
            }
            extras.putCharSequence("android.shortCriticalText", shortText)
        }

        private fun dispatchToRunningService(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (error: IllegalStateException) {
                // A vendor background-service gate must not turn a notification refresh into a
                // cancelled provider request. The workflow keeps running on the application
                // scope; the next meaningful state can still be observed when the UI returns.
                Log.w(TAG, "Unable to update AI import foreground notification", error)
            }
        }

        private fun serviceIntent(context: Context, action: String, taskId: String): Intent =
            Intent(context, AiImportForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
