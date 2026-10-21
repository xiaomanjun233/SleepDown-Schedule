package com.suda.yzune.wakeupschedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WakeUpProxyProviderTest {
    @Test
    public void emptyCourseArrayIsAValidFreshSnapshot() {
        assertTrue(WakeUpSourceResponsePolicy.isUsable(0, "[]"));
    }

    @Test
    public void failedOrBlankResponsesAreRejected() {
        assertFalse(WakeUpSourceResponsePolicy.isUsable(-1, "[]"));
        assertFalse(WakeUpSourceResponsePolicy.isUsable(0, ""));
        assertFalse(WakeUpSourceResponsePolicy.isUsable(0, null));
    }
}
