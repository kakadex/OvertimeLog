package com.overtimelog.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.core.model.YearSummary
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class StatsMode { MONTH, YEAR }

data class StatsUiState(
    val mode: StatsMode = StatsMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val year: Int = YearMonth.now().year,
    /** null = 全部；否则只统计该结算状态的记录。 */
    val settlement: SettlementStatus? = null,
    val monthSummary: MonthlySummary = MonthlySummary(YearMonth.now()),
    val yearSummary: YearSummary = YearSummary(YearMonth.now().year),
)

/**
 * 统计页状态。
 *
 * 月度汇总和年度汇总都来自 SQL 聚合；切换月份或结算状态只是换一条查询。
 * 结算状态筛选在 SQL 里完成（`settlement_status = ?`），不会把记录全捞出来再过滤。
 */
class StatsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val mode = MutableStateFlow(StatsMode.MONTH)
    private val month = MutableStateFlow(YearMonth.now())
    private val year = MutableStateFlow(YearMonth.now().year)
    private val settlement = MutableStateFlow<SettlementStatus?>(null)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val monthFilter = combine(month, settlement) { currentMonth, status ->
        currentMonth to status
    }

    private val yearFilter = combine(year, settlement) { currentYear, status ->
        currentYear to status
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthSummary = monthFilter.flatMapLatest { (currentMonth, status) ->
        container.overtimeRepository.observeMonthSummary(currentMonth, status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val yearSummary = yearFilter.flatMapLatest { (currentYear, status) ->
        container.overtimeRepository.observeYearSummary(currentYear, status)
    }

    private val summaries = combine(monthSummary, yearSummary) { monthData, yearData ->
        monthData to yearData
    }

    val uiState: StateFlow<StatsUiState> = combine(
        mode,
        month,
        year,
        settlement,
        summaries,
    ) { currentMode, currentMonth, currentYear, currentSettlement, data ->
        StatsUiState(
            mode = currentMode,
            month = currentMonth,
            year = currentYear,
            settlement = currentSettlement,
            monthSummary = data.first,
            yearSummary = data.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), StatsUiState())

    fun setMode(newMode: StatsMode) {
        mode.value = newMode
    }

    fun setSettlement(status: SettlementStatus?) {
        settlement.value = status
    }

    /** 一键把当前月份的全部记录改成统一结算状态。 */
    fun markMonthSettled(settled: Boolean) {
        viewModelScope.launch {
            container.markMonthSettled(month.value, settled).fold(
                onSuccess = { count ->
                    val action = if (settled) "已结算" else "未结算"
                    val message = if (count == 0) {
                        "本月记录本来就都是「$action」，没有需要改的"
                    } else {
                        "已把本月 $count 条记录改为「$action」（其余本来就是这个状态）"
                    }
                    _events.send(message)
                },
                onFailure = { _events.send(it.toUiMessage()) },
            )
        }
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
