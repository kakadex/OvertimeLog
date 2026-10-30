package com.overtimelog.domain.usecase

import com.overtimelog.core.calculation.CalculationEngine
import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.domain.model.OvertimeDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 用例层单测：校验、计算、规则快照、调休余额都在这里覆盖，不需要模拟器。
 */
class RecordUseCasesTest {

    private val date = LocalDate.of(2026, 10, 30)
    private val clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("Asia/Shanghai"))

    private val rule = SalaryRule(
        id = 1,
        calculationMode = SalaryMode.MONTHLY,
        monthlySalaryCents = 1_000_000,
        payDaysPerMonth = BigDecimal("21.75"),
        standardHoursPerDay = BigDecimal("8"),
    )

    private fun draft(
        start: LocalTime = LocalTime.of(18, 30),
        end: LocalTime = LocalTime.of(21, 0),
        breakMinutes: Int = 0,
        compTimeMode: CompTimeMode = CompTimeMode.NONE,
        note: String = "",
        manualAmountCents: Long? = null,
    ) = OvertimeDraft(
        date = date,
        startTime = start,
        endTime = end,
        breakMinutes = breakMinutes,
        type = OvertimeType.WORKDAY,
        compTimeMode = compTimeMode,
        note = note,
        manualAmountCents = manualAmountCents,
    )

    private fun addUseCase(
        records: FakeOvertimeRepository,
        rules: FakeSalaryRuleRepository,
        compTime: FakeCompTimeRepository = FakeCompTimeRepository(),
    ) = AddOvertimeRecordUseCase(records, rules, compTime, CalculationEngine(), clock)

    @Test
    fun `add record calculates duration and amount`() = runTest {
        val records = FakeOvertimeRepository()
        val result = addUseCase(records, FakeSalaryRuleRepository(rule))(draft())

        val record = result.getOrThrow()
        assertEquals(150, record.durationMinutes)
        assertEquals(21_551L, record.amountCents)
        assertEquals(1L, record.id)
        assertEquals(1, records.records.size)
        // 规则快照必须落库，否则以后改规则历史金额就说不清了
        assertTrue(record.ruleSnapshotJson.contains("\"multiplierBp\":1500"))
        assertTrue(record.ruleSnapshotJson.contains("\"computedHourlyRateCents\":5747"))
    }

    @Test
    fun `cross midnight record lasts four hours`() = runTest {
        val records = FakeOvertimeRepository()
        val result = addUseCase(records, FakeSalaryRuleRepository(rule))(
            draft(start = LocalTime.of(22, 0), end = LocalTime.of(2, 0)),
        )
        val record = result.getOrThrow()
        assertEquals(240, record.durationMinutes)
        // 存的结束时间是次日
        assertEquals(date.plusDays(1), record.end.toLocalDate())
    }

    @Test
    fun `break longer than duration is rejected`() = runTest {
        val records = FakeOvertimeRepository()
        val result = addUseCase(records, FakeSalaryRuleRepository(rule))(
            draft(start = LocalTime.of(18, 0), end = LocalTime.of(19, 0), breakMinutes = 90),
        )
        assertEquals(DomainError.BreakExceedsDuration, result.exceptionOrNull())
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun `note longer than 200 chars is rejected`() = runTest {
        val records = FakeOvertimeRepository()
        val result = addUseCase(records, FakeSalaryRuleRepository(rule))(draft(note = "啊".repeat(201)))
        assertEquals(DomainError.NoteTooLong, result.exceptionOrNull())
    }

    @Test
    fun `missing salary rule is rejected`() = runTest {
        val records = FakeOvertimeRepository()
        val result = addUseCase(records, FakeSalaryRuleRepository(null))(draft())
        assertEquals(DomainError.RuleNotConfigured, result.exceptionOrNull())
    }

    @Test
    fun `comp time above balance is rejected unless overdraft allowed`() = runTest {
        val records = FakeOvertimeRepository()
        val useCase = addUseCase(records, FakeSalaryRuleRepository(rule), FakeCompTimeRepository(0))

        val rejected = useCase(draft(compTimeMode = CompTimeMode.RATIO_1_1))
        assertTrue(rejected.exceptionOrNull() is DomainError.CompTimeInsufficient)

        val allowed = useCase(draft(compTimeMode = CompTimeMode.RATIO_1_1), allowCompOverdraft = true)
        assertEquals(150, allowed.getOrThrow().compTimeMinutes)
    }

    @Test
    fun `manual amount is stored and wins over automatic amount`() = runTest {
        val records = FakeOvertimeRepository()
        val record = addUseCase(records, FakeSalaryRuleRepository(rule))(
            draft(manualAmountCents = 30_000),
        ).getOrThrow()
        assertEquals(30_000L, record.amountCents)
        assertEquals(30_000L, record.manualAmountCents)
    }

    @Test
    fun `update keeps createdAt and replaces fields`() = runTest {
        val records = FakeOvertimeRepository()
        val rules = FakeSalaryRuleRepository(rule)
        val added = addUseCase(records, rules)(draft()).getOrThrow()

        val update = UpdateOvertimeRecordUseCase(records, rules, FakeCompTimeRepository(), CalculationEngine(), clock)
        val updated = update(
            added.id,
            draft(start = LocalTime.of(18, 0), end = LocalTime.of(22, 0), note = "改了"),
        ).getOrThrow()

        assertEquals(added.createdAt, updated.createdAt)
        assertEquals(240, updated.durationMinutes)
        assertEquals("改了", updated.note)
        assertEquals(1, records.records.size)
    }

    @Test
    fun `delete returns record and removes it`() = runTest {
        val records = FakeOvertimeRepository()
        val added = addUseCase(records, FakeSalaryRuleRepository(rule))(draft()).getOrThrow()

        val deleted = DeleteOvertimeRecordUseCase(records)(added.id).getOrThrow()
        assertEquals(added.id, deleted.id)
        assertTrue(records.records.isEmpty())

        // 撤销删除：重新插入，生成新 id
        RestoreOvertimeRecordUseCase(records)(deleted).getOrThrow()
        assertEquals(1, records.records.size)
        assertNotNull(records.records.first().id)
    }

    @Test
    fun `add comp time usage above balance asks for confirmation`() = runTest {
        val compTime = FakeCompTimeRepository(balanceMinutes = 30)
        val useCase = AddCompTimeUsageUseCase(compTime, clock)

        val rejected = useCase(date, 60)
        assertTrue(rejected.exceptionOrNull() is DomainError.CompTimeInsufficient)

        val allowed = useCase(date, 60, allowOverdraft = true)
        assertTrue(allowed.isSuccess)
        assertEquals(1, compTime.usages.size)
    }
}
