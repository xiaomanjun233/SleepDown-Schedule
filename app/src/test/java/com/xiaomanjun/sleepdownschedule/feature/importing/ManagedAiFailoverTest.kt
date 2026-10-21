package com.xiaomanjun.sleepdownschedule.feature.importing

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManagedAiFailoverTest {
    private fun route(id: String, responses: Boolean = false) = AiImportSettings(
        AiProviderPresets.dailyFree.copy(
            baseUrl = "https://$id.example/${if (responses) "responses" else "chat/completions"}",
            defaultModel = "model-$id",
            endpointStyle = if (responses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
            capabilities = AiProviderPresets.dailyFree.capabilities.copy(supportsResponses = responses)
        ), "test-key-$id", id
    )

    @Test fun unavailableRouteSwitchesWithIndependentModelAndEndpoint() = runBlocking {
        val visited = mutableListOf<String>()
        val value = runManagedAiCandidates(listOf(route("a"), route("b", true))) { settings, _ ->
            val config = settings.toProviderConfig().normalizedForRequest()
            visited += config.resolveRequestEndpoint()
            if (settings.managedRouteId == "a") throw AiServiceResponseException("unavailable", "", httpStatus = 503)
            assertEquals("model-b", config.model)
            assertEquals(AiEndpointStyle.RESPONSES, config.endpointStyle)
            assertEquals("test-key-b", settings.apiKey)
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(listOf("https://a.example/chat/completions", "https://b.example/responses"), visited)
    }

    @Test fun outputOrToolCommitPreventsReplay() = runBlocking {
        var calls = 0
        val failure = runCatching {
            runManagedAiCandidates(listOf(route("a"), route("b"))) { _, attempt ->
                calls++
                attempt.commit()
                throw SocketTimeoutException()
            }
        }.exceptionOrNull()
        assertTrue(failure is SocketTimeoutException)
        assertEquals(1, calls)
    }

    @Test fun cancelledOrInvalidInputDoesNotSwitch() = runBlocking {
        for (error in listOf(CancellationException("cancelled"), AiServiceResponseException("bad input", "", httpStatus = 400))) {
            var calls = 0
            val actual = runCatching {
                runManagedAiCandidates(listOf(route("a"), route("b"))) { _, _ -> calls++; throw error }
            }.exceptionOrNull()
            assertSame(error, actual)
            assertEquals(1, calls)
        }
    }

    @Test fun attemptsAreBoundedAndDuplicateRoutesAreNotRetried() = runBlocking {
        val seen = mutableListOf<String?>()
        runCatching {
            runManagedAiCandidates(listOf(route("a"), route("a")) + (1..20).map { route("r$it") }) { settings, _ ->
                seen += settings.managedRouteId
                throw IllegalStateException("network", SocketTimeoutException())
            }
        }
        assertEquals(8, seen.size)
        assertEquals(seen.distinct(), seen)
    }

    @Test fun onlyAvailabilityErrorsPermitSwitching() {
        for (code in listOf(401, 403, 404, 408, 429, 500, 503)) {
            assertTrue(AiServiceResponseException("error", "", httpStatus = code).canFailoverManagedAi())
        }
        assertFalse(AiServiceResponseException("schema error", "").canFailoverManagedAi())
        assertFalse(IllegalArgumentException("input").canFailoverManagedAi())
        assertFalse(IllegalStateException("cancelled", CancellationException()).canFailoverManagedAi())
    }
}
