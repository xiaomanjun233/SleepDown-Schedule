package com.xiaomanjun.sleepdownschedule.core.ai

import org.junit.Assert.*
import org.junit.Test

class AiUsageTest {
    @Test fun usageAggregatesActualCountersAndRetainsLatestContext() {
        val first = parseAiRequestUsage("""{"usage":{"prompt_tokens":1000,"completion_tokens":100,"prompt_tokens_details":{"cached_tokens":600}}}""", 2_000_000_000)!!
        val next = parseAiRequestUsage("""{"usage":{"input_tokens":1200,"output_tokens":200,"input_tokens_details":{"cached_tokens":900}}}""", 3_000_000_000)!!
        val total = AiUsageTotals().plus(first).plus(next)
        assertEquals(2200L, total.input)
        assertEquals(1500L, total.cached)
        assertEquals(1200L, total.contextUsed)
        assertEquals(60.0, total.averageOutputPerSecond!!, 0.001)
        assertNull(total.contextLimit)
    }
    @Test fun missingStatisticsRemainUnknown() {
        assertNull(parseAiRequestUsage("{}", 1))
        val usage = parseAiRequestUsage("""{"usage":{"input_tokens":0}}""", 0)!!
        assertEquals(0L, usage.input)
        assertNull(usage.output)
        assertNull(usage.cached)
        assertNull(AiUsageTotals().plus(usage).averageOutputPerSecond)
    }
}
