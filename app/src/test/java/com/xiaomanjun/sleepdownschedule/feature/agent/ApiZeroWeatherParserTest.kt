package com.xiaomanjun.sleepdownschedule.feature.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiZeroWeatherParserTest {
    @Test
    fun parsesCurrentSummaryAndFirstHourlyProbability() {
        val result = parseApiZeroWeatherPayload(
            payload = """
                {
                  "code": 0,
                  "data": {
                    "summary": {
                      "skycon": "阴",
                      "temperature": 22.3,
                      "apparent_temperature": 24.4,
                      "wind": { "speed_ms": 5.4 }
                    },
                    "hourly": {
                      "precipitation": [
                        { "datetime": "2026-08-25T22:00+08:00", "probability": 70 }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            fetchedAt = 123L
        )

        requireNotNull(result)
        assertEquals("阴，22°C，体感 24°C，降雨概率 70%", result.summary)
        assertEquals(22, result.temperature)
        assertEquals(24, result.apparentTemperature)
        assertEquals(70, result.precipitationProbability)
        assertEquals(5, result.windSpeed)
        assertEquals(123L, result.fetchedAt)
    }

    @Test
    fun rejectsNonSuccessEnvelope() {
        assertNull(parseApiZeroWeatherPayload("""{"code": 429, "data": {}}"""))
    }
}
