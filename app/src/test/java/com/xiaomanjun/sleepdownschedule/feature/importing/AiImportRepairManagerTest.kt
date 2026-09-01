package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.defaultConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImportRepairManagerTest {
    @Test
    fun malformedOutputIsRepairedOnFirstRound() = runBlocking {
        var repairRequests = 0

        val result = AiImportRepairManager.parseWithRepair(
            initialResult = AiScheduleImportResult("{not-json", "test"),
            scheduleConfig = defaultConfig(),
            requestRepair = { _, failure, attempt ->
                repairRequests += 1
                assertEquals(AiImportParseErrorType.JSON_PARSE_ERROR, failure.errorType)
                assertEquals(1, attempt)
                Result.success(AiScheduleImportResult(validScheduleJson(), "repair"))
            }
        ).getOrThrow()

        assertEquals(1, repairRequests)
        assertEquals(1, result.repairAttempts)
        assertEquals("高等数学", result.draft.courses.single().name)
    }

    @Test
    fun repeatedInvalidOutputStopsAfterThreeRepairRequests() = runBlocking {
        var repairRequests = 0

        val result = AiImportRepairManager.parseWithRepair(
            initialResult = AiScheduleImportResult("{broken", "test"),
            scheduleConfig = defaultConfig(),
            requestRepair = { _, _, _ ->
                repairRequests += 1
                Result.success(AiScheduleImportResult("{still-broken", "repair"))
            }
        )

        assertTrue(result.isFailure)
        assertEquals(3, repairRequests)
        assertEquals(
            AiImportRepairManager.UserFacingFormatError,
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun schemaFailureReportsMissingCourseFieldWithoutLoggingPayload() {
        val output = validScheduleJson().replace("\"name\":\"高等数学\",", "")
        val parseError = ScheduleImportParser.parse(output, defaultConfig()).exceptionOrNull()
        assertTrue(parseError != null)

        val failure = AiImportRepairManager.classifyFailure(output, checkNotNull(parseError))

        assertEquals(AiImportParseErrorType.SCHEMA_VALIDATION_ERROR, failure.errorType)
        assertEquals("courses[0].name", failure.field)
        val prompt = AiImportRepairManager.buildRepairPrompt(output, failure)
        assertTrue(prompt.contains("SCHEMA_VALIDATION_ERROR"))
        assertTrue(prompt.contains("courses[0].name"))
        assertTrue(prompt.contains("只调用 IMPORT_SCHEDULE"))
        assertFalse(prompt.contains("原始文件"))
    }

    private fun validScheduleJson(): String = """
        {
          "schemaVersion":1,
          "scheduleConfig":{
            "totalWeeks":16,
            "periods":[{"index":1,"startTime":"08:00","endTime":"08:45"}]
          },
          "courses":[{
            "name":"高等数学",
            "teacher":"张老师",
            "location":"A101",
            "weekday":1,
            "periods":[1],
            "weeks":[1,2,3],
            "weekParity":"ALL",
            "note":null
          }]
        }
    """.trimIndent()
}
