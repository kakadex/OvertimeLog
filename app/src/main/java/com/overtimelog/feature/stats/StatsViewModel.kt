package com.overtimelog.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.YearSummary
import com.overtimelog.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

enum class StatsMode { MONTH, YEAR }

data class StatsUiState(
    val mode: StatsMode = StatsMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val year: Int = YearMonth.now().year,
    val monthSummary: MonthlySummary = MonthlySummary(YearMonth.now()),
    val yearSummary: YearSummary = YearSummary(YearMonth.now().year),
)

/**
 * 统计页状态。
 *
 * 月度汇总和年度汇总都来自 SQL 聚合，切换月份只是换一条查询。
 */
class StatsViewModel(
    container: AppContainer,
) : ViewModel() {

    private val mode = MutableStateFlow(StatsMode.MONTH)
    private val month = MutableStateFlow(YearMonth.now())
    private val year = MutableStateFlow(YearMonth.now().year)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthSummary = month.flatMapLatest { container.overtimeRepository.observeMonthSummary(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val yearSummary = year.flatMapLatest { container.overtimeRepository.observeYearSummary(it) }

    val uiState: StateFlow<StatsUiState> = combine(
        mode,
        month,
        year,
        monthSummary,
        yearSummary,
    ) { currentMode, currentMonth, currentYear, monthData, yearData ->
        StatsUiState(
            mode = currentMode,
            month = currentMonth,
            year = currentYear,
            monthSummary = monthData,
            yearSummary = yearData,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMode(newMode: StatsMode) {
        mode.value = newMode
    }

    fun previous() {
        if (mode.value == StatsMode.MONTH) month.value = month.value.minusMonths(1) else year.value -= 1
    }

    fun next() {
        if (mode.value == StatsMode.MONTH) month.value = month.value.plusMonths(1) else year.value += 1
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { StatsViewModel(container) }
        }
    }
}
