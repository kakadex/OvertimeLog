package com.overtimelog.feature.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.repository.RecordFilter
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

/** 列表筛选条件。月份为 null 表示"全部月份"。 */
data class RecordsFilter(
    val month: YearMonth? = YearMonth.now(),
    val type: OvertimeType? = null,
    val settlement: SettlementStatus? = null,
    val keyword: String = "",
)

data class RecordsUiState(
    val filter: RecordsFilter = RecordsFilter(),
    val records: List<OvertimeRecord> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && records.isEmpty()
}

/** 一次性事件：删除后的"撤销"提示，以及普通错误提示。 */
sealed interface RecordsEvent {
    data class Deleted(val record: OvertimeRecord) : RecordsEvent
    data class Message(val text: String) : RecordsEvent
}

/**
 * 记录列表状态。
 *
 * 筛选条件本身是一条 StateFlow，用 flatMapLatest 转成数据库查询：改一次筛选只换一条查询，
 * 不会把上一次查询的结果残留在界面上。
 */
class RecordsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val filter = MutableStateFlow(RecordsFilter())
    private val events = Channel<RecordsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val records = filter.flatMapLatest { current ->
        container.overtimeRepository.observeRecords(
            RecordFilter(
                month = current.month,
                type = current.type,
                settlement = current.settlement,
                keyword = current.keyword,
            ),
        )
    }

    val uiState: StateFlow<RecordsUiState> = combine(filter, records) { current, list ->
        RecordsUiState(filter = current, records = list, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), RecordsUiState())

    fun setMonth(month: YearMonth?) = updateFilter { it.copy(month = month) }

    /** 点顶部年月手动输入后跳转：同时把"全部月份"改回指定月份。 */
    fun jumpTo(target: YearMonth) = updateFilter { it.copy(month = target) }

    fun previousMonth() = updateFilter {
        it.copy(month = (it.month ?: YearMonth.now()).minusMonths(1))
    }

    fun nextMonth() = updateFilter {
        it.copy(month = (it.month ?: YearMonth.now()).plusMonths(1))
    }

    fun setType(type: OvertimeType?) = updateFilter { it.copy(type = type) }

    fun setSettlement(status: SettlementStatus?) = updateFilter { it.copy(settlement = status) }

    fun setKeyword(keyword: String) = updateFilter { it.copy(keyword = keyword) }

    private fun updateFilter(block: (RecordsFilter) -> RecordsFilter) {
        filter.value = block(filter.value)
    }

    fun delete(record: OvertimeRecord) {
        viewModelScope.launch {
            container.deleteRecord(record.id).fold(
                onSuccess = { events.send(RecordsEvent.Deleted(it)) },
                onFailure = { events.send(RecordsEvent.Message(it.toUiMessage())) },
            )
        }
    }

    fun undoDelete(record: OvertimeRecord) {
        viewModelScope.launch {
            container.restoreRecord(record).onFailure {
                events.send(RecordsEvent.Message(it.toUiMessage()))
            }
        }
    }

    fun toggleSettlement(record: OvertimeRecord) {
        viewModelScope.launch {
            val settled = record.settlementStatus == SettlementStatus.SETTLED
            container.markSettled(listOf(record.id), settled = !settled).onFailure {
                events.send(RecordsEvent.Message(it.toUiMessage()))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RecordsViewModel(container) }
        }
    }
}
