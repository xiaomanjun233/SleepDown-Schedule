package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** GitHub-only implementation of the optional Xiaomi notification workaround. */
internal object XiaomiShizukuBridge {
    private const val Tag = "XiaomiShizukuBridge"
    private const val Prefs = "xiaomi_island_network_restore"
    private const val Pending = "pending"
    private const val PreviousUid = "uid"
    private const val PreviousRule = "rule"
    private const val PreviousChainEnabled = "chain_enabled"
    private const val XmsfPackage = "com.xiaomi.xmsf"
    private const val OemDenyChain = 9
    private const val DefaultRule = 0
    private const val DenyRule = 2
    private const val PermissionRequest = 4261
    private const val RecoveryRequest = 4262
    private val lock = Any()

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean = isRunning() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isRunning()) {
            onResult(false)
            return
        }
        if (isAuthorized()) {
            onResult(true)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PermissionRequest) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(PermissionRequest) }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(false)
        }
    }

    fun restoreIfInterrupted(context: Context): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Pending, false)) return@synchronized true
        if (!isAuthorized()) return@synchronized false
        val uid = prefs.getInt(PreviousUid, -1)
        if (uid < 0) return@synchronized false
        runCatching {
            val firewall = Firewall()
            firewall.setRule(uid, prefs.getInt(PreviousRule, DefaultRule))
            firewall.setChainEnabled(prefs.getBoolean(PreviousChainEnabled, true))
            prefs.edit().clear().commit().also { restored ->
                if (restored) cancelRecoveryAlarm(context)
            }
        }.onFailure { Log.w(Tag, "Xiaomi service network restore unavailable", it) }
            .getOrDefault(false)
    }

    /** The regular notification path is always called exactly once. */
    fun postWithTemporaryBypass(context: Context, post: () -> Unit) {
        if (!isAuthorized()) {
            post()
            return
        }
        synchronized(lock) {
            if (!restoreIfInterrupted(context)) {
                post()
                return
            }
            val firewall = runCatching { Firewall() }.getOrNull()
            val uid = runCatching { context.packageManager.getPackageUid(XmsfPackage, 0) }.getOrNull()
            val oldRule = if (firewall != null && uid != null) runCatching { firewall.rule(uid) }.getOrNull() else null
            val chainEnabled = if (firewall != null) runCatching { firewall.chainEnabled() }.getOrNull() else null
            if (firewall == null || uid == null || oldRule != DefaultRule || chainEnabled == null) {
                post()
                return
            }
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            if (!prefs.edit().putBoolean(Pending, true).putInt(PreviousUid, uid)
                    .putInt(PreviousRule, oldRule).putBoolean(PreviousChainEnabled, chainEnabled)
                    .commit()) {
                post()
                return
            }
            if (!scheduleRecoveryAlarm(context)) {
                prefs.edit().clear().commit()
                post()
                return
            }
            var posted = false
            try {
                if (!chainEnabled) firewall.setChainEnabled(true)
                firewall.setRule(uid, DenyRule)
                posted = true
                post()
                Thread.sleep(100)
            } catch (error: Exception) {
                if (posted) throw error
                Log.w(Tag, "Xiaomi notification bypass unavailable", error)
            } finally {
                restoreIfInterrupted(context)
            }
            if (!posted) post()
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

    private class Firewall {
        private val connection: Any = run {
            val binder = requireNotNull(SystemServiceHelper.getSystemService("connectivity"))
            requireNotNull(Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder)))
        }

        fun rule(uid: Int): Int = connection.javaClass
            .getMethod("getUidFirewallRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(connection, OemDenyChain, uid) as Int

        fun chainEnabled(): Boolean = connection.javaClass
            .getMethod("getFirewallChainEnabled", Int::class.javaPrimitiveType)
            .invoke(connection, OemDenyChain) as Boolean

        fun setRule(uid: Int, rule: Int) {
            connection.javaClass
                .getMethod("setUidFirewallRule", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(connection, OemDenyChain, uid, rule)
        }

        fun setChainEnabled(enabled: Boolean) {
            connection.javaClass
                .getMethod("setFirewallChainEnabled", Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType)
                .invoke(connection, OemDenyChain, enabled)
        }
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
