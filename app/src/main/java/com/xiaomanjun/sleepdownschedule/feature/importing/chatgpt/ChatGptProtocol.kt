package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import org.json.JSONObject
import java.math.BigInteger
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Public SIWC protocol only. Never use browser cookies or ChatGPT's private backend. */
internal object ChatGptProtocol {
    const val ISSUER = "https://auth.openai.com"
    const val AUTHORIZE = "$ISSUER/api/accounts/authorize"
    const val TOKEN = "$ISSUER/api/accounts/oauth/token"
    const val RESOURCE = "https://api.openai.com/v1"
    const val DIRECT_SCOPE = "chatgpt.tokens.use.direct"
    const val DYNAMIC_CLIENT = "dynamic_agent_client"
    const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    private val random = SecureRandom()

    fun randomSecret(): String = base64(ByteArray(32).also(random::nextBytes))
    fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun challenge(verifier: String): String = base64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    fun authorizeUrl(attempt: AuthAttempt, hostId: String, idTokenHint: String? = null, consent: Boolean = false): String {
        val params = linkedMapOf(
            "client_id" to (attempt.clientId ?: DYNAMIC_CLIENT), "ext_agent_host_id" to hostId,
            "response_type" to "code", "redirect_uri" to attempt.redirectUri, "scope" to SCOPES,
            "resource" to RESOURCE, "state" to attempt.state, "nonce" to attempt.nonce,
            "code_challenge_method" to "S256", "code_challenge" to challenge(attempt.verifier)
        )
        if (attempt.clientId == null) params["agent_name_hint"] = "SleepDown Schedule"
        else if (!idTokenHint.isNullOrBlank()) params["id_token_hint"] = idTokenHint
        if (consent) params["prompt"] = "consent"
        return "$AUTHORIZE?${form(params)}"
    }

    /** Reject pollution, fragments, origin-form ambiguity, stale state, and client substitution. */
    fun callback(target: String, attempt: AuthAttempt): AuthCallback {
        try {
            val uri = URI(target)
            require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawPath == "/auth/callback" && uri.rawFragment == null)
            val values = linkedMapOf<String, String>()
            val query = uri.rawQuery ?: throw IllegalArgumentException()
            require(query.length <= 16_384)
            query.split('&').forEach { pair ->
                val parts = pair.split('=', limit = 2)
                require(parts.size == 2)
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                require(key.isNotBlank() && key !in values && value.none { it == '\u0000' || it == '\r' || it == '\n' })
                values[key] = value
            }
            require(MessageDigest.isEqual(attempt.state.toByteArray(), values["state"].orEmpty().toByteArray()))
            if ("error" in values) {
                require("code" !in values)
                throw ChatGptAuthException(if (values["error"] == "access_denied") "已取消 ChatGPT 授权。" else "ChatGPT 授权未完成，请重试。")
            }
            val code = values["code"].orEmpty()
            require(code.isNotBlank())
            val client = values["client_id"] ?: attempt.clientId
            require(!client.isNullOrBlank() && client != DYNAMIC_CLIENT && client.length <= 512)
            require(attempt.clientId == null || attempt.clientId == client)
            return AuthCallback(code, client)
        } catch (e: ChatGptAuthException) { throw e }
        catch (_: Exception) { throw ChatGptAuthException("ChatGPT 登录回调校验失败，请重新登录。") }
    }

    fun verifyIdToken(token: String, jwks: JSONObject, clientId: String, nonce: String?, nowSeconds: Long): VerifiedIdentity {
        try {
            require(token.length <= 65_536)
            val parts = token.split('.')
            require(parts.size == 3)
            val decoder = Base64.getUrlDecoder()
            val header = JSONObject(String(decoder.decode(parts[0]), Charsets.UTF_8))
            require(header.getString("alg") == "RS256" && !header.has("crit"))
            val kid = header.getString("kid")
            val keys = jwks.getJSONArray("keys")
            val matching = (0 until keys.length()).map { keys.getJSONObject(it) }.filter { it.optString("kid") == kid }
            require(matching.size == 1)
            val key = matching.single()
            require(key.getString("kty") == "RSA")
            require(!key.has("use") || key.getString("use") == "sig")
            require(!key.has("alg") || key.getString("alg") == "RS256")
            val n = BigInteger(1, decoder.decode(key.getString("n")))
            require(n.bitLength() >= 2048)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, BigInteger(1, decoder.decode(key.getString("e")))))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            require(verifier.verify(decoder.decode(parts[2])))
            val claims = JSONObject(String(decoder.decode(parts[1]), Charsets.UTF_8))
            require(claims.getString("iss") == ISSUER)
            val aud = claims.get("aud")
            if (aud is String) require(aud == clientId)
            else {
                val audiences = claims.getJSONArray("aud")
                require((0 until audiences.length()).any { audiences.getString(it) == clientId })
                if (audiences.length() > 1) require(claims.optString("azp") == clientId)
            }
            if (claims.has("azp")) require(claims.getString("azp") == clientId)
            require(claims.getLong("exp") > nowSeconds)
            if (claims.has("nbf")) require(claims.getLong("nbf") <= nowSeconds + 60)
            if (claims.has("iat")) require(claims.getLong("iat") <= nowSeconds + 60)
            if (nonce != null) require(MessageDigest.isEqual(nonce.toByteArray(), claims.optString("nonce").toByteArray()))
            val subject = claims.getString("sub")
            require(subject.isNotBlank())
            return VerifiedIdentity(subject, claims.optString("email").takeIf(String::isNotBlank), claims.optString("name").takeIf(String::isNotBlank))
        } catch (_: Exception) { throw ChatGptAuthException("ChatGPT 身份校验失败，请重新登录。") }
    }

    fun models(body: JSONObject): List<ChatGptModel> {
        val models = body.getJSONArray("models")
        return (0 until models.length()).map { models.getJSONObject(it) }
            .filter { it.optString("visibility") == "list" }
            .mapNotNull {
                val slug = it.optString("slug")
                if (slug.isBlank()) null else ChatGptModel(slug, it.optString("display_name").ifBlank { slug },
                    it.optBoolean("supports_images", false))
            }.distinctBy { it.slug }
    }
}

