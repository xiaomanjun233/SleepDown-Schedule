package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AiLiveReasoningTest {
    @After fun resetSession() = AiEduImportProgressSession.update(null)

    @Test fun streamingWindowIsThrottledAndFinalFragmentIsFlushedWithoutTruncatingResult() {
        var now = 0L
        val updates = mutableListOf<String>()
        val publisher = AiReasoningStreamPublisher(updates::add) { now }
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"reasoning_content":"识别课程"}}]}""")
        publisher.publish(stream.reasoning)
        now += 1_000_000L
        stream.consume("""{"choices":[{"delta":{"reasoning_content":"，核对星期"}}]}""")
        publisher.publish(stream.reasoning)
        assertEquals(listOf("识别课程"), updates)
        publisher.publish(stream.reasoning, force = true)
        assertEquals("识别课程，核对星期", updates.last())
        now += 100_000_000L
        stream.reasoning.append("甲".repeat(5_000))
        publisher.publish(stream.reasoning)
        assertEquals(4_000, updates.last().length)
        assertEquals(5_009, stream.reasoning.length)
    }

    @Test fun responsesReasoningDeltasReachTheSameReadingWindow() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.reasoning_summary_text.delta","delta":"检查节次"}""")
        stream.consume("""{"type":"response.reasoning_summary_text.delta","delta":"与周数"}""")
        val updates = mutableListOf<String>()
        AiReasoningStreamPublisher(updates::add).publish(stream.reasoning, force = true)
        assertEquals(listOf("检查节次与周数"), updates)
    }

    @Test fun cancelledAndEarlierRepairCallbacksCannotOverwriteTheCurrentTask() {
        val session = AiEduImportProgressSession
        session.update(AiEduImportProgress(taskId = "first", requestSent = true))
        val original = session.beginReasoning("first")
        original("初次结果")
        val repair = session.beginReasoning("first")
        original("迟到的旧片段")
        assertEquals("", session.liveReasoning.value.text)
        repair("格式修复")
        assertEquals("格式修复", session.liveReasoning.value.text)
        session.update(AiEduImportProgress(taskId = "second", requestSent = true))
        repair("另一个迟到片段")
        assertEquals("", session.liveReasoning.value.text)
        val current = session.beginReasoning("second")
        session.update(AiEduImportProgress(taskId = "second", finished = true))
        current("任务结束后的片段")
        assertEquals("", session.liveReasoning.value.text)
    }
}
