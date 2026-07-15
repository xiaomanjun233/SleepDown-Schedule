package com.example.courseschedule.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val BENCHMARK_COURSE_NAME = "Baseline Profile Course"
private const val UI_TIMEOUT_MILLIS = 10_000L

internal fun MacrobenchmarkScope.ensureBenchmarkCourse() {
    if (device.hasObject(By.text(BENCHMARK_COURSE_NAME))) return

    requireNotNull(device.wait(Until.findObject(By.desc("添加")), UI_TIMEOUT_MILLIS)).click()
    requireNotNull(device.wait(Until.findObject(By.text("添加单节课")), UI_TIMEOUT_MILLIS)).click()
    val nameField = requireNotNull(device.wait(Until.findObject(By.text("课程名称")), UI_TIMEOUT_MILLIS))
    nameField.click()
    nameField.text = BENCHMARK_COURSE_NAME
    device.pressBack()
    requireNotNull(device.wait(Until.findObject(By.desc("保存")), UI_TIMEOUT_MILLIS)).click()
    requireNotNull(device.wait(Until.findObject(By.text(BENCHMARK_COURSE_NAME)), UI_TIMEOUT_MILLIS))
}

internal fun MacrobenchmarkScope.openBenchmarkCourseEditor() {
    requireNotNull(device.wait(Until.findObject(By.text(BENCHMARK_COURSE_NAME)), UI_TIMEOUT_MILLIS)).click()
    requireNotNull(device.wait(Until.findObject(By.text("编辑单节课")), UI_TIMEOUT_MILLIS))
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.closeBenchmarkCourseEditor() {
    requireNotNull(device.wait(Until.findObject(By.desc("取消")), UI_TIMEOUT_MILLIS)).click()
    device.wait(Until.gone(By.text("编辑单节课")), UI_TIMEOUT_MILLIS)
    device.waitForIdle()
}
