package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter

internal fun retainedEduSessionProfile(
    adapter: EduAdapter,
    scheduleId: Int,
    cookies: List<AutoRefreshCookie>,
    authenticatedUrl: String,
    webStorage: AutoRefreshWebStorage?,
    desktopMode: Boolean,
    previous: AutoRefreshScheduleProfile?
): AutoRefreshScheduleProfile {
    require(AutoRefreshLoginRoutes.isSessionPage("", authenticatedUrl)) { "请先完成学校登录并进入教务系统" }
    val existing = previous?.takeIf {
        it.schoolId == adapter.school.id && it.adapterId == adapter.adapterId && it.scheduleId == scheduleId
    }
    // Manual refresh restores the visible browser, not the API runner. Classic ASP.NET can put
    // its session in the URL, and native WebView also retains storage outside the API allowlist.
    // An empty CookieManager result must not discard either kind of browser session.
    return AutoRefreshScheduleProfile(
        schoolId = adapter.school.id, schoolName = adapter.school.name,
        adapterId = adapter.adapterId, adapterName = adapter.adapterName,
        username = "", password = "", scheduleId = scheduleId,
        cookies = cookies, authenticatedUrl = authenticatedUrl, webStorage = webStorage,
        desktopMode = desktopMode, sessionOnly = true, automatic = false,
        avatarPath = existing?.avatarPath, lastRefreshAt = existing?.lastRefreshAt ?: 0,
        lastResult = "登录态已保留，请打开教务页面手动刷新课表"
    )
}
