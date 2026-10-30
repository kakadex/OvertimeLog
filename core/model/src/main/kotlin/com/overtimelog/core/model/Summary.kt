package com.overtimelog.core.model

import java.time.YearMonth

/** 按加班类型拆开的月度小计，统计页的表格和图表都用它。 */
data class TypeSummary(
    val type: OvertimeType,
    val minutes: Int,
    val amountCents: Long,
    val count: Int,
)

/**
 * 月度汇总。所有统计都由 SQL 聚合出来，不加载全量记录（架构文档 8.2）。
 */
data class MonthlySummary(
    val month: YearMonth,
    val totalMinutes: Int = 0,
    val totalAmountCents: Long = 0,
    val settledAmountCents: Long = 0,
    val unsettledAmountCents: Long = 0,
    val recordCount: Int = 0,
    val overtimeDays: Int = 0,
    val compTimeEarnedMinutes: Int = 0,
    val compTimeUsedMinutes: Int = 0,
    val byType: List<TypeSummary> = emptyList(),
) {
    val compTimeBalanceMinutes: Int get() = compTimeEarnedMinutes - compTimeUsedMinutes
}

/** 年度里的一个月，用于年度统计的柱状图。 */
data class MonthPoint(
    val month: YearMonth,
    val minutes: Int,
    val amountCents: Long,
)

data class YearSummary(
    val year: Int,
    val months: List<MonthPoint> = emptyList(),
) {
    val totalMinutes: Int get() = months.sumOf { it.minutes }
    val totalAmountCents: Long get() = months.sumOf { it.amountCents }
}
