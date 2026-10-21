package com.xiaomanjun.sleepdownschedule.feature.importing

import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

class ChatGptResponseReaderTest {
    private val complete = """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"你好"}]}]}"""

    @Test fun readsCompletedJsonWithoutRequiringAnSseContentType() {
        val result = StringReader(complete).buffered().readChatGptResponse()
        assertEquals("你好", parseResponsesTextResult(result).content)
    }

    @Test fun nullErrorInACompletedResponsePreservesTheAnswer() {
        val body = complete.replace("\"object\":", "\"error\":null,\"object\":")
        val result = StringReader(body).buffered().readChatGptResponse()
        assertEquals("你好", parseResponsesTextResult(result).content)
    }

    @Test fun detectsAnActualEventStreamAndPublishesItsDeltas() {
        val payloads = mutableListOf<String>()
        val stream = ": keep-alive\r\n\r\nevent: response.output_text.delta\r\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\r\n\r\n" +
            "data: {\"type\":\"response.completed\",\"response\":$complete}\r\n\r\n"
        val result = StringReader(stream).buffered().readChatGptResponse(onEvent = payloads::add)
        assertEquals(2, payloads.size)
        assertEquals("你好", parseResponsesTextResult(result).content)
    }

    @Test fun doesNotMistakeAnHtmlChallengeForAnEmptyModelAnswer() {
        val error = assertThrows(AiServiceResponseException::class.java) {
            StringReader("<!DOCTYPE html><title>secret gateway body</title>").buffered().readChatGptResponse()
        }
        assertTrue(error.message!!.contains("网页"))
        assertEquals("", error.rawBody)
        assertFalse(error.message!!.contains("secret"))
    }

    @Test fun detectsEventStreamsBeginningWithMetadata() {
        for (metadata in listOf("id: 42", "retry: 1000")) {
            val stream = "$metadata\ndata: {\"type\":\"response.completed\",\"response\":$complete}\n\n"
            val result = StringReader(stream).buffered().readChatGptResponse()
            assertEquals("你好", parseResponsesTextResult(result).content)
        }
    }

    @Test fun incompleteJsonAndTruncatedStreamsCannotBecomeCompletedAnswers() {
        listOf(
            complete.replace("completed", "in_progress"),
            """{"output_text":"partial"}""",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"
        ).forEach { body ->
            assertThrows(AiServiceResponseException::class.java) {
                StringReader(body).buffered().readChatGptResponse()
            }
        }
    }

    @Test fun structuredErrorsAreNotTreatedAsSuccessfulJson() {
        val failure = assertThrows(AiServiceResponseException::class.java) {
            StringReader("""{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"secret"}}""")
                .buffered().readChatGptResponse()
        }
        assertTrue(failure.message!!.contains("使用受限"))
        assertFalse(failure.message!!.contains("secret"))
    }
}
