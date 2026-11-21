package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduLoginHistoryStore
import com.xiaomanjun.sleepdownschedule.feature.importing.eduWebOrigin
import java.net.URI
import java.util.Locale

internal object AutoRefreshLoginRoutes {
    fun isSessionPage(url: String?): Boolean {
        val value = url?.takeIf(EduLoginHistoryStore::shouldRememberUrl) ?: return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (eduWebOrigin(value) == null || uri.userInfo != null) return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val firstRoute = uri.fragment.orEmpty().lowercase(Locale.ROOT)
        return !LoginRoute.containsMatchIn(path) && !LoginRoute.containsMatchIn(firstRoute) &&
            path.substringAfterLast('/') != "default2.aspx"
    }

    fun isVerifiedOrigin(verifiedUrl: String, currentUrl: String?): Boolean =
        isSessionPage(currentUrl) && eduWebOrigin(currentUrl) == eduWebOrigin(verifiedUrl)

    private val LoginRoute = Regex("(^|/)(login|slogin|logon|signin|authserver|authorize)([/.?_]|$)")
}
