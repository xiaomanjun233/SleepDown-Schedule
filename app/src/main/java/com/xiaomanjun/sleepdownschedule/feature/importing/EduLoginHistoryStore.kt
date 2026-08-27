package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EduLoginHistoryEntry(
    val id: String,
    val adapterId: String,
    val title: String,
    val url: String,
    val cookie: String,
    val updatedAt: Long
)

object EduLoginHistoryStore {
    private const val Prefs = "edu_login_history"
    private const val Payload = "encrypted_entries"
    private const val KeyAlias = "sleepdown_edu_login_history_key"
    private const val MaxEntries = 8

    fun load(context: Context): List<EduLoginHistoryEntry> {
        val encrypted = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .getString(Payload, null)
            ?: return emptyList()
        val json = decrypt(encrypted) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        EduLoginHistoryEntry(
                            id = item.getString("id"),
                            adapterId = item.optString("adapterId"),
                            title = item.optString("title"),
                            url = item.getString("url"),
                            cookie = item.optString("cookie"),
                            updatedAt = item.optLong("updatedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun remember(context: Context, adapter: EduAdapter, url: String, cookie: String? = null) {
        val normalized = normalizeEduUrl(url)
        val host = runCatching { URL(normalized).host.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val id = "${adapter.adapterId}|$host"
        val previous = load(context).firstOrNull { it.id == id }
        val entry = EduLoginHistoryEntry(
            id = id,
            adapterId = adapter.adapterId,
            title = "${adapter.school.name} · $host",
            url = normalized,
            cookie = cookie?.takeIf { it.isNotBlank() } ?: previous?.cookie.orEmpty(),
            updatedAt = System.currentTimeMillis()
        )
        save(context, (listOf(entry) + load(context).filterNot { it.id == id }).take(MaxEntries))
    }

    fun restoreCookies(entry: EduLoginHistoryEntry) {
        if (entry.cookie.isBlank()) return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        entry.cookie.split(';')
            .map(String::trim)
            .filter { it.contains('=') }
            .forEach { manager.setCookie(entry.url, "$it; Path=/") }
        manager.flush()
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    private fun save(context: Context, entries: List<EduLoginHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("adapterId", entry.adapterId)
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("cookie", entry.cookie)
                    .put("updatedAt", entry.updatedAt)
            )
        }
        val encrypted = encrypt(array.toString()) ?: return
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit {
                putString(Payload, encrypted)
            }
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, payload.copyOfRange(0, 12))
        )
        String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
