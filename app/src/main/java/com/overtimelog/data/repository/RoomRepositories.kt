package com.overtimelog.data.repository

import com.overtimelog.core.model.CompTimeBalance
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.MonthPoint
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.core.model.TypeSummary
import com.overtimelog.core.model.YearSummary
import com.overtimelog.data.local.dao.CompTimeUsageDao
import com.overtimelog.data.local.dao.DayMarkerRow
import com.overtimelog.data.local.dao.OvertimeRecordDao
import com.overtimelog.data.local.dao.SalaryRuleDao
import com.overtimelog.data.local.dao.TypeSummaryRow
import com.overtimelog.data.local.mapper.Mappers.toDomain
import com.overtimelog.data.local.mapper.Mappers.toEntity
import com.overtimelog.domain.repository.CompTimeRepository
import com.overtimelog.domain.repository.DayMarker
import com.overtimelog.domain.repository.OvertimeRepository
import com.overtimelog.domain.repository.RecordFilter
import com.overtimelog.domain.repository.SalaryRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/**
 * Room 实现的加班记录仓库。
 *
 * 月度汇总不是把记录全捞出来再算，而是用几条轻量查询（合计、按类型、获得调休、使用调休）
 * 在 SQL 层聚合后再 combine —— 记录涨到一万条时统计仍然很快。
 */
class RoomOvertimeRepository(
    private val recordDao: OvertimeRecordDao,
    private val usageDao: CompTimeUsageDao,
) : OvertimeRepository {

    override fun observeRecords(filter: RecordFilter): Flow<List<OvertimeRecord>> {
        val month = filter.month
        // 不筛月份时用一个足够大的区间，避免为"全部"单独写一条 SQL。
        val from = month?.atDay(1)?.toEpochDay() ?: Long.MIN_VALUE / 2
        val to = month?.atEndOfMonth()?.toEpochDay() ?: Long.MAX_VALUE / 2
        return recordDao
            .observeRange(
                from = from,
                to = to,
                type = filter.type?.code,
                settlement = filter.settlement?.code,
                keyword = filter.keyword.trim(),
            )
            .map { list -> list.map { it.toDomain() } }
    }

    override fun observeDayRecords(date: LocalDate): Flow<List<OvertimeRecord>> =
        recordDao.observeDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun observeMonthSummary(month: YearMonth, settlement: SettlementStatus?): Flow<MonthlySummary> {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        val status = settlement?.code
        return combine(
            recordDao.observeTotals(from, to, status),
            recordDao.observeMonthlyByType(from, to, status),
            // 调休是独立账本：不随结算状态筛选变化，始终按整月统计
            recordDao.observeCompEarned(from, to, null),
            usageDao.observeUsedInRange(from, to),
        ) { totals, byType, earned, used ->
            MonthlySummary(
                month = month,
                totalMinutes = totals.totalMinutes,
                totalAmountCents = totals.totalAmountCents,
                settledAmountCents = totals.settledAmountCents,
                unsettledAmountCents = totals.unsettledAmountCents,
                recordCount = totals.recordCount,
                overtimeDays = totals.overtimeDays,
                compTimeEarnedMinutes = earned,
                compTimeUsedMinutes = used,
                byType = byType.map { it.toTypeSummary() },
            )
        }
    }

    override fun observeYearSummary(year: Int, settlement: SettlementStatus?): Flow<YearSummary> {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return recordDao.observeMonthlyTotals(from, to, settlement?.code).map { rows ->
            val byMonth = rows.associateBy { it.month }
            val months = (1..12).map { m ->
                val row = byMonth[m]
                MonthPoint(
                    month = YearMonth.of(year, m),
                    minutes = row?.minutes ?: 0,
                    amountCents = row?.amountCents ?: 0L,
                )
            }
            YearSummary(year = year, months = months)
        }
    }

    override fun observeDayMarkers(month: YearMonth): Flow<List<DayMarker>> {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        return recordDao.observeDayMarkers(from, to).map { rows -> rows.map { it.toDayMarker() } }
    }

    override suspend fun getById(id: Long): OvertimeRecord? = recordDao.getById(id)?.toDomain()

    override suspend fun insert(record: OvertimeRecord): Long = recordDao.insert(record.toEntity())

    override suspend fun update(record: OvertimeRecord) = recordDao.update(record.toEntity())

    override suspend fun delete(id: Long) = recordDao.deleteById(id)

    override suspend fun getAllInRange(from: LocalDate, to: LocalDate): List<OvertimeRecord> =
        recordDao.getAllInRange(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun markSettled(ids: List<Long>, status: SettlementStatus) {
        if (ids.isEmpty()) return
        recordDao.updateSettlement(ids, status.code, System.currentTimeMillis())
    }

    override suspend fun clearAll() = recordDao.deleteAll()

    private fun TypeSummaryRow.toTypeSummary(): TypeSummary = TypeSummary(
        type = OvertimeType.fromCode(type),
        minutes = totalMinutes,
        amountCents = totalAmountCents,
        count = count,
    )

    private fun DayMarkerRow.toDayMarker(): DayMarker = DayMarker(
        day = LocalDate.ofEpochDay(day),
        types = types
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .map { OvertimeType.fromCode(it) }
            .toSet(),
        count = count,
    )
}

/** 薪资规则仓库：保存新规则时把旧规则置为 inactive，历史记录仍保留自己的快照。 */
class RoomSalaryRuleRepository(
    private val salaryRuleDao: SalaryRuleDao,
) : SalaryRuleRepository {

    override fun observeActiveRule(): Flow<SalaryRule?> =
        salaryRuleDao.observeActive().map { it?.toDomain() }

    override suspend fun getActiveRule(): SalaryRule? =
        salaryRuleDao.getActive()?.toDomain()

    override suspend fun saveRule(rule: SalaryRule): SalaryRule = try {
        val id = salaryRuleDao.insert(rule.copy(active = true).toEntity())
        salaryRuleDao.deactivateAllExcept(id)
        rule.copy(id = id, active = true)
    } catch (t: Throwable) {
        throw DomainError.StorageError(t)
    }
}

/** 调休仓库：获得数来自加班记录，使用数来自使用记录，余额是两者之差。 */
class RoomCompTimeRepository(
    private val recordDao: OvertimeRecordDao,
    private val usageDao: CompTimeUsageDao,
) : CompTimeRepository {

    override fun observeBalance(): Flow<CompTimeBalance> =
        combine(recordDao.observeCompEarnedAll(), usageDao.observeUsedAll()) { earned, used ->
            CompTimeBalance(earnedMinutes = earned, usedMinutes = used)
        }

    override fun observeUsages(): Flow<List<CompTimeUsage>> =
        usageDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addUsage(usage: CompTimeUsage): Result<Unit> = try {
        usageDao.insert(usage.toEntity())
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(DomainError.StorageError(t))
    }

    override suspend fun deleteUsage(id: Long) = usageDao.deleteById(id)

    override suspend fun clearAll() = usageDao.deleteAll()
}
