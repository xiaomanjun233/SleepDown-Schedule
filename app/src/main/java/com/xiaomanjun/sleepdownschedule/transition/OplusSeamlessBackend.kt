package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Rect as AndroidRect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.core.view.doOnPreDraw
import com.xiaomanjun.sleepdownschedule.BuildConfig
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val OplusClassName = "com.oplus.animation.OplusViewSeamless"
private const val OplusCallbackClassName =
    "com.oplus.animation.OplusViewSeamless\$AnimationCallback"
private const val OplusCallbackFactoryClassName =
    "com.xiaomanjun.sleepdownschedule.transition.OplusVendorCallbackFactory"
private const val ViewSeamlessOpenKey = "view_seamless_open"
private const val ViewSeamlessCloseKey = "view_seamless_close"
private const val BundleColorKey = "view_seamless_color"
private const val BundleRadiusKey = "view_seamless_radius"
private const val BundleRectKey = "view_seamless_rect"
private const val BundleBitmapKey = "view_seamless_bitmap"
private const val BundleForceLeashAlphaOutKey = "view_seamless_force_leash_alpha_out"
private const val BundleViewVisibleKey = "view_seamless_view_visible"
private const val BundleViewWithAlphaKey = "view_seamless_view_with_alpha"
private const val SourceFrameCommitTimeoutMillis = 300L
private const val SourceReadyTimeoutMillis = 300L
private const val ReturnCleanupWatchdogMillis = 1_400L
private const val ReturnSourceCommitTimeoutMillis = 300L
private const val ReturnSnapshotRevealProgress = 0.94f
private const val LogTag = "ActivityTransition"

enum class OplusAnimationOperation { Open, Close }

/** Kept vendor-free so this interface is safe to load on every Android device. */
interface OplusAnimationCallbackSink {
    fun onAnimationStart(operation: OplusAnimationOperation, entering: Boolean)
    fun onAnimationEnd(operation: OplusAnimationOperation, entering: Boolean)
    fun onAnimationProgress(operation: OplusAnimationOperation, progress: Float)
}

private data class OplusApi(
    val runtimeClass: Class<*>,
    val callbackClass: Class<*>,
    val setSeamlessView: Method,
    val finishCurrentAnimation: Method?,
    val setSkipViewSeamless: Method?,
    val version: Int,
    val featureEnabled: Boolean?
)

private data class PreparedBitmap(
    val bitmap: Bitmap,
    val ownedCopy: Boolean
)

/** Pure gesture discriminator so horizontal page gestures never tear down a return bridge. */
internal class ReturnVerticalDragDetector(touchSlopPx: Float) {
    private val thresholdPx = touchSlopPx.coerceAtLeast(1f)
    private var tracking = false
    private var triggered = false
    private var downX = 0f
    private var downY = 0f

    fun onTouch(actionMasked: Int, rawX: Float, rawY: Float): Boolean = when (actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            tracking = true
            triggered = false
            downX = rawX
            downY = rawY
            false
        }
        MotionEvent.ACTION_MOVE -> {
            if (!tracking || triggered) {
                false
            } else {
                val deltaX = abs(rawX - downX)
                val deltaY = abs(rawY - downY)
                (deltaY >= thresholdPx && deltaY > deltaX).also { isVerticalDrag ->
                    if (isVerticalDrag) triggered = true
                }
            }
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            tracking = false
            triggered = false
            false
        }
        else -> false
    }
}

