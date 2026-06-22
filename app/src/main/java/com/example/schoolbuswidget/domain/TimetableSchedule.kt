package com.example.schoolbuswidget.domain

import java.time.LocalDate

enum class ScheduleRuleKind {
    /** 工作日或假期（依据节假日/周末规则） */
    DAY_TYPE,

    /** 每周固定星期 */
    WEEKLY,

    /** 日期区间（含起止日） */
    DATE_RANGE,
}

data class TimetableSchedule(
    val id: String,
    val name: String,
    val ruleKind: ScheduleRuleKind,
    val dayType: ServiceDayType? = null,
    /** ISO-8601：1=周一 … 7=周日 */
    val weekdays: Set<Int>? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)
