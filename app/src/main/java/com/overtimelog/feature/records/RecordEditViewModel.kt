package com.overtimelog.feature.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.calculation.CalculationResult
import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.data.settings.BreakUnit
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.model.OvertimeDraft
import com.overtimelog.ui.common.BreakTime
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 录入/编辑页状态。
 *
 * 数字输入保留成字符串（[breakText]、[manualAmountText]），否则用户删到空或先输入"-"时
 * 会被强行改回 0，输入体验很别扭。解析失败时只是不更新预览，不丢用户输入。
 */
data class RecordEditUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val start: LocalTime = LocalTime.of(18, 0),
    val end: LocalTime = LocalTime.of(21, 0),
    val breakText: String = "0",
    /** 休息扣除的输入单位，来自设置；只影响显示与解析，落库永远是分钟。 */
    val breakUnit: BreakUnit = BreakUnit.HOURS,
    val type: OvertimeType = OvertimeType.WORKDAY,
    val compTimeMode: CompTimeMode = CompTimeMode.NONE,
    val manualAmountText: String = "",
    val manualCompText: String = "",
    val note: String = "",
    val settled: Boolean = false,
    val preview: CalculationResult? = null,
    val warning: String? = null,
    val ruleMissing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

sealed interface RecordEditEvent {
    data class Saved(val recordId: Long) : RecordEditEvent
    /** 删除成功，界面直接返回上一页。 */
    data object Deleted : RecordEditEvent
    data class Message(val text: String) : RecordEditEvent
    data class AskOverdraft(val message: String) : RecordEditEvent
}

/**
 * 新增/编辑记录。
 *
 * 每次输入变化都重算一遍预览（取消上一次未完成的计算），用的就是保存时那个 CalculationEngine。
 * 保存时用例会重新读一次规则再算，所以预览和落库不会出现两个数。
 */
