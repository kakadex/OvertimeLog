package com.overtimelog.domain.model

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import java.time.LocalDate
import java.time.LocalTime

/**
 * 录入页交给用例的"草稿"。
 *
 * 用 [LocalDate] + [LocalTime] 而不是完整时间戳，是因为用户填的就是"哪天的几点到几点"；
 * 跨天判断交给计算引擎，草稿本身不预设。
 */
data class OvertimeDraft(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val breakMinutes: Int = 0,
    val type: OvertimeType = OvertimeType.WORKDAY,
    val compTimeMode: CompTimeMode = CompTimeMode.NONE,
    val note: String = "",
    val settlementStatus: SettlementStatus = SettlementStatus.UNSETTLED,
    val manualAmountCents: Long? = null,
    val manualCompTimeMinutes: Int? = null,
    val workId: Long? = null,
)
