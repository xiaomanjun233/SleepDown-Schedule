package com.xiaomanjun.sleepdownschedule.core.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperOrientationTest {
    @Test fun previewOrientationsUseTheWindowAspectOnTablets() {
        assertEquals(2136f / 3200f,
            calculateWallpaperPreviewAspect(3200, 2136, WallpaperPreviewOrientation.Portrait), 0.0001f)
        assertEquals(3200f / 2136f,
            calculateWallpaperPreviewAspect(2136, 3200, WallpaperPreviewOrientation.Landscape), 0.0001f)
        assertEquals(WallpaperPreviewOrientation.Landscape, wallpaperOrientation(3200, 2136))
        assertEquals(WallpaperPreviewOrientation.Portrait, wallpaperOrientation(2136, 3200))
    }
}
