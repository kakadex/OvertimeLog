package com.overtimelog.ui.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 「简单记模式」里"小时数"的解析与格式化。
 *
 * 用户在录入页填的是小时（如 `2`、`2.5`、`0.75`），内部统一换算成分钟存库，
 * 这样列表、统计、导出都和其它记录走同一套逻辑，不需要为简单记单独维护一份数据。
 */
object SimpleHours {

    /** 单条记录的时长上限，与用例层校验保持一致。 */
    const val MAX_MINUTES = 24 * 60

    /**
     * "2.5" → 150 分钟。
     *
     * 返回 null 表示格式不对或超出范围（<=0 或 >24 小时），调用方据此提示"填错了"，
     * 而不是悄悄按 0 分钟存下去。
     */
    fun parseMinutes(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val hours = trimmed.toBigDecimalOrNull() ?: return null
        if (hours <= BigDecimal.ZERO) return null
        val minutes = hours
            .multiply(BigDecimal(60))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
        return minutes.takeIf { it in 1..MAX_MINUTES }
    }

    /** 150 分钟 → "2.5"；用于编辑简单记记录时把原值填回输入框。 */
    fun format(minutes: Int): String = BigDecimal(minutes)
        .divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    /** 输入框下方的提示语：已填合法值就给"= 2 小时 30 分"，否则给一句填写示例。 */
    fun hint(text: String): String {
        val minutes = parseMinutes(text)
        return if (minutes != null) {
            "= ${Durations.text(minutes)}"
        } else {
            "填 0 到 24 之间的小时数，可带小数，例如 2 或 2.5"
        }
    }
}
