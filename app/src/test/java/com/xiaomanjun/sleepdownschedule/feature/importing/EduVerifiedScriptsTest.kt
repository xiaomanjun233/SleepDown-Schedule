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
    @Test fun submissionBlocksDuplicateUploadsAndRetainsTheLocallySavedNameForRetry() {
        val submission = EduScriptSubmission()
        assertNotNull(submission.begin())
        assertNull(submission.begin())
        submission.finish("示例大学", uploaded = false)
        assertEquals("示例大学", submission.begin()?.savedName)
        submission.finish("示例大学", uploaded = true)
        assertNull(submission.begin())
    }
    @Test fun savedLoginEntryKeepsItsServiceAndSpaRouteButNotTransientCredentials() {
        assertEquals("https://login.example.edu/cas/login?service=https%3A%2F%2Fedu.example.edu%2F",
            cleanLocalEduLoginUrl("https://login.example.edu/cas/login;jsessionid=secret?service=https%3A%2F%2Fedu.example.edu%2F&ticket=st&access_token=token&state=oauth#private"))
        assertEquals("http://203.0.113.10:9527/#/authserver/login?redirect_uri=abc",
            cleanLocalEduLoginUrl("http://203.0.113.10:9527/#/authserver/login?redirect_uri=abc"))
    }
}
