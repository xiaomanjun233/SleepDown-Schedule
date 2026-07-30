package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AiEduImportProgressSessionTest {
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
