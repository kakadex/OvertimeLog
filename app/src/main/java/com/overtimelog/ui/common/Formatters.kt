package com.overtimelog.ui.common

import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 金额与时长格式化。
 *
 * 所有"分 -> 元"的展示都走这里，避免每个页面各写一套四舍五入，导致同一个数在两页显示不一样。
 */
object Money {

    /** 展示用：¥215.51 */
    fun format(cents: Long): String = "¥" + yuanText(cents)

    /** 纯数字：215.51（CSV、输入框回填用） */
    fun yuanText(cents: Long): String =
        BigDecimal(cents)
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            .toPlainString()

    /** 把用户输入的"元"解析成分；非法输入返回 null。 */
    fun parseYuan(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** 小时工资展示：57.47 元/小时 */
    fun hourlyRate(cents: Long): String = "${yuanText(cents)} 元/小时"
}

object Durations {

    /** 150 -> "2 小时 30 分" */
    fun text(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m 分钟"
            m == 0 -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    /** 150 -> "2.5 小时"，用于统计卡片这种空间紧张的地方 */
    fun hoursText(minutes: Int): String {
        val hours = BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
        return hours.stripTrailingZeros().toPlainString() + " 小时"
    }
}

object DateTexts {

    private val dateWithWeekday = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    private val yearMonth = DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE)
    private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
    private val timePattern = DateTimeFormatter.ofPattern("HH:mm")

    fun month(month: YearMonth): String = month.format(yearMonth)
    fun day(date: LocalDate): String = date.format(dateWithWeekday)
    fun full(date: LocalDate): String = date.format(fullDate)
    fun time(time: LocalTime): String = time.format(timePattern)
    fun range(start: LocalTime, end: LocalTime): String = "${time(start)} — ${time(end)}"
}

/** 枚举的中文文案集中在这里，避免散落在各页面。 */
fun OvertimeType.label(): String = when (this) {
    OvertimeType.WORKDAY -> "工作日"
    OvertimeType.REST_DAY -> "休息日"
    OvertimeType.HOLIDAY -> "法定节假日"
    OvertimeType.CUSTOM -> "自定义"
}

fun SettlementStatus.label(): String = when (this) {
    SettlementStatus.SETTLED -> "已结算"
    SettlementStatus.UNSETTLED -> "未结算"
}

fun CompTimeMode.label(): String = when (this) {
    CompTimeMode.NONE -> "不计调休"
    CompTimeMode.RATIO_1_1 -> "1:1"
    CompTimeMode.RATIO_1_1_5 -> "1:1.5"
    CompTimeMode.MANUAL -> "手动填写"
}
