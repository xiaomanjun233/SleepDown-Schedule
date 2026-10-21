package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Encrypted with a device-bound Keystore key; excluded from Android and .sleepdown backups. */
internal object WebDavCredentials {
    private const val Alias = "sleepdown_webdav_v1"
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "webdav.credentials"))
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(Alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    @Synchronized fun read(context: Context): WebDavConnection? {
        val atomic = file(context)
        if (!atomic.baseFile.exists()) return null
        val bytes = atomic.readFully()
        require(bytes.size in 29..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
        return WebDavConnection(json.getString("address"), json.getString("username"), json.getString("password"))
    }
    @Synchronized fun save(context: Context, connection: WebDavConnection) {
        WebDavClient.folderUrl(connection.address)
        require(connection.address.length <= 4096 && connection.username.length <= 1024 && connection.password.length <= 4096)
        val plain = JSONObject().put("address", connection.address).put("username", connection.username)
            .put("password", connection.password).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = try { cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
        val atomic = file(context)
        val output = atomic.startWrite()
        try { output.write(encrypted); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        WebDavAutomation.connectionSaved(context, connection)
    }
}
