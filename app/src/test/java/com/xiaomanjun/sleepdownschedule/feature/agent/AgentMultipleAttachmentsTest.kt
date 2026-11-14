package com.xiaomanjun.sleepdownschedule.feature.agent

import org.junit.Assert.*
import org.junit.Test

class AgentMultipleAttachmentsTest {
    @Test fun markersRoundTripEveryImageAndLegacyContentRemainsCompatible() {
        val names = listOf("first.jpg", "second.png", "third.webp")
        val restored = parseAgentMessageContent(agentMessageContent("按顺序核对", names))
        assertEquals(names, restored.attachmentFileNames)
        assertEquals("first.jpg", restored.attachmentFileName)
        assertEquals("按顺序核对", restored.text)
        assertEquals(listOf("old.jpg"), parseAgentMessageContent("[[agent_image:old.jpg]]\n旧消息").attachmentFileNames)
        assertEquals("无图片", agentMessageContent("无图片", emptyList()))
    }

    @Test fun replacingOrGuidingCandidatePreservesTheWholeImageGroup() {
        val mailbox = com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox<AgentTaskInput>()
        val images = listOf(AgentImageAttachment("image/jpeg", "AQ==", "1"), AgentImageAttachment("image/png", "Ag==", "2"))
        val id = mailbox.submit(AgentTaskInput("核对", images = images), com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.QUEUE)
        mailbox.replace(id, AgentTaskInput("核对第二页", images = images))
        assertTrue(mailbox.steer(id))
        assertEquals(images, mailbox.drainSteering().single().allImages)
    }
}
