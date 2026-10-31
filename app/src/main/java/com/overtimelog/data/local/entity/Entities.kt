package com.overtimelog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 加班记录表。字段与架构文档 8.1 的建表语句一一对应。
 *
 * 金额用"分"、倍率用"千分基点"、时间用 epoch，是为了让 SQL 能直接 SUM/GROUP BY，
 * 一万条记录做月度统计也不用把实体全部加载进内存。
 */
@Entity(
    tableName = "overtime_records",
    indices = [
        Index(value = ["date_epoch_day"]),
        Index(value = ["type"]),
        Index(value = ["settlement_status"]),
        Index(value = ["date_epoch_day", "type"]),
    ],
)
data class OvertimeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "work_id") val workId: Long? = null,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "end_epoch_millis") val endEpochMillis: Long,
    @ColumnInfo(name = "break_minutes") val breakMinutes: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "hourly_rate_cents") val hourlyRateCents: Long,
    @ColumnInfo(name = "multiplier_bp") val multiplierBp: Int,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "comp_time_minutes") val compTimeMinutes: Int,
    @ColumnInfo(name = "comp_time_mode") val compTimeMode: String,
    @ColumnInfo(name = "manual_amount_cents") val manualAmountCents: Long?,
    @ColumnInfo(name = "settlement_status") val settlementStatus: String,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "rule_snapshot_json") val ruleSnapshotJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /**
     * 1 = 这条记录是「简单记」录的（只填加班小时数，没填上下班时间）。
     *
     * 老记录没有这一列，迁移时统一填 0，即按原来的"计时录入"显示。
     */
    @ColumnInfo(name = "is_simple_entry", defaultValue = "0") val isSimpleEntry: Boolean = false,
)

/** 薪资规则表。V1 同时只会有一条 active = 1 的记录，历史规则保留不动。 */
@Entity(tableName = "salary_rules")
data class SalaryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "calculation_mode") val calculationMode: String,
    @ColumnInfo(name = "monthly_salary_cents") val monthlySalaryCents: Long,
    @ColumnInfo(name = "hourly_rate_cents") val hourlyRateCents: Long,
    @ColumnInfo(name = "pay_days") val payDays: String,
    @ColumnInfo(name = "standard_hours_per_day") val standardHoursPerDay: String,
    @ColumnInfo(name = "workday_multiplier_bp") val workdayMultiplierBp: Int,
    @ColumnInfo(name = "rest_day_multiplier_bp") val restDayMultiplierBp: Int,
    @ColumnInfo(name = "holiday_multiplier_bp") val holidayMultiplierBp: Int,
    @ColumnInfo(name = "custom_multiplier_bp") val customMultiplierBp: Int,
    @ColumnInfo(name = "comp_time_ratio_bp") val compTimeRatioBp: Int,
    @ColumnInfo(name = "effective_from") val effectiveFrom: Long,
    @ColumnInfo(name = "active") val active: Boolean,
)

/** 调休使用记录（"我用掉了一部分调休"）。 */
@Entity(tableName = "comp_time_usages", indices = [Index(value = ["date_epoch_day"])])
data class CompTimeUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "minutes") val minutes: Int,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * 节假日人工覆盖表。
 *
 * V1 不内置法定节假日数据（见 PRD 风险说明：年份/地区差异大），留表是为了 V1.1 能直接接 HolidayProvider。
 */
@Entity(tableName = "holiday_overrides")
data class HolidayOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "is_holiday") val isHoliday: Boolean,
    @ColumnInfo(name = "name") val name: String,
)