class RecordEditViewModel(
    private val container: AppContainer,
    private val recordId: Long?,
    private val initialDate: LocalDate?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecordEditUiState(isEditing = recordId != null, loading = recordId != null),
    )
    val uiState: StateFlow<RecordEditUiState> = _state.asStateFlow()

    private val _events = Channel<RecordEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            if (recordId == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        date = initialDate ?: LocalDate.now(),
                        start = settings.defaultStartTime,
                        end = settings.defaultEndTime,
                        breakUnit = settings.breakUnit,
                        breakText = BreakTime.format(settings.defaultBreakMinutes, settings.breakUnit),
                        type = settings.defaultOvertimeType,
                    )
                }
                refreshPreview()
            } else {
                val record = container.overtimeRepository.getById(recordId)
                if (record == null) {
                    _state.update { it.copy(loading = false, error = "记录不存在，可能已被删除") }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            date = record.date,
                            start = record.start.toLocalTime(),
                            end = record.end.toLocalTime(),
                            breakUnit = settings.breakUnit,
                            breakText = BreakTime.format(record.breakMinutes, settings.breakUnit),
                            type = record.type,
                            compTimeMode = record.compTimeMode,
                            manualCompText = record.compTimeMinutes.toString(),
                            manualAmountText = record.manualAmountCents?.let { cents ->
                                com.overtimelog.ui.common.Money.yuanText(cents)
                            }.orEmpty(),
                            note = record.note,
                            settled = record.settlementStatus == SettlementStatus.SETTLED,
                        )
                    }
                    refreshPreview()
                }
            }
        }
    }

    fun onDateChange(date: LocalDate) {
        _state.update { it.copy(date = date) }
        refreshPreview()
    }

    fun onStartChange(time: LocalTime) {
        _state.update { it.copy(start = time) }
        refreshPreview()
    }

    fun onEndChange(time: LocalTime) {
        _state.update { it.copy(end = time) }
        refreshPreview()
    }

    fun onBreakChange(text: String) {
        // 小时单位允许 "0.25" 这种小数，长度放宽到 6 位；分钟单位 4 位足够。
        val limit = if (_state.value.breakUnit == BreakUnit.HOURS) 6 else 4
        if (text.length > limit) return
        _state.update { it.copy(breakText = text) }
        refreshPreview()
    }

    fun onTypeChange(type: OvertimeType) {
        _state.update { it.copy(type = type) }
        refreshPreview()
    }

    fun onCompTimeModeChange(mode: CompTimeMode) {
        _state.update { it.copy(compTimeMode = mode) }
        refreshPreview()
    }

    fun onManualAmountChange(text: String) {
        if (text.length > 12) return
        _state.update { it.copy(manualAmountText = text) }
        refreshPreview()
    }

    fun onManualCompChange(text: String) {
        if (text.length > 5) return
        _state.update { it.copy(manualCompText = text) }
        refreshPreview()
    }

    fun onNoteChange(text: String) {
        if (text.length > 200) return
        _state.update { it.copy(note = text) }
    }

    fun onSettledChange(settled: Boolean) {
        _state.update { it.copy(settled = settled) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun draftOrNull(): OvertimeDraft? {
        val s = _state.value
        val breakMinutes = BreakTime.parse(s.breakText, s.breakUnit) ?: return null
        val manualAmountCents = if (s.manualAmountText.isBlank()) {
            null
        } else {
            com.overtimelog.ui.common.Money.parseYuan(s.manualAmountText) ?: return null
        }
        val manualCompMinutes = if (s.compTimeMode == CompTimeMode.MANUAL) {
            s.manualCompText.toIntOrNull() ?: 0
        } else {
            null
        }
        return OvertimeDraft(
            date = s.date,
            startTime = s.start,
            endTime = s.end,
            breakMinutes = breakMinutes,
            type = s.type,
            compTimeMode = s.compTimeMode,
            note = s.note,
            settlementStatus = if (s.settled) SettlementStatus.SETTLED else SettlementStatus.UNSETTLED,
            manualAmountCents = manualAmountCents,
            manualCompTimeMinutes = manualCompMinutes,
        )
    }

    private fun refreshPreview() {
        previewJob?.cancel()
        val draft = draftOrNull() ?: return
        previewJob = viewModelScope.launch {
            container.calculatePreview(draft).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            preview = result,
                            warning = result.warnings.firstOrNull()?.toUiMessage(),
                            ruleMissing = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            preview = null,
                            warning = null,
                            ruleMissing = error is DomainError.RuleNotConfigured,
                        )
                    }
                },
            )
        }
    }

    fun save(allowOverdraft: Boolean = false) {
        val draft = draftOrNull()
        if (draft == null) {
            _state.update { it.copy(error = "休息分钟或金额格式不对，请检查") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = if (recordId == null) {
                container.addRecord(draft, allowOverdraft)
            } else {
                container.updateRecord(recordId, draft, allowOverdraft)
            }
            result.fold(
                onSuccess = { record -> _events.send(RecordEditEvent.Saved(record.id)) },
                onFailure = { error ->
                    if (error is DomainError.CompTimeInsufficient) {
                        _events.send(
                            RecordEditEvent.AskOverdraft(
                                "调休余额只剩 ${error.balance} 分钟，继续保存会变成透支。仍然保存吗？",
                            ),
                        )
                    } else {
                        _events.send(RecordEditEvent.Message(error.toUiMessage()))
                    }
                },
            )
            _state.update { it.copy(saving = false) }
        }
    }

    /** 删除当前记录（只在编辑已有记录时可用）。 */
    fun delete() {
        val id = recordId ?: return
        viewModelScope.launch {
            container.deleteRecord(id).fold(
                onSuccess = { _events.send(RecordEditEvent.Deleted) },
                onFailure = { _events.send(RecordEditEvent.Message(it.toUiMessage())) },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer, recordId: Long?, initialDate: LocalDate?) = viewModelFactory {
            initializer { RecordEditViewModel(container, recordId, initialDate) }
        }
    }
}
