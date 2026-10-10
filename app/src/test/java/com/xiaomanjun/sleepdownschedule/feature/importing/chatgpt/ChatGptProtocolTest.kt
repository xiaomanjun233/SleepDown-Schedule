package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class ChatGptProtocolTest {
    private val attempt = AuthAttempt(null, "http://127.0.0.1:49152/auth/callback", "test-state", "test-nonce", "test-verifier")

    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val secrets = (0..20).map { ChatGptProtocol.randomSecret() }
        assertEquals(secrets.size, secrets.toSet().size)
        assertTrue(secrets.all { it.matches(Regex("[A-Za-z0-9_-]{43}")) })
    }

    @Test fun newAndReturningAuthorizationUseDistinctParameters() {
        val fresh = ChatGptProtocol.authorizeUrl(attempt, "urn:uuid:host")
        assertTrue(fresh.startsWith(ChatGptProtocol.AUTHORIZE + "?"))
        assertTrue(fresh.contains("client_id=dynamic_agent_client"))
        assertTrue(fresh.contains("agent_name_hint=SleepDown+Schedule"))
        assertTrue(fresh.contains("code_challenge_method=S256"))
        assertFalse(fresh.contains("prompt=consent"))
        val returning = ChatGptProtocol.authorizeUrl(AuthAttempt("issued-client", attempt.redirectUri), "urn:uuid:host", "fake.id.token", true)
        assertTrue(returning.contains("client_id=issued-client"))
        assertTrue(returning.contains("id_token_hint=fake.id.token"))
        assertTrue(returning.contains("prompt=consent"))
        assertFalse(returning.contains("agent_name_hint"))
    }

    @Test fun dynamicCallbackRequiresIssuedClient() {
        val accepted = ChatGptProtocol.callback("/auth/callback?code=sample&state=test-state&client_id=issued", attempt)
        assertEquals("issued", accepted.clientId)
        rejectsCallback("/auth/callback?code=sample&state=test-state")
        rejectsCallback("/auth/callback?code=sample&state=test-state&client_id=dynamic_agent_client")
    }

    @Test fun callbackRejectsPollutionWrongRouteAndStaleState() {
        listOf(
            "/auth/callback?code=sample&state=wrong&client_id=issued",
            "/auth/callback?code=sample&state=test-state&state=test-state&client_id=issued",
            "/auth/callback?code=sample&state=test-state&%73tate=test-state&client_id=issued",
            "/auth/callback?code=sample&state=test-state&client_id=issued&code=other",
            "/callback?code=sample&state=test-state&client_id=issued",
            "http://127.0.0.1:49152/auth/callback?code=sample&state=test-state&client_id=issued",
            "/auth/callback?code=sample&state=test-state&client_id=issued#fragment",
            "/auth/callback?code=sample&state=test-state&client_id=issued%0Aheader",
            "/auth/callback?code=sample&state=test-state&client_id=issued&error=access_denied"
        ).forEach(::rejectsCallback)
    }

    @Test fun returningCallbackCannotSubstituteClient() {
        val returning = AuthAttempt("issued", attempt.redirectUri, "test-state")
        assertEquals("issued", ChatGptProtocol.callback("/auth/callback?code=sample&state=test-state", returning).clientId)
        assertThrows(ChatGptAuthException::class.java) {
            ChatGptProtocol.callback("/auth/callback?code=sample&state=test-state&client_id=other", returning)
        }
    }

    @Test fun deniedConsentStillRequiresCorrectState() {
        val denied = assertThrows(ChatGptAuthException::class.java) { ChatGptProtocol.callback("/auth/callback?error=access_denied&state=test-state", attempt) }
        assertEquals("已取消 ChatGPT 授权。", denied.message)
        val stale = assertThrows(ChatGptAuthException::class.java) { ChatGptProtocol.callback("/auth/callback?error=access_denied&state=wrong", attempt) }
        assertNotEquals(denied.message, stale.message)
    }

    @Test fun idTokenRequiresSignedIssuerAudienceExpiryAndNonce() {
        val claims = claims()
        assertEquals("account-subject", verify(sign(claims)).subject)
        assertEquals("person@example.test", verify(sign(claims)).email)
        listOf(
            claims().put("iss", "https://attacker.invalid"),
            claims().put("aud", "another-client"),
            claims().put("exp", 1_000),
            claims().put("nonce", "wrong-nonce"),
            claims().put("sub", ""),
            claims().put("nbf", 9_999),
            claims().put("aud", JSONArray(listOf("issued", "other"))),
            claims().put("azp", "other")
        ).forEach { invalid -> assertThrows(ChatGptAuthException::class.java) { verify(sign(invalid)) } }
        assertEquals("account-subject", verify(sign(claims().put("aud", JSONArray(listOf("issued", "other"))).put("azp", "issued"))).subject)
    }

    @Test fun idTokenRejectsUnsignedTamperedAndUnknownKeyTokens() {
        assertThrows(ChatGptAuthException::class.java) { verify(sign(claims(), "none")) }
        assertThrows(ChatGptAuthException::class.java) { verify(sign(claims(), "HS256")) }
        assertThrows(ChatGptAuthException::class.java) { verify(sign(claims(), kid = "other")) }
        val token = sign(claims()).split('.')
        val tampered = "${token[0]}.${ChatGptProtocol.base64(claims().put("sub", "attacker").toString().toByteArray())}.${token[2]}"
        val error = assertThrows(ChatGptAuthException::class.java) { verify(tampered) }
        assertNull(error.cause)
        assertFalse(error.toString().contains(tampered))
    }

    @Test fun modelCatalogUsesVisibleServerOrderAndDisplayNames() {
        val catalog = ChatGptProtocol.models(JSONObject("""{"models":[
            {"slug":"second-alphabetically","display_name":"First choice","visibility":"list"},
            {"slug":"hidden","visibility":"hidden"},
            {"slug":"another","display_name":"Second choice","visibility":"list","supports_images":true},
            {"slug":"unknown-visibility"}]}"""))
        assertEquals(listOf("second-alphabetically", "another"), catalog.map { it.slug })
        assertEquals(listOf("First choice", "Second choice"), catalog.map { it.displayName })
        assertFalse(catalog[0].supportsImages)
        assertTrue(catalog[1].supportsImages)
    }

    private fun rejectsCallback(target: String) { assertThrows(ChatGptAuthException::class.java) { ChatGptProtocol.callback(target, attempt) } }
    private fun claims() = JSONObject().put("iss", ChatGptProtocol.ISSUER).put("aud", "issued")
        .put("sub", "account-subject").put("email", "person@example.test").put("exp", 2_000).put("nonce", "test-nonce")
    private fun verify(token: String) = ChatGptProtocol.verifyIdToken(token, jwks, "issued", "test-nonce", 1_000)
    private fun sign(claims: JSONObject, algorithm: String = "RS256", kid: String = "key") : String {
        val header = JSONObject().put("alg", algorithm).put("kid", kid)
        val input = "${ChatGptProtocol.base64(header.toString().toByteArray())}.${ChatGptProtocol.base64(claims.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keyPair.private); update(input.toByteArray()) }.sign()
        return "$input.${ChatGptProtocol.base64(signature)}"
    }
    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val publicKey = keyPair.public as RSAPublicKey
        private val jwks = JSONObject().put("keys", JSONArray().put(JSONObject().put("kty", "RSA").put("kid", "key").put("alg", "RS256").put("use", "sig")
            .put("n", ChatGptProtocol.base64(publicKey.modulus.toByteArray())).put("e", ChatGptProtocol.base64(publicKey.publicExponent.toByteArray()))))
    }
}
