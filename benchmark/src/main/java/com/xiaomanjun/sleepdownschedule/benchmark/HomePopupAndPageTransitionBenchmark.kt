package com.xiaomanjun.sleepdownschedule.benchmark

import android.os.SystemClock
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
class HomePopupAndPageTransitionBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun addCoursePopupWithoutCompilation() = measureAddDestination(
        actionText = "添加单节课",
        destinationResource = "benchmark_home_destination_add_course"
    )

    @Test fun manualImportPopupWithoutCompilation() = measureAddDestination(
        actionText = "手动导入课表",
        destinationResource = "benchmark_home_destination_manual_import"
    )

    @Test fun eduImportPageWithoutCompilation() = measureAddDestination(
        actionText = "教务系统导入",
        destinationResource = "benchmark_home_destination_edu_import"
    )

    private fun measureAddDestination(
        actionText: String,
        destinationResource: String
    ) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            startBenchmarkHomeWithWallpaper()
            requireNotNull(
                device.wait(Until.findObject(By.desc("添加")), UI_TIMEOUT_MILLIS)
            ).click()
            check(device.wait(Until.hasObject(By.text(actionText)), UI_TIMEOUT_MILLIS)) {
                "Add menu action '$actionText' did not become visible"
            }
            device.waitForIdle(BenchmarkIdleTimeoutMillis)
            SystemClock.sleep(900L)
        }
    ) {
        val action = requireNotNull(
            device.wait(Until.findObject(By.text(actionText)), UI_TIMEOUT_MILLIS)
        )
        val center = action.visibleCenter
        device.click(center.x, center.y)
        SystemClock.sleep(TRANSITION_CAPTURE_MILLIS)
        check(device.wait(Until.hasObject(By.res(destinationResource)), UI_TIMEOUT_MILLIS)) {
            "Destination '$destinationResource' did not become visible"
        }
        device.waitForIdle(BenchmarkIdleTimeoutMillis)
    }

    companion object {
        private const val ITERATIONS = 5
        private const val UI_TIMEOUT_MILLIS = 10_000L
        private const val TRANSITION_CAPTURE_MILLIS = 800L
    }
}
