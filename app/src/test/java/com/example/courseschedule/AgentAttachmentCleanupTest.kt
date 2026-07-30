package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentAttachmentCleanupTest {
    private val referenced = "11111111-1111-1111-1111-111111111111.jpg"
    private val orphan = "22222222-2222-2222-2222-222222222222.png"

    @Test
    fun onlyManagedFilesWithoutDatabaseReferencesAreSelected() {
        val selected = orphanedAgentAttachmentNames(
            existingNames = setOf(
                referenced,
                orphan,
                "user-photo.jpg",
                "../outside.jpg",
                "33333333-3333-3333-3333-333333333333.txt"
            ),
            referencedNames = setOf(referenced)
        )

        assertEquals(setOf(orphan), selected)
        assertFalse(referenced in selected)
    }

    @Test
    fun parsingPersistedMessagesKeepsTheirAttachmentReference() {
        val content = agentMessageContent("请识别这张课表", referenced)

        assertEquals(referenced, parseAgentMessageContent(content).attachmentFileName)
        assertEquals("请识别这张课表", parseAgentMessageContent(content).text)
    }
}
