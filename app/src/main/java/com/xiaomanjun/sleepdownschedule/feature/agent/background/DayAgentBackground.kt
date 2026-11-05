package com.xiaomanjun.sleepdownschedule.feature.agent.background

import com.xiaomanjun.sleepdownschedule.core.identity.applyAppNotificationIcon
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import com.xiaomanjun.sleepdownschedule.feature.agent.*

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val mailbox: com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox<AgentTaskInput> = com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox(),
        var interaction: com.xiaomanjun.sleepdownschedule.feature.importing.AiImportInteraction? = null,
        @Volatile
        var conversationVisible: Boolean = false,
        @Volatile
        var generation: Long = 0L
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun key(scheduleId: Int, date: LocalDate): String = "$scheduleId:$date"

    private fun entry(scheduleId: Int, date: LocalDate): Entry {
        val entryKey = key(scheduleId, date)
        entries[entryKey]?.let { return it }
        val candidate = Entry()
        return entries.putIfAbsent(entryKey, candidate) ?: candidate
    }

    fun observe(scheduleId: Int, date: LocalDate): StateFlow<DayAgentBackgroundRunState> =
        entry(scheduleId, date).state.asStateFlow()

    fun observeInputs(scheduleId: Int, date: LocalDate) = entry(scheduleId, date).mailbox.inputs
    fun submitInput(scheduleId: Int, date: LocalDate, input: AgentTaskInput, mode: com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode): Boolean {
        val entry = entry(scheduleId, date)
        return synchronized(entry) {
            if (!entry.state.value.running) return@synchronized false
            if (input.text.isBlank() || input.text.length > 6_000) return@synchronized false
            entry.mailbox.trySubmit(input, mode) != null
        }
    }
    fun replaceInput(scheduleId: Int, date: LocalDate, id: String, input: AgentTaskInput) = entry(scheduleId, date).mailbox.replace(id, input)
    fun removeInput(scheduleId: Int, date: LocalDate, id: String) { entry(scheduleId, date).mailbox.remove(id) }
    fun guideInput(context: Context, scheduleId: Int, facts: DayAgentFacts, id: String): Boolean {
        val entry = entry(scheduleId, facts.date)
        return synchronized(entry) {
            if (entry.state.value.running) return@synchronized entry.mailbox.steer(id)
            val input = entry.mailbox.inputs.value.firstOrNull { it.id == id } ?: return@synchronized false
            start(context, scheduleId, facts, input.value.text, imageAttachments = input.value.allImages).also { accepted ->
                if (accepted) entry.mailbox.remove(id)
            }
        }
    }

    fun setConversationVisible(scheduleId: Int, date: LocalDate, visible: Boolean) {
        val entryKey = key(scheduleId, date)
        val entry = entry(scheduleId, date)
        entry.conversationVisible = visible
        if (!visible) evictIfIdle(entryKey, entry)
    }

    fun start(
        context: Context,
        scheduleId: Int,
        facts: DayAgentFacts,
        question: String,
        imageAttachment: AgentImageAttachment? = null,
        imageAttachments: List<AgentImageAttachment> = emptyList()
    ): Boolean {
        val appContext = context.applicationContext
        val app = appContext as CourseScheduleApp
        val entry = entry(scheduleId, facts.date)
        synchronized(entry) {
            if (entry.job?.isActive == true || entry.state.value.running) return false
            val generation = ++entry.generation
            entry.mailbox.resume()
            entry.state.value = DayAgentBackgroundRunState(
                running = true,
                statuses = listOf(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在准备"))
            )
            val foregroundError = runCatching {
                DayAgentForegroundServiceHost.startThinking(appContext)
            }.exceptionOrNull()
            if (foregroundError != null) {
                entry.state.value = DayAgentBackgroundRunState(
                    error = foregroundError.message ?: "无法启动后台思考服务"
                )
                evictIfIdle(key(scheduleId, facts.date), entry)
                return false
            }
            entry.job = app.applicationScope.launch(Dispatchers.IO) {
                var currentInput = AgentTaskInput(question, imageAttachment, imageAttachments)
                while (true) {
                val interaction = com.xiaomanjun.sleepdownschedule.feature.importing.AiImportInteraction(currentInput.text)
                interaction.mailbox = entry.mailbox
                entry.interaction = interaction
                entry.state.value = DayAgentBackgroundRunState(running = true,
                    statuses = listOf(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在准备")))
                val buffer = StringBuilder()
                var lastStreamPublishUptime = 0L
                val result = runCatching {
                    DayAgentRepository(appContext).sendMessage(
                        scheduleId = scheduleId,
                        facts = facts,
                        question = currentInput.text,
                        imageAttachments = currentInput.allImages,
                        interaction = interaction,
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
                        },
                        onStreamReset = resetCallback@{
                            if (entry.generation != generation) return@resetCallback
                            synchronized(buffer) {
                                buffer.setLength(0)
                                lastStreamPublishUptime = 0L
                            }
                            entry.state.update { it.copy(streamingText = "") }
                        }
                    )
                }.getOrElse { Result.failure(it) }
                if (entry.generation != generation) return@launch
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    val queued = synchronized(entry) {
                        entry.mailbox.nextQueued().also { next ->
                            if (next == null) entry.state.update { it.copy(running = false, streamingText = "", error = null) }
                        }
                    }
                    if (queued != null) { currentInput = queued; continue }
                    entry.state.update {
                        it.copy(running = false, streamingText = "", error = null)
                    }
                    runCatching {
                        DayAgentForegroundServiceHost.finishThinking(
                            appContext,
                            alertUser = !entry.conversationVisible
                        )
                    }
                } else {
                    entry.mailbox.pause()
                    entry.state.update {
                        it.copy(
                            running = false,
                            streamingText = "",
                            error = failure.message ?: "模型回复失败"
                        )
                    }
                    runCatching {
                        DayAgentForegroundServiceHost.failThinking(
                            appContext,
                            failure.message ?: "模型回复失败",
                            alertUser = !entry.conversationVisible
                        )
                    }
                }
                break
                }
                synchronized(entry) {
                    if (entry.generation == generation) entry.job = null
                }
                evictIfIdle(key(scheduleId, facts.date), entry)
            }
        }
        return true
    }

    fun cancel(context: Context, scheduleId: Int, date: LocalDate) {
        val entry = entry(scheduleId, date)
        synchronized(entry) {
            entry.generation++
            entry.mailbox.pause()
            entry.interaction?.markCancelled()
            val connection = entry.interaction
            (context.applicationContext as CourseScheduleApp).applicationScope.launch(Dispatchers.IO) { connection?.disconnect() }
            entry.job?.cancel()
            entry.job = null
            entry.state.value = DayAgentBackgroundRunState()
        }
        DayAgentForegroundServiceHost.cancelThinking(context.applicationContext)
        evictIfIdle(key(scheduleId, date), entry)
    }

    private fun evictIfIdle(entryKey: String, entry: Entry) {
        synchronized(entry) {
            if (entry.job?.isActive != true && !entry.state.value.running && !entry.conversationVisible && entry.mailbox.inputs.value.isEmpty()) {
                entries.remove(entryKey, entry)
            }
        }
    }
}

