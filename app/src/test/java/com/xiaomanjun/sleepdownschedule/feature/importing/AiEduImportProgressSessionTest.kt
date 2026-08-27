package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.agent.*

import org.junit.Assert.assertEquals
import org.junit.Test

class AiEduImportProgressSessionTest {
    @Test
    fun importStepsUseAgentExecutionVocabulary() {
        val statuses = aiEduAgentRunStatuses(
            AiEduImportProgress(
                steps = listOf("DOM 深度抓取", "已读取 AI 配置", "本地校验通过")
            )
        )

        assertEquals(AgentRunStatusIcon.SEARCH, statuses[0].icon)
        assertEquals(AgentRunStatusIcon.SETTINGS, statuses[1].icon)
        assertEquals(AgentRunStatusIcon.SCHEDULE, statuses[2].icon)
    }

    @Test
    fun choosingAnActionConsumesEveryCallback() {
        var confirmCount = 0
        var cancelCount = 0
        AiEduImportProgressSession.clearActions()
        AiEduImportProgressSession.setActions(
            onConfirm = { confirmCount++ },
            onCancel = { cancelCount++ }
        )

        AiEduImportProgressSession.confirm()
        AiEduImportProgressSession.confirm()
        AiEduImportProgressSession.cancel()

        assertEquals(1, confirmCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun terminalProgressReleasesCapturedActions() {
        var invoked = 0
        AiEduImportProgressSession.clearActions()
        AiEduImportProgressSession.setActions(onConfirm = { invoked++ })

        AiEduImportProgressSession.update(
            AiEduImportProgress(
                steps = listOf("本地校验通过"),
                finished = true
            )
        )
        AiEduImportProgressSession.confirm()

        assertEquals(0, invoked)
    }
}
