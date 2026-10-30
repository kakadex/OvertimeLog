package com.overtimelog.domain.usecase

import com.overtimelog.core.calculation.CalcWarning
import com.overtimelog.core.calculation.CalculationEngine
import com.overtimelog.core.calculation.CalculationInput
import com.overtimelog.core.calculation.CalculationResult
import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.domain.model.OvertimeDraft
import com.overtimelog.domain.repository.CompTimeRepository
import com.overtimelog.domain.repository.OvertimeRepository
import com.overtimelog.domain.repository.SalaryRuleRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/** 草稿 → 计算引擎输入。跨天不在这里处理，引擎自己会加一天。 */
internal fun OvertimeDraft.toInput(rule: SalaryRule): CalculationInput {
    val start = date.atTime(startTime)
    var end = date.atTime(endTime)
    if (!end.isAfter(start)) end = end.plusDays(1)
    return CalculationInput(
        start = start,
        end = end,
        breakMinutes = breakMinutes,
        type = type,
        rule = rule,
        compTimeMode = compTimeMode,
        manualAmountCents = manualAmountCents,
        manualCompTimeMinutes = manualCompTimeMinutes,
    )
}

/**
 * 规则快照。每条记录都存一份，以后改薪资规则不会让历史金额变样（ADR-002）。
 * 快照里同时保留"规则原始值"和"这次实际算出来的时薪/倍率"，方便对账。
 *
 * 这里手写 JSON 而不是用 org.json：用例是纯 Kotlin 逻辑，不应该依赖 Android SDK，
 * 否则它就没办法在普通 JVM 单测里跑了。
 */
internal object RuleSnapshot {

    fun toJson(rule: SalaryRule, result: CalculationResult, now: Instant): String = buildString {
        append('{')
        append("\"ruleId\":").append(rule.id).append(',')
        append("\"calculationMode\":\"").append(rule.calculationMode.code).append("\",")
        append("\"monthlySalaryCents\":").append(rule.monthlySalaryCents).append(',')
        append("\"hourlyRateCents\":").append(rule.hourlyRateCents).append(',')
        append("\"computedHourlyRateCents\":").append(result.hourlyRateCents).append(',')
        append("\"payDaysPerMonth\":\"").append(rule.payDaysPerMonth.toPlainString()).append("\",")
        append("\"standardHoursPerDay\":\"").append(rule.standardHoursPerDay.toPlainString()).append("\",")
        append("\"multiplierBp\":").append(result.multiplierBp).append(',')
        append("\"compTimeRatioBp\":").append(rule.compTimeRatioBp).append(',')
        append("\"snapshotAt\":\"").append(now).append("\"")
        append('}')
    }
}

/** 录入页的实时预览：不发工资、不写库，只算一遍。 */
class CalculatePreviewUseCase(
    private val ruleRepository: SalaryRuleRepository,
    private val engine: CalculationEngine,
) {
    suspend operator fun invoke(draft: OvertimeDraft): Result<CalculationResult> {
        val rule = ruleRepository.getActiveRule() ?: return Result.failure(DomainError.RuleNotConfigured)
        if (!rule.isConfigured) return Result.failure(DomainError.RuleNotConfigured)
        return Result.success(engine.calculate(draft.toInput(rule)))
    }
}

/**
 * 新增/更新记录共用的校验与装配逻辑。
 *
 * 所有校验都在用例层做（UI 只做即时提示），保存时重新读取当前规则再算一遍，
 * 避免"预览之后规则被改"导致存进去的金额和预览不一致（架构文档 9.1）。
 */
abstract class BaseSaveRecordUseCase(
    protected val recordRepository: OvertimeRepository,
    protected val ruleRepository: SalaryRuleRepository,
    protected val compTimeRepository: CompTimeRepository,
    protected val engine: CalculationEngine,
    protected val clock: Clock,
) {

    protected suspend fun assemble(
        draft: OvertimeDraft,
        recordId: Long,
        createdAt: Instant,
        now: Instant,
        allowCompOverdraft: Boolean,
    ): Result<OvertimeRecord> {
        if (draft.note.length > MAX_NOTE_LENGTH) return Result.failure(DomainError.NoteTooLong)
        if (draft.breakMinutes < 0 || draft.breakMinutes > MAX_BREAK_MINUTES) {
            return Result.failure(DomainError.InvalidBreak)
        }
        val manualAmount = draft.manualAmountCents
        if (manualAmount != null && (manualAmount < 0 || manualAmount > MAX_MANUAL_AMOUNT_CENTS)) {
            return Result.failure(DomainError.InvalidAmount)
        }

        val rule = ruleRepository.getActiveRule() ?: return Result.failure(DomainError.RuleNotConfigured)
        if (!rule.isConfigured) return Result.failure(DomainError.RuleNotConfigured)

        val calculation = engine.calculate(draft.toInput(rule))
        if (calculation.durationMinutes < 1) return Result.failure(DomainError.BreakExceedsDuration)
        if (calculation.durationMinutes > MAX_DURATION_MINUTES) return Result.failure(DomainError.DurationTooLong)

        if (!allowCompOverdraft && calculation.compTimeMinutes > 0) {
            val balance = compTimeRepository.observeBalance().first().balanceMinutes
            if (calculation.compTimeMinutes > balance) {
                return Result.failure(DomainError.CompTimeInsufficient(balance))
            }
        }

        val start = draft.date.atTime(draft.startTime)
        val rawEnd = draft.date.atTime(draft.endTime)
        val end = if (!rawEnd.isAfter(start)) rawEnd.plusDays(1) else rawEnd

        val record = OvertimeRecord(
            id = recordId,
            workId = draft.workId,
            date = draft.date,
            start = start,
            end = end,
            breakMinutes = draft.breakMinutes,
            type = draft.type,
            durationMinutes = calculation.durationMinutes,
            hourlyRateCents = calculation.hourlyRateCents,
            multiplierBp = calculation.multiplierBp,
            amountCents = calculation.amountCents,
            compTimeMinutes = calculation.compTimeMinutes,
            compTimeMode = draft.compTimeMode,
            manualAmountCents = draft.manualAmountCents,
            settlementStatus = draft.settlementStatus,
            note = draft.note.trim(),
            ruleSnapshotJson = RuleSnapshot.toJson(rule, calculation, now),
            createdAt = createdAt,
            updatedAt = now,
        )
        return Result.success(record)
    }

    protected companion object {
        const val MAX_NOTE_LENGTH = 200
        const val MAX_BREAK_MINUTES = 1439
        const val MAX_DURATION_MINUTES = 24 * 60
        const val MAX_MANUAL_AMOUNT_CENTS = 100_000_000L // 100 万元
    }
}

