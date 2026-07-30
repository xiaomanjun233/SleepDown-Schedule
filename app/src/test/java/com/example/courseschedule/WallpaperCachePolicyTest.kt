package com.example.courseschedule

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class WallpaperCachePolicyTest {
    @Test
    fun normalMemoryLevelsKeepTheCache() {
        assertFalse(shouldClearHomeWallpaperCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }

    @Test
    fun lowAndBackgroundMemoryLevelsReleaseCachedReferences() {
        assertTrue(shouldClearHomeWallpaperCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(shouldClearHomeWallpaperCaches(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(shouldClearHomeWallpaperCaches(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
    }
}
