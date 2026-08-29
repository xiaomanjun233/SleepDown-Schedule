package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    val description: String
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
    description
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
        description = parts[10]
    )
}

fun EduAdapter.isGeneralEduTool(): Boolean {
    return school.id in setOf("zhengfang_jiaowu", "chaoxing_jiaowu", "qingguo_jiaowu", "urp_jiaowu")
}

fun EduAdapter.isAiEduImportTool(): Boolean {
    return school.id == "AI_EDU_IMPORT" && adapterId == "AI_EDU_IMPORT"
}

fun EduAdapter.requiresManualEduUrl(): Boolean {
    return isGeneralEduTool() || isAiEduImportTool()
}

fun EduAdapter.isEduTestTool(): Boolean {
    return school.id == "GLOBAL_TOOLS" && adapterId == "GENERAL_TOOL_01"
}

fun EduAdapter.isWakeUpImportTool(): Boolean {
    return school.id == "GLOBAL_TOOLS" && adapterId.equals("WakeUp", ignoreCase = true)
}

fun EduAdapter.isStarLinkImportTool(): Boolean {
    return school.id == "GLOBAL_TOOLS" && adapterId.equals("StarLink", ignoreCase = true)
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
        val warehouseAdapters = runCatching {
            val pbAdapters = context.assets.open("$Root/school_index.pb").use { input ->
                parseProtocolV2Index(input.readBytes())
            }
            // The official v2 protobuf index is synced from upstream and does not carry private
            // SleepDown adaptations (e.g. 西南大学/武汉科技大学). Merge bundled YAML entries for
            // schools missing from the protobuf so private forks keep their adapters.
            val pbSchoolIds = pbAdapters.map { it.school.id }.toSet()
            pbAdapters + loadLegacyYamlAdapters(context).filter { it.school.id !in pbSchoolIds }
        }.getOrElse {
            // Keep the source YAML path as a compatibility fallback for development snapshots and
            // private warehouse forks that have not published the v2 protobuf artifact yet.
            loadLegacyYamlAdapters(context)
        }.map { adapter ->
            if (adapter.isEduTestTool()) adapter.copy(importUrl = EDU_BRIDGE_TEST_PAGE_URL) else adapter
        }.filter { it.assetJsPath.isNotBlank() && (it.importUrl.isNotBlank() || it.isGeneralEduTool()) }
            .sortedWith(compareBy<EduAdapter> { it.school.initial }.thenBy { it.school.name }.thenBy { it.adapterName })
        return listOf(aiEduImportAdapter()) + warehouseAdapters
    }

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

    fun loadScript(context: Context, adapter: EduAdapter): String {
        val path = if (adapter.assetJsPath.contains("/")) {
            "$Root/resources/${adapter.assetJsPath}"
        } else {
            "$Root/resources/${adapter.school.folder}/${adapter.assetJsPath}"
        }
        return context.assets.open(path).bufferedReader().readText()
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

    /** Parses the official shiguang_warehouse protocol-v2 protobuf without a codegen runtime. */
    internal fun parseProtocolV2Index(bytes: ByteArray): List<EduAdapter> {
        val reader = ProtoReader(bytes)
        var protocolVersion = 0
        val schools = mutableListOf<ProtocolSchool>()
        while (!reader.exhausted) {
            val tag = reader.readTag()
            when (tag.fieldNumber) {
                1 -> protocolVersion = reader.readInt32(tag.wireType)
                2 -> reader.readString(tag.wireType) // version_id is informational at runtime.
                3 -> schools += parseProtocolSchool(reader.readMessage(tag.wireType))
                else -> reader.skip(tag.wireType)
            }
        }
        require(protocolVersion == ProtocolV2) {
            "不支持的拾光仓库索引协议：v$protocolVersion（需要 v$ProtocolV2）"
        }
        require(schools.isNotEmpty()) { "拾光仓库 v2 索引中没有学校数据" }
        return schools.flatMap { record ->
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
                    description = adapter.description
                )
            }
        }
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
}

