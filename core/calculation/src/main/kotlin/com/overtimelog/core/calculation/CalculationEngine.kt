package com.overtimelog.core.calculation

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.core.model.SalaryRule
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime

/**
 * 计算引擎：全项目唯一的加班时长/加班费/调休算法实现（架构文档第 6 节）。
 *
 * 三条硬约束：
 * 1. 纯函数、无状态、无 IO、无 Android 依赖 —— 所以能在 JVM 上飞快地跑参数化测试；
 * 2. 金额全程 BigDecimal，最后一步才 HALF_UP 到"分"，避免 0.1 + 0.2 这类误差；
 * 3. 结束时间不晚于开始时间时，自动按"次日"处理，并给出提示，而不是报错。
 *
 * 录入页的实时预览和保存落库都调用 [calculate]，保证"看到多少就存多少"。
 */
class CalculationEngine {

    /**
     * 计算时长。
     *
     * @param breakMinutes 休息扣除分钟数，会直接从总时长里减掉。
     */
    fun calculateDuration(
        start: LocalDateTime,
        end: LocalDateTime,
        breakMinutes: Int,
    ): DurationResult {
        // 用 !isAfter 而不是 isBefore：开始和结束完全相同也算跨天（24 小时班次）。
        val crossDay = !end.isAfter(start)
        val realEnd = if (crossDay) end.plusDays(1) else end
        val rawMinutes = Duration.between(start, realEnd).toMinutes().toInt()
        return DurationResult(
            durationMinutes = rawMinutes - breakMinutes,
            rawMinutes = rawMinutes,
            crossDay = crossDay,
        )
    }

    /**
     * 换算小时工资（分）。
     *
     * 月薪模式：月薪 ÷ 计薪天数 ÷ 日标准工时。中间保留 10 位小数，最后四舍五入到分。
     */
    fun calculateHourlyRate(rule: SalaryRule): Long = when (rule.calculationMode) {
        SalaryMode.HOURLY -> rule.hourlyRateCents
        SalaryMode.MONTHLY -> {
            if (rule.payDaysPerMonth <= BigDecimal.ZERO || rule.standardHoursPerDay <= BigDecimal.ZERO) {
                0L
            } else {
                BigDecimal(rule.monthlySalaryCents)
                    .divide(rule.payDaysPerMonth, 10, RoundingMode.HALF_UP)
                    .divide(rule.standardHoursPerDay, 10, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLong()
            }
        }
    }

    /**
     * 一次算出时长、时薪、倍率、金额和调休分钟数。
     *
     * 注意：这里不做"是否允许保存"的裁决 —— 休息时长非法等情况只放进 [CalculationResult.warnings]，
     * 由用例层决定拒绝保存。这样录入页可以边输入边显示提示，而不是输入到一半就弹错。
     */
    fun calculate(input: CalculationInput): CalculationResult {
        val warnings = mutableListOf<CalcWarning>()

        val duration = calculateDuration(input.start, input.end, input.breakMinutes)
        if (duration.crossDay) {
            warnings += CalcWarning.EndBeforeStartTreatedAsNextDay
        }
        if (input.breakMinutes < 0 || input.breakMinutes >= duration.rawMinutes) {
            warnings += CalcWarning.BreakExceedsDuration
        }
        if (duration.durationMinutes > MAX_DURATION_MINUTES) {
            warnings += CalcWarning.DurationExceeds24Hours
        }

        val hourlyRateCents = calculateHourlyRate(input.rule)
        val multiplierBp = input.rule.multiplierBpFor(input.type)
        val safeMinutes = duration.durationMinutes.coerceAtLeast(0)

        val autoAmountCents = amountOf(hourlyRateCents, safeMinutes, multiplierBp)
        val amountCents = input.manualAmountCents ?: autoAmountCents

        val compTimeMinutes = compTimeOf(input, safeMinutes)

        return CalculationResult(
            durationMinutes = duration.durationMinutes,
            hourlyRateCents = hourlyRateCents,
            multiplierBp = multiplierBp,
            amountCents = amountCents,
            compTimeMinutes = compTimeMinutes,
            warnings = warnings,
        )
    }

    /**
     * 金额 = 时薪 × (分钟 / 60) × 倍率。
     *
     * 倍率用"千分基点"：1500 表示 1.5 倍，所以最后除以 1000，而不是 10000。
     * 中间保留 10 位小数，只在最后一步 HALF_UP 到分。
     */
    private fun amountOf(hourlyRateCents: Long, minutes: Int, multiplierBp: Int): Long =
        BigDecimal(hourlyRateCents)
            .multiply(BigDecimal(minutes))
            .divide(BigDecimal(60), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal(multiplierBp))
            .divide(BP_SCALE, 10, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

    /** 调休分钟数：1:1 就是加班分钟数，1:1.5 是 1.5 倍，手动模式直接用用户填的值。 */
    private fun compTimeOf(input: CalculationInput, minutes: Int): Int = when (input.compTimeMode) {
        CompTimeMode.NONE -> 0
        CompTimeMode.RATIO_1_1 -> minutes
        CompTimeMode.RATIO_1_1_5 -> BigDecimal(minutes)
            .multiply(BigDecimal("1.5"))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
        CompTimeMode.MANUAL -> input.manualCompTimeMinutes?.coerceAtLeast(0) ?: 0
    }

    private companion object {
        const val MAX_DURATION_MINUTES = 24 * 60
        /** 千分基点：1500 -> 1.5 倍。 */
        val BP_SCALE: BigDecimal = BigDecimal(1_000)
    }
}
