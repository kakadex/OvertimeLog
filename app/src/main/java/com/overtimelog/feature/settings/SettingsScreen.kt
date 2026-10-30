package com.overtimelog.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.data.settings.BreakUnit
import com.overtimelog.data.settings.ThemeMode
import com.overtimelog.BuildConfig
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.BreakTime
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.label
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.TimePickerModal
import java.time.LocalDate

/**
 * 设置页：薪资规则入口、录入默认值、主题、导出、清除数据、隐私说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenSalaryRule: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicense: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<DefaultTimeTarget?>(null) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var breakInput by remember { mutableStateOf("") }

    // 默认上下班时间决定的默认工时长（跨天按次日算），让用户一眼看到改时间的效果。
    val defaultStartMinutes = settings.defaultStartTime.hour * 60 + settings.defaultStartTime.minute
    val defaultEndMinutes = settings.defaultEndTime.hour * 60 + settings.defaultEndTime.minute
    val defaultRawMinutes = if (defaultEndMinutes <= defaultStartMinutes) {
        defaultEndMinutes + 24 * 60 - defaultStartMinutes
    } else {
        defaultEndMinutes - defaultStartMinutes
    }
    val defaultDurationText = Durations.text((defaultRawMinutes - settings.defaultBreakMinutes).coerceAtLeast(0))

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "薪资规则") {
                    Column {
                        if (rule == null) {
                            Text("还没有配置，加班费会算不出来。", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val current = rule!!
                            InfoRow(
                                "计算方式",
                                if (current.calculationMode == SalaryMode.MONTHLY) "按月薪" else "按时薪",
                            )
                            if (current.calculationMode == SalaryMode.MONTHLY) {
                                InfoRow("月薪", Money.format(current.monthlySalaryCents))
                                InfoRow("计薪天数", current.payDaysPerMonth.toPlainString())
                                InfoRow("日标准工时", current.standardHoursPerDay.toPlainString() + " 小时")
                            } else {
                                InfoRow("时薪", Money.hourlyRate(current.hourlyRateCents))
                            }
                            InfoRow(
                                "倍率",
                                "工作日 ${current.workdayMultiplierBp / 1000.0} / " +
                                    "休息日 ${current.restDayMultiplierBp / 1000.0} / " +
                                    "节假日 ${current.holidayMultiplierBp / 1000.0}",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenSalaryRule) {
                            Text(if (rule == null) "去配置" else "修改薪资规则")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "录入默认值") {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingTime = DefaultTimeTarget.START }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认上班时间", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = DateTexts.time(settings.defaultStartTime),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingTime = DefaultTimeTarget.END }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认下班时间", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = DateTexts.time(settings.defaultEndTime),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "默认工时长 $defaultDurationText（跨天按次日计算），实际录入时可以改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("默认休息扣除", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "按${BreakTime.unitLabel(settings.breakUnit)}填写",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    breakInput = BreakTime.format(
                                        settings.defaultBreakMinutes,
                                        settings.breakUnit,
                                    )
                                    showBreakDialog = true
                                },
                            ) {
                                Text(
                                    BreakTime.format(settings.defaultBreakMinutes, settings.breakUnit) +
                                        " " + BreakTime.unitLabel(settings.breakUnit),
                                )
                            }
                        }

                        Text("休息扣除单位", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(BreakUnit.HOURS to "小时", BreakUnit.MINUTES to "分钟").forEach { (unit, text) ->
                                FilterChip(
                                    selected = settings.breakUnit == unit,
                                    onClick = { viewModel.setBreakUnit(unit) },
                                    label = { Text(text) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("默认加班类型", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OvertimeType.entries.forEach { type ->
                                FilterChip(
                                    selected = settings.defaultOvertimeType == type,
                                    onClick = { viewModel.setDefaultType(type) },
                                    label = { Text(type.label()) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "跟随系统",
                            ThemeMode.LIGHT to "浅色",
                            ThemeMode.DARK to "深色",
                        ).forEach { (mode, labelText) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = { Text(labelText) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "数据") {
                    Column {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("加班记录_${LocalDate.now()}.csv")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("导出 CSV（全部记录）")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("清除全部数据", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "导出走系统文件选择器，不申请存储权限。清除后无法恢复，建议先导出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "隐私与关于") {
                    Column {
                        Text(
                            text = "所有加班记录只保存在这台手机上，应用不联网、不上传任何数据。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onOpenPrivacy) { Text("查看隐私说明") }
                        TextButton(onClick = onOpenLicense) { Text("开源许可（MIT）") }
                        InfoRow("版本", "${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）")
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清除全部数据？",
            text = "所有加班记录、调休记录都会被删除，且无法恢复。建议先导出 CSV。",
            confirmText = "确认清除",
            onConfirm = {
                viewModel.clearAllData()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    editingTime?.let { target ->
        TimePickerModal(
            initialTime = if (target == DefaultTimeTarget.START) {
                settings.defaultStartTime
            } else {
                settings.defaultEndTime
            },
            title = if (target == DefaultTimeTarget.START) "默认上班时间" else "默认下班时间",
            onConfirm = { time ->
                if (target == DefaultTimeTarget.START) {
                    viewModel.setDefaultTimes(time, settings.defaultEndTime)
                } else {
                    viewModel.setDefaultTimes(settings.defaultStartTime, time)
                }
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }

    if (showBreakDialog) {
        val parsed = BreakTime.parse(breakInput, settings.breakUnit)
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            title = { Text("默认休息扣除") },
            text = {
                OutlinedTextField(
                    value = breakInput,
                    onValueChange = { if (it.length <= 6) breakInput = it },
                    label = { Text("时长（${BreakTime.unitLabel(settings.breakUnit)}）") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (settings.breakUnit == BreakUnit.HOURS) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                    ),
                    singleLine = true,
                    isError = breakInput.isNotBlank() && parsed == null,
                    supportingText = {
                        Text(
                            BreakTime.hint(breakInput, settings.breakUnit)
                                ?: "例如 0.5 小时 = 30 分钟",
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let {
                            viewModel.setDefaultBreakMinutes(it)
                            showBreakDialog = false
                        }
                    },
                    enabled = parsed != null,
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBreakDialog = false }) { Text("取消") }
            },
        )
    }
}

private enum class DefaultTimeTarget { START, END }
