package com.xiaomanjun.sleepdownschedule.core.identity

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupAppIconPreferences

enum class AppIconMode(val label: String) {
    LIGHT("浅色"),
    DARK("深色"),
    FOLLOW_DARK_MODE("跟随")
}

internal enum class LauncherAlias(val classSuffix: String) {
    FOLLOW(".LauncherFollow"),
    LIGHT(".LauncherLight"),
    DARK(".LauncherDark")
}

private const val LauncherAliasNamespace = "com.xiaomanjun.sleepdownschedule"

internal fun resolveLauncherAlias(
    mode: AppIconMode,
    followsSystemDarkMode: Boolean,
    darkTheme: Boolean
): LauncherAlias = when (mode) {
    AppIconMode.LIGHT -> LauncherAlias.LIGHT
    AppIconMode.DARK -> LauncherAlias.DARK
    AppIconMode.FOLLOW_DARK_MODE -> when {
        followsSystemDarkMode -> LauncherAlias.FOLLOW
        darkTheme -> LauncherAlias.DARK
        else -> LauncherAlias.LIGHT
    }
}

internal fun launcherAliasClassName(alias: LauncherAlias): String =
    LauncherAliasNamespace + alias.classSuffix

object AppIconManager {
    private const val PreferencesName = "app_icon_preferences"
    private const val ModeKey = "mode"
    private const val FollowsSystemDarkModeKey = "follows_system_dark_mode"
    private const val DarkThemeKey = "dark_theme"

    fun currentMode(context: Context): AppIconMode {
        val stored = preferences(context).getString(
            ModeKey,
            AppIconMode.FOLLOW_DARK_MODE.name
        )
        return runCatching { AppIconMode.valueOf(stored.orEmpty()) }
            .getOrDefault(AppIconMode.FOLLOW_DARK_MODE)
    }

    fun backupPreferences(context: Context): BackupAppIconPreferences {
        val storage = preferences(context)
        return BackupAppIconPreferences(
            mode = currentMode(context).name,
            followsSystemDarkMode = storage.getBoolean(FollowsSystemDarkModeKey, true),
            darkTheme = storage.getBoolean(DarkThemeKey, false)
        )
    }

    fun applyBackupPreferences(context: Context, backup: BackupAppIconPreferences) {
        val mode = runCatching { AppIconMode.valueOf(backup.mode) }
            .getOrElse { throw IllegalArgumentException("未知 app icon mode: ${backup.mode}") }
        val committed = preferences(context).edit()
            .putString(ModeKey, mode.name)
            .putBoolean(FollowsSystemDarkModeKey, backup.followsSystemDarkMode)
            .putBoolean(DarkThemeKey, backup.darkTheme)
            .commit()
        check(committed) { "无法提交 app icon preferences" }
        applyStoredMode(context)
    }

    fun setMode(context: Context, mode: AppIconMode) {
        preferences(context).edit {
            putString(ModeKey, mode.name)
        }
        applyStoredMode(context)
    }

    fun syncAppearance(
        context: Context,
        followsSystemDarkMode: Boolean,
        darkTheme: Boolean
    ) {
        preferences(context).edit {
            putBoolean(FollowsSystemDarkModeKey, followsSystemDarkMode)
            putBoolean(DarkThemeKey, darkTheme)
        }
    }

    fun applyStoredMode(context: Context) {
        val preferences = preferences(context)
        val desired = resolveLauncherAlias(
            mode = currentMode(context),
            followsSystemDarkMode = preferences.getBoolean(FollowsSystemDarkModeKey, true),
            darkTheme = preferences.getBoolean(DarkThemeKey, false)
        )
        val packageManager = context.packageManager
        val aliases = LauncherAlias.entries

        // A launcher alias is a distinct launcher activity. Enabling the replacement before
        // disabling the old alias makes some launchers persist both entries as separate icons.
        // Disable stale aliases first, then publish exactly one desired entry.
        aliases.asSequence()
            .filterNot { it == desired }
            .forEach { alias ->
                setAliasEnabled(packageManager, context, alias, enabled = false)
            }
        setAliasEnabled(packageManager, context, desired, enabled = true)
    }

    private fun setAliasEnabled(
        packageManager: PackageManager,
        context: Context,
        alias: LauncherAlias,
        enabled: Boolean
    ) {
        val component = ComponentName(
            context.packageName,
            launcherAliasClassName(alias)
        )
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) != desiredState) {
            packageManager.setComponentEnabledSetting(
                component,
                desiredState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
