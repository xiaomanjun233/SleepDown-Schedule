package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xiaomanjun.sleepdownschedule.MainActivity

/** User-started, bounded OAuth callback work; never restarted and never used for idle keepalive. */
class ChatGptLoginService : Service() {
    private var generation = -1L
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            ChatGptAuthManager.cancelLogin()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        generation = intent?.getLongExtra(EXTRA_GENERATION, -1L) ?: -1L
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "ChatGPT 登录", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 0, Intent(this, ChatGptLoginService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("正在连接 ChatGPT")
            .setContentText("请在浏览器中完成授权，完成后返回 SleepDown。")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "取消登录", cancel).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        else startForeground(NOTIFICATION, notification)
        ChatGptAuthManager.onLoginServiceReady(generation)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        ChatGptAuthManager.onLoginServiceStopped(generation)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ChatGptAuthManager.onLoginServiceStopped(generation)
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_GENERATION = "chatgpt_login_generation"
        private const val ACTION_CANCEL = "com.xiaomanjun.sleepdownschedule.CHATGPT_LOGIN_CANCEL"
        private const val CHANNEL = "chatgpt_login"
        private const val NOTIFICATION = 20261010
    }
}
