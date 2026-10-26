package com.xiaomanjun.sleepdownschedule.core.ui.interaction

/** On interruption, replay the authored route from the captured visual as its new endpoint.
 * No momentum, braking, substitute trajectory or phase is inserted before that route. */
internal class MorphCurveContinuity {
    private var sampler: ((Float, Boolean) -> FloatArray)? = null
    private var rebase: ((FloatArray, Boolean) -> (Float) -> FloatArray)? = null
    private var closing = false
    private var flight: Flight? = null
    val isActive: Boolean get() = flight != null
    val durationMillis: Int? get() = flight?.durationMillis

    fun bind(sampler: (Float, Boolean) -> FloatArray,
        rebase: (FloatArray, Boolean) -> (Float) -> FloatArray) {
        this.sampler = sampler
        this.rebase = rebase
    }
    fun unbind(sampler: (Float, Boolean) -> FloatArray) {
        if (this.sampler === sampler) { this.sampler = null; rebase = null; flight = null }
    }
    fun reset() { flight = null; closing = false }

    fun redirect(progress: Float, closing: Boolean, fullDurationMillis: Int) {
        if (this.closing == closing) return
        val sample = sampler
        val rebuild = rebase
        if (sample == null || rebuild == null || progress <= 0.00001f || progress >= 0.99999f) {
            flight = null
        } else {
            val current = flight?.values(progress) ?: sample(progress, this.closing)
            flight = Flight(progress, closing, current, rebuild(current, closing), fullDurationMillis)
        }
        this.closing = closing
    }

    fun value(channel: Int, progress: Float, original: Float): Float =
        flight?.values(progress)?.get(channel) ?: original

    private class Flight(
        private val startProgress: Float, private val closing: Boolean,
        private val current: FloatArray, private val route: (Float) -> FloatArray,
        val durationMillis: Int
    ) {
        private val referenceStart = route(if (closing) 1f else 0f)
        private val endpoint = route(if (closing) 0f else 1f)
        private var cachedProgress = Float.NaN
        private var cachedValues = current

        fun values(p: Float): FloatArray {
            if (p == cachedProgress) return cachedValues
            val local = if (closing) (p / startProgress).coerceIn(0f, 1f)
                else ((p - startProgress) / (1f - startProgress)).coerceIn(0f, 1f)
            val elapsed = if (closing) 1f - local else local
            cachedProgress = p
            cachedValues = when {
                elapsed <= 0f -> current
                elapsed >= 1f -> endpoint
                else -> route(local).also { frame ->
                    // The route's rect is already rebased. Carry current ink/material opacity
                    // into its authored handoff so only the endpoint changes at a reversal.
                    for (i in frame.indices) {
                        if (current[i] == referenceStart[i]) continue
                        val range = endpoint[i] - referenceStart[i]
                        frame[i] = if (kotlin.math.abs(range) > 0.00001f)
                            current[i] + (endpoint[i] - current[i]) * (frame[i] - referenceStart[i]) / range
                        else frame[i] + (current[i] - referenceStart[i]) * (1f - elapsed)
                    }
                }
            }
            return cachedValues
        }
    }
}