/** 新增加班记录。 */
class AddOvertimeRecordUseCase(
    recordRepository: OvertimeRepository,
    ruleRepository: SalaryRuleRepository,
    compTimeRepository: CompTimeRepository,
    engine: CalculationEngine,
    clock: Clock,
) : BaseSaveRecordUseCase(recordRepository, ruleRepository, compTimeRepository, engine, clock) {

    suspend operator fun invoke(
        draft: OvertimeDraft,
        allowCompOverdraft: Boolean = false,
    ): Result<OvertimeRecord> {
        val now = Instant.now(clock)
        val record = assemble(draft, 0, now, now, allowCompOverdraft).getOrElse { return Result.failure(it) }
        return try {
            val id = recordRepository.insert(record)
            Result.success(record.copy(id = id))
        } catch (t: Throwable) {
            Result.failure(DomainError.StorageError(t))
        }
    }
}

/** 修改已有记录，保持 createdAt 不变。 */
class UpdateOvertimeRecordUseCase(
    recordRepository: OvertimeRepository,
    ruleRepository: SalaryRuleRepository,
    compTimeRepository: CompTimeRepository,
    engine: CalculationEngine,
    clock: Clock,
) : BaseSaveRecordUseCase(recordRepository, ruleRepository, compTimeRepository, engine, clock) {

    suspend operator fun invoke(
        recordId: Long,
        draft: OvertimeDraft,
        allowCompOverdraft: Boolean = false,
    ): Result<OvertimeRecord> {
        val existing = recordRepository.getById(recordId)
            ?: return Result.failure(DomainError.RecordNotFound)
        val now = Instant.now(clock)
        val record = assemble(draft, recordId, existing.createdAt, now, allowCompOverdraft)
            .getOrElse { return Result.failure(it) }
        return try {
            recordRepository.update(record)
            Result.success(record)
        } catch (t: Throwable) {
            Result.failure(DomainError.StorageError(t))
        }
    }
}

/** 删除记录，返回被删的那条，供 Snackbar 的"撤销"使用。 */
class DeleteOvertimeRecordUseCase(
    private val recordRepository: OvertimeRepository,
) {
    suspend operator fun invoke(recordId: Long): Result<OvertimeRecord> {
        val record = recordRepository.getById(recordId)
            ?: return Result.failure(DomainError.RecordNotFound)
        return try {
            recordRepository.delete(recordId)
            Result.success(record)
        } catch (t: Throwable) {
            Result.failure(DomainError.StorageError(t))
        }
    }
}

/** 撤销删除：重新插入，id 重新生成。 */
class RestoreOvertimeRecordUseCase(
    private val recordRepository: OvertimeRepository,
) {
    suspend operator fun invoke(record: OvertimeRecord): Result<Unit> = try {
        recordRepository.insert(record.copy(id = 0))
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }
}

/** 批量标记已结算 / 未结算。 */
class MarkRecordsSettledUseCase(
    private val recordRepository: OvertimeRepository,
) {
    suspend operator fun invoke(ids: List<Long>, settled: Boolean): Result<Unit> = try {
        recordRepository.markSettled(
            ids,
            if (settled) com.overtimelog.core.model.SettlementStatus.SETTLED
            else com.overtimelog.core.model.SettlementStatus.UNSETTLED,
        )
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }
}

/** 清除全部记录（设置页"清除数据"）。 */
class ClearAllDataUseCase(
    private val recordRepository: OvertimeRepository,
    private val compTimeRepository: CompTimeRepository,
) {
    suspend operator fun invoke(): Result<Unit> = try {
        recordRepository.clearAll()
        compTimeRepository.clearAll()
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }
}

/** 给 UI 用的警告文案映射放在 Presentation 层，这里只提供一个便捷判断。 */
fun List<CalcWarning>.hasBlockingWarning(): Boolean =
    any { it is CalcWarning.BreakExceedsDuration }
