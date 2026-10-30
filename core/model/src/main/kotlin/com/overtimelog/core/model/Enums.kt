package com.overtimelog.core.model

/**
 * 加班类型。
 *
 * [defaultMultiplierBp] 只是"新建规则时的默认值"，真正参与计算的是 [SalaryRule] 里保存的倍率。
 * 倍率一律用基点（bp）表达：1500 表示 1.5 倍，2000 表示 2.0 倍，避免浮点误差。
 */
enum class OvertimeType(val code: String, val defaultMultiplierBp: Int) {
    WORKDAY("WORKDAY", 1500),
    REST_DAY("REST_DAY", 2000),
    HOLIDAY("HOLIDAY", 3000),
    CUSTOM("CUSTOM", 1000),
    ;

    companion object {
        fun fromCode(code: String): OvertimeType =
            entries.firstOrNull { it.code == code } ?: WORKDAY
    }
}

/** 结算状态：这笔加班费是否已经发放/调休是否已经用掉。 */
enum class SettlementStatus(val code: String) {
    UNSETTLED("UNSETTLED"),
    SETTLED("SETTLED"),
    ;

    companion object {
        fun fromCode(code: String): SettlementStatus =
            entries.firstOrNull { it.code == code } ?: UNSETTLED
    }
}

/** 这条记录怎么换调休：不使用、1:1、1:1.5、手动填写分钟数。 */
enum class CompTimeMode(val code: String) {
    NONE("NONE"),
    RATIO_1_1("RATIO_1_1"),
    RATIO_1_1_5("RATIO_1_1_5"),
    MANUAL("MANUAL"),
    ;

    companion object {
        fun fromCode(code: String): CompTimeMode =
            entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** 薪资规则的计算方式：直接给时薪，或给月薪由系统换算。 */
enum class SalaryMode(val code: String) {
    HOURLY("HOURLY"),
    MONTHLY("MONTHLY"),
    ;

    companion object {
        fun fromCode(code: String): SalaryMode =
            entries.firstOrNull { it.code == code } ?: MONTHLY
    }
}
