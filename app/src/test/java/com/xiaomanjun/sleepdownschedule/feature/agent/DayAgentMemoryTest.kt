package com.xiaomanjun.sleepdownschedule.feature.agent

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DayAgentMemoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2026, 10, 8)

    @Test fun staleAgentCannotOverwriteAnEditMadeWhileRequestWasRunning() {
        DayAgentPreferences.saveMemory(context, "旧偏好")
        DayAgentPreferences.saveMemory(context, "用户刚修改的偏好")
        assertFalse(DayAgentPreferences.saveMemoryFromAgent(context, "模型旧版本", today, "旧偏好"))
        assertEquals("用户刚修改的偏好", DayAgentPreferences.memory(context))
    }

    @Test fun oversizedAgentTextDoesNotSilentlyTruncateExistingEntries() {
        DayAgentPreferences.saveMemory(context, "保留的条目")
        assertFalse(DayAgentPreferences.saveMemoryFromAgent(context, "新".repeat(1201), today, "保留的条目"))
        assertEquals("保留的条目", DayAgentPreferences.memory(context))
    }

    @Test fun agentCanStillCorrectExistingMemoryRatherThanOnlyAppend() {
        DayAgentPreferences.saveMemory(context, "周一喜欢早课\n课程名保留英文")
        assertTrue(DayAgentPreferences.saveMemoryFromAgent(context, "周一避免早课\n课程名保留英文", today, "周一喜欢早课\n课程名保留英文"))
        assertEquals("周一避免早课\n课程名保留英文", DayAgentPreferences.memory(context))
    }
}
