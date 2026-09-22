package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import java.net.URI

/** Navigation prerequisites from the reviewed adapters; API validation still decides success. */
internal object AutoRefreshLoginRoutes {
    fun entryUrl(adapter: EduAdapter): String =
        if (ShiguangApiAdapterCatalog.isSwuAdapter(adapter)) SwuAuthRoutes.SsoUrl else adapter.importUrl

    fun isSessionPage(schoolId: String, url: String?): Boolean {
        val uri = runCatching { URI(url.orEmpty()) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return false
        val host = uri.host.lowercase()
        val path = uri.path.orEmpty().lowercase()
        val pageName = path.substringAfterLast('/')
        if (pageName in setOf("default2.aspx", "login.aspx", "login.asp", "login.jsp", "login.html") ||
            path.contains("/authserver") || path.contains("/cas/login") ||
            path.contains("/login_") || path.endsWith("/login") || path.contains("/am/ui/login") ||
            Regex("(^|/)login([/?]|$)").containsMatchIn(uri.fragment.orEmpty().lowercase())
        ) return false
        return when (schoolId) {
            "SWU" -> SwuAuthRoutes.isCoursePage(url)
            // These adapters explicitly reject other hosts / origins.
            "CQIE" -> host == "njw.cqie.edu.cn"
            "QHNU" -> AutoRefreshWebSession.origin(url) == "https://yjsxt.qhnu.edu.cn"
            // Preserve WebVPN prefixes: both adapters locate teaching pages by path.
            "NJUPT" -> Regex("/(kbcx|xtgl)/").containsMatchIn(path)
            "JUST" -> path.contains("/jwglxt/")
            "NWPU" -> host == "jwxt.nwpu.edu.cn" || path.contains("/student/for-std/")
            "GXMU" -> host == "jwxt.gxmu.edu.cn"
            else -> true
        }
    }
}
