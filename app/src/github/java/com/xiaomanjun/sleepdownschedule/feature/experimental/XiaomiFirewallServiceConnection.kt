package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

internal object XiaomiFirewallServiceConnection {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var service: IXiaomiFirewallService? = null

    fun get(context: Context): IXiaomiFirewallService {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        check(Looper.myLooper() != Looper.getMainLooper()) { "Firewall binding must run off the main thread" }
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = binder?.takeIf { it.pingBinder() }?.let(IXiaomiFirewallService.Stub::asInterface)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                connected.countDown()
            }
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, XiaomiFirewallService::class.java.name))
            .daemon(false).processNameSuffix("island_firewall").debuggable(false).version(1)
        mainHandler.post {
            try {
                Shizuku.bindUserService(args, connection)
            } catch (error: Exception) {
                Log.w("XiaomiShizukuBridge", "Cannot bind OEM firewall service", error)
                connected.countDown()
            }
        }
        try {
            check(connected.await(3, TimeUnit.SECONDS)) { "Firewall service connection timed out" }
            return checkNotNull(service?.takeIf { it.asBinder().pingBinder() }) { "Firewall service unavailable" }
        } finally {
            // Remove this request's callback; daemon(false) ends the service with its client process.
            mainHandler.post {
                runCatching { Shizuku.unbindUserService(args, connection, false) }
            }
        }
    }
}
