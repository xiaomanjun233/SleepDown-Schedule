package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.util.UUID

private const val LocalGeneration = "self-use-v1"
internal fun EduAdapter.isSelfUseAdapter() = warehouseGeneration == LocalGeneration

/** Exact origins only: sharing a vendor, parent domain or CAS login never proves a school match. */
internal fun eduOrigin(value: String): String? = runCatching {
    val uri = URI(value.trim())
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme !in setOf("http", "https") || uri.userInfo != null) return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    val port = uri.port.takeIf { it >= 0 } ?: if (scheme == "https") 443 else 80
    "$scheme://$host:$port"
}.getOrNull()

internal fun matchingLocalEduAdapters(url: String, adapters: List<EduAdapter>): List<EduAdapter> {
    val origin = eduOrigin(url) ?: return emptyList()
    return adapters.filter { !it.isAiEduImportTool() && !it.isGeneralEduTool() &&
        eduOrigin(it.importUrl) == origin }.distinctBy { it.school.id to it.adapterId }
        .sortedWith(compareByDescending<EduAdapter> { it.isSelfUseAdapter() }.thenBy { it.displayName })
}

/** Private, opt-in adapters; this store is separate from the upstream index and updater. */
internal object LocalEduAdapterStore {
    private fun root(context: Context) = File(context.filesDir, "local-edu-adapters")
    private fun record(context: Context, id: String, extension: String = "zip"): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "本地适配标识无效" }
        return AtomicFile(File(root(context), "$id.$extension"))
    }
    private fun decode(bytes: ByteArray): JsonObject = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    @Synchronized fun load(context: Context): List<EduAdapter> = root(context).listFiles().orEmpty()
        .filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".zip") || it.name.endsWith(".zip.bak") }
        .map { File(it.parentFile, it.name.removeSuffix(".bak")) }.distinctBy { it.name }
        .sortedBy { if (it.extension == "zip") 0 else 1 }
        .distinctBy { it.nameWithoutExtension }
        .mapNotNull { file -> runCatching {
            val bytes = AtomicFile(file).readFully()
            (if (file.extension == "zip") LocalEduAdapterPackage.decode(bytes).adapter.copy(warehouseGeneration = LocalGeneration)
                else eduAdapterFromIntentKey(decode(bytes).getValue("adapter").jsonPrimitive.content))
                ?.takeIf { it.isSelfUseAdapter() && it.adapterId == file.nameWithoutExtension }
        }.getOrNull() }.distinctBy { it.adapterId }

    @Synchronized fun source(context: Context, adapter: EduAdapter): String {
        require(adapter.isSelfUseAdapter())
        val file = record(context, adapter.adapterId)
        return if (file.baseFile.isFile || File(file.baseFile.path + ".bak").isFile)
            LocalEduAdapterPackage.decode(file.readFully()).source
        else decode(record(context, adapter.adapterId, "json").readFully()).getValue("source").jsonPrimitive.content
    }

    @Synchronized fun save(context: Context, name: String, url: String, source: String, id: String? = null): EduAdapter {
        require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() })
        val loginUrl = cleanLocalEduLoginUrl(url)
        require(source.toByteArray().size in 1..262_144) { "脚本为空或超过 256 KiB" }
        require(source.contains("shiguangBridge") && source.contains("notifyTaskCompletion")) { "脚本需使用拾光 Bridge 协议" }
        val key = id ?: UUID.randomUUID().toString()
        val schoolId = "LOCAL_${key.replace("-", "_").uppercase(java.util.Locale.ROOT)}"
        val adapter = EduAdapter(EduSchool(schoolId, name.trim(), schoolId, "L"), key,
            "自用适配", "BACHELOR_AND_ASSOCIATE", "adapter.js", loginUrl,
            "小漫君", "保存在本机的自用拾光适配", LocalGeneration)
        val data = LocalEduAdapterPackage.encode(adapter, source)
        check(root(context).mkdirs() || root(context).isDirectory)
        val file = record(context, key)
        val output = file.startWrite()
        try { output.write(data); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        return adapter
    }

    @Synchronized fun delete(context: Context, adapter: EduAdapter) {
        require(adapter.isSelfUseAdapter())
        record(context, adapter.adapterId).delete()
        record(context, adapter.adapterId, "json").delete()
    }
}
