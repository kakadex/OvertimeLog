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
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUpdateCheckOnStartup(enabled: Boolean)
    suspend fun setPrivacyNoticeSeen()
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setReminderTime(hour: Int, minute: Int)
}
