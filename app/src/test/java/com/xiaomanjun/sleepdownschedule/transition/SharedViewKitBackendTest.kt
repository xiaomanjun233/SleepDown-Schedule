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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test fun sourceViewHasFullBoundsAndOutlineAndLivesUntilSessionRelease() = runBlocking {
        val request = request()
        assertEquals(TransitionBackendOpenResult.Started, backend.open(request))
        val view = checkNotNull(api.inputView)
        assertEquals(100, view.width)
        assertEquals(140, view.height)
        assertEquals(10, view.left)
        assertEquals(20, view.top)
        assertEquals(20f, api.openBundle!!.getFloat(SharedViewKitContract.Radius), 0f)
        api.callbacks!!.onStart(SharedViewKitContract.Open)
        api.callbacks!!.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.isAttachedToWindow)
        assertEquals(View.INVISIBLE, view.visibility)
        assertTrue(backend.requestClose(source, request.session))
        api.callbacks!!.onEnd(SharedViewKitContract.Back)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(view.isAttachedToWindow)
        assertFalse(anchor.bitmap!!.isRecycled)
    }

    @Test fun surfaceColorUsesTheCardRatherThanTheBlackActivityBackground() {
        val pixels = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(54, 59, 63))
        }
        assertEquals(android.graphics.Color.rgb(54, 59, 63), transitionSurfaceColor(pixels, android.graphics.Color.BLACK))
        pixels.eraseColor(android.graphics.Color.TRANSPARENT)
        assertEquals(android.graphics.Color.BLACK, transitionSurfaceColor(pixels, android.graphics.Color.BLACK))
        pixels.recycle()
    }

    @Test fun explicitCardFillWinsOverColoredCapsulePixelsOnOpenAndReturn() = runBlocking {
        anchor.bitmap!!.eraseColor(android.graphics.Color.GREEN)
        val cardFill = android.graphics.Color.rgb(43, 44, 46)
        val base = request()
        val opening = base.copy(payload = base.payload!!.copy(openingAnchor = anchor.copy(surfaceColor = cardFill)))
        assertEquals(TransitionBackendOpenResult.Started, backend.open(opening))
        assertEquals(cardFill, api.openBundle!!.getInt(SharedViewKitContract.Color))
        api.callbacks!!.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        val changedThemeFill = android.graphics.Color.rgb(240, 241, 243)
        returning = anchor.copy(surfaceColor = changedThemeFill)
        assertTrue(backend.requestClose(source, base.session))
        assertEquals(changedThemeFill, api.returnBundle!!.getInt(SharedViewKitContract.Color))
    }

    @Test fun unsupportedDevicesDoNotRegisterOrLaunch() = runBlocking {
        api.protocol = 0
        val request = request()
        assertTrue(backend.open(request) is TransitionBackendOpenResult.Rejected)
        assertEquals(0, api.optionsCalls)
        assertNull(shadowOf(source).nextStartedActivity)
        assertEquals(TransitionSessionState.SourceReady, request.session.currentState)
    }

    @Test fun specialCourseRouteUsesNativeReturnAndSafelyHandlesADeletedSource() = runBlocking {
        val request = request(routeId = TransitionRouteId.SpecialCourseToEditor)
        assertTrue(request.route.legacyProfile is LegacyTransitionProfile.Depth)
        assertEquals(TransitionBackendOpenResult.Started, backend.open(request))
        api.callbacks!!.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TransitionSessionState.Open, request.session.currentState)
        returning = null
        val destination = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(backend.requestClose(destination, request.session))
        assertFalse(api.returnBundle!!.containsKey(SharedViewKitContract.Bounds))
        assertEquals(2, api.returnBundle!!.getInt(SharedViewKitContract.BackType))
        assertEquals(0, releases.get())
        api.callbacks!!.onEnd(SharedViewKitContract.Back)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, handoffs.get())
        assertEquals(1, releases.get())
        assertNull(TransitionPayloadStore.session(request.session.id))
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
        assertFalse(api.returnBundle!!.containsKey(SharedViewKitContract.Bitmap))
        assertSame(destination, api.returnActivity)
        // The real source stays hidden for the whole return animation; restoring it here would
        // show the business button underneath the card the ROM is still flying back.
        assertEquals(0, releases.get())
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

    @Test fun landingCoverIsDrawnBeforeBackEndAndRetiresAfterTheRestoredFrame() = runBlocking {
        val request = openAndSettle()
        returning = anchor.copy(bitmap = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
        })
        val destination = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(backend.requestClose(destination, request.session))
        fun sourcePixel(): Int {
            val root = source.window.decorView
            val capture = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(capture))
            return capture.getPixel(60, 90).also { capture.recycle() }
        }
        assertEquals(android.graphics.Color.CYAN, sourcePixel())
        assertEquals(0, releases.get())
        api.callbacks!!.onEnd(SharedViewKitContract.Back)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        source.window.decorView.viewTreeObserver.dispatchOnPreDraw()
        assertNotEquals(android.graphics.Color.CYAN, sourcePixel())
        assertEquals(1, releases.get())
    }

    @Test fun sourceDestructionAlsoRemovesThePrearmedLandingCover() = runBlocking {
        val request = openAndSettle()
        assertTrue(backend.requestClose(Robolectric.buildActivity(Activity::class.java).setup().get(), request.session))
        backend.onActivityDestroyed(source)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(TransitionPayloadStore.session(request.session.id))
        assertEquals(1, releases.get())
    }

    @Test fun declaredReturnBitmapReplacesOpeningPixelsAndStaysAliveUntilBackCompletes() = runBlocking {
        api.supportsReturnBitmap = true
        val request = request()
        assertEquals(TransitionBackendOpenResult.Started, backend.open(request))
        val callbacks = api.callbacks!!
        callbacks.onEnd(SharedViewKitContract.Open)
        shadowOf(Looper.getMainLooper()).idle()
        val button = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
        }
        returning = TransitionAnchorFrame(ComposeRect(30f, 40f, 94f, 104f), 32f, button)
        val destination = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(backend.requestClose(destination, request.session))
        @Suppress("DEPRECATION")
        val passed = api.returnBundle!!.getParcelable<Bitmap>(SharedViewKitContract.Bitmap)!!
        assertNotSame(button, passed)
        assertNotSame(api.inputBitmap, passed)
        assertEquals(64, passed.width)
        assertEquals(android.graphics.Color.CYAN, passed.getPixel(32, 32))
        assertFalse(passed.isRecycled)
        callbacks.onEnd(SharedViewKitContract.Back)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(passed.isRecycled)
        assertFalse(button.isRecycled)
        assertFalse(anchor.bitmap!!.isRecycled)
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

    private fun request(activity: Activity = source, routeId: TransitionRouteId = TransitionRouteId.CourseManagementToDetail): TransitionOpenRequest {
        val payload = TransitionPayload(anchor, TransitionAnchorProvider { returning },
            onOpeningSourceHandoff = { handoffs.incrementAndGet() }, onSourceReleased = { releases.incrementAndGet() })
        val session = TransitionPayloadStore.create(routeId, payload)
        session.moveTo(TransitionSessionState.SourceReady)
        return TransitionOpenRequest(activity, Intent(activity, Activity::class.java).putTransitionIdentity(session),
            TransitionRouteCatalog.get(session.routeId), session, payload)
    }

    private class FakeApi : SharedViewKitApi {
        var supportsReturnBitmap = false
        var protocol = 100
        var provideOptions = true
        var optionsCalls = 0
        var callbacks: SharedViewKitCallbacks? = null
        var cancels = 0
        var returnBundle: Bundle? = null
        var returnActivity: Activity? = null
        var inputBitmap: Bitmap? = null
        var inputView: SharedViewKitSourceView? = null
        var openBundle: Bundle? = null
        var recycleRejectedInput = false
        override fun supportedProtocol() = protocol
        override fun extras() = mapOf(SharedViewKitContract.Bounds to "rect|WINDOW|bounds",
            SharedViewKitContract.BackType to "int|VIEW_ANIM_FOLLOW_SYSTEM,VIEW_ANIM_FADE_OUT|back") +
            if (supportsReturnBitmap) mapOf(SharedViewKitContract.Bitmap to "Bitmap||return pixels") else emptyMap()
        override fun options(source: View, extras: Bundle,
            callbacks: SharedViewKitCallbacks): ActivityOptions? {
            val view = source as SharedViewKitSourceView
            inputView = view
            val bitmap = view.bitmap
            assertTrue(view.isAttachedToWindow)
            assertTrue(view.clipToOutline)
            val outline = android.graphics.Outline().also { view.outlineProvider.getOutline(view, it) }
            assertEquals(view.radiusPx, outline.radius, 0f)
            optionsCalls++
            inputBitmap = bitmap
            openBundle = Bundle(extras)
            if (!provideOptions && recycleRejectedInput) bitmap.recycle()
            this.callbacks = callbacks
            return if (provideOptions) ActivityOptions.makeBasic() else null
        }
        override fun updateReturn(activity: Activity, extras: Bundle) { returnActivity = activity; returnBundle = extras }
        override fun cancel() { cancels++ }
    }
}
