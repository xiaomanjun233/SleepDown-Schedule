package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/** Owns only the SDK handshake. Business anchors and source pixels stay in the existing payload. */
internal class SharedViewKitBackend(
    private val api: SharedViewKitApi = PlatformSharedViewKitApi
) : TransitionBackend {
    override val name = "sharedViewKit"
    private val handler = Handler(Looper.getMainLooper())
    private val resources = mutableMapOf<TransitionSessionId, Resource>()
    private var movingSession: TransitionSessionId? = null

    private class Resource(
        val session: TransitionSession,
        val source: WeakReference<Activity>,
        val capabilities: SharedViewKitCapabilities,
        val generation: Long,
        var closing: Boolean = false,
        var watchdog: Runnable? = null
    )

    override suspend fun open(request: TransitionOpenRequest): TransitionBackendOpenResult {
        val anchor = request.payload?.openingAnchor
        if (anchor?.isValid != true || !anchor.cornerRadiusPx.isFinite() || anchor.cornerRadiusPx < 0f)
            return rejected("invalidAnchor")
        if (request.activity.isDestroyed || request.activity.isFinishing ||
            request.activity.isInMultiWindowMode || request.activity.isInPictureInPictureMode)
            return rejected("unsupportedWindow")
        if (movingSession != null) return rejected("animationInProgress")
        val capabilities = sdk("capability") { if (api.supportedProtocol() > 0) SharedViewKitCapabilities.from(api.extras()) else null }
            ?: return rejected("unsupportedProtocol")
        // Pilot routes must be able to return safely when their business source was removed.
        if (capabilities.missingTargetBackType == null) return rejected("safeReturnUnavailable")
        val session = request.session
        if (!session.moveTo(TransitionSessionState.NativeRegistered)) return rejected("invalidSessionState")
        val resource = Resource(session, WeakReference(request.activity), capabilities, session.nextCallbackGeneration())
        resources[session.id] = resource
        if (!TransitionPayloadStore.registerCleanup(session.id) { dispatch { release(session.id) } })
            return rejected("sessionReleased")
        val callbacks = SharedViewKitCallbacks(
            onStart = { mode -> handler.post { onStart(resource, mode) } },
            onEnd = { mode -> handler.post { onEnd(resource, mode) } },
            onCancel = { mode -> handler.post {
                if (current(resource)) {
                    if (mode == SharedViewKitContract.Back && resource.closing) complete(resource)
                    else if (mode == SharedViewKitContract.Open && !resource.closing &&
                        resource.session.currentState in setOf(TransitionSessionState.NativeRegistered, TransitionSessionState.NativeRunning))
                        fallback(resource, "openCancelled")
                }
            } }
        )
        val extras = Bundle().apply {
            capabilities.fallbackType?.let { putInt(SharedViewKitContract.FallbackType, it) }
        }
        val options = sdk("options") { api.options(checkNotNull(anchor.bitmap),
            boundsForKit(request.activity.window.decorView, anchor, capabilities),
            anchor.cornerRadiusPx, extras, callbacks) }
        if (options == null) {
            session.invalidateCallbacks()
            release(session.id)
            return rejected("optionsUnavailable")
        }
        return try {
            movingSession = session.id
            request.activity.startActivity(request.intent, options.toBundle())
            watch(resource)
            TransitionBackendOpenResult.Started
        } catch (_: RuntimeException) {
            session.invalidateCallbacks()
            release(session.id)
            rejected("activityStartFailed")
        }
    }

    override fun requestClose(activity: Activity, session: TransitionSession): Boolean {
        val resource = resources[session.id] ?: return false
        if (session.currentState !in setOf(TransitionSessionState.NativeRunning, TransitionSessionState.Open) ||
            (movingSession != null && movingSession != session.id)) {
            fallback(resource, "closeStateUnavailable")
            return false
        }
        val source = resource.source.get()?.takeUnless { it.isDestroyed || it.isFinishing }
        val anchor = TransitionPayloadStore.payload(session.id)?.returnAnchorProvider?.resolve()
        val bundle = Bundle().apply {
            if (source != null && anchor?.isValid == true) {
                putParcelable(SharedViewKitContract.Bounds, boundsForKit(source.window.decorView, anchor, resource.capabilities))
                putFloat("sharedView.radius", anchor.cornerRadiusPx)
            } else putInt(SharedViewKitContract.BackType, checkNotNull(resource.capabilities.missingTargetBackType))
        }
        val updated = sdk("return") { api.updateReturn(activity, bundle); true } == true
        if (!updated || !session.moveTo(TransitionSessionState.Closing)) {
            fallback(resource, "returnUpdateFailed")
            return false
        }
        resource.closing = true
        movingSession = session.id
        TransitionPayloadStore.releaseSource(session.id)
        watch(resource)
        return true
    }

    private fun onStart(resource: Resource, mode: Int) {
        if (!current(resource)) return
        if (mode == SharedViewKitContract.Open && !resource.closing &&
            resource.session.moveFrom(TransitionSessionState.NativeRegistered, TransitionSessionState.NativeRunning)) {
            TransitionPayloadStore.handoffOpeningSource(resource.session.id)
        }
    }

    private fun onEnd(resource: Resource, mode: Int) {
        if (!current(resource)) return
        if (mode == SharedViewKitContract.Back && resource.closing) complete(resource)
        else if (mode == SharedViewKitContract.Open && !resource.closing) {
            onStart(resource, mode)
            if (resource.session.moveFrom(TransitionSessionState.NativeRunning, TransitionSessionState.Open)) {
                disarm(resource)
                if (movingSession == resource.session.id) movingSession = null
            }
        }
    }

    private fun fallback(resource: Resource, reason: String) {
        if (!current(resource)) return
        Log.d("ActivityTransition", "backend=$name route=${resource.session.routeId.wireName} fallback=$reason")
        resource.session.invalidateCallbacks()
        release(resource.session.id)
        // The destination may already exist: let its existing host play Legacy, never launch twice.
        TransitionPayloadStore.handoffOpeningSource(resource.session.id)
        resource.session.moveTo(TransitionSessionState.LegacyRunning)
    }

    private fun complete(resource: Resource) {
        if (!current(resource)) return
        resource.session.invalidateCallbacks()
        if (movingSession == resource.session.id) movingSession = null
        resource.session.moveTo(TransitionSessionState.Finished)
        TransitionPayloadStore.remove(resource.session.id)
    }

    override fun onActivityDestroyed(activity: Activity) {
        resources.values.toList().forEach { resource ->
            if (resource.source.get() === activity) {
                resource.session.invalidateCallbacks()
                resource.session.moveTo(TransitionSessionState.Cancelled)
                TransitionPayloadStore.remove(resource.session.id)
            } else if (activity.intent.transitionSessionIdOrNull() == resource.session.id &&
                !(activity.isFinishing && resource.closing)) {
                fallback(resource, "destinationDestroyed")
            }
        }
    }

    private fun watch(resource: Resource) {
        disarm(resource)
        resource.watchdog = Runnable {
            if (current(resource)) {
                if (resource.closing) {
                    if (movingSession == resource.session.id) sdk("timeout") { api.cancel() }
                    complete(resource)
                } else fallback(resource, "openCallbackTimeout")
            }
        }.also { handler.postDelayed(it, CallbackTimeoutMillis) }
    }

    private fun disarm(resource: Resource) {
        resource.watchdog?.let(handler::removeCallbacks)
        resource.watchdog = null
    }

    private fun current(resource: Resource) = resources[resource.session.id] === resource &&
        resource.session.isCurrentCallback(resource.generation)

    private fun release(id: TransitionSessionId) {
        resources.remove(id)?.let(::disarm)
        if (movingSession == id) {
            movingSession = null
            sdk("cancel") { api.cancel() }
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    private inline fun <T> sdk(operation: String, block: () -> T): T? = try { block() }
        catch (error: RuntimeException) { Log.w("SharedViewKit", "$operation: ${error.javaClass.simpleName}"); null }
        catch (error: LinkageError) { Log.w("SharedViewKit", "$operation: ${error.javaClass.simpleName}"); null }

    private fun rejected(reason: String) = TransitionBackendOpenResult.Rejected(reason)

    companion object { internal const val CallbackTimeoutMillis = 3_000L }
}

/** Compose anchors are always in window coordinates; only the SDK metadata chooses the output. */
internal fun boundsForKit(root: View, anchor: TransitionAnchorFrame, capabilities: SharedViewKitCapabilities): Rect {
    val offset = IntArray(2)
    if (!capabilities.windowCoordinates) {
        val inWindow = IntArray(2)
        root.getLocationOnScreen(offset)
        root.getLocationInWindow(inWindow)
        offset[0] -= inWindow[0]
        offset[1] -= inWindow[1]
    }
    val bounds = anchor.boundsInWindow
    return Rect(bounds.left.roundToInt() + offset[0], bounds.top.roundToInt() + offset[1],
        bounds.right.roundToInt() + offset[0], bounds.bottom.roundToInt() + offset[1])
}
