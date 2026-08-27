package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import com.xiaomanjun.sleepdownschedule.*

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class RemoteTransportMetadata(
    val configVersion: Long,
    val keyId: String,
    val baseUrl: String,
    val model: String,
    val expiresAt: Long,
    val applicationId: String
)

internal object RemoteSecretCrypto {
    private const val SaltPrefix = "SleepDown|RemoteConfig|v1|"
    private const val HkdfInfo = "SleepDown AI Key Transport v1"

    fun deriveKey(remoteSecret: String, applicationId: String, certificateSha256: String): ByteArray {
        require(remoteSecret.isNotBlank()) { "Remote config secret is unavailable" }
        val normalizedCertificate = normalizeCertificateSha256(certificateSha256)
        require(normalizedCertificate.matches(Regex("[0-9a-f]{64}"))) { "Invalid signing certificate digest" }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest((SaltPrefix + applicationId + "|" + normalizedCertificate).toByteArray(Charsets.UTF_8))
        return hkdfSha256(remoteSecret.toByteArray(Charsets.UTF_8), salt, HkdfInfo.toByteArray(Charsets.UTF_8), 32)
    }

    fun canonicalAad(metadata: RemoteTransportMetadata): String = listOf(
        "SleepDown", "AIKey", "v1", metadata.configVersion.toString(), metadata.keyId,
        metadata.baseUrl, metadata.model, metadata.expiresAt.toString(), metadata.applicationId
    ).joinToString("|")

    fun decrypt(remoteSecret: String, certificateSha256: String, config: RemoteAiConfig, applicationId: String): String {
        require(config.cipher == "AES-256-GCM" && config.cipherVersion == 1 && config.kdfVersion == 1) {
            "Unsupported remote AI encryption version"
        }
        val key = deriveKey(remoteSecret, applicationId, certificateSha256)
        val nonce = decodeBase64Url(config.nonce)
        val ciphertext = decodeBase64Url(config.ciphertext)
        require(nonce.size == 12) { "Invalid AES-GCM nonce" }
        val metadata = RemoteTransportMetadata(
            config.configVersion, config.keyId, config.baseUrl, config.model, config.expiresAt, applicationId
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(canonicalAad(metadata).toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun normalizeCertificateSha256(value: String): String = value
        .lowercase()
        .replace(Regex("[^0-9a-f]"), "")

    fun encodeBase64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(ikm)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val copied = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, copied)
            offset += copied
            counter++
        }
        return output
    }
}

internal object SigningCertificateDigest {
    @Suppress("DEPRECATION")
    fun current(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = requireNotNull(packageInfo.signingInfo)
            signingInfo.apkContentsSigners.firstOrNull()
                ?: signingInfo.signingCertificateHistory.first()
        } else {
            requireNotNull(packageInfo.signatures).first()
        }
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
