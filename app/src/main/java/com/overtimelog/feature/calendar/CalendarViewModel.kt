package com.overtimelog.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.DayKind
import com.overtimelog.core.model.HolidayCalendar
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.repository.DayMarker
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val markers: Map<LocalDate, DayMarker> = emptyMap(),
    val dayRecords: List<OvertimeRecord> = emptyList(),
    /** 当月每一天的性质（是否放假、是否补班、节日名），日历格子直接用。 */
    val dayKinds: Map<LocalDate, com.overtimelog.core.model.DayInfo> = emptyMap(),
    /** 选中那天的性质，用来在详情区显示"法定节假日 / 调休补班"。 */
    val selectedDayInfo: com.overtimelog.core.model.DayInfo? = null,
    /** 当前年份是否有内置放假安排；没有时提示用户可手动标记。 */
    val hasHolidayData: Boolean = true,
)

/** 日历页的一次性事件：删除成功（带记录用于撤销）或出错提示。 */
sealed interface CalendarEvent {
    data class Deleted(val record: OvertimeRecord) : CalendarEvent
    data class Message(val text: String) : CalendarEvent
}

/** 从远程更新节假日数据的状态。 */
data class HolidaySyncState(
    val running: Boolean = false,
    val message: String? = null,
)

/**
 * 日历页状态。
 *
 * 月份标记用一条轻量 SQL（只查日期/类型/条数）而不是加载全天记录；
 * 选中的那一天才去查明细，所以翻月份不会卡。
 */
class CalendarViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _events = Channel<CalendarEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val markers = month.flatMapLatest { current ->
        container.overtimeRepository.observeDayMarkers(current)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayRecords = selectedDate.flatMapLatest { date ->
        container.overtimeRepository.observeDayRecords(date)
    }

    /** 用户手动改过的日期：`2026-10-10=MAKEUP_WORKDAY` -> Map。 */
    private val overrides = container.settingsRepository.settings.map { settings ->
        settings.dayOverrides.mapNotNull { (dateText, kindCode) ->
            val day = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@mapNotNull null
            val dayKind = DayKind.fromCode(kindCode) ?: return@mapNotNull null
            day to dayKind
        }.toMap()
    }

