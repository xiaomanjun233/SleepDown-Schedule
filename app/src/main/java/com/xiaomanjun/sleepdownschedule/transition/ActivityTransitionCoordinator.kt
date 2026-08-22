package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import com.xiaomanjun.sleepdownschedule.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Unified control plane. Existing Morph/depth renderers remain the visual implementation. */
object ActivityTransitionCoordinator {
    private const val LogTag = "ActivityTransition"
    private val installed = AtomicBoolean(false)
    private val legacyBackend = LegacyTransitionBackend()
    private val oplusBackend = OplusSeamlessBackend()

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    oplusBackend.onSourceActivityResumed(activity)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (activity.isFinishing) {
                        oplusBackend.onActivityDestroyed(activity)
                        val id = activity.intent.transitionSessionIdOrNull()
                        val session = TransitionPayloadStore.session(id)
                        if (session != null &&
                            !(session.hasReachedNativeRunning &&
                                session.currentState == TransitionSessionState.Closing)
                        ) {
                            session.moveTo(TransitionSessionState.Cancelled)
                            TransitionPayloadStore.remove(id)
                        }
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            }
        )
    }

    suspend fun open(
        activity: Activity,
        routeId: TransitionRouteId,
        intent: android.content.Intent,
        payload: TransitionPayload? = null
    ): TransitionLaunchResult = openInternal(
        activity = activity,
        routeId = routeId,
        intent = intent,
        payload = payload,
        forceNativeForDebug = false,
        allowDebugDestinationOverride = false
    )

    /** Debug-source-set bisection hook; never callable in a release build. */
    internal suspend fun openDebugNativeCandidate(
        activity: Activity,
        routeId: TransitionRouteId,
        intent: android.content.Intent,
        payload: TransitionPayload
    ): TransitionLaunchResult {
        check(BuildConfig.DEBUG) { "Debug transition harness is unavailable in release builds" }
        return openInternal(
            activity = activity,
            routeId = routeId,
            intent = intent,
            payload = payload,
            forceNativeForDebug = true,
            allowDebugDestinationOverride = true
        )
    }

    private suspend fun openInternal(
        activity: Activity,
        routeId: TransitionRouteId,
        intent: android.content.Intent,
        payload: TransitionPayload?,
        forceNativeForDebug: Boolean,
        allowDebugDestinationOverride: Boolean
    ): TransitionLaunchResult = withContext(Dispatchers.Main.immediate) {
        val route = TransitionRouteCatalog.get(routeId)
        validateDestination(route, intent)
            ?.takeUnless { allowDebugDestinationOverride }
            ?.let { reason ->
            val failed = TransitionPayloadStore.create(routeId, payload)
            failed.moveTo(TransitionSessionState.Failed)
            log(failed, "coordinator", "rejected", reason)
            TransitionPayloadStore.remove(failed.id)
            return@withContext TransitionLaunchResult.Failed(failed.id, reason)
        }
        val parentId = payload?.parentSessionId ?: activity.activeTransitionSessionIdOrNull()
        val normalizedPayload = payload?.copy(parentSessionId = parentId)
        val session = TransitionPayloadStore.create(routeId, normalizedPayload, parentId)
        intent.putTransitionIdentity(session)
        val legacyRequest = TransitionOpenRequest(activity, intent, route, session, normalizedPayload)
        val returnAnchorReady = if (!route.requiresReturnAnchor) {
            true
        } else {
            runCatching { normalizedPayload?.returnAnchorProvider?.resolve()?.isValid == true }
                .getOrDefault(false)
        }
        val nativeDestination = route.nativeDestinationClassName
        val nativeEligible = (forceNativeForDebug || TransitionFeatureGate.allowsOplus(route)) &&
            (!route.requiresOpeningAnchor || normalizedPayload?.openingAnchor?.isValid == true) &&
            returnAnchorReady &&
            (allowDebugDestinationOverride || nativeDestination != null)
        if (nativeEligible) {
            session.moveTo(TransitionSessionState.SourceReady)
            val nativeIntent = if (allowDebugDestinationOverride) {
                android.content.Intent(intent)
            } else {
                android.content.Intent(intent).setClassName(
                    activity.packageName,
                    checkNotNull(nativeDestination)
                )
            }.also { it.putTransitionIdentity(session) }
            val nativeRequest = legacyRequest.copy(intent = nativeIntent)
            when (val native = oplusBackend.open(nativeRequest)) {
                TransitionBackendOpenResult.Started -> {
                    return@withContext TransitionLaunchResult.NativeRegistered(session.id)
                }
                is TransitionBackendOpenResult.Rejected -> {
                    log(session, oplusBackend.name, "fallback", native.reason)
                }
            }
        }
        launchLegacy(legacyRequest)
    }

    /** Synchronous entry for routes whose catalog policy can never select a native backend. */
    fun openImmediate(
        activity: Activity,
        routeId: TransitionRouteId,
        intent: android.content.Intent
    ): TransitionLaunchResult {
        val route = TransitionRouteCatalog.get(routeId)
        require(route.nativePolicy == TransitionNativePolicy.Never) {
            "Native-capable route $routeId must use suspend open()"
        }
        val session = TransitionPayloadStore.create(
            routeId = routeId,
            payload = null,
            parentSessionId = activity.activeTransitionSessionIdOrNull()
        )
        intent.putTransitionIdentity(session)
        val request = TransitionOpenRequest(activity, intent, route, session, null)
        session.moveTo(TransitionSessionState.LegacyRunning)
        return when (val result = legacyBackend.openImmediate(request)) {
            TransitionBackendOpenResult.Started -> {
                session.moveTo(TransitionSessionState.Open)
                TransitionPayloadStore.remove(session.id)
                TransitionLaunchResult.LegacyStarted(session.id)
            }
            is TransitionBackendOpenResult.Rejected -> {
                session.moveTo(TransitionSessionState.Failed)
                TransitionPayloadStore.remove(session.id)
                TransitionLaunchResult.Failed(session.id, result.reason)
            }
        }
    }

    fun requestNativeClose(activity: Activity, sessionId: TransitionSessionId?): Boolean {
        val session = TransitionPayloadStore.session(sessionId) ?: return false
        return oplusBackend.requestClose(activity, session)
    }

    fun beginLegacyClose(sessionId: TransitionSessionId?) {
        TransitionPayloadStore.session(sessionId)?.moveTo(TransitionSessionState.Closing)
    }

    fun markOpen(sessionId: TransitionSessionId?) {
        TransitionPayloadStore.session(sessionId)?.moveTo(TransitionSessionState.Open)
    }

    fun finishAfterLegacy(activity: Activity, sessionId: TransitionSessionId?) {
        TransitionPayloadStore.session(sessionId)?.moveTo(TransitionSessionState.Finished)
        TransitionPayloadStore.remove(sessionId)
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    /** Called before super.onCreate so the destination can recognize its matching native session. */
    fun prepareDestinationBeforeOnCreate(activity: Activity): Boolean {
        val session = TransitionPayloadStore.session(activity.intent.transitionSessionIdOrNull())
            ?: return false
        val route = TransitionRouteCatalog.get(session.routeId)
        val candidate = route.destinationWindowPolicy ==
            TransitionDestinationWindowPolicy.OpaqueNativeCandidate &&
            session.currentState in setOf(
                TransitionSessionState.NativeRegistered,
                TransitionSessionState.NativeRunning
            )
        return candidate
    }

    /** Installs the captured page background before Compose draws the first native-candidate frame. */
    fun installDestinationWindowBackground(activity: Activity) {
        val id = activity.intent.transitionSessionIdOrNull()
        val session = TransitionPayloadStore.session(id) ?: return
        if (session.currentState !in setOf(
                TransitionSessionState.NativeRegistered,
                TransitionSessionState.NativeRunning,
                TransitionSessionState.Open,
                TransitionSessionState.LegacyRunning
            )
        ) return
        val payload = TransitionPayloadStore.payload(id) ?: return
        val background = payload.backgroundBitmap ?: return
        activity.window.setBackgroundDrawable(BitmapDrawable(activity.resources, background))
    }

    private suspend fun launchLegacy(request: TransitionOpenRequest): TransitionLaunchResult {
        if (!request.session.moveTo(TransitionSessionState.LegacyRunning)) {
            request.session.moveTo(TransitionSessionState.Failed)
            TransitionPayloadStore.remove(request.session.id)
            return TransitionLaunchResult.Failed(request.session.id, "invalidLegacyState")
        }
        return when (val result = legacyBackend.open(request)) {
            TransitionBackendOpenResult.Started -> {
                log(request.session, legacyBackend.name, "started", "selected")
                TransitionLaunchResult.LegacyStarted(request.session.id)
            }
            is TransitionBackendOpenResult.Rejected -> {
                request.session.moveTo(TransitionSessionState.Failed)
                TransitionPayloadStore.remove(request.session.id)
                TransitionLaunchResult.Failed(request.session.id, result.reason)
            }
        }
    }

    private fun validateDestination(
        route: TransitionRouteSpec,
        intent: android.content.Intent
    ): String? {
        val actual = intent.component?.className ?: return "implicitInternalIntent"
        return if (actual == route.destinationClassName) null
        else "destinationMismatch:$actual"
    }

    private fun log(
        session: TransitionSession,
        backend: String,
        event: String,
        reason: String
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            LogTag,
            "route=${session.routeId.wireName} session=${session.id.value} " +
                "backend=$backend state=${session.currentState} event=$event reason=$reason"
        )
    }
}

/** Context-safe convenience for catalog routes that are guaranteed to use a non-native backend. */
fun Context.openRegisteredActivity(
    routeId: TransitionRouteId,
    intent: android.content.Intent
): TransitionLaunchResult? {
    val activity = findTransitionActivity()
    if (activity == null) {
        startActivity(intent)
        return null
    }
    return ActivityTransitionCoordinator.openImmediate(activity, routeId, intent)
}

private tailrec fun Context.findTransitionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findTransitionActivity()
    else -> null
}

private fun Activity.activeTransitionSessionIdOrNull(): TransitionSessionId? =
    intent.transitionSessionIdOrNull()?.takeIf { TransitionPayloadStore.session(it) != null }
