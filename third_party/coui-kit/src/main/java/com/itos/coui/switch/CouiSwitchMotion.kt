package com.itos.coui.switch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.itos.coui.motion.CouiSpring
import kotlin.math.abs

/** COUISwitch 的受控值同步与切换外观；拖动、吸附由 CouiSwitchDrag 持有。 */
internal class CouiSwitchMotion(initial: Boolean, val travel: Float, private val rtl: Boolean) {
    private var observed = initial
    private var completedAttachment: Boolean? = null
    private val toggle = CouiSpring(endpoint(initial), minimumChange = .1f)
    private val drag = CouiSwitchDrag(travel,
        onPositionChanged = { updatePosition(it, toggling = false) },
        onAttached = ::finishAttachment)
    var translation by mutableFloatStateOf(endpoint(initial).toInt().toFloat())
        private set
    var stretch by mutableFloatStateOf(1f)
        private set
    var fraction by mutableFloatStateOf(if (initial) 1f else 0f)
        private set
    var settled by mutableIntStateOf(0)
        private set
    var onCommit: (Boolean) -> Unit = {}

    private fun endpoint(checked: Boolean) = if (checked != rtl) travel else 0f
    private fun logical(value: Float) = if (rtl) 1f - value / travel else value / travel

    fun update(checked: Boolean, enabled: Boolean) {
        if (checked != observed) {
            observed = checked
            if (completedAttachment == checked) {
                // 原厂吸附提交 setChecked(target, false)，不再启动普通切换弹簧。
                completedAttachment = null
                snap(checked)
            } else animateToggle(checked)
        } else if (!enabled && drag.isActive) {
            // 禁用输入只终止手势；startLoading / setEnabled 不取消原厂正在执行的 Toggle。
            cancel()
        }
    }

    fun begin(x: Float, y: Float): Boolean {
        if (toggle.isRunning || drag.isActive) return false
        completedAttachment = null
        drag.begin(translation, x, y)
        stretch = 1f
        return true
    }

    fun drag(x: Float, y: Float) {
        drag.dragTo(x, y)
    }

    fun attach(inputVelocity: Float, inputVelocityY: Float) {
        drag.attach(inputVelocity, inputVelocityY)
    }

    fun reconcile(checked: Boolean) {
        if (completedAttachment == checked) { completedAttachment = null; return }
        completedAttachment = null
        if (!toggle.isRunning && !drag.isActive && abs(translation - endpoint(checked)) >= .1f) animateToggle(checked)
    }

    fun cancel() {
        completedAttachment = null
        snap(observed)
    }

    fun dispose() {
        toggle.cancelComplete()
        drag.cancel()
    }

    private fun finishAttachment() {
        stretch = 1f
        val checked = (translation >= travel.toInt() / 2) != rtl
        completedAttachment = checked
        onCommit(checked)
        settled++
    }

    private fun animateToggle(checked: Boolean) {
        drag.cancel()
        if (!toggle.isRunning) toggle.snapTo(translation)
        toggle.animateTo(endpoint(checked), CouiSwitchDefaults.toggleResponse, CouiSwitchDefaults.toggleBounce,
            onFrame = { updatePosition(it, toggling = true) },
            onEnd = { stretch = 1f; settled++ })
    }

    private fun updatePosition(value: Float, toggling: Boolean) {
        translation = value.toInt().toFloat()
        val p = logical(if (toggling) value else translation)
        fraction = p.coerceIn(0f, 1f)
        stretch = if (toggling) {
            if (p < .34725848f) 1f + p / .34725848f * .29999995f
            else 1.3f - (p - .34725848f) / .65274155f * .29999995f
        } else 1f
    }

    private fun snap(checked: Boolean) {
        dispose()
        toggle.snapTo(endpoint(checked))
        translation = endpoint(checked).toInt().toFloat()
        fraction = if (checked) 1f else 0f
        stretch = 1f
    }
}
