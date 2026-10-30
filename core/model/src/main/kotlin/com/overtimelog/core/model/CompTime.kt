package com.overtimelog.core.model

import java.time.Instant
import java.time.LocalDate

/** 一次"用掉调休"的记录。[minutes] 是使用分钟数，余额 = 获得 - 使用。 */
data class CompTimeUsage(
    val id: Long = 0,
    val date: LocalDate,
    val minutes: Int,
    val note: String = "",
    val createdAt: Instant = Instant.now(),
)

/** 调休余额。三者都可能是负数（透支时 balance 为负），UI 需要能显示出来并提示。 */
data class CompTimeBalance(
    val earnedMinutes: Int = 0,
    val usedMinutes: Int = 0,
) {
    val balanceMinutes: Int get() = earnedMinutes - usedMinutes
}
