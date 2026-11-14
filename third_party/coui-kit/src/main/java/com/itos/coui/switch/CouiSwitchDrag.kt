package com.itos.coui.switch

import com.itos.coui.motion.CouiSpring
import kotlin.math.abs

/** COUISpringDragHelper 的开关双锚点分支；不持有 checked 或提交业务值。 */
internal class CouiSwitchDrag(
    private val travel: Float,
    private val onPositionChanged: (Float) -> Unit,
    private val onAttached: () -> Unit,
) {
    private val horizontal = CouiSpring(0f)
    private val vertical = CouiSpring(0f)
    private var phase = Phase.Idle
    private var start = 0f
    private var downX = 0f
    private var downY = 0f
    private var deltaSatisfied = false

    val isActive: Boolean get() = phase != Phase.Idle

    fun begin(position: Float, x: Float, y: Float) {
        horizontal.snapTo(position)
        vertical.snapTo(0f)
        start = position
        downX = x
        downY = y
        deltaSatisfied = false
        phase = Phase.Drag
    }

    fun dragTo(x: Float, y: Float) {
        if (phase != Phase.Drag) return
        deltaSatisfied = deltaSatisfied || abs(x - downX) >= CouiSwitchDefaults.transformDistancePx ||
            abs(y - downY) >= CouiSwitchDefaults.transformDistancePx
        // 原厂 overDistance 为零，只限制目标，不截断弹簧当前位置。
        val target = (start + x - downX).coerceIn(0f, travel)
        val distance = abs(target - horizontal.value)
        // 保留 dragTo 的两级启动阈值；停稳后重新启动不继承旧速度。
        if (distance >= .01f && (horizontal.isRunning || distance >= 1f)) {
            horizontal.animateTo(target, CouiSwitchDefaults.dragResponse, CouiSwitchDefaults.dragBounce,
                velocity = if (horizontal.isRunning) null else 0f, onFrame = onPositionChanged)
        }
    }

    fun attach(inputVelocity: Float, inputVelocityY: Float) {
        if (phase != Phase.Drag) return
        val position = horizontal.value
        val v = inputVelocity.toInt().toFloat()
        val vy = inputVelocityY.toInt().toFloat()
        // 等距时保留第一个锚点；选锚点量化速度，吸附初速度保留原始浮点值。
        var target = if (abs(position) <= abs(travel - position)) 0f else travel
        if (abs(v) >= CouiSwitchDefaults.transformVelocityPx && deltaSatisfied) target = if (v > 0f) travel else 0f
        else if (abs(vy) >= CouiSwitchDefaults.transformVelocityPx && deltaSatisfied) target = if (vy > 0f) travel else 0f
        cancel()
        phase = Phase.Attach
        if (abs(target - position) < 1f) {
            finishAttachment()
            return
        }
        horizontal.animateTo(target, CouiSwitchDefaults.attachResponse, CouiSwitchDefaults.attachBounce,
            velocity = inputVelocity.coerceIn(-CouiSwitchDefaults.transformVelocityPx, CouiSwitchDefaults.transformVelocityPx),
            onFrame = onPositionChanged, onEnd = ::finishAttachment)
        // Y 的目标虽为零，初速度仍可让它继续运行；两路结束后才通知吸附完成。
        vertical.snapTo(0f)
        vertical.animateTo(0f, CouiSwitchDefaults.attachResponse, CouiSwitchDefaults.attachBounce,
            velocity = inputVelocityY.coerceIn(-CouiSwitchDefaults.transformVelocityPx, CouiSwitchDefaults.transformVelocityPx),
            onEnd = ::finishAttachment)
    }

    fun cancel() {
        phase = Phase.Idle
        horizontal.cancelComplete()
        vertical.cancelComplete()
    }

    private fun finishAttachment() {
        if (phase != Phase.Attach || horizontal.isRunning || vertical.isRunning) return
        phase = Phase.Idle
        onAttached()
    }

    private enum class Phase { Idle, Drag, Attach }
}
