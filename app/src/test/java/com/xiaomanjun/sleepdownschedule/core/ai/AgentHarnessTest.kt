package com.xiaomanjun.sleepdownschedule.core.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class AgentHarnessTest {
    @Test fun toolsSettleOncePerCallIdAndChangedArgumentsCannotReuseAnId() {
        val events = mutableListOf<AgentHarness.Event>()
        val harness = AgentHarness(4, observe = events::add)
        var applied = 0
        val result = harness.run(step = { round ->
            val value = harness.tool("patch-1", "patch", "revision=0", conflict = { -1 }) { ++applied }
            assertEquals(1, value)
            if (round == 0) AgentHarness.Next.Continue else AgentHarness.Next.Complete(value)
        }, converge = { fail("Should have completed"); -1 })
        assertEquals(1, result)
        assertEquals(1, applied)
        assertEquals(-1, harness.tool("patch-1", "patch", "revision=1", conflict = { -1 }) { ++applied })
        assertEquals(1, applied)
        assertTrue(events.any { it.phase == AgentHarness.Phase.TOOL_REPLAYED })
        assertEquals(AgentHarness.Phase.COMPLETED, events.last { it.tool == null }.phase)
    }

    @Test fun cancellationPreventsTheNextRequestAndNeverBecomesConvergence() {
        var cancelled = false
        var requests = 0
        val harness = AgentHarness(4, checkActive = { if (cancelled) throw CancellationException() })
        try {
            harness.run(step = { requests++; cancelled = true; AgentHarness.Next.Continue }, converge = { fail("Cancelled turns must not converge") })
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(1, requests) }
    }

    @Test fun mutableReadsWithNewIdsExecuteAgainAndStepsAreBounded() {
        var reads = 0
        val harness = AgentHarness(3)
        val result = harness.run(step = { index ->
            harness.tool("read-$index", "read", "same", conflict = { -1 }) { ++reads }
            AgentHarness.Next.Continue
        }, converge = { reads })
        assertEquals(3, result)
    }
}
