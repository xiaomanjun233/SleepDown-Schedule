package com.xiaomanjun.sleepdownschedule

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal sealed interface BootstrapFetchResult {
    data class Updated(val bootstrap: RemoteBootstrap, val etag: String?) : BootstrapFetchResult
    data object NotModified : BootstrapFetchResult
}

internal class RemoteConfigClient(
    private val apiBaseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun fetchBootstrap(etag: String?): BootstrapFetchResult {
        val connection = (URL("$apiBaseUrl/api/v1/bootstrap").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SleepDown-Version-Code", BuildConfig.VERSION_CODE.toString())
            setRequestProperty("X-SleepDown-Version-Name", BuildConfig.VERSION_NAME)
            etag?.let { setRequestProperty("If-None-Match", it) }
        }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> BootstrapFetchResult.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val encoded = connection.inputStream.use { it.readBytesLimited(MaxBootstrapBytes) }.toString(Charsets.UTF_8)
                    val bootstrap = json.decodeFromString<RemoteBootstrap>(encoded)
                    validateBootstrap(bootstrap)
                    BootstrapFetchResult.Updated(bootstrap, connection.getHeaderField("ETag"))
                }
                else -> error("Remote configuration request failed ($status)")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun postInstallation(path: String, payload: InstallationPayload): Boolean {
        val connection = (URL("$apiBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun validateBootstrap(bootstrap: RemoteBootstrap) {
        require(bootstrap.schemaVersion == 1) { "Unsupported bootstrap schema" }
        require(bootstrap.serverTime > 0) { "Invalid server time" }
        bootstrap.ai?.let { ai ->
            require(ai.baseUrl.startsWith("https://")) { "Remote AI base URL must use HTTPS" }
            require(ai.endpointStyle == "responses" || ai.endpointStyle == "chat_completions") { "Unknown AI endpoint style" }
            require(!ai.enabled || (ai.nonce.isNotBlank() && ai.ciphertext.isNotBlank())) { "Enabled AI config has no ciphertext" }
        }
		require(bootstrap.donations.title.length <= 120) { "Donation title is too long" }
		require(bootstrap.donations.message.length <= 500) { "Donation message is too long" }
		bootstrap.donations.entries.forEach { item ->
			require(item.supporterId.isNotBlank() && item.supporterId.length <= 120) { "Invalid donation supporter ID" }
			require(item.amountCents > 0) { "Invalid donation amount" }
			require(item.currency.length == 3 && item.currency.all(Char::isUpperCase)) { "Invalid donation currency" }
		}
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Remote configuration response is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object { const val MaxBootstrapBytes = 512 * 1024 }
}
