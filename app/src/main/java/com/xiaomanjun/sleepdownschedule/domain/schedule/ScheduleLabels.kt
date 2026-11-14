package com.xiaomanjun.sleepdownschedule.domain.schedule

fun scheduleWeekdayLabel(weekday: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]

/** Compress only consecutive week numbers; gaps must never become scheduled weeks. */
fun compactWeeksLabel(weeks: Collection<Int>, emptyLabel: String = "未选择"): String {
    return if (weeks.isEmpty()) emptyLabel else "第${compactNumberRanges(weeks)}周"
}

fun compactPeriodsLabel(periods: Collection<Int>, emptyLabel: String = "未指定节次"): String =
    if (periods.isEmpty()) emptyLabel else "第${compactNumberRanges(periods)}节"

private fun compactNumberRanges(values: Collection<Int>): String {
    val sorted = values.distinct().sorted()
    val ranges = buildList {
        var start = sorted.first()
        var previous = start
        sorted.drop(1).forEach { week ->
            if (week != previous + 1) {
                add(if (start == previous) "$start" else "$start–$previous")
                start = week
            }
            previous = week
        }
        add(if (start == previous) "$start" else "$start–$previous")
    }
    return ranges.joinToString("、")
}
