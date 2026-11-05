package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class EduVerifiedScriptsTest {
    @After fun clear() { EduVerifiedScripts.current.value?.let { EduVerifiedScripts.clear(it.proposal.taskId) } }
    @Test fun sourceSnapshotIsRetainedUntilItsFileChangesAndOldTaskCannotClearNewScript() {
        EduVerifiedScripts.bindTask("old")
        EduVerifiedScripts.record("old", "学校", "https://edu.example.edu/login", "folder/../adapter.js", "hash1", "verified source")
        val verified = checkNotNull(EduVerifiedScripts.current.value)
        EduAdapterSaveProposals.offer(verified.proposal)
        EduVerifiedScripts.fileChanged("old", "other.js", "different")
        assertEquals("verified source", EduVerifiedScripts.current.value?.proposal?.source)
        EduVerifiedScripts.fileChanged("old", "./adapter.js", "hash2")
        assertNull(EduVerifiedScripts.current.value)
        assertNull(EduAdapterSaveProposals.current.value)
        EduVerifiedScripts.record("new", "新学校", "https://new.example.edu/", "adapter.js", "new", "new source")
        EduVerifiedScripts.clear("old")
        assertEquals("new source", EduVerifiedScripts.current.value?.proposal?.source)
    }
    @Test fun onlyTheMatchingConfirmedScriptGetsMarkedSaved() {
        EduVerifiedScripts.record("task", "学校", "https://edu.example.edu/", "adapter.js", "hash", "source")
        val id = checkNotNull(EduVerifiedScripts.current.value).proposal.id
        EduVerifiedScripts.markSaved("old-id")
        assertFalse(EduVerifiedScripts.current.value!!.saved)
        EduVerifiedScripts.markSaved(id)
        assertTrue(EduVerifiedScripts.current.value!!.saved)
    }
    @Test fun savedLoginEntryKeepsItsServiceButNotTransientCredentials() {
        assertEquals("https://login.example.edu/cas/login?service=https%3A%2F%2Fedu.example.edu%2F",
            cleanLocalEduLoginUrl("https://login.example.edu/cas/login;jsessionid=secret?service=https%3A%2F%2Fedu.example.edu%2F&ticket=st&access_token=token&state=oauth#private"))
    }
}
