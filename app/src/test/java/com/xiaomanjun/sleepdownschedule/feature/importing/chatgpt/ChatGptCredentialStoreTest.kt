package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import org.junit.Assert.*
import org.junit.Test

class ChatGptCredentialStoreTest {
    @Test fun sameEmailRegistrationsKeepDistinctClientAndIdentityMappings() {
        val first = registration("client-one", "subject-one")
        val second = registration("client-two", "subject-two")
        val vault = ChatGptVault(activeClientId = second.clientId, registrations = mutableListOf(first, second))
        val restored = roundTrip(vault)
        assertEquals(vault.hostId, restored.hostId)
        assertEquals("subject-two", restored.active()!!.subject)
        assertNotEquals(restored.registrations[0].publicAccount().displayName, restored.registrations[1].publicAccount().displayName)
        assertEquals(listOf("client-one", "client-two"), restored.registrations.map { it.clientId })
    }

    @Test fun interruptedRefreshPersistsLatestReplacementWithoutPromotingUnverifiedIdentity() {
        val registration = registration("issued", "subject")
        registration.pendingTokens = ChatGptTokens("new-access", "new-refresh", "new-id", 2_000,
            setOf(ChatGptProtocol.DIRECT_SCOPE))
        registration.pendingIdentityValidation = true
        val restored = roundTrip(ChatGptVault(activeClientId = "issued", registrations = mutableListOf(registration))).active()!!
        assertEquals("old-access", restored.tokens!!.accessToken)
        assertEquals("new-refresh", restored.pendingTokens!!.refreshToken)
        assertTrue(restored.pendingIdentityValidation)
        assertFalse(restored.publicAccount().toString().contains("new-refresh"))
        assertFalse(restored.tokens.toString().contains("old-access"))
    }

    @Test fun signOutRetainsHostAndRegistrationButRemovesAllCredentialAndModelFields() {
        val registration = registration("issued", "subject")
        val vault = ChatGptVault(activeClientId = "issued", registrations = mutableListOf(registration))
        registration.tokens = null
        registration.pendingTokens = null
        registration.models = emptyList()
        val json = ChatGptCredentialStore.encode(vault)
        assertFalse(json.toString().contains("old-access"))
        assertFalse(json.toString().contains("old-refresh"))
        val restored = ChatGptCredentialStore.decode(json)
        assertEquals(vault.hostId, restored.hostId)
        assertEquals("issued", restored.active()!!.clientId)
        assertFalse(restored.active()!!.publicAccount().isSignedIn)
        assertFalse(restored.active()!!.publicAccount().planUsageEnabled)
        assertTrue(restored.active()!!.models.isEmpty())
    }

    @Test fun pendingUnverifiedRegistrationDoesNotReplaceActiveAccount() {
        val registration = registration("active-client", "subject")
        val vault = ChatGptVault(activeClientId = registration.clientId, registrations = mutableListOf(registration), pendingClientId = "issued-pending")
        val restored = roundTrip(vault)
        assertEquals("issued-pending", restored.pendingClientId)
        assertEquals("active-client", restored.active()!!.clientId)
        assertEquals(1, restored.registrations.size)
    }

    private fun registration(client: String, subject: String) = ChatGptRegistration(client, subject, "same@example.test", null,
        ChatGptTokens("old-access", "old-refresh", "old-id", 1_000, setOf(ChatGptProtocol.DIRECT_SCOPE)), listOf(ChatGptModel("example-model", "Example")))
    private fun roundTrip(vault: ChatGptVault) = ChatGptCredentialStore.decode(ChatGptCredentialStore.encode(vault))
}
