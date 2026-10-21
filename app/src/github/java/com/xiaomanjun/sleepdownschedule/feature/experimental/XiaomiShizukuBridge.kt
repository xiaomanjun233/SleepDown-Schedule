package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku

/** GitHub-only implementation of the optional Xiaomi notification workaround. */
internal object XiaomiShizukuBridge {
    private const val Tag = "XiaomiShizukuBridge"
    private const val Prefs = "xiaomi_island_network_restore"
    private const val Pending = "pending"
    private const val PreviousUid = "uid"
    private const val PreviousRule = "rule"
    private const val PreviousChainEnabled = "chain_enabled"
    private const val XmsfPackage = "com.xiaomi.xmsf"
    private const val DefaultRule = 0
    private const val DenyRule = 2
    private const val PermissionRequest = 4261
    private const val RecoveryRequest = 4262
    private val lock = Any()

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean = isRunning() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun observeState(onChanged: () -> Unit): () -> Unit {
        val received = Shizuku.OnBinderReceivedListener { onChanged() }
        val dead = Shizuku.OnBinderDeadListener { onChanged() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        return {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit): () -> Unit {
        if (!isRunning()) {
            onResult(false)
            return {}
        }
        var finished = false
        lateinit var listener: Shizuku.OnRequestPermissionResultListener
        lateinit var dead: Shizuku.OnBinderDeadListener
        fun finish(granted: Boolean?, notify: Boolean = true) {
            if (finished) return
            finished = true
            Shizuku.removeRequestPermissionResultListener(listener)
            Shizuku.removeBinderDeadListener(dead)
            if (notify) onResult(granted == true)
        }
        listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PermissionRequest) return
                Log.i(Tag, "Shizuku permission result: granted=${grantResult == PackageManager.PERMISSION_GRANTED}")
                finish(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        dead = Shizuku.OnBinderDeadListener { finish(false) }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.addBinderDeadListener(dead)
        runCatching {
            Shizuku.requestPermission(PermissionRequest)
            Log.i(Tag, "Manual permission request dispatched to Shizuku")
        }.onFailure {
            Log.w(Tag, "Cannot request Shizuku permission", it)
            finish(false)
        }
        return { finish(null, notify = false) }
    }

    fun restoreIfInterrupted(context: Context): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Pending, false)) return@synchronized true
        if (!isAuthorized()) return@synchronized false
        val uid = prefs.getInt(PreviousUid, -1)
        if (uid < 0) return@synchronized false
        runCatching {
            val firewall = XiaomiFirewallServiceConnection.get(context)
            firewall.restore(uid, prefs.getInt(PreviousRule, DefaultRule), prefs.getBoolean(PreviousChainEnabled, true))
            prefs.edit().clear().commit().also { restored ->
                if (restored) cancelRecoveryAlarm(context)
            }
        }.onFailure { Log.w(Tag, "Xiaomi service network restore unavailable", it) }
            .getOrDefault(false)
    }

    /** The regular notification path is always called exactly once. */
    fun postWithTemporaryBypass(context: Context, post: () -> Unit): Boolean {
        if (!isAuthorized()) {
            post()
            return false
        }
        synchronized(lock) {
            if (!restoreIfInterrupted(context)) {
                post()
                return false
            }
            val firewall = runCatching { XiaomiFirewallServiceConnection.get(context) }
                .onFailure { Log.w(Tag, "Cannot connect OEM firewall service", it) }.getOrNull()
            val uid = runCatching { context.packageManager.getPackageUid(XmsfPackage, 0) }.getOrNull()
            val state = if (firewall != null && uid != null) runCatching { firewall.readState(uid) }
                .onFailure { Log.w(Tag, "Cannot read OEM firewall state", it) }.getOrNull() else null
            val oldRule = state?.takeIf { it.size == 2 }?.get(1)
            val chainEnabled = state?.takeIf { it.size == 2 && it[0] in 0..1 }?.let { it[0] == 1 }
            // Android reads effective rules as ALLOW (1) / DENY (2), not DEFAULT (0).
            // Accept both representations and restore exactly what was read.
            if (firewall == null || uid == null || !isKnownIslandFirewallRule(oldRule) || chainEnabled == null) {
                Log.w(Tag, "Cannot read Xiaomi service firewall state")
                post()
                return false
            }
            if (chainEnabled && oldRule == DenyRule) {
                post()
                return true
            }
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            if (!prefs.edit().putBoolean(Pending, true).putInt(PreviousUid, uid)
                    .putInt(PreviousRule, requireNotNull(oldRule)).putBoolean(PreviousChainEnabled, chainEnabled)
                    .commit()) {
                post()
                return false
            }
            if (!scheduleRecoveryAlarm(context)) {
                prefs.edit().clear().commit()
                post()
                return false
            }
            var posted = false
            try {
                firewall.deny(uid)
                posted = true
                post()
                Log.i(Tag, "OEM firewall bypass posted through authorized service")
                Thread.sleep(100)
            } catch (error: Exception) {
                if (posted) throw error
                Log.w(Tag, "Xiaomi notification bypass unavailable", error)
            } finally {
                restoreIfInterrupted(context)
            }
            if (!posted) post()
            return posted
        }
    }

    private fun recoveryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, RecoveryRequest, Intent(context, XiaomiNetworkRestoreReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleRecoveryAlarm(context: Context): Boolean = runCatching {
        val alarm = requireNotNull(context.getSystemService(AlarmManager::class.java))
        val trigger = System.currentTimeMillis() + 5_000L
        val pending = recoveryIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }.onFailure { Log.w(Tag, "Xiaomi network recovery alarm unavailable", it) }.isSuccess

    private fun cancelRecoveryAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(recoveryIntent(context))
    }

}

internal class XiaomiNetworkRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            try {
                XiaomiSuperIsland.restoreInterruptedBypass(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
