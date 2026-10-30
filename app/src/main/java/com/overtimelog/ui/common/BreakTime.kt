package com.overtimelog.ui.common

import com.overtimelog.data.settings.BreakUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 休息扣除的"分钟 ↔ 用户输入单位"换算。
 *
 * 存储层永远是分钟；这里只负责录入页和设置页的显示与解析，切换单位不会动到已有数据。
 */
object BreakTime {

    /** 150 分钟 + 小时单位 -> "2.5"；90 分钟 + 分钟单位 -> "90"。 */
    fun format(minutes: Int, unit: BreakUnit): String = when (unit) {
        BreakUnit.MINUTES -> minutes.toString()
        BreakUnit.HOURS -> BigDecimal(minutes)
            .divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * 把用户输入解析成分钟；非法或超出 0..1439 返回 null。
     *
     * 小时单位支持小数（0.5 = 30 分钟），四舍五入到整分钟。
     */
    fun parse(text: String, unit: BreakUnit): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val minutes = when (unit) {
            BreakUnit.MINUTES -> trimmed.toIntOrNull()
            BreakUnit.HOURS -> trimmed.toBigDecimalOrNull()
                ?.multiply(BigDecimal(60))
                ?.setScale(0, RoundingMode.HALF_UP)
                ?.toInt()
        } ?: return null
        return minutes.takeIf { it in 0..MAX_BREAK_MINUTES }
    }

    /** 输入框下面的换算提示：小时单位显示"= 30 分钟"，分钟单位显示"= 0.5 小时"。 */
    fun hint(text: String, unit: BreakUnit): String? {
        val minutes = parse(text, unit) ?: return null
        return when (unit) {
            BreakUnit.HOURS -> "= $minutes 分钟"
            BreakUnit.MINUTES -> "= ${format(minutes, BreakUnit.HOURS)} 小时"
        }
    }

    fun unitLabel(unit: BreakUnit): String = when (unit) {
        BreakUnit.MINUTES -> "分钟"
        BreakUnit.HOURS -> "小时"
    }

    const val MAX_BREAK_MINUTES = 1439
}
