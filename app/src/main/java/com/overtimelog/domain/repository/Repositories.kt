package com.overtimelog.domain.repository

import com.overtimelog.core.model.CompTimeBalance
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.core.model.YearSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/** 列表筛选条件。null 表示"不限"，keyword 为空表示不搜备注。 */
data class RecordFilter(
    val month: YearMonth? = null,
    val type: OvertimeType? = null,
    val settlement: SettlementStatus? = null,
    val keyword: String = "",
)

/** 日历上某一天的标记信息。 */
data class DayMarker(
    val day: LocalDate,
    val types: Set<OvertimeType>,
    val count: Int,
)

/**
 * 加班记录仓库。接口定义在领域层，实现（Room）在 data 层 —— 这样用例和 ViewModel 都不认识 Room。
 */
interface OvertimeRepository {
    fun observeRecords(filter: RecordFilter): Flow<List<OvertimeRecord>>
    fun observeDayRecords(date: LocalDate): Flow<List<OvertimeRecord>>
    /** [settlement] 为空表示不筛选；统计页用它切换「全部 / 未结算 / 已结算」。 */
    fun observeMonthSummary(month: YearMonth, settlement: SettlementStatus? = null): Flow<MonthlySummary>
    fun observeYearSummary(year: Int, settlement: SettlementStatus? = null): Flow<YearSummary>
    fun observeDayMarkers(month: YearMonth): Flow<List<DayMarker>>
    suspend fun getById(id: Long): OvertimeRecord?
    suspend fun insert(record: OvertimeRecord): Long
    suspend fun update(record: OvertimeRecord)
    suspend fun delete(id: Long)
    suspend fun getAllInRange(from: LocalDate, to: LocalDate): List<OvertimeRecord>
    suspend fun markSettled(ids: List<Long>, status: SettlementStatus)

    /** 整月统一改结算状态，返回实际改动条数。 */
    suspend fun markMonthSettled(month: YearMonth, status: SettlementStatus): Int
    suspend fun clearAll()
}

interface SalaryRuleRepository {
    fun observeActiveRule(): Flow<SalaryRule?>
    suspend fun getActiveRule(): SalaryRule?
    suspend fun saveRule(rule: SalaryRule): SalaryRule
}

interface CompTimeRepository {
    fun observeBalance(): Flow<CompTimeBalance>
    fun observeUsages(): Flow<List<CompTimeUsage>>
    suspend fun addUsage(usage: CompTimeUsage): Result<Unit>
    suspend fun deleteUsage(id: Long)
    suspend fun clearAll()
}
