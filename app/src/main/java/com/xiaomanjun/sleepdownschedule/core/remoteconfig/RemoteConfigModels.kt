package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import kotlinx.serialization.Serializable

@Serializable
data class RemoteBootstrap(
    val schemaVersion: Int,
    val serverTime: Long,
    val notices: List<RemoteNotice> = emptyList(),
    val agreements: RemoteAgreementSet = RemoteAgreementSet(),
    val ai: RemoteAiConfig? = null,
    val donations: RemoteDonationSection = RemoteDonationSection(),
    val transitions: RemoteTransitionConfig = RemoteTransitionConfig()
)

/** Server-side kill switches. Missing or stale configuration always keeps native transitions off. */
@Serializable
data class RemoteTransitionConfig(
    val oplusViewSeamlessEnabled: Boolean = false,
    val oplusRouteAllowlist: Set<String> = emptySet()
)

@Serializable
data class RemoteDonationSection(
    val published: Boolean = false,
    val title: String = "感谢每一份支持",
    val message: String = "",
    val entries: List<RemoteDonationEntry> = emptyList()
)

@Serializable
data class RemoteDonationEntry(
    val id: Long,
    val supporterId: String,
    val amountCents: Long,
    val currency: String = "CNY",
    val displayOrder: Long = 0,
    val enabled: Boolean = true
)

@Serializable
data class RemoteNotice(
    val id: Long,
    val title: String,
    val content: String,
    val severity: String,
    val displayMode: String,
    val startAt: Long,
    val endAt: Long? = null,
    val minVersionCode: Long? = null,
    val maxVersionCode: Long? = null,
    val enabled: Boolean = true
)

@Serializable
data class RemoteAgreementSet(
    val privacy: RemoteAgreementSummary? = null,
    val terms: RemoteAgreementSummary? = null
)

@Serializable
data class RemoteAgreementSummary(
    val version: Long,
    val title: String,
    val url: String,
    val required: Boolean,
    val forceReaccept: Boolean
)

@Serializable
data class RemoteAiConfig(
    val enabled: Boolean,
    val configVersion: Long,
    val keyId: String,
    val baseUrl: String,
    val model: String,
    val endpointStyle: String,
    val supportsVision: Boolean,
    val cipher: String,
    val cipherVersion: Int = 1,
    val kdfVersion: Int,
    val nonce: String,
    val ciphertext: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val message: String = ""
)

internal enum class RemoteAiAvailability { AVAILABLE, DISABLED, EXPIRED, UNSUPPORTED }

internal fun RemoteAiConfig.availability(nowSeconds: Long): RemoteAiAvailability = when {
    !enabled -> RemoteAiAvailability.DISABLED
    expiresAt <= nowSeconds -> RemoteAiAvailability.EXPIRED
    cipher != "AES-256-GCM" || cipherVersion != 1 || kdfVersion != 1 -> RemoteAiAvailability.UNSUPPORTED
    else -> RemoteAiAvailability.AVAILABLE
}

@Serializable
data class InstallationPayload(
    val installationId: String,
    val versionCode: Long,
    val versionName: String,
    val androidApi: Int,
    val deviceModel: String
)

data class RemoteExperienceState(
    val agreement: RemoteAgreementSummary? = null,
    val notice: RemoteNotice? = null
)

data class RemoteConfigState(
    val bootstrap: RemoteBootstrap? = null,
    val lastFetchTimeMillis: Long = 0L,
    val lastError: String? = null,
    val isRefreshing: Boolean = false
)
