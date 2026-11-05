package com.xiaomanjun.sleepdownschedule.core.ai

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One task owns its inbox. Steering joins a step; queued work starts a later user turn. */
internal class AgentMailbox<T> {
    enum class Mode { STEER, QUEUE }
    data class Input<T>(val id: String, val mode: Mode, val value: T)
    private val pending = MutableStateFlow<List<Input<T>>>(emptyList())
    val inputs = pending.asStateFlow()
    @Volatile var paused: Boolean = false
        private set

    @Synchronized fun submit(value: T, mode: Mode): String {
        return checkNotNull(trySubmit(value, mode)) { "最多保留 16 条待处理消息" }
    }
    @Synchronized fun trySubmit(value: T, mode: Mode): String? {
        if (pending.value.size >= 16) return null
        val id = UUID.randomUUID().toString()
        pending.value = pending.value + Input(id, mode, value)
        return id
    }
    @Synchronized fun replace(id: String, value: T): Boolean {
        if (pending.value.none { it.id == id }) return false
        pending.value = pending.value.map { if (it.id == id) it.copy(value = value) else it }
        return true
    }
    @Synchronized fun remove(id: String) { pending.value = pending.value.filterNot { it.id == id } }
    @Synchronized fun steer(id: String): Boolean {
        if (pending.value.none { it.id == id }) return false
        pending.value = pending.value.map { if (it.id == id) it.copy(mode = Mode.STEER) else it }
        return true
    }
    @Synchronized fun drainSteering(): List<T> {
        if (paused) return emptyList()
        val steering = pending.value.filter { it.mode == Mode.STEER }
        pending.value = pending.value.filterNot { it.mode == Mode.STEER }
        return steering.map { it.value }
    }
    @Synchronized fun nextQueued(): T? {
        if (paused) return null
        // A late steering message arriving at turn completion becomes the next turn, never lost.
        val input = pending.value.firstOrNull() ?: return null
        pending.value = pending.value.filterNot { it.id == input.id }
        return input.value
    }
    @Synchronized fun pause() { paused = true }
    @Synchronized fun resume() { paused = false }
}
