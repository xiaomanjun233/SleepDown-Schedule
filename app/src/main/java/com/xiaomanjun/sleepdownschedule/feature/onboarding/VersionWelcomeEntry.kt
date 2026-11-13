package com.xiaomanjun.sleepdownschedule.feature.onboarding

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.MainActivity

internal object VersionWelcomeEntry {
    private const val PreferencesName = "version_welcome"
    private const val Version20Seen = "version_2_0_seen"
    private const val ReplayExtra = "com.xiaomanjun.sleepdownschedule.extra.REPLAY_VERSION_WELCOME"

    fun shouldShow(context: Context, intent: Intent): Boolean {
        if (intent.getBooleanExtra(ReplayExtra, false)) return true
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            !BuildConfig.APPLICATION_ID.endsWith(".benchmark") &&
            !preferences(context).getBoolean(Version20Seen, false)
    }

    fun replay(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(ReplayExtra, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    fun markSeen(context: Context) {
        preferences(context).edit { putBoolean(Version20Seen, true) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
