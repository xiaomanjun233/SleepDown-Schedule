package com.itos.coui.input

import android.view.View

/** COUIHapticFeedbackConstants；由当前 ROM 消费，不合成另一套马达波形。 */
enum class CouiHapticFeedback(val code: Int) {
    Switch(302),
    ScrollEdge(307),
}

/** 保留 View 和系统触觉设置的门控；不支持该反馈码的 ROM 返回 false。 */
fun View.performCouiHapticFeedback(feedback: CouiHapticFeedback, enabled: Boolean = true): Boolean =
    enabled && isAttachedToWindow && performHapticFeedback(feedback.code)
