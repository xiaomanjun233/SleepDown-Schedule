package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconModeTest {
    @Test
    fun explicitModesIgnoreThemeState() {
        assertEquals(
            LauncherAlias.LIGHT,
            resolveLauncherAlias(AppIconMode.LIGHT, followsSystemDarkMode = true, darkTheme = true)
        )
        assertEquals(
            LauncherAlias.DARK,
            resolveLauncherAlias(AppIconMode.DARK, followsSystemDarkMode = true, darkTheme = false)
        )
    }

    @Test
    fun followModeUsesDynamicAliasWhenAppFollowsSystem() {
        assertEquals(
            LauncherAlias.FOLLOW,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                followsSystemDarkMode = true,
                darkTheme = false
            )
        )
    }

    @Test
    fun followModeUsesAppThemeWhenDarkModeIsManual() {
        assertEquals(
            LauncherAlias.LIGHT,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                followsSystemDarkMode = false,
                darkTheme = false
            )
        )
        assertEquals(
            LauncherAlias.DARK,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                followsSystemDarkMode = false,
                darkTheme = true
            )
        )
    }
}
