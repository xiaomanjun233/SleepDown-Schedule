package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pure cryptographic envelope; production key is non-exportable AndroidKeyStore AES-256. */
internal object ChatGptCredentialCipher {
    private val aad = "SleepDown.ChatGPT.v1".toByteArray(Charsets.UTF_8)
    fun encrypt(plaintext: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return JSONObject().put("version", 1).put("iv", encoder.encodeToString(cipher.iv))
            .put("data", encoder.encodeToString(ciphertext)).toString().toByteArray(Charsets.UTF_8)
    }
    fun decrypt(envelopeBytes: ByteArray, key: SecretKey): String {
        require(envelopeBytes.size <= 4 * 1_048_576)
        val envelope = JSONObject(String(envelopeBytes, Charsets.UTF_8))
        require(envelope.getInt("version") == 1)
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(envelope.getString("iv"))
        require(iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(decoder.decode(envelope.getString("data"))), Charsets.UTF_8)
    }
}
