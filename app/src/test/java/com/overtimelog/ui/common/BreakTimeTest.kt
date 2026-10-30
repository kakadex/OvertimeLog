package com.overtimelog.ui.common

import com.overtimelog.data.settings.BreakUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 休息扣除单位换算。
 *
 * 这里最容易出错的是"小时小数"和"边界"：0.5 小时必须是 30 分钟，
 * 24 小时必须被判为非法（超过 1439 分钟上限），否则会把休息扣成负数时长。
 */
class BreakTimeTest {

    @Test
    fun `format hours keeps at most two decimals`() {
        assertEquals("0", BreakTime.format(0, BreakUnit.HOURS))
        assertEquals("0.5", BreakTime.format(30, BreakUnit.HOURS))
        assertEquals("2.5", BreakTime.format(150, BreakUnit.HOURS))
        assertEquals("1.25", BreakTime.format(75, BreakUnit.HOURS))
    }

    @Test
    fun `format minutes is plain integer`() {
        assertEquals("0", BreakTime.format(0, BreakUnit.MINUTES))
        assertEquals("90", BreakTime.format(90, BreakUnit.MINUTES))
    }

    @Test
    fun `parse hours converts to minutes`() {
        assertEquals(30, BreakTime.parse("0.5", BreakUnit.HOURS))
        assertEquals(75, BreakTime.parse("1.25", BreakUnit.HOURS))
        assertEquals(60, BreakTime.parse("1", BreakUnit.HOURS))
        assertEquals(0, BreakTime.parse("0", BreakUnit.HOURS))
    }

    @Test
    fun `parse minutes stays minutes`() {
        assertEquals(90, BreakTime.parse("90", BreakUnit.MINUTES))
        assertEquals(0, BreakTime.parse("0", BreakUnit.MINUTES))
    }

    @Test
    fun `invalid and out of range input returns null`() {
        assertNull(BreakTime.parse("", BreakUnit.HOURS))
        assertNull(BreakTime.parse("abc", BreakUnit.HOURS))
        assertNull(BreakTime.parse("-1", BreakUnit.MINUTES))
        assertNull(BreakTime.parse("24", BreakUnit.HOURS)) // 1440 分钟超过上限
        assertNull(BreakTime.parse("1440", BreakUnit.MINUTES))
        assertNull(BreakTime.parse("1.5.2", BreakUnit.HOURS))
    }

    @Test
    fun `hint shows the other unit`() {
        assertEquals("= 30 分钟", BreakTime.hint("0.5", BreakUnit.HOURS))
        assertEquals("= 1.5 小时", BreakTime.hint("90", BreakUnit.MINUTES))
        assertNull(BreakTime.hint("abc", BreakUnit.HOURS))
    }
}
