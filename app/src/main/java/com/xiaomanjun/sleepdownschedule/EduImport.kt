package com.xiaomanjun.sleepdownschedule

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
    private val quotedValue = Regex("""^\s*([A-Za-z_]+):\s*"?(.*?)"?\s*(?:#.*)?$""")

    fun loadAdapters(context: Context): List<EduAdapter> {
        val schools = parseSchools(context.assets.open("$Root/index/root_index.yaml").bufferedReader().readText())
        val warehouseAdapters = schools.flatMap { school ->
            runCatching {
                parseAdapters(
                    school = school,
                    text = context.assets.open("$Root/resources/${school.folder}/adapters.yaml").bufferedReader().readText()
                )
            }.getOrDefault(emptyList())
        }.map { adapter ->
            if (adapter.isEduTestTool()) adapter.copy(importUrl = EDU_BRIDGE_TEST_PAGE_URL) else adapter
        }.filter { it.assetJsPath.isNotBlank() && (it.importUrl.isNotBlank() || it.isGeneralEduTool()) }
            .sortedWith(compareBy<EduAdapter> { it.school.initial }.thenBy { it.school.name }.thenBy { it.adapterName })
        return listOf(aiEduImportAdapter()) + warehouseAdapters
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
}

class EduImportBridge(
    private val context: Context,
    private val adapter: EduAdapter? = null,
    private val baseConfig: () -> ScheduleConfigEntity,
    private val basePeriods: () -> List<PeriodEntity> = { defaultPeriods() },
    private val onDraft: (ImportDraft) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onTaskCompleted: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val taskLock = Any()
    private var configJson: String? = null
    private var coursesJson: String? = null
    private var timeSlotsJson: String? = null
    private var taskCompletionCount: Int = 0
    private var completionDelivered: Boolean = false

    fun taskCompletionCount(): Int = synchronized(taskLock) { taskCompletionCount }

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
            taskCompletionCount += 1
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

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onMessage(message)
        }
    }

    @JavascriptInterface
    fun showAlert(title: String?, message: String?, confirmText: String?): Boolean {
        val text = listOfNotNull(title, message).joinToString("：").ifBlank { confirmText ?: "确定" }
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            onMessage(text)
        }
        return true
    }

    @JavascriptInterface
    fun showPrompt(title: String?, message: String?, defaultValue: String?, validator: String?): String {
        val text = listOfNotNull(title, message).joinToString("：")
        if (text.isNotBlank()) {
            mainHandler.post { onMessage(text) }
        }
        return defaultValue.orEmpty()
    }

    @JavascriptInterface
    fun showSingleSelection(title: String?, optionsJson: String?, defaultIndex: Int): Int {
        val size = runCatching { JSONArray(optionsJson ?: "[]").length() }.getOrDefault(0)
        val selected = when {
            size <= 0 -> -1
            defaultIndex in 0 until size -> defaultIndex
            else -> 0
        }
        if (!title.isNullOrBlank()) {
            mainHandler.post { onMessage("$title：已自动选择第 ${selected + 1} 项") }
        }
        return selected
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
