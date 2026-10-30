package com.overtimelog.ui.common

import com.overtimelog.core.calculation.CalcWarning
import com.overtimelog.core.model.DomainError

/**
 * 把领域错误翻译成中文提示。
 *
 * 领域层只负责"是什么错"，这里负责"怎么说给用户听"，所以改文案不用动用例。
 */
fun Throwable.toUiMessage(): String = when (this) {
    is DomainError -> message ?: "操作失败"
    else -> "操作失败，请重试"
}

fun CalcWarning.toUiMessage(): String = when (this) {
    is CalcWarning.EndBeforeStartTreatedAsNextDay -> "结束时间早于开始时间，已按次日计算"
    is CalcWarning.BreakExceedsDuration -> "休息时长不能大于等于加班总时长"
    is CalcWarning.DurationExceeds24Hours -> "单次加班超过 24 小时，请确认是否填错"
    is CalcWarning.CompTimeBalanceInsufficient -> "调休余额不足，只有 $balanceMinutes 分钟"
}
