package com.xiaomanjun.sleepdownschedule.core.ai

import java.util.concurrent.CancellationException

/**
 * Provider-independent turn/step/tool lifecycle. Inspired by Codex run_turn and DSH agent-loop;
 * implemented for Android without their shell, plugin host or model-specific transports.
 * See docs/ai/AGENT_HARNESS.md for pinned upstream sources and adaptation boundaries.
 */
internal class AgentHarness(
    private val maxSteps: Int,
    private val checkActive: () -> Unit = {},
    private val observe: (Event) -> Unit = {}
) {
    enum class Phase { STARTED, STEP_STARTED, STEP_FINISHED, TOOL_STARTED, TOOL_FINISHED,
        TOOL_REPLAYED, COMPLETED, CANCELLED, FAILED, LIMIT_REACHED }
    data class Event(val phase: Phase, val step: Int, val tool: String? = null)
    sealed interface Next<out T> {
        data class Complete<T>(val value: T) : Next<T>
        data object Continue : Next<Nothing>
        data object Converge : Next<Nothing>
    }
    private data class ToolReceipt(val fingerprint: String, val value: Any?)
    private val receipts = linkedMapOf<String, ToolReceipt>()
    private var stepIndex = -1
    private var used = false

    init { require(maxSteps in 1..64) }

    private fun emit(phase: Phase, tool: String? = null) = observe(Event(phase, stepIndex, tool))
    fun ensureActive() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("AI 任务已取消")
        checkActive()
    }

    /** No implicit network retry: the provider decides which failures are safe to retry. */
    fun <T> run(step: (Int) -> Next<T>, converge: () -> T): T {
        check(!used) { "A harness owns exactly one turn" }
        used = true
        emit(Phase.STARTED)
        try {
            for (index in 0 until maxSteps) {
                stepIndex = index
                ensureActive()
                emit(Phase.STEP_STARTED)
                val next = step(index)
                ensureActive()
                emit(Phase.STEP_FINISHED)
                when (next) {
                    is Next.Complete -> { emit(Phase.COMPLETED); return next.value }
                    Next.Continue -> Unit
                    Next.Converge -> { val value = converge(); ensureActive(); emit(Phase.COMPLETED); return value }
                }
            }
            emit(Phase.LIMIT_REACHED)
            val value = converge()
            ensureActive()
            emit(Phase.COMPLETED)
            return value
        } catch (cancelled: CancellationException) {
            emit(Phase.CANCELLED)
            throw cancelled
        } catch (error: Exception) {
            emit(Phase.FAILED)
            throw error
        }
    }

    /** Replay only the same call ID and payload; new IDs may intentionally repeat mutable reads. */
    @Suppress("UNCHECKED_CAST")
    fun <T> tool(id: String, name: String, fingerprint: String, conflict: () -> T, execute: () -> T): T {
        ensureActive()
        if (id.isNotBlank()) receipts[id]?.let { previous ->
            if (previous.fingerprint != fingerprint) return conflict()
            emit(Phase.TOOL_REPLAYED, name)
            return previous.value as T
        }
        check(receipts.size < 256) { "本轮工具调用过多，请缩小任务范围" }
        emit(Phase.TOOL_STARTED, name)
        val result = execute()
        // Settle a successful side effect before checking cancellation; never blindly replay it.
        if (id.isNotBlank()) receipts[id] = ToolReceipt(fingerprint, result)
        emit(Phase.TOOL_FINISHED, name)
        ensureActive()
        return result
    }
}
