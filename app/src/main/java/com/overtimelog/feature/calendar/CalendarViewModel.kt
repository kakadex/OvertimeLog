package com.overtimelog.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.repository.DayMarker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val markers: Map<LocalDate, DayMarker> = emptyMap(),
    val dayRecords: List<OvertimeRecord> = emptyList(),
)

/**
 * 日历页状态。
 *
 * 月份标记用一条轻量 SQL（只查日期/类型/条数）而不是加载全天记录；
 * 选中的那一天才去查明细，所以翻月份不会卡。
 */
class CalendarViewModel(
    container: AppContainer,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val markers = month.flatMapLatest { current ->
        container.overtimeRepository.observeDayMarkers(current)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayRecords = selectedDate.flatMapLatest { date ->
        container.overtimeRepository.observeDayRecords(date)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        markers,
        dayRecords,
    ) { currentMonth, date, markerList, records ->
        CalendarUiState(
            month = currentMonth,
            selectedDate = date,
            markers = markerList.associateBy { it.day },
            dayRecords = records,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

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

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        if (YearMonth.from(date) != month.value) {
            month.value = YearMonth.from(date)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CalendarViewModel(container) }
        }
    }
}
