package com.overtimelog.feature.comptime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.CompTimeBalance
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.core.model.DomainError
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CompTimeUiState(
    val balance: CompTimeBalance = CompTimeBalance(),
    val usages: List<CompTimeUsage> = emptyList(),
)

sealed interface CompTimeEvent {
    data class Message(val text: String) : CompTimeEvent
    data class AskOverdraft(val text: String) : CompTimeEvent
}

/**
 * 调休页状态：余额 + 使用明细。
 *
 * "获得"来自加班记录里的调休分钟数，"使用"来自这里新增的记录，余额是两者之差。
 */
class CompTimeViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val uiState: StateFlow<CompTimeUiState> = combine(
        container.compTimeRepository.observeBalance(),
        container.compTimeRepository.observeUsages(),
    ) { balance, usages ->
        CompTimeUiState(balance = balance, usages = usages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompTimeUiState())

    private val _events = Channel<CompTimeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun addUsage(date: LocalDate, minutes: Int, note: String, allowOverdraft: Boolean = false) {
        viewModelScope.launch {
            container.addCompTimeUsage(date, minutes, note, allowOverdraft).fold(
                onSuccess = { _events.send(CompTimeEvent.Message("已记录用掉 $minutes 分钟调休")) },
                onFailure = { error ->
                    if (error is DomainError.CompTimeInsufficient) {
                        _events.send(
                            CompTimeEvent.AskOverdraft(
                                "调休余额只剩 ${error.balance} 分钟，继续记录会变成负余额。仍然记录吗？",
                            ),
                        )
                    } else {
                        _events.send(CompTimeEvent.Message(error.toUiMessage()))
                    }
                },
            )
        }
    }

    fun deleteUsage(id: Long) {
        viewModelScope.launch {
            container.deleteCompTimeUsage(id).onFailure {
                _events.send(CompTimeEvent.Message(it.toUiMessage()))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CompTimeViewModel(container) }
        }
    }
}