class EduImportBridge(
    private val context: Context,
    private val adapter: EduAdapter? = null,
    private val baseConfig: () -> ScheduleConfigEntity,
    private val basePeriods: () -> List<PeriodEntity> = { defaultPeriods() },
    private val onDraft: (ImportDraft) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onInteractionRequest: (EduBridgeInteractionRequest) -> Unit = {},
    private val onTaskCompleted: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val taskLock = Any()
    private var configJson: String? = null
    private var coursesJson: String? = null
    private var timeSlotsJson: String? = null
    private var completionDelivered: Boolean = false

    fun beginTask() {
        synchronized(taskLock) {
            configJson = null
            coursesJson = null
            timeSlotsJson = null
            completionDelivered = false
        }
    }

    @JavascriptInterface
    fun saveCourseConfig(json: String): Boolean {
        synchronized(taskLock) { configJson = json }
        return true
    }

    @JavascriptInterface
    fun saveImportedCourses(json: String): Boolean {
        synchronized(taskLock) { coursesJson = json }
        return true
    }

    @JavascriptInterface
    fun savePresetTimeSlots(json: String): Boolean {
        synchronized(taskLock) { timeSlotsJson = json }
        return true
    }

    @JavascriptInterface
    fun notifyTaskCompletion() {
        val payload = synchronized(taskLock) {
            if (completionDelivered) return
            completionDelivered = true
            Triple(configJson, coursesJson, timeSlotsJson ?: "[]")
        }
        val (config, courses, slots) = payload
        if (courses == null) {
            mainHandler.post {
                try {
                    onMessage("导入脚本没有返回课程数据，可以尝试 AI 兜底扒页。")
                } finally {
                    runCatching(onTaskCompleted)
                }
            }
            return
        }
        runCatching {
            ShiguangImportMapper.toDraft(adapter, baseConfig(), basePeriods(), config ?: "{}", courses, slots)
        }.onSuccess {
            mainHandler.post {
                try {
                    onDraft(it)
                } finally {
                    runCatching(onTaskCompleted)
                }
            }
        }.onFailure {
            mainHandler.post {
                try {
                    onMessage(it.message ?: "教务数据解析失败")
                } finally {
                    runCatching(onTaskCompleted)
                }
            }
        }
    }

    /** Terminates a script-driven import without fabricating an empty course payload. */
    @JavascriptInterface
    fun reportTaskFailure(message: String?) {
        synchronized(taskLock) {
            if (completionDelivered) return
            completionDelivered = true
        }
        mainHandler.post {
            try {
                onMessage(message?.takeIf { it.isNotBlank() } ?: "导入脚本执行失败")
            } finally {
                runCatching(onTaskCompleted)
            }
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onMessage(message)
        }
    }

    @JavascriptInterface
    fun requestAlert(requestId: String, title: String?, message: String?, confirmText: String?) {
        mainHandler.post {
            onInteractionRequest(
                EduBridgeInteractionRequest.Alert(
                    requestId = requestId,
                    title = title.orEmpty().ifBlank { "提示" },
                    message = message.orEmpty(),
                    confirmText = confirmText.orEmpty().ifBlank { "确定" }
                )
            )
        }
    }

    @JavascriptInterface
    fun requestPrompt(
        requestId: String,
        title: String?,
        message: String?,
        defaultValue: String?,
        validator: String?
    ) {
        mainHandler.post {
            onInteractionRequest(
                EduBridgeInteractionRequest.Prompt(
                    requestId = requestId,
                    title = title.orEmpty().ifBlank { "请输入" },
                    message = message.orEmpty(),
                    defaultValue = defaultValue.orEmpty(),
                    validator = validator?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    @JavascriptInterface
    fun requestSingleSelection(
        requestId: String,
        title: String?,
        optionsJson: String?,
        defaultIndex: Int
    ) {
        val options = runCatching {
            val array = JSONArray(optionsJson ?: "[]")
            List(array.length()) { index -> array.optString(index) }
        }.getOrDefault(emptyList())
        mainHandler.post {
            onInteractionRequest(
                EduBridgeInteractionRequest.SingleSelection(
                    requestId = requestId,
                    title = title.orEmpty().ifBlank { "请选择" },
                    options = options,
                    defaultIndex = defaultIndex.takeIf { it in options.indices } ?: -1
                )
            )
        }
    }
}

object ShiguangImportMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toDraft(
        adapter: EduAdapter?,
        baseConfig: ScheduleConfigEntity,
        basePeriods: List<PeriodEntity>,
        configJson: String,
        coursesJson: String,
        timeSlotsJson: String
    ): ImportDraft {
        val config = json.decodeFromString(ShiguangCourseConfig.serializer(), configJson)
        val slots = json.decodeFromString(ListSerializer(ShiguangTimeSlot.serializer()), timeSlotsJson)
        val courses = json.decodeFromString(ListSerializer(ShiguangCourse.serializer()), coursesJson)
        val basePeriodMap = basePeriods.ifEmpty { defaultPeriods() }.associateBy { it.periodIndex }
        val importedPeriods = slots.mapNotNull {
            val number = it.number ?: return@mapNotNull null
            val start = it.startTime.orEmpty()
            val end = it.endTime.orEmpty()
            if (start.isBlank() || end.isBlank()) return@mapNotNull null
            if (!isPlausibleImportedPeriod(start, end, baseConfig.classDurationMinutes)) return@mapNotNull null
            if (basePeriodMap[number]?.let { base -> isLikelyWrongImportedPeriod(number, start, end, base) } == true) return@mapNotNull null
            PeriodEntity(number, start, end)
        }.distinctBy { it.periodIndex }.sortedBy { it.periodIndex }
        val totalWeeks = (config.totalWeeks ?: config.semesterTotalWeeks ?: courses.flatMap { it.normalizedWeeks() }.maxOrNull() ?: baseConfig.totalWeeks).coerceAtLeast(1)
        val mappedCourses = courses.mapNotNull { course ->
            val day = course.day ?: course.dayOfWeek ?: course.weekday ?: return@mapNotNull null
            val sectionRange = course.normalizedSections() ?: return@mapNotNull null
            val weeks = course.normalizedWeeks().filter { it > 0 }.ifEmpty { (1..totalWeeks).toList() }
            CourseEntity(
                name = (course.name ?: course.courseName).orEmpty().ifBlank { "未命名课程" },
                teacher = (course.teacher ?: course.teachers?.joinToString("、"))?.ifBlank { null },
                location = (course.position ?: course.classroom ?: course.location ?: course.room)?.ifBlank { null },
                weekday = day.coerceIn(1, 7),
                periods = (sectionRange.first..sectionRange.last).toList(),
                weeks = weeks,
                weekParity = WeekParity.ALL,
                note = null
            )
        }
        val maxCoursePeriod = mappedCourses.flatMap { it.periods }.maxOrNull() ?: 0
        val forcedPeriods = forcedSchoolPeriods(adapter)
        val periods = expandImportedPeriods(
            imported = if (forcedPeriods != null) emptyList() else importedPeriods,
            base = forcedPeriods ?: basePeriods.ifEmpty { defaultPeriods() },
            requiredMaxPeriod = maxCoursePeriod,
            classDurationMinutes = baseConfig.classDurationMinutes,
            breakDurationMinutes = baseConfig.breakDurationMinutes
        )
        return ImportDraft(
            config = baseConfig.copy(
                totalWeeks = totalWeeks,
                termStartDate = config.semesterStartDate ?: baseConfig.termStartDate
            ),
            periods = periods,
            courses = mappedCourses
        )
    }
}

private fun forcedSchoolPeriods(adapter: EduAdapter?): List<PeriodEntity>? {
    if (adapter?.school?.id != "SWU") return null
    return listOf(
        PeriodEntity(1, "08:00", "08:45"),
        PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "10:00", "10:45"),
        PeriodEntity(4, "10:55", "11:40"),
        PeriodEntity(5, "12:10", "12:55"),
        PeriodEntity(6, "13:05", "13:50"),
        PeriodEntity(7, "14:00", "14:45"),
        PeriodEntity(8, "14:55", "15:40"),
        PeriodEntity(9, "15:50", "16:35"),
        PeriodEntity(10, "16:55", "17:40"),
        PeriodEntity(11, "17:50", "18:35"),
        PeriodEntity(12, "19:20", "20:05"),
        PeriodEntity(13, "20:15", "21:00"),
        PeriodEntity(14, "21:10", "21:55")
    )
}

private val importTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun isPlausibleImportedPeriod(start: String, end: String, classDurationMinutes: Int): Boolean {
    val startTime = runCatching { LocalTime.parse(start, importTimeFormatter) }.getOrNull() ?: return false
    val endTime = runCatching { LocalTime.parse(end, importTimeFormatter) }.getOrNull() ?: return false
    val minutes = java.time.Duration.between(startTime, endTime).toMinutes()
    val expected = classDurationMinutes.coerceIn(1, 300)
    return minutes in 1..maxOf(90, expected * 2)
}

private fun isLikelyWrongImportedPeriod(index: Int, start: String, end: String, base: PeriodEntity): Boolean {
    val importedStart = runCatching { LocalTime.parse(start, importTimeFormatter) }.getOrNull() ?: return true
    val importedEnd = runCatching { LocalTime.parse(end, importTimeFormatter) }.getOrNull() ?: return true
    val baseStart = runCatching { LocalTime.parse(base.startTime, importTimeFormatter) }.getOrNull() ?: return false
    val baseEnd = runCatching { LocalTime.parse(base.endTime, importTimeFormatter) }.getOrNull() ?: return false
    val startOffset = kotlin.math.abs(java.time.Duration.between(baseStart, importedStart).toMinutes())
    val endOffset = kotlin.math.abs(java.time.Duration.between(baseEnd, importedEnd).toMinutes())
    return index >= 10 && (startOffset > 90 || endOffset > 90)
}

private fun expandImportedPeriods(
    imported: List<PeriodEntity>,
    base: List<PeriodEntity>,
    requiredMaxPeriod: Int,
    classDurationMinutes: Int,
    breakDurationMinutes: Int
): List<PeriodEntity> {
    val hasImportedPeriods = imported.isNotEmpty()
    val targetMax = if (hasImportedPeriods) {
        maxOf(requiredMaxPeriod, imported.maxOfOrNull { it.periodIndex } ?: 0)
    } else {
        maxOf(requiredMaxPeriod, base.maxOfOrNull { it.periodIndex } ?: 0)
    }
    if (targetMax <= 0) return defaultPeriods()
    val importedByIndex = imported.associateBy { it.periodIndex }
    val baseByIndex = base.associateBy { it.periodIndex }
    val result = mutableListOf<PeriodEntity>()
    for (index in 1..targetMax) {
        val existing = importedByIndex[index] ?: baseByIndex[index]
        if (existing != null) {
            result += existing
        } else {
            result += buildNextPeriod(index, result.lastOrNull(), classDurationMinutes, breakDurationMinutes)
        }
    }
    return result
}

private fun buildNextPeriod(index: Int, previous: PeriodEntity?, classDurationMinutes: Int, breakDurationMinutes: Int): PeriodEntity {
    val duration = classDurationMinutes.coerceIn(1, 300).toLong()
    val breakDuration = breakDurationMinutes.coerceIn(0, 300).toLong()
    val start = previous?.endTime
        ?.let { runCatching { LocalTime.parse(it, importTimeFormatter).plusMinutes(breakDuration) }.getOrNull() }
        ?: LocalTime.of(8, 0).plusMinutes((index - 1).coerceAtLeast(0).toLong() * (duration + breakDuration))
    val end = start.plusMinutes(duration)
    return PeriodEntity(index, start.format(importTimeFormatter), end.format(importTimeFormatter))
}

@Serializable
data class ShiguangCourseConfig(
    val semesterStartDate: String? = null,
    val totalWeeks: Int? = null,
    val semesterTotalWeeks: Int? = null
)

@Serializable
data class ShiguangTimeSlot(
    val number: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

@Serializable
data class ShiguangCourse(
    val name: String? = null,
    val courseName: String? = null,
    val teacher: String? = null,
    val teachers: List<String>? = null,
    val position: String? = null,
    val location: String? = null,
    val room: String? = null,
    val day: Int? = null,
    val dayOfWeek: Int? = null,
    val weekday: Int? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val sections: List<Int>? = null,
    val weeks: List<Int>? = null,
    val weekList: List<Int>? = null,
    @SerialName("classroom") val classroom: String? = null
)

private fun ShiguangCourse.normalizedSections(): IntRange? {
    val fromList = sections?.filter { it > 0 }?.sorted()
    if (!fromList.isNullOrEmpty()) return fromList.first()..fromList.last()
    val start = startSection ?: return null
    val end = endSection ?: start
    return minOf(start, end)..maxOf(start, end)
}

private fun ShiguangCourse.normalizedWeeks(): List<Int> {
    return (weeks ?: weekList).orEmpty()
}
