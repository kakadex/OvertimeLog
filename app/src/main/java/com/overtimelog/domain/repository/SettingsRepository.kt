package com.overtimelog.domain.repository

import com.overtimelog.core.model.OvertimeType
import com.overtimelog.data.settings.AppSettings
import com.overtimelog.data.settings.BreakUnit
import com.overtimelog.data.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** 设置读写。实现放在 data 层（DataStore）。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultBreakMinutes(minutes: Int)
    suspend fun setBreakUnit(unit: BreakUnit)
    suspend fun setDefaultTimes(start: LocalTime, end: LocalTime)
    suspend fun setDefaultOvertimeType(type: OvertimeType)
    /** 切换「简单记模式」：开启后录入页只填加班小时数，不问上下班时间。 */
    suspend fun setSimpleEntryMode(enabled: Boolean)
    /** 简单记模式预填的小时数（字符串，保留用户输入的原文）。 */
    suspend fun setDefaultSimpleHours(text: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUpdateCheckOnStartup(enabled: Boolean)
    /** 手动指定某天的性质（休息日 / 节假日 / 补班）；传 null 表示恢复自动判断。 */
    suspend fun setDayOverride(date: java.time.LocalDate, kindCode: String?)
    /** 用远程拿到的某一年放假安排替换本地缓存（只替换该年）。 */
    suspend fun replaceRemoteHolidays(year: Int, entries: Set<String>)
    suspend fun setPrivacyNoticeSeen()
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setReminderTime(hour: Int, minute: Int)
}
