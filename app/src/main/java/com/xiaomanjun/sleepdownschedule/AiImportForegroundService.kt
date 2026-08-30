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
import android.os.IBinder
import androidx.core.content.ContextCompat

class AiImportForegroundService : Service() {
    private lateinit var notificationManager: NotificationManager
    private var activeTaskId: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        when (intent?.action) {
            ACTION_START -> {
                activeTaskId = taskId
                startForeground(
                    RUNNING_NOTIFICATION_ID,
                    runningNotification(taskId, intent.getStringExtra(EXTRA_STATUS).orEmpty())
                )
            }
            ACTION_UPDATE -> if (taskId == activeTaskId) {
                notificationManager.notify(
                    RUNNING_NOTIFICATION_ID,
                    runningNotification(taskId, intent.getStringExtra(EXTRA_STATUS).orEmpty())
                )
            }
            ACTION_COMPLETE -> if (taskId == activeTaskId) {
                val count = intent.getIntExtra(EXTRA_COURSE_COUNT, 0)
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (NotificationScheduler.canPostNotifications(this)) {
                    notificationManager.notify(
                        RESULT_NOTIFICATION_ID,
                        completedNotification(count)
                    )
                }
                stopSelf()
            }
            ACTION_FAILED -> if (taskId == activeTaskId) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (NotificationScheduler.canPostNotifications(this)) {
                    notificationManager.notify(
                        RESULT_NOTIFICATION_ID,
                        failedNotification(taskId, intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
                    )
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun runningNotification(taskId: String, status: String): Notification =
        Notification.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("SleepDown · AI 导入")
            .setContentText(status.ifBlank { "正在整理输入" })
            .setContentIntent(progressPendingIntent(taskId, 8401))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(0xFF0A84FF.toInt())
            .setProgress(0, 0, true)
            .requestPromotedOngoing("AI导入中")
            .build()

    private fun completedNotification(courseCount: Int): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("课表解析完成 · 发现 ${courseCount} 门课程")
            .setContentText("点击查看导入预览")
            .setContentIntent(progressPendingIntent(activeTaskId.orEmpty(), 8402))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(0xFF0A84FF.toInt())
            .build()

    private fun failedNotification(taskId: String, message: String): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("AI 导入未完成")
            .setContentText(message.ifBlank { "点击查看任务详情" })
            .setContentIntent(progressPendingIntent(taskId, 8403))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

    private fun progressPendingIntent(taskId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, AiEduImportProgressActivity::class.java)
                .putExtra(AiImportTaskManager.EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    RUNNING_CHANNEL_ID,
                    "AI 导入运行状态",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) },
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    "AI 导入结果",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { setShowBadge(false) }
            )
        )
    }

    private fun Notification.Builder.requestPromotedOngoing(shortText: String): Notification.Builder = apply {
        runCatching {
            javaClass.getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE).invoke(this, true)
            extras.putBoolean("android.requestPromotedOngoing", true)
            javaClass.getMethod("setShortCriticalText", String::class.java).invoke(this, shortText)
            extras.putString("android.shortCriticalText", shortText)
        }
    }

    companion object {
        private val ACTION_START = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_START"
        private val ACTION_UPDATE = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_UPDATE"
        private val ACTION_COMPLETE = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_COMPLETE"
        private val ACTION_FAILED = "${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_FAILED"
        private const val EXTRA_TASK_ID = "ai_import_task_id"
        private const val EXTRA_STATUS = "ai_import_status"
        private const val EXTRA_COURSE_COUNT = "ai_import_course_count"
        private const val EXTRA_MESSAGE = "ai_import_message"
        private const val RUNNING_CHANNEL_ID = "ai_import_running"
        private const val RESULT_CHANNEL_ID = "ai_import_result"
        private const val RUNNING_NOTIFICATION_ID = 20260830
        private const val RESULT_NOTIFICATION_ID = 20260831

        fun start(context: Context, taskId: String, status: String) {
            ContextCompat.startForegroundService(
                context,
                serviceIntent(context, ACTION_START, taskId).putExtra(EXTRA_STATUS, status)
            )
        }

        fun update(context: Context, taskId: String, status: String) {
            context.startService(
                serviceIntent(context, ACTION_UPDATE, taskId).putExtra(EXTRA_STATUS, status)
            )
        }

        fun complete(context: Context, taskId: String, courseCount: Int) {
            context.startService(
                serviceIntent(context, ACTION_COMPLETE, taskId)
                    .putExtra(EXTRA_COURSE_COUNT, courseCount)
            )
        }

        fun fail(context: Context, taskId: String, message: String) {
            context.startService(
                serviceIntent(context, ACTION_FAILED, taskId).putExtra(EXTRA_MESSAGE, message)
            )
        }

        private fun serviceIntent(context: Context, action: String, taskId: String): Intent =
            Intent(context, AiImportForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
