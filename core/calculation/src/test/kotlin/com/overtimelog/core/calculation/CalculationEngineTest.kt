package com.overtimelog.core.calculation

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.core.model.SalaryRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 计算引擎单测，对应 PRD 第 9.1 节的验收标准。
 *
 * 纯 JVM 测试：`./gradlew :core:calculation:test` 即可跑，不需要模拟器。
 */
class CalculationEngineTest {

    private val engine = CalculationEngine()

    /** 月薪 10000 元、21.75 天、8 小时 —— PRD 给出的验收示例。 */
    private val monthlyRule = SalaryRule(
        calculationMode = SalaryMode.MONTHLY,
        monthlySalaryCents = 1_000_000,
        payDaysPerMonth = BigDecimal("21.75"),
        standardHoursPerDay = BigDecimal("8"),
    )

    /** 时薪 50 元。 */
    private val hourlyRule = SalaryRule(
        calculationMode = SalaryMode.HOURLY,
        hourlyRateCents = 5_000,
    )

    private fun at(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 10, 30, hour, minute)

    // ---------- 时长 ----------

    @Test
    fun `workday 18-30 to 21-00 is 150 minutes`() {
        val r = engine.calculateDuration(at(18, 30), at(21), 0)
        assertEquals(150, r.durationMinutes)
        assertFalse(r.crossDay)
    }

    @Test
    fun `cross midnight 22-00 to 02-00 is 240 minutes`() {
        val r = engine.calculateDuration(at(22), at(2), 0)
        assertEquals(240, r.durationMinutes)
        assertTrue(r.crossDay)
    }

    @Test
    fun `break is subtracted from duration`() {
        val r = engine.calculateDuration(at(18), at(22), 30)
        assertEquals(210, r.durationMinutes)
        assertEquals(240, r.rawMinutes)
    }

    @Test
    fun `same start and end means 24 hours`() {
        val r = engine.calculateDuration(at(9), at(9), 0)
        assertEquals(1440, r.durationMinutes)
        assertTrue(r.crossDay)
    }

    // ---------- 时薪 ----------

    @Test
    fun `monthly salary 10000 gives hourly rate 57 yuan 47 fen`() {
        // 1 000 000 分 / 21.75 / 8 = 5747.126... 分 -> 5747 分 = 57.47 元
        assertEquals(5_747L, engine.calculateHourlyRate(monthlyRule))
    }

    @Test
    fun `hourly mode uses configured rate directly`() {
        assertEquals(5_000L, engine.calculateHourlyRate(hourlyRule))
    }

    @Test
    fun `invalid pay days yields zero instead of crashing`() {
        val broken = monthlyRule.copy(payDaysPerMonth = BigDecimal.ZERO)
        assertEquals(0L, engine.calculateHourlyRate(broken))
    }

    // ---------- 金额与倍率 ----------

    @Test
    fun `workday 1_5x and weekend 2x and holiday 3x`() {
        val start = at(18)
        val end = at(20) // 2 小时 = 120 分钟
        val workday = engine.calculate(
            CalculationInput(start, end, type = OvertimeType.WORKDAY, rule = hourlyRule),
        )
        val restDay = engine.calculate(
            CalculationInput(start, end, type = OvertimeType.REST_DAY, rule = hourlyRule),
        )
        val holiday = engine.calculate(
            CalculationInput(start, end, type = OvertimeType.HOLIDAY, rule = hourlyRule),
        )
        // 50 元/小时 × 2 小时 = 100 元 = 10000 分，再乘倍率
        assertEquals(15_000L, workday.amountCents) // 150 元
        assertEquals(20_000L, restDay.amountCents) // 200 元
        assertEquals(30_000L, holiday.amountCents) // 300 元
        assertEquals(1500, workday.multiplierBp)
        assertEquals(3000, holiday.multiplierBp)
    }

