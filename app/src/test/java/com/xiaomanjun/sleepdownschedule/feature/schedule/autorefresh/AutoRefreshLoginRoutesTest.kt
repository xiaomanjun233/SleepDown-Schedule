package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshLoginRoutesTest {
    @Test fun portalsDoNotCountAsTeachingSessions() {
        mapOf(
            "SWU" to "https://i.swu.edu.cn/",
            "CQIE" to "https://i.cqie.edu.cn/",
            "QHNU" to "http://one.qhnu.edu.cn/default/portal/index.jsp",
            "NJUPT" to "https://i.njupt.edu.cn/",
            "JUST" to "https://my.just.edu.cn/",
            "NWPU" to "https://ecampus.nwpu.edu.cn/",
            "GXMU" to "https://cas.gxmu.edu.cn/lyuapServer/login"
        ).forEach { (school, url) -> assertFalse(school, AutoRefreshLoginRoutes.isSessionPage(school, url)) }
    }

    @Test fun teachingPagesAndRecognizedWebVpnPathsCanProceedToApiValidation() {
        mapOf(
            "SWU" to SwuAuthRoutes.CoursePageUrl,
            "CQIE" to "https://njw.cqie.edu.cn/",
            "QHNU" to "https://yjsxt.qhnu.edu.cn/home",
            "NJUPT" to "https://i.njupt.edu.cn/http/fixture/kbcx/xskbcx_cxXskbcxIndex.html",
            "JUST" to "https://my.just.edu.cn/http/webvpnfixture/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html",
            "NWPU" to "https://jwxt.nwpu.edu.cn/student/home",
            "GXMU" to "https://jwxt.gxmu.edu.cn/new/student/"
        ).forEach { (school, url) -> assertTrue(school, AutoRefreshLoginRoutes.isSessionPage(school, url)) }
    }

    @Test fun loginPagesAndLookalikeHostsCannotEnableCredentialCapture() {
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("IMU", "https://jwxt.imu.edu.cn/login"))
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("QHNU", "https://yjsxt.qhnu.edu.cn/#/login"))
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("CQIE", "https://njw.cqie.edu.cn.example.com/"))
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("SWU", "https://jw.swu.edu.cn/jwglxt/xtgl/login_slogin.html"))
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("QHNU", "https://yjsxt.qhnu.edu.cn:8443/home"))
        assertFalse(AutoRefreshLoginRoutes.isSessionPage("CQIE", "https://user@njw.cqie.edu.cn/"))
    }
}
