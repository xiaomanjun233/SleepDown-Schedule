package com.xiaomanjun.sleepdownschedule.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportHistoryBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun historyOpenWithoutCompilation() = measure(CompilationMode.None())
    @Test fun historyOpenWithBaselineProfile() = measure(CompilationMode.Partial())

    private fun measure(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startBenchmarkHome() }
    ) {
        requireNotNull(device.wait(Until.findObject(By.desc("添加")), UI_TIMEOUT_MILLIS)).click()
        requireNotNull(device.wait(Until.findObject(By.text("手动导入课表")), UI_TIMEOUT_MILLIS)).click()
        requireNotNull(device.wait(Until.findObject(By.text("导入历史")), UI_TIMEOUT_MILLIS)).click()
        requireNotNull(device.wait(Until.findObject(By.text("导入历史")), UI_TIMEOUT_MILLIS))
        device.pressBack()
        device.waitForIdle(BenchmarkIdleTimeoutMillis)
    }

    companion object { private const val UI_TIMEOUT_MILLIS = 10_000L }
}
