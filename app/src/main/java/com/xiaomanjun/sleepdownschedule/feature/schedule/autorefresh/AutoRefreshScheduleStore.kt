package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AutoRefreshCookie(
    val url: String,
    val value: String
)

internal data class AutoRefreshScheduleProfile(
    val schoolId: String,
    val schoolName: String,
    val adapterId: String,
    val adapterName: String,
    val username: String,
    val password: String,
    val scheduleId: Int,
    val cookies: List<AutoRefreshCookie> = emptyList(),
    val automatic: Boolean = false,
    val frequencyMinutes: Long = 60,
    val avatarPath: String? = null,
    val lastRefreshAt: Long = 0,
    val lastResult: String = "尚未刷新"
)

internal object AutoRefreshScheduleStore {
    private const val PrefsName = "auto_refresh_schedule"
    private const val PayloadKey = "encrypted_profile"
    private const val KeyAlias = "sleepdown_auto_refresh_schedule_key"
    private val lock = Any()
    private val state = MutableStateFlow<AutoRefreshScheduleProfile?>(null)
    @Volatile private var initialized = false

    fun observe(context: Context): StateFlow<AutoRefreshScheduleProfile?> {
        ensureLoaded(context)
        return state
    }

    fun load(context: Context): AutoRefreshScheduleProfile? {
        ensureLoaded(context)
        return state.value
    }

    fun save(context: Context, profile: AutoRefreshScheduleProfile) {
        synchronized(lock) {
            val encrypted = encrypt(profile.toJson().toString())
                ?: throw IllegalStateException("无法加密保存教务登录信息")
            context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit(commit = true) {
                putString(PayloadKey, encrypted)
            }
            initialized = true
            state.value = profile
        }
    }

    fun update(
        context: Context,
        transform: (AutoRefreshScheduleProfile) -> AutoRefreshScheduleProfile
    ): AutoRefreshScheduleProfile? = synchronized(lock) {
        val current = load(context) ?: return@synchronized null
        transform(current).also { save(context, it) }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit(commit = true) {
                remove(PayloadKey)
            }
            initialized = true
            state.value = null
        }
    }

    private fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val encrypted = context.applicationContext
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(PayloadKey, null)
            state.value = encrypted
                ?.let(::decrypt)
                ?.let { json -> runCatching { JSONObject(json).toProfile() }.getOrNull() }
            initialized = true
        }
    }

    private fun AutoRefreshScheduleProfile.toJson(): JSONObject = JSONObject()
        .put("schoolId", schoolId)
        .put("schoolName", schoolName)
        .put("adapterId", adapterId)
        .put("adapterName", adapterName)
        .put("username", username)
        .put("password", password)
        .put("scheduleId", scheduleId)
        .put("automatic", automatic)
        .put("frequencyMinutes", frequencyMinutes)
        .put("avatarPath", avatarPath)
        .put("lastRefreshAt", lastRefreshAt)
        .put("lastResult", lastResult)
        .put("cookies", JSONArray().apply {
            cookies.forEach { cookie ->
                put(JSONObject().put("url", cookie.url).put("value", cookie.value))
            }
        })

    private fun JSONObject.toProfile(): AutoRefreshScheduleProfile {
        val cookieArray = optJSONArray("cookies") ?: JSONArray()
        return AutoRefreshScheduleProfile(
            schoolId = getString("schoolId"),
            schoolName = getString("schoolName"),
            adapterId = getString("adapterId"),
            adapterName = optString("adapterName"),
            username = getString("username"),
            password = getString("password"),
            scheduleId = getInt("scheduleId"),
            cookies = buildList {
                repeat(cookieArray.length()) { index ->
                    val item = cookieArray.optJSONObject(index) ?: return@repeat
                    val url = item.optString("url")
                    val value = item.optString("value")
                    if (url.isNotBlank() && value.isNotBlank()) add(AutoRefreshCookie(url, value))
                }
            },
            automatic = optBoolean("automatic", false),
            frequencyMinutes = optLong("frequencyMinutes", 60).coerceAtLeast(15),
            avatarPath = optString("avatarPath").takeIf(String::isNotBlank),
            lastRefreshAt = optLong("lastRefreshAt"),
            lastResult = optString("lastResult", "尚未刷新")
        )
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
    }.getOrNull()

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > 12)
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
