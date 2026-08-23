@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.transition

import androidx.annotation.Keep
import com.oplus.animation.OplusViewSeamless

/**
 * The only class with a static vendor SDK reference. It is instantiated reflectively after the
 * runtime implementation has been proven present, so common transition code remains loadable on
 * non-ColorOS devices.
 */
@Keep
class OplusVendorCallbackFactory private constructor() {
    companion object {
        @JvmStatic
        fun create(operationName: String, sinkValue: Any): Any =
            OplusVendorAnimationCallback(
                OplusAnimationOperation.valueOf(operationName),
                sinkValue as OplusAnimationCallbackSink
            )
    }
}

@Keep
private class OplusVendorAnimationCallback(
    private val operation: OplusAnimationOperation,
    private val sink: OplusAnimationCallbackSink
) : OplusViewSeamless.AnimationCallback() {
    override fun animationProgress(progress: Float) {
        sink.onAnimationProgress(operation, progress)
    }

    override fun onAnimationStart(entering: Boolean) {
        sink.onAnimationStart(operation, entering)
    }

    override fun onAnimationEnd(entering: Boolean) {
        sink.onAnimationEnd(operation, entering)
    }
}
