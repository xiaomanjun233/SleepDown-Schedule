package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
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
        val kitBitmap: Bitmap,
        val sourceView: SharedViewKitSourceView,
        var closing: Boolean = false,
        var watchdog: Runnable? = null
    ) {
        var kitReturnBitmap: Bitmap? = null
        var pendingReturnAnchor: TransitionAnchorFrame? = null
        var releaseReturnCover: (() -> Unit)? = null
        var returnStarted = false
        var coverAttached = false
        /**
         * The vendor path may return the same Bitmap instance from its own copy step and recycle it
         * when the request is rejected. Only this app-owned software copy is ever handed over, so a
         * failed handshake can never invalidate the payload bitmap that Legacy still needs.
         */
        fun recycleKitBitmap() {
            releaseReturnCover?.invoke()
            releaseReturnCover = null
            if (!coverAttached) pendingReturnAnchor?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            pendingReturnAnchor = null
            sourceView.release()
            if (!kitBitmap.isRecycled) kitBitmap.recycle()
            kitReturnBitmap?.let { if (!it.isRecycled) it.recycle() }
            kitReturnBitmap = null
        }
    }

    override suspend fun open(request: TransitionOpenRequest): TransitionBackendOpenResult {
        val anchor = request.payload?.openingAnchor
        if (anchor?.isValid != true || !anchor.cornerRadiusPx.isFinite() || anchor.cornerRadiusPx < 0f)
            return rejected("invalidAnchor")
        if (request.activity.isDestroyed || request.activity.isFinishing ||
            request.activity.isInMultiWindowMode || request.activity.isInPictureInPictureMode)
            return rejected("unsupportedWindow")
        if (movingSession != null) return rejected(AnimationInProgressReason)
        val capabilities = sdk("capability") {
            val protocol = api.supportedProtocol()
            if (com.xiaomanjun.sleepdownschedule.BuildConfig.SLEEPDOWN_SHARED_VIEW_KIT_TEST)
                Log.i("SharedViewKit", "route=${request.route.id.wireName} supportedProtocol=$protocol")
            if (protocol > 0) SharedViewKitCapabilities.from(api.extras()) else null
        }
            ?: return rejected("unsupportedProtocol")
        // Pilot routes must be able to return safely when their business source was removed.
        if (capabilities.missingTargetBackType == null) return rejected("safeReturnUnavailable")
        // Compose GraphicsLayer snapshots are Config#HARDWARE. The vendor implementation reads
        // pixels for the return mask color and rejects hardware bitmaps, so hand it an app-owned
        // software copy; the payload bitmap stays untouched for the Legacy fallback.
        val kitBitmap = anchor.bitmap?.softwareCopyForKit(anchor.cornerRadiusPx)
            ?: return rejected("softwareBitmapUnavailable")
        if (com.xiaomanjun.sleepdownschedule.BuildConfig.SLEEPDOWN_SHARED_VIEW_KIT_TEST)
            Log.i("SharedViewKit", "bitmap=${anchor.bitmap.config}->${kitBitmap.config} ${kitBitmap.width}x${kitBitmap.height}")
        val session = request.session
        val sourceView = SharedViewKitSourceView.attach(request.activity, anchor, kitBitmap)
        if (sourceView == null) {
            kitBitmap.recycle()
            return rejected("sourceViewUnavailable")
        }
        if (!session.moveTo(TransitionSessionState.NativeRegistered)) {
            sourceView.release()
            kitBitmap.recycle()
            return rejected("invalidSessionState")
        }
        val resource = Resource(session, WeakReference(request.activity), capabilities, session.nextCallbackGeneration(), kitBitmap, sourceView)
        sourceView.onNativeSourceRestored = {
            dispatch { if (current(resource) && resource.closing && resource.returnStarted) showReturnCover(resource) }
        }
        resources[session.id] = resource
        if (!TransitionPayloadStore.registerCleanup(session.id) { dispatch { release(session.id) } }) {
            release(session.id)
            return rejected("sessionReleased")
        }
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
            putFloat(SharedViewKitContract.Radius, sourceView.radiusPx)
            capabilities.fallbackType?.let { putInt(SharedViewKitContract.FallbackType, it) }
            putInt(SharedViewKitContract.Color, anchor.surfaceColor ?: transitionSurfaceColor(kitBitmap,
                request.payload?.backgroundColor ?: android.graphics.Color.TRANSPARENT))
        }
        val options = sdk("options") { api.options(sourceView, extras, callbacks) }
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
        val anchor = runCatching {
            TransitionPayloadStore.payload(session.id)?.returnAnchorProvider?.resolve()
        }.getOrNull()
        val bundle = Bundle().apply {
            if (source != null && anchor?.isValid == true) {
                putParcelable(SharedViewKitContract.Bounds, boundsForKit(source.window.decorView, anchor, resource.capabilities))
                putFloat(SharedViewKitContract.Radius, anchor.cornerRadiusPx)
                if (resource.capabilities.returnBitmap) {
                    anchor.bitmap?.softwareCopyForKit(anchor.cornerRadiusPx)?.let { bitmap ->
                        // The opening menu and the return FAB are different surfaces. Updating
                        // bounds alone leaves the SDK animating the opening menu's old pixels.
                        resource.kitReturnBitmap = bitmap
                        putParcelable(SharedViewKitContract.Bitmap, bitmap)
                    }
                }
            } else putInt(SharedViewKitContract.BackType, checkNotNull(resource.capabilities.missingTargetBackType))
            val pixels = resource.kitReturnBitmap ?: anchor?.bitmap
            putInt(SharedViewKitContract.Color, anchor?.surfaceColor ?: pixels?.let {
                transitionSurfaceColor(it, android.graphics.Color.TRANSPARENT)
            } ?: android.graphics.Color.TRANSPARENT)
        }
        val updated = sdk("return") { api.updateReturn(activity, bundle); true } == true
        if (!updated || !session.moveTo(TransitionSessionState.Closing)) {
            fallback(resource, "returnUpdateFailed")
            return false
        }
        resource.closing = true
        movingSession = session.id
        // Prepare pixels only. Do not draw a second button underneath the whole return animation.
        if (source != null && anchor?.isValid == true) {
            (resource.kitReturnBitmap ?: anchor.bitmap)?.softwareCopyForKit(anchor.cornerRadiusPx)?.let { bitmap ->
                resource.pendingReturnAnchor = anchor.copy(bitmap = bitmap)
            }
        }
        watch(resource)
        return true
    }

    private fun onStart(resource: Resource, mode: Int) {
        if (!current(resource)) return
        if (mode == SharedViewKitContract.Back && resource.closing) resource.returnStarted = true
        if (mode == SharedViewKitContract.Open && !resource.closing &&
            resource.session.moveFrom(TransitionSessionState.NativeRegistered, TransitionSessionState.NativeRunning)) {
            TransitionPayloadStore.handoffOpeningSource(resource.session.id)
            resource.sourceView.visibility = View.INVISIBLE
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
        // ROMs without a source-alpha signal get an end-only handoff, never an early cover.
        showReturnCover(resource)
        val source = resource.source.get()?.takeUnless { it.isDestroyed || it.isFinishing }
        val releaseReturnCover = resource.releaseReturnCover
        resource.releaseReturnCover = null
        TransitionPayloadStore.releaseSource(resource.session.id)
        releaseReturnCover?.let { if (source != null) ReturnCoverHandoff(source.window.decorView, it).start() else it() }
        resource.session.invalidateCallbacks()
        if (movingSession == resource.session.id) movingSession = null
        resource.session.moveTo(TransitionSessionState.Finished)
        TransitionPayloadStore.remove(resource.session.id)
    }

    private fun showReturnCover(resource: Resource) {
        if (resource.coverAttached) return
        val source = resource.source.get()?.takeUnless { it.isDestroyed || it.isFinishing } ?: return
        val anchor = resource.pendingReturnAnchor ?: return
        source.attachOpeningSourceSnapshotHandoff(anchor)?.let {
            resource.coverAttached = true
            resource.releaseReturnCover = it
        }
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
        val resource = resources.remove(id)
        resource?.let(::disarm)
        if (movingSession == id) {
            movingSession = null
            sdk("cancel") { api.cancel() }
        }
        resource?.recycleKitBitmap()
    }

    private fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    private inline fun <T> sdk(operation: String, block: () -> T): T? = try { block() }
        catch (error: RuntimeException) { Log.w("SharedViewKit", "$operation: ${error.javaClass.simpleName}"); null }
        catch (error: LinkageError) { Log.w("SharedViewKit", "$operation: ${error.javaClass.simpleName}"); null }

    private fun rejected(reason: String) = TransitionBackendOpenResult.Rejected(reason)

    companion object {
        internal const val CallbackTimeoutMillis = 3_000L
        /** A native leash is already moving; starting Legacy here would stack a second page. */
        internal const val AnimationInProgressReason = "animationInProgress"
    }
}

