package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

// Not data classes: a generated toString must never expose a token set.
internal class ChatGptTokens(
    val accessToken: String, val refreshToken: String?, val idToken: String?,
    val expiresAt: Long, val scopes: Set<String>, val earliestRefreshAt: Long = 0
)
internal class ChatGptRegistration(
    val clientId: String, val subject: String, val email: String?, val name: String?,
    var tokens: ChatGptTokens?, var models: List<ChatGptModel> = emptyList(), var pendingTokens: ChatGptTokens? = null,
    var pendingIdentityValidation: Boolean = false
) {
    fun publicAccount() = ChatGptAccount(clientId,
        "${email ?: name ?: "ChatGPT"} · ${clientId.takeLast(6)}", email, tokens != null,
        tokens?.scopes?.contains(ChatGptProtocol.DIRECT_SCOPE) == true)
}
internal class ChatGptVault(
    val hostId: String = "urn:uuid:${UUID.randomUUID()}",
    var activeClientId: String? = null,
    val registrations: MutableList<ChatGptRegistration> = mutableListOf(),
    var pendingClientId: String? = null
) {
    fun active() = registrations.find { it.clientId == activeClientId }
}

/** App-private, excluded from cloud/device backup and the app's files-path FileProvider. */
internal class ChatGptCredentialStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "chatgpt_session_v1.enc"))

    fun read(): ChatGptVault {
        try {
            return decode(JSONObject(ChatGptCredentialCipher.decrypt(file.readFully(), key())))
        } catch (_: FileNotFoundException) {
            // openRead/readFully first recovers AtomicFile's backup (including API 26's .bak).
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ChatGptVault()
            throw ChatGptAuthException("无法读取本机 ChatGPT 安全凭据，请检查设备安全设置。")
        } catch (_: Exception) {
            // Do not silently overwrite unreadable credentials or fall back to plaintext.
            throw ChatGptAuthException("无法读取本机 ChatGPT 安全凭据，请检查设备安全设置。")
        }
    }

    fun write(vault: ChatGptVault) {
        try {
            val encrypted = ChatGptCredentialCipher.encrypt(encode(vault).toString(), key())
            val output = file.startWrite()
            try {
                // AtomicFile fsyncs before rename. The containing directory is app-private.
                output.write(encrypted)
                file.finishWrite(output)
            } catch (e: Exception) { file.failWrite(output); throw e }
        } catch (_: Exception) { throw ChatGptAuthException("无法安全保存 ChatGPT 登录信息，请重试。") }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "sleepdown_chatgpt_credentials_v1"
        internal fun encode(vault: ChatGptVault): JSONObject = JSONObject()
            .put("host_id", vault.hostId).put("active", vault.activeClientId ?: JSONObject.NULL)
            .put("pending_client", vault.pendingClientId ?: JSONObject.NULL)
            .put("registrations", JSONArray().apply {
                vault.registrations.forEach { registration ->
                    put(JSONObject().put("client_id", registration.clientId).put("subject", registration.subject)
                        .put("email", registration.email ?: JSONObject.NULL).put("name", registration.name ?: JSONObject.NULL)
                        .put("models", JSONArray().apply { registration.models.forEach {
                            put(JSONObject().put("slug", it.slug).put("name", it.displayName).put("images", it.supportsImages))
                        } })
                        .put("tokens", encodeTokens(registration.tokens))
                        .put("pending_tokens", encodeTokens(registration.pendingTokens))
                        .put("pending_identity_validation", registration.pendingIdentityValidation))
                }
            })

        internal fun decode(json: JSONObject): ChatGptVault {
            val host = json.getString("host_id")
            require(host.startsWith("urn:uuid:"))
            UUID.fromString(host.removePrefix("urn:uuid:"))
            val vault = ChatGptVault(host, json.nullableString("active"))
            vault.pendingClientId = json.nullableString("pending_client")?.also { require(it != ChatGptProtocol.DYNAMIC_CLIENT) }
            val items = json.getJSONArray("registrations")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val clientId = item.getString("client_id")
                require(clientId.isNotBlank() && clientId != ChatGptProtocol.DYNAMIC_CLIENT && vault.registrations.none { it.clientId == clientId })
                val tokens = decodeTokens(item.optJSONObject("tokens"))
                val models = item.optJSONArray("models") ?: JSONArray()
                vault.registrations += ChatGptRegistration(clientId, item.getString("subject"), item.nullableString("email"), item.nullableString("name"), tokens,
                    (0 until models.length()).map { models.getJSONObject(it) }.map {
                        ChatGptModel(it.getString("slug"), it.getString("name"), it.optBoolean("images"))
                    }, decodeTokens(item.optJSONObject("pending_tokens")), item.optBoolean("pending_identity_validation"))
            }
            return vault
        }
        private fun encodeTokens(tokens: ChatGptTokens?): Any = tokens?.let {
            JSONObject().put("access", it.accessToken).put("refresh", it.refreshToken ?: JSONObject.NULL)
                .put("id", it.idToken ?: JSONObject.NULL).put("expires", it.expiresAt)
                .put("earliest_refresh", it.earliestRefreshAt).put("scopes", JSONArray(it.scopes.toList()))
        } ?: JSONObject.NULL
        private fun decodeTokens(token: JSONObject?): ChatGptTokens? = token?.let {
            val scopes = it.getJSONArray("scopes")
            ChatGptTokens(it.getString("access"), it.nullableString("refresh"), it.nullableString("id"),
                it.getLong("expires"), (0 until scopes.length()).map { index -> scopes.getString(index) }.toSet(), it.optLong("earliest_refresh"))
        }
    }
}

internal fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
