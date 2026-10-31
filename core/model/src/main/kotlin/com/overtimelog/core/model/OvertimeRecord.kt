package com.overtimelog.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 一条加班记录。
 *
 * 金额字段全部是"分"（Long），倍率是基点（Int）。这样做的原因见架构文档 ADR-001：
 * 浮点会在统计求和时累积误差，而整数分可以直接让 SQLite 做 SUM。
 *
 * [ruleSnapshotJson] 保存保存当时的规则快照：以后改倍率或时薪，这条记录的金额不会被悄悄改掉。
 */
data class OvertimeRecord(
    val id: Long = 0,
    val workId: Long? = null,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val breakMinutes: Int = 0,
    val type: OvertimeType = OvertimeType.WORKDAY,
    val durationMinutes: Int,
    val hourlyRateCents: Long,
    val multiplierBp: Int,
    val amountCents: Long,
    val compTimeMinutes: Int = 0,
    /** 这条记录选择的是哪种调休方式；编辑时要能原样回填，所以单独存一份。 */
    val compTimeMode: CompTimeMode = CompTimeMode.NONE,
    /** 用户手动改过的最终金额（分）；为空表示按规则自动计算。 */
    val manualAmountCents: Long? = null,
    val settlementStatus: SettlementStatus = SettlementStatus.UNSETTLED,
    val note: String = "",
    val ruleSnapshotJson: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * 这条记录是"简单记"录的：只填了加班小时数，没有真实的上下班时间。
     *
     * 为 true 时 [start]/[end] 只是占位（当天 00:00 起、按时长往后推），
     * 列表和导出都不显示具体时刻，避免让用户看到自己没填过的时间。
     */
    val isSimpleEntry: Boolean = false,
) {
    /** 时长展示成 "2 小时 30 分"，列表和预览共用，避免各页面各写一套。 */
    val durationText: String
        get() {
            val h = durationMinutes / 60
            val m = durationMinutes % 60
            return when {
                h == 0 -> "${m} 分钟"
                m == 0 -> "${h} 小时"
                else -> "${h} 小时 ${m} 分"
            }
        }
}
