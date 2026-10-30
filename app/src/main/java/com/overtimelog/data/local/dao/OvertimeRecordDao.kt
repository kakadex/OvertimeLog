package com.overtimelog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.overtimelog.data.local.entity.OvertimeRecordEntity
import kotlinx.coroutines.flow.Flow

/** 月度按类型的聚合行。列名必须与 SQL 里的别名一致，Room 靠名字映射。 */
data class TypeSummaryRow(
    val type: String,
    val totalMinutes: Int,
    val totalAmountCents: Long,
    val count: Int,
)

/** 月度合计。 */
data class MonthTotalsRow(
    val totalMinutes: Int,
    val totalAmountCents: Long,
    val settledAmountCents: Long,
    val unsettledAmountCents: Long,
    val recordCount: Int,
    val overtimeDays: Int,
)

/** 日历用的轻量投影：哪些天有记录、有哪些类型。 */
data class DayMarkerRow(
    val day: Long,
    val count: Int,
    val types: String?,
)

/** 年度统计的按月聚合行。[month] 是 1..12。 */
data class MonthRow(
    val month: Int,
    val minutes: Int,
    val amountCents: Long,
)

@Dao
interface OvertimeRecordDao {

    /**
     * 列表查询。
     *
     * 三个筛选条件都允许传 null 表示"不限"；关键字只在非空时参与 LIKE。
     * 排序用开始时间倒序，同一天里晚开始的排在前面。
     */
    @Query(
        """
        SELECT * FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
          AND (:type IS NULL OR type = :type)
          AND (:settlement IS NULL OR settlement_status = :settlement)
          AND (:keyword = '' OR note LIKE '%' || :keyword || '%')
        ORDER BY start_epoch_millis DESC
        """,
    )
    fun observeRange(
        from: Long,
        to: Long,
        type: String?,
        settlement: String?,
        keyword: String,
    ): Flow<List<OvertimeRecordEntity>>

    @Query("SELECT * FROM overtime_records WHERE date_epoch_day = :epochDay ORDER BY start_epoch_millis ASC")
    fun observeDay(epochDay: Long): Flow<List<OvertimeRecordEntity>>

    /** 概览页"最近记录"只要前 N 条，别把整月都读进内存。 */
    @Query(
        """
        SELECT * FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
        ORDER BY start_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(from: Long, to: Long, limit: Int): Flow<List<OvertimeRecordEntity>>

    @Query("SELECT * FROM overtime_records WHERE id = :id")
    suspend fun getById(id: Long): OvertimeRecordEntity?

    @Insert
    suspend fun insert(entity: OvertimeRecordEntity): Long

    @Update
    suspend fun update(entity: OvertimeRecordEntity)

    @Query("DELETE FROM overtime_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM overtime_records WHERE date_epoch_day BETWEEN :from AND :to ORDER BY start_epoch_millis DESC")
    suspend fun getAllInRange(from: Long, to: Long): List<OvertimeRecordEntity>

    @Query(
        """
        SELECT type AS type,
               COALESCE(SUM(duration_minutes), 0) AS totalMinutes,
               COALESCE(SUM(amount_cents), 0) AS totalAmountCents,
               COUNT(*) AS count
        FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
          AND (:settlement IS NULL OR settlement_status = :settlement)
        GROUP BY type
        ORDER BY totalMinutes DESC
        """,
    )
    fun observeMonthlyByType(
        from: Long,
        to: Long,
        settlement: String?,
    ): Flow<List<TypeSummaryRow>>

    @Query(
        """
        SELECT COALESCE(SUM(duration_minutes), 0) AS totalMinutes,
               COALESCE(SUM(amount_cents), 0) AS totalAmountCents,
               COALESCE(SUM(CASE WHEN settlement_status = 'SETTLED' THEN amount_cents ELSE 0 END), 0) AS settledAmountCents,
               COALESCE(SUM(CASE WHEN settlement_status = 'UNSETTLED' THEN amount_cents ELSE 0 END), 0) AS unsettledAmountCents,
               COUNT(*) AS recordCount,
               COUNT(DISTINCT date_epoch_day) AS overtimeDays
        FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
          AND (:settlement IS NULL OR settlement_status = :settlement)
        """,
    )
    fun observeTotals(from: Long, to: Long, settlement: String?): Flow<MonthTotalsRow>

    @Query(
        """
        SELECT COALESCE(SUM(comp_time_minutes), 0) FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
          AND (:settlement IS NULL OR settlement_status = :settlement)
        """,
    )
    fun observeCompEarned(from: Long, to: Long, settlement: String?): Flow<Int>

    @Query("SELECT COALESCE(SUM(comp_time_minutes), 0) FROM overtime_records")
    fun observeCompEarnedAll(): Flow<Int>

    @Query(
        """
        SELECT date_epoch_day AS day, COUNT(*) AS count, GROUP_CONCAT(DISTINCT type) AS types
        FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
        GROUP BY date_epoch_day
        """,
    )
    fun observeDayMarkers(from: Long, to: Long): Flow<List<DayMarkerRow>>

    /**
     * 年度：按月聚合。
     *
     * date_epoch_day 是"天序号"，乘 86400 当成秒喂给 SQLite 的 unixepoch，再取月份。
     * 因为我们存的本来就是本地日历日，用 UTC 解析回来仍是同一个日期，不会跨时区错位。
     */
    @Query(
        """
        SELECT CAST(strftime('%m', date_epoch_day * 86400, 'unixepoch') AS INTEGER) AS month,
               COALESCE(SUM(duration_minutes), 0) AS minutes,
               COALESCE(SUM(amount_cents), 0) AS amountCents
        FROM overtime_records
        WHERE date_epoch_day BETWEEN :from AND :to
          AND (:settlement IS NULL OR settlement_status = :settlement)
        GROUP BY month
        """,
    )
    fun observeMonthlyTotals(from: Long, to: Long, settlement: String?): Flow<List<MonthRow>>

    @Query("UPDATE overtime_records SET settlement_status = :status, updated_at = :now WHERE id IN (:ids)")
    suspend fun updateSettlement(ids: List<Long>, status: String, now: Long)

    /** 整月批量改结算状态：只改状态不同的行，返回实际改动条数。 */
    @Query(
        """
        UPDATE overtime_records
        SET settlement_status = :status, updated_at = :now
        WHERE date_epoch_day BETWEEN :from AND :to
          AND settlement_status != :status
        """,
    )
    suspend fun updateSettlementInRange(from: Long, to: Long, status: String, now: Long): Int

    @Query("DELETE FROM overtime_records")
    suspend fun deleteAll()
}