/** Observes, but always delegates, source-window input during the short return cleanup window. */
private class ReturnInteractionObserver private constructor(
    activity: Activity,
    private val delegate: Window.Callback,
    private val onVerticalDrag: () -> Unit
) : InvocationHandler {
    private val activity = WeakReference(activity)
    private val active = AtomicBoolean(true)
    private val detector = ReturnVerticalDragDetector(
        ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
    )

    val callback: Window.Callback = Proxy.newProxyInstance(
        delegate.javaClass.classLoader,
        arrayOf(Window.Callback::class.java),
        this
    ) as Window.Callback

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        if (active.get() && method.name == "dispatchTouchEvent") {
            val event = arguments?.firstOrNull() as? MotionEvent
            if (event != null && detector.onTouch(event.actionMasked, event.rawX, event.rawY)) {
                onVerticalDrag()
            }
        }
        return try {
            method.invoke(delegate, *(arguments ?: emptyArray()))
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    fun detach() {
        active.set(false)
        val sourceActivity = activity.get() ?: return
        runCatching {
            if (sourceActivity.window.callback === callback) {
                sourceActivity.window.callback = delegate
            }
        }
    }

    companion object {
        fun attach(activity: Activity, onVerticalDrag: () -> Unit): ReturnInteractionObserver? {
            val delegate = activity.window.callback ?: return null
            val observer = ReturnInteractionObserver(activity, delegate, onVerticalDrag)
            return if (runCatching { activity.window.callback = observer.callback }.isSuccess) {
                observer
            } else {
                null
            }
        }
    }
}

private class NativeSessionResource(
    val sessionId: TransitionSessionId,
    val sourceActivity: WeakReference<Activity>,
    val sourceView: View,
    val api: OplusApi,
    initialAnchor: TransitionAnchorFrame,
    initialBitmap: PreparedBitmap,
    initialCallback: Any
) {
    private val disposed = AtomicBoolean(false)
    private val returnCleanupStarted = AtomicBoolean(false)
    private var currentAnchor = initialAnchor
    private var preparedBitmap = initialBitmap
    // ColorOS keeps the OPEN ViewInfo/bitmap inside its remote transition. Keep every software
    // generation alive until the exact session is disposed; replacing or recycling the OPEN
    // generation while preparing CLOSE can invalidate the retained native session.
    private val preparedBitmaps = mutableListOf(initialBitmap)
    private var returnInteractionObserver: ReturnInteractionObserver? = null
    @Keep private var callback: Any? = initialCallback
    @Keep private var returnCallback: Any? = null
    @Volatile var returnIssued: Boolean = false
    @Volatile var returnAnimationStarted: Boolean = false
    @Volatile var returnAnimationEnded: Boolean = false

    fun prepareReturnBitmap(anchor: TransitionAnchorFrame): PreparedBitmap? {
        // Keep a distinct app-owned return generation. ColorOS retains the OPEN bitmap for the
        // whole remote session, while the CLOSE registration reads this new generation.
        val next = prepareSoftwareBitmap(anchor.bitmap) ?: return null
        if (!anchor.isValid) {
            if (next.ownedCopy) next.bitmap.recycle()
            return null
        }
        preparedBitmaps += next
        preparedBitmap = next
        currentAnchor = anchor
        return next
    }

    fun prepareSourceViewForReturn(): SourceBridgeInspection {
        val source = sourceView as? OplusRegistrationSourceView
            ?: return SourceBridgeInspection(sourceView, AndroidRect(), "returnBridgeTypeMismatch")
        source.prepareForReturn(currentAnchor, preparedBitmap.bitmap)
        return inspectSourceBridge(source)
    }

    fun retainReturnCallback(next: Any) {
        returnCallback = next
    }

    fun showReturnPixels() {
        (sourceView as? OplusRegistrationSourceView)?.showReturnPixels()
    }

    fun observeReturnInteraction(activity: Activity, onVerticalDrag: () -> Unit) {
        if (disposed.get() || returnInteractionObserver != null) return
        returnInteractionObserver = ReturnInteractionObserver.attach(activity, onVerticalDrag)
    }

    fun beginReturnCleanup(): Boolean = returnCleanupStarted.compareAndSet(false, true)

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        returnInteractionObserver?.detach()
        returnInteractionObserver = null
        callback = null
        returnCallback = null
        sourceView.background = null
        runCatching { (sourceView.parent as? ViewGroup)?.removeView(sourceView) }
        preparedBitmaps.forEach { prepared ->
            if (prepared.ownedCopy && !prepared.bitmap.isRecycled) {
                prepared.bitmap.recycle()
            }
        }
        preparedBitmaps.clear()
    }
}

/** Session-scoped wrapper around the verified ColorOS course-detail ViewSeamless call. */
internal class OplusSeamlessBackend : TransitionBackend {
    override val name: String = "oplusViewSeamless"
    private val resources = ConcurrentHashMap<TransitionSessionId, NativeSessionResource>()

