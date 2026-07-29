package com.example.courseschedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DayAgentBackgroundRunState(
    val running: Boolean = false,
    val streamingText: String = "",
    val statuses: List<AgentRunStatus> = emptyList(),
    val error: String? = null
)

/**
 * Owns an agent turn independently from the conversation Dialog. The Dialog is only a
 * subscriber, so dismissing it, navigating away, or backgrounding the Activity no longer
 * cancels the HTTP stream. Completed assistant messages are still persisted by
 * [DayAgentRepository], which also makes a reopened conversation resume the same session.
 */
internal object DayAgentRunCoordinator {
    private data class Entry(
        val state: MutableStateFlow<DayAgentBackgroundRunState> =
            MutableStateFlow(DayAgentBackgroundRunState()),
        var job: Job? = null,
        var conversationVisible: Boolean = false,
        var generation: Long = 0L
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()

    private fun key(scheduleId: Int, date: LocalDate): String = "$scheduleId:$date"

    private fun entry(scheduleId: Int, date: LocalDate): Entry =
        entries.getOrPut(key(scheduleId, date)) { Entry() }

    fun observe(scheduleId: Int, date: LocalDate): StateFlow<DayAgentBackgroundRunState> =
        entry(scheduleId, date).state.asStateFlow()

    fun setConversationVisible(scheduleId: Int, date: LocalDate, visible: Boolean) {
        entry(scheduleId, date).conversationVisible = visible
    }

    fun start(
        context: Context,
        scheduleId: Int,
        facts: DayAgentFacts,
        question: String,
        imageAttachment: AgentImageAttachment? = null
    ): Boolean {
        val appContext = context.applicationContext
        val entry = entry(scheduleId, facts.date)
        synchronized(entry) {
            if (entry.job?.isActive == true || entry.state.value.running) return false
            val generation = ++entry.generation
            entry.state.value = DayAgentBackgroundRunState(
                running = true,
                statuses = listOf(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在准备"))
            )
            DayAgentForegroundService.startThinking(appContext)
            entry.job = scope.launch {
                val buffer = StringBuilder()
                var lastStreamPublishUptime = 0L
                val result = DayAgentRepository(appContext).sendMessage(
                    scheduleId = scheduleId,
                    facts = facts,
                    question = question,
                    imageAttachment = imageAttachment,
                    onStatus = statusCallback@{ status ->
                        if (entry.generation != generation) return@statusCallback
                        entry.state.update { current ->
                            val statuses = if (
                                status.icon == AgentRunStatusIcon.THINKING &&
                                current.statuses.lastOrNull()?.icon == AgentRunStatusIcon.THINKING
                            ) {
                                current.statuses.dropLast(1) + status
                            } else {
                                current.statuses + status
                            }
                            current.copy(statuses = statuses)
                        }
                    },
                    onDelta = deltaCallback@{ delta ->
                        if (entry.generation != generation) return@deltaCallback
                        /*
                         * Coalesce high-frequency deltas. Every publish hands the full
                         * accumulated text back to the conversation UI, which re-runs the
                         * reasoning split and markdown parse on the whole string, so raw
                         * per-token publishing degrades quadratically on long answers.
                         * Trailing text withheld here is never lost: completion clears the
                         * stream and the persisted message carries the full content.
                         */
                        val snapshot = synchronized(buffer) {
                            buffer.append(delta)
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastStreamPublishUptime < 48L) return@deltaCallback
                            lastStreamPublishUptime = now
                            buffer.toString()
                        }
                        entry.state.update { it.copy(streamingText = snapshot) }
                    }
                )
                if (entry.generation != generation) return@launch
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    entry.state.update {
                        it.copy(running = false, streamingText = "", error = null)
                    }
                    DayAgentForegroundService.finishThinking(
                        appContext,
                        alertUser = !entry.conversationVisible
                    )
                } else {
                    entry.state.update {
                        it.copy(
                            running = false,
                            streamingText = "",
                            error = failure.message ?: "模型回复失败"
                        )
                    }
                    DayAgentForegroundService.failThinking(
                        appContext,
                        failure.message ?: "模型回复失败",
                        alertUser = !entry.conversationVisible
                    )
                }
                synchronized(entry) {
                    if (entry.generation == generation) entry.job = null
                }
            }
        }
        return true
    }

