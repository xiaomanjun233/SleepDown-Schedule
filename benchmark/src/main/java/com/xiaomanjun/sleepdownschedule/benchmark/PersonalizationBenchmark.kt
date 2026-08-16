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
class PersonalizationBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun openWithoutCompilation() = measureOpen(CompilationMode.None())
    @Test fun openFreeformCompactWithoutCompilation() = measureOpenFreeform(FREEFORM_COMPACT)
    @Test fun openFreeformMediumWithoutCompilation() = measureOpenFreeform(FREEFORM_MEDIUM)
    @Test fun openFreeformWideWithoutCompilation() = measureOpenFreeform(FREEFORM_WIDE)
    @Test fun openWithBaselineProfile() = measureOpen(CompilationMode.Partial())
    @Test fun closeWithoutCompilation() = measureClose(CompilationMode.None())
    @Test fun closeWithBaselineProfile() = measureClose(CompilationMode.Partial())
    @Test fun repeatedWithBaselineProfile() = measureRepeated(CompilationMode.Partial())

    private fun measureOpen(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startHome() }
    ) { openPersonalization() }

    private fun measureOpenFreeform(bounds: FreeformBounds) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startBenchmarkHomeFreeformWithWallpaper(bounds) }
    ) { openPersonalization() }

    private fun measureClose(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startHome(); openPersonalization() }
    ) { closePersonalization() }

    private fun measureRepeated(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startHome() }
    ) {
        repeat(2) { openPersonalization(); closePersonalization() }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startHome() {
        startBenchmarkHomeWithWallpaper()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openPersonalization() {
        requireNotNull(
            device.wait(
                Until.findObject(By.desc("benchmark_personalize_button")),
                UI_TIMEOUT_MILLIS
            )
        ) { "Personalization button is not exposed to UiAutomator" }.click()
        check(
            device.wait(
                Until.hasObject(By.res("benchmark_personalize_panel")),
                UI_TIMEOUT_MILLIS
            )
        ) { "Personalization panel did not open" }
        device.waitForIdle(BenchmarkIdleTimeoutMillis)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.closePersonalization() {
        device.pressBack()
    }

    companion object {
        private const val ITERATIONS = 5
        private const val UI_TIMEOUT_MILLIS = 10_000L
        private val FREEFORM_COMPACT = FreeformBounds(1150, 268, 2050, 1868)
        private val FREEFORM_MEDIUM = FreeformBounds(950, 168, 2250, 1968)
        private val FREEFORM_WIDE = FreeformBounds(500, 318, 2700, 1818)
    }
}