    override suspend fun open(request: TransitionOpenRequest): TransitionBackendOpenResult {
        val anchor = request.payload?.openingAnchor
            ?: return rejected(request, "missingOpeningAnchor")
        val preparedBitmap = prepareSoftwareBitmap(anchor.bitmap)
            ?: return rejected(request, "softwareBitmapUnavailable")
        val api = resolveApi(request.activity)
        if (api == null) {
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, "runtimeApiUnavailable")
        }
        // Compose applying the hidden-source state is not enough: the source window can still
        // expose its previously latched buffer below the vendor leash. Wait until a frame that no
        // longer contains the business source has actually reached SurfaceFlinger, then attach the
        // registration-only bridge. This is a frame-commit barrier, not a timing delay.
        if (!request.activity.awaitSourceFrameCommit()) {
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, "sourceFrameCommitTimeout")
        }
        val sourceView = request.activity.createOplusPlainSnapshotView(
            anchor = anchor,
            bitmap = preparedBitmap.bitmap
        )
        val sourceInspection = inspectSourceBridge(sourceView)
        logSourceBridge(request.session, "open", sourceInspection)
        if (!sourceInspection.ready || sourceView == null) {
            sourceView?.let(::detachOplusSnapshotView)
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, sourceInspection.failureReason ?: "sourceBridgeMissing")
        }
        val decision = OplusCapabilityPolicy.evaluate(
            OplusCapabilityFacts(
                runtimeClassPresent = true,
                requiredMethodsPresent = true,
                version = api.version,
                featureEnabled = api.featureEnabled,
                sourceReady = true,
                softwareBitmapReady = true,
                unsupportedWindowMode = request.activity.isInMultiWindowMode ||
                    request.activity.isInPictureInPictureMode,
                opaqueDestinationCandidate = request.route.destinationWindowPolicy ==
                    TransitionDestinationWindowPolicy.OpaqueNativeCandidate
            )
        )
        if (!decision.supported) {
            detachOplusSnapshotView(sourceView)
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, decision.reason)
        }

        val generation = request.session.nextCallbackGeneration()
        val callback = createVendorCallback(
            operation = OplusAnimationOperation.Open,
            sink = callbackSink(request.session, generation)
        ) ?: run {
            detachOplusSnapshotView(sourceView)
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, "callbackAdapterUnavailable")
        }
        val bundle = seamlessBundle(
            opening = true,
            anchor = anchor,
            bitmap = preparedBitmap.bitmap,
            forceSourceLeashAlphaOut =
                request.payload.nativeSourceLeashAlphaOutOnOpen
        )
        val accepted = invokeSetSeamlessView(
            api = api,
            sourceView = sourceView,
            activity = request.activity,
            bundle = bundle,
            callback = callback
        )
        if (!accepted) {
            request.session.invalidateCallbacks()
            detachOplusSnapshotView(sourceView)
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, "setSeamlessViewRejected")
        }

        if (!request.session.moveTo(TransitionSessionState.NativeRegistered)) {
            request.session.invalidateCallbacks()
            finishCurrentAnimation(api)
            detachOplusSnapshotView(sourceView)
            if (preparedBitmap.ownedCopy) preparedBitmap.bitmap.recycle()
            return rejected(request, "invalidRegisteredState")
        }
        val resource = NativeSessionResource(
            request.session.id,
            WeakReference(request.activity),
            sourceView,
            api,
            anchor,
            preparedBitmap,
            callback
        )
        resources[request.session.id] = resource
        val cleanupRegistered = TransitionPayloadStore.registerCleanup(request.session.id) {
            resources.remove(request.session.id)?.dispose()
        }
        if (!cleanupRegistered) {
            request.session.invalidateCallbacks()
            finishCurrentAnimation(api)
            resources.remove(request.session.id)?.dispose()
            return rejected(request, "sessionDisposedDuringRegistration")
        }
        return runCatching {
            request.activity.startActivity(request.intent, bundle)
            log(request.session, "registered", "accepted")
            TransitionBackendOpenResult.Started
        }.getOrElse { error ->
            request.session.invalidateCallbacks()
            finishCurrentAnimation(api)
            resources.remove(request.session.id)?.dispose()
            rejected(request, "nativeStart:${error.javaClass.simpleName}")
        }
    }

    fun requestClose(activity: Activity, session: TransitionSession): Boolean {
        if (!session.hasReachedNativeRunning) {
            return fallbackClose(session, "nativeOpenNeverRan")
        }
        if (TransitionRouteCatalog.get(session.routeId).nativeClosePolicy ==
            TransitionNativeClosePolicy.LegacyOnly
        ) {
            return handoffToLegacyClose(activity, session, "routeLegacyClose")
        }
        val closeSourceState = session.currentState
        if (closeSourceState != TransitionSessionState.NativeRunning &&
            closeSourceState != TransitionSessionState.Open
        ) {
            return fallbackClose(session, "invalidCloseState:$closeSourceState")
        }
        if (!session.moveFrom(closeSourceState, TransitionSessionState.Closing)) {
            return fallbackClose(session, "closeStateClaimLost")
        }
        val resource = resources[session.id]
            ?: return fallbackClose(session, "sessionResourceUnavailable")
        if (resource.sourceActivity.get() == null) {
            return fallbackClose(session, "sourceActivityUnavailable")
        }
        val payload = TransitionPayloadStore.payload(session.id)
            ?: return fallbackClose(session, "returnPayloadUnavailable")

        // The public SDK identifies a session by the source View. Reuse the exact OPEN bridge, but
        // move it to the independently captured live return anchor before registering CLOSE. A new
        // View would have a different identity and ColorOS would reverse toward the opening menu.
        val liveAnchor = runCatching {
            payload.returnAnchorProvider?.resolve()
        }.getOrNull()
            ?.takeIf(TransitionAnchorFrame::isValid)
        val anchor = liveAnchor
            ?: payload.openingAnchor?.takeIf(TransitionAnchorFrame::isValid)
            ?: return fallbackClose(session, "returnAnchorUnavailable")
        val prepared = runCatching { resource.prepareReturnBitmap(anchor) }.getOrNull()
            ?: return fallbackClose(session, "returnBitmapUnavailable")
        val sourceInspection = resource.prepareSourceViewForReturn()
        logSourceBridge(session, "close", sourceInspection)
        if (!sourceInspection.ready) {
            return fallbackClose(session, sourceInspection.failureReason ?: "returnSourceUnavailable")
        }

        val generation = session.nextCallbackGeneration()
        val callback = createVendorCallback(
            operation = OplusAnimationOperation.Close,
            sink = callbackSink(session, generation)
        ) ?: return fallbackClose(session, "returnCallbackAdapterUnavailable")
        resource.retainReturnCallback(callback)
        resource.returnIssued = true
        resource.returnAnimationStarted = false
        resource.returnAnimationEnded = false
        val registeredAnchor = anchor.copy(
            boundsInWindow = sourceInspection.bounds.toComposeRect()
        )
        val accepted = invokeSetSeamlessView(
            api = resource.api,
            sourceView = resource.sourceView,
            activity = activity,
            bundle = seamlessBundle(
                opening = false,
                anchor = registeredAnchor,
                bitmap = prepared.bitmap
            ),
            callback = callback
        )
        if (!accepted) {
            resource.returnIssued = false
            return fallbackClose(session, "returnSetSeamlessViewRejected")
        }
        log(session, "closing", "accepted")
        return true
    }

    private fun handoffToLegacyClose(
        activity: Activity,
        session: TransitionSession,
        reason: String
    ): Boolean {
        // TODO(OPLUS_DEFERRED_20260823): This is an attempted native-to-Legacy ownership handoff,
        // not a verified fix. On PLJ110 the signed acceptance build still appended/selected the
        // centered fade after the full app Morph. Keep it marked until WM Shell transition traces
        // identify which owner remains active; do not replace it with skipBackAnim (that hard-cuts).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        val resource = resources.remove(session.id)
        val systemCloseDisabled = resource?.api?.let { api ->
            setSkipViewSeamless(api, activity)
        } == true
        session.invalidateCallbacks()
        if (session.currentState == TransitionSessionState.NativeRunning) {
            resource?.api?.let(::finishCurrentAnimation)
        }
        resource?.dispose()
        session.moveTo(TransitionSessionState.LegacyRunning)
        log(session, "legacyClose", "$reason:systemCloseDisabled=$systemCloseDisabled")
        return false
    }

    private fun fallbackBeforeNativeStart(session: TransitionSession, reason: String) {
        if (!session.moveFrom(
                TransitionSessionState.NativeRegistered,
                TransitionSessionState.LegacyRunning
            )
        ) return
        TransitionPayloadStore.handoffOpeningSource(session.id)
        session.invalidateCallbacks()
        resources[session.id]?.api?.let(::finishCurrentAnimation)
        resources.remove(session.id)?.dispose()
        log(session, "fallback", reason)
    }

    fun onSourceActivityResumed(activity: Activity) {
        resources.values
            .filter { it.sourceActivity.get() === activity && it.returnIssued }
            .forEach { resource ->
                resource.observeReturnInteraction(activity) {
                    completeReturnCleanup(resource, "verticalDrag")
                }
                activity.window.decorView.postDelayed({
                    completeReturnCleanup(resource, "watchdog")
                }, ReturnCleanupWatchdogMillis)
            }
    }

    private fun completeReturnCleanup(
        resource: NativeSessionResource,
        reason: String
    ) {
        if (resources[resource.sessionId] !== resource || !resource.returnIssued) return
        val sourceActivity = resource.sourceActivity.get() ?: return
        sourceActivity.runOnUiThread {
            if (resources[resource.sessionId] !== resource || !resource.beginReturnCleanup()) {
                return@runOnUiThread
            }
            val session = TransitionPayloadStore.session(resource.sessionId)
            session?.invalidateCallbacks()
            session?.moveTo(TransitionSessionState.Finished)
            if (session != null) log(session, "returnCleanup", reason)
            resource.showReturnPixels()
            TransitionPayloadStore.releaseSource(resource.sessionId)
            sourceActivity.afterCommittedSourceFrames {
                TransitionPayloadStore.remove(resource.sessionId)
            }
        }
    }

    fun onActivityDestroyed(activity: Activity) {
        resources.values
            .filter { it.sourceActivity.get() === activity }
            .forEach { resource ->
                val session = TransitionPayloadStore.session(resource.sessionId)
                session?.invalidateCallbacks()
                session?.moveTo(TransitionSessionState.Cancelled)
                TransitionPayloadStore.remove(resource.sessionId)
            }
    }

    fun cancel(session: TransitionSession, reason: String) {
        session.invalidateCallbacks()
        resources[session.id]?.api?.let(::finishCurrentAnimation)
        resources.remove(session.id)?.dispose()
        session.moveTo(TransitionSessionState.Cancelled)
        log(session, "cancelled", reason)
    }

    private fun fallbackClose(session: TransitionSession, reason: String): Boolean {
        session.invalidateCallbacks()
        val resource = resources.remove(session.id)
        // End the rejected/stale native request before the validated Legacy close takes over.
        // End only the rejected request; the validated Legacy renderer owns the visible close.
        resource?.api?.let(::finishCurrentAnimation)
        resource?.dispose()
        session.moveTo(TransitionSessionState.LegacyRunning)
        log(session, "fallbackClose", reason)
        return false
    }

    private fun callbackSink(
        session: TransitionSession,
        generation: Long
    ): OplusAnimationCallbackSink = object : OplusAnimationCallbackSink {
        override fun onAnimationStart(operation: OplusAnimationOperation, entering: Boolean) {
            if (!session.isCurrentCallback(generation)) return
            when (operation) {
                OplusAnimationOperation.Open -> {
                    TransitionPayloadStore.handoffOpeningSource(session.id)
                    session.moveTo(TransitionSessionState.NativeRunning)
                }
                OplusAnimationOperation.Close -> {
                    resources[session.id]?.returnAnimationStarted = true
                    session.moveTo(TransitionSessionState.Closing)
                }
            }
            log(session, "animationStart", "${operation.name}:entering=$entering")
        }

        override fun onAnimationEnd(operation: OplusAnimationOperation, entering: Boolean) {
            if (!session.isCurrentCallback(generation)) return
            when (operation) {
                OplusAnimationOperation.Open -> {
                    if (session.currentState == TransitionSessionState.NativeRegistered) {
                        fallbackBeforeNativeStart(session, "animationEndBeforeStart")
                        return
                    }
                    session.moveTo(TransitionSessionState.Open)
                    log(session, "animationEnd", "open:entering=$entering")
                }
                OplusAnimationOperation.Close -> {
                    val resource = resources[session.id]
                    if (resource?.returnIssued != true || !resource.returnAnimationStarted) {
                        log(session, "animationEndIgnored", "closeBeforeConfirmedStart")
                        return
                    }
                    resource.returnAnimationEnded = true
                    resource.showReturnPixels()
                    log(session, "animationEnd", "close:entering=$entering")
                    resource.sourceActivity.get()?.window?.decorView?.postOnAnimation {
                        completeReturnCleanup(resource, "closeEnd")
                    }
                }
            }
        }

        override fun onAnimationProgress(
            operation: OplusAnimationOperation,
            progress: Float
        ) {
            if (operation == OplusAnimationOperation.Close &&
                progress >= ReturnSnapshotRevealProgress
            ) {
                resources[session.id]?.showReturnPixels()
            }
        }
    }

    private fun rejected(
        request: TransitionOpenRequest,
        reason: String
    ): TransitionBackendOpenResult.Rejected {
        log(request.session, "rejected", reason)
        return TransitionBackendOpenResult.Rejected(reason)
    }
}

