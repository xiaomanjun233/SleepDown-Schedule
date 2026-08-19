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
class PersonalizationSliderBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun continuousBlurDragWithoutCompilation() = measure(CompilationMode.None())
    @Test fun continuousBlurDragWithBaselineProfile() = measure(CompilationMode.Partial())

    private fun measure(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startBenchmarkHome() }
    ) {
        requireNotNull(device.wait(Until.findObject(By.desc("benchmark_personalize_button")), UI_TIMEOUT_MILLIS)).click()
        val slider = requireNotNull(device.wait(Until.findObject(By.desc("benchmark_personalization_blur_slider")), UI_TIMEOUT_MILLIS))
        val bounds = slider.visibleBounds
        val y = bounds.centerY()
        device.swipe(bounds.left + 8, y, bounds.right - 8, y, 24)
        device.swipe(bounds.right - 8, y, bounds.left + 8, y, 24)
        device.waitForIdle(BenchmarkIdleTimeoutMillis)
        device.pressBack()
    }

    companion object { private const val UI_TIMEOUT_MILLIS = 10_000L }
}
