package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Test

class EduWebSecurityTest {
    @Test fun credentialPromptsExposeTheRightTypeWithoutMisclassifyingVerificationCodes() {
        assertEquals(EduPromptCredentialType.Username, eduPromptCredentialType("请输入学号", ""))
        assertEquals(EduPromptCredentialType.Username, eduPromptCredentialType("请输入账号", "随后填写教务密码"))
        assertEquals(EduPromptCredentialType.Password, eduPromptCredentialType("统一认证密码", ""))
        assertEquals(EduPromptCredentialType.Password, eduPromptCredentialType("Password", ""))
        assertEquals(null, eduPromptCredentialType("验证码", "输入动态密码"))
        assertEquals(null, eduPromptCredentialType("选择学期", ""))
    }

    @Test
    fun normalizesWebAddressesWithoutChangingExplicitHttpScheme() {
        assertEquals("https://jw.example.edu.cn", normalizeEduUrl("jw.example.edu.cn"))
        assertEquals("https://jw.example.edu.cn:8080/jsxsd", normalizeEduUrl("jw.example.edu.cn:8080/jsxsd"))
        assertEquals("https://203.0.113.10:8090/eams", normalizeEduUrl("203.0.113.10:8090/eams"))
        assertEquals("https://localhost:8080", normalizeEduUrl("localhost:8080"))
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
        assertEquals("", normalizeEduUrl("tel:10086"))
        assertEquals("", normalizeEduUrl("intent://login#Intent;scheme=test;end"))
    }

}