private fun resolveApi(activity: Activity): OplusApi? = runCatching {
    val runtime = Class.forName(OplusClassName, false, activity.javaClass.classLoader)
    val callback = Class.forName(OplusCallbackClassName, false, runtime.classLoader)
    val setMethod = runtime.getMethod(
        "setSeamlessView",
        View::class.java,
        android.content.Context::class.java,
        Bundle::class.java,
        callback
    )
    val version = runtime.getMethod("getVersion").invoke(null) as Int
    val featureEnabled = runtime.methods
        .firstOrNull { it.name == "isFeatureEnabled" && it.parameterCount == 0 }
        ?.let { it.invoke(null) as? Boolean }
    OplusApi(
        runtimeClass = runtime,
        callbackClass = callback,
        setSeamlessView = setMethod,
        finishCurrentAnimation = runtime.methods.firstOrNull {
            it.name == "finishCurrentAnimation" && it.parameterCount == 0
        },
        setSkipViewSeamless = runtime.methods.firstOrNull {
            it.name == "setSkipViewSeamless" &&
                it.parameterCount == 1 &&
                Activity::class.java.isAssignableFrom(it.parameterTypes[0])
        },
        version = version,
        featureEnabled = featureEnabled
    )
}.getOrNull()

private fun createVendorCallback(
    operation: OplusAnimationOperation,
    sink: OplusAnimationCallbackSink
): Any? = runCatching {
    val factory = Class.forName(OplusCallbackFactoryClassName, true, sink.javaClass.classLoader)
    factory.getMethod("create", String::class.java, Any::class.java)
        .invoke(null, operation.name, sink)
}.getOrNull()

