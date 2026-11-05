package com.xiaomanjun.sleepdownschedule.feature.importing

import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Compressed source pages are private assets; the bounded conversation JSON keeps references only. */
internal object AiImportHistoryImages {
    private val managedName = Regex("^([A-Za-z0-9_-]+)-image-([A-Za-z0-9_-]+)\\.img$")

    fun owner(fileName: String): String? = managedName.matchEntire(fileName)?.groupValues?.get(1)

    fun name(ownerId: String, reference: JSONObject): String? {
        val local = reference.optString("fileName")
        if (owner(local) == ownerId) return local
        val asset = reference.optString("assetId").takeIf { it.matches(Regex("asset_[A-Za-z0-9_-]+")) } ?: return null
        return "$ownerId-image-$asset.img".takeIf { owner(it) == ownerId }
    }

    fun persist(directory: File, ownerId: String, images: List<RenderedPageImage>): JSONArray = JSONArray().apply {
        images.forEach { image ->
            val bytes = Base64.decode(image.base64, Base64.DEFAULT)
            require(bytes.isNotEmpty() && bytes.size <= MaxAiImportFileBytes) { "导入历史图片大小无效" }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val name = "$ownerId-image-$hash.img"
            val file = File(directory, name)
            if (!file.isFile || file.length() != bytes.size.toLong()) writeHistoryAsset(file, bytes)
            put(JSONObject().put("pageIndex", image.pageIndex).put("mimeType", image.mimeType).put("fileName", name))
        }
    }

    fun file(directory: File, ownerId: String, reference: JSONObject): File? {
        val name = name(ownerId, reference) ?: return null
        return File(directory, name).takeIf { it.isFile && it.length() in 1..MaxAiImportFileBytes.toLong() }
    }

    fun load(directory: File, ownerId: String, references: JSONArray): List<RenderedPageImage> = buildList {
        for (index in 0 until references.length()) {
            val reference = references.optJSONObject(index) ?: continue
            val asset = file(directory, ownerId, reference) ?: continue
            add(RenderedPageImage(reference.optInt("pageIndex", index), reference.optString("mimeType", "image/jpeg"),
                Base64.encodeToString(asset.readBytes(), Base64.NO_WRAP)))
        }
    }
}

internal fun writeHistoryAsset(target: File, bytes: ByteArray) {
    val atomic = AtomicFile(target)
    val output = atomic.startWrite()
    try {
        output.write(bytes)
        atomic.finishWrite(output)
    } catch (error: Throwable) {
        atomic.failWrite(output)
        throw error
    }
}
