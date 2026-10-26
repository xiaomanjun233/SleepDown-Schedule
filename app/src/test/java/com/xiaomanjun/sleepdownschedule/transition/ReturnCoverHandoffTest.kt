package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ReturnCoverHandoffTest {
    @Test fun coverLeavesBeforeTheFirstSourceDrawInsteadOfOverlappingItForTwoFrames() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        val root = owner.get().window.decorView
        var releases = 0
        ReturnCoverHandoff(root) { releases++ }.start()
        assertEquals(0, releases)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, releases)
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ReturnCoverHandoff.MaximumWaitMillis))
        assertEquals(1, releases)
        owner.pause().stop().destroy()
    }

    @Test fun detachCleansUpTheBridgeWithoutWaitingForADraw() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        val root = owner.get().window.decorView
        var releases = 0
        val bridge = ReturnCoverHandoff(root) { releases++ }
        bridge.start()
        bridge.onViewDetachedFromWindow(root)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, releases)
        owner.pause().stop().destroy()
    }

    @Test fun aBackgroundedSourceCannotRetainAnUndrawnCover() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        var releases = 0
        ReturnCoverHandoff(owner.get().window.decorView) { releases++ }.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ReturnCoverHandoff.MaximumWaitMillis))
        assertEquals(1, releases)
        owner.pause().stop().destroy()
    }

    @Test fun unattachedSourceReleasesImmediatelyAndOnlyOnce() {
        var releases = 0
        val bridge = ReturnCoverHandoff(View(RuntimeEnvironment.getApplication())) { releases++ }
        bridge.start()
        bridge.run()
        assertEquals(1, releases)
    }
}
