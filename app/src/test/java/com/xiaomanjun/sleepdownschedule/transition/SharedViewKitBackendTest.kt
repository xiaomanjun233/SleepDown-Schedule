package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.Application
import android.app.ActivityOptions
import android.content.Intent
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.compose.ui.geometry.Rect as ComposeRect
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SharedViewKitBackendTest {
    private val api = FakeApi()
    private val backend = SharedViewKitBackend(api)
    private val source = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val anchor = TransitionAnchorFrame(ComposeRect(10f, 20f, 110f, 160f), 20f,
        Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888))
    private val handoffs = AtomicInteger()
    private val releases = AtomicInteger()
    private var returning: TransitionAnchorFrame? = anchor

    @After fun cleanup() { TransitionPayloadStore.clearForTests(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun unsupportedDevicesDoNotRegisterOrLaunch() = runBlocking {
        api.protocol = 0
        val request = request()
        assertTrue(backend.open(request) is TransitionBackendOpenResult.Rejected)
        assertEquals(0, api.optionsCalls)
        assertNull(shadowOf(source).nextStartedActivity)
        assertEquals(TransitionSessionState.SourceReady, request.session.currentState)
    }

    @Test fun vendorRecyclingRejectedInputCannotInvalidateLegacySnapshot() = runBlocking {
        api.provideOptions = false
        api.recycleRejectedInput = true
        assertTrue(backend.open(request()) is TransitionBackendOpenResult.Rejected)
        assertNotSame(anchor.bitmap, api.inputBitmap)
        assertTrue(api.inputBitmap!!.isRecycled)
        assertFalse(anchor.bitmap!!.isRecycled)
    }

    @Test fun concurrentOpenIsRejectedWithoutLaunchingAnotherDestination() = runBlocking {
        backend.open(request())
        val second = request()
        val result = backend.open(second) as TransitionBackendOpenResult.Rejected
        assertEquals(SharedViewKitBackend.AnimationInProgressReason, result.reason)
        assertEquals(1, api.optionsCalls)
        assertNotNull(shadowOf(source).nextStartedActivity)
        assertNull(shadowOf(source).nextStartedActivity)
    }

    @Test fun invalidSessionReleasesItsPrivateBitmapBeforeSdkOptions() = runBlocking {
        val request = request()
        request.session.moveTo(TransitionSessionState.Cancelled)
        assertTrue(backend.open(request) is TransitionBackendOpenResult.Rejected)
        assertEquals(0, api.optionsCalls)
        assertFalse(anchor.bitmap!!.isRecycled)
    }

    @Test fun nullOptionsLeaveLegacyAFreeSourceAndDoNotCancelOtherAnimations() = runBlocking {
        api.provideOptions = false
        val request = request()
        assertTrue(backend.open(request) is TransitionBackendOpenResult.Rejected)
        assertEquals(0, api.cancels)
        api.callbacks!!.onStart(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, handoffs.get())
        assertNull(shadowOf(source).nextStartedActivity)
    }

    @Test fun openingAndReturningUseOneSessionAndFreshBusinessBounds() = runBlocking {
        val request = request()
        assertEquals(TransitionBackendOpenResult.Started, backend.open(request))
        val callbacks = api.callbacks!!
        callbacks.onStart(SharedViewKitContract.Open)
        callbacks.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, handoffs.get())
        assertEquals(TransitionSessionState.Open, request.session.currentState)
        returning = anchor.copy(boundsInWindow = ComposeRect(30f, 40f, 130f, 180f))
        val destination = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(backend.requestClose(destination, request.session))
        @Suppress("DEPRECATION")
        assertEquals(Rect(30, 40, 130, 180), api.returnBundle!!.getParcelable<Rect>(SharedViewKitContract.Bounds))
        assertSame(destination, api.returnActivity)
        assertEquals(1, releases.get())
        // A delayed OPEN completion cannot finish or reopen a closing session.
        callbacks.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.Closing, request.session.currentState)
        callbacks.onEnd(SharedViewKitContract.Back)
        callbacks.onEnd(SharedViewKitContract.Back)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(TransitionPayloadStore.session(request.session.id))
        assertEquals(1, releases.get())
        assertEquals(0, api.cancels)
        assertNotNull(shadowOf(source).nextStartedActivity)
        assertNull(shadowOf(source).nextStartedActivity)
    }

    @Test fun deletedReturnTargetUsesTheDeclaredSafeReturn() = runBlocking {
        val request = openAndSettle()
        returning = null
        assertTrue(backend.requestClose(source, request.session))
        assertEquals(2, api.returnBundle!!.getInt(SharedViewKitContract.BackType))
        assertFalse(api.returnBundle!!.containsKey(SharedViewKitContract.Bounds))
    }

    @Test fun missingOpenCallbackFallsBackInsideTheAlreadyStartedDestination() = runBlocking {
        val request = request()
        backend.open(request)
        val stale = api.callbacks!!
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SharedViewKitBackend.CallbackTimeoutMillis))
        assertEquals(TransitionSessionState.LegacyRunning, request.session.currentState)
        assertEquals(1, api.cancels)
        assertEquals(1, handoffs.get())
        stale.onStart(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.LegacyRunning, request.session.currentState)
        assertNotNull(shadowOf(source).nextStartedActivity)
        assertNull(shadowOf(source).nextStartedActivity)
    }

    @Test fun quickBackBeforeNativeStartLetsLegacyCloseAndIgnoresLateCallbacks() = runBlocking {
        val request = request()
        backend.open(request)
        assertFalse(backend.requestClose(source, request.session))
        api.callbacks!!.onStart(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.LegacyRunning, request.session.currentState)
        assertEquals(1, api.cancels)
    }

    @Test fun missingCloseCallbackReleasesTheSessionWithABoundedWatchdog() = runBlocking {
        val request = openAndSettle()
        backend.requestClose(source, request.session)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SharedViewKitBackend.CallbackTimeoutMillis))
        assertNull(TransitionPayloadStore.session(request.session.id))
        assertEquals(1, api.cancels)
        assertEquals(1, releases.get())
    }

    @Test fun delayedOpenCancellationCannotDisturbAnAlreadySettledPage() = runBlocking {
        val request = openAndSettle()
        api.callbacks!!.onCancel(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.Open, request.session.currentState)
        assertEquals(0, api.cancels)
    }

    @Test fun destroyingAnOlderSourceCannotCancelAnotherSessionsAnimation() = runBlocking {
        val first = openAndSettle()
        val otherSource = Robolectric.buildActivity(Activity::class.java).setup().get()
        val second = request(otherSource)
        backend.open(second)
        backend.onActivityDestroyed(source)
        assertNull(TransitionPayloadStore.session(first.session.id))
        assertEquals(0, api.cancels)
        api.callbacks!!.onStart(SharedViewKitContract.Open)
        api.callbacks!!.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.Open, second.session.currentState)
    }

    @Test fun screenCoordinatesUseTheWindowOffsetWhileWindowCoordinatesDoNot() {
        val view = object : View(source) {
            override fun getLocationOnScreen(outLocation: IntArray) { outLocation[0] = 130; outLocation[1] = 240 }
            override fun getLocationInWindow(outLocation: IntArray) { outLocation[0] = 30; outLocation[1] = 40 }
        }
        assertEquals(Rect(110, 220, 210, 360), boundsForKit(view, anchor, SharedViewKitCapabilities(false, 2, null)))
        assertEquals(Rect(10, 20, 110, 160), boundsForKit(view, anchor, SharedViewKitCapabilities(true, 2, null)))
    }

    @Test fun oldActivityNamesResolveThroughAliasesWithoutTheRetiredImplementation() {
        val targets = mapOf(
            "OplusCourseManagementActivity" to "CourseManagementActivity",
            "OplusCourseManagementDetailActivity" to "NativeCourseManagementDetailActivity",
            "OplusAiImportHistoryActivity" to "AiImportHistoryActivity",
            "OplusEduSchoolSelectActivity" to "EduSchoolSelectActivity")
        targets.forEach { (oldName, target) ->
            val info = source.packageManager.getActivityInfo(ComponentName(source.packageName,
                "com.xiaomanjun.sleepdownschedule.$oldName"), 0)
            assertEquals("com.xiaomanjun.sleepdownschedule.$target", info.targetActivity)
            assertFalse(info.exported)
        }
    }

    private suspend fun openAndSettle(): TransitionOpenRequest = request().also {
        backend.open(it)
        api.callbacks!!.onStart(SharedViewKitContract.Open)
        api.callbacks!!.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun request(activity: Activity = source): TransitionOpenRequest {
        val payload = TransitionPayload(anchor, TransitionAnchorProvider { returning },
            onOpeningSourceHandoff = { handoffs.incrementAndGet() }, onSourceReleased = { releases.incrementAndGet() })
        val session = TransitionPayloadStore.create(TransitionRouteId.CourseManagementToDetail, payload)
        session.moveTo(TransitionSessionState.SourceReady)
        return TransitionOpenRequest(activity, Intent(activity, Activity::class.java).putTransitionIdentity(session),
            TransitionRouteCatalog.get(session.routeId), session, payload)
    }

    private class FakeApi : SharedViewKitApi {
        var protocol = 100
        var provideOptions = true
        var optionsCalls = 0
        var callbacks: SharedViewKitCallbacks? = null
        var cancels = 0
        var returnBundle: Bundle? = null
        var returnActivity: Activity? = null
        var inputBitmap: Bitmap? = null
        var recycleRejectedInput = false
        override fun supportedProtocol() = protocol
        override fun extras() = mapOf(SharedViewKitContract.Bounds to "rect|WINDOW|bounds",
            SharedViewKitContract.BackType to "int|VIEW_ANIM_FOLLOW_SYSTEM,VIEW_ANIM_FADE_OUT|back")
        override fun options(bitmap: Bitmap, bounds: Rect, radiusPx: Float, extras: Bundle,
            callbacks: SharedViewKitCallbacks): ActivityOptions? {
            optionsCalls++
            inputBitmap = bitmap
            if (!provideOptions && recycleRejectedInput) bitmap.recycle()
            this.callbacks = callbacks
            return if (provideOptions) ActivityOptions.makeBasic() else null
        }
        override fun updateReturn(activity: Activity, extras: Bundle) { returnActivity = activity; returnBundle = extras }
        override fun cancel() { cancels++ }
    }
}
