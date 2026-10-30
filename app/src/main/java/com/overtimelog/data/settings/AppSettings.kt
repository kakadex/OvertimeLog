package com.overtimelog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** 主题模式：跟随系统 / 浅色 / 深色。 */
enum class ThemeMode(val code: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK"),
    ;

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * 休息扣除的输入单位。
 *
 * 只影响"用户怎么填、界面怎么显示"，数据库里永远存分钟（Int），
 * 这样切换单位不会改变已有记录，导出和统计也不受影响。
 */
enum class BreakUnit(val code: String) {
    MINUTES("MINUTES"),
    HOURS("HOURS"),
    ;

    companion object {
        fun fromCode(code: String?): BreakUnit =
            entries.firstOrNull { it.code == code } ?: HOURS
    }
}

/**
 * 应用设置。
 *
 * 这些是"轻量、不需要查询"的偏好，所以放 DataStore 而不是 Room；
 * 只有薪资规则、记录、调休这类需要事务和聚合的数据才进数据库。
 */
data class AppSettings(
    /** 默认休息扣除，单位永远是分钟。 */
    val defaultBreakMinutes: Int = 0,
    /** 录入页休息扣除的输入单位，默认按小时。 */
    val breakUnit: BreakUnit = BreakUnit.HOURS,
    val defaultOvertimeType: OvertimeType = OvertimeType.WORKDAY,
    /** 默认上班、下班时间，新建记录时直接带出来。 */
    val defaultStartTime: LocalTime = LocalTime.of(18, 0),
    val defaultEndTime: LocalTime = LocalTime.of(21, 0),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hasSeenPrivacyNotice: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 30,
)

/** DataStore 文件名。用 `by preferencesDataStore` 保证全进程只有一个实例。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            defaultBreakMinutes = prefs[KEY_DEFAULT_BREAK] ?: 0,
            breakUnit = BreakUnit.fromCode(prefs[KEY_BREAK_UNIT]),
            defaultOvertimeType = OvertimeType.fromCode(prefs[KEY_DEFAULT_TYPE] ?: OvertimeType.WORKDAY.code),
            defaultStartTime = minutesOfDayToTime(prefs[KEY_DEFAULT_START] ?: DEFAULT_START_MINUTES),
            defaultEndTime = minutesOfDayToTime(prefs[KEY_DEFAULT_END] ?: DEFAULT_END_MINUTES),
            themeMode = ThemeMode.fromCode(prefs[KEY_THEME]),
            hasSeenPrivacyNotice = prefs[KEY_SEEN_PRIVACY] ?: false,
            reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
            reminderHour = prefs[KEY_REMINDER_HOUR] ?: 18,
            reminderMinute = prefs[KEY_REMINDER_MINUTE] ?: 30,
        )
    }

    override suspend fun setDefaultBreakMinutes(minutes: Int) {
        store.edit { it[KEY_DEFAULT_BREAK] = minutes.coerceIn(0, 1439) }
    }

    override suspend fun setBreakUnit(unit: BreakUnit) {
        store.edit { it[KEY_BREAK_UNIT] = unit.code }
    }

    override suspend fun setDefaultTimes(start: LocalTime, end: LocalTime) {
        store.edit {
            it[KEY_DEFAULT_START] = start.hour * 60 + start.minute
            it[KEY_DEFAULT_END] = end.hour * 60 + end.minute
        }
    }

    override suspend fun setDefaultOvertimeType(type: OvertimeType) {
        store.edit { it[KEY_DEFAULT_TYPE] = type.code }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.code }
    }

    override suspend fun setPrivacyNoticeSeen() {
        store.edit { it[KEY_SEEN_PRIVACY] = true }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        store.edit {
            it[KEY_REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[KEY_REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    private fun minutesOfDayToTime(minutes: Int): LocalTime {
        val safe = minutes.coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(safe / 60, safe % 60)
    }

    private companion object {
        const val DEFAULT_START_MINUTES = 18 * 60
        const val DEFAULT_END_MINUTES = 21 * 60

        val KEY_DEFAULT_BREAK = intPreferencesKey("default_break_minutes")
        val KEY_BREAK_UNIT = stringPreferencesKey("break_unit")
        val KEY_DEFAULT_TYPE = stringPreferencesKey("default_overtime_type")
        val KEY_DEFAULT_START = intPreferencesKey("default_start_minutes_of_day")
        val KEY_DEFAULT_END = intPreferencesKey("default_end_minutes_of_day")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SEEN_PRIVACY = booleanPreferencesKey("seen_privacy_notice")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
