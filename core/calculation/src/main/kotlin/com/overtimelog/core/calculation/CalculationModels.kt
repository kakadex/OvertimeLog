package com.overtimelog.core.calculation

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryRule
import java.time.LocalDateTime

/** 计算引擎的输入：用户在录入页填的东西，加上当前生效规则。 */
data class CalculationInput(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val breakMinutes: Int = 0,
    val type: OvertimeType = OvertimeType.WORKDAY,
    val rule: SalaryRule,
    val compTimeMode: CompTimeMode = CompTimeMode.NONE,
    /** 手动改金额（分）。非空时优先于自动金额，自动金额仍会写进快照。 */
    val manualAmountCents: Long? = null,
    /** 手动填调休分钟数，仅在 [CompTimeMode.MANUAL] 下生效。 */
    val manualCompTimeMinutes: Int? = null,
    /**
     * 「简单记模式」：用户直接填的加班分钟数。
     *
     * 非空时完全不看 [start]、[end]、[breakMinutes]，时长就用这个值 ——
     * 这正是"不想填上下班时间"的场景要的效果，且金额仍由同一个引擎算，不会出现两套算法。
     */
    val simpleDurationMinutes: Int? = null,
)

/** 计算结果。录入页的实时预览和最终落库用的是同一个方法，所以两者不会算出两个数。 */
data class CalculationResult(
    val durationMinutes: Int,
    val hourlyRateCents: Long,
    val multiplierBp: Int,
    val amountCents: Long,
    val compTimeMinutes: Int,
    val warnings: List<CalcWarning> = emptyList(),
)

/** 只算时长的结果，跨天与否单独标出来，方便 UI 提示"已按次日计算"。 */
data class DurationResult(
    val durationMinutes: Int,
    val rawMinutes: Int,
    val crossDay: Boolean,
)

/** 计算过程中的提醒。这些不是异常，UI 用黄色提示条展示即可。 */
sealed interface CalcWarning {
    data object EndBeforeStartTreatedAsNextDay : CalcWarning
    data object BreakExceedsDuration : CalcWarning
    data object DurationExceeds24Hours : CalcWarning
    data class CompTimeBalanceInsufficient(val balanceMinutes: Int) : CalcWarning
}
