package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.security.MessageDigest

data class EduSchool(
    val id: String,
    val name: String,
    val folder: String,
    val initial: String = "#"
)

data class EduAdapter(
    val school: EduSchool,
    val adapterId: String,
    val adapterName: String,
    val category: String,
    val assetJsPath: String,
    val importUrl: String,
    val maintainer: String,
    val description: String,
    val warehouseGeneration: String = ""
) {
    val displayName: String = "${school.name} · $adapterName"
}

sealed interface EduBridgeInteractionRequest {
    val requestId: String

    data class Alert(
        override val requestId: String,
        val title: String,
        val message: String,
        val confirmText: String
    ) : EduBridgeInteractionRequest

    data class Prompt(
        override val requestId: String,
        val title: String,
        val message: String,
        val defaultValue: String,
        val validator: String?
    ) : EduBridgeInteractionRequest

    data class SingleSelection(
        override val requestId: String,
        val title: String,
        val options: List<String>,
        val defaultIndex: Int
    ) : EduBridgeInteractionRequest
}

fun EduAdapter.toIntentKey(): String = listOf(
    school.id,
    school.name,
    school.folder,
    school.initial,
    adapterId,
    adapterName,
    category,
    assetJsPath,
    importUrl,
    maintainer,
    description,
    warehouseGeneration
).joinToString("\u001F")

fun eduAdapterFromIntentKey(value: String?): EduAdapter? {
    val parts = value?.split("\u001F") ?: return null
    if (parts.size < 11) return null
    return EduAdapter(
        school = EduSchool(parts[0], parts[1], parts[2], parts[3]),
        adapterId = parts[4],
        adapterName = parts[5],
        category = parts[6],
        assetJsPath = parts[7],
        importUrl = parts[8],
        maintainer = parts[9],
        description = parts[10],
        warehouseGeneration = parts.getOrElse(11) { "" }
    )
}

fun EduAdapter.isGeneralEduTool(): Boolean {
    return category == "GENERAL_TOOL"
}

fun EduAdapter.isManualShareCodeTool(): Boolean {
    if (!isGeneralEduTool() || !school.id.equals("GLOBAL_TOOLS", ignoreCase = true)) {
        return false
    }
    return adapterId.equals("WakeUp", ignoreCase = true) ||
        adapterId.equals("StarLink", ignoreCase = true)
}

fun EduAdapter.isDevelopmentOnlyGeneralTool(): Boolean {
    if (!isGeneralEduTool() || !school.id.equals("GLOBAL_TOOLS", ignoreCase = true)) {
        return false
    }
    return adapterId.equals("GENERAL_TOOL_01", ignoreCase = true) ||
        adapterId.equals("GENERAL_TOOL_02", ignoreCase = true)
}

fun EduAdapter.isAiEduImportTool(): Boolean {
    return school.id == "AI_EDU_IMPORT" && adapterId == "AI_EDU_IMPORT"
}

fun EduAdapter.requiresManualEduUrl(): Boolean {
    return isAiEduImportTool()
}

fun aiEduImportAdapter(): EduAdapter = EduAdapter(
    school = EduSchool(
        id = "AI_EDU_IMPORT",
        name = "AI教务导入",
        folder = "GLOBAL_TOOLS",
        initial = "#"
    ),
    adapterId = "AI_EDU_IMPORT",
    adapterName = "AI解析当前教务页面",
    category = "AI_EDU",
    assetJsPath = "",
    importUrl = "",
    maintainer = "SleepDown",
    description = "打开学校教务系统课表页后，使用 AI 解析当前页面内容。"
)

object ShiguangWarehouse {
    private const val Root = "shiguang_warehouse-main"
    private const val ProtocolV2 = 2
    private val quotedValue = Regex("""^\s*([A-Za-z_]+):\s*"?(.*?)"?\s*(?:#.*)?$""")

