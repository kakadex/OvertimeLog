package com.overtimelog.ui.common

import com.overtimelog.core.model.DayInfo
import com.overtimelog.core.model.DayKind
import com.overtimelog.core.model.OvertimeType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 录入页「加班类型」那行说明文字。
 *
 * 这里钉住两件事：
 * ① 上班日 / 周末 / 节假日 / 补班要说出各自的日期性质，用户才知道默认值从哪来；
 * ② 用户手动改过类型时，文案必须明确"换日期不会改掉你的选择"，否则用户会以为被系统覆盖了。
 */
class DayTypeHintTest {

    private fun info(kind: DayKind, name: String = "") = DayInfo(
        date = java.time.LocalDate.of(2026, 10, 1),
        kind = kind,
        name = name,
    )

    @Test
    fun `holiday explains why the type is holiday`() {
        val text = dayTypeHint(info(DayKind.HOLIDAY, "国庆节"), OvertimeType.HOLIDAY, manuallySet = false)
        assertTrue(text.contains("国庆节"))
        assertTrue(text.contains("法定节假日"))
    }

    @Test
    fun `makeup workday defaults to workday wording`() {
        val text = dayTypeHint(info(DayKind.MAKEUP_WORKDAY, "国庆节后补班"), OvertimeType.WORKDAY, false)
        assertTrue(text.contains("国庆节后补班"))
        assertTrue(text.contains("工作日"))
    }

    @Test
    fun `weekend explains rest day`() {
        val text = dayTypeHint(info(DayKind.REST_DAY), OvertimeType.REST_DAY, false)
        assertTrue(text.contains("周末休息日"))
        assertTrue(text.contains("休息日"))
    }

    @Test
    fun `manual choice is described as kept`() {
        val text = dayTypeHint(info(DayKind.WORKDAY), OvertimeType.HOLIDAY, manuallySet = true)
        assertTrue(text.contains("手动"))
        assertTrue(text.contains("换日期不会改掉"))
    }

    @Test
    fun `unknown day info still gives a usable hint`() {
        assertTrue(dayTypeHint(null, OvertimeType.WORKDAY, false).isNotBlank())
        assertTrue(dayTypeHint(null, OvertimeType.HOLIDAY, true).contains("法定节假日"))
    }
}
