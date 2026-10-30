package com.overtimelog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.data.settings.AppSettings
import com.overtimelog.data.settings.ThemeMode
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态：偏好设置 + 当前薪资规则 + 导出/清除动作。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), AppSettings())

    val rule: StateFlow<SalaryRule?> = container.salaryRuleRepository.observeActiveRule()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), null)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setDefaultBreakMinutes(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setDefaultBreakMinutes(minutes) }
    }

    fun setBreakUnit(unit: com.overtimelog.data.settings.BreakUnit) {
        viewModelScope.launch { container.settingsRepository.setBreakUnit(unit) }
    }

    fun setDefaultTimes(start: java.time.LocalTime, end: java.time.LocalTime) {
        viewModelScope.launch { container.settingsRepository.setDefaultTimes(start, end) }
    }

    fun setDefaultType(type: com.overtimelog.core.model.OvertimeType) {
        viewModelScope.launch { container.settingsRepository.setDefaultOvertimeType(type) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun exportCsv(uri: String) {
        viewModelScope.launch {
            container.exportCsv(uri).fold(
                onSuccess = { _events.send("已导出 ${it.count} 条记录") },
                onFailure = { _events.send(it.toUiMessage()) },
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.clearAllData().fold(
                onSuccess = { _events.send("已清除全部记录") },
                onFailure = { _events.send(it.toUiMessage()) },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
