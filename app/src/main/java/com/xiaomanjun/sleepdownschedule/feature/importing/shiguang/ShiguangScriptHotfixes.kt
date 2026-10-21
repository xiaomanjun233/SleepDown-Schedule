package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.content.Context
import java.security.MessageDigest
import org.json.JSONArray

internal data class ShiguangScriptHotfix(val resourcePath: String, val sourceSha256: String)

internal fun loadShiguangScriptHotfixes(context: Context): List<ShiguangScriptHotfix> =
    context.assets.open("shiguang_script_hotfixes.json").bufferedReader().use { reader ->
        val entries = JSONArray(reader.readText())
        List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            ShiguangScriptHotfix(entry.getString("resourcePath"), entry.getString("sourceSha256"))
        }
    }

internal fun resolveShiguangScriptHotfix(
    resourcePath: String,
    remoteSource: String,
    hotfixes: List<ShiguangScriptHotfix>,
    readBundled: () -> String
): String {
    val relevant = hotfixes.filter { it.resourcePath == resourcePath }
    if (relevant.isEmpty()) return remoteSource
    val remoteHash = shiguangScriptSha256(remoteSource)
    if (relevant.none { it.sourceSha256 == remoteHash }) return remoteSource
    // Replace only a verified broken revision. An upstream update always resumes normally.
    return readBundled().also {
        require(shiguangScriptSha256(it) != remoteHash) { "拾光修复脚本仍是旧版本" }
    }
}

internal fun shiguangScriptSha256(source: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(source.replace("\r\n", "\n").trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
