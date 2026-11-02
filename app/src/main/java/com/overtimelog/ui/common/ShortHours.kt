package com.overtimelog.ui.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 日历格子里"当天加班小时数"的紧凑写法。
 *
 * 日历格子空间极小（一个格子还要放日期、休/班角标、节日名），所以不能写"2 小时 30 分"，
 * 统一压成 `3h`、`2.5h` 这种形式：
 * - 整小时不带小数（180 分钟 → `3h`）；
 * - 有零头保留一位小数（90 分钟 → `1.5h`，30 分钟 → `0.5h`）；
 * - 不足 6 分钟（四舍五入后为 0.0）时显示 `1m` 之类，避免出现 `0h` 让用户以为没记上。
 */
object ShortHours {

    /** 分钟数 → 日历里显示的短文本，例如 180 → "3h"、150 → "2.5h"。 */
    fun of(minutes: Int): String {
        if (minutes <= 0) return ""
        val hours = BigDecimal(minutes)
            .divide(BigDecimal(60), 1, RoundingMode.HALF_UP)
        return if (hours.compareTo(BigDecimal.ZERO) == 0) {
            // 不足 3 分钟，按小时显示会变成 0h，改显示分钟
            "${minutes}m"
        } else {
            hours.stripTrailingZeros().toPlainString() + "h"
        }
    }
}