    @Test
    fun `monthly rule 2_5 hours workday is 215 yuan 51 fen`() {
        val r = engine.calculate(
            CalculationInput(at(18, 30), at(21), type = OvertimeType.WORKDAY, rule = monthlyRule),
        )
        assertEquals(150, r.durationMinutes)
        assertEquals(5_747L, r.hourlyRateCents)
        // 5747 × 150 / 60 × 1.5 = 21551.25 分 -> 21551 分 = 215.51 元
        assertEquals(21_551L, r.amountCents)
    }

    @Test
    fun `amount rounds half up to fen`() {
        // 100 分/小时、加班 1 分钟、1 倍：1.666... 分 -> 2 分
        val rounding = engine.calculate(
            CalculationInput(
                at(10), at(10, 1),
                type = OvertimeType.CUSTOM,
                rule = hourlyRule.copy(hourlyRateCents = 100),
            ),
        )
        assertEquals(2L, rounding.amountCents)

        // 1 分/小时、加班 1 分钟：0.0166 分 -> 0 分
        val tiny = engine.calculate(
            CalculationInput(
                at(10), at(10, 1),
                type = OvertimeType.CUSTOM,
                rule = hourlyRule.copy(hourlyRateCents = 1),
            ),
        )
        assertEquals(0L, tiny.amountCents)
    }

    @Test
    fun `manual amount overrides automatic amount`() {
        val r = engine.calculate(
            CalculationInput(
                start = at(18),
                end = at(20),
                rule = hourlyRule,
                manualAmountCents = 12_345,
            ),
        )
        assertEquals(12_345L, r.amountCents)
    }

    // ---------- 调休 ----------

    @Test
    fun `comp time 1 to 1 and 1 to 1_5`() {
        val base = CalculationInput(at(18), at(20), rule = hourlyRule)
        assertEquals(120, engine.calculate(base.copy(compTimeMode = CompTimeMode.RATIO_1_1)).compTimeMinutes)
        assertEquals(180, engine.calculate(base.copy(compTimeMode = CompTimeMode.RATIO_1_1_5)).compTimeMinutes)
        assertEquals(0, engine.calculate(base.copy(compTimeMode = CompTimeMode.NONE)).compTimeMinutes)
    }

    @Test
    fun `manual comp time uses user input`() {
        val r = engine.calculate(
            CalculationInput(
                start = at(18),
                end = at(20),
                rule = hourlyRule,
                compTimeMode = CompTimeMode.MANUAL,
                manualCompTimeMinutes = 90,
            ),
        )
        assertEquals(90, r.compTimeMinutes)
    }

    // ---------- 边界与提示 ----------

    @Test
    fun `cross day produces warning`() {
        val r = engine.calculate(CalculationInput(at(22), at(2), rule = hourlyRule))
        assertTrue(r.warnings.any { it is CalcWarning.EndBeforeStartTreatedAsNextDay })
        assertEquals(240, r.durationMinutes)
    }

    @Test
    fun `break larger than duration produces warning and non positive duration`() {
        val r = engine.calculate(CalculationInput(at(18), at(19), breakMinutes = 90, rule = hourlyRule))
        assertTrue(r.warnings.any { it is CalcWarning.BreakExceedsDuration })
        assertEquals(-30, r.durationMinutes)
    }

    @Test
    fun `duration over 24 hours produces warning`() {
        // 30 日 09:00 -> 31 日 10:00 = 25 小时（结束时间在时钟上晚于开始，所以不跨天，但超过 24 小时）
        val start = LocalDateTime.of(2026, 10, 30, 9, 0)
        val end = LocalDateTime.of(2026, 10, 31, 10, 0)
        val r = engine.calculate(CalculationInput(start, end, rule = hourlyRule))
        assertEquals(1500, r.durationMinutes)
        assertTrue(r.warnings.any { it is CalcWarning.DurationExceeds24Hours })
    }

    @Test
    fun `negative break produces warning`() {
        val r = engine.calculate(CalculationInput(at(18), at(20), breakMinutes = -10, rule = hourlyRule))
        assertTrue(r.warnings.any { it is CalcWarning.BreakExceedsDuration })
    }
}
