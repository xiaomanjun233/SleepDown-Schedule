package com.xiaomanjun.sleepdownschedule.core.identity

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupAppIconPreferences

enum class AppIconMode(val label: String) {
    LIGHT("浅色"),
    DARK("深色"),
    FOLLOW_DARK_MODE("跟随")
}

/** 应用图标风格：简约（原默认）与看板娘。 */
enum class AppIconStyle(val label: String) {
    MINIMAL("简约"),
    KANBAN("看板娘")
}

internal enum class LauncherAlias(val classSuffix: String) {
    MINIMAL_FOLLOW(".LauncherFollow"),
    MINIMAL_LIGHT(".LauncherLight"),
    MINIMAL_DARK(".LauncherDark"),
    KANBAN_FOLLOW(".LauncherKanbanFollow"),
    KANBAN_LIGHT(".LauncherKanbanLight"),
    KANBAN_DARK(".LauncherKanbanDark")
}

private const val LauncherAliasNamespace = "com.xiaomanjun.sleepdownschedule"

private fun styleAlias(style: AppIconStyle): (AppIconMode) -> LauncherAlias = when (style) {
    AppIconStyle.MINIMAL -> { mode ->
        when (mode) {
            AppIconMode.LIGHT -> LauncherAlias.MINIMAL_LIGHT
            AppIconMode.DARK -> LauncherAlias.MINIMAL_DARK
            AppIconMode.FOLLOW_DARK_MODE -> LauncherAlias.MINIMAL_FOLLOW
        }
    }
    AppIconStyle.KANBAN -> { mode ->
        when (mode) {
            AppIconMode.LIGHT -> LauncherAlias.KANBAN_LIGHT
            AppIconMode.DARK -> LauncherAlias.KANBAN_DARK
            AppIconMode.FOLLOW_DARK_MODE -> LauncherAlias.KANBAN_FOLLOW
        }
    }
}

internal fun resolveLauncherAlias(
    mode: AppIconMode,
    style: AppIconStyle,
    followsSystemDarkMode: Boolean,
    darkTheme: Boolean
): LauncherAlias {
    val resolvedMode = when (mode) {
        AppIconMode.LIGHT, AppIconMode.DARK -> mode
        AppIconMode.FOLLOW_DARK_MODE -> when {
            followsSystemDarkMode -> AppIconMode.FOLLOW_DARK_MODE
            darkTheme -> AppIconMode.DARK
            else -> AppIconMode.LIGHT
        }
    }
    // FOLLOW 模式下跟随系统深浅时，仍走对应风格的 FOLLOW alias（图标带 night 变体自动切换）。
    return styleAlias(style)(resolvedMode)
}

internal fun launcherAliasClassName(alias: LauncherAlias): String =
    LauncherAliasNamespace + alias.classSuffix

/**
 * 根据当前选择的图标风格和模式，返回对应的 mipmap 资源 ID。
 * 用于在应用内显示当前使用的图标（如关于页）。
 */
fun currentIconResId(
    context: Context,
    darkTheme: Boolean = AppIconManager.currentDarkTheme(context)
): Int {
    val style = AppIconManager.currentStyle(context)
    val mode = AppIconManager.currentMode(context)
    
    // 解析实际使用的模式
    val resolvedMode = when (mode) {
        AppIconMode.LIGHT, AppIconMode.DARK -> mode
        AppIconMode.FOLLOW_DARK_MODE -> if (darkTheme) AppIconMode.DARK else AppIconMode.LIGHT
    }
    
    return when (style) {
        AppIconStyle.MINIMAL -> when (resolvedMode) {
            AppIconMode.LIGHT -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher_light
            AppIconMode.DARK -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher_dark
            AppIconMode.FOLLOW_DARK_MODE -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher
        }
        AppIconStyle.KANBAN -> when (resolvedMode) {
            AppIconMode.LIGHT -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher_kanban_light
            AppIconMode.DARK -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher_kanban_dark
            AppIconMode.FOLLOW_DARK_MODE -> com.xiaomanjun.sleepdownschedule.R.mipmap.ic_launcher_kanban
        }
    }
}

