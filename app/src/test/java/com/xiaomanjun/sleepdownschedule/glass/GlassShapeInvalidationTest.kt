package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassShapeInvalidationTest {
    @Test fun equivalentCallbackReplacementTracksNewDependenciesWithoutInvalidatingMaterial() {
        val oldRadius = mutableStateOf(12.dp)
        val newRadius = mutableStateOf(12.dp)
        val callback = mutableStateOf<() -> Shape>({ RoundedCornerShape(oldRadius.value) })
        val shape = derivedGlassShape(callback)
        val observer = SnapshotStateObserver { it() }
        var invalidations = 0
        var observed: Shape? = null
        fun observe() = observer.observeReads("material", { invalidations++ }) { observed = shape.value }
        observer.start()
        try {
            observe()
            Snapshot.withMutableSnapshot { callback.value = { RoundedCornerShape(newRadius.value) } }
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            Snapshot.withMutableSnapshot { newRadius.value = 24.dp }
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
            observe()
            assertEquals(RoundedCornerShape(24.dp), observed)

            Snapshot.withMutableSnapshot { oldRadius.value = 48.dp }
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
