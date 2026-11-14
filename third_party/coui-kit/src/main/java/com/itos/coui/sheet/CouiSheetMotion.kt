package com.itos.coui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Velocity
import com.itos.coui.motion.CouiSpring
import kotlin.math.abs

/** COUIBottomSheetDialog 普通底部窗口的位移与遮罩；不进入居中/锚点的缩放分支。 */
internal class CouiSheetMotion {
    var offset by mutableFloatStateOf(0f)
        private set
    var dim by mutableFloatStateOf(0f)
        private set
    var entered by mutableStateOf(false)
        private set
    var height by mutableFloatStateOf(0f)
        private set
    private var viewport = 0f
    private var peek = 0f
    private var target: Boolean? = null
    private var dragging = false
    private var lastNestedDelta: Float? = null
    val hasNestedDrag get() = lastNestedDelta != null
    private var dismissVelocity: Float? = null
    var onHidden: () -> Unit = {}
    private val hideDistance: Float
        // 原厂先把测量高度扩展 paddingBottom，再在位移距离中减掉；这里记录的是窗口内可见高度。
        get() = height.coerceAtLeast(1f)
    private val translation = CouiSpring(0f)
    private val alpha = CouiSpring(0f, minimumChange = .001f)

    fun update(visible: Boolean, height: Float, viewport: Float, peek: Float) {
        if (height <= 0f) return
        val resized = this.height != height || this.viewport != viewport || this.peek != peek
        val first = this.height == 0f
        this.height = height
        this.viewport = viewport
        this.peek = peek
        if (first) {
            offset = hideDistance
        }
        if (target != visible || (resized && !dragging)) {
            dragging = false
            lastNestedDelta = null
            target = visible
            if (!visible) entered = false
            val velocity = if (!visible) dismissVelocity else null
            dismissVelocity = null
            settle(visible, velocity)
        }
    }

    fun drag(delta: Float, nested: Boolean = false): Float {
        if (target != true) return 0f
        val next = (offset + delta).coerceIn(0f, hideDistance)
        val consumed = next - offset
        if (consumed == 0f) return 0f
        // 拖动接管几何后，旧动画的 pending 目标与速度不再属于本次手势。
        if (!dragging) {
            translation.cancelComplete()
            alpha.cancelComplete()
            dragging = true
        }
        lastNestedDelta = if (nested) delta else null
        offset = next
        dim = 1f - offset / hideDistance
        return consumed
    }

    /** ViewDragHelper 只在纵向速度占主导或面板越过中线时请求隐藏。 */
    fun release(velocity: Velocity): Boolean {
        val hide = offset > 0f && velocity.y >= 0f &&
            ((velocity.y > 500f && velocity.y > abs(velocity.x)) || offset > hideDistance * .5f)
        return endDrag(velocity.y, hide)
    }

    /** skipCollapsed 的嵌套释放按面板最后实际消费的方向判定。 */
    fun releaseNested(velocity: Float): Boolean {
        val lastDelta = lastNestedDelta ?: return false
        return endDrag(velocity, offset > 0f && lastDelta > 0f)
    }

    fun cancelDrag() {
        endDrag(0f, hide = false)
    }

    private fun endDrag(velocity: Float, hide: Boolean): Boolean {
        if (!dragging) return false
        dragging = false
        lastNestedDelta = null
        // 只有宿主改变 visible 才能隐藏。宿主拒绝退出时，面板仍恢复展开。
        dismissVelocity = if (hide) velocity.coerceAtLeast(0f) else null
        settle(true, if (hide) 0f else velocity)
        return hide
    }

    private fun settle(show: Boolean, velocity: Float? = null) {
        val ratio = ((height - peek) / (viewport - peek).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val response = if (show) .25f + .20f * ratio else .18f + .19f * ratio
        if (!translation.isRunning) translation.snapTo(offset)
        translation.animateTo(if (show) 0f else hideDistance + 1f, response, velocity = velocity,
            onFrame = { value ->
                offset = value.coerceAtLeast(0f)
            },
            onEnd = {
                if (!dragging) {
                    alpha.snapTo(if (target == true) 1f else 0f)
                    dim = if (target == true) 1f else 0f
                    if (target == true) { entered = true; dismissVelocity = null }
                    else if (target == false) onHidden()
                }
            })
        if (!alpha.isRunning) alpha.snapTo(dim)
        // 原厂普通底部窗口的遮罩与位移共用 response；归一化后保留一像素的结束精度。
        alpha.animateTo(if (show) 1f else 0f, response, minimumChange = 1f / hideDistance,
            onFrame = { dim = it.coerceIn(0f, 1f) })
    }

    fun dispose() {
        dragging = false
        lastNestedDelta = null
        translation.cancelComplete()
        alpha.cancelComplete()
    }
}
