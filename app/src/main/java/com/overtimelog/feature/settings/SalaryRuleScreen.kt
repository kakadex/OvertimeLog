package com.overtimelog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.components.SectionCard

/**
 * 薪资规则编辑页。
 *
 * 保存后写一条新规则并把旧规则置为历史；已有记录的金额不会变（每条记录存了规则快照）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryRuleScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SalaryRuleViewModel = viewModel(factory = SalaryRuleViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SalaryRuleEvent.Saved -> onBack()
                is SalaryRuleEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            // statusBarsPadding：全面屏状态栏会压住标题和返回键（同 RecordEditScreen）
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("薪资规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部多留 24dp：最后的「保存规则」按钮不被手势条/导航栏压住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "怎么算时薪") {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.mode == SalaryMode.MONTHLY,
                                onClick = { viewModel.setMode(SalaryMode.MONTHLY) },
                                label = { Text("按月薪") },
                            )
                            FilterChip(
                                selected = state.mode == SalaryMode.HOURLY,
                                onClick = { viewModel.setMode(SalaryMode.HOURLY) },
                                label = { Text("按时薪") },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (state.mode == SalaryMode.MONTHLY) {
                            Field(
                                value = state.monthlySalaryText,
                                onChange = viewModel::setMonthlySalary,
                                label = "月薪（元）",
                            )
                            Spacer(Modifier.height(8.dp))
                            Field(
                                value = state.payDaysText,
                                onChange = viewModel::setPayDays,
                                label = "月计薪天数（默认 21.75）",
                                decimal = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            Field(
                                value = state.hoursPerDayText,
                                onChange = viewModel::setHoursPerDay,
                                label = "日标准工时（小时）",
                                decimal = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "小时工资 = 月薪 ÷ 月计薪天数 ÷ 日标准工时",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Field(
                                value = state.hourlyRateText,
                                onChange = viewModel::setHourlyRate,
                                label = "时薪（元/小时）",
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "加班倍率") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(
                                value = state.workdayText,
                                onChange = viewModel::setWorkday,
                                label = "工作日",
                                decimal = true,
                                modifier = Modifier.weight(1f),
                            )
                            Field(
                                value = state.restDayText,
                                onChange = viewModel::setRestDay,
                                label = "休息日",
                                decimal = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(
                                value = state.holidayText,
                                onChange = viewModel::setHoliday,
                                label = "法定节假日",
                                decimal = true,
                                modifier = Modifier.weight(1f),
                            )
                            Field(
                                value = state.customText,
                                onChange = viewModel::setCustom,
                                label = "自定义",
                                decimal = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = "常见写法：工作日 1.5、休息日 2、法定节假日 3。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.error != null) {
                item {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Text(
                    text = "修改规则只影响之后的记录。已经保存的记录带着当时的规则快照，金额不会变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(if (state.saving) "保存中…" else "保存规则")
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
