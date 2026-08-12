package com.xiaomanjun.sleepdownschedule.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseEditorAnimationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun editorOpenCloseWithoutCompilation() = measureEditorJourney(CompilationMode.None())

    @Test
    fun editorOpenCloseWithBaselineProfile() = measureEditorJourney(CompilationMode.Partial())

    private fun measureEditorJourney(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            ensureBenchmarkCourse()
        }
    ) {
        openBenchmarkCourseEditor()
        closeBenchmarkCourseEditor()
    }
}
