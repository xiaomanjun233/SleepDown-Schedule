package com.xiaomanjun.sleepdownschedule.feature.settings

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment

class HolidayApiTest {
    private val importedPlan = HolidayPlan("国庆", listOf(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02")),
        listOf(HolidayMakeup(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-02"))))
    private val importedEntries = listOf(
        ScheduleAdjustment("2026-10-01", label = "学校放假"),
        ScheduleAdjustment("2026-10-02"),
        ScheduleAdjustment("2026-10-10", "2026-10-02")
    )

    @Test fun recognizesAnAddedGroupWithoutRequiringTheSameLabelOrListOrder() {
        assertTrue(importedPlan.isAlreadyAdded(importedEntries.reversed() + ScheduleAdjustment("2026-12-01")))
    }

    @Test fun preservesSchoolPairingWhenTheHolidayGroupWasAlreadyAdded() {
        val edited = importedEntries.map { if (it.date == "2026-10-10") it.copy(sourceDate = "2026-10-05") else it }
        assertTrue(importedPlan.isAlreadyAdded(edited))
    }

    @Test fun partialOrChangedDayTypesAreNotReportedAsACompleteAddedGroup() {
        assertFalse(importedPlan.isAlreadyAdded(importedEntries.drop(1)))
        assertFalse(importedPlan.isAlreadyAdded(importedEntries.map { if (it.date == "2026-10-10") it.copy(sourceDate = null) else it }))
        assertFalse(importedPlan.isAlreadyAdded(importedEntries.map { if (it.date == "2026-10-01") it.copy(sourceDate = "2026-10-05") else it }))
    }

    @Test fun emptyOrAnotherYearsPlanIsNotAlreadyAdded() {
        assertFalse(HolidayPlan("空", emptyList(), emptyList()).isAlreadyAdded(importedEntries))
        assertFalse(importedPlan.copy(restDates = listOf(LocalDate.parse("2027-10-01"))).isAlreadyAdded(importedEntries))
    }

    @Test fun parsesRestAndMakeupWithoutGuessingSchoolCourseMapping() {
        val result = HolidayApi.parse("""{"code":0,"holiday":{
          "10-10":{"date":"2026-10-10","name":"国庆后调休","holiday":false,"after":true},
          "10-01":{"date":"2026-10-01","name":"国庆节","holiday":true,"wage":3}
        }}""", 2026)
        assertEquals(listOf(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10")), result.map { it.date })
        assertTrue(result.first().isRest)
        assertFalse(result.last().isRest)
    }

    @Test(expected = IllegalArgumentException::class) fun serviceFailureIsNotAnEmptyHolidayCalendar() {
        HolidayApi.parse("""{"code":-1,"holiday":{}}""", 2026)
    }

    @Test(expected = IllegalArgumentException::class) fun unpublishedYearIsReported() {
        HolidayApi.parse("""{"code":0,"holiday":{}}""", 2027)
    }

    @Test(expected = IllegalArgumentException::class) fun wrongYearIsRejected() {
        HolidayApi.parse("""{"code":0,"holiday":{"10-01":{"date":"2025-10-01","holiday":true}}}""", 2026)
    }

    @Test(expected = IllegalStateException::class) fun missingTypeIsNotAssumedToBeRest() {
        HolidayApi.parse("""{"code":0,"holiday":{"10-01":{"date":"2026-10-01"}}}""", 2026)
    }

    private fun rest(date: String, name: String, wage: Int = 2) =
        HolidayProposal(LocalDate.parse(date), name, true, wage)
    private fun legalHoliday(date: String, name: String) =
        HolidayProposal(LocalDate.parse(date), name, true, 3)
    private fun makeup(date: String, name: String) = HolidayProposal(LocalDate.parse(date), name, false, 1)

    /** The national swap makes up the last workdays lost inside a period, not the nearest weekday. */
    @Test fun pairsMakeupDaysWithTheLastWorkdaysLostInsideTheirHoliday() {
        val plans = planHolidays(listOf(
            makeup("2026-02-14", "春节前补班"),
            rest("2026-02-15", "春节"), legalHoliday("2026-02-16", "除夕"), legalHoliday("2026-02-17", "初一"),
            legalHoliday("2026-02-18", "初二"), legalHoliday("2026-02-19", "初三"), rest("2026-02-20", "初四"),
            rest("2026-02-21", "初五"), rest("2026-02-22", "初六"), rest("2026-02-23", "初七"),
            makeup("2026-02-28", "春节后补班")
        ))
        assertEquals(1, plans.size)
        assertEquals("春节", plans.single().name)
        assertEquals(9, plans.single().restDates.size)
        assertEquals(
            listOf(LocalDate.parse("2026-02-20"), LocalDate.parse("2026-02-23")),
            plans.single().makeups.map { it.suggestedSource }
        )
    }

    /** 官方 2026 安排：9月20日补10月6日课，10月10日补10月7日课；中秋自身不需要补班。 */
    @Test fun makeupsBelongToTheFestivalThatNeedsThem() {
        val plans = planHolidays(listOf(
            makeup("2026-09-20", "中秋节前补班"),
            legalHoliday("2026-09-25", "中秋节"), rest("2026-09-26", "中秋节"), rest("2026-09-27", "中秋节"),
            legalHoliday("2026-10-01", "国庆节"), legalHoliday("2026-10-02", "国庆节"),
            legalHoliday("2026-10-03", "国庆节"), rest("2026-10-04", "国庆节"),
            rest("2026-10-05", "国庆节"), rest("2026-10-06", "国庆节"), rest("2026-10-07", "国庆节"),
            makeup("2026-10-10", "国庆节后补班")
        ))
        assertEquals(listOf("中秋节", "国庆节"), plans.map { it.name })
        assertTrue(plans[0].makeups.isEmpty())
        assertEquals(
            listOf(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-07")),
            plans[1].makeups.map { it.suggestedSource }
        )
    }

    /** 2024 官方安排：4月7日补4月5日课，9月14日补9月17日课。 */
    @Test fun shortHolidaysKeepTheirOwnSingleMakeupDay() {
        val plans = planHolidays(listOf(
            legalHoliday("2024-01-01", "元旦"),
            legalHoliday("2024-02-10", "春节"), legalHoliday("2024-02-11", "春节"), legalHoliday("2024-02-12", "初一"),
            rest("2024-02-13", "初二"), rest("2024-02-14", "初三"), rest("2024-02-15", "初四"),
            rest("2024-02-16", "初五"), rest("2024-02-17", "初六"),
            makeup("2024-02-04", "春节前补班"), makeup("2024-02-18", "春节后补班"),
            legalHoliday("2024-04-04", "清明节"), rest("2024-04-05", "清明节"), rest("2024-04-06", "清明节"),
            makeup("2024-04-07", "清明节后补班"),
            rest("2024-09-15", "中秋节"), rest("2024-09-16", "中秋节"), legalHoliday("2024-09-17", "中秋节"),
            makeup("2024-09-14", "中秋节前补班")
        ))
        assertEquals(listOf("元旦", "春节", "清明节", "中秋节"), plans.map { it.name })
        assertTrue(plans[0].makeups.isEmpty())
        assertEquals(LocalDate.parse("2024-04-05"), plans[2].makeups.single().suggestedSource)
        assertEquals(LocalDate.parse("2024-09-17"), plans[3].makeups.single().suggestedSource)
    }

    @Test fun weekendOnlyHolidayHasNoMakeupTarget() {
        val plans = planHolidays(listOf(
            rest("2026-01-03", "元旦"), rest("2026-01-04", "元旦"),
            makeup("2026-01-05", "元旦后补班")
        ))
        assertNull(plans.single().makeups.single().suggestedSource)
    }
}
