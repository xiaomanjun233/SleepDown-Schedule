package com.xiaomanjun.sleepdownschedule.core.ai

import org.junit.Assert.*
import org.junit.Test

class AgentMailboxTest {
    @Test fun fullCandidateQueueRejectsWithoutThrowingOrDroppingExistingMessages() {
        val mailbox = AgentMailbox<String>()
        repeat(16) { assertNotNull(mailbox.trySubmit("候选$it", AgentMailbox.Mode.QUEUE)) }
        val before = mailbox.inputs.value
        assertNull(mailbox.trySubmit("暂不接收", AgentMailbox.Mode.QUEUE))
        assertEquals(before, mailbox.inputs.value)
        mailbox.pause()
        assertTrue(mailbox.replace(before.last().id, "停止后仍可编辑"))
        assertEquals("停止后仍可编辑", mailbox.inputs.value.last().value)
        assertEquals(before.last().id, mailbox.inputs.value.last().id)
    }
    @Test fun sentCandidateStaysQueuedUntilUserGuidesItAndKeepsItsIdentity() {
        val mailbox = AgentMailbox<String>()
        val first = mailbox.submit("下一轮处理", AgentMailbox.Mode.QUEUE)
        val candidate = mailbox.submit("纠正地点", AgentMailbox.Mode.QUEUE)
        assertTrue(mailbox.drainSteering().isEmpty())
        assertTrue(mailbox.replace(candidate, "纠正为实验楼"))
        assertTrue(mailbox.steer(candidate))
        assertEquals(candidate, mailbox.inputs.value.last().id)
        assertEquals(listOf("纠正为实验楼"), mailbox.drainSteering())
        assertEquals(first, mailbox.inputs.value.single().id)
        assertFalse(mailbox.steer(candidate))
    }
    @Test fun pausedSteeringIsRetainedAndLateSteeringBecomesTheNextTurn() {
        val mailbox = AgentMailbox<String>()
        mailbox.submit("晚到的要求", AgentMailbox.Mode.STEER)
        mailbox.pause()
        assertTrue(mailbox.drainSteering().isEmpty())
        assertEquals(1, mailbox.inputs.value.size)
        mailbox.resume()
        assertEquals("晚到的要求", mailbox.nextQueued())
        assertTrue(mailbox.inputs.value.isEmpty())
    }
    @Test fun steeringAndQueueHaveDistinctBoundariesAndCanBeEditedOrWithdrawn() {
        val mailbox = AgentMailbox<String>()
        val first = mailbox.submit("下一任务", AgentMailbox.Mode.QUEUE)
        val steering = mailbox.submit("改成周二", AgentMailbox.Mode.STEER)
        assertEquals(listOf("改成周二"), mailbox.drainSteering())
        assertFalse(mailbox.replace(steering, "过期"))
        assertTrue(mailbox.replace(first, "更正后的下一任务"))
        mailbox.pause()
        assertNull(mailbox.nextQueued())
        mailbox.resume()
        assertEquals("更正后的下一任务", mailbox.nextQueued())
        val withdrawn = mailbox.submit("撤回", AgentMailbox.Mode.QUEUE)
        mailbox.remove(withdrawn)
        assertTrue(mailbox.inputs.value.isEmpty())
    }
}