private fun invokeSetSeamlessView(
    api: OplusApi,
    sourceView: View,
    activity: Activity,
    bundle: Bundle,
    callback: Any
): Boolean = runCatching {
    api.setSeamlessView.invoke(null, sourceView, activity, bundle, callback) as Boolean
}.getOrElse { error ->
    if (BuildConfig.DEBUG) {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        Log.d(LogTag, "backend=oplus operation=setSeamlessView error=${cause.javaClass.simpleName}")
    }
    false
}

private fun finishCurrentAnimation(api: OplusApi) {
    runCatching { api.finishCurrentAnimation?.invoke(null) }
}

private fun setSkipViewSeamless(api: OplusApi, activity: Activity): Boolean = runCatching {
    val method = api.setSkipViewSeamless ?: return@runCatching false
    method.invoke(null, activity)
    true
}.getOrDefault(false)

private data class SourceBridgeInspection(
    val view: View?,
    val bounds: AndroidRect,
    val failureReason: String?
) {
    val ready: Boolean get() = failureReason == null
}

private fun inspectSourceBridge(view: View?): SourceBridgeInspection {
    val bounds = AndroidRect()
    val failure = when {
        view == null -> "sourceBridgeMissing"
        !view.isAttachedToWindow -> "sourceBridgeDetached"
        !view.isLaidOut -> "sourceBridgeNotLaidOut"
        view.width <= 0 || view.height <= 0 -> "sourceBridgeZeroSize"
        !runCatching { view.getBoundsOnScreen(bounds) }.isSuccess || bounds.isEmpty ->
            "sourceBridgeBoundsEmpty"
        else -> null
    }
    return SourceBridgeInspection(view, bounds, failure)
}

