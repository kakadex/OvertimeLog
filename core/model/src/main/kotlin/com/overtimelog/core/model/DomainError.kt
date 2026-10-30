package com.overtimelog.core.model

/**
 * 领域层错误。用例返回 Result<T>，失败时带这些类型；UI 再把它们翻译成中文文案。
 *
 * 这样做的原因：领域层不写 UI 文案，换语言或改措辞时只改 Presentation 层。
 */
sealed class DomainError : Throwable() {
    data object InvalidTimeRange : DomainError()
    data object InvalidBreak : DomainError()
    data object BreakExceedsDuration : DomainError()
    data object DurationTooLong : DomainError()
    data object RuleNotConfigured : DomainError()
    data object NoteTooLong : DomainError()
    data object InvalidAmount : DomainError()
    data object RecordNotFound : DomainError()
    data class CompTimeInsufficient(val balance: Int) : DomainError()
    data class StorageError(val reason: Throwable) : DomainError()

    override val message: String
        get() = when (this) {
            is InvalidTimeRange -> "时间范围不合法"
            is InvalidBreak -> "休息时长只能是 0 到 1439 分钟"
            is BreakExceedsDuration -> "休息时长不能大于等于总时长"
            is DurationTooLong -> "单条记录不能超过 24 小时"
            is RuleNotConfigured -> "薪资规则还没配置"
            is NoteTooLong -> "备注最多 200 字"
            is InvalidAmount -> "金额或分钟数不合法"
            is RecordNotFound -> "记录不存在，可能已被删除"
            is CompTimeInsufficient -> "调休余额不足（剩余 $balance 分钟）"
            is StorageError -> "数据读写失败"
        }
}
