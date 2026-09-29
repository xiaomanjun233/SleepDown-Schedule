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
    private const val PendingMode = "pending_mode"
    private const val SystemFirewallMode = "system_firewall"
    private const val PreviousRule = "previous_rule"
    private const val PreviousChainEnabled = "previous_chain_enabled"
    private const val RecoveryRequest = 4263
    private const val FirewallCommandClass =
        "com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootFirewallCommand"
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
        val restored = if (prefs.getString(PendingMode, null) == SystemFirewallMode) {
            restoreFirewall(
                context, uid,
                FirewallState(
                    prefs.getBoolean(PreviousChainEnabled, false),
                    prefs.getInt(PreviousRule, -1)
                )
            )
        } else {
            // Recover a pending rule created by the earlier iptables implementation.
            removeRule(uid)
        }
        if (restored) {
            prefs.edit().remove(PendingUid).remove(PendingMode)
                .remove(PreviousRule).remove(PreviousChainEnabled).commit()
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
                Log.w(Tag, "temporary bypass skipped: previous rule could not be restored")
                post()
                return
            }
            val uid = runCatching { context.packageManager.getPackageUid("com.xiaomi.xmsf", 0) }.getOrNull()
            if (uid == null || uid < 0) {
                Log.w(Tag, "temporary bypass skipped: Xiaomi service unavailable")
                post()
                return
            }
            val state = firewallState(context, uid)
            if (state == null) {
                Log.w(Tag, "temporary bypass skipped: OEM firewall state unavailable")
                post()
                return
            }
            if (state.chainEnabled && state.rule == 2) {
                post()
                return
            }
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            if (!prefs.edit().putInt(PendingUid, uid)
                    .putString(PendingMode, SystemFirewallMode)
                    .putInt(PreviousRule, state.rule)
                    .putBoolean(PreviousChainEnabled, state.chainEnabled).commit() ||
                !scheduleRecovery(context)) {
                Log.w(Tag, "temporary bypass skipped: recovery could not be scheduled")
                prefs.edit().remove(PendingUid).remove(PendingMode)
                    .remove(PreviousRule).remove(PreviousChainEnabled).commit()
                post()
                return
            }
            if (!firewallCommand(context, "deny", uid).succeeded()) {
                Log.w(Tag, "temporary bypass skipped: OEM firewall deny failed")
                restoreIfInterrupted(context)
                post()
                return
            }
            Log.i(Tag, "OEM_DENY bypass active for focus notification")
            try {
                post()
                Thread.sleep(100)
            } finally {
                if (!restoreIfInterrupted(context)) Log.e(Tag, "temporary bypass restore failed")
            }
        }
    }

    private data class FirewallState(val chainEnabled: Boolean, val rule: Int)

    private fun firewallState(context: Context, uid: Int): FirewallState? {
        val result = firewallCommand(context, "state", uid)
        if (result?.exitCode != 0) return null
        val match = Regex("(?:^|\\n)STATE ([01]) ([012])(?:$|\\n)").find(result.output) ?: return null
        return FirewallState(match.groupValues[1] == "1", match.groupValues[2].toInt())
    }

    private fun restoreFirewall(context: Context, uid: Int, state: FirewallState): Boolean =
        state.rule in 0..2 && firewallCommand(
            context, "restore", uid, if (state.chainEnabled) "1" else "0", state.rule.toString()
        ).succeeded()

    private fun firewallCommand(context: Context, action: String, uid: Int, vararg args: String): CommandResult? {
        val apk = context.applicationInfo.sourceDir ?: return null
        val shell = "CLASSPATH=${shellQuote(apk)} app_process /system/bin $FirewallCommandClass " +
            (listOf(action, uid.toString()) + args).joinToString(" ")
        return command(shell, 8_000L)
    }

    private fun CommandResult?.succeeded(): Boolean =
        this?.exitCode == 0 && output.lineSequence().any { it == "OK" }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

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
