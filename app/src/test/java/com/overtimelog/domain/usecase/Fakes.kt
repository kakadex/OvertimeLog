package com.overtimelog.domain.usecase

import com.overtimelog.core.model.CompTimeBalance
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.core.model.YearSummary
import com.overtimelog.domain.repository.CompTimeRepository
import com.overtimelog.domain.repository.DayMarker
import com.overtimelog.domain.repository.OvertimeRepository
import com.overtimelog.domain.repository.RecordFilter
import com.overtimelog.domain.repository.SalaryRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.YearMonth

/**
 * 测试替身：内存里的仓库。
 *
 * 用例测试不碰 Room、不碰 Android，所以跑起来是毫秒级的；
 * 真正的 SQL 正确性交给 DAO 的 instrumentation 测试（后续里程碑补上）。
 */
class FakeOvertimeRepository : OvertimeRepository {

    val records = mutableListOf<OvertimeRecord>()
    private var nextId = 1L

    override fun observeRecords(filter: RecordFilter): Flow<List<OvertimeRecord>> =
        flowOf(records.toList())

    override fun observeDayRecords(date: LocalDate): Flow<List<OvertimeRecord>> =
        flowOf(records.filter { it.date == date })

    override fun observeMonthSummary(month: YearMonth, settlement: SettlementStatus?): Flow<MonthlySummary> =
        flowOf(MonthlySummary(month))

    override fun observeYearSummary(year: Int, settlement: SettlementStatus?): Flow<YearSummary> =
        flowOf(YearSummary(year))

    override fun observeDayMarkers(month: YearMonth): Flow<List<DayMarker>> = flowOf(emptyList())

    override suspend fun getById(id: Long): OvertimeRecord? = records.firstOrNull { it.id == id }

    override suspend fun insert(record: OvertimeRecord): Long {
        val id = nextId++
        records += record.copy(id = id)
        return id
    }

    override suspend fun update(record: OvertimeRecord) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record
    }

    override suspend fun delete(id: Long) {
        records.removeAll { it.id == id }
    }

    override suspend fun getAllInRange(from: LocalDate, to: LocalDate): List<OvertimeRecord> =
        records.filter { it.date >= from && it.date <= to }.sortedByDescending { it.start }

    override suspend fun markSettled(ids: List<Long>, status: SettlementStatus) {
        records.replaceAll { if (it.id in ids) it.copy(settlementStatus = status) else it }
    }

    override suspend fun markMonthSettled(month: YearMonth, status: SettlementStatus): Int {
        var changed = 0
        records.replaceAll { record ->
            val sameMonth = record.date.year == month.year && record.date.month == month.month
            if (sameMonth && record.settlementStatus != status) {
                changed++
                record.copy(settlementStatus = status)
            } else {
                record
            }
        }
        return changed
    }

    override suspend fun clearAll() = records.clear()
}

class FakeSalaryRuleRepository(var rule: SalaryRule?) : SalaryRuleRepository {
    override fun observeActiveRule(): Flow<SalaryRule?> = flowOf(rule)
    override suspend fun getActiveRule(): SalaryRule? = rule
    override suspend fun saveRule(rule: SalaryRule): SalaryRule {
        this.rule = rule
        return rule
    }
}

class FakeCompTimeRepository(private val balanceMinutes: Int = 0) : CompTimeRepository {

    val usages = mutableListOf<CompTimeUsage>()

    override fun observeBalance(): Flow<CompTimeBalance> =
        flowOf(CompTimeBalance(earnedMinutes = balanceMinutes, usedMinutes = 0))

    override fun observeUsages(): Flow<List<CompTimeUsage>> = flowOf(usages.toList())

    override suspend fun addUsage(usage: CompTimeUsage): Result<Unit> {
        usages += usage
        return Result.success(Unit)
    }

    override suspend fun deleteUsage(id: Long) {
        usages.removeAll { it.id == id }
    }

    override suspend fun clearAll() = usages.clear()
}
