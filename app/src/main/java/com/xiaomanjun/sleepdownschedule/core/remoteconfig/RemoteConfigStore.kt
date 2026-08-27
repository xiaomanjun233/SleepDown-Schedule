package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import com.xiaomanjun.sleepdownschedule.*

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class RemoteConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
    @Volatile private var serverAnchor: ServerTimeAnchor? = null

    data class Cached(val bootstrap: RemoteBootstrap, val etag: String?, val lastFetchTimeMillis: Long)
    private data class ServerTimeAnchor(val serverSeconds: Long, val elapsedRealtime: Long)

    fun load(): Cached? {
        val encoded = prefs.getString(KeyBootstrap, null) ?: return null
        val bootstrap = RemoteConfigCacheCodec.decode(encoded) ?: return null
        val receivedWall = prefs.getLong(KeyReceivedWall, 0L)
        // Never cap a positive persisted delta: doing so could keep a cached credential
        // artificially alive across repeated offline process restarts. A clock that jumps
        // forward may expire free AI early, which is the safer failure mode.
        val wallDelta = (System.currentTimeMillis() - receivedWall).coerceAtLeast(0L) / 1_000L
        serverAnchor = ServerTimeAnchor(bootstrap.serverTime + wallDelta, SystemClock.elapsedRealtime())
        return Cached(bootstrap, prefs.getString(KeyEtag, null), prefs.getLong(KeyLastFetch, 0L))
    }

    fun save(bootstrap: RemoteBootstrap, etag: String?, fetchedAtMillis: Long = System.currentTimeMillis()) {
        prefs.edit(commit = true) {
            putString(KeyBootstrap, RemoteConfigCacheCodec.encode(bootstrap))
            if (etag == null) remove(KeyEtag) else putString(KeyEtag, etag)
            putLong(KeyLastFetch, fetchedAtMillis)
            putLong(KeyReceivedWall, fetchedAtMillis)
        }
        serverAnchor = ServerTimeAnchor(bootstrap.serverTime, SystemClock.elapsedRealtime())
    }

    fun markNotModified(fetchedAtMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KeyLastFetch, fetchedAtMillis) }
    }

    fun estimatedServerTimeSeconds(fallback: Long = System.currentTimeMillis() / 1_000L): Long {
        val anchor = serverAnchor ?: return fallback
        val delta = (SystemClock.elapsedRealtime() - anchor.elapsedRealtime).coerceAtLeast(0L) / 1_000L
        return anchor.serverSeconds + delta
    }

    companion object {
        private const val PrefName = "sleepdown_remote_config"
        private const val KeyBootstrap = "bootstrap_json_v1"
        private const val KeyEtag = "bootstrap_etag_v1"
        private const val KeyLastFetch = "last_fetch_time_v1"
        private const val KeyReceivedWall = "received_wall_time_v1"
    }
}

internal object RemoteConfigCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(bootstrap: RemoteBootstrap): String = json.encodeToString(bootstrap)

    fun decode(encoded: String): RemoteBootstrap? = runCatching {
        json.decodeFromString<RemoteBootstrap>(encoded)
    }.getOrNull()
}
