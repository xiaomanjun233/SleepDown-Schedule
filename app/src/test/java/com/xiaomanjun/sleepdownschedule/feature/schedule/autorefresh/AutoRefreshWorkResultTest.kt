package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import androidx.work.ListenableWorker
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshWorkResultTest {
    @Test fun successfulChecksFinishWithoutRetry() {
        assertTrue(autoRefreshWorkResult(AutoRefreshOutcome(true, "没有变化"), 0) is ListenableWorker.Result.Success)
    }

    @Test fun expiredLoginAndAdapterFailuresNeverRetryAutomatically() {
        for (attempt in 0..3) assertTrue(autoRefreshWorkResult(
            AutoRefreshOutcome(false, "请重新登录"), attempt) is ListenableWorker.Result.Failure)
    }

    @Test fun networkFailuresRetryAtMostTwice() {
        val failure = AutoRefreshOutcome(false, "连接超时", retryable = true)
        for (attempt in 0..1) assertTrue(autoRefreshWorkResult(failure, attempt) is ListenableWorker.Result.Retry)
        for (attempt in 2..4) assertTrue(autoRefreshWorkResult(failure, attempt) is ListenableWorker.Result.Failure)
    }
}
