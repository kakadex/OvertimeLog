package com.overtimelog.data.local.mapper

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.data.local.entity.CompTimeUsageEntity
import com.overtimelog.data.local.entity.OvertimeRecordEntity
import com.overtimelog.data.local.entity.SalaryRuleEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Entity ↔ 领域模型转换。
 *
 * 时间统一按"系统时区"换算成 epoch；展示时再换回本地时间。
 * 这里用一个显式的 zone 参数而不是到处调 ZoneId.systemDefault()，以后做时区相关测试时可以直接注入。
 */
object Mappers {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()

    fun Long.toLocalDateTime(): LocalDateTime =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

    fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    fun OvertimeRecordEntity.toDomain(): OvertimeRecord = OvertimeRecord(
        id = id,
        workId = workId,
        date = LocalDate.ofEpochDay(dateEpochDay),
        start = startEpochMillis.toLocalDateTime(),
        end = endEpochMillis.toLocalDateTime(),
        breakMinutes = breakMinutes,
        type = OvertimeType.fromCode(type),
        durationMinutes = durationMinutes,
        hourlyRateCents = hourlyRateCents,
        multiplierBp = multiplierBp,
        amountCents = amountCents,
        compTimeMinutes = compTimeMinutes,
        compTimeMode = CompTimeMode.fromCode(compTimeMode),
        manualAmountCents = manualAmountCents,
        settlementStatus = SettlementStatus.fromCode(settlementStatus),
        note = note,
        ruleSnapshotJson = ruleSnapshotJson,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    fun OvertimeRecord.toEntity(): OvertimeRecordEntity = OvertimeRecordEntity(
        id = id,
        workId = workId,
        dateEpochDay = date.toEpochDay(),
        startEpochMillis = start.toEpochMillis(),
        endEpochMillis = end.toEpochMillis(),
        breakMinutes = breakMinutes,
        type = type.code,
        durationMinutes = durationMinutes,
        hourlyRateCents = hourlyRateCents,
        multiplierBp = multiplierBp,
        amountCents = amountCents,
        compTimeMinutes = compTimeMinutes,
        compTimeMode = compTimeMode.code,
        manualAmountCents = manualAmountCents,
        settlementStatus = settlementStatus.code,
        note = note,
        ruleSnapshotJson = ruleSnapshotJson,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

    fun SalaryRuleEntity.toDomain(): SalaryRule = SalaryRule(
        id = id,
        calculationMode = SalaryMode.fromCode(calculationMode),
        monthlySalaryCents = monthlySalaryCents,
        hourlyRateCents = hourlyRateCents,
        payDaysPerMonth = BigDecimal(payDays),
        standardHoursPerDay = BigDecimal(standardHoursPerDay),
        workdayMultiplierBp = workdayMultiplierBp,
        restDayMultiplierBp = restDayMultiplierBp,
        holidayMultiplierBp = holidayMultiplierBp,
        customMultiplierBp = customMultiplierBp,
        compTimeRatioBp = compTimeRatioBp,
        effectiveFrom = Instant.ofEpochMilli(effectiveFrom),
        active = active,
    )

    fun SalaryRule.toEntity(): SalaryRuleEntity = SalaryRuleEntity(
        id = id,
        calculationMode = calculationMode.code,
        monthlySalaryCents = monthlySalaryCents,
        hourlyRateCents = hourlyRateCents,
        payDays = payDaysPerMonth.toPlainString(),
        standardHoursPerDay = standardHoursPerDay.toPlainString(),
        workdayMultiplierBp = workdayMultiplierBp,
        restDayMultiplierBp = restDayMultiplierBp,
        holidayMultiplierBp = holidayMultiplierBp,
        customMultiplierBp = customMultiplierBp,
        compTimeRatioBp = compTimeRatioBp,
        effectiveFrom = effectiveFrom.toEpochMilli(),
        active = active,
    )

    fun CompTimeUsageEntity.toDomain(): CompTimeUsage = CompTimeUsage(
        id = id,
        date = LocalDate.ofEpochDay(dateEpochDay),
        minutes = minutes,
        note = note,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    fun CompTimeUsage.toEntity(): CompTimeUsageEntity = CompTimeUsageEntity(
        id = id,
        dateEpochDay = date.toEpochDay(),
        minutes = minutes,
        note = note,
        createdAt = createdAt.toEpochMilli(),
    )

    /** 把起始日期 + 两个 LocalTime 拼成跨天安全的起止时间。 */
    fun toDateTime(date: LocalDate, time: LocalTime): LocalDateTime = date.atTime(time)

    fun compTimeModeOrNone(code: String?): CompTimeMode = CompTimeMode.fromCode(code ?: "")
}
