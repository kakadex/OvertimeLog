package com.overtimelog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.overtimelog.core.model.DomainError
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.Money
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * 薪资规则编辑页状态。
 *
 * 所有金额输入都是"元"的字符串，保存时才换算成分；倍率同样是"1.5"这种可读写法，
 * 落库时乘 1000 变成基点。
 */
data class SalaryRuleUiState(
    val loading: Boolean = true,
    val mode: SalaryMode = SalaryMode.MONTHLY,
    val monthlySalaryText: String = "10000",
    val hourlyRateText: String = "",
    val payDaysText: String = "21.75",
    val hoursPerDayText: String = "8",
    val workdayText: String = "1.5",
    val restDayText: String = "2",
    val holidayText: String = "3",
    val customText: String = "1",
    val saving: Boolean = false,
    val error: String? = null,
)

sealed interface SalaryRuleEvent {
    data object Saved : SalaryRuleEvent
    data class Message(val text: String) : SalaryRuleEvent
}

class SalaryRuleViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(SalaryRuleUiState())
    val uiState: StateFlow<SalaryRuleUiState> = _state.asStateFlow()

    private val _events = Channel<SalaryRuleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var existingRule: SalaryRule? = null

    init {
        viewModelScope.launch {
            val rule = container.salaryRuleRepository.getActiveRule()
            existingRule = rule
            if (rule == null) {
                _state.update { it.copy(loading = false) }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        mode = rule.calculationMode,
                        monthlySalaryText = Money.yuanText(rule.monthlySalaryCents),
                        hourlyRateText = Money.yuanText(rule.hourlyRateCents),
                        payDaysText = rule.payDaysPerMonth.toPlainString(),
                        hoursPerDayText = rule.standardHoursPerDay.toPlainString(),
                        workdayText = formatBp(rule.workdayMultiplierBp),
                        restDayText = formatBp(rule.restDayMultiplierBp),
                        holidayText = formatBp(rule.holidayMultiplierBp),
                        customText = formatBp(rule.customMultiplierBp),
                    )
                }
            }
        }
    }

    fun setMode(mode: SalaryMode) = update { it.copy(mode = mode) }
    fun setMonthlySalary(text: String) = update { it.copy(monthlySalaryText = text.take(12)) }
    fun setHourlyRate(text: String) = update { it.copy(hourlyRateText = text.take(10)) }
    fun setPayDays(text: String) = update { it.copy(payDaysText = text.take(6)) }
    fun setHoursPerDay(text: String) = update { it.copy(hoursPerDayText = text.take(4)) }
    fun setWorkday(text: String) = update { it.copy(workdayText = text.take(5)) }
    fun setRestDay(text: String) = update { it.copy(restDayText = text.take(5)) }
    fun setHoliday(text: String) = update { it.copy(holidayText = text.take(5)) }
    fun setCustom(text: String) = update { it.copy(customText = text.take(5)) }

    private fun update(block: (SalaryRuleUiState) -> SalaryRuleUiState) {
        _state.update(block)
        _state.update { it.copy(error = null) }
    }

    fun save() {
        val s = _state.value
        val monthlyCents = Money.parseYuan(s.monthlySalaryText)
        val hourlyCents = Money.parseYuan(s.hourlyRateText)
        val payDays = s.payDaysText.toBigDecimalOrNull()
        val hours = s.hoursPerDayText.toBigDecimalOrNull()
        val workdayBp = parseBp(s.workdayText)
        val restBp = parseBp(s.restDayText)
        val holidayBp = parseBp(s.holidayText)
        val customBp = parseBp(s.customText)

        val invalid = when {
            s.mode == SalaryMode.MONTHLY && (monthlyCents == null || monthlyCents <= 0) -> "月薪要填大于 0 的数字"
            s.mode == SalaryMode.MONTHLY && (payDays == null || payDays <= BigDecimal.ZERO) -> "计薪天数要大于 0"
            s.mode == SalaryMode.MONTHLY && (hours == null || hours <= BigDecimal.ZERO) -> "日标准工时要大于 0"
            s.mode == SalaryMode.HOURLY && (hourlyCents == null || hourlyCents <= 0) -> "时薪要填大于 0 的数字"
            workdayBp == null || restBp == null || holidayBp == null || customBp == null -> "倍率要填大于 0 的数字，比如 1.5"
            else -> null
        }
        if (invalid != null) {
            _state.update { it.copy(error = invalid) }
            return
        }

        val rule = SalaryRule(
            id = 0,
            calculationMode = s.mode,
            monthlySalaryCents = monthlyCents ?: 0,
            hourlyRateCents = hourlyCents ?: 0,
            payDaysPerMonth = payDays ?: SalaryRule.DEFAULT_PAY_DAYS,
            standardHoursPerDay = hours ?: SalaryRule.DEFAULT_STANDARD_HOURS,
            workdayMultiplierBp = workdayBp!!,
            restDayMultiplierBp = restBp!!,
            holidayMultiplierBp = holidayBp!!,
            customMultiplierBp = customBp!!,
            compTimeRatioBp = existingRule?.compTimeRatioBp ?: 1000,
            effectiveFrom = Instant.now(container.clock),
            active = true,
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                container.salaryRuleRepository.saveRule(rule)
                _events.send(SalaryRuleEvent.Saved)
            } catch (t: Throwable) {
                val message = if (t is DomainError) t.message ?: "保存失败" else "保存失败，请重试"
                _events.send(SalaryRuleEvent.Message(message))
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun formatBp(bp: Int): String =
        BigDecimal(bp).divide(BigDecimal(1000)).stripTrailingZeros().toPlainString()

    private fun parseBp(text: String): Int? {
        val value = text.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return value.multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SalaryRuleViewModel(container) }
        }
    }
}
