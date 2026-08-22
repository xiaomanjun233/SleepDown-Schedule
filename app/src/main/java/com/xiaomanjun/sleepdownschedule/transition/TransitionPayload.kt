package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import androidx.compose.ui.geometry.Rect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class TransitionAnchorFrame(
    val boundsInWindow: Rect,
    val cornerRadiusPx: Float,
    val bitmap: Bitmap?
) {
    val isValid: Boolean
        get() = boundsInWindow.width > 1f && boundsInWindow.height > 1f &&
            bitmap != null && !bitmap.isRecycled
}

/**
 * Keeps the exact source pixels visible while native registration waits for an attached/layout
 * frame. Ownership is handed off exactly once by either Oplus' OPEN callback or the Legacy backend.
 *
 * TODO(OPLUS_DEFERRED_20260823): Retained as investigation evidence. The signed PLJ110 acceptance
 * build still flashed a blank frame on the AI-history OPEN routes with this handoff installed.
 * Do not treat this helper as a verified fix or extend it to more routes until that investigation
 * is explicitly resumed.
 */
internal fun Activity.attachOpeningSourceSnapshotHandoff(
    anchor: TransitionAnchorFrame
): (() -> Unit)? {
    if (!anchor.isValid) return null
    val bitmap = anchor.bitmap ?: return null
    val decor = window.decorView
    val overlay = decor.overlay
    val bounds = anchor.boundsInWindow
    val drawable = BitmapDrawable(resources, bitmap).apply {
        setBounds(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt()
        )
    }
    val released = AtomicBoolean(false)
    val release: () -> Unit = {
        if (released.compareAndSet(false, true)) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                overlay.remove(drawable)
            } else {
                decor.post { overlay.remove(drawable) }
            }
        }
    }
    return runCatching {
        overlay.add(drawable)
        release
    }.getOrNull()
}

fun interface TransitionAnchorProvider {
    fun resolve(): TransitionAnchorFrame?
}

/** Resolves the real, layout-owned Android View used by a native transition backend. */
fun interface TransitionNativeSourceViewProvider {
    fun resolve(): View?
}

class StaticTransitionAnchorProvider(
    private val anchor: TransitionAnchorFrame?
) : TransitionAnchorProvider {
    override fun resolve(): TransitionAnchorFrame? = anchor
}

data class TransitionPayload(
    val openingAnchor: TransitionAnchorFrame?,
    val returnAnchorProvider: TransitionAnchorProvider? =
        openingAnchor?.let(::StaticTransitionAnchorProvider),
    val nativeSourceViewProvider: TransitionNativeSourceViewProvider? = null,
    val backgroundBitmap: Bitmap? = null,
    val parentSessionId: TransitionSessionId? = null,
    /**
     * Keeps a source-owned visual handoff alive until a renderer has actually taken ownership.
     * Native invokes it from the matching OPEN start callback; Legacy invokes it immediately
     * before installing its own validated placeholder. The store guarantees exactly-once release.
     */
    val onOpeningSourceHandoff: (() -> Unit)? = null,
    /**
     * Requests ColorOS to alpha the source Activity leash once its native OPEN animation starts.
     * This is needed only when [onOpeningSourceHandoff] preserves visible source pixels through
     * asynchronous registration; ordinary routes retain the system default.
     */
    val nativeSourceLeashAlphaOutOnOpen: Boolean = false,
    /**
     * Restores the real business-owned source after the session's renderer/bridge has released it.
     * It is session scoped and runs exactly once for native completion, Legacy completion or
     * cancellation, so source Activities never have to guess from an early onResume callback.
     */
    val onSourceReleased: (() -> Unit)? = null
)

private class CleanupHandle(private val cleanup: () -> Unit) {
    private val executed = AtomicBoolean(false)

    fun runOnce() {
        if (executed.compareAndSet(false, true)) runCatching(cleanup)
    }
}

private class StoredTransitionPayload(
    val session: TransitionSession,
    val payload: TransitionPayload?
) {
    private val closed = AtomicBoolean(false)
    private val cleanup = CopyOnWriteArrayList<CleanupHandle>()
    private val openingSourceHandoff = payload?.onOpeningSourceHandoff?.let(::CleanupHandle)
    private val sourceRelease = payload?.onSourceReleased?.let(::CleanupHandle)

    fun register(action: () -> Unit): Boolean {
        val handle = CleanupHandle(action)
        if (closed.get()) {
            handle.runOnce()
            return false
        }
        cleanup += handle
        if (closed.get()) {
            cleanup.remove(handle)
            handle.runOnce()
            return false
        }
        return true
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        openingSourceHandoff?.runOnce()
        sourceRelease?.runOnce()
        cleanup.forEach(CleanupHandle::runOnce)
        cleanup.clear()
    }

    fun handoffOpeningSource() {
        openingSourceHandoff?.runOnce()
    }

    fun releaseSource() {
        sourceRelease?.runOnce()
    }
}

/** Process-local ownership for transient bitmaps, anchors and backend resources. */
object TransitionPayloadStore {
    private const val MaximumRetainedSessions = 24
    private val entries = ConcurrentHashMap<TransitionSessionId, StoredTransitionPayload>()

    fun create(
        routeId: TransitionRouteId,
        payload: TransitionPayload?,
        parentSessionId: TransitionSessionId? = payload?.parentSessionId
    ): TransitionSession {
        val session = TransitionSession(TransitionSessionId.create(), routeId, parentSessionId)
        val stored = StoredTransitionPayload(session, payload)
        entries[session.id] = stored
        trimTerminalEntries()
        return session
    }

    fun session(id: TransitionSessionId?): TransitionSession? = id?.let { entries[it]?.session }

    fun payload(id: TransitionSessionId?): TransitionPayload? = id?.let { entries[it]?.payload }

    fun registerCleanup(id: TransitionSessionId, cleanup: () -> Unit): Boolean {
        val entry = entries[id]
        if (entry == null) {
            runCatching(cleanup)
            return false
        }
        return entry.register(cleanup)
    }

    fun handoffOpeningSource(id: TransitionSessionId) {
        entries[id]?.handoffOpeningSource()
    }

    /** Restores business-owned source content without disposing the native frame-sync bridge. */
    fun releaseSource(id: TransitionSessionId) {
        entries[id]?.releaseSource()
    }

    fun remove(id: TransitionSessionId?) {
        val entry = id?.let(entries::remove) ?: return
        entry.close()
    }

    internal fun clearForTests() {
        entries.keys.toList().forEach(::remove)
    }

    private fun trimTerminalEntries() {
        if (entries.size <= MaximumRetainedSessions) return
        entries.values
            .filter { it.session.currentState.terminal }
            .take(entries.size - MaximumRetainedSessions)
            .forEach { remove(it.session.id) }
    }
}
