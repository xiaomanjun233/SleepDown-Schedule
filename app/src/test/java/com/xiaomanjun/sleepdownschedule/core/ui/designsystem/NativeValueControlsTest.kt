package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.ui.Modifier
import org.junit.Assert.*
import org.junit.Test

class NativeValueControlsTest {
    @Test fun clickSpringPreviewsIntermediateValuesWithoutSnappingOrCommittingEachFrame() {
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        val nativeUpdates = mutableListOf<Int>()
        val request = NativeSliderRequest({ 30f }, 0f..100f, previews::add, {}, commits::add,
            Modifier, snapValue = 50f)
        val session = NativeSliderSession { request }
        val adapter = NativeSliderProgressAdapter({ request }, session, nativeUpdates::add)
        session.start()
        listOf(4000, 4900, 5100, 6500).forEach { adapter.changed(it, true, snapToDefault = false) }
        assertEquals(listOf(40f, 49f, 51f, 65f), previews)
        assertTrue(nativeUpdates.isEmpty())
        assertTrue(commits.isEmpty())
        adapter.changed(7000, true)
        session.finish(false)
        assertEquals(listOf(70f), commits)
    }

    @Test fun dragSnapsNativeThumbAndPreviewThenCanLeaveTheSnapZone() {
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        val nativeUpdates = mutableListOf<Int>()
        val request = NativeSliderRequest({ 30f }, 0f..100f, previews::add, {}, commits::add,
            Modifier, snapValue = 50f)
        val session = NativeSliderSession { request }
        lateinit var adapter: NativeSliderProgressAdapter
        adapter = NativeSliderProgressAdapter({ request }, session) {
            nativeUpdates += it
            adapter.changed(it, false) // COUI setProgress synchronously echoes a programmatic change.
        }
        session.start()
        adapter.changed(4910, true)
        assertEquals(listOf(5000), nativeUpdates)
        assertEquals(listOf(50f), previews)
        adapter.changed(5400, true)
        assertEquals(54f, previews.last(), 0.0001f)
        adapter.changed(5080, true)
        session.finish(false)
        assertEquals(listOf(50f), commits)
        assertEquals(listOf(5000, 5000), nativeUpdates)
    }

    @Test fun snapCancellationRestoresPreviewAndKeyboardCanStepAway() {
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        val request = NativeSliderRequest({ 30f }, 0f..100f, previews::add, {}, commits::add,
            Modifier, snapValue = 50f)
        val session = NativeSliderSession { request }
        val adapter = NativeSliderProgressAdapter({ request }, session) {}
        session.start()
        adapter.changed(5050, true)
        session.finish(true)
        assertEquals(listOf(50f, 30f), previews)
        assertTrue(commits.isEmpty())
        adapter.changed(5100, true)
        assertEquals(51f, commits.single(), 0.0001f)
    }

    @Test fun dragPreviewsThenCommitsExactlyOnceBeforePreviewCloses() {
        val events = mutableListOf<String>()
        val request = NativeSliderRequest({ 30f }, 0f..100f,
            { events += "preview:$it" }, { events += "active:$it" },
            { events += "commit:$it" }, Modifier)
        val session = NativeSliderSession { request }
        session.start(); session.start(); session.change(70f); session.finish(false); session.finish(false)
        assertEquals(listOf("active:true", "preview:70.0", "commit:70.0", "active:false"), events)
    }
    @Test fun cancelRestoresTheStartValueWithoutCommitting() {
        val events = mutableListOf<String>()
        val request = NativeSliderRequest({ 30f }, 0f..100f,
            { events += "preview:$it" }, { events += "active:$it" },
            { events += "commit:$it" }, Modifier)
        val session = NativeSliderSession { request }
        session.start(); session.change(70f); session.finish(true)
        assertEquals(listOf("active:true", "preview:70.0", "preview:30.0", "active:false"), events)
    }
    @Test fun accessibilityChangesCommitWithoutAStuckTouchPreview() {
        var committed = 0f
        val session = NativeSliderSession {
            NativeSliderRequest({ 30f }, 0f..100f, {}, { fail("No touch preview expected") }, { committed = it }, Modifier)
        }
        session.change(200f)
        assertEquals(100f, committed, 0f)
        assertFalse(session.tracking)
    }
    @Test fun blurMinimumDefaultAndMaximumSurviveDisplayRoundTrips() {
        assertEquals(0.2f, nativeCardBlurScale(0f), 0.0001f)
        assertEquals(1f, nativeCardBlurScale(50f), 0.0001f)
        assertEquals(2f, nativeCardBlurScale(100f), 0.0001f)
        assertEquals(0f, nativeCardBlurPercent(0f), 0.0001f)
        listOf(0.2f, 0.5f, 1f, 1.6f, 2f).forEach {
            assertEquals(it, nativeCardBlurScale(nativeCardBlurPercent(it)), 0.0001f)
        }
    }
}