private fun View.getBoundsOnScreen(outBounds: AndroidRect) {
    val location = IntArray(2)
    getLocationOnScreen(location)
    outBounds.set(location[0], location[1], location[0] + width, location[1] + height)
}

/**
 * Registration bridge retained for the complete OPEN -> CLOSE session.
 *
 * ColorOS needs a real attached View with a non-null background to resolve source geometry and
 * background color. The animation pixels are supplied independently through BUNDLE_BITMAP, so
 * this View deliberately records no display-list content. Painting the bitmap here as well would
 * latch a second copy into the source Activity surface before ColorOS hides the View at animation
 * start, leaving the familiar stationary card/button underneath the moving leash.
 */
private suspend fun Activity.createOplusPlainSnapshotView(
    anchor: TransitionAnchorFrame,
    bitmap: Bitmap
): View? {
    if (!anchor.isValid || bitmap.isRecycled) return null
    val parent = window.decorView as? ViewGroup ?: return null
    val bounds = anchor.boundsInWindow
    val width = bounds.width.roundToInt().coerceAtLeast(1)
    val height = bounds.height.roundToInt().coerceAtLeast(1)
    val sourceView = OplusRegistrationSourceView(this).apply {
        background = BitmapDrawable(resources, bitmap)
        applyOplusRoundedOutline(anchor.cornerRadiusPx)
        alpha = 1f
        visibility = View.VISIBLE
        isClickable = false
        isLongClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val layoutParams = FrameLayout.LayoutParams(width, height).apply {
        leftMargin = bounds.left.roundToInt()
        topMargin = bounds.top.roundToInt()
    }
    val added = runCatching {
        parent.addView(sourceView, layoutParams)
        true
    }.getOrDefault(false)
    if (!added) return null

    val ready = withTimeoutOrNull(SourceReadyTimeoutMillis) {
        if (inspectSourceBridge(sourceView).ready) {
            true
        } else {
            suspendCancellableCoroutine { continuation ->
                sourceView.doOnPreDraw {
                    if (continuation.isActive) {
                        continuation.resume(inspectSourceBridge(sourceView).ready)
                    }
                }
                sourceView.requestLayout()
                sourceView.invalidate()
            }
        }
    } == true
    if (!ready) {
        detachOplusSnapshotView(sourceView)
        return null
    }
    return sourceView
}

private class OplusRegistrationSourceView(activity: Activity) : View(activity) {
    private var drawsReturnPixels = false
    private var returnPixelsReady = false

    fun prepareForReturn(anchor: TransitionAnchorFrame, bitmap: Bitmap) {
        val bounds = anchor.boundsInWindow
        val left = bounds.left.roundToInt()
        val top = bounds.top.roundToInt()
        val width = bounds.width.roundToInt().coerceAtLeast(1)
        val height = bounds.height.roundToInt().coerceAtLeast(1)
        background = BitmapDrawable(resources, bitmap)
        applyOplusRoundedOutline(anchor.cornerRadiusPx)
        layoutParams = FrameLayout.LayoutParams(width, height).apply {
            leftMargin = left
            topMargin = top
        }
        // The source Activity is stopped behind an opaque destination. Publish the live return
        // geometry synchronously; ColorOS will alpha this View out before revealing that window.
        layout(left, top, left + width, top + height)
        // Registration needs the real background, bounds and outline, but drawing the bitmap from
        // frame zero would leave a stationary duplicate below the moving ColorOS leash.
        returnPixelsReady = true
        drawsReturnPixels = false
        alpha = 1f
        visibility = View.VISIBLE
        requestLayout()
        invalidate()
    }

    fun showReturnPixels() {
        if (!returnPixelsReady || drawsReturnPixels) return
        drawsReturnPixels = true
        // TODO(OPLUS_DEFERRED_20260823): Restoring alpha/visibility here did not remove the CLOSE
        // blank frame on PLJ110. It is retained as evidence of the attempted source-buffer handoff;
        // verify the actual Surface/transaction ordering before changing this path again.
        alpha = 1f
        visibility = View.VISIBLE
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        // OPEN uses only geometry/background metadata, avoiding a second latched source copy.
        // CLOSE lets ColorOS frame-sync this exact return bitmap before removing its leash.
        if (drawsReturnPixels) super.draw(canvas)
    }
}

private fun Activity.afterCommittedSourceFrames(onCommitted: () -> Unit) {
    val decor = window.decorView
    val completed = AtomicBoolean(false)
    fun complete() {
        if (completed.compareAndSet(false, true)) onCommitted()
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !decor.isAttachedToWindow) {
        decor.postOnAnimation(::complete)
        return
    }

    fun awaitCommittedFrame(remaining: Int) {
        if (completed.get()) return
        val observer = decor.viewTreeObserver
        if (!observer.isAlive) {
            decor.postOnAnimation(::complete)
            return
        }
        lateinit var callback: Runnable
        callback = Runnable {
            if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
            if (remaining <= 1) complete()
            else decor.post { awaitCommittedFrame(remaining - 1) }
        }
        observer.registerFrameCommitCallback(callback)
        decor.postInvalidateOnAnimation()
    }

    awaitCommittedFrame(2)
    decor.postDelayed(::complete, ReturnSourceCommitTimeoutMillis)
}

private suspend fun Activity.awaitSourceFrameCommit(): Boolean {
    // ViewSeamless itself requires a much newer ColorOS release; this branch only keeps the
    // vendor-free backend safe on older Android versions during capability probing.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    val decorView = window.decorView
    if (!decorView.isAttachedToWindow) return false
    return withTimeoutOrNull(SourceFrameCommitTimeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val observer = decorView.viewTreeObserver
            if (!observer.isAlive) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            lateinit var callback: Runnable
            callback = Runnable {
                if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                if (continuation.isActive) continuation.resume(true)
            }
            observer.registerFrameCommitCallback(callback)
            continuation.invokeOnCancellation {
                decorView.post {
                    if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                }
            }
            // Force a traversal after registration so the callback cannot belong to an older
            // already-submitted frame.
            decorView.postInvalidateOnAnimation()
        }
    } == true
}

