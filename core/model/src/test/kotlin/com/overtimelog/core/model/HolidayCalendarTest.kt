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
}
