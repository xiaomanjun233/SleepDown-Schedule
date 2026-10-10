package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/** A cancel arriving during durable save restores the previous active session before publication. */
internal class ChatGptLoginCommit {
    private val phase = AtomicInteger(0) // pending, cancelled, accepted
    fun cancel() { phase.compareAndSet(0, 1) }
    fun install(write: () -> Unit, restore: () -> Unit, publish: () -> Unit) {
        if (phase.get() != 0) throw CancellationException()
        write()
        if (!phase.compareAndSet(0, 2)) {
            restore()
            throw CancellationException()
        }
        publish()
    }
}
