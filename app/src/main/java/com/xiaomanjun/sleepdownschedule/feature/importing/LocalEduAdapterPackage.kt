package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One canonical single-school warehouse, including the actual protocol-v2 compiled index. */
internal object LocalEduAdapterPackage {
    data class Decoded(val adapter: EduAdapter, val source: String)

    fun encode(adapter: EduAdapter, source: String): ByteArray {
        val files = yamlFiles(adapter) + linkedMapOf(
            "resources/${adapter.school.folder}/${adapter.assetJsPath}" to source.toByteArray(Charsets.UTF_8),
            "school_index.pb" to index(adapter)
        )
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip -> files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            } }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size <= 1_048_576) { "本地适配包超过大小限制" }
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && files.size < 4 && entry.name !in files) { "本地适配包目录无效" }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 262_144) { "本地适配资源超过256KiB" }
                    output.write(buffer, 0, count)
                }
                files[entry.name] = output.toByteArray()
            }
        }
        val adapter = ShiguangWarehouse.parseProtocolV2Snapshot(checkNotNull(files["school_index.pb"])).adapters.single()
        require(adapter.school.folder.matches(Regex("[A-Za-z0-9_-]{1,100}")) && adapter.assetJsPath == "adapter.js")
        yamlFiles(adapter).forEach { (path, expected) ->
            require(files[path]?.contentEquals(expected) == true) { "适配元数据与已编译索引不一致" }
        }
        val scriptPath = "resources/${adapter.school.folder}/${adapter.assetJsPath}"
        val source = requireNotNull(files[scriptPath]) { "适配脚本缺失" }.toString(Charsets.UTF_8)
        return Decoded(adapter, source)
    }

    private fun yamlFiles(adapter: EduAdapter): Map<String, ByteArray> {
        fun quote(value: String) = JsonPrimitive(value).toString()
        val school = adapter.school
        val root = """schools:
  - id: ${quote(school.id)}
    name: ${quote(school.name)}
    initial: ${quote(school.initial)}
    resource_folder: ${quote(school.folder)}
"""
        val metadata = """adapters:
  - adapter_id: ${quote(adapter.adapterId)}
    adapter_name: ${quote(adapter.adapterName)}
    category: ${quote(adapter.category)}
    asset_js_path: ${quote(adapter.assetJsPath)}
    import_url: ${quote(adapter.importUrl)}
    maintainer: ${quote(adapter.maintainer)}
    description: ${quote(adapter.description)}
"""
        return linkedMapOf("index/root_index.yaml" to root.toByteArray(Charsets.UTF_8),
            "resources/${school.folder}/adapters.yaml" to metadata.toByteArray(Charsets.UTF_8))
    }

    // Field numbers and enum values follow upstream proto/school_index.proto v2.
    private fun index(adapter: EduAdapter): ByteArray = message {
        number(1, 2); text(2, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("'TIME_'yyyyMMddHHmmss_SSS", java.util.Locale.ROOT)))
        data(3, message {
            text(1, adapter.school.id); text(2, adapter.school.name)
            text(3, adapter.school.initial); text(4, adapter.school.folder)
            data(5, message {
                text(1, adapter.adapterId); text(2, adapter.adapterName)
                number(3, when (adapter.category) { "GENERAL_TOOL" -> 1; "BACHELOR_AND_ASSOCIATE" -> 2; "POSTGRADUATE" -> 3; else -> error("适配分类无效") })
                text(4, adapter.assetJsPath); text(5, adapter.importUrl)
                text(6, adapter.description); text(7, adapter.maintainer)
            })
        })
    }

    private fun message(write: Writer.() -> Unit): ByteArray = Writer().apply(write).bytes()
    private class Writer {
        private val output = ByteArrayOutputStream()
        private fun varint(value: Int) {
            var remaining = value
            while (remaining > 127) { output.write((remaining and 127) or 128); remaining = remaining ushr 7 }
            output.write(remaining)
        }
        fun number(field: Int, value: Int) { varint(field shl 3); varint(value) }
        fun text(field: Int, value: String) = data(field, value.toByteArray(Charsets.UTF_8))
        fun data(field: Int, value: ByteArray) { varint((field shl 3) or 2); varint(value.size); output.write(value) }
        fun bytes(): ByteArray = output.toByteArray()
    }
}