/** Fixed full-color PNGs for SystemUI, independent of launcher/night resource resolution. */
fun currentLiveUpdateIconResId(context: Context): Int {
    val dark = when (AppIconManager.currentMode(context)) {
        AppIconMode.LIGHT -> false
        AppIconMode.DARK -> true
        AppIconMode.FOLLOW_DARK_MODE -> AppIconManager.currentDarkTheme(context)
    }
    return when (AppIconManager.currentStyle(context)) {
        AppIconStyle.MINIMAL -> if (dark) {
            com.xiaomanjun.sleepdownschedule.R.drawable.ic_live_update_minimal_dark
        } else {
            com.xiaomanjun.sleepdownschedule.R.drawable.ic_live_update_minimal_light
        }
        AppIconStyle.KANBAN -> if (dark) {
            com.xiaomanjun.sleepdownschedule.R.drawable.ic_live_update_kanban_dark
        } else {
            com.xiaomanjun.sleepdownschedule.R.drawable.ic_live_update_kanban_light
        }
    }
}

object AppIconManager {
    private const val PreferencesName = "app_icon_preferences"
    private const val ModeKey = "mode"
    private const val StyleKey = "style"
    private const val FollowsSystemDarkModeKey = "follows_system_dark_mode"
    private const val DarkThemeKey = "dark_theme"
    private var lastAppliedIconResId: Int? = null
    internal var onIconChanged: (() -> Unit)? = null

    fun currentDarkTheme(context: Context): Boolean = if (
        preferences(context).getBoolean(FollowsSystemDarkModeKey, true)
    ) {
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    } else preferences(context).getBoolean(DarkThemeKey, false)

    fun currentMode(context: Context): AppIconMode {
        val stored = preferences(context).getString(
            ModeKey,
            AppIconMode.FOLLOW_DARK_MODE.name
        )
        return runCatching { AppIconMode.valueOf(stored.orEmpty()) }
            .getOrDefault(AppIconMode.FOLLOW_DARK_MODE)
    }

    fun currentStyle(context: Context): AppIconStyle {
        val stored = preferences(context).getString(
            StyleKey,
            AppIconStyle.KANBAN.name
        )
        return runCatching { AppIconStyle.valueOf(stored.orEmpty()) }
            .getOrDefault(AppIconStyle.KANBAN)
    }

    fun backupPreferences(context: Context): BackupAppIconPreferences {
        val storage = preferences(context)
        return BackupAppIconPreferences(
            mode = currentMode(context).name,
            style = currentStyle(context).name,
            followsSystemDarkMode = storage.getBoolean(FollowsSystemDarkModeKey, true),
            darkTheme = storage.getBoolean(DarkThemeKey, false)
        )
    }

    fun applyBackupPreferences(context: Context, backup: BackupAppIconPreferences) {
        val mode = runCatching { AppIconMode.valueOf(backup.mode) }
            .getOrElse { throw IllegalArgumentException("未知 app icon mode: ${backup.mode}") }
        val style = runCatching { AppIconStyle.valueOf(backup.style) }
            .getOrDefault(AppIconStyle.KANBAN)
        val committed = preferences(context).edit()
            .putString(ModeKey, mode.name)
            .putString(StyleKey, style.name)
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

    fun setStyle(context: Context, style: AppIconStyle) {
        preferences(context).edit {
            putString(StyleKey, style.name)
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
            style = currentStyle(context),
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
        com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler.refreshLiveUpdateIcon(context)
        val iconResId = currentIconResId(context)
        if (lastAppliedIconResId != iconResId) {
            lastAppliedIconResId = iconResId
            onIconChanged?.invoke()
        }
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
