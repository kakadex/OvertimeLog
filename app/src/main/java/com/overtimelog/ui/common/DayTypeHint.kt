package com.overtimelog.ui.common

import com.overtimelog.core.model.DayInfo
import com.overtimelog.core.model.DayKind
import com.overtimelog.core.model.OvertimeType

/**
 * 录入页「加班类型」下面那行说明：讲清楚默认值为什么是这个类型。
 *
 * 用户不读代码，所以这里必须说人话：
 * "这天是法定节假日，默认按法定节假日算" 比 "DayKind.HOLIDAY" 有用得多。
 *
 * @param dayInfo 当前选中日期的性质；为 null（还没算出来）时给一句通用说明
 * @param currentType 当前选中的加班类型
 * @param manuallySet 用户是否亲手改过类型——改过之后要说明"你的选择会保留"
 */
fun dayTypeHint(dayInfo: DayInfo?, currentType: OvertimeType, manuallySet: Boolean): String {
    if (dayInfo == null) {
        return if (manuallySet) "已手动选择「${currentType.label()}」" else "默认按日期自动判定"
    }
    val kindText = when (dayInfo.kind) {
        DayKind.WORKDAY -> "工作日"
        DayKind.REST_DAY -> "周末休息日"
        DayKind.HOLIDAY -> dayInfo.name.ifBlank { "法定节假日" }
        DayKind.MAKEUP_WORKDAY -> dayInfo.name.ifBlank { "调休补班日" }
    }
    val autoLabel = dayInfo.kind.defaultOvertimeType().label()
    return if (manuallySet) {
        "这天是$kindText（默认「$autoLabel」），你手动改成了「${currentType.label()}」；换日期不会改掉你的选择"
    } else {
        "这天是$kindText，默认按「$autoLabel」算；可以按实际情况改"
    }
}
