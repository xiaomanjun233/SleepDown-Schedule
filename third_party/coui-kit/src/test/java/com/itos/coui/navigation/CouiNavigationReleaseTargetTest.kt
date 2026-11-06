package com.itos.coui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 点击、滑选和取消只提交一次，且程序同步不经过释放路径。 */
class CouiNavigationReleaseTargetTest {
    @Test
    fun tapOnAnotherItemCommitsOnlyThatItem() {
        assertEquals(2, couiNavigationReleaseTarget(preview = 0, pending = null, down = 0, up = 2, cancelled = false))
        assertNull(couiNavigationReleaseTarget(preview = 2, pending = null, down = 2, up = 2, cancelled = false))
    }

    @Test
    fun slideCommitsPendingItemAndCancelNeverCommits() {
        assertEquals(1, couiNavigationReleaseTarget(preview = 1, pending = 1, down = 0, up = 1, cancelled = false))
        assertNull(couiNavigationReleaseTarget(preview = 1, pending = 1, down = 0, up = 0, cancelled = true))
        assertNull(couiNavigationReleaseTarget(preview = 0, pending = null, down = 0, up = 2, cancelled = true))
    }

    @Test
    fun slidingBackToTheDownItemIsNotASelection() {
        assertNull(couiNavigationReleaseTarget(preview = 0, pending = null, down = 0, up = 0, cancelled = false))
    }
}