private fun View.applyOplusRoundedOutline(radiusPx: Float) {
    val safeRadius = radiusPx.coerceAtLeast(0f)
    clipToOutline = safeRadius > 0f
    outlineProvider = if (safeRadius > 0f) {
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, safeRadius)
            }
        }
    } else {
        ViewOutlineProvider.BOUNDS
    }
    invalidateOutline()
}

private fun detachOplusSnapshotView(view: View) {
    view.background = null
    runCatching { (view.parent as? ViewGroup)?.removeView(view) }
}

private fun logSourceBridge(
    session: TransitionSession,
    operation: String,
    inspection: SourceBridgeInspection
) {
    if (!BuildConfig.DEBUG) return
    val view = inspection.view
    Log.d(
        LogTag,
        "route=${session.routeId.wireName} session=${session.id.value} " +
            "backend=oplus operation=$operation sourceBridgeClass=${view?.javaClass?.name} " +
            "sourceBridgeAttached=${view?.isAttachedToWindow} " +
            "sourceBridgeLaidOut=${view?.isLaidOut} sourceBridgeWidth=${view?.width} " +
            "sourceBridgeHeight=${view?.height} sourceBridgeBounds=${inspection.bounds} " +
            "sourceBridgeAlpha=${view?.alpha} sourceBridgeVisibility=${view?.visibility} " +
            "fallbackReason=${inspection.failureReason}"
    )
}

