package com.overtimelog.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.CompTimeBalance
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.repository.CompTimeRepository
import com.overtimelog.domain.repository.OvertimeRepository
import com.overtimelog.domain.repository.RecordFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class OverviewUiState(
    val month: YearMonth = YearMonth.now(),
    val summary: MonthlySummary = MonthlySummary(YearMonth.now()),
    val balance: CompTimeBalance = CompTimeBalance(),
    val recent: List<OvertimeRecord> = emptyList(),
    val loading: Boolean = true,
)

/**
 * 概览页状态。
 *
 * 月度汇总、调休余额、最近记录三条 Flow 都直接来自数据库，数据一变界面自动刷新，
 * 不需要在保存后手动"刷新列表"（架构文档 9.1）。
 */
class OverviewViewModel(
    overtimeRepository: OvertimeRepository,
    compTimeRepository: CompTimeRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<OverviewUiState> = combine(
        month.flatMapLatest { overtimeRepository.observeMonthSummary(it) },
        compTimeRepository.observeBalance(),
        month.flatMapLatest { overtimeRepository.observeRecords(RecordFilter(month = it)) },
        month,
    ) { summary, balance, records, currentMonth ->
        OverviewUiState(
            month = currentMonth,
            summary = summary,
            balance = balance,
            recent = records.take(3),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun backToCurrentMonth() {
        month.value = YearMonth.now()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OverviewViewModel(container.overtimeRepository, container.compTimeRepository) }
        }
    }
}