    /** 远程更新下来的安排：`2027-10-01=国庆节|1` -> Map(日期 -> 名称/是否放假)。 */
    private val remoteHolidays = container.settingsRepository.settings.map { settings ->
        settings.remoteHolidays.mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@mapNotNull null
            val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val value = parts[1]
            val name = value.substringBeforeLast('|', value)
            val holiday = value.substringAfterLast('|', "1") == "1"
            day to (name to holiday)
        }.toMap()
    }

    /** 已缓存了哪些年份的远程数据，用来判断"这一年有没有安排"。 */
    private val remoteYears = container.settingsRepository.settings.map { settings ->
        settings.remoteHolidays.mapNotNull { it.substringBefore('-').toIntOrNull() }.toSet()
    }

    /** 两份"日期性质"输入（手动改动 + 远程数据）先合成一份，避免 combine 参数过多。 */
    private val dayHints = combine(overrides, remoteHolidays, remoteYears) { manual, remote, years ->
        DayHints(manual = manual, remote = remote, years = years)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        markers,
        dayRecords,
        dayHints,
    ) { currentMonth, date, markerList, records, hints ->
        val kinds = (1..currentMonth.lengthOfMonth()).associate { dayOfMonth ->
            val day = currentMonth.atDay(dayOfMonth)
            day to HolidayCalendar.resolve(day, hints.manual, hints.remote)
        }
        CalendarUiState(
            month = currentMonth,
            selectedDate = date,
            markers = markerList.associateBy { it.day },
            dayRecords = records,
            dayKinds = kinds,
            selectedDayInfo = kinds[date] ?: HolidayCalendar.resolve(date, hints.manual, hints.remote),
            hasHolidayData = HolidayCalendar.hasDataFor(currentMonth.year, hints.years),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), CalendarUiState())

    /** 日期性质的三种输入：手动改动优先，其次远程数据，最后内置常量。 */
    private data class DayHints(
        val manual: Map<LocalDate, DayKind>,
        val remote: Map<LocalDate, Pair<String, Boolean>>,
        val years: Set<Int>,
    )

    fun previousMonth() {
        val target = month.value.minusMonths(1)
        month.value = target
        selectedDate.value = target.atDay(1)
    }

    fun nextMonth() {
        val target = month.value.plusMonths(1)
        month.value = target
        selectedDate.value = target.atDay(1)
    }

    /**
     * 跳到指定年月（点顶部「2026年11月」手动输入后调用）。
     *
     * 选中日期落到该月 1 号：如果用户输入的是当前月份，则保留今天的选中状态更自然。
     */
    fun jumpTo(target: YearMonth) {
        val current = month.value
        if (target == current) return
        month.value = target
        val today = LocalDate.now()
        selectedDate.value = if (YearMonth.from(today) == target) today else target.atDay(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        if (YearMonth.from(date) != month.value) {
            month.value = YearMonth.from(date)
        }
    }

    /** "今"按钮：把月份和选中日期都拉回今天。 */
    fun backToToday() {
        val today = LocalDate.now()
        month.value = YearMonth.from(today)
        selectedDate.value = today
    }

    /**
     * 手动改某天的性质（休息日 / 节假日 / 补班）。
     *
     * [kind] 传 null 表示恢复自动判断。改完日历会立刻重算，因为 overrides 是 Flow。
     */
    fun setDayOverride(date: LocalDate, kind: DayKind?) {
        viewModelScope.launch {
            container.settingsRepository.setDayOverride(date, kind?.code)
        }
    }

    private val _syncState = MutableStateFlow(HolidaySyncState())
    val syncState: StateFlow<HolidaySyncState> = _syncState

    /**
     * 从远程更新当前显示年份的放假安排。
     *
     * 国家没有官方机器可读接口，这里用第三方整理的数据；只在用户主动点击时联网，
     * 结果按年存进 DataStore，之后离线可用。
     */
    fun syncHolidays() {
        if (_syncState.value.running) return
        val year = month.value.year
        _syncState.value = HolidaySyncState(running = true)
        viewModelScope.launch {
            container.holidayRepository.fetchYear(year).fold(
                onSuccess = { days ->
                    if (days.isEmpty()) {
                        _syncState.value = HolidaySyncState(
                            message = "$year 年的放假安排还没公布，等公布后再点更新",
                        )
                        return@fold
                    }
                    val entries = days.values.map { day ->
                        val flag = if (day.holiday) 1 else 0
                        "${day.date}=${day.name.ifBlank { if (day.holiday) "节假日" else "补班" }}|$flag"
                    }.toSet()
                    container.settingsRepository.replaceRemoteHolidays(year, entries)
                    _syncState.value = HolidaySyncState(
                        message = "已更新 $year 年放假安排，共 ${days.size} 天",
                    )
                },
                onFailure = { error ->
                    _syncState.value = HolidaySyncState(
                        message = error.message ?: "更新失败，请稍后再试",
                    )
                },
            )
        }
    }

    fun clearSyncMessage() {
        _syncState.value = _syncState.value.copy(message = null)
    }

    fun delete(record: OvertimeRecord) {
        viewModelScope.launch {
            container.deleteRecord(record.id).fold(
                onSuccess = { _events.send(CalendarEvent.Deleted(it)) },
                onFailure = { _events.send(CalendarEvent.Message(it.toUiMessage())) },
            )
        }
    }

    fun undoDelete(record: OvertimeRecord) {
        viewModelScope.launch {
            container.restoreRecord(record).onFailure {
                _events.send(CalendarEvent.Message(it.toUiMessage()))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CalendarViewModel(container) }
        }
    }
}
