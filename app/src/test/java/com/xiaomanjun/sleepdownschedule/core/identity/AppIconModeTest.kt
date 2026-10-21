package com.xiaomanjun.sleepdownschedule.core.identity

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconModeTest {
    @Test
    fun launcherAliasClassNameUsesNamespaceInsteadOfVariantApplicationId() {
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.LauncherFollow",
            launcherAliasClassName(LauncherAlias.MINIMAL_FOLLOW)
        )
        assertEquals(
            "com.xiaomanjun.sleepdownschedule.LauncherKanbanFollow",
            launcherAliasClassName(LauncherAlias.KANBAN_FOLLOW)
        )
    }

    @Test
    fun explicitModesIgnoreThemeState() {
        assertEquals(
            LauncherAlias.MINIMAL_LIGHT,
            resolveLauncherAlias(
                AppIconMode.LIGHT,
                AppIconStyle.MINIMAL,
                followsSystemDarkMode = true,
                darkTheme = true
            )
        )
        assertEquals(
            LauncherAlias.MINIMAL_DARK,
            resolveLauncherAlias(
                AppIconMode.DARK,
                AppIconStyle.MINIMAL,
                followsSystemDarkMode = true,
                darkTheme = false
            )
        )
        assertEquals(
            LauncherAlias.KANBAN_LIGHT,
            resolveLauncherAlias(
                AppIconMode.LIGHT,
                AppIconStyle.KANBAN,
                followsSystemDarkMode = true,
                darkTheme = true
            )
        )
        assertEquals(
            LauncherAlias.KANBAN_DARK,
            resolveLauncherAlias(
                AppIconMode.DARK,
                AppIconStyle.KANBAN,
                followsSystemDarkMode = true,
                darkTheme = false
            )
        )
    }

    @Test
    fun followModeUsesDynamicAliasWhenAppFollowsSystem() {
        assertEquals(
            LauncherAlias.MINIMAL_FOLLOW,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.MINIMAL,
                followsSystemDarkMode = true,
                darkTheme = false
            )
        )
        assertEquals(
            LauncherAlias.KANBAN_FOLLOW,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.KANBAN,
                followsSystemDarkMode = true,
                darkTheme = false
            )
        )
    }

    @Test
    fun followModeUsesAppThemeWhenDarkModeIsManual() {
        assertEquals(
            LauncherAlias.MINIMAL_LIGHT,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.MINIMAL,
                followsSystemDarkMode = false,
                darkTheme = false
            )
        )
        assertEquals(
            LauncherAlias.MINIMAL_DARK,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.MINIMAL,
                followsSystemDarkMode = false,
                darkTheme = true
            )
        )
        assertEquals(
            LauncherAlias.KANBAN_LIGHT,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.KANBAN,
                followsSystemDarkMode = false,
                darkTheme = false
            )
        )
        assertEquals(
            LauncherAlias.KANBAN_DARK,
            resolveLauncherAlias(
                AppIconMode.FOLLOW_DARK_MODE,
                AppIconStyle.KANBAN,
                followsSystemDarkMode = false,
                darkTheme = true
            )
        )
    }
}
