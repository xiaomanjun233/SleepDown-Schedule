package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import org.junit.Assert.*
import org.junit.Test

class AgentSpecialCoursesTest {
    private val first = TimeSlotCourse("a", "早读", "07:00", "07:40", location = "原教室", note = "保留备注", endWeek = 18)
    private val second = TimeSlotCourse("b", "午休", "12:00", "13:00", afterPeriod = 4)
    @Test fun partialUpdatesPreserveUnmentionedFieldsAndOtherCourses() {
        val result = applySpecialCoursePatch(encodeTimeSlotCourses(listOf(first, second)), """{"upsert":[{"id":"a","name":"晨读"}]}""")
        val slots = decodeTimeSlotCourses(requireNotNull(result))
        assertEquals(first.copy(name = "晨读"), slots.first { it.id == "a" })
        assertEquals(second, slots.first { it.id == "b" })
        assertEquals(result, applySpecialCoursePatch(result, """{"upsert":[{"id":"a","name":"晨读"}]}"""))
    }
    @Test fun invalidWholePatchCannotSilentlyEraseOrSalvageCourses() {
        val source = encodeTimeSlotCourses(listOf(first, second))
        assertNull(applySpecialCoursePatch(source, """{"deleteIds":["b"],"upsert":[{"id":"a","startTime":"19:00"}]}"""))
        assertEquals(listOf(first, second), decodeTimeSlotCourses(source))
        assertNull(normalizeSpecialCoursePatch("""{"upsert":[{"id":"a","madeUpField":"value"}]}"""))
        assertNull(normalizeSpecialCoursePatch("""{"upsert":[{"id":"a"}],"deleteIds":["a"]}"""))
    }
    @Test fun createAndDeleteUseTheCurrentDomainValidator() {
        val result = applySpecialCoursePatch(encodeTimeSlotCourses(listOf(first)),
            """{"deleteIds":["a"],"upsert":[{"id":"new","name":"晚自习","startTime":"19:00","endTime":"20:00","weekdays":[2,4],"displayMode":"SMART"}]}""")
        val slot = decodeTimeSlotCourses(requireNotNull(result)).single()
        assertEquals(listOf(2, 4), slot.weekdays)
        assertEquals(TimeSlotDisplayMode.SMART, slot.displayMode)
        assertNull(applySpecialCoursePatch(result, """{"upsert":[{"id":"new","weekdays":[8]}]}"""))
    }
}
