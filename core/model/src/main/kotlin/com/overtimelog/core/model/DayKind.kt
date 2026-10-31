package com.overtimelog.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 某一天是什么日子，决定日历上怎么标、加班默认按哪种倍率算。
 *
 * 和 [OvertimeType] 的区别：[OvertimeType] 是"这条记录按哪种钱算"，由用户选；
 * [DayKind] 是"这天本身是什么性质"，由日历数据决定，用来在日历上做休 / 班标记。
 */
enum class DayKind(val code: String) {
    /** 普通工作日。 */
    WORKDAY("WORKDAY"),

    /** 周末休息日（不含法定节假日）。 */
    REST_DAY("REST_DAY"),

    /** 法定节假日。 */
    HOLIDAY("HOLIDAY"),

    /** 调休补班（周末上班）。 */
    MAKEUP_WORKDAY("MAKEUP_WORKDAY"),
    ;

    companion object {
        fun fromCode(code: String?): DayKind? = entries.firstOrNull { it.code == code }
    }
}

/** 某一天的日历信息：性质 + 显示用的名字（如"国庆节""春节前补班"）。 */
data class DayInfo(
    val date: LocalDate,
    val kind: DayKind,
    /** 节假日 / 补班的名字；普通周末和工作日为空。 */
    val name: String = "",
    /** 用户自己改过（覆盖了内置数据），界面可以标记一下。 */
    val manual: Boolean = false,
)

/**
 * 中国法定节假日与调休安排。
 *
 * 数据按年份写死：官方每年 11 月左右才公布次年安排，所以这里只能覆盖已知年份。
 * 拿不到数据的年份（或官方临时调整）由用户在日历里手动改，改动会存进 DataStore，
 * 优先级高于这里的常量，见 [DayCalendar.resolve]。
 *
 * 标记约定：`休` = 放假（法定节假日或周末），`班` = 调休补班要上班。
 */
object HolidayCalendar {

