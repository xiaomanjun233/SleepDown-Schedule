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
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AiImportForegroundService : Service() {
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeTaskId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
                activeJob?.cancel()
                activeTaskId = taskId
                val notification = runningNotification(
                    taskId,
                    intent.getStringExtra(EXTRA_STATUS).orEmpty()
                )
                startForeground(
                    RUNNING_NOTIFICATION_ID,
                    notification
                )
                acquireWakeLock()
                activeJob = AiImportTaskManager.launchPending(taskId, serviceScope)
                if (activeJob == null) {
                    Log.e(TAG, "No pending AI import workflow for task=$taskId")
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    Log.d(TAG, "AI import workflow owned by foreground service task=$taskId")
                }
            }
            ACTION_UPDATE -> if (taskId == activeTaskId) {
                notificationManager.notify(
                    RUNNING_NOTIFICATION_ID,
                    runningNotification(taskId, intent.getStringExtra(EXTRA_STATUS).orEmpty())
                )
            }
            ACTION_COMPLETE -> if (taskId == activeTaskId) {
                val count = intent.getIntExtra(EXTRA_COURSE_COUNT, 0)
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (NotificationScheduler.canPostNotifications(this)) {
                    notificationManager.notify(
                        RESULT_NOTIFICATION_ID,
                        completedNotification(taskId, count)
                    )
                }
                activeTaskId = null
                stopSelf()
            }
            ACTION_FAILED -> if (taskId == activeTaskId) {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (NotificationScheduler.canPostNotifications(this)) {
                    notificationManager.notify(
                        RESULT_NOTIFICATION_ID,
                        failedNotification(taskId, intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
                    )
                }
                activeTaskId = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun runningNotification(taskId: String, status: String): Notification {
        val stage = stageFor(status)
        val builder = Notification.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("SleepDown · AI 导入")
            .setContentText(status.ifBlank { "正在整理输入" })
            .setContentIntent(progressPendingIntent(taskId, 8401))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_EVENT)
            .setColor(0xFF0A84FF.toInt())
            .requestPromotedOngoing("AI导入中")
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(aiProgressStyle(stage))
        } else {
            builder.setProgress(AI_STAGE_COUNT, stage, false)
        }
        return builder.build()
            .also { notification ->
                val promotable = runCatching {
                    notification.javaClass
                        .getMethod("hasPromotableCharacteristics")
                        .invoke(notification) as? Boolean
                }.getOrNull()
                Log.d(
                    TAG,
                    "AI import live update built: promotable=$promotable, " +
                        "requested=${notification.extras.getBoolean("android.requestPromotedOngoing", false)}"
                )
            }
    }

    private fun completedNotification(taskId: String, courseCount: Int): Notification {
        val builder = Notification.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("课表解析完成 · 发现 ${courseCount} 门课程")
            .setContentText("点击查看导入预览")
            .setContentIntent(progressPendingIntent(taskId, 8402))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(0xFF0A84FF.toInt())
            .requestPromotedOngoing("待查看")
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(aiProgressStyle(AI_STAGE_COUNT))
        } else {
            builder.setProgress(AI_STAGE_COUNT, AI_STAGE_COUNT, false)
        }
        return builder.build()
    }

    private fun stageFor(status: String): Int = when {
        "生成导入预览" in status -> 5
        "校验" in status -> 4
        "解析" in status -> 3
        "已发送" in status -> 2
        else -> 1
    }

    @Suppress("NewApi")
    private fun aiProgressStyle(progress: Int): Notification.ProgressStyle =
        Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(AI_STAGE_COUNT)
                        .setColor(0xFF0A84FF.toInt())
                )
            )
            .setProgress(progress)
            .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.ic_agent_thinking))

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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepDown:ai_import")
            ?.apply { acquire(AI_IMPORT_WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
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
        // The old channel was created at LOW importance and Android does not allow apps to raise
        // an existing channel. A new ID lets AI import use the same promotable importance as the
        // mature course live-update path.
        private const val RUNNING_CHANNEL_ID = "ai_import_live_update"
        private const val RESULT_CHANNEL_ID = "ai_import_result"
        private const val RUNNING_NOTIFICATION_ID = 20260830
        private const val RESULT_NOTIFICATION_ID = 20260831
        private const val AI_STAGE_COUNT = 6
        private const val AI_IMPORT_WAKE_LOCK_TIMEOUT_MILLIS = 30L * 60L * 1_000L
        private const val TAG = "SleepDownAiImport"

        fun start(context: Context, taskId: String, status: String) {
            ContextCompat.startForegroundService(
                context,
                serviceIntent(context, ACTION_START, taskId).putExtra(EXTRA_STATUS, status)
            )
        }

        fun update(context: Context, taskId: String, status: String) {
            dispatchToRunningService(
                context,
                serviceIntent(context, ACTION_UPDATE, taskId).putExtra(EXTRA_STATUS, status)
            )
        }

        fun complete(context: Context, taskId: String, courseCount: Int) {
            dispatchToRunningService(
                context,
                serviceIntent(context, ACTION_COMPLETE, taskId)
                    .putExtra(EXTRA_COURSE_COUNT, courseCount)
            )
        }

        fun fail(context: Context, taskId: String, message: String) {
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

        private fun dispatchToRunningService(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (error: IllegalStateException) {
                // A vendor background-service gate must not turn a notification refresh into a
                // cancelled provider request. The active foreground service keeps the task alive;
                // the next meaningful state can still be observed when the UI returns.
                Log.w(TAG, "Unable to update AI import foreground notification", error)
            }
        }

        private fun serviceIntent(context: Context, action: String, taskId: String): Intent =
            Intent(context, AiImportForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
