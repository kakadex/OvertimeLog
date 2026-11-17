package com.overtimelog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 日历日期性质判定。
 *
 * 这是日历上"休 / 班"标记的唯一来源，判错的后果很直接：把补班日标成休息、
 * 或把法定节假日当成普通工作日，用户就会按错的倍率记加班。
 * 优先级：手动 > 远程 > 内置 > 星期几，这里逐条钉死。
 */
class HolidayCalendarTest {

    @Test
    fun `plain weekday is workday`() {
        // 2026-10-13 是星期二，不在任何假期里
        val info = HolidayCalendar.resolve(LocalDate.of(2026, 10, 13))
        assertEquals(DayKind.WORKDAY, info.kind)
        assertEquals("", info.name)
    }

    @Test
    fun `plain weekend is rest day`() {
        val info = HolidayCalendar.resolve(LocalDate.of(2026, 10, 17)) // 周六
        assertEquals(DayKind.REST_DAY, info.kind)
    }

    @Test
    fun `national holiday is recognized with name`() {
        val info = HolidayCalendar.resolve(LocalDate.of(2026, 10, 1))
        assertEquals(DayKind.HOLIDAY, info.kind)
        assertEquals("国庆节", info.name)
    }

    @Test
    fun `makeup workday on weekend is recognized`() {
        // 2026-10-10 是周六，但属于国庆调休补班
        val info = HolidayCalendar.resolve(LocalDate.of(2026, 10, 10))
        assertEquals(DayKind.MAKEUP_WORKDAY, info.kind)
        assertEquals("国庆节后补班", info.name)
    }

    @Test
    fun `spring festival days are holidays including eve`() {
        assertEquals(DayKind.HOLIDAY, HolidayCalendar.resolve(LocalDate.of(2026, 2, 16)).kind) // 除夕
        assertEquals(DayKind.HOLIDAY, HolidayCalendar.resolve(LocalDate.of(2026, 2, 17)).kind) // 初一
        assertEquals(DayKind.MAKEUP_WORKDAY, HolidayCalendar.resolve(LocalDate.of(2026, 2, 28)).kind)
    }

    @Test
    fun `manual override wins over built in data`() {
        val date = LocalDate.of(2026, 10, 1)
        val info = HolidayCalendar.resolve(date, manualOverrides = mapOf(date to DayKind.MAKEUP_WORKDAY))
        assertEquals(DayKind.MAKEUP_WORKDAY, info.kind)
        assertTrue(info.manual)
    }

    @Test
    fun `remote data wins over built in data but loses to manual`() {
        val date = LocalDate.of(2026, 10, 10)
        val remote = mapOf(date to ("公司调休" to true))
        assertEquals(DayKind.HOLIDAY, HolidayCalendar.resolve(date, remote = remote).kind)
        assertEquals("公司调休", HolidayCalendar.resolve(date, remote = remote).name)

        val manualRemote = HolidayCalendar.resolve(
            date,
            manualOverrides = mapOf(date to DayKind.WORKDAY),
            remote = remote,
        )
        assertEquals(DayKind.WORKDAY, manualRemote.kind)
    }

    @Test
    fun `unknown year has no data but still classifies weekends`() {
        val date = LocalDate.of(2028, 3, 4) // 周六
        assertFalse(HolidayCalendar.hasDataFor(2028))
        assertEquals(DayKind.REST_DAY, HolidayCalendar.resolve(date).kind)
    }

    @Test
    fun `remote years count as having data`() {
        assertFalse(HolidayCalendar.hasDataFor(2028))
        assertTrue(HolidayCalendar.hasDataFor(2028, remoteYears = setOf(2028)))
    }

    @Test
    fun `built in years cover 2025 and 2026`() {
        assertTrue(HolidayCalendar.hasDataFor(2025))
        assertTrue(HolidayCalendar.hasDataFor(2026))
    }

    // ---------- 默认加班类型跟随日期（2026-11-17 起） ----------

    @Test
    fun `plain weekday defaults to workday type`() {
        // 星期二，不在任何假期里；性质是工作日，默认类型也必须是工作日
        val date = LocalDate.of(2026, 10, 13)
        assertEquals(DayKind.WORKDAY, HolidayCalendar.resolve(date).kind)
        assertEquals(OvertimeType.WORKDAY, DayKind.WORKDAY.defaultOvertimeType())
    }

