package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class EduWebSecurityTest {
    @Test
    fun normalizesWebAddressesWithoutChangingExplicitHttpScheme() {
        assertEquals("https://jw.example.edu.cn", normalizeEduUrl("jw.example.edu.cn"))
        assertEquals("http://jw.example.edu.cn/login", normalizeEduUrl("http://jw.example.edu.cn/login"))
        assertEquals("https://jw.example.edu.cn/login", normalizeEduUrl("https://jw.example.edu.cn/login"))
    }

    @Test
    fun rejectsNonWebSchemesFromAddressInput() {
        assertEquals("", normalizeEduUrl("http://"))
        assertEquals("", normalizeEduUrl("HTTPS://"))
        assertEquals("", normalizeEduUrl("file:///data/user/0/private.db"))
        assertEquals("", normalizeEduUrl("content://com.example.provider/item"))
        assertEquals("", normalizeEduUrl("javascript:alert(1)"))
        assertEquals("", normalizeEduUrl("intent://login#Intent;scheme=test;end"))
    }

    @Test
    fun preservesBundledBridgeTestPage() {
        assertEquals(EDU_BRIDGE_TEST_PAGE_URL, normalizeEduUrl(EDU_BRIDGE_TEST_PAGE_URL))
    }
}
