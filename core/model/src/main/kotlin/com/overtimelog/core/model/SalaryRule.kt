package com.overtimelog.core.model

import java.math.BigDecimal
import java.time.Instant

/**
 * 薪资规则。
 *
 * V1 只保留"当前生效规则"（[active] = true），但表结构支持多条历史规则：
 * 记录落库时会把规则快照写进 [OvertimeRecord.ruleSnapshotJson]，所以改规则不会污染历史金额。
 *
 * 月薪换算：小时工资 = 月薪 ÷ 月计薪天数 ÷ 日标准工时。
 * 计薪天数和日工时用 [BigDecimal] 而不是 Double，避免 21.75 × 8 这类换算出现尾数误差。
 */
data class SalaryRule(
    val id: Long = 0,
    val calculationMode: SalaryMode = SalaryMode.MONTHLY,
    val monthlySalaryCents: Long = 0,
    val hourlyRateCents: Long = 0,
    val payDaysPerMonth: BigDecimal = DEFAULT_PAY_DAYS,
    val standardHoursPerDay: BigDecimal = DEFAULT_STANDARD_HOURS,
    val workdayMultiplierBp: Int = 1500,
    val restDayMultiplierBp: Int = 2000,
    val holidayMultiplierBp: Int = 3000,
    val customMultiplierBp: Int = 1000,
    val compTimeRatioBp: Int = 1000,
    val effectiveFrom: Instant = Instant.EPOCH,
    val active: Boolean = true,
) {
    /** 按加班类型取倍率（基点）。 */
    fun multiplierBpFor(type: OvertimeType): Int = when (type) {
        OvertimeType.WORKDAY -> workdayMultiplierBp
        OvertimeType.REST_DAY -> restDayMultiplierBp
        OvertimeType.HOLIDAY -> holidayMultiplierBp
        OvertimeType.CUSTOM -> customMultiplierBp
    }

    /** 规则是否已经配好可用于计算：时薪模式要有正时薪，月薪模式要有正工资。 */
    val isConfigured: Boolean
        get() = when (calculationMode) {
            SalaryMode.HOURLY -> hourlyRateCents > 0
            SalaryMode.MONTHLY -> monthlySalaryCents > 0 &&
                payDaysPerMonth > BigDecimal.ZERO &&
                standardHoursPerDay > BigDecimal.ZERO
        }

    companion object {
        /** 21.75 是国家规定的月计薪天数。 */
        val DEFAULT_PAY_DAYS: BigDecimal = BigDecimal("21.75")
        val DEFAULT_STANDARD_HOURS: BigDecimal = BigDecimal("8")
    }
}