    @Test
    fun `weekend defaults to rest day type`() {
        assertEquals(OvertimeType.REST_DAY, DayKind.REST_DAY.defaultOvertimeType())
    }

    @Test
    fun `national holiday defaults to holiday type`() {
        assertEquals(OvertimeType.HOLIDAY, DayKind.HOLIDAY.defaultOvertimeType())
    }

    @Test
    fun `makeup workday on weekend defaults to workday type not rest day`() {
        // 补班日虽然落在周六日，但性质是"要上班"，默认必须按工作日算，不能按休息日
        assertEquals(OvertimeType.WORKDAY, DayKind.MAKEUP_WORKDAY.defaultOvertimeType())
    }

    @Test
    fun `day hints resolve default type for holiday makeup and weekend`() {
        val hints = DayHints()
        assertEquals(OvertimeType.HOLIDAY, hints.defaultOvertimeType(LocalDate.of(2026, 10, 1)))
        assertEquals(OvertimeType.WORKDAY, hints.defaultOvertimeType(LocalDate.of(2026, 10, 10))) // 补班
        assertEquals(OvertimeType.REST_DAY, hints.defaultOvertimeType(LocalDate.of(2026, 10, 17))) // 周六
        assertEquals(OvertimeType.WORKDAY, hints.defaultOvertimeType(LocalDate.of(2026, 10, 13))) // 周二
    }

    @Test
    fun `manual override changes the default type too`() {
        // 用户把国庆某天手动改成"补班"，默认类型要跟着变成工作日——两处判定必须是同一份数据
        val date = LocalDate.of(2026, 10, 1)
        val hints = DayHints(manual = mapOf(date to DayKind.MAKEUP_WORKDAY))
        assertEquals(OvertimeType.WORKDAY, hints.defaultOvertimeType(date))
    }

    @Test
    fun `remote data changes the default type too`() {
        val date = LocalDate.of(2026, 12, 25) // 周五，公司临时放假
        val hints = DayHints(remote = mapOf(date to ("公司假期" to true)))
        assertEquals(OvertimeType.HOLIDAY, hints.defaultOvertimeType(date))
    }

    @Test
    fun `day hints report holiday data availability`() {
        assertFalse(DayHints().hasDataFor(2029))
        assertTrue(DayHints(remoteYears = setOf(2029)).hasDataFor(2029))
        assertTrue(DayHints().hasDataFor(2026))
    }

    // ---------- 换日期时的类型联动（默认跟随日期，手动选过就保留） ----------

    @Test
    fun `changing date re-derives type when user never picked one`() {
        val hints = DayHints()
        val workday = LocalDate.of(2026, 10, 13) // 周二
        val holiday = LocalDate.of(2026, 10, 1) // 国庆节
        assertEquals(
            OvertimeType.HOLIDAY,
            hints.typeAfterDateChange(holiday, currentType = OvertimeType.WORKDAY, manuallySet = false),
        )
        assertEquals(
            OvertimeType.WORKDAY,
            hints.typeAfterDateChange(workday, currentType = OvertimeType.HOLIDAY, manuallySet = false),
        )
    }

    @Test
    fun `changing date keeps the user's own choice`() {
        val hints = DayHints()
        val workday = LocalDate.of(2026, 10, 13)
        // 用户在工作日把这笔记成"法定节假日"（比如公司另给假），换日期不能被冲掉
        assertEquals(
            OvertimeType.CUSTOM,
            hints.typeAfterDateChange(workday, currentType = OvertimeType.CUSTOM, manuallySet = true),
        )
    }

    @Test
    fun `saved record type equal to the auto value is treated as default`() {
        val hints = DayHints()
        val holiday = LocalDate.of(2026, 10, 1)
        // 当初就是默认值 → 继续跟随日期
        assertFalse(hints.isManualType(holiday, OvertimeType.HOLIDAY))
        // 当初改过 → 保留
        assertTrue(hints.isManualType(holiday, OvertimeType.WORKDAY))
    }
}
