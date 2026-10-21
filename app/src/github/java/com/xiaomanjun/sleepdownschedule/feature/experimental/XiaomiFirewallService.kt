package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.util.Log
import kotlin.system.exitProcess

/** Shizuku constructs this Binder in its authorized shell/root process, outside app hidden-API limits. */
class XiaomiFirewallService : IXiaomiFirewallService.Stub() {
    private val firewall by lazy { XiaomiFirewall() }

    override fun readState(uid: Int): IntArray = invokeFirewall { firewall.readState(uid) }
    override fun deny(uid: Int) = invokeFirewall { firewall.deny(uid) }
    override fun restore(uid: Int, rule: Int, chainEnabled: Boolean) =
        invokeFirewall { firewall.restore(uid, rule, chainEnabled) }
    override fun destroy() = exitProcess(0)

    private fun <T> invokeFirewall(action: () -> T): T = try {
        action()
    } catch (error: Exception) {
        Log.w("XiaomiFirewallService", "OEM firewall operation failed", error)
        throw IllegalStateException("OEM firewall operation failed", error)
    }
}