/** Read a few interior edge pixels once, never a GPU readback or a per-frame bitmap scan. */
internal fun transitionSurfaceColor(bitmap: Bitmap, fallback: Int): Int {
    if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return fallback
    val pixels = buildList {
        for (y in listOf(0.12f, 0.88f)) for (x in listOf(0.25f, 0.5f, 0.75f)) {
            val pixel = bitmap.getPixel((x * (bitmap.width - 1)).roundToInt(), (y * (bitmap.height - 1)).roundToInt())
            if (android.graphics.Color.alpha(pixel) >= 192) add(pixel)
        }
    }
    if (pixels.isEmpty()) return fallback
    fun median(component: (Int) -> Int) = pixels.map(component).sorted()[pixels.size / 2]
    return android.graphics.Color.rgb(median(android.graphics.Color::red), median(android.graphics.Color::green),
        median(android.graphics.Color::blue))
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

/**
 * Compose GraphicsLayer snapshots are Config#HARDWARE, which the vendor handshake rejects while
 * reading the return mask color. Always hand the Kit an independent ARGB_8888 copy: the payload
 * bitmap then stays valid for Legacy fallback even if the vendor recycles its own input.
 */
internal fun Bitmap.softwareCopyForKit(radiusPx: Float): Bitmap? = runCatching {
    if (isRecycled) return@runCatching null
    val software = copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
    val radius = radiusPx.coerceIn(0f, minOf(width, height) / 2f)
    if (radius == 0f) return@runCatching software
    try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rounded ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.BitmapShader(software, android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP)
            }
            android.graphics.Canvas(rounded).drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        }
    } finally { software.recycle() }
}.getOrNull()
