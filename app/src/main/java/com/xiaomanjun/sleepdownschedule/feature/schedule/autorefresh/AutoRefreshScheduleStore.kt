package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class RefreshConnection(val label: String) {
    Legacy("需要重新连接"),
    Ready("已连接"),
    BrowserOnly("仅手动核对"),
    SignInRequired("需要重新登录"),
    OptionsRequired("需要重新确认教务选项"),
    AdapterChanged("需要重新验证适配器"),
    ScheduleMissing("待选择目标课表")
}

internal data class AutoRefreshScheduleProfile(
    val schoolId: String,
    val schoolName: String,
    val adapterId: String,
    val adapterName: String,
    val scheduleId: Int,
    val bindingId: String? = null,
    val isolatedBrowser: Boolean = false,
    val adapterSource: String? = null,
    val scriptFingerprint: String? = null,
    val connection: RefreshConnection = RefreshConnection.Legacy,
    val automatic: Boolean = false,
    val frequencyMinutes: Long = AutoRefreshFrequency.DailyMinutes,
    val authenticatedUrl: String? = null,
    val desktopMode: Boolean = false,
    val interactionAnswers: Map<String, String> = emptyMap(),
    val avatarPath: String? = null,
    val lastRefreshAt: Long = 0,
    val lastResult: String = "尚未刷新"
) {
    val browserProfileName: String? get() = bindingId?.takeIf { isolatedBrowser }
        ?.let(AutoRefreshWebSession::profileName)
    val canCheckInBackground: Boolean get() = connection == RefreshConnection.Ready &&
        browserProfileName != null && scriptFingerprint != null && authenticatedUrl != null
    val automaticRefreshEnabled: Boolean get() = automatic && canCheckInBackground

    fun matches(school: String, adapter: String, schedule: Int): Boolean =
        schoolId == school && adapterId == adapter && scheduleId == schedule

    fun withTargetSchedule(id: Int): AutoRefreshScheduleProfile = copy(
        scheduleId = id,
        // The worker records ScheduleMissing only when checking a verified connection.
        connection = if (connection == RefreshConnection.ScheduleMissing) RefreshConnection.Ready else connection,
        lastRefreshAt = 0,
        lastResult = "已设置自动刷新的目标课表"
    )
}

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
            val committed = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .edit().putString(PayloadKey, encrypted).commit()
            check(committed) { "教务绑定保存失败，请重试" }
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
            val preferences = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            check(preferences.edit().remove(PayloadKey).commit()) { "移除教务绑定失败，请重试" }
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
                ?.let { json -> runCatching { parseAutoRefreshProfile(JSONObject(json)) }.getOrNull() }
            initialized = true
        }
    }

    private fun AutoRefreshScheduleProfile.toJson(): JSONObject = JSONObject()
        .put("schoolId", schoolId)
        .put("schoolName", schoolName)
        .put("adapterId", adapterId)
        .put("adapterName", adapterName)
        .put("scheduleId", scheduleId)
        .put("bindingId", bindingId)
        .put("isolatedBrowser", isolatedBrowser)
        .put("adapterSource", adapterSource)
        .put("scriptFingerprint", scriptFingerprint)
        .put("connection", connection.name)
        .put("automatic", automatic)
        .put("frequencyMinutes", frequencyMinutes)
        .put("authenticatedUrl", authenticatedUrl)
        .put("desktopMode", desktopMode)
        .put("interactionAnswers", JSONObject(interactionAnswers))
        .put("avatarPath", avatarPath)
        .put("lastRefreshAt", lastRefreshAt)
        .put("lastResult", lastResult)

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

internal fun parseAutoRefreshProfile(json: JSONObject): AutoRefreshScheduleProfile = with(json) {
    val bindingId = optString("bindingId").takeIf { AutoRefreshWebSession.isBindingId(it) }
    val connection = RefreshConnection.entries.firstOrNull { it.name == optString("connection") }
        ?.takeIf { bindingId != null } ?: RefreshConnection.Legacy
    AutoRefreshScheduleProfile(
        schoolId = getString("schoolId"),
        schoolName = getString("schoolName"),
        adapterId = getString("adapterId"),
        adapterName = optString("adapterName"),
        scheduleId = getInt("scheduleId"),
        bindingId = bindingId,
        isolatedBrowser = optBoolean("isolatedBrowser"),
        adapterSource = optString("adapterSource").takeIf(String::isNotBlank),
        scriptFingerprint = optString("scriptFingerprint").takeIf { it.matches(Regex("[0-9a-f]{64}")) },
        connection = connection,
        automatic = optBoolean("automatic", false),
        frequencyMinutes = AutoRefreshFrequency.normalize(optLong("frequencyMinutes", AutoRefreshFrequency.DailyMinutes)),
        authenticatedUrl = optString("authenticatedUrl").takeIf { bindingId != null && AutoRefreshLoginRoutes.isSessionPage(it) },
        desktopMode = optBoolean("desktopMode"),
        interactionAnswers = optJSONObject("interactionAnswers")?.takeIf { bindingId != null }?.let { answers ->
            answers.keys().asSequence().associateWith(answers::getString)
        }.orEmpty(),
        avatarPath = optString("avatarPath").takeIf(String::isNotBlank),
        lastRefreshAt = optLong("lastRefreshAt"),
        lastResult = if (connection == RefreshConnection.Legacy) "请重新连接教务以启用新的刷新会话"
            else optString("lastResult", "尚未刷新")
    )
}