private fun prepareSoftwareBitmap(source: Bitmap?): PreparedBitmap? {
    if (source == null || source.isRecycled) return null
    // The vendor process must never receive the renderer-owned payload bitmap. Keeping a
    // dedicated software copy also lets a session recycle its bridge without touching Morph.
    val copy = runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        ?: return null
    return PreparedBitmap(copy, true)
}

private fun seamlessBundle(
    opening: Boolean,
    anchor: TransitionAnchorFrame,
    bitmap: Bitmap,
    forceSourceLeashAlphaOut: Boolean = false
): Bundle = Bundle().apply {
    putBoolean(if (opening) ViewSeamlessOpenKey else ViewSeamlessCloseKey, true)
    putFloat(BundleRadiusKey, anchor.cornerRadiusPx.coerceAtLeast(0f))
    putParcelable(BundleRectKey, anchor.boundsInWindow.toAndroidRect())
    putParcelable(BundleBitmapKey, bitmap)
    if (!opening) putInt(BundleColorKey, bitmap.averageColorInt())
    if (opening && forceSourceLeashAlphaOut) {
        // This is the public SDK's source-window handoff. It keeps the real source visible while
        // Activity launch is asynchronous, then removes that source leash as the native spring
        // begins so no stationary copy remains below the moving bitmap leash.
        putBoolean(BundleForceLeashAlphaOutKey, true)
    }
    // Both public OPEN and CLOSE registrations must report the attached source as visible. Passing
    // false on CLOSE makes ColorOS abandon the registered target and use its centered fade.
    putBoolean(BundleViewVisibleKey, true)
    putBoolean(BundleViewWithAlphaKey, true)
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRect(): AndroidRect = AndroidRect(
    left.roundToInt(),
    top.roundToInt(),
    right.roundToInt(),
    bottom.roundToInt()
)

private fun AndroidRect.toComposeRect(): androidx.compose.ui.geometry.Rect =
    androidx.compose.ui.geometry.Rect(
        left = left.toFloat(),
        top = top.toFloat(),
        right = right.toFloat(),
        bottom = bottom.toFloat()
    )

private fun Bitmap.averageColorInt(): Int = runCatching {
    val sample = if (width > 16 || height > 16) {
        Bitmap.createScaledBitmap(this, 16, 16, true)
    } else {
        this
    }
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += pixel shr 16 and 0xFF
        green += pixel shr 8 and 0xFF
        blue += pixel and 0xFF
    }
    if (sample !== this) sample.recycle()
    val count = pixels.size.coerceAtLeast(1)
    (0xFF shl 24) or ((red / count).toInt() shl 16) or
        ((green / count).toInt() shl 8) or (blue / count).toInt()
}.getOrElse { 0xFF121212.toInt() }

private fun log(session: TransitionSession, event: String, reason: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(
        LogTag,
        "route=${session.routeId.wireName} session=${session.id.value} " +
            "backend=oplus state=${session.currentState} event=$event reason=$reason"
    )
}
