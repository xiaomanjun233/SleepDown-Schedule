package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import java.net.URI

/** Existing unified-auth redirects retained alongside the shared education browser. */
internal object SwuAuthRoutes {
    const val PortalUrl = "https://i.swu.edu.cn/"
    const val TeachingRootUrl = "https://jw.swu.edu.cn/"
    const val SsoUrl = "https://jw.swu.edu.cn/sso/zllogin"
    const val UnifiedAuthRootUrl = "https://uaaap.swu.edu.cn/"
    const val UnifiedAuthLoginUrl = "https://uaaap.swu.edu.cn/cas/login"
    const val IdentityRootUrl = "https://idm.swu.edu.cn/"
    const val IdentityLoginUrl = "https://idm.swu.edu.cn/am/UI/Login"
    const val CoursePageUrl =
        "https://jw.swu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"

    fun isCoursePage(url: String?): Boolean {
        val route = parse(url) ?: return false
        return route.host == TeachingHost && route.path == CoursePagePath
    }

    fun isTeachingLoginPage(url: String?): Boolean {
        val route = parse(url) ?: return false
        return route.host == TeachingHost &&
            route.path.startsWith("/jwglxt/xtgl/login_", ignoreCase = true)
    }

    fun isAuthenticatedTeachingPage(url: String?): Boolean {
        val route = parse(url) ?: return false
        return route.host == TeachingHost &&
            route.path.startsWith("/jwglxt/", ignoreCase = true) &&
            !isTeachingLoginPage(url)
    }

    fun isSsoEntry(url: String?): Boolean {
        val route = parse(url) ?: return false
        return route.host == TeachingHost && route.path.equals("/sso/zllogin", ignoreCase = true)
    }

    fun isUnifiedAuthPage(url: String?): Boolean {
        val host = parse(url)?.host ?: return false
        return host == UnifiedAuthHost || host == IdentityHost
    }

    private fun parse(url: String?): Route? = runCatching {
        val uri = URI(url?.trim().orEmpty())
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return@runCatching null
        Route(
            host = uri.host?.lowercase().orEmpty(),
            path = uri.path.orEmpty()
        )
    }.getOrNull()

    private data class Route(
        val host: String,
        val path: String
    )

    private const val TeachingHost = "jw.swu.edu.cn"
    private const val UnifiedAuthHost = "uaaap.swu.edu.cn"
    private const val IdentityHost = "idm.swu.edu.cn"
    private const val CoursePagePath = "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
}