internal class AuthAttempt(val clientId: String?, val redirectUri: String,
    val state: String = ChatGptProtocol.randomSecret(), val nonce: String = ChatGptProtocol.randomSecret(),
    val verifier: String = ChatGptProtocol.randomSecret())
internal class AuthCallback(val code: String, val clientId: String)
internal data class VerifiedIdentity(val subject: String, val email: String?, val name: String?)

/** Deliberately contains only a safe message; never chain a transport/body exception. */
class ChatGptAuthException(message: String, internal val oauthCode: String? = null, internal val status: Int? = null) : Exception(message)

internal class ChatGptHttp {
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; connections.forEach { it.disconnect() } }
    fun json(url: String, form: Map<String, String>? = null, accessToken: String? = null): JSONObject =
        request(url, form, accessToken).let { if (it.isBlank()) JSONObject() else try { JSONObject(it) } catch (_: Exception) { throw ChatGptAuthException("ChatGPT 返回了无法识别的数据。") } }

    private fun request(url: String, form: Map<String, String>?, accessToken: String?): String {
        // Discovery is allowed to select an auth endpoint, never another origin.
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host in setOf("auth.openai.com", "api.openai.com") && uri.userInfo == null && uri.port in setOf(-1, 443))
        val connection = URL(url).openConnection() as HttpURLConnection
        connections += connection
        try {
            if (cancelled) throw ChatGptAuthException("ChatGPT 请求已取消。")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (form != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val bytes = ChatGptProtocol.form(form).toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = source?.use {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 1_048_576) throw ChatGptAuthException("ChatGPT 响应过大。")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }.orEmpty()
            if (status != 200) {
                val code = runCatching {
                    val error = JSONObject(body).opt("error")
                    if (error is JSONObject) error.optString("code") else error as? String
                }.getOrNull()?.takeIf { it.matches(Regex("[a-z_]{1,80}")) }
                val message = when (status) {
                    401 -> "ChatGPT 登录状态未被接受，请检查账号或重新登录。"
                    403 -> "此 ChatGPT 账号、地区或授权暂不支持此操作。"
                    429 -> "ChatGPT 请求暂受速率或用量限制，请稍后重试；详情可在 ChatGPT 用量设置查看。"
                    in 500..599 -> "ChatGPT 服务暂不可用，请稍后重试。"
                    else -> "ChatGPT 请求未完成（HTTP $status）。"
                }
                throw ChatGptAuthException(message, code, status)
            }
            return body
        } catch (e: ChatGptAuthException) { throw e }
        catch (_: Exception) { throw ChatGptAuthException("无法连接 ChatGPT，请检查网络后重试。") }
        finally { connections -= connection; connection.disconnect() }
    }
}