    fun loadAdapters(context: Context): List<EduAdapter> {
        val officialAdapters = runCatching {
            ShiguangWarehouseUpdater.cachedIndexFile(context)
                .takeIf { it.isFile }
                ?.readBytes()
                ?.let(::parseProtocolV2Snapshot)
                ?.adapters
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: loadBundledAdapters(context)
        val warehouseAdapters = officialAdapters
            .filter { it.category in OfficialCategories }
            .sortedWith(compareBy<EduAdapter> { it.school.initial }.thenBy { it.school.name }.thenBy { it.adapterName })
        return listOf(aiEduImportAdapter()) + warehouseAdapters
    }

    private fun loadBundledAdapters(context: Context): List<EduAdapter> {
        val protocolAdapters = runCatching {
            context.assets.open("$Root/school_index.pb").use { input ->
                parseProtocolV2Snapshot(input.readBytes()).adapters
            }
        }.getOrNull()
        return protocolAdapters?.takeIf { it.isNotEmpty() } ?: loadLegacyYamlAdapters(context)
    }

    private val OfficialCategories = setOf(
        "GENERAL_TOOL",
        "BACHELOR_AND_ASSOCIATE",
        "POSTGRADUATE"
    )

    private fun loadLegacyYamlAdapters(context: Context): List<EduAdapter> {
        val schools = parseSchools(
            context.assets.open("$Root/index/root_index.yaml").bufferedReader().use { it.readText() }
        )
        return schools.flatMap { school ->
            runCatching {
                parseAdapters(
                    school = school,
                    text = context.assets.open("$Root/resources/${school.folder}/adapters.yaml")
                        .bufferedReader()
                        .use { it.readText() }
                )
            }.getOrDefault(emptyList())
        }
    }

    suspend fun resolveScript(context: Context, adapter: EduAdapter): String = withContext(Dispatchers.IO) {
        val relativePath = ShiguangWarehouseUpdater.resourceRelativePath(adapter)
        var remoteFailure: Exception? = null
        if (ShiguangWarehouseUpdater.hasValidRemoteIndex(context)) {
            try {
                return@withContext ShiguangWarehouseUpdater.resolveRemoteScript(context, adapter)
            } catch (error: Exception) {
                remoteFailure = error
            }
        }
        try {
            context.assets.open("$Root/resources/$relativePath").bufferedReader().use { it.readText() }
        } catch (bundledFailure: Exception) {
            throw IOException(
                "无法获取拾光脚本 $relativePath：${remoteFailure?.message ?: bundledFailure.message}",
                remoteFailure ?: bundledFailure
            )
        }
    }

    private fun parseSchools(text: String): List<EduSchool> {
        val result = mutableListOf<EduSchool>()
        var current = linkedMapOf<String, String>()
        fun flush() {
            val id = current["id"]
            val name = current["name"]
            val folder = current["resource_folder"]
            val initial = current["initial"].orEmpty().ifBlank { "#" }
            if (!id.isNullOrBlank() && !name.isNullOrBlank() && !folder.isNullOrBlank()) {
                result += EduSchool(id, name, folder, initial)
            }
            current = linkedMapOf()
        }
        text.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("- ")) {
                flush()
            }
            val normalized = line.replaceFirst("- ", "")
            val match = quotedValue.find(normalized) ?: return@forEach
            current[match.groupValues[1]] = match.groupValues[2].trim()
        }
        flush()
        return result
    }

    private fun parseAdapters(school: EduSchool, text: String): List<EduAdapter> {
        val result = mutableListOf<EduAdapter>()
        var current = linkedMapOf<String, String>()
        fun flush() {
            val adapterId = current["adapter_id"]
            val adapterName = current["adapter_name"]
            val category = current["category"]
            val assetJsPath = current["asset_js_path"]
            val importUrl = current["import_url"]
            if (!adapterId.isNullOrBlank() && !adapterName.isNullOrBlank() && !category.isNullOrBlank() && assetJsPath != null && importUrl != null) {
                result += EduAdapter(
                    school = school,
                    adapterId = adapterId,
                    adapterName = adapterName,
                    category = category,
                    assetJsPath = assetJsPath,
                    importUrl = importUrl,
                    maintainer = current["maintainer"].orEmpty(),
                    description = current["description"].orEmpty()
                )
            }
            current = linkedMapOf()
        }
        text.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("- ")) {
                flush()
            }
            val normalized = line.replaceFirst("- ", "")
            val match = quotedValue.find(normalized) ?: return@forEach
            current[match.groupValues[1]] = match.groupValues[2].trim()
        }
        flush()
        return result
    }

    /** Parses one immutable official protocol-v2 warehouse snapshot. */
    internal fun parseProtocolV2Snapshot(bytes: ByteArray): ShiguangWarehouseSnapshot {
        val reader = ProtoReader(bytes)
        var protocolVersion = 0
        var versionId = ""
        val schools = mutableListOf<ProtocolSchool>()
        while (!reader.exhausted) {
            val tag = reader.readTag()
            when (tag.fieldNumber) {
                1 -> protocolVersion = reader.readInt32(tag.wireType)
                2 -> versionId = reader.readString(tag.wireType)
                3 -> schools += parseProtocolSchool(reader.readMessage(tag.wireType))
                else -> reader.skip(tag.wireType)
            }
        }
        require(protocolVersion == ProtocolV2) {
            "不支持的拾光仓库索引协议：v$protocolVersion（需要 v$ProtocolV2）"
        }
        require(schools.isNotEmpty()) { "拾光仓库 v2 索引中没有学校数据" }
        val indexSha = bytes.sha256Hex()
        val adapters = schools.flatMap { record ->
            val school = EduSchool(
                id = record.id,
                name = record.name,
                folder = record.resourceFolder,
                initial = record.initial.ifBlank { "#" }
            )
            record.adapters.map { adapter ->
                EduAdapter(
                    school = school,
                    adapterId = adapter.id,
                    adapterName = adapter.name,
                    category = adapter.category,
                    assetJsPath = adapter.assetJsPath,
                    importUrl = adapter.importUrl,
                    maintainer = adapter.maintainer,
                    description = adapter.description,
                    warehouseGeneration = indexSha
                )
            }
        }
        require(adapters.isNotEmpty()) { "拾光仓库 v2 索引中没有适配器" }
        return ShiguangWarehouseSnapshot(
            protocolVersion = protocolVersion,
            versionId = versionId,
            indexSha = indexSha,
            adapters = adapters
        )
    }

    private fun parseProtocolSchool(reader: ProtoReader): ProtocolSchool {
        var id = ""
        var name = ""
        var initial = "#"
        var resourceFolder = ""
        val adapters = mutableListOf<ProtocolAdapter>()
        while (!reader.exhausted) {
            val tag = reader.readTag()
            when (tag.fieldNumber) {
                1 -> id = reader.readString(tag.wireType)
                2 -> name = reader.readString(tag.wireType)
                3 -> initial = reader.readString(tag.wireType)
                4 -> resourceFolder = reader.readString(tag.wireType)
                5 -> adapters += parseProtocolAdapter(reader.readMessage(tag.wireType))
                else -> reader.skip(tag.wireType)
            }
        }
        require(id.isNotBlank() && name.isNotBlank() && resourceFolder.isNotBlank()) {
            "拾光仓库 v2 索引包含不完整的学校记录"
        }
        return ProtocolSchool(id, name, initial, resourceFolder, adapters)
    }

    private fun parseProtocolAdapter(reader: ProtoReader): ProtocolAdapter {
        var id = ""
        var name = ""
        var category = "ADAPTER_CATEGORY_UNKNOWN"
        var assetJsPath = ""
        var importUrl = ""
        var description = ""
        var maintainer = ""
        while (!reader.exhausted) {
            val tag = reader.readTag()
            when (tag.fieldNumber) {
                1 -> id = reader.readString(tag.wireType)
                2 -> name = reader.readString(tag.wireType)
                3 -> category = when (reader.readInt32(tag.wireType)) {
                    1 -> "GENERAL_TOOL"
                    2 -> "BACHELOR_AND_ASSOCIATE"
                    3 -> "POSTGRADUATE"
                    else -> "ADAPTER_CATEGORY_UNKNOWN"
                }
                4 -> assetJsPath = reader.readString(tag.wireType)
                5 -> importUrl = reader.readString(tag.wireType)
                6 -> description = reader.readString(tag.wireType)
                7 -> maintainer = reader.readString(tag.wireType)
                else -> reader.skip(tag.wireType)
            }
        }
        require(id.isNotBlank() && name.isNotBlank()) {
            "拾光仓库 v2 索引包含不完整的适配器记录"
        }
        return ProtocolAdapter(id, name, category, assetJsPath, importUrl, description, maintainer)
    }

    private data class ProtocolSchool(
        val id: String,
        val name: String,
        val initial: String,
        val resourceFolder: String,
        val adapters: List<ProtocolAdapter>
    )

    private data class ProtocolAdapter(
        val id: String,
        val name: String,
        val category: String,
        val assetJsPath: String,
        val importUrl: String,
        val description: String,
        val maintainer: String
    )

    private data class ProtoTag(val fieldNumber: Int, val wireType: Int)

    /** Minimal bounded protobuf reader for the stable fields used by school_index.proto. */
    private class ProtoReader(private val bytes: ByteArray) {
        private var position = 0
        val exhausted: Boolean get() = position >= bytes.size

        fun readTag(): ProtoTag {
            val raw = readVarint().toInt()
            require(raw != 0) { "拾光仓库 v2 索引包含无效字段标签" }
            return ProtoTag(fieldNumber = raw ushr 3, wireType = raw and 0x07)
        }

        fun readInt32(wireType: Int): Int {
            require(wireType == 0) { "拾光仓库 v2 索引字段类型不匹配" }
            return readVarint().toInt()
        }

        fun readString(wireType: Int): String = readBytes(wireType).toString(Charsets.UTF_8)

        fun readMessage(wireType: Int): ProtoReader = ProtoReader(readBytes(wireType))

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> advance(8)
                2 -> advance(readLength())
                5 -> advance(4)
                else -> error("拾光仓库 v2 索引使用了不支持的字段类型：$wireType")
            }
        }

        private fun readBytes(wireType: Int): ByteArray {
            require(wireType == 2) { "拾光仓库 v2 索引字段类型不匹配" }
            val length = readLength()
            val end = position + length
            if (length < 0 || end < position || end > bytes.size) throw EOFException("拾光仓库 v2 索引已截断")
            return bytes.copyOfRange(position, end).also { position = end }
        }

        private fun readLength(): Int {
            val length = readVarint()
            require(length <= Int.MAX_VALUE) { "拾光仓库 v2 索引字段过大" }
            return length.toInt()
        }

        private fun advance(count: Int) {
            val end = position + count
            if (count < 0 || end < position || end > bytes.size) throw EOFException("拾光仓库 v2 索引已截断")
            position = end
        }

        private fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (position >= bytes.size) throw EOFException("拾光仓库 v2 索引已截断")
                val value = bytes[position++].toInt() and 0xFF
                result = result or ((value and 0x7F).toLong() shl shift)
                if ((value and 0x80) == 0) return result
                shift += 7
            }
            error("拾光仓库 v2 索引包含过长的 varint")
        }
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}

internal data class ShiguangWarehouseSnapshot(
    val protocolVersion: Int,
    val versionId: String,
    val indexSha: String,
    val adapters: List<EduAdapter>
)
