package com.xiaomanjun.sleepdownschedule.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiteeAppUpdaterTest {
    @Test
    fun componentSelectsNewestReleaseEvenWhenGiteeReturnsOldestFirst() {
        val latest = release("v1.2.6-exp10", true)
        val candidates = listOf(release("v1.2.6-exp", true), latest, release("v1.2.6-exp2", true))
        assertEquals(latest, GiteeAppUpdater.selectLatestAssetRelease(candidates))
        assertEquals(latest, GiteeAppUpdater.selectLatestAssetRelease(candidates.reversed()))
        assertEquals(latest, GiteeAppUpdater.selectLatestAssetRelease(candidates + release("v1.3.0-exp").copy(apkUrl = null)))
    }

    @Test
    fun comparesNumericVersions() {
        assertTrue(GiteeAppUpdater.isVersionNewer("v1.1", "1.0"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.0.1", "1.0"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.0", "1.0"))
        assertFalse(GiteeAppUpdater.isVersionNewer("v0.9.9", "1.0"))
    }

    @Test
    fun stableReleaseSupersedesSameVersionBeta() {
        assertTrue(GiteeAppUpdater.isVersionNewer("1.0", "1.0 beta"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.0 beta", "1.0"))
    }

    @Test
    fun numberedBetaDoesNotBecomeAnExtraVersionComponent() {
        assertTrue(GiteeAppUpdater.isVersionNewer("v1.4.3", "1.4.3_beta2"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.3_beta99", "1.4.3"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.3_beta10", "1.4.3_beta2"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.3_beta2", "1.4.3_beta10"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.4_beta1", "1.4.3"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.2", "1.4.3_beta2"))
    }

    @Test
    fun releaseStagesAndBuildMetadataAreOrderedIndependently() {
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.3-rc1", "1.4.3-beta10"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.3+99", "1.4.3+1"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.3_beta2", "v1.4.3-beta2"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.3-exp2", "1.4.3-exp"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.4.3-exp", "1.4.3-exp2"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.3_beta7", "1.4.3-exp"))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.4.3", "1.4.3-exp"))
    }

    private fun release(tag: String, prerelease: Boolean = false) =
        GiteeReleaseInfo(tag, tag, "", "app.apk", "https://example.test/app.apk", "", prerelease)

    @Test
    fun channelsFilterBothReleaseFlagAndBetaNameAndIgnoreListOrder() {
        val stable = release("1.4.3")
        val nextBeta = release("1.4.4_beta1")
        val flagged = release("1.5.0", prerelease = true)
        val releases = listOf(nextBeta, release("1.4.2"), flagged, stable)
        assertEquals(stable, GiteeAppUpdater.selectRelease(releases, false))
        assertEquals(flagged, GiteeAppUpdater.selectRelease(releases, true))
        assertEquals(nextBeta, GiteeAppUpdater.selectRelease(listOf(nextBeta, stable), true))
    }

    @Test
    fun stableStillWinsAtSameVersionWhenBetaChannelIsEnabled() {
        val stable = release("1.4.3")
        assertEquals(stable, GiteeAppUpdater.selectRelease(listOf(stable, release("1.4.3_beta20", true)), true))
    }

    @Test
    fun experimentalChannelOnlySelectsExperimentalReleases() {
        val experimental = release("v1.4.3-exp", prerelease = true)
        val beta = release("v1.4.4_beta1", prerelease = true)
        val stable = release("v1.4.4")
        assertEquals(
            experimental,
            GiteeAppUpdater.selectRelease(
                listOf(stable, beta, experimental),
                AppUpdateChannel.Experimental
            )
        )
        assertEquals(
            beta,
            GiteeAppUpdater.selectRelease(
                listOf(experimental, beta),
                AppUpdateChannel.Beta
            )
        )
    }

    @Test
    fun beta8UpgradesBeta7WithoutSelectingExperimentalReleases() {
        val beta8 = release("v1.2.6_beta8", true)
        val stable = release("v1.2.5")
        val releases = listOf(release("v1.2.6-exp3", true), release("v1.3.0-exp3"),
            release("v1.2.6_beta7", true), stable, beta8)
        assertEquals(beta8, GiteeAppUpdater.selectRelease(releases, true))
        assertEquals(stable, GiteeAppUpdater.selectRelease(releases, false))
        assertTrue(GiteeAppUpdater.isVersionNewer(beta8.tagName, "1.2.6_beta7"))
        assertFalse(GiteeAppUpdater.isVersionNewer("1.2.6_beta7", beta8.tagName))
        assertTrue(GiteeAppUpdater.isVersionNewer("1.2.6-exp3", "1.2.6-exp2"))
    }
}
