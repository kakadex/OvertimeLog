package com.overtimelog.domain.usecase

import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.DomainError
import com.overtimelog.domain.repository.CompTimeRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 新增一条"用掉调休"的记录。
 *
 * 默认余额不足就拒绝（PRD FR-008：提示后允许用户确认继续）。UI 收到 [DomainError.CompTimeInsufficient]
 * 后弹确认框，用户点"仍然继续"时再用 allowOverdraft = true 调一次。
 */
class AddCompTimeUsageUseCase(
    private val compTimeRepository: CompTimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        date: LocalDate,
        minutes: Int,
        note: String = "",
        allowOverdraft: Boolean = false,
    ): Result<Unit> {
        if (minutes <= 0 || minutes > MAX_USAGE_MINUTES) return Result.failure(DomainError.InvalidAmount)
        if (note.length > MAX_NOTE_LENGTH) return Result.failure(DomainError.NoteTooLong)

        if (!allowOverdraft) {
            val balance = compTimeRepository.observeBalance().first().balanceMinutes
            if (minutes > balance) return Result.failure(DomainError.CompTimeInsufficient(balance))
        }

        return compTimeRepository.addUsage(
            CompTimeUsage(
                date = date,
                minutes = minutes,
                note = note.trim(),
                createdAt = Instant.now(clock),
            ),
        )
    }

    private companion object {
        const val MAX_USAGE_MINUTES = 24 * 60
        const val MAX_NOTE_LENGTH = 200
    }
}

class DeleteCompTimeUsageUseCase(
    private val compTimeRepository: CompTimeRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> = try {
        compTimeRepository.deleteUsage(id)
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }
}