    fun cancel(context: Context, scheduleId: Int, date: LocalDate) {
        val entry = entry(scheduleId, date)
        synchronized(entry) {
            entry.generation++
            entry.job?.cancel()
            entry.job = null
            entry.state.value = DayAgentBackgroundRunState()
        }
        DayAgentForegroundService.cancelThinking(context.applicationContext)
    }
}

class DayAgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COMPLETE -> {
                val alert = intent.getBooleanExtra(EXTRA_ALERT, true)
                stopRunningNotification()
                if (alert) {
                    notificationManager.notify(RESULT_NOTIFICATION_ID, completedNotification())
                }
                stopSelf()
            }
            ACTION_FAILED -> {
                val alert = intent.getBooleanExtra(EXTRA_ALERT, true)
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                stopRunningNotification()
                if (alert) {
                    notificationManager.notify(
                        RESULT_NOTIFICATION_ID,
                        failedNotification(message)
                    )
                }
                stopSelf()
            }
            ACTION_CANCEL -> {
                stopRunningNotification()
                stopSelf()
            }
            else -> startForeground(RUNNING_NOTIFICATION_ID, runningNotification())
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun openAppPendingIntent(requestCode: Int): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun runningNotification(): Notification =
        Notification.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("今日助手")
            .setContentText("模型思考中")
            .setContentIntent(openAppPendingIntent(7301))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(0xFF0A84FF.toInt())
            .setProgress(0, 0, true)
            .requestPromotedOngoing("思考中")
            .build()

    private fun completedNotification(): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("今日助手已回复")
            .setContentText("点击返回应用继续对话")
            .setContentIntent(openAppPendingIntent(7302))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setColor(0xFF0A84FF.toInt())
            .build()

    private fun failedNotification(message: String): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_thinking)
            .setContentTitle("今日助手回复失败")
            .setContentText(message.ifBlank { "点击返回应用重试" })
            .setContentIntent(openAppPendingIntent(7303))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

    private fun stopRunningNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(RUNNING_NOTIFICATION_ID)
    }

    private fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    RUNNING_CHANNEL_ID,
                    "今日助手运行状态",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "模型回复生成期间显示实时状态"
                    setShowBadge(false)
                },
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    "今日助手回复",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "模型完成回复后提醒返回应用"
                    setShowBadge(false)
                }
            )
        )
    }

    private fun Notification.Builder.requestPromotedOngoing(
        shortText: String
    ): Notification.Builder = apply {
        runCatching {
            javaClass.getMethod(
                "setRequestPromotedOngoing",
                java.lang.Boolean.TYPE
            ).invoke(this, true)
            extras.putBoolean("android.requestPromotedOngoing", true)
            javaClass.getMethod(
                "setShortCriticalText",
                String::class.java
            ).invoke(this, shortText)
        }
    }

    companion object {
        private const val ACTION_START =
            "com.example.courseschedule.action.DAY_AGENT_START"
        private const val ACTION_COMPLETE =
            "com.example.courseschedule.action.DAY_AGENT_COMPLETE"
        private const val ACTION_FAILED =
            "com.example.courseschedule.action.DAY_AGENT_FAILED"
        private const val ACTION_CANCEL =
            "com.example.courseschedule.action.DAY_AGENT_CANCEL"
        private const val EXTRA_ALERT = "alert_user"
        private const val EXTRA_MESSAGE = "message"
        private const val RUNNING_CHANNEL_ID = "day_agent_running"
        private const val RESULT_CHANNEL_ID = "day_agent_result"
        private const val RUNNING_NOTIFICATION_ID = 20260731
        private const val RESULT_NOTIFICATION_ID = 20260732

        fun startThinking(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DayAgentForegroundService::class.java)
                    .setAction(ACTION_START)
            )
        }

        fun finishThinking(context: Context, alertUser: Boolean) {
            context.startService(
                Intent(context, DayAgentForegroundService::class.java)
                    .setAction(ACTION_COMPLETE)
                    .putExtra(EXTRA_ALERT, alertUser)
            )
        }

        fun failThinking(context: Context, message: String, alertUser: Boolean) {
            context.startService(
                Intent(context, DayAgentForegroundService::class.java)
                    .setAction(ACTION_FAILED)
                    .putExtra(EXTRA_MESSAGE, message)
                    .putExtra(EXTRA_ALERT, alertUser)
            )
        }

        fun cancelThinking(context: Context) {
            context.startService(
                Intent(context, DayAgentForegroundService::class.java)
                    .setAction(ACTION_CANCEL)
            )
        }
    }
}
