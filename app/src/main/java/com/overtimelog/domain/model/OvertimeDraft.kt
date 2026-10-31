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
 *
 * 「简单记模式」下用户只填一个加班小时数，这时 [simpleDurationMinutes] 非空，
 * [startTime]、[endTime]、[breakMinutes] 都不再参与计算（保留默认值只是为了少改调用方）。
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
    /**
     * 「简单记模式」的加班分钟数。
     *
     * 非空表示"只记时长、不记上下班时间"：计算引擎直接用这个时长，
     * 不再按起止时间相减，也不再扣休息；落库时把 [OvertimeRecord.isSimpleEntry] 标为 true。
     */
    val simpleDurationMinutes: Int? = null,
) {
    /** 是否走"只记时长"的录入方式。 */
    val isSimpleEntry: Boolean get() = simpleDurationMinutes != null
}