    /**
     * 内置数据：`日期 -> 名字`。
     *
     * 只放"和普通周末不一样"的日期：
     * - 法定节假日（工作日也放假）
     * - 调休补班（周末要上班）
     * 普通周末不需要记录，由星期几自动判断。
     */
    private val builtIn: Map<String, Pair<String, DayKind>> = buildMap {
        // ---------- 2025 ----------
        put("2025-01-01", "元旦" to DayKind.HOLIDAY)
        put("2025-01-26", "春节前补班" to DayKind.MAKEUP_WORKDAY)
        listOf(
            "2025-01-28" to "除夕", "2025-01-29" to "初一", "2025-01-30" to "初二",
            "2025-01-31" to "初三", "2025-02-01" to "初四", "2025-02-02" to "初五",
            "2025-02-03" to "初六", "2025-02-04" to "初七",
        ).forEach { (date, name) -> put(date, name to DayKind.HOLIDAY) }
        put("2025-02-08", "春节后补班" to DayKind.MAKEUP_WORKDAY)
        listOf("2025-04-04", "2025-04-05", "2025-04-06")
            .forEach { put(it, "清明节" to DayKind.HOLIDAY) }
        put("2025-04-27", "劳动节前补班" to DayKind.MAKEUP_WORKDAY)
        listOf("2025-05-01", "2025-05-02", "2025-05-03", "2025-05-04", "2025-05-05")
            .forEach { put(it, "劳动节" to DayKind.HOLIDAY) }
        listOf("2025-05-31", "2025-06-01", "2025-06-02")
            .forEach { put(it, "端午节" to DayKind.HOLIDAY) }
        put("2025-09-28", "国庆节前补班" to DayKind.MAKEUP_WORKDAY)
        listOf(
            "2025-10-01" to "国庆节", "2025-10-02" to "国庆节", "2025-10-03" to "国庆节",
            "2025-10-04" to "国庆节", "2025-10-05" to "国庆节", "2025-10-06" to "中秋节",
            "2025-10-07" to "国庆节", "2025-10-08" to "国庆节",
        ).forEach { (date, name) -> put(date, name to DayKind.HOLIDAY) }
        put("2025-10-11", "国庆节后补班" to DayKind.MAKEUP_WORKDAY)

        // ---------- 2026 ----------
        listOf("2026-01-01", "2026-01-02", "2026-01-03")
            .forEach { put(it, "元旦" to DayKind.HOLIDAY) }
        put("2026-01-04", "元旦后补班" to DayKind.MAKEUP_WORKDAY)
        put("2026-02-14", "春节前补班" to DayKind.MAKEUP_WORKDAY)
        listOf(
            "2026-02-15" to "春节", "2026-02-16" to "除夕", "2026-02-17" to "初一",
            "2026-02-18" to "初二", "2026-02-19" to "初三", "2026-02-20" to "初四",
            "2026-02-21" to "初五", "2026-02-22" to "初六", "2026-02-23" to "初七",
        ).forEach { (date, name) -> put(date, name to DayKind.HOLIDAY) }
        put("2026-02-28", "春节后补班" to DayKind.MAKEUP_WORKDAY)
        listOf("2026-04-04", "2026-04-05", "2026-04-06")
            .forEach { put(it, "清明节" to DayKind.HOLIDAY) }
        listOf("2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05")
            .forEach { put(it, "劳动节" to DayKind.HOLIDAY) }
        put("2026-05-09", "劳动节后补班" to DayKind.MAKEUP_WORKDAY)
        listOf("2026-06-19", "2026-06-20", "2026-06-21")
            .forEach { put(it, "端午节" to DayKind.HOLIDAY) }
        put("2026-09-20", "中秋节前补班" to DayKind.MAKEUP_WORKDAY)
        listOf("2026-09-25", "2026-09-26", "2026-09-27")
            .forEach { put(it, "中秋节" to DayKind.HOLIDAY) }
        listOf(
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04",
            "2026-10-05", "2026-10-06", "2026-10-07",
        ).forEach { put(it, "国庆节" to DayKind.HOLIDAY) }
        put("2026-10-10", "国庆节后补班" to DayKind.MAKEUP_WORKDAY)
    }

    /** 内置数据覆盖到的年份，界面可以提示"这一年还没公布安排"。 */
    val knownYears: Set<Int> = builtIn.keys.map { it.substring(0, 4).toInt() }.toSet()

    /**
     * 某天的性质：先看用户手动改过的，再看内置节假日，最后按星期几判断。
     *
     * @param manualOverrides 用户在日历里手动改的日期 -> 性质（不带名字）
     * @param remote 从远程接口更新下来的日期 -> 名称/是否放假，优先级高于内置常量、低于手动改动
     */
    fun resolve(
        date: LocalDate,
        manualOverrides: Map<LocalDate, DayKind> = emptyMap(),
        remote: Map<LocalDate, Pair<String, Boolean>> = emptyMap(),
    ): DayInfo {
        manualOverrides[date]?.let { override ->
            return DayInfo(date = date, kind = override, name = "", manual = true)
        }
        remote[date]?.let { (name, isHoliday) ->
            return DayInfo(
                date = date,
                kind = if (isHoliday) DayKind.HOLIDAY else DayKind.MAKEUP_WORKDAY,
                name = name,
                manual = false,
            )
        }
        builtIn[date.toString()]?.let { (name, kind) ->
            return DayInfo(date = date, kind = kind, name = name, manual = false)
        }
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        return DayInfo(
            date = date,
            kind = if (weekend) DayKind.REST_DAY else DayKind.WORKDAY,
            name = "",
            manual = false,
        )
    }

    /** 某年是否有官方安排数据（内置或已通过远程更新拿到）。 */
    fun hasDataFor(year: Int, remoteYears: Set<Int> = emptySet()): Boolean =
        year in knownYears || year in remoteYears
}
