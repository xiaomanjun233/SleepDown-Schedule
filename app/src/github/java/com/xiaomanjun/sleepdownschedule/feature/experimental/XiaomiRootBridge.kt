package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit

/** Root alternative for the short Xiaomi system-service network bypass. */
internal object XiaomiRootBridge {
    private const val Tag = "XiaomiRootBridge"
    private const val Prefs = "xiaomi_island_root"
    private const val Authorized = "authorized"
    private const val PendingUid = "pending_uid"
    private const val RecoveryRequest = 4263
    private val lock = Any()

    fun isAuthorized(context: Context): Boolean =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(Authorized, false)

    /** Called from an IO dispatcher after the user chooses root in settings. */
    fun requestAuthorization(context: Context): Boolean {
        val granted = command("id -u", 30_000L)?.let { it.exitCode == 0 && it.output == "0" } == true
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putBoolean(Authorized, granted).apply()
        return granted
    }

    fun restoreIfInterrupted(context: Context): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val uid = prefs.getInt(PendingUid, -1)
        if (uid < 0) return@synchronized true
        val restored = removeRule(uid)
        if (restored) {
            prefs.edit().remove(PendingUid).commit()
            context.getSystemService(AlarmManager::class.java)?.cancel(recoveryIntent(context))
        }
        restored
    }

    fun postWithTemporaryBypass(context: Context, post: () -> Unit) {
        if (!isAuthorized(context)) {
            post()
            return
        }
        synchronized(lock) {
            if (!restoreIfInterrupted(context)) {
                post()
                return
            }
            val uid = runCatching { context.packageManager.getPackageUid("com.xiaomi.xmsf", 0) }.getOrNull()
            if (uid == null || uid < 0) {
                post()
                return
            }
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            if (!prefs.edit().putInt(PendingUid, uid).commit() || !scheduleRecovery(context)) {
                prefs.edit().remove(PendingUid).commit()
                post()
                return
            }
            val inserted = command("iptables -w 1 -I OUTPUT 1 ${ruleArgs(uid)}", 5_000L)?.exitCode == 0
            if (!inserted) {
                restoreIfInterrupted(context)
                post()
                return
            }
            try {
                post()
                Thread.sleep(100)
            } finally {
                restoreIfInterrupted(context)
            }
        }
    }

    private fun ruleArgs(uid: Int): String =
        "-m owner --uid-owner $uid -m comment --comment sleepdown_island_$uid -j DROP"

    private fun removeRule(uid: Int): Boolean {
        val rule = ruleArgs(uid)
        val exists = command("iptables -w 1 -C OUTPUT $rule", 5_000L) ?: return false
        if (exists.exitCode != 0) return true
        return command("iptables -w 1 -D OUTPUT $rule", 5_000L)?.exitCode == 0
    }

    private fun recoveryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, RecoveryRequest, Intent(context, XiaomiNetworkRestoreReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleRecovery(context: Context): Boolean = runCatching {
        val alarm = requireNotNull(context.getSystemService(AlarmManager::class.java))
        val trigger = System.currentTimeMillis() + 5_000L
        val pending = recoveryIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }.onFailure { Log.w(Tag, "Root recovery alarm unavailable", it) }.isSuccess

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun command(shell: String, timeoutMillis: Long): CommandResult? = runCatching {
        val process = ProcessBuilder("su", "-c", shell).redirectErrorStream(true).start()
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        CommandResult(process.exitValue(), process.inputStream.bufferedReader().use { it.readText().trim() })
    }.onFailure { Log.w(Tag, "Root command unavailable: ${it.javaClass.simpleName}") }.getOrNull()
}