open class DayAgentForegroundServiceHost : Service() {
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

    private fun runningNotification(): Notification {
        return Notification.Builder(this, RUNNING_CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle("AI 助理")
            .setContentText("模型思考中")
            .setContentIntent(openAppPendingIntent(7301))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .requestPromotedOngoing("思考中")
            .build()
    }

    private fun completedNotification(): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle("AI 助理已回复")
            .setContentText("点击返回应用继续对话")
            .setContentIntent(openAppPendingIntent(7302))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()

    private fun failedNotification(message: String): Notification =
        Notification.Builder(this, RESULT_CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle("AI 助理回复失败")
            .setContentText(message.ifBlank { "点击返回应用重试" })
            .setContentIntent(openAppPendingIntent(7303))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

    private fun stopRunningNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(RUNNING_NOTIFICATION_ID)
    }

    private fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    RUNNING_CHANNEL_ID,
                    "AI 助理运行状态",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "模型回复生成期间显示实时状态"
                    setShowBadge(false)
                },
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    "AI 助理回复",
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
        private val ACTION_START = "${BuildConfig.APPLICATION_ID}.action.DAY_AGENT_START"
        private val ACTION_COMPLETE = "${BuildConfig.APPLICATION_ID}.action.DAY_AGENT_COMPLETE"
        private val ACTION_FAILED = "${BuildConfig.APPLICATION_ID}.action.DAY_AGENT_FAILED"
        private val ACTION_CANCEL = "${BuildConfig.APPLICATION_ID}.action.DAY_AGENT_CANCEL"
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
