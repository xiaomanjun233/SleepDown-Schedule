package com.xiaomanjun.sleepdownschedule.core.performance

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Suppress("DEPRECATION")
class WallpaperCachePolicyTest {
    @Test fun oldPrivateStoragePrefixKeepsRestoredWallpaper() {
        val restored = File("/data/user/10/app/files/wallpaper/restored.webp")
        val unused = File("/data/user/10/app/files/wallpaper/unused.webp")
        assertEquals(setOf(unused), unreferencedWallpaperFiles(
            listOf("file:///data/user/0/app/files/wallpaper/restored.webp"), listOf(restored, unused)))
    }

    @Test fun pendingNewWallpaperIsNotCleanedBeforeItsConfigurationIsSaved() {
        val file = kotlin.io.path.createTempFile("wallpaper", ".webp").toFile()
        try {
            val now = System.currentTimeMillis()
            assertFalse(wallpaperFileReadyForCleanup(file, now))
            file.setLastModified(now - 25 * 60 * 60 * 1000L)
            assertTrue(wallpaperFileReadyForCleanup(file, now))
        } finally { file.delete() }
    }

    @Test fun restoredFileCanBeLoadedFromCurrentPrivateDirectory() {
        val directory = kotlin.io.path.createTempDirectory("wallpaper-restore").toFile()
        val wallpaper = File(directory, "wallpaper").apply { mkdir() }
        val restored = File(wallpaper, "restored.webp").apply { writeText("test") }
        try {
            assertEquals(restored, resolveManagedWallpaperFile(directory,
                "file:///data/user/0/old.app/files/wallpaper/restored.webp"))
        } finally { restored.delete(); wallpaper.delete(); directory.delete() }
    }
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

    @Test
    fun scheduleWallpaperCleanupKeepsOnlyReferencedFiles() {
        val current = "file:///data/user/0/app/files/wallpaper/current.jpg"
        val old = "file:///data/user/0/app/files/wallpaper/old.jpg"
        val other = "file:///data/user/0/app/files/wallpaper/other.png"

        assertEquals(
            setOf(old, other),
            unreferencedScheduleWallpaperUris(
                referencedUris = listOf(current),
                candidateUris = listOf(current, old, other)
            )
        )
    }

    @Test
    fun restoredWallpaperSurvivesAndroidPrivateDirectoryAliases() {
        val restored = File("/data/user/0/app/files/wallpaper/restored.webp")
        val obsolete = File("/data/user/0/app/files/wallpaper/obsolete.webp")
        val unused = unreferencedWallpaperFiles(
            referencedUris = listOf("file:///data/data/app/files/wallpaper/restored.webp"),
            candidateFiles = listOf(restored, obsolete),
            canonicalize = { file ->
                file.path.replace('\\', '/').replace("/data/data/app/", "/data/user/0/app/")
            }
        )

        assertEquals(setOf(obsolete), unused)
    }
}
