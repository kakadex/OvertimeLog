package com.overtimelog.domain.usecase

import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.domain.repository.OvertimeRepository
import java.time.YearMonth

/**
 * 把某个月的全部记录统一标记为已结算 / 未结算。
 *
 * 返回实际改动的条数：已经是目标状态的记录不会重复写库，所以界面可以用
 * "改了 N 条 / 本来就是这个状态" 来反馈。
 */
class MarkMonthSettledUseCase(
    private val overtimeRepository: OvertimeRepository,
) {
    suspend operator fun invoke(month: YearMonth, settled: Boolean): Result<Int> = try {
        val status = if (settled) SettlementStatus.SETTLED else SettlementStatus.UNSETTLED
        Result.success(overtimeRepository.markMonthSettled(month, status))
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }
}
